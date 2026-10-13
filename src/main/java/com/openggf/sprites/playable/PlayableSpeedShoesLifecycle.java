package com.openggf.sprites.playable;

import com.openggf.timer.Timer;
import com.openggf.timer.timers.SpeedShoesTimer;

/** Native timer admission and behavioral rewind restoration; the sprite owns its activation flag. */
final class PlayableSpeedShoesLifecycle {
    private PlayableSpeedShoesLifecycle() { }

    static SpeedShoesTimer admit(AbstractPlayableSprite sprite, int durationFrames) {
        SpeedShoesTimer admitted = new SpeedShoesTimer("SpeedShoes-" + sprite.getCode(), sprite, durationFrames);
        Timer existing = sprite.currentTimerManager().getTimerForCode(admitted.getCode());
        if (sprite.hasSpeedShoes() && existing != null) {
            admitted.setTicks(Math.max(admitted.getTicks(), existing.getTicks()));
        }
        return admitted;
    }

    static int remainingTicks(AbstractPlayableSprite sprite, boolean active) {
        if (!active) return 0;
        Timer timer = sprite.currentTimerManager().getTimerForCode("SpeedShoes-" + sprite.getCode());
        return timer != null ? timer.getTicks() : 0;
    }

    static void restoreTimer(AbstractPlayableSprite sprite, boolean active, int remainingTicks) {
        var timers = sprite.currentTimerManager();
        String code = "SpeedShoes-" + sprite.getCode();
        if (!active) {
            timers.removeTimerForCode(code);
            return;
        }
        SpeedShoesTimer replacement = new SpeedShoesTimer(code, sprite);
        replacement.setTicks(remainingTicks > 0 ? remainingTicks : SpeedShoesTimer.ROM_DURATION_FRAMES);
        timers.registerTimer(replacement);
    }
}
