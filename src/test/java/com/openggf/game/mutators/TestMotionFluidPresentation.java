package com.openggf.game.mutators;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TestMotionFluidPresentation {
    @ParameterizedTest @ValueSource(ints={256,320,384,426,480})
    void everyVisibleTintPixelMatchesTheCapturedFluidRegionAndViewport(int width) {
        var region = new MotionMutatorRuntime.FluidRegion(105,220,96);
        var bands = MotionFluidRenderEffect.bands(region,80,180,width,224);
        assertFalse(bands.isEmpty()); assertTrue(bands.size() <= 193);
        for (var band : bands) {
            assertTrue(band.x() >= 0 && band.y() >= 0 && band.y() < 224 && band.x()+band.width() <= width);
            for (int x=band.x();x<band.x()+band.width();x++) assertTrue(region.contains(x+80,band.y()+180));
        }
    }
}
