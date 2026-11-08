package com.openggf.game.sonic2.competition.state;

import com.openggf.game.rewind.RewindSnapshottable;
import java.util.function.BooleanSupplier;

/** Native Slow_motion_flag byte; write from the native cheat owner, never from input polling. */
public final class Sonic2NativeControlState implements BooleanSupplier, RewindSnapshottable<Boolean> {
    private boolean slowMotion;
    public void writeSlowMotionFlag(boolean value) { slowMotion = value; }
    @Override public boolean getAsBoolean() { return slowMotion; }
    @Override public String key() { return "sonic2-native-controls"; }
    @Override public Boolean capture() { return slowMotion; }
    @Override public void restore(Boolean value) { slowMotion = java.util.Objects.requireNonNull(value); }
    @Override public void resetForMissingSnapshot() { slowMotion = false; }
}
