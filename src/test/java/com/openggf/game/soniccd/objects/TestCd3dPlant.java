package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAssetLoader;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.Cd3dPlant.ChildKind.*;
import static com.openggf.game.soniccd.objects.Cd3dPlant.DespawnEntry.*;
import static com.openggf.game.soniccd.objects.Cd3dPlant.NativeCall.*;
import static com.openggf.game.soniccd.objects.Cd3dPlant.UnsupportedBoundary.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before the production port; every case is UNRUN by the external author.
 * Obj3DPlant (Level/Palmtree Panic/Objects/3D Ramp/Main.asm:7-134) at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. The mapping fixture is the reviewed 38-byte USA
 * MapSpr_3DPlant include; the offsets are the reviewed Obj3DPlant_Offsets1/2 words. Nothing is keyed on a
 * fixture coordinate, frame or route; constructed assets never authenticate media.
 */
class TestCd3dPlant {
    private static final int PLANT_MAP = 0x20E020;
    /** Common row 36: X $590, Y $7C4, ID $2C, subtype 1, Present only, no saved flag. */
    private static final int ROW_X = 0x590;
    private static final int ROW_Y = 0x7C4;
    /** MapSpr_3DPlant: frame 0 (four pieces, trailing zero), frame 1 (two pieces, trailing zero). */
    private static final byte[] PLANT_MAPPINGS = bytes(
            0x00, 0x04, 0x00, 0x1A,
            4, 0xEC, 0x04, 0x00, 0x00, 0xF8, 0xF4, 0x0E, 0x00, 0x02, 0xF8, 0xFC, 0x05, 0x00, 0x0E, 0xE8,
            0x0C, 0x08, 0x00, 0x12, 0xF0, 0x00,
            2, 0xF4, 0x00, 0x00, 0x15, 0xFC, 0xFC, 0x09, 0x00, 0x16, 0xF4, 0x00);

