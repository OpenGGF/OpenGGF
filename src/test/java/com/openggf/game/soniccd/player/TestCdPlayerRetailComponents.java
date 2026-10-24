package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStockLaunch;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdPlayerVisualAdapter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Supplied-disc component correspondence, not a player frame or native boot.
 * Literal standing contact agrees with the retained USA retail rest observation;
 * no observation file or reference state is opened or imported at test runtime.
 */
class TestCdPlayerRetailComponents {
    @ParameterizedTest(name = "{0}: disc-backed contact, math and Sonic visual views")
    @EnumSource(CdReleaseVariant.class)
    void authenticatedStartSurfaceAndVisualConsumersUseTheDetachedDiscAssets(CdReleaseVariant variant)
            throws Exception {
        Path cue = suppliedCue(variant);
        var source = CdStockLaunch.prepare(cue, CdStockLaunch.Request.r11a());
        var assets = source.assets(); // Preparation has already closed the native disc owner.
        assertEquals(variant, source.release().variant());
        assertTrue(source.rom().isEmpty());
        assertEquals(64, assets.loadPlan().initialCentre().centreX());
        assertEquals(1899, assets.loadPlan().initialCentre().centreY());

        // One explicit GroundCol call from the source-defined load position and
        // Init dimensions. This does not execute Init or the containing mode.
        var initial = new CdPlayerGroundCollision.State(64 << 16, 1899 << 16, 0, 0, 0);
        var contact = new CdPlayerGroundCollision(assets.terrain()).ground(
                new CdPlayerGroundCollision.Input(initial, 9, 19, 4, 0));
        assertEquals(64 << 16, contact.state().xPosition());
        assertEquals(1898 << 16, contact.state().yPosition());
        assertEquals(0xFC, contact.state().angle());
        assertEquals(0, contact.state().flags());
        assertEquals(0xFC, contact.primaryAngle());
        assertEquals(0, contact.secondaryAngle());
        assertEquals(1899 << 16, initial.yPosition(), "input must remain detached and immutable");

        var math = new CdPlayerMath(assets.player());
        assertEquals(new CdPlayerMath.SineCosine(0, 256), math.calcSine(0));
        assertEquals(115, math.calcSine(19).sine());
        assertEquals(-117, math.calcSine(147).sine(), "retain the shipped table asymmetry");
        assertEquals(0x20, math.calcAngle(1, 1));
        assertEquals(0xA0, math.calcAngle(Short.MIN_VALUE, Short.MIN_VALUE));
        assertEquals(0x100, math.calcAngle(Short.MAX_VALUE, -1));
        assertEquals(0, math.calcAngleByte(Short.MAX_VALUE, -1));

        var visual = CdPlayerVisualAdapter.artSet(assets.sonic(), 0x10000);
        assertEquals(198, visual.mappingFrames().size());
        assertEquals(2171, visual.artTiles().length);
        assertEquals(24, visual.bankSize());
        assertEquals(0x10000, visual.basePatternIndex());
        assertTrue(visual.dplcFrames().isEmpty(), "Draw must not select the native cue");
        assertFalse(assets.sonic().dplcFrames().get(1).pieces().isEmpty());
        assertEquals(0x220, assets.sonic().dplcFrames().get(1).outputBytes());

        // Explicit component ingress from the separately proven cold cut.
        // Frame one is a chosen service input, not a forced boot/animation cue.
        var art = new CdPlayerArtRuntime(assets.sonic(), new CdPlayerArtRuntime.State(
                new byte[0x300], new byte[0x300], 0, false));
        assertTrue(art.serviceVInt(0).isEmpty());
        assertEquals(0x2E0, art.serviceVInt(1).orElseThrow().bytes().length());
        var state = art.capture();
        assertEquals(1, state.lastFrame());
        assertFalse(state.dirty());
        for (int offset = 0x220; offset < 0x300; offset++) {
            assertEquals(0, state.cpuBuffer().unsignedByte(offset));
            assertEquals(0, state.residentBank().unsignedByte(offset));
        }
        var resident = CdPlayerVisualAdapter.residentPatterns(state);
        assertEquals(24, resident.length);
        boolean visiblePixel = false;
        for (var tile : resident) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) { visiblePixel |= tile.getPixel(x, y) != 0; }
            }
        }
        assertTrue(visiblePixel, "the selected native cue must contain actual sprite pixels");
    }

    private static Path suppliedCue(CdReleaseVariant variant) throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(),
                "Set soniccd.media.root to the supplied USA, Japan and Europe directories");
        String directory = switch (variant) {
            case USA -> "USA";
            case JAPAN -> "Japan";
            case EUROPE -> "Europe";
        };
        try (var entries = Files.list(Path.of(root).resolve(directory))) {
            var cues = entries.filter(path -> path.getFileName().toString()
                    .toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "configured release must contain exactly one CUE");
            return cues.getFirst();
        }
    }
}
