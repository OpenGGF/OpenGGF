package paradise.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Presentation timeline for cards, toasts, HUD slides, the last shot's trail and the
 * scorecard tally. It is driven by explicit golf events and one {@link #tick} per unpaused
 * presentation row; it never reads or changes gameplay. Sound requests are returned as
 * {@link Cue} values for the adapter to map onto ROM sounds.
 */
public final class GolfFeedback {
    public enum Card { NONE, HOLE, HANDOFF }
    public enum Toast { NONE, TEE_OFF, PENALTY_DAMAGE, PENALTY_LOST, PENALTY_TIME, PENALTY, FINISH, GOLFER_DONE, REFUND }
    public enum Cue { HANDOFF, READY, FINISH, PENALTY, TALLY_TICK, TALLY_END, CLEAR }

    public static final int CARD_IN = 18, CARD_OUT = 14, HOLE_HOLD = 96;
    public static final int TOAST_TICKS = 120, TEE_OFF_TICKS = 54, HUD_SLIDE = 14, LOCK_FLASH = 10;
    /**
     * Results follow Sonic 2's order: the finish (signpost sound) first, the Stage Clear jingle
     * once it has registered, then the scorecard drop and its tally. Uncelebrated results
     * (concession, room errors) skip straight to the card.
     */
    public static final int CLEAR_DELAY = 36, CARD_DELAY = 84, CARD_DROP = 24, TALLY_DELAY = 36, TALLY_STEP = 3;
    public static final int TRAIL_POINTS = 28, TRAIL_STRIDE = 2;

    public record State(long clock, Card card, long cardStart, long cardExit, Toast toast, long toastStart,
                        int toastValue, int toastOwner, boolean hud, long hudChange, int hudFrom, long lockStart,
                        long pop0, long pop1, List<Integer> trail, long resultsStart, boolean celebrate, boolean tallyDone) {
        public State {
            Objects.requireNonNull(card); Objects.requireNonNull(toast);
            trail = List.copyOf(trail);
            if (trail.size() % 2 != 0 || trail.size() > TRAIL_POINTS * 2 || hudFrom < 0 || hudFrom > 1000)
                throw new IllegalArgumentException("Invalid golf feedback state");
        }
    }

    private long clock;
    private Card card = Card.NONE;
    private long cardStart, cardExit = -1;
    private Toast toast = Toast.NONE;
    private long toastStart;
    private int toastValue, toastOwner;
    private boolean hud;
    private long hudChange = -HUD_SLIDE;
    private int hudFrom;
    private long lockStart = -LOCK_FLASH, pop0 = -100, pop1 = -100, resultsStart = -1;
    private boolean celebrate, tallyDone;
    private final ArrayList<Integer> trail = new ArrayList<>();
    private final ArrayList<Cue> pending = new ArrayList<>();

    public State snapshot() {
        return new State(clock, card, cardStart, cardExit, toast, toastStart, toastValue, toastOwner, hud, hudChange,
                hudFrom, lockStart, pop0, pop1, trail, resultsStart, celebrate, tallyDone);
    }
    public void restore(State s) {
        clock = s.clock(); card = s.card(); cardStart = s.cardStart(); cardExit = s.cardExit(); toast = s.toast();
        toastStart = s.toastStart(); toastValue = s.toastValue(); toastOwner = s.toastOwner(); hud = s.hud();
        hudChange = s.hudChange(); hudFrom = s.hudFrom(); lockStart = s.lockStart(); pop0 = s.pop0(); pop1 = s.pop1();
        trail.clear(); trail.addAll(s.trail()); resultsStart = s.resultsStart(); celebrate = s.celebrate();
        tallyDone = s.tallyDone(); pending.clear();
    }
    public long clock() { return clock; }

    // ---- events -------------------------------------------------------------------------
    /** Practice and single-golfer turns introduce the hole with a title-card sweep. */
    public void holeIntro() { card = Card.HOLE; cardStart = clock; cardExit = -1; }
    /** The first press of a hole intro sends it away early; it never blocks input. */
    public void dismissIntro() { if (card == Card.HOLE && cardExit < 0) cardExit = Math.max(clock, cardStart + CARD_IN); }
    public void handoff() { card = Card.HANDOFF; cardStart = clock; cardExit = -1; pending.add(Cue.HANDOFF); }
    public void ready() {
        if (card == Card.HANDOFF && cardExit < 0) cardExit = clock;
        showToast(Toast.TEE_OFF, 0, 0); pending.add(Cue.READY);
    }
    public void committed() { lockStart = clock; if (card == Card.HOLE) dismissIntro(); }
    public void penalty(Toast kind) {
        if (!isPenalty(kind)) throw new IllegalArgumentException("Not a penalty toast");
        showToast(kind, 1, 0);
        // Damage already plays the native hurt sound; the other penalties need their own cue.
        if (kind != Toast.PENALTY_DAMAGE) pending.add(Cue.PENALTY);
    }
    /** matchDone: the whole hole/match ended; otherwise only this golfer finished. */
    public void finished(int owner, int total, boolean matchDone) {
        showToast(matchDone ? Toast.FINISH : Toast.GOLFER_DONE, total, owner); pending.add(Cue.FINISH);
    }
    public void refunded(int rewindsLeft) { showToast(Toast.REFUND, rewindsLeft, 0); }
    public void scored(int player) { if (player == 0) pop0 = clock; else pop1 = clock; }
    /** celebrate: a completed hole/match plays the Stage Clear jingle; concessions and errors do not. */
    public void results(boolean celebrate) {
        if (resultsStart >= 0) return;
        resultsStart = clock; tallyDone = false; this.celebrate = celebrate;
        if (card != Card.NONE && cardExit < 0) cardExit = clock;
    }
    public void trail(int x, int y) {
        if (clock % TRAIL_STRIDE != 0) return;
        trail.add(x); trail.add(y);
        while (trail.size() > TRAIL_POINTS * 2) { trail.removeFirst(); trail.removeFirst(); }
    }
    public void clearTrail() { trail.clear(); }

    private void showToast(Toast kind, int value, int owner) { toast = kind; toastStart = clock; toastValue = value; toastOwner = owner; }

    /**
     * One unpaused presentation row. hudWanted asks the control panel to be on screen;
     * tallyTarget is the largest scorecard total, used for the S2-style count-up.
     * Returns the sound cues raised since the previous tick, in order.
     */
    public List<Cue> tick(boolean running, boolean hudWanted, int tallyTarget) {
        if (running) {
            clock++;
            if (hudWanted != hud) { hudFrom = Math.round(hudShown() * 1000); hud = hudWanted; hudChange = clock; }
            if (card == Card.HOLE && cardExit < 0 && clock - cardStart >= CARD_IN + HOLE_HOLD) cardExit = clock;
            if (card != Card.NONE && cardExit >= 0 && clock - cardExit >= CARD_OUT) { card = Card.NONE; cardExit = -1; }
            if (toast != Toast.NONE && clock - toastStart >= (toast == Toast.TEE_OFF ? TEE_OFF_TICKS : TOAST_TICKS)) toast = Toast.NONE;
            if (celebrate && resultsStart >= 0 && clock - resultsStart == CLEAR_DELAY) pending.add(Cue.CLEAR);
            if (resultsStart >= 0 && !tallyDone) {
                int shown = tally(tallyTarget);
                if (shown >= tallyTarget && scorecardAge() >= CARD_DROP + TALLY_DELAY) { tallyDone = true; pending.add(Cue.TALLY_END); }
                // S2 results (s2.asm Obj3A tally): one blip every fourth frame while counting.
                else if (shown > 0 && clock % 4 == 0) pending.add(Cue.TALLY_TICK);
            }
        }
        var cues = List.copyOf(pending); pending.clear(); return cues;
    }

    // ---- derived presentation values ----------------------------------------------------
    /** 0 hidden .. 1 shown, eased. */
    public float hudShown() {
        float t = GolfMotion.easeOut(GolfMotion.progress(clock - hudChange, HUD_SLIDE));
        float from = hudFrom / 1000f, to = hud ? 1 : 0;
        return from + (to - from) * t;
    }
    public Card card() { return card; }
    /** Card visibility 0..1: enters with an overshoot, leaves with an ease-in. */
    public float cardIn() {
        if (card == Card.NONE) return 0;
        if (cardExit >= 0) return 1 - GolfMotion.easeIn(GolfMotion.progress(clock - cardExit, CARD_OUT));
        return GolfMotion.easeOutBack(GolfMotion.progress(clock - cardStart, CARD_IN));
    }
    public boolean cardLeaving() { return card != Card.NONE && cardExit >= 0; }
    public long cardAge() { return clock - cardStart; }
    public Toast toast() { return toast; }
    public long toastAge() { return clock - toastStart; }
    public int toastValue() { return toastValue; }
    public int toastOwner() { return toastOwner; }
    public float lockFlash() { return 1 - GolfMotion.progress(clock - lockStart, LOCK_FLASH); }
    public float pop(int player) { return 1 - GolfMotion.progress(clock - (player == 0 ? pop0 : pop1), 18); }
    public List<Integer> trailPoints() { return List.copyOf(trail); }
    public boolean resultsShown() { return resultsStart >= 0; }
    public long resultsAge() { return resultsStart < 0 ? 0 : clock - resultsStart; }
    /** Ticks since the scorecard began to drop; negative while a celebration plays first. */
    public long scorecardAge() { return resultsStart < 0 ? -1 : clock - resultsStart - (celebrate ? CARD_DELAY : 0); }
    /** Displayed scorecard count: 0 until the card has landed, then one per TALLY_STEP ticks up to the target. */
    public int tally(int target) {
        if (resultsStart < 0) return 0;
        return (int) Math.clamp((scorecardAge() - CARD_DROP - TALLY_DELAY) / TALLY_STEP, 0, Math.max(0, target));
    }
    public boolean tallyDone() { return tallyDone; }
    /** Small horizontal shake for penalties, decaying over 20 ticks. */
    public static boolean isPenalty(Toast kind) {
        return kind == Toast.PENALTY_DAMAGE || kind == Toast.PENALTY_LOST || kind == Toast.PENALTY_TIME || kind == Toast.PENALTY;
    }
    public int shake() {
        if (!isPenalty(toast)) return 0;
        long age = clock - toastStart;
        if (age >= 20) return 0;
        int amplitude = (int) Math.ceil(3 * (1 - age / 20f));
        return (age & 2) == 0 ? amplitude : -amplitude;
    }
}
