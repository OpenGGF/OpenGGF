package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_81F36} (phase 1, {@code ObjDat3_831D8}: priority {@code $280}, frame 7, offsets
 * {@code word_81F5E}) and {@code loc_81F7E} (phase 2, {@code ObjDat3_831E4}: priority {@code $180},
 * frame 6, offsets {@code word_81F8C}) (sonic3k.asm:174037-174093). Both follow the boss by their
 * subtype's offset and are drawn only on V-ints whose bit 1 is clear; either deletes itself once the
 * boss's {@code status} bit 7 is set.
 */
final class DdzEndBossPartObjectInstance extends AbstractDdzObjectInstance {
    static final int KIND_FLICKER = 0;
    static final int KIND_REAR = 1;
    /** {@code word_81F5E}. */
    private static final int[][] FLICKER_OFFSETS = {{0x10, 0x38}, {0x10, 0x87}, {0x28, 0x60}};
    /** {@code word_81F8C}. */
    private static final int[][] REAR_OFFSETS = {{-0x20, 0x24}, {-0x10, 0x44}};
    private static final int PALETTE = 2;

    private int kind;
    private int subtype;
    private int x;
    private int y;
    private boolean visible;


    DdzEndBossPartObjectInstance(DdzEndBossObjectInstance boss, int kind, int subtype) {
        super(new ObjectSpawn(boss == null ? 0 : boss.getX(), boss == null ? 0 : boss.getY(), 0,
                (kind << 4) | subtype, 0, false, 0), "DDZEndBossPart", boss);
        this.kind = kind;
        this.subtype = subtype;
    }

    /** Rewind probe for {@code ObjectRewindDynamicCodecs}; mirrors {@link #recreateForRewind}. */
    private DdzEndBossPartObjectInstance(ObjectSpawn spawn) {
        this(null, spawn.subtype() >> 4, spawn.subtype() & 0xF);
    }

    @Override
    public DdzEndBossPartObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        int packed = ctx.spawn().subtype();
        return new DdzEndBossPartObjectInstance(null, packed >> 4, packed & 0xF);
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
        if (!(parent instanceof DdzEndBossObjectInstance boss) || boss.isDestroyed()) {
            goDelete();
            return;
        }
        int[] offset = (kind == KIND_FLICKER ? FLICKER_OFFSETS : REAR_OFFSETS)[subtype >> 1];
        x = (boss.getX() + offset[0]) & 0xFFFF;
        y = (boss.getY() + offset[1]) & 0xFFFF;
        // Child_Draw_Sprite / Child_CheckParent
        if (boss.destroyedStatus()) {
            goDelete();
            return;
        }
        visible = (vIntRunCount & 2) == 0;
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(kind == KIND_FLICKER ? 0x280 : 0x180);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable() || !visible) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(kind == KIND_FLICKER ? 7 : 6, x, y, false, false, PALETTE);
        }
    }


}
