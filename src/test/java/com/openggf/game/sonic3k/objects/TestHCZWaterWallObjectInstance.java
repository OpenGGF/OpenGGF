package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
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
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestHCZWaterWallObjectInstance {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
    }

    @AfterEach
    void resetSessionTimingFixture() {
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

        int pullUpdates = 1;
        int frame = 1;
        while (timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE) > 0
                && frame < 10_000) {
            serviceBoundary(timing, coordinator, HardwareServiceBoundary.PRE_MAIN_LOOP);
            int incompleteBeforeObject = timing.incompleteCount(
                    HardwareWorkKind.KOS_MODULE_QUEUE);
            waterWall.update(frame, player);
            pullUpdates++;
            assertEquals(playerInitialY - (pullUpdates * 8), player.getCentreY(),
                    "loc_302E6 should pull the player while Kos_modules_left is nonzero");
            assertEquals(wallInitialY, waterWall.getY(),
                    "vertical geyser must not enter loc_30338 while queued art is pending");
            assertEquals(incompleteBeforeObject,
                    timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE),
                    "object polling must not publish hardware readiness");
            serviceBoundary(timing, coordinator, HardwareServiceBoundary.POST_OBJECTS);
            frame++;
        }

        assertEquals(0, timing.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE),
                "the final module must become ready at POST_OBJECTS");
        assertEquals(wallInitialY, waterWall.getY(),
                "POST_OBJECTS publishes readiness but does not run the object consumer");

        serviceBoundary(timing, coordinator, HardwareServiceBoundary.PRE_MAIN_LOOP);
        waterWall.update(frame, player);
        serviceBoundary(timing, coordinator, HardwareServiceBoundary.POST_OBJECTS);

        assertEquals(playerInitialY - ((pullUpdates + 1) * 8), player.getCentreY(),
                "loc_302FA falls through into the first loc_30338 rise tick when the queue clears");
        assertEquals(wallInitialY - 8, waterWall.getY(),
                "the first rise tick should happen in the same update that finishes queued-art setup");
        assertEquals(Sonic3kAnimationIds.BLANK.id(), player.getAnimationId());
        assertEquals(-1, player.getForcedAnimationId(),
                "water-wall object control must supersede the prior tunnel override");
        assertEquals(Sonic3kAnimationIds.BLANK.id(), sidekick.getAnimationId());
        assertEquals(-1, sidekick.getForcedAnimationId());
        assertEquals(4, inheritedSessionTiming.incompleteCount(HardwareWorkKind.KOS_MODULE_QUEUE),
                "the fixture must not consume unrelated work inherited from another test session");
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
        timing.service(boundary);
        coordinator.afterTimingService(boundary);
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
