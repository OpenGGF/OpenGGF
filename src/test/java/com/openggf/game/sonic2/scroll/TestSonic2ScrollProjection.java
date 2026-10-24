package com.openggf.game.sonic2.scroll;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.runtime.HtzRuntimeState;
import com.openggf.level.scroll.*;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class TestSonic2ScrollProjection {
    @BeforeEach void prepare() { TestEnvironment.resetAll(); }
    @AfterEach void cleanup() { SessionManager.clear(); }

    @ParameterizedTest(name = "zone {0} act {1} quake {2}")
    @MethodSource("handlers")
    void allNativeContextsMatchAndSamplingLeavesNativePhaseAndEffectsUnchanged(int zone, int act, boolean quake) {
        var tables = tables();
        var htz = mock(HtzRuntimeState.class);
        when(htz.earthquakeActive()).thenReturn(quake);
        when(htz.cameraBgXOffset()).thenReturn(29);
        when(htz.cameraBgYOffset()).thenReturn(13);
        GameServices.zoneRuntimeRegistry().install(htz);
        GameServices.gameState().setScreenShakeActive(true);
        ZoneScrollHandler live = handler(zone, act, tables), control = handler(zone, act, tables);
        int x = 0x280, y = 0x80;
        for (int frame = 7; frame < 10; frame++) {
            x += 7; y += 3;
            live.update(new int[224], x, y, frame, act);
            control.update(new int[224], x, y, frame, act);
        }
        int[] words = new int[224], controlWords = new int[224];
        live.update(words, x, y, 10, act);
        control.update(controlWords, x, y, 10, act);
        var nativeOutput = output(live, words, y);
        var phase = ((ScrollProjectionSource) live).captureProjection(
                new ScrollProjectionContext(x, y, 10, act, 320, 224, nativeOutput));
        boolean shakeBefore = GameServices.gameState().isScreenShakeActive();
        int timerBefore = live instanceof SwScrlDez dez ? dez.getDezShakeTimer() : -1;
        assertEquals(nativeOutput, phase.sample(new ScrollView(x, y, 320, 224)));
        assertArrayEquals(words, Arrays.copyOf(phase.sample(new ScrollView(x, y, 320, 597)).horizontal(), 224));
        var view = new ScrollView(x + 131, y + 59, 333, 597);
        var sampled = phase.sample(view);
        phase.sample(new ScrollView(x - 73, y - 19, 511, 301));
        assertEquals(sampled, phase.sample(view));
        assertEquals(shakeBefore, GameServices.gameState().isScreenShakeActive());
        if (live instanceof SwScrlDez dez) assertEquals(timerBefore, dez.getDezShakeTimer());
        assertEquals(nativeOutput, output(live, words, y));
        live.update(words, x + 41, y + 17, 11, act);
        control.update(controlWords, x + 41, y + 17, 11, act);
        assertEquals(output(control, controlWords, y + 17), output(live, words, y + 17));
        assertEquals(nativeOutput, phase.sample(new ScrollView(x, y, 320, 224)));
    }

    @Test
    void capturedRippleAndEventGateDoNotReadLiveTablesOrRuntime() {
        var tables = tables();
        var nativeHandler = handler(1, 0, tables);
        int[] words = new int[224];
        nativeHandler.update(words, 0x600, 0x470, 8, 0);
        var context = new ScrollProjectionContext(0x600, 0x470, 8, 0, 320, 224, output(nativeHandler, words, 0x470));
        var phase = ((ScrollProjectionSource) nativeHandler).captureProjection(context);
        var view = new ScrollView(0x611, 0x48B, 333, 401);
        var expected = phase.sample(view);
        reset(tables);
        GameServices.zoneRuntimeRegistry().clear();
        GameServices.gameState().setScreenShakeActive(false);
        assertEquals(expected, phase.sample(view));
        verifyNoInteractions(tables);
    }

    private static ScrollProjection output(ZoneScrollHandler handler, int[] words, int cameraY) {
        short fg = handler.getVscrollFactorFG();
        if (fg == 0) fg = (short) cameraY;
        return new ScrollProjection(words, handler.getPerLineVScrollBG(), handler.getPerColumnVScrollBG(),
                handler.getPerColumnVScrollFG(), fg, handler.getVscrollFactorBG(), handler.getBgCameraX(),
                handler.getBgPeriodWidth(), handler.getShakeOffsetX(), handler.getShakeOffsetY(), handler.getBgTilemapUpdateMode());
    }

    private static ZoneScrollHandler handler(int zone, int act, ParallaxTables tables) {
        var camera = new BackgroundCamera();
        camera.init(zone, act, 0x280, 0x80);
        return switch (zone) {
            case 0 -> new SwScrlEhz(tables);
            case 1 -> new SwScrlCpz(tables);
            case 2 -> new SwScrlArz(tables);
            case 3 -> new SwScrlCnz(tables);
            case 4 -> new SwScrlHtz(tables, camera);
            case 5 -> new SwScrlMcz(tables);
            case 6 -> new SwScrlOoz(tables);
            case 7 -> new SwScrlMtz(camera);
            case 8 -> new SwScrlScz();
            case 9 -> new SwScrlWfz(tables, camera);
            case 10 -> { var dez = new SwScrlDez(tables, camera); dez.triggerScreenShake(7); yield dez; }
            default -> throw new AssertionError(zone);
        };
    }

    private static ParallaxTables tables() {
        var tables = mock(ParallaxTables.class);
        byte[] ripple = new byte[66];
        for (int i = 0; i < ripple.length; i++) ripple[i] = (byte) (((i * 13) & 7) - 3);
        when(tables.getRippleData()).thenReturn(ripple);
        when(tables.getRippleDataLength()).thenReturn(ripple.length);
        when(tables.getRippleSigned(anyInt())).thenAnswer(call -> {
            int index = call.getArgument(0); return index >= 0 && index < ripple.length ? (int) ripple[index] : 0;
        });
        when(tables.getRippleByte(anyInt())).thenAnswer(call -> {
            int index = call.getArgument(0); return index >= 0 && index < ripple.length ? ripple[index] : (byte) 0;
        });
        when(tables.getMczRowHeights()).thenReturn(new byte[]{37, 23, 18, 7, 7, 2, 2, 48, 13, 19, 32, 64,
                32, 19, 13, 48, 2, 2, 7, 7, 32, 18, 23, 37});
        byte[] rows = new byte[32];
        for (int i = 0; i < 16; i++) { rows[i * 2] = (byte) 128; rows[i * 2 + 1] = (byte) ((i % 5) * 4); }
        when(tables.getWfzNormalArray()).thenReturn(rows);
        when(tables.getWfzTransArray()).thenReturn(rows.clone());
        return tables;
    }

    private static Stream<Arguments> handlers() {
        return IntStream.range(0, 11).boxed().flatMap(zone -> Stream.of(0, 1).flatMap(act ->
                (zone == 4 ? Stream.of(false, true) : Stream.of(false)).map(quake -> Arguments.of(zone, act, quake))));
    }
}
