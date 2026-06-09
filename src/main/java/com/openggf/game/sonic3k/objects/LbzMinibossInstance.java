package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Launch Base Zone Act 1 miniboss.
 *
 * <p>ROM: {@code Obj_LBZMiniboss} at {@code sonic3k.asm:151366}. This object is
 * spawned by {@code Obj_LBZ1Robotnik}'s {@code ChildObjDat_8D264} handoff after
 * Robotnik drops the carried yellow box.
 */
public final class LbzMinibossInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {
    private static final int ROUTINE_INIT = 0x00;
    private static final int ROUTINE_INIT_WAIT = 0x02;
    private static final int ROUTINE_RISE_WAIT = 0x04;
    private static final int ROUTINE_OPENING = 0x06;
    private static final int ROUTINE_TRACK_PLAYER = 0x08;
    private static final int ROUTINE_ESCAPE = 0x0A;

    private static final int INITIAL_HITS = 6;
    private static final int OPEN_COLLISION_FLAGS = 0x06;
    private static final int HIT_REACTION_FRAMES = 0x20;
    private static final int INIT_WAIT = 0x10;
    private static final int RISE_WAIT = 0x3F;
    private static final int OPENING_WAIT = 0x10;
    private static final int OPENING_LOOP_START = 0x0F;
    private static final int TRACK_VEL = 0x0100;
    private static final int PLAYER_TARGET_Y_OFFSET = -0x38;
    private static final int KNUCKLES_TARGET_X_OFFSET = 0x20;
    private static final int TARGET_DEADBAND = 4;
    private static final int ARM_COLLISION_FLAGS = 0x98;
    private static final int BODY_PALETTE_LINE = 1;
    private static final int SIGNPOST_APPARENT_ACT = 0;

    private static final int[] BODY_RAW_FRAMES = {0, 1, 0, 2};
    private static final int[] PANEL_FRAMES = {7, 8, 7, 8, 7, 6};
    private static final int[] PANEL_ANGLE_A = {0x55, 0x00, 0xD5, 0xAA, 0x80, 0x68};
    private static final int[] PANEL_ANGLE_A_REVERSE = {0x2A, 0x7A, 0xB0, 0xDA, 0x00, 0x18};
    private static final int[] PANEL_ANGLE_B = {0xD5, 0x80, 0x55, 0x2A, 0x00, 0xF4};
    private static final int[] PANEL_ANGLE_B_REVERSE = {0xAA, 0x00, 0x2A, 0x55, 0x80, 0x8C};
    private static final int[] PANEL_ROUTINE4_WAITS = {0, 0x14, 9, 9, 9, 5};
    private static final int[] PANEL_ROUTINE8_WAITS = {0x34, 0x14, 9, 9, 9, 3};
    private static final int[] PANEL_DETACH_X_VEL = {0x100, -0x200, 0x300, 0x200, -0x100, -0x80};
    private static final int[] PANEL_DETACH_Y_VEL = {-0x100, -0x200, -0x200, -0x100, -0x200, -0x100};
    private static final int[] CENTER_RAW_FRAMES = {3, 4, 5, 5, 5, 5, 4, 3, 4};
    private static final int[] CENTER_RAW_DELAYS = {7, 7, 7, 0x3F, 0x3F, 0x3F, 7, 7, 7};

    private final SubpixelMotion.State motion;
    private final List<PanelState> panels = new ArrayList<>();
    private int routine = ROUTINE_INIT;
    private int waitTimer = -1;
    private WaitCallback waitCallback = WaitCallback.NONE;
    private int hitCount = INITIAL_HITS;
    private int collisionFlags;
    private int hitReactionTimer;
    private int savedRoutine;
    private int homeX;
    private int openingLoopCounter;
    private int bodyFrame;
    private int bodyAnimIndex;
    private int bodyAnimTimer;
    private int centerChildFrame = CENTER_RAW_FRAMES[0];
    private int centerChildAnimIndex;
    private int centerChildAnimTimer = CENTER_RAW_DELAYS[0];
    private boolean parentBit0;
    private boolean parentBit1;
    private boolean defeated;
    private boolean defeatFlowSpawned;
    private S3kBossExplosionController defeatExplosionController;

    private enum WaitCallback {
        NONE,
        START_RISE_WAIT,
        ARM_OPENING,
        OPENING_STEP
    }

