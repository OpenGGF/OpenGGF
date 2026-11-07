package com.openggf.level.rings;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestRingNativeDisplayInternalAccess {
    @Test void eachViewReadsOnlyItsRealCursorWindowAndDoesNotClearSparkleOrReadTheUnion() {
        var manager = mock(RingManager.class);
        var streaming = mock(NativeCompetitionRingStreaming.class);
        when(manager.nativeDisplayStreaming()).thenReturn(streaming);
        when(streaming.start(0)).thenReturn(0); when(streaming.end(0)).thenReturn(2);
        when(streaming.start(1)).thenReturn(1); when(streaming.end(1)).thenReturn(3);
        when(manager.nativeDisplayStageFrame(91)).thenReturn(3);
        when(manager.getSparkleStartIndex()).thenReturn(4);
        when(manager.getSparkleFrameCount()).thenReturn(4);
        when(manager.getSparkleFrameDelay()).thenReturn(6);
        var a = new RingSpawn(20, 30); var b = new RingSpawn(40, 50); var c = new RingSpawn(900, 70);
        when(manager.isCollected(b)).thenReturn(true);
        when(manager.getSparkleStartFrame(b)).thenReturn(89);
        var rows = List.of(a, b, c);
        var first = RingNativeDisplayInternalAccess.window(manager, rows, 0, 91);
        var second = RingNativeDisplayInternalAccess.window(manager, rows, 1, 91);
        assertEquals(List.of(0, 1), first.rows().stream().map(RingNativeDisplayInternalAccess.Row::index).toList());
        assertEquals(List.of(1, 2), second.rows().stream().map(RingNativeDisplayInternalAccess.Row::index).toList());
        assertEquals(first.rows().get(1), second.rows().getFirst());
        assertEquals(900, second.rows().getLast().x());
        assertEquals(89, first.rows().get(1).sparkleStartFrame());
        assertTrue(first.rows().get(1).collected());
        assertEquals(3, first.animationFrame());
        assertThrows(UnsupportedOperationException.class, () -> first.rows().clear());
        verify(manager, never()).getActiveSpawns();
        verify(manager, never()).isRenderable(any(), anyInt());
        assertThrows(IllegalArgumentException.class, () -> RingNativeDisplayInternalAccess.window(manager, rows, 2, 91));
    }
}
