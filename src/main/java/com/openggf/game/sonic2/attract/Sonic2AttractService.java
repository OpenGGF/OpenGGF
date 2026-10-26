package com.openggf.game.sonic2.attract;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.data.Rom;
import com.openggf.game.CharacterKey;
import com.openggf.game.rewind.RewindSnapshottable;

import java.io.IOException;
import java.util.Objects;

/**
 * ROM-native title attract selection and controller playback, without a live world or mode shell.
 *
 * <p>The host owns these distinct native boundaries:
 * <ol>
 * <li>{@link #enterTitle()} at the title's Demo_Time_left assignment, then
 * {@link #onTitleVInt()} once for each Vint_Title. The 640 ticks include the title intro;
 * palette-fade, intro-text and SEGA V-ints are not title ticks. Navigation does not reset it.
 * TitleScreen_Loop tests expiry BEFORE the intro-complete/Start gates. At zero, call
 * {@link #requestNextLaunch(boolean)}; unavailable competition neither skips EHZ nor rotates.</li>
 * <li>After loading the exact plan, call {@link #beginPlayback()} at Level's demo-input setup,
 * before the title card leaves. Title-card/fade-in V-ints do not advance the 1,640-tick timer
 * or either script. Construct Sonic and Tails regardless of the options team. Competition
 * requires its actual two-player session; a campaign sidekick is not that session.</li>
 * <li>Service {@link #onLevelUpdates()} once at the canonical Do_Updates owner, including its
 * deferred H-int case, which can occur during the pending world iteration. A lag V-int
 * does not call it. Immediately before MoveSonicInDemo's
 * world iteration, call {@link #stepControllers(LogicalInputSnapshot)} with PHYSICAL input;
 * apply its controllers to the world, then call {@link #finishIteration(boolean)} after
 * the native final ObjectsManager. The last expired/aborted iteration still runs.</li>
 * <li>BEGIN_FADE means the native 60-tick level fade: continue controller playback and the
 * RunObjects/BuildSprites/ObjectsManager subset, without incrementing Level_frame_counter or
 * running ordinary water/rings/animation work. The host owns its palette writes on fade
 * iterations 1,4,...,58. Physical Start during this fade changes the return to title but
 * does not shorten it. For an early RunObjects-triggered Level_Inactive_flag reload, abandon
 * the pending iteration, reload the active plan, and call {@link #restartPlaybackForLevelReload()}
 * at the new Level demo-input setup. World reconstruction/history isolation remain host-owned.</li>
 * </ol>
 * <p>Retain the physical sample for this service's abort test and publish the returned scripted
 * controllers before ordinary gameplay pause handling. Native PauseGame sees the previous
 * controller sample before WaitForVint; this iteration's fresh physical Start must not instead
 * be consumed as a campaign pause. TITLE_SCREEN returns directly to title; SEGA_SCREEN restarts
 * the earlier boot presentation. The host must preserve that distinction.
 * <p>Register this stable service in the host rewind registry before recording attract history;
 * starting/replacing a live world still needs the host's normal history-isolation boundary.
 * The injected ROM is not closed here. A replacement ROM needs a new service; issued readers
 * retain immutable bytes. This controller alone does not activate or certify an attract product.
 *
 * <p>Owners: TitleScreen_Demo (s2.asm:4584-4627), Level (s2.asm:4757-5036),
 * Level_MainLoop and its fade (s2.asm:5088-5151), MoveDemo_On (s2.asm:5720-5825).
 */
public final class Sonic2AttractService implements RewindSnapshottable<Sonic2AttractService.Snapshot> {
    public enum SessionKind { CAMPAIGN, COMPETITION }
    public enum Phase { TITLE, AWAITING_LEVEL_SETUP, PLAYBACK, FADING, COMPLETE }
    public enum LaunchStatus { NOT_DUE, COMPETITION_UNAVAILABLE, READY }
    public enum Event { NONE, BEGIN_FADE, COMPLETE }
    public enum CompletionReason { NONE, TIME_EXPIRED, LEVEL_INACTIVE, PHYSICAL_START }
    public enum ReturnTarget { NONE, TITLE_SCREEN, SEGA_SCREEN }

