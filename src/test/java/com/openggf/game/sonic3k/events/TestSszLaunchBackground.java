package com.openggf.game.sonic3k.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelTilemapManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;

class TestSszLaunchBackground {
    @Test void crumbleCommandWritesTwoRowsOfSixteenLiteralDescriptors() {
        var level = mock(LevelManager.class);
        var tilemap = mock(LevelTilemapManager.class);
        when(level.getTilemapManager()).thenReturn(tilemap);
        when(tilemap.isRetainedBackgroundPlaneAuthoritative()).thenReturn(true);
        when(level.captureBackgroundVdpPlane()).thenReturn(new byte[8192]);
        var state = new SszZoneRuntimeState(0, PlayerCharacter.SONIC_ALONE);
        state.setBackgroundCameraY(0x780);
        state.launch().setBackgroundRoundedY(0x780);
        state.launch().setBackgroundPlane(new byte[8192]);
        state.launch().setTileRowOffset(0xE700);
        SszLaunchBackground.update(level, state);
        for (int y : new int[]{14, 15}) for (int x = 0; x < 16; x++)
            verify(tilemap).setRetainedBackgroundTileDescriptorAtTilemapCell(x, y, 0x6061);
        verify(tilemap).isRetainedBackgroundPlaneAuthoritative();
        verifyNoMoreInteractions(tilemap);
        assertEquals(0, state.launch().tileRowOffset());
        verify(level).uploadBackgroundTilemap();
    }

    @Test void drawTileRowUsesNativeByteSignAndDoubleUpdate() {
        assertArrayEquals(new int[0], SszLaunchBackground.enteringRows(0x780, 0x780));
        assertArrayEquals(new int[]{0x770}, SszLaunchBackground.enteringRows(0x780, 0x770));
        assertArrayEquals(new int[]{0x870}, SszLaunchBackground.enteringRows(0x780, 0x790));
        assertArrayEquals(new int[]{0x760, 0x770}, SszLaunchBackground.enteringRows(0x780, 0x760));
        assertArrayEquals(new int[]{0xF0, 0x100}, SszLaunchBackground.enteringRows(0, 0x80));
        assertArrayEquals(new int[]{0xE0}, SszLaunchBackground.enteringRows(0xFF0, 0));
    }
}
