package com.openggf.game.soniccd.objects.ccz;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The player's pinball flipper launch, {@code ObjSonic_ChkFlipper} (Level/_Objects/Sonic/Main.asm:3112-3158) together with
 * the velocity add that {@code ObjSonic_CheckJump} performs with its result ({@code ObjSonic_CheckJump .GotAngle}, Level/_Objects/Sonic/Main.asm:1798, 1815-1825).
 * The Collision Chaos player profile's {@code flipperLaunch} hook calls {@link #launch}; the flipper object stays the shared
 * lane's (it implements {@link CczFlipper}).
 *
 * <p>When the player stands on a flipper the launch plays {@code FM_SPRING}, starts the flipper's swing animation, takes
 * the angle {@code CalcAngle(flipperX - playerX, flipperY + 24 - playerY)} and the speed
 * {@code -$A00 + (-$A00 * force) / 64}, where force is {@code playerX - flipperX + oWidth}, or {@code 64 - force} for a flipper
 * that is not flipped horizontally; it adds {@code speed * cos / 256} to X and {@code speed * sin / 256} to Y velocity.
 * The caller keeps the rest of the jump ({@code .NotOnObj} is skipped; the jump state, radii and {@code FM_JUMP} stay the
 * player's).
 */
public final class CczFlipperLaunch {
    /** FM_SPRING (Sound Drivers/FM/_Sound IDs.inc:18). */
    public static final int SPRING_SOUND = 0x98;

    /** The angle byte and signed speed {@code ObjSonic_ChkFlipper} returns in d0 and d2. */
    public record Result(int angle, int speed) { }

    private CczFlipperLaunch() { }

    /** The routine's arithmetic for a player at ({@code playerX}, {@code playerY}) on {@code flipper} (Level/_Objects/Sonic/Main.asm:3125-3152). */
    public static Result compute(CczFlipper flipper, int playerX, int playerY) {
        int d1 = (short) (flipper.flipperX() - playerX);
        int d2 = (short) (flipper.flipperY() + 24 - playerY);
        int angle = TrigLookupTable.calcAngle((short) d1, (short) d2) & 0xFF;
        int force = (short) (playerX - flipper.flipperX() + flipper.flipperWidth());
        if (!flipper.flipperFlippedHorizontally()) force = (short) (64 - force);
        int speed = -0xA00;
        int scaled = (-0xA00 * force) / 64;                  // muls.w d3,d1; divs.w #64,d1 truncates toward zero
        return new Result(angle, (short) (speed + (short) scaled));
    }

    /**
     * {@code ObjSonic_CheckJump .GotAngle} for a flipper: returns false (the ROM's "not on a flipper", Z clear) unless the
     * object the player stands on is a {@link CczFlipper}; otherwise plays the sound, swings the flipper and adds the
     * launch to the player's velocity.
     */
    public static boolean launch(AbstractPlayableSprite player, ObjectServices services) {
        if (!player.isOnObject()) return false;                           // btst #3,oFlags(a0)
        ObjectInstance standing = CczFlipperObject.standingOn(player);
        if (standing == null) standing = player.getLatchedSolidObjectInstance();
        if (!(standing instanceof CczFlipper flipper)) return false;      // cmpi.b #$1E,oID(a1)
        if (services != null) services.playSfx(SPRING_SOUND);
        flipper.flipperSwing();
        Result result = compute(flipper, player.getCentreX(), player.getCentreY());
        int cos = TrigLookupTable.cosHex(result.angle());
        int sin = TrigLookupTable.sinHex(result.angle());
        player.setXSpeed((short) (player.getXSpeed() + ((result.speed() * cos) >> 8)));
        player.setYSpeed((short) (player.getYSpeed() + ((result.speed() * sin) >> 8)));
        return true;
    }
}
