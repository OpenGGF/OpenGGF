package com.openggf.tests.trace;

import com.openggf.trace.replay.Sonic2TornadoRidePrelude;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic2TornadoRidePrelude {

    @Test
    void derivesSczSeedsFromNativePreludeFormulas() {
        Sonic2TornadoRidePrelude.Seed seed = Sonic2TornadoRidePrelude.forZone("scz");

        assertEquals(0x5000, seed.playerYSubpixel());
        assertEquals(0xC0, seed.tornadoYSubpixel8());
    }

    @Test
    void derivesWfzSeedsFromNativePreludeFormulas() {
        Sonic2TornadoRidePrelude.Seed seed = Sonic2TornadoRidePrelude.forZone("wfz");

        assertEquals(0x3800, seed.playerYSubpixel());
        assertEquals(0, seed.tornadoYSubpixel8());
    }

    @Test
    void leavesOtherZonesClean() {
        Sonic2TornadoRidePrelude.Seed seed = Sonic2TornadoRidePrelude.forZone("ehz");

        assertEquals(0, seed.playerYSubpixel());
        assertEquals(0, seed.tornadoYSubpixel8());
    }
}
