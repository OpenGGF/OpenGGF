package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.game.sonic2.competition.Sonic2CompetitiveSpecialStageAdapter.Events;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Obj5A's competitive rainbow, tied-message, hand and final handoff phases. */
public final class CompetitiveHalfpipeCheckpoint {
    public enum Phase { IDLE, WAITING, RAINBOW, TIE_MESSAGE, WINNER_HAND, FINAL_FADE, COMPLETE }
    public record Tick(boolean archived, boolean fadeMusic) { }
    public record State(Phase phase, int sector, int rainbowStep, int timer,
                        int phraseId, int winner, int handY, boolean handMovingDown,
                        List<CompetitiveHalfpipeRenderer.Rainbow> rainbow, int p1Wins, int p2Wins) {
        public State {
            Objects.requireNonNull(phase, "phase");
            rainbow = List.copyOf(rainbow);
        }
    }

    private static final Tick NONE = new Tick(false, false);
    private final byte[] frames;
    private final byte[] positions;
    private Phase phase = Phase.IDLE;
    private int sector;
    private int rainbowStep;
    private int timer;
    private int phraseId = -1;
    private int winner;
    private int p1Wins, p2Wins;
    private int handY = 0x1C;
    private boolean handMovingDown;
    private List<CompetitiveHalfpipeRenderer.Rainbow> rainbow = List.of();

    public CompetitiveHalfpipeCheckpoint(CompetitiveHalfpipeRomData data) {
        Objects.requireNonNull(data, "data");
        frames = data.rom().slice(0x357F4, 10);
        positions = data.rom().slice(0x35846, 9 * 7 * 2);
        if (frames[9] >= 0 || (positions[8 * 14 + 12] & 0xFF) != 0xE8) {
            throw new IllegalArgumentException("Invalid native competitive rainbow");
        }
    }

    public void request() {
        if (phase != Phase.IDLE || sector >= 3) {
            throw new IllegalStateException("A competitive checkpoint is already pending or complete");
        }
        phase = Phase.WAITING;
        rainbowStep = 0;
        rainbow = List.of();
        timer = 0;
        winner = 0;
    }

    /** Called after this RunObjects pass has applied both humans' item touches. */
    public Tick tick(int previousFrame, boolean drawing4, int p1Rings, int p2Rings, Events events) {
        Objects.requireNonNull(events, "events");
        if (phase == Phase.WAITING) {
            // Obj5A_Init waits for Straight4 <= last mappings < Drop1.
            if (previousFrame != 0x14) return NONE;
            phase = Phase.RAINBOW;
        }
        if (phase == Phase.RAINBOW) {
            if (!drawing4) return NONE;
            int mapping = frames[rainbowStep];
            if (mapping >= 0) {
                List<CompetitiveHalfpipeRenderer.Rainbow> next = new ArrayList<>(7);
                // Obj5A_Init allocates base six first, through base zero last.
                for (int base = 6; base >= 0; base--) {
                    int index = rainbowStep * 14 + base * 2;
                    next.add(new CompetitiveHalfpipeRenderer.Rainbow(
                            positions[index] & 0xFF, positions[index + 1] & 0xFF, mapping));
                }
                rainbow = List.copyOf(next);
                rainbowStep++;
                return NONE;
            }
            rainbow = List.of();
            int comparison = Short.compare((short) p1Rings, (short) p2Rings);
            int resolvedSector = sector++;
            timer = 0x46;
            if (comparison == 0) {
                phraseId = 0x10;
                phase = Phase.TIE_MESSAGE;
                // Retail's nonfinal tie bypasses VSReset. The ring banks carry
                // into the next sector and its archive remains zero.
                if (sector < 3) {
                    events.tiedSector(resolvedSector);
                    return NONE;
                }
                events.archivedSector(resolvedSector, p1Rings, p2Rings);
                return new Tick(true, false);
            }
            winner = comparison > 0 ? 1 : 2;
            // Live SS_2P_BCD_Score owns HUD icons at comparison time. Results
            // later compare archived words, which may include more touches.
            if (winner == 1) p1Wins++; else p2Wins++;
            phraseId = winner == 1 ? 0x0C : 0x14;
            phase = Phase.WINNER_HAND;
            handY = 0x1C;
            handMovingDown = false;
            return NONE;
        }
        if (phase == Phase.WINNER_HAND) {
            moveHand();
            if (--timer != 0) return NONE;
            // Obj5A_Handshake calls VSReset at expiry, not at comparison. Ring
            // touches during the hand are therefore included in the archive.
            events.archivedSector(sector - 1, p1Rings, p2Rings);
            if (sector == 3) {
                phase = Phase.FINAL_FADE;
                timer = 0x30;
                return new Tick(true, true);
            }
            phase = Phase.IDLE;
            phraseId = 0x0A;
            return new Tick(true, false);
        }
        if (phase == Phase.TIE_MESSAGE && --timer == 0) {
            if (sector == 3) finish(events);
            else {
                phraseId = 0x0A;
                phase = Phase.IDLE;
            }
        } else if (phase == Phase.FINAL_FADE && --timer == 0) {
            finish(events);
        }
        return NONE;
    }

    private void moveHand() {
        // Obj5A_Handshake starts upwards. The native clamp repeats the endpoint
        // row before reversing: Y25,25 ... Y31,31 around target Y28.
        if (!handMovingDown) {
            if (--handY <= 0x18) {
                handY = 0x19;
                handMovingDown = true;
            }
        } else if (++handY >= 0x20) {
            handY = 0x1F;
            handMovingDown = false;
        }
    }

    private void finish(Events events) {
        phase = Phase.COMPLETE;
        rainbow = List.of();
        events.stageComplete();
    }

    public boolean complete() { return phase == Phase.COMPLETE; }
    /** Number of comparisons already performed; archives may follow later. */
    public int sector() { return sector; }
    public int wins(int slot) {
        if (slot < 0 || slot > 1) throw new IllegalArgumentException("slot " + slot);
        return slot == 0 ? p1Wins : p2Wins;
    }
    public CompetitiveHalfpipeRenderer.Overlay overlay() {
        return new CompetitiveHalfpipeRenderer.Overlay(false, 0, phraseId,
                phase == Phase.WINNER_HAND, handY, winner, rainbow, List.of());
    }
    public State capture() {
        return new State(phase, sector, rainbowStep, timer, phraseId, winner, handY, handMovingDown, rainbow, p1Wins, p2Wins);
    }
    public void restore(State state) {
        Objects.requireNonNull(state, "state");
        phase = state.phase();
        sector = state.sector();
        rainbowStep = state.rainbowStep();
        timer = state.timer();
        phraseId = state.phraseId();
        winner = state.winner();
        handY = state.handY();
        handMovingDown = state.handMovingDown();
        rainbow = List.copyOf(state.rainbow());
        p1Wins = state.p1Wins(); p2Wins = state.p2Wins();
    }
}
