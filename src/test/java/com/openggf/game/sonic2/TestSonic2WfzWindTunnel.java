package com.openggf.game.sonic2;

import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic2WfzWindTunnel {

    @Test
    void platingHoldingFlagSkipsWindTunnelLeaveAnimation() throws Exception {
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic", (short) 0x1710, (short) 0x04C0);

        invokeWindTunnel(provider, player);
        assertEquals(Sonic2AnimationIds.FLOAT2.id(), player.getAnimationId());

        player.setAnimationId(Sonic2AnimationIds.HANG);
        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(player);
        provider.setWfzWindTunnelHolding(true);
        invokeWindTunnel(provider, player);

        assertTrue(provider.isWfzWindTunnelHolding());
        assertEquals(Sonic2AnimationIds.HANG.id(), player.getAnimationId(),
                "WindTunnel_holding_flag returns before the active tunnel's Walk leave write");
    }

    @Test
    void clearingHoldingFlagRestoresNormalWindTunnelLeavePath() throws Exception {
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider();
        TestablePlayableSprite player = new TestablePlayableSprite(
                "sonic", (short) 0x1710, (short) 0x04C0);

        invokeWindTunnel(provider, player);
        player.setAnimationId(Sonic2AnimationIds.HANG);
        provider.setWfzWindTunnelHolding(false);
        player.setCentreX((short) 0x1AF0);
        invokeWindTunnel(provider, player);

        assertEquals(Sonic2AnimationIds.WALK.id(), player.getAnimationId());
    }

    @Test
    void patchedWindReleasesAbilitiesAndClampsAtEachTunnelTop() throws Exception {
        Sonic2ZoneFeatureProvider provider = new Sonic2ZoneFeatureProvider(
                new Sonic2WindTunnelProfile(0x420, true, true));
        TestablePlayableSprite player = new TestablePlayableSprite("knuckles", (short) 0, (short) 0);
        player.setDirectionalInputPressed(true, false, false, false);
        for (int[] tunnel : new int[][] {{0x1600, 0x420}, {0x2200, 0x618}}) {
            player.setCentreX((short) tunnel[0]);
            player.setCentreY((short) (tunnel[1] + 1));
            player.setRollingJump(true);
            player.setDoubleJumpFlag(1);
            player.setSubpixelRaw(0x3456, 0x789A);
            invokeWindTunnel(provider, player);
            assertEquals(tunnel[1], player.getCentreY());
            assertEquals(0x789A, player.getYSubpixelRaw());
            assertEquals(0, player.getDoubleJumpFlag());
            org.junit.jupiter.api.Assertions.assertFalse(player.getRollingJump());
            invokeWindTunnel(provider, player);
            assertEquals(tunnel[1], player.getCentreY(), "Up must stop at the tunnel top");
        }
    }

    @Test
    void firstTunnelShorteningAndStockUnclampedEntryRemainDistinct() throws Exception {
        TestablePlayableSprite player = new TestablePlayableSprite("knuckles", (short) 0, (short) 0);
        player.setCentreX((short) 0x1600);
        player.setCentreY((short) 0x400);
        player.setRollingJump(true);
        player.setDoubleJumpFlag(1);
        player.setDirectionalInputPressed(true, false, false, false);
        invokeWindTunnel(new Sonic2ZoneFeatureProvider(new Sonic2WindTunnelProfile(0x420, true, true)), player);
        assertEquals(0x400, player.getCentreY());
        assertEquals(0x1600, player.getCentreX());
        invokeWindTunnel(new Sonic2ZoneFeatureProvider(), player);
        assertEquals(0x3FF, player.getCentreY());
        assertTrue(player.getRollingJump());
        assertEquals(1, player.getDoubleJumpFlag());
    }

    private static void invokeWindTunnel(
            Sonic2ZoneFeatureProvider provider, AbstractPlayableSprite player) throws Exception {
        Method method = Sonic2ZoneFeatureProvider.class.getDeclaredMethod(
                "updateWfzWindTunnel", AbstractPlayableSprite.class);
        method.setAccessible(true);
        method.invoke(provider, player);
    }
}
