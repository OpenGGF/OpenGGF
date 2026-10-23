package com.openggf.game.sonic2.specialstage;

import java.util.List;

/** Engine-internal construction and opaque rewind bridge for native two-human halfpipes. */
public final class CompetitiveSpecialStagePlayerAccess {
    private CompetitiveSpecialStagePlayerAccess() { }

    public static final class Pair {
        private final SharedSwap swap = new SharedSwap();
        private final List<Sonic2SpecialStagePlayer> players;

        private Pair() {
            var sonic = new Sonic2SpecialStagePlayer(Sonic2SpecialStagePlayer.PlayerType.SONIC, true, swap);
            var tails = new Sonic2SpecialStagePlayer(Sonic2SpecialStagePlayer.PlayerType.TAILS, false, swap);
            players = List.of(sonic, tails);
            sonic.setOtherPlayer(tails); tails.setOtherPlayer(sonic);
            for (var player : players) {
                player.initializeScalarStateFromRomObjectRoutine();
                player.setSpawned(true);
            }
            tails.tickTailsTailsStartupAnimation();
        }
        public List<Sonic2SpecialStagePlayer> players() { return players; }
        public Sonic2SpecialStagePlayer player(int slot) { return players.get(slot); }
        public void step(int slot, int held, int pressed, int animationTimer) {
            var player = player(slot);
            player.setGlobalAnimFrameTimer(animationTimer);
            player.update(held, pressed);
        }
        public void collectRing(int slot) {
            if (player(slot).getRings() < 999) player(slot).collectRing();
        }
        public int loseRings(int slot) { return player(slot).loseRingsFromBombHit(); }
        public void resetRings() {
            for (var player : players) while (player.getRings() != 0) player.loseRingsFromBombHit();
        }
        /** SSClearObjs removes both native object IDs before terminal publication. */
        public void clearPlayers() { for (var player : players) player.setSpawned(false); }
        public Object capture() {
            return new PairState(player(0).captureRewindSnapshot(), player(1).captureRewindSnapshot(), swap.value);
        }
        public void restore(Object snapshot) {
            var state = (PairState) snapshot;
            player(0).restoreRewindSnapshot(state.sonic());
            player(1).restoreRewindSnapshot(state.tails());
            swap.value = state.swap();
        }
    }
    private record PairState(Sonic2SpecialStageSnapshot.PlayerSnapshot sonic,
                             Sonic2SpecialStageSnapshot.PlayerSnapshot tails, boolean swap) { }
    private static final class SharedSwap implements SwapPositionsPort {
        private boolean value;
        @Override public void set(boolean value) { this.value = value; }
        @Override public boolean get() { return value; }
        @Override public void toggle() { value = !value; }
    }
    public static Pair create() { return new Pair(); }
    public static Object captureTrack(Sonic2TrackAnimator animator) { return animator.captureRewindSnapshot(); }
    public static void restoreTrack(Sonic2TrackAnimator animator, Object state) {
        animator.restoreRewindSnapshot((Sonic2SpecialStageSnapshot.TrackAnimatorSnapshot) state);
    }
    public static Object captureObject(Sonic2SpecialStageObject object) { return object.captureRewindSnapshot(); }
    public static Sonic2SpecialStageObject restoreObject(Object value) {
        var state = (Sonic2SpecialStageSnapshot.ObjectSnapshot) value;
        if (state.type() == Sonic2SpecialStageSnapshot.SpecialStageObjectType.RING) {
            var ring = new Sonic2SpecialStageRing(); ring.restoreRewindSnapshot(state); return ring;
        }
        if (state.type() == Sonic2SpecialStageSnapshot.SpecialStageObjectType.BOMB) {
            var bomb = new Sonic2SpecialStageBomb(); bomb.restoreRewindSnapshot(state); return bomb;
        }
        throw new IllegalArgumentException("Competitive halfpipe object " + state.type());
    }
    public static Object captureRainbow(Sonic2SpecialStageCheckpoint checkpoint) {
        return checkpoint.captureRewindSnapshot();
    }
    public static void restoreRainbow(Sonic2SpecialStageCheckpoint checkpoint, Object value) {
        checkpoint.restoreRewindSnapshot((Sonic2SpecialStageSnapshot.CheckpointSnapshot) value);
    }
}
