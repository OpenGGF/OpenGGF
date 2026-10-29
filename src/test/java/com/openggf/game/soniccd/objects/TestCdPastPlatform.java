package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual original USA B source cases. N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Platform/Main.asm:7-118,135-226,527-555 and Data/Mappings (Platform 1).asm:1-130.
 * Tests were frozen before the one production materialization; every Java vector was UNRUN by author.
 * Detached source/format and shared component behavior do not prove private Past invocation, Frame
 * adoption, a completed World frame, queue/SAT/pixels, stopped Solid publication or regional parity.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdPastPlatform {
    private static final int WHOLE = 40, X0 = 0x1200, Y0 = 0x0300, MAP = 0x20CD1A;
    private static final int[] WIDTHS = {16,32,48,0}, RANGES = {2,3,4,6};
    private static final String MAPPINGS_HEX =
            "0006001c004604f0050001f0f00508010000050005f000050805000008f0050001e0f0050001f0f005000100f00500011000050005e000050009f000050009000005080510000cf0050001d0f0050001e0f0050001f0f005000100f005000110f00500012000050005d000050009e000050009f000050009000005000910000508052000";

    @Test void namedPastFactoryRetainsTheExactOriginalSourceAndThreeUnanimatedViews() throws Exception {
        var a = retail(); var p = CdPlatform.Assets.fromPastAssets(a);
        assertEquals(CdReleaseVariant.USA, a.identity().variant());
        assertEquals(CdStagePayload.R11B.stage(), a.identity().stage());
        assertEquals(CdNativeFile.R11B, a.identity().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, a.identity().domain());
        assertEquals("b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265", a.identity().mmdSha256());
        assertEquals(MAP, p.mappingsAddress());
        assertEquals(0xCD1A, a.view(PLATFORM_MAPPINGS).range().fileOffset());
        assertEquals(0xC992, a.view(PLATFORM_WIDTHS).range().fileOffset());
        assertEquals(0xC996, a.view(PLATFORM_RANGES).range().fileOffset());
        assertArrayEquals(HexFormat.of().parseHex(MAPPINGS_HEX), p.visual().mappings().copy());
        assertArrayEquals(a.view(PLATFORM_MAPPINGS).bytes().copy(), p.visual().mappings().copy());
        assertArrayEquals(new byte[]{16,32,48,0}, p.widths().copy());
        assertArrayEquals(new byte[]{2,3,4,6}, p.ranges().copy());
        assertArrayEquals(a.view(PLATFORM_WIDTHS).bytes().copy(), p.widths().copy());
        assertArrayEquals(a.view(PLATFORM_RANGES).bytes().copy(), p.ranges().copy());
        assertEquals("a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7", sha(p.visual().mappings().copy()));
        assertEquals("87d0fad66b7f6fe31844c828bbfa818f478c885f0f7672d1bb38d89ed6bfb9d3", sha(p.widths().copy()));
        assertEquals("ce513997025ae549282209565f4c74aae2258cd6c391acd643d3b7f1b8071c9a", sha(p.ranges().copy()));
        assertEquals(132, p.visual().mappings().length());
        assertEquals(0, p.visual().mappingTableOffset());
        assertTrue(p.visual().animations().isEmpty());
        assertEquals(0, p.visual().animationTable().length());
        assertThrows(IndexOutOfBoundsException.class, () -> p.visual().mappings().unsignedByte(132));
        assertThrows(IndexOutOfBoundsException.class, () -> p.widths().unsignedByte(4));
        assertThrows(IndexOutOfBoundsException.class, () -> p.ranges().unsignedByte(4));
    }

    @Test void allThreeCountedFramesKeepLiteralPiecesAttributesAndTheirFiniteTileUnion() throws Exception {
        var p = CdPlatform.Assets.fromPastAssets(retail());
        int[][][] expected = {
                {{-16,5,1,-16},{-16,5,0x801,0},{0,5,5,-16},{0,5,0x805,0}},
                {{-16,5,1,-32},{-16,5,1,-16},{-16,5,1,0},{-16,5,1,16},
                        {0,5,5,-32},{0,5,9,-16},{0,5,9,0},{0,5,0x805,16}},
                {{-16,5,1,-48},{-16,5,1,-32},{-16,5,1,-16},{-16,5,1,0},{-16,5,1,16},{-16,5,1,32},
                        {0,5,5,-48},{0,5,9,-32},{0,5,9,-16},{0,5,9,0},{0,5,9,16},{0,5,0x805,32}}
        };
        int[] targets = {6,28,70};
        var registry = mappings(p);
        var union = new HashSet<Integer>();
        assertEquals(3, p.visual().mappingFrames().size());
        for (int frame = 0; frame < 3; frame++) {
            var decoded = p.visual().mappingFrames().get(frame);
            assertEquals(targets[frame], decoded.relativeOffset());
            assertEquals(expected[frame].length, decoded.pieces().size());
            assertEquals(1 + 5 * expected[frame].length, decoded.bytes().length());
            var resolved = assertInstanceOf(CdObjectDrawQueue.FrameMapping.class, registry.resolve(MAP, false, frame));
            assertEquals(expected[frame].length, resolved.pieces().size());
            for (int piece = 0; piece < expected[frame].length; piece++) {
                int[] e = expected[frame][piece]; var actual = decoded.pieces().get(piece);
                assertEquals(e[0], actual.y()); assertEquals(e[1], actual.sizeByte());
                assertEquals(e[2], actual.rawTileWord()); assertEquals(e[3], actual.x());
                var raw = resolved.pieces().get(piece);
                assertEquals(e[0], raw.y()); assertEquals(e[1], raw.sizeByte());
                assertEquals(e[2], raw.rawTileWord()); assertEquals(e[3], raw.x());
                for (int tile = 0; tile < actual.tileCount(); tile++) {
                    int index = actual.tileOffset() + tile;
                    assertTrue(index < 57); union.add(index);
                }
            }
        }
        assertEquals(Set.of(1,2,3,4,5,6,7,8,9,10,11,12), union);
    }

    @Test void factoryIsTheOnlyNewTypedEntryAndExistingIdentityGuardsCannotMintAnAOrWarpOwner() throws Exception {
        var method = CdPlatform.Assets.class.getMethod("fromPastAssets", CdR11bAssets.class);
        assertEquals(CdPlatform.Assets.class, method.getReturnType());
        assertEquals(1, method.getParameterCount());
        assertEquals(1L, java.util.Arrays.stream(CdPlatform.Assets.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("fromPastAssets")).count());
        assertNotNull(CdPlatform.Assets.class.getMethod("fromObjectAssets", CdR11aObjectAssets.class));
        assertEquals(4, CdPlatform.Assets.class.getRecordComponents().length);
        assertEquals(5, CdPlatform.Globals.class.getRecordComponents().length);
        assertEquals(3, CdPlatform.Inputs.class.getRecordComponents().length);
        assertEquals(6, CdPlatform.SolidCall.class.getRecordComponents().length);
        assertEquals(4, CdPlatform.PrimaryAngleCall.class.getRecordComponents().length);
        assertEquals(2, CdPlatform.Result.class.getRecordComponents().length);
        assertEquals(26, CdPlatform.Site.values().length);
        assertEquals(1, CdPlatform.class.getConstructors().length);
        assertEquals(8, CdPlatform.class.getConstructors()[0].getParameterCount());
        assertThrows(NullPointerException.class, () -> CdPlatform.Assets.fromPastAssets(null));
        var id = retail().identity();
        for (var variant : List.of(CdReleaseVariant.JAPAN, CdReleaseVariant.EUROPE)) {
            assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.Identity(variant,
                    id.stage(), id.file(), id.domain(), id.mmdSha256(), id.observedWholeDiscSha256()));
        }
        for (var file : List.of(CdNativeFile.R11A, CdNativeFile.WARP)) {
            assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.Identity(id.variant(),
                    id.stage(), file, id.domain(), id.mmdSha256(), id.observedWholeDiscSha256()));
        }
        assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.Identity(id.variant(),
                CdStagePayload.R11A.stage(), id.file(), id.domain(), id.mmdSha256(), id.observedWholeDiscSha256()));
        // These ordinary Identity rejections are not fabricated owner instances or private factory authority.
    }

    @Test void genuinePastInitUsesEachWidthAndRangeAndPreservesEveryOtherImageAndFraction() throws Exception {
        var a = retail(); var p = CdPlatform.Assets.fromPastAssets(a);
        for (int subtype = 0; subtype < 16; subtype++) {
            var h = new Harness(a, p); h.seed(subtype, 0);
            var cut = h.placement.capture(); byte[] expected = initExpected(h.slot());
            var result = h.run(0,0, X0-128, Y0-128,1);
            assertArrayEquals(expected, result.state().copyBytes());
            assertArrayEquals(expected, h.draws.getFirst().copyBytes());
            assertEquals(1, h.draws.size()); assertTrue(h.calls.isEmpty()); assertTrue(h.angles.isEmpty());
            assertEquals(List.of(new CdPlatform.RoutineEntered(CdPlatform.NativeRoutine.INIT),
                    new CdPlatform.CallReturned(CdPlatform.NativeCall.DRAW_OBJECT)), result.effects());
            assertPool(cut.pool(), h.pool(), Set.of(WHOLE));
            assertEquals(cut.status(), h.placement.status());
            assertArrayEquals(cut.savedFlags().copy(), h.placement.capture().savedFlags().copy());
            assertEquals(0x4321, h.slot().word(X_FRACTION));
            assertEquals(0x8765, h.slot().word(Y_FRACTION));
        }
        for (int zone : new int[]{1,0xFFFF}) {
            var h = new Harness(a, p); h.seed(0xFE, 0); byte[] expected = initExpected(h.slot());
            assertArrayEquals(expected, h.run(zone,0, X0-128,Y0-128,1).state().copyBytes());
            assertEquals(MAP, h.slot().longBits(MAPPINGS));
        }
    }

    @Test void zeroNativeWidthDoesNotInventAFourthFrameOrFailBeforeMappingSelection() throws Exception {
        var a = retail(); var p = CdPlatform.Assets.fromPastAssets(a);
        var h = new Harness(a,p); h.seed(3,0); byte[] expected=initExpected(h.slot());
        var result = h.run(0,0, X0-128,Y0-128,1);
        assertEquals(2, result.state().routine()); assertEquals(3, result.state().unsignedByte(MAP_FRAME));
        assertEquals(0, result.state().unsignedByte(WIDTH)); assertEquals(1, h.draws.size());
        assertTrue(h.calls.isEmpty()); assertArrayEquals(expected,h.slot().copyBytes());
        var atSelection = h.pool().capture();
        var error = assertThrows(UnsupportedOperationException.class,
                () -> mappings(p).resolve(h.slot().longBits(MAPPINGS), false, h.slot().unsignedByte(MAP_FRAME)));
        assertTrue(error.getMessage().contains("frame=3"));
        assertTrue(error.getMessage().contains("outside the registered source table"));
        assertPool(atSelection, h.pool(), Set.of());
        // The actual shared resolver selected after Init; this is not a completed DrawObjects/SAT witness.
    }

    @Test void genuinePastSineAndOffscreenTopKeepNativeWordArithmeticAndBothFractions() throws Exception {
        var a = retail(); var p = CdPlatform.Assets.fromPastAssets(a);
        // variant,oldPhase,oldSag,expectedX,expectedY,phase,XV,sag. Actual sine[$40]=256.
        int[][] vectors = {{0,0x3F,0xFE,X0,Y0+96,0x40,0x1234,0xFE},
                {1,0x40,0xFE,X0+96,Y0+255,0x41,0x6000,0xFD},
                {2,0x3F,0xFE,X0+96,Y0+96,0x40,0x6000,0xFD},
                {3,0x3F,0xFE,X0-96,Y0+96,0x40,0xA000,0xFD}};
        for (int[] v : vectors) {
            var h = new Harness(a,p); h.seed(v[0]<<4,0); h.run(0,0,X0-128,Y0-128,1);
            h.edit(h.slot().moveByte(PLATFORM_PHASE,v[1]).moveByte(PLATFORM_SAG,v[2])
                    .moveByte(PLATFORM_RANGE,6).moveByte(SPRITE_FLAGS,4).moveByte(SOLID_TYPE,0xA5)
                    .moveWord(X_VELOCITY,0x1234));
            var before=h.placement.capture(); var expected=h.slot().copyBytes();
            word(expected,8,v[3]); word(expected,12,v[4]); expected[0x2A]=(byte)v[5];
            word(expected,0x10,v[6]); expected[0x2C]=(byte)v[7]; expected[0x25]=0;
            var oldPlayer=h.player; var oldGlobals=h.solidGlobals;
            var result=h.run(0,0,X0-128,Y0-128,1);
            assertArrayEquals(expected,result.state().copyBytes());
            assertEquals(1,h.calls.size()); assertEquals(CdSolidObject.Entry.TOP_SOLID_OBJECT,h.calls.getFirst().entry());
            var done=assertInstanceOf(CdSolidObject.Completed.class,h.calls.getFirst().outcome()).completedResult();
            assertEquals(0,done.d0().orElseThrow()); assertTrue(done.otherSlots().isEmpty());
            assertEquals(oldPlayer,h.player); assertEquals(oldGlobals,h.solidGlobals);
            assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
            assertPool(before.pool(),h.pool(),Set.of(WHOLE));
            assertEquals(0x4321,result.state().word(X_FRACTION)); assertEquals(0x8765,result.state().word(Y_FRACTION));
        }
    }

    @Test void pastInitChildUsesTheRealFirstDirtyFreeSlotAndOnlyItsLiteralStores() throws Exception {
        var a=retail(); var p=CdPlatform.Assets.fromPastAssets(a);
        var h=new Harness(a,p); h.seed(0x41,0xFA);
        h.pool().write(dirty(h.pool().state(32)).moveByte(ID,0));
        var before=h.placement.capture(); var child=before.pool().slots().getFirst(); byte[] expected=child.copyBytes();
        expected[0]=0x0A; word(expected,8,X0+0xF8); word(expected,12,Y0-16);
        expected[0x39]=(byte)0xF0; word(expected,0x34,h.slot().identity().nativeAddress24());
        expected[0x28]=2; expected[0x38]=(byte)0xF8;
        var result=h.run(0,0,X0-128,Y0-128,1);
        assertArrayEquals(initExpected(before.pool().slots().get(WHOLE-32)), result.state().copyBytes());
        assertArrayEquals(expected,h.pool().state(32).copyBytes());
        assertEquals(List.of(new CdPlatform.SlotFound(32)), result.effects().stream()
                .filter(CdPlatform.SlotFound.class::isInstance).toList());
        assertEquals(1,result.effects().stream().filter(CdPlatform.ChildInitialized.class::isInstance).count());
        assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
        assertPool(before.pool(),h.pool(),Set.of(WHOLE,32));
        assertTrue(h.calls.isEmpty()); assertEquals(1,h.draws.size());
        // Child Init is not executed here; its inherited dirty routine/fractions survive FindObjSlot.
    }

    @Test void fullPastPoolKeepsTheRealCapacityPrefixThenDrawsTheClearedParentOnce() throws Exception {
        var a=retail(); var h=new Harness(a,CdPlatform.Assets.fromPastAssets(a)); h.seed(0x40,2);
        for(int whole=32;whole<128;whole++) if(whole!=WHOLE) h.pool().write(h.pool().state(whole).moveByte(ID,0x7F));
        var before=h.placement.capture(); var result=h.run(0,0,X0-128,Y0-128,1);
        assertTrue(result.deleted()); assertEquals(0,result.state().id());
        assertArrayEquals(new byte[64],result.state().copyBytes());
        assertEquals(1,h.draws.size()); assertArrayEquals(new byte[64],h.draws.getFirst().copyBytes());
        assertTrue(result.effects().contains(new CdPlatform.Deleted(CdPlatform.DeleteSite.INIT_CAPACITY)));
        assertTrue(h.calls.isEmpty()); assertTrue(h.angles.isEmpty());
        assertPool(before.pool(),h.pool(),Set.of(WHOLE));
        assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
    }

    @Test void nonzeroStopWordBypassesUnownedSubtypeAndFarAnchorAfterOneRealTop() throws Exception {
        var a=retail(); var h=new Harness(a,CdPlatform.Assets.fromPastAssets(a)); h.seed(0xFF,0);
        h.run(0,0,X0-128,Y0-128,1);
        h.edit(h.slot().moveByte(SPRITE_FLAGS,4).moveByte(SOLID_TYPE,0x55).moveWord(PLATFORM_ANCHOR_X,0x8000));
        var before=h.placement.capture(); byte[] expected=h.slot().copyBytes(); expected[0x25]=0;
        var result=h.run(0,0x0100,0,0,0xFF);
        assertArrayEquals(expected,result.state().copyBytes()); assertFalse(result.deleted());
        assertEquals(1,h.calls.size()); assertEquals(CdPlatform.Site.TIME_STOP_TOP,h.calls.getFirst().site());
        assertInstanceOf(CdSolidObject.Completed.class,h.calls.getFirst().outcome());
        assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
        assertPool(before.pool(),h.pool(),Set.of(WHOLE));
        assertTrue(h.angles.isEmpty());
    }

    private static CdObjectMappings mappings(CdPlatform.Assets assets) {
        return CdObjectMappings.declaredSourceViews(List.of(new CdObjectMappings.DeclaredTable(
                assets.mappingsAddress(),assets.visual().mappings(),0,3)),List.of());
    }
    private static CdObjectState dirty(CdObjectState s) {
        for(var field:CdObjectState.ByteField.values()) s=s.moveByte(field,(0x53+7*field.byteOffset())&0xFF);
        for(var field:CdObjectState.WordField.values()) s=s.moveWord(field,(0xA153+0x103*field.byteOffset())&0xFFFF);
        for(var field:CdObjectState.LongField.values()) s=s.moveLong(field,0xA1536729+0x01030507*field.byteOffset());
        return s;
    }
    private static byte[] initExpected(CdObjectState before) {
        byte[] e=before.copyBytes(); e[1]|=4; word(e,2,0x44BE); e[0x18]=2;
        word(e,0x38,before.centreX()); word(e,0x3A,before.centreY()); word(e,0x36,before.centreY());
        putLong(e,4,MAP); int subtype=before.subtype();
        e[0x1A]=(byte)(subtype&3); e[0x19]=(byte)WIDTHS[subtype&3]; e[0x16]=8;
        e[0x2D]=(byte)RANGES[(subtype>>>2)&3]; e[0x24]+=2; return e;
    }
    private static void assertPool(CdObjectPool.Snapshot before,CdObjectPool pool,Set<Integer> changed) {
        for(var old:before.slots()) if(!changed.contains(old.identity().wholeIndex())) {
            assertEquals(old,pool.state(old.identity().wholeIndex()),"whole "+old.identity().wholeIndex());
        }
    }
    private static void word(byte[] e,int at,int value) { e[at]=(byte)(value>>>8);e[at+1]=(byte)value; }
    private static void putLong(byte[] e,int at,int value) { word(e,at,value>>>16);word(e,at+2,value); }

    private static final class Harness {
        final CdObjectPlacementRuntime placement;
        final CdPlatform body;
        final List<CdPlatform.SolidCall> calls=new ArrayList<>();
        final List<CdPlatform.PrimaryAngleCall> angles=new ArrayList<>();
        final List<CdObjectState> draws=new ArrayList<>();
        CdSolidObject.Player player=new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(
                new CdPlayerMotionState(0x01008000,0x00671234,0x123,0x400,0,2,0x10,2,4,9,19,
                        0,0,1,0,0,0,0,0x56,0x78),0x780,2,0,0),1,0,0);
        CdSolidObject.Globals solidGlobals=new CdSolidObject.Globals(0,4,0x12,0x34);
        Harness(CdR11bAssets actual,CdPlatform.Assets assets) {
            byte[] saved=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];
            for(int i=0;i<saved.length;i++) saved[i]=(byte)(17*i+3);
            placement=CdObjectPlacementRuntime.fromAssets(actual,CdAssetBytes.copyOf(saved));
            var movement=CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromPast(actual));
            body=new CdPlatform(placement,assets,new CdPlayerMath(actual.player()),
                    CdPlayerTerrain.fromStageTerrain(actual.terrain()),new CdSolidObject(movement),
                    this::commit,this::angle,(pool,identity)->draws.add(pool.state(identity.wholeIndex())));
        }
        CdObjectPool pool(){return placement.pool();}
        CdObjectState slot(){return pool().state(WHOLE);}
        void edit(CdObjectState value){pool().write(value);}
        void seed(int subtype,int subtype2) {
            edit(dirty(slot()).moveByte(ID,0x21).moveByte(ROUTINE,0).moveByte(SUBTYPE,subtype)
                    .moveByte(SUBTYPE2,subtype2).moveByte(SPRITE_FLAGS,0x21).moveByte(FLAGS,0x20)
                    .moveByte(SAVED_FLAGS_ID,0).moveWord(X,X0).moveWord(Y,Y0)
                    .moveWord(X_FRACTION,0x4321).moveWord(Y_FRACTION,0x8765));
        }
        CdPlatform.Result run(int zone,int stop,int cameraX,int cameraY,int timeZone) {
            return body.run(WHOLE,new CdPlatform.Globals(zone,stop,cameraX,cameraY,timeZone),
                    new CdPlatform.Inputs(player,new CdSolidObject.Gates(0,0),solidGlobals));
        }
        void commit(CdPlatform.SolidCall call) {
            pool().validateRestore(call.actualCut()); assertPool(call.actualCut(),pool(),Set.of());
            assertEquals(player,call.expectedPlayer()); assertEquals(solidGlobals,call.expectedGlobals());
            var r=assertInstanceOf(CdSolidObject.Completed.class,call.outcome()).completedResult();
            assertTrue(r.otherSlots().isEmpty()); assertTrue(r.fmRequests().isEmpty());
            pool().validateWrite(r.object()); calls.add(call);
            pool().write(r.object()); player=r.player(); solidGlobals=r.globals();
        }
        void angle(CdPlatform.PrimaryAngleCall call) {
            angles.add(call); fail("These bounded source cases never reach the ceiling angle port");
        }
    }

    private String retainedRoot;
    private CdR11bAssets retained;
    private CdR11bAssets retail() throws Exception {
        String root=System.getProperty("soniccd.media.root");
        assertNotNull(root,"Original USA media cases require soniccd.media.root");
        assertFalse(root.isBlank(),"Original USA media root must not be blank");
        if(retained!=null&&root.equals(retainedRoot)) return retained;
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))) {
            var cues=paths.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size(),"Configured USA source must contain exactly one existing CUE");
            cue=cues.getFirst();
        }
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            retained=CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(programs));
        }
        retainedRoot=root; return retained;
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
