package sitarhero;

import com.openggf.mods.scene.*;
import sitarhero.model.Role;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Cosmetic regression checks, compiled with the external example just like RhythmChecks. */
public final class PerformerChecks {
    public static void main(String[] args) {
        nativePixelsPartitionExactly(); compositeMasksLeaveBackingLayersIntact(); pivotsAndRotationKeepNativePalette();
        hitsHaveFiniteEnvelopes(); drumsAndChordsRemainIndependent(); pauseAndClockResetCancelHits();
        drawRequiresActualHitsAndPlacesHandsAfterProps();
        System.out.println("7 external performer checks passed");
    }

    private static void compositeMasksLeaveBackingLayersIntact() {
        int[] glass = new int[40 * 32]; Arrays.fill(glass, 0xFF88CCEE);
        int[] sleeve = new int[40 * 16];
        for (int y = 3; y < 5; y++) for (int x = 5; x < 7; x++) sleeve[y * 40 + x] = 0xFFAA2211;
        var sources = List.of(new PerformerCutout.Positioned(new SceneSprite(new SceneImage(40, 32, glass), 20, 16), 0, 0),
                new PerformerCutout.Positioned(new SceneSprite(new SceneImage(40, 16, sleeve), 20, 8), 0, -8));
        var rig = PerformerRig.of(sources, "robotnik", "s3k");
        for (int pixel : rig.pixels.body().image().pixels()) eq(0xFF88CCEE, pixel);
        for (var limb : rig.pixels.limbs()) for (int pixel : limb.nativePart.image().pixels())
            yes(pixel == 0 || pixel == 0xFFAA2211, "glass cannot be transplanted into an arm");
    }

    private static void nativePixelsPartitionExactly() {
        int[] pixels = new int[40 * 48];
        for (int y = 0; y < 48; y++) for (int x = 0; x < 40; x++)
            pixels[y * 40 + x] = (x + y) % 3 == 0 ? 0 : 0xFF000000 | (y * 40 + x);
        var source = new SceneSprite(new SceneImage(40, 48, pixels), 20, 24);
        for (String who : List.of("sonic", "tails", "knuckles", "silver-sonic", "mecha-sonic", "egg-robo", "robotnik")) {
            var rig = PerformerRig.of(source, who, "s3k");
            var pieces = new ArrayList<PerformerCutout.Positioned>();
            pieces.add(new PerformerCutout.Positioned(rig.pixels.body(), 0, 0));
            for (var limb : rig.pixels.limbs()) pieces.add(new PerformerCutout.Positioned(limb.nativePart, limb.x, limb.y));
            var restored = PerformerCutout.compose(pieces);
            yes(Arrays.equals(pixels, restored.image().pixels()), who + " must preserve every untransformed native pixel");
            eq(source.originX(), restored.originX()); eq(source.originY(), restored.originY());
            // Above all arm masks: eyes, muzzle, ears/spines survive intact.
            for (int x = 0; x < 40; x++) eq(source.image().pixel(x, 0), rig.pixels.body().image().pixel(x, 0));
        }
    }

    private static void pivotsAndRotationKeepNativePalette() {
        var source = new SceneSprite(new SceneImage(5, 5, new int[] {
                0,0,0,0,0, 0,0,0xFF112233,0,0, 0,0,0xFF445566,0xFF778899,0,
                0,0,0xFFAABBCC,0,0, 0,0,0,0,0}), 2, 2);
        var split = PerformerCutout.split(source, new PerformerCutout.Mask(0, 0, -1,-1, 2,-1, 2,2, -1,2));
        var limb = split.limbs().getFirst();
        eq(0, limb.x); eq(0, limb.y);
        yes(limb.turn(0) == limb.nativePart, "zero turn must retain exact pixels");
        for (int step = -9; step <= 9; step++) {
            yes(limb.turn(step) == limb.turn(step), "rotation must be cached");
            for (int pixel : limb.turn(step).image().pixels())
                yes(pixel == 0 || Arrays.stream(source.image().pixels()).anyMatch(value -> value == pixel), "no painted/interpolated limb pixels");
        }
    }

