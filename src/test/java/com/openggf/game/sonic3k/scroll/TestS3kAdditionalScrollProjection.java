package com.openggf.game.sonic3k.scroll;

import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.game.sonic3k.runtime.*;
import com.openggf.level.scroll.*;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestS3kAdditionalScrollProjection {
    @BeforeEach void setup() { TestEnvironment.resetAll(); }
    @AfterEach void cleanup() { SessionManager.clear(); }

    @ParameterizedTest(name = "{0} act {1} x {2} y {3}")
    @MethodSource("contexts")
    void allHeldNativeModesMatchAndSamplingCannotAdvanceNativeState(String name, int act, int x, int y) {
        Fixture live = fixture(name, act), control = fixture(name, act);
        for (int frame = 7; frame < 11; frame++) {
            live.tick(x, y, frame, act);
            control.tick(x, y, frame, act);
        }
        var nativeOutput = output(live.handler, live.words, y);
        var phase = ((ScrollProjectionSource) live.handler).captureProjection(
                new ScrollProjectionContext(x, y, 10, act, 320, 224, nativeOutput));
        byte[] stateBefore = live.state == null ? null : live.state.captureBytes();
        var original = phase.sample(new ScrollView(x, y, 320, 224));
        assertEquals(nativeOutput, original.withSpritePositions(Map.of()));
        assertArrayEquals(live.words, Arrays.copyOf(phase.sample(new ScrollView(x, y, 320, 599)).horizontal(), 224));
        var view = new ScrollView(x + 97, y + 43, 491, 599);
        var sampled = phase.sample(view);
        phase.sample(new ScrollView(x - 137, y - 71, 333, 317));
        assertEquals(sampled, phase.sample(view));
        if (live.state != null) assertArrayEquals(stateBefore, live.state.captureBytes());
        assertEquals(nativeOutput, output(live.handler, live.words, y));
        if (live.handler instanceof SwScrlFbz fbz) {
            for (int slot = 0; slot < 10; slot++) {
                var cloud = fbz.cloudPositionAtAddressSlot(slot);
                assertEquals(new ScrollSpritePosition((x + cloud.x() - 0x80) & 0xFFFF,
                                (y + cloud.y() - 0x80) & 0xFFFF),
                        original.spritePositions().get(new ScrollSpriteKey("s3k/fbz-cloud", slot)));
            }
        }
        live.tick(x + 7, y + 3, 11, act);
        control.tick(x + 7, y + 3, 11, act);
        assertEquals(output(control.handler, control.words, y + 3), output(live.handler, live.words, y + 3));
        if (live.state != null) assertArrayEquals(control.state.captureBytes(), live.state.captureBytes());
        assertEquals(original, phase.sample(new ScrollView(x, y, 320, 224)));
    }

    @Test
    void lbzAdditiveLaunchAndFrozenFallUsePredispatchWordsWithoutConsumingPendingShakeOrChangingRewind() {
        Fixture live = fixture("lbz-launch", 1), control = fixture("lbz-launch", 1);
        int x = 0x4390, y = 0x668;
        for (int frame = 7; frame < 11; frame++) {
            live.tick(x, y, frame, 1);
            control.tick(x, y, frame, 1);
        }
        var state = (LbzZoneRuntimeState) live.state;
        state.requestScreenShakeOffset(5);
        ((LbzZoneRuntimeState) control.state).requestScreenShakeOffset(5);
        Object rewind = live.handler.captureRewindState();
        short[] retained = lbzWords((SwScrlLbz) live.handler);
        byte[] stateBefore = state.captureBytes();
        var nativeOutput = output(live.handler, live.words, y);
        var phase = ((ScrollProjectionSource) live.handler).captureProjection(
                new ScrollProjectionContext(x, y, 10, 1, 320, 224, nativeOutput));
        assertEquals(nativeOutput, phase.sample(new ScrollView(x, y, 320, 224)));
        phase.sample(new ScrollView(x + 79, y - 143, 577, 733));
        phase.sample(new ScrollView(x - 31, y + 251, 777, 255));
        assertArrayEquals(retained, lbzWords((SwScrlLbz) live.handler));
        assertArrayEquals(stateBefore, state.captureBytes());
        live.tick(x, y, 11, 1);
        control.tick(x, y, 11, 1);
        assertEquals(output(control.handler, control.words, y), output(live.handler, live.words, y));
        assertArrayEquals(control.state.captureBytes(), state.captureBytes());
        live.handler.restoreRewindState(rewind);
        state.restoreBytes(stateBefore);
        // Rewind rebuild takes the already-dispatched branch, retaining the same additive words.
        live.tick(x, y, 10, 1);
        assertArrayEquals(retained, lbzWords((SwScrlLbz) live.handler));
        var restoredOutput = output(live.handler, live.words, y);
        var restoredPhase = ((ScrollProjectionSource) live.handler).captureProjection(
                new ScrollProjectionContext(x, y, 10, 1, 320, 224, restoredOutput));
        assertEquals(restoredOutput, restoredPhase.sample(new ScrollView(x, y, 320, 224)));
        state.setFinalFallActive(true);
        live.tick(x + 16, y + 16, 12, 1);
        var frozen = output(live.handler, live.words, y + 16);
        var frozenPhase = ((ScrollProjectionSource) live.handler).captureProjection(
                new ScrollProjectionContext(x + 16, y + 16, 12, 1, 320, 224, frozen));
        assertEquals(frozen, frozenPhase.sample(new ScrollView(x + 16, y + 16, 320, 224)));
    }

    @Test
    void fbzPhaseOwnsItsDriftAndCloudSlotsAfterRuntimeAndNativeHandlerChange() {
        Fixture f = fixture("fbz-boss", 1);
        f.tick(0x2800, 0x400, 19, 1);
        var nativeOutput = output(f.handler, f.words, 0x400);
        var phase = ((ScrollProjectionSource) f.handler).captureProjection(
                new ScrollProjectionContext(0x2800, 0x400, 19, 1, 320, 224, nativeOutput));
        var view = new ScrollView(0x2913, 0x417, 341, 659);
        var expected = phase.sample(view);
        GameServices.zoneRuntimeRegistry().clear();
        f.handler.update(new int[224], 0, 0, 99, 0);
        assertEquals(expected, phase.sample(view));
    }

    private static short[] lbzWords(SwScrlLbz handler) {
        short[] words = new short[0xEC / 2];
        for (int i = 0; i < words.length; i++) words[i] = handler.getLbz2HScrollWordForTest(i);
        return words;
    }

    private static ScrollProjection output(ZoneScrollHandler handler, int[] words, int y) {
        short foreground = handler.getVscrollFactorFG();
        if (foreground == 0) foreground = (short) y;
        return new ScrollProjection(words, handler.getPerLineVScrollBG(), handler.getPerColumnVScrollBG(),
                handler.getPerColumnVScrollFG(), foreground, handler.getVscrollFactorBG(), handler.getBgCameraX(),
                handler.getBgPeriodWidth(), handler.getShakeOffsetX(), handler.getShakeOffsetY(), handler.getBgTilemapUpdateMode());
    }

    private static Fixture fixture(String name, int act) {
        S3kZoneRuntimeState state = null;
        ZoneScrollHandler handler;
        if (name.startsWith("icz")) {
            var icz = mock(IczZoneRuntimeState.class);
            when(icz.screenShakeOffsetY()).thenReturn(3);
            state = icz;
            handler = new SwScrlIcz();
        } else if (name.startsWith("fbz")) {
            var events = new Sonic3kFBZEvents();
            events.init(act);
            events.setOutdoorBobOffset(7);
            if (name.equals("fbz-outdoor")) events.setBackgroundOutdoor(true);
            if (name.equals("fbz-boss")) events.setBossBackgroundState(0x10, 29, 13);
            var fbz = new FbzZoneRuntimeState(act, PlayerCharacter.SONIC_ALONE, events);
            state = fbz;
            handler = new SwScrlFbz(() -> fbz);
        } else if (name.startsWith("lbz")) {
            var lbz = new LbzZoneRuntimeState(act, PlayerCharacter.SONIC_ALONE);
            lbz.setLaunchActive(name.equals("lbz-launch"));
            lbz.setFgAccum(0x9800);
            lbz.setBgAccum(0x12400);
            lbz.setBgLaunchSpeed(-0x8000);
            state = lbz;
            byte[] waterline = new byte[4096], waves = new byte[320];
            for (int i = 0; i < waterline.length; i++) waterline[i] = (byte) (i & 31);
            for (int i = 0; i < waves.length / 2; i++) {
                short wave = (short) ((i * 7) % 13 - 6);
                waves[i * 2] = (byte) (wave >> 8); waves[i * 2 + 1] = (byte) wave;
            }
            handler = new SwScrlLbz(waterline, waves);
        } else if (name.startsWith("hpz")) {
            boolean far = name.equals("hpz-far");
            handler = new SwScrlHpz() {
                @Override protected boolean isFarFraming() { return far; }
                @Override protected int screenShakeOffset() { return 3; }
            };
        } else if (name.equals("ddz")) {
            var ddz = new DdzZoneRuntimeState(act, PlayerCharacter.SONIC_ALONE);
            ddz.setForegroundWindowBase(0x200);
            ddz.setBossPosition(0x300, 0x100);
            ddz.addBackgroundScroll(0x2E3);
            state = ddz;
            handler = new SwScrlDdz();
        } else throw new AssertionError(name);
        handler.init(act, 0, 0);
        return new Fixture(handler, state, new int[224]);
    }

    private record Fixture(ZoneScrollHandler handler, S3kZoneRuntimeState state, int[] words) {
        void tick(int x, int y, int frame, int act) {
            GameServices.zoneRuntimeRegistry().clear();
            if (state != null) GameServices.zoneRuntimeRegistry().install(state);
            handler.update(words, x, y, frame, act);
        }
    }

    private static Stream<Arguments> contexts() {
        return Stream.of(
                Arguments.of("icz-intro", 0, 0x100, 0x680),
                Arguments.of("icz-indoor", 0, 0x3A00, 0x800),
                Arguments.of("icz-outdoor", 1, 0x700, 0x500),
                Arguments.of("icz-indoor", 1, 0x1900, 0x780),
                Arguments.of("fbz-indoor", 0, 0x1000, 0x200),
                Arguments.of("fbz-outdoor", 0, 0x1000, 0x200),
                Arguments.of("fbz-boss", 1, 0x2800, 0x400),
                Arguments.of("lbz", 0, 0x1240, 0x380),
                Arguments.of("lbz", 1, 0x4390, 0x668),
                Arguments.of("lbz-launch", 1, 0x4390, 0x668),
                Arguments.of("hpz-near", 1, 0xA20, 0x320),
                Arguments.of("hpz-far", 1, 0xF20, 0x320),
                Arguments.of("hpz-far", 0, 0xF20, 0x320),
                Arguments.of("ddz", 0, 0x340, 0x130));
    }
}
