package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.sonic3k.objects.bosses.TestHczEndBossRobotnikShip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Declared Ship MAIN/escape component inlets for native subtype-4 explosions.
 * Native call chains and word operations own these oracles; no route or fitted
 * clock is supplied. The replay boundary covers this object manager and its RNG.
 */
class TestHczEndBossShipExplosion {
    @BeforeEach
    void headless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void resetGraphics() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void mainCreatesOneExactShipControllerAfterItsOwnSlotAndDoesNotRetry() {
        Fixture fixture = Fixture.create();
        Family family = fixture.family();
        assertTrue(controllers(fixture.manager()).isEmpty());

        family.boss().getState().defeated = true;
        family.ship().update(1, null);
        HczEndBossShipExplosionController controller = onlyController(fixture.manager());
        assertSame(family.ship(), getField(controller, "ship"));
        assertEquals(4, controller.getSpawn().subtype());
        assertTrue(controller.getSlotIndex() > family.ship().getSlotIndex());
        assertEquals(4, getInt(family.ship(), "routine"));
        assertTrue(explosions(fixture.manager()).isEmpty(), "allocation itself does not dispatch the controller");
        assertTrue(fixture.services().sfx.isEmpty());

        family.ship().update(2, null);
        family.ship().update(3, null);
        assertEquals(List.of(controller), controllers(fixture.manager()));
    }

    @Test
    void noLaterControllerSlotKeepsWaitAndDoesNotRetryWhenCapacityReturns() {
        Fixture fixture = Fixture.create();
        Family family = fixture.family();
        List<Integer> occupied = reserveAfter(fixture.manager(), family.ship().getSlotIndex());
        long seed = fixture.services().rng().getSeed();

        family.boss().getState().defeated = true;
        family.ship().update(1, null);
        assertEquals(4, getInt(family.ship(), "routine"));
        assertTrue(controllers(fixture.manager()).isEmpty());
        int earlier = fixture.manager().allocateDynamicSlot();
        assertTrue(earlier < family.ship().getSlotIndex(), "an earlier free SST is not an after-current allocation");
        fixture.manager().releaseDynamicSlot(earlier);
        fixture.manager().releaseDynamicSlot(occupied.getLast());

        family.ship().update(2, null);
        assertTrue(controllers(fixture.manager()).isEmpty(), "MAIN was left even when creation failed");
        assertEquals(seed, fixture.services().rng().getSeed());
        assertTrue(fixture.services().sfx.isEmpty());
    }

    @Test
    void firstEntryEmitsThenNegativeEightyKeepsThreeEntryCadenceBeyond128Emissions() {
        Fixture fixture = Fixture.create();
        Family family = fixture.family();
        HczEndBossShipExplosionController controller = fixture.defeatShip(family);
        int emissions = 0;
        for (int entry = 0; entry <= 3 * 130; entry++) {
            family.ship().setPosition(0x4000 + entry, 0x700 - entry);
            controller.update(entry, null);
            assertEquals((0x4000 + entry) & 0xFFFF, controller.getX());
            assertEquals((0x700 - entry) & 0xFFFF, controller.getY());
            List<S3kBossExplosionChild> effects = explosions(fixture.manager());
            assertEquals(entry % 3 == 0 ? 1 : 0, effects.size(), "native Obj_Wait entry " + entry);
            emissions += effects.size();
            // Isolate controller lifetime from the independently tested effect
            // animation, keeping real SST capacity available for each attempt.
            effects.forEach(fixture.manager()::removeDynamicObject);
        }
        assertEquals(131, emissions, "the negative $80 byte is never decremented toward zero");
        assertFalse(controller.isDestroyed());
        assertFalse(getBoolean(controller, "pendingDelete"));
    }

    @Test
    void successfulChildAllocationConsumesOneSharedRandomWithLowAndHighWordOffsets() {
        Fixture fixture = Fixture.create();
        Family family = fixture.family();
        HczEndBossShipExplosionController controller = fixture.defeatShip(family);
        family.ship().setPosition(2, 0xFFF8);
        fixture.services().rng().setSeed(0x12345678L);
        GameRng expected = new GameRng(GameRng.Flavour.S3K, 0x12345678L);
        assertEquals(0x1234C399, expected.nextRaw(), "native Random_Number word-fold oracle");

        controller.update(0, null);

        S3kBossExplosionChild effect = onlyExplosion(fixture.manager());
        assertTrue(effect.getSlotIndex() > controller.getSlotIndex());
        assertEquals(0xFFFB, effect.getX(), "low word $C399 gives -7, wrapped as a word");
        assertEquals(0x000C, effect.getY(), "swapped high word $1234 gives +20, not bits 8..15");
        assertEquals(expected.getSeed(), fixture.services().rng().getSeed());
        assertTrue(effect.nativeInitSfxForTest());
        assertTrue(fixture.services().sfx.isEmpty(), "controller/allocation do not own the child INIT SFX");
    }

