package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.CdPlayerMath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual original-USA sources and explicit constructed component ingress; no World/route proof.
 * N1ecc5f3 Pata-Bata/Main:7-80, Object Spawner:388-446. Java UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdR12bPataBataBinding {
    static CdR12bTerrainAssets terrain,equalTerrain;static CdR12bPlayerAssets player;
    static final int SLOT=45,NORMAL=0x20B2E4,DAMAGED=0x20B306;
    @BeforeAll static void media()throws Exception{
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){var cues=paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){terrain=CdR12bTerrainAssets.fromPrograms(source);equalTerrain=CdR12bTerrainAssets.fromPrograms(source);player=CdR12bPlayerAssets.fromPrograms(source);}
    }
    @Test void actualFactoryBorrowsCachedVisualsOneSharedScriptAndTheRawSeventyTwoByteTable(){
        var o=terrain.objects();var a=CdPataBata.Assets.fromAct2PastAssets(o,player);assertSame(o.pataBataNormal(),a.normal());assertSame(o.pataBataDamaged(),a.damaged());assertSame(o.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS),a.objectTileIds());assertEquals(72,a.objectTileIds().length());assertEquals(0x26,(short)a.objectTileIds().unsignedWord(2));assertEquals(0x23EA,a.objectTileIds().unsignedWord(0x26));assertEquals(NORMAL,a.normalMappingsAddress());assertEquals(DAMAGED,a.damagedMappingsAddress());
        assertSame(o.view(CdR12bObjectAssets.View.ANI_PATA_BATA),a.animations().bytes());assertEquals(2,a.animations().scriptCount());assertEquals(0,a.animations().tableOffset());assertEquals(3,a.normal().mappingFrames().size());assertEquals(3,a.damaged().mappingFrames().size());assertEquals(50,terrain.sectionSources().source(CdR12bSectionSources.SourceId.ART_PATA_BATA).tileCount());assertEquals(-12,a.math().calcSine(0xFE).sine());assertEquals(-6,a.math().calcSine(0xFF).sine());
    }
    @Test void retainedPlacementGateRejectsEqualForeignOwnersBeforeNullHelpers(){
        var p=placement();var before=p.capture();assertEquals(terrain.identity(),equalTerrain.identity());assertNotSame(terrain.placements(),equalTerrain.placements());assertThrows(IllegalArgumentException.class,()->CdPataBata.fromAct2PastAssets(p,equalTerrain,player,null,null));assertThrows(IllegalArgumentException.class,()->CdPataBata.fromAct2PastAssets(new CdObjectPlacementRuntime(terrain.placements()),terrain,player,null,null));
        assertThrows(NullPointerException.class,()->CdPataBata.fromAct2PastAssets(null,terrain,player,null,null));assertThrows(NullPointerException.class,()->CdPataBata.fromAct2PastAssets(p,null,player,null,null));assertThrows(NullPointerException.class,()->CdPataBata.Assets.fromAct2PastAssets(null,player));assertThrows(NullPointerException.class,()->CdPataBata.Assets.fromAct2PastAssets(terrain.objects(),null));nonwrites(before,p,Set.of());
    }
    @Test void bothFormsKeepLiteralInitMotionFullSixtyFourNonwritesAndOneDraw(){
        for(int subtype:new int[]{0,1,0x7F,0x80,0xFF}){
            var h=new H(subtype);var before=h.p.capture();var source=h.slot();var out=h.run();boolean damaged=subtype!=0;byte[] expected=source.copyBytes();expected[0x24]=2;expected[1]=(byte)0x84;expected[0x18]=3;expected[0x20]=0x2A;expected[0x17]=expected[0x19]=expected[0x16]=16;
            w(expected,2,0x23EA);l(expected,4,damaged?DAMAGED:NORMAL);l(expected,8,damaged?0x01000000:0x00FFC000);l(expected,12,damaged?0x07002FFF:0x06FF1FFF);w(expected,0x2A,0x100);l(expected,0x2C,0x07008000);l(expected,0x30,damaged?-0x4000:-0x8000);w(expected,0x34,damaged?0xFF00:0xFE00);w(expected,0x36,damaged?0xFF00:0xFE00);w(expected,0x38,damaged?4:3);expected[0x1C]=expected[0x1D]=(byte)(damaged?1:0);expected[0x1B]=1;expected[0x1E]=(byte)(damaged?3:7);
            assertArrayEquals(expected,out.state().copyBytes());assertEquals(1,h.draws);assertFalse(out.converted());assertFalse(out.deleted());assertSame(h.math,h.body.assets().math());assertEquals(4,out.effects().size());assertFalse(h.body.isCurrentDrawCall(h.p.pool(),h.original));nonwrites(before,h.p,Set.of(SLOT));
        }
    }
    @Test void conversionStopsBeforeInitAnimationDrawAndSavedClear(){
        for(int subtype:new int[]{0,1}){var h=new H(subtype);h.globals=new CdPataBata.Globals(1,subtype==0?0:1,0,0x100);h.edit(s->s.moveByte(SAVED_FLAGS_ID,7));h.p.bsetSavedFlag(7,0,CdObjectPlacementRuntime.SavedBit.LOADED);var before=h.p.capture();var result=h.run();byte[] expected=new byte[64];expected[0]=0x18;w(expected,8,0x100);w(expected,12,0x700);assertArrayEquals(expected,result.state().copyBytes());assertTrue(result.converted());assertEquals(0,h.draws);assertEquals(List.of(new CdPataBata.ExplosionConversion(0x100,0x700)),result.effects());nonwrites(before,h.p,Set.of(SLOT));}
        var h=new H(0);h.globals=new CdPataBata.Globals(1,1,0,0x100);assertFalse(h.run().converted());assertEquals(1,h.draws);
    }
    @Test void rawWordFaultKeepsOnlyReachedInitStoresAndDoesNotMaskSubtypeTwo(){
        var h=new H(0);h.edit(s->s.moveByte(SUBTYPE2,0xFF).moveWord(TILE,0xBEEF).moveLong(MAPPINGS,0x89ABCDEF));byte[] expected=h.slot().copyBytes();expected[0x24]=2;expected[1]|=4;expected[0x18]=3;expected[0x20]=0x2A;expected[0x17]=expected[0x19]=expected[0x16]=16;w(expected,0x2A,0x100);l(expected,0x2C,0x07008000);var failure=assertThrows(CdPataBata.UnsupportedPataBata.class,h::run);assertEquals(CdPataBata.UnsupportedBoundary.SET_OBJECT_TILE_ID,failure.boundary());assertArrayEquals(expected,h.slot().copyBytes());assertEquals(0,h.draws);assertFalse(h.body.isCurrentDrawCall(h.p.pool(),new CdObjectState.Identity(SLOT)));h.edit(s->s.moveByte(ROUTINE,0).moveByte(SUBTYPE2,1));assertEquals(0x2399,h.run().state().word(TILE));
    }
    @Test void drawRequiresOriginalReferenceFullPoolAndSynchronousScopeIncludingCaughtReentry(){
        var h=new H(0);h.draw=(pool,id)->{assertTrue(h.body.isCurrentDrawCall(pool,id));assertFalse(h.body.isCurrentDrawCall(pool,new CdObjectState.Identity(id.wholeIndex())));assertFalse(h.body.isCurrentDrawCall(placement().pool(),id));assertThrows(IllegalStateException.class,()->h.body.run(SLOT,h.globals));assertTrue(h.body.isCurrentDrawCall(pool,id));var old=pool.state(46);pool.write(old.moveByte(ID,0x12));assertFalse(h.body.isCurrentDrawCall(pool,id));pool.write(old);assertTrue(h.body.isCurrentDrawCall(pool,id));pool.write(pool.state(SLOT).moveByte(SPRITE_FLAGS,0x84));};h.run();assertFalse(h.body.isCurrentDrawCall(h.p.pool(),h.original));
    }
    @Test void drawFailureKeepsOriginalCauseAndNativePrefixAndRetiresReceipt(){
        var h=new H(0);var fault=new Fault();h.draw=(p,id)->{p.write(p.state(SLOT).moveByte(SPRITE_FLAGS,0));throw fault;};var failure=assertThrows(CdPataBata.UnsupportedPataBata.class,h::run);assertSame(fault,failure.getCause());assertEquals(CdPataBata.UnsupportedBoundary.DRAW_OBJECT,failure.boundary());assertEquals(2,h.slot().routine());assertEquals(0,h.slot().spriteFlags());assertEquals(2,failure.completedEffects().size());assertFalse(h.body.isCurrentDrawCall(h.p.pool(),h.original));h.draw=(p,id)->p.write(p.state(SLOT).moveByte(SPRITE_FLAGS,128));assertDoesNotThrow(h::run);
    }
    @Test void drawReloadUsesAnchorForDespawnAndClearsOnlyItsActualLoadedBit(){
        var h=new H(0);h.edit(s->s.moveByte(SAVED_FLAGS_ID,7));h.p.bsetSavedFlag(7,0,CdObjectPlacementRuntime.SavedBit.LOADED);var before=h.p.capture();h.draw=(p,id)->p.write(p.state(SLOT).moveByte(SPRITE_FLAGS,0).moveWord(PATA_BATA_ANCHOR_X,0x4000));var out=h.run();assertTrue(out.deleted());assertArrayEquals(new byte[64],out.state().copyBytes());byte[] bank=before.savedFlags().copy();bank[23]&=0x7F;assertArrayEquals(bank,h.p.capture().savedFlags().copy());assertEquals(new CdPataBata.Despawn(CdPataBata.DespawnEntry.ANCHOR_OFFSCREEN,7,0),out.effects().get(3));
    }
    static final class H{
        final CdObjectPlacementRuntime p=placement();final CdPlayerMath math=new CdPlayerMath(player.player());final CdPataBata body;CdPataBata.Globals globals=new CdPataBata.Globals(0,0,0,0x100);CdPataBata.DrawObjectCall draw=(pool,id)->pool.write(pool.state(SLOT).moveByte(SPRITE_FLAGS,pool.state(SLOT).spriteFlags()|128));int draws;CdObjectState.Identity original;
        H(int subtype){edit(s->s.moveByte(ID,0x3E).moveByte(SUBTYPE,subtype).moveLong(X_POSITION,0x01004000).moveLong(Y_POSITION,0x07001234));body=CdPataBata.fromAct2PastAssets(p,terrain,player,math,(pool,id)->{draws++;original=id;assertTrue(body().isCurrentDrawCall(pool,id));draw.drawObject(pool,id);});}
        CdPataBata body(){return body;}CdObjectState slot(){return p.pool().state(SLOT);}void edit(java.util.function.UnaryOperator<CdObjectState> f){p.pool().write(f.apply(slot()));}CdPataBata.Result run(){return body.run(SLOT,globals);}
    }
    static CdObjectPlacementRuntime placement(){return CdObjectPlacementRuntime.fromAssets(terrain,CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));}
    static void nonwrites(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime p,Set<Integer> changed){assertEquals(before.status(),p.status());assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());for(var s:before.pool().slots())if(!changed.contains(s.identity().wholeIndex()))assertEquals(s,p.pool().state(s.identity().wholeIndex()));}
    static void w(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}static void l(byte[] b,int at,int v){w(b,at,v>>>16);w(b,at+2,v);}static class Fault extends RuntimeException{private static final long serialVersionUID=1L;}
}
