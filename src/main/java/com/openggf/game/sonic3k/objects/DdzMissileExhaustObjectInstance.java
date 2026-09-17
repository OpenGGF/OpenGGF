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
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * ROM {@code loc_8214A} (sonic3k.asm:174252-174282): the Doomsday missile's exhaust flame. It sits
 * {@code byte_82AF6[$3D]} pixels behind its missile with mapping frame {@code $10 + $3D}, copies the
 * missile's priority ({@code Child_GetPriority}), emits a {@code loc_8218E} puff on every eighth
 * {@code V_int_run_count}, and is drawn only on the two V-ints of every four whose bit 1 is clear.
 */
final class DdzMissileExhaustObjectInstance extends AbstractDdzObjectInstance {
    /** {@code byte_82AF6}: exhaust (dx, dy) per direction. */
    private static final int[][] OFFSETS = {
            {0, -0x20}, {-0x16, -0x16}, {-0x20, 0}, {-0x16, 0x16},
            {0, 0x20}, {0x16, 0x16}, {0x20, 0}, {0x16, -0x16}};
    private static final int MISSILE_PALETTE = 2;

    private int x;
    private int y;
    private int mappingFrame = 0x12;
    private boolean visible;


    DdzMissileExhaustObjectInstance(DdzMissileObjectInstance missile) {
        super(new ObjectSpawn(missile == null ? 0 : missile.getX(), missile == null ? 0 : missile.getY(),
                0, 0, 0, false, 0), "DDZMissileExhaust", missile);
    }

    @Override
    public DdzMissileExhaustObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new DdzMissileExhaustObjectInstance(null);
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
        // Child_CheckParent / Child_Draw_Sprite: btst #7,status(parent) -> Go_Delete_Sprite.
        if (!(parent instanceof DdzMissileObjectInstance missile) || missile.exploding()) {
            goDelete();
            return;
        }
        // sub_82AD8
        int direction = missile.direction();
        mappingFrame = 0x10 + direction;
        // loc_82160: Child_GetPriority, then a puff every eighth V-int.
        int phase = vIntRunCount & 7;
        if (phase == 0) {
            int angle = missile.angleForExhaust();
            int px = x;
            int py = y;
            spawnChild(() -> new DdzMissilePuffObjectInstance(px, py,
                    -TrigLookupTable.sinHex(angle), -TrigLookupTable.cosHex(angle)));
        }
        if ((phase & 2) == 0) {
            // Refresh_ChildPosition, Child_Draw_Sprite
            x = (missile.getX() + OFFSETS[direction][0]) & 0xFFFF;
            y = (missile.getY() + OFFSETS[direction][1]) & 0xFFFF;
            visible = true;
        } else {
            // bclr #7,render_flags(a0) / Child_CheckParent
            visible = false;
        }
    }

    @Override
    public boolean isHighPriority() {
        return parent instanceof DdzMissileObjectInstance missile && missile.isHighPriority();
    }

    @Override
    public int getPriorityBucket() {
        return parent instanceof DdzMissileObjectInstance missile
                ? missile.getPriorityBucket() : RenderPriority.fromS3kWord(0x180);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable() || !visible) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame, x, y, false, false, MISSILE_PALETTE);
        }
    }


}