    public LbzMinibossInstance(ObjectSpawn spawn) {
        super(spawn, "LBZMiniboss");
        this.motion = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, 0, 0);
    }

    @Override
    public int getX() {
        return motion.x & 0xFFFF;
    }

    @Override
    public int getY() {
        return motion.y & 0xFFFF;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getCollisionFlags() {
        if (defeated || hitReactionTimer > 0) {
            return 0;
        }
        return collisionFlags;
    }

    @Override
    public int getCollisionProperty() {
        return hitCount;
    }

    @Override
    public TouchRegion[] getMultiTouchRegions() {
        if (panels.isEmpty() || isDestroyed()) {
            return null;
        }
        List<TouchRegion> regions = new ArrayList<>();
        int bodyFlags = getCollisionFlags();
        if (bodyFlags != 0) {
            regions.add(new TouchRegion(getX(), getY(), bodyFlags));
        }
        for (PanelState panel : panels) {
            if (!panel.center && !panel.detached) {
                regions.add(new TouchRegion(panel.x, panel.y, ARM_COLLISION_FLAGS));
            }
        }
        return regions.isEmpty() ? null : regions.toArray(TouchRegion[]::new);
    }

    @Override
    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        if (defeated || hitReactionTimer > 0 || collisionFlags == 0) {
            return;
        }
        hitCount = Math.max(0, hitCount - 1);
        collisionFlags = 0;
        if (hitCount == 3) {
            parentBit0 = true;
        }
        if (hitCount == 0) {
            startDefeat();
            return;
        }
        savedRoutine = routine;
        routine = ROUTINE_ESCAPE;
        hitReactionTimer = HIT_REACTION_FRAMES;
        services().playSfx(Sonic3kSfx.BOSS_HIT.id);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }
        if (defeated) {
            updateDefeat(frameCounter);
            updateDynamicSpawn(getX(), getY());
            return;
        }
        updateHitReaction();
        switch (routine) {
            case ROUTINE_INIT -> initialize();
            case ROUTINE_INIT_WAIT -> tickWait();
            case ROUTINE_RISE_WAIT -> {
                moveSprite2();
                tickWait();
            }
            case ROUTINE_OPENING -> {
                animateOpeningFrame();
                tickWait();
            }
            case ROUTINE_TRACK_PLAYER -> {
                trackPlayer(playerEntity);
                moveSprite2();
                animateOpeningFrame();
                updatePanels();
            }
            case ROUTINE_ESCAPE -> updatePanels();
            default -> {
            }
        }
        updateDynamicSpawn(getX(), getY());
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        PatternSpriteRenderer bossRenderer = getRenderer(Sonic3kObjectArtKeys.LBZ_MINIBOSS);
        if (bossRenderer == null) {
            return;
        }
        bossRenderer.drawFrameIndex(bodyFrame, getX(), getY(), false, false, BODY_PALETTE_LINE);
        for (PanelState panel : panels) {
            bossRenderer.drawFrameIndex(panel.frame, panel.x, panel.y, false, false, BODY_PALETTE_LINE);
        }
    }

    public int getRoutineForTest() {
        return routine;
    }

    public int getWaitTimerForTest() {
        return waitTimer;
    }

    public int getPanelCountForTest() {
        return panels.size();
    }

    public int getXVelocityForTest() {
        return motion.xVel;
    }

    public int getYVelocityForTest() {
        return motion.yVel;
    }

    public int getHitReactionTimerForTest() {
        return hitReactionTimer;
    }

    public void forceOpenForTest(int x, int y) {
        motion.x = x;
        motion.y = y;
        motion.xSub = 0;
        motion.ySub = 0;
        motion.xVel = 0;
        motion.yVel = 0;
        homeX = x;
        bodyFrame = 0;
        bodyAnimIndex = 0;
        bodyAnimTimer = OPENING_WAIT;
        openingLoopCounter = 2;
        resetCenterChildAnimation();
        routine = ROUTINE_TRACK_PLAYER;
        waitTimer = -1;
        waitCallback = WaitCallback.NONE;
        hitCount = INITIAL_HITS;
        collisionFlags = OPEN_COLLISION_FLAGS;
        parentBit1 = true;
        ensurePanelsCreated();
        updatePanels();
        updateDynamicSpawn(getX(), getY());
    }

    private void initialize() {
        ensureArtLoaded();
        loadPalette();
        homeX = getX();
        hitCount = INITIAL_HITS;
        collisionFlags = 0;
        services().gameState().setCurrentBossId(Sonic3kObjectIds.LBZ_MINIBOSS);
        ensurePanelsCreated();
        waitTimer = INIT_WAIT;
        waitCallback = WaitCallback.START_RISE_WAIT;
        routine = ROUTINE_INIT_WAIT;
    }

    private void tickWait() {
        if (waitTimer < 0) {
            return;
        }
        waitTimer--;
        if (waitTimer >= 0) {
            return;
        }
        WaitCallback callback = waitCallback;
        waitCallback = WaitCallback.NONE;
        switch (callback) {
            case START_RISE_WAIT -> startRiseWait();
            case ARM_OPENING -> armOpening();
            case OPENING_STEP -> openingStep();
            case NONE -> {
            }
        }
    }

    private void startRiseWait() {
        routine = ROUTINE_RISE_WAIT;
        motion.yVel = 0;
        waitTimer = RISE_WAIT;
        waitCallback = WaitCallback.ARM_OPENING;
    }

    private void armOpening() {
        routine = ROUTINE_OPENING;
        parentBit1 = true;
        openingLoopCounter = OPENING_LOOP_START;
        bodyFrame = BODY_RAW_FRAMES[0];
        bodyAnimIndex = 0;
        bodyAnimTimer = OPENING_WAIT;
        resetCenterChildAnimation();
        waitTimer = OPENING_WAIT;
        waitCallback = WaitCallback.OPENING_STEP;
        updatePanels();
    }

    private void openingStep() {
        openingLoopCounter--;
        if (openingLoopCounter <= 2) {
            routine = ROUTINE_TRACK_PLAYER;
            collisionFlags = OPEN_COLLISION_FLAGS;
            return;
        }
        waitTimer = OPENING_WAIT;
        waitCallback = WaitCallback.OPENING_STEP;
    }

    private void trackPlayer(PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            motion.xVel = 0;
            motion.yVel = 0;
            return;
        }

        int targetX = player.getCentreX() & 0xFFFF;
        if (isKnuckles()) {
            targetX += spawn.subtype() == 0 ? KNUCKLES_TARGET_X_OFFSET : -KNUCKLES_TARGET_X_OFFSET;
        }
        int dx = signedDelta(targetX, getX());
        motion.xVel = Math.abs(dx) <= TARGET_DEADBAND ? 0 : (dx < 0 ? -TRACK_VEL : TRACK_VEL);

        int targetY = (player.getCentreY() + PLAYER_TARGET_Y_OFFSET) & 0xFFFF;
        int dy = signedDelta(targetY, getY());
        motion.yVel = Math.abs(dy) <= TARGET_DEADBAND ? 0 : (dy < 0 ? -TRACK_VEL : TRACK_VEL);
    }

    private boolean isKnuckles() {
        return S3kRuntimeStates.resolvePlayerCharacter(
                services().zoneRuntimeRegistry(),
                services().configuration()) == PlayerCharacter.KNUCKLES;
    }

    private int signedDelta(int target, int current) {
        return (short) ((target & 0xFFFF) - (current & 0xFFFF));
    }

    private void moveSprite2() {
        SubpixelMotion.moveSprite2(motion);
    }

    private void animateOpeningFrame() {
        bodyAnimTimer--;
        if (bodyAnimTimer < 0) {
            bodyAnimIndex++;
            if (bodyAnimIndex >= BODY_RAW_FRAMES.length) {
                bodyAnimIndex = 0;
            }
            bodyFrame = BODY_RAW_FRAMES[bodyAnimIndex];
            bodyAnimTimer = openingLoopCounter;
        }
        updateCenterChildAnimation();
    }

    private void resetCenterChildAnimation() {
        centerChildAnimIndex = 0;
        centerChildAnimTimer = CENTER_RAW_DELAYS[0];
        centerChildFrame = CENTER_RAW_FRAMES[0];
    }

    private void updateCenterChildAnimation() {
        if (!parentBit1) {
            return;
        }
        centerChildAnimTimer--;
        if (centerChildAnimTimer >= 0) {
            return;
        }
        centerChildAnimIndex = (centerChildAnimIndex + 1) % CENTER_RAW_FRAMES.length;
        centerChildFrame = CENTER_RAW_FRAMES[centerChildAnimIndex];
        centerChildAnimTimer = CENTER_RAW_DELAYS[centerChildAnimIndex];
    }

    private void updateHitReaction() {
        if (hitReactionTimer <= 0) {
            return;
        }
        hitReactionTimer--;
        if (hitReactionTimer == 0) {
            routine = savedRoutine;
            collisionFlags = OPEN_COLLISION_FLAGS;
        }
    }

    private void startDefeat() {
        defeated = true;
        collisionFlags = 0;
        parentBit0 = true;
        services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        services().gameState().addScore(1000);
        services().gameState().setCurrentBossId(Sonic3kObjectIds.LBZ_MINIBOSS);
        defeatExplosionController = new S3kBossExplosionController(getX(), getY(), 0, services().rng());
        for (PanelState panel : panels) {
            panel.detach();
        }
    }

    private void updateDefeat(int frameCounter) {
        updatePanels();
        if (defeatExplosionController != null && !defeatExplosionController.isFinished()) {
            defeatExplosionController.tick();
            for (S3kBossExplosionController.PendingExplosion explosion
                    : defeatExplosionController.drainPendingExplosions()) {
                if (explosion.playSfx()) {
                    services().playSfx(Sonic3kSfx.EXPLODE.id);
                }
                spawnChild(() -> new S3kBossExplosionChild(explosion.x(), explosion.y()));
            }
            return;
        }
        if (!defeatFlowSpawned) {
            defeatFlowSpawned = true;
            spawnChild(() -> new S3kBossDefeatSignpostFlow(
                    homeX, SIGNPOST_APPARENT_ACT, S3kBossDefeatSignpostFlow.CleanupAction.NONE));
            setDestroyed(true);
        }
    }

    private void ensurePanelsCreated() {
        if (!panels.isEmpty()) {
            return;
        }
        panels.add(new PanelState(true, 0, false));
        for (int i = 0; i < PANEL_FRAMES.length; i++) {
            panels.add(new PanelState(false, i, false));
        }
        for (int i = 0; i < PANEL_FRAMES.length; i++) {
            panels.add(new PanelState(false, i, true));
        }
        updatePanels();
    }

    private void updatePanels() {
        for (PanelState panel : panels) {
            panel.update();
        }
    }

    private void ensureArtLoaded() {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager != null && renderManager.getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            provider.ensureStandaloneArtLoaded(Sonic3kObjectArtKeys.LBZ_MINIBOSS);
        }
    }

    private void loadPalette() {
        try {
            byte[] palData = services().rom().readBytes(Sonic3kConstants.PAL_LBZ_MINIBOSS_ADDR, 32);
            S3kPaletteWriteSupport.applyLine(
                    services().paletteOwnershipRegistryOrNull(),
                    services().currentLevel(),
                    services().graphicsManager(),
                    S3kPaletteOwners.LBZ_MINIBOSS,
                    S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                    BODY_PALETTE_LINE,
                    palData);
        } catch (IOException ignored) {
            // Palette loading is best-effort in headless tests without a ROM handle.
        }
    }

    private final class PanelState {
        private static final int ROUTINE_INIT_WAIT = 2;
        private static final int ROUTINE_ROTATE_IF_ARMED = 4;
        private static final int ROUTINE_SYNC_TO_PARENT = 6;
        private static final int ROUTINE_ROTATE_AND_WAIT = 8;
        private static final int ROUTINE_OUTER_PAUSE = 10;

        private final boolean center;
        private final int index;
        private final boolean secondRing;
        private int x;
        private int y;
        private int xSub;
        private int ySub;
        private int xVel;
        private int yVel;
        private int frame;
        private int angle;
        private int angleDelta = 4;
        private int childRoutine = ROUTINE_INIT_WAIT;
        private int childWaitTimer = 0x100;
        private PanelWaitCallback waitCallback = PanelWaitCallback.START_ROTATE_IF_ARMED;
        private boolean bit1;
        private boolean detached;

        private enum PanelWaitCallback {
            NONE,
            START_ROTATE_IF_ARMED,
            AFTER_ROTATE_IF_ARMED,
            AFTER_ROTATE_AND_WAIT
        }

        private PanelState(boolean center, int index, boolean secondRing) {
            this.center = center;
            this.index = index;
            this.secondRing = secondRing;
            this.frame = center ? 7 : PANEL_FRAMES[index];
            this.angle = center ? 0 : (secondRing ? PANEL_ANGLE_B[index] : PANEL_ANGLE_A[index]);
            this.bit1 = !center && index == 5;
            if (center) {
                childWaitTimer = -1;
                waitCallback = PanelWaitCallback.NONE;
            }
        }

        private void update() {
            if (detached) {
                SubpixelMotion.State state = new SubpixelMotion.State(x, y, xSub, ySub, xVel, yVel);
                SubpixelMotion.moveSprite(state, SubpixelMotion.S3K_GRAVITY);
                x = state.x;
                y = state.y;
                xSub = state.xSub;
                ySub = state.ySub;
                xVel = state.xVel;
                yVel = state.yVel;
                return;
            }
            if (center) {
                x = getX();
                y = getY();
                if (parentBit1) {
                    frame = centerChildFrame;
                }
                return;
            }
            updateLinkedArmRoutine();
            if (parentBit0 && !secondRing) {
                detach();
            }
        }

        private void updateLinkedArmRoutine() {
            switch (childRoutine) {
                case ROUTINE_INIT_WAIT -> {
                    moveCircular();
                    tickPanelWait();
                }
                case ROUTINE_ROTATE_IF_ARMED -> {
                    if (bit1) {
                        angle = (angle + angleDelta) & 0xFF;
                        moveCircular();
                        tickPanelWait();
                    } else {
                        moveCircular();
                    }
                }
                case ROUTINE_SYNC_TO_PARENT -> {
                    PanelState parent = linkedParent();
                    angle = parent.angle;
                    moveCircular();
                    if (!parent.bit1) {
                        childRoutine = ROUTINE_ROTATE_AND_WAIT;
                    }
                }
                case ROUTINE_ROTATE_AND_WAIT -> {
                    angle = (angle + angleDelta) & 0xFF;
                    tickPanelWait();
                    moveCircular();
                }
                case ROUTINE_OUTER_PAUSE -> {
                    moveCircular();
                    tickPanelWait();
                }
                default -> moveCircular();
            }
        }

        private void tickPanelWait() {
            if (childWaitTimer < 0) {
                return;
            }
            childWaitTimer--;
            if (childWaitTimer >= 0) {
                return;
            }
            PanelWaitCallback callback = waitCallback;
            waitCallback = PanelWaitCallback.NONE;
            switch (callback) {
                case START_ROTATE_IF_ARMED -> startRotateIfArmed();
                case AFTER_ROTATE_IF_ARMED -> afterRotateIfArmed();
                case AFTER_ROTATE_AND_WAIT -> afterRotateAndWait();
                case NONE -> {
                }
            }
        }

        private void startRotateIfArmed() {
            childRoutine = ROUTINE_ROTATE_IF_ARMED;
            childWaitTimer = PANEL_ROUTINE4_WAITS[index];
            waitCallback = PanelWaitCallback.AFTER_ROTATE_IF_ARMED;
        }

        private void afterRotateIfArmed() {
            childWaitTimer = PANEL_ROUTINE8_WAITS[index];
            waitCallback = PanelWaitCallback.AFTER_ROTATE_AND_WAIT;
            if (index != 0) {
                childRoutine = ROUTINE_SYNC_TO_PARENT;
                PanelState parent = linkedParent();
                parent.bit1 = true;
                angle = parent.angle;
            } else {
                childRoutine = ROUTINE_ROTATE_AND_WAIT;
            }
        }

        private void afterRotateAndWait() {
            bit1 = false;
            angleDelta = -angleDelta;
            startRotateIfArmed();
            if (index == 5) {
                childRoutine = ROUTINE_OUTER_PAUSE;
                childWaitTimer = 0x3C;
                waitCallback = PanelWaitCallback.START_ROTATE_IF_ARMED;
                bit1 = true;
            }
            int[] table;
            if (secondRing) {
                table = angleDelta < 0 ? PANEL_ANGLE_B_REVERSE : PANEL_ANGLE_B;
            } else {
                table = angleDelta < 0 ? PANEL_ANGLE_A_REVERSE : PANEL_ANGLE_A;
            }
            angle = table[index];
        }

        private void moveCircular() {
            int shift = index == 5 ? 4 : 5;
            int xOffset = TrigLookupTable.sinHex(angle) >> shift;
            int yOffset = TrigLookupTable.cosHex(angle) >> shift;
            PanelAnchor anchor = resolvePanelAnchor();
            x = anchor.x() + xOffset;
            y = anchor.y() + yOffset;
        }

        private PanelAnchor resolvePanelAnchor() {
            if (index == 0) {
                return new PanelAnchor(getX(), getY());
            }
            PanelState previous = linkedParent();
            return new PanelAnchor(previous.x, previous.y);
        }

        private PanelState linkedParent() {
            int previousIndex = (secondRing ? 1 + PANEL_FRAMES.length : 1) + index - 1;
            return panels.get(previousIndex);
        }

        private void detach() {
            if (detached || center) {
                return;
            }
            detached = true;
            xVel = PANEL_DETACH_X_VEL[index];
            yVel = PANEL_DETACH_Y_VEL[index];
        }
    }

    private record PanelAnchor(int x, int y) {
    }
}
