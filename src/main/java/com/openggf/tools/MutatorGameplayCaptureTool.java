package com.openggf.tools;

/*
 * MutatorGameplayCaptureTool — Mutator Lab gameplay-promo capture driver (task e0603654, opus_gameplay_promo_r1).
 * Purpose: run the production GameplayCaptureSession exactly as GameplayCaptureTool.run does
 * (same arguments, one native tick per input frame, PNG every frame, offline PCM per outer frame,
 * identical state.csv), and add observe.csv with semantic values read from native owners:
 * score, live/slotless spilled-ring objects, admitted head scale/gravity, admitted gameplay
 * mutator policy (ringfall/rebound) and live badnik count. Inputs: GameplayCaptureTool CLI flags.
 * Never writes gameplay state; observation only.
 */
import com.openggf.debug.playback.*;
import com.openggf.game.GameServices;
import com.openggf.game.mutators.GameplayMutatorPolicySource;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

public final class MutatorGameplayCaptureTool {
    public static void main(String[] argv) throws Exception {
        var a = GameplayCaptureTool.Arguments.parse(argv);
        requireDisplayFreeContext();
        Bk2Movie movie = a.input() == null ? null : new Bk2MovieLoader().loadMovieOrInputLog(a.input());
        int scriptFrames = movie == null ? 0 : Math.max(0, movie.getFrameCount() - a.inputStart());
        int total = a.frames() != null ? a.frames() : a.settle() + scriptFrames;
        Path out = a.outDir(), frames = out.resolve("frames");
        Files.createDirectories(frames);
        var settings = new GameplayCaptureSession.Settings(a.width(), a.mainCharacter(), a.sidekickCharacter(),
                a.donor(), null, a.startX(), a.startY(), a.emeralds(), a.titleCard(), a.completeSpecialStage(),
                a.vIntRunCount(), a.cameraXSub(), a.starPost(), a.rings(), a.reverseGravity());
        int zone = GameplayCaptureTool.ZoneIds.resolve(a.game(), a.zone());
        int dead = -1;
        try (var session = new GameplayCaptureSession(settings);
             var state = Files.newBufferedWriter(out.resolve("state.csv"), StandardCharsets.UTF_8);
             var obs = Files.newBufferedWriter(out.resolve("observe.csv"), StandardCharsets.UTF_8);
             var bads = Files.newBufferedWriter(out.resolve("badniks.csv"), StandardCharsets.UTF_8)) {
            bads.write("frame,class,x,y"); bads.newLine();
            if (a.mod() != null) session.applyMod(a.mod());
            if (a.titleScreen()) session.startAtTitle();
            if (a.audio()) session.enableAudio();
            session.boot(a.rom(), zone, a.act(), settings);
            var audio = GameServices.audio();
            try (var capture = a.audio() ? audio.beginLiveCaptureAudio(a.fps()) : null;
                 var pcm = a.audio() ? new BufferedOutputStream(Files.newOutputStream(out.resolve("audio.pcm"))) : OutputStream.nullOutputStream()) {
                short[] samples = capture == null ? new short[0] : new short[capture.maxStereoFramesPerPacket() * 2];
                state.write(a.titleScreen() ? GameplayCaptureSession.stateHeaderWithHostState() : GameplayCaptureSession.stateHeader());
                state.newLine();
                obs.write("frame,mode,rings,score,lost_rings,lost_rings_slotless,lost_rings_collectible,head_scale,gravity_pct,"
                        + "ringfall_pct,ringfall_cap,ringfall_full,rebound_pct,rebound_cap,badniks,x,y,xvel,yvel,air,hurt,dead,cam_x,cam_y,near_badnik,near_dx,near_dy");
                obs.newLine();
                for (int f = 0; f < total; f++) {
                    int si = f - a.settle();
                    Bk2FrameInput in = movie != null && si >= 0 && si < scriptFrames ? movie.getFrame(si + a.inputStart()) : null;
                    session.step(in);
                    if (a.audio()) {
                        session.loop().presentOuterFrame(false, false);
                        int n = capture.drainPresentationFrame(samples) * 2;
                        for (int i = 0; i < n; i++) { pcm.write(samples[i] & 255); pcm.write((samples[i] >>> 8) & 255); }
                    }
                    state.write(a.titleScreen() ? session.stateLineWithHostState(f, in) : session.stateLine(f, in));
                    state.newLine();
                    obs.write(observe(session, f)); obs.newLine();
                    var lmb = GameServices.level();
                    var omb = lmb == null ? null : lmb.getObjectManager();
                    if (omb != null) for (var b : omb.activeObjectsOfType(AbstractBadnikInstance.class))
                        if (!b.isDestroyed()) { bads.write(f + "," + b.getClass().getSimpleName() + "," + b.getX() + "," + b.getY()); bads.newLine(); }
                    if (f >= a.captureFrom() && (f - a.captureFrom()) % a.every() == 0)
                        ScreenshotCapture.savePNG(session.render(), frames.resolve(String.format(Locale.ROOT, "%05d.png", f)));
                    if (session.player().getDead()) {
                        if (dead < 0) dead = f;
                        if (a.stopOnDeath() && f >= dead + a.deathGrace()) break;
                    }
                }
            }
            if (a.audio()) {
                Path raw = out.resolve("audio.pcm");
                var fmt = new javax.sound.sampled.AudioFormat(audio.outputSampleRate(), 16, 2, true, false);
                try (var ais = new javax.sound.sampled.AudioInputStream(Files.newInputStream(raw), fmt, Files.size(raw) / 4)) {
                    javax.sound.sampled.AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, out.resolve("audio.wav").toFile());
                }
                Files.delete(raw);
            }
        }
        System.out.println("done frames=" + total + " death=" + dead + " out=" + out);
    }

    private static String observe(GameplayCaptureSession s, int f) {
        var p = s.player();
        var lm = GameServices.level();
        var om = lm == null ? null : lm.getObjectManager();
        int lost = 0, slotless = 0, collectible = 0, badniks = 0, nearDx = 0, nearDy = 0;
        String near = null;
        String ringfall = ",,", rebound = ",";
        if (om != null) {
            for (var r : om.activeObjectsOfType(LostRingObjectInstance.class)) {
                if (r.isDestroyed()) continue;
                lost++; if (r.getSlotIndex() < 0) slotless++; if (r.isLostRingCollectible()) collectible++;
            }
            for (var b : om.activeObjectsOfType(AbstractBadnikInstance.class)) {
                if (b.isDestroyed()) continue;
                badniks++;
                if (p != null) {
                    int dx = b.getX() - p.getCentreX(), dy = b.getY() - p.getCentreY();
                    if (near == null || Math.abs(dx) + Math.abs(dy) < Math.abs(nearDx) + Math.abs(nearDy)) {
                        near = b.getClass().getSimpleName(); nearDx = dx; nearDy = dy;
                    }
                }
            }
            var svc = om.getObjectServices();
            var src = svc == null ? null : WorldSessionPolicyAccess.getService(svc.worldSession(), GameplayMutatorPolicySource.class);
            if (src != null) {
                var g = src.policy();
                ringfall = g.ringfallPercent() + "," + g.ringfallCap() + "," + (g.ringfallFullInventory() ? 1 : 0);
                rebound = g.defeatReboundPercent() + "," + g.defeatVerticalCap();
            }
        }
        var pol = p == null ? null : PlayableSpriteInternalAccess.mutatorPolicy(p);
        var gs = GameServices.gameStateOrNull();
        var cam = GameServices.camera();
        String mode = String.valueOf(s.loop().getCurrentGameMode());
        return f + "," + mode + "," + (p == null ? "" : p.getRingCount()) + "," + (gs == null ? "" : gs.getScore()) + ","
                + lost + "," + slotless + "," + collectible + "," + (pol == null ? "" : pol.headScalePercent()) + ","
                + (pol == null ? "" : pol.dryAirGravityPercent()) + "," + ringfall + "," + rebound + "," + badniks + ","
                + (p == null ? ",,,,,," : p.getCentreX() + "," + p.getCentreY() + "," + p.getXSpeed() + "," + p.getYSpeed() + ","
                + (p.getAir() ? 1 : 0) + "," + (p.isHurt() ? 1 : 0) + "," + (p.getDead() ? 1 : 0)) + ","
                + (cam == null ? "," : cam.getX() + "," + cam.getY()) + ","
                + (near == null ? ",," : near + "," + nearDx + "," + nearDy);
    }

    static void requireDisplayFreeContext() {
        if (!SurfacelessEglContext.requested()) {
            throw new IllegalArgumentException("Mutator observations require -Dopenggf.headless.gl=surfaceless-egl; no desktop fallback");
        }
    }
}
