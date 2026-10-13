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
}
