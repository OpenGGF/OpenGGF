package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * Drum platform, object $28 ({@code ObjDrumPlatform}, Level/Collision Chaos/Objects/Drum Platform/Main.asm:25-237, legacy
 * ABI: {@code CC_LEGACY_DRUM_PLATFORM_ABI} is 1 in every Collision Chaos stage). A $18 wide top-solid platform that
 * bounces through an 18-step cycle ({@code ObjDrumPlatform_MotionData}, :208-227): steps 0-1 non-solid,
 * 2-5 solid, 6-17 non-solid. Each step lasts four eligible frames; a frame is eligible when the time zone is not the past and
 * {@code aniArtTimers} is zero (:158-163). The subtype is the starting step. It is solid only once drawn
 * ({@code btst #7,oSprFlags}, :65-67), with a past-only radius override by mapping frame (:68-91).
 *
 * <p>The despawn is the object's own chunk test on the base X with no on-screen exemption (:35-49).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class CczDrumPlatform extends CczObject implements ReevaluatingSolidObject,
        ObjectVisualFacing.NonDirectional {
    /** ObjDrumPlatform_MotionData: velocity (whole pixels), Y radius, mapping frame (:208-227). */
    private static final int[][] MOTION = {
            {1, 8, 0}, {5, 8, 1}, {6, 0xC, 2}, {8, 0xB, 3}, {8, 0xB, 4}, {8, 0xB, 5}, {6, 0xB, 6}, {5, 8, 7},
            {1, 8, 8}, {0xFF, 4, 9}, {0xFB, 4, 0xA}, {0xFA, 8, 0xA}, {0xF8, 8, 0xA}, {0xF8, 8, 0xA},
            {0xF8, 8, 0xA}, {0xFA, 8, 0xA}, {0xFB, 8, 0xA}, {0xFF, 4, 0xB}};
    /** ObjDrumPlatform_Priorities (:251-259). */
    private static final int[] PRIORITIES = {5, 4, 3, 2, 2, 2, 3, 4, 5, 6, 6, 6};

    private int baseX;               // oVar32
    private int velocity;            // oVar36: byte at $36, so a whole-pixel 8.8 velocity
    private int stepTimer;           // oVar3A
    private int step;                // oVar3B
    private int yVel;

    public CczDrumPlatform(ObjectSpawn spawn) {
        super(spawn, "Drum platform");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> init();
            case 2 -> early(player);
            case 4 -> solidCycle();
            default -> late(player);
        }
    }

    /** ObjDrumPlatform_Init (:100-126): ends with an rts, so the first cycle runs next frame. */
    private void init() {
        tileWord = CczObjectArt.DRUM_PLATFORM_TILE;
        priority = 3;
        width = 0x18;
        baseX = x;
        routine = 2;
        step = spawn.subtype() & 0xFF;
        if (step < 2) return;
        routine += 2;
        if (step < 6) return;
        routine += 2;
    }

    private boolean eligible() {
        if (timeZone() == 0) return false;                                 // tst.b timeZone; beq .End
        return aniArtTimer() == 0;                                         // tst.b aniArtTimers; bne .End
    }

    private int aniArtTimer() {
        var levels = services().levelManager();
        var manager = levels == null ? null : levels.getAnimatedPatternManager();
        return manager instanceof CczAnimationTimer timer ? timer.aniArtTimer() & 0xFF : 0;
    }

    /** ObjDrumPlatform_EarlyNonSolidCycle (:130-153). */
    private void early(PlayableEntity player) {
        while (true) {
            if (stepTimer == 0) {
                getOffObject(player);
                loadMotionStep();
                return;
            }
            if (!eligible()) return;
            applyVelocity();
            stepTimer = (stepTimer - 1) & 0xFF;
            if (stepTimer != 0) return;
            step = (step + 1) & 0xFF;
            if (step >= 2) {
                routine += 2;
                return;
            }
        }
    }

    /** ObjDrumPlatform_SolidCycle (:158-181). */
    private void solidCycle() {
        while (true) {
            checkTopSolid();
            if (stepTimer == 0) {
                loadMotionStep();
                return;
            }
            if (!eligible()) return;
            applyVelocity();
            stepTimer = (stepTimer - 1) & 0xFF;
            if (stepTimer != 0) return;
            step = (step + 1) & 0xFF;
            if (step >= 6) {
                routine += 2;
                return;
            }
        }
    }

    /** ObjDrumPlatform_LateNonSolidCycle (:186-210). */
    private void late(PlayableEntity player) {
        while (true) {
            if (stepTimer == 0) {
                getOffObject(player);
                loadMotionStep();
                return;
            }
            if (!eligible()) return;
            applyVelocity();
            stepTimer = (stepTimer - 1) & 0xFF;
            if (stepTimer != 0) return;
            step = (step + 1) & 0xFF;
            if (step >= 0x12) {
                stepTimer = 0;                                             // clr.w oDrumPlatformStepTimer: timer and step
                step = 0;
                routine = 2;
                return;
            }
        }
    }

    private void getOffObject(PlayableEntity player) {
        var objects = services().objectManager();
        if (objects != null && player != null) ObjectManagerInternalAccess.releaseFromReevaluatingSolid(objects, player, this);
    }

    /** ObjDrumPlatform_CheckTopSolid (:62-96). */
    private void checkTopSolid() {
        if (!renderFlagOnScreen()) return;                                 // btst #7,oSprFlags; beq .End
        if (timeZone() == 0) {
            int radius = -1;
            if (mapFrame == 2) radius = 8;
            else if (mapFrame >= 3 && mapFrame <= 6) radius = 5;
            if (radius >= 0) yRadius = radius;                             // .SetHeight
        }
        solidObject(Entry.TOP);
    }

    /** ObjDrumPlatform_ApplyVelocity (:226-233). */
    private void applyVelocity() {
        yVel = (short) (velocity << 8);
        addToY(yVel << 8);
    }

    /** ObjDrumPlatform_LoadMotionStep and _SetPriority (:238-249, 256-262). */
    private void loadMotionStep() {
        int[] record = MOTION[Math.min(step, MOTION.length - 1)];
        velocity = (byte) record[0];
        yRadius = record[1];
        mapFrame = record[2];
        stepTimer = 4;
        priority = PRIORITIES[mapFrame];
    }

    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }
    @Override public int solidYVelocity() { return yVel; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return baseX; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        return routine != 0 && outsideChunkRange(baseX, cameraX);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0) return;
        CdObjectArtProvider provider = provider();
        if (provider == null) return;
        String key = CczObjectArt.key(provider, CczObjectArt.DRUM_PLATFORM, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }

    public int step() { return step; }
    public int stepTimer() { return stepTimer; }
}
