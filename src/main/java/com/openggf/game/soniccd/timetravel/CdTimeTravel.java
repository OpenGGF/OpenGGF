package com.openggf.game.soniccd.timetravel;

import com.openggf.game.GameModule;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdZone;

import java.util.ArrayList;
import java.util.List;

/**
 * Sonic CD's campaign time state: the one owner of the period, the pending warp, the
 * per-act generator and projector results, good-future progress and the Time Stone input.
 * Every value has the writers and lifetime the disc gives it; other owners read through
 * the accessors and keep no copy. Scddisasm pin {@code 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff},
 * USA release, {@code FixBugs = 0}.
 *
 * <table>
 *   <caption>Values, writers and lifetimes</caption>
 *   <tr><th>Value (native)</th><th>Writers</th><th>Lifetime</th></tr>
 *   <tr><td>{@link #timeZone()}, {@link #warping()} ({@code timeZone}, bit 7)</td>
 *       <td>stage entry (StageSelect (IPX.asm:713-717); results, Results/Main.asm:293-300);
 *       the warp trigger ({@code .GotNewTime}, Sonic/Main.asm:638-640)</td>
 *       <td>campaign; bit 7 cleared on the arrival ({@code bclr #7,timeZone}, Level/Main.asm:314-315)</td></tr>
 *   <tr><td>{@link #warpDirection()} ({@code timeWarpDir})</td>
 *       <td>time post touch (Monitor and Time Post/Main.asm:162-167); stop
 *       ({@code .StopTimeWarp}, Sonic/Main.asm:660-663)</td>
 *       <td>level: inside the camera RAM LevelStart clears (Level/Main.asm:209-216)</td></tr>
 *   <tr><td>{@link #warpTimer()} ({@code timeWarpTimer})</td>
 *       <td>{@code RunTimeWarp} in V-INT (Level/Main.asm:971-978); the player routine
 *       (Sonic/Main.asm:597-600, 652-662)</td><td>level, as above</td></tr>
 *   <tr><td>{@link #starsActive()} ({@code timeWarp})</td>
 *       <td>{@code ObjSonic_MakeTimeWarpStars} (Sonic/Main.asm:112-116); stop and reset</td>
 *       <td>level: cleared at level start ({@code move.b d0,timeWarp}, Level/Main.asm:297)</td></tr>
 *   <tr><td>{@link #pendingWarp()} ({@code spawnMode} 2 and the {@code warp*} save)</td>
 *       <td>the trigger at 210 (Sonic/Main.asm:642-643)</td><td>until the next LevelStart</td></tr>
 *   <tr><td>{@link #warpReady()} ({@code levelRestart} on the warp)</td>
 *       <td>the trigger at 230 (Sonic/Main.asm:604-605)</td><td>until the next LevelStart</td></tr>
 *   <tr><td>{@link #goodFuture()}</td>
 *       <td>robot generator (Robot Generator/Main.asm:102); all Time Stones at LevelStart
 *       (Level/Main.asm:22-26); stage select (IPX.asm:717); results (Results/Main.asm:327-341);
 *       game over (IPX.asm:337); zone end (IPX.asm:185)</td>
 *       <td>campaign: survives death and warps; credited at the act change</td></tr>
 *   <tr><td>{@link #goodFutureFlags()} (acts 1 and 2)</td>
 *       <td>results (Results/Main.asm:316-341); first LevelStart of a run (Level/Main.asm:47)</td>
 *       <td>one zone</td></tr>
 *   <tr><td>{@link #projectorDestroyed()} ({@code projDestroyed})</td>
 *       <td>projector ({@code st projDestroyed}, Palmtree Panic/Objects/Projector/Main.asm:218);
 *       results (Results/Main.asm:285); stage select (IPX.asm:720); game over (IPX.asm:336)</td>
 *       <td>one act: survives death and warps</td></tr>
 *   <tr><td>{@link #timeStones()}</td><td>input from the special-stage owner</td><td>save file</td></tr>
 * </table>
 */
public final class CdTimeTravel implements RewindSnapshottable<CdTimeTravel.Snapshot> {
    /** {@code cmpi.b #$7F,timeStones} (Level/Main.asm:22). */
    public static final int ALL_TIME_STONES = 0x7F;

