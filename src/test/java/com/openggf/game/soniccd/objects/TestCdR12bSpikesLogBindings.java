package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original-USA retained sources with explicit component ingress; genuine helpers, not World/route/pixel proof. Java UNRUN.
 * Native N1ecc5f3a: common Spikes/Main.asm:19-83; Palmtree Panic/Objects/Log Shadow/Main.asm:7-70.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdR12bSpikesLogBindings {
    static final int SLOT=40,SPIKES_MAP=0x20BFA6,LOG_MAP=0x2087A4;
    static CdR12bTerrainAssets terrain,equalTerrain;static CdR12bPlayerAssets playerAssets;static CdR12aTerrainAssets present;static CdR11bAssets otherPast;
    @BeforeAll static void originalUsa() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            terrain=CdR12bTerrainAssets.fromPrograms(programs);equalTerrain=CdR12bTerrainAssets.fromPrograms(programs);playerAssets=CdR12bPlayerAssets.fromPrograms(programs);
            present=CdR12aTerrainAssets.fromPrograms(programs);otherPast=CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));
        }
    }
    @Test void additiveFactoriesKeepTheLegacyDtosAndConstructorPublicationFree() throws Exception {
        assertNotNull(CdSpikes.class.getMethod("fromAct2PastAssetsWithPrefix",CdObjectPlacementRuntime.class,CdR12bTerrainAssets.class,CdSolidObject.class,CdSpikes.PrefixSolidCommit.class,CdSpikes.YRewindCommit.class,CdSpikes.HurtCommit.class,CdSpikes.DrawObjectCall.class));
        assertNotNull(CdLogShadow.class.getMethod("fromAct2PastAssets",CdObjectPlacementRuntime.class,CdR12bTerrainAssets.class,CdLogShadow.DispatchObserver.class,CdLogShadow.SourceDraw.class));
        assertEquals(1,CdSpikes.class.getConstructors().length);assertEquals(7,CdSpikes.class.getConstructors()[0].getParameterCount());assertEquals(3,CdSpikes.BodyAssets.class.getRecordComponents().length);
        assertEquals(1,CdLogShadow.class.getConstructors().length);assertEquals(3,CdLogShadow.class.getConstructors()[0].getParameterCount());assertEquals(3,CdLogShadow.Assets.class.getRecordComponents().length);
        var p=CdObjectPlacementRuntime.fromAssets(terrain,bank());var before=p.capture();
        assertNotNull(CdSpikes.fromAct2PastAssetsWithPrefix(p,terrain,actualSolid(),c->fail("constructor Solid"),c->fail("constructor Y"),c->fail("constructor Hurt"),(pool,id)->fail("constructor Draw")));
        var observer=new CdLogShadow.DispatchObserver(){public void beforePublication(CdLogShadow.Publication c){fail();}public void afterPublication(CdLogShadow.Publication c){fail();}public void beforeCall(Object c){fail();}public void afterCall(Object c){fail();}};
        assertNotNull(CdLogShadow.fromAct2PastAssets(p,terrain,observer,c->fail("constructor Draw")));nonwrites(before,p,Set.of());
    }
    @Test void actualMetadataUsesBRootsRaw72AndCachedUnscriptedVisuals() throws Exception {
        var o=terrain.objects();var spikes=o.spikes();var log=CdLogShadow.Assets.fromAct2PastAssets(o);
        assertSame(spikes,o.spikes());assertEquals(1,spikes.mappingFrames().size());assertTrue(spikes.animations().isEmpty());for(var f:spikes.mappingFrames())for(var q:f.pieces())assertTrue(q.tileOffset()+q.tileCount()<=8);
        assertSame(log.visual(),o.logShadow());assertSame(log.baseTileWords(),o.view(CdR12bObjectAssets.View.LOG_SHADOW_BASE_TILES));assertEquals(2,log.visual().mappingFrames().size());assertTrue(log.visual().animations().isEmpty());for(var f:log.visual().mappingFrames())for(var q:f.pieces())assertTrue(q.tileOffset()+q.tileCount()<=16);
        assertEquals(46,CdR12bObjectAssets.View.MAP_SPR_SPIKES.ordinal());assertEquals(47,CdR12bObjectAssets.View.MAP_SPR_LOG_SHADOW.ordinal());assertEquals(48,CdR12bObjectAssets.View.LOG_SHADOW_BASE_TILES.ordinal());
        var map=o.view(CdR12bObjectAssets.View.MAP_SPR_SPIKES);assertEquals(24,map.length());assertEquals("db6b4414c086240e34231326d9832e5785550bdd914ef9d933cea9079bc78607",sha(map));assertEquals(SPIKES_MAP,CdR12bObjectAssets.View.MAP_SPR_SPIKES.range().nativeAddress());
        map=o.view(CdR12bObjectAssets.View.MAP_SPR_LOG_SHADOW);assertEquals(16,map.length());assertEquals("bb3b5b1a67fd3665a520a4a6c1d7ff89212992a766df61ec42b6c41aa1d38f7b",sha(map));assertEquals(LOG_MAP,log.mappingsAddressBits());
        assertEquals(16,log.baseTileWords().length());assertEquals("b9a30c86ce81139722854af604422196b077c78a0e79dcac3cca3b506c06d637",sha(log.baseTileWords()));assertArrayEquals(HexFormat.of().parseHex("03cb045e041803f00428038f0428037f"),log.baseTileWords().copy());
        var tiles=o.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS);assertEquals(72,tiles.length());assertEquals(0x30,tiles.unsignedWord(0x14));assertEquals(0x0330,CdSpikes.readPastTileWord(tiles,0));
    }
    @Test void exactRetainedPlacementAdmissionPrecedesMetadataHelpersAndCallbacks() throws Exception {
        var p=CdObjectPlacementRuntime.fromAssets(terrain,bank());var before=p.capture();assertEquals(terrain.identity(),equalTerrain.identity());assertNotSame(terrain.placements(),equalTerrain.placements());assertNull(field(equalTerrain,"objects"));
        for(var foreign:List.of(new CdObjectPlacementRuntime(terrain.placements()),CdObjectPlacementRuntime.fromAssets(present,bank()),CdObjectPlacementRuntime.fromAssets(otherPast,bank()))){
            assertThrows(IllegalArgumentException.class,()->CdSpikes.fromAct2PastAssetsWithPrefix(foreign,terrain,null,null,null,null,null));assertThrows(IllegalArgumentException.class,()->CdLogShadow.fromAct2PastAssets(foreign,terrain,null,null));}
        assertThrows(IllegalArgumentException.class,()->CdSpikes.fromAct2PastAssetsWithPrefix(p,equalTerrain,null,null,null,null,null));assertThrows(IllegalArgumentException.class,()->CdLogShadow.fromAct2PastAssets(p,equalTerrain,null,null));assertNull(field(equalTerrain,"objects"));
        assertThrows(NullPointerException.class,()->CdSpikes.fromAct2PastAssetsWithPrefix(p,null,null,null,null,null,null));assertThrows(NullPointerException.class,()->CdLogShadow.fromAct2PastAssets(null,terrain,null,null));nonwrites(before,p,Set.of());
    }
    @Test void publicSpikes74FormatAndOldDecoderCannotAcceptActual72ByPadding() {
        var o=terrain.objects();var tiles=o.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS);
        assertThrows(IllegalArgumentException.class,()->new CdSpikes.BodyAssets(tiles,o.spikes(),SPIKES_MAP));assertThrows(IllegalArgumentException.class,()->CdR11aObjectAssets.decodeTileWord(tiles.copy(),10,0));assertEquals(74,CdSpikes.BodyAssets.fromAct2Assets(present.objects()).objectTileIds().length());
    }
    @Test void spikesActualInitPreservesAll64NonwritesAndVflip83BeforeUnconditionalDraw() {
        for(int flip:new int[]{0,1,2,3}){
            var h=new Spikes(actualSolid(),2,0,0,0);h.edit(s->s.moveByte(SPRITE_FLAGS,flip));var before=h.placement.capture();byte[] expected=spikesInit(h.slot(),true);expected[1]&=0x7F;
            var actor=h.frame.captureActor();var shared=h.frame.captureShared();var clock=h.frame.vIntClock();var result=h.run();assertArrayEquals(expected,result.state().copyBytes());assertEquals(0x0330,result.state().word(TILE));assertEquals(SPIKES_MAP,result.state().longBits(MAPPINGS));assertEquals(List.of("draw"),h.order);assertTrue(h.calls.isEmpty());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertEquals(clock,h.frame.vIntClock());nonwrites(before,h.placement,Set.of(SLOT));
        }
    }
    @Test void spikesSecondaryFailureRetainsOnlyTheFourStoreInitPrefix() {
        for(int secondary:new int[]{1,0x80,0xFF}){
            var h=new Spikes();h.edit(s->s.moveByte(SUBTYPE2,secondary));var before=h.placement.capture();byte[] expected=spikesInit(h.slot(),false);var actor=h.frame.captureActor();
            var error=assertThrows(CdSpikes.UnsupportedSpikes.class,()->h.body.run(SLOT,h.globals,null));assertEquals(CdSpikes.UnsupportedBoundary.TILE_ID_SUBTYPE2,error.boundary());assertArrayEquals(expected,h.slot().copyBytes());assertTrue(h.order.isEmpty());assertTrue(h.calls.isEmpty());assertEquals(actor,h.frame.captureActor());nonwrites(before,h.placement,Set.of(SLOT));
        }
    }
    @Test void spikesActualSharedDebugResultIsPublishedThroughTheExistingFramePortOnce() {
        var h=new Spikes();h.main();h.edit(s->s.moveByte(SOLID_TYPE,0xA5));var before=h.placement.capture();var actor=h.frame.captureActor();var shared=h.frame.captureShared();var result=h.run();
        var done=assertInstanceOf(CdSolidObject.Completed.class,h.calls.getFirst().outcome()).completedResult();assertEquals(CdSolidObject.Entry.SOLID_OBJECT,done.entry());assertEquals(Optional.of(CdSolidObject.GetOffReason.DEBUG_MODE),done.getOffReason());assertEquals(OptionalInt.of(0),done.d0());assertEquals(0,result.state().unsignedByte(SOLID_TYPE));assertEquals(done.player(),h.player());assertEquals(done.globals(),h.solidGlobals());assertEquals(List.of("solid","draw"),h.order);assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertTrue(h.sounds.isEmpty());nonwrites(before,h.placement,Set.of(SLOT));
    }
    @Test void spikesGenuineStoppedSolidPrefixPublishesBeforeItsIdenticalFailure() {
        var h=new Spikes(new CollisionFixture().solid(),8,1,0,0x1E5);h.main();h.edit(s->s.moveByte(SOLID_TYPE,2));var before=h.placement.capture();var clock=h.frame.vIntClock();
        var error=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::run);var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,h.calls.getFirst().outcome());assertSame(error,stopped.stop().originalException());assertEquals(CdSolidObject.StopSite.STAND_RELEASE,stopped.stop().site());assertEquals(stopped.prefix().object(),h.slot());assertEquals(stopped.prefix().player(),h.player());assertEquals(stopped.prefix().globals(),h.solidGlobals());assertEquals(List.of("solid"),h.order);assertEquals(clock,h.frame.vIntClock());nonwrites(before,h.placement,Set.of(SLOT));
    }
    @Test void spikesRealLandingThreadsFrameThenYLongThenDirectHurtUsingAFreshPoolCut() {
        var h=new Spikes(new CollisionFixture().solid(),8,SLOT,0,0x1E5);h.main();h.edit(s->s.moveByte(FLAGS,8));var before=h.placement.capture();var clock=h.frame.vIntClock();var result=h.run();
        var done=assertInstanceOf(CdSolidObject.Completed.class,h.calls.getFirst().outcome()).completedResult();assertEquals(OptionalInt.of(1),done.d0());assertEquals(CdSolidObject.Path.STAND_ON,done.path());assertEquals(List.of("solid","y","hurt","draw"),h.order);assertEquals(1,h.rewinds.size());assertEquals(1,h.hurts.size());
        var rewind=h.rewinds.getFirst();var hurt=h.hurts.getFirst();assertEquals(done.player().actor(),rewind.expectedActor());assertEquals(done.player().actor().motion().centreY16_16()-(((short)done.player().actor().motion().yVelocity())<<8),rewind.finalYBits());assertEquals(rewind.finalYBits(),hurt.expectedActor().motion().centreY16_16());assertNotSame(h.calls.getFirst().actualCut(),hurt.callCut());assertEquals(hurt.sourceA2(),hurt.callCut().slots().get(SLOT-32));assertEquals(result.player(),h.player());assertEquals(4,h.frame.state().motion().routine());assertEquals(0x78,h.frame.state().timers().hurt());assertEquals(0,result.playerGlobals().shield());assertEquals(clock,h.frame.vIntClock());nonwrites(before,h.placement,Set.of(SLOT));
    }
    @Test void spikesCallbackFaultNonpublicationAndReentryKeepTheirNativeCuts() {
        var missing=new Spikes();missing.main();missing.edit(s->s.moveByte(SOLID_TYPE,0xA5));missing.publish=false;assertThrows(IllegalStateException.class,missing::run);assertEquals(List.of("solid"),missing.order);
        var faulted=new Spikes();faulted.main();var before=faulted.placement.capture();var fault=new Fault();faulted.after=c->{throw fault;};assertSame(fault,assertThrows(Fault.class,faulted::run));assertEquals(List.of("solid"),faulted.order);var done=assertInstanceOf(CdSolidObject.Completed.class,faulted.calls.getFirst().outcome()).completedResult();assertEquals(done.player(),faulted.player());nonwrites(before,faulted.placement,Set.of(SLOT));
        var guarded=new Spikes();guarded.main();guarded.after=c->assertThrows(IllegalStateException.class,guarded::run);guarded.drawHook=()->assertThrows(IllegalStateException.class,guarded::run);guarded.run();assertEquals(List.of("solid","draw"),guarded.order);
    }
    @Test void logActualInitPublishesNativeSevenStoresThenTileThenOrAndOneDraw() {
        var h=new Log();var before=h.placement.capture();byte[] expected=logInit(h.slot());w(expected,2,0x445E);expected[1]&=0x7F;var result=h.run();assertArrayEquals(expected,result.state().copyBytes());assertEquals(3,h.publications.size());assertEquals(List.of("store1","store2","store3","draw"),h.order);assertEquals(0x045E,h.publications.get(1).output().word(TILE));assertEquals(0x445E,h.publications.get(2).output().word(TILE));assertEquals(LOG_MAP,result.state().longBits(MAPPINGS));assertEquals(1,result.state().unsignedByte(MAP_FRAME));nonwrites(before,h.placement,Set.of(SLOT));
    }
    @Test void logRawPeriodMaskFutureBranchAndActUseTheActualEightWords() {
        int[][] cases={{0,0,0,0x43CB},{0,255,1,0x445E},{1,255,0,0x4418},{1,0,1,0x43F0},{2,0,0,0x4428},{2,0,1,0x438F},{2,1,0,0x4428},{2,1,1,0x437F},{2,254,0,0x43CB},{0x80,255,1,0x445E}};
        for(int[] c:cases){var h=new Log();h.edit(s->s.moveByte(SAVED_FLAGS_ID,0));h.globals=new CdLogShadow.Globals(c[0],c[1],c[2],0,0x1080);h.run();assertEquals(c[3],h.draws.getFirst().word(TILE));assertEquals((c[0]&0x80)!=0,h.slot().id()==0);}
    }
    @Test void logReachedBaseFaultAndInvalidRoutineKeepExactNativeNonwrites() {
        var h=new Log();h.globals=new CdLogShadow.Globals(0,0,8,0,0x1080);var before=h.placement.capture();byte[] expected=logInit(h.slot());var error=assertThrows(CdLogShadow.UnsupportedLogShadow.class,h::run);assertEquals(CdLogShadow.UnsupportedBoundary.BASE_TILE_ADDRESS,error.boundary());assertEquals(16,error.value());assertArrayEquals(expected,h.slot().copyBytes());assertEquals(List.of("store1"),h.order);nonwrites(before,h.placement,Set.of(SLOT));
        var invalid=new Log();invalid.edit(s->s.moveByte(ROUTINE,3));before=invalid.placement.capture();assertEquals(CdLogShadow.UnsupportedBoundary.ROUTINE_DISPATCH,assertThrows(CdLogShadow.UnsupportedLogShadow.class,invalid::run).boundary());nonwrites(before,invalid.placement,Set.of());assertTrue(invalid.order.isEmpty());
    }
    @Test void logDrawReloadUsesActualChangedXAndSavedIdBeforeBankClearAndFullDelete() {
        var h=new Log();h.edit(s->s.moveByte(ROUTINE,2).moveByte(SAVED_FLAGS_ID,5));h.placement.bsetSavedFlag(9,0,CdObjectPlacementRuntime.SavedBit.LOADED);var before=h.placement.capture();h.drawHook=()->h.edit(s->s.moveWord(X,0x4000).moveByte(SAVED_FLAGS_ID,9));var result=h.run();assertTrue(result.deleted());assertArrayEquals(new byte[64],h.slot().copyBytes());assertEquals(List.of("draw","store1","store2"),h.order);assertEquals(29,h.publications.getFirst().savedIndex());assertEquals(0,h.placement.savedFlag(9,0)&0x80);assertEquals(before.savedFlags().unsignedByte(17),h.placement.savedFlag(5,0));
        byte[] bank=before.savedFlags().copy();bank[29]&=0x7F;assertArrayEquals(bank,h.placement.capture().savedFlags().copy());for(var s:before.pool().slots())if(s.identity().wholeIndex()!=SLOT)assertEquals(s,h.pool().state(s.identity().wholeIndex()));assertEquals(before.status(),h.placement.status());
    }
    @Test void logOriginalOperationReceiptsGuardCopiesRetireAndPreserveCallbackFailures() {
        var h=new Log();h.beforeCall=()->assertThrows(IllegalStateException.class,h::run);h.run();assertFalse(h.body.isCurrentCall(h.drawCalls.getFirst()));assertFalse(h.body.isCurrentCall(h.publications.getFirst()));
        var failed=new Log();var before=failed.placement.capture();var fault=new Fault();failed.afterStore=c->{if(c.ordinal()==2)throw fault;};assertSame(fault,assertThrows(Fault.class,failed::run));assertEquals(List.of("store1","store2"),failed.order);byte[] expected=logInit(before.pool().slots().get(SLOT-32));w(expected,2,0x045E);assertArrayEquals(expected,failed.slot().copyBytes());assertFalse(failed.body.isCurrentCall(failed.publications.getLast()));nonwrites(before,failed.placement,Set.of(SLOT));
    }

    /** Constructed component owner ports; they confer no private World/Frame-call authority. */
    static final class Spikes {
        final CdObjectPlacementRuntime placement=CdObjectPlacementRuntime.fromAssets(terrain,bank());final CdPlayerFrameRuntime frame;final CdSpikes body;
        final List<CdSpikes.PrefixSolidCall> calls=new ArrayList<>();final List<CdSpikes.YRewindCall> rewinds=new ArrayList<>();final List<CdSpikes.HurtCall> hurts=new ArrayList<>();final List<String> order=new ArrayList<>();final List<CdPlayerTouchResponse.FmSound> sounds=new ArrayList<>();
        CdSpikes.Globals globals=new CdSpikes.Globals(0,0,0x1080);int shield=1,combineRing=7;boolean publish=true;Consumer<CdSpikes.PrefixSolidCall> after=c->{};Runnable drawHook=()->{};CdPlayerTouchResponse.WorldGlobals world=new CdPlayerTouchResponse.WorldGlobals(7,3,0,0,5000,0);
        Spikes(){this(actualSolid(),2,0,0x100,0x1E5);}
        Spikes(CdSolidObject solid,int flags,int stand,int debug,int y){
            dirtyPool(placement);edit(s->s.moveByte(ID,0x26).moveByte(ROUTINE,0).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,7).moveByte(SPRITE_FLAGS,0x61).moveByte(FLAGS,0).moveWord(X,0x1000).moveWord(Y,0x200));
            frame=frame(flags,stand,debug,y);body=CdSpikes.fromAct2PastAssetsWithPrefix(placement,terrain,solid,this::commit,this::rewind,this::hurt,this::draw);
        }
        CdObjectPool pool(){return placement.pool();}CdObjectState slot(){return pool().state(SLOT);}void edit(UnaryOperator<CdObjectState> op){pool().write(op.apply(slot()));}
        void main(){edit(s->s.moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(SOLID_TYPE,0).moveByte(WIDTH,16).moveByte(Y_RADIUS,8).moveWord(TILE,0x330).moveLong(MAPPINGS,SPIKES_MAP).moveByte(PRIORITY,3));}
        CdSolidObject.Player player(){return playerOf(frame);}CdSolidObject.Globals solidGlobals(){return globalsOf(frame);}
        CdPlayerTouchResponse.PlayerGlobals contact(){var e=frame.environment();return new CdPlayerTouchResponse.PlayerGlobals(0,e.globals().invincible(),shield,combineRing,e.globals().debugCheat(),e.globals().debugMode(),e.movement().globals().scoreChain(),e.movement().gates().miniSonic());}
        CdSpikes.Result run(){var e=frame.environment();return body.run(SLOT,globals,new CdSpikes.Inputs(player(),new CdSolidObject.Gates(e.globals().debugMode(),e.movement().gates().miniSonic()),solidGlobals(),contact(),world));}
        void commit(CdSpikes.PrefixSolidCall c){assertEquals(c.actualCut().slots(),pool().capture().slots());assertEquals(player(),c.expectedPlayer());assertEquals(solidGlobals(),c.expectedGlobals());calls.add(c);order.add("solid");if(!publish)return;
            CdObjectState object;List<CdObjectState> others;List<CdPlayerTouchResponse.FmSound> fm;CdPlayerFrameRuntime.ObjectSolidPublication ready;
            if(c.outcome() instanceof CdSolidObject.Completed d){var r=d.completedResult();object=r.object();others=r.otherSlots();fm=r.fmRequests();ready=frame.prepareObjectSolidResult(c.expectedPlayer(),c.expectedGlobals(),r);}else{var r=((CdSolidObject.NativeStopped)c.outcome()).prefix();object=r.object();others=r.otherSlots();fm=r.fmRequests();ready=frame.prepareObjectSolidPrefix(c.expectedPlayer(),c.expectedGlobals(),r);}
            var images=new ArrayList<CdObjectState>();images.add(object);images.addAll(others);var overlay=CdObjectPool.overlay(c.actualCut(),images);pool().validateRestore(overlay);frame.validatePreparedObjectSolidPublication(ready);images.forEach(pool()::write);frame.applyPreparedObjectSolidPublication(ready);sounds.addAll(fm);after.accept(c);
        }
        void rewind(CdSpikes.YRewindCall c){assertEquals(player().actor(),c.expectedActor());assertEquals(slot().identity(),c.sourceIdentity());rewinds.add(c);order.add("y");frame.applyObjectPreHurtYRewind(c.expectedActor(),c.finalYBits());}
        void hurt(CdSpikes.HurtCall c){assertEquals(c.callCut().slots(),pool().capture().slots());assertEquals(player().actor(),c.expectedActor());assertEquals(contact(),c.expectedPlayer());assertEquals(world,c.expectedWorld());hurts.add(c);order.add("hurt");var ready=frame.prepareObjectHurtResult(c.expectedActor(),c.expectedPlayer(),c.result());var overlay=CdObjectPool.overlay(c.callCut(),c.result().changedSlots());pool().validateRestore(overlay);frame.validatePreparedObjectSolidPublication(ready);c.result().changedSlots().forEach(pool()::write);frame.applyPreparedObjectSolidPublication(ready);shield=c.result().player().shield();combineRing=c.result().player().combineRing();world=c.result().world();for(var call:c.result().calls())if(call instanceof CdPlayerTouchResponse.PlayFm fm)sounds.add(fm.sound());}
        void draw(CdObjectPool pool,CdObjectState.Identity id){assertSame(pool(),pool);assertEquals(slot().identity(),id);drawHook.run();order.add("draw");edit(s->s.moveByte(SPRITE_FLAGS,s.spriteFlags()&0x7F));}
    }
    static final class Log {
        final CdObjectPlacementRuntime placement=CdObjectPlacementRuntime.fromAssets(terrain,bank());final CdLogShadow body;final List<CdLogShadow.Publication> publications=new ArrayList<>();final List<CdLogShadow.DrawCall> drawCalls=new ArrayList<>();final List<CdObjectState> draws=new ArrayList<>();final List<String> order=new ArrayList<>();Runnable beforeCall=()->{},drawHook=()->{};Consumer<CdLogShadow.Publication> afterStore=c->{};CdLogShadow.Globals globals=new CdLogShadow.Globals(0,0xFF,1,0,0x1080);
        Log(){dirtyPool(placement);edit(s->s.moveByte(ID,0x33).moveByte(ROUTINE,0).moveByte(SAVED_FLAGS_ID,0).moveByte(SUBTYPE,1).moveByte(SPRITE_FLAGS,0xE3).moveWord(X,0x1000).moveWord(Y,0x200));body=CdLogShadow.fromAct2PastAssets(placement,terrain,new CdLogShadow.DispatchObserver(){
            public void beforePublication(CdLogShadow.Publication c){assertTrue(body.isCurrentCall(c));assertFalse(body.isCurrentCall(new CdLogShadow.Publication(c.ordinal(),c.cut(),c.source(),c.output(),c.savedIndex())));assertEquals(c.cut().pool().slots(),pool().capture().slots());assertEquals(slot(),c.source());publications.add(c);}
            public void afterPublication(CdLogShadow.Publication c){assertTrue(body.isCurrentCall(c));order.add("store"+c.ordinal());if(c.output()!=null)assertEquals(c.output(),slot());else assertArrayEquals(new byte[64],slot().copyBytes());afterStore.accept(c);}
            public void beforeCall(Object c){assertTrue(body.isCurrentCall(c));var d=assertInstanceOf(CdLogShadow.DrawCall.class,c);assertFalse(body.isCurrentCall(new CdLogShadow.DrawCall(d.cut(),d.source())));assertEquals(d.source(),slot());drawCalls.add(d);beforeCall.run();}
            public void afterCall(Object c){assertTrue(body.isCurrentCall(c));}
        },this::draw);}
        void draw(CdLogShadow.DrawCall c){assertTrue(body.isCurrentCall(c));draws.add(slot());order.add("draw");drawHook.run();edit(s->s.moveByte(SPRITE_FLAGS,s.spriteFlags()&0x7F));}
        CdObjectPool pool(){return placement.pool();}CdObjectState slot(){return pool().state(SLOT);}void edit(UnaryOperator<CdObjectState> op){pool().write(op.apply(slot()));}CdLogShadow.Result run(){return body.run(SLOT,globals);}
    }
    static CdPlayerFrameRuntime frame(int flags,int stand,int debug,int y){
        var motion=new CdPlayerMotionState(0x10008000,(y<<16)|0x1234,0x123,0x100,0x333,2,0x10,flags,4,9,19,0,0,1,0,0,0,0,0x56,0x78);
        var state=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(1,0x230F60,0x780,2,24),new CdPlayerBody.Timers(0,0,0,0x3456),new CdPlayerBody.History(bytes(256,0x35),0),0x12,new CdPlayerBody.ObjectLinks(0,stand),0x34);
        var b=terrain.loadPlan().bounds();var environment=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x600,0xC,0x80),new CdPlayerMovement.Bounds(b.left(),b.right(),b.bottom(),0),new CdPlayerMovement.Gates(1,0,0,0,0,0,0),new CdPlayerMovement.Globals(0,96,0x12,0x34,0x5678,4,0,0,0,0)),new CdPlayerBody.Controls(0,0,0,0,0),new CdPlayerBody.Globals(0x1234,160,0,debug,0,0,0,0),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);
        return CdPlayerFrameRuntime.fromAct2PastEntry(terrain,playerAssets,state,environment,new CdPlayerArtRuntime.State(new byte[768],new byte[768],0,false),new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),17));
    }
    static CdSolidObject.Player playerOf(CdPlayerFrameRuntime f){var s=f.state();var h=s.header();return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(),h.tile(),h.priority(),s.timers().hurt(),s.rotationDistance()),h.slotId(),s.objectLinks().pushObject(),s.objectLinks().standObject());}
    static CdSolidObject.Globals globalsOf(CdPlayerFrameRuntime f){var e=f.environment();var g=e.movement().globals();return new CdSolidObject.Globals(e.globals().invincible(),g.scoreChain(),g.primaryAngle(),g.secondaryAngle());}
    /** Explicit decoded-format collision control; genuine shared Solid computes its stop/Hurt prerequisites. No actual-B terrain parity. */
    static final class CollisionFixture {
        CdSolidObject solid(){byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256],heights=new byte[4096],widths=new byte[4096];fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;Arrays.fill(fg,2,fg.length,(byte)2);ids[1]=1;ids[2]=2;Arrays.fill(heights,16,32,(byte)16);Arrays.fill(widths,32,48,(byte)16);angles[2]=(byte)0xC0;var t=CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths);
            byte[] animations=new byte[578];for(int i=0;i<60;i++)w(animations,i*2,120);animations[122]=(byte)0xFF;byte[] maps=new byte[398],dplcs=new byte[398];for(int i=0;i<198;i++){w(maps,i*2,396);w(dplcs,i*2,396);}var visual=CdSonicVisualAssets.decode(maps,dplcs,new byte[69472]);byte[] atan=new byte[257];for(int i=0;i<=256;i++)atan[i]=(byte)(i/8);return new CdSolidObject(new CdPlayerMovement(t,CdR11aPlayerAssets.decode(animations,new byte[60],new byte[]{6,7,8,0x49,0x4C,(byte)0xFF},new byte[4],new byte[640],atan,visual)));}
    }
    private static byte[] spikesInit(CdObjectState old,boolean finish){byte[] b=old.copyBytes();b[0x24]+=2;l(b,4,SPIKES_MAP);b[1]|=4;b[0x18]=3;if(finish){w(b,2,0x330);b[0x19]=16;b[0x16]=8;if((b[1]&2)!=0){b[0x19]=18;b[0x20]=(byte)0x83;}}return b;}
    private static byte[] logInit(CdObjectState old){byte[] b=old.copyBytes();b[0x24]+=2;b[1]|=4;b[0x18]=6;l(b,4,LOG_MAP);b[0x1A]=b[0x28];b[0x19]=16;b[0x16]=16;return b;}
    private static CdSolidObject actualSolid(){return new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromAct2Past(terrain,playerAssets)));}
    private static void nonwrites(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime p,Set<Integer> changed){for(var s:before.pool().slots())if(!changed.contains(s.identity().wholeIndex()))assertEquals(s,p.pool().state(s.identity().wholeIndex()));assertEquals(before.status(),p.status());assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());}
    private static void dirtyPool(CdObjectPlacementRuntime p){for(int i=32;i<128;i++)p.pool().write(dirty(p.pool().state(i)).moveByte(ID,0));}
    private static CdAssetBytes bank(){byte[] b=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];for(int i=0;i<b.length;i++)b[i]=(byte)(i*37+0xA6);return CdAssetBytes.copyOf(b);}private static CdAssetBytes bytes(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
    private static void w(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}private static void l(byte[] b,int at,int v){w(b,at,v>>>16);w(b,at+2,v);}
    private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}private static String sha(CdAssetBytes b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.copy()));}
    private static CdObjectState dirty(CdObjectState s){try{byte[] b=new byte[64];for(int i=0;i<64;i++)b[i]=(byte)(s.identity().wholeIndex()*13+i*7);var c=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);c.setAccessible(true);return c.newInstance(field(s,"owner"),s.identity(),b);}catch(Exception e){throw new AssertionError(e);}}
    static final class Fault extends RuntimeException{private static final long serialVersionUID=1L;}
}