    @Test
    void failedEffectAllocationDoesNotDrawRandomOrSoundAndKeepsTheOriginalWait() {
        Fixture fixture = Fixture.create();
        Family family = fixture.family();
        HczEndBossShipExplosionController controller = fixture.defeatShip(family);
        List<Integer> occupied = reserveAfter(fixture.manager(), controller.getSlotIndex());
        fixture.services().rng().setSeed(0x12345678L);

        controller.update(0, null);
        assertTrue(explosions(fixture.manager()).isEmpty());
        assertEquals(0x12345678L, fixture.services().rng().getSeed());
        assertTrue(fixture.services().sfx.isEmpty());
        assertEquals(2, getInt(controller, "interval"));
        fixture.manager().releaseDynamicSlot(occupied.getLast());

        controller.update(1, null);
        controller.update(2, null);
        assertTrue(explosions(fixture.manager()).isEmpty(), "capacity returning does not bypass Obj_Wait");
        controller.update(3, null);
        assertEquals(1, explosions(fixture.manager()).size());
        assertNotEquals(0x12345678L, fixture.services().rng().getSeed());
        assertTrue(fixture.services().sfx.isEmpty());
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void onlyTheActualExplosionFirstOwnEntryPlaysNativeInitSound() {
        Fixture fixture = Fixture.create();
        HczEndBossShipExplosionController controller = fixture.defeatShip(fixture.family());
        controller.update(0, null);
        S3kBossExplosionChild effect = onlyExplosion(fixture.manager());
        assertTrue(fixture.services().sfx.isEmpty());

        effect.update(0, null);
        effect.update(1, null);
        assertEquals(List.of(Sonic3kSfx.EXPLODE.id), fixture.services().sfx);
    }

    @Test
    void shipBitFiveSelectsControllerDeleteBeforeRefreshAndRetiresTheReferenceForItsTail() {
        Fixture fixture = Fixture.create();
        Family family = fixture.family();
        HczEndBossShipExplosionController controller = fixture.defeatShip(family);
        int oldX = controller.getX(), oldY = controller.getY();
        seedEscape(family.ship(), 0, 0x4000BEEF);
        family.ship().update(2, null); // Actual underflow writes bit 5 and selects its own delete.
        assertFalse(family.ship().isDestroyed());
        assertTrue(getBoolean(family.ship(), "pendingDelete"));

        long seed = fixture.services().rng().getSeed();
        controller.update(2, null);
        assertFalse(controller.isDestroyed(), "Go_Delete_Sprite selects its next entry");
        assertTrue(getBoolean(controller, "pendingDelete"));
        assertNull(getField(controller, "ship"));
        assertEquals(oldX, controller.getX(), "parent checks precede position refresh");
        assertEquals(oldY, controller.getY());
        assertTrue(explosions(fixture.manager()).isEmpty());
        assertEquals(seed, fixture.services().rng().getSeed());
        var snapshot = controller.captureRewindState();
        family.ship().update(3, null); // Parent code is now absent.

        for (int cycle = 0; cycle < 2; cycle++) {
            var restored = ObjectConstructionContext.construct(fixture.services(),
                    () -> controller.recreateForRewind(new RewindRecreateContext(
                            controller.getSpawn(), snapshot, fixture.services(), fixture.manager(), null)));
            restored.setServices(fixture.services());
            restored.restoreRewindState(snapshot);
            assertNull(getField(restored, "ship"), "a selected delete does not retain a dead required owner");
            restored.update(3, null);
            assertTrue(restored.isDestroyed());
            assertEquals(oldX, restored.getX());
            assertEquals(oldY, restored.getY());
        }
    }

    @Test
    void absentParentCodeSelectsDeletionWithoutAnExtraEmission() {
        Fixture fixture = Fixture.create();
        Family family = fixture.family();
        HczEndBossShipExplosionController controller = fixture.defeatShip(family);
        family.ship().setPosition(0x1234, 0x5678);
        family.ship().setDestroyed(true); // Declared zero-code component inlet.
        long seed = fixture.services().rng().getSeed();

        controller.update(0, null);

        assertTrue(getBoolean(controller, "pendingDelete"));
        assertFalse(controller.isDestroyed());
        assertNull(getField(controller, "ship"));
        assertEquals(seed, fixture.services().rng().getSeed());
        assertTrue(explosions(fixture.manager()).isEmpty());
        controller.update(1, null);
        assertTrue(controller.isDestroyed());
    }

    @Test
    void identicalCoordinateFamiliesRecreateExactShipLinksAndReplaySharedRngTwice() {
        Fixture fixture = Fixture.create();
        Family familyA = fixture.family();
        Family familyB = fixture.family();
        HczEndBossShipExplosionController controllerA = fixture.defeatShip(familyA);
        HczEndBossShipExplosionController controllerB = fixture.defeatShip(familyB);
        controllerA.update(0, null);
        controllerB.update(0, null);
        removeExplosions(fixture.manager());
        fixture.manager().setRewindInPlaceRestoreEnabledForTest(false);
        ObjectRefId shipAId = id(fixture.manager(), familyA.ship());
        ObjectRefId shipBId = id(fixture.manager(), familyB.ship());
        ObjectRefId controllerAId = id(fixture.manager(), controllerA);
        ObjectRefId controllerBId = id(fixture.manager(), controllerB);
        RewindRegistry registry = new RewindRegistry();
        registry.register(fixture.manager().rewindSnapshottable());
        registry.register(fixture.services().rng());
        CompositeSnapshot snapshot = registry.capture();
        List<ForwardStep> expected = forward(fixture, familyA.ship(), familyB.ship(), controllerA, controllerB);

        for (int cycle = 0; cycle < 2; cycle++) {
            for (ObjectInstance object : List.copyOf(fixture.manager().getActiveObjects())) {
                fixture.manager().removeDynamicObject(object);
            }
            registry.restore(snapshot);
            var restoredShipA = (HczEndBossRobotnikShip) resolve(fixture.manager(), shipAId);
            var restoredShipB = (HczEndBossRobotnikShip) resolve(fixture.manager(), shipBId);
            var restoredA = (HczEndBossShipExplosionController) resolve(fixture.manager(), controllerAId);
            var restoredB = (HczEndBossShipExplosionController) resolve(fixture.manager(), controllerBId);
            assertNotSame(controllerA, restoredA);
            assertNotSame(controllerB, restoredB);
            assertSame(restoredShipA, getField(restoredA, "ship"));
            assertSame(restoredShipB, getField(restoredB, "ship"));
            assertNotSame(getField(restoredA, "ship"), getField(restoredB, "ship"));
            assertEquals(expected, forward(fixture, restoredShipA, restoredShipB, restoredA, restoredB));
        }
    }

    @Test
    void rootDefeatWaitDoesNotInventASeparateControllerOrRandomDraw() {
        Fixture fixture = Fixture.create();
        HczEndBossInstance boss = fixture.boss();
        boss.getState().defeated = true;
        long seed = fixture.services().rng().getSeed();
        boss.onDefeatStarted(); // Declared production defeat-entry component inlet.
        for (int entry = 0; entry < 6; entry++) {
            boss.update(entry, null);
        }
        assertEquals(0x3F - 6, getInt(boss, "waitTimer"));
        assertEquals(seed, fixture.services().rng().getSeed());
        assertTrue(explosions(fixture.manager()).isEmpty());
        assertTrue(controllers(fixture.manager()).isEmpty(), "only actual Ship MAIN allocates subtype 4");
        assertTrue(fixture.services().sfx.isEmpty());
    }

    private static List<ForwardStep> forward(Fixture fixture, HczEndBossRobotnikShip shipA,
                                             HczEndBossRobotnikShip shipB,
                                             HczEndBossShipExplosionController controllerA,
                                             HczEndBossShipExplosionController controllerB) {
        List<ForwardStep> result = new ArrayList<>();
        for (int entry = 1; entry <= 6; entry++) {
            shipA.setPosition(0x4000 + entry, 0x740 + entry);
            shipB.setPosition(0x4000 - entry, 0x740 - entry);
            controllerA.update(entry, null);
            controllerB.update(entry, null);
            List<Position> positions = explosions(fixture.manager()).stream()
                    .map(effect -> new Position(effect.getX(), effect.getY())).toList();
            result.add(new ForwardStep(controllerA.getX(), controllerA.getY(), getInt(controllerA, "interval"),
                    controllerB.getX(), controllerB.getY(), getInt(controllerB, "interval"),
                    positions, fixture.services().rng().getSeed()));
            removeExplosions(fixture.manager());
        }
        return List.copyOf(result);
    }

    private record Position(int x, int y) { }
    private record ForwardStep(int ax, int ay, int aWait, int bx, int by, int bWait,
                               List<Position> effects, long seed) { }

    private static ObjectRefId id(ObjectManager manager, ObjectInstance object) {
        return manager.captureIdentityContext().requireIdentityTable().idFor(object);
    }

    private static ObjectInstance resolve(ObjectManager manager, ObjectRefId id) {
        return manager.captureIdentityContext().requireIdentityTable().resolveObject(id, true);
    }

    private static List<Integer> reserveAfter(ObjectManager manager, int parentSlot) {
        List<Integer> reserved = new ArrayList<>();
        for (int slot; (slot = manager.allocateSlotAfter(parentSlot)) >= 0;) {
            reserved.add(slot);
        }
        assertFalse(reserved.isEmpty());
        return reserved;
    }

    private static List<HczEndBossShipExplosionController> controllers(ObjectManager manager) {
        return manager.activeObjectsOfType(HczEndBossShipExplosionController.class).stream()
                .filter(controller -> !controller.isDestroyed()).toList();
    }

    private static HczEndBossShipExplosionController onlyController(ObjectManager manager) {
        assertEquals(1, controllers(manager).size());
        return controllers(manager).getFirst();
    }

    private static List<S3kBossExplosionChild> explosions(ObjectManager manager) {
        return manager.activeObjectsOfType(S3kBossExplosionChild.class).stream()
                .filter(effect -> !effect.isDestroyed()).toList();
    }

    private static S3kBossExplosionChild onlyExplosion(ObjectManager manager) {
        assertEquals(1, explosions(manager).size());
        return explosions(manager).getFirst();
    }

    private static void removeExplosions(ObjectManager manager) {
        explosions(manager).forEach(manager::removeDynamicObject);
    }

    private record Family(HczEndBossInstance boss, HczEndBossRobotnikShip ship) { }

    private record Fixture(RecordingServices services, ObjectManager manager) {
        static Fixture create() {
            Camera camera = mock(Camera.class);
            when(camera.getY()).thenReturn((short) 0x100);
            RecordingServices services = new RecordingServices();
            services.withConfiguration(SonicConfigurationService.createStandalone())
                    .withCamera(camera).withGameState(new GameStateManager())
                    .withGraphicsManager(GraphicsManager.getInstance());
            ObjectManager manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0,
                    null, null, GraphicsManager.getInstance(), camera, services);
            services.withDirectObjectManager(manager);
            manager.reset(0);
            return new Fixture(services, manager);
        }

        HczEndBossInstance boss() {
            return manager.createDynamicObject(() -> new HczEndBossInstance(
                    new ObjectSpawn(0x4000, 0x738, 0x9A, 0, 0, false, 0x738)));
        }

        Family family() {
            AbstractObjectInstance earlier = manager.createDynamicObject(() -> new AbstractObjectInstance(
                    new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "earlier-slot") {
                @Override public void appendRenderCommands(List<GLCommand> commands) { }
            });
            HczEndBossInstance boss = boss();
            HczEndBossRobotnikShip ship = manager.createDynamicObject(() -> new HczEndBossRobotnikShip(boss));
            assertNotNull(ship);
            ship.update(0, null); // Actual Ship INIT allocates its Head.
            manager.removeDynamicObject(earlier);
            return new Family(boss, ship);
        }

        HczEndBossShipExplosionController defeatShip(Family family) {
            family.boss().getState().defeated = true;
            family.ship().update(1, null);
            return controllers(manager).stream()
                    .filter(controller -> getField(controller, "ship") == family.ship())
                    .findFirst().orElseThrow();
        }
    }

    private static final class RecordingServices extends TestObjectServices {
        private final List<Integer> sfx = new ArrayList<>();
        @Override public void playSfx(int soundId) { sfx.add(soundId); }
    }
}
