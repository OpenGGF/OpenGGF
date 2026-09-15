package com.openggf.game.sonic3k;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.SozLightingState;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSozLightingState {
    @Test void coldLoadAndSeamlessEntryUseDifferentNativeInitialStates() {
        var cold = new SozLightingState();
        assertEquals(0, cold.darknessLevel());
        assertEquals(new SozLightingState.PaletteUpdate(-1, 0), cold.tickPalette());
        assertEquals(1, cold.darknessLevel());
        assertEquals(899, cold.masterTimer());
        for (int i = 0; i < 899; i++) cold.tickPalette();
        assertEquals(1, cold.darknessLevel());
        assertEquals(0x34, cold.tickPalette().lightOffset());
        assertEquals(2, cold.darknessLevel());
        assertEquals(1, cold.fadeStep());
        for (int i = 0; i < 3; i++) assertEquals(-1, cold.tickPalette().lightOffset());
        assertEquals(0x68, cold.tickPalette().lightOffset());
        assertEquals(2, cold.fadeStep());

        var seamless = new SozLightingState();
        seamless.initializeSeamlessDarkness();
        assertEquals(5, seamless.darknessLevel());
        assertEquals(1799, seamless.masterTimer());
        assertEquals(4, seamless.fadeStep());
        assertEquals(0xD0, seamless.fadeOffset());
        assertEquals(new SozLightingState.PaletteUpdate(-1, 0x80), seamless.tickPalette());
    }

    @Test void lightSwitchReversesPartialFadeAndForcesSandWriteBeforeExpiry() {
        var state = new SozLightingState();
        state.initializeSeamlessDarkness();
        state.tickPalette(); // sand timer now five, next offset eight
        state.resetLight();
        assertEquals(0, state.darknessLevel());
        assertEquals(-4, state.fadeRemaining());
        assertEquals(new SozLightingState.PaletteUpdate(0x9C, 0x68), state.tickPalette());
        assertEquals(4, state.sandTimer());
        assertEquals(8, state.sandOffset()); // forced write did not advance the sand phase
        state.resetLight();
        assertEquals(-3, state.fadeRemaining());
        assertEquals(0x68, state.tickPalette().lightOffset());
        for (int i = 0; i < 8; i++) state.tickPalette();
        assertEquals(0, state.fadeStep());
        assertEquals(0, state.fadeOffset());
        assertEquals(0, state.fadeRemaining());
    }

    @Test void torchUsesReadBeforeIncrementThreeFramesAndDarkBank() {
        var state = new SozLightingState();
        for (int expected : new int[]{0, 1, 2, 0}) {
            assertEquals(expected, state.tickTorch());
            for (int i = 0; i < 7; i++) assertEquals(-1, state.tickTorch());
        }
        state.initializeSeamlessDarkness();
        assertEquals(6, state.tickTorch());
        state.resetLight();
        state.tickPalette(); // step three uses bank three
        for (int i = 0; i < 7; i++) assertEquals(-1, state.tickTorch());
        assertEquals(5, state.tickTorch());
    }

    @Test void bossHoldDoesNotRestartBrighteningAndSuppressesTorchUploads() {
        var state = new SozLightingState();
        state.initializeSeamlessDarkness();
        state.enterBossLight();
        for (int i = 0; i < 20; i++) {
            state.holdBossTimers();
            state.tickPalette();
            assertEquals(-1, state.tickTorch());
            assertEquals(0x7FFE, state.masterTimer());
        }
        assertEquals(0, state.fadeStep());
        assertEquals(0, state.darknessLevel());
    }

    @Test void rewindRestoresAllCoupledClocksForIdenticalForwardReplay() {
        var runtime = new SozZoneRuntimeState(1, PlayerCharacter.SONIC_AND_TAILS);
        var light = runtime.lighting();
        light.initializeSeamlessDarkness();
        light.resetLight();
        for (int i = 0; i < 5; i++) { light.tickPalette(); light.tickTorch(); }
        byte[] snapshot = runtime.captureBytes();
        var palettes = new SozLightingState.PaletteUpdate[30];
        int[] torches = new int[30];
        for (int i = 0; i < 30; i++) {
            palettes[i] = light.tickPalette();
            torches[i] = light.tickTorch();
        }
        byte[] end = runtime.captureBytes();
        runtime.restoreBytes(snapshot);
        for (int i = 0; i < 30; i++) {
            assertEquals(palettes[i], light.tickPalette());
            assertEquals(torches[i], light.tickTorch());
        }
        assertArrayEquals(end, runtime.captureBytes());
    }
}
