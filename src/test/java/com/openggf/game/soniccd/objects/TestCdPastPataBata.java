package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdPataBata.NativeCall.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional B-source components, authored before the local production output existed, then frozen.
 * All Java is UNRUN by this author. J=58c38a6296a3194fa4a8c0791085c446d35c70ae;
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Root owns compile-RED and positive execution.
 * Native literals: Pata-Bata/Main.asm:7-80; its Data/Animations.asm:1-5 and both mapping includes;
 * Object Tile ID Set.asm:7-17; Object Tile IDs (Act 1 Past).asm:7-44;
 * Functions (Misc).asm:69-101; Object Animate.asm:14-61; Object Spawner.asm:388-446.
 * Actual authenticated B assets supply tables/math. Every placement, dirty image, callback mutation,
 * damaged subtype and malformed view below is explicitly constructed component ingress. None is
 * the natural-route slot image, a production Draw owner, current art, native execution or parity.
 */
class TestCdPastPataBata {
    private static final int SLOT = 32;
    private static final byte[] SCRIPTS = hex("0004000b070000010201ff030000010201ff");
    private static final byte[] NORMAL = hex("00060011001c02f0070000f0f00700080002f80d0010f0080000180001f00f0019f0");
    private static final byte[] DAMAGED = hex("00060011001c02f0070000f0f00700290002f80d0010f00800001800020000003100f00f0019f000");
    private static final byte[] NORMAL_INIT = hex(
            "3e04243f0020b38600ff800006ff1fff" +
            "00000000000010100310000100000700" +
            "2a000001020000000000010007008000" +
            "ffff8000fe00fe000003000000000000");
    private static final byte[] DAMAGED_INIT = hex(
            "3e04243f0020b3a800ffc00007002fff" +
            "00000000000010100310000101010300" +
            "2a00000102000000ff00010007008000" +
            "ffffc000ff00ff000004000000000000");
    private static CdR11bAssets cachedPast;

    @Test
    void pastFactoryRejectsNullWithoutAnyMediaOrDispatch() {
        assertThrows(NullPointerException.class, () -> CdPataBata.Assets.fromPastAssets(null));
        assertEquals(0x3E, CdPataBata.OBJECT_ID);
    }

