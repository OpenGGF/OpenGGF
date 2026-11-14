package com.openggf.game.soniccd.fmv;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.program.CdProgramData;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The opening video against the native USA reference: idle at the title until IPX's first attract demo runs
 * {@code OPENING} ({@code .Demos}, IPX.asm:948-954), recorded as {@code rehost-programs/opening-attract}.
 * Engine and native frames are aligned per bank (the frame each bank starts showing); the bank schedule itself
 * is compared separately because the drive rate differs (below).
 */
class TestCdOpeningNative {
    private static final String RUN = "opening-attract";
    private static final int[] DUMPS = {3404, 3405, 3406, 3407, 3408, 3410, 3411, 3412, 3415, 3420, 3463, 3464, 3465,
            3470, 5000, 5001, 8000, 8600};

    private record Run(CdOpeningProgram program, List<Integer> bankStarts, Map<Integer, List<String>> memory,
                       Map<Integer, Integer> pictures) { }

    private static Run run() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdFmvNative.usaCue())) {
            CdOpeningProgram program = CdFmvScreen.opening(new CdFmvAssets(new CdProgramData(disc)), disc.assets(),
                    CdSubCpuAudio.ProgramPcm.detached());
            Map<Integer, Map<String, String>> nativeFrames = CdFmvNative.frames(RUN);
            List<Integer> nativeStarts = CdFmvNative.bankStarts(nativeFrames);
            Map<String, Integer> wanted = new HashMap<>();
            for (int d : DUMPS) wanted.put(key(nativeStarts, d), d);
            Map<String, Integer> shots = new HashMap<>();
            for (int s = 3405; s <= 3540; s++) shots.put(key(nativeStarts, s), s);   // post-frame picture N shows state N
            List<Integer> starts = new ArrayList<>();
            Map<Integer, List<String>> memory = new HashMap<>();
            Map<Integer, Integer> pictures = new HashMap<>();
            for (int f = 0; f < 7000 && !program.done(); f++) {
                program.frame(0);
                if (program.streamer().banksTaken() > starts.size()) starts.add(f);
                if (starts.isEmpty()) continue;
                String k = (starts.size() - 1) + ":" + (f - starts.get(starts.size() - 1));
                Integer d = wanted.get(k);
                if (d != null) memory.put(d, CdFmvNative.memoryDifferences(program.screen(), RUN, d, CdFmvNative.Layout.VIDEO));
                Integer s = shots.get(k);
                if (s != null) pictures.put(s, CdFmvNative.equalPixels(CdFmvNative.picture(program.screen().frame()),
                        CdFmvNative.screenshot(RUN, s)));
            }
            return new Run(program, starts, memory, pictures);
        }
    }

    private static String key(List<Integer> starts, int frame) {
        int n = -1;
        for (int i = 0; i < starts.size(); i++) if (starts.get(i) <= frame) n = i;
        return n + ":" + (frame - starts.get(Math.max(n, 0)));
    }

    @Test
    void videoMemoryAndPicturesEqualNative() throws Exception {
        Run run = run();
        System.out.println("CD opening video memory against native: " + run.memory());
        assertEquals(DUMPS.length, run.memory().size(), "every native dump was reached");
        run.memory().forEach((frame, diff) -> assertEquals(List.of(), diff, "native frame " + frame));
        int equal = run.pictures().values().stream().mapToInt(Integer::intValue).min().orElse(0);
        System.out.println("CD opening pictures (native 3405-3540, 320x224): smallest equal count " + equal + " of 71680");
        assertEquals(136, run.pictures().size());
        assertEquals(320 * 224, equal, "every picture equals the native screenshot");
    }

    /**
     * The bank schedule: 91 banks, each 59 or 60 frames after the last. Native (BizHawk 2.11) runs the drive at
     * 59.937 frames a second, the engine at the hardware's 59.923,
     * so a 59-frame interval comes every 16 banks natively and every 13 here: over 90 intervals the totals differ by
     * at most one frame. The first bank starts 80 frames into the program (the 20-frame seek, native 17 for this file,
     * plus a second of data). The program ends 122 frames after its last bank; native 130, the difference being the
     * sub program's copy loop and the IPX return, whose CPU time the engine does not model.
     */
    @Test
    void bankScheduleAndLengthMatchNative() throws Exception {
        Run run = run();
        List<Integer> nativeStarts = CdFmvNative.bankStarts(CdFmvNative.frames(RUN));
        assertEquals(nativeStarts.size(), run.bankStarts().size(), "banks");
        int nativeSpan = nativeStarts.getLast() - nativeStarts.getFirst();
        int engineSpan = run.bankStarts().getLast() - run.bankStarts().getFirst();
        for (int i = 1; i < run.bankStarts().size(); i++) {
            int d = run.bankStarts().get(i) - run.bankStarts().get(i - 1);
            assertTrue(d == 59 || d == 60, "interval " + d);
        }
        System.out.println("CD opening banks: native span " + nativeSpan + ", engine " + engineSpan + "; first bank at program frame "
                + run.bankStarts().getFirst() + "; program length " + run.program().frames() + " frames, last bank to end "
                + (run.program().frames() - 1 - run.bankStarts().getLast()));
        assertTrue(Math.abs(nativeSpan - engineSpan) <= 1, "bank span");
        // The first bank's frame is the seek (an unsupported drive timing, CdFmvStreamer.SEEK_VINTS) plus a packet:
        // this assertion depends on the chosen value by definition; native's is 77 after its file request.
        assertEquals(CdFmvStreamer.SEEK_VINTS + 60, run.bankStarts().getFirst());
        assertTrue(run.program().done());
    }

    /**
     * Which PCM plays when. Asserted, independent of the seek: every copy holds one whole packet's PCM, and packet m's
     * copy lands while bank m-1 is shown (after its own PCM arrived, before its bank is shown). Reported, not asserted,
     * because it moves frame for frame with the seek (CdFmvStreamer.SEEK_VINTS): where in the sound each bank starts.
     * Native (correlating each packet's stream PCM with the recorded audio) puts packet m 44-45 frames after bank m.
     */
    @Test
    void pcmCopiesFollowTheSyncRule() throws Exception {
        Run run = run();
        List<CdFmvStreamer.Copy> copies = run.program().streamer().copies();
        assertEquals(91, copies.size());
        List<Integer> offsets = new ArrayList<>();
        for (CdFmvStreamer.Copy copy : copies) {
            assertEquals(16, copy.sectorsOfPacket(), "a whole packet's PCM: " + copy);
            int m = copy.packet();
            if (m < 1 || m >= run.bankStarts().size()) continue;
            long frame = copy.vint() - 1;
            assertTrue(frame >= run.bankStarts().get(m - 1) && frame < run.bankStarts().get(m), "packet " + m + " copied while bank " + (m - 1) + " is shown");
            double audioStart = frame + 32768.0 / (12_500_000.0 / 384 * 2060 / 2048) * 53_693_175.0 / 896_040.0;
            if (m >= 2) offsets.add((int) Math.round(audioStart - run.bankStarts().get(m)));
        }
        int nativeLow = nativeOffset(10), nativeHigh = nativeOffset(85);
        int lo = offsets.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int hi = offsets.stream().mapToInt(Integer::intValue).max().orElseThrow();
        System.out.println("CD opening audio (seek-dependent, reported): packet m sounds " + lo + "-" + hi
                + " frames after bank m starts (native " + nativeLow + " at packet 10, " + nativeHigh + " at packet 85; seek "
                + CdFmvStreamer.SEEK_VINTS + " vs native 17 frames)");
    }

    /** The frame offset of packet {@code m}'s PCM in the native audio, by correlation near its expected place. */
    private static int nativeOffset(int m) throws Exception {
        Map<Integer, Map<String, String>> frames = CdFmvNative.frames(RUN);
        List<Integer> starts = CdFmvNative.bankStarts(frames);
        int last = starts.getLast() + 300;
        long[] cumulative = new long[last + 2];
        for (int f = 1; f <= last; f++) cumulative[f] = cumulative[f - 1] + Integer.parseInt(frames.get(f).get("samples"));
        ByteBuffer audio = ByteBuffer.wrap(CdFmvNative.bytes(RUN + "/audio.s16")).order(ByteOrder.LITTLE_ENDIAN);
        byte[] stm;
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdFmvNative.usaCue())) {
            stm = disc.assets().read(com.openggf.game.soniccd.CdNativeFile.OPENSTM, (long) m * 75 * 2048, 0x8000);
        }
        int n = 4410;
        double[] ref = new double[n];
        for (int i = 0; i < n; i++) {                      // 44.1 kHz from the chip's 32,742.8 bytes a second
            int b = stm[(int) (i * 32742.8 / 44100.0) + 1500] & 0xFF;
            ref[i] = (b & 0x80) != 0 ? b & 0x7F : -(b & 0x7F);
        }
        double best = -1;
        int bestFrame = 0;
        for (int f = starts.get(m) + 30; f <= starts.get(m) + 60; f++) {
            for (long s = cumulative[f - 1]; s < cumulative[f]; s += 7) {
                double sum = 0, energy = 1e-9;
                long at = s + Math.round(1500 * 44100.0 / 32742.8);
                for (int i = 0; i < n; i++) {
                    long k = (at + i) * 4;
                    if (k + 3 >= audio.capacity()) break;
                    double v = audio.getShort((int) k) + audio.getShort((int) k + 2);
                    sum += ref[i] * v;
                    energy += v * v;
                }
                double score = sum / Math.sqrt(energy);
                if (score > best) { best = score; bestFrame = f; }
            }
        }
        return bestFrame - starts.get(m);
    }
}
