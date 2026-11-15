package com.openggf.game.soniccd.special;

import com.openggf.game.ResultsScreen;
import com.openggf.graphics.GLCommand;

import java.util.List;

/**
 * The special stage results tally ({@code Results}, scddisasm/src/Special Stage/Main.asm:281-402). The ring bonus is 20 per
 * ring and the time bonus 20 per second left; each frame moves 20 points from the ring bonus,
 * then once it is empty from the time bonus, into the score, with an extra life at each
 * 5000-point threshold ({@code AddScore}, scddisasm/src/Special Stage/Main.asm:433-455). The screen lasts 8 seconds
 * (Main.asm:355) unless Start is pressed; leaving adds what is left. The header reads
 * "SPECIAL STAGE" when time ran out, "GOT THEM ALL!!" with all seven stones and "TIME STONES"
 * otherwise ({@code DrawResultsBase}, scddisasm/src/Special Stage/Main.asm:495-503).
 *
 * <p>Presentation (results art, the Time Stone row, digits and the extra-life icons) is not
 * drawn yet; {@link #appendRenderCommands} adds nothing.
 */
public final class CdSpecialStageResults implements ResultsScreen {
    public enum Header { SPECIAL_STAGE, TIME_STONES, GOT_THEM_ALL }

    private int ringBonus;
    private int timeBonus;
    private int score;
    private int nextLifeScore;
    private int lives;
    private int extraLives;
    private int timer = 8 * 60;
    private boolean tallied;
    private boolean complete;
    private final Header header;
    private CdSpecialSound lastSound;

    public CdSpecialStageResults(CdSpecialStageResult result, int score, int nextLifeScore, int lives) {
        this.ringBonus = result.ringBonus();
        this.timeBonus = result.timeBonus();
        this.score = score;
        this.nextLifeScore = nextLifeScore;
        this.lives = lives;
        this.header = timeBonus == 0 ? Header.SPECIAL_STAGE
                : result.timeStones() == 0x7F ? Header.GOT_THEM_ALL : Header.TIME_STONES;
    }

    /** One {@code Results_MainLoop} pass (scddisasm/src/Special Stage/Main.asm:360-391); {@code startTapped} ends it early. */
    public void frame(boolean startTapped) {
        if (complete) return;
        lastSound = null;
        if (ringBonus != 0) {
            tally();
            ringBonus -= 20;
        } else if (timeBonus != 0) {
            timeBonus -= 20;
            tally();
        }
        if (timer != 0) timer--; // VInt_Finish (scddisasm/src/Special Stage/Main.asm:987-989)
        if (!tallied && ringBonus + timeBonus == 0) {
            tallied = true;
            lastSound = CdSpecialSound.KACHING;
        }
        if (startTapped || timer == 0) {
            addScore(ringBonus + timeBonus); // .Done (Main.asm:393-396)
            lastSound = CdSpecialSound.WARP;
            complete = true;
        }
    }

    private void tally() {
        lastSound = CdSpecialSound.TALLY; // USA plays the tally sound (Main.asm:420-423)
        addScore(20);
    }

    private void addScore(int points) {
        int total = score + points;
        while (Integer.compareUnsigned(total, nextLifeScore) >= 0) {
            nextLifeScore += 5000;
            lives++;
            extraLives++;
            if (lives >= 250) lives = 249;
        }
        score = total >= 1_000_000 ? 999_999 : total;
    }

    @Override public void update(int frameCounter, Object context) { frame(false); }
    @Override public boolean isComplete() { return complete; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    public int score() { return score; }
    public int lives() { return lives; }
    public int extraLives() { return extraLives; }
    public int ringBonus() { return ringBonus; }
    public int timeBonus() { return timeBonus; }
    public Header header() { return header; }
    public CdSpecialSound sound() { return lastSound; }
}
