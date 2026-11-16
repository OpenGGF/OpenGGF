package com.openggf.game.sonic3k.scroll;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollProjectionSource;
import com.openggf.level.scroll.ScrollProjectionContext;
import com.openggf.level.scroll.ScrollProjectionPhase;

/** Projects LBZ's completed event publication. Renderer rebuilds never dispatch background events. */
public class SwScrlLbz extends AbstractZoneScrollHandler implements ScrollProjectionSource {
    static final int[] LBZ1_BG_DEFORM = LbzBackgroundEvents.LBZ1_BG_DEFORM;
    private final byte[] waterlineData;
    private final byte[] deathEggWaveData;
    private final boolean detached;
    private LbzBackgroundEvents picture;
    private boolean beforeScreenInit = true;
    public SwScrlLbz() { this(null, null); }
    public SwScrlLbz(byte[] waterlineData) { this(waterlineData, null); }
    public SwScrlLbz(byte[] waterlineData, byte[] deathEggWaveData) { this(waterlineData, deathEggWaveData, false); }
    private SwScrlLbz(byte[] waterlineData, byte[] deathEggWaveData, boolean detached) {
        this.waterlineData = waterlineData;
        this.deathEggWaveData = deathEggWaveData;
        this.detached = detached;
    }
    /** Immutable ROM inputs for the fresh-Level event owner; no live continuation is stored here. */
    public LbzBackgroundEvents createBackgroundPublication() { return new LbzBackgroundEvents(waterlineData, deathEggWaveData); }
    public static SwScrlLbz detachedPresentation() { return new SwScrlLbz(null, null, true); }
    @Override public void update(int[] words, int x, int y, int frame, int act) {
        var manager = !detached && GameServices.hasRuntime()
                && GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager m ? m : null;
        var events = manager == null ? null : manager.getLbzEvents();
        if (events != null && events.backgroundPublication() != null) {
            beforeScreenInit = false;
            picture = events.backgroundPublication();
            picture.copyPublishedWords(words);
        } else if (events != null) {
            // Before fresh ScreenInit there is no completed native picture.
            // Loading/rebuild must not manufacture or advance a detached owner.
            java.util.Arrays.fill(words, ((-x) & 0xFFFF) << 16);
            beforeScreenInit = true;
            picture = null; minScrollOffset = maxScrollOffset = 0; vscrollFactorBG = 0;
            return;
        } else {
            beforeScreenInit = false;
            // Explicit detached pictures own no gameplay continuation.
            if (picture == null) picture = LbzBackgroundEvents.detachedPresentation(waterlineData, deathEggWaveData);
            picture.update(words, x, y, frame, act);
        }
        minScrollOffset = picture.getMinScrollOffset();
        maxScrollOffset = picture.getMaxScrollOffset();
        vscrollFactorBG = picture.getVscrollFactorBG();
    }
    @Override public int getBgCameraX() { return picture == null ? Integer.MIN_VALUE : picture.getBgCameraX(); }
    @Override public int getBgPeriodWidth() { return picture == null ? 512 : picture.getBgPeriodWidth(); }
    @Override public short getVscrollFactorFG() { return picture == null ? 0 : picture.getVscrollFactorFG(); }
    @Override public int getShakeOffsetY() { return picture == null ? 0 : picture.getShakeOffsetY(); }
    short getLbz2HScrollWordForTest(int i) { return picture.getLbz2HScrollWordForTest(i); }
    @Override public ScrollProjectionPhase captureProjection(ScrollProjectionContext context) {
        if (!beforeScreenInit) return picture.captureProjection(context);
        // loc_6310 (sonic3k.asm:7788-7798): initial DeformBgLayer precedes
        // LevelSetup/ScreenInit. Freeze the already-published empty BG phase;
        // sampling must not initialize, tick, or retain a detached event owner.
        var output = context.nativeOutput();
        return view -> {
            int[] words = new int[view.height()];
            java.util.Arrays.fill(words, ((-view.cameraX()) & 0xFFFF) << 16);
            return new com.openggf.level.scroll.ScrollProjection(words, null, null, null,
                    (short) (output.foregroundY() + view.cameraY() - context.cameraY()),
                    output.backgroundY(), output.backgroundX(), output.backgroundPeriod(),
                    output.shakeX(), output.shakeY(), output.tilemapMode());
        };
    }
}
