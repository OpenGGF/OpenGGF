package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.RespawnState;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * MHZ Act 1 cutscene button paired with {@link Mhz1CutsceneKnucklesInstance}.
 *
 * <p>ROM reference: {@code Obj_MHZ1CutsceneButton}. This owns the cutscene
 * switch and creates both the fixed door child and the later peering Knuckles
 * child used by the {@code _unkFAB8=$0C} release callback.
 */
public final class Mhz1CutsceneButtonInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {
    private static final int INIT_Y_OFFSET = 4;
    private static final int PRIORITY = 4;
    private static final int CALLBACK_WAIT = 0x5F;
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x1B, 4, 5);

    private final int x;
    private final int y;
    private boolean pressed;
    private boolean normalPressed;
    private boolean contactStanding;
    private boolean doorSpawned;
    private boolean peerSpawned;
    private CutsceneKnucklesMhz1Instance spawnedKnuckles;
    private boolean doorSwitchActive;
    private boolean doorLowered;
    private boolean doorMoving;
    private int timer;

    public Mhz1CutsceneButtonInstance(ObjectSpawn spawn) {
        super(spawn, "MHZ1CutsceneButton");
        this.x = spawn.x();
        this.y = spawn.y() + INIT_Y_OFFSET;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        spawnDoorOnce();
        Mhz1CutsceneKnucklesInstance knuckles = Mhz1CutsceneKnucklesInstance.getActiveInstance();
        if (usesNormalSwitchPath(knuckles)) {
            updateNormalSwitchPath();
            return;
        }
        if (knuckles == null) {
            return;
        }
        if (!pressed) {
            if (knuckles.getWorkspaceRoutineForTest() < 0x0A) {
                return;
            }
            spawnMhz1KnucklesOnce();
            if (!spawnedKnucklesInButtonRange()) {
                return;
            }
            pressCutsceneButton();
            return;
        }
        if (timer >= 0) {
            timer--;
            return;
        }
        knuckles.signalButtonCallback();
    }

    private boolean usesNormalSwitchPath(Mhz1CutsceneKnucklesInstance activeCutscene) {
        if (activeCutscene != null) {
            return false;
        }
        if (lastStarPostHitIsSet()) {
            return true;
        }
        return S3kRuntimeStates.currentMhz(services().zoneRuntimeRegistry())
                .map(state -> state.playerCharacter() == PlayerCharacter.KNUCKLES)
                .orElse(false);
    }

    private boolean lastStarPostHitIsSet() {
        RespawnState checkpointState = services().checkpointState();
        return checkpointState != null && checkpointState.getLastCheckpointIndex() > 0;
    }

    private void updateNormalSwitchPath() {
        boolean standing = contactStanding;
        contactStanding = false;

        if (!normalPressed) {
            if (!standing) {
                return;
            }
            normalPressed = true;
            services().playSfx(Sonic3kSfx.SWITCH.id);
            if (!doorMoving) {
                doorSwitchActive = true;
                doorLowered = !doorLowered;
                services().playSfx(Sonic3kSfx.SWITCH.id);
            }
            return;
        }

        doorSwitchActive = false;
        if (!standing) {
            normalPressed = false;
        }
    }

    private void spawnMhz1KnucklesOnce() {
        if (peerSpawned) {
            return;
        }
        peerSpawned = true;
        spawnFreeChild(() -> {
            spawnedKnuckles = new CutsceneKnucklesMhz1Instance(new ObjectSpawn(
                    0x0374, 0x066C, Sonic3kObjectIds.CUTSCENE_KNUCKLES, 0x1C, 0, false, 0), this);
            return spawnedKnuckles;
        });
    }

    private boolean spawnedKnucklesInButtonRange() {
        if (spawnedKnuckles == null || spawnedKnuckles.isDestroyed()) {
            return false;
        }
        int knucklesX = spawnedKnuckles.getX();
        int knucklesY = spawnedKnuckles.getY();
        return knucklesX >= x - 0x18 && knucklesX < x + 0x18
                && knucklesY >= y - 0x18 && knucklesY < y + 0x18;
    }

    private void pressCutsceneButton() {
        pressed = true;
        doorSwitchActive = true;
        doorLowered = true;
        timer = CALLBACK_WAIT;
        services().playSfx(Sonic3kSfx.SWITCH.id);
    }

    private void spawnDoorOnce() {
        if (doorSpawned) {
            return;
        }
        doorSpawned = true;
        spawnFreeChild(() -> new Mhz1CutsceneDoorInstance(this));
    }

    boolean isDoorSwitchActive() {
        return doorSwitchActive;
    }

    void clearDoorSwitchActive() {
        doorSwitchActive = false;
    }

    boolean isDoorLowered() {
        return doorLowered;
    }

    void setDoorLowered(boolean doorLowered) {
        this.doorLowered = doorLowered;
    }

    void setDoorMoving(boolean doorMoving) {
        this.doorMoving = doorMoving;
    }

    boolean isDoorMovingForTest() {
        return doorMoving;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        if (contact.standing()) {
            contactStanding = true;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.BUTTON);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex((pressed || normalPressed) ? 1 : 0, x, y, false, false);
    }
}
