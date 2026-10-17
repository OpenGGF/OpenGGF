package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.sprites.NativePositionOps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;

/**
 * Declared diagnostic of Tails' LRZ2 seven-Chaos ring, sanctuary, failed sphere stage
 * and results return. Origin: the 2026-10-14 S3K parity/coverage task, verified twins
 * on 57774b451 plus the results geometry/priority correction (2026-10-17).
 *
 * <p>Inputs: {@code --rom ABSOLUTE_S3K_ROM --out-dir NEW_DIRECTORY}. Fixed protocol:
 * native320, Limited/RAW_RGB, seven Chaos Emeralds, V-int/RNG zero after boot,
 * positioned approach/hold until the ring forms, one contact placement, then the
 * retained event-relative controller suffix. No trace values or native observations
 * supply gameplay. This is a declared component journey, not authentic cold input.
 * It records original PNGs, state.csv and an event ledger, bounded at 30,000 frames.
 */
public final class S3kSevenChaosGatewayCaptureTool {
    private S3kSevenChaosGatewayCaptureTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("Usage: S3kSevenChaosGatewayCaptureTool --rom ABSOLUTE_S3K_ROM --out-dir NEW_DIRECTORY");
            return;
        }
        Path rom = null, out = null;
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + args[i]);
            switch (args[i]) {
                case "--rom" -> { if (rom != null) throw new IllegalArgumentException("Duplicate --rom"); rom = Path.of(args[i + 1]); }
                case "--out-dir" -> { if (out != null) throw new IllegalArgumentException("Duplicate --out-dir"); out = Path.of(args[i + 1]); }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (rom == null || out == null || !rom.isAbsolute() || !out.isAbsolute())
            throw new IllegalArgumentException("Supply absolute --rom and --out-dir paths");
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")
                && (!"surfaceless-egl".equals(System.getProperty("openggf.headless.gl"))
                || present(System.getenv("DISPLAY")) || present(System.getenv("WAYLAND_DISPLAY"))))
            throw new IllegalStateException("Linux capture requires surfaceless-egl and unset DISPLAY/WAYLAND_DISPLAY");
        if (!Files.isRegularFile(rom) || Files.size(rom) != 0x400000)
            throw new IllegalArgumentException("Expected the verified 4 MiB S3K lock-on ROM");
        var image = new com.openggf.data.Rom();
        com.openggf.data.RomFingerprint fingerprint;
        try {
            if (!image.open(rom.toString())) throw new IllegalArgumentException("Cannot open ROM");
            fingerprint = com.openggf.data.RomFingerprint.of(image.readAllBytes());
        } finally { image.close(); }
        if (com.openggf.data.RomIdentityTable.verify(com.openggf.data.RomIdentity.S3K, fingerprint).isEmpty())
            throw new IllegalArgumentException("The declared reference protocol requires the verified S3K lock-on image");
        System.out.println("Build: " + com.openggf.version.AppVersion.identity());
        System.out.println("Declared seven-Chaos protocol: " + fingerprint);
        capture(rom, out);
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }

    private static final int RING_X = 0x2640, RING_Y = 0x640;

    private static void capture(Path rom, Path out) throws Exception {
        if (Files.exists(out)) throw new IllegalStateException("refuse existing output");
        Files.createDirectories(out);
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null,
                "1111111", false, false, null, null);
        try (var session = new GameplayCaptureSession(settings);
             var state = Files.newBufferedWriter(out.resolve("state.csv"));
             var ledger = Files.newBufferedWriter(out.resolve("ledger.csv"))) {
            var config = GameServices.configuration();
            config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_COLOR_EXPANSION, "LIMITED");
            config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_COLOR_PROFILE, "RAW_RGB");
            session.boot(rom, 9, 1, settings);
            GameServices.level().getObjectManager().initVblaCounter(0);
            GameServices.rng().setSeed(0);
            state.write("frame,phase,mode,zone,act,x,y,object_controlled,cam_x,cam_y,rings,emeralds,converted,star_post_proxy,big_ring_return,ss_fade_to_white,ss_ready,fade_active\n");
            int phase = 0, approachY = -1, hold = 0, contact = -1, hubFirst = -1, release = -1, pedestal = -1,
                    stage = -1, results = -1, returned = -1;
            boolean hubControlled = false;
            var shots = new TreeMap<Integer, String>();
            for (int n = 0; n < 30000; n++) {
                var p = session.player();
                if (phase == 0) {
                    if (approachY < 0) approachY = p.getCentreY();
                    int target = RING_Y - 8;
                    approachY = approachY < target ? Math.min(approachY + 0x80, target) : Math.max(approachY - 0x80, target);
                    place(p, RING_X - 40, approachY);
                    if (approachY == target) phase = 1;
                } else if (phase == 1) {
                    place(p, RING_X - 40, RING_Y - 8);
                    hold++;
                    if (ringMapping() >= 8) {
                        place(p, RING_X + 8, RING_Y - 4);
                        phase = 2; contact = n; ledger.write("contact," + n + "\n");
                        shots.put(n + 1, "intake+1"); shots.put(n + 20, "intake+20");
                    } else if (hold > 1500) throw new IllegalStateException("ring did not form");
                }
                Bk2FrameInput input = new Bk2FrameInput(n, 0, 0, false, "-");
                if (release >= 0) {
                    int t = n - (release + 20);
                    if (t >= 0 && t < 45) input = new Bk2FrameInput(n, 0x08, 0, false, "R");
                    else if (t == 91) input = new Bk2FrameInput(n, 0x10, 1, false, "A");
                }
                session.step(input);
                var mode = session.loop().getCurrentGameMode();
                var level = GameServices.level();
                boolean level0 = mode == GameMode.LEVEL;
                int zone = level.getCurrentZone(), act = level.getCurrentAct();
                boolean controlled = level0 && session.player().isObjectControlled();
                if (phase >= 2) {
                    if (hubFirst < 0 && level0 && zone == 0x17 && act == 1) { hubFirst = n; ledger.write("hub_first," + n + "\n"); }
                    if (hubFirst >= 0 && release < 0 && level0 && zone == 0x17) {
                        if (controlled) hubControlled = true;
                        else if (hubControlled) {
                            release = n; ledger.write("hub_release," + n + "\n");
                            for (int o : new int[]{0, 20, 130}) shots.put(n + o, "hub+" + o);
                        }
                    }
                    if (release >= 0 && pedestal < 0 && level0 && zone == 0x17 && controlled) {
                        pedestal = n; ledger.write("pedestal," + n + "\n"); shots.put(n + 1, "pedestal+1");
                    }
                    if (stage < 0 && mode == GameMode.SPECIAL_STAGE) {
                        stage = n; ledger.write("special_stage," + n + "\n");
                        for (int k = 0; k <= 160; k += 2) shots.put(n + k, String.format(java.util.Locale.ROOT, "stage+%03d", k));
                        shots.put(n + 300, "stage+300");
                    }
                    if (stage >= 0 && results < 0 && mode == GameMode.SPECIAL_STAGE_RESULTS) {
                        results = n; ledger.write("results," + n + "\n");
                        for (int k = 0; k <= 160; k += 2) shots.put(n + k, String.format(java.util.Locale.ROOT, "results+%03d", k));
                        shots.put(n + 300, "results+300");
                    }
                    if (results >= 0 && returned < 0 && level0 && zone == 9 && act == 1 && !controlled) {
                        returned = n; ledger.write("lrz2_first_level_frame," + n + "\n");
                        shots.put(n, "return+0"); shots.put(n + 30, "return+30");
                    }
                }
                String stem = shots.get(n);
                var image = session.render();
                if (stem != null) ScreenshotCapture.savePNG(image, out.resolve(stem + ".png"));
                var gs = GameServices.gameState();
                state.write(n + "," + phase + "," + mode + "," + zone + "," + act + ","
                        + (level0 ? session.player().getCentreX() + "," + session.player().getCentreY() : ",") + ","
                        + controlled + "," + GameServices.camera().getX() + "," + GameServices.camera().getY() + ","
                        + (level0 ? session.player().getRingCount() : "") + "," + gs.getS3kEmeraldStates() .toString().replace(", ", "")
                        + "," + gs.isEmeraldsConverted() + "," + level.getTransitions().isLastStarPostHitSet() + ","
                        + level.hasBigRingReturn() + ","
                        + (mode == GameMode.SPECIAL_STAGE && session.loop().getActiveSpecialStageProvider() != null
                            ? session.loop().getActiveSpecialStageProvider().isEntryFadeToWhiteActive() + ","
                              + session.loop().getActiveSpecialStageProvider().isEntryPresentationReady() : ",")
                        + "," + GameServices.fade().isActive() + "\n");
                if (returned >= 0 && n > returned + 31) break;
            }
            ledger.write("DONE contact=" + contact + " hub_first=" + hubFirst + " hub_release=" + release + " pedestal=" + pedestal
                    + " stage=" + stage + " results=" + results + " returned=" + returned + "\n");
            if (returned < 0) throw new IllegalStateException("did not return; release=" + release + " stage=" + stage);
        }
    }

    private static void place(com.openggf.sprites.playable.AbstractPlayableSprite p, int x, int y) {
        NativePositionOps.writeXPosPreserveSubpixel(p, x);
        NativePositionOps.writeYPosPreserveSubpixel(p, y);
        p.setXSpeed((short) 0); p.setGSpeed((short) 0); p.setYSpeed((short) 0);
        p.setAir(true); p.setJumping(false);
    }

    private static int ringMapping() throws Exception {
        for (var o : GameServices.level().getObjectManager().activeObjectsOfType(
                com.openggf.game.sonic3k.objects.Sonic3kSSEntryRingObjectInstance.class)) {
            if (o.getSpawn().x() == RING_X && o.getSpawn().y() == RING_Y) {
                var m = o.getClass().getDeclaredMethod("getMappingFrame");
                m.setAccessible(true);
                return (int) m.invoke(o);
            }
        }
        return -1;
    }
}
