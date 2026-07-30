package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.S3kSanctuaryRuntimeState;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
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
 * Successful HPZ special-stage return transformation.
 * ROM: {@code loc_2ECD0}-{@code loc_2EDCA}.
 */
public final class HPZSuperEmeraldReturnEffectObjectInstance
        extends AbstractObjectInstance implements RewindRecreatable {
    private static final int[] CAMERA_X =
            {0x15A0, 0x1540, 0x1600, 0x1500, 0x1640, 0x14B0, 0x1690};
    private static final int[] PEDESTAL_Y =
            {0x368, 0x3A0, 0x3A0, 0x350, 0x350, 0x390, 0x390};

    private HPZSSEntryControlObjectInstance parentRef;
    private int angle;
    private int displayAngle;
    private int radius = 0xE000;
    private int displayRadius;
    private final int[] laneMappingFrames = {0, 1, 2, 3, 4, 5, 6, 7};
    private boolean drawCurrentFrame;
    private boolean startSoundPlayed;
    private boolean collapsed;
    private boolean completed;

    private record RewindExtra(
            ObjectRefId parentId, int angle, int displayAngle, int radius, int displayRadius,
            int[] laneMappingFrames, boolean drawCurrentFrame, boolean startSoundPlayed,
            boolean collapsed, boolean completed)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {
        private RewindExtra {
            laneMappingFrames = laneMappingFrames.clone();
        }
    }

    public HPZSuperEmeraldReturnEffectObjectInstance(
            HPZSSEntryControlObjectInstance parent) {
        super(new ObjectSpawn(0, 0, 0xB5, 0, 0, false, 0),
                "HPZSuperEmeraldReturnEffect");
        parentRef = parent;
    }

    private HPZSuperEmeraldReturnEffectObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HPZSuperEmeraldReturnEffect");
    }

    @Override
    public HPZSuperEmeraldReturnEffectObjectInstance recreateForRewind(
            RewindRecreateContext ctx) {
        return new HPZSuperEmeraldReturnEffectObjectInstance(ctx.spawn());
    }

    private S3kSanctuaryRuntimeState runtime() {
        return parentRef == null ? null : parentRef.runtimeForChild();
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        S3kSanctuaryRuntimeState runtime = runtime();
        if (completed || runtime == null) {
            return;
        }
        if (!startSoundPlayed) {
            startSoundPlayed = true;
            if (tryServices() != null) {
                services().playSfx(Sonic3kSfx.SIGNPOST.id);
            }
        }
        if (collapsed) {
            completed = true;
            runtime.completeReturnTransformation();
            if (tryServices() != null) {
                services().playSfx(Sonic3kSfx.PERFECT.id);
            }
            ObjectLifetimeOps.expireDynamic(this);
            return;
        }
        runtime.updateEmeraldFlicker();
        if (radius == 0) {
            drawCurrentFrame = false;
            collapsed = true;
            runtime.completePedestalTransformation();
            if (tryServices() != null) {
                services().playSfx(Sonic3kSfx.SUPER_EMERALD.id);
            }
            return;
        }
        drawCurrentFrame = true;
        displayAngle = angle;
        displayRadius = radius;
        angle = (angle + 2) & 0xFF;
        for (int lane = 0; lane < laneMappingFrames.length; lane++) {
            laneMappingFrames[lane] = laneMappingFrames[lane] == 8
                    ? 0 : laneMappingFrames[lane] + 1;
        }
        radius = (radius - 0x100) & 0xFFFF;
    }

    int radiusForTest() {
        return radius;
    }

    int mappingFrameForTest() {
        return laneMappingFrames[0];
    }

    int mappingFrameForTest(int lane) {
        return laneMappingFrames[lane];
    }

    int displayRadiusForTest() {
        return displayRadius;
    }

    boolean drawsCurrentFrameForTest() {
        return drawCurrentFrame;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        S3kSanctuaryRuntimeState runtime = runtime();
        if (completed || !drawCurrentFrame
                || runtime == null || runtime.returnStage() < 0) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.INVINCIBILITY_STARS);
        if (renderer == null) {
            return;
        }
        int stage = runtime.returnStage();
        int centreX = CAMERA_X[stage] + 0xA0;
        int centreY = PEDESTAL_Y[stage];
        int pixelRadius = displayRadius >>> 8;
        for (int i = 0; i < 8; i++) {
            int childAngle = (displayAngle + i * 0x10) & 0xFF;
            int dx = TrigLookupTable.cosHex(childAngle) * pixelRadius >> 8;
            int dy = TrigLookupTable.sinHex(childAngle) * pixelRadius >> 8;
            renderer.drawFrameIndex(laneMappingFrames[i], centreX + dx, centreY + dy,
                    false, false, 0);
            renderer.drawFrameIndex(laneMappingFrames[i], centreX - dx, centreY - dy,
                    false, false, 0);
        }
    }

    @Override public int getX() {
        S3kSanctuaryRuntimeState runtime = runtime();
        return runtime == null || runtime.returnStage() < 0
                ? 0x1640 : CAMERA_X[runtime.returnStage()] + 0xA0;
    }
    @Override public int getY() {
        S3kSanctuaryRuntimeState runtime = runtime();
        return runtime == null || runtime.returnStage() < 0
                ? 0x368 : PEDESTAL_Y[runtime.returnStage()];
    }
    @Override public int getOutOfRangeReferenceX() { return getX(); }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parentRef)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(
                new RewindExtra(parentId, angle, displayAngle, radius, displayRadius,
                        laneMappingFrames, drawCurrentFrame, startSoundPlayed,
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
            angle = extra.angle();
            displayAngle = extra.displayAngle();
            radius = extra.radius();
            displayRadius = extra.displayRadius();
            System.arraycopy(extra.laneMappingFrames(), 0, laneMappingFrames, 0,
                    laneMappingFrames.length);
            drawCurrentFrame = extra.drawCurrentFrame();
            startSoundPlayed = extra.startSoundPlayed();
            collapsed = extra.collapsed();
            completed = extra.completed();
        }
    }
}
