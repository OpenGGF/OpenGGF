package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * ROM {@code Obj_57E34} (sonic3k.asm:116920-116893): the act-1 cutscene Knuckles beam.
 *
 * <p>{@code Obj_57C1E} allocates it with {@code subtype = $60} for every player mode. The
 * subtype counts down; on the frame it reaches zero the spawner allocates
 * {@code Obj_CutsceneKnuckles} with subtype {@code $2C} ({@code CutsceneKnux_SSZ}) at X
 * {@code $100} and records {@code $3E = $C4E} as his base Y. It then rides him down the same
 * {@code Gradual_SwingOffset($20000,$800)} arc the leader used, and when the swing speed turns
 * non-negative it sets {@code _unkFAB8} bit 0 — the flag {@code CutsceneKnux_SSZ} routine 2
 * waits on — and deletes itself.
 */
public final class SszCutsceneKnucklesSpawnerObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code move.w #$C4E,$3E(a0)}. */
    static final int KNUCKLES_BASE_Y = 0xC4E;
    /** {@code move.b #$2C,subtype(a1)} = {@code CutsceneKnux_SSZ}. */
    static final int CUTSCENE_KNUCKLES_SUBTYPE = 0x2C;
    /** {@code bset #0,(_unkFAB8).w}. */
    static final int FLAG_BEAM_LANDED = 0;

    private static final int SWING_SPEED = 0x20000;
    private static final int SWING_ACCELERATION = 0x800;

    @RewindTransient(reason = "Constructor-derived from the immutable spawn record; recreateForRewind rebuilds it.")
    private final int x;
    /** {@code subtype(a0)}. */
    private int delay;
    private final S3kGradualSwing swing = new S3kGradualSwing();
    private CutsceneKnucklesSszInstance knuckles;

    private record RewindExtra(int delay, S3kGradualSwing.Value swing,
                               ObjectRefId knucklesId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszCutsceneKnucklesSpawnerObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SszCutsceneKnucklesSpawner");
        this.x = spawn.x();
        this.delay = spawn.subtype();
    }

    @Override
    public SszCutsceneKnucklesSpawnerObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SszCutsceneKnucklesSpawnerObjectInstance(ctx.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId knucklesId = context.identityTable()
                .map(table -> table.encodeObject(knuckles)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                delay, swing.captureRewindStateValue(), knucklesId));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            delay = extra.delay();
            swing.restoreRewindStateValue(extra.swing());
            knuckles = extra.knucklesId() == null ? null
                    : (CutsceneKnucklesSszInstance) context.requireIdentityTable()
                            .resolveObject(extra.knucklesId(), true);
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (delay != 0) {
            // tst.w subtype(a0) / subq.w #1,subtype(a0) / bne: the allocation happens on the
            // frame the counter reaches zero, $60 frames after Obj_57C1E ran.
            delay--;
            if (delay != 0) {
                return;
            }
            knuckles = spawnChild(() -> new CutsceneKnucklesSszInstance(new ObjectSpawn(
                    CutsceneKnucklesSszInstance.SPAWN_X, KNUCKLES_BASE_Y,
                    0, CUTSCENE_KNUCKLES_SUBTYPE, 0, false, 0)));
            if (knuckles == null) {
                // bne.s locret_57E94: a failed allocation leaves the spawner holding subtype 0,
                // after which loc_57E64 rides a null a1. The engine simply stops.
                com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
                return;
            }
        }
        int offset = swing.step(SWING_SPEED, SWING_ACCELERATION);
        if (knuckles != null) {
            knuckles.setBeamY((KNUCKLES_BASE_Y + offset) & 0xFFFF);
        }
        if (swing.rising()) {
            return;
        }
        SszZoneRuntimeState state =
                S3kRuntimeStates.currentSsz(services().zoneRuntimeRegistry()).orElse(null);
        if (state != null) {
            state.setCutsceneFlag(FLAG_BEAM_LANDED);
        }
        com.openggf.level.objects.ObjectLifetimeOps.deleteNoRespawn(this);
    }

    int delayForTest() { return delay; }
    CutsceneKnucklesSszInstance knucklesForTest() { return knuckles; }

    @Override public int getX() { return x; }
    @Override public int getY() { return KNUCKLES_BASE_Y; }
    @Override public int getOutOfRangeReferenceX() { return x; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // No mappings: the spawner only positions the cutscene Knuckles object.
    }
}
