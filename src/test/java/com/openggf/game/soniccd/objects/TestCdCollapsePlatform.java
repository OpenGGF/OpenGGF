package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.level.CdR11aTerrain;
import com.openggf.game.soniccd.level.CdSonicVisualAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdCollapsePlatform.Site.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-only R1 tests. Native source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Palmtree Panic/Objects/Collapsing Platform/Main.asm:7-308 and Data (Present).asm:11-59.
 * Constructed public-format terrain and actual shared Solid are used throughout; no fake native return,
 * probe implementation, private constructor, raw state importer or forged pool owner exists here.
 * All 64 bytes are compared, but inaccessible opaque bytes can only be tested at their public zero value.
 * Commit is a contract harness, NOT production Frame, private phase, FM ledger or World authority.
 * ALL methods UNRUN at author freeze. Parent owns RED, compilation, positive checks and review.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdCollapsePlatform {
    private static final int WHOLE = 40, X0 = 3248, Y0 = 784;
    private static final int MAP1 = 0x20C520, MAP2 = 0x20C5EC, MAP3 = 0x20C5AE, MAP4 = 0x20C7AC;
    private static final byte[] SIZES1 = HexFormat.of().parseHex("00020403ffff000000010203030400050505050606060606");
    private static final byte[] SIZES2 = HexFormat.of().parseHex("000c000c0014001a0020002405010000000000000303010202020303020202020103030501030504");
    private static final int[] COUNTS = {6, 6, 4, 4, 2, 2};
    private static final int[][] SINGLE_FRAMES = {{0,0,0,0,0,0}, {0,0,0,0,0,0}, {1,2,2,2}, {2,2,2,2}, {3,5}, {5,4}};
    private static final int[] MULTI_FRAMES = {-1,-1,0,0,0,1,2,3,3,4,0,5,5,5,5,6,6,6,6,6};

    @Test void fourAliasesAddressOnlyExistingNativeStorage() {
        assertEquals(0x2A, COLLAPSE_DELAY.byteOffset());
        assertEquals(0x2C, COLLAPSE_FALL_DELTA.byteOffset());
        assertEquals(0x3E, COLLAPSE_SOLID_MARKER.byteOffset());
        assertEquals(0x3F, COLLAPSE_SAVED_SOURCE_ID.byteOffset());
        var p = new CdObjectPool(); var before = p.state(32);
        var changed = before.moveWord(COLLAPSE_DELAY, 0xA1B2).moveLong(COLLAPSE_FALL_DELTA, 0xC3D4E5F6)
                .moveByte(COLLAPSE_SOLID_MARKER, 0x97).moveByte(COLLAPSE_SAVED_SOURCE_ID, 0x68);
        byte[] b = new byte[64]; word(b, 0x2A, 0xA1B2); putLong(b, 0x2C, 0xC3D4E5F6); b[0x3E] = (byte) 0x97; b[0x3F] = 0x68;
        assertArrayEquals(b, changed.copyBytes()); assertArrayEquals(new byte[64], before.copyBytes());
    }

    @Test void initAllByteSubtypesWithNativePresentHeaderIndexesPreservesAllNonwrites() {
        for (int subtype = 0; subtype < 256; subtype++) {
            int index = subtype & 15;
            if (index >= (subtype < 128 ? 1 : 6)) continue;
            var h = new Harness(); h.edit(o -> dirty(o).moveByte(ID, 0x20).moveByte(ROUTINE, 0)
                    .moveByte(SPRITE_FLAGS, 0x24).moveByte(FLAGS, 0x52));
            h.edit(o -> o.moveByte(SUBTYPE, subtypeValue(index, o, 0))); // replaced below by the actual loop byte
            h.edit(withSubtype(subtype));
            var before = h.slot(); h.run(); var cut = h.calls.getFirst().actualCut().slots().get(WHOLE - 32);
            byte[] e = before.copyBytes(); e[0x24] = 2; e[1] |= 4; e[0x18] = 3; word(e, 2, 0x44BE);
            putLong(e, 4, subtype < 128 ? MAP1 : MAP2);
            if ((subtype & 0x10) != 0) { e[1] |= 1; e[0x22] |= 1; }
            e[0x1A] = (byte) index;
            e[0x17] = e[0x19] = (byte) (subtype < 128 ? 40 : COUNTS[index] * 8);
            e[0x16] = (byte) (subtype < 128 || index >= 2 ? 34 : 18);
            assertArrayEquals(e, cut.copyBytes(), "Init subtype " + subtype);
            e[0x25] = 0; assertArrayEquals(e, h.drawSeen.getFirst().copyBytes(), "post-Top subtype " + subtype);
            assertEquals(List.of(MAIN_TOP), h.sites()); assertTrue(h.fm.isEmpty());
        }
    }

    @Test void everySecondarySubtypeUsesLiteralTileWithoutSelectorAdmission() {
        for (int secondary = 0; secondary < 256; secondary++) {
            var h = new Harness(); final int s = secondary; h.edit(o -> o.moveByte(SUBTYPE2, s));
            h.run(); assertEquals(0x44BE, h.slot().word(TILE)); assertEquals(secondary, h.slot().subtype2());
        }
    }

    @Test void illegalInitIndexStopsAfterExactReachedPrefixAndBeforeMain() {
        var h = new Harness(); h.edit(o -> dirty(o).moveByte(ID, 0x20).moveByte(ROUTINE, 0)
                .moveByte(SPRITE_FLAGS, 0x20).moveByte(SUBTYPE, 1));
        var before = h.slot(); var ex = assertThrows(CdCollapsePlatform.UnsupportedCollapsePlatform.class, h::run);
        assertEquals(CdCollapsePlatform.UnsupportedBoundary.SIZE_HEADER_READ, ex.boundary());
        assertEquals(CdCollapsePlatform.NativeTable.SIZES_1, ex.table()); assertEquals(0x403, ex.byteOffset());
        byte[] e = before.copyBytes(); e[0x24] += 2; e[1] |= 4; e[0x18] = 3; word(e, 2, 0x44BE); putLong(e, 4, MAP1); e[0x1A] = 1;
        assertArrayEquals(e, h.slot().copyBytes()); assertTrue(h.calls.isEmpty()); assertTrue(h.drawSeen.isEmpty());
    }

    @Test void initByteWidthAndSignedByteAbsoluteArithmeticWrapBeforeStores() {
        var h = new Harness(assets(new byte[]{0,2,31,(byte) 0x80}, SIZES2), new Terrain().owner());
        h.edit(o -> o.moveByte(SUBTYPE, 0)); h.run();
        assertEquals(0, h.slot().unsignedByte(X_RADIUS)); assertEquals(0, h.slot().unsignedByte(WIDTH));
        assertEquals(10, h.slot().unsignedByte(Y_RADIUS));
    }

    @Test void allUnsupportedDirtyRoutineBytesStopBeforeAnyInputReadOrWrite() {
        for (int r = 0; r < 256; r++) {
            if ((r & 1) == 0 && r <= 6) continue;
            var h = new Harness(); final int raw = r; h.edit(o -> dirty(o).moveByte(ID, 0x20).moveByte(ROUTINE, raw));
            var before = h.pool().capture();
            var ex = assertThrows(CdCollapsePlatform.UnsupportedCollapsePlatform.class, () -> h.body.run(WHOLE, null, null));
            assertEquals(CdCollapsePlatform.UnsupportedBoundary.ROUTINE_DISPATCH, ex.boundary());
            assertEquals(r, ex.byteOffset()); assertPool(before, h.pool()); assertTrue(h.calls.isEmpty()); assertTrue(h.drawSeen.isEmpty());
        }
    }

    @Test void mainCallsActualTopEvenWithNoVisibilityAndRawUnusedGlobals() {
        var h = new Harness(); h.main(0x9F); h.edit(o -> o.moveByte(SPRITE_FLAGS, 4).moveByte(SOLID_TYPE, 0xA7));
        var r = h.run(new CdCollapsePlatform.DespawnGlobals(3, 0xFF, 0xD00));
        assertEquals(List.of(MAIN_TOP), h.sites()); var s = completed(h.calls.getFirst());
        assertEquals(0, s.d0().orElseThrow()); assertEquals(CdSolidObject.GetOffReason.OBJECT_OFFSCREEN, s.getOffReason().orElseThrow());
        assertEquals(0, r.state().unsignedByte(SOLID_TYPE)); assertTrue(h.fm.isEmpty());
    }

    @Test void singleAllNativeBreakupSubtypesUseActualSpeedSignAndScriptOrder() {
        for (int subtype = 0x80; subtype <= 0xFF; subtype++) {
            int index = subtype & 31; if (index > 5) continue;
            for (int speed : new int[]{0, 1, -1, -32768}) {
                var h = new Harness(); h.main(subtype); var p = h.contact(); p.xv = speed; h.player = p.player(); h.run();
                boolean reverse = (subtype & 0x40) == 0 ? (subtype & 0x20) != 0
                        : (short) ((subtype & 0x20) == 0 ? speed : -speed) < 0;
                int n = COUNTS[index]; assertEquals(n, h.children().size(), "subtype " + subtype);
                for (int i = 0; i < n; i++) {
                    var c = h.children().get(i); int at = reverse ? n - 1 - i : i;
                    assertEquals(X0 - 8 * (n - 1) + at * 16, c.centreX()); assertEquals(Y0, c.centreY());
                    assertEquals(SINGLE_FRAMES[index][at], c.unsignedByte(MAP_FRAME));
                    assertEquals(9 + 12 * i, c.word(COLLAPSE_DELAY)); assertEquals(4, c.routine());
                    assertEquals(0x20000, c.longBits(COLLAPSE_FALL_DELTA)); assertEquals(MAP4, c.longBits(MAPPINGS));
                    assertEquals(0xFF, c.unsignedByte(COLLAPSE_SOLID_MARKER)); assertEquals(8, c.unsignedByte(WIDTH));
                    assertEquals(index < 2 ? 18 : 34, c.unsignedByte(Y_RADIUS));
                    assertEquals(4, c.spriteFlags()); assertEquals(0, c.flags());
                }
                assertArrayEquals(new byte[64], h.drawSeen.getFirst().copyBytes()); assertEquals(List.of(0xA3), h.fm);
                assertEquals(List.of(MAIN_TOP, MAIN_GET_OFF), h.sites());
            }
        }
    }

    @Test void multiAllNativeShapeBytesUseFiveByFourSourceCellsAndLastRowOnly() {
        for (int subtype = 0; subtype < 128; subtype += 16) {
            var h = new Harness(); h.main(subtype); h.contactPlayer(); var result = h.run();
            var children = h.children(); assertEquals(18, children.size()); int child = 0;
            boolean flipped = (subtype & 0x10) != 0;
            for (int cell = 0; cell < 20; cell++) {
                if (MULTI_FRAMES[cell] < 0) continue;
                var c = children.get(child++); int row = cell / 5, col = cell % 5;
                assertEquals(X0 + (flipped ? 32 - col * 16 : -32 + col * 16), c.centreX());
                assertEquals(Y0 + 24 - row * 16, c.centreY()); assertEquals(MULTI_FRAMES[cell], c.unsignedByte(MAP_FRAME));
                assertEquals(9 + row * 5 + col * 12, c.word(COLLAPSE_DELAY)); assertEquals(MAP3, c.longBits(MAPPINGS));
                assertEquals(flipped ? 5 : 4, c.spriteFlags()); assertEquals(flipped ? 1 : 0, c.flags());
                assertEquals(row == 3 ? 0xFF : 0, c.unsignedByte(COLLAPSE_SOLID_MARKER));
                assertEquals(row == 3 ? 8 : 0, c.unsignedByte(X_RADIUS)); assertEquals(row == 3 ? 9 : 0, c.unsignedByte(Y_RADIUS));
            }
            var found = result.effects().stream().filter(CdCollapsePlatform.SlotFound.class::isInstance)
                    .map(CdCollapsePlatform.SlotFound.class::cast).toList();
            assertEquals(20, found.size()); assertEquals(32, found.get(0).wholeIndex());
            assertEquals(32, found.get(1).wholeIndex()); assertEquals(32, found.get(2).wholeIndex());
            assertEquals(List.of(MAIN_TOP, MAIN_GET_OFF), h.sites());
        }
    }

    @Test void nativeC0ConditionalVectorsAndDirtySingleChildrenCompareEntireImages() {
        var h = new Harness(); h.main(0xC0); h.contactPlayer();
        var before = new ArrayList<CdObjectState>();
        for (int i = 32; i < 38; i++) { var s = dirty(h.pool().state(i)).moveByte(ID, 0).moveByte(SPRITE_FLAGS, 0x62); h.pool().write(s); before.add(s); }
        h.run();
        for (int i = 0; i < 6; i++) {
            byte[] e = before.get(i).copyBytes(); e[0x18] = 3; word(e, 2, 0x44BE); e[1] |= 4; putLong(e, 4, MAP4);
            putLong(e, 0x2C, 0x20000); e[0] = 0x20; e[0x24] = 4; word(e, 0x0C, Y0); e[0x3E] = (byte) 0xFF;
            e[0x17] = e[0x19] = 8; e[0x16] = 18; e[0x1A] = 0; word(e, 8, 3208 + 16 * i); word(e, 0x2A, 9 + i * 12);
            assertArrayEquals(e, h.pool().state(32 + i).copyBytes());
        }
        assertEquals(0x44, h.savedIdAtBreakSound); // Actual FM callback precedes parent deletion and reads its saved ID.
    }

    @Test void dirtyMultiChildrenKeepNonLastMarkerRadiiFractionsVelocitiesAndSavedBytes() {
        var h = new Harness(); h.main(0x10); h.contactPlayer();
        var before = new ArrayList<CdObjectState>();
        for (int i = 32; i < 50; i++) {
            if (i == WHOLE) continue;
            var s = dirty(h.pool().state(i)).moveByte(ID, 0).moveByte(COLLAPSE_SOLID_MARKER, 1); h.pool().write(s); before.add(s);
        }
        // Include the next free image after the live parent to make exactly eighteen dirty child candidates.
        var s = dirty(h.pool().state(50)).moveByte(ID, 0).moveByte(COLLAPSE_SOLID_MARKER, 1); h.pool().write(s); before.add(s);
        h.run(); int ordinal = 0;
        for (int cell = 0; cell < 20; cell++) {
            if (MULTI_FRAMES[cell] < 0) continue;
            var old = before.get(ordinal++); byte[] e = old.copyBytes(); int row = cell / 5, col = cell % 5;
            e[0x1A] = (byte) MULTI_FRAMES[cell]; e[1] |= 5; e[0x18] = 3; word(e, 2, 0x44BE); putLong(e, 4, MAP3);
            putLong(e, 0x2C, 0x20000); e[0] = 0x20; e[0x24] = 4; e[0x22] |= 1;
            if (row == 3) { e[0x3E] = (byte) 0xFF; e[0x17] = e[0x19] = 8; e[0x16] = 9; }
            word(e, 0x0C, Y0 + 24 - 16 * row); word(e, 8, X0 + 32 - 16 * col); word(e, 0x2A, 9 + row * 5 + col * 12);
            assertArrayEquals(e, h.pool().state(old.identity().wholeIndex()).copyBytes());
        }
    }

    @Test void fullPoolCapacityBranchesReachAllFourMainAndCapacitySitesInNativeFmOrder() {
        for (int subtype : new int[]{0, 0xC0}) {
            var h = new Harness(); h.main(subtype); h.fillPool(); var p = h.contact(); p.flags = 8; p.stand = WHOLE; p.charge = 1;
            h.edit(o -> o.moveByte(FLAGS, 8)); h.player = p.player(); var before = h.pool().capture(); h.run();
            var top = subtype == 0 ? MULTI_CAPACITY_TOP : SINGLE_CAPACITY_TOP;
            var off = subtype == 0 ? MULTI_CAPACITY_GET_OFF : SINGLE_CAPACITY_GET_OFF;
            assertEquals(List.of(MAIN_TOP, MAIN_GET_OFF, top, off), h.sites()); assertEquals(List.of(0xA3), h.fm);
            assertEquals(List.of("solid:MAIN_TOP", "solid:MAIN_GET_OFF", "shared:FM_CHARGESTOP", "fm:163",
                    "solid:" + top, "solid:" + off, "shared:FM_CHARGESTOP", "draw"), h.order);
            assertEquals(2, h.sharedFm.size()); assertArrayEquals(new byte[64], h.slot().copyBytes());
            for (int i = 0; i < 96; i++) if (i != WHOLE - 32) assertEquals(before.slots().get(i), h.pool().state(i + 32));
            assertEquals(4, h.calls.get(2).actualCut().slots().get(WHOLE - 32).routine());
            assertEquals(0x20, h.calls.get(2).actualCut().slots().get(WHOLE - 32).unsignedByte(COLLAPSE_SAVED_SOURCE_ID));
        }
    }

    @Test void partialCapacityRetainsTwoActualChildrenAndThenDeletesOnlyParent() {
        var h = new Harness(); h.main(0xC0); h.fillPool(); h.pool().objNull(32); h.pool().objNull(41); h.contactPlayer(); h.run();
        assertEquals(0x20, h.pool().state(32).id()); assertEquals(0x20, h.pool().state(41).id());
        assertEquals(9, h.pool().state(32).word(COLLAPSE_DELAY)); assertEquals(21, h.pool().state(41).word(COLLAPSE_DELAY));
        assertEquals(List.of(MAIN_TOP, MAIN_GET_OFF, SINGLE_CAPACITY_TOP, SINGLE_CAPACITY_GET_OFF), h.sites());
        assertArrayEquals(new byte[64], h.slot().copyBytes());
    }

    @Test void allocationPrecedesEvenNegativeOrUnavailableMultiScriptRead() {
        var data = Arrays.copyOf(SIZES1, 4); var h = new Harness(assets(data, SIZES2), new Terrain().owner());
        h.main(0); h.contactPlayer(); h.fillPool(); h.run();
        assertEquals(List.of(MAIN_TOP, MAIN_GET_OFF, MULTI_CAPACITY_TOP, MULTI_CAPACITY_GET_OFF), h.sites());
        var free = new Harness(assets(data, SIZES2), new Terrain().owner()); free.main(0); free.contactPlayer();
        var before = free.pool().state(32); var ex = assertThrows(CdCollapsePlatform.UnsupportedCollapsePlatform.class, free::run);
        assertEquals(CdCollapsePlatform.UnsupportedBoundary.MULTI_FRAME_READ, ex.boundary()); assertEquals(4, ex.byteOffset());
        assertEquals(before, free.pool().state(32)); assertEquals(4, free.slot().routine()); assertEquals(0x20, free.slot().unsignedByte(COLLAPSE_SAVED_SOURCE_ID));
        assertEquals(List.of(0xA3), free.fm); assertTrue(free.drawSeen.isEmpty());
    }

    @Test void multiMissingLaterFrameRetainsEarlierChildrenAndLeavesFoundDirtySlotUntouched() {
        var h = new Harness(assets(Arrays.copyOf(SIZES1, 8), SIZES2), new Terrain().owner()); h.main(0); h.contactPlayer();
        var waiting = dirty(h.pool().state(34)).moveByte(ID, 0); h.pool().write(waiting);
        var ex = assertThrows(CdCollapsePlatform.UnsupportedCollapsePlatform.class, h::run);
        assertEquals(CdCollapsePlatform.UnsupportedBoundary.MULTI_FRAME_READ, ex.boundary()); assertEquals(8, ex.byteOffset());
        assertEquals(0x20, h.pool().state(32).id()); assertEquals(0x20, h.pool().state(33).id()); assertEquals(waiting, h.pool().state(34));
        assertEquals(33, h.pool().state(32).word(COLLAPSE_DELAY)); assertEquals(45, h.pool().state(33).word(COLLAPSE_DELAY));
    }

    @Test void singleMissingFrameKeepsDirtyHeaderBeforeReadAndEarlierCompleteChildren() {
        var h = new Harness(assets(SIZES1, Arrays.copyOf(SIZES2, 16)), new Terrain().owner()); h.main(0xC0); h.contactPlayer();
        var waiting = dirty(h.pool().state(34)).moveByte(ID, 0); h.pool().write(waiting);
        var ex = assertThrows(CdCollapsePlatform.UnsupportedCollapsePlatform.class, h::run);
        assertEquals(CdCollapsePlatform.UnsupportedBoundary.SINGLE_FRAME_READ, ex.boundary()); assertEquals(16, ex.byteOffset());
        byte[] e = waiting.copyBytes(); e[0x18] = 3; word(e, 2, 0x44BE); e[1] |= 4; putLong(e, 4, MAP4);
        putLong(e, 0x2C, 0x20000); e[0] = 0x20; e[0x24] = 4; word(e, 0x0C, Y0); e[0x3E] = (byte) 0xFF;
        e[0x17] = e[0x19] = 8; e[0x16] = 18;
        assertArrayEquals(e, h.pool().state(34).copyBytes()); assertEquals(2, h.children().size() - 1);
        assertEquals(9, h.pool().state(32).word(COLLAPSE_DELAY)); assertEquals(21, h.pool().state(33).word(COLLAPSE_DELAY));
        assertTrue(h.drawSeen.isEmpty()); assertEquals(List.of(0xA3), h.fm);
    }

    @Test void singleReadsFrameAsRawByteRatherThanMultiSignedSkip() {
        byte[] sizes = SIZES2.clone(); sizes[14] = (byte) 0xFF;
        var h = new Harness(assets(SIZES1, sizes), new Terrain().owner()); h.main(0xC0); h.contactPlayer(); h.run();
        assertEquals(0xFF, h.pool().state(32).unsignedByte(MAP_FRAME)); assertEquals(6, h.children().size());
    }

    @Test void singleBitFourIsRetainedAtReachedBreakupDirectoryAndDoesNotFailNoContactMain() {
        var no = new Harness(); no.main(0x90); no.edit(o -> o.moveByte(SPRITE_FLAGS, 4)); no.run(); assertEquals(2, no.slot().routine());
        var yes = new Harness(); yes.main(0x90); yes.contactPlayer();
        var ex = assertThrows(CdCollapsePlatform.UnsupportedCollapsePlatform.class, yes::run);
        assertEquals(CdCollapsePlatform.NativeTable.SIZES_2, ex.table()); assertEquals(CdCollapsePlatform.UnsupportedBoundary.SIZE_HEADER_READ, ex.boundary());
        assertEquals(0x103, ex.byteOffset()); assertEquals(4, yes.slot().routine()); assertEquals(List.of(0xA3), yes.fm);
    }

    @Test void delayWordWrapAndZeroTransitionRunWithoutUnreachedInputsOrTailGlobals() {
        for (int old : new int[]{0, 1, 0xFFFF, 0x8000}) {
            var h = new Harness(); h.edit(o -> o.moveByte(ROUTINE, 4).moveWord(COLLAPSE_DELAY, old).moveByte(COLLAPSE_SOLID_MARKER, 0));
            h.body.run(WHOLE, null, null); assertEquals((old - 1) & 0xFFFF, h.slot().word(COLLAPSE_DELAY));
            assertEquals(old == 1 ? 6 : 4, h.slot().routine()); assertEquals(Y0, h.slot().centreY());
            assertTrue(h.calls.isEmpty()); assertEquals(1, h.drawSeen.size());
        }
    }

    @Test void delayAnyNonzeroMarkerAndNewZeroTopGetOffSeeRoutineSixWithoutFall() {
        for (int marker : new int[]{1, 0x7F, 0xFF}) {
            var h = new Harness(); h.main(0xC0); h.contactPlayer(); h.edit(o -> o.moveByte(ROUTINE, 4)
                    .moveWord(COLLAPSE_DELAY, 1).moveByte(COLLAPSE_SOLID_MARKER, marker).moveLong(COLLAPSE_FALL_DELTA, 0x20000));
            h.run(); assertEquals(List.of(DELAY_TOP, DELAY_GET_OFF), h.sites()); assertEquals(6, h.slot().routine());
            assertEquals(Y0, h.slot().centreY()); assertEquals(0x20000, h.slot().longBits(COLLAPSE_FALL_DELTA));
            for (var call : h.calls) assertEquals(6, call.actualCut().slots().get(WHOLE - 32).routine());
            assertTrue(h.fm.isEmpty());
        }
    }

    @Test void delayNonzeroTimerKeepsActualTopContactWithoutGetOffOrCollapseSound() {
        var h = new Harness(); h.main(0xC0); h.contactPlayer(); h.edit(o -> o.moveByte(ROUTINE, 4)
                .moveWord(COLLAPSE_DELAY, 9).moveByte(COLLAPSE_SOLID_MARKER, 0xFE)); h.run();
        assertEquals(List.of(DELAY_TOP), h.sites()); assertEquals(1, completed(h.calls.getFirst()).d0().orElseThrow());
        assertEquals(8, h.slot().word(COLLAPSE_DELAY)); assertEquals(WHOLE, h.player.standObject()); assertTrue(h.fm.isEmpty());
    }

    @Test void delayZeroTopReturnPreventsGetOffEvenOnTransitionToFall() {
        var h = new Harness(); h.main(0xC0); h.edit(o -> o.moveByte(ROUTINE, 4).moveByte(SPRITE_FLAGS, 4)
                .moveWord(COLLAPSE_DELAY, 1).moveByte(COLLAPSE_SOLID_MARKER, 1)); h.run();
        assertEquals(List.of(DELAY_TOP), h.sites()); assertEquals(0, completed(h.calls.getFirst()).d0().orElseThrow());
        assertEquals(6, h.slot().routine()); assertTrue(h.fm.isEmpty());
    }

    @Test void fallFullLongArithmeticSignedWordThresholdEqualityAndWrap() {
        int[][] cases = {{0x03101234,0x20000,0x112,0}, {0x03101234,0x20000,0x111,1},
                {0x00001234,-1,0,0}, {0x80001234,0,0,0}, {0xFFFFF000,0x20000,1,0}};
        for (int[] v : cases) {
            var h = new Harness(); h.edit(o -> o.moveByte(ROUTINE, 6).moveLong(Y_POSITION, v[0])
                    .moveLong(COLLAPSE_FALL_DELTA, v[1]).moveWord(X_VELOCITY, 0x1234).moveWord(Y_VELOCITY, 0xABCD));
            var p = new P(); p.y = v[2] << 16; h.player = p.player(); var before = h.slot(); h.run();
            if (v[3] != 0) assertArrayEquals(new byte[64], h.drawSeen.getFirst().copyBytes());
            else {
                byte[] e = before.copyBytes(); putLong(e, 0x0C, v[0] + v[1]); putLong(e, 0x2C, v[1] + 0x4000);
                assertArrayEquals(e, h.drawSeen.getFirst().copyBytes());
            }
            assertEquals(1, h.drawSeen.size()); assertTrue(h.calls.isEmpty()); assertTrue(h.fm.isEmpty());
        }
    }

    @Test void missingFallInputsRetainsMotionPrefixBeforeReachedPlayerRead() {
        var h = new Harness(); h.edit(o -> o.moveByte(ROUTINE, 6).moveLong(Y_POSITION, 0x03101234).moveLong(COLLAPSE_FALL_DELTA, -1));
        var before = h.slot(); assertThrows(NullPointerException.class, () -> h.body.run(WHOLE, null, null));
        byte[] e = before.copyBytes(); putLong(e, 0x0C, 0x03101233); putLong(e, 0x2C, 0x3FFF);
        assertArrayEquals(e, h.slot().copyBytes()); assertTrue(h.drawSeen.isEmpty());
    }

    @Test void breakupAndFallDeletionAlwaysDrawClearedSlotAndNeverClearCachedSavedId() {
        for (boolean split : new boolean[]{true, false}) {
            var h = new Harness(); h.placement.bsetSavedFlag(0x44, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
            if (split) { h.main(0xC0); h.contactPlayer(); }
            else { h.edit(o -> o.moveByte(ROUTINE, 6).moveLong(COLLAPSE_FALL_DELTA, 0x4000000)); }
            h.run(new CdCollapsePlatform.DespawnGlobals(0x81, 1, 0xD00));
            assertArrayEquals(new byte[64], h.drawSeen.getFirst().copyBytes());
            assertEquals(0x80, h.placement.savedFlag(0x44, 1)); assertArrayEquals(new byte[64], h.slot().copyBytes());
        }
    }

    @Test void wrapperReloadsPostDrawRoutineWithSignedByteTailComparison() {
        for (int raw : new int[]{0, 2, 3, 4, 6, 0x7F, 0x80, 0xFF}) {
            var h = new Harness(); h.edit(o -> o.moveByte(ROUTINE, 4).moveWord(COLLAPSE_DELAY, 7));
            h.afterDraw = (pool, slot) -> pool.write(pool.state(slot.wholeIndex()).moveByte(ROUTINE, raw));
            h.run(new CdCollapsePlatform.DespawnGlobals(0x81, 1, 0xD00));
            if ((byte) raw >= 4) assertEquals(raw, h.slot().routine()); else assertArrayEquals(new byte[64], h.slot().copyBytes());
        }
    }

    @Test void tailChunkEqualityAndRawWarpDirectionUseReachedSignedArithmeticOnly() {
        for (int x : new int[]{0xA00, 0xA80}) {
            var h = new Harness(); h.main(0xC0); h.edit(o -> o.moveByte(SPRITE_FLAGS, 4).moveWord(X, x));
            h.run(new CdCollapsePlatform.DespawnGlobals(1, 0, 0x800)); assertEquals(x == 0xA00 ? 0x20 : 0, h.slot().id());
        }
        for (int direction : new int[]{1, 0xFF, 0x80, 0x7F}) {
            var h = new Harness(); h.main(0xC0); h.edit(o -> o.moveByte(SPRITE_FLAGS, 4));
            for (int p = 0; p < 3; p++) { h.placement.bsetSavedFlag(0x44, p, CdObjectPlacementRuntime.SavedBit.LOADED);
                h.placement.bsetSavedFlag(0x44, p, CdObjectPlacementRuntime.SavedBit.MEMBER_2); }
            h.run(new CdCollapsePlatform.DespawnGlobals(0x81, direction, 0xD00));
            int difference = (short) (1 - (byte) direction); int selected = difference < 0 ? 0 : Math.min(2, difference);
            for (int p = 0; p < 3; p++) assertEquals(p == selected ? 2 : 0x82, h.placement.savedFlag(0x44, p));
        }
    }

    @Test void nativeStoppedStandPrefixCommitsSynchronouslyThenRethrowsIdenticalOriginalException() {
        for (int address : new int[]{1, 5, 31, 128, 255}) {
            var h = new Harness(); h.main(0xC0); var p = h.contact(); p.flags = 8; p.stand = address; p.yv = 0; h.player = p.player();
            var before = h.slot(); var ex = assertThrows(RuntimeException.class, h::run);
            assertEquals(1, h.calls.size()); var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, h.calls.getFirst().outcome());
            assertSame(stopped.stop().originalException(), ex); assertEquals(stopped.prefix().object(), h.slot());
            assertEquals(stopped.prefix().player(), h.player); assertEquals(stopped.prefix().globals(), h.solidGlobals);
            assertEquals(8, h.slot().flags()); assertEquals(0, h.slot().unsignedByte(SOLID_TYPE)); assertEquals(before.routine(), h.slot().routine());
            assertEquals(Set.of(SOLID_TYPE, FLAGS), stopped.prefix().objectWrites());
            assertFalse(stopped.prefix().playerWrites().contains(CdSolidObject.PlayerField.STAND_OBJECT));
            assertEquals(address, h.player.standObject()); assertTrue(h.fm.isEmpty()); assertTrue(h.drawSeen.isEmpty());
        }
    }

    @Test void realSecondFloorUnavailableRetainsOnlyReachedFirstRawAngleAndPositionMasks() {
        var t = new Terrain(); t.fg[2 + 2 * 42 + 15] = (byte) 0xF9; t.row(15, 0x2001); t.angles[1] = 3;
        Arrays.fill(t.heights, 16, 32, (byte) 1);
        var h = new Harness(assets(), t.owner()); h.main(0xC0); h.edit(o -> o.moveWord(X, 0x1000));
        var p = h.contact(); p.x = 0x10008000; p.spr = 0x44; h.player = p.player();
        var ex = assertThrows(RuntimeException.class, h::run);
        var stopped = assertInstanceOf(CdSolidObject.NativeStopped.class, h.calls.getFirst().outcome());
        assertSame(stopped.stop().originalException(), ex); var prefix = stopped.prefix();
        assertEquals(1, prefix.object().unsignedByte(SOLID_TYPE)); assertEquals(3, prefix.globals().primaryAngle());
        assertEquals(0x34, prefix.globals().secondaryAngle());
        assertEquals(Set.of(CdSolidObject.GlobalField.PRIMARY_ANGLE), prefix.globalWrites());
        assertEquals(Set.of(CdSolidObject.PlayerField.CENTRE_X, CdSolidObject.PlayerField.CENTRE_X_FRACTION,
                CdSolidObject.PlayerField.CENTRE_Y, CdSolidObject.PlayerField.CENTRE_Y_FRACTION), prefix.playerWrites());
        assertFalse(prefix.calls().stream().anyMatch(CdSolidObject.FloorDistance.class::isInstance));
        assertEquals(prefix.object(), h.slot()); assertEquals(prefix.player(), h.player); assertTrue(h.drawSeen.isEmpty());
    }

    @Test void sameValueSolidStoresStillProduceOneActualCutAndOneCommitWithoutMetadataReplay() {
        var h = new Harness(); h.main(0xC0); h.edit(o -> o.moveByte(SPRITE_FLAGS, 4).moveByte(SOLID_TYPE, 0));
        var before = h.slot(); var r = h.run(); var call = h.calls.getFirst(); var s = completed(call);
        assertEquals(before, s.object()); assertEquals(Set.of(SOLID_TYPE), s.objectWrites());
        assertEquals(before, call.actualCut().slots().get(WHOLE - 32)); assertEquals(96, call.actualCut().slots().size());
        for (int n = 0; n < 5; n++) { assertEquals(r.effects(), r.effects()); assertEquals(s, completed(call)); }
        assertEquals(1, h.calls.size()); assertEquals(1, h.drawSeen.size()); assertTrue(h.fm.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> r.effects().clear());
    }

    @Test void omittedPublicationOrChangedUnlistedCurrentPoolImageStopsBeforeDraw() {
        var missing = new Harness(); missing.main(0xC0); missing.edit(o -> o.moveByte(SPRITE_FLAGS, 4).moveByte(SOLID_TYPE, 0x7F));
        missing.publish = false; assertThrows(IllegalStateException.class, missing::run);
        assertEquals(0x7F, missing.slot().unsignedByte(SOLID_TYPE)); assertTrue(missing.drawSeen.isEmpty());
        var unrelated = new Harness(); unrelated.main(0xC0); unrelated.edit(o -> o.moveByte(SPRITE_FLAGS, 4));
        unrelated.afterCommit = call -> unrelated.pool().write(unrelated.pool().state(91).moveByte(COLLAPSE_SAVED_SOURCE_ID, 0xA5));
        assertThrows(IllegalStateException.class, unrelated::run); assertEquals(0xA5, unrelated.pool().state(91).unsignedByte(COLLAPSE_SAVED_SOURCE_ID));
        assertTrue(unrelated.drawSeen.isEmpty());
    }

    @Test void actualOtherSlotPublicationAndAllUnchangedPoolImagesAreRequired() {
        var h = new Harness(); h.main(0xC0); h.contactPlayer(); var p = h.contact(); p.flags = 8; p.stand = 47; h.player = p.player();
        var other = dirty(h.pool().state(47)).moveByte(ID, 0).moveByte(FLAGS, 0xA9); h.pool().write(other);
        h.publishOthers = false; assertThrows(IllegalStateException.class, h::run);
        assertEquals(other, h.pool().state(47)); assertEquals(1, h.calls.size()); assertTrue(h.fm.isEmpty());
        var c = completed(h.calls.getFirst()); assertEquals(List.of(other.moveByte(FLAGS, 0xA1)), c.otherSlots());
    }

    @Test void rootContractHarnessRejectsForeignCutAndStaleExpectedFrameValuesBeforePublication() {
        var h = new Harness(); h.main(0xC0); h.edit(o -> o.moveByte(SPRITE_FLAGS, 4)); h.run(); var call = h.calls.getFirst();
        var foreign = new CdObjectPool();
        var fake = new CdCollapsePlatform.SolidCall(call.site(), call.entry(), foreign.capture(), call.expectedPlayer(), call.expectedGlobals(), call.outcome());
        assertThrows(IllegalArgumentException.class, () -> h.commit(fake));
        var stale = new CdCollapsePlatform.SolidCall(call.site(), call.entry(), call.actualCut(), call.expectedPlayer(),
                new CdSolidObject.Globals(0, 0, 0, 0), call.outcome());
        assertThrows(IllegalStateException.class, () -> h.commit(stale));
        assertThrows(IllegalArgumentException.class, () -> new CdCollapsePlatform.SolidCall(MAIN_GET_OFF,
                CdSolidObject.Entry.TOP_SOLID_OBJECT, call.actualCut(), call.expectedPlayer(), call.expectedGlobals(), call.outcome()));
    }

    @Test void rootFullCutPreflightIncludesUntouchedIdZeroDirtyTailBytes() {
        var h = new Harness(); h.main(0xC0); h.edit(o -> o.moveByte(SPRITE_FLAGS, 4)); var before = h.slot();
        h.beforeCommit = call -> h.pool().write(h.pool().state(91).moveByte(COLLAPSE_SAVED_SOURCE_ID, 0x5A));
        assertThrows(IllegalStateException.class, h::run); assertEquals(before, h.slot()); assertTrue(h.drawSeen.isEmpty());
    }

    @Test void transientGuardRejectsSolidFmAndDrawReentryWithoutASecondDispatch() {
        var h = new Harness(); h.main(0xC0); h.contactPlayer();
        h.beforeCommit = call -> assertThrows(IllegalStateException.class, () -> h.body.run(WHOLE, world(), h.inputs()));
        h.beforeFm = sound -> assertThrows(IllegalStateException.class, () -> h.body.run(WHOLE, world(), h.inputs()));
        h.afterDraw = (pool, slot) -> assertThrows(IllegalStateException.class, () -> h.body.run(WHOLE, world(), h.inputs()));
        h.run(); assertEquals(2, h.calls.size()); assertEquals(List.of(0xA3), h.fm); assertEquals(1, h.drawSeen.size());
    }

    @Test void hostCommitFmAndDrawFailuresRetainOnlyTheAlreadyPublishedSourcePrefix() {
        var first = new Harness(); first.main(0xC0); first.contactPlayer(); var stop = new IllegalStateException("host commit");
        first.afterCommit = call -> { throw stop; }; assertSame(stop, assertThrows(IllegalStateException.class, first::run));
        assertEquals(2, first.slot().routine()); assertEquals(8, first.slot().flags()); assertTrue(first.fm.isEmpty());
        var sound = new Harness(); sound.main(0xC0); sound.contactPlayer();
        sound.beforeFm = id -> { throw stop; }; assertSame(stop, assertThrows(IllegalStateException.class, sound::run));
        assertEquals(2, sound.slot().routine()); assertEquals(List.of(MAIN_TOP, MAIN_GET_OFF), sound.sites()); assertTrue(sound.fm.isEmpty());
        var draw = new Harness(); draw.edit(o -> o.moveByte(ROUTINE, 4).moveWord(COLLAPSE_DELAY, 1));
        draw.afterDraw = (pool, slot) -> { pool.write(pool.state(slot.wholeIndex()).moveByte(COLLISION_STATUS, 0x71)); throw stop; };
        assertSame(stop, assertThrows(IllegalStateException.class, draw::run)); assertEquals(6, draw.slot().routine()); assertEquals(0x71, draw.slot().unsignedByte(COLLISION_STATUS));
    }

    @Test void physicalAscendingWalkKeepsEarlierChildDelayAndVisitsLaterChildInSamePass() {
        var h = new Harness(); h.main(0xC0); h.fillPool(); h.pool().objNull(32); h.pool().objNull(41); h.contactPlayer();
        for (int whole = 32; whole < 128; whole++) {
            if (h.pool().state(whole).id() == 0x20) h.body.run(whole, world(), h.inputs());
        }
        assertEquals(9, h.pool().state(32).word(COLLAPSE_DELAY)); assertEquals(20, h.pool().state(41).word(COLLAPSE_DELAY));
        assertEquals(4, h.pool().state(41).routine()); assertEquals(0, h.slot().id());
    }

    @Test void ingressWidthsWrongIdAndNullAssetFactoryFailWithoutForgedAuthority() {
        assertThrows(IllegalArgumentException.class, () -> new CdCollapsePlatform.DespawnGlobals(256, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdCollapsePlatform.DespawnGlobals(1, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CdCollapsePlatform.DespawnGlobals(1, 0, 65536));
        assertDoesNotThrow(() -> new CdCollapsePlatform.DespawnGlobals(0xFF, 0xFF, 0xFFFF));
        assertThrows(NullPointerException.class, () -> CdCollapsePlatform.Assets.fromObjectAssets(null));
        var h = new Harness(); h.edit(o -> o.moveByte(ID, 0x21)); var before = h.pool().capture();
        assertThrows(IllegalArgumentException.class, h::run); assertPool(before, h.pool());
        assertThrows(IllegalArgumentException.class, () -> h.pool().write(new CdObjectPool().state(WHOLE)));
    }

    private static CdCollapsePlatform.DespawnGlobals world() { return new CdCollapsePlatform.DespawnGlobals(1, 0, 0xD00); }
    private static UnaryOperator<CdObjectState> withSubtype(int subtype) { return o -> o.moveByte(SUBTYPE, subtype); }
    private static int subtypeValue(int index, CdObjectState ignored, int ignored2) { return index; }
    private static CdSolidObject.Result completed(CdCollapsePlatform.SolidCall c) {
        return assertInstanceOf(CdSolidObject.Completed.class, c.outcome()).completedResult();
    }
    private static CdCollapsePlatform.Assets assets() { return assets(SIZES1, SIZES2); }
    private static CdCollapsePlatform.Assets assets(byte[] sizes1, byte[] sizes2) {
        return new CdCollapsePlatform.Assets(visual(1), visual(6), visual(7), visual(6), CdAssetBytes.copyOf(sizes1),
                CdAssetBytes.copyOf(sizes2), MAP1, MAP2, MAP3, MAP4);
    }
    private static CdObjectVisualAssets visual(int frames) {
        byte[] bytes = new byte[2 * frames + 2]; for (int i = 0; i < frames; i++) word(bytes, 2 * i, 2 * frames);
        return CdObjectVisualAssets.decodeStatic(bytes, frames, 57);
    }
    /** Public typed fields only: opaque gaps retain zero, never an inaccessible nonzero claim. */
    private static CdObjectState dirty(CdObjectState s) {
        for (var f : CdObjectState.ByteField.values()) s = s.moveByte(f, 0x53 + 7 * f.byteOffset());
        for (var f : CdObjectState.WordField.values()) s = s.moveWord(f, 0xA153 + 0x103 * f.byteOffset());
        for (var f : CdObjectState.LongField.values()) s = s.moveLong(f, 0xA1536729 + 0x01030507 * f.byteOffset());
        return s;
    }
    private static void assertPool(CdObjectPool.Snapshot before, CdObjectPool pool) {
        for (var s : before.slots()) assertEquals(s, pool.state(s.identity().wholeIndex()));
    }
    private static void word(byte[] b, int at, int value) { b[at] = (byte) (value >>> 8); b[at + 1] = (byte) value; }
    private static void putLong(byte[] b, int at, int value) { word(b, at, value >>> 16); word(b, at + 2, value); }
    private static CdObjectPlacements layout() {
        var b = ByteBuffer.allocate(34); b.putShort((short) 12).putShort((short) 28).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        b.putShort((short) X0).putShort((short) Y0).put((byte) 0xA0).put((byte) 0xC0).put((byte) 0x40).put((byte) 0);
        b.putShort((short) 0xFFFF).putInt(0).putShort((short) 0); b.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(b.array());
    }

    private static final class Harness {
        final CdObjectPlacementRuntime placement = new CdObjectPlacementRuntime(layout());
        final List<CdCollapsePlatform.SolidCall> calls = new ArrayList<>();
        final List<CdObjectState> drawSeen = new ArrayList<>();
        final List<Integer> fm = new ArrayList<>(); final List<CdPlayerTouchResponse.FmSound> sharedFm = new ArrayList<>();
        final List<String> order = new ArrayList<>(); final CdCollapsePlatform body;
        CdSolidObject.Player player = new P().player();
        CdSolidObject.Gates gates = new CdSolidObject.Gates(0, 0);
        CdSolidObject.Globals solidGlobals = new CdSolidObject.Globals(0, 4, 0x12, 0x34);
        Consumer<CdCollapsePlatform.SolidCall> beforeCommit = c -> {}, afterCommit = c -> {};
        Consumer<Integer> beforeFm = sound -> {};
        CdCollapsePlatform.DrawObjectCall afterDraw = (pool, slot) -> {};
        boolean publish = true, publishOthers = true; int savedIdAtBreakSound = -1;
        Harness() { this(assets(), new Terrain().owner()); }
        Harness(CdCollapsePlatform.Assets data, CdSolidObject solid) {
            edit(o -> o.moveByte(ID, 0x20).moveWord(X, X0).moveWord(Y, Y0).moveWord(X_FRACTION, 0x1234)
                    .moveWord(Y_FRACTION, 0x5678).moveByte(SUBTYPE, 0xC0).moveByte(SAVED_FLAGS_ID, 0x44));
            body = new CdCollapsePlatform(placement, data, solid, this::commit, (pool, slot) -> {
                order.add("draw"); var state = pool.state(slot.wholeIndex()); drawSeen.add(state);
                pool.write(state.moveByte(SPRITE_FLAGS, state.spriteFlags() & 0x7F)); afterDraw.drawObject(pool, slot);
            }, sound -> { beforeFm.accept(sound); savedIdAtBreakSound = slot().unsignedByte(SAVED_FLAGS_ID);
                order.add("fm:" + sound); fm.add(sound); });
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> op) { pool().write(op.apply(slot())); }
        void main(int subtype) { edit(o -> o.moveByte(ROUTINE, 2).moveByte(SUBTYPE, subtype).moveByte(SPRITE_FLAGS, 0x84)
                .moveByte(PRIORITY, 3).moveWord(TILE, 0x44BE).moveLong(MAPPINGS, subtype < 128 ? MAP1 : MAP2)
                .moveByte(WIDTH, subtype < 128 ? 40 : 48).moveByte(X_RADIUS, subtype < 128 ? 40 : 48).moveByte(Y_RADIUS, subtype < 128 ? 34 : 18)); }
        P contact() { var p = new P(); p.y = ((slot().centreY() - slot().unsignedByte(Y_RADIUS) - 19) << 16) | 0x1234; return p; }
        void contactPlayer() { player = contact().player(); }
        CdCollapsePlatform.Inputs inputs() { return new CdCollapsePlatform.Inputs(player, gates, solidGlobals); }
        CdCollapsePlatform.Result run() { return run(world()); }
        CdCollapsePlatform.Result run(CdCollapsePlatform.DespawnGlobals world) { return body.run(WHOLE, world, inputs()); }
        List<CdCollapsePlatform.Site> sites() { return calls.stream().map(CdCollapsePlatform.SolidCall::site).toList(); }
        List<CdObjectState> children() {
            var children = new ArrayList<CdObjectState>(); for (int i = 32; i < 128; i++) if (i != WHOLE && pool().state(i).id() == 0x20) children.add(pool().state(i));
            return children;
        }
        void fillPool() { for (int i = 32; i < 128; i++) if (i != WHOLE) pool().write(pool().state(i).moveByte(ID, 0x31)); }
        /** Contract harness: compare full current images and expected semantic Frame values before one publication. */
        void commit(CdCollapsePlatform.SolidCall call) {
            beforeCommit.accept(call); pool().validateRestore(call.actualCut());
            if (!player.equals(call.expectedPlayer()) || !solidGlobals.equals(call.expectedGlobals())) throw new IllegalStateException("stale expected Frame values");
            for (var s : call.actualCut().slots()) if (!s.equals(pool().state(s.identity().wholeIndex()))) throw new IllegalStateException("stale full pool image");
            CdObjectState object; List<CdObjectState> others; CdSolidObject.Player next; CdSolidObject.Globals nextGlobals;
            List<CdPlayerTouchResponse.FmSound> requests;
            if (call.outcome() instanceof CdSolidObject.Completed done) {
                var r = done.completedResult(); object = r.object(); others = r.otherSlots(); next = r.player(); nextGlobals = r.globals(); requests = r.fmRequests();
            } else {
                var stopped = (CdSolidObject.NativeStopped) call.outcome(); var p = stopped.prefix();
                object = p.object(); others = p.otherSlots(); next = p.player(); nextGlobals = p.globals(); requests = List.of();
            }
            pool().validateWrite(object); others.forEach(pool()::validateWrite);
            calls.add(call); order.add("solid:" + call.site());
            if (publish) {
                pool().write(object); if (publishOthers) others.forEach(pool()::write);
                player = next; solidGlobals = nextGlobals;
                for (var sound : requests) { sharedFm.add(sound); order.add("shared:" + sound); }
            }
            afterCommit.accept(call);
        }
    }

    private static final class P {
        int x = (X0 << 16) | 0x8000, y = 0x01001234, xv, yv = 0x100, gv, routine = 2, angle = 0x10, flags = 2;
        int spr = 4, xr = 9, yr = 19, anim, prev, jump = 1, charge, lock, stick, ctrl, pri = 0x56, sec = 0x78;
        int tile = 0x780, priority = 2, hurt, rot, id = 1, push, stand;
        CdSolidObject.Player player() {
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x, y, xv, yv, gv,
                    routine, angle, flags, spr, xr, yr, anim, prev, jump, charge, lock, stick, ctrl, pri, sec), tile,
                    priority, hurt, rot), id, push, stand);
        }
    }
    /** Existing J TestCdBoulder public-format fixture, bounded to real terrain and shared movement algorithms. */
    private static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        final byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Terrain() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; ids[2] = 2; Arrays.fill(heights, 16, 32, (byte) 16); Arrays.fill(widths, 32, 48, (byte) 16); angles[2] = (byte) 0xC0;
        }
        void row(int y, int value) { for (int x = 0; x < 16; x++) word(chunks, 512 + (y * 16 + x) * 2, value); }
        CdSolidObject owner() {
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths);
            byte[] animations = new byte[578]; for (int i = 0; i < 60; i++) word(animations, i * 2, 120); animations[122] = (byte) 0xFF;
            byte[] maps = new byte[398], dplcs = new byte[398]; for (int i = 0; i < 198; i++) { word(maps, i * 2, 396); word(dplcs, i * 2, 396); }
            var visual = CdSonicVisualAssets.decode(maps, dplcs, new byte[69472]); byte[] atan = new byte[257];
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
            var assets = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6,7,8,0x49,0x4C,(byte) 0xFF}, new byte[4], new byte[640], atan, visual);
            return new CdSolidObject(new CdPlayerMovement(terrain, assets));
        }
    }
}
