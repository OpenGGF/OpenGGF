package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM {@code loc_7D056} (sonic3k.asm:166838-166864): the object {@code loc_7B888} allocates
 * beside the beaten Mecha Sonic to hand Sky Sanctuary act 1 over.
 *
 * <p>Three lines of ROM and one subroutine. {@code loc_7D056} writes {@code $2E = (2*60)-1} and
 * installs {@code loc_7D062}, which calls {@code sub_868F8} with {@code d0 = 2} on every
 * dispatch. That routine pre-decrements {@code $2E} and then refuses to fire while Player 1 is
 * dead ({@code status} bit 7), in the air, or already in a routine of 6 or more — so the act does
 * not end on a timer alone, it ends on the first frame after the timer on which the leader is
 * standing. Then it writes the routine, sets the ending pose and allocates
 * {@code Obj_LevelResults}.
 *
 * <p><b>This is the act-1 handover, and it is the results screen — not a level request.</b>
 * {@code StartNewLevel $B00} belongs to {@code loc_581D2}, the Death Egg launch, which runs from
 * {@code End_of_level_flag} afterwards and is slice 8's.
 *
 * <p>{@code loc_7D078} then waits for {@code _unkFAA8} to clear before
 * {@code Restore_PlayerControl} and {@code Delete_Current_Sprite}. Nothing in act 1 clears that
 * flag, so in the shipped game this object waits out the rest of the act; that is the branch, not
 * an omission.
 *
 * <p>Dated against the native {@code hpz_3} segment of
 * {@code s3k-sonic-tails-complete-emeralds}, comparison only: the slot appears on frame 1439, the
 * same frame the boss reaches {@code off_7B838}'s routine 4, which is what {@code loc_7B888}
 * allocating it in its own dispatch produces.
 */
public final class SszMechaSonicActEndObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {

    /** {@code move.w #(2*60)-1,$2E(a0)}. */
    public static final int HANDOVER_WAIT = (2 * 60) - 1;
    /** {@code moveq #2,d0} — the routine {@code sub_868F8} writes when it fires. */
    public static final int RESULTS_ROUTINE = 2;
    private int timer = HANDOVER_WAIT;
    private int routine;
    private boolean resultsRequested;
    private boolean sidekickPosed;

    public SszMechaSonicActEndObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZMechaSonicActEnd");
    }

    private record RewindExtra(int timer, int routine, boolean resultsRequested, boolean sidekickPosed)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(timer, routine, resultsRequested, sidekickPosed));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            timer = extra.timer();
            routine = extra.routine();
            resultsRequested = extra.resultsRequested();
            sidekickPosed = extra.sidekickPosed();
        }
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SszMechaSonicActEndObjectInstance(context.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (isDestroyed()) {
            return;
        }
        if (routine == 0) {
            runHandoverGate(player);
            return;
        }
        // loc_7D078: Check_TailsEndPose, then hold until _unkFAA8 clears. In act 1 nothing
        // clears it, so this object stays put rather than restoring control and deleting.
        SszZoneRuntimeState ssz = sszState();
        if (ssz != null && ssz.mechaSonicBeaten()) {
            if (!sidekickPosed && services().playerQuery().nativeP2OrNull()
                    instanceof AbstractPlayableSprite sidekick && !sidekick.getDead() && !sidekick.getAir()) {
                sidekickPosed = true;
                sidekick.setControlLocked(false);
                setEndingPose(sidekick);
            }
            return;
        }
        if (player instanceof AbstractPlayableSprite leader) restoreControl(leader);
        if (services().playerQuery().nativeP2OrNull() instanceof AbstractPlayableSprite sidekick)
            restoreControl(sidekick);
        ObjectLifetimeOps.deleteNoRespawn(this);
    }

    /** {@code loc_7D062} and the {@code sub_868F8} it calls with {@code d0 = 2}. */
    private void runHandoverGate(PlayableEntity player) {
        // subq.w #1,$2E(a0) / bpl.s locret: the decrement happens before every gate test.
        timer = (short) (timer - 1);
        if (timer >= 0) {
            return;
        }
        if (!(player instanceof AbstractPlayableSprite leader)) {
            return;
        }
        // btst #7,status(a1) and btst #Status_InAir,status(a1), directly. The third test,
        // cmpi.b #6,routine(a1) / bhs, refuses while the leader is in the death or drowning
        // routines; the engine's player exposes no routine byte, and getDead() is the state
        // those routines describe. Recorded as the one approximation here rather than silently
        // dropped.
        if (leader.getDead() || leader.getAir()) {
            return;
        }
        routine = RESULTS_ROUTINE;
        resultsRequested = true;
        setEndingPose(leader);
        services().gameState().setEndOfLevelFlag(false);
        services().gameState().setEndOfLevelActive(true);
        // AllocateObject is attempted once, after advancing our routine, even if full.
        spawnFreeChild(() -> ObjectConstructionContext.construct(services(), () -> new Results(
                S3kRuntimeStates.resolvePlayerCharacter(services().zoneRuntimeRegistry(),
                        services().configuration()), services().currentAct())));
    }

    private static void setEndingPose(AbstractPlayableSprite sprite) {
        ObjectControlState.nativeBit7FullControl().applyTo(sprite);
        sprite.setAnimationId(Sonic3kAnimationIds.VICTORY);
        sprite.setSpindash(false);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setPushing(false);
        // FixBugs=0: status bits 5/6 are cleared on this object, not the player.
    }

    private static void restoreControl(AbstractPlayableSprite sprite) {
        ObjectControlState.none().applyTo(sprite);
        sprite.setAir(false);
        sprite.setAnimationId(Sonic3kAnimationIds.WAIT);
        sprite.setAnimationFrameIndex(0);
        sprite.setAnimationFrameCount(0);
    }

    public static final class Results extends S3kResultsScreenObjectInstance {
        Results(PlayerCharacter character, int act) { super(character, act); }
        private Results() { super(true); }
        @Override protected boolean shouldRestorePlayerControlsOnExit() { return false; }
        @Override protected boolean shouldRestoreCameraBoundsOnExit(int zone, int act) { return false; }
        @Override public Results recreateForRewind(RewindRecreateContext context) {
            return ObjectConstructionContext.construct(context.objectServices(), Results::new);
        }
    }

    private SszZoneRuntimeState sszState() {
        return S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
    }

    /** {@code loc_7D056} never draws: its routines end {@code rts}, not {@code Draw_Sprite}. */
    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }

    public int timerForTest() { return timer; }
    public int routineForTest() { return routine; }
    public boolean resultsRequestedForTest() { return resultsRequested; }
}
