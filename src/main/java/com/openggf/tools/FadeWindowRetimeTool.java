package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Retimes a controller movie authored under the double fade advance of the pre-8179ccd80
 * capture harness, then verifies the result by one uninterrupted fresh replay.
 *
 * <p>Purpose: until 8179ccd80 ({@code fix(capture): leave fade service with the production
 * loop}) {@link GameplayCaptureSession#step} advanced a runtime-ready loop's FadeManager a
 * second time, so every fade ran at double speed in direct-level captures. Movies authored
 * then budget about half of each frozen fade window; under the production single fade owner
 * every later input lands early. Inside such a window the outgoing level is frozen: neither
 * gameplay nor {@code V_int_run_count} advances, and no controller input is read.
 *
 * <p>Method: replay the movie from a fresh boot with the production harness and find the first
 * not-yet-retimed frozen fade window (FadeManager active and the object-manager VInt counter
 * unchanged across the step, on frames where the loop owns the gameplay fade lifecycle: the
 * old driver added its second advance only there). Its native length {@code n} is measured, never taken from a
 * fixture frame number. The authored movie spent {@code ceil(n / authoredFadeAdvances)} inputs
 * there; the difference is inserted as repeats of the last authored window input, so the edge
 * history at the first live frame is unchanged. The edited movie is replayed again from boot
 * for the next window. The final pass is the uninterrupted verification replay; it reports each
 * window, every full level load, the first death and the endpoint. A death or a mismatch with the
 * route's own invariants means the movie needs real re-authoring, not more retiming. Fade-in
 * windows, which run alongside live gameplay, are never edited.
 *
 * <p>Inputs: ROM, game, zone, one-based act, width, main, sidekick ({@code none} for solo),
 * input BK2/log, output BK2 (a {@code .script} is written beside it), optional
 * {@code --emeralds DDDDDDD} and {@code --authored-fade-advances N} (default 2; 1 is a no-op).
 * Origin: S3K cold LRZ→HPZ→SSZ campaign lane, 2026-10-15, after bisecting the LRZ/HPZ cold
 * route reds to 8179ccd80. This is fixture authoring; it never changes runtime behavior.
 */
public final class FadeWindowRetimeTool {
    /** Frames a window still open at the movie's end may be followed to measure its length. */
    private static final int MAX_TAIL_MEASURE = 600;

    private FadeWindowRetimeTool() { }

    /** A frozen fade window: its start in the edited movie, native length and inserted repeats. */
    public record Window(int start, int nativeLength, int authoredLength, int inserted) { }

    /** One fresh replay of a movie. */
    public record Pass(Window firstNewWindow, List<Window> windows, List<Integer> loads, int deathFrame,
                       int frames, int endX, int endY, int endRings, int endZone, int endAct, GameMode endMode) { }

    public record Result(List<Bk2FrameInput> frames, List<Window> retimed, Pass verification) { }

    public static void main(String[] args) throws Exception {
        List<String> positional = new ArrayList<>();
        String emeralds = null;
        int advances = 2;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--emeralds" -> emeralds = args[++i];
                case "--authored-fade-advances" -> advances = Integer.parseInt(args[++i]);
                default -> positional.add(args[i]);
            }
        }
        if (positional.size() != 9) {
            throw new IllegalArgumentException("Usage: FadeWindowRetimeTool ROM GAME ZONE ACT WIDTH MAIN SIDEKICK "
                    + "INPUT OUTPUT.bk2 [--emeralds DDDDDDD] [--authored-fade-advances N]");
        }
        String game = positional.get(1);
        var settings = new GameplayCaptureSession.Settings(Integer.parseInt(positional.get(4)), positional.get(5),
                "none".equalsIgnoreCase(positional.get(6)) ? "" : positional.get(6), "off", null, null, null,
                emeralds, false, false);
        var result = run(Path.of(positional.get(0)), game, positional.get(2), Integer.parseInt(positional.get(3)),
                settings, Path.of(positional.get(7)), Path.of(positional.get(8)), advances);
        System.out.println(report(result));
        var verification = result.verification();
        if (verification.deathFrame() >= 0 || verification.frames() != result.frames().size()) {
            System.exit(2);
        }
    }

    /** Retimes {@code input}, verifies it, and writes {@code output} plus its {@code .script}. */
    public static Result run(Path rom, String game, String zone, int oneBasedAct,
                             GameplayCaptureSession.Settings settings, Path input, Path output,
                             int advances) throws Exception {
        var result = retime(rom, GameplayCaptureTool.ZoneIds.resolve(game, zone), oneBasedAct - 1, settings,
                frames(input), advances);
        String header = "# Retimed by FadeWindowRetimeTool from " + input.getFileName() + " (authored fade advances "
                + advances + "); windows start:native->+inserted " + describe(result.retimed()) + "\n";
        // Keep the source script's provenance comments when the movie has one beside it.
        Path sourceScript = Path.of(input.toString().replaceFirst("\\.bk2$", "") + ".script");
        String provenance = Files.isRegularFile(sourceScript) ? Files.readAllLines(sourceScript).stream()
                .filter(line -> line.startsWith("#")).map(line -> line + "\n").reduce("", String::concat) : "";
        String script = provenance + header + script(result.frames());
        Files.writeString(Path.of(output.toString().replaceFirst("\\.bk2$", "") + ".script"), script);
        InputLogAuthorTool.author(script, output, game);
        return result;
    }

    static List<Bk2FrameInput> frames(Path input) throws Exception {
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(input);
        List<Bk2FrameInput> frames = new ArrayList<>(movie.getFrameCount());
        for (int i = 0; i < movie.getFrameCount(); i++) frames.add(movie.getFrame(i));
        return frames;
    }

    public static Result retime(Path rom, int zone, int act, GameplayCaptureSession.Settings settings,
                                List<Bk2FrameInput> original, int authoredFadeAdvances) throws Exception {
        if (authoredFadeAdvances < 1) throw new IllegalArgumentException("authored fade advances must be positive");
        List<Bk2FrameInput> frames = new ArrayList<>(original);
        List<Window> retimed = new ArrayList<>();
        Set<Integer> processed = new HashSet<>();
        while (true) {
            Pass pass = replay(rom, zone, act, settings, frames, processed);
            Window found = pass.firstNewWindow();
            if (found == null) return new Result(List.copyOf(frames), List.copyOf(retimed), pass);
            processed.add(found.start());
            retimed.add(insertRepeats(frames, found.start(), found.nativeLength(), authoredFadeAdvances));
        }
    }

    /**
     * The authored movie spent {@code ceil(n / advances)} inputs in a native {@code n}-frame window;
     * repeat its last window input for the remainder so later inputs meet the same live frames.
     */
    static Window insertRepeats(List<Bk2FrameInput> frames, int start, int nativeLength, int advances) {
        int authored = Math.ceilDiv(nativeLength, advances);
        int extra = nativeLength - authored;
        int at = start + authored;
        if (extra <= 0 || at > frames.size()) return new Window(start, nativeLength, authored, 0);
        var held = frames.get(at - 1);
        for (int n = 0; n < extra; n++) frames.add(at, held);
        return new Window(start, nativeLength, authored, extra);
    }

    /**
     * One uninterrupted replay from a fresh boot. Stops at the first window whose start is not in
     * {@code processed}, otherwise runs every frame and reports the verification state.
     */
    static Pass replay(Path rom, int zone, int act, GameplayCaptureSession.Settings settings,
                       List<Bk2FrameInput> frames, Set<Integer> processed) throws Exception {
        List<Window> windows = new ArrayList<>();
        List<Integer> loads = new ArrayList<>();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, zone, act, settings);
            Object level = GameServices.level().getCurrentLevel();
            int vint = vint();
            int start = -1, length = 0, death = -1, frame = 0;
            // A movie that ends inside an unretimed window is measured past its end with the last
            // input held (those probe frames are never recorded); the insertion stays inside it.
            for (; frame < frames.size() || (length > 0 && !processed.contains(start)
                    && frame < frames.size() + MAX_TAIL_MEASURE); frame++) {
                // The old driver added its second fade advance only while the loop owned the
                // gameplay fade lifecycle; frames outside that ownership kept native timing.
                boolean doubledByOldDriver = session.loop().ownsGameplayFadeLifecycle();
                session.step(indexed(frames.get(Math.min(frame, frames.size() - 1)), frame));
                int now = vint();
                var fade = GameServices.fadeOrNull();
                boolean frozen = doubledByOldDriver && fade != null && fade.isActive() && now == vint;
                vint = now;
                Object current = GameServices.levelOrNull() == null ? null : GameServices.level().getCurrentLevel();
                if (current != level) {
                    loads.add(frame);
                    level = current;
                }
                if (frozen) {
                    if (length++ == 0) start = frame;
                } else if (length > 0) {
                    var window = new Window(start, length, -1, 0);
                    windows.add(window);
                    if (!processed.contains(start)) {
                        return new Pass(window, windows, loads, -1, frame + 1, 0, 0, 0, -1, -1, null);
                    }
                    length = 0;
                }
                if (session.player().getDead()) {
                    death = frame;
                    frame++;
                    break;
                }
            }
            var player = session.player();
            var levelManager = GameServices.level();
            return new Pass(null, windows, loads, death, Math.min(frame, frames.size()), player.getCentreX(), player.getCentreY(),
                    player.getRingCount(), levelManager.getCurrentZone(), levelManager.getCurrentAct(),
                    session.loop().getCurrentGameMode());
        }
    }

    /** Inserted repeats keep their source index; give every replayed row its movie position. */
    private static Bk2FrameInput indexed(Bk2FrameInput f, int index) {
        return new Bk2FrameInput(index, f.p1InputMask(), f.p1ActionMask(), f.p1StartPressed(), f.p2InputMask(),
                f.p2ActionMask(), f.p2StartPressed(), f.debugModeTogglePressed(), f.debugShiftDown(),
                f.debugControlDown(), f.debugAltDown(), f.debugSuperDown(), f.rawLine());
    }

    private static int vint() {
        var level = GameServices.levelOrNull();
        return level == null || level.getObjectManager() == null ? Integer.MIN_VALUE
                : level.getObjectManager().getVblaCounter();
    }

    static String report(Result result) {
        var v = result.verification();
        return "FADE-RETIME windows=" + describe(result.retimed()) + " frames=" + result.frames().size()
                + " verification: replayed=" + v.frames() + " loads=" + v.loads() + " death=" + v.deathFrame()
                + " end=" + v.endX() + "," + v.endY() + " rings=" + v.endRings() + " zone=" + v.endZone()
                + "/" + v.endAct() + " mode=" + v.endMode();
    }

    private static String describe(List<Window> windows) {
        var text = new StringBuilder("[");
        for (var w : windows) {
            if (text.length() > 1) text.append(' ');
            text.append(w.start()).append(':').append(w.nativeLength()).append("->+").append(w.inserted());
        }
        return text.append(']').toString();
    }

    /** InputLogAuthorTool script with explicit P1/P2 lanes, one run per line. */
    static String script(List<Bk2FrameInput> frames) {
        var result = new StringBuilder();
        String previous = null;
        int count = 0;
        for (var frame : frames) {
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
        for (int i = 0; i < directionNames.length; i++) if ((directions & (1 << i)) != 0) held.add(directionNames[i]);
        for (int i = 0; i < actionNames.length; i++) if ((actions & (1 << i)) != 0) held.add(actionNames[i]);
        if (start) held.add("S");
        return held.isEmpty() ? "-" : String.join("+", held);
    }
}
