package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_64C24} ({@code ChildObjDat_665FC}): spin-dash dust under the Hidden Palace
 * Knuckles while he charges. The code pointer never changes, so {@code SetUp_ObjAttributes}
 * resets {@code mapping_frame} to {@code $A} on every pass before {@code Animate_RawNoSST} plays
 * {@code byte_6683A}; frames only differ from {@code $A} on the passes where the delay expires.
 * The dust deletes itself once the parent sets {@code $38} bit 0.
 */
public final class HpzKnucklesDustObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int SCRIPT = 0x6683A;
    private static final int BASE_FRAME = 0xA;
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x80);

    private CutsceneKnucklesHpzInstance parent;
    private final S3kRawAnimation.State anim = new S3kRawAnimation.State();
    private int x;
    private int y;
    private boolean flipX;

    private record RewindExtra(int animScript, int animFrame, int animTimer, int mappingFrame,
                               int x, int y, boolean flipX, ObjectRefId parentId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public HpzKnucklesDustObjectInstance(ObjectSpawn spawn, CutsceneKnucklesHpzInstance parent) {
        super(spawn, "HpzKnucklesDust");
        this.parent = parent;
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public HpzKnucklesDustObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzKnucklesDustObjectInstance(ctx.spawn(), null);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        anim.mappingFrame = BASE_FRAME;
        if (parent == null || parent.isDestroyed() || parent.dustSuppressed()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        x = parent.getX();
        y = parent.getY();
        flipX = parent.renderFlipX();
        HpzKnucklesCutsceneSupport.scripts(services()).animateNoSst(anim, SCRIPT, () -> { });
        updateDynamicSpawn(x, y);
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public boolean isPersistent() { return true; }
    int mappingFrameForTest() { return anim.mappingFrame; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_KNUX_BOSS_DUST);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(anim.mappingFrame, x, y, flipX, false);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                anim.script, anim.animFrame, anim.animFrameTimer, anim.mappingFrame,
                x, y, flipX, parentId));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra e) {
            anim.script = e.animScript();
            anim.animFrame = e.animFrame();
            anim.animFrameTimer = e.animTimer();
            anim.mappingFrame = e.mappingFrame();
            x = e.x();
            y = e.y();
            flipX = e.flipX();
            parent = e.parentId() == null ? null
                    : (CutsceneKnucklesHpzInstance) context.requireIdentityTable()
                    .resolveObject(e.parentId(), true);
        }
    }
}
