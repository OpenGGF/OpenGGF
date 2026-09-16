package com.openggf.tests;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Controller-only positioned scenarios for the SOZ connected-mechanism audit. */
final class SozConnectedMechanismRoute {
    enum Scene {
        UPPER(0x2130, 0xB0, 680), LOWER(0x20C0, 0x56C, 4000),
        ROCK(0x4754, 0x5AC, 240), SWITCH(0x4808, 0x5AC, 180);
        final int x, y, frames;
        Scene(int x, int y, int frames) { this.x = x; this.y = y; this.frames = frames; }
    }
    private final Scene scene;
    // Controller waypoints: first shaft, spike corridor, switch8, swing, switch9.
    // These are input decisions only; no object, event or player state is written.
    private int lowerPhase;
    private int chargedFrame = -1;
    private int roomSwitchChargedFrame = -1;
    private int openingJumpFrame = -1;
    private int corridorJumpFrame = -1;
    private int finalSwitchChargedFrame = -1;
    SozConnectedMechanismRoute(Scene scene) { this.scene = scene; }

    static void assertRoster(Scene scene) {
        org.junit.jupiter.api.Assertions.assertInstanceOf(com.openggf.sprites.playable.Sonic.class, GameServices.camera().getFocusedSprite());
        var followers = GameServices.sprites().getRegisteredSidekicks();
        org.junit.jupiter.api.Assertions.assertEquals(scene == Scene.LOWER ? 0 : 1, followers.size());
        if (scene != Scene.LOWER)
            org.junit.jupiter.api.Assertions.assertEquals("tails", GameServices.sprites().getSidekickCharacterName(followers.getFirst()));
    }

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
                int target = 0x22A0;
                if (!aiming) {
                    if (lowerPhase == 0 && x >= 0x2400) lowerPhase = 1;
                    if (lowerPhase == 1 && x <= 0x22A0) lowerPhase = 2;
                    if (lowerPhase == 2 && y <= 0x390) lowerPhase = 3;
                    if (lowerPhase == 3 && x >= 0x2440) lowerPhase = 4;
                    if (lowerPhase == 4 && x >= 0x25F0 && y < 0x1D0) lowerPhase = 5;
                    if (lowerPhase == 5 && SozZoneRuntimeState.trigger(8) == 128) lowerPhase = 6;
                    if (lowerPhase == 6 && x >= 0x27A0) lowerPhase = 7;
                    if (lowerPhase == 7 && player.isOnObject() && !player.getAir() && y < 0x150 && x >= 0x2800) lowerPhase = 8;
                    if (lowerPhase == 8 && x >= 0x2900 && y < 0x100) lowerPhase = 9;
                    if (lowerPhase == 9 && x <= 0x2900 && player.getXSpeed() <= 0) lowerPhase = 10;
                    if (lowerPhase == 10 && SozZoneRuntimeState.trigger(9) == 128) {
                        lowerPhase = 11; finalSwitchChargedFrame = frame;
                    }
                    right = lowerPhase != 1;
                    left = lowerPhase == 1;
                }
                jump = (frame >= 10 && frame < 70) || (!aiming && frame % 90 < 24);
                if (lowerPhase == 3 && x >= 0x2380 && x < 0x23E0 && y > 0x280) {
                    if (roomSwitchChargedFrame < 0 && SozZoneRuntimeState.trigger(12) == 128)
                        roomSwitchChargedFrame = frame;
                    jump = roomSwitchChargedFrame >= 0 && frame - roomSwitchChargedFrame < 30;
                }
                if (lowerPhase == 4) {
                    if (corridorJumpFrame < 0 && x >= 0x2460) corridorJumpFrame = frame;
                    jump = frame == corridorJumpFrame || (x >= 0x2590 && frame % 60 < 30);
                }
                if (lowerPhase == 5 || lowerPhase == 6) jump = lowerPhase == 6 && x >= 0x26D0 && frame % 60 < 30;
                if (lowerPhase == 7) {
                    // Subtype05's six-link swing reaches its low left endpoint at27A0.
                    int platformX = 0x27A0;
                    int desired = Math.max(-256, Math.min(256, (platformX - x) * 16));
                    left = player.getXSpeed() > desired; right = player.getXSpeed() < desired;
                    jump = frame % 60 < 30;
                    if (player.isOnObject() && !player.getAir()) { left = false; right = false; jump = false; }
                }
                if (lowerPhase == 8) jump = frame % 60 < 30;
                if (lowerPhase == 9) { left = true; right = false; jump = frame % 60 < 30; }
                if (lowerPhase == 10) jump = false;
                if (lowerPhase == 11) jump = frame - finalSwitchChargedFrame < 30;
                if (aiming) {
                    if (openingJumpFrame < 0 && x >= 0x2100) openingJumpFrame = frame;
                    left = false; right = true;
                    jump = openingJumpFrame >= 0 && frame - openingJumpFrame < 30;
                }
                if (!aiming && lowerPhase == 2) {
                    int desired = Math.max(-128, Math.min(128, (target - x) * 16));
                    left = player.getXSpeed() > desired;
                    right = player.getXSpeed() < desired;
                    jump = false;
                }
            }
            case ROCK -> { right = true; jump = frame < 30; }
            case SWITCH -> {
                right = true;
                if (chargedFrame < 0 && SozZoneRuntimeState.trigger(11) == 128) chargedFrame = frame;
                jump = chargedFrame >= 0 && frame - chargedFrame < 30;
            }
        }
        if (scene == Scene.LOWER && S3kRuntimeStates.currentSoz(GameServices.zoneRuntimeRegistry())
                .orElseThrow().events().backgroundRoutine() == 0) {
            left = false; right = false; jump = false;
        }
        int buttons = (left ? 4 : 0) | (right ? 8 : 0) | (jump ? 16 : 0);
        return new Bk2FrameInput(frame, buttons, jump ? 1 : 0, false,
                String.format("0x%02X", buttons));
    }
}
