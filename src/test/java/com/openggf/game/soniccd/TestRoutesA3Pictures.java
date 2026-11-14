package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.tests.trace.TraceReplayFrameClosureDriver;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceBinder;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glFinish;

/**
 * Native pictures of the objects the full-act routes (native/rehost-long) of Palmtree Panic, Collision Chaos and Tidal Tempest
 * meet and the earlier pictures did not cover: the Palmtree Panic swing, platform, tunnel path and collapsing platform (and its
 * pieces), the Collision Chaos block, hidden block and spikes, and Tidal Tempest's switch, door, collapse floor (and its pieces),
 * horizontal move blocks, animal and Taga-Taga with its missiles. The shots are the routes' own movies retaken at chosen trace rows
 * ({@code native/rehost-fixtures-routesa3/<payload>/shots.json}, user-local, not committed; the test skips without them) by
 * {@code rehost-fixtures-routesa3/_tools/take_shots.py}, rows picked by {@code pick_rows.py} from the object_near events of the
 * route's trace (a native object of the kind on screen); every shot's player X/Y, camera X/Y and level frame counter equal its trace
 * row. The engine plays the route's input (comparison only: nothing read from {@code physics.csv} reaches the engine except the
 * BK2 buttons, as in {@link TestCczObjectsPictures}) up to each shot's level frame and is compared only while its player and camera
 * still equal the trace (checked on the last row of each level frame, the completed one). Pairing, the one rule every Sonic CD
 * picture test uses: a picture shows the state after the level frame before the one its row counts (Level/Main.asm:829-831).
 * No shot is chosen by agreement or dropped. One headless GL context serves the whole class, so the stages load one after another
 * in one session; a stage whose replay leaves the native route earlier than its own full-act replay does (R31C, R32A, R42A, which
 * the fixture replay, with a session of its own, replays green or further) reports where, and its later shots are not compared.
 *
 * <p>What is compared: the pixels this lane's objects draw. The engine frame is rendered twice more, tiles only and object sprites
 * over those tiles; where the two differ an object sprite is drawn. Those pixels inside a box around each of this lane's on-screen
 * objects, minus the player's box and the HUD, are compared with the native frame at 3 bits per channel against the full engine
 * frame.
 */
@EnabledOnOs(OS.LINUX)
class TestRoutesA3Pictures {
    private static final CdStagePayload[] PAYLOADS = {CdStagePayload.R11A, CdStagePayload.R11B, CdStagePayload.R11C,
            CdStagePayload.R12A, CdStagePayload.R31A, CdStagePayload.R31B, CdStagePayload.R31C, CdStagePayload.R32A,
            CdStagePayload.R41A, CdStagePayload.R41B, CdStagePayload.R41C, CdStagePayload.R42A};
    /** This lane's classes, by the name after {@code .objects.} (nested classes with {@code $}). */
    private static final java.util.Set<String> KINDS = java.util.Set.of("ppz.PpzSwing", "ppz.PpzPlatform", "ppz.PpzTunnelPath",
            "ppz.ObjCollapsePlatform", "ccz.CczBlock", "ccz.CczHiddenBlock", "ccz.CczSpikes", "ttz.TtzSwitch", "ttz.TtzDoor",
            "ttz.TtzAnimal", "ttz.TtzCollapseFloor", "ttz.TtzCollapseFloor$Piece", "ttz.TtzHMoveBlock", "ttz.ObjTagaTaga", "ttz.ObjTagaTaga$Missile");
    private static final int BOX = 0x30;

    static int level(int argb) {
        int r = Math.round(((argb >> 16) & 0xFF) * 7f / 255f);
        int g = Math.round(((argb >> 8) & 0xFF) * 7f / 255f);
        int b = Math.round((argb & 0xFF) * 7f / 255f);
        return (r << 6) | (g << 3) | b;
    }

    /** A native shot: the engine level frame it shows ({@link #pictureLevelFrame}), its file and the row's own lf. */
    private record Shot(int levelFrames, Path file, int rowLevelFrames) { }

    /** Probe frame {@code offset + row + 1} of the trace run is the row's own picture: the state after lf(row)-1. */
    private static int pictureLevelFrame(List<Integer> counters, int traceRow) {
        return counters.get(traceRow) - 1;
    }

