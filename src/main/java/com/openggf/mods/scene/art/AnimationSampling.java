package com.openggf.mods.scene.art;

import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;

/** Explicit scene timing for decoded ROM frame lists, including speed-driven player scripts. */
@com.openggf.game.ModApi
public final class AnimationSampling {
    private AnimationSampling() { }
    @com.openggf.game.ModApi
    public record Timing(int fixedTicks, int longDelayThreshold, int longDelayTicks, int delayAdjustment) {
        public Timing {
            if (fixedTicks < 0 || fixedTicks > 65536 || longDelayThreshold < 1 || longDelayThreshold > 256
                    || longDelayTicks < 1 || longDelayTicks > 65536 || delayAdjustment < -255 || delayAdjustment > 65536) {
                throw new IllegalArgumentException("Invalid animation timing policy");
            }
        }
        public static Timing rom(int speedDrivenTicks) { return new Timing(0, 253, speedDrivenTicks, 0); }
        public static Timing fixed(int ticks) {
            if (ticks < 1) throw new IllegalArgumentException("Fixed animation delay must be positive");
            return new Timing(ticks, 253, ticks, 0);
        }
        public int delay(SceneSpriteSet set, int animation) {
            int nativeDelay = set.animationDelay(animation);
            int delay = fixedTicks > 0 ? fixedTicks : nativeDelay > longDelayThreshold ? longDelayTicks
                    : Math.addExact(nativeDelay, delayAdjustment);
            if (delay < 1) throw new IllegalArgumentException("Animation policy produced a nonpositive delay");
            return delay;
        }
    }
    public static SceneSprite frame(SceneSpriteSet set, int animation, long ticks, Timing timing) {
        if (set == null) return null;
        int[] frames = set.animationFrames(animation);
        if (frames.length == 0) return null;
        int index = (int) Math.floorMod(Math.floorDiv(ticks, timing.delay(set, animation)), (long) frames.length);
        int frame = frames[index];
        return set.frame(frame >= 0xF0 ? frames[0] : frame);
    }
    public static SceneSprite still(SceneSpriteSet set, int animation) {
        if (set == null) return null;
        int[] frames = set.animationFrames(animation);
        return frames.length == 0 ? null : set.frame(frames[0]);
    }
}
