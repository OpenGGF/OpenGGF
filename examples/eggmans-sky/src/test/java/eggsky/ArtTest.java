package eggsky;

import eggsky.art.Art;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Expected loader failures are inspectable and retried; programming faults reach the owner. */
class ArtTest {
    @Test void failedDecodeRetriesAtABoundedIntervalInsteadOfPermanentlyCachingNull() {
        ArtFixture fixture=new ArtFixture();fixture.spriteFailure=new IllegalArgumentException("corrupt test art");
        Art art=fixture.game().art;
        assertNull(art.set("ship"));assertEquals(1,fixture.spriteLoads);
        fixture.spriteFailure=null;
        for(int i=0;i<100;i++)assertNull(art.set("ship"));
        assertEquals(1,fixture.spriteLoads,"drawing cannot retry a failed decoder every time");
        fixture.ticks=120;
        assertNotNull(art.set("ship"),"retry a repaired source after the bounded delay");
        assertEquals(2,fixture.spriteLoads);
    }
    @Test void failedPalettePreparationNeverPublishesAPartialPalette() {
        ArtFixture fixture=new ArtFixture();fixture.paletteFailure=new IllegalArgumentException("corrupt palette");
        Art art=fixture.game().art;assertNull(art.set("ship"));assertEquals(1,fixture.paletteLoads);
        fixture.paletteFailure=null;fixture.ticks=120;assertNotNull(art.set("ship"));
        assertEquals(3,fixture.paletteLoads,"retry prepares both complete palette ranges");
    }
    @Test void programmingFaultIsNotTurnedIntoAMissingSprite() {
        ArtFixture fixture=new ArtFixture();fixture.spriteFailure=new NullPointerException("programming fault");
        assertThrows(NullPointerException.class,()->fixture.game().art.set("ship"));
    }
    @Test void failedRequestsHaveBoundedDistinctCauseAndOwnerStatus() throws Exception {
        ArtFixture fixture=new ArtFixture();Art art=fixture.game().art;
        assertNull(art.set("unknown"));
        Object unsupported=find(art,"set:unknown");
        assertEquals("UNSUPPORTED",value(unsupported,"kind"));
        fixture.spriteFailure=new IllegalArgumentException("corrupt test art");assertNull(art.set("ship"));
        Object invalid=find(art,"set:ship");
        assertEquals("INVALID_REQUEST",value(invalid,"kind"));assertTrue(value(invalid,"cause").contains("corrupt test art"));
        assertEquals("eggmans-sky",value(invalid,"owner"));
        for(int i=0;i<200;i++)art.set("unknown:"+i);
        assertTrue(failures(art).size()<=64,"failed lookup references are bounded");
        ArtFixture missing=new ArtFixture();missing.primaryAvailable=false;
        Art absent=missing.game().art;assertNull(absent.set("ship"));
        assertEquals("OPTIONAL_ROM_MISSING",value(find(absent,"set:ship"),"kind"));
    }
    @Test void creatureBanksUseRequestsAndCopiedPalettesAndKeepLiveSpritesAfterEviction() {
        ArtFixture fixture=new ArtFixture();Art art=fixture.game().art;
        var def=new eggsky.art.FaunaDef("test","Test","s3k",1,
                com.openggf.mods.scene.RomSpriteRequest.of(1,com.openggf.mods.scene.RomSpriteRequest.Compression.NEMESIS,2,0),
                0,new int[]{0},8,false);
        int[] palette=new int[64];palette[1]=0xFF123456;
        var active=art.creature(def,palette,"first");assertNotNull(active);
        assertSame(active,art.creature(def,palette,"another label"));assertEquals(1,fixture.spriteLoads);
        palette[1]=0xFF654321;assertNotNull(art.creature(def,palette,"first"));assertEquals(2,fixture.spriteLoads);
        for(int i=0;i<64;i++){palette[1]=i;assertNotNull(art.creature(def,palette,"explored:"+i));}
        palette[1]=0xFF123456;assertNotNull(art.creature(def,palette,"first"));
        assertEquals(67,fixture.spriteLoads,"old bank evicted at the per-ROM capacity");
        assertArrayEquals(fixture.sprite.image().pixels(),active.frame(0).image().pixels());
    }
    private List<?> failures(Art art) { return art.failures(); }
    private Object find(Art art,String request) throws Exception {
        for(Object entry:failures(art))if(value(entry,"request").equals(request))return entry;
        fail("failed request is inspectable: "+request);return null;
    }
    private String value(Object object,String field) throws Exception {return object.getClass().getMethod(field).invoke(object).toString();}
}