    @Test
    void actualBFactoryRetainsExactFiniteViewsBothScriptsAndFiftyTileBounds() throws Exception {
        var b = past();
        var a = CdPataBata.Assets.fromPastAssets(b);
        assertEquals(72, a.objectTileIds().length());
        assertEquals("7f6759c3ab336c2eee70a2fea5128e9920c86d418590ae557e0310948c563da1", sha(a.objectTileIds().copy()));
        assertArrayEquals(b.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes().copy(), a.objectTileIds().copy());
        assertArrayEquals(SCRIPTS, a.animations().bytes().copy());
        assertEquals(0, a.animations().tableOffset());
        assertEquals(2, a.animations().scriptCount());
        assertEquals(0x20B386, a.normalMappingsAddress());
        assertEquals(0x20B3A8, a.damagedMappingsAddress());
        assertArrayEquals(NORMAL, a.normal().mappings().copy());
        assertArrayEquals(DAMAGED, a.damaged().mappings().copy());
        assertArrayEquals(b.view(CdR11bAssets.Asset.ANI_PATA_BATA).bytes().copy(), a.animations().bytes().copy());
        assertArrayEquals(b.view(CdR11bAssets.Asset.MAP_SPR_PATA_BATA1).bytes().copy(), a.normal().mappings().copy());
        assertArrayEquals(b.view(CdR11bAssets.Asset.MAP_SPR_PATA_BATA2).bytes().copy(), a.damaged().mappings().copy());
        for (var visual : List.of(a.normal(), a.damaged())) {
            assertEquals(0, visual.mappingTableOffset());
            assertEquals(List.of(6, 17, 28), visual.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
            assertEquals(2, visual.animations().size());
            assertArrayEquals(SCRIPTS, visual.animationTable().copy());
            for (int i = 0; i < 2; i++) {
                var script = visual.animations().get(i);
                assertEquals(i == 0 ? 7 : 3, script.delay());
                assertEquals(List.of(0, 0, 1, 2, 1), script.frameBytes());
                assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF, script.terminal());
            }
        }
        assertEquals(List.of(2, 2, 1), a.normal().mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(List.of(2, 2, 2), a.damaged().mappingFrames().stream().map(f -> f.pieces().size()).toList());
        assertEquals(new CdObjectVisualAssets.Piece(-16, 7, 0x29, 0), a.damaged().mappingFrames().getFirst().pieces().get(1));
        assertEquals(new CdObjectVisualAssets.Piece(0, 0, 0x31, 0), a.damaged().mappingFrames().get(2).pieces().getFirst());
        assertThrows(IllegalArgumentException.class, () -> CdObjectVisualAssets.decode(DAMAGED, 3, SCRIPTS, 2, 49));
        assertEquals(640, b.view(CdR11bAssets.Asset.SINE_TABLE).bytes().length());
        assertEquals("bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9", sha(b.view(CdR11bAssets.Asset.SINE_TABLE).bytes().copy()));
        assertEquals(97, a.math().calcSine(0x10).sine());
        assertEquals(103, a.math().calcSine(0x11).sine());
        assertEquals(-12, a.math().calcSine(0xFE).sine());
        assertEquals(-6, a.math().calcSine(0xFF).sine());
        assertEquals(256, a.math().calcSine(0).cosine());
    }

    @Test
    void legacyAFactoryRemainsSourceCompatibleWithItsActualAOwners() throws Exception {
        CdR11aAssets level;
        try (var source = openUsa()) {
            level = CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
        var objects = level.openingObjects();
        var a = CdPataBata.Assets.fromObjectAssets(objects, level.player());
        assertSame(objects.pataBataNormal(), a.normal());
        assertSame(objects.pataBataDamaged(), a.damaged());
        assertEquals(74, a.objectTileIds().length());
        assertEquals(objects.view(CdR11aObjectAssets.Table.PATA_BATA_NORMAL_MAPPINGS).range().nativeAddress(), a.normalMappingsAddress());
        assertEquals(objects.view(CdR11aObjectAssets.Table.PATA_BATA_DAMAGED_MAPPINGS).range().nativeAddress(), a.damagedMappingsAddress());
        assertArrayEquals(SCRIPTS, a.animations().bytes().copy());
    }

    @Test
    void constructedNormalAndDamagedInitReachDrawWithLiteralFull64Images() throws Exception {
        var a = factory();
        for (int subtype : new int[]{0, 0xFF}) {
            var h = new Harness(subtype, a);
            byte[] before = h.state().copyBytes();
            byte[] expected = subtype == 0 ? NORMAL_INIT : DAMAGED_INIT;
            var result = h.run();
            assertArrayEquals(expected, result.state().copyBytes());
            assertArrayEquals(expected, h.draw.observed.getFirst().copyBytes());
            assertEquals(1, h.draw.observed.size());
            assertSame(h.runtime.pool(), h.draw.actualPool);
            assertEquals(new CdObjectState.Identity(SLOT), h.draw.observed.getFirst().identity());
            assertEquals(successCalls(), result.effects());
            assertEquals(0x80, h.runtime.savedFlag(1, 0));
            assertTrue(h.runtime.sourceIdentity().isEmpty(), "constructed placement gains no media authority");
            assertEquals(0, before[0x24]);
            assertEquals(0x3E, h.state().id());
        }
    }

    @Test
    void constructedDirtyInitKeepsAliasesFractionsAndPhaseWithActualBSine() throws Exception {
        var a = factory();
        for (int subtype : new int[]{0, 0xFF}) {
            var h = new Harness(subtype, a);
            h.put(h.state().moveByte(SPRITE_FLAGS, 0xD2).moveByte(FLAGS, 0xA3)
                    .moveByte(PREVIOUS_ANIMATION, 0x77).moveByte(ROUTINE_2, 0x35)
                    .moveByte(COLLISION_STATUS, 0xA5).moveByte(ANGLE, 0x62)
                    .moveLong(X_POSITION, 0x01004000).moveLong(Y_POSITION, 0x07001234)
                    .moveWord(PATA_BATA_PHASE, 0x1234).moveLong(TUNNEL_PATH_DATA, 0x89ABCDEF)
                    .moveWord(X_VELOCITY, 0xBEEF).moveWord(Y_VELOCITY, 0xCAFE));
            var old = h.state();
            byte[] expected = old.copyBytes();
            expected[1] = 0x57; expected[0x24] = 2; expected[0x18] = 3; expected[0x20] = 0x2A;
            expected[0x16] = expected[0x17] = expected[0x19] = 0x10;
            word(expected, 2, 0x243F); word(expected, 0x2A, 0x0100); longword(expected, 0x2C, 0x07008000);
            longword(expected, 4, subtype == 0 ? 0x20B386 : 0x20B3A8);
            longword(expected, 8, subtype == 0 ? 0x00FFC000 : 0x01000000);
            // Real sine(10)=97 and sine(11)=103; low lane FFFF survives SWAP before ASR.
            longword(expected, 12, subtype == 0 ? 0x070CBFFF : 0x0706FFFF);
            longword(expected, 0x30, subtype == 0 ? -0x8000 : -0x4000);
            word(expected, 0x34, subtype == 0 ? 0x1034 : 0x1134);
            word(expected, 0x36, subtype == 0 ? 0xFE00 : 0xFF00); word(expected, 0x38, subtype == 0 ? 3 : 4);
            expected[0x1C] = expected[0x1D] = (byte) (subtype == 0 ? 0 : 1);
            expected[0x1A] = 0; expected[0x1B] = 1; expected[0x1E] = (byte) (subtype == 0 ? 7 : 3);
            byte[] atDraw = expected.clone(); atDraw[1] = (byte) 0xD7;
            assertArrayEquals(expected, h.run().state().copyBytes());
            assertArrayEquals(atDraw, h.draw.observed.getFirst().copyBytes());
            assertEquals(0x4000, old.word(X_FRACTION));
            assertEquals(0x40, old.unsignedByte(SCREEN_Y)); // HIGH byte of X fraction, not Y.
            assertEquals(0x1234, old.word(PATA_BATA_PHASE));
            assertEquals(0x35, h.state().unsignedByte(SOLID_TYPE)); // ROUTINE_2 alias unchanged.
        }
    }

    @Test
    void constructedNonzeroRoutinesAndFullDeltaRegisterLanesUseBMath() throws Exception {
        var a = factory();
        for (int routine : new int[]{1, 3, 0xFF}) {
            var h = new Harness(0, a);
            h.put(mainInput(h.state()).moveByte(ROUTINE, routine).moveWord(TILE, 0xBEEF)
                    .moveLong(MAPPINGS, 0xDEADBEEF).moveByte(PRIORITY, 0xE5));
            byte[] expected = h.state().copyBytes();
            longword(expected, 12, 0x02008000); expected[0x1E] = 0;
            assertArrayEquals(expected, h.run().state().copyBytes());
        }
        // Native independent literals: sine(01)=6; preserve delta HIGH word through SWAP.
        int[][] lanes = {{0x12345678, 0x1334, 0x13345678, 0x02069234},
                {0xFFFF8000, 0x00FF, 0x00FF8000, 0x02077FFF},
                {0x00008000, 0x0100, 0x01008000, 0x02068000},
                {0x80000000, 0x8100, 0x81000000, 0x02070000}};
        for (int[] c : lanes) {
            var h = new Harness(0, a);
            h.put(mainInput(h.state()).moveLong(PATA_BATA_X_DELTA, c[0])
                    .moveWord(PATA_BATA_ANCHOR_X, c[1]).moveWord(PATA_BATA_PHASE, 0x0100)
                    .moveWord(PATA_BATA_SHIFT, 0));
            byte[] expected = h.state().copyBytes(); longword(expected, 8, c[2]); longword(expected, 12, c[3]); expected[0x1E] = 0;
            assertArrayEquals(expected, h.body.run(SLOT, new CdPataBata.Globals(0, 0, 0, c[1])).state().copyBytes());
        }
    }

    @Test
    void constructedSigned8000Equality80AndSixBitShiftCountsKeepNativeBranches() throws Exception {
        var a = factory();
        var edge = new Harness(0, a);
        edge.put(mainInput(edge.state()).moveLong(PATA_BATA_X_DELTA, 0x80000000)
                .moveWord(PATA_BATA_PHASE, 0xC000));
        byte[] expected = edge.state().copyBytes(); longword(expected, 8, 0x81000000);
        longword(expected, 12, 0x01E09000); expected[0x1E] = 0;
        assertArrayEquals(expected, edge.run().state().copyBytes()); // NEG.W 8000 remains negative: no reversal.
        var flip = new Harness(0, a);
        flip.put(mainInput(flip.state()).moveLong(X_POSITION, 0x017F8000).moveLong(PATA_BATA_X_DELTA, 0x8000)
                .moveLong(PATA_BATA_ANCHOR_Y, 0x07008000).moveWord(PATA_BATA_PHASE, 0x4567)
                .moveWord(PATA_BATA_PHASE_SPEED, 0xFF00));
        expected = flip.state().copyBytes(); longword(expected, 12, 0x06FFDFFF);
        longword(expected, 0x30, -0x8000); word(expected, 0x34, 0xFF00); expected[1] = 5; expected[0x22] = 1; expected[0x1E] = 0;
        assertArrayEquals(expected, flip.run().state().copyBytes()); // equality80 reverses then adds ONCE.
        int[][] shifts = {{0, 0x01017FFF}, {31, 0x02007FFF}, {32, 0x02007FFF},
                {63, 0x02007FFF}, {64, 0x01017FFF}, {0xFFFF, 0x02007FFF}};
        for (int[] c : shifts) {
            var h = new Harness(0, a);
            h.put(mainInput(h.state()).moveLong(PATA_BATA_X_DELTA, -0x8000)
                    .moveWord(PATA_BATA_PHASE, 0xC000).moveWord(PATA_BATA_SHIFT, c[0]));
            expected = h.state().copyBytes(); longword(expected, 8, 0x00FF8000); longword(expected, 12, c[1]); expected[0x1E] = 0;
            assertArrayEquals(expected, h.run().state().copyBytes(), "constructed shift WORD " + c[0]);
        }
    }

    @Test
    void constructedFullByteSubtype2ReadsGenuineNeighborsThenFailsAtExtent() throws Exception {
        var a = factory();
        int[][] cases = {{0, 0x243F}, {1, 0x23ED}, {16, 0}};
        for (int[] c : cases) {
            var h = new Harness(0, a); h.put(h.state().moveByte(SUBTYPE2, c[0]));
            byte[] expected = NORMAL_INIT.clone(); expected[0x29] = (byte) c[0]; word(expected, 2, c[1]);
            assertArrayEquals(expected, h.run().state().copyBytes());
        }
        var h = new Harness(0xFF, a);
        h.put(h.state().moveByte(SUBTYPE2, 17).moveWord(TILE, 0xBEEF).moveLong(PATA_BATA_X_DELTA, 0x12345678));
        byte[] expected = h.state().copyBytes();
        expected[1] = 4; expected[0x24] = 2; expected[0x18] = 3; expected[0x20] = 0x2A;
        expected[0x16] = expected[0x17] = expected[0x19] = 0x10;
        word(expected, 0x2A, 0x0100); longword(expected, 0x2C, 0x07008000);
        var failure = assertThrows(CdPataBata.UnsupportedPataBata.class, h::run);
        assertEquals(CdPataBata.UnsupportedBoundary.SET_OBJECT_TILE_ID, failure.boundary());
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE)), failure.completedEffects());
        assertArrayEquals(expected, h.state().copyBytes());
        assertTrue(h.draw.observed.isEmpty());
        assertEquals(0x80, h.runtime.savedFlag(1, 0));
    }

    @Test
    void constructedSignedOddAndShortTileViewsPreserveOnlyTheReachedInitPrefix() throws Exception {
        var a = factory();
        byte[] signed = a.objectTileIds().copy(); word(signed, 2, 0xFFFE);
        var reachesZero = new Harness(0, withTiles(a, signed));
        reachesZero.put(reachesZero.state().moveByte(SUBTYPE2, 1));
        assertEquals(0x24, reachesZero.run().state().word(TILE)); // -2 + 2 = directory word0, not upfront rejection.
        byte[] odd = a.objectTileIds().copy(); word(odd, 2, 0x27);
        for (byte[] malformed : List.of(signed, odd, new byte[3])) {
            var h = new Harness(0, withTiles(a, malformed)); h.put(h.state().moveWord(TILE, 0xBEEF));
            byte[] expected = h.state().copyBytes();
            expected[1] = 4; expected[0x24] = 2; expected[0x18] = 3; expected[0x20] = 0x2A;
            expected[0x16] = expected[0x17] = expected[0x19] = 0x10;
            word(expected, 0x2A, 0x0100); longword(expected, 0x2C, 0x07008000);
            var failure = assertThrows(CdPataBata.UnsupportedPataBata.class, h::run);
            assertEquals(CdPataBata.UnsupportedBoundary.SET_OBJECT_TILE_ID, failure.boundary());
            assertArrayEquals(expected, h.state().copyBytes());
            assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE)), failure.completedEffects());
            assertTrue(h.draw.observed.isEmpty());
        }
    }

    @Test
    void constructedDrawMutationIsLiveTailAuthorityAndDrawFaultKeepsItsPrefix() throws Exception {
        var a = factory();
        for (boolean setBit7 : new boolean[]{false, true}) {
            var h = new Harness(0, a);
            h.runtime.bsetSavedFlag(1, 0, CdObjectPlacementRuntime.SavedBit.MEMBER_3);
            h.draw.afterClear = (pool, state) -> pool.write(state.moveWord(PATA_BATA_ANCHOR_X, 0x9000)
                    .moveByte(SPRITE_FLAGS, state.spriteFlags() | (setBit7 ? 0x80 : 0)));
            var result = h.run();
            assertArrayEquals(NORMAL_INIT, h.draw.observed.getFirst().copyBytes());
            assertEquals(1, h.draw.observed.size());
            if (setBit7) {
                byte[] expected = NORMAL_INIT.clone(); word(expected, 0x2A, 0x9000); expected[1] = (byte) 0x84;
                assertArrayEquals(expected, result.state().copyBytes());
                assertEquals(0x84, h.runtime.savedFlag(1, 0));
                assertEquals(successCalls(), result.effects());
            } else {
                assertArrayEquals(new byte[64], result.state().copyBytes());
                assertEquals(4, h.runtime.savedFlag(1, 0));
                assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), call(ANIMATE_OBJECT), call(DRAW_OBJECT),
                        new CdPataBata.Despawn(CdPataBata.DespawnEntry.ANCHOR_OFFSCREEN, 1, 0), call(CHECK_OBJ_DESPAWN2)), result.effects());
            }
        }
        var failed = new Harness(0, a);
        var cause = new IllegalStateException("constructed Draw failure after actual pool write");
        failed.draw.afterClear = (pool, state) -> { pool.write(state.moveWord(PATA_BATA_ANCHOR_X, 0x9000)); throw cause; };
        var failure = assertThrows(CdPataBata.UnsupportedPataBata.class, failed::run);
        assertSame(cause, failure.getCause());
        assertEquals(CdPataBata.UnsupportedBoundary.DRAW_OBJECT, failure.boundary());
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE), call(ANIMATE_OBJECT)), failure.completedEffects());
        byte[] expected = NORMAL_INIT.clone(); word(expected, 0x2A, 0x9000);
        assertArrayEquals(expected, failed.state().copyBytes());
        assertEquals(0x80, failed.runtime.savedFlag(1, 0));
    }

    @Test
    void constructedAnimationFaultRetainsNativeMotionBeforeAnyDraw() throws Exception {
        var h = new Harness(0, factory());
        h.put(mainInput(h.state()).moveLong(PATA_BATA_X_DELTA, -0x8000)
                .moveLong(PATA_BATA_ANCHOR_Y, 0x07008000).moveWord(PATA_BATA_PHASE_SPEED, 0xFE00)
                .moveByte(ANIMATION, 2).moveByte(PREVIOUS_ANIMATION, 2).moveByte(ANIMATION_TIME, 0)
                .moveByte(ANIMATION_FRAME, 0xE0));
        byte[] expected = h.state().copyBytes(); longword(expected, 8, 0x00FF8000);
        longword(expected, 12, 0x06FF1FFF); word(expected, 0x34, 0xFE00);
        var failure = assertThrows(CdPataBata.UnsupportedPataBata.class, h::run);
        assertEquals(CdPataBata.UnsupportedBoundary.ANIMATE_OBJECT, failure.boundary());
        assertEquals(List.of(call(DESTROY_ON_GOOD_FUTURE)), failure.completedEffects());
        assertArrayEquals(expected, h.state().copyBytes());
        assertTrue(h.draw.observed.isEmpty());
        assertEquals(0x80, h.runtime.savedFlag(1, 0));
    }

    @Test
    void constructedHeldDirtyAnimationAndBothRealFfScriptsUseOnlyReachedReads() throws Exception {
        var a = factory();
        var held = new Harness(0, a);
        held.put(mainInput(held.state()).moveByte(ANIMATION, 0xFF).moveByte(PREVIOUS_ANIMATION, 0xFF)
                .moveByte(ANIMATION_FRAME, 0xFF).moveByte(MAP_FRAME, 0xE7));
        byte[] expected = held.state().copyBytes(); longword(expected, 12, 0x02008000); expected[0x1E] = 0;
        assertArrayEquals(expected, held.run().state().copyBytes()); // held invalid IDs/frame are not eagerly read.
        for (int animation : new int[]{0, 1}) {
            var h = new Harness(0, a);
            h.put(mainInput(h.state()).moveByte(ANIMATION, animation).moveByte(PREVIOUS_ANIMATION, animation)
                    .moveByte(ANIMATION_FRAME, 5).moveByte(ANIMATION_TIME, 0).moveByte(MAP_FRAME, 0xE7));
            expected = h.state().copyBytes(); longword(expected, 12, 0x02008000);
            expected[0x1A] = 0; expected[0x1B] = 1; expected[0x1E] = (byte) (animation == 0 ? 7 : 3);
            assertArrayEquals(expected, h.run().state().copyBytes());
            assertArrayEquals(expected, h.draw.observed.getFirst().copyBytes());
        }
    }

    @Test
    void constructedGoodFutureConversionDeletes64RestoresOnlyWordsAndSkipsDrawBank() throws Exception {
        var a = factory();
        int[][] cases = {{0, 0}, {0x81, 0}, {1, 0xFF}};
        for (int[] c : cases) {
            var h = new Harness(c[1], a);
            h.put(h.state().moveByte(ROUTINE, 0xFF).moveByte(SPRITE_FLAGS, 0x80).moveByte(FLAGS, 0xA3)
                    .moveLong(X_POSITION, 0x12345678).moveLong(Y_POSITION, 0x9ABCDEF0)
                    .moveLong(PATA_BATA_X_DELTA, 0x12345678).moveLong(TUNNEL_PATH_DATA, 0x89ABCDEF));
            byte[] expected = new byte[64]; expected[0] = 0x18; word(expected, 8, 0x1234); word(expected, 12, 0x9ABC);
            var result = h.body.run(SLOT, new CdPataBata.Globals(1, c[0], 0x80, 0));
            assertArrayEquals(expected, result.state().copyBytes());
            assertEquals(List.of(new CdPataBata.ExplosionConversion(0x1234, 0x9ABC)), result.effects());
            assertTrue(result.converted()); assertFalse(result.deleted());
            assertTrue(h.draw.observed.isEmpty());
            assertEquals(0x80, h.runtime.savedFlag(1, 0));
        }
        var presentNormal = new Harness(0, a);
        assertFalse(presentNormal.body.run(SLOT, new CdPataBata.Globals(1, 1, 0, 0)).converted());
        assertArrayEquals(NORMAL_INIT, presentNormal.state().copyBytes());
    }

    /** Setup only: deliberately constructed Main ingress, including held animation timer1. */
    private static CdObjectState mainInput(CdObjectState s) {
        return s.moveByte(ROUTINE, 3).moveByte(SPRITE_FLAGS, 4).moveByte(FLAGS, 0)
                .moveWord(PATA_BATA_ANCHOR_X, 0x0100).moveLong(PATA_BATA_ANCHOR_Y, 0x02008000)
                .moveLong(PATA_BATA_X_DELTA, 0).moveWord(PATA_BATA_PHASE, 0)
                .moveWord(PATA_BATA_PHASE_SPEED, 0).moveWord(PATA_BATA_SHIFT, 3).moveByte(ANIMATION_TIME, 1);
    }

    private static CdPataBata.Assets withTiles(CdPataBata.Assets a, byte[] tiles) {
        return new CdPataBata.Assets(CdAssetBytes.copyOf(tiles), a.normal(), a.damaged(), a.animations(),
                a.normalMappingsAddress(), a.damagedMappingsAddress(), a.math());
    }

    private static CdPataBata.Assets factory() throws Exception { return CdPataBata.Assets.fromPastAssets(past()); }
    private static CdR11bAssets past() throws Exception {
        if (cachedPast == null) {
            try (var source = openUsa()) { cachedPast = CdR11bAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(source)); }
        }
        return cachedPast;
    }

    private static CdProgramAssets openUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Root supplies soniccd.media.root for actual-USA component checks");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "USA needs exactly one actual CUE"); cue = cues.getFirst();
        }
        return CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty());
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime;
        final Draw draw = new Draw();
        final CdPataBata body;
        Harness(int subtype, CdPataBata.Assets assets) {
            var b = ByteBuffer.allocate(34);
            b.putShort((short) 12).putShort((short) 28).putShort((short) -1).putInt(0).putShort((short) 0);
            b.putShort((short) 0x0100).putShort((short) 0x0700).put((byte) 0xBE).put((byte) subtype).put((byte) 0x20).put((byte) 0);
            b.putShort((short) -1).putInt(0).putShort((short) 0).putShort((short) -1).putInt(0);
            runtime = new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(b.array()));
            runtime.spawnObjects(0, 0);
            body = new CdPataBata(runtime, assets, draw);
        }
        CdObjectState state() { return runtime.pool().state(SLOT); }
        void put(CdObjectState state) { runtime.pool().write(state); }
        CdPataBata.Result run() { return body.run(SLOT, new CdPataBata.Globals(0, 0, 0, 0)); }
    }

    /** Explicit component port: entry bit7 clear plus an optional declared mutation/failure. */
    private static final class Draw implements CdPataBata.DrawObjectCall {
        final List<CdObjectState> observed = new ArrayList<>();
        CdObjectPool actualPool;
        BiConsumer<CdObjectPool, CdObjectState> afterClear;
        @Override public void drawObject(CdObjectPool pool, CdObjectState.Identity slot) {
            actualPool = pool;
            var state = pool.state(slot.wholeIndex()); observed.add(state);
            pool.write(state.moveByte(SPRITE_FLAGS, state.spriteFlags() & 0x7F));
            if (afterClear != null) afterClear.accept(pool, pool.state(slot.wholeIndex()));
        }
    }

    private static CdPataBata.Effect call(CdPataBata.NativeCall c) { return new CdPataBata.CallReturned(c); }
    private static List<CdPataBata.Effect> successCalls() {
        return List.of(call(DESTROY_ON_GOOD_FUTURE), call(ANIMATE_OBJECT), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2));
    }
    private static byte[] hex(String text) { return HexFormat.of().parseHex(text); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void word(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static void longword(byte[] bytes, int at, int value) { word(bytes, at, value >>> 16); word(bytes, at + 2, value); }
}
