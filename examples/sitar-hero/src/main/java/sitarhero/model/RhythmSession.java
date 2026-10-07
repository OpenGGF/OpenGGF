package sitarhero.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Sample-clock rhythm rules. Inputs are chronological player events, never draw frames.
 * GH III's documented mechanics are the reference; the PoC's ±100 ms judgment window
 * and rock-meter weights are explicit tuning constants, not verified executable parity.
 */
public final class RhythmSession {
    private final Chart chart;
    private final boolean drums;
    private final long window;
    private final byte[] status;
    private final Map<Integer, Integer> phraseRemaining = new HashMap<>();
    private final Map<Integer, Boolean> phraseBroken = new HashMap<>();
    private int next;
    private int partial;
    private int held;
    private boolean whammy;
    private long position = -1;
    private long score;
    private int hits;
    private int misses;
    private int streak;
    private int bestStreak;
    private double rock = .5;
    private long starSamples;
    private boolean starActive;
    private boolean failed;
    private boolean audible = true;
    private int sustain = -1;
    private int sustainEarned;
    private int lastHit = -1;
    private boolean hopoStrumGrace;

    public RhythmSession(Chart chart, boolean drums, int sampleRate) {
        this.chart = java.util.Objects.requireNonNull(chart);
        this.drums = drums;
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate");
        window = Math.max(1, sampleRate / 10);
        position = -window; // the first gem retains its early window during lead-in
        status = new byte[chart.notes().size()];
        for (ChartNote note : chart.notes()) if (note.phrase() >= 0)
            phraseRemaining.merge(note.phrase(), 1, Integer::sum);
    }

    /** Advance to an audible position using the last input state. Backward readings are ignored. */
    public void advance(long sample) {
        if (sample < position || failed) return;
        // Keep the final gem's full late window even after the finite audio ends.
        long target = Math.min(sample, chart.length() + window + 1);
        // Boundary processing makes score/charge independent of update frequency.
        while (next < status.length) {
            ChartNote note = chart.notes().get(next);
            if (eligibleHopo(note) && matches(note.lanes(), held)
                    && target >= note.onset() - window) {
                advanceContinuous(Math.max(position, note.onset() - window));
                hit();
            } else if (target > note.onset() + window) {
                advanceContinuous(Math.max(position, note.onset() + window));
                miss();
                if (failed) return;
            } else break;
        }
        advanceContinuous(target);
    }

    private void advanceContinuous(long target) {
        if (target < position) return;
        long start = Math.max(0, Math.min(position, chart.length()));
        long finish = Math.max(0, Math.min(target, chart.length()));
        if (sustain >= 0) {
            ChartNote note = chart.notes().get(sustain);
            if (note.end() > note.onset()) {
                int ticks = (int) Math.min(note.sustainTicks(),
                        Math.max(0, finish - note.onset()) * note.sustainTicks() / (note.end() - note.onset()));
                for (int tick = sustainEarned + 1; tick <= ticks; tick++) {
                    long boundary = note.onset() + ((long) tick * (note.end() - note.onset())
                            + note.sustainTicks() - 1) / note.sustainTicks();
                    advanceCharge(start, Math.max(start, boundary), note);
                    start = Math.max(start, boundary);
                    score += multiplier();
                }
                sustainEarned = ticks;
            }
            advanceCharge(start, finish, note);
            if (finish >= note.end()) sustain = -1;
        } else advanceCharge(start, finish, null);
        position = target;
    }

    private void advanceCharge(long from, long to, ChartNote tail) {
        if (starActive) {
            long used = Math.min(Math.max(0, to - from), starSamples);
            starSamples -= used; from += used;
            if (starSamples == 0) starActive = false;
        }
        if (!starActive && whammy && tail != null && tail.phrase() >= 0) {
            long duration = Math.max(0, Math.min(to, tail.end()) - Math.max(from, tail.onset()));
            starSamples = Math.min(chart.samplesPerBeat() * 32, starSamples + duration);
        }
    }

