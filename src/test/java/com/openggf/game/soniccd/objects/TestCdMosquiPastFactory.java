package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual B72/source views with explicit component ingress. N1ecc5f3a Mosqui/Main.asm:26-151,
 * Collision Check.asm:157-179 and Object Animate.asm:14-95. Fixed public-format terrain uses the
 * genuine floor helper; its callback stand-ins are not a Frame, private World invocation or FM driver.
 * No expected result invokes/replays the body/helper. No natural route/current-pixel/parity claim.
 */
class TestCdMosquiPastFactory {
    private static final int SLOT=40,X0=0x270,Y0=0x730,MAP=0x20B53E,SCRIPT=0x20B522,DMAP=0x20B57A,DSCRIPT=0x20B530;
    private static CdR11bAssets actual;

    @Test void namedFactoryKeepsTheExactLegacyA74SurfaceAndRefusesFabricatedLegacyAssets()throws Exception{
        var h=new H(new Terrain());assertEquals(1,CdMosqui.class.getConstructors().length);assertEquals(4,CdMosqui.class.getConstructors()[0].getParameterCount());
        assertEquals(7,CdMosqui.Assets.class.getRecordComponents().length);assertEquals(7,CdMosqui.Globals.class.getRecordComponents().length);assertEquals(2,CdMosqui.Result.class.getRecordComponents().length);
        assertEquals(3,CdMosqui.PrimaryAngleCall.class.getRecordComponents().length);assertEquals(3,CdMosqui.FmCall.class.getRecordComponents().length);
        assertNotNull(CdMosqui.class.getMethod("fromPastAssets",CdObjectPlacementRuntime.class,CdR11bAssets.class,CdPlayerTerrain.class,
                CdMosqui.PrimaryAngleCommit.class,CdMosqui.PlayFmSound.class,CdMosqui.DrawObjectCall.class));
        var before=h.runtime.capture();assertThrows(IllegalStateException.class,h.body::assets);same(before,h.runtime);
        var b=retail();var normal=CdObjectVisualAssets.decode(b.view(CdR11bAssets.Asset.MAP_SPR_MOSQUI_NORMAL).bytes().copy(),5,
                b.view(CdR11bAssets.Asset.ANI_MOSQUI_NORMAL).bytes().copy(),2,70);
        assertThrows(IllegalArgumentException.class,()->new CdMosqui.Assets(b.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes(),normal,normal,1,2,3,4));
        assertThrows(NullPointerException.class,()->CdMosqui.fromPastAssets(h.runtime,null,h.terrain.probes(),c->{},c->{},(p,i)->{}));
    }
    @Test void dirtyNormalAndDamagedInitMainPreserveAll64BytesAndFractionsAcrossFourFlips()throws Exception{
        for(int subtype:new int[]{0,255})for(int flip=0;flip<4;flip++){
            var h=new H(new Terrain());h.put(dirty(h.slot()).moveByte(ID,0x3D).moveByte(ROUTINE,0).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0)
                    .moveByte(SPRITE_FLAGS,0xA0|flip).moveByte(FLAGS,0x50|flip).moveWord(X,X0).moveWord(Y,Y0)
                    .moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678).moveByte(SAVED_FLAGS_ID,0)
                    .moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_TIME,1));
            var before=h.runtime.capture();byte[] expected=initExpected(h.slot(),subtype!=0);int x=h.slot().longBits(X_POSITION)+(subtype==0?-0x10000:-0x8000);
            putLong(expected,8,x);expected[0x1E]=0;var result=h.run();assertArrayEquals(expected,h.draws.getFirst().copyBytes());expected[1]&=0x7F;
            assertArrayEquals(expected,result.state().copyBytes());otherImages(before,h.runtime,SLOT);assertTrue(h.angles.isEmpty());assertTrue(h.fm.isEmpty());
        }
    }
    @Test void rawBSecondaryWordsUseActualFinite72BytesAndRetainTheInitPrefixOnFirstMissingWord()throws Exception{
        var bytes=retail().view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes();
        for(int secondary=0;secondary<18;secondary++){
            var h=new H(new Terrain());h.put(h.slot().moveByte(ROUTINE,0).moveByte(SUBTYPE2,secondary));h.run();assertEquals(bytes.unsignedWord(36+2*secondary),h.slot().word(TILE));
        }
        for(int secondary:new int[]{18,19,128,255}){
            var h=new H(new Terrain());h.put(h.slot().moveByte(ROUTINE,0).moveByte(SUBTYPE2,secondary).moveWord(TILE,0xBEEF));var before=h.slot();
            var fault=assertThrows(CdMosqui.UnsupportedMosqui.class,h::run);assertEquals(CdMosqui.UnsupportedBoundary.TILE_ADDRESS,fault.boundary());
            byte[] e=before.copyBytes();e[0x24]=2;e[1]|=4;e[0x18]=3;e[0x17]=e[0x19]=e[0x16]=16;e[0x20]=0x2B;word(e,0x2A,X0);
            assertArrayEquals(e,h.slot().copyBytes());assertTrue(h.draws.isEmpty());assertTrue(h.angles.isEmpty());
        }
    }
    @Test void actualMainRangeAndFullDebugWordSelectDiveWithoutMovementOrAnimation()throws Exception{
        for(int[] delta:new int[][]{{-48,-48},{47,63},{48,0},{0,64},{-49,0},{0,-49}}){
            var h=new H(new Terrain());h.seed(2,0);var before=h.slot();boolean in=delta[0]>=-48&&delta[0]<48&&delta[1]>=-48&&delta[1]<64;
            h.run(new CdMosqui.Globals(0,0,0,0,0x100,(X0+delta[0])&65535,(Y0+delta[1])&65535));
            assertEquals(in?4:2,h.slot().routine());assertEquals(in?before.longBits(X_POSITION):before.longBits(X_POSITION)-0x10000,h.slot().longBits(X_POSITION));
            assertEquals(in?1:0,h.slot().unsignedByte(ANIMATION));assertTrue(h.angles.isEmpty());
        }
        var h=new H(new Terrain());h.seed(2,0);h.run(new CdMosqui.Globals(0,0,0,0x100,0x100,X0,Y0));assertEquals(2,h.slot().routine());assertEquals(X0-1,h.slot().centreX());
    }
    @Test void realNormalAndDamagedSecondScriptsUseLiteralSpeedFramesAndFcWithoutDiveReplay()throws Exception{
        for(boolean damaged:new boolean[]{false,true}){
            var h=new H(new Terrain());h.seed(4,damaged?255:0);h.put(h.slot().moveByte(ANIMATION,1).moveByte(PREVIOUS_ANIMATION,1).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0));
            int speed=damaged?14:6;h.run();assertEquals(2,h.slot().unsignedByte(MAP_FRAME));assertEquals(1,h.slot().unsignedByte(ANIMATION_FRAME));
            for(int frame:new int[]{3,4}){for(int row=0;row<=speed;row++)h.run();assertEquals(frame,h.slot().unsignedByte(MAP_FRAME));}
            for(int row=0;row<=speed;row++)h.run();assertEquals(6,h.slot().routine());assertEquals(4,h.slot().unsignedByte(MAP_FRAME));
            assertEquals(3,h.slot().unsignedByte(ANIMATION_FRAME));assertEquals(speed,h.slot().unsignedByte(ANIMATION_TIME));assertEquals(Y0,h.slot().centreY());
            assertTrue(h.angles.isEmpty());assertTrue(h.fm.isEmpty());
        }
    }
    @Test void heldTimerNeverReadsForeignLivePointerOrChangesHeldRawMapAndNextCallStopsAtRead()throws Exception{
        var h=new H(new Terrain());h.seed(4,0);h.put(h.slot().moveLong(MOSQUI_ANIMATION_SCRIPT,0xDEADBEEF).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,255)
                .moveByte(ANIMATION_TIME,1).moveByte(ANIMATION_FRAME,0xA5).moveByte(MAP_FRAME,0xFE));
        h.run();assertEquals(0,h.slot().unsignedByte(ANIMATION_TIME));assertEquals(0xFE,h.slot().unsignedByte(MAP_FRAME));assertEquals(0xA5,h.slot().unsignedByte(ANIMATION_FRAME));
        var before=h.slot();var failure=assertThrows(CdMosqui.UnsupportedMosqui.class,h::run);assertEquals(CdMosqui.UnsupportedBoundary.ANIMATION_SCRIPT_POINTER,failure.boundary());
        assertEquals(before.moveByte(ANIMATION_TIME,255),h.slot());assertEquals(1,h.draws.size());assertTrue(h.angles.isEmpty());
    }
    @Test void malformedDirectoryKeepsOriginalIdResetAndSubqPrefixBeforeTheSharedSelectedReadFault()throws Exception{
        var h=new H(new Terrain());h.seed(4,0);h.put(h.slot().moveByte(ANIMATION,2).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,0xA7).moveByte(ANIMATION_TIME,0x55));var before=h.slot();
        var fault=assertThrows(CdMosqui.UnsupportedMosqui.class,h::run);assertEquals(CdMosqui.UnsupportedBoundary.ANIMATION_DIRECTORY_READ,fault.boundary());assertNotNull(fault.getCause());
        assertEquals(before.moveByte(PREVIOUS_ANIMATION,2).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,255),h.slot());assertTrue(h.draws.isEmpty());
    }
    @Test void malformedFrameIndexKeepsSpeedStoreAndOriginalIndexWithoutClampingOrSuccessfulResult()throws Exception{
        var h=new H(new Terrain());h.seed(4,0);h.put(h.slot().moveByte(ANIMATION,1).moveByte(PREVIOUS_ANIMATION,1).moveByte(ANIMATION_FRAME,255).moveByte(ANIMATION_TIME,0));var before=h.slot();
        var fault=assertThrows(CdMosqui.UnsupportedMosqui.class,h::run);assertEquals(CdMosqui.UnsupportedBoundary.ANIMATION_FRAME_READ,fault.boundary());
        assertEquals(before.moveByte(ANIMATION_TIME,6),h.slot());assertTrue(h.draws.isEmpty());assertTrue(h.angles.isEmpty());
    }
    @Test void divePublishesYWordThenEveryRawAngleIncludingOddBeforeCorrectedRoutineAndA7()throws Exception{
        var terrain=new Terrain();terrain.angles[7]=0x23;var h=new H(terrain);h.seed(6,0);h.put(h.slot().moveWord(Y,0x772).moveByte(SPRITE_FLAGS,0x84));var before=h.slot();
        h.run();assertEquals(List.of(0,0x23),h.angles.stream().map(CdMosqui.PrimaryAngleCall::value).toList());assertEquals(List.of("angle:0","angle:35","fm","draw"),h.order);
        assertNotSame(h.angles.get(0).actualCut(),h.angles.get(1).actualCut());
        for(var call:h.angles){assertEquals(0x778,call.expectedSource().centreY());assertEquals(6,call.expectedSource().routine());assertEquals(0x5678,call.expectedSource().word(Y_FRACTION));}
        assertEquals(0x23,h.scratch);assertEquals(1,h.fm.size());var fm=h.fm.getFirst();assertEquals(0x777,fm.expectedSource().centreY());assertEquals(8,fm.expectedSource().routine());
        byte[] expected=before.copyBytes();word(expected,12,0x777);expected[0x24]=8;expected[1]&=0x7F;assertArrayEquals(expected,h.slot().copyBytes());
    }
    @Test void signedMinusEightThresholdAndPriorVisibilityGateTheOneActualA7Cut()throws Exception{
        for(int y:new int[]{0x770,0x771})for(boolean visible:new boolean[]{false,true}){
            var h=new H(new Terrain());h.seed(6,0);h.put(h.slot().moveWord(Y,y).moveByte(SPRITE_FLAGS,visible?0x84:4));h.run();
            assertEquals(y==0x770?6:8,h.slot().routine());assertEquals(y==0x770?0x776:0x777,h.slot().centreY());assertEquals(y==0x771&&visible?1:0,h.fm.size());
        }
    }
    @Test void actualConstructedTerrainReadStopRetainsYPlusSixAndCompletedPrezeroOnly()throws Exception{
        var t=new Terrain();t.fg[2+7*42+2]=(byte)0xF9;var h=new H(t);h.seed(6,0);h.put(h.slot().moveByte(SPRITE_FLAGS,0x44));var before=h.slot();
        assertThrows(IllegalStateException.class,h::run);assertEquals(before.addWord(Y,6),h.slot());assertEquals(List.of(0),h.angles.stream().map(CdMosqui.PrimaryAngleCall::value).toList());
        assertEquals(0,h.scratch);assertTrue(h.fm.isEmpty());assertTrue(h.draws.isEmpty());
    }
    @Test void callbackFullPoolMutationOnReturnIsRejectedWithoutRollingBackTheKnownAttack()throws Exception{
        var h=new H(new Terrain());h.seed(6,0);h.onAngle=c->h.pool().write(h.pool().state(75).moveByte(COLLISION_STATUS,0x55));
        assertThrows(IllegalStateException.class,h::run);assertEquals(0x55,h.pool().state(75).unsignedByte(COLLISION_STATUS));assertEquals(Y0+6,h.slot().centreY());assertTrue(h.draws.isEmpty());
    }
    @Test void readOnlyAngleCutRejectsSavedBankMutationAndRetiresScopeAfterTheFault()throws Exception{
        var h=new H(new Terrain());h.seed(6,0);h.onAngle=c->h.runtime.bsetSavedFlag(11,0,CdObjectPlacementRuntime.SavedBit.LOADED);
        assertThrows(IllegalStateException.class,h::run);assertEquals(0x80,h.runtime.savedFlag(11,0));assertEquals(Y0+6,h.slot().centreY());
        h.onAngle=c->{};h.run();assertEquals(Y0+12,h.slot().centreY());assertEquals(1,h.draws.size());
    }
    @Test void originalAngleFailureWinsAndFullPlacementVerificationFaultIsSuppressed()throws Exception{
        var h=new H(new Terrain());h.seed(6,0);var original=new IllegalStateException("angle owner fault");h.onAngle=c->{h.pool().write(h.pool().state(75).moveByte(COLLISION_STATUS,0x55));throw original;};
        assertSame(original,assertThrows(IllegalStateException.class,h::run));assertEquals(1,original.getSuppressed().length);assertEquals(Y0+6,h.slot().centreY());assertTrue(h.draws.isEmpty());assertTrue(h.fm.isEmpty());
    }
    @Test void originalFmFailureKeepsCorrectionAndAnglePrefixBeforeDrawAndSuppressesPoolFault()throws Exception{
        var t=new Terrain();t.angles[7]=0x23;var h=new H(t);h.seed(6,0);h.put(h.slot().moveWord(Y,0x772).moveByte(SPRITE_FLAGS,0x84));
        var original=new IllegalArgumentException("FM owner fault");h.onFm=c->{h.pool().write(h.pool().state(76).moveByte(COLLISION_STATUS,0x33));throw original;};
        assertSame(original,assertThrows(IllegalArgumentException.class,h::run));assertEquals(1,original.getSuppressed().length);assertEquals(0x777,h.slot().centreY());assertEquals(8,h.slot().routine());assertEquals(0x23,h.scratch);assertTrue(h.draws.isEmpty());
    }
    @Test void samePastInstanceRejectsAngleFmAndDrawReentryWithoutASecondDispatch()throws Exception{
        var h=new H(new Terrain());h.seed(6,0);h.put(h.slot().moveWord(Y,0x772).moveByte(SPRITE_FLAGS,0x84));
        h.onAngle=c->assertThrows(IllegalStateException.class,h::run);h.onFm=c->assertThrows(IllegalStateException.class,h::run);h.onDraw=()->assertThrows(IllegalStateException.class,h::run);
        h.run();assertEquals(2,h.angles.size());assertEquals(1,h.fm.size());assertEquals(1,h.draws.size());
    }
    @Test void noOpAngleStandInDemonstratesThatDataCutsCannotAuthenticateActualFrameAdoption()throws Exception{
        var h=new H(new Terrain());h.seed(6,0);h.ignoreAngles=true;var before=h.runtime.capture();var result=h.run();
        assertFalse(h.angles.isEmpty());assertEquals(0xA5,h.scratch,"Component lacks an actual Frame; Root must enforce adoption independently");
        assertTrue(result.effects().stream().anyMatch(CdMosqui.GlobalWrite.class::isInstance));otherImages(before,h.runtime,SLOT);
        var state=h.runtime.capture();result.effects().forEach(Object::toString);same(state,h.runtime);
        assertThrows(UnsupportedOperationException.class,()->result.effects().clear());
    }
    @Test void waitDeletesBeforeActualDrawAndThenReloadsClearedAnchorWithoutReusingSavedId()throws Exception{
        var h=new H(new Terrain());h.seed(8,0);h.put(h.slot().moveByte(SPRITE_FLAGS,4).moveByte(SAVED_FLAGS_ID,10));h.runtime.bsetSavedFlag(10,0,CdObjectPlacementRuntime.SavedBit.LOADED);
        var result=h.run();assertArrayEquals(new byte[64],h.draws.getFirst().copyBytes());assertArrayEquals(new byte[64],h.slot().copyBytes());assertEquals(0,h.runtime.savedFlag(10,0));
        var despawns=result.effects().stream().filter(CdMosqui.Despawn.class::isInstance).map(CdMosqui.Despawn.class::cast).toList();assertEquals(2,despawns.size());
        assertEquals(10,despawns.get(0).savedId());assertEquals(0,despawns.get(0).period());assertEquals(0,despawns.get(1).savedId());assertEquals(-1,despawns.get(1).period());
    }

    private static final class H{
        final Terrain terrain;final CdObjectPlacementRuntime runtime;final CdMosqui body;
        final List<CdMosqui.PrimaryAngleCall> angles=new ArrayList<>();final List<CdMosqui.FmCall> fm=new ArrayList<>();final List<CdObjectState> draws=new ArrayList<>();final List<String> order=new ArrayList<>();
        Consumer<CdMosqui.PrimaryAngleCall> onAngle=c->{};Consumer<CdMosqui.FmCall> onFm=c->{};Runnable onDraw=()->{};int scratch=0xA5;boolean ignoreAngles;
        H(Terrain terrain)throws Exception{this.terrain=terrain;runtime=CdObjectPlacementRuntime.fromAssets(retail(),CdAssetBytes.copyOf(new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES]));
            body=CdMosqui.fromPastAssets(runtime,retail(),terrain.probes(),c->{assertCut(c.actualCut(),c.expectedSource());angles.add(c);order.add("angle:"+c.value());if(!ignoreAngles)scratch=c.value();onAngle.accept(c);},
                    c->{assertCut(c.actualCut(),c.expectedSource());assertEquals(CdMosqui.Sound.FM_A7,c.sound());fm.add(c);order.add("fm");onFm.accept(c);},
                    (pool,id)->{var state=pool.state(id.wholeIndex());draws.add(state);order.add("draw");onDraw.run();pool.write(state.moveByte(SPRITE_FLAGS,state.spriteFlags()&0x7F));});seed(0,0);}
        CdObjectPool pool(){return runtime.pool();}CdObjectState slot(){return pool().state(SLOT);}void put(CdObjectState state){pool().write(state);}
        void seed(int routine,int subtype){put(pool().state(SLOT).moveByte(ID,0x3D).moveByte(ROUTINE,routine).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0).moveByte(SPRITE_FLAGS,4)
                .moveByte(PRIORITY,3).moveWord(TILE,0x2396).moveWord(X,X0).moveWord(Y,Y0).moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678)
                .moveByte(X_RADIUS,16).moveByte(WIDTH,16).moveByte(Y_RADIUS,16).moveByte(COLLISION_TYPE,0x2B).moveWord(MOSQUI_ANCHOR_X,X0)
                .moveLong(MAPPINGS,subtype==0?MAP:DMAP).moveLong(MOSQUI_ANIMATION_SCRIPT,subtype==0?SCRIPT:DSCRIPT)
                .moveLong(MOSQUI_X_DELTA,subtype==0?-0x10000:-0x8000).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_TIME,1));}
        CdMosqui.Result run(){return run(new CdMosqui.Globals(0,0,0,0,0x100,0,0));}CdMosqui.Result run(CdMosqui.Globals globals){return body.run(SLOT,globals);}
        void assertCut(CdObjectPool.Snapshot cut,CdObjectState source){pool().validateRestore(cut);assertEquals(pool().capture().slots(),cut.slots());assertEquals(slot(),source);assertEquals(source,cut.slots().get(SLOT-32));}
    }
    /** Same fixed public-format floor as legacy Mosqui tests; production queries and angle writes remain genuine. */
    private static final class Terrain{
        final byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256],heights=new byte[4096],widths=new byte[4096];
        Terrain(){fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;fg[2+7*42+2]=2;for(int c=0;c<16;c++){int at=512+(8*16+c)*2;chunks[at]=0x20;chunks[at+1]=1;}ids[1]=7;angles[7]=0x22;Arrays.fill(heights,112,128,(byte)16);}
        CdPlayerTerrain probes(){return new CdPlayerTerrain(CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths));}
    }
    private static CdObjectState dirty(CdObjectState s){for(var f:CdObjectState.ByteField.values())s=s.moveByte(f,0x53+7*f.byteOffset());for(var f:CdObjectState.WordField.values())s=s.moveWord(f,0xA153+0x103*f.byteOffset());for(var f:CdObjectState.LongField.values())s=s.moveLong(f,0xA1536729+0x01030507*f.byteOffset());return s;}
    private static byte[] initExpected(CdObjectState s,boolean damaged){byte[] e=s.copyBytes();e[0x24]=2;e[1]|=4;e[0x18]=3;e[0x17]=e[0x19]=e[0x16]=16;e[0x20]=0x2B;word(e,0x2A,s.centreX());word(e,2,0x2396);putLong(e,4,damaged?DMAP:MAP);putLong(e,0x30,damaged?DSCRIPT:SCRIPT);putLong(e,0x2C,damaged?-0x8000:-0x10000);return e;}
    private static void word(byte[] b,int at,int n){b[at]=(byte)(n>>>8);b[at+1]=(byte)n;}private static void putLong(byte[] b,int at,int n){word(b,at,n>>>16);word(b,at+2,n);}
    private static void otherImages(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime runtime,int except){var actual=runtime.capture();assertEquals(before.status(),actual.status());assertArrayEquals(before.savedFlags().copy(),actual.savedFlags().copy());for(int i=0;i<96;i++)if(i!=except-32)assertEquals(before.pool().slots().get(i),actual.pool().slots().get(i));}
    private static void same(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime runtime){otherImages(before,runtime,-1);}
    private static CdR11bAssets retail()throws Exception{
        if(actual!=null)return actual;String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){actual=CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source));}return actual;
    }
}
