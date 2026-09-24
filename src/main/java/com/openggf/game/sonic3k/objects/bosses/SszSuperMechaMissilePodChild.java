package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.objects.S3kRawAnimation;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.io.IOException;
import java.util.List;

/**
 * {@code loc_7C78E}: the {@code ChildObjDat_7D49A} missile pod attached to Super Mecha Sonic.
 * The ROM keeps it at {@code (+$14,-4)}, mirrors the X offset with the parent, animates through
 * {@code byte_7D67B}, and deletes it as soon as the parent's {@code $38} bit 6 clears.
 */
public final class SszSuperMechaMissilePodChild extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int DX = 0x14;
    private static final int DY = -4;

    private final SszMechaSonicObjectInstance parent;
    private int x;
    private int y;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();
    private transient S3kRawAnimation animator;

    private record RewindExtra(int x, int y, int animFrame, int animFrameTimer, int mappingFrame)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszSuperMechaMissilePodChild(ObjectSpawn spawn,
                                        SszMechaSonicObjectInstance parent) {
        super(spawn, "SSZSuperMechaMissilePod");
        this.parent = parent;
        x = spawn.x();
        y = spawn.y();
        animation.script = Sonic3kConstants.SSZ_MECHA_MISSILE_POD_ANIM_ADDR;
    }

    public SszSuperMechaMissilePodChild(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new SszSuperMechaMissilePodChild(context.spawn());
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(x, y, animation.animFrame, animation.animFrameTimer,
                        animation.mappingFrame));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            x = extra.x();
            y = extra.y();
            animation.animFrame = extra.animFrame();
            animation.animFrameTimer = extra.animFrameTimer();
            animation.mappingFrame = extra.mappingFrame();
        }
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (parent == null || !parent.missilePodVisible()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        boolean flipped = parent.renderFlippedForTest();
        x = (parent.getX() + (flipped ? -DX : DX)) & 0xFFFF;
        y = (parent.getY() + DY) & 0xFFFF;
        if (animator == null) {
            try {
                animator = S3kRawAnimation.load(services().romReader(),
                        Sonic3kConstants.SSZ_MECHA_ANIM_BLOCK_ADDR,
                        Sonic3kConstants.SSZ_MECHA_ANIM_BLOCK_SIZE);
            } catch (IOException | RuntimeException ignored) {
                return;
            }
        }
        animator.animateNoSst(animation,
                Sonic3kConstants.SSZ_MECHA_MISSILE_POD_ANIM_ADDR, () -> { });
    }

    public int mappingFrameForTest() { return animation.mappingFrame; }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getOnScreenHalfWidth() { return 0x18; }
    @Override public int getOnScreenHalfHeight() { return 0x18; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x280); }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MECHA_SONIC_EXTRA);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrameForTest(), x, y,
                    parent != null && parent.renderFlippedForTest(), false, 1);
        }
    }
}
