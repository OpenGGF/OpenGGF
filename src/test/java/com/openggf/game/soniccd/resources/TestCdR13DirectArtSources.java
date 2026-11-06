package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.lang.reflect.Modifier;
import static com.openggf.game.soniccd.resources.TestCdR13SectionArt.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual source/reader proof only. Java UNRUN. */
class TestCdR13DirectArtSources {
    @Test void liveOriginalLifeOperandsAndWholeRawIncludeArePinnedInBothImages()throws Exception{
        for(var a:actual().all()){var s=CdR13DirectArtSources.fromAssets(a);assertSame(a.identity(),s.identity());assertTrue(s.ownsAssets(a));assertEquals(0x32F3C,s.lifeIconRange().fileOffset());assertEquals(384,s.lifeIconRange().length());assertEquals(0x232F3C,s.lifeIconRange().nativeAddress());assertEquals("0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a",hash(s.lifeIcon().copy()));assertSame(s.lifeIcon(),s.lifeIcon());}
    }
    @Test void nativeBlockRefillUsesOneGenuineNeighborAndReturnsEveryOriginalCanonicalByte()throws Exception{
        for(var a:actual().all()){boolean c=a.identity().file()==CdNativeFile.R13C;var s=CdR13DirectArtSources.fromAssets(a);assertEquals(c?4790:4772,s.blocksBlobRange().length());assertEquals(c?4791:4773,s.blocksReaderRange().length());assertEquals(0x38490,s.blocksReaderRange().fileOffset());assertArrayEquals(a.view(CdR13FutureAssets.Asset.LEVEL_BLOCKS).copy(),s.blocksReader().slice(0,s.blocksBlobRange().length()).copy());assertEquals(c?"40bdab146bd75ac7c01c1052074cbcd7020fb8fc61a22798a5a936d6f85b76ba":"913d1dcc34ce97966288755305ad80fdc063e73acec2dbc5b202df8df80d3e5c",hash(s.blocksReader().copy()));
            var d=CdNemesisPlcDecoder.prepare(s.blocksReader(),0x238490,dirty());while(d.remainingTiles()!=0)d=CdNemesisPlcDecoder.decodeTile(d).state();assertEquals(s.blocksReader().length(),d.inputOffset());
            var shortRead=s.blocksReader().slice(0,s.blocksReader().length()-1);assertThrows(CdNemesisPlcDecoder.DecodeException.class,()->decode(shortRead));assertSame(s.blocksReader(),s.blocksReader());}
    }
    @Test void directViewsAreImmutableAndOwnerPairCannotBeReplacedByEqualIdentity()throws Exception{
        var f=actual();var s=CdR13DirectArtSources.fromAssets(f.good());assertFalse(s.ownsAssets(f.sameGood()));assertFalse(s.ownsAssets(f.bad()));assertFalse(s.ownsAssets(null));byte[] b=s.lifeIcon().copy();b[0]^=1;assertNotEquals(b[0],s.lifeIcon().copy()[0]);assertThrows(java.nio.ReadOnlyBufferException.class,()->s.lifeIcon().buffer().put(0,(byte)1));assertThrows(NullPointerException.class,()->CdR13DirectArtSources.fromAssets(null));
        for(var ctor:CdR13DirectArtSources.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(ctor.getModifiers()));var methods=Arrays.stream(CdR13DirectArtSources.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())&&Modifier.isStatic(m.getModifiers())).toList();assertEquals(1,methods.size());assertArrayEquals(new Class<?>[]{CdR13FutureAssets.class},methods.getFirst().getParameterTypes());}
    private static void decode(CdAssetBytes bytes){var d=CdNemesisPlcDecoder.prepare(bytes,0x238490,dirty());while(d.remainingTiles()!=0)d=CdNemesisPlcDecoder.decodeTile(d).state();}
}
