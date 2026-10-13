package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;

/** Ending_BackgroundEvent: sub_5B092/loc_58D44 and sub_5B0A8. */
public final class SwScrlEnding extends AbstractZoneScrollHandler implements ScrollOutputRebuilder {
    private final int[] islandBands,cloudBands;
    private final SwScrlAiz aiz=SwScrlAiz.detachedPresentation();
    private int foregroundY,backgroundX;
    public SwScrlEnding(Rom rom)throws IOException{
        islandBands=readBands(rom,0x5B144,11);cloudBands=readBands(rom,0x5B15A,10);
    }
    private static int[] readBands(Rom rom,int address,int count)throws IOException{
        byte[] bytes=rom.readBytes(address,count*2);int[] bands=new int[count];
        for(int i=0;i<count;i++)bands[i]=(bytes[i*2]&255)<<8|(bytes[i*2+1]&255);
        if(bands[count-1]!=0x7FFF)throw new IOException("Native ending deformation terminator missing");
        return bands;
    }
    @Override public void update(int[] output,int cameraX,int cameraY,int frame,int act){
        fillScroll(output,cameraX,cameraY,frame,act,false);
    }
    @Override public void rebuildOutput(int[] output,int cameraX,int cameraY,int frame,int act){
        fillScroll(output,cameraX,cameraY,frame,act,true);
    }
    private void fillScroll(int[] output,int cameraX,int cameraY,int frame,int act,boolean rebuild){
        if(!(GameServices.zoneRuntimeRegistry().current() instanceof EndingZoneRuntimeState ending))return;
        var ram=ending.screen();foregroundY=ram.foregroundRoutine>=16&&ram.foregroundRoutine<=36?cameraY:
                ram.foregroundRoutine>=56?cameraY:ram.foregroundY;
        if(ram.backgroundRoutine>=8&&ram.backgroundRoutine<=16){
            if(!rebuild&&ram.aizWaveOriginFrame==Integer.MIN_VALUE)ram.aizWaveOriginFrame=frame;
            aiz.setDetachedWaveOriginFrame(ram.aizWaveOriginFrame==Integer.MIN_VALUE?frame:ram.aizWaveOriginFrame);
            aiz.update(output,cameraX,cameraY,frame,0);
            vscrollFactorBG=(short)(aiz.getVscrollFactorBG()-(ram.aizBackgroundScrollFixed>>16));
            if(!rebuild)ram.backgroundCameraY=vscrollFactorBG;
            backgroundX=-(short)output[0];resetScrollTracking();
            for(int i=0;i<224;i++)trackOffsetFromPacked(output[i]);return;
        }
        boolean cloud=ram.backgroundRoutine!=0;
        if(!rebuild&&ram.backgroundScrollFrame!=frame){ram.backgroundScrollFrame=frame;
            if(cloud)cloudParameters(ram);else islandParameters(ram);}
        vscrollFactorBG=(short)ram.backgroundCameraY;backgroundX=ram.hScrollWords[2];
        int foregroundX=ram.foregroundRoutine>=56?cameraX:ram.foregroundX;
        compose(output,foregroundX,ram.backgroundCameraY,ram.hScrollWords,cloud?cloudBands:islandBands);
        resetScrollTracking();for(int i=0;i<224;i++)trackOffsetFromPacked(output[i]);
    }
    /** loc_58D44 consumes the old longword, clears its fraction, then writes backwards. */
    static void islandParameters(EndingZoneRuntimeState.ScreenRam ram){
        ram.backgroundCameraY=Math.min(0x120,ram.scrollYFixed>>>16);
        int value=ram.cloudDriftFixed&0xFFFF0000;ram.cloudDriftFixed+=0x8000;
        int step=value>>3;ram.hScrollWords[0x18/2]=0x5E;
        for(int i=0;i<8;i++){ram.hScrollWords[(0x16-i*2)/2]=(short)(value>>16);value-=step;}
    }
    /** sub_5B0A8 uses the accumulator before adding $800, and scatters from its ramp. */
    static void cloudParameters(EndingZoneRuntimeState.ScreenRam ram){
        ram.backgroundCameraY=ram.scrollYFixed>>>16;
        int value=ram.scrollXFixed&0xFFFF0000,step=(value>>4)+ram.cloudDriftFixed;
        ram.cloudDriftFixed+=0x800;
        for(int i=0;i<32;i++){for(int j=0;j<4;j++)ram.hScrollWords[0x14/2+i*4+j]=(short)(value>>16);value+=step;}
        put(ram,0x10,word(ram,0x14));put(ram,8,word(ram,0x14));
        put(ram,0xC,word(ram,0x1C));put(ram,0x12,word(ram,0x24));
        put(ram,6,word(ram,0x2C));put(ram,0xA,word(ram,0x34));put(ram,0xE,word(ram,0x3C));
    }
    private static int word(EndingZoneRuntimeState.ScreenRam ram,int offset){return ram.hScrollWords[offset/2];}
    private static void put(EndingZoneRuntimeState.ScreenRam ram,int offset,int value){ram.hScrollWords[offset/2]=(short)value;}
    /** loc_5AFC4 enters AIZ1_Deform before it initializes the delayed Plane B rows. */
    public static void prepareBackgroundForRedraw(ObjectServices services){
        var ram=((EndingZoneRuntimeState)services.zoneRuntimeState()).screen();
        if(ram.backgroundRoutine==8)ram.backgroundCameraY=(short)services.camera().getY()>>1;
        else ram.backgroundCameraY=ram.scrollYFixed>>>16;
    }
    /** AIZ1_BGDrawArray=$220,$7FFF, a5=HScroll_table-$04. First source long is cleared. */
    public static int backgroundSourceX(ObjectServices services,int sourceY){
        var ram=((EndingZoneRuntimeState)services.zoneRuntimeState()).screen();
        if(ram.backgroundRoutine>=8&&ram.backgroundRoutine<=16)
            return (sourceY&0xFFF)<0x220?0:((short)(services.camera().getX()-0x1300)>>1);
        return 0x200;
    }
    /** ApplyDeformation with a5=HScroll_table+$004, including $8080 per-line consumption. */
    static void compose(int[] output,int cameraX,int bgY,short[] words,int[] bands){
        int band=0,source=2,skip=(short)bgY;
        while(band<bands.length-1&&skip>=(bands[band]&0x7FFF)){
            int height=bands[band]&0x7FFF;skip-=height;
            source+=(bands[band]&0x8000)==0?1:height;band++;
        }
        if((bands[band]&0x8000)!=0)source+=skip;
        int remaining=(bands[band]&0x7FFF)-skip;
        for(int line=0;line<224;line++){
            if(source<0||source>=words.length)throw new IllegalStateException("Ending HScroll_table overrun");
            output[line]=((-cameraX&0xFFFF)<<16)|(-words[source]&0xFFFF);
            if((bands[band]&0x8000)!=0)source++;
            if(--remaining==0){if((bands[band]&0x8000)==0)source++;remaining=bands[++band]&0x7FFF;}
        }
    }
    @Override public short getVscrollFactorFG(){return (short)foregroundY;}
    @Override public int getBgCameraX(){return backgroundX;}
}
