package com.openggf.level.objects;

import com.openggf.camera.CameraPresentationAccess;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.SonicGame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared driver for the NATIVE widescreen draw mode contract: a 16:9 NATIVE run plays
 * exactly the native 4:3 game, and the dormant pose bake leaves no gameplay trace.
 * See docs/architecture/designs/2026-11-13-widescreen-native-draw-mode.md.
 */
final class WidescreenNativeDrawEquivalence {
    private WidescreenNativeDrawEquivalence() { }

    /** One frame's gameplay fingerprint: player, camera, RNG and every live object. */
    record Frame(int frame, int x, int y, int xSpeed, int ySpeed, int cameraX, int cameraY, long rng,
                 List<String> objects) { }

    record Run(List<Frame> frames, int simulationWidth, int presentationWidth, int poses,
               Frame beforeBake, Frame afterBake, int dormantFrames) { }

    static Run run(SonicGame game, int zone, int act, String aspect, String drawMode, int frames)
            throws Exception {
        var config = SonicConfigurationService.getInstance();
        Object oldAspect = config.getConfigValue(SonicConfiguration.DISPLAY_ASPECT);
        Object oldMode = config.getConfigValue(SonicConfiguration.WIDESCREEN_DRAW_MODE);
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        SharedLevel level = null;
        try {
            config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, aspect);
            config.setConfigValue(SonicConfiguration.WIDESCREEN_DRAW_MODE, drawMode);
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
            config.resolveDisplayAspect();
            level = SharedLevel.load(game, zone, act);
            var fixture = HeadlessTestFixture.builder().withSharedLevel(level).build();
            var camera = fixture.camera();
            var objects = GameServices.level().getObjectManager();
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();

            // Probe isolation: bake explicitly and compare the complete fingerprint.
            Frame before = sample(-1, fixture);
            objects.dormantLayer.reset();
            objects.prepareDormantPresentation(registry);
            Frame after = sample(-1, fixture);
            int poses = objects.dormantLayer.poseCount();

            List<Frame> trace = new ArrayList<>(frames);
            int dormantFrames = 0;
            for (int frame = 0; frame < frames; frame++) {
                boolean jump = frame % 90 >= 60 && frame % 90 < 64;
                fixture.stepFrame(false, false, false, true, jump);
                trace.add(sample(frame, fixture));
                if (hasDormantDraws(objects)) dormantFrames++;
            }
            return new Run(trace, camera.getWidth(), CameraPresentationAccess.width(camera), poses, before, after,
                    dormantFrames);
        } finally {
            if (level != null) level.dispose();
            config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, oldAspect);
            config.setConfigValue(SonicConfiguration.WIDESCREEN_DRAW_MODE, oldMode);
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkip);
            config.resolveDisplayAspect();
        }
    }

    /** Whether any bucket would add a dormant draw this frame (draw closures are not run). */
    private static boolean hasDormantDraws(ObjectManager objects) {
        for (int bucket = com.openggf.graphics.RenderPriority.MIN; bucket <= com.openggf.graphics.RenderPriority.MAX; bucket++) {
            if (!objects.dormantLayer.withDormantEmissions(bucket, null, List.of()).isEmpty()) return true;
        }
        return false;
    }

    private static Frame sample(int frame, HeadlessTestFixture fixture) {
        var sprite = fixture.sprite();
        var camera = fixture.camera();
        var rng = GameServices.rngOrNull();
        List<String> objects = new ArrayList<>();
        for (ObjectInstance instance : GameServices.level().getObjectManager().getActiveObjects()) {
            int slot = instance instanceof AbstractObjectInstance object ? object.getSlotIndex() : -1;
            String at;
            try {
                at = instance.getX() + "," + instance.getY();
            } catch (NullPointerException spawnless) {
                at = "-"; // Some S3K helpers carry no spawn and no position of their own.
            }
            objects.add(slot + ":" + instance.getClass().getSimpleName() + "@" + at
                    + (instance.isDestroyed() ? "x" : ""));
        }
        objects.sort(Comparator.naturalOrder());
        return new Frame(frame, sprite.getCentreX(), sprite.getCentreY(), sprite.getXSpeed(), sprite.getYSpeed(),
                camera.getX(), camera.getY(), rng == null ? 0 : rng.getSeed(), List.copyOf(objects));
    }

    /**
     * Negative control: EDGE at 16:9 widens the object windows, so its gameplay must
     * differ from 4:3 somewhere on the same route. Proves the fingerprint can disagree.
     */
    static void assertEdgeDiverges(SonicGame game, int zone, int act, int frames) throws Exception {
        Run nativeRun = run(game, zone, act, "NATIVE_4_3", "EDGE", frames);
        Run edge = run(game, zone, act, "WIDE_16_9", "EDGE", frames);
        assertEquals(400, edge.simulationWidth());
        org.junit.jupiter.api.Assertions.assertNotEquals(nativeRun.frames(), edge.frames(),
                "EDGE 16:9 should load objects earlier than 4:3 on this route");
    }

    /** NATIVE at 32:9 must equal native 4:3 frame for frame, and the bake must be invisible. */
    static void assertEquivalent(SonicGame game, int zone, int act, int frames) throws Exception {
        Run nativeRun = run(game, zone, act, "NATIVE_4_3", "EDGE", frames);
        // 32:9 has the widest margins: its left band lies beyond the native 0x80 unload distance.
        Run wide = run(game, zone, act, "SUPER_32_9", "NATIVE", frames);

        assertEquals(320, nativeRun.simulationWidth());
        assertEquals(320, wide.simulationWidth(), "NATIVE draw mode simulates the 320-pixel window");
        assertEquals(800, wide.presentationWidth(), "NATIVE draw mode still presents the 32:9 width");
        assertEquals(0, nativeRun.poses(), "no margin, no dormant layer");
        assertTrue(wide.poses() > 0, "the 16:9 NATIVE run baked dormant poses");
        assertEquals(wide.beforeBake(), wide.afterBake(), "the dormant pose bake left a gameplay trace");
        assertEquals(0, nativeRun.dormantFrames());
        assertTrue(wide.dormantFrames() > 0, "no frame drew a dormant object in the margins");
        System.out.printf("%s: %d poses, dormant draws on %d of %d frames%n",
                game, wide.poses(), wide.dormantFrames(), frames);
        for (int i = 0; i < frames; i++) {
            assertEquals(nativeRun.frames().get(i), wide.frames().get(i), "gameplay diverged at frame " + i);
        }
    }
}
