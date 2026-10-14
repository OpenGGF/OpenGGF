package com.openggf.game.sonic3k.ending;

import com.openggf.game.GameServices;
import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.resources.S3kKosRamDestinations;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.game.timing.HardwareWorkKind;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;

/** Ending_ScreenEvent loc_5AC12 / loc_5ADCE: native queue and Load_Level handoffs. */
public final class EndingSceneResourceHandoff {
    private EndingSceneResourceHandoff() { }
    private static S3kEndingSceneState scene(ObjectServices s) { return S3kEndingCoordinator.state(s).scene(); }
    public static void queueAiz(ObjectServices s) { queue(s,S3kEndingSceneState.AIZ); }
    public static void queueSsz(ObjectServices s) { queue(s,S3kEndingSceneState.SSZ); }
    private static void queue(ObjectServices s,int sceneId) {
        var state=scene(s);state.beginQueue(sceneId);
        try {
            var rom=s.rom();var direct=S3kRuntimeArtCoordinator.from(s).directQueue();
            int entry=Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR+(sceneId==S3kEndingSceneState.AIZ?26:21)*24;
            // The native AIZ restore uses the post-intro main block/art sources.
            state.appendDirect(direct.queueStandardKos(rom,rom.read32BitAddr(entry+16)&0xFFFFFF,
                    S3kKosRamDestinations.RAM_START).ordinal());
            state.appendDirect(direct.queueStandardKos(rom,rom.read32BitAddr(entry+8)&0xFFFFFF,
                    S3kKosRamDestinations.BLOCK_TABLE).ordinal());
            if(sceneId==S3kEndingSceneState.AIZ)
                state.appendDirect(direct.queueStandardKos(rom,rom.read32BitAddr(entry+12)&0xFFFFFF,
                        S3kKosRamDestinations.blockTableOffset(0x268)).ordinal());
            var art=S3kEndingCoordinator.state(s).art();
            art.queueModule(s,rom.read32BitAddr(entry)&0xFFFFFF,0);
            if(sceneId==S3kEndingSceneState.AIZ)art.queueModule(s,rom.read32BitAddr(entry+4)&0xFFFFFF,0xBE);
        }catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    public static boolean ready(ObjectServices s) {
        var resources=S3kRuntimeArtCoordinator.from(s);var state=scene(s);
        if(state.pendingScene()<0)return false;
        // Native tst.b Kos_modules_left waits on the physical FIFO, including actor uploads.
        if(resources.moduleQueue().hasPendingPhysicalModules())return false;
        for(int i=0;i<state.directCount();i++)
            if(!resources.directQueue().isReady(s.hardwareTiming().pendingHandle(
                    HardwareWorkKind.KOS_DECOMPRESSION_QUEUE,state.directOrdinal(i)).orElseThrow()))return false;
        return true;
    }
    public static void installAiz(ObjectServices s) { install(s,S3kEndingSceneState.AIZ); }
    public static void installSsz(ObjectServices s) { install(s,S3kEndingSceneState.SSZ); }
    private static void install(ObjectServices s,int sceneId) {
        if(!ready(s)||scene(s).pendingScene()!=sceneId)throw new IllegalStateException("Ending scene resources are not ready");
        if(!(s.currentLevel() instanceof Sonic3kLevel level))throw new IllegalStateException("Ending has no S3K level");
        try {
            var rom=s.rom();var state=scene(s);var queue=S3kRuntimeArtCoordinator.from(s).directQueue();
            byte[][] payload=new byte[state.directCount()][];
            for(int i=0;i<payload.length;i++)payload[i]=queue.claim(s.hardwareTiming().pendingHandle(
                    HardwareWorkKind.KOS_DECOMPRESSION_QUEUE,state.directOrdinal(i)).orElseThrow());
            if(sceneId==S3kEndingSceneState.AIZ)adjustAizChunks(payload[0]);
            int tableIndex=sceneId==S3kEndingSceneState.AIZ?0:27;
            int layoutAddress=rom.read32BitAddr(Sonic3kConstants.LEVEL_PTRS_ADDR+tableIndex*4)&0xFFFFFF;
            int collisionPtr=rom.read32BitAddr(Sonic3kConstants.SOLID_INDEXES_ADDR+tableIndex*4);
            int collision=collisionPtr&0xFFFFFF;
            boolean noninterleaved=(collisionPtr&0x80000000)!=0||(collision&1)!=0||collision>=0x260000;
            int collisionLength=noninterleaved?0x600:0xC00;
            byte[] primary=rom.readBytes(collision,collisionLength);
            byte[] secondary=rom.readBytes(collision+(noninterleaved?0x600:1),collisionLength);
            var context=new LayoutMutationContext(LevelMutationSurface.forLevel(level),s.levelManager()::applyMutationEffects);
            s.zoneLayoutMutationPipeline().applyImmediatelyWithoutRedraw(c->{
                level.applyBlockOverlay(payload[0],0,false);
                level.applyChunkOverlay(payload[1],0,false);
                if(sceneId==S3kEndingSceneState.AIZ)level.applyChunkOverlay(payload[2],0x268,false);
                level.applySceneCollisionIndices(primary,secondary);
                try{level.applySceneLayout(rom,layoutAddress);}catch(IOException failure){throw new UncheckedIOException(failure);}
                return MutationEffects.NONE;
            },context);
            // Existing semantic level installation refreshes geometry without a fresh zone/load boundary.
            s.levelManager().setLevel(level);
            loadTargetPalette(s,3);
            if(sceneId==S3kEndingSceneState.AIZ){
                loadTargetPalette(s,0x2A);
                int sszPalette=rom.read32BitAddr(Sonic3kConstants.PAL_POINTERS_ADDR+31*8)&0xFFFFFF;
                S3kEndingPalette.targetLine(s,1,sszPalette+0x20);
                s.paletteOwnershipRegistryOrNull().applyTargetPatch("s3k-ending",3,8,rom.readBytes(0x4FAE4,8));
            }else {
                for(int line=1;line<4;line++)S3kEndingPalette.targetLine(s,line,S3kEndingResources.PAL_ENDING_ISLAND+(line-1)*32);
                // loc_5AE36 calls sub_5B11C after Pal_Ending1, replacing its
                // first two target lines. The background redraw starts at20,
                // so it does not revisit the initial stage0 palette owner.
                S3kEndingPalette.targetLine(s,1,S3kEndingResources.PAL_ENDING_SKY);
                S3kEndingPalette.targetLine(s,2,S3kEndingResources.PAL_SSZ2_REUNION);
            }
            state.installed();
            int x=sceneId==S3kEndingSceneState.AIZ?0x1EB8:0x200;
            int y=sceneId==S3kEndingSceneState.AIZ?0x1E0:0x200;
            state.foreground().begin(x,y);
            // Background scene events select their own deformation/source coordinates.
        }catch(IOException failure){throw new UncheckedIOException(failure);}
    }
    private static void loadTargetPalette(ObjectServices s,int index)throws IOException{
        var rom=s.rom();int entry=Sonic3kConstants.PAL_POINTERS_ADDR+index*8;
        int source=rom.read32BitAddr(entry)&0xFFFFFF;
        int destination=rom.read16BitAddr(entry+4)&0xFFFF;
        int length=((rom.read16BitAddr(entry+6)&0xFFFF)+1)*4;
        for(int offset=0;offset<length;offset+=32)
            s.paletteOwnershipRegistryOrNull().applyTargetPatch("s3k-ending",(destination-0xFC00+offset)/32,0,
                    rom.readBytes(source+offset,Math.min(32,length-offset)));
    }
    /** Draw_PlaneVertBottomUp publishes two real retained descriptor rows per call. */
    public static boolean drawDelayedRow(ObjectServices s,boolean background){
        var plane=background?scene(s).background():scene(s).foreground();
        if(background&&scene(s).activeScene()==S3kEndingSceneState.AIZ)
            return plane.advanceComplex(y->com.openggf.game.sonic3k.scroll.SwScrlEnding.backgroundSourceX(s,y),
                    s.levelManager()::getBackgroundTileDescriptorAtWorld);
        return plane.advance(background?s.levelManager()::getBackgroundTileDescriptorAtWorld:
                s.levelManager()::getForegroundTileDescriptorAtWorld);
    }
    private static void adjustAizChunks(byte[] chunks){
        // Adjust_AIZ1Chunks2 writes native 128x128 table words, not layout bytes.
        for(int offset:new int[]{0x4C70,0x4CF0,0x4D70,0x52F0,0x4980,0x4D80,0x3420,0x3FA0})fill(chunks,offset,8,0x4B);
        word(chunks,0x3430,0x402);fill(chunks,0x3432,6,0x4B);word(chunks,0x343E,2);
        word(chunks,0x3FB4,0xC);word(chunks,0x3FB6,0x406);word(chunks,0x3FB8,0x4B);word(chunks,0x3FBA,0x4B);word(chunks,0x3FBE,0x40A);
        word(chunks,0x5820,0x40B);fill(chunks,0x5822,6,0x4B);word(chunks,0x5834,0xC);word(chunks,0x5836,0x406);fill(chunks,0x5838,2,0x4B);
    }
    private static void fill(byte[] bytes,int offset,int count,int value){for(int i=0;i<count;i++)word(bytes,offset+i*2,value);}
    private static void word(byte[] bytes,int offset,int value){bytes[offset]=(byte)(value>>>8);bytes[offset+1]=(byte)value;}

    /** Scene layouts change width; resident VRAM must also survive a rewind across uploads. */
    public static final class GeometryAdapter implements RewindSnapshottable<GeometrySnapshot>{
        private byte[] lastPatterns;
        private Sonic3kLevel.SceneLayoutSnapshot lastLayout;
        @Override public String key(){return "s3k-ending-scene-resources";}
        @Override public GeometrySnapshot capture(){
            if(!active())return GeometrySnapshot.EMPTY;
            var level=(Sonic3kLevel)GameServices.level().getCurrentLevel();
            byte[] bytes=new byte[level.getPatternCount()*32];
            byte[] pixels=new byte[64];
            for(int tile=0;tile<level.getPatternCount();tile++){
                Pattern pattern=level.getPattern(tile);
                pattern.copyInto(pixels,0);
                for(int pixel=0;pixel<64;pixel+=2)bytes[tile*32+pixel/2]=
                        (byte)((pixels[pixel]<<4)|(pixels[pixel+1]&15));
            }
            if(Arrays.equals(bytes,lastPatterns))bytes=lastPatterns;else lastPatterns=bytes;
            var layout=level.captureSceneLayout();
            if(sameLayout(layout,lastLayout))layout=lastLayout;else lastLayout=layout;
            return new GeometrySnapshot(layout,bytes);
        }
        @Override public void restore(GeometrySnapshot snapshot){
            if(snapshot.layout()==null)return;
            var manager=GameServices.level();var level=(Sonic3kLevel)manager.getCurrentLevel();
            level.restoreSceneLayout(snapshot.layout());
            level.applyPatternOverlay(snapshot.patterns(),0,true);
            Sonic3kPlcLoader.refreshAffectedRenderers(List.of(new Sonic3kPlcLoader.TileRange(0,snapshot.patterns().length/32)),manager);
            manager.setLevel(level);lastPatterns=snapshot.patterns();lastLayout=snapshot.layout();
        }
        private static boolean sameLayout(Sonic3kLevel.SceneLayoutSnapshot a,Sonic3kLevel.SceneLayoutSnapshot b){
            return b!=null&&a.mapData()==b.mapData()&&a.width()==b.width()&&a.height()==b.height()
                    &&a.fgWidth()==b.fgWidth()&&a.fgHeight()==b.fgHeight()&&a.bgWidth()==b.bgWidth()
                    &&a.bgHeight()==b.bgHeight()&&Arrays.equals(a.backgroundRows(),b.backgroundRows());
        }
        private boolean active(){return GameServices.hasRuntime()&&
                (GameServices.zoneRuntimeState() instanceof EndingZoneRuntimeState||
                 GameServices.zoneRuntimeState() instanceof SszZoneRuntimeState ssz&&ssz.actIndex()==1);}
    }
    public record GeometrySnapshot(Sonic3kLevel.SceneLayoutSnapshot layout,byte[] patterns){
        private static final GeometrySnapshot EMPTY=new GeometrySnapshot(null,null);
    }
}
