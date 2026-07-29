package com.openggf.game.sonic3k;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestHyperFormTrailSample {
    @Test
    void evenAndOddFramesUseRomCAnd14ByteHistoryOffsets() {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX(3)).thenReturn((short) 30);
        when(player.getCentreY(3)).thenReturn((short) 31);
        when(player.getArtTileAttributeHistory(3)).thenReturn((byte) 0x80);
        when(player.getCentreX(5)).thenReturn((short) 50);
        when(player.getCentreY(5)).thenReturn((short) 51);
        when(player.getArtTileAttributeHistory(5)).thenReturn((byte) 0x05);
        when(player.getMappingFrame()).thenReturn(0xB7);
        when(player.getRenderHFlip()).thenReturn(true);
        when(player.getRenderVFlip()).thenReturn(false);
        when(player.getPriorityBucket()).thenReturn(4);

        HyperFormTrailSample even = HyperFormTrailSample.sample(player, 2);
        HyperFormTrailSample odd = HyperFormTrailSample.sample(player, 3);

        assertEquals(30, even.centreX());
        assertEquals(31, even.centreY());
        assertEquals((byte) 0x80, even.historicalArtTileAttribute());
        assertEquals(true, even.highPriority());
        assertEquals(50, odd.centreX());
        assertEquals(51, odd.centreY());
        assertEquals((byte) 0x05, odd.historicalArtTileAttribute());
        assertEquals(false, odd.highPriority());
        assertEquals(0xB7, odd.mappingFrame());
        assertEquals(true, odd.horizontalFlip());
        assertEquals(false, odd.verticalFlip());
        assertEquals(4, odd.priorityBucket());

        HyperFormTrailRenderer.RenderOutput evenOutput =
                HyperFormTrailRenderer.output(even);
        assertEquals(true, evenOutput.highPriority());
        assertEquals(true, evenOutput.horizontalFlip());
        assertEquals(false, evenOutput.verticalFlip());
        assertEquals(4, evenOutput.priorityBucket());

        when(player.getPriorityBucket()).thenReturn(6);
        HyperFormTrailSample laterOdd = HyperFormTrailSample.sample(player, 5);
        HyperFormTrailRenderer.RenderOutput oddOutput =
                HyperFormTrailRenderer.output(laterOdd);
        assertEquals(false, oddOutput.highPriority());
        assertEquals(6, oddOutput.priorityBucket(),
                "trail stays in the live player's numeric render bucket");
    }
}
