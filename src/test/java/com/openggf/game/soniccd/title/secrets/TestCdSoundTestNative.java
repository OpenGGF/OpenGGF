package com.openggf.game.soniccd.title.secrets;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.fmv.CdFmvNativeAccess;
import com.openggf.game.soniccd.fmv.CdProgramScreenState;
import com.openggf.game.soniccd.program.CdProgramData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sound test and the hidden pictures against native USA runs ({@code rehost-programs/st-*}): power on, the
 * title's sound-test code (Down Down Down Left Right A, Title Screen/Objects/Menu/Main.asm:356-395), the three
 * numbers of one code held in with Down and Right, Start. The engine replays the native pad from the program's
 * first main-loop pass (the frame the native {@code lrSelDelay} first counts); the hidden picture, loaded after
 * IPX's disc load, is aligned on its first picture.
 */
class TestCdSoundTestNative {
    @ParameterizedTest
    @CsvSource({"ss8,1,", "fun,2,FUN_IS_INFINITE", "mcsonic,3,MC_SONIC", "tails,4,TAILS", "batman,5,BATMAN", "cute,6,CUTE_SONIC"})
    void soundTestAndPictureEqualNative(String code, int exit, String picture) throws Exception {
        String run = "st-" + code;
        Map<Integer, Map<String, String>> frames = CdFmvNativeAccess.frames(run);
        int firstPass = frames.keySet().stream().sorted().filter(f -> frames.get(f).get("lr").equals("0001")).findFirst().orElseThrow();
        int offset = firstPass - 25;                       // engine frame 24 (0-based) is the first pass: native firstPass
        List<Integer> dumps = CdFmvNativeAccess.dumpFrames(run), shots = CdFmvNativeAccess.screenshotFrames(run);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdFmvNativeAccess.usaCue())) {
            CdFmvAssets assets = new CdFmvAssets(new CdProgramData(disc));
            CdSoundTestProgram program = new CdSoundTestProgram(assets);
            Map<Integer, List<String>> memory = new TreeMap<>();
            Map<Integer, Integer> pictures = new TreeMap<>();
            int f = 0;
            for (; f < 4000 && !program.done(); f++) {
                program.frame(pad(frames.get(f + offset + 1)));
                int n = f + offset + 1;
                if (dumps.contains(n)) memory.put(n, CdFmvNativeAccess.memoryDifferences(program.screen(), run, n, CdFmvNativeAccess.SECRETS));
                if (shots.contains(n)) pictures.put(n, CdFmvNativeAccess.pictureEqual(program.screen(), run, n));
            }
            int end = f + offset;
            List<String> diff = new ArrayList<>();
            memory.forEach((n, d) -> { if (!d.isEmpty()) diff.add(n + "=" + d); });
            pictures.forEach((n, e) -> { if (e != 320 * 224) diff.add(n + " picture " + e); });
            System.out.println("CD sound test " + code + ": exit " + program.exitCode() + " after native frame " + end + "; "
                    + memory.size() + " dumps and " + pictures.size() + " pictures compared, differing " + diff);
            assertTrue(program.done());
            assertEquals(exit, program.exitCode());
            assertTrue(pictures.size() > 100);
            assertEquals(List.of(), diff);
            if (picture == null || picture.isEmpty()) return;
            // The hidden picture: anchored on its first picture that is not all white (its first frames show the
            // white of the sound test's own fade), then compared frame by frame with the native pad replayed.
            CdSecretImageProgram.Picture which = CdSecretImageProgram.Picture.valueOf(picture);
            CdSecretImageProgram probe = new CdSecretImageProgram(assets, which);
            // Screenshots are every other frame: try the first two pictures after the white ones.
            List<int[]> early = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                probe.frame(0);
                early.add(CdFmvNativeAccess.picture(probe.screen()));
            }
            int firstColoured = 1;
            while (java.util.Arrays.equals(early.get(firstColoured), early.get(0))) firstColoured++;
            int first = -1;
            for (int s : shots) {
                if (s <= end || first >= 0) continue;
                int[] shot = CdFmvNativeAccess.screenshot(run, s);
                for (int i = firstColoured; i < firstColoured + 2 && first < 0; i++) {
                    if (java.util.Arrays.equals(early.get(i), shot)) first = s - i;
                }
            }
            assertTrue(first > end, "the hidden picture's first faded frame appears natively");
            CdSecretImageProgram image = new CdSecretImageProgram(assets, which);
            int compared = 0, last = -1;
            for (int n = first; n <= frames.size() && !image.done(); n++) {
                image.frame(pad(frames.get(n)));
                if (shots.contains(n)) {
                    assertTrue(java.util.Arrays.equals(CdFmvNativeAccess.picture(image.screen()), CdFmvNativeAccess.screenshot(run, n)),
                            "hidden picture at native " + n);
                    compared++;
                }
                last = n;
            }
            System.out.println("CD hidden picture " + picture + ": native frames " + first + "-" + last + ", " + compared
                    + " pictures equal; returned " + image.done());
            assertTrue(image.done(), "Start held ends the picture");
            assertTrue(compared > 100, "pictures compared");
        }
    }

    /** The pad byte from the probe's input column (start, A, C, B, right, left, down, up). */
    static int pad(Map<String, String> row) {
        if (row == null) return 0;
        int pad = 0;
        for (String b : row.get("input").split("\\+")) {
            pad |= switch (b) {
                case "Up" -> 0x01; case "Down" -> 0x02; case "Left" -> 0x04; case "Right" -> 0x08;
                case "B" -> 0x10; case "C" -> 0x20; case "A" -> 0x40; case "Start" -> 0x80; default -> 0;
            };
        }
        return pad;
    }
}
