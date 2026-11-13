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
    @Test void foreignDirectAndHiddenCallersCannotOpenBatchOrPassHandles() throws Exception {
        var source=temp.resolve("BatchProbe.java");Files.writeString(source,"""
            package foreign;
            import com.openggf.graphics.*;
            import com.openggf.level.objects.*;
            import com.openggf.level.rings.*;
            import java.util.function.*;
            public class BatchProbe {
                public static Object batch(GraphicsManager g){return PresentationGraphicsAccess.batch(g);}
                public static Function<GraphicsManager,Object> hiddenBatch(){return PresentationGraphicsAccess::batch;}
                public static Object objects(ObjectManager m,GraphicsManager g){return ObjectDrawInternalAccess.bucketPass(m,g);}
                public static BiFunction<ObjectManager,GraphicsManager,Object> hiddenObjects(){return ObjectDrawInternalAccess::bucketPass;}
                public static Object rings(RingManager m){return RingDrawInternalAccess.attractedPass(m);}
                public static Function<RingManager,Object> hiddenRings(){return RingDrawInternalAccess::attractedPass;}
            }
            """);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","25","-classpath",System.getProperty("java.class.path"),"-d",temp.toString(),source.toString()));
        try(var loader=new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()},getClass().getClassLoader())) {
            var probe=Class.forName("foreign.BatchProbe",true,loader);
            var graphics=new GraphicsManager();
            for(var direct:new Object[][]{{"batch",new Class<?>[]{GraphicsManager.class},new Object[]{graphics}},
                    {"objects",new Class<?>[]{com.openggf.level.objects.ObjectManager.class,GraphicsManager.class},new Object[]{null,graphics}},
                    {"rings",new Class<?>[]{com.openggf.level.rings.RingManager.class},new Object[]{null}}}) {
                var denied=assertThrows(InvocationTargetException.class,
                        ()->probe.getMethod((String)direct[0],(Class<?>[])direct[1]).invoke(null,(Object[])direct[2]));
                assertInstanceOf(SecurityException.class,denied.getCause(),(String)direct[0]);
            }
            @SuppressWarnings("unchecked")var batch=(java.util.function.Function<GraphicsManager,Object>)probe.getMethod("hiddenBatch").invoke(null);
            assertThrows(SecurityException.class,()->batch.apply(graphics));
            @SuppressWarnings("unchecked")var objects=(java.util.function.BiFunction<com.openggf.level.objects.ObjectManager,GraphicsManager,Object>)probe.getMethod("hiddenObjects").invoke(null);
            assertThrows(SecurityException.class,()->objects.apply(null,graphics));
            @SuppressWarnings("unchecked")var rings=(java.util.function.Function<com.openggf.level.rings.RingManager,Object>)probe.getMethod("hiddenRings").invoke(null);
            assertThrows(SecurityException.class,()->rings.apply(null));
        }
    }
    @Test void anAdmittedBatchAnswersLikeTheCheckedBridgeWithoutAllocatingPerCall() {
        var graphics=new GraphicsManager();graphics.initHeadless();
        try {
            var batch=PresentationGraphicsAccess.batch(graphics);
            assertTrue(batch.boundTo(graphics));
            assertFalse(batch.boundTo(new GraphicsManager()));
            assertEquals(PresentationGraphicsAccess.maskIsPerInstance(graphics),batch.maskIsPerInstance());
            assertEquals(PresentationGraphicsAccess.viewport(graphics),batch.viewport());
            var threads=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
            long thread=Thread.currentThread().threadId();
            int calls=2000;
            long start=threads.getThreadAllocatedBytes(thread);
            for(int i=0;i<calls;i++) batch.maskIsPerInstance();
            long batched=threads.getThreadAllocatedBytes(thread)-start;
            start=threads.getThreadAllocatedBytes(thread);
            for(int i=0;i<calls;i++) PresentationGraphicsAccess.maskIsPerInstance(graphics);
            long checked=threads.getThreadAllocatedBytes(thread)-start;
            // The checked bridge walks the stack each call; the measurement must be able to tell them apart.
            assertTrue(checked>=(long)calls*64,"checked bridge allocated "+checked+" bytes for "+calls+" calls");
            assertTrue(batched<calls,"admitted batch allocated "+batched+" bytes for "+calls+" calls");
        } finally { graphics.cleanup(); }
    }
    @Test void simultaneousWorldOwnersReserveDistinctNumericRangesAndReleaseOnlyTheirPixels() {
        var graphics=new GraphicsManager();graphics.initHeadless();
        Object firstOwner=new Object(),secondOwner=new Object();
        var first=PresentationGraphicsAccess.acquire(graphics,firstOwner);
        var second=PresentationGraphicsAccess.acquire(graphics,secondOwner);
        int a=PresentationGraphicsAccess.base(first,graphics,firstOwner),b=PresentationGraphicsAccess.base(second,graphics,secondOwner);
        assertEquals(a+6144,b,"each owner reserves all 24 poses' 256 private DPLC slots");
        var red=new com.openggf.level.Pattern();red.setPixel(0,0,(byte)1);
        var blue=new com.openggf.level.Pattern();blue.setPixel(0,0,(byte)2);
        for(int offset:new int[]{0,1023,1024,6143}) {graphics.cachePatternTexture(red,a+offset);graphics.cachePatternTexture(blue,b+offset);}
        PresentationGraphicsAccess.release(first,firstOwner);
        for(int offset:new int[]{0,1023,1024,6143}) {
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
    @Test void ownerBudgetRejectsAnIncompleteBankAndReleasedCrowdCapacityCanBeReused() {
        var graphics=new GraphicsManager();
        var owners=new java.util.ArrayList<Object>();
        var banks=new java.util.ArrayList<PresentationGraphicsAccess.TrailBank>();
        try {
            for(int i=0;i<10;i++) {
                Object owner=new Object();owners.add(owner);
                var bank=PresentationGraphicsAccess.acquire(graphics,owner);banks.add(bank);
                int base=PresentationGraphicsAccess.base(bank,graphics,owner);
                assertEquals(PatternAtlasRange.PRESENTATION_TRAILS.base()+i*6144,base);
                assertTrue(PatternAtlasRange.PRESENTATION_TRAILS.contains(base+6143),"every admitted owner has a complete crowd bank");
            }
            assertThrows(IllegalStateException.class,()->PresentationGraphicsAccess.acquire(graphics,new Object()),
                    "the remaining 4096 slots cannot fit another complete crowd");
            int released=PresentationGraphicsAccess.base(banks.get(4),graphics,owners.get(4));
            PresentationGraphicsAccess.release(banks.get(4),owners.get(4));
            Object replacement=new Object();owners.add(replacement);
            var bank=PresentationGraphicsAccess.acquire(graphics,replacement);banks.add(bank);
            assertEquals(released,PresentationGraphicsAccess.base(bank,graphics,replacement));
            assertFalse(PresentationGraphicsAccess.valid(banks.get(4),graphics,owners.get(4)));
        } finally {
            for(int i=0;i<banks.size();i++)PresentationGraphicsAccess.release(banks.get(i),owners.get(i));
            graphics.cleanup();
        }
    }
}
