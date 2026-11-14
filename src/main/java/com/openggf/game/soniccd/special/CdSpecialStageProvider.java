package com.openggf.game.soniccd.special;

import com.openggf.game.GameServices;
import com.openggf.game.ResultsScreen;
import com.openggf.game.SpecialStageAccessType;
import com.openggf.game.SpecialStageDebugProvider;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.soniccd.CdDiscDataSource;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Sonic CD's special stage on the engine's {@code SPECIAL_STAGE} mode. A stage starts from a
 * {@link CdSpecialStageEntry} (the program-flow owner calls {@link #begin}); a plain
 * {@link #initializeStage(int)} enters stage {@code n} in normal mode with the stones last
 * reported, for development. When it ends, {@link #result()} is the exit contract.
 */
public final class CdSpecialStageProvider implements SpecialStageProvider {
    private final Supplier<CdDiscDataSource> disc;
    private CdSpecialStageAssets assets;
    private CdSpecialStageSession session;
    private CdSpecialStagePresentation presentation;
    private CdSpecialStageEntry pending;
    private int timeStones;
    private int held;
    private SpecialStageViewport viewport = SpecialStageViewport.nativeViewport();

    public CdSpecialStageProvider(Supplier<CdDiscDataSource> disc) {
        this.disc = Objects.requireNonNull(disc, "disc");
    }

    private java.util.function.Consumer<CdSpecialStageResult> onEnded = result -> { };
    private boolean reported;
    /** The exit contract of the stage that ended last; the engine resets the session before its results. */
    private CdSpecialStageResult ended;

    /** Receives the exit contract once, when the stage ends (the program flow's return). */
    public void onEnded(java.util.function.Consumer<CdSpecialStageResult> listener) {
        this.onEnded = Objects.requireNonNull(listener, "listener");
    }

    /** Primes the next {@link #initializeStage(int)} with the flow's entry, without starting it. */
    public void prepare(CdSpecialStageEntry entry) {
        pending = Objects.requireNonNull(entry, "entry");
    }

    /** The program-flow entry: stage, mode and stones (see {@link CdSpecialStageEntry}). */
    public void begin(CdSpecialStageEntry entry) throws IOException {
        pending = Objects.requireNonNull(entry, "entry");
        initializeStage(entry.stageId());
    }

    @Override
    public void initializeStage(int stageIndex) throws IOException {
        if (assets == null) assets = CdSpecialStageAssets.load(Objects.requireNonNull(disc.get(), "Sonic CD disc"));
        CdSpecialStageEntry entry = pending != null && pending.stageId() == stageIndex ? pending
                : CdSpecialStageEntry.normal(stageIndex, timeStones);
        pending = null;
        session = new CdSpecialStageSession(assets, entry);
        presentation = new CdSpecialStagePresentation(assets, entry.stageId());
        held = 0;
        reported = false;
    }

    @Override public void initialize() throws IOException { initializeStage(session == null ? 0 : session.stage().stageId()); }

    @Override
    public void update() {
        if (session == null) return;
        session.frame(held);
        // PlaySubFMSounds: the update's FM requests reach the driver when its output is collected
        // (Main.asm:3233, 2686-2696). They are requested by native FM ID.
        for (CdSpecialSound sound : session.takeSounds()) GameServices.audio().playSfx(sound.fmId());
        if (session.ended() && !reported) {
            reported = true;
            CdSpecialStageResult result = result();
            ended = result;
            onEnded.accept(result);
        }
    }

    /**
     * {@code SPCmd_LoadSpecStage} starts the special-stage CD track before the program runs
     * (SPX.asm:1491-1494): the audio lane's {@code SCMD_SPECMUS} command.
     */
    @Override public int getStageMusicId() { return com.openggf.game.soniccd.audio.CdSubCpuAudio.commandId("SCMD_SPECMUS"); }

    /** After it returns, the results track plays once outside Time Attack (SPX.asm:1497-1503): {@code SCMD_RESULTMUS}. */
    @Override public int getResultsMusicId() { return com.openggf.game.soniccd.audio.CdSubCpuAudio.commandId("SCMD_RESULTMUS"); }

    @Override
    public void draw() {
        if (session == null || presentation == null) return;
        presentation.draw(GameServices.graphics(), session, viewport.innerH32OriginX());
    }

    @Override public void handleInput(int heldButtons, int pressedButtons) { held = heldButtons & 0xFF; }
    @Override public boolean isFinished() { return session != null && session.ended(); }
    @Override public void reset() { session = null; presentation = null; }
    @Override public boolean isInitialized() { return session != null; }
    @Override public boolean hasSpecialStages() { return true; }
    @Override public SpecialStageAccessType getAccessType() { return SpecialStageAccessType.GIANT_RING; }
    @Override public int getCurrentStage() { return session == null ? 0 : session.stage().stageId(); }

    /** The exit contract once {@link #isFinished()}. */
    public CdSpecialStageResult result() {
        CdSpecialStageResult result = session.result();
        timeStones = result.timeStones();
        return result;
    }

    public CdSpecialStageSession session() { return session; }
    @Override public boolean isEmeraldCollected() { return session != null && session.ended() && session.result().timeStoneWon(); }
    @Override public int getEmeraldIndex() { return isEmeraldCollected() ? getCurrentStage() : -1; }
    @Override public int getRingsCollected() { return session == null ? 0 : session.stage().rings(); }
    @Override public void setEmeraldCollected(boolean collected) { }
    @Override public void setSpecialStageViewport(SpecialStageViewport viewport) { this.viewport = Objects.requireNonNull(viewport); }
    @Override public SpecialStageViewport getSpecialStageViewport() { return viewport; }
    @Override public boolean isSpriteDebugMode() { return false; }
    @Override public void toggleSpriteDebugMode() { }
    @Override public void cyclePlaneDebugMode() { }
    @Override public SpecialStageDebugProvider getDebugProvider() { return null; }
    @Override public boolean isAlignmentTestMode() { return false; }
    @Override public void toggleAlignmentTestMode() { }
    @Override public void adjustAlignmentOffset(int delta) { }
    @Override public void adjustAlignmentSpeed(double delta) { }
    @Override public void toggleAlignmentStepMode() { }
    @Override public void renderAlignmentOverlay(int viewportWidth, int viewportHeight) { }
    @Override public void renderLagCompensationOverlay(int viewportWidth, int viewportHeight) { }
    @Override public void setLagCompensation(double factor) { }

    /**
     * Results for the stage that ended, after the engine reset the session. Score (units of ten), the next
     * extra-life score and lives are the campaign's ({@code score}, {@code nextLifeScore}, {@code lives}).
     */
    @Override
    public ResultsScreen createResultsScreen(int ringsCollected, boolean gotEmerald, int stageIndex, int totalEmeraldCount) {
        CdSpecialStageResult result = session != null && session.ended() ? session.result() : ended;
        // Temporary and Time Attack runs branch past the results screen (Special Stage/Main.asm:239-241).
        if (result != null && !result.showsResults()) return com.openggf.game.NoOpResultsScreen.INSTANCE;
        var game = GameServices.gameStateOrNull();
        var policy = GameServices.module().getGameService(com.openggf.game.internal.ScoreAdditionPolicy.class);
        int nextLife = policy instanceof com.openggf.game.soniccd.program.CdScorePolicy cd ? cd.nextLifeScore() / 10 : 5000;
        return new CdSpecialStageResults(result, game == null ? 0 : game.getScore() / 10, nextLife,
                game == null ? 3 : game.getLives());
    }
}
