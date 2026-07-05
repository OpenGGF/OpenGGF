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
        // ROM Spring routines call SolidObject, whose x-range check rejects only when
        // d0 > 2*halfWidth (Solid_ChkCollision `cmp d3,d0; bhi`), so the right edge
        // (Sonic flush against the object's right face) still collides — inclusive.
        // Required for the LR spring to register the side contact that fires its push
        // bounce when Sonic falls flush against it (S1 SYZ1 f502 -> f816).
        assertTrue(verticalProfile.inclusiveRightEdge());
        assertFalse(verticalProfile.bypassesOffscreenSolidGate());
        assertTrue(verticalProfile.stickyContactBuffer());
        assertEquals(SolidRoutineKind.FULL_SOLID, horizontalProfile.kind());
        assertTrue(horizontalProfile.inclusiveRightEdge());
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

    /**
     * ROM: Spring_BounceUp (docs/s1disasm/_incObj/41 Springs.asm:96)
     * {@code move.b #2,obRoutine(a1)} unconditionally forces Sonic's OWN object
     * routine to 2 (Sonic_Control) the instant an up-spring fires — regardless
     * of what routine he was previously in. Routine 4 is the hurt/knockback
     * routine (see AbstractPlayableSprite.hurt: "Mirrors ROM routine=4 check").
     * So a spring landing during the hurt knockback overrides routine 4 back to
     * 2 immediately, restoring player control the same frame the spring fires
     * — it does not wait for a normal grounded landing to clear hurt the way
     * Sonic_HurtStop/Sonic_ResetOnFloor would.
     */
    @Test
    void upSpringRestoresControlImmediatelyEvenWhileHurt() {
        Sonic1SpringObjectInstance spring = new Sonic1SpringObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x41, 0x00, 0, false, 0));
        spring.setServices(new StubObjectServices());
        spring.update(0, null);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x100);
        player.setHurt(true);
        player.setAirForTest(true);

        SolidContact standing = new SolidContact(true, false, false, true, false);
        spring.onSolidContact(player, standing, 0);

        assertEquals(-0x1000, player.getYSpeed(), "spring must still impart its bounce velocity");
        assertFalse(player.isHurt(),
                "ROM Spring_BounceUp pokes obRoutine(a1)=2 unconditionally, ending the hurt "
                        + "routine on contact; the engine must restore control (D-pad/jump) the "
                        + "same frame instead of waiting for a normal grounded hurt-stop landing");
    }

    /**
     * ROM: Spring_BounceDwn (docs/s1disasm/_incObj/41 Springs.asm:203) mirrors
     * Spring_BounceUp's unconditional {@code move.b #2,obRoutine(a1)}.
     */
    @Test
    void downSpringRestoresControlImmediatelyEvenWhileHurt() {
        Sonic1SpringObjectInstance spring = new Sonic1SpringObjectInstance(
                new ObjectSpawn(0x100, 0x100, 0x41, 0x20, 0, false, 0));
        spring.setServices(new StubObjectServices());
        spring.update(0, null);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x100);
        player.setHurt(true);
        player.setAirForTest(true);

        SolidContact touchBottom = new SolidContact(false, false, true, false, false);
        spring.onSolidContact(player, touchBottom, 0);

        assertEquals(0x1000, player.getYSpeed(), "down spring bounces downward (negated strength)");
        assertFalse(player.isHurt(), "down spring must also restore control per Spring_BounceDwn");
    }
}
