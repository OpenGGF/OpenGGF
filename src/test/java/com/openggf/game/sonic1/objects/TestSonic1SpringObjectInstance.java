package com.openggf.game.sonic1.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidRoutineKind;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic1SpringObjectInstance {
    @Test
    void exposesFullSolidRoutineProfileForVerticalAndHorizontalSprings() {
        Sonic1SpringObjectInstance vertical = new Sonic1SpringObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x41, 0x00, 0, false, 0));
        Sonic1SpringObjectInstance horizontal = new Sonic1SpringObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x41, 0x10, 0, false, 0));

        SolidRoutineProfile verticalProfile = vertical.getSolidRoutineProfile();
        SolidRoutineProfile horizontalProfile = horizontal.getSolidRoutineProfile();

        assertEquals(SolidRoutineKind.FULL_SOLID, verticalProfile.kind());
        assertFalse(verticalProfile.inclusiveRightEdge());
        assertFalse(verticalProfile.bypassesOffscreenSolidGate());
        assertTrue(verticalProfile.stickyContactBuffer());
        assertEquals(SolidRoutineKind.FULL_SOLID, horizontalProfile.kind());
        assertFalse(horizontalProfile.inclusiveRightEdge());
        assertFalse(horizontalProfile.bypassesOffscreenSolidGate());
        assertTrue(horizontalProfile.stickyContactBuffer());
    }

    @Test
    void upSpringSuppressesSolidContactUntilAnimationResetCompletes() {
        Sonic1SpringObjectInstance spring = new Sonic1SpringObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x41, 0x00, 0, false, 0));
        spring.setServices(new StubObjectServices());
        spring.update(0, null);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x100);
        SolidContact standing = new SolidContact(true, false, false, true, false);

        assertTrue(spring.isSolidFor(player));
        spring.onSolidContact(player, standing, 0);
        assertEquals(-0x1000, player.getYSpeed());
        assertFalse(spring.isSolidFor(player));

        for (int frame = 1; frame <= 11; frame++) {
            player.setYSpeed((short) 0);
            spring.update(frame, player);
            assertFalse(spring.isSolidFor(player), "spring should ignore contact during ROM animation/reset frame " + frame);
            spring.onSolidContact(player, standing, frame);
            assertEquals(0, player.getYSpeed(), "spring relaunched while ROM routine does not call SolidObject at frame " + frame);
        }

        spring.update(12, player);
        assertTrue(spring.isSolidFor(player));
        spring.onSolidContact(player, standing, 12);
        assertEquals(-0x1000, player.getYSpeed());
    }
}