    /** How a stage is being entered; the program-flow owner names it before the load. */
    public enum Entry {
        /** IPX StageSelect (IPX.asm:713-720): period and good future from the {@code StageSels} entry. */
        STAGE_SELECT,
        /** The results object moved to the next act (Results/Main.asm:277-341). */
        NEXT_ACT,
        /** {@code RunLevel} loaded the warp destination (IPX.asm:551-580). */
        WARP_ARRIVAL,
        /** The same stage again after a death or a restart. */
        RESTART
    }

    /** Typed audio requests by native command, for the audio owner to drain. */
    public enum Sound {
        /** {@code SCMD_PASTSFX} voice clip (Monitor and Time Post/Main.asm:162,170). */
        PAST_VOICE,
        /** {@code SCMD_FUTURESFX} voice clip (Monitor and Time Post/Main.asm:166-170). */
        FUTURE_VOICE,
        /** {@code SCMD_FADEPCM} when leaving the past (Sonic/Main.asm:619-620). */
        FADE_PCM,
        /** {@code FadeOutMusic} at the restart (Sonic/Main.asm:604-605). */
        FADE_OUT_MUSIC
    }

    /** The rewind image of every value. */
    public record Snapshot(CdTimeZone timeZone, boolean warping, int warpDirection, int warpTimer,
                           boolean starsActive, CdWarpRequest pendingWarp, boolean warpReady,
                           boolean goodFuture, int goodFutureFlags, int goodFutures, boolean projectorDestroyed,
                           int timeStones, CdStagePayload loadedPayload, Entry nextEntry) { }

    private CdTimeZone timeZone = CdTimeZone.PRESENT;
    private boolean warping;
    private int warpDirection;
    private int warpTimer;
    private boolean starsActive;
    private CdWarpRequest pendingWarp;
    private boolean warpReady;
    private boolean goodFuture;
    private int goodFutureFlags;
    private int goodFutures;
    private boolean projectorDestroyed;
    private int timeStones;
    private CdStagePayload loadedPayload;
    private Entry nextEntry;
    private final List<Sound> sounds = new ArrayList<>();

    /** The module's owner, or null when the module is not Sonic CD's. */
    public static CdTimeTravel of(GameModule module) {
        return module == null ? null : module.getGameService(CdTimeTravel.class);
    }

    // ---- Reads -------------------------------------------------------------------

    public CdTimeZone timeZone() { return timeZone; }
    public boolean warping() { return warping; }
    public int warpDirection() { return warpDirection; }
    public int warpTimer() { return warpTimer; }
    public boolean starsActive() { return starsActive; }
    public CdWarpRequest pendingWarp() { return pendingWarp; }
    public boolean warpReady() { return warpReady; }
    public boolean goodFuture() { return goodFuture; }
    public int goodFutureFlags() { return goodFutureFlags; }
    /** {@code goodFutures}: one bit per zone whose act 3 ran in the good future (IPX.asm:183-297). */
    public int goodFutures() { return goodFutures; }
    public boolean projectorDestroyed() { return projectorDestroyed; }
    public int timeStones() { return timeStones; }
    public CdStagePayload loadedPayload() { return loadedPayload; }

    /** The ready warp for the program-flow owner, or null: leave the stage and run the cutscene. */
    public CdWarpRequest readyWarp() {
        return warpReady ? pendingWarp : null;
    }

    /** {@code RunBossLevel}: act 3 runs the good future file when {@code goodFuture} is set (IPX.asm:611-618). */
    public CdStagePayload act3Payload(CdZone zone) {
        return CdStagePayload.valueOf("R" + zone.nativeRound() + "3" + (goodFuture
                ? CdPeriod.GOOD_FUTURE : CdPeriod.BAD_FUTURE).payloadSuffix());
    }

    /** Drains the audio requests made since the last call. */
    public List<Sound> drainSounds() {
        List<Sound> out = List.copyOf(sounds);
        sounds.clear();
        return out;
    }

    // ---- Stage entry ---------------------------------------------------------------

    /** The program-flow owner says how the next stage is entered; otherwise it is inferred. */
    public void prepareEntry(Entry entry) {
        nextEntry = entry;
    }

