package com.openggf.util;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.TileLoadRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestDplcStaticFlattener {

    @Test
    void contiguousPieceIsRemappedToAbsoluteSourceTiles() {
        // One frame: a 2x2-tile piece at vram tile 0.
        // DPLC frame 0 loads 4 contiguous source tiles starting at source tile 10.
        // Expect: single piece survives, tileIndex remapped 0 -> 10.
        SpriteMappingPiece piece = new SpriteMappingPiece(0, 0, 2, 2, 0, false, false, 0, false);
        SpriteMappingFrame mapping = new SpriteMappingFrame(List.of(piece));
        SpriteDplcFrame dplc = new SpriteDplcFrame(List.of(new TileLoadRequest(10, 4)));

        List<SpriteMappingFrame> out = DplcStaticFlattener.applyDplcRemap(List.of(mapping), List.of(dplc));

        // frame count preserved; piece remapped, not split (contiguous)
        assertEquals(1, out.size());
        List<SpriteMappingPiece> pieces = out.get(0).pieces();
        assertEquals(1, pieces.size());
        SpriteMappingPiece remapped = pieces.get(0);
        assertEquals(10, remapped.tileIndex());
        assertEquals(2, remapped.widthTiles());
        assertEquals(2, remapped.heightTiles());
    }

    @Test
    void nonContiguousPieceIsSplitIntoSingleTileSubPieces() {
        // One 2x1-tile piece at vram tile 0; DPLC loads tile0<-src 10, tile1<-src 99 (two requests).
        // Expect the piece split into two 1x1 pieces with tileIndex 10 and 99.
        SpriteMappingPiece piece = new SpriteMappingPiece(0, 0, 2, 1, 0, false, false, 0, false);
        SpriteMappingFrame mapping = new SpriteMappingFrame(List.of(piece));
        SpriteDplcFrame dplc = new SpriteDplcFrame(
                List.of(new TileLoadRequest(10, 1), new TileLoadRequest(99, 1)));

        List<SpriteMappingFrame> out = DplcStaticFlattener.applyDplcRemap(List.of(mapping), List.of(dplc));

        assertEquals(1, out.size());
        List<SpriteMappingPiece> pieces = out.get(0).pieces();
        assertEquals(2, pieces.size());

        SpriteMappingPiece first = pieces.get(0);
        assertEquals(10, first.tileIndex());
        assertEquals(1, first.widthTiles());
        assertEquals(1, first.heightTiles());
        assertEquals(0, first.xOffset());
        assertEquals(0, first.yOffset());

        SpriteMappingPiece second = pieces.get(1);
        assertEquals(99, second.tileIndex());
        assertEquals(1, second.widthTiles());
        assertEquals(1, second.heightTiles());
        assertEquals(8, second.xOffset());
        assertEquals(0, second.yOffset());
    }

    @Test
    void emptyDplcListReturnsMappingsUnchanged() {
        SpriteMappingPiece piece = new SpriteMappingPiece(0, 0, 1, 1, 5, false, false, 0, false);
        SpriteMappingFrame mapping = new SpriteMappingFrame(List.of(piece));
        List<SpriteMappingFrame> mappings = List.of(mapping);

        List<SpriteMappingFrame> out = DplcStaticFlattener.applyDplcRemap(mappings, List.of());

        assertSame(mappings, out);
        assertEquals(1, out.size());
        assertEquals(5, out.get(0).pieces().get(0).tileIndex());
    }
}
