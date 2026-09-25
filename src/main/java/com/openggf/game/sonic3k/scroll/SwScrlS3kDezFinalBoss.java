package com.openggf.game.sonic3k.scroll;

import com.openggf.camera.NativeViewportFraming;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;

/** DEZ3 sub_5A508, sub_5A76C and loc_5A734: boss on Plane A, arena on Plane B. */
public final class SwScrlS3kDezFinalBoss extends SwScrlS3kDefault {
    private short foregroundY;
    private int backgroundX;

    @Override public void update(int[] buffer, int cameraX, int cameraY, int clock, int act) {
        var state = GameServices.hasRuntime() && GameServices.zoneRuntimeState() instanceof DezFinalBossZoneRuntimeState dez
                ? dez : null;
        if (state == null) return;
        int inset = NativeViewportFraming.inset(GameServices.camera().getWidth());
        render(buffer, cameraX, state, inset);
    }

    void render(int[] buffer, int cameraX, DezFinalBossZoneRuntimeState state) {
        render(buffer, cameraX, state, 0);
    }

    private void render(int[] buffer, int cameraX, DezFinalBossZoneRuntimeState state, int inset) {
        resetScrollTracking();
        // During a staged refill loc_5A734 retains the previous horizontal plane word.
        int foregroundX = state.retainedPlaneX() != 0 ? state.retainedPlaneX() : state.planeX();
        // Native HScroll words are measured from the 320px view. Widescreen
        // reveals equal margins around it, so translate both planes by the
        // viewport inset; never feed the visible left edge to ROM object logic.
        short fg = (short) (-foregroundX + inset);
        foregroundY = (short) state.planeY();
        vscrollFactorBG = (short) state.screenY();
        // sub_5A508 uses Camera_X & $1FF for the arena's repeated draw window.
        backgroundX = cameraX & 0x1FF;
        for (int line = 0; line < 224; line++) {
            // HScroll_table+$08 remains zero; +$0A is Camera_X. The $E0 band
            // is measured from Camera_Y_copy, so normally the last 32 lines move.
            short bg = state.screenY() + line < 0xE0 ? (short) inset : (short) -cameraX;
            buffer[line] = (fg & 0xFFFF) << 16 | bg & 0xFFFF;
            trackOffset(fg, bg);
        }
    }
    @Override public short getVscrollFactorFG() { return foregroundY; }
    @Override public int getBgCameraX() { return backgroundX - 1; }
    @Override public int getBgPeriodWidth() { return 512; }
}
