package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_652A2} ({@code ChildObjDat_66602}, sixteen copies): when the defeated Hidden
 * Palace Knuckles lands, each copy places itself at {@code Camera + ($140,$C0)} plus its
 * {@code byte_652DE} offset, waits {@code subtype * 4} frames ({@code loc_652FE}) and then turns
 * into {@code Obj_Explosion} at routine 2 (no animal): {@code sfx_Break}, then
 * {@code Map_Explosion} frames 0-4 with the high-priority bit set. {@code loc_1E626} falls
 * into {@code loc_1E66E}, so the first animation step and {@code Draw_Sprite} happen on the
 * frame the object becomes an explosion.
 *
 * <p>{@link #normalExplosion} is the same {@code Obj_Explosion} routine 2 object as created by
 * {@code Obj_NormalExpControl} ({@code Child6_MakeNormalExplosion}, sonic3k.asm:176782).
 */
public final class HpzKnucklesCeilingExplosionObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code byte_652DE}. */
    private static final int[] OFFSETS = {
            -0x10, -0x58, -0x18, -0x30, -8, -0x10, -0x10, -0x70,
            -0x20, -0x60, -8, -0x38, -0x20, -0x10, -0x20, -0x40,
            -0x40, -0x58, -0x30, -0x30, -0x40, -8, -0x48, -0x38,
            -0x38, -0x20, -0x60, -0x40, -0x58, -0x20, -0x70, -8
    };
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x80);

    private int phase;
    private int x;
    private int y;
    private int timer;
    private int animTimer;
    private int mappingFrame;
    /** Draw_Sprite only runs once the object is {@code Obj_Explosion}'s {@code loc_1E66E}. */
    private boolean visible;

    public HpzKnucklesCeilingExplosionObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HpzKnucklesCeilingExplosion");
        x = spawn.x();
        y = spawn.y();
    }

    /**
     * {@code Obj_Explosion} created at routine 2 at {@code (x,y)} with {@code art_tile} bit 7 set;
     * its first update plays {@code sfx_Break}.
     */
    static HpzKnucklesCeilingExplosionObjectInstance normalExplosion(int x, int y) {
        var explosion = new HpzKnucklesCeilingExplosionObjectInstance(
                new ObjectSpawn(x, y, 0, 0, 0, false, 0));
        explosion.phase = 1;
        return explosion;
    }

    @Override
    public HpzKnucklesCeilingExplosionObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzKnucklesCeilingExplosionObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = false;
        switch (phase) {
            case 0 -> {
                int subtype = spawn.subtype() & 0xFF;
                var camera = services().camera();
                x = ((camera.getX() & 0xFFFF) + 0x140 + OFFSETS[subtype]) & 0xFFFF;
                y = ((camera.getY() & 0xFFFF) + 0xC0 + OFFSETS[subtype + 1]) & 0xFFFF;
                timer = subtype << 2;
                phase = 1;
            }
            case 1 -> {
                timer = (short) (timer - 1);
                if (timer >= 0) {
                    return;
                }
                // Obj_Explosion routine 2 (loc_1E61A) falls into loc_1E626 and loc_1E66E.
                services().playSfx(Sonic3kSfx.BREAK.id);
                animTimer = 3;
                mappingFrame = 0;
                phase = 2;
                if (!animate()) {
                    return;
                }
            }
            default -> {
                if (!animate()) {
                    return;
                }
            }
        }
        updateDynamicSpawn(x, y);
    }

    /** {@code loc_1E66E}; false once {@code Delete_Current_Sprite} ran. */
    private boolean animate() {
        animTimer = (animTimer - 1) & 0xFF;
        if ((byte) animTimer < 0) {
            animTimer = 7;
            mappingFrame++;
            if (mappingFrame == 5) {
                ObjectLifetimeOps.expireDynamic(this);
                return false;
            }
        }
        visible = true;
        return true;
    }

    /** {@code add.w d0,x_pos(a1)} / {@code add.w d0,y_pos(a1)} from {@code loc_83E90}. */
    void moveTo(int newX, int newY) {
        x = newX;
        y = newY;
        updateDynamicSpawn(x, y);
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.EXPLOSION);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, false, false);
        }
    }
}
