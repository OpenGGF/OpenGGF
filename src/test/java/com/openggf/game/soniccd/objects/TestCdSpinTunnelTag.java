package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.player.CdPlayerMotionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored and hashed BEFORE production; all Java remains UNRUN by this external author.
 * Literal expectations from scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Level/_Objects/Spin Tunnel/Main (Tag).asm:7-188 and Object Spawner.asm:385-446.
 * No shared gameplay model computes expected values. Complete 20-field records and all 64 object bytes
 * are compared. Fixtures dirty every available storage alias; unnamed gaps retain the owner's zeros.
 * These tests exercise pure source cuts, not shared DrawObject, storage publication or integration.
 */
class TestCdSpinTunnelTag {
    private static final int MAP = 0x00CD1000; // Explicit constructed pointer, never a production operand claim.
    private static final CdSpinTunnelTag TAG = new CdSpinTunnelTag(new CdSpinTunnelTag.Assets(MAP));
    private static final CdSpinTunnelTag.Gates GATES = new CdSpinTunnelTag.Gates(0, 0, 0, 0);
    private static final Set<CdSpinTunnelTag.PlayerField> ROLL = Set.of(
            CdSpinTunnelTag.PlayerField.FLAGS, CdSpinTunnelTag.PlayerField.Y_RADIUS,
            CdSpinTunnelTag.PlayerField.X_RADIUS, CdSpinTunnelTag.PlayerField.CENTRE_Y_WORD,
            CdSpinTunnelTag.PlayerField.ANIMATION);

