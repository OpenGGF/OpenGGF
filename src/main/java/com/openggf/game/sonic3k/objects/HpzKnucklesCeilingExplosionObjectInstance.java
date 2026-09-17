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
 * {@code Map_Explosion} frames 0-4 with the high-priority bit set.
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

    @Override
    public HpzKnucklesCeilingExplosionObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzKnucklesCeilingExplosionObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        visible = phase == 2;
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
                // Obj_Explosion routine 2 (loc_1E61A) falls into loc_1E626.
                services().playSfx(Sonic3kSfx.BREAK.id);
                animTimer = 3;
                mappingFrame = 0;
                phase = 2;
            }
            default -> {
                // loc_1E66E
                animTimer = (animTimer - 1) & 0xFF;
                if ((byte) animTimer >= 0) {
                    break;
                }
                animTimer = 7;
                mappingFrame++;
                if (mappingFrame == 5) {
                    ObjectLifetimeOps.expireDynamic(this);
                    return;
                }
            }
        }
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
