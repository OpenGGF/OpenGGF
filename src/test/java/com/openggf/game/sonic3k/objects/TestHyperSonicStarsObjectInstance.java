package com.openggf.game.sonic3k.objects;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestHyperSonicStarsObjectInstance {

    @Test
    void ownsFourOrbitersAndCreatesFourDiagonalDashSparks() {
        AbstractPlayableSprite owner = hyperOwner();
        HyperSonicStarsObjectInstance stars = new HyperSonicStarsObjectInstance(owner);

        assertEquals(4, stars.orbitingStarCount());
        assertEquals(0, stars.visibleSparkCount());

        stars.triggerDashSparks();

        assertEquals(0, stars.visibleSparkCount(),
                "ROM anim flag is consumed on the star object's next update");
        stars.update(0, owner);
        assertEquals(4, stars.visibleSparkCount());
    }

    @Test
    void exposesFixedInvincibilityStarsOwnershipToThePowerUpSpawner() {
        AbstractPlayableSprite owner = hyperOwner();
        HyperSonicStarsObjectInstance stars = new HyperSonicStarsObjectInstance(owner);

        assertTrue(stars.isInvincibilityStars());
        assertSame(owner, stars.boundPlayer());
        assertTrue(stars.isPersistent(),
                "fixed Hyper-star slots must not be retired by ordinary out-of-range culling");
    }

    @Test
    void lifecycleEndsAsSoonAsTheHyperTierEnds() {
        AbstractPlayableSprite owner = mock(AbstractPlayableSprite.class);
        SuperStateController form = mock(SuperStateController.class);
        when(owner.getSuperStateController()).thenReturn(form);
        when(form.isHyperFormActive()).thenReturn(true, false);
        HyperSonicStarsObjectInstance stars = new HyperSonicStarsObjectInstance(owner);

        stars.update(0, owner);
        assertFalse(stars.isDestroyed());

        stars.update(1, owner);
        assertTrue(stars.isDestroyed());
    }

    @Test
    void childrenHonorIndependentOneThroughFourFrameInitDelays() throws Exception {
        HyperSonicStarsObjectInstance stars = new HyperSonicStarsObjectInstance(hyperOwner());
        Method updateChild = HyperSonicStarsObjectInstance.class
                .getDeclaredMethod("updateChild", int.class, boolean.class);
        updateChild.setAccessible(true);

        updateChild.invoke(stars, 0, true);
        updateChild.invoke(stars, 1, true);

        assertEquals(0, field(stars, "delay0"));
        assertEquals(0, field(stars, "frame0"));
        assertEquals(1, field(stars, "delay1"));
        assertEquals(6, field(stars, "frame1"));
        assertEquals(3, field(stars, "delay2"));
        assertEquals(4, field(stars, "delay3"));
    }

    @Test
    void nativeSparkAnimationRunsFramesThreeFourFiveWithGravity() throws Exception {
        HyperSonicStarsObjectInstance stars = new HyperSonicStarsObjectInstance(hyperOwner());
        stars.triggerDashSparks();
        Method updateSparks = HyperSonicStarsObjectInstance.class.getDeclaredMethod("updateSparks");
        updateSparks.setAccessible(true);

        for (int frame = 0; frame < 4; frame++) updateSparks.invoke(stars);
        assertEquals(4, field(stars, "sparkFrame"));
        assertEquals(-0x200 + 4 * 0x18, field(stars, "syv0"));
        for (int frame = 4; frame < 12; frame++) updateSparks.invoke(stars);
        assertEquals(0, stars.visibleSparkCount());
    }

    @Test
    void orbitUsesRomSineForXAndCosineForY() throws Exception {
        HyperSonicStarsObjectInstance stars = new HyperSonicStarsObjectInstance(hyperOwner());
        Method updateChild = HyperSonicStarsObjectInstance.class
                .getDeclaredMethod("updateChild", int.class, boolean.class);
        updateChild.setAccessible(true);
        Field angle = HyperSonicStarsObjectInstance.class.getDeclaredField("angle0");
        angle.setAccessible(true);
        angle.setInt(stars, 0x10);

        updateChild.invoke(stars, 0, true);

        assertEquals(com.openggf.physics.TrigLookupTable.sinHex(0x10) << 3,
                field(stars, "xAcc0"));
        assertEquals(com.openggf.physics.TrigLookupTable.cosHex(0x10) << 3,
                field(stars, "yAcc0"));
        assertEquals((byte) (field(stars, "xAcc0") >> 8), field(stars, "x0"));
        assertEquals((byte) (field(stars, "yAcc0") >> 8), field(stars, "y0"));
    }

    @Test
    void rewindRoundTripPreservesMidOrbitAndMidSparkStateAndRebindsOwner() throws Exception {
        AbstractPlayableSprite owner = hyperOwner();
        HyperSonicStarsObjectInstance stars = new HyperSonicStarsObjectInstance(owner);
        Method updateChild = HyperSonicStarsObjectInstance.class
                .getDeclaredMethod("updateChild", int.class, boolean.class);
        Method updateSparks = HyperSonicStarsObjectInstance.class.getDeclaredMethod("updateSparks");
        updateChild.setAccessible(true);
        updateSparks.setAccessible(true);
        updateChild.invoke(stars, 0, true);
        stars.triggerDashSparks();
        for (int i = 0; i < 5; i++) updateSparks.invoke(stars);
        int expectedAngle = field(stars, "angle0");
        int expectedSparkX = field(stars, "sx0");
        int expectedSparkFrame = field(stars, "sparkFrame");
        RewindIdentityTable identities = new RewindIdentityTable();
        identities.registerPlayer(owner, PlayerRefId.mainPlayer());
        RewindCaptureContext identityContext =
                RewindCaptureContext.withIdentityTable(identities);
        PerObjectRewindSnapshot snapshot = stars.captureRewindState(identityContext);

        updateChild.invoke(stars, 0, true);
        for (int i = 0; i < 4; i++) updateSparks.invoke(stars);
        stars.restoreRewindState(snapshot, identityContext);

        assertEquals(expectedAngle, field(stars, "angle0"));
        assertEquals(expectedSparkX, field(stars, "sx0"));
        assertEquals(expectedSparkFrame, field(stars, "sparkFrame"));

        PerObjectRewindSnapshot identitySnapshot =
                stars.captureRewindState(identityContext);
        RewindRecreateContext context = mock(RewindRecreateContext.class);
        when(context.spawn()).thenReturn(stars.getSpawn());
        HyperSonicStarsObjectInstance recreated =
                (HyperSonicStarsObjectInstance) stars.recreateForRewind(context);
        recreated.restoreRewindState(identitySnapshot, identityContext);
        assertTrue(recreated.isBoundTo(owner));
    }

    private static AbstractPlayableSprite hyperOwner() {
        AbstractPlayableSprite owner = mock(AbstractPlayableSprite.class);
        SuperStateController form = mock(SuperStateController.class);
        when(owner.getSuperStateController()).thenReturn(form);
        when(form.isHyperFormActive()).thenReturn(true);
        return owner;
    }

    private static int field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
