package com.openggf.tools;

import com.openggf.data.RetailRomRevision;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real engine pixels for revised backgrounds and S2's graphics-owned resource overlays. */
class TestRetailRevisionRendering {
    record Route(RetailRomRevision revision, String property, String defaultName,
                 int zone, int nativeZone, String name) {
        @Override public String toString() { return revision + " " + name; }
        Path path() { return Path.of(System.getProperty(property, defaultName)).toAbsolutePath(); }
    }

    static Stream<Route> routes() {
        return Stream.of(
                new Route(RetailRomRevision.S1_REV00, "sonic1.rev00.rom.path", "s1-r0.gen", 0, 0, "GHZ1"),
                new Route(RetailRomRevision.S1_REV01, "sonic1.rom.path", "s1.gen", 0, 0, "GHZ1"),
                new Route(RetailRomRevision.S1_REV00, "sonic1.rev00.rom.path", "s1-r0.gen", 3, 1, "LZ1"),
                new Route(RetailRomRevision.S1_REV01, "sonic1.rom.path", "s1.gen", 3, 1, "LZ1"),
                new Route(RetailRomRevision.S2_REV00, "sonic2.rev00.rom.path", "s2-r0.gen", 4, 7, "HTZ1"),
                new Route(RetailRomRevision.S2_REV01, "sonic2.rom.path", "s2.gen", 4, 7, "HTZ1"),
                new Route(RetailRomRevision.S2_REV00, "sonic2.rev00.rom.path", "s2-r0.gen", 9, 6, "WFZ"),
                new Route(RetailRomRevision.S2_REV01, "sonic2.rom.path", "s2.gen", 9, 6, "WFZ"));
    }

    @ParameterizedTest
    @MethodSource("routes")
    void drawsTerrainAndNativePlayerFromTheSelectedOriginal(Route route) throws Exception {
        assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")),
                "This rendering witness requires explicitly selected surfaceless EGL");
        assumeTrue(Files.isRegularFile(route.path()), () -> "Missing original ROM: " + route.path());
        try (Rom original = new Rom()) {
            assertTrue(original.open(route.path().toString()));
            assertEquals(route.revision(), RetailRomRevision.detect(original));
        }
        // WFZ starts over empty sky on the Tornado ($60,$4CC). Its first native
        // fortress placements begin at $1A0; use the deck for the terrain witness.
        boolean fortress = route.name().equals("WFZ");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null,
                fortress ? 0x300 : null, fortress ? 0x480 : null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(route.path(), route.zone(), 0, settings);
            assertEquals(route.nativeZone(), GameServices.level().getCurrentLevel().getZoneIndex(),
                    "Capture zones use engine progression order, resources use native ROM IDs");
            for (int frame = 0; frame < 60; frame++) {
                session.step(null);
                session.renderFrame();
            }
            var terrain = session.render(false);
            var full = session.render(true);
            assertTrue(Arrays.stream(terrain.pixels()).distinct().count() > 8,
                    () -> route + " terrain and palette must produce a nonblank frame; mode="
                            + session.loop().getCurrentGameMode() + ", player="
                            + session.player().getCentreX() + "," + session.player().getCentreY()
                            + ", dead=" + session.player().getDead() + ", camera="
                            + GameServices.camera().getX() + "," + GameServices.camera().getY());
            int sx = session.player().getRenderCentreX() - GameServices.camera().getX();
            int sy = session.player().getRenderCentreY() - GameServices.camera().getY();
            int changed = 0;
            for (int y = Math.max(0, sy - 24); y < Math.min(full.height(), sy + 24); y++) {
                for (int x = Math.max(0, sx - 24); x < Math.min(full.width(), sx + 24); x++) {
                    if (full.argb(x, y) != terrain.argb(x, y)) changed++;
                }
            }
            assertTrue(changed > 100, "Native playable pixels near (" + sx + "," + sy + "): " + changed);
        }
    }
}
