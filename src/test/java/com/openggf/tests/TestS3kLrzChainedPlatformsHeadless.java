package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.LrzChainedPlatformObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Three real ROM group placements; positioned entry, not cold-route evidence. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzChainedPlatformsHeadless {
    @AfterEach void reset() {
        CrossGameFeatureProvider.getInstance().resetState();
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
    }

    @ParameterizedTest
    @CsvSource({"5568,2704,4", "6592,1424,4", "7488,2192,8"})
    void placedGroupAllocatesItsPartsAndRestoresTheirIndependentPhases(int x, int y, int count) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1)
                .startPosition((short)x, (short)(y - 60)).startPositionIsCentre().build();
        fixture.sprite().setRingCount(99);
        for (int i = 0; i < 12; i++) fixture.stepFrame(false, false, false, false, false);
        assertEquals(count, parts().size());
        assertEquals(count, parts().stream().map(p -> p.getX() + ":" + p.getY()).distinct().count());
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepFrame(false, false, false, false, false);
        CompositeSnapshot after = registry.capture();
        // Remove dynamic parts, retaining the placed root: restore must not duplicate the group.
        parts().stream().filter(p -> p.getSpawn().subtype() < 0x80).forEach(p -> p.setDestroyed(true));
        fixture.stepFrame(false, false, false, false, false);
        registry.restore(before);
        assertEquals(count, parts().size());
        assertState(before, registry.capture());
        fixture.stepFrame(false, false, false, false, false);
        assertState(after, registry.capture());
    }

    @ParameterizedTest(name = "contact {0}px {1} {2}")
    @CsvSource({"320,off,sonic", "352,off,sonic", "400,off,sonic", "528,off,sonic", "800,off,sonic",
            "320,s1,sonic", "352,s1,sonic", "400,s1,sonic", "528,s1,sonic", "800,s1,sonic",
            "320,s2,sonic", "352,s2,sonic", "400,s2,sonic", "528,s2,sonic", "800,s2,sonic",
            "320,off,tails", "320,off,knuckles"})
    void topCarriesThePlayerButTheUndersideHurts(int width, String donor, String character) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var aspect = java.util.Arrays.stream(WidescreenAspect.values())
                .filter(a -> a.pixelWidth() == width).findFirst().orElseThrow();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name());
        config.resolveDisplayAspect();
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1)
                .startPosition((short)0x15C0, (short)0xA50).startPositionIsCentre()
                .withCrossGameDonation(donor.equals("off") ? null : donor).build();
        assertEquals(width, GameServices.camera().getWidth() & 0xFFFF);
        assertEquals(character, fixture.sprite().getCode());
        assertEquals(!donor.equals("off"), CrossGameFeatureProvider.isActive());
        for (int i = 0; i < 8; i++) fixture.stepFrame(false, false, false, false, false);
        var platform = parts().stream().filter(p -> p.getSpawn().subtype() == 0x80).findFirst().orElseThrow();
        var player = fixture.sprite();
        player.setRingCount(99);
        player.setCentreX((short)platform.getX());
        player.setCentreY((short)(platform.getY() - 0x18 - player.getYRadius() - 1));
        player.setAir(true);
        player.setXSpeed((short)0);
        player.setYSpeed((short)0x200);
        fixture.stepFrame(false, false, false, false, false);
        assertFalse(player.isHurt(), "top landing is safe");
        assertTrue(GameServices.level().getObjectManager().hasObjectStandingBit(player, platform));
        int relativeX = player.getCentreX() - platform.getX();
        for (int i = 0; i < 8; i++) fixture.stepFrame(false, false, false, false, false);
        assertEquals(relativeX, player.getCentreX() - platform.getX(), "the platform carries its rider");
        player.setCentreX((short)platform.getX());
        player.setCentreY((short)(platform.getY() + 0x18 + player.getYRadius() - 1));
        player.setAir(true);
        player.setXSpeed((short)0);
        player.setYSpeed((short)-0x200);
        // One checkpoint consumes a stale riding bit before fresh contact, as in SolidObjectFull_1P.
        fixture.stepFrame(false, false, false, false, false);
        fixture.stepFrame(false, false, false, false, false);
        assertTrue(player.isHurt(), "high d6 bits 18/19 are the spiked underside");
    }

    private static List<LrzChainedPlatformObjectInstance> parts() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(LrzChainedPlatformObjectInstance.class::isInstance)
                .map(LrzChainedPlatformObjectInstance.class::cast).toList();
    }
    private static void assertState(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> key + ": " + diff);
        }
    }
}
