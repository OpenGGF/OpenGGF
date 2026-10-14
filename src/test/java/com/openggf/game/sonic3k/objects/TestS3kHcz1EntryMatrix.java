package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.tests.SozAcceptanceConfigurations;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHcz1EntryMatrix {
    static Stream<Arguments> configurations() {
        return Stream.of(WidescreenAspect.values())
                .flatMap(aspect -> Stream.of("off", "s1", "s2").map(donor -> Arguments.of(aspect, donor)));
    }

    static Stream<Arguments> legacyConfigurations() {
        return Stream.of(512, 640).flatMap(width -> Stream.of("off", "s1", "s2")
                .map(donor -> Arguments.of(width, donor)));
    }

    @ParameterizedTest(name = "HCZ1 legacy custom geometry {0}px donor={1}")
    @MethodSource("legacyConfigurations")
    void legacyCustomWidthsRetainEntryReplayAndRepeatedResetGeometry(int width, String donor) throws Exception {
        Hcz1Route.withConfiguration(width, donor, route -> {
            int startX = route.fixture.sprite().getCentreX();
            while (route.fixture.sprite().getCentreX() <= startX + 64) {
                assertTrue(route.frames < 900, "legacy entry did not admit rightward movement");
                route.step();
            }
            assertEquals(width, route.fixture.camera().getWidth());
            TestS3kHcz1RouteRewind.replayTwice(route, 30, "LEGACY ENTRY " + width + "/" + donor);
            verifyRepeatedLoads(route, width, donor, null);
        });
    }

    @ParameterizedTest(name = "HCZ1 entry/replay width={0} donor={1}")
    @MethodSource("configurations")
    void entryAdmitsMovementAndReplays(WidescreenAspect aspect, String donor) throws Exception {
        Hcz1Route.withConfiguration(aspect, donor, route -> {
            int width = aspect.pixelWidth();
            int startX = route.fixture.sprite().getCentreX();
            while (route.fixture.sprite().getCentreX() <= startX + 64) {
                assertTrue(route.frames < 900, "entry did not admit rightward movement");
                route.step();
            }
            assertEquals(width, route.fixture.camera().getWidth());
            assertEquals(1, GameServices.sprites().getSidekicks().size());
            assertFalse(route.fixture.sprite().isObjectControlled());
            TestS3kHcz1RouteRewind.replayTwice(route, 30, "ENTRY " + width + "/" + donor,
                    () -> assertProductionOwners(aspect, donor));
            assertProductionOwners(aspect, donor);
        });
    }

    @ParameterizedTest(name = "HCZ1 repeated production reset width={0} donor={1}")
    @MethodSource("configurations")
    void repeatedLoadsReplaceOwnersAndRetainPlayableConfiguration(WidescreenAspect aspect, String donor) throws Exception {
        Hcz1Route.withConfiguration(aspect, donor, route -> {
            int width = aspect.pixelWidth();
            verifyRepeatedLoads(route, width, donor, aspect);
        });
    }

    private static void assertProductionOwners(WidescreenAspect aspect, String donor) {
        SozAcceptanceConfigurations.assertDisplay(aspect);
        SozAcceptanceConfigurations.assertUsableTeam(donor);
        var main = GameServices.sprites().getMainPlayable();
        assertSame(main, GameServices.camera().getFocusedSprite(), "current main owns camera focus");
        var followers = GameServices.sprites().getRegisteredSidekicks();
        assertEquals(1, followers.size());
        assertTrue(followers.getFirst().isCpuControlled());
        assertSame(main, followers.getFirst().getCpuController().getLeader());
    }

    private static void verifyRepeatedLoads(Hcz1Route route, int width, String donor,
                                            WidescreenAspect aspect) throws Exception {
        // Reach non-default water/player/camera state through the production opening.
        while (route.frames < 600) route.step();
        for (int cycle = 0; cycle < 2; cycle++) {
            var manager = GameServices.level();
            var oldObjects = manager.getObjectManager();
            manager.resetState();
            manager.loadZoneAndAct(1, 0);
            assertNotSame(oldObjects, manager.getObjectManager());
            assertEquals(1, manager.getCurrentZone());
            assertEquals(0, manager.getCurrentAct());
            var player = GameServices.sprites().getMainPlayable();
            int[] start = GameServices.module().getZoneRegistry().getStartPosition(1, 0);
            assertEquals(start[0], player.getCentreX() & 0xffff);
            assertEquals(start[1], player.getCentreY() & 0xffff);
            assertFalse(player.getDead() || player.isDrowningDeath());
            assertFalse(player.isOnObject());
            assertFalse(player.isObjectControlled());
            assertEquals(width, GameServices.camera().getWidth());
            if (aspect != null) {
                assertProductionOwners(aspect, donor);
            }
            assertEquals(1, GameServices.sprites().getSidekicks().size());
            var follower = GameServices.sprites().getSidekicks().getFirst();
            assertTrue(follower.isCpuControlled());
            assertSame(player, follower.getCpuController().getLeader());
            assertEquals(!donor.equals("s1"), player.getGameRules().playerCapability().spindashEnabled());
            assertEquals(!donor.equals("off"), com.openggf.game.CrossGameFeatureProvider.isActive());
            if (!donor.equals("off")) {
                assertEquals(donor, com.openggf.game.CrossGameFeatureProvider.getInstance().getDonorGameId());
            }
            route.fixture.stepIdleFrames(30);
            assertNotEquals(start[1], player.getCentreY() & 0xffff,
                    "the new HCZ entry must execute its falling movement");
            assertFalse(player.getDead() || player.isDrowningDeath());
        }
    }
}