    /**
     * LevelStart for {@code payload}. Returns the carry to restore when this load is the
     * warp destination, else null. Without a prepared entry the same stage is a restart,
     * the pending warp's destination is its arrival, and anything else a stage select.
     */
    public CdWarpCarry levelStart(CdStagePayload payload) {
        Entry entry = nextEntry != null ? nextEntry : inferEntry(payload);
        CdWarpCarry arrival = null;
        switch (entry) {
            case STAGE_SELECT -> {
                // StageSelect (IPX.asm:713-720): zoneAct, timeZone, goodFuture; projDestroyed cleared.
                timeZone = CdTimeZone.of(payload.stage().period());
                goodFuture = payload.stage().period() == CdPeriod.GOOD_FUTURE;
                projectorDestroyed = false;
                // First LevelStart of the run: plcLoadFlags clear, so goodFutureFlags is cleared (Level/Main.asm:39-47).
                goodFutureFlags = 0;
            }
            case NEXT_ACT -> timeZone = CdTimeZone.of(payload.stage().period());
            case WARP_ARRIVAL -> {
                if (pendingWarp != null) arrival = pendingWarp.carry();
                timeZone = CdTimeZone.of(payload.stage().period());
            }
            case RESTART -> { }
        }
        // cmpi.b #$7F,timeStones; tst.b timeAttackMode; move.b #1,goodFuture (Level/Main.asm:22-26).
        // Time Attack is not hosted, so its branch reads clear.
        if (timeStones == ALL_TIME_STONES) goodFuture = true;
        // .FromWhite: bclr #7,timeZone (Level/Main.asm:314-315).
        warping = false;
        // .ClearCamera covers timeWarpDir and timeWarpTimer (Level/Main.asm:209-216; _Definitions.inc:301-303).
        warpDirection = 0;
        warpTimer = 0;
        // move.b d0,timeWarp; move.b d0,spawnMode; move.w d0,levelRestart (Level/Main.asm:297-301).
        starsActive = false;
        pendingWarp = null;
        warpReady = false;
        loadedPayload = payload;
        nextEntry = null;
        return arrival;
    }

    private Entry inferEntry(CdStagePayload payload) {
        if (warpReady && pendingWarp != null && payload == pendingWarp.destination(goodFuture)) {
            return Entry.WARP_ARRIVAL;
        }
        return payload == loadedPayload ? Entry.RESTART : Entry.STAGE_SELECT;
    }

    // ---- Time posts and the warp ----------------------------------------------------

    /**
     * {@code ObjTimePost_Main .Touched}: {@code timeWarpDir} -1 for a past post, 1 for a future
     * post, and the matching voice clip (Monitor and Time Post/Main.asm:162-170).
     */
    public void touchTimePost(boolean past) {
        warpDirection = past ? -1 : 1;
        sounds.add(past ? Sound.PAST_VOICE : Sound.FUTURE_VOICE);
    }

    /**
     * {@code RunTimeWarp}, once per V-INT while unpaused: advances an active timer unless the
     * player is charging (Level/Main.asm:704-709, 971-978).
     */
    public void vIntTick(boolean playerCharging) {
        if (playerCharging || warpTimer == 0) return;
        warpTimer = (warpTimer + 1) & 0xFFFF;
    }

    /** {@code move.w #1,timeWarpTimer.w} (Sonic/Main.asm:597-600). */
    void startTimer() {
        if (warpTimer == 0) warpTimer = 1;
    }

    /** {@code clr.w timeWarpTimer; clr.b timeWarp} while too slow before 90 (Sonic/Main.asm:652-655). */
    void resetTimer() {
        warpTimer = 0;
        starsActive = false;
    }

    /**
     * Metallic Madness's controlled-player branch: {@code clr.w timeWarpTimer; clr.b timeWarp}
     * (Sonic/Main.asm:303-307); the direction is kept.
     */
    public void clearTimerAndStars() {
        resetTimer();
    }

    /** {@code .StopTimeWarp}: timer, direction and stars all cleared (Sonic/Main.asm:660-663). */
    public void stopTimeWarp() {
        warpTimer = 0;
        warpDirection = 0;
        starsActive = false;
    }

    /** {@code move.b #1,timeWarp} when the stars are created (Sonic/Main.asm:116). */
    void startStars() {
        starsActive = true;
    }

