package com.openggf.game.sonic3k;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Transient state for the Hidden Palace Super Emerald sanctuary.
 *
 * <p>Durable emerald values remain owned by {@link S3kEmeraldProgression};
 * this value owns only the ROM controller's phase, timers and re-entry context.
 */
public final class S3kSanctuaryRuntimeState {
    public static final List<Integer> ROM_SCAN_ORDER = List.of(5, 3, 1, 0, 2, 4, 6);

    public enum Phase {
        INTRO_WAIT,
        WAITING_FOR_INTRO_SIGNAL,
        CONVERTING,
        READY,
        RETURN_TRANSFORM,
        SELECTING,
        LEAVING
    }

    public record Snapshot(
            Phase phase,
            int introTimer,
            List<Integer> conversionOrder,
            int conversionCursor,
            int selectedStage,
            int selectionTimer,
            boolean reentry,
            int returnStage,
            boolean returnSucceeded,
            boolean pedestalTransformActive,
            boolean pedestalRevealPending,
            int revealPendingSubtype,
            int emeraldFlicker,
            int originZone,
            int originAct) {
        public Snapshot {
            conversionOrder = List.copyOf(conversionOrder);
        }
    }

    private final S3kEmeraldProgression progression;
    private Phase phase;
    private int introTimer;
    private List<Integer> conversionOrder = List.of();
    private int conversionCursor;
    private int selectedStage = -1;
    private int selectionTimer;
    private boolean reentry;
    private int returnStage = -1;
    private boolean returnSucceeded;
    private boolean pedestalTransformActive;
    private boolean pedestalRevealPending;
    private int revealPendingSubtype = -1;
    private int emeraldFlicker;
    private int originZone = -1;
    private int originAct = -1;

    public S3kSanctuaryRuntimeState(S3kEmeraldProgression progression, boolean reentry) {
        this(progression, reentry, -1, false);
    }

    public S3kSanctuaryRuntimeState(
            S3kEmeraldProgression progression, boolean reentry,
            int returnStage, boolean returnSucceeded) {
        this.progression = Objects.requireNonNull(progression, "progression");
        this.reentry = reentry;
        this.returnStage = returnStage;
        this.returnSucceeded = returnSucceeded;
        this.pedestalTransformActive = returnSucceeded;
        this.phase = returnSucceeded ? Phase.RETURN_TRANSFORM
                : reentry ? Phase.READY : Phase.INTRO_WAIT;
        this.introTimer = 0x1F;
    }

    public Phase phase() {
        return phase;
    }

    public boolean updateIntro() {
        if (phase != Phase.INTRO_WAIT) {
            return false;
        }
        introTimer--;
        if (introTimer >= 0) {
            return false;
        }
        phase = Phase.WAITING_FOR_INTRO_SIGNAL;
        return true;
    }

    public boolean signalIntroComplete() {
        if (phase != Phase.WAITING_FOR_INTRO_SIGNAL) {
            return false;
        }
        ArrayList<Integer> order = new ArrayList<>();
        for (int index : ROM_SCAN_ORDER) {
            if (progression.state(index) == S3kEmeraldProgression.EmeraldState.CHAOS) {
                order.add(index);
            }
        }
        conversionOrder = List.copyOf(order);
        conversionCursor = 0;
        if (!conversionOrder.isEmpty()) {
            // ROM loc_90AF2 sets Emeralds_converted_flag before loc_90B22 can
            // perform the first individual state-1 -> state-2 mutation.
            progression.beginSanctuaryConversion();
            phase = Phase.CONVERTING;
        } else {
            phase = Phase.READY;
        }
        return true;
    }

    public void skipIntroForTest() {
        phase = Phase.WAITING_FOR_INTRO_SIGNAL;
        introTimer = -1;
    }

    public List<Integer> conversionOrder() {
        return conversionOrder;
    }

    public int convertNext() {
        if (phase != Phase.CONVERTING || conversionCursor >= conversionOrder.size()) {
            return -1;
        }
        int index = conversionOrder.get(conversionCursor++);
        progression.convert(index);
        if (conversionCursor >= conversionOrder.size()) {
            phase = Phase.READY;
        }
        return index;
    }

