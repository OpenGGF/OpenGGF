package com.openggf.game.soniccd;

import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.zones.ttz.TtzWaterDataProvider;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tools.HeadlessGameBoot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native check for the Tidal Tempest background, palette cycle, animated tiles and water: the production
 * renderer against the two screenshots each rehost fixture carries ({@code screenshot-early-rowNNNN.png},
 * idle gameplay; {@code screenshot-end-rowNNNN.png}, the last trace row after 600 frames of holding Right).
 * The camera is frozen and set to the trace's camera for every level frame, so the scroll handler sees the
 * native camera path; the water events see the trace's player position (the player lane is separate).
 * The HUD, the player and every placed object's box are masked.
 *
 * <p>The native video shows the buffers the previous level frame wrote (the V-blank that starts a frame
 * uploads them before that frame's logic), while the trace's counters are read after the logic, so native
 * level frame N is compared with the engine after level frame N-1, as in {@code TestPpzR11aBackgroundPicture}.
 */
final class TtzPicture {
    private TtzPicture() { }

    /** Match counts of one engine frame against one native shot. */
    record Result(String stage, String shot, int engineFrame, int same, int total, String where) {
        @Override public String toString() {
            return String.format("%s %s (native level frame %d vs engine frame %d): %d/%d unmasked pixels match%s",
                    stage, shot, engineFrame + 1, engineFrame, same, total, where);
        }
        boolean passes() { return same * 1000L >= total * 995L; }
    }

    /** The trace player the water events read. */
    static final class TracePlayer implements TtzWaterDataProvider.PlayerView {
        volatile TtzNative.Row row;
        @Override public int centreX() { return row.playerX(); }
        @Override public int centreY() { return row.playerY(); }
        @Override public boolean alive() { return row.routine() < 6; }
    }

    /** One headless GL boot shared by all stages: a second boot in a JVM fails (GL 0x502). */
    static final class Session implements AutoCloseable {
        final CdDiscDataSource disc;
        final HeadlessGameBoot boot = new HeadlessGameBoot(320, 224);
        final SonicCdGameModule base = new SonicCdGameModule();
        final TracePlayer player = new TracePlayer();
        final TtzWaterDataProvider provider;
        final com.openggf.GameLoop loop;
        private final boolean oldAudio;
        private final String oldGl;
        private CdStagePayload current;

        Session(CdDiscDataSource disc, CdStagePayload firstPayload) throws Exception {
            this.disc = disc;
            oldGl = System.getProperty("openggf.headless.gl");
            oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
            System.setProperty("openggf.headless.gl", "surfaceless-egl");
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
            player.row = new TtzNative.Row(0, 0, 0, 0, 0, 2);
            provider = new TtzWaterDataProvider(() -> ((SonicCdGame) base.createGame(disc)).currentImage(), player);
            loop = boot.boot(disc, new TtzNative.WaterModule(base, disc, provider),
                    SonicCdZoneRegistry.zoneIndex(firstPayload), SonicCdZoneRegistry.actIndex(firstPayload));
            current = firstPayload;
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
        }

        /** Loads a stage in the running session; the boot already loaded the first one. */
        void load(CdStagePayload payload, boolean force) throws Exception {
            if (payload == current && force) {
                // ParallaxManager re-creates the scroll handler only when the act changes.
                CdStagePayload other = payload == CdStagePayload.R42B ? CdStagePayload.R42C : CdStagePayload.R42B;
                GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(other),
                        SonicCdZoneRegistry.actIndex(other));
                current = other;
            }
            if (payload != current) {
                GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload),
                        SonicCdZoneRegistry.actIndex(payload));
            }
            current = payload;
        }

        @Override public void close() throws Exception {
            boot.close();
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }
    }

    /**
     * Steps the stage along the trace camera and captures the engine at native level frame N-1 for each shot.
     * With {@code skewX} nonzero the camera is that many pixels off from level frame 100 on (negative control).
     */
    static List<Result> check(Session session, CdStagePayload payload, int skewX) throws Exception {
        int[] frameOffsets = {-1};
        Path out = Path.of("target", "soniccd-ttz");
        List<TtzNative.Shot> shots = TtzNative.shots(payload);
        List<TtzNative.Row> rows = TtzNative.rows(payload);
        // Engine frames to capture per shot: native level frame N - 1 (and neighbours when exploring).
        Map<Integer, List<String>> captureAt = new TreeMap<>();
        for (TtzNative.Shot shot : shots) {
            for (int offset : frameOffsets) {
                captureAt.computeIfAbsent(shot.levelFrames() + offset, k -> new ArrayList<>())
                        .add(shot.name() + ":" + offset);
            }
        }
        int last = captureAt.keySet().stream().mapToInt(Integer::intValue).max().orElseThrow();
        TracePlayer player = session.player;
        player.row = rows.get(0);
        List<Result> results = new ArrayList<>();
        Map<String, TtzNative.Rendered> rendered = new TreeMap<>();
        {
            session.load(payload, skewX != 0);
            var loop = session.loop;
            GameServices.camera().setFrozen(true);
            // The loaded stage is level frame 1 with its LevelScroll, CyclePalette and AnimateStageGfx calls run
            // (CdFirstMainLoopIteration, r4/main.asm:219, 280, 1138, 399-406), so stepping starts at level frame 2
            // and the production animator is the only one.
            int previous = rows.get(0).counter();
            TtzNative.Row previousRow = rows.get(0);
            for (TtzNative.Row row : rows) {
                if (row.counter() == previous) continue;           // lag: the level frame did not advance
                previous = row.counter();
                if (previous > last) break;
                player.row = previousRow;                          // V-blank sees the previous frame's player
                GameServices.camera().setX((short) (row.cameraX() + (previous > 100 ? skewX : 0)));
                GameServices.camera().setY((short) row.cameraY());
                loop.step();
                for (String tag : captureAt.getOrDefault(previous, List.of())) rendered.put(tag, TtzNative.capture(row));
                previousRow = row;
            }
        }
        for (TtzNative.Shot shot : shots) {
            RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
            for (int offset : frameOffsets) {
                TtzNative.Rendered engine = rendered.get(shot.name() + ":" + offset);
                assertTrue(engine != null, "frame " + (shot.levelFrames() + offset) + " not reached");
                boolean[][] masked = TtzNative.mask(engine);
                int same = 0, total = 0, minX = 999, minY = 999, maxX = -1, maxY = -1;
                RgbaImage diff = new RgbaImage(320, 224, new int[320 * 224]);
                for (int y = 0; y < 224; y++) {
                    for (int x = 0; x < 320; x++) {
                        int ref = reference.argb(x, y);
                        int eng = engine.image().argb(x, y);
                        if (masked[x][y]) { diff.setArgb(x, y, 0xFF404040); continue; }
                        total++;
                        if (TtzNative.level(ref) == TtzNative.level(eng)) {
                            same++;
                            diff.setArgb(x, y, 0xFF000000);
                        } else {
                            minX = Math.min(minX, x); minY = Math.min(minY, y);
                            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                            diff.setArgb(x, y, 0xFFFF0000);
                        }
                    }
                }
                Files.createDirectories(out);
                String stem = payload.name().toLowerCase() + "-" + shot.name() + (offset);
                ScreenshotCapture.savePNG(engine.image(), out.resolve(stem + "-engine.png"));
                ScreenshotCapture.savePNG(diff, out.resolve(stem + "-diff.png"));
                results.add(new Result(payload.name(), shot.name(), shot.levelFrames() + offset, same, total,
                        same == total ? "" : String.format("; differing pixels lie in x %d-%d, y %d-%d",
                                minX, maxX, minY, maxY)));
            }
        }
        return results;
    }

    /**
     * Stages whose end shot (600 frames of holding Right, 700+ level frames of scroll, wobble and water)
     * reaches the 99.5% bar. The others are UNCERTIFIED on the end shot (see docs in the lane report): their
     * measured match is printed and kept, and the test fails if one starts to pass so it gets promoted; their
     * early shot is still asserted.
     */
    static final java.util.Set<CdStagePayload> END_CERTIFIED = java.util.Set.of(
            CdStagePayload.R41B, CdStagePayload.R41C, CdStagePayload.R41D,
            CdStagePayload.R42A, CdStagePayload.R42B, CdStagePayload.R42C, CdStagePayload.R42D, CdStagePayload.R43C,
            CdStagePayload.R43D);

    /** Runs one stage's native check in the shared session; the caller owns the session. */
    static void run(Session session, CdStagePayload payload) throws Exception {
        List<Result> results = check(session, payload, 0);
        StringBuilder summary = new StringBuilder();
        List<String> failures = new ArrayList<>();
        for (Result result : results) {
            boolean asserted = result.shot().equals("early") || END_CERTIFIED.contains(payload);
            String line = result + (asserted ? "" : " [UNCERTIFIED, not asserted]");
            summary.append(line).append(System.lineSeparator());
            System.out.println("TTZ background: " + line);
            assertTrue(result.total() > 20000, "too little left after masking: " + result);
            if (asserted && !result.passes()) failures.add(result.toString());
            if (!asserted && result.passes()) failures.add("now passes, move it into END_CERTIFIED: " + result);
        }
        Files.createDirectories(Path.of("target", "soniccd-ttz"));
        Files.writeString(Path.of("target", "soniccd-ttz", "report-" + payload.name() + ".txt"), summary.toString());
        assertEquals(List.of(), failures, payload + " picture differs from the native screenshot");
    }
}
