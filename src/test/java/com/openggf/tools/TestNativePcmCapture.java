package com.openggf.tools;

import com.openggf.audio.synth.NativeCompatiblePcmOutput;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code GameplayCaptureTool --native-pcm}: the stage follows the BK2's own SyncSettings and never substitutes a core. */
class TestNativePcmCapture {

    private static final Path EMERALDS = Path.of(
            "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2");
    private static final Path COMPLETE_MAME = Path.of("src/test/resources/traces/s3k/_movies/s3k-complete-sonic-tails.bk2");

    /** The locked-on S3K header's region field ($1F0): "JUE" padded with spaces. */
    private static final byte[] JUE = "JUE             ".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Test
    void regionMustResolveToAnNtscConsole() {
        assertTrue(NativePcmCapture.resolvesToNtsc(0, JUE), "autodetect prefers USA for JUE");
        assertTrue(NativePcmCapture.resolvesToNtsc(1, JUE));
        assertTrue(NativePcmCapture.resolvesToNtsc(3, JUE));
        assertFalse(NativePcmCapture.resolvesToNtsc(2, JUE), "Europe is PAL");
        assertFalse(NativePcmCapture.resolvesToNtsc(4, JUE), "Japan PAL");
        assertFalse(NativePcmCapture.resolvesToNtsc(0, "E               ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),
                "autodetect of a Europe-only header is PAL");
        assertThrows(IllegalArgumentException.class, () -> NativePcmCapture.configFromBk2(EMERALDS, Path.of("unused"), "PAL"));
    }

    @Test
    void nukedThreeBandMovieSelectsItsOwnFilterSettings() throws Exception {
        assertEquals(new NativeCompatiblePcmOutput.Config(NativeCompatiblePcmOutput.Filter.THREE_BAND,
                        26214, 880, 5000, 100, 100, 100),
                NativePcmCapture.configFromBk2(EMERALDS, JUE));
    }

    @Test
    void mameCoreMovieIsRefusedRatherThanSubstituted() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> NativePcmCapture.configFromBk2(COMPLETE_MAME, JUE));
        assertTrue(refused.getMessage().contains("GenesisFMSoundChip 0"), refused.getMessage());
    }

    @Test
    void nativePcmRequiresAudioAndABk2Input() {
        String[] base = {"--game", "s3k", "--zone", "aiz", "--act", "1", "--out-dir", "out"};
        assertFalse(GameplayCaptureTool.Arguments.parse(base).nativePcm());
        assertThrows(IllegalArgumentException.class, () -> GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s3k", "--zone", "aiz", "--act", "1", "--out-dir", "out", "--native-pcm",
                "--input", "movie.bk2"}));
        assertThrows(IllegalArgumentException.class, () -> GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s3k", "--zone", "aiz", "--act", "1", "--out-dir", "out", "--native-pcm", "--audio",
                "--input", "script.txt"}));
        assertTrue(GameplayCaptureTool.Arguments.parse(new String[] {
                "--game", "s3k", "--zone", "aiz", "--act", "1", "--out-dir", "out", "--native-pcm", "--audio",
                "--input", "movie.bk2"}).nativePcm());
    }
}
