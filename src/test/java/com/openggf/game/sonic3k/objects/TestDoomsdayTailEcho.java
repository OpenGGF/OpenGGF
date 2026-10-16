package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.difficulty.DoomsdayMissileRecipe;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.mods.objects.DoomsdayFactory;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Controlled native graph evidence; no fuel, readable bait or complete Doomsday route is claimed. */
class TestDoomsdayTailEcho {
    @ParameterizedTest @EnumSource(DoomsdayMissileRecipe.class)
    void onlyThirdMissileChangesActualRideDispatchesAndAllThreeKeepTheirExhaust(DoomsdayMissileRecipe recipe)
            throws Exception {
        World world = new World(recipe);
        world.fire();
        assertEquals(3, world.missiles().size(), "loc_81FB0 still allocates exactly three indices");
        assertEquals(2, number(world.launcher, "count"));
        for (int index = 0; index < 3; index++) {
            var missile = world.missiles().get(index);
            assertEquals(index, number(missile, "index"));
            assertEquals(1, missile.getSpawn().subtype());
            assertSame(world.launcher, missile.parent);
            assertEquals(index == 2 ? recipe : DoomsdayMissileRecipe.STOCK, value(missile, "missileRecipe"));
            missile.update(1, world.player); // Obj_DDZMissile init is its own dispatch.
            int seed = index * 16 + (index == 2 && recipe == DoomsdayMissileRecipe.TAIL_ECHO ? 16 : 0);
            assertEquals(seed, number(missile, "timer"));
            assertEquals(DdzMissileObjectInstance.STATE_RIDE_LAUNCHER, number(missile, "state"));
            int rideDispatches = 0;
            while (number(missile, "state") == DdzMissileObjectInstance.STATE_RIDE_LAUNCHER
                    && rideDispatches < 64) {
                missile.update(2 + rideDispatches++, world.player);
            }
            assertEquals(seed + 1, rideDispatches, "loc_82060 releases only after signed --timer < 0");
            assertEquals(DdzMissileObjectInstance.STATE_DRIFT, number(missile, "state"));
            assertEquals(0xB, number(missile, "timer"));
            assertEquals((short) 0x200, value(missile, "xVel"));
            assertEquals(0x40, number(missile, "angle"));
        }
        var exhaust = world.manager.activeObjectsOfType(DdzMissileExhaustObjectInstance.class);
        assertEquals(3, exhaust.size());
        assertEquals(3, exhaust.stream().map(flame -> flame.parent).distinct().count());
        assertEquals(2, number(world.launcher, "count"), "release does not spend a missile");
    }

