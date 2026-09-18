package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code loc_44BCC}/{@code loc_44BF8} (sonic3k.asm:90084-90118): one of the eight pieces
 * {@code Obj_SSZCollapsingColumn} breaks into.
 *
 * <p>Init: {@code render_flags $84}, {@code height_pixels 8}, {@code width_pixels 8},
 * {@code priority $200}, {@code make_art_tile(ArtTile_SSZMisc+$10,3,1)} over
 * {@code Map_SSZFloatingPlatform}, with the {@code word_46618} row's mapping frame.
 *
 * <p>{@code loc_44BF8} hangs the piece on the column — {@code y_pos = column y + $30(a0)} — while
 * its {@code $32(a0)} delay counts down, reporting back to the column with
 * {@code subq.b #1,routine(a1)} on the frame the delay reaches zero. After that it falls with a
 * {@code $3800} gravity accumulator in the {@code $34(a0)} longword. It deletes itself the first
 * frame {@code Draw_Sprite} leaves {@code render_flags} bit 7 clear, i.e. when it has left the
 * screen, and reports to the column then too if the delay never expired.
 */
public final class SszCollapsingColumnDebrisObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code move.w #$200,priority(a0)}. */
    private static final int PRIORITY_BUCKET = RenderPriority.fromS3kWord(0x200);
    /** {@code make_art_tile(ArtTile_SSZMisc+$10,3,1)}. */
    private static final int PALETTE_LINE = 3;
    /** {@code addi.l #$3800,$34(a0)}. */
    private static final int GRAVITY = 0x3800;

    private SszCollapsingColumnObjectInstance column;
    /** {@code $30(a0)}: the Y offset from the column while the piece still hangs. */
    private int hangOffsetY;
    /** {@code $32(a0)}: frames left before the piece lets go. */
    private int hangDelay;
    private int mappingFrame;
    /** {@code $34(a0)}: the 16.16 fall accumulator. */
    private int fallAccumulator;
    private boolean reported;
    private int x;
    private int y;

    private record RewindExtra(ObjectRefId columnId, int hangOffsetY, int hangDelay,
                               int mappingFrame, int fallAccumulator, boolean reported,
                               int x, int y)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszCollapsingColumnDebrisObjectInstance(ObjectSpawn spawn,
            SszCollapsingColumnObjectInstance column, int hangOffsetY, int hangDelay,
            int mappingFrame) {
        super(spawn, "SSZCollapsingColumnDebris");
        this.column = column;
        this.hangOffsetY = hangOffsetY;
        this.hangDelay = hangDelay;
        this.mappingFrame = mappingFrame;
        this.x = spawn.x();
        this.y = spawn.y();
    }

    /** Probe/rewind constructor: the spawn alone, with the row re-supplied on restore. */
    public SszCollapsingColumnDebrisObjectInstance(ObjectSpawn spawn) {
        this(spawn, null, 0, 0, 0);
    }

    @Override
    public SszCollapsingColumnDebrisObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SszCollapsingColumnDebrisObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // tst.b render_flags(a0) / bmi.s loc_44C14: bit 7 is Draw_Sprite's "was on screen" flag,
        // so a piece that has left the window deletes on its next pass.
        if (!isOnScreen()) {
            if (hangDelay > 0) {
                report();
            }
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        hangDelay--;
        if (hangDelay >= 0) {
            if (hangDelay == 0) {
                report();
            }
            if (column != null) {
                y = (column.getY() + hangOffsetY) & 0xFFFF;
            }
            return;
        }
        fallAccumulator += GRAVITY;
        y = (y + (short) (fallAccumulator >> 16)) & 0xFFFF;
    }

    /** {@code subq.b #1,routine(a1)}, once per piece. */
    private void report() {
        if (!reported && column != null) {
            reported = true;
            column.reportDebrisSettled();
        }
    }

    /** The {@code parent3(a0)} link, as an {@code ObjectRefId} sidecar restores it. */
    public SszCollapsingColumnObjectInstance columnForTest() { return column; }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return PRIORITY_BUCKET; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 8; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer =
                getRenderer(Sonic3kObjectArtKeys.SSZ_COLLAPSING_COLUMN);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, false, false, PALETTE_LINE);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId columnId = context.identityTable()
                .map(table -> table.encodeObject(column)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                columnId, hangOffsetY, hangDelay, mappingFrame, fallAccumulator, reported, x, y));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            hangOffsetY = extra.hangOffsetY();
            hangDelay = extra.hangDelay();
            mappingFrame = extra.mappingFrame();
            fallAccumulator = extra.fallAccumulator();
            reported = extra.reported();
            x = extra.x();
            y = extra.y();
            column = extra.columnId() == null ? null
                    : (SszCollapsingColumnObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.columnId(), true);
        }
    }
}