    /** {@code gameplay_frame_counter} of every trace row. */
    private static List<Integer> counters(Path csv) throws Exception {
        List<String> lines = Files.readAllLines(csv);
        int counter = List.of(lines.getFirst().split(",")).indexOf("gameplay_frame_counter");
        List<Integer> out = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) out.add(Integer.parseInt(line.split(",")[counter], 16));
        return out;
    }

    /**
     * Shots (stage@row level frame) whose native frame does not show the trace's state, so no level frame pairs them.
     * They are compared and reported apart from the kinds; each must still fail (below 95% of its object pixels), or the
     * test fails so it gets promoted. None: every retaken shot is on its trace row (class comment).
     */
    private static final Map<String, String> UNCERTIFIED = Map.of();

    /**
     * Kinds that are measured but not certified, with the floor they must keep (percent of compared pixels). Horizontal move
     * block ($24): the shots at the bottom of the screen (R41B@1469, @1692) show an engine block row where native draws none, and
     * R42A@805/@816 differ on blocks beside the player; the other R41B shots (@1431, @1520, @1640) equal native. The first
     * suspect is the VDP's sprite table limit (the blocks are many pieces each; the engine has no sprite limit, which the
     * reimplementation boundary leaves out), not established. A kind in this map fails when it reaches 95% (promote it).
     */
    private static final Map<String, Integer> KNOWN_PARTIAL = Map.of("ttz.TtzHMoveBlock", 80);

    /** Object pixels compared and equal per uncertified shot. */
    private static final Map<String, int[]> UNCERTIFIED_SCORES = new LinkedHashMap<>();

    /** Pixels compared and equal, per kind of object. */
    private static final class Tally {
        int same;
        int total;
        final List<String> shots = new ArrayList<>();
    }

    @Test
    void objectSpritesMatchTheNativeFrames() throws Exception {
        UNCERTIFIED_SCORES.clear();
        Path cue = CdTestMedia.usaCue();
        Map<CdStagePayload, List<Shot>> shots = new LinkedHashMap<>();
        Map<CdStagePayload, Path> routes = new LinkedHashMap<>();
        for (CdStagePayload payload : PAYLOADS) {
            Path index = CdTestMedia.nativeFile("rehost-fixtures-routesa3/" + payload + "/shots.json");
            List<Integer> counters = counters(CdTestMedia.nativeFile("rehost-long/" + payload + "/trace/physics.csv"));
            List<Shot> list = new ArrayList<>();
            for (JsonNode shot : new ObjectMapper().readTree(index.toFile()).get("shots")) {
                list.add(new Shot(pictureLevelFrame(counters, shot.get("trace_row").asInt()),
                        index.resolveSibling(shot.get("file").asText()), shot.get("level_frames").asInt()));
            }
            list.sort(java.util.Comparator.comparingInt(Shot::levelFrames));
            shots.put(payload, list);
            routes.put(payload, CdTestMedia.nativeFile("rehost-long/" + payload + "/movie.bk2").getParent());
        }

        String oldGl = System.getProperty("openggf.headless.gl");
        boolean oldAudio = GameServices.configuration().getBoolean(SonicConfiguration.AUDIO_ENABLED);
        System.setProperty("openggf.headless.gl", "surfaceless-egl");
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        Map<String, Tally> kinds = new LinkedHashMap<>();
        List<String> report = new ArrayList<>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue);
             CdFreshStageBoots boots = new CdFreshStageBoots(320, 224)) {
            for (CdStagePayload payload : PAYLOADS) {
                boots.boot(disc, payload);
                playStage(payload, shots.get(payload), routes.get(payload), kinds, report);
            }
        } finally {
            GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, oldAudio);
            if (oldGl == null) System.clearProperty("openggf.headless.gl");
            else System.setProperty("openggf.headless.gl", oldGl);
        }

        StringBuilder text = new StringBuilder();
        List<String> failures = new ArrayList<>();
        for (var entry : UNCERTIFIED_SCORES.entrySet()) {
            int[] score = entry.getValue();
            boolean passes = score[1] > 0 && score[0] * 100L >= score[1] * 95L;
            text.append(String.format("UNCERTIFIED %s %d/%d: %s%n", entry.getKey(), score[0], score[1],
                    UNCERTIFIED.get(entry.getKey())));
            if (passes) failures.add(entry.getKey() + " is listed as uncertified but now passes: " + score[0] + "/" + score[1]);
        }
        for (var entry : kinds.entrySet()) {
            Tally t = entry.getValue();
            text.append(String.format("%s %d/%d over %d shots (%s)%n", entry.getKey(), t.same, t.total, t.shots.size(),
                    String.join(", ", t.shots)));
            Integer floor = KNOWN_PARTIAL.get(entry.getKey());
            if (floor != null) {
                text.append("  NOT CERTIFIED (floor ").append(floor).append("%)\n");
                if (t.same * 100L >= t.total * 95L) failures.add(entry.getKey() + " now passes " + t.same + "/" + t.total + ": promote it");
                if (t.same * 100L < t.total * floor) failures.add(entry.getKey() + " fell below its floor: " + t.same + "/" + t.total);
            } else if (t.total >= 150 && t.same * 100L < t.total * 95L) {
                failures.add(entry.getKey() + " " + t.same + "/" + t.total);
            }
        }
        Path out = Path.of("target", "soniccd-routesa3");
        Files.createDirectories(out);
        Files.writeString(out.resolve("report.txt"), String.join("\n", report) + "\n" + text);
        System.out.println("ROUTESA3 pictures:\n" + String.join("\n", report) + "\n" + text);
        assertTrue(!kinds.isEmpty(), "no object of this lane was on screen in any shot");
        assertTrue(failures.isEmpty(), "kinds below 95%: " + failures + "\n" + text);
    }

    private static String kindName(ObjectInstance o) {
        String name = o.getClass().getName();
        int at = name.indexOf(".objects.");
        return at < 0 ? name : name.substring(at + ".objects.".length());
    }

    /** The engine's three renders of one level frame and the boxes of this lane's on-screen objects. */
    private record Snap(RgbaImage tiles, RgbaImage objectsOnly, RgbaImage full, List<int[]> boxes, List<String> names,
                        int psx, int psy) { }

    /**
     * Replays the route's own movie as the fixture replay does ({@code CdFixtureReplay.replay}: trace rows are comparison data and
     * give only the row phases, lag rows run the V-blank only) and renders each shot's level frame, once the engine reaches it, while the
     * player and camera equal the trace's completed level frames.
     */
    private static void playStage(CdStagePayload payload, List<Shot> shots, Path route, Map<String, Tally> kinds,
                                  List<String> report) throws Exception {
        AbstractPlayableSprite sonic = GameServices.camera().getFocusedSprite();
        HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
        TraceData trace = TraceData.load(route.resolve("trace"));
        Bk2Movie movie = new Bk2MovieLoader().load(route.resolve("movie.bk2"));
        runner.setBk2Movie(movie, trace.metadata().bk2FrameOffset());
        TraceBinder binder = new TraceBinder(
                ToleranceConfig.DEFAULT.withRingCountMode(ToleranceConfig.RingCountMode.DISABLED));
        int next = 0;
        for (int i = 0; i < trace.frameCount() && next < shots.size(); i++) {
            TraceFrame expected = trace.getFrame(i);
            TraceFrame prior = i > 0 ? trace.getFrame(i - 1) : null;
            TraceExecutionPhase phase;
            if (i == 0) {
                phase = TraceExecutionPhase.FULL_LEVEL_FRAME;
                runner.consumeRecordingFrameInputOnly();
            } else {
                phase = TraceReplayBootstrap.phaseForReplay(trace, prior, expected);
                TraceReplayFrameClosureDriver.driveGeneral(phase, runner::stepFrameFromRecording,
                        runner::skipFrameFromRecording, () -> { });
            }
            if (TraceReplayBootstrap.shouldCompareGameplayStateForReplay(phase)
                    && CdFixtureReplay.compare(binder, CdFixtureReplay.completedLevelFrame(trace, i, prior, expected, phase),
                            sonic).hasError()) {
                report.add(payload + ": engine left the native route at row " + i + "; later shots not compared");
                return;
            }
            int levelFrame = GameServices.level().getFrameCounter();
            while (next < shots.size() && levelFrame == shots.get(next).levelFrames()) {
                compare(payload, shots.get(next), capture(sonic), kinds, report);
                next++;
            }
        }
        if (next < shots.size()) report.add(payload + ": " + (shots.size() - next) + " shots after the replay's last row not compared");
    }

    private static Snap capture(AbstractPlayableSprite sonic) {
        int cx = GameServices.camera().getX(), cy = GameServices.camera().getY();
        List<int[]> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ObjectInstance o : GameServices.level().getObjectManager().getActiveObjects()) {
            if (o.isDestroyed() || !KINDS.contains(kindName(o))) continue;
            int sx = o.getX() - cx, sy = o.getY() - cy;
            if (sx < -BOX || sx > 320 + BOX || sy < -BOX || sy > 224 + BOX) continue;
            boxes.add(new int[] {sx - BOX, sy - BOX, sx + BOX, sy + BOX});
            names.add(kindName(o));
        }
        return new Snap(render(LevelManager.LevelRenderOptions.tilesOnly()),
                render(new LevelManager.LevelRenderOptions(false, true, false, false, false, false, false)),
                render(LevelManager.LevelRenderOptions.gameplay()), boxes, names,
                sonic.getCentreX() - cx, sonic.getCentreY() - cy);
    }

    /** Terrain agreement with the native frame: pixels no sprite covers, outside the HUD, the player and the lane's boxes. */
    private static double terrainAgreement(Snap snap, RgbaImage reference) {
        int same = 0, total = 0;
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                if (snap.tiles().argb(x, y) != snap.objectsOnly().argb(x, y)) continue;      // a sprite covers it
                if ((x < 128 && y < 56) || (x < 64 && y >= 200)) continue;                  // HUD
                if (Math.abs(x - snap.psx()) < 24 && y > snap.psy() - 32 && y < snap.psy() + 32) continue;
                boolean inBox = false;
                for (int[] b : snap.boxes()) inBox |= x >= b[0] && x < b[2] && y >= b[1] && y < b[3];
                if (inBox) continue;
                total++;
                if (level(reference.argb(x, y)) == level(snap.full().argb(x, y))) same++;
            }
        }
        return total == 0 ? 0 : (double) same / total;
    }

    private static void compare(CdStagePayload payload, Shot shot, Snap snap, Map<String, Tally> kinds,
                                List<String> report) throws Exception {
        RgbaImage reference = ScreenshotCapture.loadPNG(shot.file());
        if (snap.boxes().isEmpty()) return;
        double terrain = terrainAgreement(snap, reference);
        Path out = Path.of("target", "soniccd-routesa3");
        Files.createDirectories(out);
        ScreenshotCapture.savePNG(snap.full(), out.resolve("engine-" + payload + "-lf" + shot.levelFrames() + ".png"));
        RgbaImage tiles = snap.tiles(), objectsOnly = snap.objectsOnly(), full = snap.full();
        int psx = snap.psx(), psy = snap.psy();
        List<int[]> boxes = snap.boxes();
        List<String> names = snap.names();
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            int same = 0, total = 0;
            for (int y = Math.max(0, b[1]); y < Math.min(224, b[3]); y++) {
                for (int x = Math.max(0, b[0]); x < Math.min(320, b[2]); x++) {
                    if (tiles.argb(x, y) == objectsOnly.argb(x, y)) continue;           // no object sprite here
                    if (Math.abs(x - psx) < 24 && y > psy - 32 && y < psy + 32) continue;  // the player is on top
                    if ((x < 128 && y < 56) || (x < 64 && y >= 200)) continue;            // HUD
                    total++;
                    if (level(reference.argb(x, y)) == level(full.argb(x, y))) same++;
                }
            }
            if (total == 0) continue;
            String shotKey = payload + "@" + shot.rowLevelFrames();
            if (UNCERTIFIED.containsKey(shotKey)) {
                int[] score = UNCERTIFIED_SCORES.computeIfAbsent(shotKey, kk -> new int[2]);
                score[0] += same;
                score[1] += total;
                report.add(String.format("%s row lf %d = engine lf %d (terrain %.2f) %s box(%d,%d): %d/%d [UNCERTIFIED]",
                        payload, shot.rowLevelFrames(), shot.levelFrames(), terrain, names.get(i), (b[0] + b[2]) / 2,
                        (b[1] + b[3]) / 2, same, total));
                continue;
            }
            Tally t = kinds.computeIfAbsent(names.get(i), kk -> new Tally());
            t.same += same;
            t.total += total;
            String label = payload + "@" + shot.rowLevelFrames();
            if (!t.shots.contains(label)) t.shots.add(label);
            report.add(String.format("%s row lf %d = engine lf %d (terrain %.2f) %s box(%d,%d): %d/%d", payload,
                    shot.rowLevelFrames(), shot.levelFrames(), terrain, names.get(i), (b[0] + b[2]) / 2, (b[1] + b[3]) / 2,
                    same, total));
        }
    }

    private static RgbaImage render(LevelManager.LevelRenderOptions options) {
        GraphicsManager graphics = GameServices.graphics();
        graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
        graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
        var level = GameServices.level();
        level.setClearColor();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithRenderOptions(GameServices.sprites(), options);
        graphics.flush();
        if (graphics.getUiRenderPipeline() != null) graphics.getUiRenderPipeline().renderFadePass();
        glFinish();
        return ScreenshotCapture.captureFramebuffer(320, 224);
    }
}
