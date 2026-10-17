package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Writes to {@code Palette_cycle_counters+$00} come from objects, which LevelLoop runs in
 * Process_Sprites (sonic3k.asm:7926) after Animate_Palette (7923). Obj_AIZPlaneIntro
 * sets it (loc_67712, 135756) and Obj_CutsceneKnuckles clears it (loc_61F22, 128796);
 * both call {@link S3kPaletteCycleCounterWriter}, the interface used here. Level's
 * loc_60DE clears the byte with the rest of the bank (7652).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kAiz1AnPalGateWrites {
    @Test
    void objectPhaseWritesTakeEffectAfterThatFramesCall() {
        boot();
        // Calls 1 and 9 are normal runs. Writing the gate in call 9's frame (objects
        // before the engine's palette call) still lets that run advance counters+$02.
        for (int call = 1; call <= 8; call++) paletteCall();
        writer().writePaletteCycleCountersByte(0, 0xFF);
        paletteCall();
        assertEquals(12, state().paletteCycleWord(2), "call 9 ran before the frame's object write");
        assertEquals(0xFF, state().paletteCycleByte(0));
        for (int call = 10; call <= 24; call++) paletteCall();
        assertEquals(12, state().paletteCycleWord(2), "calls 17 and 25 are held by the gate");
        writer().writePaletteCycleCountersByte(0, 0);
        paletteCall(); // call 25: the release lands after this frame's AnPal
        assertEquals(12, state().paletteCycleWord(2), "the run in the releasing frame is still held");
        assertEquals(0, state().paletteCycleByte(0));
        for (int call = 26; call <= 33; call++) paletteCall();
        assertEquals(18, state().paletteCycleWord(2), "call 33 runs with the gate clear");
    }

    @Test
    void gateAndCountersRestoreTwiceWithForwardReplay() {
        boot();
        for (int call = 1; call <= 5; call++) paletteCall();
        writer().writePaletteCycleCountersByte(0, 0xFF);
        paletteCall();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        List<int[]> forward = record();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved);
            assertEquals(0xFF, state().paletteCycleByte(0), "cycle " + cycle);
            List<int[]> replay = record();
            for (int i = 0; i < forward.size(); i++) {
                assertArrayEquals(forward.get(i), replay.get(i), "cycle " + cycle + " call " + i);
            }
        }
    }

    @Test
    void freshLevelLoadClearsTheGate() throws Exception {
        boot();
        writer().writePaletteCycleCountersByte(0, 0xFF);
        paletteCall();
        assertEquals(0xFF, state().paletteCycleByte(0));
        GameServices.level().loadZoneAndAct(0, 0);
        GameServices.level().consumePendingInitialProcessSpritesPass();
        assertEquals(0, state().paletteCycleByte(0), "loc_60DE clears Palette_cycle_counters");
        assertEquals(0, state().paletteCycleWord(2));
    }

    /** Release the gate in the window's 8th frame; record the timer, counter0, counters+$02 and the gate. */
    private static List<int[]> record() {
        List<int[]> rows = new ArrayList<>();
        for (int call = 0; call < 20; call++) {
            if (call == 7) writer().writePaletteCycleCountersByte(0, 0);
            paletteCall();
            rows.add(new int[] {state().paletteCycleCounter1(), state().paletteCycleCounter0(),
                    state().paletteCycleWord(2), state().paletteCycleByte(0)});
        }
        assertFalse(Arrays.equals(rows.get(0), rows.get(rows.size() - 1)));
        return rows;
    }

    private static void boot() {
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro()
                .startPosition((short) 0x1EA6, (short) 0x490).startPositionIsCentre().build();
        GameServices.level().consumePendingInitialProcessSpritesPass();
        for (int i = 0; i < 40 && animator().paletteFadeTimerRunning(); i++) paletteCall();
        assertEquals(0, state().paletteCycleWord(2));
    }

    private static void paletteCall() {
        var level = GameServices.level();
        level.setFrameCounter(level.getFrameCounter() + 1);
        var registry = GameServices.paletteOwnershipRegistryOrNull();
        if (registry != null) registry.beginFrame();
        animator().update();
    }

    private static S3kPaletteCycleCounterWriter writer() {
        return animator();
    }

    private static Sonic3kGlobalAnimationState state() {
        return animator().globalAnimationState();
    }

    private static Sonic3kLevelAnimationManager animator() {
        return (Sonic3kLevelAnimationManager) GameServices.level().getAnimatedPatternManager();
    }
}
