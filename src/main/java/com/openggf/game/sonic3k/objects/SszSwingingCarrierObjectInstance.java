package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code Obj_SSZSwingingCarrier} ({@code $75}, sonic3k.asm:92023-92190): the hub of the arm
 * that carries a player around a pivot. Eight act-1 placements: five {@code $00}, one {@code $80}
 * and two {@code $82}.
 *
 * <p>Two subtype fields are read, and nothing else. Bit 7 picks the mode: clear is the pendulum,
 * which swings through a {@code Gradual_SwingOffset($20000,$821)} arc biased by {@code $41}; set
 * is the continuous rotator, which adds one to its angle every frame, or subtracts one when the
 * placement's X-flip bit is set. Bits 0-1 are read by the arc child as {@code (subtype & 3) + 6},
 * the number of segments in the arm — so {@code $82} is a rotator with eight segments and
 * {@code $80} a rotator with six.
 *
 * <p>Bit 7 also changes the hub's own art: {@code width_pixels $30} with mapping frame 1 for the
 * pendulum, {@code $8} with frame 4 for the rotator.
 *
 * <p>The hub itself is not solid and never touches a player. It owns the angle in {@code $20(a0)}
 * — the byte pair the SST calls {@code anim}/{@code anim_frame}, used here as a word — and the
 * {@link SszSwingingCarrierArcObjectInstance} it allocates reads it every frame. When the hub
 * culls on the coarse camera distance it kills the arc with {@code st routine(a1)} and clears bit
 * 7 of its respawn entry, so the whole three-object chain reloads from the placement.
 */
public final class SszSwingingCarrierObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    /** {@code move.w #$180,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x180);
    /** {@code make_art_tile(ArtTile_SSZMisc+$74,2,0)}. */
    private static final int PALETTE_LINE = 2;
    /** {@code moveq #$30,d0} / {@code moveq #1,d1}. */
    private static final int PENDULUM_WIDTH = 0x30;
    private static final int PENDULUM_FRAME = 1;
    /** {@code moveq #8,d0} / {@code moveq #4,d1} for a negative subtype. */
    private static final int ROTATOR_WIDTH = 0x08;
    private static final int ROTATOR_FRAME = 4;
    /** {@code move.l #$20000,d0} / {@code move.l #$821,d1}. */
    private static final int SWING_SPEED = 0x20000;
    private static final int SWING_ACCELERATION = 0x821;
    /** {@code addi.w #$41,d0}: the pendulum hangs about a quarter turn round. */
    public static final int PENDULUM_BIAS = 0x41;

    private final int x;
    private final int y;
    private final boolean rotator;
    private final int widthPixels;
    private final int mappingFrame;
    /** {@code $20(a0)}: the arm's angle, read by the arc child. */
    private int angle;
    /** {@code $2E}/{@code $32}/{@code $36}: the {@code Gradual_SwingOffset} longwords and flag. */
    private int swingSpeed;
    private int swingOffset;
    private boolean swingReversed;
    /** {@code $38(a0)}: the arc child's slot handle. */
    private SszSwingingCarrierArcObjectInstance arc;
    private boolean arcSpawned;

    private record RewindExtra(ObjectRefId arcId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszSwingingCarrierObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZSwingingCarrier");
        this.x = spawn.x();
        this.y = spawn.y();
        this.rotator = (spawn.subtype() & 0x80) != 0;
        this.widthPixels = rotator ? ROTATOR_WIDTH : PENDULUM_WIDTH;
        this.mappingFrame = rotator ? ROTATOR_FRAME : PENDULUM_FRAME;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        spawnArcOnce();
        coarseXCullViewport(x);
        if (isDestroyed()) {
            if (arc != null) {
                arc.killFromHub();
                arc = null;
            }
            return;
        }
        if (rotator) {
            // loc_46192: one step per frame, reversed by the placement's X-flip bit.
            angle = (angle + (isRenderFlipped() ? -1 : 1)) & 0xFFFF;
        } else {
            angle = (gradualSwingOffset() + PENDULUM_BIAS) & 0xFFFF;
        }
    }

    /** {@code jsr (AllocateObjectAfterCurrent)} in the init. */
    private void spawnArcOnce() {
        if (arcSpawned) {
            return;
        }
        arcSpawned = true;
        arc = spawnChild(() -> new SszSwingingCarrierArcObjectInstance(
                new ObjectSpawn(x, y, 0, getSpawn().subtype(), getSpawn().renderFlags(), false, 0),
                this));
    }

    /** {@code Gradual_SwingOffset} (sonic3k.asm:92484-92515); returns the offset's high word. */
    private int gradualSwingOffset() {
        int step = SWING_ACCELERATION;
        if (swingReversed) {
            step = -step;
            swingOffset += swingSpeed;
            if (swingOffset < 0) {
                swingSpeed -= step;
            } else {
                swingSpeed = SWING_SPEED;
                swingOffset = 0;
                swingReversed = false;
            }
        } else {
            swingOffset += swingSpeed;
            if (swingOffset > 0) {
                swingSpeed -= step;
            } else {
                swingSpeed = -SWING_SPEED;
                swingOffset = 0;
                swingReversed = true;
            }
        }
        return (short) (swingOffset >> 16);
    }

    /** {@code $20(a0)}, the value the arc reads. */
    public int angleWord() { return angle; }

    public boolean isRotator() { return rotator; }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return widthPixels; }
    @Override public int getOnScreenHalfHeight() { return 8; }

    public SszSwingingCarrierArcObjectInstance arcForTest() { return arc; }
    int mappingFrameForTest() { return mappingFrame; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_ELEVATOR_BAR);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, isRenderFlipped(), false, PALETTE_LINE);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId arcId = context.identityTable()
                .map(table -> table.encodeObject(arc)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(arcId));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            arc = extra.arcId() == null ? null
                    : (SszSwingingCarrierArcObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.arcId(), true);
        }
    }

    /** {@code btst #0,status(a0)}: the placement's X-flip bit. */
    private boolean isRenderFlipped() {
        return (getSpawn().renderFlags() & 1) != 0;
    }

}
