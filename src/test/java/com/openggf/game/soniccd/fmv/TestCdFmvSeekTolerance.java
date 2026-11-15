package com.openggf.game.soniccd.fmv;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.ending.CdEndingProgram;
import com.openggf.game.soniccd.program.CdProgramData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drive seek ({@link CdFmvStreamer#SEEK_VINTS}) is an unsupported hardware timing with a stated tolerance of 12-46
 * V-ints. At both ends the opening and the good ending show the same pictures relative to each bank as at the chosen
 * value, take every bank, and copy only whole packets of PCM: the native picture comparisons, aligned per bank, do not
 * depend on the value. No native data needed beyond the disc.
 */
class TestCdFmvSeekTolerance {
    private record Video(List<Integer> pictures, int banks, boolean wholeCopies, boolean done) { }

    @ParameterizedTest
    @ValueSource(ints = {12, 46})
    void seekWithinToleranceChangesNoPictureAndTearsNoCopy(int seek) throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdFmvNative.usaCue())) {
            CdFmvAssets assets = new CdFmvAssets(new CdProgramData(disc));
            for (boolean ending : new boolean[] {false, true}) {
                Video reference = run(assets, disc, ending, CdFmvStreamer.SEEK_VINTS);
                Video other = run(assets, disc, ending, seek);
                String what = (ending ? "good ending" : "opening") + " at seek " + seek;
                System.out.println("CD video seek tolerance: " + what + ": " + other.banks() + " banks, whole copies "
                        + other.wholeCopies() + ", " + other.pictures().size() + " bank-relative pictures");
                assertTrue(other.done(), what);
                assertEquals(reference.banks(), other.banks(), what);
                assertTrue(other.wholeCopies(), what + ": a torn copy");
                assertEquals(reference.pictures(), other.pictures(), what + ": pictures relative to the banks");
            }
        }
    }

    /**
     * Picture hashes at fixed offsets into banks 5, 15, 25 ... (never the last: when the video ends after it follows the
     * last copy, which moves with the seek), and the copy and bank counts.
     */
    private static Video run(CdFmvAssets assets, CdDiscDataSource disc, boolean ending, int seek) {
        CdFmvStreamer streamer = new CdFmvStreamer(new CdFmvStream(disc.assets(), ending ? CdNativeFile.GOODENDSTM : CdNativeFile.OPENSTM),
                CdFmvStream.Layout.SOUND, CdSubCpuAudio.ProgramPcm.detached(),
                assets.from(ending ? "pcmRegValuesGoodEnding" : "pcmRegValues", 14), seek);
        IntConsumer frame;
        java.util.function.Supplier<CdProgramScreenState> screen;
        java.util.function.BooleanSupplier done;
        if (ending) {
            CdEndingProgram program = new CdEndingProgram(assets, streamer, true);
            frame = program::frame; screen = program::screen; done = program::done;
        } else {
            CdOpeningProgram program = new CdOpeningProgram(assets, streamer);
            frame = program::frame; screen = program::screen; done = program::done;
        }
        List<Integer> pictures = new ArrayList<>();
        int bankStart = -1, banks = 0;
        for (int f = 0; f < 20000 && !done.getAsBoolean(); f++) {
            frame.accept(0);
            if (streamer.banksTaken() > banks) { banks = streamer.banksTaken(); bankStart = f; }
            int into = f - bankStart;
            if (banks > 0 && banks % 10 == 5 && (into == 1 || into == 8 || into == 30 || into == 50)) {
                pictures.add(Arrays.hashCode(CdFmvNative.picture(screen.get().frame())));
            }
        }
        boolean whole = streamer.copies().stream().allMatch(c -> c.sectorsOfPacket() == 16);
        return new Video(pictures, banks, whole, done.getAsBoolean());
    }
}
