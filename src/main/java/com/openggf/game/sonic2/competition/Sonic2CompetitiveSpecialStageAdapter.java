package com.openggf.game.sonic2.competition;

import com.openggf.control.InputHandler;

/**
 * Competitive halfpipe boundary. Its owner must supply two human actors and competitive checkpoints;
 * ordinary CPU-sidekick and summed-ring gate methods are deliberately absent from this port.
 */
public final class Sonic2CompetitiveSpecialStageAdapter {
    public interface Halfpipe {
        boolean supportsCompetitiveStage(int index);
        void loadCompetitiveStage(int index, Events events);
        void step(InputHandler input);
        void draw();
        void reset();
    }
    public interface Events {
        void collectedRing(int slot);
        void hitBomb(int slot);
        void checkpoint();
        /** VSReset's six-word archive and bank clear, at the native hand/tie clock. */
        void archivedSector(int sector, int p1Rings, int p2Rings);
        /** A nonfinal tie advances the sector without archiving or clearing either bank. */
        void tiedSector(int sector);
        /** RingCheckTrigger has cleared Object_RAM/SAT after the final 70/48-tick wait. */
        void stageComplete();
    }
    private final Sonic2CompetitionSession session;
    private final Halfpipe halfpipe;
    private final Events events = new Events() {
        @Override public void collectedRing(int slot) {
            requireActive();
            var state = session.levelState(slot);
            state.setRings(Math.min(999, state.getRings() + 1));
        }
        @Override public void hitBomb(int slot) {
            requireActive();
            var state = session.levelState(slot);
            state.setRings(Math.max(0, state.getRings() - 10));
        }
        @Override public void checkpoint() {
            requireActive();
            session.finishSpecialSector();
        }
        @Override public void archivedSector(int sector, int p1Rings, int p2Rings) {
            requireActive();
            session.archiveSpecialSector(sector, p1Rings, p2Rings);
        }
        @Override public void tiedSector(int sector) {
            requireActive();
            session.tieSpecialSector(sector);
        }
        @Override public void stageComplete() {
            requireActive();
            session.completeSpecialStage();
        }
    };
    public Sonic2CompetitiveSpecialStageAdapter(Sonic2CompetitionSession session, Halfpipe halfpipe) {
        this.session = java.util.Objects.requireNonNull(session);
        this.halfpipe = java.util.Objects.requireNonNull(halfpipe);
    }
    public boolean supportsStage(int index) { return halfpipe.supportsCompetitiveStage(index); }
    public void load() {
        requireActive();
        if (!supportsStage(session.specialStageIndex())) throw new IllegalStateException("Competitive halfpipe unavailable");
        halfpipe.loadCompetitiveStage(session.specialStageIndex(), events);
    }
    public void step(InputHandler input) { requireActive(); halfpipe.step(input); }
    public void draw() { requireActive(); halfpipe.draw(); }
    public void reset() { halfpipe.reset(); }
    private void requireActive() {
        if (session.phase() != Sonic2CompetitionSession.Phase.SPECIAL_STAGE)
            throw new IllegalStateException("Competitive special stage is not active");
    }
}
