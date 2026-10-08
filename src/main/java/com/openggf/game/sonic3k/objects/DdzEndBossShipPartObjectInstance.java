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
 * ROM {@code loc_81D72} / {@code loc_81DCC} (sonic3k.asm:173860-173928): the three phase-2 ship
 * parts ({@code ObjDat3_8320E}: priority {@code $200}). Subtype 0 (frame {@code $3A}, animated by
 * {@code byte_832D0}, offset {@code $64,$4C}) carries the Master Emerald {@code loc_81CC6}; subtype 2
 * is frame 5 at {@code $10,$20}; subtype 4 is frame {@code $3D} animated by {@code byte_832D9} at
 * {@code $1C,$8C}. When the boss's {@code $38} bit 4 is set (the exit), each part sets its
 * {@code status} bit 7, takes {@code word_81E1C} velocity, gets an {@code Obj_CreateBossExplosion}
 * subtype 4 and becomes {@code Obj_FlickerMove} following the wrap and camera delta.
 */
final class DdzEndBossShipPartObjectInstance extends AbstractDdzObjectInstance
        implements DdzCreateBossExplosionObjectInstance.ExplosionStopFlag {
    /** {@code word_81DB4}. */
    private static final int[][] OFFSETS = {{0x64, 0x4C}, {0x10, 0x20}, {0x1C, 0x8C}};
    /** {@code byte_81DB0}. */
    private static final int[] FRAMES = {0x3A, 5, 0x3D};
    /** {@code word_81E1C}. */
    private static final int[][] BREAK_VELOCITIES = {{0x80, -0x200}, {-0x100, -0x100}, {-0x200, -0x300}};
    private static final int PALETTE = 2;

    private int subtype;
    private int xPos;
    private int yPos;
    private short xVel;
    private short yVel;
    private boolean broken;
    private boolean flickerVisible;
    private boolean initialized;
    private final S3kRawAnimation.State animation = new S3kRawAnimation.State();


    DdzEndBossShipPartObjectInstance(DdzEndBossObjectInstance boss, int subtype) {
        this(new ObjectSpawn(boss == null ? 0 : boss.getX(), boss == null ? 0 : boss.getY(), 0, subtype, 0, false, 0), boss, subtype);
    }

    private DdzEndBossShipPartObjectInstance(ObjectSpawn spawn, DdzEndBossObjectInstance boss, int subtype) {
        super(spawn, "DDZEndBossShipPart", boss);
        this.subtype = subtype;
        if (boss != null) {
            xPos = (boss.getX() & 0xFFFF) << 16;
            yPos = (boss.getY() & 0xFFFF) << 16;
        }
        int script = subtype == 0 ? Sonic3kConstants.DDZ_ANIM_SHIP_PART_0_ADDR
                : subtype == 4 ? Sonic3kConstants.DDZ_ANIM_SHIP_PART_2_ADDR : 0;
        S3kRawAnimation.set(animation, script);
        animation.mappingFrame = FRAMES[subtype >> 1];
    }

    /** Rewind probe for {@code ObjectRewindDynamicCodecs}; mirrors {@link #recreateForRewind}. */
    private DdzEndBossShipPartObjectInstance(ObjectSpawn spawn) {
        this(spawn, null, spawn.subtype());
    }

    @Override
    public DdzEndBossShipPartObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        // The parent link is restored later; retain the captured spawn instead of
        // deriving a new origin from the temporarily absent parent.
        return new DdzEndBossShipPartObjectInstance(ctx.spawn());
    }

    @Override
    public int getX() {
        return (xPos >>> 16) & 0xFFFF;
    }

    @Override
    public int getY() {
        return (yPos >>> 16) & 0xFFFF;
    }

    int mappingFrame() {
        return animation.mappingFrame;
    }

    DdzEndBossObjectInstance boss() {
        return parent instanceof DdzEndBossObjectInstance boss ? boss : null;
    }

    @Override
    public boolean stopsChildExplosions() {
        return false;
    }

    @Override
    protected void updateObject(int vIntRunCount, PlayableEntity player) {
        if (broken) {
            flickerMove();
            return;
        }
        DdzEndBossObjectInstance boss = boss();
        if (boss == null || boss.isDestroyed()) {
            goDelete();
            return;
        }
        if (!initialized) {
            initialized = true;
            if (subtype == 0) {
                spawnChild(() -> new DdzBossMasterEmeraldObjectInstance(this));
            }
            return;
        }
        if (animation.script != 0) {
            DdzObjectSupport.rawAnimations(services()).animateMultiDelay(animation, () -> { });
        }
        int[] offset = OFFSETS[subtype >> 1];
        xPos = ((boss.getX() + offset[0]) & 0xFFFF) << 16 | (xPos & 0xFFFF);
        yPos = ((boss.getY() + offset[1]) & 0xFFFF) << 16 | (yPos & 0xFFFF);
        if (boss.flag(4)) {
            broken = true;
            int[] velocity = BREAK_VELOCITIES[subtype >> 1];
            xVel = (short) velocity[0];
            yVel = (short) velocity[1];
            int x = getX();
            int y = getY();
            spawnChild(() -> new DdzCreateBossExplosionObjectInstance(x, y, 4, this));
        }
    }

    /** {@code loc_81E28} and {@code Obj_FlickerMove}. */
    private void flickerMove() {
        xPos -= DdzObjectSupport.wrapOffset(services()) << 16;
        xPos += DdzObjectSupport.cameraDelta(services()) << 16;
        xPos += xVel << 8;
        yPos += yVel << 8;
        yVel = (short) (yVel + 0x38);
        if (outOfRangeX(getX()) || DdzObjectSupport.outOfRangeY(services(), getY())) {
            goDelete();
            return;
        }
        flickerVisible = !flickerVisible;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x200);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawable() || !initialized || (broken && flickerVisible)) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DDZ_MISC);
        if (renderer != null) {
            renderer.drawFrameIndex(animation.mappingFrame, getX(), getY(), false, false, PALETTE);
        }
    }


}
