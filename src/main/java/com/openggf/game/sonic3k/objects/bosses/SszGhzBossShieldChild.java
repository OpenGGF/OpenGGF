package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * {@code ChildObjDat_7A69E} -&gt; {@code loc_7A558} (sonic3k.asm:162906-162920): the flickering
 * emitter the Green Hill recreation carries {@code $1E} pixels in front of itself once it starts
 * its run.
 *
 * <p>It is set up with {@code SetUp_ObjAttributes3}, which starts at the priority word — so the
 * mappings and art tile are the ones {@code CreateChild1_Normal} copied from the ship, and this
 * draws {@code Map_RobotnikShip} frame 6, not the ball-and-chain sheet. {@code word_7A65A} gives
 * priority {@code $200}, {@code 8} by {@code 4} and no collision byte of its own.
 *
 * <p>{@code loc_7A568} is three tests before it does anything: the parent's {@code status} bit 7
 * deletes it outright, the parent's {@code $38} bit 6 hides it for the frame, and
 * {@code (V_int_run_count+3)} bit 0 hides it every other frame — the flicker is a skipped draw,
 * not an animation. Only on the frames it survives does it refresh its position, add itself to the
 * collision response list and draw.
 *
 * <p><b>It cannot hurt anything.</b> {@code word_7A65A}'s last byte is the
 * {@code collision_flags} {@code SetUp_ObjAttributes3} writes and it is {@code 0}, so the
 * {@code Add_SpriteToCollisionResponseList} entry is inert every frame — this is a decoration on
 * the nose of the ship, not a second hitbox. The one object in this fight that touches the player
 * is the ball, through {@code loc_7A514}'s {@code Child_DrawTouch_Sprite_FlickerMove}.
 *
 * <p><b>The first test is what deletes it, and it fires at the killing hit.</b> Nothing inside
 * {@code Obj_SSZGHZBoss} sets {@code status} bit 7 on the ship — the hit window sets bit 6
 * ({@code sub_7A5A0}) and the defeat sets {@code $38} bit 4 ({@code loc_7A3F8}) — but the shared
 * touch response does: {@code Touch_Enemy}'s {@code .checkhurtenemy} runs
 * {@code subq.b #1,boss_hitcount2(a1) / bne.s .bossnotdefeated / bset #7,status(a1)}
 * (sonic3k.asm:20922) when the last hit lands. So {@code loc_7A568} takes {@code loc_7A59A} and
 * {@code Delete_Current_Sprite} on the frame after the eighth hit, well before the escape run
 * ends — not when the ship's slot is finally freed.
 */
public final class SszGhzBossShieldChild extends AbstractObjectInstance
        implements RewindRecreatable {

    /** {@code word_7A65A}: {@code dc.w $200} / {@code dc.b 8,4,6,0}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x200);
    private static final int MAPPING_FRAME = 6;
    private static final int HALF_WIDTH = 8;
    private static final int HALF_HEIGHT = 4;
    /** {@code ChildObjDat_7A69E}: {@code dc.b $1E,0}. */
    static final int CHILD_DX = 0x1E;

    private SszGhzBossObjectInstance parent;
    private int x;
    private int y;
    private boolean visibleThisFrame;

    public SszGhzBossShieldChild(ObjectSpawn spawn, SszGhzBossObjectInstance parent) {
        super(spawn, "SSZGHZBossShield");
        this.parent = parent;
        this.x = spawn.x();
        this.y = spawn.y();
    }

    /** Probe/rewind constructor: the spawn alone, with the parent re-resolved on restore. */
    public SszGhzBossShieldChild(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override
    public SszGhzBossShieldChild recreateForRewind(RewindRecreateContext ctx) {
        return new SszGhzBossShieldChild(ctx.spawn());
    }

    private record RewindExtra(ObjectRefId parentId, int x, int y, boolean visibleThisFrame)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context)
                .withObjectSubclassExtra(new RewindExtra(parentId, x, y, visibleThisFrame));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            y = extra.y();
            visibleThisFrame = extra.visibleThisFrame();
            parent = extra.parentId() == null ? null
                    : (SszGhzBossObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.parentId(), true);
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visibleThisFrame = false;
        if (parent == null || parent.isDestroyed() || parent.hasStatusBit7()) {
            // btst #7,status(a1) / bne.s loc_7A59A -> jmp (Delete_Current_Sprite).
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (parent.isChainPhaseActive()) {
            // btst #6,$38(a1): hidden while the ball and chain is the threat.
            return;
        }
        if ((vIntRunCount & 1) != 0) {
            return;
        }
        // Refresh_ChildPositionAdjusted negates child_dx when the parent is X-flipped.
        int dx = parent.isRenderFlippedForTest() ? -CHILD_DX : CHILD_DX;
        x = (parent.getX() + dx) & 0xFFFF;
        y = parent.getY() & 0xFFFF;
        visibleThisFrame = true;
    }

    /** True on the frames {@code loc_7A568} reaches its draw and its collision-list entry. */
    public boolean isVisibleForTest() { return visibleThisFrame; }


    /** No {@code Obj_WaitOffscreen} in this chain either: the parent's escape takes it off screen. */
    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return HALF_WIDTH; }
    @Override public int getOnScreenHalfHeight() { return HALF_HEIGHT; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visibleThisFrame) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(MAPPING_FRAME, x, y,
                    parent != null && parent.isRenderFlippedForTest(), false, 0);
        }
    }
}
