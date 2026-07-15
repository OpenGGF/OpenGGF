package com.openggf.game.sonic3k.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.MgzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Object 0x57 - MGZ Trigger Platform.
 *
 * <p>ROM: Obj_MGZTriggerPlatform (sonic3k.asm:70910-71029).
 * The high subtype nibble selects one of three table-driven platform shapes:
 * a horizontal escape platform (nibble $0) or vertical trigger platforms
 * (nibbles $1 and $2) that move 1px/frame or 2px/frame once their trigger fires.
 *
 * <p>Subtype bits:
 * <ul>
 *   <li>Bits [7:4]: config index into byte_34568</li>
 *   <li>Bits [3:0]: Level_trigger_array index to monitor</li>
 * </ul>
 *
 * <p>Render/status bit 0 reverses the movement direction for both horizontal
 * and vertical variants.
 */
public class MGZTriggerPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, RomObjectCodePointerProvider,
        SpawnRewindRecreatable {

    private static final String ART_KEY = Sonic3kObjectArtKeys.MGZ_TRIGGER_PLATFORM;
    private static final int PRIORITY_BUCKET = 5; // ROM: priority = $280

    private static final int SCREEN_SHAKE_MASK = 0x3F;
    private static final int[] SCREEN_SHAKE_CONTINUOUS = {
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3,
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3
    };

    private enum Mode {
        HORIZONTAL_DELETE,
        VERTICAL_MOVE
    }

    private int triggerIndex;
    private int frameIndex;
    private int widthPixels;
    private int heightPixels;
    private int totalFrames;
    private int stepPerFrame;
    private int direction;
    private Mode mode;

    private int currentX;
    private int currentY;
    private int remainingFrames;
    private boolean activated;
    private boolean completed;

    public MGZTriggerPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MGZTriggerPlatform");

        int highNibble = spawn.subtype() & 0xF0;
        int configIndex = highNibble >> 4;
        if (configIndex > 2) {
            configIndex = 2;
        }

        this.widthPixels = switch (configIndex) {
            case 0 -> 0x40;
            case 1, 2 -> 0x20;
            default -> 0x20;
        };
        this.heightPixels = switch (configIndex) {
            case 0 -> 0x1E;
            case 1, 2 -> 0x40;
            default -> 0x40;
        };
        this.frameIndex = (configIndex == 0) ? 0 : 1;
        this.totalFrames = 0x40;
        this.stepPerFrame = configIndex;
        this.mode = (configIndex == 0) ? Mode.HORIZONTAL_DELETE : Mode.VERTICAL_MOVE;

        this.triggerIndex = spawn.subtype() & 0x0F;
        this.direction = ((spawn.renderFlags() & 0x01) != 0) ? -1 : 1;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.remainingFrames = totalFrames;

        // ROM: vertical variants spawned after the trigger has already fired are
        // immediately fast-forwarded to their final Y and marked complete.
        if (mode == Mode.VERTICAL_MOVE && Sonic3kLevelTriggerManager.testAny(triggerIndex)) {
            currentY += direction * totalFrames * stepPerFrame;
            remainingFrames = 0;
            completed = true;
        }

        updateDynamicSpawn(currentX, currentY);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            clearScreenShake();
            return;
        }

        boolean wasActivated = activated;
        if (!completed && Sonic3kLevelTriggerManager.testAny(triggerIndex)) {
            activated = true;
        }

        if (!completed && wasActivated) {
            advanceActiveMotion();
            applyScreenShake(frameCounter);
        } else {
            clearScreenShake();
        }

        updateDynamicSpawn(currentX, currentY);
    }

    private void advanceActiveMotion() {
        if (mode == Mode.HORIZONTAL_DELETE) {
            currentX += direction * 2;
        } else {
            currentY += direction * stepPerFrame;
        }

        remainingFrames--;
        if (remainingFrames > 0) {
            return;
        }

        completed = true;
        clearScreenShake();

        if (mode == Mode.HORIZONTAL_DELETE) {
            markRemembered();
            setDestroyed(true);
        }
    }

    private void markRemembered() {
        var svc = tryServices();
        if (svc == null) {
            return;
        }
        ObjectManager objectManager = svc.objectManager();
        ObjectLifetimeOps.markSpawnRemembered(objectManager, spawn);
    }

    private void applyScreenShake(int frameCounter) {
        MgzZoneRuntimeState mgzState = resolveMgzRuntimeState();
        if (mgzState == null) {
            return;
        }
        mgzState.requestScreenShakeOffset(SCREEN_SHAKE_CONTINUOUS[frameCounter & SCREEN_SHAKE_MASK]);
    }

    private void clearScreenShake() {
        MgzZoneRuntimeState mgzState = resolveMgzRuntimeState();
        if (mgzState != null) {
            mgzState.clearScreenShakeOffset();
        }
    }

    private MgzZoneRuntimeState resolveMgzRuntimeState() {
        var svc = tryServices();
        if (svc == null || svc.zoneRuntimeRegistry() == null) {
            return null;
        }
        return S3kRuntimeStates.currentMgz(svc.zoneRuntimeRegistry()).orElse(null);
    }

    @Override
    public void onUnload() {
        clearScreenShake();
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(widthPixels + 0x0B, heightPixels, heightPixels + 1);
    }

    @Override
    public boolean usesInclusiveRightEdge() {
        // ROM SolidObjectFull's horizontal entry check rejects only values
        // above d1*2 (bhi), retaining the exact right edge.
        // sonic3k.asm:41390-41401.
        return true;
    }

    @Override
    public boolean airborneStaleStandingBitReturnsNoContact(PlayableEntity player) {
        // SolidObjectFull2_1P sees this object's retained standing bit before
        // SolidObject_cont. An airborne rider clears the bit and returns without
        // resolving another contact (sonic3k.asm:41066-41084).
        return true;
    }

    @Override
    public boolean suppressesGroundingRecoveryFromAirborneStaleRide(PlayableEntity player) {
        // Player slots run before this later object slot. Preserve the airborne
        // movement pass until SolidObjectFull consumes the stale standing bit.
        return true;
    }

    @Override
    public int romObjectCodePointerHighWord() {
        // Obj_MGZTriggerPlatform lives at $000345D4 in the locked-on ROM.
        return 0x0003;
    }

    @Override
    public int getOnScreenHalfWidth() {
        // ROM Render_Sprites consumes byte_34568's width_pixels value.
        return widthPixels;
    }

    @Override
    public int getOnScreenHalfHeight() {
        // render_flags bit 2 selects the custom height_pixels visibility path.
        return heightPixels;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        if (playerEntity == null || contact == null || !contact.standing()
                || (getSpawn().subtype() & 0xF0) != 0x10) {
            return;
        }

        // The two vertical variants are adjacent placements, but FindFreeObj's
        // live SST landscape can allocate the subtype-$1x landing platform
        // before a subtype-$2x sibling even when the engine's placement slots
        // are reversed. The later native SolidObjectFull sees the just-grounded
        // player and may publish Status_Push. Re-run only those reversed,
        // earlier-engine-slot $2x siblings after this landing checkpoint.
        // sonic3k.asm:70910-71029,41370-41534.
        int landingSlot = getSlotIndex();
        ObjectManager objectManager = services().objectManager();
        for (MGZTriggerPlatformObjectInstance sibling :
                objectManager.activeObjectsOfType(MGZTriggerPlatformObjectInstance.class)) {
            if (sibling.getSlotIndex() >= landingSlot) {
                break;
            }
            if ((sibling.getSpawn().subtype() & 0xF0) == 0x20) {
                objectManager.processImmediateInlineSolidCheckpoint(sibling, playerEntity, List.of());
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ART_KEY);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(frameIndex, currentX, currentY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (ctx == null) {
            return;
        }
        ctx.drawRect(currentX, currentY, widthPixels + 0x0B, heightPixels, 0.2f, 0.9f, 0.2f);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY_BUCKET);
    }
}
