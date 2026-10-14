package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.io.UncheckedIOException;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Registration is ROM-independent; actual ending use reads both native banks or fails closed. */
class TestSwScrlEndingResources {
    @AfterEach void reset() { TestEnvironment.resetAll(); }

    private static EndingZoneRuntimeState ending() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var state = new EndingZoneRuntimeState(PlayerCharacter.SONIC_ALONE);
        GameServices.zoneRuntimeRegistry().install(state);
        state.screen().foregroundX = 0x123;
        state.screen().backgroundCameraY = 7;
        state.screen().scrollYFixed = 7 << 16;
        state.screen().cloudDriftFixed = 0x00148000;
        for (int i = 0; i < state.screen().hScrollWords.length; i++)
            state.screen().hScrollWords[i] = (short) (i * 3);
        return state;
    }

    @Test void unselectedEndingDoesNotReadRomOrPublishScroll() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var rom = spy(new Rom());
        var handler = new SwScrlEnding(rom);
        int[] output = new int[224]; Arrays.fill(output, 0x12345678);
        handler.update(output, 0, 0, 17, 1);
        handler.rebuildOutput(output, 0, 0, 17, 1);
        verify(rom, never()).readBytes(anyLong(), anyInt());
        assertTrue(Arrays.stream(output).allMatch(v -> v == 0x12345678));
    }

    enum InvalidRom { UNOPENED, TRUNCATED_CLOUD_BANK, MALFORMED_CLOUD_TERMINATOR }

    @ParameterizedTest @EnumSource(InvalidRom.class)
    void relevantUseRejectsInvalidRomBeforeNativeRamOrOutputChanges(InvalidRom kind) throws Exception {
        Rom rom;
        if (kind == InvalidRom.UNOPENED) rom = new Rom();
        else {
            // Deliberately malformed test views, never runtime fallback tables.
            byte[] bytes = new byte[kind == InvalidRom.TRUNCATED_CLOUD_BANK ? 0x5B16D : 0x5B16E];
            for (int offset = 0x5B144; offset < 0x5B158; offset += 2) bytes[offset + 1] = 0x20;
            bytes[0x5B158] = 0x7F; bytes[0x5B159] = (byte) 0xFF;
            rom = Rom.fromReader(RomByteReader.fromBytes(bytes), "malformed ending band test view");
        }
        var state = ending(); byte[] before = state.captureBytes();
        var handler = assertDoesNotThrow(() -> new SwScrlEnding(rom));
        int[] output = new int[224]; Arrays.fill(output, 0x12345678);
        var failure = assertThrows(UncheckedIOException.class,
                () -> handler.update(output, 0x123, 7, 17, 1));
        assertTrue(failure.getCause().getMessage().contains(kind == InvalidRom.MALFORMED_CLOUD_TERMINATOR
                ? "terminator" : "out of bounds"));
        assertArrayEquals(before, state.captureBytes());
        assertTrue(Arrays.stream(output).allMatch(v -> v == 0x12345678));
    }

    @RequiresRom(SonicGame.SONIC_3K)
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void firstRelevantUseReadsBothBanksOnceAndFreshRebuildDoesNotAdvanceRam(boolean rebuildFirst)
            throws Exception {
        var state = ending(); var rom = spy(GameServices.rom().getRom());
        var handler = new SwScrlEnding(rom);
        verify(rom, never()).readBytes(anyLong(), anyInt());
        byte[] initial = state.captureBytes(); int[] output = new int[224];
        if (rebuildFirst) {
            handler.rebuildOutput(output, 0x123, 7, 17, 1);
            assertArrayEquals(initial, state.captureBytes());
        } else handler.update(output, 0x123, 7, 17, 1);
        // word_5B144 first band is $A0: source HScroll+$04 is unchanged at this native row.
        assertEquals(0xFEDDFFFAL, output[0] & 0xFFFFFFFFL);
        handler.update(output, 0x123, 7, 17, 1);
        assertEquals(0x00150000, state.screen().cloudDriftFixed);
        assertEquals(17, state.screen().backgroundScrollFrame);
        verify(rom, times(1)).readBytes(0x5B144, 22);
        verify(rom, times(1)).readBytes(0x5B15A, 20);
        byte[] saved = state.captureBytes(); int[] published = output.clone();
        state.screen().cloudDriftFixed = -1; state.screen().hScrollWords[139] = -1;
        state.restoreBytes(saved);
        var freshlyDerived = new SwScrlEnding(rom);
        freshlyDerived.rebuildOutput(output, 0x123, 7, 17, 1);
        assertArrayEquals(saved, state.captureBytes());
        assertArrayEquals(published, output);
        verify(rom, times(2)).readBytes(0x5B144, 22);
        verify(rom, times(2)).readBytes(0x5B15A, 20);
    }
}
