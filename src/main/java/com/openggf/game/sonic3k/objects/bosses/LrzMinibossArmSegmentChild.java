package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * The Lava Reef miniboss's arm-segment child: subtype {@code 0} of each ring
 * ({@code loc_78838}, sonic3k.asm:160288-160345).
 *
 * <p>It is the anchor the ten links chain off, and it does not hang from the boss. Its position
 * is set <b>once</b>, by {@code sub_78BEE} at {@code loc_7885A} -- routine 0, which runs on the
 * creation frame and never again: {@code Camera_X + $20}, or {@code + $120} on the mirrored ring,
 * and {@code Camera_Y + $1B8}. It is a camera-relative <i>placement</i>, not a camera <i>lock</i>:
 * once placed, the only thing that ever moves the segment again is its own {@code MoveSprite2} at
 * {@code loc_78890}. The arena camera does not travel during the fight, so in practice the two
 * arms rise from the bottom corners of the screen, but nothing re-reads the camera to keep them
 * there.
 *
 * <p>{@code off_78850} is five routines. The segment tracks the parent's {@code $38} bit 3: when
 * the boss extends ({@code loc_78868}) it rises at {@code -$200} for {@code $6F} frames, and when
 * the boss retracts ({@code loc_788A4}) it falls back at {@code $200} and then sets the parent's
 * bit 2 ({@code loc_788CC}), which is the hand's cue to stop firing.
 */
final class LrzMinibossArmSegmentChild extends AbstractBossChild implements RewindRecreatable, LrzMinibossRingChild {

    /** {@code word_78D66}: priority 0, {@code $08 $08} size, mapping frame 8, collision 0. */
    private static final int MAPPING_FRAME = 8;
    private static final int PRIORITY_BUCKET = 0;
    /** {@code sub_78BEE} (sonic3k.asm:160617-160626). */
    private static final int CAMERA_X_OFFSET = 0x20;
    private static final int CAMERA_X_OFFSET_MIRRORED = 0x120;
    private static final int CAMERA_Y_OFFSET = 0x1B8;
    private static final int EXTEND_VELOCITY = -0x200;   // loc_78868
    private static final int RETRACT_VELOCITY = 0x200;   // loc_788A4
    private static final int MOVE_TIMER = 0x6F;          // both branches

    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_AWAIT_EXTEND = 2;
    private static final int ROUTINE_EXTENDING = 4;
    private static final int ROUTINE_AWAIT_RETRACT = 6;
    private static final int ROUTINE_RETRACTING = 8;

    private static final int CONTINUATION_7889C = 1;
    private static final int CONTINUATION_788CC = 2;

    private boolean mirrored;
    private int childSubtype;
    private int routine = ROUTINE_INIT;
    private int continuation;
    private int waitTimer;
    private int yVelocity;
    private int xFixed;
    private int yFixed;

    LrzMinibossArmSegmentChild(AbstractBossInstance parent, int childSubtype, boolean mirrored) {
        super(parent, "LRZMinibossArmSegment", PRIORITY_BUCKET, 0x9D);
        this.mirrored = mirrored;
        this.childSubtype = childSubtype;
        // loc_787FE gives the mirrored ring $2E = $10 before loc_7880A adds subtype * 2. The
        // arm segment is the one child that never waits on it -- loc_78838's routine table runs
        // straight away, where the links and the hand go through sub_78BD6's Wait_Draw first --
        // but the SST value is this, and awaitExtend() overwrites it before anything reads it.
        this.waitTimer = (mirrored ? 0x10 : 0) + childSubtype * 2;
        this.xFixed = currentX << 16;
        this.yFixed = currentY << 16;
    }

    @Override
    public LrzMinibossArmSegmentChild recreateForRewind(RewindRecreateContext ctx) {
        return parent == null ? null
                : new LrzMinibossArmSegmentChild(parent, childSubtype, mirrored);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!shouldUpdate(vIntRunCount)) {
            return;
        }
        switch (routine) {
            case ROUTINE_INIT -> {
                anchorToCamera();
                routine = ROUTINE_AWAIT_EXTEND;
                awaitExtend();
            }
            case ROUTINE_AWAIT_EXTEND -> awaitExtend();
            case ROUTINE_EXTENDING, ROUTINE_RETRACTING -> moveAndWait();
            case ROUTINE_AWAIT_RETRACT -> awaitRetract();
            default -> { }
        }
        updateDynamicSpawn();
    }

    /** {@code sub_78BEE} (sonic3k.asm:160617-160626), run once from routine 0's {@code loc_7885A}. */
    private void anchorToCamera() {
        var services = tryServices();
        if (services == null || services.camera() == null) {
            return;
        }
        int cameraX = Short.toUnsignedInt(services.camera().getX());
        int cameraY = Short.toUnsignedInt(services.camera().getY());
        currentX = cameraX + (mirrored ? CAMERA_X_OFFSET_MIRRORED : CAMERA_X_OFFSET);
        currentY = cameraY + CAMERA_Y_OFFSET;
        xFixed = currentX << 16;
        yFixed = currentY << 16;
    }

    /** {@code loc_78868}: the boss extending its arms starts the rise. */
    private void awaitExtend() {
        if (!bossArmsExtended()) {
            return;
        }
        routine = ROUTINE_EXTENDING;
        yVelocity = EXTEND_VELOCITY;
        waitTimer = MOVE_TIMER;
        continuation = CONTINUATION_7889C;
    }

    /** {@code loc_788A4}: the boss retracting starts the fall back. */
    private void awaitRetract() {
        if (bossArmsExtended()) {
            return;
        }
        routine = ROUTINE_RETRACTING;
        yVelocity = RETRACT_VELOCITY;
        waitTimer = MOVE_TIMER;
        continuation = CONTINUATION_788CC;
    }

    /** {@code loc_78890}: {@code MoveSprite2} then {@code Obj_Wait}. */
    private void moveAndWait() {
        yFixed += yVelocity << 8;
        currentY = yFixed >> 16;
        waitTimer--;
        if (waitTimer >= 0) {
            return;
        }
        if (continuation == CONTINUATION_7889C) {
            // loc_7889C
            routine = ROUTINE_AWAIT_RETRACT;
        } else if (continuation == CONTINUATION_788CC) {
            // loc_788CC: bset #2,$38(a1) - the hand's reload cue.
            routine = ROUTINE_AWAIT_EXTEND;
            if (parent instanceof LrzMinibossInstance boss) {
                boss.setHandReloadFlag();
            }
        }
        continuation = 0;
    }

    private boolean bossArmsExtended() {
        return parent instanceof LrzMinibossInstance boss && (boss.getFlags38() & (1 << 3)) != 0;
    }

    @Override public int ringSubtype() { return childSubtype; }
    @Override public boolean ringMirrored() { return mirrored; }
    @Override public int ringXFixed() { return xFixed; }
    @Override public int ringYFixed() { return yFixed; }

    boolean isMirrored() {
        return mirrored;
    }

    int getRoutine() {
        return routine;
    }

    @Override public void syncPositionWithParent() { /* camera-anchored: sub_78BEE */ }
    @Override public boolean isPersistent() { return true; }
    @Override public boolean isHighPriority() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.LRZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(MAPPING_FRAME, currentX, currentY, mirrored, false);
    }

    @Override
    public ObjectSpawn getSpawn() {
        return super.getSpawn();
    }
}
