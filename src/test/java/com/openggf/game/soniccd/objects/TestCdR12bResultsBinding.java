package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static org.junit.jupiter.api.Assertions.*;

/** Actual retained USA R12B data plus explicit component scalar ingress, not World/Frame admission.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Results/Main.asm:61-149,349-366.
 * Authored before production; every Java check is UNRUN by this packet's author. */
class TestCdR12bResultsBinding {
    private static final int WHOLE=40;
    private static CdR12bTerrainAssets terrain,independent;

    @BeforeAll static void actualSource()throws Exception{
        String root=System.getProperty("soniccd.media.root");
        org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"optional actual original CD media root");
        Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var media=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){terrain=CdR12bTerrainAssets.fromPrograms(media);independent=CdR12bTerrainAssets.fromPrograms(media);}
    }

    @Test void actualAssetsRetainTheOnlyOperandSelectedInitAndCompleteMapSourceAfterMediaClose(){
        var o=terrain.objects();var assets=CdResults.Assets.fromAct2PastAssets(o);
        assertSame(o.view(CdR12bObjectAssets.View.RESULTS_INIT_DATA),assets.initData());assertSame(o.view(CdR12bObjectAssets.View.MAP_SPR_RESULTS),assets.mappings());
        assertEquals(0x20DEEC,assets.mappingsAddress());assertEquals(622,assets.mappings().length());assertEquals(0,assets.mappings().unsignedByte(621));
        assertEquals("00cc0000012000000110020000f0000100cc000001200002",HexFormat.of().formatHex(assets.initData().copy()));
        assertEquals(3,CdResults.Assets.class.getRecordComponents().length);assertEquals(1,CdResults.class.getConstructors().length);
        assertThrows(NullPointerException.class,()->CdResults.Assets.fromAct2PastAssets(null));
    }

    @Test void sameSourcePairGateRejectsEqualIndependentOrUnboundPlacementBeforeMetadataOrAnyPort(){
        var p=placement();var before=p.capture();
        assertThrows(IllegalArgumentException.class,()->CdResults.fromAct2PastAssets(CdObjectPlacementRuntime.fromAssets(independent,bank()),terrain,null,null,null,null,null,null,null,null));
        assertThrows(IllegalArgumentException.class,()->CdResults.fromAct2PastAssets(new CdObjectPlacementRuntime(terrain.placements()),terrain,null,null,null,null,null,null,null,null));
        assertThrows(IllegalArgumentException.class,()->CdResults.fromAct2PastAssets(p,independent,null,null,null,null,null,null,null,null));
        assertNotNull(CdResults.fromAct2PastAssets(p,terrain,null,null,null,null,null,null,null,null));nonwrites(before,p,Set.of());
    }

    @Test void actualBoundDelayChangesOnlyTheHighTimerByteAndReachesNoExternalOwner(){
        var p=placement();seed(p,0,0x02A7);var before=p.capture();var body=CdResults.fromAct2PastAssets(p,terrain,null,null,null,null,null,null,null,null);
        var result=body.run(WHOLE);assertEquals(0x01A7,result.state().word(RESULTS_TIMER_WORD));assertEquals(0,result.state().routine());nonwrites(before,p,Set.of(WHOLE));
    }

    @Test void reachedCueFailureKeepsTheActualBytePrefixAndOriginalExceptionBeforeRoutineAdvance(){
        var p=placement();seed(p,0,0x01A7);var before=p.capture();var original=new Fault();var order=new ArrayList<String>();
        var body=CdResults.fromAct2PastAssets(p,terrain,c->{throw new AssertionError("read after failed cue");},null,null,c->{order.add("cue");assertEquals(0x10,c.nativeId());assertEquals(0,c.expectedSource().routine());assertEquals(0x00A7,c.expectedSource().word(RESULTS_TIMER_WORD));throw original;},null,null,null,null);
        assertSame(original,assertThrows(Fault.class,()->body.run(WHOLE)));assertEquals(List.of("cue"),order);assertEquals(0,p.pool().state(WHOLE).routine());assertEquals(0x00A7,p.pool().state(WHOLE).word(RESULTS_TIMER_WORD));nonwrites(before,p,Set.of(WHOLE));
    }

    @Test void realComponentUsesAllFourLiteralMappingRootsFromTheActualDataWithNative480AndThreeRows(){
        for(int[] c:new int[][]{{1,0,0x20DEEC},{0x502,0,0x20DEF6},{1,0x80,0x20DF00},{0x502,0x80,0x20DF0A}}){
            var p=placement();seed(p,2,0x1234);var before=p.capture();var reads=new ArrayList<CdResults.ReadField>();
            var body=CdResults.fromAct2PastAssets(p,terrain,call->{reads.add(call.field());return switch(call.field()){
                case PLC_HEAD_LONG->0;case ZONE_ACT_WORD->c[0];case CAMERA_X_WORD->0x1000;case PLAYER_X_WORD->0x1151;case GOOD_FUTURE_BYTE->c[1];case ACT_BYTE->1;
                default->throw new AssertionError("unreached scalar "+call.field());};},null,null,null,null,null,null,null);
            body.run(WHOLE);for(int whole:new int[]{WHOLE,32,33}){var row=p.pool().state(whole);assertEquals(0x3A,row.id());assertEquals(4,row.routine());assertEquals(c[2],row.longBits(MAPPINGS));assertEquals(480,row.word(RESULTS_TIMER_WORD));}
            assertEquals(204,p.pool().state(WHOLE).word(RESULTS_SCREEN_Y_WORD));assertEquals(288,p.pool().state(WHOLE).word(RESULTS_DEST_X));assertEquals(3,p.pool().state(33).unsignedByte(MAP_FRAME));
            assertEquals(c[0]==0x502?0:1,reads.stream().filter(x->x==CdResults.ReadField.PLAYER_X_WORD).count());nonwrites(before,p,Set.of(WHOLE,32,33));
        }
    }

    @Test void missingReadOwnerStopsAtTheFirstRealHeadReadAndDoesNotInventReadinessOrProgrammeState(){
        var p=placement();seed(p,2,0x9A57);var before=p.capture();var body=CdResults.fromAct2PastAssets(p,terrain,null,null,null,null,null,null,null,null);
        var stopped=assertThrows(CdResults.UnsupportedResults.class,()->body.run(WHOLE));assertEquals(CdResults.UnsupportedBoundary.READ_OWNER,stopped.boundary());nonwrites(before,p,Set.of());
    }

    private static CdAssetBytes bank(){byte[] b=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];for(int i=0;i<b.length;i++)b[i]=(byte)(i*29+0x67);return CdAssetBytes.copyOf(b);}
    private static CdObjectPlacementRuntime placement(){return CdObjectPlacementRuntime.fromAssets(terrain,bank());}
    private static void seed(CdObjectPlacementRuntime p,int routine,int timer){p.pool().write(p.pool().state(WHOLE).moveByte(ID,0x3A).moveByte(ROUTINE,routine).moveWord(RESULTS_TIMER_WORD,timer));}
    private static void nonwrites(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime p,Set<Integer> changed){
        var after=p.capture();assertEquals(before.status(),after.status());assertArrayEquals(before.savedFlags().copy(),after.savedFlags().copy());
        for(int i=32;i<128;i++)if(!changed.contains(i))assertEquals(before.pool().slots().get(i-32),after.pool().slots().get(i-32));
    }
    private static final class Fault extends RuntimeException{private static final long serialVersionUID=1L;}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
