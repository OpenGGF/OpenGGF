package com.openggf.game.sonic2.competition.state;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;

/** Independent actor status_secondary bit 1; Super_Sonic_flag and its timer have different owners. */
public final class Sonic2SecondaryInvincibility implements RewindSnapshottable<Sonic2SecondaryInvincibility.State> {
    private boolean primary;
    private boolean secondary;
    @RewindTransient(reason = "structural live-world binding, not gameplay values")
    private GameplayModeContext context;
    @RewindTransient(reason = "structural native roster; actors are resolved again after recreation")
    private Sonic2CompetitionSession session;
    @RewindTransient(reason = "structural ownership lifetime; retired actors return to ordinary inference")
    private boolean bound;

    public record State(boolean primary, boolean secondary) { }

    public void initialize(GameplayModeContext context, Sonic2CompetitionSession session) {
        retire();
        this.context = context;
        this.session = session;
        bound = true;
        // Fresh actor RAM has a clear status byte, even when emerald progress was retained by options VS.
        primary = secondary = false;
        bindActors();
    }

    private void bindActors() {
        for (int slot = 0; slot < 2; slot++) {
            final int index = slot;
            final var actor = session.player(slot);
            PlayableSpriteInternalAccess.bindNativeSecondaryInvincibility(actor,
                    new PlayableSpriteInternalAccess.SecondaryInvincibility() {
                        @Override public Boolean read() {
                            return bound && session.player(index) == actor
                                    && com.openggf.game.competition.NativeCompetitionRoster.from(context.getWorldSession()) == session
                                    ? (index == 0 ? primary : secondary) : null;
                        }
                        @Override public void write(boolean value) {
                            if (read() == null) return;
                            if (index == 0) primary = value; else secondary = value;
                        }
                    });
        }
    }

    public void retire() {
        bound = false;
        primary = secondary = false;
    }

    public void swapPlayers() {
        // teleport_swap_table (s2.asm:26169-26171): actor bytes include status_secondary; global Super does not travel.
        boolean previous = primary;
        primary = secondary;
        secondary = previous;
    }

    @Override public String key() { return "s2-native-secondary-invincibility"; }
    @Override public State capture() { return new State(primary, secondary); }
    @Override public void restore(State state) {
        primary = state.primary();
        secondary = state.secondary();
        // Canonical sprite restore may have recreated either stable human slot before this adapter runs.
        if (bound) bindActors();
    }
    @Override public void resetForMissingSnapshot() { primary = secondary = false; if (bound) bindActors(); }
}
