package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * The phase-2 rocket's children from {@code ChildObjDat_83294} (sonic3k.asm:174590-174616).
 *
 * <p>{@link #KIND_FLAME} is {@code loc_82588}: {@code word_8323E} (priority {@code $280}, frame
 * {@code $33}) at {@code +8,0}, animated by {@code byte_832C3}, dropping to priority {@code $100} once
 * the rocket flies. {@link #KIND_EXHAUST} is {@code loc_825BC} running {@code loc_82160}: frame
 * {@code $12} at {@code -$18,0} with the rocket's priority, a puff on every eighth V-int and drawn on
 * V-ints with bit 1 clear. Both delete themselves when the rocket explodes.
 */
final class DdzEndBossRocketFlameObjectInstance extends AbstractDdzObjectInstance {
    static final int KIND_FLAME = 0;
    static final int KIND_EXHAUST = 1;
    private static final int PALETTE = 2;

    private final int kind;
    private int x;
    private int y;
    private boolean visible;
    private boolean initialized;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();


    DdzEndBossRocketFlameObjectInstance(DdzEndBossRocketObjectInstance rocket, int kind) {
        super(new ObjectSpawn(rocket == null ? 0 : rocket.getX(), rocket == null ? 0 : rocket.getY(), 0, kind, 0, false, 0),
                "DDZEndBossRocketFlame", rocket);
        this.kind = kind;
        if (rocket != null) {
            x = (rocket.getX() + offsetX()) & 0xFFFF;
            y = rocket.getY() & 0xFFFF;
        }
        S3kRawAnimation.set(animation, Sonic3kConstants.DDZ_ANIM_ROCKET_FLAME_ADDR);
        animation.mappingFrame = kind == KIND_FLAME ? 0x33 : 0x12;
    }

    @Override
    public DdzEndBossRocketFlameObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzEndBossRocketFlameObjectInstance(null, ctx.spawn().subtype());
    }

    private int offsetX() {
        return kind == KIND_FLAME ? 8 : -0x18;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity player) {
        DdzEndBossRocketObjectInstance rocket = rocket();
        if (rocket == null || rocket.exploding()) {
            goDelete();
            return;
        }
        if (!initialized) {
            initialized = true;
            // loc_82588 draws on its init pass; loc_825BC does not.
            visible = kind == KIND_FLAME;
            return;
        }
        if (kind == KIND_FLAME) {
            DdzObjectSupport.rawAnimations(services()).animateMultiDelay(animation, () -> { });
            refresh();
            visible = true;
            return;
        }
        int phase = vIntRunCount & 7;
        if (phase == 0) {
            int px = x;
            int py = y;
            spawnChild(() -> new DdzMissilePuffObjectInstance(px, py, 0, -0x100));
        }
        if ((phase & 2) == 0) {
            refresh();
            visible = true;
        } else {
            visible = false;
        }
    }

    private void refresh() {
        DdzEndBossRocketObjectInstance rocket = rocket();
        x = (rocket.getX() + offsetX()) & 0xFFFF;
        y = rocket.getY() & 0xFFFF;
    }

    private DdzEndBossRocketObjectInstance rocket() {
        return parent instanceof DdzEndBossRocketObjectInstance rocket ? rocket : null;
    }

    @Override
    public boolean isHighPriority() {
        return kind == KIND_EXHAUST && rocket() != null && rocket().isHighPriority();
    }

    @Override
    public int getPriorityBucket() {
        if (kind == KIND_EXHAUST) {
            return rocket() != null ? rocket().getPriorityBucket() : RenderPriority.fromS3kWord(0x180);
        }
        return RenderPriority.fromS3kWord(rocket() != null && rocket().flying() ? 0x100 : 0x280);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable() || !visible) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(animation.mappingFrame, x, y, false, false, PALETTE);
        }
    }


}
