package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-derived expectations, not executed native/retail evidence. Pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Monitor and Time Post/Main.asm:570-589.
 * Regional constants select the source rule only; these tests certify no media or regional admission.
 */
class TestCdPlayerSpeedShoesReward {
    @ParameterizedTest @MethodSource("sourceVariantsAndRawTimeZones")
    void nativeFiveStoresAndOrderedCommandsUseTheWholeRawByte(CdReleaseVariant variant, int rawTimeZone) {
        var before = new CdPlayerSpeedShoesReward.State(0xA5, 0xFEDC,
                new CdPlayerMovement.Physics(-32768, 32767, -1));
        var result = CdPlayerSpeedShoesReward.give(before, variant, rawTimeZone);
        assertEquals(new CdPlayerSpeedShoesReward.State(1, variant == CdReleaseVariant.USA ? 1320 : 1200,
                new CdPlayerMovement.Physics(0xC00, 0x18, 0x80)), result.state());
        var shoes = CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS;
        var fade = CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_FADEPCM;
        assertEquals(rawTimeZone == 0 ? List.of(fade, shoes) : List.of(shoes), result.commands(),
                "TST.B timeZone; BNE: warp bit is not masked and period bits are not normalized");
        assertEquals(new CdPlayerSpeedShoesReward.State(0xA5, 0xFEDC,
                new CdPlayerMovement.Physics(-32768, 32767, -1)), before);
        assertEquals(result, CdPlayerSpeedShoesReward.give(before, variant, rawTimeZone));
    }

    private static Stream<Arguments> sourceVariantsAndRawTimeZones() {
        return Arrays.stream(CdReleaseVariant.values()).flatMap(variant ->
                Stream.of(0, 1, 2, 0x80, 0x81, 0x82, 0xFF).map(zone -> Arguments.of(variant, zone)));
    }

    @ParameterizedTest @CsvSource({"0,0,0,0,0", "1,1,3072,24,128", "2,65535,-1,2,-3",
            "128,1200,1536,12,128", "255,1320,32767,-32768,0"})
    void existingFlagsTimersAndPhysicsAreReplacedEvenWhenAStoreKeepsItsValue(int flag, int timer,
            int topSpeed, int acceleration, int deceleration) {
        var before = new CdPlayerSpeedShoesReward.State(flag, timer,
                new CdPlayerMovement.Physics(topSpeed, acceleration, deceleration));
        assertEquals(new CdPlayerSpeedShoesReward.State(1, 1320, new CdPlayerMovement.Physics(0xC00, 0x18, 0x80)),
                CdPlayerSpeedShoesReward.give(before, CdReleaseVariant.USA, 1).state());
    }

    @Test void typedNativeCommandIdsAreThePinnedSubCpuDirectoryValues() {
        // System.inc:144 rsset 1; SCMD_SHOESMUS at :252, SCMD_FADEPCM at :274.
        assertEquals(0x6C, CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS.nativeId());
        assertEquals(0x82, CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_FADEPCM.nativeId());
    }

    @Test void resultDefensivelyCopiesOrderedCommandsAndExposesImmutableValues() {
        var state = new CdPlayerSpeedShoesReward.State(1, 1320, new CdPlayerMovement.Physics(0xC00, 0x18, 0x80));
        var source = new ArrayList<>(List.of(CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_FADEPCM,
                CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS));
        var result = new CdPlayerSpeedShoesReward.Result(state, source);
        source.clear();
        assertEquals(List.of(CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_FADEPCM,
                CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS), result.commands());
        assertEquals(state, result.state());
        assertThrows(UnsupportedOperationException.class, () -> result.commands().clear());
        var given = CdPlayerSpeedShoesReward.give(state, CdReleaseVariant.USA, 0);
        assertThrows(UnsupportedOperationException.class, () -> given.commands().set(0,
                CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS));
        assertEquals(result, given);
    }

    @Test void allNullInputsAreRejectedBeforeAResultExists() {
        var state = new CdPlayerSpeedShoesReward.State(0, 0, new CdPlayerMovement.Physics(0, 0, 0));
        assertThrows(NullPointerException.class, () -> new CdPlayerSpeedShoesReward.State(0, 0, null));
        assertThrows(NullPointerException.class, () -> CdPlayerSpeedShoesReward.give(null, CdReleaseVariant.USA, 1));
        assertThrows(NullPointerException.class, () -> CdPlayerSpeedShoesReward.give(state, null, 1));
        assertThrows(NullPointerException.class, () -> new CdPlayerSpeedShoesReward.Result(null, List.of()));
        assertThrows(NullPointerException.class, () -> new CdPlayerSpeedShoesReward.Result(state, null));
        assertThrows(NullPointerException.class, () -> new CdPlayerSpeedShoesReward.Result(state, Arrays.asList(
                CdPlayerSpeedShoesReward.SubCpuCommand.SCMD_SHOESMUS, null)));
    }

    @ParameterizedTest @CsvSource({"-1,0", "256,0", "-2147483648,0", "2147483647,0",
            "0,-1", "0,65536", "0,-2147483648", "0,2147483647"})
    void stateRejectsNonNativeByteAndWordValuesWithoutNarrowing(int flag, int timer) {
        assertThrows(IllegalArgumentException.class, () -> new CdPlayerSpeedShoesReward.State(flag, timer,
                new CdPlayerMovement.Physics(0, 0, 0)));
    }

    @ParameterizedTest @ValueSource(ints = {-1, 256, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void giveRejectsNonNativeTimeZoneWithoutNarrowing(int rawTimeZone) {
        var state = new CdPlayerSpeedShoesReward.State(255, 65535, new CdPlayerMovement.Physics(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CdPlayerSpeedShoesReward.give(state, CdReleaseVariant.USA, rawTimeZone));
    }

    @Test void stateKeepsBothNativeWidthEndpointsAndSignedPhysicsWords() {
        assertEquals(0, new CdPlayerSpeedShoesReward.State(0, 0, new CdPlayerMovement.Physics(0, 0, 0)).speedShoes());
        var maximum = new CdPlayerSpeedShoesReward.State(255, 65535, new CdPlayerMovement.Physics(-32768, 32767, -1));
        assertEquals(255, maximum.speedShoes()); assertEquals(65535, maximum.speedShoesTimer());
        assertEquals(new CdPlayerMovement.Physics(-32768, 32767, -1), maximum.physics());
    }
}
