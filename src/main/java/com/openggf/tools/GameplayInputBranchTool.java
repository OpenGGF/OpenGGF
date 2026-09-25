package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.ScreenshotCapture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Tries controller scripts from one cold-replayed prefix using engine-owned rewind state.
 * Origin: S&K bring-up, LRZ2 route authoring, 2026-09-24. No ROM/trace state is injected.
 * Inputs: ROM, game, zone, one-based act, width, main, sidekick, BK2, prefix length,
 * output directory, then one or more InputLogAuthorTool scripts. Each result includes
 * the complete prefix in its script/BK2/CSV; PNGs sample the candidate every30 frames.
 * A chosen candidate still needs a fresh uninterrupted replay and route certification.
 */
public final class GameplayInputBranchTool {
    private GameplayInputBranchTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 11) {
            throw new IllegalArgumentException("Usage: GameplayInputBranchTool ROM GAME ZONE ACT WIDTH "
                    + "MAIN SIDEKICK INPUT PREFIX OUT_DIR SCRIPT [SCRIPT ...]");
        }
        var settings = new GameplayCaptureSession.Settings(Integer.parseInt(args[4]), args[5],
                "none".equalsIgnoreCase(args[6]) ? "" : args[6], "off", null, null, null);
        run(Path.of(args[0]), args[1], GameplayCaptureTool.ZoneIds.resolve(args[1], args[2]),
                Integer.parseInt(args[3]) - 1, settings, Path.of(args[7]), Integer.parseInt(args[8]),
                Path.of(args[9]), List.of(args).subList(10, args.length));
    }

    static void run(Path rom, String game, int zone, int act, GameplayCaptureSession.Settings settings,
                    Path input, int prefix, Path output, List<String> candidates) throws Exception {
        var loader = new Bk2MovieLoader();
        var baseMovie = loader.loadMovieOrInputLog(input);
        String baseScript = prefixScript(baseMovie, prefix);
        if (candidates.isEmpty()) throw new IllegalArgumentException("No candidate scripts");
        if (settings.startX() != null || settings.startY() != null) {
            throw new IllegalArgumentException("Branch authoring requires a cold entry");
        }
        Files.createDirectories(output);
        try (var existing = Files.list(output)) {
            if (existing.findAny().isPresent()) {
                throw new IllegalArgumentException("Output directory is not empty: " + output);
            }
        }
        var prefixRows = new StringBuilder(GameplayCaptureSession.stateHeader()).append('\n');
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, zone, act, settings);
            for (int frame = 0; frame < prefix; frame++) {
                var pad = baseMovie.getFrame(frame);
                session.step(pad);
                session.render();
                prefixRows.append(session.stateLine(frame, pad)).append('\n');
                if (session.player().getDead()) {
                    throw new IllegalStateException("Cold prefix died at input " + frame);
                }
            }
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var checkpoint = registry.capture();
            var checkpointLevel = GameServices.level().getCurrentLevel();
            var previousInput = prefix == 0 ? null : baseMovie.getFrame(prefix - 1);
            var objects = new StringBuilder();
            for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
                if (object.getSpawn() != null
                        && Math.abs(object.getX() - session.player().getCentreX()) <= 512) {
                    objects.append(object.getClass().getSimpleName()).append(' ')
                            .append(object.getX()).append(',').append(object.getY())
                            .append(" spawn=").append(object.getSpawn()).append('\n');
                }
            }
            Files.writeString(output.resolve("prefix-objects.txt"), objects, StandardOpenOption.CREATE_NEW);
            for (int i = 0; i < candidates.size(); i++) {
                if (GameServices.level().getCurrentLevel() != checkpointLevel) {
                    throw new IllegalStateException("Previous candidate reloaded the level; "
                            + "start a fresh probe rather than restoring across a load boundary");
                }
                registry.restore(checkpoint);
                session.restoreInputHistory(previousInput);
                String script = baseScript + candidates.get(i) + '\n';
                Path stem = output.resolve("variant-" + i);
                Files.writeString(Path.of(stem + ".script"), script, StandardOpenOption.CREATE_NEW);
                InputLogAuthorTool.author(script, Path.of(stem + ".bk2"), game);
                var movie = loader.loadMovieOrInputLog(Path.of(stem + ".bk2"));
                var rows = new StringBuilder(prefixRows);
                for (int frame = prefix; frame < movie.getFrameCount(); frame++) {
                    var pad = movie.getFrame(frame);
                    session.step(pad);
                    var picture = session.render();
                    rows.append(session.stateLine(frame, pad)).append('\n');
                    boolean terminal = session.player().getDead()
                            || GameServices.level().getCurrentLevel() != checkpointLevel;
                    if ((frame - prefix) % 30 == 0 || frame == movie.getFrameCount() - 1 || terminal) {
                        ScreenshotCapture.savePNG(picture, Path.of(stem + "-" + frame + ".png"));
                        System.out.printf("variant=%d input=%d x=%d y=%d dead=%s%n", i, frame,
                                session.player().getCentreX(), session.player().getCentreY(),
                                session.player().getDead());
                    }
                    if (terminal) break;
                }
                Files.writeString(Path.of(stem + ".csv"), rows, StandardOpenOption.CREATE_NEW);
            }
        }
    }

    static String prefixScript(Bk2Movie movie, int prefix) {
        if (prefix < 0 || prefix > movie.getFrameCount()) {
            throw new IllegalArgumentException("Prefix outside input movie: " + prefix);
        }
        var result = new StringBuilder();
        String previous = null;
        int count = 0;
        for (int i = 0; i < prefix; i++) {
            var frame = movie.getFrame(i);
            if (frame.debugModeTogglePressed() || frame.debugShiftDown() || frame.debugControlDown()
                    || frame.debugAltDown() || frame.debugSuperDown()) {
                throw new IllegalArgumentException("Debug input is not controller-only: " + i);
            }
            String buttons = buttons(frame.p1InputMask(), frame.p1ActionMask(), frame.p1StartPressed())
                    + " / " + buttons(frame.p2InputMask(), frame.p2ActionMask(), frame.p2StartPressed());
            if (!buttons.equals(previous) && count != 0) {
                result.append(count).append(' ').append(previous).append('\n');
                count = 0;
            }
            previous = buttons;
            count++;
        }
        if (count != 0) result.append(count).append(' ').append(previous).append('\n');
        return result.toString();
    }

    private static String buttons(int directions, int actions, boolean start) {
        var held = new ArrayList<String>();
        String[] directionNames = {"U", "D", "L", "R"};
        String[] actionNames = {"A", "B", "C"};
        for (int i = 0; i < directionNames.length; i++) {
            if ((directions & (1 << i)) != 0) held.add(directionNames[i]);
        }
        for (int i = 0; i < actionNames.length; i++) {
            if ((actions & (1 << i)) != 0) held.add(actionNames[i]);
        }
        if (start) held.add("S");
        return held.isEmpty() ? "-" : String.join("+", held);
    }
}
