package com.openggf.game.sonic2.competition.lifecycle;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.competition.NativePlayerDeathPass;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic2.competition.Sonic2CompetitionPlayerState;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.level.LevelManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Native human death, narrow checkpoint respawn and Obj39 result ownership.
 *
 * <p>The host publishes this stable owner and {@link NativePlayerDeathPass} on
 * the actors' actual world policy, registers this rewind adapter, and calls
 * {@link #initializeAct()} after creating both human actors and cameras. This
 * service retains controller-slot numbers, never actor references. The roster
 * must be rebound before forward execution after destructive graph restore.
 * Session counters, actors, cameras and the object graph have their own adapters.
 *
 * <p>{@link #recordCameraDisplacement} receives the signed native word changes
 * AFTER each camera pass, including a zero on a stationary pass. Routine $A
 * reads the previous camera pass, before the next camera update. Do not replace
 * these words with target distance, snap a camera, or derive readiness from a
 * fitted wait. Drowning's existing pre-death dispatch precedes this service;
 * the existing caller retains sensors and post-movement animation/PLC dispatch.
 *
 * <p>Independent respawn is NOT Obj79_LoadData. It restores only saved position,
 * art and solidity, while the running timer, camera bounds and world continue.
 * The larger P1 checkpoint bank is retained for RAM fidelity, not applied to
 * independent respawn. P2 has no camera/water checkpoint bank. The supplied
 * environment reader captures the host's real current P1 globals at activation.
 * No level reload, durable configuration or emerald reset is performed here.
 *
 * <p>Source: CheckGameOver / Obj01_ResetLevel (s2.asm:38274-38368),
 * Obj02_CheckGameOver / Obj02_ResetLevel (s2.asm:41142-41257),
 * Obj79_SaveData (s2.asm:44733-44772), Obj39_Dismiss (s2.asm:27737-27775).
 */
public final class Sonic2CompetitionLifecycle implements NativePlayerDeathPass,
        RewindSnapshottable<Sonic2CompetitionLifecycle.Snapshot> {
    public enum Routine { CONTROL, DEAD, GONE, RESPAWNING }

    /** Must use the canonical fixed-prefix overwrite, including occupied slots 2/3. */
    @FunctionalInterface
    public interface CardSink { void begin(int ownerSlot, boolean timeOver); }

    public record SlotState(Routine routine, Routine dispatchedRoutine, int cameraXDiff, int cameraYDiff,
                            int artTile, boolean releaseRespawnAnimation, boolean initialPassPending) {
        public SlotState {
            Objects.requireNonNull(routine, "routine");
            Objects.requireNonNull(dispatchedRoutine, "dispatchedRoutine");
            cameraXDiff = (short) cameraXDiff;
            cameraYDiff = (short) cameraYDiff;
            artTile &= 0xFFFF;
        }
    }

    public record Snapshot(String romFingerprint, boolean initialized,
                           List<SlotState> slots,
                           List<Sonic2CompetitionCheckpoints.SavedPlayer> checkpoints) {
        public Snapshot {
            Objects.requireNonNull(romFingerprint, "romFingerprint");
            slots = List.copyOf(slots);
            // An unseeded checkpoint bank intentionally contains null entries.
            checkpoints = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(checkpoints));
        }
    }

    private final Sonic2CompetitionSession session;
    private final Sonic2CompetitionLifecycleRom rom;
    private final CardSink cards;
    private final Supplier<Sonic2CompetitionCheckpoints.PrimaryEnvironment> checkpointEnvironment;
    private final Sonic2CompetitionCheckpoints checkpoints = new Sonic2CompetitionCheckpoints();
    private final Routine[] routines = {Routine.CONTROL, Routine.CONTROL};
    private final Routine[] dispatched = {Routine.CONTROL, Routine.CONTROL};
    private final int[] cameraXDiff = new int[2], cameraYDiff = new int[2], artTiles = new int[2];
    private final boolean[] releaseAnimation = new boolean[2];
    private final boolean[] initialPassPending = {true, true};
    private boolean initialized;

    public Sonic2CompetitionLifecycle(Rom source, Sonic2CompetitionSession session, CardSink cards,
            Supplier<Sonic2CompetitionCheckpoints.PrimaryEnvironment> checkpointEnvironment) throws IOException {
        this.rom = Sonic2CompetitionLifecycleRom.load(Objects.requireNonNull(source, "source"));
        this.session = Objects.requireNonNull(session, "session");
        this.cards = Objects.requireNonNull(cards, "cards");
        this.checkpointEnvironment = Objects.requireNonNull(checkpointEnvironment, "checkpointEnvironment");
    }

    public static Sonic2CompetitionLifecycle from(LevelManager level) {
        return level == null ? null : WorldSessionPolicyAccess.getService(
                level.getWorldSession(), Sonic2CompetitionLifecycle.class);
    }

    public void initializeAct() {
        if (session.phase() != Sonic2CompetitionSession.Phase.ACT)
            throw new IllegalStateException("No native act to initialize");
        for (int slot = 0; slot < 2; slot++) {
            var actor = requireHuman(slot);
            Objects.requireNonNull(session.camera(slot), "native camera");
            routines[slot] = Routine.CONTROL;
            dispatched[slot] = Routine.CONTROL;
            cameraXDiff[slot] = cameraYDiff[slot] = 0;
            releaseAnimation[slot] = false;
            initialPassPending[slot] = true;
            artTiles[slot] = slot == 0 ? rom.sonicArtTile() : rom.tailsArtTile();
            checkpoints.seed(slot, actor.getCentreX(), actor.getCentreY(), artTile(slot),
                    solidBits(actor), actor.getLayer());
        }
        initialized = true;
    }

    public Routine routine(int slot) { checkSlot(slot); return routines[slot]; }
    public Sonic2CompetitionCheckpoints.SavedPlayer checkpoint(int slot) { return checkpoints.saved(slot); }

    /**
     * Actual S2 primary routine, independent of current dispatch or result phase.
     * Obj0A_ReduceAir (s2.asm:42146-42176) preserves entry 2/4 during its wait;
     * only Obj0A_PlayerHasDrowned (s2.asm:42179-42191) writes 6. S2's $A is
     * camera-wait respawn, not the S3K drowning semantic.
     */
    public int nativeRoutineByte(int slot) {
        var actor = requireLiveHuman(slot);
        if (!initialized || initialPassPending[slot]) return 0;
        return switch (routines[slot]) {
            case DEAD -> 6;
            case GONE -> 8;
            case RESPAWNING -> 10;
            case CONTROL -> actor.getDead() ? 6 : actor.isHurt() ? 4 : 2;
        };
    }

    /** Applies the captured live control byte without replaying hurt/death effects. */
    public void applyCapturedControlRoutineByte(int slot, int raw) {
        if (raw != 2 && raw != 4) throw new IllegalArgumentException("Captured S2 control routine " + raw);
        var actor = requireLiveHuman(slot);
        requireInitialized();
        if (actor.getDead() || nativeRoutineByte(slot) >= 6)
            throw new IllegalStateException("Native death routine cannot accept a control-byte swap");
        com.openggf.sprites.playable.PlayableSpriteInternalAccess.applyCapturedControlRoutineByte(actor, raw);
        initialPassPending[slot] = false;
    }

    /** Host must supply actual signed word displacements, also on zero-motion passes. */
    public void recordCameraDisplacement(int slot, int xDiff, int yDiff) {
        checkSlot(slot);
        cameraXDiff[slot] = (short) xDiff;
        cameraYDiff[slot] = (short) yDiff;
    }

    /** Logical native art word: fixed ROM tile/palette bits plus the actor's live priority. */
    public int artTile(int slot) {
        checkSlot(slot);
        return (artTiles[slot] & 0x7FFF) | (requireHuman(slot).isHighPriority() ? 0x8000 : 0);
    }

    public boolean canActivateCheckpoint(int slot, int subtype) {
        requireInitialized();
        return checkpoints.canActivate(slot, subtype);
    }

    /** Called independently for P1 then P2 by Obj79, even after its pole has activated. */
    public void saveCheckpoint(int slot, int subtype, int postX, int postY) {
        requireInitialized();
        var actor = requireHuman(slot);
        var bank = session.playerState(slot);
        checkpoints.save(slot, subtype, postX, postY, artTile(slot), solidBits(actor), actor.getLayer(),
                bank.getRings(), bank.getRingExtraLifeFlags(), bank.getTimerFrames(),
                slot == 0 ? Objects.requireNonNull(checkpointEnvironment.get(), "P1 checkpoint globals") : null);
    }

    /** Dispatcher query before movement; the last routine-A call still owns this pass. */
    public boolean ownsDispatch(AbstractPlayableSprite actor) {
        int slot = session.slotOf(actor);
        return slot >= 0 && !actor.isCpuControlled() && session.phase() == Sonic2CompetitionSession.Phase.ACT
                && from(actor.currentLevelManagerIfAvailable()) == this
                && (routines[slot] != Routine.CONTROL || actor.getDead());
    }

    /** Routine 8 has no Animate/PLC/Display; 6 and A retain their canonical animation call. */
    public boolean animatesThisPass(AbstractPlayableSprite actor) {
        int slot = session.slotOf(actor);
        return slot >= 0 && dispatched[slot] != Routine.GONE;
    }

    @Override public boolean runDeathPass(AbstractPlayableSprite actor) {
        int slot = session.slotOf(actor);
        if (slot < 0 || actor.isCpuControlled() || session.phase() != Sonic2CompetitionSession.Phase.ACT)
            return false;
        // A roster from another/retired world cannot grant authority to this service.
        if (from(actor.currentLevelManagerIfAvailable()) != this) return false;
        if (!initialized) {
            if (actor.getDead()) throw new IllegalStateException("Native respawn bank was not seeded");
            return false;
        }
        // S2's leading title pass uses the canonical control dispatcher, not
        // S3K's initial-assembly callback. Its first accepted pass completes Init.
        if (routines[slot] == Routine.CONTROL) initialPassPending[slot] = false;
        if (routines[slot] == Routine.CONTROL && !actor.getDead()) {
            dispatched[slot] = Routine.CONTROL;
            if (releaseAnimation[slot]) {
                actor.setForcedAnimationId(-1);
                actor.setObjectRoutineOverride(null);
                releaseAnimation[slot] = false;
            }
            return false;
        }
        if (routines[slot] == Routine.CONTROL) routines[slot] = Routine.DEAD;
        dispatched[slot] = routines[slot];
        switch (routines[slot]) {
            case DEAD -> dead(slot, actor);
            case GONE -> {
                actor.setHidden(true); // Obj01/02_Gone has no DisplaySprite or Animate call.
                if (actor.tickDeathCountdown()) actor.currentLevelManager().requestRespawn();
            }
            case RESPAWNING -> {
                actor.setForcedAnimationId(rom.respawnAnimation());
                if (cameraXDiff[slot] == 0 && cameraYDiff[slot] == 0) {
                    routines[slot] = Routine.CONTROL;
                    actor.setObjectRoutineOverride(2);
                    releaseAnimation[slot] = true;
                }
            }
            default -> throw new IllegalStateException("Unreachable native death dispatch");
        }
        if (dispatched[slot] != Routine.DEAD)
            com.openggf.sprites.playable.PlayableSpriteInternalAccess.markFollowerHistoryHandledWithoutWrite(actor);
        return true;
    }

    private void dead(int slot, AbstractPlayableSprite actor) {
        Camera camera = session.camera(slot);
        camera.setScrollLocked(true);
        actor.setSpindash(false);
        // ADDI.W and BGE are signed word operations, including wrap at $7FFF.
        if (actor.getCentreY() > (short) (camera.getMaxY() + rom.deathMargin())) {
            var game = session.gameState(slot);
            int lives = (game.getLives() - 1) & 0xFF;
            game.restoreSaveProgress(lives, game.getContinues(), game.getCollectedChaosEmeraldIndices(),
                    game.getCollectedSuperEmeraldIndices(), game.isEmeraldsConverted());
            if (slot == 0) actor.setDeathCountdown(60);
            if (lives == 0) {
                clearOwnTimeOver(slot);
                finish(slot, actor, false, true);
            } else if (slot == 1 && session.levelState(0).isTimeOver() && session.levelState(1).isTimeOver()) {
                // REV01 Obj02_ResetLevel: P1 already owns the shared TIME OVER pair.
                finish(slot, actor, true, false);
            } else if (session.levelState(slot).isTimeOver()) {
                finish(slot, actor, true, true);
            } else {
                respawn(slot, actor);
            }
        }
        // Obj01/02_Dead always falls through ObjectMoveAndFall, even after
        // CheckGameOver restored position or selected Gone/Respawning.
        actor.move(actor.getXSpeed(), actor.getYSpeed());
        actor.setYSpeed((short) (actor.getYSpeed() + rom.gravity()));
        actor.recordFollowerHistoryForTick();
    }

    private void finish(int slot, AbstractPlayableSprite actor, boolean timeOver, boolean createCard) {
        actor.setDeathCountdown(0);
        actor.setObjectRoutineOverride(8);
        routines[slot] = Routine.GONE;
        session.levelState(0).pauseTimer();
        session.levelState(1).pauseTimer();
        if (createCard) cards.begin(slot, timeOver);
    }

    private void clearOwnTimeOver(int slot) {
        var bank = session.playerState(slot);
        var old = bank.capture();
        bank.restore(new Sonic2CompetitionPlayerState.State(old.game(), old.rings(), old.totalRings(),
                old.monitors(), old.extraLifeFlags(), old.frames(), old.paused(), false,
                old.nextLife(), old.checkpoint()));
    }

    private void respawn(int slot, AbstractPlayableSprite actor) {
        var saved = checkpoints.saved(slot);
        session.camera(slot).setScrollLocked(false);
        NativePositionOps.writeXPosPreserveSubpixel(actor, saved.x());
        NativePositionOps.writeYPosPreserveSubpixel(actor, saved.y());
        artTiles[slot] = saved.artTile();
        actor.setHighPriority((saved.artTile() & 0x8000) != 0);
        actor.setTopSolidBit((byte) (saved.solidBits() >>> 8));
        actor.setLrbSolidBit((byte) saved.solidBits());
        actor.setLayer(saved.layer());
        // Neither running time nor a completed signpost's timer-enable byte is restored.
        session.levelState(slot).setRings(0);
        session.levelState(slot).setRingExtraLifeFlags(0);
        actor.setObjectControlled(false);
        actor.setObjectControlAllowsCpu(false);
        actor.setObjectControlSuppressesMovement(false);
        actor.setXSpeed((short) 0); actor.setYSpeed((short) 0); actor.setGSpeed((short) 0);
        actor.setHurt(false);
        actor.setDead(false);
        actor.clearDrowningDeathState();
        com.openggf.sprites.playable.PlayableSpriteInternalAccess.clearScriptedNativeStatus(actor);
        actor.setAir(true);
        actor.setAnimationId(rom.respawnAnimation());
        actor.setForcedAnimationId(rom.respawnAnimation());
        actor.setMoveLockTimer(0);
        if (slot == 0) actor.setDeathCountdown(0); // P2's Part3 does not write this word.
        actor.setObjectRoutineOverride(10);
        routines[slot] = Routine.RESPAWNING;
    }

    /** Native parent low-byte semantics are supplied by the actual surviving left card. */
    public void deathCardDismissed(int ownerSlot) {
        checkSlot(ownerSlot);
        if (session.phase() != Sonic2CompetitionSession.Phase.ACT) return;
        boolean timedOut = session.levelState(0).isTimeOver() || session.levelState(1).isTimeOver();
        if (timedOut) checkpoints.clearPrimarySavedTimer();
        session.deathCardDismissed(ownerSlot, timedOut);
    }

    private static int solidBits(AbstractPlayableSprite actor) {
        return (actor.getTopSolidBit() & 0xFF) << 8 | actor.getLrbSolidBit() & 0xFF;
    }
    private AbstractPlayableSprite requireHuman(int slot) {
        checkSlot(slot);
        var actor = Objects.requireNonNull(session.player(slot), "native human");
        if (actor.isCpuControlled()) throw new IllegalStateException("CPU follower is not a native human");
        return actor;
    }
    private AbstractPlayableSprite requireLiveHuman(int slot) {
        var actor = requireHuman(slot);
        var sprites = actor.currentSpriteManagerOrNull();
        if (from(actor.currentLevelManagerIfAvailable()) != this || sprites == null
                || sprites.getSprite(actor.getCode()) != actor)
            throw new IllegalStateException("Native routine owner is not registered in this world");
        return actor;
    }
    private static void checkSlot(int slot) {
        if (slot < 0 || slot > 1) throw new IllegalArgumentException("native slot " + slot);
    }
    private void requireInitialized() {
        if (!initialized) throw new IllegalStateException("Native respawn bank was not seeded");
    }

    @Override public String key() { return "sonic2-competition-lifecycle"; }
    @Override public Snapshot capture() {
        return new Snapshot(rom.fingerprint(), initialized, List.of(
                new SlotState(routines[0], dispatched[0], cameraXDiff[0], cameraYDiff[0], artTiles[0], releaseAnimation[0], initialPassPending[0]),
                new SlotState(routines[1], dispatched[1], cameraXDiff[1], cameraYDiff[1], artTiles[1], releaseAnimation[1], initialPassPending[1])),
                checkpoints.capture());
    }
    @Override public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!rom.fingerprint().equals(snapshot.romFingerprint()) || snapshot.slots().size() != 2)
            throw new IllegalArgumentException("Foreign native lifecycle snapshot");
        var validated = new Sonic2CompetitionCheckpoints();
        validated.restore(snapshot.checkpoints());
        if (snapshot.initialized()) { validated.saved(0); validated.saved(1); }
        for (int slot = 0; slot < 2; slot++) {
            var saved = snapshot.slots().get(slot);
            if (!snapshot.initialized() && saved.routine() != Routine.CONTROL)
                throw new IllegalArgumentException("Unseeded native death routine");
        }
        checkpoints.restore(validated.capture());
        for (int slot = 0; slot < 2; slot++) {
            var saved = snapshot.slots().get(slot);
            routines[slot] = saved.routine();
            dispatched[slot] = saved.dispatchedRoutine();
            cameraXDiff[slot] = saved.cameraXDiff(); cameraYDiff[slot] = saved.cameraYDiff();
            artTiles[slot] = saved.artTile(); releaseAnimation[slot] = saved.releaseRespawnAnimation();
            initialPassPending[slot] = saved.initialPassPending();
        }
        initialized = snapshot.initialized();
    }
    @Override public void resetForMissingSnapshot() {
        checkpoints.clear();
        initialized = false;
        for (int slot = 0; slot < 2; slot++) {
            routines[slot] = Routine.CONTROL;
            dispatched[slot] = Routine.CONTROL;
            cameraXDiff[slot] = cameraYDiff[slot] = artTiles[slot] = 0;
            releaseAnimation[slot] = false;
            initialPassPending[slot] = true;
        }
    }
}
