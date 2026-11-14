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
    @Test void independentlyOwnedStageBanksDoNotSharePatternAddresses() {
        var image = new CharacterPresentation.Image(8, 8, new byte[64]);
        var set = new StageSpriteSet(Map.of(1, new StageSpriteSet.Frame(image, 4, 8)));
        int base = com.openggf.graphics.PatternAtlasRange.CHARACTER_STAGE_PRESENTATION.base();
        var leader = StageSpriteCompiler.compile(set, null, base);
        var follower = StageSpriteCompiler.compile(set, null, base + 0x8000);
        assertTrue(leader.basePatternIndex() + leader.artTiles().length < follower.basePatternIndex());
        assertEquals(leader.mappingFrames(), follower.mappingFrames());
        assertEquals(leader.dplcFrames(), follower.dplcFrames());
    }
    @Test void oversizedStageBankCannotOverwriteTheOtherActor() {
        var frame = new StageSpriteSet.Frame(new CharacterPresentation.Image(256, 256, new byte[256*256]), 128, 128);
        var frames = new java.util.HashMap<Integer, StageSpriteSet.Frame>();
        for (int i = 0; i < 33; i++) frames.put(i, frame);
        assertThrows(IllegalArgumentException.class, () -> StageSpriteCompiler.compile(new StageSpriteSet(frames), null));
    }
    @Test void explicitOrientationsAreAdditiveAndNeverChangeNativeIds() {
        var a = new StageSpriteSet.Frame(new CharacterPresentation.Image(1, 1, new byte[]{2}), 0, 0);
        var b = new StageSpriteSet.Frame(new CharacterPresentation.Image(1, 1, new byte[]{5}), 0, 0);
        var base = new StageSpriteSet(Map.of(6, a));
        var oriented = base.withMirroredFrames(Map.of(6, b));
        assertEquals(java.util.List.of("frames"), java.util.Arrays.stream(StageSpriteSet.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList());
        assertSame(a, base.frames().get(6));
        assertEquals(6, StageSpriteCompiler.orientedFrameIndex(base, 6, true, false));
        int selected = StageSpriteCompiler.orientedFrameIndex(oriented, 6, true, false);
        assertNotEquals(6, selected);
        assertEquals(6, StageSpriteCompiler.orientedFrameIndex(oriented, 6, false, false));
        var art = StageSpriteCompiler.compile(oriented, null);
        int tile = art.dplcFrames().get(selected).requests().getFirst().startTile();
        assertEquals(5, art.artTiles()[tile].getPixel(0, 0));
        assertFalse(art.mappingFrames().get(selected).pieces().getFirst().hFlip());
        assertThrows(IllegalArgumentException.class, () -> base.withMirroredFrames(Map.of(7, b)));
        assertThrows(IllegalArgumentException.class, () -> base.withOrientationFrames(false, false, Map.of(6, b)));
    }

    @Test void slotsRejectInvalidNativeIdsAndUnboundedHotspots() {
        var frame=new StageSpriteSet.Frame(new CharacterPresentation.Image(1,1,new byte[1]),0,0);
        assertThrows(IllegalArgumentException.class,()->new StageSpriteSet(Map.of(256,frame)));
        assertThrows(IllegalArgumentException.class,()->new StageSpriteSet.Frame(frame.image(),Integer.MAX_VALUE,0));
    }
}
