package com.openggf.game.soniccd.title.secrets;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.fmv.CdFmvNativeAccess;
import com.openggf.game.soniccd.program.CdProgramData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Best staff times against native ({@code rehost-programs/bt-scroll}: power on, the title code Right Right Up Up Down
 * C, Down held 300 frames, Up 100, Start). Aligned on the first picture that is not black (the fade's first step),
 * then every picture compared with the native pad replayed, to the program's return.
 */
class TestCdBestTimesNative {
    @Test
    void bestTimesEqualNative() throws Exception {
        String run = "bt-scroll";
        Map<Integer, Map<String, String>> frames = CdFmvNativeAccess.frames(run);
        List<Integer> shots = CdFmvNativeAccess.screenshotFrames(run), dumps = CdFmvNativeAccess.dumpFrames(run);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdFmvNativeAccess.usaCue())) {
            CdFmvAssets assets = new CdFmvAssets(new CdProgramData(disc));
            CdBestTimesProgram probe = new CdBestTimesProgram(assets);
            List<int[]> early = new ArrayList<>();
            for (int i = 0; i < 30; i++) { probe.frame(0); early.add(CdFmvNativeAccess.picture(probe.screen())); }
            int coloured = 1;
            while (java.util.Arrays.equals(early.get(coloured), early.get(0))) coloured++;
            int first = -1;
            for (int s : shots) {
                int[] shot = CdFmvNativeAccess.screenshot(run, s);
                for (int i = coloured; i < coloured + 2 && first < 0; i++) if (java.util.Arrays.equals(early.get(i), shot)) first = s - i;
                if (first >= 0) break;
            }
            assertTrue(first > 0, "the fade-in appears natively");
            CdBestTimesProgram program = new CdBestTimesProgram(assets);
            List<String> diff = new ArrayList<>();
            int pictures = 0, memory = 0, n = first;
            for (; !program.done() && frames.containsKey(n); n++) {
                program.frame(TestCdSoundTestNative.pad(frames.get(n)));
                if (shots.contains(n)) {
                    pictures++;
                    if (!java.util.Arrays.equals(CdFmvNativeAccess.picture(program.screen()), CdFmvNativeAccess.screenshot(run, n))) diff.add(n + " picture");
                }
                if (dumps.contains(n)) { memory++; List<String> d = CdFmvNativeAccess.memoryDifferences(program.screen(), run, n, CdFmvNativeAccess.BEST_TIMES); if (!d.isEmpty()) diff.add(n + "=" + d); }
            }
            System.out.println("CD best staff times: from native frame " + first + " to " + n + ", " + pictures + " pictures and "
                    + memory + " dumps compared, differing " + diff + "; scroll ended at " + program.scroll());
            assertTrue(program.done());
            assertTrue(pictures > 400);
            assertEquals(List.of(), diff);
        }
    }
}
