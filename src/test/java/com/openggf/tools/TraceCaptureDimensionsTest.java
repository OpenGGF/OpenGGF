package com.openggf.tools;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceCaptureDimensionsTest {

    @ParameterizedTest
    @CsvSource({
            "320,1,320,224,NATIVE_4_3,SUPPORTED",
            "400,2,800,448,WIDE_16_9,SUPPORTED",
            "528,1,528,224,ULTRA_21_9,SMOKE",
            "800,3,2400,672,SUPER_32_9,EXPLORATORY"
    })
    void resolvesLogicalAndPhysicalDimensionsWithoutChangingNativeHeight(
            int logicalWidth, int scale, int physicalWidth, int physicalHeight,
            String aspect, String supportTier) {
        TraceCaptureDimensions dimensions = TraceCaptureDimensions.resolve(logicalWidth, scale);

        assertEquals(logicalWidth, dimensions.logicalWidth());
        assertEquals(224, dimensions.logicalHeight());
        assertEquals(scale, dimensions.scale());
        assertEquals(physicalWidth, dimensions.physicalWidth());
        assertEquals(physicalHeight, dimensions.physicalHeight());
        assertEquals(aspect, dimensions.aspect().name());
        assertEquals(supportTier, dimensions.supportTier());
    }

    @ParameterizedTest
    @CsvSource({"0,400,224", "-3,400,224"})
    void normalizesNonpositiveRuntimeScaleToOne(
            int requestedScale, int physicalWidth, int physicalHeight) {
        TraceCaptureDimensions dimensions =
                TraceCaptureDimensions.resolve(400, requestedScale);

        assertEquals(1, dimensions.scale());
        assertEquals(physicalWidth, dimensions.physicalWidth());
        assertEquals(physicalHeight, dimensions.physicalHeight());
    }
}
