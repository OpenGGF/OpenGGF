package com.openggf.game.sonic3k.competition;

import com.openggf.game.GameServices;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character.*;
import static org.junit.jupiter.api.Assertions.*;

/** The miniature-to-main pose pairing, read from both ROM animation tables. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kArenaPoseMap {
    private static S3kArenaPoseMap poses() throws Exception {
        var rom = GameServices.rom().getRom();
        TestEnvironment.configureRomFixture(rom);
        var resources = S3kCompetitionResources.identify(rom);
        return new S3kArenaFullSizeArt(rom, resources, S3kMiniatureAnimation.Bank.fromRom(rom, resources),
                new S3kCompetitionAssets(rom, resources)).poses();
    }

    @Test void walkAndRunKeepTheirAngleGroup() throws Exception {
        var poses = poses();
        for (var character : S3kCompetitionPlayer.Character.values()) {
            for (int group = 0; group < 4; group++) {
                // AniSonic2P00 $1,$2,$3 + 3g against AniSonic00 $7,$1,$3 + 8g (steps 0, 2, 4).
                assertEquals(7 + 8 * group, poses.frame(character, 1 + 3 * group), character + " walk " + group);
                assertEquals(1 + 8 * group, poses.frame(character, 2 + 3 * group), character + " walk " + group);
                assertEquals(3 + 8 * group, poses.frame(character, 3 + 3 * group), character + " walk " + group);
                // AniSonic2P01 $D,$E + 2g against AniSonic01 $21,$23 + 4g.
                assertEquals(0x21 + 4 * group, poses.frame(character, 0xD + 2 * group), character + " run " + group);
                assertEquals(0x23 + 4 * group, poses.frame(character, 0xE + 2 * group), character + " run " + group);
            }
        }
    }

    @Test void scriptedPosesPairWithTheSameAnimation() throws Exception {
        var poses = poses();
        // Roll: the repeated ball pairs with the main script's repeated ball.
        assertEquals(0x96, poses.frame(SONIC, 0x28));
        assertEquals(0x97, poses.frame(SONIC, 0x29));
        assertEquals(0x99, poses.frame(SONIC, 0x2A));
        assertEquals(0x9A, poses.frame(KNUCKLES, 0x28));
        assertEquals(0x96, poses.frame(TAILS, 0x28));
        assertEquals(0x98, poses.frame(TAILS, 0x2A));
        assertEquals(0x9D, poses.frame(SONIC, 0x22), "skid");
        assertEquals(0x8D, poses.frame(SONIC, 0x21), "hurt");
        assertEquals(0x8E, poses.frame(SONIC, 0x15), "spring keeps the shared upright frame");
        assertEquals(0xBA, poses.frame(SONIC, 0x1B), "standing");
        assertEquals(0xA0, poses.frame(TAILS, 0x2C), "Tails flight");
        assertEquals(0xD0, poses.frame(KNUCKLES, 0x27), "Knuckles push");
    }

    @Test void theLocalAnimationResolvesSharedFrames() throws Exception {
        var poses = poses();
        // The miniature table reuses its waiting script for LOOK_UP; the main one does not.
        assertEquals(0xC3, poses.frame(SONIC, 7, false, 0x1B));
        // Tumble: miniature $15+f divides by $2C, main $31+2f by $16.
        assertEquals(0x31, poses.frame(SONIC, 0, true, 0x15));
        assertEquals(0x37, poses.frame(SONIC, 0, true, 0x18));
        assertEquals(0x37, poses.frame(SONIC, 0x18));
        assertEquals(0x8E, poses.frame(SONIC, 0x10, false, 0x15));
    }

    @Test void everyScriptedMiniatureFrameHasAPose() throws Exception {
        var poses = poses();
        // The 2P maps keep Knuckles' third push frame ($27) in their shared layout; only
        // AniKnuckles2P04 reaches it, so Sonic and Tails fall back to standing there.
        assertEquals("[39]", java.util.Arrays.toString(poses.unmappedFrames(SONIC)));
        assertEquals("[39]", java.util.Arrays.toString(poses.unmappedFrames(TAILS)));
        // Map_Knuckles2P frames $2C-$31 belong to no AniKnuckles2P script and no Competition routine.
        assertEquals("[44, 45, 46, 47, 48, 49]", java.util.Arrays.toString(poses.unmappedFrames(KNUCKLES)));
    }
}