    public boolean exitEligible() {
        return phase != Phase.RETURN_TRANSFORM
                && progression.states().stream().noneMatch(value -> value == 1 || value == 2);
    }

    public boolean beginPedestalSelection(int stageIndex) {
        if (phase != Phase.READY
                || progression.state(stageIndex) != S3kEmeraldProgression.EmeraldState.GRAY_SUPER) {
            return false;
        }
        selectedStage = stageIndex;
        selectionTimer = 0xF;
        phase = Phase.SELECTING;
        return true;
    }

    /**
     * ROM loc_90926: {@code subq.w #1,$2E / bpl}. Starting at $F therefore
     * publishes on update 16, not update 15.
     */
    public boolean updatePedestalSelection() {
        if (phase != Phase.SELECTING) {
            return false;
        }
        selectionTimer--;
        if (selectionTimer >= 0) {
            return false;
        }
        phase = Phase.LEAVING;
        return true;
    }

    public void markSpecialStageResult(int stageIndex, boolean success) {
        selectedStage = stageIndex;
        returnStage = stageIndex;
        returnSucceeded = success;
        pedestalTransformActive = success;
        selectionTimer = 0;
        reentry = true;
        phase = success ? Phase.RETURN_TRANSFORM : Phase.READY;
    }

    public int returnStage() {
        return returnStage;
    }

    public boolean returnSucceeded() {
        return returnSucceeded;
    }

    public boolean transformationActive() {
        return phase == Phase.RETURN_TRANSFORM;
    }

    public boolean forceGrayPedestal(int subtype) {
        return pedestalTransformActive && returnStage == subtype;
    }

    public void updateEmeraldFlicker() {
        emeraldFlicker = (emeraldFlicker + 1) % 3;
    }

    public int emeraldFlicker() {
        return emeraldFlicker;
    }

    public void completeReturnTransformation() {
        if (phase == Phase.RETURN_TRANSFORM) {
            phase = Phase.READY;
        }
    }

    public void completePedestalTransformation() {
        pedestalTransformActive = false;
    }

    public void beginPedestalReveal(int subtype) {
        pedestalRevealPending = true;
        revealPendingSubtype = subtype;
    }

    public boolean revealPending(int subtype) {
        return pedestalRevealPending && revealPendingSubtype == subtype;
    }

    public void completePedestalReveal(int subtype) {
        if (revealPending(subtype)) {
            pedestalRevealPending = false;
            revealPendingSubtype = -1;
        }
    }

    public int selectedStage() {
        return selectedStage;
    }

    public int selectionTimer() {
        return selectionTimer;
    }

    public boolean isReentry() {
        return reentry;
    }

    public void setOrigin(int zone, int act) {
        originZone = zone;
        originAct = act;
    }

    public int originZone() {
        return originZone;
    }

    public int originAct() {
        return originAct;
    }

    public Snapshot capture() {
        return new Snapshot(phase, introTimer, conversionOrder, conversionCursor,
                selectedStage, selectionTimer, reentry, returnStage, returnSucceeded,
                pedestalTransformActive, pedestalRevealPending,
                revealPendingSubtype, emeraldFlicker,
                originZone, originAct);
    }

    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        phase = snapshot.phase();
        introTimer = snapshot.introTimer();
        conversionOrder = snapshot.conversionOrder();
        conversionCursor = snapshot.conversionCursor();
        selectedStage = snapshot.selectedStage();
        selectionTimer = snapshot.selectionTimer();
        reentry = snapshot.reentry();
        returnStage = snapshot.returnStage();
        returnSucceeded = snapshot.returnSucceeded();
        pedestalTransformActive = snapshot.pedestalTransformActive();
        pedestalRevealPending = snapshot.pedestalRevealPending();
        revealPendingSubtype = snapshot.revealPendingSubtype();
        emeraldFlicker = snapshot.emeraldFlicker();
        originZone = snapshot.originZone();
        originAct = snapshot.originAct();
    }
}