    /** Native zone/act and script addresses, rather than an engine-index or campaign-team guess. */
    public record LaunchPlan(int demoNumber, int nativeZoneAndAct, SessionKind sessionKind,
                             int player1ScriptAddress, int player2ScriptAddress) {
        public int nativeZone() { return nativeZoneAndAct >>> 8; }
        public int actIndex() { return nativeZoneAndAct & 0xFF; }
        public boolean scriptedPlayer2() { return player2ScriptAddress >= 0; }
        public CharacterKey player1Character() { return CharacterKey.SONIC; }
        public CharacterKey player2Character() { return CharacterKey.TAILS; }
        /** TitleScreen_Demo resets lives/counters, but deliberately does not clear emerald RAM. */
        public boolean resetEmeraldProgress() { return false; }
    }

    public record LaunchRequest(LaunchStatus status, LaunchPlan plan) { }

    /** The 16-bit byte index and 8-bit down-counter from Demo_button_index/Demo_press_counter. */
    public record InputCursor(int byteIndex, int pressCounter) {
        public InputCursor {
            if (byteIndex < 0 || byteIndex > 0xFFFF || (byteIndex & 1) != 0
                    || pressCounter < 0 || pressCounter > 0xFF) {
                throw new IllegalArgumentException("Invalid native demo input cursor");
            }
        }
    }

    /** Apply these inputs to this iteration before handling its eventual finishIteration event. */
    public record ControllerFrame(Phase phase, LogicalInputSnapshot controllers,
                                  int player1HeldByte, int player2HeldByte, boolean physicalStartPressed) { }

    public record Transition(Event event, CompletionReason reason, ReturnTarget target) {
        public static final Transition NONE = new Transition(Event.NONE, CompletionReason.NONE, ReturnTarget.NONE);
    }

    public record Snapshot(String romFingerprint, Phase phase, int demoNumber, int activeDemoNumber,
                           int timeLeft, InputCursor player1, InputCursor player2,
                           CompletionReason reason, boolean startLatched, boolean iterationPending) { }

    private static final InputCursor EMPTY_CURSOR = new InputCursor(0, 0);
    private final Sonic2AttractData data;
    private Phase phase;
    private int demoNumber;
    private int activeDemoNumber = -1;
    private int timeLeft;
    private InputCursor player1 = EMPTY_CURSOR;
    private InputCursor player2 = EMPTY_CURSOR;
    private CompletionReason reason = CompletionReason.NONE;
    private boolean startLatched;
    private boolean iterationPending;

    public Sonic2AttractService(Rom rom) throws IOException {
        data = new Sonic2AttractData(rom);
        enterTitle();
    }

    public Phase phase() { return phase; }
    public int demoNumber() { return demoNumber; }
    public int timeLeft() { return timeLeft; }
    public int titleDuration() { return data.titleTicks; }
    public int playbackDuration() { return data.playbackTicks; }
    public int fadeDuration() { return data.fadeTicks; }
    public InputCursor player1Cursor() { return player1; }
    public InputCursor player2Cursor() { return player2; }
    public LaunchPlan activePlan() { return activeDemoNumber < 0 ? null : data.plans.get(activeDemoNumber); }
    public LaunchPlan nextLaunchPlan() { return data.plans.get(demoNumber & 7); }

    public void enterTitle() {
        phase = Phase.TITLE;
        activeDemoNumber = -1;
        timeLeft = data.titleTicks;
        player1 = player2 = EMPTY_CURSOR;
        reason = CompletionReason.NONE;
        startLatched = iterationPending = false;
    }

    /** Vint_Title (s2.asm:679-685), not a general title presentation/update callback. */
    public boolean onTitleVInt() {
        requirePhase(Phase.TITLE);
        decrementTimer();
        return timeLeft == 0;
    }

    public LaunchRequest requestNextLaunch(boolean competitionReady) {
        requirePhase(Phase.TITLE);
        LaunchPlan plan = nextLaunchPlan();
        if (timeLeft != 0) return new LaunchRequest(LaunchStatus.NOT_DUE, plan);
        if (plan.sessionKind() == SessionKind.COMPETITION && !competitionReady) {
            return new LaunchRequest(LaunchStatus.COMPETITION_UNAVAILABLE, plan);
        }
        activeDemoNumber = demoNumber;
        // TitleScreen_Demo increments the native word, then clears it at the four-plan bound.
        demoNumber = (demoNumber + 1) & 0xFFFF;
        if (demoNumber >= Sonic2AttractData.DEMO_COUNT) demoNumber = 0;
        phase = Phase.AWAITING_LEVEL_SETUP;
        return new LaunchRequest(LaunchStatus.READY, plan);
    }

