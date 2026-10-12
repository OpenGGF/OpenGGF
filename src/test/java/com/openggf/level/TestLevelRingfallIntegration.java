package com.openggf.level;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.game.rules.GameRules;
import com.openggf.game.mutators.GameplayMutatorPolicy;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.objects.*;
import com.openggf.level.rings.*;
import com.openggf.tests.MutatorPhysicsWorld;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Isolated
class TestLevelRingfallIntegration {
    @AfterEach void clear() { SessionManager.clear(); }

    @ParameterizedTest
    @CsvSource({"SONIC_1,100,0,32", "SONIC_2,100,0,32", "SONIC_3K,100,0,32",
            "SONIC_1,50,0,16", "SONIC_2,50,0,16", "SONIC_3K,50,0,16",
            "SONIC_1,10,2,2", "SONIC_2,10,2,2", "SONIC_3K,10,2,2"})
    void productionCoordinatorCreatesResolvedNativeRingObjectsAndLosesEntireInventory(
            String game, int percent, int cap, int expected) {
        ObjectSlotLayout layout = switch (game) {
            case "SONIC_1" -> ObjectSlotLayout.SONIC_1; case "SONIC_3K" -> ObjectSlotLayout.SONIC_3K; default -> ObjectSlotLayout.SONIC_2;
        };
        TestEnvironment.configureGameModuleFixture(switch (game) {
            case "SONIC_1" -> new Sonic1GameModule(); case "SONIC_3K" -> new Sonic3kGameModule(); default -> new Sonic2GameModule();
        });
        GameplayMutatorPolicy policy = new GameplayMutatorPolicy(percent, cap, 100, 0xC00, 100, false);
        var services = new StubObjectServices() {
            @Override public com.openggf.game.session.WorldSession worldSession() { return world; }
            private final com.openggf.game.session.WorldSession world = MutatorPhysicsWorld.create(() -> policy);
        };
        var registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(layout);
        ObjectManager objects = new ObjectManager(List.of(), registry, 0, null, null, null, null, services);
        LevelManager levels = mock(LevelManager.class);
        levels.objectManager = objects; when(levels.getObjectManager()).thenReturn(objects);
        var liveLevels = com.openggf.game.GameServices.level();
        // Native assembly creates this owner; this isolated producer fixture has no ROM load.
        liveLevels.resetLevelGamestate(com.openggf.game.GameServices.currentOrBootstrapGameModule().createLevelState());
        var nativeState = liveLevels.getLevelGamestate();
        when(levels.getLevelGamestate()).thenReturn(nativeState);
        RingFrame frame = new RingFrame(List.of(new RingFramePiece(0, 0, 1, 1, 0, false, false, 0)));
        RingSpriteSheet sheet = new RingSpriteSheet(new Pattern[] {new Pattern()}, List.of(frame, frame, frame), 1, 1, 1, 2);
        AudioManager audio = mock(AudioManager.class);
        RingManager rings = new RingManager(List.of(), sheet, levels, null, audio);
        levels.ringManager = rings;
        Player player = new Player(); player.setRingCount(100); player.setCentreX((short) 0x120); player.setCentreY((short) 0x90);
        assertEquals(100, player.getRingCount());
        player.rules(switch (game) { case "SONIC_1" -> GameRules.SONIC_1; case "SONIC_3K" -> GameRules.SONIC_3K; default -> GameRules.SONIC_2; });
        LevelLostRingSpawnCoordinator coordinator = new LevelLostRingSpawnCoordinator(levels);
        coordinator.spawnImmediately(player, 50);
        var firstSpill = objects.activeObjectsOfType(LostRingObjectInstance.class);
        assertEquals(expected, firstSpill.size());
        int[] firstSlots = firstSpill.stream().mapToInt(LostRingObjectInstance::getSlotIndex).toArray();
        assertEquals(0, player.getRingCount());
        verify(audio).playSfx(GameSound.RING_SPILL);
        // Native Obj37 objects keep their slots when a later spill resets the shared mirror.
        player.setRingCount(5); coordinator.spawnImmediately(player, 51);
        int second = percent == 100 ? Math.min(5, cap == 0 ? 32 : cap) : Math.min(Math.max(1, 5 * percent / 100), cap == 0 ? 32 : cap);
        var bothSpills = objects.activeObjectsOfType(LostRingObjectInstance.class);
        assertEquals(expected + second, bothSpills.size());
        assertTrue(bothSpills.containsAll(firstSpill));
        assertArrayEquals(firstSlots, firstSpill.stream().mapToInt(LostRingObjectInstance::getSlotIndex).toArray());
        assertEquals(expected + second, objects.getAllocatedSlotCount());
        assertEquals(0, player.getRingCount());
        verify(audio, times(2)).playSfx(GameSound.RING_SPILL);
    }

