package com.openggf.game.soniccd.zones.ttz;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.CdStageLoader;
import com.openggf.level.scroll.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class TestTtzImmutableScrollPhase {
    @ParameterizedTest
    @EnumSource(value = CdStagePayload.class, names = "R4.*", mode = EnumSource.Mode.MATCH_ALL)
    void discBackedPhaseReprojectsBandsAndRetainsDispatch(CdStagePayload payload) throws Exception {
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var image = new CdStageLoader(disc).readStage(payload);
            int[] water = {0x7fff};
            var handler = new TtzScrollHandler(payload, () -> water[0], () -> image);
            handler.init(0, 512, 300);
            int[] words = new int[224];
            handler.update(words, 528, 300, 1, 0);
            var nativeOutput = new ScrollProjection(words, null, null, null, (short)300,
                    handler.getVscrollFactorBG(), 0, 512, 0, 0, BgTilemapUpdateMode.STATIC_WINDOW);
            var context = new ScrollProjectionContext(528, 300, 1, 0, 320, 224, nativeOutput);
            var phase = handler.captureProjection(context);
            Object state = handler.captureRewindState();
            assertEquals(nativeOutput, phase.sample(new ScrollView(528, 300, 320, 224)));
            var shifted = phase.sample(new ScrollView(528, 364, 320, 224));
            int rows = shifted.backgroundY() - nativeOutput.backgroundY();
            assertTrue(rows > 0 && rows <= 64);
            for (int i = 0; i < 150; i++)
                assertEquals((short)words[i + rows], (short)shifted.horizontal()[i], "band at " + i);
            assertNotEquals(nativeOutput.backgroundY(), shifted.backgroundY(), "vertical camera projection");
            water[0] = 0;
            handler.update(new int[224], 544, 380, 2, 0);
            assertEquals(shifted, phase.sample(new ScrollView(528, 364, 320, 224)), "frozen phase");
            Object later = handler.captureRewindState();
            var wide = phase.sample(new ScrollView(528, 364, 640, 448));
            assertEquals(448, wide.horizontal().length);
            assertEquals(shifted.horizontal()[223], wide.horizontal()[447]);
            assertEquals(later, handler.captureRewindState(), "sampling cannot consume a clock");
            assertNotEquals(state, later, "native dispatch still advances");
            water[0] = 400;
            handler.update(words, 528, 390, 3, 0);
            var wetOutput = new ScrollProjection(words, null, null, null, (short)390,
                    handler.getVscrollFactorBG(), 0, 512, 0, 0, BgTilemapUpdateMode.STATIC_WINDOW);
            var wetPhase = handler.captureProjection(new ScrollProjectionContext(528, 390, 3, 0, 320, 224, wetOutput));
            water[0] = 0;
            assertEquals(wetOutput, wetPhase.sample(new ScrollView(528, 390, 320, 224)), "water split and wobble frozen");

        }
    }
}