    /** Exact Level demo setup; call once, after the initial object pass and before title-card exit. */
    public void beginPlayback() {
        requirePhase(Phase.AWAITING_LEVEL_SETUP);
        startLatched = false;
        initializePlaybackInputs();
    }

    /**
     * The earlier Level_MainLoop branch reloads the same zone without TitleScreen_Demo's rotation.
     * Call at the reloaded Level's input setup, not at the old world's post-ObjectsManager test.
     * A pending physical-Start change to Game_Mode survives Level's title-card bit operations.
     */
    public void restartPlaybackForLevelReload() {
        requirePhase(Phase.PLAYBACK);
        initializePlaybackInputs();
    }

    private void initializePlaybackInputs() {
        LaunchPlan plan = activePlan();
        player1 = new InputCursor(0, data.rom.readU8(plan.player1ScriptAddress() + 1));
        player2 = plan.scriptedPlayer2()
                ? new InputCursor(0, data.rom.readU8(plan.player2ScriptAddress() + 1)) : EMPTY_CURSOR;
        timeLeft = data.playbackTicks;
        reason = CompletionReason.NONE;
        iterationPending = false;
        phase = Phase.PLAYBACK;
    }

    /**
     * Do_Updates (s2.asm:805-814), which may be deferred to H-int during the world iteration.
     * Service it once at that owner, before finishIteration; step itself never ticks this clock.
     */
    public void onLevelUpdates() {
        requirePlaybackPhase();
        decrementTimer();
    }

    /** One MoveSonicInDemo dispatch; physical directions/actions cannot alter the ROM sample. */
    public ControllerFrame stepControllers(LogicalInputSnapshot physical) {
        Objects.requireNonNull(physical, "physicalInput");
        requirePlaybackPhase();
        if (iterationPending) throw new IllegalStateException("Finish the preceding world iteration first");
        boolean physicalStart = physical.player1().startPressed() || physical.player2().startPressed();
        startLatched |= physicalStart;
        LaunchPlan plan = activePlan();
        int held1 = held(plan.player1ScriptAddress(), player1);
        player1 = advance(plan.player1ScriptAddress(), player1);
        int held2 = 0;
        if (plan.scriptedPlayer2()) {
            held2 = held(plan.player2ScriptAddress(), player2);
            player2 = advance(plan.player2ScriptAddress(), player2);
        }
        iterationPending = true;
        return new ControllerFrame(phase, LogicalInputSnapshot.ofPlayers(logicalPad(held1), logicalPad(held2)),
                held1, held2, physicalStart);
    }

    /**
     * Native post-world mode/timer/Level_Inactive_flag test. The flag here is the observation
     * AFTER the final ObjectsManager; the earlier RunObjects-triggered reload is host-owned.
     */
    public Transition finishIteration(boolean inactiveAfterObjectsManager) {
        requirePlaybackPhase();
        if (!iterationPending) throw new IllegalStateException("No controller/world iteration to finish");
        iterationPending = false;
        if (phase == Phase.PLAYBACK) {
            if (startLatched) {
                phase = Phase.COMPLETE;
                reason = CompletionReason.PHYSICAL_START;
                return new Transition(Event.COMPLETE, reason, ReturnTarget.TITLE_SCREEN);
            }
            if (inactiveAfterObjectsManager || timeLeft == 0) {
                reason = inactiveAfterObjectsManager ? CompletionReason.LEVEL_INACTIVE : CompletionReason.TIME_EXPIRED;
                timeLeft = data.fadeTicks;
                phase = Phase.FADING;
                return new Transition(Event.BEGIN_FADE, reason, ReturnTarget.SEGA_SCREEN);
            }
        } else if (timeLeft == 0) {
            phase = Phase.COMPLETE;
            if (startLatched) reason = CompletionReason.PHYSICAL_START;
            return new Transition(Event.COMPLETE, reason,
                    startLatched ? ReturnTarget.TITLE_SCREEN : ReturnTarget.SEGA_SCREEN);
        }
        return Transition.NONE;
    }

    private int held(int address, InputCursor cursor) {
        // ADDA.W sign-extends the word, even though Demo_button_index is captured as unsigned RAM.
        return data.rom.readU8(address + (short) cursor.byteIndex());
    }

