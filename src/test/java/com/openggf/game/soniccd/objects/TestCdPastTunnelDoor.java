package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectTileIds;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Original B metadata with explicitly constructed component player/secondary inputs.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Spin Tunnel/Main (Door).asm:7-129,
 * Object Solid.asm:174-196,473-479; B Object Tile IDs:7-44. No actor comes from a trace.
 * A component-owned immutable31x64 value is the actual secondary source in this harness, not a second
 * CdReservedObjectBank. It is not the private Past Visit: Root owns actual World/Frame authority.
 * Genuine prefix stops use the existing shared algorithm on public constructed-format terrain.
 * All16 declarations were frozen before production; all Java execution remains UNRUN by author.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdPastTunnelDoor {
    private static final int WHOLE=40, MAP=0x21D6C4, X0=0x0100, Y0=0x0080;
    private static final byte[] ANIM=HexFormat.of().parseHex("00020000000101010101010101fc");
    private static final byte[] MAPS=HexFormat.of().parseHex(
            "0010000402d0030000e4f0010004e40002f80c0006e8f804000a0800");

    @Test void pastFactoryUsesActualTwoNamedViewsAndUnpaddedTile72() throws Exception {
        var a=retail(); var assets=CdTunnelDoor.Assets.fromPastAssets(a);
        assertEquals(CdReleaseVariant.USA,a.identity().variant());
        assertEquals(CdNativeFile.R11B,a.identity().file());
        assertEquals(CdStagePayload.R11B.stage(),a.identity().stage());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,a.identity().domain());
        assertEquals("b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265",a.identity().mmdSha256());
        assertEquals(0x1D6B6,a.view(ANI_TUNNEL_DOOR).range().fileOffset());
        assertEquals(0x1D6C4,a.view(MAP_SPR_TUNNEL_DOOR).range().fileOffset());
        assertArrayEquals(ANIM,a.view(ANI_TUNNEL_DOOR).bytes().copy());
        assertArrayEquals(MAPS,a.view(MAP_SPR_TUNNEL_DOOR).bytes().copy());
        assertArrayEquals(ANIM,assets.visual().animationTable().copy());
        assertArrayEquals(MAPS,assets.visual().mappings().copy());
        assertEquals(MAP,assets.mappingsAddress()); assertEquals(72,assets.objectTileIds().length());
        assertArrayEquals(a.view(OBJECT_TILE_IDS).bytes().copy(),assets.objectTileIds().copy());
        assertEquals(List.of(16,4),assets.visual().mappingFrames().stream()
                .map(com.openggf.game.soniccd.level.CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(0,0,1,1,1,1,1,1,1,1),assets.visual().animations().getFirst().frameBytes());
        int[][][] pieces={{{-8,12,6,-24},{-8,4,10,8}},{{-48,3,0,-28},{-16,1,4,-28}}};
        var union=new java.util.HashSet<Integer>();
        for(int frame=0;frame<2;frame++) for(int piece=0;piece<2;piece++) {
            var actual=assets.visual().mappingFrames().get(frame).pieces().get(piece); int[] e=pieces[frame][piece];
            assertEquals(e[0],actual.y()); assertEquals(e[1],actual.sizeByte());
            assertEquals(e[2],actual.rawTileWord()); assertEquals(e[3],actual.x());
            for(int tile=0;tile<actual.tileCount();tile++) union.add(actual.tileOffset()+tile);
        }
        assertEquals(Set.of(0,1,2,3,4,5,6,7,8,9,10,11),union);
        assertEquals(0x3C,assets.objectTileIds().unsignedWord(24));
        assertEquals(62,CdObjectTileIds.endOfBody(assets.objectTileIds(),12));
        assertEquals(0x8328,assets.objectTileIds().unsignedWord(60));
        assertThrows(IndexOutOfBoundsException.class,()->assets.objectTileIds().unsignedWord(72));
        assertThrows(NullPointerException.class,()->CdTunnelDoor.Assets.fromPastAssets(null));
    }

    @Test void prefixFactoryKeepsLegacyConstructorAndBankTokenSurface() throws Exception {
        assertEquals(1,CdTunnelDoor.class.getConstructors().length);
        assertEquals(7,CdTunnelDoor.class.getConstructors()[0].getParameterCount());
        // This literal null call compiles without an ambiguous public constructor overload.
        assertThrows(NullPointerException.class,()->new CdTunnelDoor(null,null,null,null,null,null,null));
        assertEquals(3,CdTunnelDoor.Assets.class.getRecordComponents().length);
        assertEquals(7,CdTunnelDoor.SolidCut.class.getRecordComponents().length);
        assertEquals(2,CdTunnelDoor.SolidCall.class.getRecordComponents().length);
        assertEquals(7,CdTunnelDoor.PrefixSolidCut.class.getRecordComponents().length);
        assertEquals(2,CdTunnelDoor.PrefixSolidCall.class.getRecordComponents().length);
        assertEquals(4,CdTunnelDoor.Result.class.getRecordComponents().length);
        assertEquals(CdSolidObject.Result.class,CdTunnelDoor.SolidCall.class.getMethod("result").getReturnType());
        assertEquals(CdSolidObject.Outcome.class,CdTunnelDoor.PrefixSolidCall.class.getMethod("outcome").getReturnType());
        assertNotNull(CdTunnelDoor.SolidOwner.class.getMethod("preflight",CdTunnelDoor.SolidCut.class));
        assertNotNull(CdTunnelDoor.SolidOwner.class.getMethod("commit",CdTunnelDoor.SolidCall.class));
        var a=retail(); var h=new Harness(a,CdTunnelDoor.Assets.fromPastAssets(a),false);
        assertThrows(NullPointerException.class,()->CdTunnelDoor.withPrefixOwner(h.runtime,null,h.assets,h.solid,h,h::draw,h::fm));
        assertThrows(NullPointerException.class,()->CdTunnelDoor.withPrefixOwner(h.runtime,h::readSecondary,h.assets,h.solid,null,h::draw,h::fm));
        assertEquals(0,h.reads,"named factory must not read secondary before native Main");
        var real=CdReservedObjectBank.clearedAfterLevelStart();
        var foreign=CdReservedObjectBank.clearedAfterLevelStart();
        assertNotEquals(real.state(1),foreign.state(1));
        assertThrows(IllegalArgumentException.class,()->real.write(foreign.state(1)));
        assertArrayEquals(new byte[64],real.state(1).copyBytes());
        // Separate old-bank value tests do not supply either bank to the new raw-source body.
    }

    @Test void rawSecondaryExtentAndNullFailOnlyAtReachedRead() throws Exception {
        var a=retail(); var assets=CdTunnelDoor.Assets.fromPastAssets(a);
        for(int extent:new int[]{-1,63,65}) {
            var h=new Harness(a,assets,false); h.seed(0); h.returnExtent=extent;
            assertEquals(0,h.reads);
            assertThrows(RuntimeException.class,h::run);
            assertEquals(1,h.calls.size()); assertEquals(CdTunnelDoor.Participant.PRIMARY,h.calls.getFirst().cut().participant());
            var result=completed(h.calls.getFirst());
            assertEquals(result.object(),h.slot()); assertEquals(result.player(),h.player);
            assertEquals(1,h.reads); assertTrue(h.draws.isEmpty());
            assertEquals(2,h.slot().routine()); assertEquals(0x8328,h.slot().word(TILE));
        }
    }

    @Test void everyRawSecondaryByteMustBeZeroAfterPrimaryPrefix() throws Exception {
        var a=retail(); var assets=CdTunnelDoor.Assets.fromPastAssets(a);
        for(int offset=0;offset<64;offset++) {
            var h=new Harness(a,assets,false); h.seed(0); h.changeReserved(offset,1);
            var source=h.runtime.capture(); var expectedRaw=h.rawReserved.copy();
            var failure=assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class,h::run,"byte"+offset);
            assertEquals(CdTunnelDoor.UnsupportedBoundary.SECONDARY_PLAYER_OWNER,failure.boundary());
            assertEquals(offset,failure.value()); assertEquals(WHOLE,failure.wholeIndex());
            assertEquals(1,h.calls.size()); assertEquals(completed(h.calls.getFirst()).object(),h.slot());
            assertArrayEquals(expectedRaw,h.rawReserved.copy()); assertArrayEquals(source.savedFlags().copy(),h.runtime.capture().savedFlags().copy());
            assertOtherSlots(source.pool(),h.pool(),Set.of(WHOLE)); assertTrue(h.draws.isEmpty());
        }
    }

    @Test void genuinePastInitFallsThroughPrimaryThenFreshSecondary() throws Exception {
        var a=retail(); var h=new Harness(a,CdTunnelDoor.Assets.fromPastAssets(a),false);
        h.seed(0); h.edit(dirty(h.slot())); var before=h.runtime.capture(); var original=h.slot();
        var oldPlayer=h.player; var oldGlobals=h.globals; var r=h.run();
        var expected=initPrefix(original).moveWord(TILE,0x8328).moveByte(SOLID_TYPE,0);
        assertEquals(expected,r.state()); assertEquals(expected,h.draws.getFirst());
        assertEquals(List.of("INIT","MAIN","PRIMARY","SECONDARY","DRAW_OBJECT","CHECK_OBJ_DESPAWN"),trace(r));
        assertEquals(2,h.calls.size()); var primary=h.calls.getFirst(); var secondary=h.calls.get(1);
        assertNotSame(primary.cut().callCut(),secondary.cut().callCut());
        assertEquals(completed(primary).object(),secondary.cut().callCut().slots().get(WHOLE-32));
        assertEquals(completed(primary).player(),secondary.cut().expectedPrimary());
        assertEquals(completed(primary).globals(),secondary.cut().expectedGlobals());
        assertArrayEquals(new byte[64],secondary.cut().secondary().orElseThrow().copy());
        assertTrue(h.reads>=3); assertEquals(oldPlayer,h.player); assertEquals(oldGlobals,h.globals);
        assertArrayEquals(before.savedFlags().copy(),h.runtime.capture().savedFlags().copy());
        assertOtherSlots(before.pool(),h.pool(),Set.of(WHOLE)); assertArrayEquals(new byte[31*64],h.rawReserved.copy());
        assertEquals(0x4321,h.slot().word(X_FRACTION)); assertEquals(0x8765,h.slot().word(Y_FRACTION));
    }

    @Test void genuinePrimaryNativeStopCommitsPrefixAndRethrowsBeforeSecondary() throws Exception {
        var a=retail(); var assets=CdTunnelDoor.Assets.fromPastAssets(a);
        for(int oldLink:new int[]{1,31,128,255}) {
            var h=new Harness(a,assets,true); h.seed(2); var p=new P();
            p.routine=2;p.y=0x00661234;p.flags=8;p.stand=oldLink;h.player=p.player();
            var failure=assertThrows(RuntimeException.class,h::run);
            assertEquals(1,h.calls.size()); var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,h.calls.getFirst().outcome());
            assertSame(stopped.stop().originalException(),failure);
            assertEquals(stopped.prefix().object(),h.slot()); assertEquals(stopped.prefix().player(),h.player);
            assertEquals(stopped.prefix().globals(),h.globals); assertEquals(0,h.reads);
            assertTrue(h.draws.isEmpty()); assertTrue(h.sounds.isEmpty());
            assertEquals(CdTunnelDoor.Participant.PRIMARY,h.calls.getFirst().cut().participant());
        }
    }

    @Test void absentSecondaryExecutesRealEntryOneThenClearZeroOutcome() throws Exception {
        var a=retail(); var h=new Harness(a,CdTunnelDoor.Assets.fromPastAssets(a),false);h.seed(2);
        var r=h.run(); assertEquals(2,h.calls.size());
        var first=h.calls.getFirst();var second=h.calls.get(1);var outcome=completed(second);
        assertEquals(CdTunnelDoor.Participant.SECONDARY,second.cut().participant());
        assertNotSame(first.outcome(),second.outcome()); assertNotSame(first.cut().callCut(),second.cut().callCut());
        assertEquals(0,completed(first).object().unsignedByte(SOLID_TYPE));
        assertEquals(0,second.cut().callCut().slots().get(WHOLE-32).unsignedByte(SOLID_TYPE));
        assertEquals(0,outcome.object().unsignedByte(SOLID_TYPE));assertEquals(0,outcome.d0().orElseThrow());
        assertEquals(Set.of(SOLID_TYPE),outcome.objectWrites());
        assertEquals(CdSolidObject.GetOffReason.PLAYER_ABSENT,outcome.getOffReason().orElseThrow());
        assertEquals(List.of(new CdSolidObject.RoutineCall(CdSolidObject.Routine.STOP_OBJ_PUSH),
                new CdSolidObject.RoutineCall(CdSolidObject.Routine.GET_OFF_OBJECT)),outcome.calls());
        assertTrue(outcome.playerWrites().isEmpty());assertTrue(outcome.globalWrites().isEmpty());assertTrue(outcome.otherSlots().isEmpty());
        assertEquals(second.cut().expectedPrimary(),r.finalPrimary());
        assertEquals(second.cut().expectedGlobals(),r.finalGlobals());
        assertNotEquals(r.finalPrimary(),outcome.player()); assertEquals(0,outcome.player().slotId());
        assertNotSame(h.rawReads.getFirst(),h.rawReads.getLast());
        assertArrayEquals(h.rawReads.getFirst().copy(),h.rawReads.getLast().copy());
        // Entry1 and clear0 are genuine shared stores represented by its SOLID_TYPE write set, not a fabricated d0.
    }

    @Test void preflightFailureOrMutationStopsBeforeTheSharedHelper() throws Exception {
        var a=retail();var assets=CdTunnelDoor.Assets.fromPastAssets(a);
        var failure=new IllegalArgumentException("component preflight");
        var rejected=new Harness(a,assets,false);rejected.seed(0);rejected.preflightHook=cut->{throw failure;};
        assertSame(failure,assertThrows(IllegalArgumentException.class,rejected::run));
        assertTrue(rejected.calls.isEmpty());assertEquals(0,rejected.reads);assertTrue(rejected.draws.isEmpty());
        assertEquals(2,rejected.slot().routine());assertEquals(0x8328,rejected.slot().word(TILE));
        var changed=new Harness(a,assets,false);changed.seed(0);
        changed.preflightHook=cut->changed.pool().write(changed.pool().state(91).moveByte(COLLAPSE_SAVED_SOURCE_ID,0x5A));
        assertThrows(IllegalStateException.class,changed::run);
        assertEquals(0x5A,changed.pool().state(91).unsignedByte(COLLAPSE_SAVED_SOURCE_ID));
        assertTrue(changed.calls.isEmpty());assertEquals(0,changed.reads);assertTrue(changed.draws.isEmpty());
    }

    @Test void commitOmissionOrStaleReadbackStopsBeforeLaterNativeWork() throws Exception {
        var a=retail();var assets=CdTunnelDoor.Assets.fromPastAssets(a);
        var omitted=new Harness(a,assets,false);omitted.seed(0);omitted.publishObject=false;
        assertThrows(IllegalStateException.class,omitted::run);
        assertEquals(1,omitted.calls.size());assertEquals(0,omitted.reads);assertTrue(omitted.draws.isEmpty());
        assertEquals(0x66,omitted.slot().unsignedByte(SOLID_TYPE));
        var stale=new Harness(a,assets,false);stale.seed(0);stale.staleReturn=true;
        assertThrows(IllegalStateException.class,stale::run);
        assertEquals(1,stale.calls.size());assertEquals(0,stale.reads);assertTrue(stale.draws.isEmpty());
        var other=new Harness(a,assets,true);other.seed(2);other.standing(41);other.publishOthers=false;
        var old=other.pool().state(41);
        assertThrows(IllegalStateException.class,other::run);
        assertFalse(completed(other.calls.getFirst()).otherSlots().isEmpty());
        assertEquals(old,other.pool().state(41));assertEquals(0,other.reads);assertTrue(other.draws.isEmpty());
    }

    @Test void callbackFailureRetainsOriginalWithSuppressedVerificationFault() throws Exception {
        var a=retail();var assets=CdTunnelDoor.Assets.fromPastAssets(a);
        var readFailure=new AssertionError("raw source original");
        var source=new Harness(a,assets,false);source.seed(0);
        source.readHook=()->{source.pool().write(source.pool().state(91).moveByte(COLLAPSE_SAVED_SOURCE_ID,0x5A));throw readFailure;};
        assertSame(readFailure,assertThrows(AssertionError.class,source::run));
        assertTrue(readFailure.getSuppressed().length>0);assertEquals(1,source.calls.size());assertTrue(source.draws.isEmpty());
        assertEquals(0x5A,source.pool().state(91).unsignedByte(COLLAPSE_SAVED_SOURCE_ID));

        var commitFailure=new AssertionError("secondary commit original");
        var commit=new Harness(a,assets,false);commit.seed(0);
        commit.commitHook=call->{if(call.cut().participant()==CdTunnelDoor.Participant.SECONDARY){
            commit.changeReserved(63,1);throw commitFailure;}};
        assertSame(commitFailure,assertThrows(AssertionError.class,commit::run));
        assertTrue(commitFailure.getSuppressed().length>0);assertEquals(2,commit.calls.size());assertTrue(commit.draws.isEmpty());

        var fmFailure=new AssertionError("FM original");
        var fm=new Harness(a,assets,false);fm.seed(2);fm.edit(fm.slot().moveByte(SUBTYPE,0));
        var p=new P();p.routine=2;p.yv=-1;fm.player=p.player();
        fm.fmHook=()->{fm.pool().write(fm.pool().state(91).moveByte(COLLAPSE_SAVED_SOURCE_ID,0x6B));throw fmFailure;};
        assertSame(fmFailure,assertThrows(AssertionError.class,fm::run));
        assertTrue(fmFailure.getSuppressed().length>0);assertEquals(0,fm.reads);
        assertEquals(0x0B,fm.pool().state(32).id());assertEquals(4,fm.slot().routine());assertTrue(fm.draws.isEmpty());

        var drawFailure=new AssertionError("Draw original");
        var draw=new Harness(a,assets,false);draw.seed(6);
        draw.drawHook=()->{draw.pool().write(draw.pool().state(91).moveByte(COLLAPSE_SAVED_SOURCE_ID,0x7C));throw drawFailure;};
        assertSame(drawFailure,assertThrows(AssertionError.class,draw::run));
        assertTrue(drawFailure.getSuppressed().length>0);assertEquals(2,draw.slot().routine());
    }

    @Test void animateAndResetDoNotReadUnreachedSecondarySource() throws Exception {
        var a=retail();var assets=CdTunnelDoor.Assets.fromPastAssets(a);
        for(int routine:new int[]{4,6}) {
            var h=new Harness(a,assets,false);h.seed(routine);h.changeReserved(0,1);h.changeReserved(63,2);
            h.readHook=()->fail("native Animate/Reset does not select whole1");
            var before=h.rawReserved.copy();h.run();
            assertEquals(0,h.reads);assertTrue(h.calls.isEmpty());assertEquals(1,h.draws.size());
            assertArrayEquals(before,h.rawReserved.copy());
        }
    }

    @Test void malformedPastDirectoryRowsRetainTheExactPublishedInitPrefix() throws Exception {
        var a=retail();var actual=CdTunnelDoor.Assets.fromPastAssets(a);
        for(int selector=0;selector<18;selector++) for(int invalid:new int[]{34,35,71,72,0x8000,0xFFFF}) {
            byte[] bytes=actual.objectTileIds().copy();word(bytes,selector*2,invalid);
            var h=new Harness(a,new CdTunnelDoor.Assets(actual.visual(),actual.mappingsAddress(),CdAssetBytes.copyOf(bytes)),false);
            h.seed(0);var before=h.slot();
            var failure=assertThrows(IllegalArgumentException.class,h::run);
            assertEquals("Object tile signed relative word points outside declared data bodies",failure.getMessage());
            assertEquals(initPrefix(before),h.slot());assertTrue(h.calls.isEmpty());assertEquals(0,h.reads);assertTrue(h.draws.isEmpty());
        }
        for(int subtype2:new int[]{1,2,255}) {
            var h=new Harness(a,actual,false);h.seed(0);h.edit(h.slot().moveByte(SUBTYPE2,subtype2));var before=h.slot();
            var failure=assertThrows(CdTunnelDoor.UnsupportedTunnelDoor.class,h::run);
            assertEquals(CdTunnelDoor.UnsupportedBoundary.TILE_ID_SUBTYPE2,failure.boundary());assertEquals(subtype2,failure.value());
            assertEquals(initPrefix(before),h.slot());assertEquals(0,h.reads);
        }
    }

    @Test void strictA74FacadeAndExistingSelectorNegativesRemainExact() throws Exception {
        var a=retail();var assets=CdTunnelDoor.Assets.fromPastAssets(a);
        assertThrows(IllegalArgumentException.class,()->CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(),12,0));
        assertEquals(0x8328,CdR11aObjectAssets.decodeTileWord(legacyTiles(),12,0));
        assertThrows(UnsupportedOperationException.class,()->CdR11aObjectAssets.decodeTileWord(legacyTiles(),17,0));
        assertThrows(UnsupportedOperationException.class,()->CdR11aObjectAssets.decodeTileWord(legacyTiles(),12,1));
        for(int size:new int[]{0,36,70,71,73,75,76}) {
            assertThrows(IllegalArgumentException.class,()->new CdTunnelDoor.Assets(assets.visual(),MAP,CdAssetBytes.copyOf(new byte[size])));
        }
        byte[] alternate=assets.objectTileIds().copy();word(alternate,60,0x1357);
        var h=new Harness(a,new CdTunnelDoor.Assets(assets.visual(),MAP,CdAssetBytes.copyOf(alternate)),false);
        h.seed(0);h.run();assertEquals(0x1357,h.slot().word(TILE));
        // Mutated public-format bytes above are not a claim of another authenticated source.
    }

    @Test void nativeOpeningAndDirtySplashCreationPrecedeA4AndSecondary() throws Exception {
        var a=retail();var assets=CdTunnelDoor.Assets.fromPastAssets(a);
        var h=new Harness(a,assets,false);h.seed(2);h.edit(h.slot().moveByte(SUBTYPE,0));
        var child=dirty(h.pool().state(32)).moveByte(ID,0).moveByte(ROUTINE,0xAA);
        h.pool().write(child);var p=new P();p.routine=2;p.yv=-1;h.player=p.player();
        var expected=child.moveByte(ID,0x0B).moveWord(X,X0).moveWord(Y,Y0-4);
        h.fmHook=()->{assertEquals(0,h.reads);assertEquals(expected,h.pool().state(32));assertEquals(4,h.slot().routine());};
        var result=h.run();assertEquals(expected,h.pool().state(32));
        assertEquals(List.of(CdTunnelDoor.FmSound.FM_A4),h.sounds);assertEquals(1,h.calls.size());
        assertEquals(CdTunnelDoor.Participant.SECONDARY,h.calls.getFirst().cut().participant());
        assertEquals(List.of("MAIN","SPLASH:32","FM_A4","SECONDARY","DRAW_OBJECT","CHECK_OBJ_DESPAWN"),trace(result));
        assertEquals(0xAA,h.pool().state(32).routine(),"body creates but never dispatches the child");

        var full=new Harness(a,assets,false);full.seed(2);full.edit(full.slot().moveByte(SUBTYPE,0));full.player=p.player();
        for(int whole=32;whole<128;whole++) if(whole!=WHOLE) full.pool().write(full.pool().state(whole).moveByte(ID,0x7E));
        full.run();assertEquals(4,full.slot().routine());assertTrue(full.sounds.isEmpty());assertEquals(1,full.calls.size());

        for(int[] vector:new int[][]{{231,2},{232,4},{279,4},{280,2}}) {
            var narrow=new Harness(a,assets,false);narrow.seed(0);narrow.edit(narrow.slot().moveByte(SUBTYPE,2));
            var player=new P();player.routine=2;player.yv=-1;player.x=(vector[0]<<16)|0x1234;narrow.player=player.player();
            narrow.run();assertEquals(vector[1],narrow.slot().routine());assertEquals(24,narrow.slot().unsignedByte(WIDTH));
            assertTrue(narrow.sounds.isEmpty());
        }
    }

    @Test void actualAnimationFcResetAndPostDrawDespawnRetainNativeOrder() throws Exception {
        var a=retail();var assets=CdTunnelDoor.Assets.fromPastAssets(a);
        var h=new Harness(a,assets,false);h.seed(4);h.edit(h.slot().moveByte(PREVIOUS_ANIMATION,1).moveByte(MAP_FRAME,7));
        int[] frames={0,0,1,1,1,1,1,1,1,1};
        for(int i=0;i<frames.length;i++) {
            var result=h.run();assertEquals(frames[i],result.state().unsignedByte(MAP_FRAME));
            assertEquals(i+1,result.state().unsignedByte(ANIMATION_FRAME));assertEquals(0,result.state().unsignedByte(ANIMATION_TIME));
            assertEquals(4,result.state().routine());
        }
        assertEquals(6,h.run().state().routine());
        var reset=h.run();assertEquals(2,reset.state().routine());assertEquals(1,reset.state().unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(0,reset.state().unsignedByte(MAP_FRAME));assertEquals(0,h.reads);assertTrue(h.calls.isEmpty());
        for(int[] v:new int[][]{{640,0},{768,1}}) {
            var edge=new Harness(a,assets,false);edge.seed(6);edge.edit(edge.slot().moveWord(X,v[0]));
            var r=edge.run();assertEquals(v[1]!=0,r.deleted());assertEquals(1,edge.draws.size());
            if(v[1]!=0) assertArrayEquals(new byte[64],edge.slot().copyBytes());
        }
        var visible=new Harness(a,assets,false);visible.seed(6);visible.edit(visible.slot().moveWord(X,0x380));
        visible.drawHook=()->visible.edit(visible.slot().moveByte(SPRITE_FLAGS,visible.slot().spriteFlags()|0x80));
        assertFalse(visible.run().deleted(),"actual post-Draw bit7 bypasses ordinary offscreen distance");
    }

    @Test void prefixModeReentryAndOutsideInvocationValuesGrantNoAuthority() throws Exception {
        var a=retail();var h=new Harness(a,CdTunnelDoor.Assets.fromPastAssets(a),false);h.seed(0);
        h.preflightHook=cut->h.body.run(WHOLE,h.world(),h.inputs());
        assertThrows(IllegalStateException.class,h::run);assertTrue(h.calls.isEmpty());assertEquals(0,h.reads);
        h.preflightHook=cut->{};h.seed(2);h.run();
        assertEquals(2,h.calls.size(),"standalone component guard retires after the failed invocation");
        var before=h.runtime.capture();var primary=h.player;var globals=h.globals;var reserved=h.rawReserved.copy();
        var observed=h.calls.getFirst();var record=new CdTunnelDoor.PrefixSolidCall(observed.cut(),observed.outcome());
        assertSame(observed.outcome(),record.outcome());
        assertOtherSlots(before.pool(),h.pool(),Set.of());assertEquals(primary,h.player);assertEquals(globals,h.globals);
        assertArrayEquals(reserved,h.rawReserved.copy());
        assertThrows(UnsupportedOperationException.class,()->h.last.effects().clear());
        // Constructing an observational record runs no producer; no private World/Frame authority is tested here.
        // This fresh standalone component invocation is not permission to retry a failed World Frame.
    }

    private static CdSolidObject.Result completed(CdTunnelDoor.PrefixSolidCall call) {
        return assertInstanceOf(CdSolidObject.Completed.class,call.outcome()).completedResult();
    }
    private static CdObjectState initPrefix(CdObjectState source) {
        return source.addByte(ROUTINE,2).moveLong(MAPPINGS,MAP).moveByte(PRIORITY,1)
                .moveByte(SPRITE_FLAGS,source.spriteFlags()|4).moveByte(WIDTH,source.subtype()==2?24:44).moveByte(Y_RADIUS,8);
    }
    private static CdObjectState dirty(CdObjectState s) {
        for(var field:CdObjectState.ByteField.values()) s=s.moveByte(field,0xB9);
        for(var field:CdObjectState.WordField.values()) s=s.moveWord(field,0xA65C);
        for(var field:CdObjectState.LongField.values()) s=s.moveLong(field,0x93E7516B);
        return normal(s,0).moveByte(SPRITE_FLAGS,0x31).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0)
                .moveByte(MAP_FRAME,9).moveWord(TUNNEL_DOOR_TIMER,0x12AB);
    }
    private static CdObjectState normal(CdObjectState s,int routine) {
        return s.moveByte(ID,0x0D).moveByte(ROUTINE,routine).moveByte(SPRITE_FLAGS,0x84)
                .moveWord(X,X0).moveWord(Y,Y0).moveWord(X_FRACTION,0x4321).moveWord(Y_FRACTION,0x8765)
                .moveByte(WIDTH,44).moveByte(Y_RADIUS,8).moveByte(PRIORITY,1).moveLong(MAPPINGS,MAP)
                .moveWord(TILE,0x8328).moveByte(MAP_FRAME,0).moveByte(SUBTYPE,1).moveByte(SUBTYPE2,0)
                .moveByte(SOLID_TYPE,0x66).moveByte(SAVED_FLAGS_ID,0);
    }
    private static void assertOtherSlots(CdObjectPool.Snapshot before,CdObjectPool pool,Set<Integer> changed) {
        for(var source:before.slots()) if(!changed.contains(source.identity().wholeIndex()))
            assertEquals(source,pool.state(source.identity().wholeIndex()),"whole"+source.identity().wholeIndex());
    }
    private static List<String> trace(CdTunnelDoor.Result result) {
        return result.effects().stream().map(effect->switch(effect){
            case CdTunnelDoor.RoutineEntered x->x.routine().name();
            case CdTunnelDoor.Solid x->x.participant().name();
            case CdTunnelDoor.SplashCreated x->"SPLASH:"+x.state().identity().wholeIndex();
            case CdTunnelDoor.PlayedFm x->x.sound().name();
            case CdTunnelDoor.CallReturned x->x.call().name();
            case CdTunnelDoor.LoadedCleared x->"UNLOAD:"+x.savedId()+":"+x.period();
            case CdTunnelDoor.Deleted x->"DELETE";
        }).toList();
    }
    private static byte[] legacyTiles() {
        int[] words={36,38,40,44,46,48,58,52,56,54,50,60,62,64,66,68,70,72,
                0x23A0,0x23B0,0x2409,0x2370,0x2000,0x2428,0x4334,0x0320,0,0x4000,0x4000,
                0x0409,0x0374,0x8328,0,0,0x0490,0x03E4,0};
        byte[] bytes=new byte[74];for(int i=0;i<words.length;i++) word(bytes,i*2,words[i]);return bytes;
    }
    private static void word(byte[] bytes,int at,int value){bytes[at]=(byte)(value>>>8);bytes[at+1]=(byte)value;}

    private static final class Harness implements CdTunnelDoor.PrefixSolidOwner {
        final CdObjectPlacementRuntime runtime;
        final CdTunnelDoor.Assets assets;
        final CdSolidObject solid;
        final CdTunnelDoor body;
        final List<CdTunnelDoor.PrefixSolidCall> calls=new ArrayList<>();
        final List<CdObjectState> draws=new ArrayList<>();
        final List<CdTunnelDoor.FmSound> sounds=new ArrayList<>();
        final List<CdAssetBytes> rawReads=new ArrayList<>();
        CdAssetBytes rawReserved=CdAssetBytes.copyOf(new byte[31*64]);
        CdSolidObject.Player player=new P().player();
        CdSolidObject.Gates gates=new CdSolidObject.Gates(0,0);
        CdSolidObject.Globals globals=new CdSolidObject.Globals(0,4,0x12,0x34);
        Consumer<CdTunnelDoor.PrefixSolidCut> preflightHook=cut->{};
        Consumer<CdTunnelDoor.PrefixSolidCall> commitHook=call->{};
        Runnable readHook=()->{},fmHook=()->{},drawHook=()->{};
        int reads,returnExtent=64;
        boolean publishObject=true,publishOthers=true,staleReturn;
        CdTunnelDoor.Result last;
        Harness(CdR11bAssets actual,CdTunnelDoor.Assets assets,boolean constructedProbeFixture) {
            this.assets=assets;byte[] saved=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];
            for(int i=0;i<saved.length;i++) saved[i]=(byte)(17*i+3);
            runtime=CdObjectPlacementRuntime.fromAssets(actual,CdAssetBytes.copyOf(saved));
            solid=constructedProbeFixture?terrainSolid():new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromPast(actual)));
            body=CdTunnelDoor.withPrefixOwner(runtime,this::readSecondary,assets,solid,this,this::draw,this::fm);
        }
        CdObjectPool pool(){return runtime.pool();}
        CdObjectState slot(){return pool().state(WHOLE);}
        void edit(CdObjectState value){pool().write(value);}
        void seed(int routine){edit(normal(slot(),routine));}
        void standing(int link){
            var p=new P();p.routine=2;p.y=0x00661234;p.flags=8;p.stand=link;player=p.player();
            if(link>=32&&link<128) pool().write(pool().state(link).moveByte(ID,0x12).moveByte(FLAGS,0xA8));
        }
        void changeReserved(int offset,int value){byte[] bytes=rawReserved.copy();bytes[offset]=(byte)value;rawReserved=CdAssetBytes.copyOf(bytes);}
        CdAssetBytes readSecondary(){
            reads++;readHook.run();if(returnExtent<0)return null;
            var image=rawReserved.slice(0,returnExtent);rawReads.add(image);return image;
        }
        CdTunnelDoor.Globals world(){return new CdTunnelDoor.Globals(0,0,128);}
        CdTunnelDoor.SolidInputs inputs(){return new CdTunnelDoor.SolidInputs(player,gates,globals);}
        CdTunnelDoor.LiveSolid live(){return new CdTunnelDoor.LiveSolid(player,gates,globals);}
        CdTunnelDoor.Result run(){last=body.run(WHOLE,world(),inputs());return last;}
        public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut cut){
            pool().validateRestore(cut.callCut());assertOtherSlots(cut.callCut(),pool(),Set.of());
            assertEquals(player,cut.expectedPrimary());assertEquals(gates,cut.expectedGates());assertEquals(globals,cut.expectedGlobals());
            if(cut.secondary().isPresent())assertArrayEquals(rawReserved.slice(0,64).copy(),cut.secondary().orElseThrow().copy());
            preflightHook.accept(cut);return live();
        }
        public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall call){
            preflightValues(call.cut());calls.add(call);
            CdObjectState object;List<CdObjectState> others;CdSolidObject.Player next;CdSolidObject.Globals nextGlobals;
            if(call.outcome() instanceof CdSolidObject.Completed done){
                object=done.completedResult().object();others=done.completedResult().otherSlots();
                next=done.completedResult().player();nextGlobals=done.completedResult().globals();
            }else{
                var prefix=((CdSolidObject.NativeStopped)call.outcome()).prefix();
                object=prefix.object();others=prefix.otherSlots();next=prefix.player();nextGlobals=prefix.globals();
            }
            if(publishObject)pool().write(object);
            if(publishOthers)others.forEach(pool()::write);
            if(call.cut().participant()==CdTunnelDoor.Participant.PRIMARY){player=next;globals=nextGlobals;}
            commitHook.accept(call);
            return staleReturn?new CdTunnelDoor.LiveSolid(player,gates,new CdSolidObject.Globals(1,4,0x12,0x34)):live();
        }
        void preflightValues(CdTunnelDoor.PrefixSolidCut cut){
            assertOtherSlots(cut.callCut(),pool(),Set.of());assertEquals(player,cut.expectedPrimary());
            assertEquals(gates,cut.expectedGates());assertEquals(globals,cut.expectedGlobals());
        }
        void fm(CdTunnelDoor.FmSound sound){sounds.add(sound);fmHook.run();}
        void draw(CdObjectPool actual,CdObjectState.Identity identity){
            assertSame(pool(),actual);assertEquals(WHOLE,identity.wholeIndex());var source=slot();draws.add(source);
            edit(source.moveByte(SPRITE_FLAGS,source.spriteFlags()&0x7F));drawHook.run();
        }
    }
    private static final class P {
        int x=0x01008000,y=0x00801234,xv=0x123,yv=0,gv=0,routine=6,angle=0x10,flags=2;
        int spr=4,xr=9,yr=19,anim=0,prev=0,jump=1,charge=0,lock=0,stick=0,ctrl=0;
        int pri=0x56,sec=0x78,tile=0x780,priority=2,hurt=0,rot=0,push=0,stand=0;
        CdSolidObject.Player player(){
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x,y,xv,yv,gv,
                    routine,angle,flags,spr,xr,yr,anim,prev,jump,charge,lock,stick,ctrl,pri,sec),tile,priority,hurt,rot),1,push,stand);
        }
    }
    /** Public constructed-format probe fixture retained from the existing component shape; never authenticated B terrain. */
    private static CdSolidObject terrainSolid(){
        byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256];
        byte[] heights=new byte[4096],widths=new byte[4096];
        fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;Arrays.fill(fg,2,fg.length,(byte)2);
        ids[1]=1;ids[2]=2;Arrays.fill(heights,16,32,(byte)16);Arrays.fill(widths,32,48,(byte)16);angles[2]=(byte)0xC0;
        var terrain=CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths);
        byte[] animations=new byte[578];for(int i=0;i<60;i++)word(animations,i*2,120);animations[122]=(byte)0xFF;
        byte[] maps=new byte[398],dplcs=new byte[398];
        for(int i=0;i<198;i++){word(maps,i*2,396);word(dplcs,i*2,396);}
        var visual=CdSonicVisualAssets.decode(maps,dplcs,new byte[69472]);
        byte[] atan=new byte[257];for(int i=0;i<=256;i++)atan[i]=(byte)(i/8);
        var player=CdR11aPlayerAssets.decode(animations,new byte[60],new byte[]{6,7,8,0x49,0x4C,(byte)0xFF},
                new byte[4],new byte[640],atan,visual);
        return new CdSolidObject(new CdPlayerMovement(terrain,player));
    }

    private String retainedRoot;
    private CdR11bAssets retained;
    private CdR11bAssets retail() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Original USA media requires soniccd.media.root");
        assertFalse(root.isBlank(),"Original USA media root must not be blank");
        if(retained!=null&&root.equals(retainedRoot))return retained;
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){
            var cues=paths.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size(),"Configured USA source must contain exactly one existing CUE");cue=cues.getFirst();
        }
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            retained=CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));
        }
        retainedRoot=root;return retained;
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
