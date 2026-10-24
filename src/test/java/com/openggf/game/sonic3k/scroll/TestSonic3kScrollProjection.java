package com.openggf.game.sonic3k.scroll;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.*;
import com.openggf.level.scroll.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kScrollProjection {
    @BeforeEach
    void setup() {
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        GameServices.camera().setLevelStarted(true);
    }

    @AfterEach
    void cleanup() {
        SessionManager.clear();
    }

    @ParameterizedTest(name = "{0} act {1} stage {2}")
    @MethodSource("contexts")
    void frozenPhaseMatchesNativeAndCannotMutateNativeStateOrNextTick(
            String name, int act, int stage) throws Exception {
        Fixture live = fixture(name, act, stage), control = fixture(name, act, stage);
        int x = name.equals("ending") ? 0x1D40 : name.equals("ssz") ? 0x1180 : 0xA40;
        int y = name.equals("ssz") && act == 0 ? 0xA30 : name.equals("lrz3") ? 0x560 : 0x180;
        for (int frame = 7; frame <= 10; frame++) {
            live.tick(x, y, frame, act);
            control.tick(x, y, frame, act);
        }
        live.install(x, y);
        var expected = output(live.handler, live.words, y);
        byte[] before = live.state == null ? null : live.state.captureBytes();
        Object handlerBefore = live.handler.captureRewindState();
        var phase =
                ((ScrollProjectionSource) live.handler)
                        .captureProjection(
                                new ScrollProjectionContext(x, y, 10, act, 320, 224, expected));
        assertEquals(expected, phase.sample(new ScrollView(x, y, 320, 224)));
        assertArrayEquals(
                live.words,
                Arrays.copyOf(phase.sample(new ScrollView(x, y, 320, 513)).horizontal(), 224));
        var view = new ScrollView(x + 97, y + 43, 491, 513);
        var sampled = phase.sample(view);
        if (name.equals("ssz") && act == 0) {
            assertEquals(
                    512,
                    sampled.backgroundPeriod(),
                    "SSZ1 retains its native nametable period at directed cameras");
        }
        phase.sample(new ScrollView(x - 37, y - 53, 333, 317));
        assertEquals(sampled, phase.sample(view));
        if (before != null) assertArrayEquals(before, live.state.captureBytes());
        assertTrue(
                com.openggf.game.rewind.RewindSnapshotDiff.keyEquals(
                        "scroll", handlerBefore, live.handler.captureRewindState()));
        assertEquals(expected, output(live.handler, live.words, y));
        // Advance an untouched control owner through the same native loop. Sampling may
        // neither consume pending requests nor change the next accumulator/deform output.
        live.tick(x + 7, y + 3, 11, act);
        control.tick(x + 7, y + 3, 11, act);
        assertEquals(
                output(control.handler, control.words, y + 3),
                output(live.handler, live.words, y + 3));
        if (before != null)
            assertArrayEquals(control.state.captureBytes(), live.state.captureBytes());
        // The captured phase remains immutable after replacing its live runtime owner.
        GameServices.zoneRuntimeRegistry().clear();
        assertEquals(sampled, phase.sample(view));
    }

    private static ScrollProjection output(ZoneScrollHandler handler, int[] words, int y) {
        short foreground = handler.getVscrollFactorFG();
        boolean explicit =
                handler instanceof com.openggf.game.internal.ExplicitForegroundVerticalScroll source
                        && source.foregroundVerticalScrollExplicit();
        if (foreground == 0 && !explicit) foreground = (short) y;
        return new ScrollProjection(
                words,
                handler.getPerLineVScrollBG(),
                handler.getPerColumnVScrollBG(),
                handler.getPerColumnVScrollFG(),
                foreground,
                handler.getVscrollFactorBG(),
                handler.getBgCameraX(),
                handler.getBgPeriodWidth(),
                handler.getShakeOffsetX(),
                handler.getShakeOffsetY(),
                handler.getBgTilemapUpdateMode());
    }

    private static Fixture fixture(String name, int act, int stage) throws Exception {
        var rom = GameServices.rom().getRom();
        S3kZoneRuntimeState state = null;
        ZoneScrollHandler handler =
                switch (name) {
                    case "default" -> new SwScrlS3kDefault();
                    case "aiz" -> SwScrlAiz.detachedPresentation();
                    case "hcz" -> new SwScrlHcz(rom.readBytes(0x5077E, 4096));
                    case "cnz" -> new SwScrlCnz();
                    case "mhz" -> new SwScrlMhz();
                    case "mgz" -> new SwScrlMgz();
                    case "soz" -> new SwScrlSoz(rom);
                    case "lrz" -> new SwScrlLrz();
                    case "lrz3" -> new SwScrlLrz3(rom);
                    case "ssz" -> new SwScrlSsz();
                    case "dez" -> new SwScrlS3kDez();
                    case "dez-final" -> new SwScrlS3kDezFinalBoss();
                    case "gumball" -> new SwScrlGumball();
                    case "pachinko" -> new SwScrlPachinko();
                    case "slots" ->
                            new SwScrlSlots(
                                    com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomTestData
                                            .resources());
                    case "ending" -> new SwScrlEnding(rom);
                    default -> throw new AssertionError(name);
                };
        if (name.equals("soz")) {
            var soz = new SozZoneRuntimeState(act, PlayerCharacter.SONIC_ALONE);
            soz.events().backgroundRoutine(stage);
            state = soz;
        } else if (name.startsWith("lrz")) {
            var lrz =
                    new LrzZoneRuntimeState(
                            name.equals("lrz3") ? 22 : 9, act, PlayerCharacter.SONIC_ALONE);
            lrz.setBackgroundRoutine(stage);
            lrz.bossAct().setForegroundRoutine(12);
            lrz.bossAct().rebuildLavaHeights(73, 1);
            state = lrz;
        } else if (name.equals("ssz")) {
            var ssz = new SszZoneRuntimeState(act, PlayerCharacter.SONIC_ALONE);
            ssz.markScreenInitApplied();
            ssz.setForegroundRoutine(stage);
            ssz.setCloudDrift(0x12345000);
            ssz.setCloudOscillator(7);
            state = ssz;
        } else if (name.equals("ending")) {
            var ending = new EndingZoneRuntimeState(PlayerCharacter.SONIC_ALONE);
            ending.screen().backgroundRoutine = stage;
            ending.screen().cloudDriftFixed = 0x12345678;
            ending.screen().scrollXFixed = 0x1000000;
            ending.screen().scrollYFixed = 0x1200000;
            state = ending;
        }
        handler.init(act, 0, 0);
        return new Fixture(handler, state, new int[224]);
    }

    private record Fixture(ZoneScrollHandler handler, S3kZoneRuntimeState state, int[] words) {
        void install(int x, int y) {
            GameServices.zoneRuntimeRegistry().clear();
            if (state != null) GameServices.zoneRuntimeRegistry().install(state);
            var camera = GameServices.camera();
            camera.setX((short) x);
            camera.setY((short) y);
            camera.setXCopy((short) x);
            camera.setYCopy((short) y);
        }

        void tick(int x, int y, int frame, int act) {
            install(x, y);
            if (handler instanceof SwScrlSlots slots) slots.updateForTest(null, words, x, y, frame);
            else handler.update(words, x, y, frame, act);
        }
    }

    private static Stream<Arguments> contexts() {
        return Stream.of(
                Arguments.of("default", 0, 0),
                Arguments.of("aiz", 0, 0),
                Arguments.of("aiz", 1, 0),
                Arguments.of("hcz", 0, 0),
                Arguments.of("hcz", 1, 0),
                Arguments.of("cnz", 0, 0),
                Arguments.of("mhz", 0, 0),
                Arguments.of("mhz", 1, 0),
                Arguments.of("mgz", 0, 0),
                Arguments.of("mgz", 1, 0),
                Arguments.of("soz", 0, 0),
                Arguments.of("soz", 0, 12),
                Arguments.of("soz", 1, 0),
                Arguments.of("soz", 1, 0x14),
                Arguments.of("soz", 1, 0x24),
                Arguments.of("soz", 1, 0x2C),
                Arguments.of("lrz", 0, 0),
                Arguments.of("lrz", 1, 0),
                Arguments.of("lrz3", 0, 0),
                Arguments.of("lrz3", 0, 12),
                Arguments.of("ssz", 0, 0),
                Arguments.of("ssz", 1, 0),
                Arguments.of("ssz", 1, 12),
                Arguments.of("ssz", 1, 24),
                Arguments.of("dez", 0, 0),
                Arguments.of("dez", 1, 0),
                Arguments.of("dez-final", 0, 0),
                Arguments.of("gumball", 0, 0),
                Arguments.of("pachinko", 0, 0),
                Arguments.of("slots", 0, 0),
                Arguments.of("ending", 0, 0),
                Arguments.of("ending", 0, 4),
                Arguments.of("ending", 0, 16));
    }
}
