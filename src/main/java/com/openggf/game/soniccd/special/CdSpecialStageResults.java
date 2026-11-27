package com.openggf.game.soniccd.special;

import com.openggf.control.InputHandler;
import com.openggf.game.LevelBackdropResultsScreen;
import com.openggf.game.ResultsScreen;
import com.openggf.game.internal.SpecialStageStartInput;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdScorePolicy;
import com.openggf.graphics.GLCommand;
import com.openggf.level.Palette;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The special stage results ({@code Results}, scddisasm/src/Special Stage/Main.asm:281-402). The ring bonus is 20 per
 * ring and the time bonus 20 per second left; each frame moves 20 points from the ring bonus,
 * then once it is empty from the time bonus, into the score, with an extra life at each
 * 5000-point threshold ({@code AddScore}, scddisasm/src/Special Stage/Main.asm:433-455). The screen lasts 8 seconds
 * (Main.asm:355) unless Start is pressed; leaving adds what is left. The header reads
 * "SPECIAL STAGE" when time ran out, "GOT THEM ALL!!" with all seven stones and "TIME STONES"
 * otherwise ({@code DrawResultsBase}, scddisasm/src/Special Stage/Main.asm:495-503).
 *
 * <p>Before the tally the picture fades in from white: one V-blank after {@code PrepFadeFromWhite},
 * then {@code FadeFromWhite}'s 24 steps (Main.asm:349-353). {@code VInt_Results} runs on every one of
 * those V-blanks, so the numbers and icons are drawn from the first frame. The engine owns the
 * closing {@code FadeToWhite} (Main.asm:400).
 *
 * <p>Scores are in units of ten, as the program keeps them. The campaign receives
 * the stored score, next threshold and lives when the screen ends.
 */
public final class CdSpecialStageResults implements ResultsScreen, LevelBackdropResultsScreen, SpecialStageStartInput {
    public enum Header { SPECIAL_STAGE, TIME_STONES, GOT_THEM_ALL }

    /** {@code PrepFadeFromWhite}'s V-blank and {@code FadeFromWhite}'s 24 (Main.asm:349-353, 2493-2525). */
    static final int FADE_IN_FRAMES = 1 + 24;

    private int ringBonus;
    private int timeBonus;
    private int score;
    private int nextLifeScore;
    private int lives;
    private int extraLives;
    private int timer = 8 * 60;
    private int fadeIn = FADE_IN_FRAMES;
    private boolean tallied;
    private boolean complete;
    private final Header header;
    private final List<CdSpecialSound> sounds = new ArrayList<>(2);
    private final CdSpecialStageResultsPicture picture;
    private final CdProgramScreenRenderer renderer = new CdProgramScreenRenderer();
    record Tally(int score, int nextLifeScore, int lives) { }

    private final Consumer<Tally> onComplete;

    /** The tally alone, without a picture or campaign (for the tally's own checks). */
    public CdSpecialStageResults(CdSpecialStageResult result, int score, int nextLifeScore, int lives) {
        this(result, score, nextLifeScore, lives, null, points -> { });
    }

    /**
     * @param assets the stage program's data, for the picture
     * @param onComplete receives the stored tally (score and threshold in units of ten)
     */
    CdSpecialStageResults(CdSpecialStageResult result, int score, int nextLifeScore, int lives,
                          CdSpecialStageAssets assets, Consumer<Tally> onComplete) {
        this.ringBonus = result.ringBonus();
        this.timeBonus = result.timeBonus();
        this.score = score;
        this.nextLifeScore = nextLifeScore;
        this.lives = lives;
        this.onComplete = Objects.requireNonNull(onComplete, "onComplete");
        this.header = timeBonus == 0 ? Header.SPECIAL_STAGE
                : result.timeStones() == 0x7F ? Header.GOT_THEM_ALL : Header.TIME_STONES;
        try {
            this.picture = assets == null ? null
                    : new CdSpecialStageResultsPicture(assets, result.stageId(), header, result.timeStones());
        } catch (IOException e) {
            throw new UncheckedIOException("Sonic CD special stage results art", e);
        }
    }

    /** One frame: a fade-in step, or one {@code Results_MainLoop} pass (scddisasm/src/Special Stage/Main.asm:360-391). */
    public void frame(boolean startTapped) {
        if (complete) return;
        sounds.clear();
        if (fadeIn > 0) {
            int step = FADE_IN_FRAMES - fadeIn--;
            if (picture != null && step > 0) picture.fadeFromWhiteStep(step - 1);
            vint();
            return;
        }
        if (ringBonus != 0) {
            tally();
            ringBonus -= 20;
        } else if (timeBonus != 0) {
            timeBonus -= 20;
            tally();
        }
        vint();
        if (timer != 0) timer--; // VInt_Finish (scddisasm/src/Special Stage/Main.asm:984-989)
        if (!tallied && ringBonus + timeBonus == 0) {
            tallied = true;
            sounds.add(CdSpecialSound.KACHING);
        }
        if (startTapped || timer == 0) {
            addScore(ringBonus + timeBonus); // .Done (Main.asm:393-396); the bonuses keep their values
            sounds.add(CdSpecialSound.WARP);
            vint(); // FadeToWhite's first V-blank shows the final score
            complete = true;
            onComplete.accept(new Tally(score, nextLifeScore, lives));
        }
    }

    private void vint() {
        if (picture == null) return;
        try {
            picture.vint(score, timeBonus, ringBonus, extraLives);
        } catch (IOException e) {
            throw new UncheckedIOException("Sonic CD special stage results art", e);
        }
    }

    private void tally() {
        sounds.add(CdSpecialSound.TALLY); // USA plays the tally sound (Main.asm:420-423)
        addScore(20);
    }

    private void addScore(int points) {
        // AddScore (Special Stage/Main.asm:433-452) applies thresholds before storing the capped score.
        int total = score + points;
        while (Integer.compareUnsigned(total, nextLifeScore) >= 0) {
            nextLifeScore += 5000;
            lives = CdScorePolicy.capSpecialStageLives(lives + 1);
            extraLives++;
        }
        score = total >= 1_000_000 ? 999_999 : total;
    }

    /**
     * The engine passes its {@link InputHandler} as the context; Start is the program's {@code ctrlTap}
     * bit 7, read by {@code VInt_Results} (Main.asm:968) and tested after it (Main.asm:388-389).
     */
    @Override
    public void update(int frameCounter, Object context) {
        boolean start = context instanceof InputHandler input && input.logical().player1().startPressed();
        frame(start);
        for (CdSpecialSound sound : sounds) com.openggf.game.GameServices.audio().playSfx(sound.fmId());
    }

    @Override public boolean isComplete() { return complete; }

    @Override public boolean ownsStartInput() { return !complete; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (picture != null) renderer.draw(picture.frame());
    }

    @Override public boolean drawsLevelBackdrop() { return false; }

    /** Backdrop colour 0 ({@code ResultsVDPRegs} register 7, Main.asm:3525). */
    @Override
    public Palette.Color resultsBackdropColor() {
        if (picture == null) return null;
        Palette.Color colour = new Palette.Color();
        colour.fromSegaFormat(picture.colour(0));
        return colour;
    }

    CdSpecialStageResultsPicture picture() { return picture; }
    public int score() { return score; }
    public int lives() { return lives; }
    public int extraLives() { return extraLives; }
    public int ringBonus() { return ringBonus; }
    public int timeBonus() { return timeBonus; }
    public Header header() { return header; }
    /** The sounds the last frame requested, in order. */
    public List<CdSpecialSound> sounds() { return List.copyOf(sounds); }
}
