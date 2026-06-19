package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * Visible dynamic child created by S3K's fixed {@code Obj_AirCountdown}
 * controller.
 *
 * <p>ROM refs: visible child init/update and water-surface delete paths at
 * docs/skdisasm/sonic3k.asm:33306-33370. Fixed controllers live outside
 * dynamic SST, but {@code AirCountdown_MakeItem} allocates these children via
 * the normal dynamic {@code AllocateObject} scan (sonic3k.asm:33591-33610).
 */
public final class S3kAirCountdownObjectInstance extends AbstractObjectInstance implements RewindRecreatable {
    private static final int ROUTINE_INIT = 0x00;
    private static final int ROUTINE_RISE = 0x02;
    private static final int ROUTINE_CHECK_WATER = 0x04;
    private static final int ROUTINE_DELETE = 0x08;
    private static final int ANIM_REGULAR_BUBBLE = 0x06;
    private static final int ANIM_SURFACE_POP = 0x0D;
    private static final int[] WOBBLE = {
            0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2,
            2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2,
            2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0,
            0, -1, -1, -1, -1, -1, -2, -2, -2, -2, -2, -3, -3, -3, -3, -3,
            -3, -3, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4,
            -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -3,
            -3, -3, -3, -3, -3, -3, -2, -2, -2, -2, -2, -1, -1, -1, -1, -1
    };

    private int routine;
    // Non-final so the generic field capturer reapplies it after generic
    // rewind recreate supplies placeholder constructor values.
    private int subtype;
    private int x;
    private int y;
    private int ySubpixel;
    private int yVel;
    private int renderFlags;
    private int anim;
    private int animFrame;
    private int animTimer;
    private int mappingFrame;
    private int obj34;
    private int obj3c;
    private int angle;
    // Non-final so the generic field capturer reapplies it after a rewind recreate.
    private int initialDisplayTimer;

    public S3kAirCountdownObjectInstance(int x, int y, int subtype, int angle) {
        this(x, y, subtype, angle, 0);
    }

    public S3kAirCountdownObjectInstance(int x, int y, int subtype, int angle, int displayTimer) {
        super(null, "AirCountdown");
        this.x = x & 0xFFFF;
        this.y = y & 0xFFFF;
        this.subtype = subtype & 0xFF;
        this.angle = angle & 0xFF;
        this.initialDisplayTimer = displayTimer & 0xFFFF;
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        ObjectSpawn capturedSpawn = ctx.spawn();
        int spawnX = capturedSpawn != null ? capturedSpawn.x() : 0;
        int spawnY = capturedSpawn != null ? capturedSpawn.y() : 0;
        return new S3kAirCountdownObjectInstance(spawnX, spawnY, 0, 0, 0);
    }

    @Override
    public int getX() {
        return x & 0xFFFF;
    }

    @Override
    public int getY() {
        return y & 0xFFFF;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (routine == ROUTINE_DELETE) {
            setDestroyed(true);
            return;
        }
        boolean initialisedThisFrame = false;
        if (routine == ROUTINE_INIT) {
            routine = ROUTINE_RISE;
            yVel = 0xFF00;
            renderFlags = 0x84;
            anim = subtype & 0xFF;
            initialiseAnimationFrame();
            mappingFrame = 0;
            obj34 = x & 0xFFFF;
            obj3c = initialDisplayTimer;
            initialisedThisFrame = true;
        }

        if (routine == ROUTINE_RISE && !initialisedThisFrame) {
            animateRegularBubble();
        }
        showNumberIfTimerExpires();
        if (hasReachedWaterSurface()) {
            routine = ROUTINE_DELETE;
            anim = ANIM_SURFACE_POP;
            animTimer = 0;
            return;
        }

        moveSprite2();
        applyWobble();
        if ((renderFlags & 0x80) == 0) {
            setDestroyed(true);
            return;
        }
        renderFlags = isWithinSolidContactBounds()
                ? (renderFlags | 0x80)
                : (renderFlags & 0x7F);
    }

    private boolean hasReachedWaterSurface() {
        try {
            WaterSystem waterSystem = services().waterSystem();
            int zoneId = services().featureZoneId();
            int actId = services().featureActId();
            if (!waterSystem.hasWater(zoneId, actId)) {
                return false;
            }
            return (short) ((y & 0xFFFF) - waterSystem.getWaterLevelY(zoneId, actId)) <= 0;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    private void moveSprite2() {
        int fixedY = ((y & 0xFFFF) << 8) | (ySubpixel & 0xFF);
        fixedY = (fixedY + (short) yVel) & 0xFFFFFF;
        y = (fixedY >> 8) & 0xFFFF;
        ySubpixel = fixedY & 0xFF;
    }

    private void initialiseAnimationFrame() {
        if (anim == ANIM_REGULAR_BUBBLE) {
            animFrame = 1;
            animTimer = 0x0E;
            mappingFrame = 0;
            return;
        }
        animFrame = 0;
        animTimer = 0;
        mappingFrame = 0;
    }

    private void animateRegularBubble() {
        if (anim != ANIM_REGULAR_BUBBLE) {
            return;
        }
        if (animFrame == 0) {
            initialiseAnimationFrame();
            return;
        }
        if (animTimer > 0) {
            animTimer--;
            return;
        }

        if (animFrame < 3) {
            mappingFrame = animFrame;
            animFrame++;
            animTimer = 0x0E;
            return;
        }

        // Ani_AirCountdown byte_18718 ends with $FC. Animate_Sprite advances
        // the routine from AirCountdown_Animate to AirCountdown_ChkWater
        // (docs/skdisasm/General/Sprites/Bubbles/Anim - Air Countdown.asm).
        routine = ROUTINE_CHECK_WATER;
    }

    private void showNumberIfTimerExpires() {
        if (obj3c == 0) {
            return;
        }
        obj3c = (short) ((obj3c - 1) & 0xFFFF);
        if (obj3c != 0 || anim >= ANIM_REGULAR_BUBBLE + 1) {
            return;
        }
        // AirCountdown_ShowNumber converts the moving child into the fixed
        // screen-space number display for $0F frames
        // (docs/skdisasm/sonic3k.asm:33410-33432).
        obj3c = 0x0F;
        yVel = 0;
        renderFlags = 0x80;
        routine = ROUTINE_CHECK_WATER + 8;
    }

    private void applyWobble() {
        int offset = WOBBLE[angle & 0x7F];
        angle = (angle + 1) & 0xFF;
        x = (obj34 + offset) & 0xFFFF;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Known visual limitation: S3K AirCountdown uses uncached ROM art/DPLC
        // (ArtUnc_AirCountdown), which is not yet wired into the object renderer.
        // The production object still preserves lifecycle, subtype, and timer
        // state so gameplay/RNG cadence remains native.
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }

    @Override
    public String traceDebugDetails() {
        return String.format("r=%02X sub=%02X yv=%04X rf=%02X anim=%02X af=%02X tm=%02X map=%02X $34=%04X $3C=%04X",
                routine & 0xFF,
                subtype & 0xFF,
                yVel & 0xFFFF,
                renderFlags & 0xFF,
                anim & 0xFF,
                animFrame & 0xFF,
                animTimer & 0xFF,
                mappingFrame & 0xFF,
                obj34 & 0xFFFF,
                obj3c & 0xFFFF);
    }
}
