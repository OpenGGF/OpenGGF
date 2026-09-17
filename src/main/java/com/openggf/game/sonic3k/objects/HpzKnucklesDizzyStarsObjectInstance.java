package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
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
 * ROM {@code loc_6563A} ({@code ChildObjDat_66644}, {@code ObjDat3_6654E}): stars over the
 * Hidden Palace Knuckles, left where he was when it was created.
 *
 * <p>Subtype 0 ({@code loc_649B4}, dizzy after the fall) spins {@code byte_66875} for {@code $63}
 * frames ({@code loc_65662}), holds frame 0 for {@code $3F} and shows frame 5 for {@code $B}
 * before {@code Go_Delete_Sprite}. A non-zero subtype ({@code loc_643C6}, the surprise after the
 * defeat) skips straight to the frame 5 wait. Each wait is {@code Wait_Draw}: {@code Obj_Wait}
 * then {@code Draw_Sprite}, so the callback pass still draws.
 */
public final class HpzKnucklesDizzyStarsObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int SCRIPT = 0x66875;
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);
    private static final int PHASE_INIT = 0;
    private static final int PHASE_SPIN = 1;
    private static final int PHASE_HOLD = 2;
    private static final int PHASE_FINAL = 3;
    private static final int PHASE_DELETE = 4;

    private final S3kRawAnimation.State anim = new S3kRawAnimation.State();
    private int phase;
    private int timer;
    private boolean visible;

    private record RewindExtra(int animFrame, int animTimer, int mappingFrame, int phase,
                               int timer, boolean visible)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public HpzKnucklesDizzyStarsObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HpzKnucklesDizzyStars");
    }

    @Override
    public HpzKnucklesDizzyStarsObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzKnucklesDizzyStarsObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        switch (phase) {
            case PHASE_INIT -> {
                anim.mappingFrame = 1;
                timer = 0x63;
                phase = PHASE_SPIN;
                if ((spawn.subtype() & 0xFF) != 0) {
                    // move.l #Wait_Draw,(a0) / bra.w loc_65692
                    enterFinal();
                }
            }
            case PHASE_SPIN -> {
                timer = (short) (timer - 1);
                if (timer < 0) {
                    // loc_65678
                    phase = PHASE_HOLD;
                    anim.mappingFrame = 0;
                    timer = 0x3F;
                    return;
                }
                HpzKnucklesCutsceneSupport.scripts(services()).animateNoSst(anim, SCRIPT, () -> { });
                visible = true;
            }
            case PHASE_HOLD -> {
                timer = (short) (timer - 1);
                if (timer < 0) {
                    enterFinal();
                }
                visible = true;
            }
            case PHASE_FINAL -> {
                timer = (short) (timer - 1);
                if (timer < 0) {
                    phase = PHASE_DELETE;
                }
                visible = true;
            }
            default -> ObjectLifetimeOps.expireDynamic(this);
        }
    }

    /** {@code loc_65692}. */
    private void enterFinal() {
        phase = PHASE_FINAL;
        anim.mappingFrame = 5;
        timer = 0xB;
    }

    int mappingFrameForTest() { return anim.mappingFrame; }

    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_KNUX_DIZZY_STARS);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(anim.mappingFrame, spawn.x(), spawn.y(), false, false);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                anim.animFrame, anim.animFrameTimer, anim.mappingFrame, phase, timer, visible));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra e) {
            anim.animFrame = e.animFrame();
            anim.animFrameTimer = e.animTimer();
            anim.mappingFrame = e.mappingFrame();
            phase = e.phase();
            timer = e.timer();
            visible = e.visible();
        }
    }
}
