package com.openggf.game.sonic2;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.events.*;
import com.openggf.game.sonic2.kis2.Kis2Rules;
import com.openggf.game.sonic2.objects.*;
import com.openggf.game.sonic2.objects.bosses.Sonic2EHZBossInstance;
import com.openggf.level.*;
import com.openggf.level.objects.*;
import com.openggf.physics.*;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.playable.*;
import com.openggf.tests.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native camera/event seams, with authored terrain rather than a published route replay. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestTasS2VerticalCamera {
    private static class LockOnKnuckles extends Knuckles {
        LockOnKnuckles() { super("knuckles", (short)0, (short)0); setGameRulesForTest(Kis2Rules.RULES); applyExternalPhysicsProfile(com.openggf.game.sonic2.kis2.Kis2Physics.KNUCKLES); }
    }
    private record World(AbstractPlayableSprite player, PlayableSpriteMovement movement, ObjectManager objects) { }
    private static World world(boolean lockOn, int floor) {
        TestEnvironment.resetAll(); TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        var level = mock(LevelManager.class); when(level.getCurrentLevel()).thenReturn(mock(Level.class));
        byte[] full = new byte[16]; Arrays.fill(full, (byte)16);
        var tile = new SolidTile(1, full, full.clone(), (byte)0); var solid = new ChunkDesc(0xF001);
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt(), anyBoolean())).thenAnswer(c -> (int)c.getArgument(2) >= floor ? solid : null);
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt())).thenReturn(tile);
        when(level.getSolidTileForChunkDesc(any(ChunkDesc.class), anyInt(), anyBoolean())).thenReturn(tile);
        when(level.getChunkDescAt(anyByte(), anyInt(), anyInt())).thenAnswer(c -> (int)c.getArgument(2) >= floor ? solid : null);
        GroundSensor.setLevelManager(level);
        var camera = GameServices.camera();
        camera.setMinX((short)0); camera.setMaxX((short)0x4000);
        camera.setMinY((short)0); camera.setMaxY((short)0x1000);
        var services = new TestObjectServices().withCamera(camera).withGameModule(GameServices.module())
                .withSolidExecutionRegistry(new com.openggf.game.solid.DefaultSolidExecutionRegistry())
                .withRng(GameServices.rng()).withAudioManager(GameServices.audio());
        var objects = new ObjectManager(List.of(), GameServices.module().createObjectRegistry(), 0, null, null, null, camera, services);
        services.withDirectObjectManager(objects); when(level.getObjectManager()).thenReturn(objects);
        var terrain = new TerrainCollisionManager(); var collision = new CollisionSystem(terrain); collision.setObjectManager(objects);
        var mode = TestEnvironment.activeGameplayMode();
        mode.attachLevelManagers(mode.getWaterSystem(), mode.getParallaxManager(), terrain, collision, mode.getSpriteManager(), level);
        assertEquals(-1, ObjectTerrainUtils.checkFloorDist(0x2990, floor - 12, 12).distance(),
                "the object floor scan must see the same real terrain as the player sensors");
        AbstractPlayableSprite player = lockOn ? new LockOnKnuckles() : new Sonic("sonic", (short)0, (short)0);
        GameServices.sprites().addSprite(player); camera.setFocusedSprite(player);
        return new World(player, new PlayableSpriteMovement(player, collision, GameServices.gameState()), objects);
    }
    @Test void actualJumpCatchesCameraAtSixteenInsteadOfSixAndReachesWfzEndingGateEarlier() {
        try (var reel = com.openggf.tests.reel.S2ComponentTestReel.open(0)) {
            assertEquals(4, endingGate(true));
            assertEquals(11, endingGate(false));
        }
    }
    private static int endingGate(boolean jump) {
        var w = world(false, 0x620); var p = w.player(); var camera = GameServices.camera();
        p.setCentreX((short)0x2A00); p.setCentreY((short)(0x620 - p.getYRadius() - 1));
        p.setAir(true); p.setYSpeed((short)0x100);
        w.movement().handleMovement(false,false,false,false,false,false,false,false);
        assertFalse(p.getAir(), "must actually sense floor before the optional jump");
        camera.setX((short)0x2960); camera.setY((short)0x4C0);
        var events = new Sonic2WFZEvents(); events.init(0);
        events.update(0, 0);
        assertEquals(2, events.getWfzSubRoutine(), "actual approach must admit the WFZ boss PLC gate first");
        assertFalse(p.isControlLocked());
        int frame = 0;
        while (!p.isControlLocked() && frame < 20) {
            p.setJumpInputPressed(jump && frame == 0);
            w.movement().handleMovement(false,false,false,false,jump,false,false,false);
            assertEquals(jump, p.getAir());
            int before = camera.getY(); camera.updatePosition();
            assertEquals(jump ? 16 : 6, camera.getY() - before);
            if (camera.getY() < 0x500) assertEquals(2, events.getWfzSubRoutine());
            events.update(0, ++frame);
        }
        assertTrue(p.isControlLocked(), "actual $500 ending control-lock gate must execute");
        assertEquals(4, events.getWfzSubRoutine());
        assertEquals(Sonic2Constants.PLC_TORNADO, events.getLastRequestedPlcIdForTest());
        assertEquals(2, events.getPlcRequestCountForTest());
        return frame;
    }
    @Test void highBossEntryLeavesVerticalLimitOpenForLookUpToCullCapsuleAnimals() throws Exception {
        try (var reel = com.openggf.tests.reel.S2ComponentTestReel.open(0)) {
            var high = capsuleAfterBoss(true);
            var ordinary = capsuleAfterBoss(false);
            assertTrue(high.resultsFrame() < ordinary.resultsFrame(), "look-up culling must release the actual capsule results earlier");
            assertTrue(high.lowestCamera() < 0x388);
            assertEquals(0x388, ordinary.lowestCamera());
            assertTrue(high.verticalCulls() > 0, "animals must actually disappear below the raised viewport");
            assertEquals(0, ordinary.verticalCulls(), "the retained boss limit prevents those bottom culls");
            System.out.println("S2 look-up capsule: high=" + high + ", ordinary=" + ordinary);
        }
    }
    private record CapsuleExit(int resultsFrame, int lowestCamera, int verticalCulls) { }
    private static CapsuleExit capsuleAfterBoss(boolean high) throws Exception {
        var w = world(true, 0x420); var p = w.player(); var camera = GameServices.camera();
        p.setCentreX((short)0x2990);
        // Valid entry after a high-platform jump. The jump/route is outside this
        // scenario; gravity, camera, the entire spawn window and its exit execute.
        p.setCentreY((short)(high ? 0x180 : 0x420 - p.getYRadius() - 1));
        p.setAir(high); p.setYSpeed((short)(high ? -0x600 : 0));
        camera.setX((short)0x28F0); camera.setY((short)(high ? 0x120 : 0x390));
        var events = new Sonic2EHZEvents(); events.init(1);
        events.update(1, 0);
        com.openggf.tests.reel.S2ComponentTestReel.cameraFrame(p, w.objects().getActiveObjects(), 0, 0, "boss-entry event frame0; collision-only terrain", camera.getX(), camera.getY());
        events.update(1, 1);
        com.openggf.tests.reel.S2ComponentTestReel.cameraFrame(p, w.objects().getActiveObjects(), 0, 0, "boss-entry event frame1; collision-only terrain", camera.getX(), camera.getY());
        assertEquals(4, events.getEventRoutine());
        for (int frame = 2; frame < 92; frame++) {
            w.movement().handleMovement(false,false,false,false,false,false,false,false);
            camera.updatePosition(); events.update(1, frame);
            com.openggf.tests.reel.S2ComponentTestReel.cameraFrame(p, w.objects().getActiveObjects(), 0, 0, "boss-entry camera; collision-only terrain", camera.getX(), camera.getY());
        }
        assertEquals(6, events.getEventRoutine());
        assertEquals(90, events.getBossSpawnDelay());
        assertTrue(w.objects().getActiveObjects().stream().anyMatch(Sonic2EHZBossInstance.class::isInstance));
        assertEquals(high ? 0 : 0x388, camera.getMinY(), "only the real pre-spawn camera condition may establish the limit");
        // Separate valid post-defeat capsule entry, preserving the acquired camera
        // limit. The fight is outside this test; no camera/bias/cull result is seeded.
        for (var o : List.copyOf(w.objects().getActiveObjects())) w.objects().removeDynamicObject(o);
        var capsule = new EggPrisonObjectInstance(new ObjectSpawn(0x2990, 0x408, 0x3E, 0, 0, false, 0), "EggPrison");
        w.objects().addDynamicObject(capsule);
        p.setCentreX((short)0x2990); p.setCentreY((short)0x390);
        p.setAir(true); p.setYSpeed((short)0x100); p.setXSpeed((short)0); p.setGSpeed((short)0);
        boolean sawAnimals = false, pressed = false; int lowest = Integer.MAX_VALUE; int verticalCulls = 0;
        var triggered = EggPrisonObjectInstance.class.getDeclaredField("resultsTriggered"); triggered.setAccessible(true);
        var button = EggPrisonObjectInstance.class.getDeclaredField("buttonTriggered"); button.setAccessible(true);
        for (int frame = 92; frame < 900; frame++) {
            w.movement().handleMovement(true,false,false,false,false,false,false,false);
            var belowViewport = w.objects().getActiveObjects().stream()
                    .filter(EggPrisonAnimalInstance.class::isInstance)
                    .filter(o -> o.getX() >= camera.getX() && o.getX() < camera.getX() + camera.getWidth())
                    .filter(o -> o.getY() >= camera.getY() + camera.getHeight() + 32).toList();
            w.objects().update(camera.getX(), p, List.of(), frame, false, true, true);
            for (var animal : belowViewport) if (animal.isDestroyed()) verticalCulls++;
            camera.updatePosition(); events.update(1, frame);
            w.objects().refreshPostCameraRenderState();
            com.openggf.tests.reel.S2ComponentTestReel.cameraFrame(p, w.objects().getActiveObjects(), 1, 0, "capsule look-up; collision-only terrain", camera.getX(), camera.getY());
            pressed |= button.getBoolean(capsule);
            sawAnimals |= w.objects().getActiveObjects().stream().anyMatch(EggPrisonAnimalInstance.class::isInstance);
            lowest = Math.min(lowest, camera.getY());
            if (triggered.getBoolean(capsule)) {
                assertTrue(pressed, "real landing must press the capsule");
                assertTrue(sawAnimals, "actual capsule must produce animals before results");
                assertTrue(p.getLookingUp()); assertEquals(0xC8, camera.getYPosBias());
                assertTrue(w.objects().getActiveObjects().stream().anyMatch(ResultsScreenObjectInstance.class::isInstance));
                return new CapsuleExit(frame, lowest, verticalCulls);
            }
        }
        fail("capsule animals/results exit was not reached: high=" + high + " y=" + p.getCentreY() + " camera=" + camera.getY() + " pressed=" + pressed + " animals=" + sawAnimals);
        return null;
    }
}
