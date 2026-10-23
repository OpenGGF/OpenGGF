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
    private CompetitiveHalfpipeRomData data;
    private final SpecialStageViewport viewport = SpecialStageViewport.fromLogicalWidth(640);

    @BeforeEach
    void setUp() throws Exception {
        GraphicsManager graphics = new GraphicsManager();
        graphics.initHeadless();
        data = new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
        renderer = new CompetitiveHalfpipeRenderer(data, graphics, viewport);
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

    @Test
    void nativeDisplayEventsNeitherSynthesizeMissingChildrenNorFilterAnExplicitlyDisplayedItem() {
        var pair = CompetitiveSpecialStagePlayerAccess.create();
        assertTrue(renderer.nativeSatEntries(pair.players(), new CompetitiveHalfpipeRenderFrame(List.of())).isEmpty(),
                "live actors alone cannot create HUD, actors or shadow display events");
        var ring = new PositionedRing(3, 250, 200, false);
        var body = event(CompetitiveHalfpipeRenderFrame.Type.ITEM, 40, 3, 72, 96, 3, 0x6322, 3, ring, null);
        List<SpriteSatEntry> bodyOnly = renderer.nativeSatEntries(pair.players(), new CompetitiveHalfpipeRenderFrame(List.of(body)));
        assertEquals(1, bodyOnly.size());
        assertEquals(viewport.innerH32OriginX() + 64, bodyOnly.getFirst().x());
        assertEquals(88, bodyOnly.getFirst().y());
        assertEquals(0x325, bodyOnly.getFirst().rawTileWordLow11());
        assertEquals(SpriteSatEntry.PixelOperation.NORMAL, bodyOnly.getFirst().pixelOperation());

        var shadow = event(CompetitiveHalfpipeRenderFrame.Type.ITEM_SHADOW, 41, 5, 90, 80, 0, 0xE23C, 3, null, null);
        List<SpriteSatEntry> both = renderer.nativeSatEntries(pair.players(), new CompetitiveHalfpipeRenderFrame(List.of(body, shadow)));
        assertEquals(2, both.size());
        assertEquals(SpriteSatEntry.PixelOperation.SHADOW_15, both.getLast().pixelOperation());
        assertEquals(viewport.innerH32OriginX() + 74, both.getLast().x());
        assertEquals(72, both.getLast().y());
        assertEquals(0x25A, both.getLast().rawTileWordLow11());
    }

    @Test
    void nativeQueuePreservesSameBucketEnqueueOrderRatherThanPhysicalSlotOrder() {
        var ring = new PositionedRing(0, 128, 100);
        var laterSlotFirst = event(CompetitiveHalfpipeRenderFrame.Type.ITEM, 100, 3, 40, 100, 1, 0x6322, 3, ring, null);
        var earlierSlotLast = event(CompetitiveHalfpipeRenderFrame.Type.ITEM, 8, 3, 180, 100, 2, 0x6322, 3, ring, null);
        var input = new ArrayList<>(List.of(laterSlotFirst, earlierSlotLast));
        var frame = new CompetitiveHalfpipeRenderFrame(input);
        input.clear();
        List<SpriteSatEntry> entries = renderer.nativeSatEntries(List.of(), frame);
        assertEquals(List.of(viewport.innerH32OriginX() + 36, viewport.innerH32OriginX() + 176),
                entries.stream().map(SpriteSatEntry::x).toList());
        assertEquals(List.of(0x323, 0x324), entries.stream().map(SpriteSatEntry::rawTileWordLow11).toList());
        assertThrows(UnsupportedOperationException.class, () -> frame.draws().clear());
        var laterBucket = event(CompetitiveHalfpipeRenderFrame.Type.HUD, 4, 5, 0, 0, 0, 0, 0, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> new CompetitiveHalfpipeRenderFrame(List.of(laterBucket, earlierSlotLast)));
    }

    @Test
    void nativeSpillUsesFrozenScreenPositionsAndNormalRingArtInItsActualDisplayBucket() {
        var particle = new CompetitiveHalfpipeSpill(data, 4, 20, 0x200, -0x400);
        var rising = event(CompetitiveHalfpipeRenderFrame.Type.SPILL, 90, 0, 80, 100, 0, 0x6322, 3, null, particle);
        var falling = event(CompetitiveHalfpipeRenderFrame.Type.SPILL, 8, 5, 150, 120, 1, 0x6322, 3, null, particle);
        renderer.setGeometryOffset(19, -30);
        particle.tick();
        List<SpriteSatEntry> entries = renderer.nativeSatEntries(List.of(), new CompetitiveHalfpipeRenderFrame(List.of(rising, falling)));
        assertEquals(List.of(0, 5), entries.stream().map(SpriteSatEntry::priorityBucket).toList());
        assertEquals(List.of(viewport.innerH32OriginX() + 76, viewport.innerH32OriginX() + 146),
                entries.stream().map(SpriteSatEntry::x).toList());
        assertEquals(List.of(96, 116), entries.stream().map(SpriteSatEntry::y).toList());
        assertEquals(List.of(0x322, 0x323), entries.stream().map(SpriteSatEntry::rawTileWordLow11).toList());
        assertTrue(entries.stream().allMatch(entry -> entry.pixelOperation() == SpriteSatEntry.PixelOperation.NORMAL
                && entry.paletteIndex() == 3 && "spill".equals(entry.debugSource())));
        var body = event(CompetitiveHalfpipeRenderFrame.Type.ITEM, 9, 3, 80, 100, 0, 0x6322, 3,
                new PositionedRing(0, 80, 100), null);
        assertEquals(renderer.nativeSatEntries(List.of(), new CompetitiveHalfpipeRenderFrame(List.of(body)))
                .getFirst().firstPatternIndex(), entries.getFirst().firstPatternIndex());
    }

    @Test
    void nativeCounterEventsUseFrozenValuesAndWinMarksExistOnlyWhenExplicitlyDisplayed() {
        var pair = CompetitiveSpecialStagePlayerAccess.create();
        for (int i = 0; i < 8; i++) pair.collectRing(0);
        for (int i = 0; i < 9; i++) pair.collectRing(1);
        renderer.setSectorWins(3, 3);
        var counters = event(CompetitiveHalfpipeRenderFrame.Type.COUNTERS, 4, 0, 1, 23, 0, 0, 2, null, null);
        var wins = event(CompetitiveHalfpipeRenderFrame.Type.WIN_COUNTERS, 8, 0, 2, 1, 0, 0, 2, null, null);
        List<SpriteSatEntry> digits = renderer.nativeSatEntries(pair.players(), new CompetitiveHalfpipeRenderFrame(List.of(counters)));
        assertEquals(List.of(0x20E, 0x210, 0x212), digits.stream().map(SpriteSatEntry::rawTileWordLow11).toList());
        assertEquals(List.of(0x48, 0xE0, 0xE8), digits.stream()
                .map(entry -> entry.x() - viewport.innerH32OriginX() + 4).toList());
        List<SpriteSatEntry> combined = renderer.nativeSatEntries(pair.players(), new CompetitiveHalfpipeRenderFrame(List.of(counters, wins)));
        assertEquals(6, combined.size());
        assertEquals(List.of(0x20, 0x30, 0xB8), combined.subList(3, 6).stream()
                .map(entry -> entry.x() - viewport.innerH32OriginX() + 8).toList());
        assertEquals(8, pair.player(0).getRings());
        assertEquals(9, pair.player(1).getRings());
    }

    @Test
    void nativePlayerConsumesTheExplicitPaletteAndCombinesTheWholeRomMappingAndArtWords() {
        var pair = CompetitiveSpecialStagePlayerAccess.create();
        var sonic = event(CompetitiveHalfpipeRenderFrame.Type.PLAYER, 0, 3, 0, 0, 0, 0x22E5, 1, null, null);
        var recolored = event(CompetitiveHalfpipeRenderFrame.Type.PLAYER, 0, 3, 0, 0, 0, 0x22E5, 2, null, null);
        var overflow = event(CompetitiveHalfpipeRenderFrame.Type.PLAYER, 0, 3, 0, 0, 0, 0xA2E5, 1, null, null);
        List<SpriteSatEntry> normal = renderer.nativeSatEntries(pair.players(), new CompetitiveHalfpipeRenderFrame(List.of(sonic)));
        List<SpriteSatEntry> changed = renderer.nativeSatEntries(pair.players(), new CompetitiveHalfpipeRenderFrame(List.of(recolored)));
        List<SpriteSatEntry> carried = renderer.nativeSatEntries(pair.players(), new CompetitiveHalfpipeRenderFrame(List.of(overflow)));
        assertEquals(3, normal.size());
        assertEquals(List.of(0x2E5, 0x2F5, 0x2FE), normal.stream().map(SpriteSatEntry::rawTileWordLow11).toList());
        assertTrue(normal.stream().allMatch(piece -> piece.paletteIndex() == 1 && piece.piecePriority()));
        assertTrue(changed.stream().allMatch(piece -> piece.paletteIndex() == 2 && piece.piecePriority()),
                "the Draw palette is authoritative even when its art word still carries palette one");
        assertTrue(carried.stream().allMatch(piece -> piece.paletteIndex() == 1 && !piece.piecePriority()),
                "ROM mapping 8000 plus art A2E5 wraps to 22E5, clearing rather than OR-ing priority");
        for (int i = 0; i < normal.size(); i++) {
            SpriteSatEntry before = normal.get(i), after = changed.get(i), wrapped = carried.get(i);
            assertEquals(before.firstPatternIndex(), after.firstPatternIndex());
            assertEquals(before.firstPatternIndex(), wrapped.firstPatternIndex());
            assertEquals(before.rawTileWordLow11(), wrapped.rawTileWordLow11());
            assertEquals(before.x(), after.x()); assertEquals(before.y(), after.y());
            assertEquals(before.widthTiles(), after.widthTiles()); assertEquals(before.heightTiles(), after.heightTiles());
            assertEquals(before.hFlip(), after.hFlip()); assertEquals(before.vFlip(), after.vFlip());
        }
    }

    private static CompetitiveHalfpipeRenderFrame.Draw event(CompetitiveHalfpipeRenderFrame.Type type,
            int slot, int bucket, int x, int y, int mappingFrame, int nativeArt, int palette,
            Sonic2SpecialStageObject item, CompetitiveHalfpipeSpill spill) {
        return new CompetitiveHalfpipeRenderFrame.Draw(type, slot, bucket, 0, x, y, mappingFrame,
                nativeArt, palette, false, item, spill);
    }

    private static final class PositionedRing extends Sonic2SpecialStageRing {
        PositionedRing(int animation, int x, int y) {
            this(animation, x, y, true);
        }
        PositionedRing(int animation, int x, int y, boolean visible) {
            initialize(8, 0x80); // Native negative angle creates no shadow child.
            animIndex = animation;
            screenX = x;
            screenY = y;
            onScreen = visible;
        }
    }
}
