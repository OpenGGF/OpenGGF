package com.openggf.game.sonic3k.objects;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;

/** Full ordinary-input HCZ1 routes across the supported viewport/donor product. */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("slow-suite")
class TestS3kHcz1CompatibilityRoutes {
    static Stream<Arguments> configurations() {
        return Stream.of(320, 400, 512, 640, 800)
                .flatMap(width -> Stream.of("off", "s1", "s2").map(donor -> Arguments.of(width, donor)));
    }

    @ParameterizedTest(name = "HCZ1 complete route width={0} donor={1}")
    @MethodSource("configurations")
    void completeRouteReachesProductionReload(int width, String donor) throws Exception {
        System.out.printf("HCZCONFIG width=%d donor=%s%n", width, donor);
        Hcz1Route.withConfiguration(width, donor, Hcz1Route::complete);
    }
}
