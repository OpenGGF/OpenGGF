package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.game.sonic3k.difficulty.AizBombingRunRecipe;
import com.openggf.game.sonic3k.difficulty.AizEndBossRecipe;
import com.openggf.game.sonic3k.difficulty.AizMinibossAttackPolicy;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TestAizNativeDifficultyPresets {
    @BeforeEach void headless() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() { GraphicsManager.getInstance().resetState(); }

    @Test
    void alternatingArmsPresentTwentyFourWarningsAndKeepNativeCharacterShotCounts() throws Exception {
        for (String character : List.of("sonic", "tails", "knuckles")) {
            var h = new Harness(character);
            h.endRecipe = AizEndBossRecipe.ALTERNATING_ARMS;
            var boss = h.bossWithArms();
            assertEquals(8, boss.getState().hitCount);
            invoke(boss, "onHoverComplete");
            assertTrue(boss.usesAlternatingArmPattern());
            assertEquals(0, boss.leadingArm());
            assertEquals(0x2F, field(boss, "waitTimer"));
            var props = h.live(AizEndBossPropellerChild.class);
            int[] warningPresentations = new int[2];
            var shots = new ArrayList<String>();
            int previousFlames = 0;
            for (int frame = 0; frame <= 150; frame++) {
                for (var propeller : props) {
                    propeller.update(frame + 1, null);
                    if ((int) field(propeller, "routine") == 10) {
                        warningPresentations[(int) field(propeller, "subtype")]++;
                        propeller.appendRenderCommands(new ArrayList<>());
                    }
                }
                var flames = h.live(AizEndBossFlameChild.class);
                while (previousFlames < flames.size()) {
                    var flame = flames.get(previousFlames++);
                    var propeller = field(flame, "propeller");
                    shots.add(frame + ":" + field(propeller, "subtype"));
                }
            }
            assertArrayEquals(new int[] {24, 24}, warningPresentations, character);
            verify(h.renderer, times(48)).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
            assertEquals(character.equals("knuckles")
                    ? List.of("31:0", "79:1", "80:0", "128:1")
                    : List.of("31:0", "79:1"), shots, character);
            invoke(boss, "onHoverComplete");
            assertEquals(1, boss.leadingArm(), "the next native fire commitment alternates its lead");
            invoke(boss, "onFireTimerExpired");
            assertEquals(0x8F, field(boss, "waitTimer"));
            invoke(boss, "beginRetreat");
            assertEquals(0x3F, field(boss, "waitTimer"));
        }
    }

    @Test
    void waterfallWarningGraphRecreatesAndReplaysTheSameCommittedShots() throws Exception {
        var h = new Harness("knuckles");
        h.endRecipe = AizEndBossRecipe.ALTERNATING_ARMS;
        var boss = h.bossWithArms();
        setField(boss, "flags38", 8); // native emerge/reveal latch
        invoke(boss, "onHoverComplete");
        // Isolate the committed child window from the later arena callback;
        // execute every actor through ObjectManager, including same-pass shots.
        boss.getState().routine = 8;
        setField(boss, "waitTimer", 1000);
        setField(boss, "bossArtLoaded", true);
        for (int tick = 1; tick <= 10; tick++) h.objects.update(0, null, List.of(), tick, false);
        RewindRegistry registry = new RewindRegistry();
        registry.register(h.objects.rewindSnapshottable());
        var snapshot = registry.capture();
        var expected = new ArrayList<ReplayWitness>();
        for (int tick = 11; tick <= 60; tick++) {
            h.objects.update(0, null, List.of(), tick, false);
            expected.add(h.replayWitness());
        }
        assertEquals(1, h.live(AizEndBossFlameChild.class).size(), "the managed pass commits its first shot");
        List.copyOf(h.objects.getActiveObjects()).forEach(h.objects::removeDynamicObject);
        registry.restore(snapshot);
        assertNotSame(boss, h.live(AizEndBossInstance.class).getFirst());
        for (int tick = 11; tick <= 60; tick++) {
            h.objects.update(0, null, List.of(), tick, false);
            assertEquals(expected.get(tick - 11), h.replayWitness(), "managed replay update " + tick);
        }
    }

    @Test
    void waterfallPresetNeedsReadyArtAndBothAllocatedPropellers() throws Exception {
        var h = new Harness("sonic");
        h.endRecipe = AizEndBossRecipe.ALTERNATING_ARMS;
        var boss = h.bossWithArms();
        when(h.renderer.isReady()).thenReturn(false);
        invoke(boss, "onHoverComplete");
        assertFalse(boss.usesAlternatingArmPattern());
        when(h.renderer.isReady()).thenReturn(true);
        var missing = h.live(AizEndBossPropellerChild.class).getFirst();
        h.objects.removeDynamicObject(missing);
        invoke(boss, "onHoverComplete");
        assertFalse(boss.usesAlternatingArmPattern());
    }

    @Test
    void waterfallWithdrawalAndDefeatRetireCommittedFlameBombAndSmoke() throws Exception {
        for (boolean defeated : List.of(false, true)) {
            var h = new Harness("knuckles");
            h.endRecipe = AizEndBossRecipe.ALTERNATING_ARMS;
            var boss = h.bossWithArms();
            invoke(boss, "onHoverComplete");
            var propeller = h.live(AizEndBossPropellerChild.class).getFirst();
            var flame = h.objects.createDynamicObject(() -> new AizEndBossFlameChild(boss, propeller, 4, true));
            var bomb = h.objects.createDynamicObject(() -> new AizEndBossBombChild(boss, 0x180, 0x180, 4, true));
            var smoke = h.objects.createDynamicObject(() -> new AizEndBossSmokeChild(boss, 0x180, 0x180, false, true));
            if (defeated) boss.getState().defeated = true;
            else h.endRecipe = null;
            assertEquals(0, flame.getCollisionFlags());
            assertEquals(0, bomb.getCollisionFlags());
            flame.update(1, null); bomb.update(1, null); smoke.update(1, null);
            assertTrue(flame.isDestroyed()); assertTrue(bomb.isDestroyed()); assertTrue(smoke.isDestroyed());
        }
    }

    @Test
    void nativeOwnerRemovalRetiresOptionalGraphsEvenWhileTheServicesRemainAvailable() throws Exception {
        var h = new Harness("sonic");
        h.endRecipe = AizEndBossRecipe.ALTERNATING_ARMS;
        h.bombRecipe = AizBombingRunRecipe.STAGGERED_PORTS;
        h.miniPolicy = hits -> 24;
        var boss = h.bossWithArms();
        invoke(boss, "onHoverComplete");
        var flame = h.objects.createDynamicObject(() -> new AizEndBossFlameChild(
                boss, h.live(AizEndBossPropellerChild.class).getFirst(), 4, true));
        h.objects.removeDynamicObject(boss);
        assertEquals(0, flame.getCollisionFlags());
        flame.update(1, null);
        assertTrue(flame.isDestroyed());
        var ship = h.ship();
        var bomb = h.objects.createDynamicObject(() -> new AizShipBombInstance(
                new ObjectSpawn(0x180, 0x180, 0, 0, 0, false, 0), ship, 0x3F64, 0x180, true));
        var fragment = h.objects.createDynamicObject(() -> new AizBombExplosionInstance(0x180, 0x180, 0, 0, ship));
        fragment.update(1, null);
        h.objects.removeDynamicObject(ship);
        assertEquals(0, fragment.getCollisionFlags());
        bomb.update(2, null); fragment.update(2, null);
        assertTrue(bomb.isDestroyed()); assertTrue(fragment.isDestroyed());
        var mini = h.objects.createDynamicObject(() -> new AizMinibossInstance(
                new ObjectSpawn(0x180, 0x180, 0x91, 0, 0, false, 0)));
        mini.getState().hitCount = 3;
        var miniFlame = h.objects.createDynamicObject(() -> new AizMinibossFlameChild(mini, 0, 0, 6, true));
        h.objects.removeDynamicObject(mini);
        miniFlame.update(1, null);
        assertEquals(0, miniFlame.getCollisionFlags());
        assertTrue(miniFlame.isDestroyed());
    }

    @Test
    void staggeredBombingKeepsTwentyOneDropsOpeningWaitRecoveryRowsAndNativeExit() throws Exception {
        int expectedExit = -1;
        for (boolean opted : List.of(false, true)) {
            var h = new Harness("sonic");
            h.bombRecipe = opted ? AizBombingRunRecipe.STAGGERED_PORTS : null;
            var ship = h.ship();
            var releaseFrames = new ArrayList<Integer>();
            var delays = new ArrayList<Integer>();
            int previous = 0;
            int exit = 0;
            for (int frame = 0; frame < 2000; frame++) {
                ship.update(frame, null);
                int count = (int) field(ship, "scriptIndex");
                if (count > previous) {
                    releaseFrames.add(frame);
                    delays.add((int) field(ship, "delayTimer"));
                    previous = count;
                }
                if (ship.isDestroyed()) { exit = frame; break; }
            }
            assertEquals(21, releaseFrames.size());
            assertEquals(421, releaseFrames.getFirst());
            assertEquals(opted ? 25 : 33, releaseFrames.get(1) - releaseFrames.getFirst());
            assertEquals(List.of(56, 56, 56), List.of(delays.get(5), delays.get(11), delays.get(17)));
            assertEquals(List.of(64, 64, 64), delays.subList(18, 21));
            if (!opted) expectedExit = exit;
            else assertEquals(expectedExit, exit, "the ship's native fixed-point exit is unchanged");
            var bombs = h.live(AizShipBombInstance.class);
            assertEquals(opted ? 0x3F64 : 0x3F5C, bombs.getFirst().sourceSecondaryXForTest());
        }
    }

    @Test
    void knucklesAndUnreadyArtDoNotConsumeTheBombingPreset() throws Exception {
        for (String character : List.of("sonic", "knuckles")) {
            var h = new Harness(character);
            h.bombRecipe = AizBombingRunRecipe.STAGGERED_PORTS;
            if (character.equals("sonic")) when(h.renderer.isReady()).thenReturn(false);
            var ship = h.ship();
            for (int frame = 0; frame <= 421; frame++) ship.update(frame, null);
            assertEquals(32, field(ship, "delayTimer"));
            assertEquals(0x3F5C, h.live(AizShipBombInstance.class).getFirst().sourceSecondaryXForTest());
        }
    }

    @Test
    void committedPortAndHarmfulFragmentsRestoreAndRetireWithTheirExactShip() throws Exception {
        var h = new Harness("sonic");
        h.bombRecipe = AizBombingRunRecipe.STAGGERED_PORTS;
        var ship = h.ship();
        for (int frame = 0; frame <= 421; frame++) ship.update(frame, null);
        var bomb = h.live(AizShipBombInstance.class).getFirst();
        int committedPort = bomb.sourceSecondaryXForTest();
        var fragment = h.objects.createDynamicObject(() -> new AizBombExplosionInstance(0x180, 0x180, 0, 0, ship));
        fragment.update(422, null);
        assertNotEquals(0, fragment.getCollisionFlags());
        RewindRegistry registry = new RewindRegistry();
        registry.register(h.objects.rewindSnapshottable());
        var snapshot = registry.capture();
        bomb.update(422, null);
        List.copyOf(h.objects.getActiveObjects()).forEach(h.objects::removeDynamicObject);
        registry.restore(snapshot);
        var restoredShip = h.live(AizBattleshipInstance.class).getFirst();
        var restoredBomb = h.live(AizShipBombInstance.class).getFirst();
        var restoredFragment = h.live(AizBombExplosionInstance.class).getFirst();
        assertSame(restoredShip, restoredBomb.sourceShipForTest());
        assertSame(restoredShip, field(restoredFragment, "difficultyOwner"));
        assertEquals(committedPort, restoredBomb.sourceSecondaryXForTest());
        int before = restoredFragment.getX();
        h.camera.setX((short) (h.camera.getX() - 0x200));
        restoredFragment.applyWrapOffset(0x200);
        assertEquals(before - 0x200, restoredFragment.getX());
        h.bombRecipe = null;
        assertEquals(0, restoredFragment.getCollisionFlags());
        restoredBomb.update(423, null); restoredFragment.update(423, null);
        assertTrue(restoredBomb.isDestroyed()); assertTrue(restoredFragment.isDestroyed());
    }

    @Test
    void failedMinibossWarningDoesNotBecomeAnUnwarnedWaveWhenSlotsReturn() throws Exception {
        var h = new Harness("sonic");
        h.miniPolicy = hits -> 24;
        var boss = h.objects.createDynamicObject(() -> new AizMinibossInstance(
                new ObjectSpawn(0x180, 0x180, 0x91, 0, 0, false, 0)));
        boss.getState().hitCount = 3;
        invoke(boss, "onInitialDelayComplete");
        var fillers = new ArrayList<AbstractObjectInstance>();
        while (h.objects.hasFreeDynamicSlot()) {
            fillers.add(h.objects.createDynamicObject(() -> new Filler()));
        }
        invoke(boss, "prepareDifficultyFollowup");
        fillers.forEach(h.objects::removeDynamicObject);
        for (int frame = 0; frame < 30; frame++) invoke(boss, "updateDifficultyFollowup");
        assertTrue(h.live(AizMinibossFlameChild.class).isEmpty());
        assertEquals(-1, field(boss, "difficultyFollowupTimer"));
    }

    private static final class Filler extends AbstractObjectInstance {
        Filler() { super(new ObjectSpawn(0x100, 0x100, 0x7F, 0, 0, false, 0), "Filler"); }
        @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
    }

    private record ReplayWitness(List<DynamicObjectEntry> states, List<List<Integer>> actualPositions) { }

    private static final class Harness {
        AizEndBossRecipe endRecipe;
        AizBombingRunRecipe bombRecipe;
        AizMinibossAttackPolicy miniPolicy;
        final PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        final Camera camera;
        final ObjectManager objects;
        Harness(String character) {
            var config = SonicConfigurationService.createStandalone();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
            camera = new Camera(config);
            when(renderer.isReady()).thenReturn(true);
            var renders = mock(ObjectRenderManager.class);
            when(renders.getRenderer(anyString())).thenReturn(renderer);
            var module = mock(GameModule.class);
            when(module.getGameService(AizEndBossRecipe.class)).thenAnswer(call -> endRecipe);
            when(module.getGameService(AizBombingRunRecipe.class)).thenAnswer(call -> bombRecipe);
            when(module.getGameService(AizMinibossAttackPolicy.class)).thenAnswer(call -> miniPolicy);
            ObjectManager[] holder = new ObjectManager[1];
            var services = new StubObjectServices() {
                @Override public Camera camera() { return camera; }
                @Override public SonicConfigurationService configuration() { return config; }
                @Override public GameModule gameModule() { return module; }
                @Override public ObjectRenderManager renderManager() { return renders; }
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public GameStateManager gameState() { return new GameStateManager(); }
            };
            objects = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            holder[0] = objects;
        }
        AizEndBossInstance bossWithArms() throws Exception {
            var boss = objects.createDynamicObject(() -> new AizEndBossInstance(
                    new ObjectSpawn(0x180, 0x180, 0x92, 0, 0, false, 0)));
            invoke(boss, "spawnArmChildren");
            boss.getState().lastUpdatedVIntRunCount = 0;
            for (var arm : live(AizEndBossArmChild.class)) arm.update(0, null);
            for (var propeller : live(AizEndBossPropellerChild.class)) propeller.update(0, null);
            return boss;
        }
        AizBattleshipInstance ship() {
            return objects.createDynamicObject(() -> new AizBattleshipInstance(
                    new ObjectSpawn(0x100, 0x8F0, 0, 0, 0, false, 0), 0x8F0));
        }
        ReplayWitness replayWitness() {
            var states = objects.rewindSnapshottable().capture().dynamicObjects().stream().map(entry -> {
                if (!entry.className().equals(AizEndBossArmChild.class.getName())
                        && !entry.className().equals(AizEndBossPropellerChild.class.getName())) return entry;
                // These stock children keep a lazy construction descriptor;
                // recreate refreshes only its x/y. Preserve every other field,
                // exact identity and full native state, and compare actual x/y below.
                var spawn = entry.spawn();
                var seed = new ObjectSpawn(0, 0, spawn.objectId(), spawn.subtype(), spawn.renderFlags(),
                        spawn.respawnTracked(), spawn.rawYWord(), spawn.layoutIndex(), spawn.ownerModId(), spawn.objectKey());
                return new DynamicObjectEntry(entry.className(), seed, entry.slotIndex(), entry.state(),
                        entry.playerOwner(), entry.objectId(), entry.ownerModId(), entry.rewindableAuxiliary());
            }).toList();
            var positions = objects.getActiveObjects().stream().filter(object -> !object.isDestroyed())
                    .map(object -> List.of(((AbstractObjectInstance) object).getSlotIndex(), object.getX(), object.getY())).toList();
            return new ReplayWitness(states, positions);
        }
        <T> List<T> live(Class<T> type) {
            return objects.getActiveObjects().stream().filter(type::isInstance)
                    .filter(object -> !object.isDestroyed()).map(type::cast).toList();
        }
    }

    private static void invoke(Object target, String method) throws Exception {
        Method call = target.getClass().getDeclaredMethod(method);
        call.setAccessible(true); call.invoke(target);
    }
    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }
    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
