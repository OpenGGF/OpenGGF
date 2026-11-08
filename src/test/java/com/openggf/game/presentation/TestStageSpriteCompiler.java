package com.openggf.game.presentation;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.StageSpriteSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestStageSpriteCompiler {
    @Test void absentStageSlotsRetainExistingConstructorContract() {
        var presentation=new CharacterPresentation("MICA");
        assertTrue(presentation.stages().isEmpty());
    }
    @Test void nativeIdsHotspotsAndSparseSlotFramesArePreserved() {
        byte[] pixels=new byte[33*48];
        pixels[32]=15; pixels[33*47]=7;
        var image=new CharacterPresentation.Image(33,48,pixels);
        var frame=new StageSpriteSet.Frame(image,17,43);
        var source=new java.util.HashMap<Integer,StageSpriteSet.Frame>();
        source.put(150,frame);
        var set=new StageSpriteSet(source);
        source.clear();
        var art=StageSpriteCompiler.compile(set,null);
        assertEquals(151,art.mappingFrames().size());
        assertTrue(art.mappingFrames().get(149).pieces().isEmpty());
        assertEquals(4,art.mappingFrames().get(150).pieces().size());
        var first=art.mappingFrames().get(150).pieces().getFirst();
        assertEquals(-17,first.xOffset()); assertEquals(-43,first.yOffset());
        assertEquals(30,art.bankSize());
        assertEquals(15,art.artTiles()[24].getPixel(0,0));
        assertEquals(7,art.artTiles()[17].getPixel(0,7));
        assertThrows(UnsupportedOperationException.class,()->set.frames().clear());
    }
    @Test void slotsRejectInvalidNativeIdsAndUnboundedHotspots() {
        var frame=new StageSpriteSet.Frame(new CharacterPresentation.Image(1,1,new byte[1]),0,0);
        assertThrows(IllegalArgumentException.class,()->new StageSpriteSet(Map.of(256,frame)));
        assertThrows(IllegalArgumentException.class,()->new StageSpriteSet.Frame(frame.image(),Integer.MAX_VALUE,0));
    }
}
