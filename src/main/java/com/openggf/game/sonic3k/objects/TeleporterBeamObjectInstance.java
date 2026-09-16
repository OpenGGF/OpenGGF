package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ROM {@code Obj_TeleporterBeam} (sonic3k.asm:91244-91404) for the HPZ teleporters.
 *
 * <p>The multi-sprite beam first stacks up to seven segments ({@code Obj_TeleporterBeamSpawn}),
 * waits 16 frames ({@code Obj_TeleporterBeamWait}), then spreads two columns apart
 * ({@code Obj_TeleporterBeamExpand}) using {@code word_46734}. It only updates and draws on
 * odd {@code Level_frame_counter} low bytes, which produces the beam's flicker.
 *
 * <p>{@code $46(a0)} is the progress byte the parent teleporter reads: it toggles 0/$FF while
 * segments are added, then counts up to {@code $18} every four frames while expanding.
 * The parent sets {@code routine} to contract the beam; on underflow the beam deletes itself
 * and clears the parent's {@code $38} flag.
 */
public final class TeleporterBeamObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    private static final int PHASE_SPAWN = 0;
    private static final int PHASE_WAIT = 1;
    private static final int PHASE_EXPAND = 2;
    private static final int PALETTE_LINE = 3;
    private static final int SEGMENT_SPACING = 0x20;
    private static final int MAX_SEGMENTS = 7;
    private static final int MAX_PROGRESS = 0x18;
    /** {@code word_46734}: per progress step, left y offset, left frame, right y offset, right frame. */
    private static final int[][] EXPAND_TABLE = {
            {-6, 3, -6, 4}, {-6, 3, -6, 4}, {-5, 3, -5, 4}, {-4, 3, -4, 6}, {-2, 3, -2, 6},
            {0, 3, 0, 6}, {0, 3, 0, 6}, {0, 5, 0, 6}, {0, 5, 0, 6}, {0, 5, 0, 6},
            {0, 5, 0, 6}, {0, 5, 0, 8}, {0, 5, 0, 8}, {0, 5, 0, 8}, {0, 5, 0, 8},
            {0, 7, 0, 8}, {0, 7, 0, 8}, {0, 7, 0, 8}, {0, 7, 0, 8}, {0, 7, 0, 8},
            {0, 7, 0, 8}, {0, 7, 0, 8}, {0, 7, 0, 8}, {0, 7, 0, 8}, {0, 7, 0, 8}
    };

    private SSZHPZTeleporterObjectInstance parent;
    private final int x;
    private final int spawnY;
    private int y;
    private final int expandBaseY;
    private int phase;
    /** {@code $46(a0)} as a signed byte. */
    private int progress;
    /** {@code $47(a0)}. */
    private int timer = 0x10;
    /** {@code mainspr_childsprites(a0)}. */
    private int segmentCount = 1;
    private boolean contracting;
    private boolean visibleThisFrame;
    private final int[] childX = new int[MAX_SEGMENTS];
    private final int[] childY = new int[MAX_SEGMENTS];
    private final int[] childFrame = new int[MAX_SEGMENTS];

    private record RewindExtra(int y, int phase, int progress, int timer, int segmentCount,
                               boolean contracting, boolean visibleThisFrame,
                               int[] childX, int[] childY, int[] childFrame, ObjectRefId parentId)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public TeleporterBeamObjectInstance(ObjectSpawn spawn, SSZHPZTeleporterObjectInstance parent) {
        super(spawn, "TeleporterBeam");
        this.parent = parent;
        this.x = spawn.x();
        this.spawnY = spawn.y();
        this.y = spawn.y();
        // move.w y_pos(a0),$44(a0) / subi.w #$88,$44(a0)
        this.expandBaseY = spawn.y() - 0x88;
    }

    @Override
    public TeleporterBeamObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new TeleporterBeamObjectInstance(ctx.spawn(), null);
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parent)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                y, phase, progress, timer, segmentCount, contracting, visibleThisFrame,
                childX.clone(), childY.clone(), childFrame.clone(), parentId));
    }

    @Override
    public void restoreRewindState(PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            y = extra.y();
            phase = extra.phase();
            progress = extra.progress();
            timer = extra.timer();
            segmentCount = extra.segmentCount();
            contracting = extra.contracting();
            visibleThisFrame = extra.visibleThisFrame();
            System.arraycopy(extra.childX(), 0, childX, 0, MAX_SEGMENTS);
            System.arraycopy(extra.childY(), 0, childY, 0, MAX_SEGMENTS);
            System.arraycopy(extra.childFrame(), 0, childFrame, 0, MAX_SEGMENTS);
            if (extra.parentId() != null) {
                parent = (SSZHPZTeleporterObjectInstance) context.requireIdentityTable()
                        .resolveObject(extra.parentId(), true);
            }
        }
    }

    /** Signed {@code $46(a0)}, read by the teleporter's transport routine. */
    public int progress() {
        return progress;
    }

    /** {@code st routine(a1)} from the parent: contract and delete. */
    public void startContracting() {
        contracting = true;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        int frameCounter = services().levelManager().getFrameCounter();
        visibleThisFrame = false;
        switch (phase) {
            case PHASE_SPAWN -> updateSpawn(frameCounter);
            case PHASE_WAIT -> updateWait(frameCounter);
            default -> updateExpand(frameCounter);
        }
    }

    private void updateSpawn(int frameCounter) {
        if (timer != 0) {
            timer--;
            return;
        }
        if ((frameCounter & 1) == 0) {
            return;
        }
        int segmentY = y;
        int listed = segmentCount;
        for (int index = 0; index < listed; index++) {
            childX[index] = x;
            childY[index] = segmentY;
            if (index == listed - 1) {
                // eori.b #-1,$46(a0): frame 1 on the first toggle, frame 2 plus a new sprite on the next.
                progress = progress == 0 ? -1 : 0;
                if (progress != 0) {
                    childFrame[index] = 1;
                } else {
                    childFrame[index] = 2;
                    segmentCount++;
                }
            } else {
                childFrame[index] = 2;
                segmentY -= SEGMENT_SPACING;
            }
        }
        if (segmentCount >= MAX_SEGMENTS && progress == 0) {
            segmentCount = MAX_SEGMENTS;
            phase = PHASE_WAIT;
            timer = 0x10;
        }
        visibleThisFrame = true;
    }

    private void updateWait(int frameCounter) {
        timer--;
        if (timer != 0) {
            visibleThisFrame = (frameCounter & 1) != 0;
            return;
        }
        phase = PHASE_EXPAND;
        segmentCount = 2;
        updateExpand(frameCounter);
    }

    private void updateExpand(int frameCounter) {
        if ((frameCounter & 1) == 0) {
            return;
        }
        int cameraY = services().camera().getY() & 0xFFFF;
        int baseY = expandBaseY;
        if ((short) (baseY - cameraY) > 0x68) {
            baseY = cameraY + 0x68;
        }
        y = baseY;
        int spread = Math.min(progress, 0x12) + 6;
        int[] row = EXPAND_TABLE[Math.max(0, Math.min(progress, MAX_PROGRESS))];
        childX[0] = x - spread;
        childY[0] = baseY + row[0];
        childFrame[0] = row[1];
        childX[1] = x + spread;
        childY[1] = baseY + row[2];
        childFrame[1] = row[3];
        if (contracting) {
            progress--;
            if (progress < 0) {
                segmentCount = 0;
                if (parent != null) {
                    parent.onBeamFinished();
                }
                setDestroyed(true);
                return;
            }
        } else if ((frameCounter & 2) != 0 && progress < MAX_PROGRESS) {
            progress++;
        }
        visibleThisFrame = true;
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getOutOfRangeReferenceX() { return x; }
    @Override public boolean isPersistent() { return true; }
    /** {@code priority = $80}. */
    @Override public int getPriorityBucket() { return 1; }

    int spawnYForTest() { return spawnY; }
    int segmentCountForTest() { return segmentCount; }
    int phaseForTest() { return phase; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visibleThisFrame) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_ENTRY_TELEPORTER);
        if (renderer == null) {
            return;
        }
        // Multi-sprite main sprite: mapping_frame stays 0 until deletion.
        renderer.drawFrameIndex(0, x, y, false, false, PALETTE_LINE);
        for (int index = 0; index < segmentCount && index < MAX_SEGMENTS; index++) {
            renderer.drawFrameIndex(childFrame[index], childX[index], childY[index], false, false, PALETTE_LINE);
        }
    }
}
