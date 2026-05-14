package com.openggf.tests.trace.s2;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.nio.file.Path;

@RequiresRom(SonicGame.SONIC_2)
abstract class AbstractS2LevelSelectTraceReplayTest extends AbstractTraceReplayTest {

    private final String route;
    private final int zone;

    AbstractS2LevelSelectTraceReplayTest(String route, int zone) {
        this.route = route;
        this.zone = zone;
    }

    @Override
    protected SonicGame game() {
        return SonicGame.SONIC_2;
    }

    @Override
    protected int zone() {
        return zone;
    }

    @Override
    protected int act() {
        return 0;
    }

    @Override
    protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s2").resolve(route);
    }
}
