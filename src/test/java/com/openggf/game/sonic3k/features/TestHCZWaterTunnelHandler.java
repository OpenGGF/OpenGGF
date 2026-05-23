package com.openggf.game.sonic3k.features;

import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestHCZWaterTunnelHandler {

    @AfterEach
    void resetTunnelHandler() {
        HCZWaterTunnelHandler.reset();
    }

    @Test
    void updateUsesNativeP2QueryAndIgnoresExtraSidekicks() {
        AbstractPlayableSprite main = playerInsideFirstHcz1Tunnel();
        AbstractPlayableSprite nativeP2 = playerInsideFirstHcz1Tunnel();
        AbstractPlayableSprite extraSidekick = playerInsideFirstHcz1Tunnel();
        ObjectPlayerQuery query = query(main, nativeP2, extraSidekick);

        HCZWaterTunnelHandler.update(query, 0);

        assertTrue(HCZWaterTunnelHandler.isPlayerInTunnel(0));
        assertTrue(HCZWaterTunnelHandler.isPlayerInTunnel(1));
        verify(nativeP2).setForcedAnimationId(Sonic3kAnimationIds.FLOAT2);
        verify(nativeP2).move(anyShort(), anyShort());
        verify(nativeP2).setAir(true);
        verify(extraSidekick, never()).setForcedAnimationId(Sonic3kAnimationIds.FLOAT2);
        verify(extraSidekick, never()).move(anyShort(), anyShort());
        verify(extraSidekick, never()).setAir(anyBoolean());
    }

    @Test
    void extraSidekickDoesNotShareNativeP2TunnelState() {
        AbstractPlayableSprite main = playerOutsideTunnels();
        AbstractPlayableSprite nativeP2 = playerOutsideTunnels();
        AbstractPlayableSprite extraSidekick = playerInsideFirstHcz1Tunnel();
        ObjectPlayerQuery query = query(main, nativeP2, extraSidekick);

        HCZWaterTunnelHandler.update(query, 0);

        assertFalse(HCZWaterTunnelHandler.isPlayerInTunnel(0));
        assertFalse(HCZWaterTunnelHandler.isPlayerInTunnel(1));
        verify(extraSidekick, never()).setForcedAnimationId(Sonic3kAnimationIds.FLOAT2);
        verify(extraSidekick, never()).move(anyShort(), anyShort());
    }

    @Test
    void p1AndNativeP2TunnelFlagsRemainIndependent() {
        AbstractPlayableSprite main = playerInsideFirstHcz1Tunnel();
        AbstractPlayableSprite nativeP2 = playerOutsideTunnels();
        ObjectPlayerQuery query = query(main, nativeP2);

        HCZWaterTunnelHandler.update(query, 0);

        assertTrue(HCZWaterTunnelHandler.isPlayerInTunnel(0));
        assertFalse(HCZWaterTunnelHandler.isPlayerInTunnel(1));
        verify(main).setForcedAnimationId(Sonic3kAnimationIds.FLOAT2);
        verify(nativeP2, never()).setForcedAnimationId(Sonic3kAnimationIds.FLOAT2);
    }

    @Test
    void p1AndNativeP2ExitTimersRemainIndependent() {
        AbstractPlayableSprite main = playerInsideFirstHcz1Tunnel();
        AbstractPlayableSprite nativeP2 = playerInsideFirstHcz1Tunnel();
        HCZWaterTunnelHandler.update(query(main, nativeP2), 0);

        when(main.getCentreX()).thenReturn((short) 0x0200);
        when(main.getCentreY()).thenReturn((short) 0x0200);
        HCZWaterTunnelHandler.update(query(main, nativeP2), 0);

        verify(main).setForcedAnimationId(Sonic3kAnimationIds.HURT);
        verify(nativeP2, never()).setForcedAnimationId(Sonic3kAnimationIds.HURT);
        assertFalse(HCZWaterTunnelHandler.isPlayerInTunnel(0));
        assertTrue(HCZWaterTunnelHandler.isPlayerInTunnel(1));
    }

    private static ObjectPlayerQuery query(AbstractPlayableSprite main, AbstractPlayableSprite... sidekicks) {
        return new ObjectPlayerQuery(() -> main, () -> List.of(sidekicks));
    }

    private static AbstractPlayableSprite playerInsideFirstHcz1Tunnel() {
        return playerAt(0x0400, 0x05A0);
    }

    private static AbstractPlayableSprite playerOutsideTunnels() {
        return playerAt(0x0200, 0x0200);
    }

    private static AbstractPlayableSprite playerAt(int centreX, int centreY) {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) centreX);
        when(player.getCentreY()).thenReturn((short) centreY);
        when(player.isDebugMode()).thenReturn(false);
        when(player.isHurt()).thenReturn(false);
        when(player.getDead()).thenReturn(false);
        when(player.isObjectControlled()).thenReturn(false);
        return player;
    }
}
