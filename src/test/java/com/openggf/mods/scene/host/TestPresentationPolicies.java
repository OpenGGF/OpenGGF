package com.openggf.mods.scene.host;

import com.openggf.mods.scene.art.TitleCardPresentation;
import com.openggf.mods.ui.AtlasFont;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Optional font policy and pure title clock boundaries independent of any mod or ROM. */
class TestPresentationPolicies {
    private static final byte[] CASE_FONT=("= A\n##\n##\n\n= a\n#\n.\n\n= B\n###\n#.#\n\n= ?\n##\n.#\n").getBytes(StandardCharsets.UTF_8);
    @Test void exactCaseUsesItsGlyphThenUppercaseThenTheConfiguredFallback(){
        var font=AtlasFont.parse(CASE_FONT,2,AtlasFont.GlyphCase.EXACT_FIRST,'?');
        assertEquals(1,font.width("a"));assertEquals(2,font.width("A"));assertEquals(3,font.width("b"));assertEquals(2,font.width("\u2603"));
        assertEquals(16,font.width("a b \u2603"));assertEquals(3,font.width(" "));assertEquals(0,font.width(""));
        var c=new RecordingCanvas(320,224,null);font.draw(c,"a b \u2603",0,0,0xFFFFFFFF,1);
        assertEquals(3,c.ops().size());
        assertEquals(3,c.ops().get(0).u0());assertEquals(1,c.ops().get(0).x1()-c.ops().get(0).x0());
        assertEquals(5,c.ops().get(1).u0());assertEquals(6,c.ops().get(1).x0());
        assertEquals(9,c.ops().get(2).u0());assertEquals(14,c.ops().get(2).x0());
        assertEquals(0xFFFFFFFF,c.ops().get(0).image().pixels()[3]);
        assertEquals(0,c.ops().get(0).image().pixels()[3+c.ops().get(0).image().width()]);
    }
    @Test void uppercaseRemainsTheDefaultAndMalformedPoliciesFailBeforeDrawing(){
        assertThrows(IllegalArgumentException.class,()->AtlasFont.parse(CASE_FONT,2));
        byte[] onlyUpper="= A\n##\n##\n".getBytes(StandardCharsets.UTF_8);
        var font=AtlasFont.parse(onlyUpper,2);assertEquals(2,font.width("a"));assertEquals(3,font.width("?"));
        var c=new RecordingCanvas(320,224,null);font.draw(c,"a ?",0,0,0xFFFFFFFF,1);assertEquals(1,c.ops().size());
        assertThrows(IllegalArgumentException.class,()->AtlasFont.parse(onlyUpper,2,AtlasFont.GlyphCase.EXACT_FIRST,'?'));
        assertThrows(NullPointerException.class,()->AtlasFont.parse(onlyUpper,2,null,'?'));
        assertThrows(IllegalArgumentException.class,()->font.draw(c,"A",0,0,0xFFFFFFFF,0));
    }
    @Test void titleArrivalAndReleaseRemainIndependentBoundedClocks(){
        var start=TitleCardPresentation.position(3,0,0);assertEquals(708,start.x());assertEquals(160,start.y());
        var held=TitleCardPresentation.position(3,Long.MAX_VALUE,0);assertEquals(260,held.x());assertEquals(160,held.y());
        assertEquals(28,TitleCardPresentation.arrivalFrames());assertEquals(90,TitleCardPresentation.holdFrames());
        assertEquals(0,TitleCardPresentation.automaticExitElapsed(118));
        assertEquals(32,64-TitleCardPresentation.position(0,28,1).y());
        assertEquals(160,TitleCardPresentation.position(1,28,2).x());assertEquals(192,TitleCardPresentation.position(1,28,3).x());
        assertEquals(260,TitleCardPresentation.position(3,28,6).x());assertEquals(292,TitleCardPresentation.position(3,28,7).x());
        assertTrue(Float.isFinite(TitleCardPresentation.position(3,Long.MAX_VALUE,Long.MAX_VALUE).x()));
        assertThrows(IllegalArgumentException.class,()->TitleCardPresentation.position(4,0,0));
        assertThrows(IllegalArgumentException.class,()->TitleCardPresentation.position(0,-1,0));
        assertThrows(IllegalArgumentException.class,()->TitleCardPresentation.automaticExitElapsed(-1));
        assertThrows(IllegalArgumentException.class,()->TitleCardPresentation.durationFrames(0));
    }
}