    @Test
    void subtypeOneInitCreatesOneMainChildAndThreeSmallChildrenInFirstFreeOrder() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 1, 0);
        var result = h.run(34, far(), present(0x380));
        assertEquals(List.of(
                new Cd3dPlant.ChildCreated(MAIN_PLANT, 32, 0x610),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 33, 0x590),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 35, 0x5F0),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 36, 0x530),
                call(INIT), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), result.effects());
        assertFalse(result.deleted());

        var parent = result.state();
        assertEquals(2, parent.routine());
        assertEquals(0x2C, parent.id());
        assertEquals(0, parent.subtype(), "BCLR #0 cleared the only set bit");
        assertEquals(4, parent.spriteFlags(), "ORI.B #4; DrawObject stand-in clears bit 7");
        assertEquals(PLANT_MAP, parent.longBits(MAPPINGS));
        assertEquals(0x4424, parent.word(TILE));
        assertEquals(0x18, parent.unsignedByte(WIDTH));
        assertEquals(0x14, parent.unsignedByte(Y_RADIUS));
        assertEquals(0x5D0, parent.centreX(), "self takes Offsets1[0] = +$40");
        assertEquals(0x05D00000, parent.longBits(X_POSITION));
        assertEquals(ROW_Y, parent.centreY());
        assertEquals(ROW_X, parent.word(PLANT_ANCHOR_X), "oVar2A is the ORIGINAL X, not the moved one");
        assertEquals(0x5D0, parent.word(PLANT_BASE_X), "oVar2C is the moved X");
        assertEquals(0, parent.unsignedByte(PRIORITY), "Init never writes the parent's priority");
        assertEquals(0, parent.unsignedByte(MAP_FRAME));
        assertEquals(0, parent.unsignedByte(COLLISION_TYPE), "no colType: never selectable by Player_ObjCollide");
        assertEquals(parent, h.slot(34));
        assertEquals(List.of(parent), h.draw.observed, "DrawObject saw the committed post-Init parent only");

        var main = h.slot(32);
        assertEquals(2, main.routine(), "ADDQ.B #2 on the fresh slot's zero routine");
        assertEquals(0x2C, main.id());
        assertEquals(0x610, main.centreX(), "Offsets1[1] = +$80 from the original X");
        assertEquals(ROW_Y, main.centreY());
        assertEquals(ROW_X, main.word(PLANT_ANCHOR_X));
        assertEquals(0x610, main.word(PLANT_BASE_X));
        assertEquals(PLANT_MAP, main.longBits(MAPPINGS));
        assertEquals(0x4424, main.word(TILE));
        assertEquals(0x18, main.unsignedByte(WIDTH));
        assertEquals(0x14, main.unsignedByte(Y_RADIUS));
        assertEquals(4, main.spriteFlags());
        assertEquals(0, main.unsignedByte(PRIORITY), ".Loop children never receive a priority");
        assertEquals(0, main.subtype(), ".Loop children never receive a subtype");
        assertEquals(0, main.unsignedByte(MAP_FRAME));

        int[][] small = {{33, 0x590}, {35, 0x5F0}, {36, 0x530}};
        for (int[] expected : small) {
            var child = h.slot(expected[0]);
            assertEquals(2, child.routine(), "slot " + expected[0]);
            assertEquals(0x2C, child.id());
            assertEquals(1, child.subtype());
            assertEquals(1, child.unsignedByte(MAP_FRAME));
            assertEquals(4, child.unsignedByte(PRIORITY));
            assertEquals(expected[1], child.centreX(), "Offsets2 from the original X");
            assertEquals(ROW_Y, child.centreY());
            assertEquals(ROW_X, child.word(PLANT_ANCHOR_X));
            assertEquals(0, child.word(PLANT_BASE_X), ".Loop2 never writes oVar2C");
            assertEquals(PLANT_MAP, child.longBits(MAPPINGS));
            assertEquals(0x4424, child.word(TILE));
            assertEquals(0xC, child.unsignedByte(WIDTH));
            assertEquals(0xC, child.unsignedByte(Y_RADIUS));
            assertEquals(4, child.spriteFlags());
        }
        assertEquals(0, h.slot(37).id(), "exactly five slots are occupied");
    }

    @Test
    void subtypeZeroInitCreatesFourMainPlantsThenThreeSmallOnes() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 0, 0);
        var result = h.run(34, far(), present(0x380));
        assertEquals(List.of(
                new Cd3dPlant.ChildCreated(MAIN_PLANT, 32, 0x610),
                new Cd3dPlant.ChildCreated(MAIN_PLANT, 33, 0x550),
                new Cd3dPlant.ChildCreated(MAIN_PLANT, 35, 0x510),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 36, 0x590),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 37, 0x5F0),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 38, 0x530),
                call(INIT), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), result.effects());
        assertEquals(0x5D0, result.state().centreX());
        assertEquals(0, result.state().subtype());
        assertEquals(0x550, h.slot(33).centreX(), "Offsets1[2] = -$40 as a signed native word");
        assertEquals(0x510, h.slot(35).centreX());
        assertEquals(0x550, h.slot(33).word(PLANT_BASE_X));
        assertEquals(0, h.slot(39).id());
    }

    @ParameterizedTest(name = "subtype {0} -> parent subtype {1}, {2} main children")
    @CsvSource({"3, 2, 1", "2, 2, 3", "0x81, 0x80, 1", "0x80, 0x80, 3"})
    void bclrClearsOnlyBitZeroAndThePreviousBitSelectsTheMainCount(int subtype, int expectedSubtype, int mainChildren) {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, subtype, 0);
        var result = h.run(34, far(), present(0x380));
        assertEquals(expectedSubtype, result.state().subtype());
        long mains = result.effects().stream()
                .filter(e -> e instanceof Cd3dPlant.ChildCreated c && c.kind() == MAIN_PLANT).count();
        assertEquals((long) mainChildren, mains);
        assertEquals(3L, result.effects().stream()
                .filter(e -> e instanceof Cd3dPlant.ChildCreated c && c.kind() == SMALL_PLANT).count());
        // A parent left with a nonzero subtype takes Main's .End: no movement even on a ramp.
        var again = h.run(34, onRamp(0x6A0), present(0x380));
        assertEquals(0x5D0, again.state().centreX());
        assertEquals(List.of(call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), again.effects());
    }

    @Test
    void childWritesRetainDirtySlotBytesAndAddTwoToADirtyRoutine() {
        var h = new Harness();
        h.write(h.slot(32).moveByte(PRIORITY, 7).moveByte(SUBTYPE, 5).moveByte(MAP_FRAME, 9)
                .moveWord(X_VELOCITY, 0x1234).moveWord(PLANT_BASE_X, 0x4444).moveByte(SPRITE_FLAGS, 0x81)
                .moveWord(X_FRACTION, 0x8000));
        h.write(h.slot(33).moveByte(PRIORITY, 6).moveWord(PLANT_BASE_X, 0x3333).moveByte(MAP_FRAME, 2)
                .moveByte(ROUTINE, 2).moveByte(SUBTYPE, 9).moveWord(Y_VELOCITY, 0xFEDC));
        h.place(34, ROW_X, ROW_Y, 1, 0);
        h.run(34, far(), present(0x380));

        var main = h.slot(32);
        assertEquals(2, main.routine());
        assertEquals(7, main.unsignedByte(PRIORITY));
        assertEquals(5, main.subtype(), "a dirty subtype survives and later selects Main's .End");
        assertEquals(9, main.unsignedByte(MAP_FRAME));
        assertEquals(0x1234, main.word(X_VELOCITY));
        assertEquals(0x610, main.word(PLANT_BASE_X), "oVar2C is rewritten from the moved X");
        assertEquals(0x85, main.spriteFlags(), "ORI.B #4 keeps dirty bits including bit 7");
        assertEquals(0x06108000, main.longBits(X_POSITION), "MOVE.W writes only the integer word");

        var small = h.slot(33);
        assertEquals(4, small.routine(), "ADDQ.B #2 on the dirty routine 2; that child later stops at ROUTINE_DISPATCH natively");
        assertEquals(4, small.unsignedByte(PRIORITY), ".Loop2 writes priority 4");
        assertEquals(1, small.subtype());
        assertEquals(1, small.unsignedByte(MAP_FRAME));
        assertEquals(0x3333, small.word(PLANT_BASE_X), ".Loop2 never writes oVar2C");
        assertEquals(0xFEDC, small.word(Y_VELOCITY));

        var mainAgain = h.run(32, onRamp(0x6A0), present(0x380));
        assertEquals(0x610, mainAgain.state().centreX(), "dirty subtype 5 is nonzero: TST.B oSubtype / BNE .End");
    }

    @Test
    void firstFreeAllocationSkipsOccupiedSlotsIncludingThoseAfterTheParent() {
        var h = new Harness();
        h.write(h.slot(32).moveByte(ID, 0x10));
        h.write(h.slot(33).moveByte(ID, 0x10));
        h.write(h.slot(36).moveByte(ID, 0x3D));
        h.place(34, ROW_X, ROW_Y, 1, 0);
        var result = h.run(34, far(), present(0x380));
        assertEquals(List.of(
                new Cd3dPlant.ChildCreated(MAIN_PLANT, 35, 0x610),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 37, 0x590),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 38, 0x5F0),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 39, 0x530),
                call(INIT), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), result.effects());
        assertEquals(0x10, h.slot(32).id());
        assertEquals(0x10, h.slot(33).id());
        assertEquals(0x3D, h.slot(36).id());
    }

    @Test
    void poolExhaustionStopsAtTheNativeOutOfPoolWriteAfterCommittingEarlierWrites() {
        var h = new Harness();
        for (int whole = 32; whole < 128; whole++) {
            if (whole != 34) { h.write(h.slot(whole).moveByte(ID, 0x10)); }
        }
        h.place(34, ROW_X, ROW_Y, 1, 0);
        var failure = assertThrows(Cd3dPlant.UnsupportedPlant.class, () -> h.run(34, far(), present(0x380)));
        assertEquals(POOL_EXHAUSTED, failure.boundary());
        assertEquals(34, failure.wholeIndex());
        var parent = h.slot(34);
        assertEquals(2, parent.routine(), "the parent's own .Init iteration was committed before FindObjSlot failed");
        assertEquals(0x5D0, parent.centreX());
        assertEquals(0x5D0, parent.word(PLANT_BASE_X));
        assertEquals(ROW_X, parent.word(PLANT_ANCHOR_X));
        assertEquals(0, parent.subtype());
        assertEquals(PLANT_MAP, parent.longBits(MAPPINGS));
        assertEquals(0x4424, parent.word(TILE));
        for (int whole = 32; whole < 128; whole++) {
            if (whole != 34) { assertEquals(0x10, h.slot(whole).id(), "slot " + whole + " untouched"); }
        }
        assertTrue(h.draw.observed.isEmpty(), "the prologue's DrawObject is never reached");

        var partial = new Harness();
        for (int whole = 32; whole < 128; whole++) {
            if (whole != 34 && whole != 40) { partial.write(partial.slot(whole).moveByte(ID, 0x10)); }
        }
        partial.place(34, ROW_X, ROW_Y, 1, 0);
        var second = assertThrows(Cd3dPlant.UnsupportedPlant.class, () -> partial.run(34, far(), present(0x380)));
        assertEquals(POOL_EXHAUSTED, second.boundary());
        assertEquals(0x2C, partial.slot(40).id(), "the one free slot received the main child before the first small child failed");
        assertEquals(0x610, partial.slot(40).centreX());
        assertEquals(0, partial.slot(40).subtype());
    }

    @Test
    void mainIsStaticWhenThePlayerIsNotOnARamp() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 1, 0);
        h.run(34, far(), present(0x380));
        h.draw.observed.clear();
        var result = h.run(34, new Cd3dPlant.Player(0x6A0, 0), present(0x380));
        assertEquals(List.of(call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), result.effects());
        assertEquals(0x5D0, result.state().centreX(), "oX := oVar2C + 0");
        assertEquals(List.of(result.state()), h.draw.observed);
        var withOtherBits = h.run(34, new Cd3dPlant.Player(0x6A0, 0xFD), present(0x380));
        assertEquals(0x5D0, withOtherBits.state().centreX(), "only oPlayerCtrl bit 1 is tested");
    }

    @ParameterizedTest(name = "player X {0} -> plant X {1}")
    @CsvSource({
            "0x6A0, 0x620",  // low byte $A0 below $C0: half, added
            "0x5E8, 0x62F",  // $E8 in [$C0,$F0): $BF, half = $5F
            "0x5F5, 0x5D0",  // $F5 at or above $F0: zero
            "0x590, 0x618",  // equal to the anchor: BCC takes the right-hand branch
            "0x58F, 0x598",  // left of the anchor: $FF-$8F=$70, half $38, negated
            "0x540, 0x571",  // left: $FF-$40=$BF, half $5F, negated
            "0x500, 0x5D0",  // left: $FF-$00=$FF capped to zero
            "0x5CE, 0x62F"   // low byte $CE in [$C0,$F0): $BF
    })
    void mainShiftsMainPlantsByThePlayerChunkPositionRelativeToTheAnchor(String playerHex, String expectedHex) {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 1, 0);
        h.run(34, far(), present(0x380));
        int playerX = Integer.decode(playerHex);
        int expected = Integer.decode(expectedHex);
        var result = h.run(34, onRamp(playerX), present(0x380));
        assertEquals(expected, result.state().centreX());
        var repeated = h.run(34, onRamp(playerX), present(0x380));
        assertEquals(expected, repeated.state().centreX(), "the shift is from oVar2C, never cumulative");
        assertEquals(0x5D0, repeated.state().word(PLANT_BASE_X));
        assertEquals(ROW_X, repeated.state().word(PLANT_ANCHOR_X));
        var main = h.run(32, onRamp(playerX), present(0x380));
        assertEquals(expected + 0x40, main.state().centreX(), "the main child shifts from its own base");
    }

    @Test
    void smallPlantsNeverMove() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 1, 0);
        h.run(34, far(), present(0x380));
        for (int whole : new int[]{33, 35, 36}) {
            int before = h.slot(whole).centreX();
            var result = h.run(whole, onRamp(0x6A0), present(0x380));
            assertEquals(List.of(call(MAIN), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), result.effects());
            assertEquals(before, result.state().centreX(), "slot " + whole);
        }
    }

    @Test
    void anchorDespawnDeletesEachFamilyMemberOnItsOwnDispatch() {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 1, 0);
        h.run(34, far(), present(0x380)); // $580-$300 = $280 keeps
        assertEquals(0x2C, h.slot(34).id());
        var small = h.run(36, far(), present(0x300)); // $580-$280 = $300 despawns, whatever the child's own X
        assertEquals(List.of(call(MAIN), call(DRAW_OBJECT), new Cd3dPlant.Despawn(ANCHOR_OFFSCREEN, 0, -1),
                call(CHECK_OBJ_DESPAWN2)), small.effects());
        assertTrue(small.deleted());
        assertArrayEquals(new byte[64], h.slot(36).copyBytes());
        assertEquals(0x2C, h.slot(32).id(), "siblings wait for their own dispatch");
        assertEquals(0x2C, h.slot(34).id());
        var parent = h.run(34, far(), present(0x300));
        assertTrue(parent.deleted());
        assertEquals(0, h.slot(34).id());

        var fresh = new Harness();
        fresh.place(34, ROW_X, ROW_Y, 1, 0);
        var firstFrame = fresh.run(34, far(), present(0x300));
        assertEquals(List.of(
                new Cd3dPlant.ChildCreated(MAIN_PLANT, 32, 0x610),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 33, 0x590),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 35, 0x5F0),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 36, 0x530),
                call(INIT), call(DRAW_OBJECT), new Cd3dPlant.Despawn(ANCHOR_OFFSCREEN, 0, -1),
                call(CHECK_OBJ_DESPAWN2)), firstFrame.effects(), "Init, DrawObject, then the parent despawns in its own first dispatch");
        assertEquals(0, fresh.slot(34).id());
        assertEquals(0x2C, fresh.slot(32).id(), "children created this pass remain until visited");

        var onscreen = new Harness();
        onscreen.draw.mode = RecordingDraw.Mode.SET_BIT7;
        onscreen.place(34, ROW_X, ROW_Y, 1, 0);
        var kept = onscreen.run(34, far(), present(0x900));
        assertFalse(kept.deleted(), "bit 7 after the build skips the window test");
        var warped = onscreen.run(34, far(), new Cd3dPlant.Globals(0x81, 1, 0x380));
        assertEquals(List.of(call(MAIN), call(DRAW_OBJECT), new Cd3dPlant.Despawn(TIME_WARP, 0, -1),
                call(CHECK_OBJ_DESPAWN2)), warped.effects());
    }

    @Test
    void dirtySavedIdClearsTheOldPeriodBankBitOnDespawn() {
        var h = new Harness();
        for (int period = 0; period < 3; period++) { h.runtime.bsetSavedFlag(9, period, CdObjectPlacementRuntime.SavedBit.LOADED); }
        h.write(h.slot(34).moveByte(SAVED_FLAGS_ID, 9));
        h.place(34, ROW_X, ROW_Y, 1, 0);
        var result = h.run(34, far(), new Cd3dPlant.Globals(1, 0, 0x300));
        assertTrue(result.effects().contains(new Cd3dPlant.Despawn(ANCHOR_OFFSCREEN, 9, 1)));
        assertEquals(0, h.runtime.savedFlag(9, 1) & 0x80);
        assertEquals(0x80, h.runtime.savedFlag(9, 0) & 0x80);
        assertEquals(0x80, h.runtime.savedFlag(9, 2) & 0x80);
    }

    @ParameterizedTest(name = "routine {0}")
    @CsvSource({"4", "1", "0xFE", "0x80"})
    void routineOutsideTheTwoEntryTableStopsBeforeAnyWrite(String routineHex) {
        var h = new Harness();
        h.place(34, ROW_X, ROW_Y, 1, 0);
        h.write(h.slot(34).moveByte(ROUTINE, Integer.decode(routineHex)));
        var before = h.slot(34);
        var failure = assertThrows(Cd3dPlant.UnsupportedPlant.class, () -> h.run(34, far(), present(0x380)));
        assertEquals(ROUTINE_DISPATCH, failure.boundary());
        assertEquals(34, failure.wholeIndex());
        assertEquals(before, h.slot(34));
        assertEquals(0, h.slot(32).id(), "no child allocation");
        assertTrue(h.draw.observed.isEmpty());
    }

    @Test
    void wrongSlotIdAndInvalidInputsAreRejectedBeforeAnyWrite() {
        var h = new Harness();
        h.write(h.slot(34).moveByte(ID, 0x3D));
        assertThrows(IllegalArgumentException.class, () -> h.run(34, far(), present(0)));
        assertEquals(0x3D, h.slot(34).id());
        assertThrows(IllegalArgumentException.class, () -> new Cd3dPlant.Player(0x10000, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dPlant.Player(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dPlant.Player(0, 0x100));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dPlant.Globals(3, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dPlant.Globals(1, 0x100, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dPlant.Globals(1, 0, 0x10000));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dPlant.ChildCreated(MAIN_PLANT, 31, 0));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dPlant.ChildCreated(MAIN_PLANT, 32, 0x10000));
        assertThrows(IllegalArgumentException.class, () -> new Cd3dPlant.Despawn(ANCHOR_OFFSCREEN, 0, 1));
        assertThrows(NullPointerException.class, () -> h.plant.run(34, null, present(0)));
        assertThrows(NullPointerException.class, () -> h.plant.run(34, far(), null));
        var onePiece = bytes(0x00, 0x02, 1, 0x00, 0x00, 0x00, 0x00, 0x00);
        assertThrows(IllegalArgumentException.class, () -> new Cd3dPlant.Assets(
                CdObjectVisualAssets.decodeStatic(onePiece, 1, 28), PLANT_MAP, offsets()));
        assertThrows(NullPointerException.class, () -> new Cd3dPlant.Assets(null, PLANT_MAP, offsets()));
        assertThrows(NullPointerException.class, () -> new Cd3dPlant.Assets(plantVisual(), PLANT_MAP, null));
        assertThrows(NullPointerException.class, () -> new Cd3dPlant(null, assets(), new RecordingDraw()));
        assertThrows(NullPointerException.class, () -> new Cd3dPlant(h.runtime, null, new RecordingDraw()));
        assertThrows(NullPointerException.class, () -> new Cd3dPlant(h.runtime, assets(), null));
    }

    @Test
    void offsetsComeFromTheAssetsRecordNotFromLiterals() {
        var custom = new Cd3dPlant.Assets(plantVisual(), 0x20E020,
                new CdR11aObjectAssets.PlantOffsets(List.of(0x10, 0x20, -0x10, -0x20), List.of(1, 2, -3)));
        var runtime = new CdObjectPlacementRuntime(layout(row(ROW_X, ROW_Y, 0x2C, 0x00, 0x40, 0)));
        var draw = new RecordingDraw();
        var plant = new Cd3dPlant(runtime, custom, draw);
        var state = runtime.pool().state(34).moveWord(X, ROW_X).moveWord(Y, ROW_Y).moveByte(ID, 0x2C);
        runtime.pool().write(state);
        var result = plant.run(34, far(), present(0x380));
        assertEquals(0x5A0, result.state().centreX());
        assertEquals(List.of(
                new Cd3dPlant.ChildCreated(MAIN_PLANT, 32, 0x5B0),
                new Cd3dPlant.ChildCreated(MAIN_PLANT, 33, 0x580),
                new Cd3dPlant.ChildCreated(MAIN_PLANT, 35, 0x570),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 36, 0x591),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 37, 0x592),
                new Cd3dPlant.ChildCreated(SMALL_PLANT, 38, 0x58D),
                call(INIT), call(DRAW_OBJECT), call(CHECK_OBJ_DESPAWN2)), result.effects());
    }

    @Test
    void storageAliasesOverlapTheSharedObjectTail() {
        assertEquals(0x2A, PLANT_ANCHOR_X.byteOffset());
        assertEquals(0x2C, PLANT_BASE_X.byteOffset());
        assertEquals(MOSQUI_ANCHOR_X.byteOffset(), PLANT_ANCHOR_X.byteOffset(), "same oVar2A storage, different owner");
        assertEquals(0x2C, Cd3dPlant.OBJECT_ID);
        assertEquals(0x4424, Cd3dPlant.NATIVE_TILE_WORD);
    }

    @Test
    void authenticatedUsaAssetsBindThePlantMappingAddressAndOffsets() throws Exception {
        var objects = loadUsa().openingObjects();
        var assets = Cd3dPlant.Assets.fromObjectAssets(objects);
        assertEquals(PLANT_MAP, assets.mappingsAddress());
        assertEquals(2, assets.plant().mappingFrames().size());
        assertTrue(assets.plant().animations().isEmpty(), "the plant has no script table");
        assertEquals(List.of(0x40, 0x80, -0x40, -0x80), assets.offsets().main());
        assertEquals(List.of(0, 0x60, -0x60), assets.offsets().small());
        assertArrayEquals(PLANT_MAPPINGS, assets.plant().mappings().copy(), "the fixture is the authenticated include");
    }

    private static Cd3dPlant.Effect call(Cd3dPlant.NativeCall nativeCall) { return new Cd3dPlant.CallReturned(nativeCall); }
    private static Cd3dPlant.Player far() { return new Cd3dPlant.Player(0x60, 0); }
    private static Cd3dPlant.Player onRamp(int playerX) { return new Cd3dPlant.Player(playerX, 2); }
    private static Cd3dPlant.Globals present(int cameraX) { return new Cd3dPlant.Globals(1, 0, cameraX); }
    private static CdObjectVisualAssets plantVisual() { return CdObjectVisualAssets.decodeStatic(PLANT_MAPPINGS, 2, 28); }
    private static CdR11aObjectAssets.PlantOffsets offsets() {
        return new CdR11aObjectAssets.PlantOffsets(List.of(0x40, 0x80, -0x40, -0x80), List.of(0, 0x60, -0x60));
    }
    private static Cd3dPlant.Assets assets() { return new Cd3dPlant.Assets(plantVisual(), PLANT_MAP, offsets()); }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }

    private static int[] row(int x, int y, int id, int subtype, int periods, int subtype2) {
        return new int[]{x, y, id, subtype, periods, subtype2};
    }

    private static CdObjectPlacements layout(int[]... records) {
        int nullAt = 12 + 8 * (records.length + 1);
        var bytes = ByteBuffer.allocate(nullAt + 6);
        bytes.putShort((short) 12).putShort((short) nullAt).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        for (int[] r : records) {
            bytes.putShort((short) r[0]).putShort((short) r[1]).put((byte) r[2]).put((byte) r[3])
                    .put((byte) r[4]).put((byte) r[5]);
        }
        bytes.putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        bytes.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(bytes.array());
    }

    private static CdR11aAssets loadUsa() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for supplied-media checks");
        Path cue;
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "USA needs exactly one actual CUE");
            cue = cues.getFirst();
        }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            return CdR11aAssetLoader.load(source, CdReleaseVariant.USA, CdStageKey.parse("R11A"));
        }
    }

    /** Native DrawObject stand-in over the live pool; the shared queue is a separate owner. */
    private static final class RecordingDraw implements Cd3dPlant.DrawObjectCall {
        enum Mode { CLEAR_BIT7, SET_BIT7, UNTOUCHED }
        Mode mode = Mode.CLEAR_BIT7;
        final List<CdObjectState> observed = new ArrayList<>();

        @Override public void drawObject(CdObjectPool pool, CdObjectState.Identity slot) {
            var live = pool.state(slot.wholeIndex());
            observed.add(live);
            switch (mode) {
                case CLEAR_BIT7 -> pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() & 0x7F));
                case SET_BIT7 -> pool.write(live.moveByte(SPRITE_FLAGS, live.spriteFlags() | 0x80));
                case UNTOUCHED -> { }
            }
        }
    }

    private static final class Harness {
        final CdObjectPlacementRuntime runtime = new CdObjectPlacementRuntime(layout(row(ROW_X, ROW_Y, 0x2C, 0x01, 0x40, 0)));
        final RecordingDraw draw = new RecordingDraw();
        final Cd3dPlant plant = new Cd3dPlant(runtime, assets(), draw);

        CdObjectPool pool() { return runtime.pool(); }
        CdObjectState slot(int whole) { return pool().state(whole); }
        void write(CdObjectState state) { pool().write(state); }
        Cd3dPlant.Result run(int whole, Cd3dPlant.Player player, Cd3dPlant.Globals globals) {
            return plant.run(whole, player, globals);
        }

        /** SpawnObject's writes only (Object Spawner.asm:267-319); unflagged records write no saved ID. */
        void place(int whole, int x, int packedY, int subtype, int subtype2) {
            int rotated = ((packedY << 2) | (packedY >>> 14)) & 0xFFFF;
            int flips = rotated & 3;
            var state = slot(whole).moveWord(X, x).moveWord(Y, packedY & 0xFFF)
                    .moveByte(SPRITE_FLAGS, flips).moveByte(FLAGS, flips);
            write(state.moveByte(ID, 0x2C).moveByte(SUBTYPE, subtype).moveByte(SUBTYPE2, subtype2));
        }
    }
}
