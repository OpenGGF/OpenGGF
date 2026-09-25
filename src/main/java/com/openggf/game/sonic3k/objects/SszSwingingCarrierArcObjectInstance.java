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
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * ROM {@code loc_461A8}-{@code loc_46282} (sonic3k.asm:92110-92180): the arm itself, drawn as a
 * chain of {@code mainspr_childsprites} sub-sprites stepping away from the hub.
 *
 * <p>Init: {@code render_flags $44} (the multi-sprite bit), {@code height_pixels $68},
 * {@code width_pixels $68}, {@code make_art_tile(ArtTile_SSZMisc+$74,3,0)} over
 * {@code Map_SSZElevatorBar}, and {@code mainspr_childsprites = (subtype & 3) + 6}. It then
 * allocates the {@link SszSwingingCarrierBarObjectInstance} the player actually stands on.
 *
 * <p>Each frame it takes the hub's {@code $20} angle word, calls {@code GetSineCosine} and turns
 * the results into 16.16 steps with {@code swap / clr.w / asr.l #4} — cosine into X and sine into
 * Y, each divided by sixteen, so a full-scale {@code $100} table entry is a sixteen-pixel step.
 * The accumulator starts at the arc's own position and one segment is written per step, all at
 * mapping frame 2. The last segment is where the rider bar sits.
 *
 * <p>Priority follows the hub angle: {@code ((angle - $40) & $FF)} below {@code $80} draws the arm
 * behind at {@code $180}, at or above draws it in front at {@code $100}. Biasing by {@code $40}
 * before the halving is what makes the swap happen at the horizontal, not the vertical.
 */
public final class SszSwingingCarrierArcObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    /** {@code make_art_tile(ArtTile_SSZMisc+$74,3,0)}. */
    private static final int PALETTE_LINE = 3;
    /** {@code move.b #2,1(a1)} for every segment. */
    private static final int SEGMENT_FRAME = 2;
    /** {@code addq.w #6,d0} over {@code subtype & 3}. */
    public static final int BASE_SEGMENTS = 6;
    /** {@code subi.w #$40,d0} / {@code cmpi.w #$80,d0}. */
    private static final int PRIORITY_BIAS = 0x40;
    private static final int PRIORITY_BEHIND = 0x180;
    private static final int PRIORITY_IN_FRONT = 0x100;

    private final int baseX;
    private final int baseY;
    private final int segmentCount;
    /** {@code sub2_x_pos}/{@code sub2_y_pos}: one pair per segment. */
    private final int[] segmentX;
    private final int[] segmentY;
    private SszSwingingCarrierObjectInstance hub;
    private SszSwingingCarrierBarObjectInstance bar;
    private boolean barSpawned;
    private boolean killed;
    private int priorityWord = PRIORITY_BEHIND;

    private record RewindExtra(ObjectRefId hubId, ObjectRefId barId, boolean killed,
                               boolean barSpawned, int priorityWord,
                               int[] segmentX, int[] segmentY)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public SszSwingingCarrierArcObjectInstance(ObjectSpawn spawn,
            SszSwingingCarrierObjectInstance hub) {
        super(spawn, "SSZSwingingCarrierArc");
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.hub = hub;
        this.segmentCount = BASE_SEGMENTS + (spawn.subtype() & 3);
        this.segmentX = new int[segmentCount];
        this.segmentY = new int[segmentCount];
        for (int index = 0; index < segmentCount; index++) {
            segmentX[index] = baseX;
            segmentY[index] = baseY;
        }
    }

    /** Probe/rewind constructor. */
    public SszSwingingCarrierArcObjectInstance(ObjectSpawn spawn) {
        this(spawn, null);
    }

    @Override
    public SszSwingingCarrierArcObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new SszSwingingCarrierArcObjectInstance(ctx.spawn());
    }

    /** {@code st routine(a1)} from {@code loc_46168}. */
    void killFromHub() {
        killed = true;
    }

    // loc_461FE/loc_46210 delete only after the hub signals routine=$FF.
    // The arc has no independent range test; manager culling would break the live graph.
    @Override public boolean isPersistent() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (tryServices() == null) {
            return;
        }
        spawnBarOnce();
        if (killed) {
            // loc_461FE: the arc kills the rider bar in turn before it goes.
            if (bar != null) {
                bar.killFromArc();
                bar = null;
            }
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        int angle = hub == null ? 0 : hub.angleWord();
        int folded = (angle - PRIORITY_BIAS) & 0xFF;
        priorityWord = folded < 0x80 ? PRIORITY_BEHIND : PRIORITY_IN_FRONT;
        // swap / clr.w / asr.l #4 on each of GetSineCosine's outputs: d1 is the cosine and steps
        // X, d0 is the sine and steps Y.
        int stepX = TrigLookupTable.cosHex(angle & 0xFF) << 12;
        int stepY = TrigLookupTable.sinHex(angle & 0xFF) << 12;
        int accumulatorX = baseX << 16;
        // move.l y_pos(a0),d2 / clr.w d2: the Y accumulator starts with its fraction cleared.
        int accumulatorY = baseY << 16;
        for (int index = 0; index < segmentCount; index++) {
            accumulatorX += stepX;
            accumulatorY += stepY;
            segmentX[index] = (accumulatorX >> 16) & 0xFFFF;
            segmentY[index] = (accumulatorY >> 16) & 0xFFFF;
        }
    }

    private void spawnBarOnce() {
        if (barSpawned) {
            return;
        }
        barSpawned = true;
        bar = spawnChild(() -> new SszSwingingCarrierBarObjectInstance(
                new ObjectSpawn(baseX, baseY, 0, getSpawn().subtype(), getSpawn().renderFlags(),
                        false, 0), this));
    }

    /** The last segment, which the rider bar sits on. */
    public int tipX() { return segmentX[segmentCount - 1]; }

    public int tipY() { return segmentY[segmentCount - 1]; }

    public int segmentCount() { return segmentCount; }

    public int segmentXForTest(int index) { return segmentX[index]; }

    public int segmentYForTest(int index) { return segmentY[index]; }

    @Override public int getX() { return baseX; }
    @Override public int getY() { return baseY; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(priorityWord); }
    @Override public int getOnScreenHalfWidth() { return 0x68; }
    @Override public int getOnScreenHalfHeight() { return 0x68; }

    public int priorityWordForTest() { return priorityWord; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.SSZ_ELEVATOR_BAR);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        for (int index = 0; index < segmentCount; index++) {
            renderer.drawFrameIndex(SEGMENT_FRAME, segmentX[index], segmentY[index],
                    false, false, PALETTE_LINE);
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId hubId = context.identityTable()
                .map(table -> table.encodeObject(hub)).orElse(null);
        ObjectRefId barId = context.identityTable()
                .map(table -> table.encodeObject(bar)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                hubId, barId, killed, barSpawned, priorityWord,
                segmentX.clone(), segmentY.clone()));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            killed = extra.killed();
            barSpawned = extra.barSpawned();
            priorityWord = extra.priorityWord();
            System.arraycopy(extra.segmentX(), 0, segmentX, 0,
                    Math.min(extra.segmentX().length, segmentX.length));
            System.arraycopy(extra.segmentY(), 0, segmentY, 0,
                    Math.min(extra.segmentY().length, segmentY.length));
            hub = extra.hubId() == null ? null
                    : (SszSwingingCarrierObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.hubId(), true);
            bar = extra.barId() == null ? null
                    : (SszSwingingCarrierBarObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.barId(), true);
        }
    }
}
