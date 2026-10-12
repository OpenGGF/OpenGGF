package com.openggf.mods.scene.host.music;

import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Real PCM behavior, independent of a speaker, ROM availability or creator policy mocks. */
class TestScenePartCues {
    private Object mixer(short[] full, short[] masked, int rate) throws Exception {
        Class<?> type = assertDoesNotThrow(() -> Class.forName("com.openggf.mods.scene.host.music.PartCueMixer"),
                "Prepared-part cue mixing is missing");
        var ctor = type.getDeclaredConstructor(short[].class, short[].class, int.class); ctor.setAccessible(true);
        return ctor.newInstance(full, masked, rate);
    }
    private Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) { if (e.getCause() instanceof Exception cause) throw cause; throw e; }
    }
    private boolean play(Object m, long source, int length, double from, double to, double gain, double pan) throws Exception {
        return (boolean) call(m, "play", new Class<?>[]{long.class,int.class,double.class,double.class,double.class,double.class}, source,length,from,to,gain,pan);
    }
    private short[] mix(Object m, int frames) throws Exception {
        short[] pcm = new short[frames * 2];
        call(m, "mix", new Class<?>[]{short[].class,int.class}, pcm, frames); return pcm;
    }
    @Test void cueUsesOnlyPartResidualPreservesStereoAndReturnsToSilence() throws Exception {
        short[] full = new short[4000], backing = new short[4000];
        for (int i=0;i<2000;i++) { full[i*2]=3000; backing[i*2]=1000; full[i*2+1]=backing[i*2+1]=777; }
        Object m=mixer(full,backing,8000); assertTrue(play(m,0,480,1,.9,.5,0));
        short[] out=mix(m,600); assertEquals(0,out[0]);
        assertTrue(Arrays.stream(toInts(out)).anyMatch(v->v>100));
        for(int i=0;i<600;i++) assertEquals(0,out[i*2+1], "unselected right-side backing never becomes cue audio");
        assertEquals(0,out[out.length-2]); assertEquals(0,out[out.length-1]);
    }
    private int[] toInts(short[] pcm) { int[] ints=new int[pcm.length];for(int i=0;i<pcm.length;i++)ints[i]=pcm[i];return ints; }
    @Test void ratesPanAndGainAreFiniteBoundedAndSourceDoesNotWrap() throws Exception {
        Object m=mixer(new short[2000],new short[2000],8000);
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,.49,2.01}) {
            assertThrows(IllegalArgumentException.class,()->play(m,0,100,bad,1,.5,0));
            assertThrows(IllegalArgumentException.class,()->play(m,0,100,1,bad,.5,0));
        }
        assertThrows(IllegalArgumentException.class,()->play(m,-1,100,1,1,.5,0));
        assertThrows(IllegalArgumentException.class,()->play(m,1000,100,1,1,.5,0));
        assertThrows(IllegalArgumentException.class,()->play(m,0,2001,1,1,.5,0));
        assertThrows(IllegalArgumentException.class,()->play(m,0,0,1,1,.5,0));
        assertThrows(IllegalArgumentException.class,()->play(m,0,100,1,1,Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->play(m,0,100,1,1,1,2));
        assertTrue(play(m,999,100,1,1,.5,0)); assertArrayEquals(new short[200],mix(m,100));
    }
    @Test void polyphonyIsBoundedAndStopReleasesPcm() throws Exception {
        short[] full=new short[4000]; Arrays.fill(full,(short)30000);
        Object m=mixer(full,new short[4000],8000);
        for(int i=0;i<6;i++)assertTrue(play(m,0,100,1,1,1,0));
        assertFalse(play(m,0,100,1,1,1,0));
        short[] out=mix(m,100); assertEquals(Short.MAX_VALUE,out[20]);
        assertTrue(play(m,0,100,1,1,1,0)); call(m,"close",new Class<?>[0]);
        assertFalse(play(m,0,100,1,1,1,0)); assertArrayEquals(new short[200],mix(m,100));
        for(String field:new String[]{"full","masked"}) { var f=m.getClass().getDeclaredField(field);f.setAccessible(true);assertNull(f.get(m)); }
    }
    @Test void pitchGlideChangesWaveformAndChunksDoNotChangeTheCue() throws Exception {
        short[] full=new short[8000];for(int i=0;i<4000;i++)full[i*2]=full[i*2+1]=(short)(10000*Math.sin(i*.17));
        Object a=mixer(full,new short[8000],8000),b=mixer(full,new short[8000],8000),fixed=mixer(full,new short[8000],8000);
        play(a,50,800,1,.8,.5,-1);play(b,50,800,1,.8,.5,-1);play(fixed,50,800,1,1,.5,-1);
        short[] whole=mix(a,800),chunks=new short[1600];
        for(int i=0;i<8;i++)System.arraycopy(mix(b,100),0,chunks,i*200,200);
        assertArrayEquals(whole,chunks);assertFalse(Arrays.equals(whole,mix(fixed,800)));
        for(int i=0;i<800;i++)assertEquals(0,whole[i*2+1]);
    }
    @Test void sourceExhaustionTapersToZeroInsteadOfCuttingTheLastWaveSample() throws Exception {
        short[] full=new short[2000];Arrays.fill(full,(short)30000);
        Object m=mixer(full,new short[2000],8000);
        assertTrue(play(m,996,100,1,1,1,0));
        short[] out=mix(m,10);
        assertEquals(0,out[6],"last available source frame must already be silent before exhaustion");
        for(int i=4;i<10;i++)assertEquals(0,out[i*2]);
    }
}
