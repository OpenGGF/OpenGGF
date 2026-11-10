package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure normal-palette CyclePalette selection over borrowed media views.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: r4/palette_cycle_a.asm:4-85,
 * palette_cycle_b/d.asm:4-54, palette_cycle_c.asm:4-54; common/palette_load.asm:3-14.
 * All CycleColor arithmetic/fault ordering is reused from CdLevelPaletteCycle.single.
 * Root supplies independently authenticated scripts/row4 data at the actual Main WORD gate.
 * Completed results cannot load, publish, replay, resume or adopt any live state.
 */
public final class CdR4PaletteComposition {
    private CdR4PaletteComposition() { }
    public enum Mode { PRESENT, PAST_OR_FUTURE }
    /** Native allocated seven timer/step bytes and all four palettes, including untouched fade/water. */
    public record State(CdAssetBytes normal,CdAssetBytes water,CdAssetBytes fade,CdAssetBytes waterFade,
                        CdAssetBytes timers,CdAssetBytes steps) {
        public State {exact(normal,128);exact(water,128);exact(fade,128);exact(waterFade,128);exact(timers,7);exact(steps,7);}
    }
    /** Rooted native scripts retain lazy reads; the stage4 source is the native96-byte palette row.
     * Lists are3 (B/C/D),4 (A standard),6 (A upper), never synthesized duplicates. */
    public record Tables(List<CdLevelPaletteCycle.Script> standard,List<CdLevelPaletteCycle.Script> upper,CdAssetBytes stage4) {
        public Tables {standard=List.copyOf(standard);upper=List.copyOf(upper);Objects.requireNonNull(stage4);}
    }
    public enum Access { STAGE4_SOURCE, SCRIPT, COLOURS, NORMAL_PALETTE }
    public record Fault(int cycle,Access access,int offset,int bytes) { }
    public record Result(State state,List<CdLevelPaletteCycle.Write> writes,Optional<Fault> fault,int stage4LongsCopied) {
        public Result {Objects.requireNonNull(state);writes=List.copyOf(writes);Objects.requireNonNull(fault);}
        public boolean completed(){return fault.isEmpty();}
    }
    public static Result step(Mode mode,State before,int actByte,int cameraX,int cameraY,int timeStopWord,Tables tables) {
        Objects.requireNonNull(mode);Objects.requireNonNull(before);octet(actByte);word(cameraX);word(cameraY);word(timeStopWord);
        if(timeStopWord!=0||mode==Mode.PAST_OR_FUTURE&&actByte==0)return new Result(before,List.of(),Optional.empty(),0);
        Objects.requireNonNull(tables);boolean upper=mode==Mode.PRESENT&&actByte==0&&cameraX<0x280&&cameraY<0x160;
        List<CdLevelPaletteCycle.Script> scripts=upper?tables.upper():tables.standard();
        int count=upper?6:mode==Mode.PRESENT?4:3;
        if(scripts.size()!=count)throw new IllegalArgumentException("Exact native ordered cycle groups required");
        byte[] timers=before.timers().copy(),steps=before.steps().copy();var palette=before.normal();
        var writes=new ArrayList<CdLevelPaletteCycle.Write>();int copied=0;
        // Native A sets the latch only on the x<$280, y>=$160 branch, BEFORE LoadPalette4.
        if(mode==Mode.PRESENT&&actByte==0&&cameraX<0x280&&cameraY>=0x160){
            boolean held=(timers[4]&128)!=0;timers[4]|=(byte)128;
            if(!held){
                byte[] normal=palette.copy();
                for(int n=0;n<24;n++){
                    int at=n*4;
                    if(at>tables.stage4().length()-4)return result(before,CdAssetBytes.copyOf(normal),timers,steps,writes,new Fault(-1,Access.STAGE4_SOURCE,at,4),copied);
                    int value=(int)tables.stage4().unsignedLong(at);
                    for(int j=0;j<2;j++){int destination=32+at+2*j;int old=(normal[destination]&255)<<8|(normal[destination+1]&255);int next=(value>>>(16-16*j))&65535;normal[destination]=(byte)(next>>>8);normal[destination+1]=(byte)next;writes.add(new CdLevelPaletteCycle.Write(-1,destination/2,old,next));}
                    copied++;
                }
                palette=CdAssetBytes.copyOf(normal);
            }
        }
        for(int cycle=0;cycle<count;cycle++){
            var out=CdLevelPaletteCycle.single(scripts.get(cycle),new CdLevelPaletteCycle.SingleState(palette,timers[cycle]&255,steps[cycle]&255));
            palette=out.state().palette();timers[cycle]=(byte)out.state().timerByte();steps[cycle]=(byte)out.state().stepByte();
            for(var w:out.writes())writes.add(new CdLevelPaletteCycle.Write(cycle,w.paletteIndex(),w.beforeWord(),w.afterWord()));
            if(out.fault().isPresent()){var f=out.fault().get();return result(before,palette,timers,steps,writes,new Fault(cycle,Access.valueOf(f.access().name()),f.byteOffset(),f.bytes()),copied);}
        }
        return result(before,palette,timers,steps,writes,null,copied);
    }
    private static Result result(State before,CdAssetBytes normal,byte[] timers,byte[] steps,List<CdLevelPaletteCycle.Write> writes,Fault fault,int copied){return new Result(new State(normal,before.water(),before.fade(),before.waterFade(),CdAssetBytes.copyOf(timers),CdAssetBytes.copyOf(steps)),writes,Optional.ofNullable(fault),copied);}
    private static void exact(CdAssetBytes b,int size){if(Objects.requireNonNull(b).length()!=size)throw new IllegalArgumentException("Complete native palette/timer image required");}
    private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD required");}
    private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
}
