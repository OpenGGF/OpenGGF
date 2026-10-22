package com.openggf.game.sonic1.scroll;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.scroll.*;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TestSonic1ScrollProjection {
    @BeforeEach void prepare() {
        TestEnvironment.resetAll();
        GameServices.configuration().setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
    }
    @AfterEach void cleanup() { SessionManager.clear(); }

    @ParameterizedTest(name = "{0} act {2}")
    @MethodSource("handlers")
    void nativeCoordinatesMatchEveryRegisterAndSamplingDoesNotAdvanceNativeState(
            String name, Supplier<ZoneScrollHandler> factory, int act) {
        ZoneScrollHandler live = factory.get(), control = factory.get();
        int x = 0x280, y = 0x1C8;
        for (int frame = 7; frame < 12; frame++) {
            x += 7; y += 3;
            live.update(new int[224], x, y, frame, act);
            control.update(new int[224], x, y, frame, act);
        }
        int[] words = new int[224], controlWords = new int[224];
        live.update(words, x, y, 12, act);
        control.update(controlWords, x, y, 12, act);
        var nativeOutput = output(live, words, y);
        var phase = ((ScrollProjectionSource) live).captureProjection(
                new ScrollProjectionContext(x, y, 12, act, 320, 224, nativeOutput));
        assertEquals(nativeOutput, phase.sample(new ScrollView(x, y, 320, 224)), name);
        assertArrayEquals(words, Arrays.copyOf(phase.sample(new ScrollView(x, y, 320, 597)).horizontal(), 224),
                "A taller raster must preserve the native top224 rows");
        var requested = new ScrollView(x + 131, y + 59, 333, 597);
        var sampled = phase.sample(requested);
        assertEquals(597, sampled.horizontal().length);
        phase.sample(new ScrollView(x - 19, y - 27, 511, 301));
        assertEquals(sampled, phase.sample(requested), "sampling order must not change phase");
        assertEquals(nativeOutput, output(live, words, y), "sampling must leave native registers unchanged");
        live.update(words, x + 41, y + 17, 13, act);
        control.update(controlWords, x + 41, y + 17, 13, act);
        assertEquals(output(control, controlWords, y + 17), output(live, words, y + 17),
                "the next native tick must match an unsampled control");
        assertEquals(nativeOutput, phase.sample(new ScrollView(x, y, 320, 224)),
                "a captured phase must survive later native updates");
    }

    private static ScrollProjection output(ZoneScrollHandler handler, int[] words, int cameraY) {
        short fg = handler.getVscrollFactorFG();
        if (fg == 0) fg = (short) cameraY;
        return new ScrollProjection(words, handler.getPerLineVScrollBG(), handler.getPerColumnVScrollBG(),
                handler.getPerColumnVScrollFG(), fg, handler.getVscrollFactorBG(), handler.getBgCameraX(),
                handler.getBgPeriodWidth(), handler.getShakeOffsetX(), handler.getShakeOffsetY(), handler.getBgTilemapUpdateMode());
    }

    private static Stream<Arguments> handlers() {
        record Case(String name, Supplier<ZoneScrollHandler> factory) { }
        return List.of(new Case("GHZ", SwScrlGhz::new), new Case("END", SwScrlEnd::new),
                new Case("LZ", SwScrlLz::new), new Case("MZ", SwScrlMz::new),
                new Case("SLZ", SwScrlSlz::new), new Case("SYZ", SwScrlSyz::new),
                new Case("SBZ", SwScrlSbz::new), new Case("FZ", UniformQuarterSpeedScroll::new))
                .stream().flatMap(c -> Stream.of(0, 1, 2).map(act -> Arguments.of(c.name(), c.factory(), act)));
    }
}
