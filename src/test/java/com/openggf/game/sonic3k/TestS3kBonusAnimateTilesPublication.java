package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.Pattern;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AnimateTiles_Gumball and AnimateTiles_Pachinko (sonic3k.asm:55271-55288, 55291-55390) write
 * their art with Add_To_DMA_Queue, which VInt's Process_DMA_Queue drains before the next
 * displayed frame, like the AniPLC scripts. Found by the 2026-10-15 native bonus spot stills:
 * the engine showed each Pachinko background step one frame early.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBonusAnimateTilesPublication {
    private static final int GUMBALL_DEST_TILE = 0x54;
    private static final int PACHINKO_DEST_TILE = 0x0E9;

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void gumballSelectsArtFromTheBackgroundEventOffsetAndPublishesAtVBlank() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_GUMBALL, 0).build();
        Sonic3kPatternAnimator animator = animator();
        byte[] art = RomByteReader.fromRom(GameServices.rom().getRom())
                .slice(Sonic3kConstants.GUMBALL_ANI_TILES_ADDR, 0x100);
        for (int cameraY : new int[] {0x89, 0x8A, 0x8E, 0xC0, 0x45}) {
            GameServices.camera().setY((short) cameraY);
            byte[] before = tiles(GUMBALL_DEST_TILE, 4);
            animator.update();
            assertArrayEquals(before, tiles(GUMBALL_DEST_TILE, 4), "queued, not yet DMA'd, camera " + cameraY);
            animator.publishAniPlcAtVBlank();
            // Gumball_Deform (S3 Screen Events.asm:1700-1707): BG Y = camY asr 1, Events_bg+$10 = asr 2.
            int bgY = (short) cameraY >> 1;
            int index = ((bgY >> 1) - bgY) & 0x1F;
            assertArrayEquals(Arrays.copyOfRange(art, index * 4, index * 4 + 0x80), tiles(GUMBALL_DEST_TILE, 4),
                    "ArtUnc_AniGumball + index*4 for camera " + cameraY);
        }
    }

    @Test
    void pachinkoBackgroundStepIsPublishedByTheFollowingVBlank() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_GLOWING_SPHERE, 0).build();
        Sonic3kPatternAnimator animator = animator();
        boolean sawStep = false;
        for (int iteration = 0; iteration < 12; iteration++) {
            byte[] before = tiles(PACHINKO_DEST_TILE, 0x780 / 32);
            animator.update();
            assertArrayEquals(before, tiles(PACHINKO_DEST_TILE, 0x780 / 32),
                    "loc_28610's Add_To_DMA_Queue is not visible before VInt, iteration " + iteration);
            animator.publishAniPlcAtVBlank();
            sawStep |= !Arrays.equals(before, tiles(PACHINKO_DEST_TILE, 0x780 / 32));
        }
        assertTrue(sawStep, "the fourth AnimateTiles_Pachinko pass submitted the background art");
    }

    @Test
    void gumballArtFollowsTheCameraThroughTheIndexWrapAndRewind() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_GUMBALL, 0).build();
        Sonic3kPatternAnimator animator = animator();
        byte[] art = RomByteReader.fromRom(GameServices.rom().getRom())
                .slice(Sonic3kConstants.GUMBALL_ANI_TILES_ADDR, 0x100);
        GameServices.camera().setY((short) 0xC0);
        animator.update();
        animator.publishAniPlcAtVBlank();
        var initial = animator.capture();
        byte[] initialTiles = tiles(GUMBALL_DEST_TILE, 4);
        assertArrayEquals(gumballArt(art, 0xC0), initialTiles);
        for (int cameraY = 0xBD; cameraY >= 0; cameraY -= 3) {
            GameServices.camera().setY((short) cameraY);
            animator.update();
            animator.publishAniPlcAtVBlank();
            assertArrayEquals(gumballArt(art, cameraY), tiles(GUMBALL_DEST_TILE, 4), "camera " + cameraY);
        }
        animator.restore(initial);
        assertArrayEquals(initialTiles, tiles(GUMBALL_DEST_TILE, 4), "restore presents the captured VRAM");
        GameServices.camera().setY((short) 0x45);
        animator.update();
        animator.publishAniPlcAtVBlank();
        assertArrayEquals(gumballArt(art, 0x45), tiles(GUMBALL_DEST_TILE, 4), "after restore");
    }

    /** Gumball_Deform BG Y = camY asr 1, Events_bg+$10 = asr 2; ArtUnc_AniGumball + index*4. */
    private static byte[] gumballArt(byte[] art, int cameraY) {
        int bgY = (short) cameraY >> 1;
        int index = ((bgY >> 1) - bgY) & 0x1F;
        return Arrays.copyOfRange(art, index * 4, index * 4 + 0x80);
    }

    @Test
    void pachinkoRewindMidCompositionReplaysTheSameBackgroundArt() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_GLOWING_SPHERE, 0).build();
        Sonic3kPatternAnimator animator = animator();
        // AnimateTiles_Pachinko builds the background in a RAM buffer over four passes (rebuild,
        // then three blends) before loc_28610 queues it; the buffer is state like any other RAM.
        for (int start = 1; start <= 3; start++) {
            for (int i = 0; i < 4 + start; i++) {
                animator.update();
                animator.publishAniPlcAtVBlank();
            }
            var snapshot = animator.capture();
            byte[][] forward = new byte[8][];
            for (int i = 0; i < forward.length; i++) {
                animator.update();
                animator.publishAniPlcAtVBlank();
                forward[i] = tiles(PACHINKO_DEST_TILE, 0x780 / 32);
            }
            animator.restore(snapshot);
            for (int i = 0; i < forward.length; i++) {
                animator.update();
                animator.publishAniPlcAtVBlank();
                assertArrayEquals(forward[i], tiles(PACHINKO_DEST_TILE, 0x780 / 32),
                        "replayed pass " + i + " after a restore " + start + " passes into the cycle");
            }
        }
    }

    private static Sonic3kPatternAnimator animator() throws Exception {
        var manager = GameServices.level().getAnimatedPatternManager();
        assertTrue(manager instanceof Sonic3kLevelAnimationManager, "S3K level animation manager");
        var field = Sonic3kLevelAnimationManager.class.getDeclaredField("patternAnimator");
        field.setAccessible(true);
        return (Sonic3kPatternAnimator) field.get(manager);
    }

    private static byte[] tiles(int first, int count) {
        var level = GameServices.level().getCurrentLevel();
        byte[] packed = new byte[count * 32];
        for (int t = 0; t < count; t++) {
            Pattern pattern = level.getPattern(first + t);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x += 2) {
                    packed[t * 32 + y * 4 + x / 2] = (byte) ((pattern.getPixel(x, y) & 15) << 4
                            | pattern.getPixel(x + 1, y) & 15);
                }
            }
        }
        assertFalse(packed.length == 0);
        return packed;
    }
}