    /**
     * The trigger at 210: the new {@code timeZone} with bit 7 set, the saved values and
     * {@code spawnMode} 2 (Sonic/Main.asm:638-643); {@code SCMD_FADEPCM} when leaving the past
     * (Sonic/Main.asm:617-620).
     */
    void trigger(CdWarpRequest request) {
        if (request.fromZone() == CdTimeZone.PAST) sounds.add(Sound.FADE_PCM);
        timeZone = request.toZone();
        warping = true;
        pendingWarp = request;
    }

    /** {@code move.b #1,levelRestart; bra.w FadeOutMusic} at 230 (Sonic/Main.asm:602-605). */
    void requestRestart() {
        warpReady = true;
        sounds.add(Sound.FADE_OUT_MUSIC);
    }

    // ---- Generator, projector, results, Time Stones ---------------------------------

    /** {@code ObjRobotGenerator_ActivePast}: destroying the past generator sets {@code goodFuture} (Robot Generator/Main.asm:96-102). */
    public void destroyGenerator() {
        goodFuture = true;
    }

    /** {@code st projDestroyed} (Palmtree Panic/Objects/Projector/Main.asm:218). */
    public void destroyProjector() {
        projectorDestroyed = true;
    }

    /** The special-stage owner's Time Stone set ({@code timeStones}, bit per stone). */
    public void setTimeStones(int stones) {
        timeStones = stones & 0xFF;
    }

    /**
     * {@code ObjResults_NextLevel} for leaving act {@code completedAct} (1, 2 or 3): clears
     * {@code projDestroyed}, sets the next act's {@code timeZone} and credits {@code goodFuture}
     * (Results/Main.asm:277-341); after act 3 the zone's good future is recorded and the flag
     * cleared (IPX.asm:183-187).
     */
    public void actCompleted(CdZone zone, int completedAct) {
        projectorDestroyed = false;
        prepareEntry(Entry.NEXT_ACT);
        if (completedAct >= 3) {
            goodFutureFlags = 0;                                        // Results/Main.asm:316-321
            if (goodFuture) goodFutures |= 1 << zone.ordinal();         // IPX.asm:185-187
            goodFuture = false;
            timeZone = CdTimeZone.PRESENT;
            return;
        }
        timeZone = completedAct == 2 ? CdTimeZone.FUTURE : CdTimeZone.PRESENT;   // Results/Main.asm:293-300
        if (timeStones == ALL_TIME_STONES) {                            // Results/Main.asm:327-328
            goodFuture = true;
            return;
        }
        if (!goodFuture) return;
        goodFuture = false;                                             // Results/Main.asm:334
        goodFutureFlags |= 1 << (completedAct - 1);
        if (goodFutureFlags == 0b11) goodFuture = true;                 // Results/Main.asm:336-340
    }

    /** IPX {@code GameOver}: {@code projDestroyed} and {@code goodFuture} cleared (IPX.asm:332-338). */
    public void gameOver() {
        projectorDestroyed = false;
        goodFuture = false;
    }

    // ---- Rewind ----------------------------------------------------------------------

    @Override public String key() { return "soniccd.timetravel"; }

    @Override
    public Snapshot capture() {
        return new Snapshot(timeZone, warping, warpDirection, warpTimer, starsActive, pendingWarp, warpReady,
                goodFuture, goodFutureFlags, goodFutures, projectorDestroyed, timeStones, loadedPayload, nextEntry);
    }

    @Override
    public void restore(Snapshot s) {
        timeZone = s.timeZone();
        warping = s.warping();
        warpDirection = s.warpDirection();
        warpTimer = s.warpTimer();
        starsActive = s.starsActive();
        pendingWarp = s.pendingWarp();
        warpReady = s.warpReady();
        goodFuture = s.goodFuture();
        goodFutureFlags = s.goodFutureFlags();
        goodFutures = s.goodFutures();
        projectorDestroyed = s.projectorDestroyed();
        timeStones = s.timeStones();
        loadedPayload = s.loadedPayload();
        nextEntry = s.nextEntry();
        sounds.clear();
    }

    @Override
    public void resetForMissingSnapshot() {
        restore(new Snapshot(CdTimeZone.PRESENT, false, 0, 0, false, null, false, false, 0, 0, false, 0, null, null));
    }
}
