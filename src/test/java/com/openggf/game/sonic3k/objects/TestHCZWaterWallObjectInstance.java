package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.GameRng;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.game.sonic3k.resources.S3kKosModuleQueue;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.tests.HardwareBoundaryPump;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RequiresRom(SonicGame.SONIC_3K)
class TestHCZWaterWallObjectInstance {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
    }

    @AfterEach
    void resetSessionTimingFixture() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        HCZWaterRushObjectInstance.HCZWaterRushPaletteCycleGate.setActive(false);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void verticalGeyserLockAndReleaseUseFullObjectControlPolicyForPlayerAndSidekick() throws Exception {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x01C8);
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0x0208, (short) 0x01C8);
        HCZWaterWallObjectInstance waterWall = new HCZWaterWallObjectInstance(
                new ObjectSpawn(0x0200, 0x0200, 0x3B, 1, 0, false, 0));
        waterWall.setServices(timedServices().withSidekicks(List.of(sidekick)));
        int playerInitialY = player.getCentreY();
        int sidekickInitialY = sidekick.getY();

        waterWall.update(0, player);

        assertFullControl(player);
        assertTrue(player.isControlLocked());
        assertEquals(playerInitialY - 8, player.getCentreY());
        assertFullControl(sidekick);
        assertTrue(sidekick.isControlLocked());
        assertEquals(sidekickInitialY - 8, sidekick.getY());

        invokeReleasePlayers(waterWall, player);

        assertNoControl(player);
        assertFalse(player.isControlLocked());
        assertNoControl(sidekick);
        assertFalse(sidekick.isControlLocked());
    }

    @Test
    void verticalGeyserProcessesAllEngineSidekicksFromPlayerQuery() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x01C8);
        TestablePlayableSprite firstSidekick = new TestablePlayableSprite("tails", (short) 0x0208, (short) 0x01C8);
        TestablePlayableSprite extraSidekick = new TestablePlayableSprite("knuckles", (short) 0x0210, (short) 0x01C8);
        HCZWaterWallObjectInstance waterWall = new HCZWaterWallObjectInstance(
                new ObjectSpawn(0x0200, 0x0200, 0x3B, 1, 0, false, 0));
        waterWall.setServices(new QueryOnlyServices(player, List.of(firstSidekick, extraSidekick)));

        waterWall.update(0, player);

        assertFullControl(firstSidekick);
        assertTrue(firstSidekick.isControlLocked());
        assertFullControl(extraSidekick);
        assertTrue(extraSidekick.isControlLocked());
    }

    @Test
    void verticalGeyserWaitsForKosModuleBeforeRiseThenFallsThrough() {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0200, (short) 0x01C8);
        TestablePlayableSprite sidekick = new TestablePlayableSprite("tails", (short) 0x0208, (short) 0x01C8);
        player.setForcedAnimationId(Sonic3kAnimationIds.FLOAT2);
        sidekick.setForcedAnimationId(Sonic3kAnimationIds.FLOAT2);
        HCZWaterWallObjectInstance waterWall = new HCZWaterWallObjectInstance(
                new ObjectSpawn(0x0200, 0x0200, 0x3B, 1, 0, false, 0));
        Rom rom = TestEnvironment.currentRom();
        HardwareTimingService inheritedSessionTiming = GameServices.hardwareTiming();
        fillKosModuleFifo(inheritedSessionTiming, rom);
        HardwareTimingService timing = new HardwareTimingService();
        S3kRuntimeArtCoordinator coordinator =
                new S3kRuntimeArtCoordinator(timing);
        TestObjectServices services = new TestObjectServices() {
            @Override
            public HardwareTimingService hardwareTiming() {
                return timing;
            }

            @Override
            public Rom rom() {
                return rom;
            }

            @Override
            public RuntimeArtCoordinator runtimeArtCoordinator() {
                return coordinator;
            }
        };
        waterWall.setServices(services.withSidekicks(List.of(sidekick)));
        int playerInitialY = player.getCentreY();
        int wallInitialY = waterWall.getY();

        waterWall.update(0, player);
        assertEquals(1, timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
        assertEquals(playerInitialY - 8, player.getCentreY());
        assertEquals(wallInitialY, waterWall.getY());

        // ROM Process_Kos_Module_Queue retires the last module and clears
        // Kos_modules_left (sonic3k.asm:2784) from the LevelLoop tail (7908), after
        // Process_Sprites (7889). The polling object therefore first observes zero on
        // the following iteration's object pass — the update immediately after the
        // module state step, which the engine runs at the frame top.
        int pullUpdates = 1;
        int frame = 1;
        boolean roseAfterQueueCleared = false;
        while (frame < 10_000) {
            serviceBoundary(timing, coordinator, HardwareServiceBoundary.PRE_MAIN_LOOP);
            int incompleteBeforeObject = timing.incompleteCount(
                    HardwareWorkKind.KOS_MODULE_QUEUE);
            waterWall.update(frame, player);
            pullUpdates++;
            assertEquals(incompleteBeforeObject,
                    timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE),
                    "object polling must not publish hardware readiness");
            assertEquals(playerInitialY - (pullUpdates * 8), player.getCentreY(),
                    "loc_302E6 should pull the player while Kos_modules_left is nonzero,"
                            + " and loc_302FA keeps pulling through the fall-through tick");
            if (incompleteBeforeObject == 0) {
                assertEquals(wallInitialY - 8, waterWall.getY(),
                        "loc_302FA falls through into the first loc_30338 rise tick on the"
                                + " first object pass that observes Kos_modules_left == 0");
                roseAfterQueueCleared = true;
                serviceBoundary(timing, coordinator, HardwareServiceBoundary.POST_OBJECTS);
                break;
            }
            assertEquals(wallInitialY, waterWall.getY(),
                    "vertical geyser must not enter loc_30338 while queued art is pending");
            serviceBoundary(timing, coordinator, HardwareServiceBoundary.POST_OBJECTS);
            frame++;
        }

        assertTrue(roseAfterQueueCleared,
                "the seeded module queue must drain and release the geyser");
        assertEquals(0, timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
        assertEquals(Sonic3kAnimationIds.BLANK.id(), player.getAnimationId());
        assertEquals(-1, player.getForcedAnimationId(),
                "water-wall object control must supersede the prior tunnel override");
        assertEquals(Sonic3kAnimationIds.BLANK.id(), sidekick.getAnimationId());
        assertEquals(-1, sidekick.getForcedAnimationId());
        assertEquals(4, inheritedSessionTiming.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE),
                "the fixture must not consume unrelated work inherited from another test session");
    }

    @ParameterizedTest
    @CsvSource({"0, 0x1240, true", "0, 0x1241, true", "0, 0x127F, true",
            "0, 0x1280, false", "0, 0x0F80, true", "0, 0x0F7F, false",
            "1, 0x1241, true", "1, 0x1280, false"})
    void waitingUsesTheInclusiveUnsignedCoarseWindow(int subtype, String wallX, boolean stays) {
        var world = new DispatchWorld(false, subtype);
        var wall = world.spawnAtSlot(Integer.decode(wallX), subtype, 8);
        if (subtype == 0) {
            world.finishArtLoad();
        } else {
            world.step();
        }
        assertEquals(stays, world.manager.getActiveObjects().contains(wall));
    }

    @Test
    void horizontalTriggerDrawsThenActiveTailDeletesAndClearsThePlacementBit() {
        var world = new DispatchWorld(true, 0);
        var wall = world.wall();
        int respawnIndex = wall.getRespawnStateIndex();
        world.finishArtLoad();
        world.player.setCentreX((short) 0x1160);
        world.step();
        assertTrue(world.isRespawnBitSet(respawnIndex));
        world.step();
        assertTrue(world.manager.getActiveObjects().contains(wall));
        assertEquals(0x1108, wall.getX());

        // The previous render pass still saw the wall. The next live camera
        // samples a coarse back of $1180, so the active DrawActive tail deletes.
        world.cameraAt(0x1200, 0x500);
        world.step();
        assertFalse(world.manager.getActiveObjects().contains(wall));
        assertFalse(world.isRespawnBitSet(respawnIndex));
        world.player.setCentreX((short) 0x1000);
        world.cameraAt(0x1000, 0x500);
        world.step();
        assertTrue(world.wall() != wall, "the X cursor can admit the placement again");
    }

    @Test
    void horizontalTriggerStillTakesTheWaitingTailAfterDebrisAllocation() {
        var world = new DispatchWorld(false, 0);
        var wall = world.spawnAtSlot(0x1100, 0, 8);
        world.finishArtLoad();
        world.player.setCentreX((short) 0x1160);
        world.cameraAt(0x1200, 0x500);
        int initialSlots = world.manager.getAllocatedSlotCount();
        world.step();
        assertFalse(world.manager.getActiveObjects().contains(wall));
        assertEquals(initialSlots + 8, world.manager.getPeakSlotCount(),
                "the debris loop allocates before the parent range tail");
    }

    @Test
    void verticalEruptionAndFallApplyTheirDrawTails() {
        var erupting = new DispatchWorld(true, 1);
        erupting.startVerticalEruption();
        int eruptingRespawnIndex = erupting.wall().getRespawnStateIndex();
        erupting.cameraAt(0x1200, 0x300);
        erupting.step();
        assertFalse(erupting.hasWall());
        assertFalse(erupting.isRespawnBitSet(eruptingRespawnIndex));

        var falling = new DispatchWorld(true, 1);
        falling.startVerticalEruption();
        int fallingRespawnIndex = falling.wall().getRespawnStateIndex();
        for (int i = 0; i < 40; i++) {
            falling.cameraAt(0x1000, falling.wall().getY() - 0x30);
            falling.step();
            assertTrue(falling.hasWall());
        }
        falling.cameraAt(0x1200, falling.wall().getY() - 0x30);
        falling.step();
        assertFalse(falling.hasWall(), "FallMove runs the range tail when the prior render bit was set");
        assertFalse(falling.isRespawnBitSet(fallingRespawnIndex));
    }

    @ParameterizedTest
    @CsvSource({"0, 150, true", "1, 30, false"})
    void clearRenderBitTakesCleanupBeforeRangeAndReloadsOnlyOnUnderflow(int subtype,
            int delay, boolean cleanupKeepsRespawnBit) throws Exception {
        var world = new DispatchWorld(true, subtype);
        var provider = (Sonic3kObjectArtProvider) world.services.gameModule().getObjectArtProvider();
        // Pin this lightweight manager fixture's enemy-art context to HCZ1.
        var zone = Sonic3kObjectArtProvider.class.getDeclaredField("currentZoneIndex");
        zone.setAccessible(true);
        zone.setInt(provider, 1);

        if (subtype == 0) {
            world.finishArtLoad();
            world.player.setCentreX((short) 0x1160);
            world.renderCameraAt(0x1000, 0x700);
            world.step();
        } else {
            world.startVerticalEruption();
            world.renderCameraAt(0x1000, 0x700);
            for (int i = 0; i < 40; i++) {
                world.step();
            }
        }
        var wall = world.wall();
        int respawnIndex = wall.getRespawnStateIndex();
        world.cameraAt(0x1200, 0x500);
        world.step();
        assertTrue(world.hasWall(), "clear previous render bit selects cleanup, even outside the coarse window");
        assertEquals(cleanupKeepsRespawnBit, world.isRespawnBitSet(respawnIndex));
        for (int i = 0; i < delay; i++) {
            world.step();
        }
        assertTrue(world.manager.getActiveObjects().contains(wall), "zero still holds");
        assertEquals(0, GameServices.hardwareTiming().incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
        world.step();
        assertFalse(world.manager.getActiveObjects().contains(wall));
        assertEquals(4, GameServices.hardwareTiming().incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
        assertEquals(cleanupKeepsRespawnBit, world.isRespawnBitSet(respawnIndex));
        world.player.setCentreX((short) 0x1000);
        world.cameraAt(0x1000, 0x500);
        world.step();
        assertEquals(!cleanupKeepsRespawnBit, world.hasWall());
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 1", "1, 0", "1, 1", "1, 2"})
    void forwardSprayAdmissionControlsTheRngWordAndReplays(int subtype, int availableSlots) {
        var world = new DispatchWorld(false, subtype);
        int lastSlot = world.manager.getLastDynamicSlotExclusive() - 1;
        int parentSlot = lastSlot - availableSlots - 1;
        world.manager.createDynamicObjectAtSlot(() -> new StillSpriteInstance(
                new ObjectSpawn(0x1100, 0x580, 0x2F, 6, 0, false, 0x580)), lastSlot);
        world.spawnAtSlot(0x1100, subtype, parentSlot);
        if (subtype == 0) {
            world.finishArtLoad();
            world.player.setCentreX((short) 0x1160);
            world.step();
        } else {
            world.startVerticalEruption();
            world.cameraAt(0x1000, 0x300);
        }
        world.manager.getActiveObjects().stream()
                .filter(HCZWaterWallObjectInstance.WaterWallDebrisChild.class::isInstance)
                .toList().forEach(world.manager::removeDynamicObject);
        assertFalse(world.manager.occupiedDynamicSlotIds().containsKey(parentSlot - 1),
                "a lower hole must never satisfy a forward allocation");

        var rng = world.services.rng();
        var beforeRng = rng.capture();
        var expectedRng = new GameRng(GameRng.Flavour.S3K);
        expectedRng.restore(beforeRng);
        int randomWord = availableSlots > 0 ? expectedRng.nextWord() : 0;
        var objects = world.manager.rewindSnapshottable();
        var beforeObjects = objects.capture();
        short beforePlayerY = world.player.getCentreY();
        int beforeFrame = world.frame;
        world.step();
        assertEquals(expectedRng.capture(), rng.capture());
        var sprays = world.manager.getActiveObjects().stream()
                .filter(HCZWaterWallObjectInstance.WaterWallSprayChild.class::isInstance)
                .map(HCZWaterWallObjectInstance.WaterWallSprayChild.class::cast)
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                .toList();
        assertEquals(availableSlots, sprays.size());
        var wall = world.wall();
        for (int i = 0; i < sprays.size(); i++) {
            var spray = sprays.get(i);
            assertEquals(parentSlot + i + 1, spray.getSlotIndex());
            if (subtype == 0) {
                int offset = ((randomWord & 0xF) << 3) - 0x50;
                assertEquals(wall.getX() + offset + 4, spray.getX(),
                        "higher-slot horizontal spray moves on its first dispatch");
            } else {
                int direction = i == 0 ? 1 : -1;
                int velocity = direction * ((randomWord & 0xF) << 6);
                assertEquals(wall.getX() + direction * 0x10 + Math.floorDiv(velocity, 0x100), spray.getX());
                assertEquals(wall.getY() - 0x50 - 7, spray.getY(),
                        "higher-slot vertical spray moves on its first dispatch");
            }
        }

        var afterObjects = objects.capture();
        world.manager.setRewindInPlaceRestoreEnabledForTest(false);
        objects.restore(beforeObjects);
        rng.restore(beforeRng);
        world.player.setCentreY(beforePlayerY);
        world.frame = beforeFrame;
        world.step();
        assertEquals(expectedRng.capture(), rng.capture());
        var diff = RewindSnapshotDiff.diffKey("water-wall-spray", afterObjects, objects.capture());
        assertTrue(diff.isEmpty(), diff::toString);
    }

    private static final class DispatchWorld {
        private final Camera camera = mock(Camera.class);
        private final TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x1000, (short) 0x580);
        private final TestObjectServices services = timedServices();
        private final ObjectManager manager;
        private int frame;

        DispatchWorld(boolean placed, int subtype) {
            player.setCentreX((short) 0x1000);
            player.setCentreY((short) 0x580);
            when(camera.getWidth()).thenReturn((short) 320);
            when(camera.getHeight()).thenReturn((short) 224);
            when(camera.getFocusedSprite()).thenReturn(player);
            cameraAt(0x1000, 0x500);
            services.withCamera(camera).withGameModule(new Sonic3kGameModule());
            var placement = new ObjectSpawn(0x1100, 0x580, 0x3B, subtype, 0, true, 0x8580, 0);
            manager = new ObjectManager(placed ? List.of(placement) : List.of(), new Sonic3kObjectRegistry(),
                    0, null, null, GraphicsManager.getInstance(), camera, services);
            services.withDirectObjectManager(manager);
            manager.enableExecThenLoadPlacement();
            manager.enforceSlotLimit();
            manager.enablePermanentDestroyLatch();
            manager.reset(0x1000);
        }

        void cameraAt(int x, int y) {
            when(camera.getX()).thenReturn((short) x);
            when(camera.getY()).thenReturn((short) y);
            renderCameraAt(x, y);
        }

        void renderCameraAt(int x, int y) {
            when(camera.getXCopy()).thenReturn((short) x);
            when(camera.getYCopy()).thenReturn((short) y);
        }

        HCZWaterWallObjectInstance spawnAtSlot(int x, int subtype, int slot) {
            return manager.createDynamicObjectAtSlot(() -> new HCZWaterWallObjectInstance(
                    new ObjectSpawn(x, 0x580, 0x3B, subtype, 0, false, 0x580)), slot);
        }

        boolean isRespawnBitSet(int index) {
            return (manager.rewindSnapshottable().capture().placement().objState()[index] & 0x80) != 0;
        }

        boolean hasWall() {
            return manager.getActiveObjects().stream().anyMatch(HCZWaterWallObjectInstance.class::isInstance);
        }

        HCZWaterWallObjectInstance wall() {
            return manager.getActiveObjects().stream().filter(HCZWaterWallObjectInstance.class::isInstance)
                    .map(HCZWaterWallObjectInstance.class::cast).findFirst().orElseThrow();
        }

        void step() {
            manager.update(camera.getX(), player, List.of(), frame++);
            manager.refreshPostCameraRenderState();
        }

        void finishArtLoad() {
            step();
            var timing = services.hardwareTiming();
            for (int pass = 0; pass < 500 && timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE) > 0; pass++) {
                var coordinator = services.runtimeArtCoordinator();
                serviceBoundary(timing, coordinator, HardwareServiceBoundary.POST_OBJECTS);
                serviceBoundary(timing, coordinator, HardwareServiceBoundary.PRE_MAIN_LOOP);
            }
            assertEquals(0, timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE));
            step();
        }

        void startVerticalEruption() {
            player.setCentreX((short) 0x1100);
            player.setCentreY((short) 0x548);
            finishArtLoad();
            for (int pass = 1; pass < 56; pass++) {
                step();
            }
        }
    }

    private static void assertFullControl(TestablePlayableSprite player) {
        assertTrue(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
    }

    private static TestObjectServices timedServices() {
        HardwareTimingService timing = GameServices.hardwareTiming();
        Rom rom = TestEnvironment.currentRom();
        return new TestObjectServices() {
            @Override
            public HardwareTimingService hardwareTiming() {
                return timing;
            }

            @Override
            public Rom rom() {
                return rom;
            }

            @Override
            public RuntimeArtCoordinator runtimeArtCoordinator() {
                return GameServices.runtimeArtCoordinator();
            }
        };
    }

    private static void fillKosModuleFifo(HardwareTimingService timing, Rom rom) {
        S3kKosModuleQueue queue = S3kRuntimeArtCoordinator.current().moduleQueue();
        try {
            for (int slot = 0; slot < 4; slot++) {
                queue.queue(
                        rom,
                        Sonic3kConstants.ART_KOSM_HCZ_GEYSER_VERT_ADDR,
                        Sonic3kConstants.ARTTILE_HCZ_GEYSER + slot * 0x20);
            }
        } catch (Exception e) {
            throw new AssertionError("unable to seed the inherited four-entry KosM FIFO", e);
        }
    }

    private static void serviceBoundary(
            HardwareTimingService timing,
            RuntimeArtCoordinator coordinator,
            HardwareServiceBoundary boundary) {
        // Mirrors LevelFrameStep.serviceBoundary: the module state step runs at the
        // frame top, ahead of the ledger service, modelling LevelLoop's tail call to
        // Process_Kos_Module_Queue (sonic3k.asm:7940) reaching the next iteration's
        // Process_Kos_Queue (7887) across Wait_VSync (7888).
        HardwareBoundaryPump.service(timing, coordinator, boundary);
    }

    private static void assertNoControl(TestablePlayableSprite player) {
        assertFalse(player.isObjectControlled());
        assertFalse(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement());
    }

    private static void invokeReleasePlayers(
            HCZWaterWallObjectInstance waterWall,
            TestablePlayableSprite player) throws Exception {
        Method method = HCZWaterWallObjectInstance.class
                .getDeclaredMethod("releasePlayers", com.openggf.sprites.playable.AbstractPlayableSprite.class);
        method.setAccessible(true);
        method.invoke(waterWall, player);
    }

    private static final class QueryOnlyServices extends TestObjectServices {
        private final ObjectPlayerQuery playerQuery;
        private final HardwareTimingService timing = GameServices.hardwareTiming();
        private final Rom rom = TestEnvironment.currentRom();

        QueryOnlyServices(TestablePlayableSprite main, List<TestablePlayableSprite> sidekicks) {
            this.playerQuery = new ObjectPlayerQuery(() -> main, () -> sidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return playerQuery;
        }

        @Override
        public HardwareTimingService hardwareTiming() {
            return timing;
        }

        @Override
        public Rom rom() {
            return rom;
        }

        @Override
        public RuntimeArtCoordinator runtimeArtCoordinator() {
            return GameServices.runtimeArtCoordinator();
        }

        @Override
        public List<com.openggf.game.PlayableEntity> sidekicks() {
            throw new AssertionError("HCZ water wall should use ObjectPlayerQuery for engine sidekick participation");
        }
    }
}
