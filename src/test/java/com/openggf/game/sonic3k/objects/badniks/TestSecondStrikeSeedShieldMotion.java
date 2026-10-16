package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ShieldProjectileMotion;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** Literal ROM angle/velocity oracles for the narrow shield recipe reused by Second Strike seeds. */
class TestSecondStrikeSeedShieldMotion {
    @AfterEach void cleanup() { AbstractObjectInstance.resetCameraBoundsForTests(); }

    @ParameterizedTest
    @CsvSource({
            "484,300,2048,0,508,300",
            "516,300,-2048,0,492,300",
            "500,284,0,2048,500,308",
            "500,316,0,-2048,500,292",
            "484,284,1448,1448,505,305",
            "516,316,-1448,-1448,494,294"
    })
    void nativeAndCreatorRecipeRetainRadialEightPixelShieldReflection(
            int playerX, int playerY, int xVelocity, int yVelocity, int nextX, int nextY) {
        AbstractObjectInstance.updateCameraBounds(0, 0, 1000, 800, 0);
        assertEquals(new ShieldProjectileMotion.Velocity(xVelocity, yVelocity),
                ShieldProjectileMotion.awayFrom(500, 300, playerX, playerY));
        var seed = new S3kBadnikProjectileInstance(new ObjectSpawn(499, 302, 0x8c, 0, 0, false, 302),
                "", 4, 499, 302, 0x100, -0x200, 0x38, 0x18, 5, false);
        seed.setServices(new StubObjectServices());
        var player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(player, playerX);
        com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(player, playerY);
        // S3KBadnikProjectile_Init enables ObjDat collision on the first
        // dispatch and falls through MoveSprite_LightGravity: +$100/-$200
        // lands the seed on (500,300) with zero subpixels before the deflect.
        assertEquals(0, seed.getCollisionFlags());
        seed.update(0, player);
        assertEquals(500, seed.getX()); assertEquals(300, seed.getY());
        assertEquals(0x98, seed.getCollisionFlags());
        assertTrue(seed.onShieldDeflect(player));
        assertEquals(0, seed.getCollisionFlags());
        seed.update(0, player);
        assertEquals(nextX, seed.getX()); assertEquals(nextY, seed.getY());
    }
}
