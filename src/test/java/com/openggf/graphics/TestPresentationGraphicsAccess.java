package com.openggf.graphics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TestPresentationGraphicsAccess {
    @TempDir Path temp;
    @Test void bankHandlesCannotBeReusedAcrossOwnersHostsOrAfterRelease() {
        var graphics=new GraphicsManager();Object owner=new Object();
        var bank=PresentationGraphicsAccess.acquire(graphics,owner);
        int base=PresentationGraphicsAccess.base(bank,graphics,owner);
        assertTrue(PatternAtlasRange.PRESENTATION_TRAILS.contains(base));
        assertThrows(IllegalArgumentException.class,()->PresentationGraphicsAccess.base(bank,graphics,new Object()));
        assertThrows(IllegalArgumentException.class,()->PresentationGraphicsAccess.base(bank,new GraphicsManager(),owner));
        assertThrows(IllegalArgumentException.class,()->PresentationGraphicsAccess.release(bank,new Object()));
        PresentationGraphicsAccess.release(bank,owner);
        assertThrows(IllegalArgumentException.class,()->PresentationGraphicsAccess.base(bank,graphics,owner));
        Object nextOwner=new Object();var next=PresentationGraphicsAccess.acquire(graphics,nextOwner);
        assertEquals(base,PresentationGraphicsAccess.base(next,graphics,nextOwner));
        PresentationGraphicsAccess.release(next,nextOwner);
    }
    @Test void foreignDirectAndHiddenCallersCannotAcquireNativeArtBanks() throws Exception {
        var source=temp.resolve("PresentationProbe.java");Files.writeString(source,"""
            package foreign;
            import com.openggf.graphics.*;
            import java.util.function.BiFunction;
            public class PresentationProbe {
                public static Object direct(GraphicsManager g,Object o){return PresentationGraphicsAccess.acquire(g,o);}
                public static BiFunction<GraphicsManager,Object,Object> hidden(){return PresentationGraphicsAccess::acquire;}
            }
            """);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","25","-classpath",System.getProperty("java.class.path"),"-d",temp.toString(),source.toString()));
        try(var loader=new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()},getClass().getClassLoader())) {
            var probe=Class.forName("foreign.PresentationProbe",true,loader);
            var denied=assertThrows(InvocationTargetException.class,()->probe.getMethod("direct",GraphicsManager.class,Object.class).invoke(null,new GraphicsManager(),new Object()));
            assertInstanceOf(SecurityException.class,denied.getCause());
            @SuppressWarnings("unchecked")var hidden=(java.util.function.BiFunction<GraphicsManager,Object,Object>)probe.getMethod("hidden").invoke(null);
            assertThrows(SecurityException.class,()->hidden.apply(new GraphicsManager(),new Object()));
        }
    }
    @Test void simultaneousWorldOwnersReserveDistinctNumericRangesAndReleaseOnlyTheirPixels() {
        var graphics=new GraphicsManager();graphics.initHeadless();
        Object firstOwner=new Object(),secondOwner=new Object();
        var first=PresentationGraphicsAccess.acquire(graphics,firstOwner);
        var second=PresentationGraphicsAccess.acquire(graphics,secondOwner);
        int a=PresentationGraphicsAccess.base(first,graphics,firstOwner),b=PresentationGraphicsAccess.base(second,graphics,secondOwner);
        assertEquals(a+1024,b,"identity owner keys must not turn numeric reservation checks into identity comparisons");
        var red=new com.openggf.level.Pattern();red.setPixel(0,0,(byte)1);
        var blue=new com.openggf.level.Pattern();blue.setPixel(0,0,(byte)2);
        for(int offset:new int[]{0,1023}) {graphics.cachePatternTexture(red,a+offset);graphics.cachePatternTexture(blue,b+offset);}
        PresentationGraphicsAccess.release(first,firstOwner);
        for(int offset:new int[]{0,1023}) {
            assertNull(SpritePresentation.patternSample(graphics,a+offset));
            assertEquals(2,SpritePresentation.patternSample(graphics,b+offset).a()&15);
        }
        assertTrue(PresentationGraphicsAccess.valid(second,graphics,secondOwner));
        Object nextOwner=new Object();var reused=PresentationGraphicsAccess.acquire(graphics,nextOwner);
        assertEquals(a,PresentationGraphicsAccess.base(reused,graphics,nextOwner));
        assertFalse(PresentationGraphicsAccess.valid(first,graphics,firstOwner));
        PresentationGraphicsAccess.release(reused,nextOwner);
        PresentationGraphicsAccess.release(second,secondOwner);
        graphics.cleanup();
    }
}
