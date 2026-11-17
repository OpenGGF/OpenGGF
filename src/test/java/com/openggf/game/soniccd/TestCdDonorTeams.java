package com.openggf.game.soniccd;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.*;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Every cartridge roster pair on the disc host, including duplicate characters. */
class TestCdDonorTeams {
    static Stream<Arguments> teams() {
        return Stream.of("s1", "s2", "s3k").flatMap(donor -> {
            var characters = donor.equals("s1") ? java.util.List.of("sonic")
                    : donor.equals("s2") ? java.util.List.of("sonic", "tails")
                    : java.util.List.of("sonic", "tails", "knuckles");
            return characters.stream().flatMap(main -> characters.stream()
                    .map(side -> Arguments.of(donor, main, side)));
        });
    }

    @ParameterizedTest @MethodSource("teams")
    void rosterPairsRunWithOwnedArtAndResolvedRecoveryPoses(String donor, String main, String side) throws Exception {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, donor);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, side);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            var loop = boot.boot(disc, new SonicCdGameModule(), 0, 0);
            var follower = GameServices.sprites().getRegisteredSidekicks().getFirst();
            assertEquals(CharacterKey.parsePersisted(side), follower.characterKey());
            assertNotNull(follower.getSpriteRenderer());
            assertNotNull(follower.getCpuController());
            if (side.equals("tails")) {
                var field = follower.getCpuController().getClass().getDeclaredField("flyAnimId");
                field.setAccessible(true);
                int fly = follower.resolveAnimationId(CanonicalAnimation.TAILS_FLY);
                assertTrue(fly >= 0);
                assertEquals(fly, field.getInt(follower.getCpuController()), "pre-art CPU recovery pose");
                assertNotNull(follower.getAnimationSet().getScript(fly));
            }
            for (int i = 0; i < 240; i++) loop.step();
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertNotNull(follower.getAnimationSet().getScript(follower.getAnimationId()));
        } finally {
            config.clearSessionOverrides();
        }
    }

    static Stream<Arguments> refusedFollowers() {
        return Stream.of(Arguments.of("s1", "tails"), Arguments.of("s1", "knuckles"),
                Arguments.of("s2", "knuckles"));
    }

    @ParameterizedTest @MethodSource("refusedFollowers")
    void unavailableFollowersRefuseBeforeArtLoading(String donor, String side) throws Exception {
        var config = GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, donor);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, side);
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue());
             var boot = new HeadlessGameBoot(320, 224)) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> boot.boot(disc, new SonicCdGameModule(), 0, 0));
            assertTrue(error.getMessage().contains("Donor " + donor + " does not supply " + side));
            assertFalse(CrossGameFeatureProvider.isActive());
        } finally {
            config.clearSessionOverrides();
        }
    }
}
