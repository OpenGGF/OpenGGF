package com.openggf.game.soniccd.special;

/**
 * The water splash ({@code ObjSplash}, Special Stage/Objects/Splash/Main.asm). Outside Time
 * Attack its first update speeds the timer up for 10 updates; while Sonic stays in water the
 * small splash re-arms 2 more ticks each time its animation restarts. FixBugs = 0: the speed-up
 * outlasts neither the large splash (NOTE at Splash/Main.asm:41-42) nor is extended by it.
 */
final class CdSpecialSplash {
    boolean present;
    boolean deleting;
    int routine;
    int timer;
    final CdSpecialAnimationScripts.State animation;
    final CdSpecialSprites.Sprite sprite;

    CdSpecialSplash(CdSpecialAnimationScripts scripts) {
        sprite = new CdSpecialSprites.Sprite(0x8582, scripts);
        sprite.x = 128 + 128;
        sprite.y = 216 + 128;
        animation = sprite.anim;
    }

    /** {@code move.b #8,splashObject+oID} (Sonic/Main.asm:642): spawns only an empty slot. */
    void spawn() {
        if (present) return;
        present = true;
        deleting = false;
        routine = 0;
    }

    /** {@code DeleteSplash} (Splash/Main.asm:90-96): the object is cleared after its next run. */
    void markDeleted() {
        if (present) deleting = true;
    }

    /** {@code ObjSplash} then {@code RunObject}'s delete check (scddisasm/src/Special Stage/Sub.asm:669-689). */
    void update(CdSpecialStage stage, CdSpecialAnimationScripts scripts) {
        if (!present) return;
        boolean delete = false;
        switch (routine) {
            case 0 -> {
                animation.set(scripts, 0);
                timer = 14;
                routine = 1;
                stage.playSound(CdSpecialSound.SPLASH);
                if (!stage.flags().timeAttack()) stage.timerSpeedUp = 10;
                delete = large(stage, scripts);
            }
            case 1 -> delete = large(stage, scripts);
            default -> {
                if (stage.sonic().stampCenter != 3) delete = true;
                else if (animation.frame == 0) stage.timerSpeedUp = 2;
            }
        }
        stage.sprites().draw(sprite); // DrawObject (Splash/Main.asm:14)
        if (stage.timeStopped) delete = true;
        if (delete || deleting) {
            present = false;
            deleting = false;
        }
    }

    private boolean large(CdSpecialStage stage, CdSpecialAnimationScripts scripts) {
        timer = (timer - 1) & 0xFFFF;
        if (timer != 0) return false;
        if (stage.sonic().stampCenter != 3) return true;
        animation.set(scripts, 1);
        routine = 2;
        return false;
    }
}
