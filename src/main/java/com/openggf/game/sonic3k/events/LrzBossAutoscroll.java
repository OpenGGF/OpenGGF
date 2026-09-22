package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.runtime.LrzBossActState;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/** Special_events_routine $14: loc_59E46 through sub_59F82. */
final class LrzBossAutoscroll {
    private LrzBossAutoscroll() { }

    static void advance(LrzBossActState state, Camera camera, AbstractPlayableSprite main,
                        List<AbstractPlayableSprite> sidekicks) {
        if (state.autoscrollRoutine() < 0 || main == null) return;
        if (state.autoscrollDelay() != 0) {
            state.setAutoscrollDelay(state.autoscrollDelay() - 1);
            return;
        }
        camera.setScrollLocked(true);
        int x = camera.getX() & 0xFFFF, y = camera.getY() & 0xFFFF;
        int stage = state.autoscrollRoutine();
        // Each satisfied threshold falls straight into the next stage on this dispatch.
        if (stage == 0 && x >= 0x410) stage = 4;
        if (stage == 4 && y <= 0x330) stage = 8;
        if (stage == 8 && x >= 0x650) stage = 12;
        if (stage == 12 && y <= 0x2F0) stage = 16;
        if (stage == 16 && x >= 0x910) stage = 20;
        if (stage == 20 && y >= 0x320) stage = 24;
        state.setAutoscrollRoutine(stage);
        int dx = switch (stage) {
            case 4, 12 -> 0x16A00;
            case 20 -> 0x1D900;
            default -> 0x20000;
        };
        int dy = switch (stage) {
            case 4, 12 -> -0x16A00;
            case 20 -> 0xC400;
            default -> 0;
        };
        if (stage == 24 && x >= 0xBBF) {
            dx = 0;
            if ((main.getCentreX() & 0xFFFF) >= 0xC50) {
                camera.setMinX((short) 0xA00); camera.setMaxX((short) 0xBC0);
                camera.setMaxY((short) 0x560); camera.setMaxYTarget((short) 0x560);
                camera.setScrollLocked(false);
                state.setAutoscrollRoutine(-1);
                return;
            }
        }
        int fullX = (x << 16 | state.cameraFractionX()) + dx;
        int fullY = (y << 16 | state.cameraFractionY()) + dy;
        state.setCameraFractions(fullX, fullY);
        short nextX = (short) (fullX >> 16), nextY = (short) (fullY >> 16);
        camera.setX(nextX); camera.setMinX(nextX); camera.setMaxX(nextX);
        camera.setY(nextY); camera.setMinY(nextY); camera.setMaxY(nextY); camera.setMaxYTarget(nextY);
        constrain(main, nextX & 0xFFFF, dx, camera.getWidth());
        // loc_59F3C saves/restores d2 with MOVEM.W between native P1 and P2.
        // Preserve the shipped word truncation; extended sidekicks follow P2 semantics.
        for (var sidekick : sidekicks) constrain(sidekick, nextX & 0xFFFF, (short) dx, camera.getWidth());
    }

    private static void constrain(AbstractPlayableSprite player, int cameraX, int velocity, int width) {
        if (player.getAnimationId() == 5) player.setAnimationId(0);
        int x = player.getCentreX() & 0xFFFF;
        int left = cameraX + 0x10;
        if (x < left) {
            if (player.getPushing()) player.applyCrushDeath();
            else {
                NativePositionOps.writeXPosPreserveSubpixel(player, left);
                player.setGSpeed((short) (velocity >> 8));
            }
        } else {
            // Native right edge is camera+$120 at 320 pixels. Wider viewports retain
            // the same 32-pixel right margin; they do not change the route thresholds.
            int right = cameraX + width - 0x20;
            if (x >= right) NativePositionOps.writeXPosPreserveSubpixel(player, right);
        }
    }
}