    private static void hitsHaveFiniteEnvelopes() {
        var motion = new PerformerMotion();
        for (long tick = 0; tick < 300; tick++) eq(0, motion.stroke(Role.SITAR, 31, tick));
        motion.notePlayed(Role.SITAR, 4, 300);
        yes(motion.stroke(Role.SITAR, 31, 300) < 0, "prepare on hit");
        yes(motion.stroke(Role.SITAR, 31, 302) > 0, "contact follows hit");
        yes(motion.stroke(Role.SITAR, 31, 305) < 0, "rebound follows contact");
        eq(0, motion.stroke(Role.SITAR, 31, 320));
        eq(18, motion.age(Role.SITAR, 31, 320)); // sustain is not another note-on
    }

    private static void drumsAndChordsRemainIndependent() {
        var motion = new PerformerMotion();
        motion.notePlayed(Role.BONGOS, 1, 100);
        yes(motion.stroke(Role.BONGOS, 5, 102) != 0, "left pad attacks");
        eq(0, motion.stroke(Role.BONGOS, 10, 102)); eq(0, motion.stroke(Role.BONGOS, 16, 102));
        motion.notePlayed(Role.BONGOS, 18, 103);
        eq(1, motion.age(Role.BONGOS, 10, 104)); eq(1, motion.age(Role.BONGOS, 16, 104));
        eq(4, motion.age(Role.BONGOS, 5, 104)); eq(18, motion.age(Role.SYNTH, 31, 104));
        motion.notePlayed(Role.SYNTH, 5, 105);
        eq(0, motion.age(Role.SYNTH, 1, 105)); eq(0, motion.age(Role.SYNTH, 4, 105));
        eq(18, motion.age(Role.SYNTH, 2, 105));
        try { motion.notePlayed(Role.HARP, 32, 105); throw new AssertionError("sixth lane accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void pauseAndClockResetCancelHits() {
        var motion = new PerformerMotion();
        motion.notePlayed(Role.HARP, 2, 100);
        motion.beginDraw(101, false); eq(0, motion.stroke(Role.HARP, 31, 101));
        motion.beginDraw(102, true); eq(0, motion.stroke(Role.HARP, 31, 102));
        motion.notePlayed(Role.HARP, 2, 103); motion.beginDraw(0, true);
        eq(18, motion.age(Role.HARP, 31, 0));
        motion.notePlayed(Role.SITAR, 1, 1); motion.beginDraw(1, true); motion.beginDraw(1, true);
        eq(0, motion.age(Role.SITAR, 1, 1));
    }

    private static void drawRequiresActualHitsAndPlacesHandsAfterProps() {
        int[] pixels = new int[32 * 40]; Arrays.fill(pixels, 0xFF334477);
        var set = new SceneSpriteSet() {
            public int frameCount() { return 1; }
            public SceneSprite frame(int frame) { return new SceneSprite(new SceneImage(32, 40, pixels), 16, 20); }
            public int[] animationFrames(int id) { return new int[] {0}; }
            public int animationDelay(int id) { return 8; }
        };
        var rom = (SceneRomArt) Proxy.newProxyInstance(PerformerChecks.class.getClassLoader(), new Class<?>[] {SceneRomArt.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "gameId" -> "s3k";
                    case "character" -> set;
                    default -> null;
                });
        var actor = new PerformerArt(rom, "sonic");
        List<String> ops = new ArrayList<>();
        var canvas = (SceneCanvas) Proxy.newProxyInstance(PerformerChecks.class.getClassLoader(), new Class<?>[] {SceneCanvas.class},
                (proxy, method, values) -> { ops.add(method.getName()); return null; });
        actor.draw(canvas, 50, 50, 40, "Synth", true);
        var idle = List.copyOf(ops);
        yes(idle.indexOf("draw") < idle.indexOf("fill"), "body before instrument");
        yes(idle.lastIndexOf("draw") > idle.lastIndexOf("fill"), "native hands before any effect, after prop");
        ops.clear(); actor.notePlayed(Role.SYNTH, 5, 40); actor.draw(canvas, 50, 50, 42, "Synth", true);
        yes(ops.size() > idle.size(), "actual note creates transient key/ring effects");
        ops.clear(); actor.draw(canvas, 50, 50, 43, "Synth", false);
        yes(ops.equals(idle), "paused performer retains prop without note effects");
        ops.clear(); actor.draw(canvas, 50, 50, 60, "Synth", true);
        yes(ops.equals(idle), "resume/sustain does not pretend to play");
    }

    private static void eq(long expected, long actual) { yes(expected == actual, expected + " != " + actual); }
    private static void yes(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
