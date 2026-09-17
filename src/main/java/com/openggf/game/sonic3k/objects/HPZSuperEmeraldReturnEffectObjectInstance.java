package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.S3kSanctuaryRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Invincibility-star ring of the Super Emerald results reveal.
 * ROM: {@code loc_2ECD0}-{@code loc_2EDCA} (sonic3k.asm:64173-64274).
 *
 * <p>{@code Obj_SpecialStage_Results} allocates eight of these SSTs at once
 * ({@code loc_2E70C} converging onto the cleared pedestal, {@code loc_2E7A0}
 * expanding from the Master Emerald once all seven Super Emeralds are held). They share
 * one radius word and one creation frame, and each SST draws two mirrored child sprites,
 * so one instance stands for the whole group: lane {@code i} starts at angle
 * {@code i*$10} and mapping counter {@code i}. The group plays no sound; the results
 * object owns the Signpost, Super Emerald and Perfect effects.
 */
public final class HPZSuperEmeraldReturnEffectObjectInstance
        extends AbstractObjectInstance implements RewindRecreatable {
    /** {@code word_2E398}: the results camera X for each Super Emerald stage. */
    static final int[] CAMERA_X =
            {0x15A0, 0x1540, 0x1600, 0x1500, 0x1640, 0x14B0, 0x1690};
    /** {@code word_2E398+$10}: the matching pedestal Y ({@code loc_2ECD0} reads it as the centre). */
    static final int[] PEDESTAL_Y =
            {0x368, 0x3A0, 0x3A0, 0x350, 0x350, 0x390, 0x390};
    private static final int LANES = 8;
    /** {@code loc_2ECD0} with {@code $36} set: the expanding ring is centred on the Master Emerald. */
    private static final int EXPANDING_X = 0x1640;
    private static final int EXPANDING_Y = 0x340;

    private HPZSSEntryControlObjectInstance parentRef;
    private int stageIndex;
    private boolean expanding;
    private int angle;
    /** {@code $32}: unsigned radius word, $E000 converging / 0 expanding. */
    private int radius;
    private final int[] laneMappingFrames = {0, 1, 2, 3, 4, 5, 6, 7};
    private final int[] laneOffsetX = new int[LANES];
    private final int[] laneOffsetY = new int[LANES];
    private boolean drawCurrentFrame;
    private boolean collapsed;
    private boolean completed;

    private record RewindExtra(
            ObjectRefId parentId, int stageIndex, boolean expanding, int angle, int radius,
            int[] laneMappingFrames, int[] laneOffsetX, int[] laneOffsetY,
            boolean drawCurrentFrame, boolean collapsed, boolean completed)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {
        private RewindExtra {
            laneMappingFrames = laneMappingFrames.clone();
            laneOffsetX = laneOffsetX.clone();
            laneOffsetY = laneOffsetY.clone();
        }
    }

    /**
     * @param parent    the sanctuary controller whose runtime carries {@code _unkFAC0}
     * @param stageIndex {@code Current_special_stage_2}
     * @param expanding {@code $36}: set by {@code loc_2E7A0} for the seven-emerald ring
     */
    public HPZSuperEmeraldReturnEffectObjectInstance(
            HPZSSEntryControlObjectInstance parent, int stageIndex, boolean expanding) {
        super(new ObjectSpawn(0, 0, 0xB5, 0, 0, false, 0),
                "HPZSuperEmeraldReturnEffect");
        if (stageIndex < 0 || stageIndex >= CAMERA_X.length) {
            throw new IllegalArgumentException("stageIndex");
        }
        parentRef = parent;
        this.stageIndex = stageIndex;
        this.expanding = expanding;
        // loc_2ECD0: move.w #-$2000,$32(a0), then clr.w $32(a0) when $36 is set.
        radius = expanding ? 0 : 0xE000;
    }

    private HPZSuperEmeraldReturnEffectObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HPZSuperEmeraldReturnEffect");
    }

    @Override
    public HPZSuperEmeraldReturnEffectObjectInstance recreateForRewind(
            RewindRecreateContext ctx) {
        return new HPZSuperEmeraldReturnEffectObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (completed) {
            return;
        }
        // loc_2ED2A: every lane uses the radius from before this frame's step.
        for (int lane = 0; lane < LANES; lane++) {
            int laneAngle = (angle + lane * 0x10) & 0xFF;
            laneOffsetX[lane] = scale(TrigLookupTable.cosHex(laneAngle), radius);
            laneOffsetY[lane] = scale(TrigLookupTable.sinHex(laneAngle), radius);
            // loc_2ED5C: addq.w #1 / cmpi.w #8 / bls, else moveq #0.
            laneMappingFrames[lane] = laneMappingFrames[lane] >= 8
                    ? 0 : laneMappingFrames[lane] + 1;
        }
        angle = (angle + 2) & 0xFF;
        if (expanding) {
            // loc_2EDBC: addi.w #$100,$32 / bcs delete.
            radius += 0x100;
            if (radius > 0xFFFF) {
                finish();
                return;
            }
        } else {
            // subi.w #$100,$32 / bcs loc_2EDAE: the borrow frame deletes without
            // drawing, sets the results object's $31 and clears _unkFAC0.
            radius -= 0x100;
            if (radius < 0) {
                collapsed = true;
                S3kSanctuaryRuntimeState runtime = runtime();
                if (runtime != null) {
                    runtime.completePedestalTransformation();
                }
                finish();
                return;
            }
        }
        drawCurrentFrame = true;
    }

    private void finish() {
        drawCurrentFrame = false;
        completed = true;
        ObjectLifetimeOps.expireDynamic(this);
    }

    /** {@code mulu.w d2,d1 / swap d1} on the magnitude, sign restored with {@code neg.w}. */
    static int scale(int trig, int radiusWord) {
        int magnitude = (Math.abs(trig) * (radiusWord & 0xFFFF)) >>> 16;
        return trig < 0 ? -magnitude : magnitude;
    }

    private S3kSanctuaryRuntimeState runtime() {
        return parentRef == null ? null : parentRef.runtimeForChild();
    }

    /** {@code st $31(a1)} reached the results object: the converging ring has closed. */
    public boolean hasCollapsed() {
        return collapsed;
    }

    public boolean isFinished() {
        return completed;
    }

    public boolean isExpanding() {
        return expanding;
    }

    int radiusForTest() {
        return radius;
    }

    int mappingFrameForTest(int lane) {
        return laneMappingFrames[lane];
    }

    int offsetXForTest(int lane) {
        return laneOffsetX[lane];
    }

    int offsetYForTest(int lane) {
        return laneOffsetY[lane];
    }

    boolean drawsCurrentFrameForTest() {
        return drawCurrentFrame;
    }

    // loc_2ECD0 never writes priority (sonic3k.asm:64173-64193), so the AllocateObject-cleared
    // word 0 stands: display list 0 is the ROM value.
    private static final int PRIORITY_BUCKET = RenderPriority.bucket(0);

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isHighPriority() {
        // loc_2ECD0 art make_art_tile(ArtTile_Shield,0,1) sets bit 15 (sonic3k.asm:64176).
        return true;
    }

    /** The results objects are never unloaded by camera range while they run. */
    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (completed || !drawCurrentFrame) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.INVINCIBILITY_STARS);
        if (renderer == null) {
            return;
        }
        int centreX = getX();
        int centreY = getY();
        for (int lane = 0; lane < LANES; lane++) {
            renderer.drawFrameIndex(laneMappingFrames[lane],
                    centreX + laneOffsetX[lane], centreY + laneOffsetY[lane], false, false, 0);
            renderer.drawFrameIndex(laneMappingFrames[lane],
                    centreX - laneOffsetX[lane], centreY - laneOffsetY[lane], false, false, 0);
        }
    }

    @Override public int getX() {
        return expanding ? EXPANDING_X : CAMERA_X[stageIndex] + 0xA0;
    }
    @Override public int getY() {
        return expanding ? EXPANDING_Y : PEDESTAL_Y[stageIndex];
    }
    @Override public int getOutOfRangeReferenceX() { return getX(); }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parentRef)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(parentId, stageIndex, expanding, angle, radius,
                        laneMappingFrames, laneOffsetX, laneOffsetY, drawCurrentFrame,
                        collapsed, completed));
    }

    @Override
    public void restoreRewindState(
            PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            parentRef = extra.parentId() == null ? null
                    : (HPZSSEntryControlObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.parentId(), true);
            stageIndex = extra.stageIndex();
            expanding = extra.expanding();
            angle = extra.angle();
            radius = extra.radius();
            System.arraycopy(extra.laneMappingFrames(), 0, laneMappingFrames, 0, LANES);
            System.arraycopy(extra.laneOffsetX(), 0, laneOffsetX, 0, LANES);
            System.arraycopy(extra.laneOffsetY(), 0, laneOffsetY, 0, LANES);
            drawCurrentFrame = extra.drawCurrentFrame();
            collapsed = extra.collapsed();
            completed = extra.completed();
        }
    }
}
