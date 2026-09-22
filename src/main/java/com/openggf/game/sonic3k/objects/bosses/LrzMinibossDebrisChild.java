package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * One of the eleven pieces the Lava Reef miniboss breaks into:
 * {@code ChildObjDat_78D9E} -> {@code loc_78A70} (sonic3k.asm:160487-160498), created by
 * {@code loc_787E0} once {@code Wait_FadeToLevelMusic} has counted out.
 *
 * <p>{@code CreateChild1_Normal} (sonic3k.asm:176924-176950) numbers the children
 * {@code 0, 2, ... $14} in {@code subtype} and places each at the parent's position plus the
 * signed byte pair in the table, so the eleven offsets are a fixed pattern around the drill, not
 * a random scatter.
 *
 * <p>{@code loc_78A70} then does three things with that subtype. It picks the mapping frame from
 * {@code RawAni_78A9C} with {@code lsr.w #1}, so the frames run
 * {@code $C $C $C $11 $11 $12 $13 $D $E $F $10} -- three pieces share frame {@code $C} and two
 * share {@code $11}. It calls {@code Set_IndexedVelocity} (sonic3k.asm:179163-179177) with
 * {@code d0 = $5C}, which is a <b>byte</b> offset into {@code Obj_VelocityIndex}: {@code $5C} is
 * entry 23, and {@code subtype * 2} steps one four-byte entry per piece, so the eleven pieces take
 * entries 23 to 33. And it installs {@code Obj_FlickerMove}.
 *
 * <p>{@code Obj_FlickerMove} (sonic3k.asm:178995-179010) is {@code MoveSprite} -- which applies
 * gravity, {@code addi.w #$38,y_vel}, unlike the shots' {@code MoveSprite2} -- then the same
 * coarse {@code $280}/{@code $200} cull the shots use, and then {@code bchg #6,$38(a0) / beq}:
 * the piece is drawn on every <b>other</b> frame, and because {@code bchg} sets the condition
 * from the bit's <i>old</i> value the first frame after creation is a skipped one.
 */
final class LrzMinibossDebrisChild extends AbstractBossChild implements RewindRecreatable {

    /** {@code word_78D7E}: priority {@code $80}, {@code $18 $14} size, frame {@code $C}, collision 0. */
    private static final int PRIORITY = 0x80;
    /** {@code RawAni_78A9C} (sonic3k.asm:160500-160501), indexed by {@code subtype >> 1}. */
    private static final int[] RAW_ANI_78A9C = {
        0x0C, 0x0C, 0x0C, 0x11, 0x11, 0x12, 0x13, 0x0D, 0x0E, 0x0F, 0x10,
    };
    /** {@code ChildObjDat_78D9E}'s eleven signed {@code (dx, dy)} byte pairs. */
    static final int[][] CHILD_OFFSETS = {
        {0, -0x0C}, {-0x19, -0x0C}, {0x19, -0x0C}, {-0x0C, 0x22}, {0x0C, 0x22}, {-8, 0x36},
        {8, 0x36}, {-0x12, 4}, {0x12, 4}, {-0x12, 0x0C}, {0x12, 0x0C},
    };
    /**
     * {@code Obj_VelocityIndex} entries 23 to 33 (sonic3k.asm:179203-179213), which is where
     * {@code Set_IndexedVelocity}'s {@code d0 = $5C} byte offset lands.
     */
    static final int[][] DEBRIS_VELOCITIES = {
        {0, -0x100}, {-0x100, -0x100}, {0x100, -0x100}, {-0x200, -0x100}, {0x200, -0x100},
        {-0x200, -0x200}, {0x200, -0x200}, {-0x300, -0x200}, {0x300, -0x200},
        {-0x300, -0x300}, {0x300, -0x300},
    };
    /** {@code MoveSprite}: {@code addi.w #$38,y_vel(a0)} every frame. */
    private static final int GRAVITY = 0x38;
    static final int DEBRIS_COUNT = 11;

    private int index;
    private int xFixed;
    private int yFixed;
    private int xVelocity;
    private int yVelocity;
    private int mappingFrame;
    /** {@code $38(a0)} bit 6, the {@code bchg} flicker. */
    private boolean flickerBit;
    private boolean drawnThisFrame;

    /** Restore construction uses the live concrete boss; snapshot fields restore the phase. */
    private LrzMinibossDebrisChild(LrzMinibossInstance parent) {
        this(parent, 0);
    }

    LrzMinibossDebrisChild(AbstractBossInstance parent, int index) {
        super(parent, "LRZMinibossDebris", PRIORITY, 0x9D);
        this.index = index;
        int[] offset = CHILD_OFFSETS[index];
        int originX = parent == null ? 0 : parent.getX();
        int originY = parent == null ? 0 : parent.getY();
        this.currentX = originX + offset[0];
        this.currentY = originY + offset[1];
        this.xFixed = currentX << 16;
        this.yFixed = currentY << 16;
        this.mappingFrame = RAW_ANI_78A9C[index];
        this.xVelocity = DEBRIS_VELOCITIES[index][0];
        this.yVelocity = DEBRIS_VELOCITIES[index][1];
        updateDynamicSpawn();
    }

    @Override
    public LrzMinibossDebrisChild recreateForRewind(RewindRecreateContext ctx) {
        return parent == null ? null : new LrzMinibossDebrisChild(parent, index);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!shouldUpdate(vIntRunCount)) {
            return;
        }
        // MoveSprite: the X step uses the current x_vel, the Y step uses the y_vel from BEFORE
        // gravity is added (the ROM reads y_vel into d0, then does addi.w #$38 to the SST, then
        // adds d0). Applying gravity first loses a frame of arc from every piece.
        xFixed += xVelocity << 8;
        int yVelocityBeforeGravity = yVelocity;
        yVelocity = (short) (yVelocity + GRAVITY);
        yFixed += yVelocityBeforeGravity << 8;
        currentX = xFixed >> 16;
        currentY = yFixed >> 16;
        if (!flickerMoveKeepsAlive()) {
            ObjectLifetimeOps.destroyBossChildLatched(this);
            return;
        }
        // bchg #6,$38(a0) / beq -> no draw: the test reads the bit BEFORE the change, so the
        // first frame (bit 0 -> 1) is skipped and the second (bit 1 -> 0) draws.
        boolean previous = flickerBit;
        flickerBit = !flickerBit;
        drawnThisFrame = previous;
        updateDynamicSpawn();
    }

    /** {@code Obj_FlickerMove}'s cull, the same coarse window {@code Sprite_CheckDeleteTouchXY} uses. */
    private boolean flickerMoveKeepsAlive() {
        var objectServices = tryServices();
        if (objectServices == null || objectServices.camera() == null) {
            return true;
        }
        int cameraX = Short.toUnsignedInt(objectServices.camera().getX());
        int cameraY = Short.toUnsignedInt(objectServices.camera().getY());
        int coarseBack = (cameraX - 0x80) & 0xFF80;
        if ((((currentX & 0xFF80) - coarseBack) & 0xFFFF) > 0x280) {
            return false;
        }
        return ((currentY - cameraY + 0x80) & 0xFFFF) <= 0x200;
    }

    int getIndex() {
        return index;
    }

    int getMappingFrame() {
        return mappingFrame;
    }

    boolean wasDrawnThisFrame() {
        return drawnThisFrame;
    }

    @Override public void syncPositionWithParent() { /* it has left the parent for good */ }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }
    /** The drill is gone by the time these exist, so they must outlive it. */
    @Override protected boolean destroyWhenParentDestroyed() { return false; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!drawnThisFrame) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
    }
}
