package com.openggf.game.sonic2;

import com.openggf.data.RomByteReader;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestS2ExplicitMappingFrames {
    @Test void explicitCountReadsReorderedFramesAndRetainsNativePieceFields() {
        // Three pointers; frame 2 is stored before frame 0, as in reordered ROM tables.
        var bytes = ByteBuffer.allocate(36);
        bytes.putShort((short) 16).putShort((short) 26).putShort((short) 6);
        for (int tile : new int[]{2, 0, 1}) {
            bytes.putShort((short) 1).put((byte) -8).put((byte) 5)
                    .putShort((short) (0xE800 | tile)).putShort((short) 0x7777).putShort((short) -12);
        }
        var frames = S2SpriteDataLoader.loadMappingFrames(new RomByteReader(bytes.array()), 0, 3);
        assertEquals(3, frames.size());
        for (int index = 0; index < 3; index++) {
            var piece = frames.get(index).pieces().getFirst();
            assertEquals(index, piece.tileIndex());
            assertEquals(-12, piece.xOffset());
            assertEquals(-8, piece.yOffset());
            assertEquals(2, piece.widthTiles());
            assertEquals(2, piece.heightTiles());
            assertEquals(3, piece.paletteIndex());
            assertTrue(piece.hFlip());
            assertFalse(piece.vFlip());
            assertTrue(piece.priority());
        }
    }

    @Test void explicitCountIsBoundedBeforeReadingBytes() {
        var reader = new RomByteReader(new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> S2SpriteDataLoader.loadMappingFrames(reader, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> S2SpriteDataLoader.loadMappingFrames(reader, 0, 513));
    }
}
