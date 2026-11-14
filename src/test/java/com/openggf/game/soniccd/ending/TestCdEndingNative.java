package com.openggf.game.soniccd.ending;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.fmv.CdFmvNativeAccess;
import com.openggf.game.soniccd.fmv.CdFmvStream;
import com.openggf.game.soniccd.fmv.CdFmvStreamer;
import com.openggf.game.soniccd.fmv.CdProgramScreenState;
import com.openggf.game.soniccd.program.CdProgramData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both endings against the native USA reference, entered from Visual Mode (title flags bit 2 poked as setup so the
 * title lists Visual Mode; then Good Ending or Bad Ending): {@code rehost-programs/vm-goodend}, {@code vm-badend}.
 * Native frames map to engine frames through the bank each lies in (its swap frame); after the last bank, through
 * the last swap. Video memory is compared whole; pictures (planes, no sprites: the program uses none) exactly.
 */
class TestCdEndingNative {
    @ParameterizedTest
    @ValueSource(strings = {"good", "bad"})
    void endingEqualsNative(String which) throws Exception {
        boolean good = which.equals("good");
        String run = good ? "vm-goodend" : "vm-badend";
        Map<Integer, Map<String, String>> frames = CdFmvNativeAccess.frames(run);
        List<Integer> nativeSwaps = CdFmvNativeAccess.swaps(frames);
        List<Integer> dumps = CdFmvNativeAccess.dumpFrames(run);
        List<Integer> shots = CdFmvNativeAccess.screenshotFrames(run);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdFmvNativeAccess.usaCue())) {
            CdFmvAssets assets = new CdFmvAssets(new CdProgramData(disc));
            CdFmvStreamer streamer = new CdFmvStreamer(new CdFmvStream(disc.assets(), good ? CdNativeFile.GOODENDSTM : CdNativeFile.BADENDSTM),
                    CdFmvStream.Layout.SOUND, CdSubCpuAudio.ProgramPcm.detached(),
                    assets.from(good ? "pcmRegValuesGoodEnding" : "pcmRegValuesBadEnding", 14));
            CdEndingProgram program = new CdEndingProgram(assets, streamer, good);
            List<Integer> swaps = new ArrayList<>();
            Map<Integer, List<String>> memory = new TreeMap<>();
            Map<Integer, Integer> pictures = new TreeMap<>();
            int nativeCredits = first(frames, "c088", "FF00"), nativeCreditsEnd = last(frames, "c088", "FF00") + 1;
            int nativeExit = exitStart(frames, nativeSwaps.getLast());
            // Three clocks. Video lies in a bank: align by the bank's swap. The credits layer (plane B rows $A000-$BFFF
            // and VSRAM) moves every V-int: align by the credits' start. The tail (the 61-V-int wait and the final
            // image) by the main program's exit; native's image then runs 4 frames later after its setup (the VRAM
            // clear and Nemesis decode, CPU time the engine does not model), so the tail is compared as a sequence of
            // pictures: every native picture appears in the engine's tail, in the same order.
            Map<Integer, CdProgramScreenState> snapshots = new TreeMap<>();
            int creditsStart = -1, creditsEnd = -1, exit = -1;
            List<int[]> tail = new ArrayList<>();
            for (int f = 0; f < 20000 && !program.done(); f++) {
                program.frame(0);
                if (streamer.banksTaken() > swaps.size()) swaps.add(f);
                if (creditsStart < 0 && program.creditsActive()) creditsStart = f;
                if (creditsStart >= 0 && creditsEnd < 0 && !program.creditsActive()) creditsEnd = f;
                if (exit < 0 && program.exiting()) exit = f;
                if (exit >= 0) tail.add(com.openggf.game.soniccd.fmv.CdFmvNativeAccess.picture(program.screen()));
                else snapshots.put(f, program.screen().copy());
                if (snapshots.size() > 400) snapshots.remove(((TreeMap<Integer, CdProgramScreenState>) snapshots).firstKey());
                for (int n : union(dumps, shots)) {
                    if (n >= nativeExit) continue;
                    int video = bankFrame(n, nativeSwaps, swaps);
                    int credits = n >= nativeCredits && n < nativeCreditsEnd && creditsStart >= 0 ? creditsStart + n - nativeCredits : video;
                    if (Math.max(video, credits) != f || video < 0) continue;
                    CdProgramScreenState state = snapshots.get(video).copy();
                    state.takePlane(CdProgramScreenState.Plane.B, snapshots.get(credits));
                    if (dumps.contains(n)) memory.put(n, CdFmvNativeAccess.memoryDifferences(state, run, n, com.openggf.game.soniccd.fmv.CdFmvNativeAccess.VIDEO));
                    if (shots.contains(n)) pictures.put(n, CdFmvNativeAccess.pictureEqual(state, run, n));
                }
            }
            for (int d : dumps) if (d >= nativeExit) memory.put(d, List.of());   // the tail is checked as pictures
            int matched = 0, cursor = 0;
            java.util.TreeSet<Integer> lags = new java.util.TreeSet<>();
            List<String> order = new ArrayList<>();
            for (int s : shots) {
                if (s < nativeExit) continue;
                int[] picture = CdFmvNativeAccess.screenshot(run, s);
                int at = -1;
                for (int i = cursor; i < tail.size(); i++) if (java.util.Arrays.equals(tail.get(i), picture)) { at = i; break; }
                if (at < 0) order.add(s + " not in the engine's tail from +" + cursor);
                else { matched++; if (at != cursor || lags.isEmpty()) lags.add(s - nativeExit - at); cursor = at; }
            }
            int firstNative = nativeSwaps.getFirst();
            System.out.println("CD " + which + " ending: banks native " + nativeSwaps.size() + " engine " + swaps.size()
                    + "; credits from first bank +" + (nativeCredits - firstNative) + "/+" + (creditsStart - swaps.getFirst())
                    + " to +" + (nativeCreditsEnd - firstNative) + "/+" + (creditsEnd - swaps.getFirst())
                    + " (native/engine); exit +" + (nativeExit - nativeSwaps.getLast()) + "/+" + (exit - swaps.getLast())
                    + "; program length " + program.frames() + " frames (last bank +" + (program.frames() - 1 - swaps.getLast()) + ")");
            List<String> memoryReport = new ArrayList<>();
            memory.forEach((frame, diff) -> { if (!diff.isEmpty()) memoryReport.add(frame + "=" + diff); });
            List<String> pictureReport = new ArrayList<>();
            pictures.forEach((frame, equal) -> { if (equal != 320 * 224) pictureReport.add(frame + "=" + equal); });
            System.out.println("CD " + which + " ending: " + memory.size() + " video-memory dumps, differing " + memoryReport
                    + "; " + pictures.size() + " pictures before the exit, differing " + pictureReport + "; tail " + matched
                    + " native pictures in engine order, native later by " + lags + " frames " + order);
            assertTrue(order.stream().noneMatch(o -> o.contains("not in")), "tail order: " + order);
            assertEquals(nativeSwaps.size(), swaps.size(), "banks");
            assertEquals(nativeCredits - firstNative, creditsStart - swaps.getFirst(), "credits start");
            assertEquals(nativeCreditsEnd - firstNative, creditsEnd - swaps.getFirst(), "credits end");
            assertEquals(dumps.size(), memory.size(), "every dump reached");
            assertEquals(List.of(), memoryReport);
            assertTrue(pictures.size() > 100, "pictures compared");
            assertEquals(List.of(), pictureReport);
            assertTrue(program.done());
        }
    }

    private static java.util.Set<Integer> union(List<Integer> a, List<Integer> b) {
        java.util.Set<Integer> out = new java.util.TreeSet<>(a);
        out.addAll(b);
        return out;
    }

    /** The engine frame at the same offset from the bank swap the native frame follows; -1 before it. */
    static int bankFrame(int nativeFrame, List<Integer> nativeSwaps, List<Integer> engineSwaps) {
        int n = -1;
        for (int i = 0; i < nativeSwaps.size(); i++) if (nativeSwaps.get(i) <= nativeFrame) n = i;
        if (n < 0 || n >= engineSwaps.size()) return -1;
        return engineSwaps.get(n) + nativeFrame - nativeSwaps.get(n);
    }

    /** The first frame after the main program's last state change past the last bank: its exit wait begins. */
    private static int exitStart(Map<Integer, Map<String, String>> frames, int lastSwap) {
        int last = lastSwap;
        for (int f = lastSwap; frames.containsKey(f + 1); f++) {
            Map<String, String> a = frames.get(f), b = frames.get(f + 1);
            if (!a.get("c086").equals(b.get("c086")) || !a.get("c080").equals(b.get("c080"))) last = f + 1;
            if (b.get("opermode").equals("0002")) break;
        }
        return last + 1;
    }

    private static int first(Map<Integer, Map<String, String>> frames, String column, String value) {
        return frames.entrySet().stream().filter(e -> e.getValue().get(column).equals(value)).mapToInt(Map.Entry::getKey).min().orElse(-1);
    }

    private static int last(Map<Integer, Map<String, String>> frames, String column, String value) {
        return frames.entrySet().stream().filter(e -> e.getValue().get(column).equals(value)).mapToInt(Map.Entry::getKey).max().orElse(-1);
    }
}
