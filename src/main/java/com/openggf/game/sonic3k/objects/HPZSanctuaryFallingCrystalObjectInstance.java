package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * The controller's {@code loc_90CA2} falling crystal. Subtype 7 drives the
 * initial sanctuary ceremony; subtypes 0-6 publish conversion on landing.
 */
public final class HPZSanctuaryFallingCrystalObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    private static final int[] TARGET_X = {
            0x1640, 0x15E0, 0x16A0, 0x15A0, 0x16E0, 0x1550, 0x1730, 0x1640
    };
    private static final int[] TARGET_Y = {
            0x300, 0x328, 0x328, 0x2D8, 0x2D8, 0x318, 0x318, 0x340
    };
    private static final int[] RAW_LANDING_FRAMES = {
            0x1D, 0x1F, 0x1D, 0x20, 0x1D, 0x21, 0x1D,
            0x22, 0x1D, 0x23, 0x1D, 0x24, 0x1D
    };

    // parentRef naming opts into the engine's two-phase ObjectRefId relink.
    private HPZSSEntryControlObjectInstance parentRef;
    private int subtype;
    private int x;
    private int y;
    private int landingTimer = -1;
    private int rawAnimationTimer = -1;
    private int rawAnimationIndex;
    private int mappingFrame = 8;
    private int screenShakeTimer;
    private int lastFrameCounter;
    private boolean midpointPublished;
    private boolean published;

    private record RewindExtra(
            ObjectRefId parentId, int subtype, int x, int y, int landingTimer,
            int rawAnimationTimer, int rawAnimationIndex, int mappingFrame,
            int screenShakeTimer, int lastFrameCounter,
            boolean midpointPublished, boolean published)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    public HPZSanctuaryFallingCrystalObjectInstance(
            HPZSSEntryControlObjectInstance parent, int subtype) {
        this(parent, subtype, TARGET_Y[subtype] - 0x180);
    }

    public HPZSanctuaryFallingCrystalObjectInstance(
            HPZSSEntryControlObjectInstance parent, int subtype, int startY) {
        super(new ObjectSpawn(TARGET_X[subtype], startY,
                0xB4, subtype, 0, false, 0), "HPZSanctuaryFallingCrystal");
        parentRef = parent;
        this.subtype = subtype;
        x = TARGET_X[subtype];
        y = startY;
    }

    private HPZSanctuaryFallingCrystalObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HPZSanctuaryFallingCrystal");
        subtype = Math.min(7, spawn.subtype());
        x = spawn.x();
        y = spawn.y();
    }

    @Override
    public HPZSanctuaryFallingCrystalObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new HPZSanctuaryFallingCrystalObjectInstance(ctx.spawn());
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        lastFrameCounter = frameCounter;
        if (screenShakeTimer > 0 && --screenShakeTimer == 0 && tryServices() != null) {
            services().gameState().setScreenShakeActive(false);
        }
        if (published || parentRef == null) return;
        if (rawAnimationTimer >= 0) {
            if (rawAnimationIndex < RAW_LANDING_FRAMES.length) {
                mappingFrame = RAW_LANDING_FRAMES[rawAnimationIndex++];
                rawAnimationTimer--;
            } else {
                published = true;
                if (tryServices() != null && subtype != 7) {
                    services().playSfx(Sonic3kSfx.SUPER_EMERALD.id);
                }
                parentRef.onFallingCrystalAnimationComplete(subtype);
                setDestroyed(true);
            }
            return;
        }
        if (landingTimer < 0) {
            y += 0x10;
            if (y >= TARGET_Y[subtype]) {
                y = TARGET_Y[subtype];
                landingTimer = 0x3F;
                screenShakeTimer = 8;
                if (tryServices() != null) {
                    services().gameState().setScreenShakeActive(true);
                    services().playSfx(Sonic3kSfx.BOSS_LASER.id);
                }
            }
            return;
        }
        landingTimer--;
        if (landingTimer == 0x20 && !midpointPublished) {
            midpointPublished = true;
            parentRef.onFallingCrystalMidpoint(subtype);
        }
        if (landingTimer < 0) {
            rawAnimationTimer = RAW_LANDING_FRAMES.length;
            rawAnimationIndex = 0;
        }
    }

    @Override
    public PerObjectRewindSnapshot captureRewindState(RewindCaptureContext context) {
        ObjectRefId parentId = context.identityTable()
                .map(table -> table.encodeObject(parentRef)).orElse(null);
        return super.captureRewindState(context).withObjectSubclassExtra(new RewindExtra(
                parentId, subtype, x, y, landingTimer, rawAnimationTimer,
                rawAnimationIndex, mappingFrame, screenShakeTimer,
                lastFrameCounter, midpointPublished, published));
    }

    @Override
    public void restoreRewindState(
            PerObjectRewindSnapshot snapshot, RewindCaptureContext context) {
        super.restoreRewindState(snapshot, context);
        if (snapshot.objectSubclassExtra() instanceof RewindExtra extra) {
            parentRef = extra.parentId() == null ? null
                    : (HPZSSEntryControlObjectInstance) context.requireIdentityTable()
                    .resolveObject(extra.parentId(), true);
            subtype = extra.subtype();
            x = extra.x();
            y = extra.y();
            landingTimer = extra.landingTimer();
            rawAnimationTimer = extra.rawAnimationTimer();
            rawAnimationIndex = extra.rawAnimationIndex();
            mappingFrame = extra.mappingFrame();
            screenShakeTimer = extra.screenShakeTimer();
            lastFrameCounter = extra.lastFrameCounter();
            midpointPublished = extra.midpointPublished();
            published = extra.published();
        }
    }

    boolean midpointPublishedForTest() {
        return midpointPublished;
    }

    boolean publishedForTest() {
        return published;
    }

    int landingTimerForTest() {
        return landingTimer;
    }

    int rawAnimationTimerForTest() {
        return rawAnimationTimer;
    }

    int screenShakeTimerForTest() {
        return screenShakeTimer;
    }

    boolean shouldDrawForTest(int frameCounter) {
        return !published && (frameCounter & 1) == 0;
    }

    int renderPaletteLineForTest() {
        // ObjDat3_90FCC: make_art_tile(ArtTile_HPZEmeraldMisc,0,1).
        return 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!shouldDrawForTest(lastFrameCounter)) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HPZ_MASTER_EMERALD);
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame,
                    x, y, false, false, renderPaletteLineForTest());
        }
    }

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getOutOfRangeReferenceX() { return x; }
    HPZSSEntryControlObjectInstance parentForTest() { return parentRef; }
}
