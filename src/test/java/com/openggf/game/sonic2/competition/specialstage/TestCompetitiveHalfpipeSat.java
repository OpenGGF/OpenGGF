package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.game.SpecialStageViewport;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageObject;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageRing;
import com.openggf.game.sonic2.specialstage.CompetitiveSpecialStagePlayerAccess;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.SpriteSatMaskPostProcessor;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Retail mapping geometry reaches the production H32 admission algorithm without an ordinary SS manager. */
@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipeSat {
    private CompetitiveHalfpipeRenderer renderer;
    private final SpecialStageViewport viewport = SpecialStageViewport.fromLogicalWidth(640);

    @BeforeEach
    void setUp() throws Exception {
        GraphicsManager graphics = new GraphicsManager();
        graphics.initHeadless();
        renderer = new CompetitiveHalfpipeRenderer(new CompetitiveHalfpipeRomData(TestEnvironment.currentRom()),
                graphics, viewport);
        renderer.initialize(4);
    }

    @AfterEach
    void tearDown() {
        if (renderer != null) renderer.reset();
    }

    @Test
    void nativeHudHasTwoSeparateCountersAndNoTotalGeometry() {
        List<SpriteSatEntry> entries = renderer.satEntries(0, List.of(), List.of(), 7, 123,
                CompetitiveHalfpipeRenderer.Overlay.none());
        assertEquals(12, entries.size(), "8 native label pieces plus 1 and 3 digit pieces");
        assertTrue(entries.stream().allMatch(entry -> entry.priorityBucket() == 0));
        assertEquals(List.of(0x1FA, 0x1FE, 0x204, 0x236, 0x7A4, 0x7A8, 0x204, 0x236),
                entries.subList(0, 8).stream().map(SpriteSatEntry::rawTileWordLow11).toList());
        assertEquals(List.of(0x48, 0xE0, 0xE8, 0xF0), entries.subList(8, 12).stream()
                .map(entry -> entry.x() - viewport.innerH32OriginX() + 4).toList());
        assertTrue(entries.subList(8, 12).stream().allMatch(entry -> entry.y() == 0x18
                && entry.widthTiles() == 1 && entry.heightTiles() == 2));
    }

    @Test
    void checkpointWinsUseTheNativeObj87RingMarkMappingAndSeparatePlayerRows() {
        renderer.setSectorWins(2, 1);
        List<SpriteSatEntry> entries = renderer.satEntries(0, List.of(), List.of(), 0, 0,
                CompetitiveHalfpipeRenderer.Overlay.none());
        assertEquals(13, entries.size());
        List<SpriteSatEntry> marks = entries.subList(10, 13);
        assertEquals(List.of(0x20, 0x30, 0xB8), marks.stream()
                .map(entry -> entry.x() - viewport.innerH32OriginX() + 8).toList());
        assertTrue(marks.stream().allMatch(entry -> entry.y() == 0x28 && entry.widthTiles() == 2
                && entry.heightTiles() == 1 && entry.paletteIndex() == 2 && entry.priorityBucket() == 0
                && entry.rawTileWordLow11() == 0x234));
        assertThrows(IllegalArgumentException.class, () -> renderer.setSectorWins(4, 0));
        assertThrows(IllegalArgumentException.class, () -> renderer.setSectorWins(0, -1));
        renderer.setSectorWins(0, 0);
        assertEquals(10, renderer.satEntries(0, List.of(), List.of(), 0, 0,
                CompetitiveHalfpipeRenderer.Overlay.none()).size());
    }

    @Test
    void widePresentationRetainsTheNativeSixteenSpriteScanlineLimit() {
        List<Sonic2SpecialStageObject> rings = new ArrayList<>();
        for (int i = 0; i < 20; i++) rings.add(new PositionedRing(0, 128, 100));
        List<SpriteSatEntry> entries = renderer.satEntries(0, List.of(), rings, 0, 0,
                CompetitiveHalfpipeRenderer.Overlay.none());
        List<SpriteSatEntry> admitted = SpriteSatMaskPostProcessor.process(entries, false,
                SpriteSatMaskPostProcessor.Raster.h32(viewport.innerH32OriginX(), 0, 224));
        assertEquals(16, admitted.stream().filter(entry -> "ring".equals(entry.debugSource())).count());
        assertEquals(20, SpriteSatMaskPostProcessor.process(entries, false,
                SpriteSatMaskPostProcessor.Raster.h40(viewport.innerH32OriginX(), 0, 224)).stream()
                .filter(entry -> "ring".equals(entry.debugSource())).count());
    }

    @Test
    void nativeTableAndPixelBudgetsUseMappingPiecesBeforeTileExpansion() {
        List<Sonic2SpecialStageObject> rings = new ArrayList<>();
        for (int i = 0; i < 70; i++) rings.add(new PositionedRing(0, 128, 64 + i % 10 * 12));
        List<SpriteSatEntry> table = renderer.satEntries(0, List.of(), rings, 0, 0,
                CompetitiveHalfpipeRenderer.Overlay.none());
        List<SpriteSatEntry> admitted = SpriteSatMaskPostProcessor.process(table, false,
                SpriteSatMaskPostProcessor.Raster.h32(viewport.innerH32OriginX(), 0, 224));
        assertEquals(64, admitted.size(), "10 HUD pieces precede 54 ring pieces in the 64-entry native table");

        rings.clear();
        for (int i = 0; i < 16; i++) rings.add(new PositionedRing(9, 128, 100));
        List<SpriteSatEntry> pixels = renderer.satEntries(0, List.of(), rings, 0, 0,
                CompetitiveHalfpipeRenderer.Overlay.none());
        List<SpriteSatEntry> pixelAdmitted = SpriteSatMaskPostProcessor.process(pixels, false,
                SpriteSatMaskPostProcessor.Raster.h32(viewport.innerH32OriginX(), 0, 224));
        List<SpriteSatEntry> visibleRings = pixelAdmitted.stream()
                .filter(entry -> "ring".equals(entry.debugSource())).toList();
        assertEquals(11, visibleRings.size(), "10 three-tile pieces and two columns of the 11th exhaust H32");
        assertEquals(32, visibleRings.stream().mapToInt(SpriteSatEntry::colCountTiles).sum());
        assertEquals(2, visibleRings.getLast().colCountTiles());
    }

    @Test
    void everyCompetitivePhraseAndStartPieceReferencesActualResidentRomArt() {
        for (int phrase : new int[]{8, 10, 12, 14, 16, 18, 20}) {
            var overlay = new CompetitiveHalfpipeRenderer.Overlay(true, 0x48, phrase, true, 0x1C, 2,
                    List.of(new CompetitiveHalfpipeRenderer.Rainbow(0x80, 0x40, 8)),
                    List.of(new CompetitiveHalfpipeRenderer.BannerPart(8, 0x80, 0x48)));
            List<SpriteSatEntry> entries = renderer.satEntries(0, List.of(), List.of(), 0, 0, overlay);
            assertTrue(entries.size() > 20);
            assertTrue(entries.stream().allMatch(entry -> PatternAtlasRange.SPECIAL_STAGE_PLAYFIELD.contains(entry.firstPatternIndex())));
            if (phrase == 8) {
                assertTrue(entries.stream().anyMatch(entry -> entry.rawTileWordLow11() >= 0x3DF
                        && entry.rawTileWordLow11() <= 0x3EB),
                        "PLAYER/VS reaches the separate 13-tile ROM block");
            }
        }
    }

    @Test
    void nativeActorsAppendageAndShadowOperatorsKeepRomBucketsAndGeometryAcrossRestore() {
        var pair = CompetitiveSpecialStagePlayerAccess.create();
        List<SpriteSatEntry> original = renderer.satEntries(0, pair.players(), List.of(), 0, 0,
                CompetitiveHalfpipeRenderer.Overlay.none());
        assertEquals(2, original.stream().filter(entry -> entry.pixelOperation() == SpriteSatEntry.PixelOperation.SHADOW_15).count());
        assertTrue(original.stream().filter(entry -> entry.pixelOperation() == SpriteSatEntry.PixelOperation.SHADOW_15)
                .allMatch(entry -> entry.paletteIndex() == 3 && entry.priorityBucket() == 4));
        assertTrue(original.stream().anyMatch(entry -> "tails-tail".equals(entry.debugSource()) && entry.priorityBucket() == 1));
        for (int i = 1; i < original.size(); i++) assertTrue(original.get(i - 1).priorityBucket() <= original.get(i).priorityBucket());

        var state = pair.capture();
        pair.step(0, 4, 0x40, 5);
        pair.step(1, 8, 0x40, 5);
        pair.restore(state);
        assertEquals(original, renderer.satEntries(0, pair.players(), List.of(), 0, 0,
                CompetitiveHalfpipeRenderer.Overlay.none()));

        renderer.setGeometryOffset(13, -10);
        List<SpriteSatEntry> shifted = renderer.satEntries(0, pair.players(), List.of(), 0, 0,
                CompetitiveHalfpipeRenderer.Overlay.none());
        for (int i = 0; i < original.size(); i++) {
            SpriteSatEntry before = original.get(i), after = shifted.get(i);
            if (before.priorityBucket() == 0) assertEquals(before, after, "HUD remains in native screen coordinates");
            else {
                assertEquals(before.x() + 13, after.x());
                assertEquals(before.y() - 10, after.y());
            }
        }
        assertEquals(0x80, pair.player(0).getXPos(), "presentation offsets do not mutate either logical actor");
    }

    private static final class PositionedRing extends Sonic2SpecialStageRing {
        PositionedRing(int animation, int x, int y) {
            initialize(8, 0x80); // Native negative angle creates no shadow child.
            animIndex = animation;
            screenX = x;
            screenY = y;
            onScreen = true;
        }
    }
}
