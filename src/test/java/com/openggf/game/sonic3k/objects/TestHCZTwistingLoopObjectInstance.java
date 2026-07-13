package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestHCZTwistingLoopObjectInstance {
    @Test
    void sidekickProcessingExtendsNativeP2SemanticsIndependently() {
        HCZTwistingLoopObjectInstance loop = new HCZTwistingLoopObjectInstance(
                new ObjectSpawn(0x0840, 0x0120, 0x2A, 0x00, 0, false, 0));
        TestPlayableSprite nativeP2 = playerAtLoopEntry();
        TestPlayableSprite extraSidekick = playerAtLoopEntry();
        TestPlayableSprite thirdSidekick = playerAtLoopEntry();
        loop.setServices(new TestObjectServices().withSidekicks(
                List.of(nativeP2, extraSidekick, thirdSidekick)));

        TestPlayableSprite main = playerAtLoopEntry();

        loop.update(0, main);

        assertTrue(main.isObjectControlled(),
                "The direct update player should remain the P1 fallback when services cannot resolve main");
        assertNativeBits0To6Control(nativeP2);
        assertNativeBits0To6Control(extraSidekick);
        assertNativeBits0To6Control(thirdSidekick);
    }

    private static TestPlayableSprite playerAtLoopEntry() {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x0840);
        player.setCentreY((short) 0x0128);
        player.setGSpeed((short) 0x0800);
        player.setXSpeed((short) 0x0800);
        player.setObjectControlled(false);
        return player;
    }

    private static void assertNativeBits0To6Control(TestPlayableSprite player) {
        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlAllowsCpu());
        assertTrue(player.isObjectControlSuppressesMovement());
        assertFalse(player.isTouchResponseSuppressedByObjectControl());
    }
}
