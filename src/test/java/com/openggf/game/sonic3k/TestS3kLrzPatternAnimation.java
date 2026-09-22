package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AniPlcScriptState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code Offs_AniFunc} holds a {@code (AnimateTiles, AniPLC)} pair per act
 * (sonic3k.asm:53842-53881). Counting pairs from the table head, entry 18 is Lava Reef act 1 -
 * {@code AnimateTiles_LRZ1} with {@code AniPLC_LRZ1} - and entry 19 act 2, which pairs
 * {@code AnimateTiles_LRZ2} with {@code AniPLC_LRZ2}. The animator gave both acts
 * {@code AniPLC_LRZ1}.
 *
 * <p>The two lists are unambiguous: {@code AniPLC_LRZ1} (sonic3k.asm:56007) declares two scripts of
 * four VRAM tiles each at {@code $354} and {@code $350}, and {@code AniPLC_LRZ2} (56022) one of six
 * tiles at {@code $358} and one of eight at {@code $350}
 * ({@code zoneanimdecl duration,artaddr,vramaddr,numentries,numvramtiles}).
 *
 * <p>Both acts additionally run the custom {@code AnimateTiles_LRZ1}/{@code AnimateTiles_LRZ2}
 * split-DMA channels before the AniPLC pass. {@code loc_282D0} (sonic3k.asm:55055-55095) uploads
 * one $480-byte frame of {@code ArtUnc_AniLRZ__BG} rotated by {@code (phase & $38) * $C0} bytes,
 * split into the two transfer lengths {@code word_2834C} (55107-55119) holds for that band;
 * {@code loc_28364} (55121-55167) does the same over $180-byte frames of
 * {@code ArtUnc_AniLRZ__BG2} with {@code word_283D2} (55177-55184). The expectations below are
 * those ROM tables and the ROM art bytes, read independently of the engine.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzPatternAnimation {

    @Test void bossActRunsOnlyChannelZeroAtItsOwnTileAddress() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(22, 0).build();
        var animator = animatorOrThrow();
        assertEquals(List.of(), animator.scriptsForTesting());
        assertFalse(animator.shouldRunLrzBackgroundLayer2Channel());
        assertEquals(0x170, animator.lrzBackgroundLayer1Destination());
        byte[] otherTiles = tileBytes(GameServices.level().getCurrentLevel(), 0x320, 0x30);
        assertSplitChannel(48, 0x480, 0xC0, WORD_2834C, ART_LRZ_BG, 0x170, true);
        assertArrayEquals(otherTiles, tileBytes(GameServices.level().getCurrentLevel(), 0x320, 0x30));
    }

    @Test
    void act1LoadsAniPlcLrz1() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();

        List<AniPlcScriptState> scripts = animator().scriptsForTesting();

        assertEquals(List.of(4, 4), scripts.stream().map(AniPlcScriptState::tilesPerFrame).toList());
        assertEquals(List.of(0x354, 0x350),
                scripts.stream().map(AniPlcScriptState::destinationTileIndex).toList());
    }

    @Test
    void act2LoadsAniPlcLrz2() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 1).build();

        List<AniPlcScriptState> scripts = animator().scriptsForTesting();

        assertEquals(List.of(6, 8), scripts.stream().map(AniPlcScriptState::tilesPerFrame).toList());
        assertEquals(List.of(0x358, 0x350),
                scripts.stream().map(AniPlcScriptState::destinationTileIndex).toList());
    }

    /**
     * {@code loc_282D0} splits one frame at {@code band * $C0} bytes; {@code word_2834C}
     * (sonic3k.asm:55107-55119) holds the resulting (first, second) word counts per band.
     */
    private static final int[] WORD_2834C = {
            0x240, 0x000, 0x1E0, 0x060, 0x180, 0x0C0, 0x120, 0x120, 0x0C0, 0x180, 0x060, 0x1E0};
    /** {@code word_283D2} (sonic3k.asm:55177-55184), the same split at {@code band * $60}. */
    private static final int[] WORD_283D2 = {0x0C0, 0x000, 0x090, 0x030, 0x060, 0x060, 0x030, 0x090};
    /** {@code ArtUnc_AniLRZ__BG} / {@code ArtUnc_AniLRZ__BG2} (sonic3k.lst). */
    private static final int ART_LRZ_BG = 0x0C0300;
    private static final int ART_LRZ_BG2 = 0x0C2700;

    /**
     * {@code Animate_Init} writes {@code -1} to {@code Anim_Counters+1} and {@code +3} for
     * {@code $900} (sonic3k.asm:56411-56414) and {@code $1600} (56458-56461). {@code $901} is not
     * in that list, so it starts from the cleared counters.
     */
    @Test
    void act1SeedsBothAnimationCountersToMinusOneAndAct2DoesNot() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        assertEquals(0xFF, counter("lastLrzBg1Phase"));
        assertEquals(0xFF, counter("lastLrzBg2Phase"));

        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 1).build();
        assertEquals(0, counter("lastLrzBg1Phase"));
        assertEquals(0, counter("lastLrzBg2Phase"));
    }

    /**
     * {@code AnimateTiles_LRZ1}/{@code LRZ2} run {@code loc_282D0} and {@code loc_28364} and only
     * then branch to {@code loc_286E8}, the shared AniPLC pass.
     */
    @Test
    void bothCustomChannelsRunBeforeTheAniPlcScripts() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();

        List<String> ids = GameServices.animatedTileChannelGraph().channels().stream()
                .map(com.openggf.game.animation.AnimatedTileChannel::channelId)
                .toList();

        assertEquals(List.of("s3k.lrz.bg1", "s3k.lrz.bg2", "s3k.lrz.script.0", "s3k.lrz.script.1"), ids);
    }

    /**
     * The phase is an unsigned remainder of a 16-bit difference: {@code moveq #0,d0} zero-extends
     * before {@code sub.w}/{@code subq.w}, so a zero difference becomes {@code $FFFF} and
     * {@code $FFFF / $30} leaves 15, not the 47 a signed modulo would give.
     */
    @Test
    void channel0PhaseUsesTheUnsignedWrapOfTheWordDifference() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        Sonic3kPatternAnimator animator = animatorOrThrow();
        LrzZoneRuntimeState state = lrzState();

        state.publishDeformationWords(0, 0, 0, 0);
        assertEquals(15, animator.computeLrzBackgroundLayer1Phase());

        state.publishDeformationWords(0x40, 0, 0, 0x30);
        assertEquals(0xFFEF % 0x30, animator.computeLrzBackgroundLayer1Phase());

        state.publishDeformationWords(0x10, 0, 0, 0x41);
        assertEquals(0x30 % 0x30, animator.computeLrzBackgroundLayer1Phase());
    }

    /** {@code loc_28364}: {@code (Events_bg+$10 - Camera_X_pos_BG_copy) & $1F}, no {@code -1}. */
    @Test
    void channel1PhaseIsTheMaskedDifference() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        Sonic3kPatternAnimator animator = animatorOrThrow();
        LrzZoneRuntimeState state = lrzState();

        state.publishDeformationWords(0x100, 0, 0x105, 0);
        assertEquals(5, animator.computeLrzBackgroundLayer2Phase());

        state.publishDeformationWords(0x100, 0, 0x0FF, 0);
        assertEquals(0x1F, animator.computeLrzBackgroundLayer2Phase());
    }

    @Test
    void channel0UploadsTheRotatedFrameForEveryPhase() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        assertSplitChannel(48, 0x480, 0xC0, WORD_2834C, ART_LRZ_BG, 0x320, true);
    }

    @Test
    void channel1UploadsTheRotatedFrameForEveryPhase() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        assertSplitChannel(32, 0x180, 0x60, WORD_283D2, ART_LRZ_BG2, 0x344, false);
    }

    /**
     * {@code cmp.b 1(a3),d0 / beq} skips the upload when the phase equals the stored counter, so a
     * direct or star-post {@code $901} load whose first phase is 0 leaves the level art in place.
     */
    @Test
    void act2ClearedCounterSkipsAPhaseZeroFirstUpload() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 1).build();
        Level level = GameServices.level().getCurrentLevel();
        Sonic3kPatternAnimator animator = animatorOrThrow();
        byte[] before = tileBytes(level, 0x320, 0x24);

        lrzState().publishDeformationWords(0, 0, 0, 1);
        assertEquals(0, animator.computeLrzBackgroundLayer1Phase());
        animator.updateLrzBackgroundLayer1ForGraph();
        assertArrayEquals(before, tileBytes(level, 0x320, 0x24),
                "a cleared Anim_Counters+1 matches phase 0, so nothing is uploaded");

        lrzState().publishDeformationWords(0, 0, 0, 2);
        assertEquals(1, animator.computeLrzBackgroundLayer1Phase());
        animator.updateLrzBackgroundLayer1ForGraph();
        assertFalse(java.util.Arrays.equals(before, tileBytes(level, 0x320, 0x24)),
                "the next differing phase does upload");
    }

    /**
     * The production pass, not the channel methods directly: {@code Animate_Tiles} runs once a
     * frame from {@code LevelLoop}, and the phase it reads comes from whatever {@code LRZ1_Deform}
     * published that frame. Moving the camera has to change the destination art through
     * {@code update()} alone.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1})
    void productionUpdatePassAnimatesBothDestinationsAsTheCameraMoves(int act) {
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, act).build();
        Level level = GameServices.level().getCurrentLevel();
        Sonic3kPatternAnimator animator = animatorOrThrow();
        var handler = GameServices.parallax().getHandler(GameServices.level().getFeatureZoneId());
        assertNotNull(handler, "both acts must resolve SwScrlLrz for the deformation words");

        int[] lines = new int[224];
        boolean channel0Changed = false;
        boolean channel1Changed = false;
        byte[] previousChannel0 = null;
        byte[] previousChannel1 = null;

        for (int step = 0; step < 64; step++) {
            int cameraX = 0x0800 + step * 0x20;
            fixture.camera().setX((short) cameraX);
            fixture.camera().setXCopy((short) cameraX);
            fixture.camera().setYCopy((short) 0x0320);
            handler.update(lines, cameraX, 0x0320, step, act);
            animator.update();

            byte[] channel0 = tileBytes(level, 0x320, 0x24);
            byte[] channel1 = tileBytes(level, 0x344, 0x0C);
            if (previousChannel0 != null && !java.util.Arrays.equals(previousChannel0, channel0)) {
                channel0Changed = true;
            }
            if (previousChannel1 != null && !java.util.Arrays.equals(previousChannel1, channel1)) {
                channel1Changed = true;
            }
            previousChannel0 = channel0;
            previousChannel1 = channel1;
        }

        org.junit.jupiter.api.Assertions.assertTrue(channel0Changed,
                "loc_282D0 must reupload $320-$343 as the background phase moves, act " + (act + 1));
        org.junit.jupiter.api.Assertions.assertTrue(channel1Changed,
                "loc_28364 must reupload $344-$34F as the background phase moves, act " + (act + 1));
    }

    private static void assertSplitChannel(int phaseCount, int frameBytes, int bandBytes,
                                           int[] splitTable, int artAddress, int destTile,
                                           boolean channel0) throws java.io.IOException {
        Level level = GameServices.level().getCurrentLevel();
        Sonic3kPatternAnimator animator = animatorOrThrow();
        LrzZoneRuntimeState state = lrzState();
        var rom = GameServices.rom().getRom();

        for (int phase = 0; phase < phaseCount; phase++) {
            if (channel0) {
                state.publishDeformationWords(0, 0, 0, phase + 1);
                assertEquals(phase, animator.computeLrzBackgroundLayer1Phase());
                animator.updateLrzBackgroundLayer1ForGraph();
            } else {
                state.publishDeformationWords(0, 0, phase, 0);
                assertEquals(phase, animator.computeLrzBackgroundLayer2Phase());
                animator.updateLrzBackgroundLayer2ForGraph();
            }

            int frameOffset = (phase & 7) * frameBytes;
            int band = phase & (channel0 ? 0x38 : 0x18);
            int bandIndex = band >> 3;
            int firstBytes = splitTable[bandIndex * 2] * 2;
            int secondBytes = splitTable[bandIndex * 2 + 1] * 2;
            assertEquals(frameBytes, firstBytes + secondBytes, "phase=" + phase);

            byte[] expected = new byte[frameBytes];
            byte[] first = rom.readBytes(artAddress + frameOffset + bandIndex * bandBytes, firstBytes);
            System.arraycopy(first, 0, expected, 0, firstBytes);
            if (secondBytes > 0) {
                byte[] second = rom.readBytes(artAddress + frameOffset, secondBytes);
                System.arraycopy(second, 0, expected, firstBytes, secondBytes);
            }

            byte[] actual = tileBytes(level, destTile, frameBytes / Pattern.PATTERN_SIZE_IN_ROM);
            for (int pixel = 0; pixel < frameBytes * 2; pixel++) {
                int packed = expected[pixel / 2] & 0xFF;
                int nibble = (pixel % 2 == 0 ? packed >> 4 : packed) & 0xF;
                assertEquals(nibble, actual[pixel], "phase=" + phase + " pixel=" + pixel);
            }
        }
    }

    /** Pixel nibbles of {@code tileCount} level patterns from {@code firstTile}, in ROM order. */
    private static byte[] tileBytes(Level level, int firstTile, int tileCount) {
        byte[] data = new byte[tileCount * Pattern.PATTERN_SIZE_IN_MEM];
        int index = 0;
        for (int tile = 0; tile < tileCount; tile++) {
            Pattern pattern = level.getPattern(firstTile + tile);
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    data[index++] = pattern.getPixel(x, y);
                }
            }
        }
        return data;
    }

    private static LrzZoneRuntimeState lrzState() {
        return com.openggf.game.sonic3k.runtime.S3kRuntimeStates
                .currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    private static int counter(String fieldName) throws ReflectiveOperationException {
        Field field = Sonic3kPatternAnimator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(animatorOrThrow());
    }

    private static Sonic3kPatternAnimator animatorOrThrow() {
        try {
            return animator();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Sonic3kPatternAnimator animator() throws ReflectiveOperationException {
        var manager = GameServices.level().getAnimatedPatternManager();
        if (manager instanceof Sonic3kPatternAnimator animator) {
            return animator;
        }
        Field field = Sonic3kLevelAnimationManager.class.getDeclaredField("patternAnimator");
        field.setAccessible(true);
        return (Sonic3kPatternAnimator) field.get(manager);
    }
}
