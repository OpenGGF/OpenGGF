package com.openggf.editor;

import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.metadata.ObjectPreviewProvider;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Static preview metadata must describe native variants without factory execution. */
class TestEditorObjectPreviews {
    private static ObjectSpawn spawn(int id, int subtype) { return new ObjectSpawn(100, 200, id, subtype, 0, false, 200); }
    @Test void monitorContentsChooseStableNativeMappingFrames() {
        assertEquals(8, new Sonic1ObjectRegistry().objectPreview(spawn(0x26, 6)).parts().getFirst().frame());
        assertEquals(5, new Sonic2ObjectRegistry().objectPreview(spawn(0x26, 4)).parts().getFirst().frame());
        assertEquals(4, new Sonic3kObjectRegistry().objectPreview(spawn(1, 3)).parts().getFirst().frame());
        assertEquals(11, new Sonic2ObjectRegistry().objectPreview(spawn(0x26, 10)).parts().getFirst().frame());
    }
    @Test void springsSelectDirectionStrengthAndDownwardMirroring() {
        var s1 = new Sonic1ObjectRegistry().objectPreview(spawn(0x41, 0x12)).parts().getFirst();
        assertEquals("spring_horizontal_red", s1.artKey());
        var s2 = new Sonic2ObjectRegistry().objectPreview(spawn(0x41, 0x20)).parts().getFirst();
        assertEquals("spring_vertical", s2.artKey()); assertTrue(s2.vFlip());
        var s3 = new Sonic3kObjectRegistry().objectPreview(spawn(7, 0x32)).parts().getFirst();
        assertEquals("s3k_spring_diagonal_yellow", s3.artKey());
    }
    @Test void internalPlayersAndUnknownIdsHaveNoMisleadingSprite() {
        assertTrue(new Sonic2ObjectRegistry().objectPreview(spawn(1, 0)).parts().isEmpty());
        assertTrue(new Sonic1ObjectRegistry().objectPreview(spawn(0, 0)).parts().isEmpty());
        assertTrue(new Sonic3kObjectRegistry().objectPreview(spawn(0xfe, 0)).parts().isEmpty());
        assertEquals("Camera zone", EditorStockObjectPalette.displayName("openggf:camera-zone"));
        assertEquals("Falling Pillar", EditorStockObjectPalette.displayName("23: FallingPillar"));
    }
}