    /** One mapped event; directPressed contains newly struck drum lanes, never held lanes. */
    public void input(long sample, int frets, int directPressed, boolean strum, boolean power, boolean bend) {
        if (failed || sample < position) return;
        advance(sample);
        if (failed || finished()) return;
        held = frets & 31;
        whammy = bend;
        if (sustain >= 0 && !matches(chart.notes().get(sustain).lanes(), held)) {
            sustain = -1;
            audible = false; // dropping a tail stops the part but preserves the streak
        }
        if (power && !starActive && starSamples >= chart.samplesPerBeat() * 16) starActive = true;
        if (drums) {
            for (int lane = 1; lane <= 16; lane <<= 1) if ((directPressed & lane) != 0) strikePad(lane);
        } else if (strum) {
            if (withinNext() && matches(chart.notes().get(next).lanes(), held)) hit();
            else if (hopoStrumGrace && lastHit >= 0
                    && position <= chart.notes().get(lastHit).onset() + window
                    && matches(chart.notes().get(lastHit).lanes(), held)) hopoStrumGrace = false;
            else overstrike();
        }
        // The fresh held state may start a valid HOPO within its window.
        if (!drums) advance(sample);
    }

    private boolean withinNext() {
        return next < status.length && Math.abs(position - chart.notes().get(next).onset()) <= window;
    }
    private void strikePad(int lane) {
        if (!withinNext()) { overstrike(); return; }
        int required = chart.notes().get(next).lanes();
        if ((required & lane) == 0 || (partial & lane) != 0) { overstrike(); return; }
        partial |= lane;
        if (partial == required) hit();
    }
    private boolean eligibleHopo(ChartNote note) {
        return !drums && note.hopo() && streak > 0 && lastHit == next - 1;
    }
    private boolean matches(int required, int pressed) {
        if (Integer.bitCount(required) > 1) return required == pressed;
        return Integer.highestOneBit(pressed) == required;
    }
    private void hit() {
        ChartNote note = chart.notes().get(next);
        status[next] = 1;
        hits++; streak++; bestStreak = Math.max(bestStreak, streak);
        score += 50L * Integer.bitCount(note.lanes()) * multiplier();
        rock = Math.min(1, rock + (starActive ? .04 : .02));
        audible = true;
        lastHit = next;
        hopoStrumGrace = note.hopo();
        sustain = note.end() > note.onset() && !drums ? next : -1;
        sustainEarned = 0;
        resolvePhrase(note, false);
        next++; partial = 0;
    }
    private void miss() {
        ChartNote note = chart.notes().get(next);
        status[next] = 2;
        misses++; breakChain(); resolvePhrase(note, true);
        next++; partial = 0;
    }
    private void overstrike() {
        // A phrase requires an uninterrupted run, even when every head is later hit.
        breakUnfinishedPhrase(lastHit);
        if (withinNext()) breakUnfinishedPhrase(next);
        breakChain();
    }
    private void breakUnfinishedPhrase(int index) {
        if (index < 0 || index >= status.length) return;
        int phrase = chart.notes().get(index).phrase();
        if (phrase >= 0 && phraseRemaining.getOrDefault(phrase, 0) > 0)
            phraseBroken.put(phrase, true);
    }
    private void breakChain() {
        streak = 0; hopoStrumGrace = false; audible = false; sustain = -1;
        rock = Math.max(0, rock - (starActive ? .02 : .04));
        if (rock == 0) failed = true;
    }
    private void resolvePhrase(ChartNote note, boolean broken) {
        int phrase = note.phrase();
        if (phrase < 0) return;
        if (broken) phraseBroken.put(phrase, true);
        int remaining = phraseRemaining.merge(phrase, -1, Integer::sum);
        if (remaining == 0 && !phraseBroken.getOrDefault(phrase, false) && !starActive)
            starSamples = Math.min(chart.samplesPerBeat() * 32, starSamples + chart.samplesPerBeat() * 8);
    }

    public Chart chart() { return chart; }
    public long position() { return position; }
    public long score() { return score; }
    public int hits() { return hits; }
    public int misses() { return misses; }
    public int streak() { return streak; }
    public int bestStreak() { return bestStreak; }
    public int multiplier() { return (1 + Math.min(3, streak / 10)) * (starActive ? 2 : 1); }
    public double rock() { return rock; }
    public double starCharge() { return starSamples / (32.0 * chart.samplesPerBeat()); }
    public boolean starActive() { return starActive; }
    public boolean failed() { return failed; }
    public boolean finished() { return failed || position >= chart.length() && next == status.length; }
    public boolean partAudible() { return audible; }
    public byte status(int index) { return status[index]; }
    public int nextNote() { return next; }
    public int held() { return held; }
}
