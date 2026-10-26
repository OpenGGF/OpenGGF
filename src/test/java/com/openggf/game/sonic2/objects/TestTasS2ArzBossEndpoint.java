package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.GameModule;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic2.Sonic2ObjectArtProvider;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.level.LevelManager;
import com.openggf.level.Level;
import com.openggf.level.ChunkDesc;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.boss.BossExplosionObjectInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2ARZBossInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Final-hit endpoint overwrite, 1779M/3101S and 3325M/5373S; native pin 34fc7f13. */
@RequiresRom(SonicGame.SONIC_2)
class TestTasS2ArzBossEndpoint {
    @ParameterizedTest
    @CsvSource({"false,true,1", "true,true,1", "false,false,1", "true,false,1",
            "false,true,2", "true,true,2", "false,false,2", "true,false,2"})
    void endpointAddsToLiveRoutineAfterFinalTouch(boolean atEndpoint, boolean goingLeft, int hits) throws Exception {
        try (var reel = com.openggf.tests.reel.S2ComponentTestReel.open(15)) {
            AbstractObjectInstance.resetCameraBoundsForTests();
            var camera = mock(Camera.class);
            when(camera.getX()).thenReturn((short) 0x2A00);
            when(camera.getY()).thenReturn((short) 0x400);
            when(camera.getWidth()).thenReturn((short) 320);
            when(camera.getHeight()).thenReturn((short) 224);
            var services = new TestObjectServices().withCamera(camera).withGameState(new GameStateManager())
                    .withDebugOverlay(mock(DebugOverlayManager.class));
            var table = GameServices.module().createTouchResponseTable(RomByteReader.fromRom(TestEnvironment.currentRom()));
            var objects = new ObjectManager(List.of(), null, 0, null, table, null, camera, services);
            services.withDirectObjectManager(objects);
            var boss = new Sonic2ARZBossInstance(new ObjectSpawn(0x2AE0, 0x430, 0x89, 0, 0, false, 0));
            var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
            objects.addDynamicObject(boss);
            boss.update(0, player);
            com.openggf.tests.reel.S2ComponentTestReel.cameraFrame(player, objects.getActiveObjects(), 0, 0, "boss initialization", camera.getX(), camera.getY()); // real initialization, including child creation
            int x = goingLeft ? (atEndpoint ? 0x2AB1 : 0x2AC0) : (atEndpoint ? 0x2B10 : 0x2B00);
            set(boss, "bossXPos", x << 16);
            set(boss, "bossYPos", 0x430 << 16);
            set(boss, "bossXVel", goingLeft ? -0xC8 : 0xC8);
            set(boss, "bossYVel", 0);
            set(boss, "targetFlag", goingLeft);
            boss.getState().routine = 2;
            boss.getState().x = x;
            boss.getState().y = 0x430;
            boss.getState().hitCount = hits;
            player.setCentreX((short) x);
            player.setCentreY((short) 0x430);
            player.setRolling(true);
            player.setAnimationId(2);
            player.setAir(true);
            player.setYSpeed((short) 0x200);
            objects.snapshotTouchResponseState();
            objects.runTouchResponsesForPlayer(player, 1);
            assertEquals(hits - 1, boss.getState().hitCount, "actual player touch must deal one hit");
            assertFalse(boss.getState().defeated, "death is deferred to the boss's own later slot");
            boss.update(1, player);
            com.openggf.tests.reel.S2ComponentTestReel.cameraFrame(player, objects.getActiveObjects(), 0, 0, "endpoint/approach result", camera.getX(), camera.getY());
            assertEquals(hits == 1, boss.getState().defeated);
            assertEquals(hits == 1 ? 1000 : 0, services.gameState().getScore());
            // Obj89_Main_KillBoss writes 8, then Obj89_Main_Sub2_AtTarget adds 2.
            assertEquals((hits == 1 ? 8 : 2) + (atEndpoint ? 2 : 0), boss.getState().routine,
                    "Obj89_Main_Sub2_AtTarget adds 2 to the live routine after HandleFace");
        }
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void finalTouchReplaysThroughEscapeWithNativePlcSchedule(boolean endpoint, boolean goingLeft) throws Exception {
        var p = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        var camera = GameServices.camera();
        camera.setFocusedSprite(p);
        camera.setX((short) 0x2A00); camera.setY((short) 0x400);
        camera.setMinX((short) 0x2A00); camera.setMaxX((short) 0x2A00);
        var gameState = new GameStateManager();
        var requestedPlcs = new ArrayList<Integer>();
        var art = mock(Sonic2ObjectArtProvider.class);
        when(art.preparePlcs(any(int[].class))).thenReturn(new Sonic2ObjectArtProvider.PreparedPlc(List.of()));
        var plc = mock(Sonic2PlcService.class);
        doAnswer(call -> {
            for (var op : (Sonic2PlcService.Operation[]) call.getRawArguments()[0]) requestedPlcs.add(op.plcId());
            return null;
        }).when(plc).transact(any(Sonic2PlcService.Operation[].class));
        var module = mock(GameModule.class);
        when(module.getGameService(Sonic2PlcService.class)).thenReturn(plc);
        when(module.getObjectArtProvider()).thenReturn(art);
        var level = mock(LevelManager.class);
        when(level.getObjectRenderManager()).thenReturn(mock(ObjectRenderManager.class));
        var services = new TestObjectServices().withCamera(camera).withGameState(gameState)
                .withGameModule(module).withLevelManager(level)
                .withSolidExecutionRegistry(new com.openggf.game.solid.DefaultSolidExecutionRegistry())
                .withDebugOverlay(mock(DebugOverlayManager.class));
        var table = GameServices.module().createTouchResponseTable(RomByteReader.fromRom(TestEnvironment.currentRom()));
        var objects = new ObjectManager(List.of(), null, 0, null, table, null, camera, services);
        services.withDirectObjectManager(objects);
        when(level.getObjectManager()).thenReturn(objects);
        var boss = objects.createDynamicObject(() -> new Sonic2ARZBossInstance(
                new ObjectSpawn(0x2AE0, 0x430, 0x89, 0, 0, false, 0)));
        boss.update(0, p);
        int x = goingLeft ? (endpoint ? 0x2AB1 : 0x2AC0) : (endpoint ? 0x2B10 : 0x2B00);
        set(boss, "bossXPos", x << 16); set(boss, "bossYPos", 0x430 << 16);
        set(boss, "bossXVel", goingLeft ? -0xC8 : 0xC8); set(boss, "bossYVel", 0);
        set(boss, "targetFlag", goingLeft);
        boss.getState().routine = 2; boss.getState().hitCount = 1;
        boss.getState().x = x; boss.getState().y = 0x430;
        p.setCentreX((short) x); p.setCentreY((short) 0x430);
        p.setRolling(true); p.setAnimationId(2); p.setAir(true); p.setYSpeed((short) 0x200);
        GameServices.sprites().addSprite(p);
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var rewind = new RewindRegistry();
        rewind.register(GameServices.sprites().rewindSnapshottable());
        rewind.register(objects.rewindSnapshottable());
        rewind.register(gameState);
        rewind.register(services.rng());
        rewind.register(camera);
        var beforeTouch = rewind.capture();
        int escapeFrame = -1;
        CompositeSnapshot escaped = null;
        for (int replay = 0; replay < 2; replay++) {
            if (replay == 1) {
                rewind.restore(beforeTouch);
                boss = objects.getActiveObjects().stream().filter(Sonic2ARZBossInstance.class::isInstance)
                        .map(Sonic2ARZBossInstance.class::cast).findFirst().orElseThrow();
                requestedPlcs.clear(); camera.setMaxX((short) 0x2A00);
            }
            objects.snapshotTouchResponseState(); objects.runTouchResponsesForPlayer(p, 1);
            assertEquals(0, boss.getState().hitCount);
            assertFalse(boss.getState().defeated);
            objects.update(camera.getX(), p, List.of(), 1, false);
            assertEquals(endpoint ? 0xA : 8, boss.getState().routine);
            assertEquals(boss.getState().x, readInt(boss, "sub3X"),
                    "the killing hit does not itself set the native hammer-drop flag");
            assertEquals(List.of(Sonic2Constants.PLC_CAPSULE), requestedPlcs);
            assertEquals(1000, gameState.getScore());
            int frame = 1;
            var spawnedExplosions = new HashSet<BossExplosionObjectInstance>();
            while (boss.getState().routine != 0xC && frame < 240) {
                objects.update(camera.getX(), p, List.of(), ++frame, false);
                objects.getActiveObjects().stream().filter(BossExplosionObjectInstance.class::isInstance)
                        .map(BossExplosionObjectInstance.class::cast).forEach(spawnedExplosions::add);
            }
            assertEquals(endpoint ? 2 : 231, frame,
                    "endpoint skips 180 explosion updates and the 50-pass settle/ascend");
            assertEquals(0xC, boss.getState().routine);
            assertEquals(endpoint ? List.of(Sonic2Constants.PLC_CAPSULE)
                    : List.of(Sonic2Constants.PLC_CAPSULE, Sonic2Constants.PLC_ANIMALS_ARZ,
                            Sonic2Constants.PLC_EXPLOSION), requestedPlcs,
                    "SubA only publishes animal/explosion art at countdown == $18");
            long explosions = objects.getActiveObjects().stream().filter(BossExplosionObjectInstance.class::isInstance).count();
            assertEquals(endpoint ? 0 : 22, spawnedExplosions.size());
            assertEquals(endpoint ? 0 : 1, explosions,
                    "ordinary graph dispatch advances and retires earlier explosions");
            assertEquals(endpoint ? 0xB4 : 0x20, readInt(boss, "bossCountdown"));
            if (endpoint) assertEquals(boss.getState().x, readInt(boss, "sub3X"),
                    "skipping Sub8 keeps the hammer attached through escape");
            assertEquals(0x2A00, camera.getMaxX(), "routine C starts next dispatch");
            var escapeStart = rewind.capture();
            int escapeStartFrame = frame;
            objects.update(camera.getX(), p, List.of(), ++frame, false);
            assertEquals(0x2A02, camera.getMaxX());
            assertEquals(0x400, readInt(boss, "bossXVel"));
            assertEquals(-0x40, readInt(boss, "bossYVel"));
            while (!boss.isDestroyed() && frame < 600) objects.update(camera.getX(), p, List.of(), ++frame, false);
            assertTrue(boss.isDestroyed(), "escape must retire the actual boss");
            assertEquals(0x2C00, camera.getMaxX(), "capsule approach must become reachable");
            assertEquals(1000, gameState.getScore(), "no repeated defeat award");
            var afterEscape = rewind.capture();
            int afterEscapeFrame = frame;
            rewind.restore(escapeStart);
            var restoredEscapeStart = rewind.capture();
            for (var entry : escapeStart.entries().entrySet())
                assertEquals(List.of(), RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(),
                        restoredEscapeStart.get(entry.getKey())), entry.getKey());
            boss = objects.getActiveObjects().stream().filter(Sonic2ARZBossInstance.class::isInstance)
                    .map(Sonic2ARZBossInstance.class::cast).findFirst().orElseThrow();
            assertEquals(!endpoint, readBoolean(boss, "explosionPhaseEntered"),
                    "the native hammer-drop flag must restore in both reached defeat paths");
            frame = escapeStartFrame;
            while (!boss.isDestroyed() && frame < 600) objects.update(camera.getX(), p, List.of(), ++frame, false);
            assertEquals(afterEscapeFrame, frame);
            var replayedEscape = rewind.capture();
            for (var entry : afterEscape.entries().entrySet())
                assertEquals(List.of(), RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(),
                        replayedEscape.get(entry.getKey())), entry.getKey());
            if (replay == 0) { escapeFrame = frame; escaped = rewind.capture(); }
            else {
                assertEquals(escapeFrame, frame);
                var actual = rewind.capture();
                for (var entry : escaped.entries().entrySet())
                    assertEquals(List.of(), RewindSnapshotDiff.diffKey(entry.getKey(), entry.getValue(),
                            actual.get(entry.getKey())), entry.getKey());
            }
        }
        assertCapsuleContinuation(objects, level, camera, gameState);
    }

    /** Separate declared capsule approach, retaining the boss's actual unlocked camera. */
    private static void assertCapsuleContinuation(ObjectManager objects, LevelManager level,
                                                  Camera camera, GameStateManager gameState) throws Exception {
        assertEquals(0x2C00, camera.getMaxX());
        var p = new Sonic("sonic", (short) 0, (short) 0);
        GameServices.sprites().removeSprite("sonic");
        GameServices.sprites().addSprite(p);
        camera.setFocusedSprite(p);
        camera.setX((short) 0x2B00); camera.setY((short) 0x400);
        camera.setMaxY((short) 0x1000);
        when(level.getCurrentLevel()).thenReturn(mock(Level.class));
        byte[] heights = new byte[16]; Arrays.fill(heights, (byte) 16);
        var tile = new SolidTile(1, heights, heights.clone(), (byte) 0);
        var solid = new ChunkDesc(0xF001);
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean()))
                .thenAnswer(c -> (int) c.getArgument(2) >= 0x510 ? solid : null);
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt()))
                .thenAnswer(c -> (int) c.getArgument(2) >= 0x510 ? solid : null);
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt())).thenReturn(tile);
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt(), anyBoolean())).thenReturn(tile);
        GroundSensor.setLevelManager(level);
        var terrain = new TerrainCollisionManager();
        var collision = new CollisionSystem(terrain);
        collision.setObjectManager(objects);
        var mode = TestEnvironment.activeGameplayMode();
        mode.attachLevelManagers(mode.getWaterSystem(), mode.getParallaxManager(), terrain, collision,
                mode.getSpriteManager(), level);
        var movement = new PlayableSpriteMovement(p, collision, gameState);
        p.setCentreX((short) 0x2BE0); p.setCentreY((short) 0x470);
        p.setAir(true); p.setYSpeed((short) 0x100);
        var capsule = objects.createDynamicObject(() -> new EggPrisonObjectInstance(
                new ObjectSpawn(0x2BE0, 0x4F8, 0x3E, 0, 0, false, 0), "EggPrison"));
        boolean sawAnimals = false;
        for (int frame = 600; frame < 1500; frame++) {
            movement.handleMovement(false, false, false, false, false, false, false, false);
            objects.update(camera.getX(), p, List.of(), frame, false, true, true);
            sawAnimals |= objects.getActiveObjects().stream().anyMatch(EggPrisonAnimalInstance.class::isInstance);
            if (readBoolean(capsule, "resultsTriggered")) {
                assertTrue(readBoolean(capsule, "buttonTriggered"), "real solid landing must press the button");
                assertTrue(sawAnimals, "capsule must produce real animals before results");
                assertTrue(objects.getActiveObjects().stream().anyMatch(ResultsScreenObjectInstance.class::isInstance));
                assertTrue(objects.getActiveObjects().stream().noneMatch(
                        com.openggf.game.sonic2.objects.bosses.ARZBossPillar.class::isInstance),
                        "the real pillar children must retire after the boss defeat routine");
                return;
            }
        }
        fail("capsule continuation did not reach results");
    }

    private static boolean readBoolean(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static int readInt(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
