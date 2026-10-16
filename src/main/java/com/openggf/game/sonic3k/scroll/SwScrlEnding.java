package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.ScrollOutputRebuilder;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Ending_BackgroundEvent: sub_5B092/loc_58D44 and sub_5B0A8. */
public final class SwScrlEnding extends AbstractZoneScrollHandler implements ScrollOutputRebuilder {
    private final Rom rom;
    private BandTables bands;
    private record BandTables(int[] island, int[] clouds) {}
    private int[] aizBands; // Immutable ROM bytes, separate from captured native HScroll RAM.
    private int foregroundY,backgroundX;
    public SwScrlEnding(Rom rom)throws IOException{ this.rom=rom; }
    private BandTables bands() {
        if (bands == null) {
            try {
                // Like SOZ/LRZ3, unrelated provider registration does not consume this zone's ROM.
                // Publish the immutable pair only after both native terminators validate.
                int[] island = readBands(rom, 0x5B144, 11);
                int[] clouds = readBands(rom, 0x5B15A, 10);
                bands = new BandTables(island, clouds);
            } catch (IOException failure) {
                throw new UncheckedIOException("Native ending deformation tables", failure);
            }
        }
        return bands;
    }
    private int[] aizBands(){
        if(aizBands==null)try{aizBands=readBands(rom,0x23B258,14);}
        catch(IOException failure){throw new UncheckedIOException("Native ending AIZ deformation table",failure);}
        return aizBands;
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
        var tables = bands(); // Fail before native RAM/output changes, including pure post-restore rebuild.
        // ScreenEvents ends with V_scroll_value=Camera_Y_pos_copy, the copy taken before the
        // event handlers (sonic3k.asm:102233-102253); credits loc_5B204 advances Camera_Y_pos
        // afterwards, so its new position is first published by the following frame's copy.
        int copyY=GameServices.camera().getYCopy();
        var ram=ending.screen();foregroundY=ram.foregroundRoutine>=16&&ram.foregroundRoutine<=36?copyY:
                ram.foregroundRoutine>=56?copyY:ram.foregroundY;
        if(ram.backgroundRoutine>=8&&ram.backgroundRoutine<=16){
            var activeBands=aizBands(); // Validate before the first native bank write.
            if(!rebuild&&ram.aizDeformationFrame!=frame){
                ram.aizDeformationFrame=frame;aizParameters(ram,cameraX,cameraY);
            }
            vscrollFactorBG=(short)ram.backgroundCameraY;
            composeFrom(output,cameraX,ram.backgroundCameraY,ram.hScrollWords,activeBands,4);
            backgroundX=-(short)output[0];resetScrollTracking();
            for(int i=0;i<224;i++)trackOffsetFromPacked(output[i]);return;
        }
        boolean cloud=ram.backgroundRoutine!=0;
        if(!rebuild&&ram.backgroundScrollFrame!=frame){ram.backgroundScrollFrame=frame;
            if(cloud)cloudParameters(ram);else islandParameters(ram);}
        vscrollFactorBG=(short)ram.backgroundCameraY;backgroundX=ram.hScrollWords[2];
        int foregroundX=ram.foregroundRoutine>=56?cameraX:ram.foregroundX;
        compose(output,foregroundX,ram.backgroundCameraY,ram.hScrollWords,cloud?tables.clouds():tables.island());
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
    /** AIZ1_Deform consumes the inherited HScroll+$3C long before adding $2000.
     * Its 32-bit overflow and swapped-word stores are native RAM, not an elapsed-frame wave.
     */
    static void aizParameters(EndingZoneRuntimeState.ScreenRam ram,int cameraX,int cameraY){
        ram.backgroundCameraY=(short)cameraY>>1;
        int base=((short)(cameraX-0x1300)<<16)>>5,increment=base+base;
        int value=(increment<<3)-increment;
        put(ram,0x30,value>>16);value+=increment;
        put(ram,4,value>>16);put(ram,0x32,value>>16);put(ram,0x3A,value>>16);
        value+=increment;put(ram,0x34,value>>16);put(ram,0x38,value>>16);
        value+=increment;put(ram,0x36,value>>16);put(ram,0x14,base>>16);
        int wave=(word(ram,0x3C)&0xFFFF)<<16|(word(ram,0x3E)&0xFFFF);
        int nextWave=wave+0x2000;put(ram,0x3C,nextWave>>>16);put(ram,0x3E,nextWave);
        value=base>>1;
        for(int offset=0x12;offset>=8;offset-=2){value+=wave;put(ram,offset,value>>16);value+=base;}
        value=base;increment=base>>3;
        for(int offset=0x16;offset<=0x2E;offset+=2){value+=increment;put(ram,offset,value>>16);}
        if(ram.backgroundRoutine==16)ram.backgroundCameraY=(short)(ram.backgroundCameraY-(ram.aizBackgroundScrollFixed>>16));
    }
    /** loc_5AFF0/5B012 owns exactly one AIZ deformation before its Draw_BG tail. */
    public static void prepareAizBackgroundEvent(ObjectServices services){
        var ram=((EndingZoneRuntimeState)services.zoneRuntimeState()).screen();
        int levelFrame=services.levelManager().getFrameCounter();
        if(ram.aizDeformationFrame==levelFrame)return;
        ram.aizDeformationFrame=levelFrame;
        aizParameters(ram,services.camera().getXCopy(),services.camera().getYCopy());
    }
    private static int word(EndingZoneRuntimeState.ScreenRam ram,int offset){return ram.hScrollWords[offset/2];}
    private static void put(EndingZoneRuntimeState.ScreenRam ram,int offset,int value){ram.hScrollWords[offset/2]=(short)value;}
    /** Ending_BackgroundInit owns the first sub_5B092 before setup Process_Sprites. */
    public static void initializeBackground(ObjectServices services) {
        var ram=((EndingZoneRuntimeState)services.zoneRuntimeState()).screen();
        islandParameters(ram);ram.backgroundScrollFrame=services.levelManager().getFrameCounter();
    }
    /** loc_5AF88 draws after its BG motion writer; output composition must not advance twice. */
    public static void prepareBackgroundEvent(ObjectServices services) {
        var ram=((EndingZoneRuntimeState)services.zoneRuntimeState()).screen();
        int frame=services.levelManager().getFrameCounter();
        if(ram.backgroundScrollFrame==frame)return;
        ram.backgroundScrollFrame=frame;
        if(ram.backgroundRoutine==0)islandParameters(ram);else cloudParameters(ram);
    }
    /** loc_5AFC4 enters AIZ1_Deform before it initializes the delayed Plane B rows. */
    public static void prepareBackgroundForRedraw(ObjectServices services){
        var ram=((EndingZoneRuntimeState)services.zoneRuntimeState()).screen();
        if(ram.backgroundRoutine==8){
            prepareAizBackgroundEvent(services);
            // loc_5AFC4 clears only the first long after AIZ1_Deform, not +$3C.
            ram.cloudDriftFixed=0;put(ram,0,0);put(ram,2,0);
            put(ram,6,(((short)(services.camera().getXCopy()-0x1300)<<16)>>5)>>3);
        }else ram.backgroundCameraY=ram.scrollYFixed>>>16;
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
        composeFrom(output,cameraX,bgY,words,bands,2);
    }
    private static void composeFrom(int[] output,int cameraX,int bgY,short[] words,int[] bands,int firstWord){
        int band=0,source=firstWord,skip=(short)bgY;
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
