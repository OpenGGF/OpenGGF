package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.Arrays;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** SOZ acceptance uses the production launch roster, not raw debug configuration strings. */
public final class SozAcceptanceConfigurations {
    private SozAcceptanceConfigurations() { }

    public static boolean supportsCharacter(String donor, String character) {
        return profile(donor, character, "none").sanitizedFor(MasterTitleScreen.GameEntry.SONIC_3K)
                .mainCharacter().equals(character);
    }

    /** Keep the requested participant count; unsupported donor characters become Sonic duplicates. */
    public static String supportedFollowers(String donor, String requested) {
        if (requested.isEmpty()) return "";
        return Arrays.stream(requested.split(","))
                .map(character -> supportsCharacter(donor, character) ? character : "sonic")
                .collect(Collectors.joining(","));
    }

    public static void assertUsableTeam(String donor) {
        assertUsable(donor, GameServices.sprites().getMainPlayable(), false);
        for (var follower : GameServices.sprites().getRegisteredSidekicks()) {
            assertUsable(donor, follower, true);
        }
    }

    private static void assertUsable(String donor, AbstractPlayableSprite player, boolean follower) {
        String character = player.characterKey().persisted();
        var requested = profile(donor, follower ? "sonic" : character, follower ? character : "none");
        assertEquals(requested, requested.sanitizedFor(MasterTitleScreen.GameEntry.SONIC_3K),
                "the production launch must support this participant: " + donor + "/" + character);
        assertNotNull(player.getSpriteRenderer(), "ROM-backed playable renderer: " + character);
        assertNotNull(player.getAnimationProfile(), "playable animation profile: " + character);
        assertNotNull(player.getAnimationSet(), "ROM-backed animation scripts: " + character);
        assertTrue(player.getAnimationFrameCount() > 0, "decoded playable mappings: " + character);
    }

    private static LaunchProfile profile(String donor, String main, String follower) {
        return new LaunchProfile(false, donor, false, "global", main, follower);
    }
}
