package com.openggf.game.sonic2.competition.state;

/** One Teleport_timer/Teleport_flag owner, captured only through the session snapshot. */
public final class Sonic2TeleportClock {
    public record State(int timer, boolean flag) {
        public State {
            if (timer < 0 || timer > 255 || flag != (timer != 0))
                throw new IllegalArgumentException("Invalid native teleport state");
        }
    }
    private int timer;
    public int timer() { return timer; }
    public boolean active() { return timer != 0; }
    public void start(int ticks) {
        if (ticks < 1 || ticks > 255) throw new IllegalArgumentException("Teleport ticks");
        timer = ticks;
    }
    /** Vint_Level (s2.asm:702-713): before RunObjects, held even during slow-motion bodies. */
    public void afterNativeWait(boolean gamePaused) {
        if (timer != 0 && !gamePaused) --timer;
    }
    public State capture() { return new State(timer, active()); }
    public void restore(State state) { timer = java.util.Objects.requireNonNull(state).timer(); }
    public void reset() { timer = 0; }
}
