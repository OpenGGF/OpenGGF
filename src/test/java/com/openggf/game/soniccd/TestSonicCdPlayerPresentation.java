package com.openggf.game.soniccd;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** Physical held bytes are distinct from host pressed/legacy aggregate jump bits. */
class TestSonicCdPlayerPresentation {
    @ParameterizedTest @CsvSource({"0,0", "1,64", "2,16", "3,80", "4,32", "5,96", "6,48", "7,112"})
    void distinctActionButtonsKeepTheirNativeHeldBits(int actions, int nativeBits) {
        var p1 = PlayerInputState.of(15, 0, actions, 0, true, false);
        assertEquals(0x8F | nativeBits, SonicCdPlayerPresentation.nativeHeldByte(
                LogicalInputSnapshot.ofPlayers(p1, PlayerInputState.neutral()), false));
    }

    @Test void sourceTapIsNotTakenFromTheHostsPressedMask() {
        var p1 = PlayerInputState.of(8, 4, InputActionMasks.ACTION_B, InputActionMasks.ACTION_A, false, true);
        assertEquals(0x18, SonicCdPlayerPresentation.nativeHeldByte(
                LogicalInputSnapshot.ofPlayers(p1, PlayerInputState.neutral()), false));
    }

    @Test void genericSuppressionIsARealNeutralSampleIncludingStartAndActions() {
        var p1 = PlayerInputState.of(15, 15, 7, 7, true, true);
        assertEquals(0, SonicCdPlayerPresentation.nativeHeldByte(
                LogicalInputSnapshot.ofPlayers(p1, p1), true));
        assertEquals(0, SonicCdPlayerPresentation.nativeHeldByte(null, false));
    }
}
