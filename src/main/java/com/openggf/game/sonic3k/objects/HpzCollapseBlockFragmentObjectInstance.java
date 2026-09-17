package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_65602} ({@code ChildObjDat_6664A}, 32 copies): a {@code Map_LRZ3Platform} piece
 * of the broken {@code loc_655B2} block. {@code loc_662C0} offsets it by {@code byte_66308},
 * picks frame {@code 7 + subtype/2} and throws it left with speed growing with the subtype;
 * {@code loc_6561C} applies {@code MoveSprite_LightGravity} while the sprite is on screen and
 * deletes it once it is not.
 */
public final class HpzCollapseBlockFragmentObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code byte_66308}. */
    private static final int[] OFFSETS = {
            8, -8, 8, 0, 8, -0x10, 8, 8, 8, -0x18, 8, 0x10, 8, -0x20, 8, 0x18,
            0, -8, 0, 0, 0, -0x10, 0, 8, 0, -0x18, 0, 0x10, 0, -0x20, 0, 0x18,
            -8, -8, -8, 0, -8, -0x10, -8, 8, -8, -0x18, -8, 0x10, -8, -0x20, -8, 0x18,
            -0x10, -8, -0x10, 0, -0x10, -0x10, -0x10, 8, -0x10, -0x18, -0x10, 0x10,
            -0x10, -0x20, -0x10, 0x18
    };

    private boolean initialized;
    private int x;
    private int y;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;
    private int mappingFrame;
    private boolean firstPass = true;

    public HpzCollapseBlockFragmentObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HpzCollapseBlockFragment");
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public HpzCollapseBlockFragmentObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HpzCollapseBlockFragmentObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) {
            initialized = true;
            // loc_662C0
            int d0 = spawn.subtype() & 0xFF;
            int dx = OFFSETS[d0];
            int dy = OFFSETS[d0 + 1];
            x = (x + dx) & 0xFFFF;
            y = (y + dy) & 0xFFFF;
            mappingFrame = 7 + (d0 >> 1);
            int d3 = -(d0 << 4);
            xVel = (short) (-0x200 + d3);
            d3 = ((d3 << 1) & 0x180) + 0x80;
            yVel = dy < 0 ? -d3 : d3;
            updateDynamicSpawn(x, y);
            return;
        }
        // loc_6561C: tst.b render_flags(a0) / bpl.s -- bit 7 was forced on by loc_65602.
        if (!firstPass && !isOnScreen()) {
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        firstPass = false;
        SubpixelMotion.State s = new SubpixelMotion.State(x, y, xSub, ySub, xVel, yVel);
        SubpixelMotion.moveSprite(s, 0x20);
        x = s.x & 0xFFFF;
        y = s.y & 0xFFFF;
        xSub = s.xSub;
        ySub = s.ySub;
        xVel = (short) s.xVel;
        yVel = (short) s.yVel;
        updateDynamicSpawn(x, y);
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public boolean isHighPriority() { return true; }
    @Override public int getPriorityBucket() { return 0; }
    @Override public int getOnScreenHalfWidth() { return 4; }
    @Override public int getOnScreenHalfHeight() { return 4; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_COLLAPSE_BLOCK);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, false, false);
        }
    }
}
