package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.runtime.DdzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectRangeOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;

/** Shared lookups and ROM helpers for the Doomsday Zone objects. */
final class DdzObjectSupport {
    private DdzObjectSupport() {
    }

    static DdzZoneRuntimeState ddz(ObjectServices services) {
        var registry = services.zoneRuntimeRegistry();
        return registry == null ? null : S3kRuntimeStates.currentDdz(registry).orElse(null);
    }

    /** {@code _unkFAAE}: this frame's wrap offset, zero outside phase 2. */
    static int wrapOffset(ObjectServices services) {
        DdzZoneRuntimeState ddz = ddz(services);
        return ddz == null ? 0 : ddz.wrapOffset();
    }

    /** {@code _unkFA90}: this frame's camera X delta. */
    static int cameraDelta(ObjectServices services) {
        DdzZoneRuntimeState ddz = ddz(services);
        return ddz == null ? 0 : ddz.cameraDelta();
    }

    /** {@code _unkFA8E}: the flight controller, or {@code null}. */
    static DdzFlightControllerObjectInstance controller(ObjectServices services) {
        var manager = services.objectManager();
        if (manager == null) {
            return null;
        }
        for (ObjectInstance object : manager.getActiveObjects()) {
            if (object instanceof DdzFlightControllerObjectInstance controller && !controller.isDestroyed()) {
                return controller;
            }
        }
        return null;
    }

    /** {@code sub_82C6A}: subtract {@code $30000} from the autoscroll speed, floor {@code $10000} (signed). */
    static void penaliseScrollSpeed(DdzZoneRuntimeState ddz) {
        if (ddz == null) {
            return;
        }
        int speed = ddz.scrollSpeed() - 0x30000;
        ddz.setScrollSpeed(speed >= 0x10000 ? speed : 0x10000);
    }

    static AbstractPlayableSprite player(ObjectServices services) {
        return services.spriteManager().getMainPlayable();
    }

    /** {@code tst.b (Super_Sonic_Knux_flag).w}: DDZ hazards only act on a powered Player 1. */
    static boolean playerPowered(ObjectServices services) {
        AbstractPlayableSprite player = player(services);
        return player != null && player.isSuperSonic();
    }

    /**
     * {@code Check_InMyRange} (sonic3k.asm:179957-179981): the target's position lies inside the box
     * {@code [x + box[0], x + box[0] + box[1])} by {@code [y + box[2], y + box[2] + box[3])} of this
     * object. Signed word comparisons.
     */
    static boolean inMyRange(int x, int y, int targetX, int targetY, int[] box) {
        short left = (short) (x + box[0]);
        short tx = (short) targetX;
        if (tx < left) {
            return false;
        }
        if (tx >= (short) (left + box[1])) {
            return false;
        }
        short top = (short) (y + box[2]);
        short ty = (short) targetY;
        if (ty < top) {
            return false;
        }
        return ty < (short) (top + box[3]);
    }

    /** {@code Check_InTheirRange}: this object's position inside the box around the target. */
    static boolean inTheirRange(int x, int y, int targetX, int targetY, int[] box) {
        return inMyRange(targetX, targetY, x, y, box);
    }

    /**
     * {@code Sprite_OnScreen_Test} / {@code Sprite_CheckDelete}: the coarse X delete window against
     * {@code Camera_X_pos_coarse_back} as latched at frame start, not the camera the controller has
     * already scrolled this frame.
     */
    static boolean outOfRangeX(ObjectServices services, int x, int range) {
        DdzZoneRuntimeState state = ddz(services);
        int coarseBack = state == null || !state.controllerPresent()
                ? ObjectRangeOps.coarseCameraX(services.camera().getX() & 0xFFFF)
                : state.cameraXCoarseBack();
        return (((x & 0xFF80) - coarseBack) & 0xFFFF) > range;
    }

    /** {@code Sprite_CheckDeleteXY}'s extra Y test: {@code y - Camera_Y + $80 > $200}, unsigned. */
    static boolean outOfRangeY(ObjectServices services, int y) {
        return ((y - (services.camera().getY() & 0xFFFF) + 0x80) & 0xFFFF) > 0x200;
    }

    /**
     * {@code sub_8622C} (sonic3k.asm:181154-181215): the byte angle from ({@code fromX},{@code fromY})
     * towards ({@code toX},{@code toY}), 0 pointing down and {@code $40} right, built from
     * {@code (min << 5) / max} and the octant. Returns the low byte the DDZ callers use.
     */
    static int angleTowards(int fromX, int fromY, int toX, int toY) {
        int dx = (toX - fromX) & 0xFFFF;
        int dy = (toY - fromY) & 0xFFFF;
        int octant = 0;
        if ((short) dx < 0) {
            dx = (-dx) & 0xFFFF;
            octant += 8;
        }
        if ((short) dy < 0) {
            dy = (-dy) & 0xFFFF;
            octant += 4;
        }
        int d0 = dx;
        int d1 = dy;
        if (d1 < d0) {
            d0 = dy;
            d1 = dx;
            octant += 2;
        }
        if (d1 == 0) {
            return d0 & 0xFF;
        }
        int quotient = ((d0 << 5) & 0xFFFF) / d1;
        int angle = switch (octant) {
            case 0 -> quotient;
            case 2 -> 0x40 - quotient;
            case 4 -> 0x80 - quotient;
            case 6 -> quotient + 0x40;
            case 8 -> -quotient;
            case 10 -> quotient + 0xC0;
            case 12 -> quotient + 0x80;
            default -> 0xC0 - quotient;
        };
        return angle & 0xFF;
    }

    static S3kRawAnimation rawAnimations(ObjectServices services) {
        try {
            return S3kRawAnimation.load(services.romReader(), Sonic3kConstants.DDZ_RAW_ANIM_BASE_ADDR,
                    Sonic3kConstants.DDZ_RAW_ANIM_SIZE);
        } catch (IOException ex) {
            throw new IllegalStateException("DDZ raw animations", ex);
        }
    }
}
