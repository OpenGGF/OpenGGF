package com.openggf.game.modzone;

import com.openggf.level.scroll.BgTilemapUpdateMode;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.Objects;

/** Pins the selected stock act while retaining every scroll output and its logical rewind state. */
final class SourceActScrollHandler implements ZoneScrollHandler {
    private final ZoneScrollHandler delegate;
    private final int sourceAct;

    SourceActScrollHandler(ZoneScrollHandler delegate, int sourceAct) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.sourceAct = sourceAct;
    }

    @Override public void init(int act, int cameraX, int cameraY) { delegate.init(sourceAct, cameraX, cameraY); }
    @Override public void update(int[] words, int cameraX, int cameraY, int frame, int act) {
        delegate.update(words, cameraX, cameraY, frame, sourceAct);
    }
    @Override public BgTilemapUpdateMode getBgTilemapUpdateMode() { return delegate.getBgTilemapUpdateMode(); }
    @Override public short getVscrollFactorBG() { return delegate.getVscrollFactorBG(); }
    @Override public short getVscrollFactorFG() { return delegate.getVscrollFactorFG(); }
    @Override public short[] getPerLineVScrollBG() { return delegate.getPerLineVScrollBG(); }
    @Override public short[] getPerColumnVScrollBG() { return delegate.getPerColumnVScrollBG(); }
    @Override public short[] getPerColumnVScrollFG() { return delegate.getPerColumnVScrollFG(); }
    @Override public int getMinScrollOffset() { return delegate.getMinScrollOffset(); }
    @Override public int getMaxScrollOffset() { return delegate.getMaxScrollOffset(); }
    @Override public int getBgCameraX() { return delegate.getBgCameraX(); }
    @Override public int getBgPeriodWidth() { return delegate.getBgPeriodWidth(); }
    @Override public int getShakeOffsetX() { return delegate.getShakeOffsetX(); }
    @Override public int getShakeOffsetY() { return delegate.getShakeOffsetY(); }
    @Override public Object captureRewindState() { return delegate.captureRewindState(); }
    @Override public void restoreRewindState(Object state) { delegate.restoreRewindState(state); }
}