    @ParameterizedTest
    @CsvSource({"SONIC_1,100,0,150,150", "SONIC_2,100,0,150,150", "SONIC_3K,100,0,150,150",
            "SONIC_2,50,0,150,75", "SONIC_2,100,20,150,20", "SONIC_3K,100,0,20,20",
            "SONIC_1,100,0,999,999", "SONIC_2,100,0,999,999", "SONIC_3K,100,0,999,999"})
    void fullInventorySpillsRecoverableRingsBeyondTheNativeCeilingWithoutClaimingExtraSlots(
            String game, int percent, int cap, int held, int expected) {
        ObjectSlotLayout layout = switch (game) {
            case "SONIC_1" -> ObjectSlotLayout.SONIC_1; case "SONIC_3K" -> ObjectSlotLayout.SONIC_3K; default -> ObjectSlotLayout.SONIC_2;
        };
        TestEnvironment.configureGameModuleFixture(switch (game) {
            case "SONIC_1" -> new Sonic1GameModule(); case "SONIC_3K" -> new Sonic3kGameModule(); default -> new Sonic2GameModule();
        });
        GameplayMutatorPolicy policy = new GameplayMutatorPolicy(percent, cap, true, 100, 0xC00, 100, false);
        var services = new StubObjectServices() {
            @Override public com.openggf.game.session.WorldSession worldSession() { return world; }
            private final com.openggf.game.session.WorldSession world = MutatorPhysicsWorld.create(() -> policy);
        };
        var registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(layout);
        ObjectManager objects = new ObjectManager(List.of(), registry, 0, null, null, null, null, services);
        LevelManager levels = mock(LevelManager.class);
        levels.objectManager = objects; when(levels.getObjectManager()).thenReturn(objects);
        var liveLevels = com.openggf.game.GameServices.level();
        liveLevels.resetLevelGamestate(com.openggf.game.GameServices.currentOrBootstrapGameModule().createLevelState());
        when(levels.getLevelGamestate()).thenReturn(liveLevels.getLevelGamestate());
        RingFrame frame = new RingFrame(List.of(new RingFramePiece(0, 0, 1, 1, 0, false, false, 0)));
        RingSpriteSheet sheet = new RingSpriteSheet(new Pattern[] {new Pattern()}, List.of(frame, frame, frame), 1, 1, 1, 2);
        AudioManager audio = mock(AudioManager.class);
        RingManager rings = new RingManager(List.of(), sheet, levels, null, audio);
        levels.ringManager = rings;
        Player player = new Player(); player.setRingCount(held); player.setCentreX((short) 0x120); player.setCentreY((short) 0x90);
        player.rules(switch (game) { case "SONIC_1" -> GameRules.SONIC_1; case "SONIC_3K" -> GameRules.SONIC_3K; default -> GameRules.SONIC_2; });

        new LevelLostRingSpawnCoordinator(levels).spawnImmediately(player, 50);

        var spill = objects.activeObjectsOfType(LostRingObjectInstance.class);
        assertEquals(expected, spill.size());
        int nativePortion = Math.min(expected, 32);
        assertEquals(nativePortion, objects.getAllocatedSlotCount(), "extras never claim native SST slots");
        assertEquals(expected - nativePortion, spill.stream().filter(ring -> ring.getSlotIndex() < 0).count());
        assertTrue(spill.stream().allMatch(LostRingObjectInstance::isLostRingCollectible));
        assertEquals(nativePortion, rings.getActiveLostRings().size(), "the retired mirror stays native-sized");
        assertEquals(0, player.getRingCount());
        verify(audio).playSfx(GameSound.RING_SPILL);
        if (expected > 48) {
            // Later fan cycles rotate between earlier directions instead of stacking on them.
            var velocities = spill.stream().map(ring -> List.of(ring.getXVelForTest(), ring.getYVelForTest())).toList();
            for (int i = 48; i < velocities.size(); i++) assertNotEquals(velocities.get(i - 48), velocities.get(i));
        }
    }

    @org.junit.jupiter.api.Test
    void nativeCeilingStillAppliesWithoutFullInventory() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        GameplayMutatorPolicy policy = new GameplayMutatorPolicy(100, 0, false, 100, 0xC00, 100, false);
        var services = new StubObjectServices() {
            @Override public com.openggf.game.session.WorldSession worldSession() { return world; }
            private final com.openggf.game.session.WorldSession world = MutatorPhysicsWorld.create(() -> policy);
        };
        var registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_2);
        ObjectManager objects = new ObjectManager(List.of(), registry, 0, null, null, null, null, services);
        LevelManager levels = mock(LevelManager.class);
        levels.objectManager = objects; when(levels.getObjectManager()).thenReturn(objects);
        var liveLevels = com.openggf.game.GameServices.level();
        liveLevels.resetLevelGamestate(com.openggf.game.GameServices.currentOrBootstrapGameModule().createLevelState());
        when(levels.getLevelGamestate()).thenReturn(liveLevels.getLevelGamestate());
        RingFrame frame = new RingFrame(List.of(new RingFramePiece(0, 0, 1, 1, 0, false, false, 0)));
        RingSpriteSheet sheet = new RingSpriteSheet(new Pattern[] {new Pattern()}, List.of(frame, frame, frame), 1, 1, 1, 2);
        RingManager rings = new RingManager(List.of(), sheet, levels, null, mock(AudioManager.class));
        levels.ringManager = rings;
        Player player = new Player(); player.setRingCount(150); player.setCentreX((short) 0x120); player.setCentreY((short) 0x90);
        player.rules(GameRules.SONIC_2);
        new LevelLostRingSpawnCoordinator(levels).spawnImmediately(player, 50);
        assertEquals(32, objects.activeObjectsOfType(LostRingObjectInstance.class).size());
        assertEquals(0, player.getRingCount());
    }

    private static class Player extends TestablePlayableSprite {
        Player() { super("sonic", (short) 0, (short) 0); }
        void rules(GameRules rules) { setGameRulesForTest(rules); }
    }
}
