package com.openggf.game.sonic2.competition;

import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.rewind.snapshot.GameStateSnapshot;
import com.openggf.game.sonic2.Sonic2RevisionProfile;

import java.util.Objects;
import java.util.function.Supplier;

/** The native progress bank belongs to the controller slot and is never teleported. */
public final class Sonic2CompetitionPlayerState implements LevelState {
    private final int slot;
    private final Supplier<Sonic2RevisionProfile> revisionProfile;
    private final Progress game = new Progress();
    private int rings, totalRings, monitors, extraLifeFlags;
    private long frames;
    private boolean timerPaused;
    private boolean timedOut;
    private int nextExtraLifeScore = 5000;
    private Checkpoint checkpoint = Checkpoint.NONE;

    public Sonic2CompetitionPlayerState() { this(0, () -> Sonic2RevisionProfile.COMPATIBLE); }

    Sonic2CompetitionPlayerState(int slot, Supplier<Sonic2RevisionProfile> revisionProfile) {
        this.slot = slot;
        this.revisionProfile = Objects.requireNonNull(revisionProfile, "revisionProfile");
    }

    public record Checkpoint(int index, int x, int y, int cameraX, int cameraY, long frames) {
        static final Checkpoint NONE = new Checkpoint(0, 0, 0, 0, 0, 0);
    }
    public record State(GameStateSnapshot game, int rings, int totalRings, int monitors,
                        int extraLifeFlags, long frames, boolean paused, boolean timedOut, int nextLife,
                        Checkpoint checkpoint) { }

    private final class Progress extends GameStateManager {
        @Override public void addScore(int amount) {
            // Shared callers supply displayed points; AddPoints stores units of ten.
            // Keep the native threshold bank in ROM units and award at most one life per call.
            if (amount <= 0) return;
            super.addScore(Math.min(amount, 9999990 - getScore()));
            if (getScore() / 10 >= nextExtraLifeScore) {
                nextExtraLifeScore += 5000;
                addLife();
            }
        }
    }

    public GameStateManager gameState() { return game; }
    public int totalRings() { return totalRings; }
    public int monitorsBroken() { return monitors; }
    public int nextExtraLifeScore() { return nextExtraLifeScore; }
    public Checkpoint checkpoint() { return checkpoint; }
    public void brokenMonitor() { monitors = (monitors + 1) & 0xFFFF; }

    public void saveCheckpoint(int index, int x, int y, int cameraX, int cameraY) {
        if (index > checkpoint.index()) checkpoint = new Checkpoint(index, x, y, cameraX, cameraY, frames);
    }

    public void resetSession() { game.resetSession(); resetAct(); }
    /** Obj01_ResetLevel_Part2 keeps the running competition timer through independent respawn. */
    public void respawnFromCheckpoint() {
        resetRingsForLoss();
        timerPaused = false;
        timedOut = false;
    }
    public void resetAct() {
        int lives = game.getLives();
        int continues = game.getContinues();
        var chaos = game.getCollectedChaosEmeraldIndices();
        var supers = game.getCollectedSuperEmeraldIndices();
        boolean converted = game.isEmeraldsConverted();
        game.resetSession();
        // fixBugs=0 OptionScreen_Select_Not1P retains emerald progress; neither act-entry path clears it.
        // Preserve that launch inventory while clearing score and per-act flags.
        game.restoreSaveProgress(lives, continues, chaos, supers, converted);
        rings = totalRings = monitors = extraLifeFlags = 0;
        frames = 0;
        timerPaused = false;
        timedOut = false;
        nextExtraLifeScore = 5000;
        checkpoint = Checkpoint.NONE;
    }

    public Sonic2CompetitionResults.Metrics metrics() {
        return new Sonic2CompetitionResults.Metrics(game.getScore(), frames, rings, totalRings, monitors);
    }
    public State capture() {
        return new State(game.capture(), rings, totalRings, monitors, extraLifeFlags, frames,
                timerPaused, timedOut, nextExtraLifeScore, checkpoint);
    }
    public void restore(State state) {
        game.restore(state.game());
        rings = state.rings(); totalRings = state.totalRings(); monitors = state.monitors();
        extraLifeFlags = state.extraLifeFlags(); frames = state.frames();
        timerPaused = state.paused(); timedOut = state.timedOut(); nextExtraLifeScore = state.nextLife(); checkpoint = state.checkpoint();
    }
    @Override public void update() {
        if (timerPaused) return;
        if (frames == 35999) markTimeOver(); else frames++;
    }
    void markTimeOver() { timedOut = true; timerPaused = true; }
    @Override public int getRings() { return rings; }
    @Override public void setRings(int value) { rings = Math.max(0, Math.min(999, value)); }
    @Override public void addRings(int amount) {
        int previous = rings;
        // CollectRing_Sonic increments Rings_Collected even when held rings are
        // capped. REV01 then returns to sound before the HUD/life checks; Tails'
        // competition path always continues (s2.asm:25027-25103).
        if (amount > 0) totalRings = Math.min(999, totalRings + amount);
        if (amount > 0 && previous >= 999 && slot == 0
                && revisionProfile.get().revision() == com.openggf.data.RetailRomRevision.S2_REV01) return;
        if (amount > 0 && previous >= 999
                && revisionProfile.get().revision() != com.openggf.data.RetailRomRevision.UNKNOWN) {
            checkRingExtraLife();
            return;
        }
        setRings(rings + amount);
        if (amount > 0) {
            checkRingExtraLife();
        }
    }

    /** super_ring's REV00 ADDI.W is unclamped and never touches Rings_Collected. */
    void awardRingMonitor() {
        if (revisionProfile.get().isRev00()) rings = (rings + 10) & 0xFFFF;
        else {
            rings = Math.min(999, rings + 10);
            totalRings = Math.min(999, totalRings + 10);
        }
        checkRingExtraLife();
    }

    private void checkRingExtraLife() {
        int flag = rings >= 100 && (extraLifeFlags & 2) == 0 ? 2
                : rings >= 200 && (extraLifeFlags & 4) == 0 ? 4 : 0;
        if (flag != 0) { extraLifeFlags |= flag; game.addLife(); }
    }
    @Override public int getRingExtraLifeFlags() { return extraLifeFlags; }
    @Override public void setRingExtraLifeFlags(int flags) { extraLifeFlags = flags & 6; }
    @Override public boolean isTimeOver() { return timedOut; }
    @Override public String getDisplayTime() { return "%d:%02d".formatted(frames / 3600, frames / 60 % 60); }
    @Override public boolean shouldFlashTimer() { return frames >= 9 * 3600 && getFlashCycle(); }
    @Override public boolean getFlashCycle() { return (frames & 8) == 0; }
    @Override public void pauseTimer() { timerPaused = true; }
    @Override public void resumeTimer() { timerPaused = false; }
    @Override public boolean isTimerPaused() { return timerPaused; }
    @Override public int getElapsedSeconds() { return (int) (frames / 60); }
    @Override public long getTimerFrames() { return frames; }
    @Override public void setTimerFrames(long value) { frames = Math.max(0, Math.min(35999, value)); }
}
