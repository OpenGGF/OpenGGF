package com.openggf.game.sonic2.attract;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.CharacterKey;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.game.sonic2.attract.Sonic2AttractService.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2AttractRomData {
    private static final LogicalInputSnapshot NEUTRAL = LogicalInputSnapshot.neutral();

    @Test
    void plansAndTimerOperandsMatchTheRetailRomInstructionsAndPointers() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        var service = new Sonic2AttractService(rom);
        assertEquals(0x303B, rom.read16BitAddr(0x3D40));
        assertEquals(0x3DAC, 0x3D42 + (short) rom.read16BitAddr(0x3D42));
        assertEquals(0x43F9, rom.read16BitAddr(0x4266));
        assertEquals(0x4948, RomByteReader.fromRom(rom).readU32BE(0x4268));
        assertEquals(640, service.titleDuration());
        assertEquals(1640, service.playbackDuration());
        assertEquals(60, service.fadeDuration());
        int[] zoneActs = {0x0000, 0x0D00, 0x0F00, 0x0C00};
        int[] scripts = {0x4CA8, 0x4DD8, 0x4E38, 0x4D68};
        int[] initialCounters = {75, 70, 66, 72};
        for (int index = 0; index < 4; index++) {
            var plan = startNext(service);
            assertEquals(index, plan.demoNumber());
            assertEquals(zoneActs[index], plan.nativeZoneAndAct());
            assertEquals(zoneActs[index] >>> 8, plan.nativeZone());
            assertEquals(0, plan.actIndex());
            assertEquals(scripts[index], plan.player1ScriptAddress());
            assertEquals(index == 0 ? 0x4D08 : -1, plan.player2ScriptAddress());
            assertEquals(index == 0 ? SessionKind.COMPETITION : SessionKind.CAMPAIGN, plan.sessionKind());
            assertEquals(index == 0, plan.scriptedPlayer2());
            assertEquals(CharacterKey.SONIC, plan.player1Character());
            assertEquals(CharacterKey.TAILS, plan.player2Character());
            assertFalse(plan.resetEmeraldProgress());
            assertEquals(new InputCursor(0, initialCounters[index]), service.player1Cursor());
            assertEquals(new InputCursor(0, index == 0 ? 59 : 0), service.player2Cursor());
            service.enterTitle();
        }
    }

    @Test
    void everyPacketRunHasItsNativeUnsignedByteDurationAndNoSentinelEnd() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        int[] starts = {0x4CA8, 0x4D08, 0x4DD8, 0x4E38, 0x4D68};
        int[] ends = {0x4D08, 0x4D68, 0x4E38, 0x4E98, 0x4DD8};
        int[] counts = {48, 48, 48, 48, 56};
        int[] durations = {1832, 1836, 1846, 1794, 1890};
        for (int stream = 0; stream < starts.length; stream++) {
            assertEquals(counts[stream], (ends[stream] - starts[stream]) / 2);
            var expanded = expand(rom, starts[stream], ends[stream]);
            assertEquals(durations[stream], expanded.size());
            assertTrue(expanded.size() > 1640 + 60, "The timer ends playback before ROM data runs out");
        }
        // A duration byte $FF means 256 calls, not 255 or zero.
        assertEquals(0xFF, Byte.toUnsignedInt(rom.readByte(0x4CA8 + 29 * 2 + 1)));
        var service = new Sonic2AttractService(rom);
        startNext(service);
        int beforeLongRun = 0;
        for (int packet = 0; packet < 29; packet++) {
            beforeLongRun += Byte.toUnsignedInt(rom.readByte(0x4CA8 + packet * 2 + 1)) + 1;
        }
        for (int frame = 0; frame < beforeLongRun; frame++) stepWithoutTimer(service);
        assertEquals(new InputCursor(58, 255), service.player1Cursor());
        for (int frame = 0; frame < 256; frame++) {
            assertEquals(8, stepWithoutTimer(service).player1HeldByte());
            if (frame < 255) assertEquals(58, service.player1Cursor().byteIndex());
        }
        assertEquals(60, service.player1Cursor().byteIndex());
        // Script S/A bytes near the tail are ordinary data. Only PHYSICAL Start aborts.
        for (int frame = beforeLongRun + 256; frame < 1832; frame++) {
            var output = stepWithoutTimer(service);
            assertFalse(output.physicalStartPressed());
        }
        assertEquals(Phase.PLAYBACK, service.phase());
        assertEquals(1640, service.timeLeft(), "Packet stepping has no authority over the V-int timer");
    }

    @Test
    void allFourFullDemoAndFadeControllerSequencesMatchIndependentExpandedRomRuns() throws Exception {
        Rom rom = TestEnvironment.currentRom();
        var service = new Sonic2AttractService(rom);
        int[] ends = {0x4D08, 0x4E38, 0x4E98, 0x4DD8};
        for (int demo = 0; demo < 4; demo++) {
            var plan = startNext(service);
            List<Integer> p1 = expand(rom, plan.player1ScriptAddress(), ends[demo]);
            List<Integer> p2 = demo == 0 ? expand(rom, 0x4D08, 0x4D68) : List.of();
            for (int frame = 0; frame < 1700; frame++) {
                service.onLevelUpdates();
                ControllerFrame actual = service.stepControllers(NEUTRAL);
                assertEquals(frame < 1640 ? Phase.PLAYBACK : Phase.FADING, actual.phase());
                assertEquals((int) p1.get(frame), actual.player1HeldByte(), "P1 demo=" + demo + " call=" + frame);
                assertEquals(demo == 0 ? (int) p2.get(frame) : 0, actual.player2HeldByte(), "P2 demo=" + demo + " call=" + frame);
                assertPad(actual.controllers().player1(), actual.player1HeldByte());
                assertPad(actual.controllers().player2(), actual.player2HeldByte());
                Transition transition = service.finishIteration(false);
                if (frame == 1639) {
                    assertEquals(new Transition(Event.BEGIN_FADE, CompletionReason.TIME_EXPIRED, ReturnTarget.SEGA_SCREEN), transition);
                } else if (frame == 1699) {
                    assertEquals(new Transition(Event.COMPLETE, CompletionReason.TIME_EXPIRED, ReturnTarget.SEGA_SCREEN), transition);
                } else {
                    assertEquals(Transition.NONE, transition);
                }
            }
            assertEquals(Phase.COMPLETE, service.phase());
            assertThrows(IllegalStateException.class, () -> service.stepControllers(NEUTRAL));
            service.enterTitle();
        }
        assertEquals(0, service.demoNumber());
    }

    @Test
    void repeatedJumpPressesAreRetailHeldPressesAndPhysicalActionsCannotChangeThem() throws Exception {
        var service = new Sonic2AttractService(TestEnvironment.currentRom());
        startNext(service);
        // EHZ starts 76 neutral calls, then 67 right calls, then nine right+C calls.
        for (int frame = 0; frame < 143; frame++) stepWithoutTimer(service);
        var physical = LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0xF, 0xF,
                InputActionMasks.ACTION_A, InputActionMasks.ACTION_A, true, false), PlayerInputState.neutral());
        for (int frame = 0; frame < 9; frame++) {
            var output = service.stepControllers(physical);
            assertEquals(0x28, output.player1HeldByte());
            assertEquals(InputActionMasks.ACTION_C, output.controllers().player1().actionHeldMask());
            assertEquals(InputActionMasks.ACTION_C, output.controllers().player1().actionPressedMask(),
                    "fixBugs=0 repeats pressed on every held-C call");
            assertFalse(output.physicalStartPressed(), "Holding Start without an edge does not abort");
            assertEquals(Transition.NONE, service.finishIteration(false));
        }
    }

    @Test
    void injectedRomOperandsAndPacketsRemainTheOnlyDataAuthority() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        byte[] bytes = reader.slice(0, reader.size());
        // Mutate only test-owned in-memory bytes; no ROM files/links or fixture inputs are used.
        bytes[0x3BA4] = 0; bytes[0x3BA5] = 3;
        bytes[0x42AA] = 0; bytes[0x42AB] = 4;
        bytes[0x440A] = 0; bytes[0x440B] = 2;
        bytes[0x4CA8] = 0x7F; bytes[0x4CA9] = 0;
        bytes[0x4D08] = (byte) 0x80; bytes[0x4D09] = 0;
        try (Rom altered = Rom.fromReader(RomByteReader.fromBytes(bytes), "test-owned attract operand variant")) {
            var service = new Sonic2AttractService(altered);
            assertEquals(3, service.titleDuration());
            assertEquals(4, service.playbackDuration());
            assertEquals(2, service.fadeDuration());
            startNext(service);
            ControllerFrame output = service.stepControllers(NEUTRAL);
            assertEquals(0x7F, output.player1HeldByte());
            assertEquals(InputActionMasks.ACTION_ALL, output.controllers().player1().actionPressedMask());
            assertEquals(0xF, output.controllers().player1().pressedMask() & 0xF);
            assertTrue(output.controllers().player2().startPressed(), "ROM Start is published as controller data");
            assertFalse(output.physicalStartPressed(), "ROM Start must not masquerade as physical abort");
            assertEquals(Transition.NONE, service.finishIteration(false));
        }
    }

    @Test
    void nativeWordIndexSignExtendsForAddressingAndWrapsOnlyOnItsWordIncrement() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        byte[] bytes = reader.slice(0, reader.size());
        bytes[0x4CA6] = 0x40;
        bytes[0x4CA7] = 0;
        try (Rom variant = Rom.fromReader(RomByteReader.fromBytes(bytes), "test-owned signed demo index variant")) {
            var service = new Sonic2AttractService(variant);
            startNext(service);
            Snapshot initial = service.capture();
            service.restore(new Snapshot(initial.romFingerprint(), initial.phase(), initial.demoNumber(),
                    initial.activeDemoNumber(), initial.timeLeft(), new InputCursor(0xFFFE, 0),
                    initial.player2(), initial.reason(), false, false));
            ControllerFrame output = service.stepControllers(NEUTRAL);
            assertEquals(0x40, output.player1HeldByte(), "ADDA.W $FFFE selects the preceding ROM packet");
            assertEquals(InputActionMasks.ACTION_A, output.controllers().player1().actionPressedMask());
            assertEquals(new InputCursor(0, 75), service.player1Cursor(), "ADDQ.W wraps the index and preloads the next duration");
            assertEquals(Transition.NONE, service.finishIteration(false));
        }
    }

    static LaunchPlan startNext(Sonic2AttractService service) {
        while (service.timeLeft() != 0) service.onTitleVInt();
        LaunchRequest request = service.requestNextLaunch(true);
        assertEquals(LaunchStatus.READY, request.status());
        service.beginPlayback();
        return request.plan();
    }

    static ControllerFrame stepWithoutTimer(Sonic2AttractService service) {
        ControllerFrame frame = service.stepControllers(NEUTRAL);
        assertEquals(Transition.NONE, service.finishIteration(false));
        return frame;
    }

    private static List<Integer> expand(Rom rom, int start, int end) throws Exception {
        var result = new ArrayList<Integer>();
        for (int packet = start; packet < end; packet += 2) {
            int held = Byte.toUnsignedInt(rom.readByte(packet));
            int calls = Byte.toUnsignedInt(rom.readByte(packet + 1)) + 1;
            for (int i = 0; i < calls; i++) result.add(held);
        }
        return result;
    }

    private static void assertPad(PlayerInputState actual, int held) {
        int actions = ((held & 0x40) >>> 6) | ((held & 0x10) >>> 3) | ((held & 0x20) >>> 3);
        assertEquals(held & 0xF, actual.heldMask() & 0xF);
        assertEquals(held & 0xF, actual.pressedMask() & 0xF);
        assertEquals(actions, actual.actionHeldMask());
        assertEquals(actions, actual.actionPressedMask());
        assertEquals((held & 0x80) != 0, actual.startHeld());
        assertEquals((held & 0x80) != 0, actual.startPressed());
    }
}
