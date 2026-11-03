package com.openggf.tools;

import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

/**
 * Records a route movie's engine track: after every row, each player's exact state (centre,
 * 16-bit subpixels, raw speeds, air and roll bits) and the boundary epoch, stopping after a P1
 * death. {@link RouteTrackReference} reads it as a repair reference. Run it on the engine the
 * route's rows were authored against, typically in a temporary checkout of that revision: the
 * class uses only the capture session, the movie loader and the service locator, which that
 * engine already has, so the single source file compiles there unchanged. A track is a scoring
 * target only; it never boots, seeds or restores a world, and it is not trace data.
 *
 * <p>Origin: shared route planner step 6 (2026-11-02). The SSZ1 route tracks were first recorded
 * on 36aaddb37 with P1's centre only; the exact re-synchronising repair needs the full state.
 *
 * <pre>
 * java -cp ... com.openggf.tools.RouteTrackRecorder ROM MOVIE.bk2 OUT.csv.gz ZONE ACT WIDTH MAIN SIDEKICK|none ENGINE
 * </pre>
 * {@code ZONE} and {@code ACT} are the boot level (zero-based act); {@code ENGINE} is the revision
 * written into the header. The run boots cold with {@code V_int_run_count} 0 and draws nothing.
 */
public final class RouteTrackRecorder {
    /** The column set; P2 columns are empty on rows without a sidekick. */
    public static final String COLUMNS = "row,x,y,epoch,dead,xsub,ysub,xvel,yvel,gvel,air,roll,"
            + "p2x,p2y,p2xsub,p2ysub,p2xvel,p2yvel,p2gvel,p2air,p2roll";

    private RouteTrackRecorder() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 9) {
            throw new IllegalArgumentException("Usage: RouteTrackRecorder ROM MOVIE OUT.csv.gz ZONE ACT WIDTH MAIN "
                    + "SIDEKICK|none ENGINE");
        }
        Path rom = Path.of(args[0]);
        Path movie = Path.of(args[1]);
        Path out = Path.of(args[2]);
        int zone = Integer.parseInt(args[3]);
        int act = Integer.parseInt(args[4]);
        int width = Integer.parseInt(args[5]);
        String main = args[6];
        String sidekick = args[7].equals("none") ? "" : args[7];
        String engine = args[8];
        var loaded = new Bk2MovieLoader().loadMovieOrInputLog(movie);
        var settings = new GameplayCaptureSession.Settings(width, main, sidekick, "off", null, null, null, null, false,
                false, 0, null);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(movie)));
        try (var session = new GameplayCaptureSession(settings);
             var w = new PrintWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(out)),
                     StandardCharsets.UTF_8))) {
            session.boot(rom, zone, act, settings);
            w.println("# engine " + engine + " movie " + movie.getFileName() + " sha256 " + sha + " zone " + zone
                    + " width " + width + " roster " + main + "/" + sidekick + " vint 0");
            w.println(COLUMNS);
            Object level = GameServices.level().getCurrentLevel();
            Object mode = session.loop().getCurrentGameMode();
            Object context = SessionManager.getCurrentGameplayMode();
            long epoch = 0;
            for (int f = 0; f < loaded.getFrameCount(); f++) {
                session.step(loaded.getFrame(f));
                GameServices.graphics().runPendingRenderThreadTasks();
                Object l = GameServices.level().getCurrentLevel();
                Object m = session.loop().getCurrentGameMode();
                Object c = SessionManager.getCurrentGameplayMode();
                if (l != level || m != mode || c != context) {
                    epoch++;
                    level = l;
                    mode = m;
                    context = c;
                }
                AbstractPlayableSprite p1 = session.player();
                AbstractPlayableSprite p2 = null;
                for (AbstractPlayableSprite s : GameServices.sprites().getRegisteredSidekicks()) {
                    if (s != p1) {
                        p2 = s;
                        break;
                    }
                }
                var line = new StringBuilder().append(f).append(',').append(p1.getCentreX()).append(',')
                        .append(p1.getCentreY()).append(',').append(epoch).append(',').append(p1.getDead() ? 1 : 0);
                state(line, p1);
                if (p2 != null) {
                    line.append(',').append(p2.getCentreX()).append(',').append(p2.getCentreY());
                    state(line, p2);
                } else {
                    line.append(",,,,,,,,,");
                }
                w.println(line);
                if (p1.getDead()) break;
            }
        }
    }

    private static void state(StringBuilder line, AbstractPlayableSprite p) {
        line.append(',').append(p.getXSubpixelRaw()).append(',').append(p.getYSubpixelRaw()).append(',')
                .append(p.getXSpeed()).append(',').append(p.getYSpeed()).append(',').append(p.getGSpeed()).append(',')
                .append(p.getAir() ? 1 : 0).append(',').append(p.getRolling() ? 1 : 0);
    }
}
