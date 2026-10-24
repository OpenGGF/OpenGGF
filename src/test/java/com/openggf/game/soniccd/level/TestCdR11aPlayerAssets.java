package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.Arrays;

import static com.openggf.game.soniccd.level.CdR11aAsset.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed format fixtures only; native table identities are checked by TestCdR11aRetailAssets. */
class TestCdR11aPlayerAssets {
    @Test
    void scriptsRetainHeadersHighBitFramesCommandsPaddingAndNonmonotonicAliases() {
        Fixture f = new Fixture();
        f.script(120, 0xFF, 0x94, 0x95, 0xFF, 0xFF, 0, 0, 0);
        f.script(128, 0xFC, 0xA6, 0xA7, 0xFF);
        word(f.animations, 2, 128);
        word(f.animations, 58 * 2, 128);
        var data = f.decode();
        assertEquals(60, data.animations().size());
        assertEquals(120, data.animation(0).relativeOffset());
        assertEquals(8, data.animation(0).bytes().length());
        assertEquals((byte) 0xFF, data.animation(0).signedDelayOrHandler());
        assertEquals(0x94, data.animation(0).bytes().unsignedByte(1));
        assertEquals(0xFF, data.animation(0).bytes().unsignedByte(4));
        assertEquals((byte) 0xFC, data.animation(1).signedDelayOrHandler());
        assertEquals(128, data.animation(58).relativeOffset());
        assertArrayEquals(data.animation(1).bytes().copy(), data.animation(58).bytes().copy());
        assertEquals(120, data.animation(59).relativeOffset());
        assertSame(f.visuals, data.sonic());
        assertSame(f.visuals.mappingFrames(), data.sonic().mappingFrames());
        assertSame(f.visuals.dplcFrames(), data.sonic().dplcFrames());
        assertThrows(IndexOutOfBoundsException.class, () -> data.animation(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> data.animation(60));
    }

    @Test
    void exactScriptExtentSignedPointersAndNextDistinctBodyAreEnforced() {
        Fixture f = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> f.decodeAnimations(Arrays.copyOf(f.animations, 577)));
        assertThrows(IllegalArgumentException.class, () -> f.decodeAnimations(Arrays.copyOf(f.animations, 579)));
        for (int offset : new int[]{0, 118, 121, 578, 0x7FFE, 0xFFFE}) {
            Fixture bad = new Fixture();
            word(bad.animations, 0, offset);
            assertThrows(IllegalArgumentException.class, bad::decode);
        }
        Arrays.fill(f.animations, 0, 120, (byte) 0);
        for (int id = 0; id < 60; id++) { word(f.animations, id * 2, 122); }
        assertThrows(IllegalArgumentException.class, f::decode); // No body starts at the reviewed index end.
        Fixture crossed = new Fixture();
        crossed.script(120, 3, 1, 2, 3);
        crossed.script(124, 3, 1, 0xFF);
        word(crossed.animations, 2, 124);
        assertThrows(IllegalArgumentException.class, crossed::decode); // No command before the next body.
        Fixture truncated = new Fixture();
        word(truncated.animations, 2, 576);
        assertThrows(IllegalArgumentException.class, truncated::decode);
    }

    @Test
    void onlyReviewedHeadersAndBoundedFfFeFdCommandsAreAdmitted() {
        for (int header : new int[]{0, 0x7F, 0xFC, 0xFD, 0xFE, 0xFF}) {
            Fixture f = new Fixture();
            f.script(120, header, 1, 0xFF);
            assertEquals((byte) header, f.decode().animation(0).signedDelayOrHandler());
        }
        for (int header : new int[]{0x80, 0xF8, 0xFB}) {
            Fixture f = new Fixture();
            f.animations[120] = (byte) header;
            assertThrows(UnsupportedOperationException.class, f::decode);
        }
        for (int[] body : new int[][]{
                {3, 1, 2, 0xFE, 2}, {3, 1, 0xFD, 0}, {3, 1, 0xFD, 59}}) {
            Fixture f = new Fixture();
            f.script(120, body);
            assertArrayEquals(Arrays.copyOfRange(f.animations, 120, 120 + body.length),
                    f.decode().animation(0).bytes().slice(0, body.length).copy());
        }
        for (int[] body : new int[][]{
                {3, 1, 0xFE, 0}, {3, 1, 0xFE, 2}, {3, 1, 0xFE, 255},
                {3, 1, 0xFD, 60}, {3, 1, 0xFF, 1}, {3, 0xC6, 0xFF},
                {3, 0xFC, 0xFF}, {3, 0xFF}}) {
            Fixture f = new Fixture();
            f.script(120, body);
            assertThrows(IllegalArgumentException.class, f::decode);
        }
        for (int command : new int[]{0xFE, 0xFD}) {
            Fixture f = new Fixture();
            word(f.animations, 2, 574);
            f.script(574, 3, 1, 2, command);
            assertThrows(IllegalArgumentException.class, f::decode);
        }
        Fixture unterminated = new Fixture();
        Arrays.fill(unterminated.animations, 120, 578, (byte) 1);
        assertThrows(IllegalArgumentException.class, unterminated::decode);
    }