    @Test void exactEightFieldsAndExplicitPointerBounds() {
        assertEquals(Set.of("CENTRE_Y_WORD", "X_VELOCITY", "Y_VELOCITY", "GROUND_VELOCITY",
                "FLAGS", "Y_RADIUS", "X_RADIUS", "ANIMATION"),
                Set.copyOf(Arrays.stream(CdSpinTunnelTag.PlayerField.values()).map(Enum::name).toList()));
        assertEquals(8, CdSpinTunnelTag.PlayerField.values().length);
        assertEquals(MAP, new CdSpinTunnelTag.Assets(MAP).mappingsAddress());
        assertEquals(2, new CdSpinTunnelTag.Assets(2).mappingsAddress());
        assertEquals(0xFFFEEE, new CdSpinTunnelTag.Assets(0xFFFEEE).mappingsAddress());
        for (int bad : new int[]{-1, 0, 1, 3, 0xFFFEEF, 0xFFFF00, 0x1000000}) {
            assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.Assets(bad));
        }
        assertThrows(NullPointerException.class, () -> new CdSpinTunnelTag(null));
        assertThrows(NullPointerException.class, () -> CdSpinTunnelTag.Assets.fromObjectAssets(null));
    }

    @Test void initFallsThroughAndPreservesEveryOtherObjectByteAndEveryUnselectedMotionField() {
        var f = fixture(0, 0); var before = f.object(); var p = new P(); p.xv = 0;
        var motion = p.motion(); var beforeBytes = before.copyBytes();
        var r = TAG.step(before, motion, GATES);
        byte[] expectedBytes = beforeBytes.clone();
        expectedBytes[0x24] = 2; expectedBytes[1] = (byte) 0x85;
        expectedBytes[2] = 5; expectedBytes[3] = 0x44;
        expectedBytes[4] = 0; expectedBytes[5] = (byte) 0xCD;
        expectedBytes[6] = 0x10; expectedBytes[7] = 0; expectedBytes[0x1A] = 1;
        assertArrayEquals(expectedBytes, r.object().copyBytes());
        p.xv = 0xA00; p.gv = 0xA00; firstRoll(p, 0xED, 0x1005BEEF);
        assertResult(r, before, motion, p.motion(), writes(true, false, true), false);
        assertArrayEquals(beforeBytes, before.copyBytes());
        assertEquals(before, f.pool().state(41), "staged Init/player writes never publish to the pool");
        assertEquals(before.identity(), r.object().identity());
    }

    @ParameterizedTest
    @CsvSource({"0,1", "1,2", "2,3", "3,4", "4,5", "5,6", "11,12", "12,13",
            "127,128", "128,129", "254,255", "255,0"})
    void initFrameUsesTheFullSubtypeByteWithoutAGameplayMappingFence(int subtype, int frame) {
        var before = fixture(0, subtype).object(); var p = new P(); p.yv = 0;
        var r = TAG.step(before, p.motion(), new CdSpinTunnelTag.Gates(0, 0, 0, 8));
        assertEquals(frame, r.object().unsignedByte(MAP_FRAME));
        assertEquals(2, r.object().routine()); assertEquals(MAP, r.object().longBits(MAPPINGS));
        if (subtype == 255) {
            p.xv = 0xD00; p.gv = 0xD00; firstRoll(p, 0xEF, 0x1005BEEF);
            assertEquals(p.motion(), r.player().finalMotion());
            assertEquals(writes(true, false, true), r.player().executedWrites());
        }
    }

    @Test void mainHasNoObjectStoresEvenForPoisonedNonInitFields() {
        var before = fixture(2, 0).object(); var p = new P();
        var r = TAG.step(before, p.motion(), GATES);
        assertSame(before, r.object()); assertArrayEquals(before.copyBytes(), r.object().copyBytes());
    }

    @ParameterizedTest @ValueSource(ints = {1, 3, 4, 5, 6, 127, 128, 254, 255})
    void unownedRoutineStopsAtTypedDispatchBeforeAnyWritesOrPlayerGate(int routine) {
        var f = fixture(routine, 0); var before = f.object(); var p = new P(); p.anim = 0x2B;
        byte[] bytes = before.copyBytes();
        var e = assertThrows(CdSpinTunnelTag.UnsupportedSpinTunnelTag.class,
                () -> TAG.step(before, p.motion(), new CdSpinTunnelTag.Gates(0, 0x100, 0x100, 8)));
        assertEquals(CdSpinTunnelTag.UnsupportedBoundary.ROUTINE_DISPATCH, e.boundary());
        assertEquals(routine, e.value()); assertEquals(41, e.wholeIndex());
        assertArrayEquals(bytes, before.copyBytes()); assertEquals(before, f.pool().state(41));
    }

    @Test void foreignObjectIdIsRejectedWithoutPublishing() {
        var f = fixture(0, 0); var other = f.object().moveByte(ID, 0x35);
        assertThrows(IllegalArgumentException.class, () -> TAG.step(other, new P().motion(), GATES));
        assertEquals(f.object(), f.pool().state(41));
    }

    @ParameterizedTest @CsvSource({"43,2", "26,6", "26,128", "26,255"})
    void earlyPlayerGatesStillReturnInitAndTheIndependentDrawDecision(int animation, int routine) {
        var before = fixture(0, 0).object(); var p = new P(); p.anim = animation; p.routine = routine;
        var motion = p.motion(); var r = TAG.step(before, motion, new CdSpinTunnelTag.Gates(0, 0, 0x100, 0));
        assertEquals(2, r.object().routine()); assertEquals(0x544, r.object().word(TILE));
        assertResult(r, before, motion, motion, Set.of(), true);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void everyUnsignedPlayerRoutineBelowSixIsEligibleDespiteOtherPoisonedControls(int routine) {
        var before = fixture(2, 0).object(); var p = new P(); p.routine = routine;
        var motion = p.motion(); var r = TAG.step(before, motion, GATES);
        p.xv = 0x1234; p.gv = 0x1234; firstRoll(p, 0xED, 0x1005BEEF);
        assertResult(r, before, motion, p.motion(), writes(true, false, true), false);
    }

    @ParameterizedTest
    @CsvSource({"0,0,true,false", "1,1,true,false", "256,0,false,false",
            "0,256,true,true", "256,256,false,true", "257,257,false,true", "1,257,true,true"})
    void debugTestsReadTheHighByteOfEachIndependentNativeWord(int mode, int cheat,
                                                             boolean contact, boolean draw) {
        var before = fixture(2, 0).object(); var p = new P(); var motion = p.motion();
        var r = TAG.step(before, motion, new CdSpinTunnelTag.Gates(0, mode, cheat, 0));
        if (contact) { p.gv = 0x1234; firstRoll(p, 0xED, 0x1005BEEF); }
        assertResult(r, before, motion, p.motion(), contact ? writes(true, false, true) : Set.of(), draw);
    }

    @ParameterizedTest
    @CsvSource({"0x1000,0x0FD7,false", "0x1000,0x0FD8,true", "0x1000,0x1027,true",
            "0x1000,0x1028,false", "0xFFFC,0x0023,true", "0xFFFC,0x0024,false",
            "0x0004,0xFFDC,true", "0x0004,0xFFDB,false", "0x1000,0x8FD8,false",
            "0x1000,0x0FD6,false", "0x1000,0x8FD7,false"})
    void eachAxisUsesWrappedWordMinusFortyThroughPlusThirtyNine(int objectWord, int playerWord,
                                                              boolean contact) {
        for (boolean xAxis : new boolean[]{true, false}) {
            var before = fixture(2, 0).object().moveWord(xAxis ? X : Y, objectWord);
            var p = new P();
            if (xAxis) p.x = (playerWord << 16) | 0x1234;
            else p.y = (playerWord << 16) | 0xBEEF;
            var motion = p.motion(); var r = TAG.step(before, motion, GATES);
            assertEquals(contact, !r.player().executedWrites().isEmpty());
            if (contact) {
                p.gv = 0x1234; p.flags = 0xED; p.xr = 7; p.yr = 14; p.anim = 2;
                // Accepted Y literals are independent of the production range/roll calculations.
                if (xAxis) p.y = 0x1005BEEF;
                else p.y = switch (playerWord) {
                    case 0x0FD8 -> 0x0FDDBEEF;
                    case 0x1027 -> 0x102CBEEF;
                    case 0x0023 -> 0x0028BEEF;
                    case 0xFFDC -> 0xFFE1BEEF;
                    default -> throw new AssertionError("Missing expected literal");
                };
            }
            assertResult(r, before, motion, p.motion(), contact ? writes(true, false, true) : Set.of(), false);
        }
    }

    @ParameterizedTest
    @CsvSource({"0,0,2560", "0,2559,2560", "0,-2559,-2560", "0,2561,2561", "0,-2561,-2561",
            "0,32767,32767", "0,-32767,-32767", "0,-32768,-32768", "4,0,2560", "255,0,2560",
            "5,0,3328", "5,3327,3328", "5,-3327,-3328", "5,3584,3584", "5,-32768,-32768"})
    void subtypeZeroFloorsUnsignedWrappedMagnitudeWithoutUpperClamping(int zone, int xv, int expected) {
        var before = fixture(2, 0).object(); var p = new P(); p.xv = xv;
        var motion = p.motion(); var r = TAG.step(before, motion, new CdSpinTunnelTag.Gates(zone, 0, 0, 0));
        p.xv = expected; p.gv = expected; firstRoll(p, 0xED, 0x1005BEEF);
        assertResult(r, before, motion, p.motion(), writes(true, false, true), false);
    }

    @ParameterizedTest
    @CsvSource({"2560,95,2560", "2560,96,-2560", "2560,159,-2560", "2560,160,2560",
            "-2560,95,-2560", "-2560,96,2560", "-2560,159,2560", "-2560,160,-2560",
            "2560,255,2560", "-32768,96,-32768"})
    void subtypeZeroAngleSectorNegatesOnlyGroundVelocityWithWordWrapping(int xv, int angle, int gv) {
        var before = fixture(2, 0).object(); var p = new P(); p.xv = xv; p.angle = angle;
        var motion = p.motion(); var r = TAG.step(before, motion, GATES);
        p.gv = gv; firstRoll(p, 0xED, 0x1005BEEF);
        assertResult(r, before, motion, p.motion(), writes(true, false, true), false);
    }

    @ParameterizedTest
    @CsvSource({"0,3328", "-1,-3328", "3327,3328", "-3329,-3329", "32767,32767", "-32768,-32768"})
    void subtypeOneStoresOnlyYGroundAndAirBeforeFirstRoll(int yv, int expected) {
        var before = fixture(2, 1).object(); var p = new P(); p.yv = yv;
        var motion = p.motion(); var r = TAG.step(before, motion, GATES);
        p.yv = expected; p.gv = expected; firstRoll(p, 0xEF, 0x1005BEEF);
        assertResult(r, before, motion, p.motion(), writes(false, true, true), false);
    }

    @ParameterizedTest
    @CsvSource({"2,-1,8,3328,3328,237,true", "2,-1,4,-3328,-3328,237,true",
            "2,-1,12,3328,3328,237,true", "2,-1,252,3328,3328,237,true",
            "2,-1,0,4660,-582,237,false", "2,0,8,4660,-582,237,false",
            "2,1,4,4660,-582,237,false", "2,-32768,8,3328,3328,237,true",
            "3,0,8,3328,3328,239,true", "3,1,4,-3328,-3328,239,true",
            "3,0,12,3328,3328,239,true", "3,-1,8,4660,-582,237,false",
            "3,0,0,4660,-582,237,false", "5,0,8,3328,3328,239,true",
            "5,-1,8,4660,-582,237,false", "128,0,4,-3328,-3328,239,true",
            "254,32767,8,3328,3328,239,true", "255,0,8,3328,3328,239,true",
            "255,-32768,8,4660,-582,237,false"})
    void highSubtypesUseRawRightFirstAndOnlyTheSelectedAirBranch(int subtype, int yv, int rawHeld,
            int expectedX, int expectedGround, int expectedFlags, boolean speedStores) {
        var before = fixture(2, subtype).object(); var p = new P(); p.yv = yv;
        var motion = p.motion(); var r = TAG.step(before, motion, new CdSpinTunnelTag.Gates(0, 0, 0, rawHeld));
        p.xv = expectedX; p.gv = expectedGround; firstRoll(p, expectedFlags, 0x1005BEEF);
        assertResult(r, before, motion, p.motion(), writes(speedStores, false, speedStores), false);
    }

    @Test void everySubtypeFiveThrough255UsesTheSubtypeThreeBranch() {
        for (int subtype = 5; subtype <= 255; subtype++) {
            var before = fixture(2, subtype).object(); var p = new P(); p.yv = 0;
            var motion = p.motion(); var r = TAG.step(before, motion, new CdSpinTunnelTag.Gates(0, 0, 0, 12));
            p.xv = 0xD00; p.gv = 0xD00; firstRoll(p, 0xEF, 0x1005BEEF);
            assertResult(r, before, motion, p.motion(), writes(true, false, true), false);
            p = new P(); p.yv = 0; motion = p.motion();
            r = TAG.step(before, motion, new CdSpinTunnelTag.Gates(0, 0, 0, 4));
            p.xv = -0xD00; p.gv = -0xD00; firstRoll(p, 0xEF, 0x1005BEEF);
            assertResult(r, before, motion, p.motion(), writes(true, false, true), false);
            p = new P(); p.yv = -1; motion = p.motion();
            r = TAG.step(before, motion, new CdSpinTunnelTag.Gates(0, 0, 0, 8));
            firstRoll(p, 0xED, 0x1005BEEF);
            assertResult(r, before, motion, p.motion(), ROLL, false);
            p = new P(); p.yv = 0; motion = p.motion();
            r = TAG.step(before, motion, GATES); firstRoll(p, 0xED, 0x1005BEEF);
            assertResult(r, before, motion, p.motion(), ROLL, false);
        }
    }

    @Test void subtypeTwoPreservesAnAlreadySetAirBitWithoutAnotherAuthority() {
        var before = fixture(2, 2).object(); var p = new P(); p.flags = 0xEB; p.yv = -1;
        var motion = p.motion(); var r = TAG.step(before, motion, new CdSpinTunnelTag.Gates(0, 0, 0, 8));
        p.xv = 0xD00; p.gv = 0xD00; firstRoll(p, 0xEF, 0x1005BEEF);
        assertResult(r, before, motion, p.motion(), writes(true, false, true), false);
    }

    @ParameterizedTest
    @CsvSource({"0,291,1,-2560,-2560,239,true", "0,-291,1,-2560,-2560,239,true",
            "1,-32768,1,-2560,-2560,239,true", "32767,291,13,-2560,-2560,239,true",
            "-1,291,1,291,-582,237,false", "-32768,-291,1,-291,-582,237,false",
            "0,291,0,291,-582,237,false", "1,-291,8,-291,-582,237,false"})
    void subtypeFourBranchesDirectlyToYStoreWithoutRestoringOldYSign(int xv, int yv, int held,
            int expectedY, int expectedGround, int expectedFlags, boolean speedStores) {
        var before = fixture(2, 4).object(); var p = new P(); p.xv = xv; p.yv = yv;
        var motion = p.motion(); var r = TAG.step(before, motion, new CdSpinTunnelTag.Gates(0, 0, 0, held));
        p.yv = expectedY; p.gv = expectedGround; firstRoll(p, expectedFlags, 0x1005BEEF);
        assertResult(r, before, motion, p.motion(), writes(false, speedStores, speedStores), false);
    }

    @Test void firstRollAddsOnlyTheIntegerWordAndRetainsBothFractionsAndNonselectedFields() {
        var before = fixture(2, 0).object().moveWord(Y, 0xFFFD); var p = new P(); p.y = 0xFFFDBEEF;
        var motion = p.motion(); var r = TAG.step(before, motion, GATES);
        p.gv = 0x1234; firstRoll(p, 0xED, 0x0002BEEF);
        assertResult(r, before, motion, p.motion(), writes(true, false, true), false);
        assertEquals(0x10001234, r.player().finalMotion().centreX16_16());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5, 255})
    void alreadyRollingDoesNotStoreYRadiiOrAnimationEvenWhenSpeedsChange(int subtype) {
        var before = fixture(2, subtype).object(); var p = new P(); p.flags = 0xED; p.yv = 0;
        var motion = p.motion(); var r = TAG.step(before, motion, new CdSpinTunnelTag.Gates(0, 0, 0, 9));
        Set<CdSpinTunnelTag.PlayerField> stores;
        switch (subtype) {
            case 0 -> { p.gv = 0x1234; stores = Set.of(CdSpinTunnelTag.PlayerField.X_VELOCITY,
                    CdSpinTunnelTag.PlayerField.GROUND_VELOCITY, CdSpinTunnelTag.PlayerField.FLAGS); }
            case 1 -> { p.yv = 0xD00; p.gv = 0xD00; p.flags = 0xEF;
                stores = Set.of(CdSpinTunnelTag.PlayerField.Y_VELOCITY,
                        CdSpinTunnelTag.PlayerField.GROUND_VELOCITY, CdSpinTunnelTag.PlayerField.FLAGS); }
            case 2 -> stores = Set.of(CdSpinTunnelTag.PlayerField.FLAGS);
            case 4 -> { p.yv = -0xA00; p.gv = -0xA00; p.flags = 0xEF;
                stores = Set.of(CdSpinTunnelTag.PlayerField.Y_VELOCITY,
                        CdSpinTunnelTag.PlayerField.GROUND_VELOCITY, CdSpinTunnelTag.PlayerField.FLAGS); }
            default -> { p.xv = 0xD00; p.gv = 0xD00; p.flags = 0xEF;
                stores = Set.of(CdSpinTunnelTag.PlayerField.X_VELOCITY,
                        CdSpinTunnelTag.PlayerField.GROUND_VELOCITY, CdSpinTunnelTag.PlayerField.FLAGS); }
        }
        assertResult(r, before, motion, p.motion(), stores, false);
        assertEquals(9, r.player().finalMotion().xRadius()); assertEquals(19, r.player().finalMotion().yRadius());
        assertEquals(0x1A, r.player().finalMotion().animation());
    }

    @Test void executedStoresIncludeSameValueSpeedAndFlagsStores() {
        var before = fixture(2, 0).object(); var p = new P(); p.flags = 0xED; p.gv = p.xv;
        var motion = p.motion(); var r = TAG.step(before, motion, GATES);
        assertResult(r, before, motion, motion, Set.of(CdSpinTunnelTag.PlayerField.X_VELOCITY,
                CdSpinTunnelTag.PlayerField.GROUND_VELOCITY, CdSpinTunnelTag.PlayerField.FLAGS), false);
        var one = fixture(2, 1).object(); p.flags = 0xEF; p.yv = 0xD00; p.gv = 0xD00;
        motion = p.motion(); r = TAG.step(one, motion, GATES);
        assertResult(r, one, motion, motion, Set.of(CdSpinTunnelTag.PlayerField.Y_VELOCITY,
                CdSpinTunnelTag.PlayerField.GROUND_VELOCITY, CdSpinTunnelTag.PlayerField.FLAGS), false);
    }

    @ParameterizedTest
    @CsvSource({"0x0280,0x0080,0,false", "0x02FF,0x0080,0,false", "0x0300,0x0080,0,true",
            "0x0200,0,0,false", "0x0280,0,0,true", "0,0,0,false", "0xFF80,0,0,false",
            "0,0x0100,0,true", "0x9000,0x0080,128,false", "0x9000,0x0080,255,false",
            "0x9000,0x0080,127,true"})
    void despawnUsesCurrentXFixedUnsignedWordWindowAndLiveBitSeven(int x, int camera,
                                                                  int sprite, boolean delete) {
        var f = fixture(2, 0); var after = f.object().moveWord(X, x).moveByte(SPRITE_FLAGS, sprite)
                .moveByte(SAVED_FLAGS_ID, 0); byte[] bytes = after.copyBytes();
        var r = TAG.despawn(after, new CdSpinTunnelTag.DespawnGlobals(0, 0, camera));
        assertEquals(delete, r.deleteObject()); assertEquals(Optional.empty(), r.loadedClear());
        assertArrayEquals(bytes, after.copyBytes()); assertEquals(f.object(), f.pool().state(41));
    }

    @Test void despawnIsASecondCutThatUsesTheSuppliedPostDrawImage() {
        var preDraw = fixture(0, 0).object().moveWord(X, 0x9000).moveByte(SAVED_FLAGS_ID, 7);
        var p = new P(); p.x = 0x90001234; var motion = p.motion();
        var staged = TAG.step(preDraw, motion, new CdSpinTunnelTag.Gates(0, 0, 0x100, 0));
        assertTrue(staged.drawObject());
        var globals = new CdSpinTunnelTag.DespawnGlobals(0, 0, 0x80);
        assertFalse(TAG.despawn(staged.object(), globals).deleteObject(), "pre-draw bit7 remains set");
        // Supplied source cut only. Root tests the real shared DrawObject and reload, not this stand-in.
        var afterDrawLive = staged.object().moveByte(SPRITE_FLAGS, 5);
        assertEquals(new CdSpinTunnelTag.DespawnResult(true,
                Optional.of(new CdSpinTunnelTag.LoadedClear(7, 0, 23))), TAG.despawn(afterDrawLive, globals));
        assertEquals(0x81, preDraw.spriteFlags()); assertEquals(0x85, staged.object().spriteFlags());
        assertEquals(0x1005BEEF, staged.player().finalMotion().centreY16_16());
        var relocated = afterDrawLive.moveWord(X, 0x280);
        assertFalse(TAG.despawn(relocated, globals).deleteObject(), "current X, never an anchor or staged X");
    }

    @ParameterizedTest
    @CsvSource({"0x81,1,0,23", "0x80,1,0,23", "0x82,255,2,25", "0x81,254,2,25",
            "0x80,128,2,25", "0xFF,127,0,23", "0xFF,128,2,25", "0x82,0,2,25",
            "0x81,0,1,24", "0x82,1,1,24", "0x80,255,1,24"})
    void timeWarpAlwaysDeletesAndUsesSignedDirectionWithNativePeriodClamp(int zone, int direction,
                                                                        int period, int index) {
        var after = fixture(2, 0).object().moveWord(X, 0x80).moveByte(SPRITE_FLAGS, 0x80)
                .moveByte(SAVED_FLAGS_ID, 7);
        assertEquals(new CdSpinTunnelTag.DespawnResult(true,
                Optional.of(new CdSpinTunnelTag.LoadedClear(7, period, index))),
                TAG.despawn(after, new CdSpinTunnelTag.DespawnGlobals(zone, direction, 0x80)));
    }

    @ParameterizedTest
    @CsvSource({"254,0,764", "254,1,765", "254,2,766", "255,0,767", "255,1,768",
            "255,2,769", "255,127,894", "7,127,150"})
    void reachedSavedBankIndexIsReturnedEvenOutsideTheReal766ByteBank(int id, int period, int index) {
        var after = fixture(2, 0).object().moveWord(X, 0x9000).moveByte(SPRITE_FLAGS, 0)
                .moveByte(SAVED_FLAGS_ID, id);
        var r = TAG.despawn(after, new CdSpinTunnelTag.DespawnGlobals(period, 0x80, 0x80));
        assertTrue(r.deleteObject()); assertEquals(Optional.of(new CdSpinTunnelTag.LoadedClear(id, period, index)),
                r.loadedClear(), "no bank read, fill, rounded allocation or premature bounded rejection");
        assertEquals(0x34, after.id(), "Root owns all 64-byte deletion and loaded-bit publication");
    }

    @Test void zeroSavedIdNeverRequestsBankAccessAndKeptObjectsNeverRequestAClear() {
        var after = fixture(2, 0).object().moveByte(SAVED_FLAGS_ID, 0);
        assertEquals(new CdSpinTunnelTag.DespawnResult(true, Optional.empty()),
                TAG.despawn(after, new CdSpinTunnelTag.DespawnGlobals(255, 128, 0)));
        after = after.moveByte(SAVED_FLAGS_ID, 255);
        assertEquals(new CdSpinTunnelTag.DespawnResult(false, Optional.empty()),
                TAG.despawn(after, new CdSpinTunnelTag.DespawnGlobals(127, 128, 0)));
    }

    @Test void valuesDetachSetsAndObjectByteExportsAndOwnerRetainsNoInvocation() {
        var before = fixture(2, 0).object(); var m = new P().motion();
        var mutable = EnumSet.of(CdSpinTunnelTag.PlayerField.FLAGS);
        var player = new CdSpinTunnelTag.PlayerResult(m, m, mutable); mutable.clear();
        assertEquals(Set.of(CdSpinTunnelTag.PlayerField.FLAGS), player.executedWrites());
        assertThrows(UnsupportedOperationException.class, () -> player.executedWrites().clear());
        var first = TAG.step(before, m, GATES); byte[] bytes = first.object().copyBytes();
        byte[] exported = first.object().copyBytes(); Arrays.fill(exported, (byte) 0);
        var other = fixture(0, 255).object(); var p = new P(); p.xv = -32768;
        TAG.step(other, p.motion(), new CdSpinTunnelTag.Gates(5, 1, 1, 12));
        assertEquals(m, first.player().expectedMotion()); assertEquals(before, first.expectedObject());
        assertArrayEquals(bytes, first.object().copyBytes()); assertEquals(0x1005BEEF, first.player().finalMotion().centreY16_16());
    }

    @Test void recordsAndCallsRejectNullAndOutOfWidthInputs() {
        var o = fixture(2, 0).object(); var m = new P().motion(); var p = TAG.step(o, m, GATES).player();
        assertThrows(NullPointerException.class, () -> TAG.step(null, m, GATES));
        assertThrows(NullPointerException.class, () -> TAG.step(o, null, GATES));
        assertThrows(NullPointerException.class, () -> TAG.step(o, m, null));
        assertThrows(NullPointerException.class, () -> TAG.despawn(null, new CdSpinTunnelTag.DespawnGlobals(0, 0, 0)));
        assertThrows(NullPointerException.class, () -> TAG.despawn(o, null));
        assertThrows(NullPointerException.class, () -> new CdSpinTunnelTag.PlayerResult(null, m, Set.of()));
        assertThrows(NullPointerException.class, () -> new CdSpinTunnelTag.PlayerResult(m, null, Set.of()));
        assertThrows(NullPointerException.class, () -> new CdSpinTunnelTag.PlayerResult(m, m, null));
        assertThrows(NullPointerException.class, () -> new CdSpinTunnelTag.PlayerResult(m, m,
                java.util.Collections.singleton(null)));
        assertThrows(NullPointerException.class, () -> new CdSpinTunnelTag.Result(null, o, p, false));
        assertThrows(NullPointerException.class, () -> new CdSpinTunnelTag.Result(o, null, p, false));
        assertThrows(NullPointerException.class, () -> new CdSpinTunnelTag.Result(o, o, null, false));
        assertThrows(NullPointerException.class, () -> new CdSpinTunnelTag.DespawnResult(false, null));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.DespawnResult(false,
                Optional.of(new CdSpinTunnelTag.LoadedClear(1, 0, 5))));
        for (int bad : new int[]{-1, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.Gates(bad, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.Gates(0, 0, 0, bad));
            assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.DespawnGlobals(bad, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.DespawnGlobals(0, bad, 0));
        }
        for (int bad : new int[]{-1, 65536, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.Gates(0, bad, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.Gates(0, 0, bad, 0));
            assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.DespawnGlobals(0, 0, bad));
        }
        assertDoesNotThrow(() -> new CdSpinTunnelTag.Gates(255, 65535, 65535, 255));
        assertDoesNotThrow(() -> new CdSpinTunnelTag.DespawnGlobals(255, 255, 65535));
        for (int bad : new int[]{0, 256, -1})
            assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.LoadedClear(bad, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.LoadedClear(1, -1, 4));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.LoadedClear(1, 128, 133));
        assertThrows(IllegalArgumentException.class, () -> new CdSpinTunnelTag.LoadedClear(1, 0, 6));
    }

    private static void assertResult(CdSpinTunnelTag.Result r, CdObjectState before,
            CdPlayerMotionState expectedBefore, CdPlayerMotionState expectedAfter,
            Set<CdSpinTunnelTag.PlayerField> stores, boolean draw) {
        assertEquals(before, r.expectedObject()); assertEquals(expectedBefore, r.player().expectedMotion());
        assertEquals(expectedAfter, r.player().finalMotion(), "complete 20-field motion, including both fractions");
        assertEquals(stores, r.player().executedWrites(), "executed STORE set, including same-value stores");
        assertEquals(draw, r.drawObject());
        if (before.routine() == 2) assertArrayEquals(before.copyBytes(), r.object().copyBytes());
    }

    /** Expectation assembly only: callers supply literal flags and Y, with no native branch/arithmetic model. */
    private static void firstRoll(P p, int flags, int y) {
        p.flags = flags; p.y = y; p.xr = 7; p.yr = 14; p.anim = 2;
    }

    private static Set<CdSpinTunnelTag.PlayerField> writes(boolean x, boolean y, boolean ground) {
        var fields = EnumSet.copyOf(ROLL);
        if (x) fields.add(CdSpinTunnelTag.PlayerField.X_VELOCITY);
        if (y) fields.add(CdSpinTunnelTag.PlayerField.Y_VELOCITY);
        if (ground) fields.add(CdSpinTunnelTag.PlayerField.GROUND_VELOCITY);
        return Set.copyOf(fields);
    }

    private record Fixture(CdObjectPool pool, CdObjectState object) { }
    private static Fixture fixture(int routine, int subtype) {
        var pool = new CdObjectPool(); var o = pool.state(41);
        for (var f : CdObjectState.ByteField.values()) o = o.moveByte(f, 0xC7 + 13 * f.byteOffset());
        for (var f : CdObjectState.WordField.values()) o = o.moveWord(f, 0xA7B9 ^ (0x123 * f.byteOffset()));
        for (var f : CdObjectState.LongField.values()) o = o.moveLong(f, 0x83A5C7E9 ^ f.byteOffset());
        o = o.moveByte(ID, 0x34).moveByte(ROUTINE, routine).moveByte(SUBTYPE, subtype)
                .moveByte(SPRITE_FLAGS, 0x81).moveWord(X, 0x1000).moveWord(Y, 0x1000)
                .moveWord(X_FRACTION, 0x7654).moveWord(Y_FRACTION, 0x3210);
        pool.write(o); return new Fixture(pool, o);
    }

    /** All twenty fields are seeded distinctly; no default player RAM or actor/body owner is invented. */
    private static final class P {
        int x = 0x10001234, y = 0x1000BEEF, xv = 0x1234, yv = -0x567, gv = -0x246;
        int routine = 4, angle = 0x17, flags = 0xE9, sprite = 0xD3, xr = 9, yr = 19;
        int anim = 0x1A, prevAnim = 0x35, jump = 0x46, charge = 0x57, lock = -0x1234;
        int stick = 0x68, control = 0x79, prevPrimary = 0x8A, prevSecondary = 0x9B;
        CdPlayerMotionState motion() {
            return new CdPlayerMotionState(x, y, xv, yv, gv, routine, angle, flags, sprite, xr, yr,
                    anim, prevAnim, jump, charge, lock, stick, control, prevPrimary, prevSecondary);
        }
    }
}
