package com.openggf.game.presentation;

import com.openggf.game.CharacterPresentation.*;
import com.openggf.level.*;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCharacterSignRenderer {
    private final Image face=new Image(48,32,new byte[48*32]);
    @Test void semanticSlotsAndNativeFallback() {
        var pose=new SignFrame(face,null);
        var art=new Art(null,null,null,null,new Sign(pose,null,pose,null,pose));
        // S1, S2, S3K mapping indices; all native edge and antagonist poses remain untouched.
        for(int[] mapping:List.of(new int[]{4,-1,1,2,3,0},new int[]{0,1,3,4,5,2},new int[]{0,-1,4,5,6,3})) {
            assertSame(pose,CharacterSignRenderer.select(art,mapping[0],mapping[0],mapping[1],mapping[2],mapping[3],mapping[4]));
            assertSame(pose,CharacterSignRenderer.select(art,mapping[2],mapping[0],mapping[1],mapping[2],mapping[3],mapping[4]));
            assertSame(pose,CharacterSignRenderer.select(art,mapping[4],mapping[0],mapping[1],mapping[2],mapping[3],mapping[4]));
            assertNull(CharacterSignRenderer.select(art,mapping[3],mapping[0],mapping[1],mapping[2],mapping[3],mapping[4]));
            assertNull(CharacterSignRenderer.select(art,mapping[5],mapping[0],mapping[1],mapping[2],mapping[3],mapping[4]));
        }
        assertNull(CharacterSignRenderer.select(null,0,0,-1,1,2,3));
        assertNull(CharacterSignRenderer.select(new Art(null,null,null,null),0,0,-1,1,2,3));
    }
    @Test void tilesPreservePixelsPalettesPoleAndPreflippedSpin() {
        var pole=new SpriteMappingPiece(-4,16,1,2,0,true,false,0);
        var sheet=new ObjectSpriteSheet(new Pattern[]{new Pattern(),new Pattern()},List.of(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-24,-16,3,4,0,false,false,0),pole))),0,0);
        var nativePalette=new Palette();
        byte[] pixels=new byte[32*32];pixels[0]=7;pixels[31]=9;
        var spin=new Image(32,32,pixels);
        byte[] backingPixels=new byte[32*32];backingPixels[3]=1;
        var backing=new Backing(new Image(32,32,backingPixels),0,new int[16]);
        var composed=CharacterSignRenderer.compose(sheet,0,new SignFrame(spin,backing),8,nativePalette);
        assertEquals(-1,composed.getPaletteIndex(),"Absolute palettes preserve isolated character and native backing rows");
        var pieces=composed.getFrame(0).pieces();
        assertEquals(8,pieces.getFirst().paletteIndex());
        assertEquals(0,pieces.get(2).paletteIndex());
        assertSame(pole,pieces.getLast());
        assertFalse(pieces.getFirst().hFlip(),"spin3 assets are already oriented");
        var decoded=CharacterNameComposer.extract(composed.getPatterns(),2,4,4,0,0,32,32);
        assertArrayEquals(pixels,decoded.pixels());
        assertSame(sheet.getPatterns()[0],composed.getPatterns()[0]);
    }
}