    @Test
    void nativeTableBoundsIncludeCosineExtensionAndArctangentEndpointWithoutMathOrClamps() {
        Fixture f = new Fixture();
        var data = f.decode();
        assertEquals(0x100, data.sineWord(64));
        assertEquals(-0x100, data.sineWord(192));
        assertEquals(data.sineWord(63), data.sineWord(319));
        assertEquals(0x20, data.arcTanByte(256));
        assertEquals(0xFF, data.rampChunks().unsignedByte(5));
        assertArrayEquals(new byte[]{0x7F, 0x7F, 0x7F, 0x7F}, data.specialChunks().copy());
        for (int index : new int[]{-1, 320, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> data.sineWord(index));
        }
        for (int index : new int[]{-1, 257, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> data.arcTanByte(index));
        }
        for (int index : new int[]{-1, 60}) {
            assertThrows(IndexOutOfBoundsException.class, () -> data.miniAnimationId(index));
        }
        word(f.sine, 0, 0x101);
        assertThrows(IllegalArgumentException.class, f::decode);
        Fixture extension = new Fixture();
        word(extension.sine, 256 * 2, 1);
        assertThrows(IllegalArgumentException.class, extension::decode);
        Fixture negative = new Fixture();
        word(negative.sine, 192 * 2, 0xFEFF);
        assertThrows(IllegalArgumentException.class, negative::decode);
        for (int[] mutation : new int[][]{{0, 1}, {256, 31}, {128, 33}, {128, 0}}) {
            Fixture bad = new Fixture();
            bad.atan[mutation[0]] = (byte) mutation[1];
            assertThrows(IllegalArgumentException.class, bad::decode);
        }
    }

    @Test
    void missingExtraOrMalformedTableInputsFailInsteadOfSupplyingDefaults() {
        for (int slot = 0; slot < 6; slot++) {
            for (int delta : new int[]{-1, 1}) {
                Fixture f = new Fixture();
                byte[][] inputs = f.inputs();
                inputs[slot] = Arrays.copyOf(inputs[slot], inputs[slot].length + delta);
                assertThrows(IllegalArgumentException.class, () -> decode(inputs, f.visuals));
            }
        }
        Fixture mini = new Fixture();
        mini.mini[59] = 60;
        assertThrows(IllegalArgumentException.class, mini::decode);
        for (int index = 0; index < 5; index++) {
            Fixture ramp = new Fixture();
            ramp.ramp[index] = (byte) 0xFF;
            assertThrows(IllegalArgumentException.class, ramp::decode);
        }
        Fixture sentinel = new Fixture();
        sentinel.ramp[5] = 0;
        assertThrows(IllegalArgumentException.class, sentinel::decode);
        assertThrows(NullPointerException.class, () -> decode(new Fixture().inputs(), null));
    }

