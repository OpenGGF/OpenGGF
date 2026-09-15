package com.openggf.tests;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Controller-only positioned scenarios for the SOZ connected-mechanism audit. */
final class SozConnectedMechanismRoute {
    enum Scene {
        UPPER(0x2130, 0xB0, 680), LOWER(0x2118, 0x56C, 1200),
        ROCK(0x4754, 0x5AC, 240), SWITCH(0x4808, 0x5AC, 180);
        final int x, y, frames;
        Scene(int x, int y, int frames) { this.x = x; this.y = y; this.frames = frames; }
    }
    private final Scene scene;
    private int lowerPhase;
    private int chargedFrame = -1;
    SozConnectedMechanismRoute(Scene scene) { this.scene = scene; }

    Bk2FrameInput input(int frame, AbstractPlayableSprite player) {
        boolean left = false, right = false, jump = false;
        int x = player.getCentreX(), y = player.getCentreY() & 0x7FF;
        switch (scene) {
            case UPPER -> {
                left = frame >= 100 && x > 0x20C0;
                right = frame >= 100 && !left && player.getGSpeed() < 0;
                jump = (frame >= 30 && frame < 60) || (frame >= 100 && frame < 135);
            }
            case LOWER -> {
                boolean aiming = S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry())
                        .orElseThrow().events().backgroundRoutine() == 0x10;
                int target = 0x2130;
                if (!aiming) {
                    if (lowerPhase == 0 && x >= 0x2400) lowerPhase = 1;
                    if (lowerPhase == 1 && x <= 0x22A0) lowerPhase = 2;
                    if (lowerPhase == 2 && y <= 0x350) lowerPhase = 3;
                    right = lowerPhase != 1;
                    left = lowerPhase == 1;
                    target = 0x22A0;
                }
                jump = aiming ? frame >= 10 && frame < 70 : frame % 90 < 24;
                if (aiming || lowerPhase == 2) {
                    int desired = Math.max(-128, Math.min(128, (target - x) * 16));
                    left = player.getXSpeed() > desired;
                    right = player.getXSpeed() < desired;
                    if (!aiming) jump = false;
                }
            }
            case ROCK -> { right = true; jump = frame < 30; }
            case SWITCH -> {
                right = true;
                if (chargedFrame < 0 && SozZoneRuntimeState.trigger(11) == 128) chargedFrame = frame;
                jump = chargedFrame >= 0 && frame - chargedFrame < 30;
            }
        }
        return new Bk2FrameInput(frame, (left ? 4 : 0) | (right ? 8 : 0) | (jump ? 16 : 0),
                jump ? 1 : 0, false, "");
    }
}