    private InputCursor advance(int address, InputCursor cursor) {
        if (cursor.pressCounter() != 0) {
            return new InputCursor(cursor.byteIndex(), cursor.pressCounter() - 1);
        }
        // SUBQ.B borrows only from zero. The ROM then preloads the NEXT packet's counter.
        // There is no packet terminator: script Start bytes are data, not a completion signal.
        return new InputCursor((cursor.byteIndex() + 2) & 0xFFFF,
                data.rom.readU8(address + (short) cursor.byteIndex() + 3));
    }

    private static PlayerInputState logicalPad(int held) {
        int actions = ((held & 0x40) != 0 ? InputActionMasks.ACTION_A : 0)
                | ((held & 0x10) != 0 ? InputActionMasks.ACTION_B : 0)
                | ((held & 0x20) != 0 ? InputActionMasks.ACTION_C : 0);
        int directions = held & 0x0F;
        // fixBugs=0: MOVEQ #0,D2 makes Ctrl_Press identical to Ctrl_Held on EVERY call.
        // The fixed branch XORs the previous logical input, producing rising edges instead.
        return PlayerInputState.of(directions, directions, actions, actions,
                (held & 0x80) != 0, (held & 0x80) != 0);
    }

    private void decrementTimer() {
        if (timeLeft != 0) timeLeft = (timeLeft - 1) & 0xFFFF;
    }

    private void requirePhase(Phase required) {
        if (phase != required) throw new IllegalStateException("Expected " + required + ", got " + phase);
    }

    private void requirePlaybackPhase() {
        if (phase != Phase.PLAYBACK && phase != Phase.FADING) {
            throw new IllegalStateException("No active demo controller: " + phase);
        }
    }

    @Override public String key() { return "sonic2-attract"; }

    @Override
    public Snapshot capture() {
        return new Snapshot(data.fingerprint, phase, demoNumber, activeDemoNumber, timeLeft,
                player1, player2, reason, startLatched, iterationPending);
    }

    @Override
    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!data.fingerprint.equals(snapshot.romFingerprint())) {
            throw new IllegalArgumentException("Attract snapshot belongs to another ROM");
        }
        Objects.requireNonNull(snapshot.phase(), "phase");
        Objects.requireNonNull(snapshot.reason(), "reason");
        Objects.requireNonNull(snapshot.player1(), "player1Cursor");
        Objects.requireNonNull(snapshot.player2(), "player2Cursor");
        boolean title = snapshot.phase() == Phase.TITLE;
        int maximumTime = switch (snapshot.phase()) {
            case TITLE -> data.titleTicks;
            case AWAITING_LEVEL_SETUP -> 0;
            case PLAYBACK -> data.playbackTicks;
            case FADING -> data.fadeTicks;
            case COMPLETE -> Math.max(data.playbackTicks, data.fadeTicks);
        };
        if (snapshot.demoNumber() < 0 || snapshot.demoNumber() >= Sonic2AttractData.DEMO_COUNT
                || (title ? snapshot.activeDemoNumber() != -1
                : snapshot.activeDemoNumber() < 0 || snapshot.activeDemoNumber() >= Sonic2AttractData.DEMO_COUNT)
                || snapshot.timeLeft() < 0 || snapshot.timeLeft() > maximumTime
                || snapshot.iterationPending() && snapshot.phase() != Phase.PLAYBACK && snapshot.phase() != Phase.FADING) {
            throw new IllegalArgumentException("Invalid attract snapshot state");
        }
        if (!title && snapshot.demoNumber() != (snapshot.activeDemoNumber() + 1) % Sonic2AttractData.DEMO_COUNT
                || !title && !data.plans.get(snapshot.activeDemoNumber()).scriptedPlayer2()
                && !snapshot.player2().equals(EMPTY_CURSOR)) {
            throw new IllegalArgumentException("Attract snapshot does not match its launch plan");
        }
        phase = snapshot.phase();
        demoNumber = snapshot.demoNumber();
        activeDemoNumber = snapshot.activeDemoNumber();
        timeLeft = snapshot.timeLeft();
        player1 = snapshot.player1();
        player2 = snapshot.player2();
        reason = snapshot.reason();
        startLatched = snapshot.startLatched();
        iterationPending = snapshot.iterationPending();
    }

    @Override
    public void resetForMissingSnapshot() {
        demoNumber = 0;
        enterTitle();
    }
}