    @Test
    void everyRetainedTableIsDetachedAndNestedViewsRemainImmutable() {
        Fixture f = new Fixture();
        var data = f.decode();
        CdAssetBytes[] views = {data.animationTable(), data.miniAnimations(), data.rampChunks(),
                data.specialChunks(), data.sineTable(), data.arcTanTable(), data.animation(0).bytes()};
        byte[][] expected = Arrays.stream(views).map(CdAssetBytes::copy).toArray(byte[][]::new);
        for (byte[] input : f.inputs()) { Arrays.fill(input, (byte) 0x55); }
        for (int i = 0; i < views.length; i++) {
            assertArrayEquals(expected[i], views[i].copy());
            byte[] copy = views[i].copy();
            Arrays.fill(copy, (byte) 0x66);
            assertArrayEquals(expected[i], views[i].copy());
            CdAssetBytes view = views[i];
            assertThrows(ReadOnlyBufferException.class, () -> view.buffer().put(0, (byte) 0));
            view.buffer().position(1);
            assertEquals(0, view.buffer().position());
        }
        assertThrows(UnsupportedOperationException.class, () -> data.animations().clear());
        assertEquals(59, data.miniAnimationId(59));
        assertSame(f.visuals, data.sonic());
    }

    @Test
    void reviewedRangesAreRegionalOriginalFileViewsWithNoUnusedRemapOrAlignmentTail() {
        for (CdReleaseVariant variant : CdReleaseVariant.values()) {
            var profile = CdR11aProfile.forStage(variant, CdStageKey.parse("R11A"));
            boolean usa = variant == CdReleaseVariant.USA;
            assertEquals(usa ? 0x594A : 0x5958, profile.range(SONIC_MINI_ANIMATIONS).fileOffset());
            assertEquals(60, profile.range(SONIC_MINI_ANIMATIONS).length());
            assertEquals(usa ? 0x4710 : 0x470C, profile.range(SONIC_RAMP_CHUNKS).fileOffset());
            assertEquals(6, profile.range(SONIC_RAMP_CHUNKS).length());
            assertEquals(profile.range(LEVEL_START_LOC).endExclusive(), profile.range(SPEC_CHUNKS).fileOffset());
            assertEquals(4, profile.range(SPEC_CHUNKS).length());
            assertEquals(0x872, profile.range(SINE_TABLE).fileOffset());
            assertEquals(640, profile.range(SINE_TABLE).length());
            assertEquals(0xB58, profile.range(ARC_TAN_TABLE).fileOffset());
            assertEquals(257, profile.range(ARC_TAN_TABLE).length());
        }
    }

    private static CdR11aPlayerAssets decode(byte[][] inputs, CdSonicVisualAssets visuals) {
        return CdR11aPlayerAssets.decode(inputs[0], inputs[1], inputs[2], inputs[3], inputs[4], inputs[5], visuals);
    }

    private static void word(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    /** Synthetic parser inputs are test-only, never a production asset fallback. */
    private static final class Fixture {
        final byte[] animations = new byte[578];
        final byte[] mini = new byte[60];
        final byte[] ramp = {6, 7, 8, 0x49, 0x4C, (byte) 0xFF};
        final byte[] special = {0x7F, 0x7F, 0x7F, 0x7F};
        final byte[] sine = new byte[640];
        final byte[] atan = new byte[257];
        final CdSonicVisualAssets visuals;

        Fixture() {
            byte[] mappings = new byte[398];
            byte[] dplcs = new byte[398];
            for (int frame = 0; frame < 198; frame++) {
                word(mappings, frame * 2, 396);
                word(dplcs, frame * 2, 396);
            }
            visuals = CdSonicVisualAssets.decode(mappings, dplcs, new byte[69472]);
            for (int id = 0; id < 60; id++) {
                word(animations, id * 2, 120);
                mini[id] = (byte) id;
            }
            script(120, 3, 1, 0xFF);
            word(sine, 64 * 2, 0x100);
            word(sine, 192 * 2, 0xFF00);
            for (int i = 0; i <= 256; i++) { atan[i] = (byte) (i / 8); }
        }

        void script(int offset, int... bytes) {
            for (int i = 0; i < bytes.length; i++) { animations[offset + i] = (byte) bytes[i]; }
        }
        byte[][] inputs() { return new byte[][]{animations, mini, ramp, special, sine, atan}; }
        CdR11aPlayerAssets decode() { return TestCdR11aPlayerAssets.decode(inputs(), visuals); }
        CdR11aPlayerAssets decodeAnimations(byte[] table) {
            return CdR11aPlayerAssets.decode(table, mini, ramp, special, sine, atan, visuals);
        }
    }
}