    @Test void postReleaseDriftStraightTurnAndHomingAreByteEquivalentToStock() throws Exception {
        World stock = new World(DoomsdayMissileRecipe.STOCK);
        World echo = new World(DoomsdayMissileRecipe.TAIL_ECHO);
        stock.fire(); echo.fire();
        var a = stock.missiles().get(2);
        var b = echo.missiles().get(2);
        release(a, stock); release(b, echo);
        for (int tick = 0; tick < 12 + 32 + 32 + 16; tick++) {
            assertEquals(observed(a), observed(b), "post-release native tick " + tick);
            a.update(100 + tick, stock.player);
            b.update(100 + tick, echo.player);
            if (tick == 11) assertEquals(DdzMissileObjectInstance.STATE_STRAIGHT, number(a, "state"));
            if (tick == 43) assertEquals(DdzMissileObjectInstance.STATE_TURN, number(a, "state"));
            if (tick == 75) assertEquals(DdzMissileObjectInstance.STATE_HOME, number(a, "state"));
        }
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void finiteAllocationPrefixRetainsCountTwoAndDoesNotRetryAfterSlotsBecomeFree(int freeSlots) throws Exception {
        World world = new World(DoomsdayMissileRecipe.TAIL_ECHO);
        world.manager.reserveAllButNFreeSlots(freeSlots);
        world.fire();
        assertEquals(freeSlots, world.missiles().size());
        assertEquals(2, number(world.launcher, "count"));
        for (int index = 0; index < freeSlots; index++) assertEquals(index, number(world.missiles().get(index), "index"));
        for (int slot = 4; slot < 94; slot++) {
            final int candidate = slot;
            if (world.manager.getActiveObjects().stream()
                    .noneMatch(object -> ((AbstractObjectInstance) object).getSlotIndex() == candidate)) {
                world.manager.releaseDynamicSlot(slot);
            }
        }
        world.launcher.update(1, world.player);
        assertEquals(freeSlots, world.missiles().size(), "failed prefix does not change native spent accounting");
        assertEquals(2, number(world.launcher, "count"));
    }

    @Test void nativeSpentCounterRequiresAllThreeBeforeAnotherVolley() throws Exception {
        World world = new World(DoomsdayMissileRecipe.TAIL_ECHO); world.fire();
        world.player.setCentreX((short) 0);
        for (int expected : new int[]{1, 0, 255}) {
            world.launcher.missileSpent();
            assertEquals(expected, number(world.launcher, "count"));
            world.launcher.update(1, world.player);
            assertEquals(3, world.missiles().size(), "Player1 remains left of launcher");
        }
        world.player.setCentreX((short) 0x600);
        world.launcher.update(2, world.player);
        assertEquals(6, world.missiles().size());
        assertEquals(2, number(world.launcher, "count"));
    }

    @ParameterizedTest @ValueSource(ints = {0, 20, 52})
    void destructiveManagerRecreationRelinksPresetAndReplaysBeforeInitDuringRideAndAfterRelease(int advance)
            throws Exception {
        World world = new World(DoomsdayMissileRecipe.TAIL_ECHO); world.fire();
        for (int tick = 0; tick < advance; tick++) world.projectileTick(tick + 1);
        var saved = world.rewind.capture();
        var originalBoss = world.boss;
        var originalLauncher = world.launcher;
        var originalMissiles = world.missiles();
        for (int tick = 0; tick < 100; tick++) world.projectileTick(100 + tick);
        var expected = world.rewind.capture();
        world.restore(saved);
        assertNotSame(originalBoss, world.boss);
        assertNotSame(originalLauncher, world.launcher);
        assertEquals(DoomsdayMissileRecipe.TAIL_ECHO, world.boss.missileRecipe());
        assertSame(world.boss, world.launcher.parent);
        for (var missile : world.missiles()) {
            assertFalse(originalMissiles.contains(missile));
            assertSame(world.launcher, missile.parent);
        }
        for (int tick = 0; tick < 100; tick++) world.projectileTick(100 + tick);
        var replay = world.rewind.capture();
        assertEquals(expected.entries().keySet(), replay.entries().keySet());
        for (var key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), replay.get(key));
            assertTrue(diff.isEmpty(), key + ": " + diff);
        }
    }

    @ParameterizedTest @EnumSource(DoomsdayMissileRecipe.class)
    void nativeDestroyedParentRetiresCommittedMissilesAndExhaustWithOriginalDeletionTiming(DoomsdayMissileRecipe recipe)
            throws Exception {
        World world = new World(recipe); world.fire(); world.projectileTick(1);
        assertEquals(3, world.manager.activeObjectsOfType(DdzMissileExhaustObjectInstance.class).size());
        world.boss.setDestroyed(true); // Controlled native deleted-parent boundary.
        for (int tick = 0; tick < 4; tick++) {
            world.launcher.update(tick + 2, world.player);
            world.projectileTick(tick + 2);
        }
        assertTrue(world.launcher.isDestroyed());
        assertTrue(world.missiles().stream().allMatch(AbstractObjectInstance::isDestroyed));
        assertTrue(world.manager.activeObjectsOfType(DdzMissileExhaustObjectInstance.class).stream()
                .allMatch(AbstractObjectInstance::isDestroyed));
    }

    @ParameterizedTest @EnumSource(DoomsdayMissileRecipe.class)
    void actualManagerRootUnloadCannotStartANewVolley(DoomsdayMissileRecipe recipe) {
        World world = new World(recipe);
        world.manager.removeDynamicObject(world.boss);
        world.launcher.update(1, world.player);
        assertTrue(world.missiles().isEmpty(), "an unloaded native root must not leave a launcher producing new missiles");
        assertTrue(world.launcher.goingAway());
        assertFalse(world.launcher.isDestroyed(), "native Go_Delete retains the slot until its next dispatch");
        world.launcher.update(2, world.player);
        assertTrue(world.launcher.isDestroyed());
    }

    @ParameterizedTest @EnumSource(DoomsdayMissileRecipe.class)
    void actualManagerRootUnloadPreservesNativePendingDeleteOfCommittedRideAndExhaust(DoomsdayMissileRecipe recipe) {
        World world = new World(recipe); world.fire(); world.projectileTick(1);
        var committed = world.missiles();
        var exhaust = world.manager.activeObjectsOfType(DdzMissileExhaustObjectInstance.class);
        assertEquals(3, committed.size()); assertEquals(3, exhaust.size());
        world.manager.removeDynamicObject(world.boss);
        world.launcher.update(2, world.player);
        world.projectileTick(2);
        assertTrue(committed.stream().allMatch(AbstractDdzObjectInstance::goingAway));
        assertTrue(committed.stream().noneMatch(AbstractObjectInstance::isDestroyed));
        assertTrue(exhaust.stream().allMatch(AbstractDdzObjectInstance::goingAway));
        assertTrue(exhaust.stream().noneMatch(AbstractObjectInstance::isDestroyed));
        world.launcher.update(3, world.player);
        world.projectileTick(3);
        assertTrue(committed.stream().allMatch(AbstractObjectInstance::isDestroyed));
        assertTrue(exhaust.stream().allMatch(AbstractObjectInstance::isDestroyed));
    }

    @Test void nativeEntryAllocatesTheOriginalLauncherAndPlacedMissilesRemainStock() throws Exception {
        World world = new World(DoomsdayMissileRecipe.TAIL_ECHO, false);
        world.boss.update(0, world.player);
        assertEquals(DdzEndBossObjectInstance.ROUTINE_ENTER, world.boss.routine());
        for (int tick = 1; tick <= 144; tick++) world.boss.update(tick, world.player);
        assertEquals(DdzEndBossObjectInstance.ROUTINE_FIGHT, world.boss.routine());
        var launchers = world.manager.activeObjectsOfType(DdzEndBossLauncherObjectInstance.class);
        assertEquals(1, launchers.size());
        world.launcher = launchers.getFirst(); world.fire();
        assertEquals(3, world.missiles().size());
        assertEquals(DoomsdayMissileRecipe.TAIL_ECHO, value(world.missiles().get(2), "missileRecipe"));
        var placed = world.manager.createDynamicObject(() -> new DdzMissileObjectInstance(
                new ObjectSpawn(0x200, 0x100, 0xB8, 0, 0, false, 0x100)));
        placed.update(0, world.player);
        assertEquals(DoomsdayMissileRecipe.STOCK, value(placed, "missileRecipe"));
        assertEquals(DdzMissileObjectInstance.STATE_PLACED_WAIT, number(placed, "state"));
    }

    @Test void facadePreservesOriginalFieldsAndAcceptsOnlyFiniteRecipeAndExactNativeId() {
        var original = new ObjectSpawn(0x53F0, 0x80, 0xB6, 0, 0, false, 0x80, 371);
        assertEquals(original, DoomsdayFactory.endBoss(original, DoomsdayMissileRecipe.TAIL_ECHO).getSpawn());
        assertThrows(NullPointerException.class, () -> DoomsdayFactory.endBoss(null, DoomsdayMissileRecipe.STOCK));
        assertThrows(NullPointerException.class, () -> DoomsdayFactory.endBoss(original, null));
        assertThrows(IllegalArgumentException.class, () -> DoomsdayFactory.endBoss(
                new ObjectSpawn(0, 0, 0xB8, 0, 0, false, 0), DoomsdayMissileRecipe.TAIL_ECHO));
        assertArrayEquals(new DoomsdayMissileRecipe[]{DoomsdayMissileRecipe.STOCK, DoomsdayMissileRecipe.TAIL_ECHO},
                DoomsdayMissileRecipe.values());
    }

    private static void release(DdzMissileObjectInstance missile, World world) throws Exception {
        missile.update(0, world.player);
        for (int tick = 0; tick < 64 && number(missile, "state") == DdzMissileObjectInstance.STATE_RIDE_LAUNCHER; tick++) {
            missile.update(tick + 1, world.player);
        }
        assertEquals(DdzMissileObjectInstance.STATE_DRIFT, number(missile, "state"));
    }

    private static String observed(DdzMissileObjectInstance missile) throws Exception {
        return missile.getX() + "," + missile.getY() + "," + value(missile, "xPos") + "," + value(missile, "yPos")
                + "," + value(missile, "xVel") + "," + value(missile, "yVel") + "," + value(missile, "state")
                + "," + value(missile, "angle") + "," + value(missile, "direction") + "," + value(missile, "timer")
                + "," + value(missile, "turnRate") + "," + missile.getPriorityBucket() + "," + missile.isHighPriority();
    }

    private static Object value(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    private static int number(Object target, String name) throws Exception { return ((Number) value(target, name)).intValue(); }

    private static final class World {
        final TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x600, (short) 0x100);
        final Camera camera = new Camera(SonicConfigurationService.createStandalone());
        final TestObjectServices services = new TestObjectServices();
        final ObjectManager manager;
        final RewindRegistry rewind = new RewindRegistry();
        DdzEndBossObjectInstance boss;
        DdzEndBossLauncherObjectInstance launcher;

        World(DoomsdayMissileRecipe recipe) { this(recipe, true); }

        World(DoomsdayMissileRecipe recipe, boolean withLauncher) {
            var sprites = mock(SpriteManager.class);
            when(sprites.getMainPlayable()).thenReturn(player);
            services.withCamera(camera).withGameState(new GameStateManager()).withGameModule(new Sonic3kGameModule())
                    .withSpriteManager(sprites);
            services.zoneRuntimeRegistry().install(new DdzZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE));
            manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null, null, camera, services);
            services.withDirectObjectManager(manager); manager.reset(0);
            boss = manager.createDynamicObject(() -> new DdzEndBossObjectInstance(
                    new ObjectSpawn(0x200, 0x80, 0xB6, 0, 0, false, 0x80), recipe));
            if (withLauncher) launcher = manager.createDynamicObject(() -> new DdzEndBossLauncherObjectInstance(boss));
            rewind.register(manager.rewindSnapshottable());
            rewind.register(services.zoneRuntimeRegistry());
            AbstractObjectInstance.updateCameraBounds(0, 0, 2048, 1024, 0);
        }

        void fire() { launcher.update(0, player); }

        List<DdzMissileObjectInstance> missiles() {
            return manager.activeObjectsOfType(DdzMissileObjectInstance.class).stream()
                    .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
        }

        void projectileTick(int tick) {
            manager.getActiveObjects().stream().map(object -> (AbstractObjectInstance) object)
                    .filter(object -> object instanceof DdzMissileObjectInstance
                            || object instanceof DdzMissileExhaustObjectInstance || object instanceof DdzMissilePuffObjectInstance)
                    .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList()
                    .forEach(object -> object.update(tick, player));
        }

        void restore(CompositeSnapshot snapshot) {
            manager.setRewindInPlaceRestoreEnabledForTest(false);
            manager.getActiveObjects().stream().toList().forEach(manager::removeDynamicObject);
            rewind.restore(snapshot);
            boss = manager.activeObjectsOfType(DdzEndBossObjectInstance.class).getFirst();
            launcher = manager.activeObjectsOfType(DdzEndBossLauncherObjectInstance.class).getFirst();
        }
    }
}
