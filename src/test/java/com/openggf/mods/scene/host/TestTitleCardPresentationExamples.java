package com.openggf.mods.scene.host;

import com.openggf.mods.code.ExampleModHarness;
import com.openggf.mods.scene.*;
import com.openggf.mods.ui.SceneCanvasTransform;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Original callers at 8172c0780 supply the golden source frames and recorded CPU pixels. */
class TestTitleCardPresentationExamples {
    @TempDir static Path work;
    static ExampleModHarness flappy, slay, sitar;
    static Method flappyDraw, slayDraw, slayUpdate, slayHurry, slayShowing, slayDone, sitarDraw;
    static Constructor<?> slayConstructor;
    static Object sitarScene, sitarScreens;
    static SceneRomArt rom;
    static SceneSpriteSet card;
    static final int[] WIDTHS = {320,400,528,800};
    static final int[] AGES = {0,1,10,11,19,20,23,24,27,28,117,118,119,120,121,122,123,124,125,141,148};
    @BeforeAll static void build() throws Exception {
        flappy=ExampleModHarness.build(Path.of("examples/flappy-tails"),work.resolve("flappy"));
        slay=ExampleModHarness.build(Path.of("examples/slay-the-robotnik"),work.resolve("slay"));
        sitar=ExampleModHarness.build(Path.of("examples/sitar-hero"),work.resolve("sitar"));
        var f=flappy.loader().loadClass("flappytails.TitleCard");
        flappyDraw=Arrays.stream(f.getDeclaredMethods()).filter(m->m.getName().equals("draw")).findFirst().orElseThrow(); flappyDraw.setAccessible(true);
        var sc=slay.loader().loadClass("slaytherobotnik.scene.TitleCard");
        slayConstructor=sc.getDeclaredConstructor(SceneSpriteSet.class);slayConstructor.setAccessible(true);
        slayDraw=method(sc,"draw",SceneCanvas.class,int.class);slayUpdate=method(sc,"update");
        slayHurry=method(sc,"hurry");slayShowing=method(sc,"showing");slayDone=method(sc,"done");
        var sceneClass=sitar.loader().loadClass("sitarhero.SitarScene");
        sitarScene=sceneClass.getConstructor().newInstance(); sitarScreens=field(sitarScene,"screens");
        sitarDraw=method(sitarScreens.getClass(),"titleCard",SceneCanvas.class,long.class,boolean.class);
        var song=sitar.loader().loadClass("sitarhero.model.SongSpec").getConstructor(String.class,String.class,String.class,int.class,int.class,int.class,int.class,List.class,boolean.class)
                .newInstance("test","Test Zone","s3k",1,600,0,0,List.of(),false);
        set(sitarScene,"song",song);set(sitarScene,"loadingTick",0L);set(sitarScene,"screenTick",119L);
        var context=mock(SceneContext.class); var art=mock(SceneArt.class);rom=mock(SceneRomArt.class);
        when(context.art()).thenReturn(art);when(art.rom("s3k")).thenReturn(rom);when(rom.hasTitleCard(anyInt(),anyInt())).thenReturn(true);
        set(sitarScene,"context",context);card=frames(4,false);when(rom.titleCard(anyInt(),anyInt())).thenReturn(card);
    }
    @AfterAll static void close() throws Exception { for(var h:new ExampleModHarness[]{flappy,slay,sitar})if(h!=null)h.close(); }
    @Test void sourceFramesAndRecordedPixelsMatchThePreMigrationPresentation() throws Exception {
        List<String> fingerprints=new ArrayList<>();
        for(int width:WIDTHS){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(int age:AGES){
                var a=canvas(width);flappyDraw.invoke(null,a,card,(long)age,(width-320)/2,null,true);
                var b=canvas(width);slayDraw.invoke(slayAt(card,age),b,width);
                var c=canvas(width);sitarDraw.invoke(sitarScreens,c,(long)age,age>=119);
                assertOperations(a.ops(),b.ops());assertOperations(a.ops(),c.ops());
                digest.update(pixels(a));
                for(var op:a.ops()) { assertTrue(Arrays.stream(new SceneImage[]{card.frame(0).image(),card.frame(1).image(),card.frame(2).image(),card.frame(3).image()}).anyMatch(image->image==op.image())); }
            }
            fingerprints.add(HexFormat.of().formatHex(digest.digest()));
        }
        assertEquals(List.of("175638aafdd286e9cfbe028b14b5361fc299374dd9ede6e0fbcd7e0234b02a94","82295abfb8fcfcd24b0ca94c09527be6ba743763c166db80fa37ab4bc26fc44d","67c64b5707fd60e8443c4eedc6d73b7360fa72e924a495b867d6d214e6f3c4f5","4230fb53ca2fbfd44777a79b69f926510cda22433e1a5f3151d0355319db539c"),fingerprints,"golden pixels from original production callers");
    }
    @Test void callersKeepTheirHoldReleaseHurryAndCompletionPolicies() throws Exception {
        Object early=slayAt(card,27);slayHurry.invoke(early);assertTrue((boolean)slayShowing.invoke(early));
        Object ready=slayAt(card,28);slayHurry.invoke(ready);assertFalse((boolean)slayShowing.invoke(ready));assertFalse((boolean)slayDone.invoke(ready));
        for(int i=0;i<31;i++)slayUpdate.invoke(ready);assertTrue((boolean)slayDone.invoke(ready));
        var held=canvas(800);assertTrue((boolean)sitarDraw.invoke(sitarScreens,held,10000L,false));
        assertEquals(4,held.ops().size());assertEquals(336,held.ops().getFirst().x0());
        var gone=canvas(800);assertFalse((boolean)sitarDraw.invoke(sitarScreens,gone,160L,true));assertTrue(gone.ops().isEmpty());
        var duration=method(flappyDraw.getDeclaringClass(),"duration",int.class);
        assertEquals(142,duration.invoke(null,320));assertEquals(149,duration.invoke(null,528));assertEquals(157,duration.invoke(null,800));
    }
    @Test void missingProviderFramesAndNestedAlphaKeepSourceOwnership() throws Exception {
        for(SceneSpriteSet missing:List.of(frames(3,false),frames(4,true))){
            var c=canvas(400);when(rom.titleCard(anyInt(),anyInt())).thenReturn(missing);
            try(var outer=new SceneCanvasTransform(c,7,-2,0.5f);var inner=new SceneCanvasTransform(outer,-2,5,0.5f)){
                sitarDraw.invoke(sitarScreens,inner,28L,false);
            }
            assertEquals(3,c.ops().size());assertEquals(0x40FFFFFF,c.ops().getFirst().tint());assertEquals(141,c.ops().getFirst().x0());assertEquals(67,c.ops().getFirst().y0());
        }
        when(rom.titleCard(anyInt(),anyInt())).thenReturn(card);
        var hidden=canvas(400);flappyDraw.invoke(null,hidden,card,28L,40,null,false);assertEquals(3,hidden.ops().size());
    }
    @Test void providerMetadataOwnsTheActPieceEvenWhenZoneIdsWereHistoricallySpecialCased() throws Exception {
        Object original=field(sitarScene,"song");
        Object supplied=sitar.loader().loadClass("sitarhero.model.SongSpec").getConstructor(String.class,String.class,String.class,int.class,int.class,int.class,int.class,List.class,boolean.class)
                .newInstance("test","Test Zone","s3k",1,600,10,0,List.of(),false);
        try{
            set(sitarScene,"song",supplied);var c=canvas(400);sitarDraw.invoke(sitarScreens,c,28L,false);
            assertEquals(4,c.ops().size(),"provider frames, rather than a zone-number rule, own piece availability");
        }finally{set(sitarScene,"song",original);}
    }
    @Test void flappyReplacementNameKeepsItsArrivalOriginAndShowActPolicy() throws Exception {
        int[] calls={0};Class<?> painter=flappyDraw.getParameterTypes()[4];
        Object replacement=Proxy.newProxyInstance(painter.getClassLoader(),new Class<?>[]{painter},(proxy,m,args)->{
            calls[0]++;((SceneCanvas)args[0]).fill(((Float)args[1]).intValue(),((Float)args[2]).intValue(),32,16,0xFF112233);return null;
        });
        var c=canvas(400);flappyDraw.invoke(null,c,card,28L,40,replacement,false);
        assertEquals(1,calls[0]);assertEquals(3,c.ops().size());assertNull(c.ops().get(1).image());
        assertEquals(200,c.ops().get(1).x0());assertEquals(96,c.ops().get(1).y0());
    }
    static RecordingCanvas canvas(int width){return new RecordingCanvas(width,224,null);}
    static Object slayAt(SceneSpriteSet set,int age)throws Exception{Object result=slayConstructor.newInstance(set);for(int i=0;i<age;i++)slayUpdate.invoke(result);return result;}
    static SceneSpriteSet frames(int count,boolean absentAct){
        SceneSprite[] sprites=new SceneSprite[4];
        for(int i=0;i<4;i++){int[] p=new int[32*16];for(int n=0;n<p.length;n++)p[n]=((n+i)%3==0?0x80:0xFF)<<24|((i+1)*50)<<16|((n*7)&255)<<8|((n*11)&255);sprites[i]=new SceneSprite(new SceneImage(32,16,p),i*2,i);}
        if(absentAct)sprites[3]=null;
        return new SceneSpriteSet(){public int frameCount(){return count;}public SceneSprite frame(int i){return i<count?sprites[i]:null;}public int[] animationFrames(int i){return new int[0];}public int animationDelay(int i){return 8;}};
    }
    static Method method(Class<?> c,String name,Class<?>...args)throws Exception{var m=c.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    static void assertOperations(List<SceneDrawOp>a,List<SceneDrawOp>b){assertEquals(a.size(),b.size());for(int i=0;i<a.size();i++){var x=a.get(i);var y=b.get(i);assertSame(x.image(),y.image());assertEquals(x.u0(),y.u0());assertEquals(x.v0(),y.v0());assertEquals(x.u1(),y.u1());assertEquals(x.v1(),y.v1());assertEquals(x.x0(),y.x0());assertEquals(x.y0(),y.y0());assertEquals(x.x1(),y.x1());assertEquals(x.y1(),y.y1());assertEquals(x.tint(),y.tint());}}
    /** CPU raster of recorded quads, independent of the title/font presentation code. */
    static byte[] pixels(RecordingCanvas canvas){
        int width=canvas.width(),height=canvas.height();int[] out=new int[width*height];var sources=new IdentityHashMap<SceneImage,int[]>();
        for(var op:canvas.ops())for(int y=Math.max(0,(int)op.y0());y<Math.min(height,(int)op.y1());y++)for(int x=Math.max(0,(int)op.x0());x<Math.min(width,(int)op.x1());x++){
            int sx=(int)(op.u0()+(x-op.x0())*(op.u1()-op.u0())/(op.x1()-op.x0()));
            int sy=(int)(op.v0()+(y-op.y0())*(op.v1()-op.v0())/(op.y1()-op.y0()));
            int source=sources.computeIfAbsent(op.image(),SceneImage::pixels)[sy*op.image().width()+sx],tint=op.tint(),dest=out[y*width+x];
            int alpha=(source>>>24)*(tint>>>24)/255,result=0xFF000000;
            for(int shift=0;shift<=16;shift+=8){int channel=(((source>>>shift)&255)*((tint>>>shift)&255)/255*alpha+((dest>>>shift)&255)*(255-alpha))/255;result|=channel<<shift;}out[y*width+x]=result;
        }
        byte[] bytes=new byte[out.length*4];for(int i=0;i<out.length;i++)for(int j=0;j<4;j++)bytes[i*4+j]=(byte)(out[i]>>>(j*8));return bytes;
    }
}
