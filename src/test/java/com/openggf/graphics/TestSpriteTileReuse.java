package com.openggf.graphics;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.graphics.SpritePresentation.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Identity counts measure eliminated Tile constructions, not CPU time or GPU throughput. */
class TestSpriteTileReuse {
    private static final HeadSatReplay HEAD_REPLAY = (host, entries, emitter) -> { };
    private static final PaletteReplay PALETTE_REPLAY = (host, palette) -> palette;
    private static final HeadMask HEAD_MASK = new HeadMask(0, 2, Set.of(1), List.of(new MaskPoint(0, 0)));
    private GraphicsManager graphics;

    @BeforeEach void setup() {
        GraphicsManager.destroyForReinit();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
    }

    @AfterEach void cleanup() { SessionManager.clear(); GraphicsManager.destroyForReinit(); }

    @Test void unchangedSceneReusesEveryTileWhileScrollingKeepsOnlyScreenAnchoredHud() {
        Frame idle = scene(null, 100, false);
        Frame stable = scene(idle, 100, false);
        assertEquals(160, idle.tiles().size());
        assertEquals(160, reused(idle, stable), "160/160 reused; no new Tiles on an unchanged preparation");
        Frame scrolling = scene(stable, 108, false);
        assertEquals(32, reused(stable, scrolling), "32/160 HUD tiles reused; all 128 world tiles moved");
        Frame debris = scene(scrolling, 116, true);
        assertEquals(32, reused(scrolling, debris), "new debris ordering/art cannot borrow earlier world tiles");
        Frame fresh = scene(null, 116, true);
        assertEquals(fresh, debris, "reuse must produce the same frame as a fresh preparation");
        assertEquals(0, reused(fresh, debris), "the legacy no-hint path retains no hidden previous owner");
        assertEquals(160, reused(idle, scene(idle, 100, false)), "retaining old snapshots remains safe");
    }

    private Frame scene(Frame previous, int cameraX, boolean debris) {
        return SpritePresentationRenderer.prepare(graphics, cameraX, 200, () -> {
            PatternDesc descriptor = new PatternDesc();
            SpritePresentation.layer(graphics, Layer.OBJECT);
            for (int i = 0; i < 128; i++) {
                int slot = debris ? 127 - i : i;
                graphics.renderPatternWithId(1000 + slot, descriptor, 100 + slot * 8, 224);
            }
            SpritePresentation.layer(graphics, Layer.HUD);
            for (int i = 0; i < 32; i++)
                graphics.renderPatternWithId(2000 + i, descriptor, cameraX + 8 + i * 8, 208);
        }, previous);
    }

    @ParameterizedTest(name = "changed {0} replaces the tile")
    @MethodSource("changedTiles")
    void everyScalarAndSubjectFieldUsesExactRecordEquality(String field, Tile changed) {
        Tile seed = seed();
        Frame original = raw(seed, null);
        Frame equal = raw(replace(seed, "subject", replace(seed.subject(), "id", new String(seed.subject().id()))), original);
        assertSame(original.tiles().getFirst(), equal.tiles().getFirst(), "equal independent Subject records can reuse");
        Frame next = raw(changed, original);
        assertEquals(changed, next.tiles().getFirst(), field);
        assertNotEquals(seed, changed, "positive control for " + field);
        assertNotSame(original.tiles().getFirst(), next.tiles().getFirst(), field);
        assertEquals(seed, original.tiles().getFirst(), "the retained frame remains exact");
    }

    static Stream<Arguments> changedTiles() {
        Tile seed = seed();
        var cases = new ArrayList<Arguments>();
        Map<String, Object> fields = Map.ofEntries(
                Map.entry("layer", Layer.PLAYER), Map.entry("patternId", 43), Map.entry("palette", 3),
                Map.entry("hFlip", true), Map.entry("vFlip", true), Map.entry("priority", true),
                Map.entry("x", 21f), Map.entry("y", 41f), Map.entry("width", 16f), Map.entry("height", 4f),
                Map.entry("priorityShader", true), Map.entry("occlusionMask", 7), Map.entry("ghost", false),
                Map.entry("ghostAlpha", .5f), Map.entry("rowStart", 2), Map.entry("rowEnd", 6));
        fields.forEach((name, value) -> cases.add(Arguments.of(name, replace(seed, name, value))));
        Subject subject = seed.subject();
        Map<String, Object> subjects = Map.of("id", "unknown-another-owner", "part", Part.APPENDAGE,
                "suppressed", true, "headScalePercent", 125, "head", replace(subject.head(), "piece", 2),
                "body", new Body(Role.TEAM, 11, 20), "paletteReplay", (PaletteReplay) (host, palette) -> palette + 1);
        subjects.forEach((name, value) -> cases.add(Arguments.of("subject." + name,
                replace(seed, "subject", replace(subject, name, value)))));
        Map<String, Object> heads = Map.ofEntries(
                Map.entry("mask", new HeadMask(1, 2, Set.of(1), List.of(new MaskPoint(0, 0)))),
                Map.entry("originX", 11), Map.entry("originY", 21), Map.entry("hFlip", true), Map.entry("vFlip", true),
                Map.entry("percent", 175), Map.entry("fragmentBase", 30001),
                Map.entry("patterns", Map.of(42, new PatternVersion(2, 0, 0, 0))),
                Map.entry("replay", (HeadSatReplay) (host, entries, emitter) -> { }));
        heads.forEach((name, value) -> cases.add(Arguments.of("subject.head." + name,
                replace(seed, "subject", replace(subject, "head", replace(subject.head(), name, value))))));
        for (var entry : Map.<String, Object>of("role", Role.BADNIK, "anchorX", 12, "anchorY", 22).entrySet())
            cases.add(Arguments.of("subject.body." + entry.getKey(), replace(seed, "subject",
                    replace(subject, "body", replace(subject.body(), entry.getKey(), entry.getValue())))));
        cases.add(Arguments.of("subject.head absent", replace(seed, "subject", replace(subject, "head", null))));
        cases.add(Arguments.of("subject.body absent", replace(seed, "subject", replace(subject, "body", null))));
        cases.add(Arguments.of("subject.paletteReplay absent", replace(seed, "subject", replace(subject, "paletteReplay", null))));
        return cases.stream();
    }

    private static Tile seed() {
        var head = new HeadTransform(HEAD_MASK,
                1, 10, 20, false, false, 150, 30000, Map.of(42, new PatternVersion(1, 0, 0, 0)),
                HEAD_REPLAY);
        return new Tile(Layer.OBJECT, 42, 2, false, false, false, 20, 40, 8, 8,
                false, 15, true, 1, 0, 8,
                new Subject("unknown-owner", Part.BODY, false, 150, head,
                        new Body(Role.LEADER, 10, 20), PALETTE_REPLAY));
    }

    /** Mutate one record component independently of the production comparator. */
    @SuppressWarnings("unchecked")
    private static <T extends Record> T replace(T record, String field, Object value) {
        try {
            var components = record.getClass().getRecordComponents();
            Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++)
                values[i] = components[i].getName().equals(field) ? value : components[i].getAccessor().invoke(record);
            var types = Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
            return (T) record.getClass().getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** Mock only scalar host state to exercise values (e.g. signed alpha zero) independently of setter clamps. */
    private Frame raw(Tile tile, Frame previous) {
        GraphicsManager host = mock(GraphicsManager.class);
        when(host.isUseSpritePriorityShader()).thenReturn(tile.priorityShader());
        when(host.getCurrentSpriteTileOcclusionPaletteMask()).thenReturn(tile.occlusionMask());
        when(host.isGhostRenderEffectActive()).thenReturn(tile.ghost());
        when(host.getGhostRenderAlpha()).thenReturn(tile.ghostAlpha());
        host.spritePresentationSubject = tile.subject();
        return SpritePresentation.prepare(host, 0, 0, () -> {
            SpritePresentation.layer(host, tile.layer());
            if (tile.rowClipped()) host.spritePresentationBuilder.addRows(host, tile.patternId(), tile,
                    (int) tile.x(), (int) tile.y(), tile.rowStart(), tile.rowEnd());
            else host.spritePresentationBuilder.add(host, tile.patternId(), tile,
                    tile.x(), tile.y(), tile.width(), tile.height());
        }, ignored -> new Attributes(tile.palette(), tile.hFlip(), tile.vFlip(), tile.priority()), previous);
    }

    @Test void nanAndSignedZeroMatchTileRecordEqualityForEveryFloatComponent() {
        for (String field : List.of("x", "y", "width", "height", "ghostAlpha")) {
            Tile firstNan = replace(seed(), field, Float.intBitsToFloat(0x7fc00001));
            Tile secondNan = replace(firstNan, field, Float.intBitsToFloat(0x7fc00002));
            Frame before = raw(firstNan, null);
            assertEquals(firstNan, secondNan);
            assertSame(before.tiles().getFirst(), raw(secondNan, before).tiles().getFirst(), field);
            Tile positiveZero = replace(seed(), field, 0f);
            Tile negativeZero = replace(positiveZero, field, -0f);
            Frame zero = raw(positiveZero, null);
            assertNotEquals(positiveZero, negativeZero);
            assertNotSame(zero.tiles().getFirst(), raw(negativeZero, zero).tiles().getFirst(), field);
        }
    }

    @Test void equalOpaqueHeadCallbacksMustKeepTheirIdentityAndBehavior() {
        var oldCalls = new java.util.concurrent.atomic.AtomicInteger();
        var newCalls = new java.util.concurrent.atomic.AtomicInteger();
        HeadSatReplay oldReplay = new EqualHeadReplay(oldCalls), newReplay = new EqualHeadReplay(newCalls);
        Tile before = replace(seed(), "subject", replace(seed().subject(), "head",
                replace(seed().subject().head(), "replay", oldReplay)));
        Tile input = replace(before, "subject", replace(before.subject(), "head",
                replace(before.subject().head(), "replay", newReplay)));
        assertEquals(before, input, "opaque callback equality can deliberately ignore behavior");
        Frame original = raw(before, null);
        assertSame(original.tiles().getFirst(), raw(before, original).tiles().getFirst());
        Tile prepared = raw(input, original).tiles().getFirst();
        assertNotSame(original.tiles().getFirst(), prepared);
        assertSame(newReplay, prepared.subject().head().replay());
        prepared.subject().head().replay().replay(graphics, List.of(), entry -> { });
        assertEquals(0, oldCalls.get()); assertEquals(1, newCalls.get());
    }

    @Test void equalOpaquePaletteCallbacksMustKeepTheirIdentityAndBehaviorWithoutAHead() {
        PaletteReplay oldReplay = new EqualPaletteReplay(2), newReplay = new EqualPaletteReplay(3);
        Subject subject = new Subject("palette-owner", Part.WORLD, false, 100, null, null, oldReplay);
        Tile before = replace(seed(), "subject", subject);
        Tile input = replace(before, "subject", replace(subject, "paletteReplay", newReplay));
        assertEquals(before, input, "opaque callback equality can deliberately ignore behavior");
        Frame original = raw(before, null);
        assertSame(original.tiles().getFirst(), raw(before, original).tiles().getFirst());
        Tile prepared = raw(input, original).tiles().getFirst();
        assertNotSame(original.tiles().getFirst(), prepared);
        assertSame(newReplay, prepared.subject().paletteReplay());
        assertEquals(3, prepared.subject().paletteReplay().palette(graphics, 0));
    }

    private record EqualHeadReplay(java.util.concurrent.atomic.AtomicInteger calls) implements HeadSatReplay {
        @Override public void replay(GraphicsManager graphics, List<SpriteSatEntry> entries,
                                     java.util.function.Consumer<SpriteSatEntry> emitter) { calls.incrementAndGet(); }
        @Override public boolean equals(Object other) { return other instanceof EqualHeadReplay; }
        @Override public int hashCode() { return 1; }
    }

    private record EqualPaletteReplay(int result) implements PaletteReplay {
        @Override public int palette(GraphicsManager graphics, int nativePalette) { return result; }
        @Override public boolean equals(Object other) { return other instanceof EqualPaletteReplay; }
        @Override public int hashCode() { return 1; }
    }

    @Test void rowClippingAndFlipsReuseOnlyTheirExactVisibleRows() {
        PatternDesc descriptor = new PatternDesc();
        descriptor.setHFlip(true); descriptor.setVFlip(true);
        Runnable rows = () -> graphics.renderPatternRowsWithId(42, descriptor, 0, true, true, false, 120, 240, -2, 6);
        Frame first = SpritePresentationRenderer.prepare(graphics, 100, 200, rows);
        Frame same = SpritePresentationRenderer.prepare(graphics, 100, 200, rows, first);
        assertSame(first.tiles().getFirst(), same.tiles().getFirst());
        assertEquals(0, first.tiles().getFirst().rowStart()); assertEquals(6, first.tiles().getFirst().rowEnd());
        Frame changed = SpritePresentationRenderer.prepare(graphics, 100, 200,
                () -> graphics.renderPatternRowsWithId(42, descriptor, 0, true, true, false, 120, 240, 2, 8), first);
        assertNotSame(first.tiles().getFirst(), changed.tiles().getFirst());
        assertEquals(2, changed.tiles().getFirst().rowStart()); assertEquals(8, changed.tiles().getFirst().rowEnd());
    }

    @Test void changedArtGenerationIsFrozenSeparatelyFromReusedTileAndPrimitiveBoundaries() {
        Pattern art = new Pattern(); art.setPixel(0, 0, (byte) 3);
        Runnable producer = () -> {
            SpritePresentationRenderer.bindPatternBank(graphics, 9000, new Pattern[]{art});
            graphics.renderPatternWithId(9000, new PatternDesc(), 10, 20);
            graphics.registerCommand(new GLCommand(GLCommand.CommandType.RECTI,
                    org.lwjgl.opengl.GL11.GL_QUADS, 1, 0, 0, 10, 20, 12, 22));
        };
        Frame first = SpritePresentationRenderer.prepare(graphics, 0, 0, producer);
        var retainedVersions = Map.copyOf(first.patternVersions());
        art.setPixel(0, 0, (byte) 7);
        Frame next = SpritePresentationRenderer.prepare(graphics, 0, 0, producer, first);
        assertSame(first.tiles().getFirst(), next.tiles().getFirst());
        assertEquals(retainedVersions, first.patternVersions());
        assertEquals(3, SpritePresentationRenderer.pattern(first.patternVersions().get(9000)).getPixel(0, 0));
        assertEquals(7, SpritePresentationRenderer.pattern(next.patternVersions().get(9000)).getPixel(0, 0));
        assertEquals(1, next.primitives().getFirst().beforeTile());
        assertEquals(first.primitives(), next.primitives());
        assertThrows(UnsupportedOperationException.class, () -> first.patternVersions().clear());
        assertThrows(UnsupportedOperationException.class, () -> next.tiles().clear());
    }

    @Test void changedCountsAndOrdinalsDoNotSearchForMatchingTilesElsewhere() {
        Frame original = scene(null, 100, false);
        Frame shorter = SpritePresentationRenderer.prepare(graphics, 100, 200, () -> {
            graphics.renderPatternWithId(1001, new PatternDesc(), 108, 224);
        }, original);
        assertEquals(original.tiles().get(1), shorter.tiles().getFirst());
        assertNotSame(original.tiles().get(1), shorter.tiles().getFirst());
        Frame empty = SpritePresentationRenderer.prepare(graphics, 0, 0, () -> { }, original);
        assertTrue(empty.tiles().isEmpty());
        Frame longer = scene(shorter, 100, false);
        assertEquals(original, longer);
        assertEquals(0, reused(original, longer));
    }

    @Test void composedHeadTopologyIsOnlyAHintAndCannotReplaceNativeInputOrArt() {
        Pattern art = new Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) art.setPixel(x, y, (byte) 3);
        var mask = new HeadMask(0, 4, Set.of(0), List.of(new MaskPoint(0, 0),
                new MaskPoint(8, 0), new MaskPoint(8, 4), new MaskPoint(0, 4)));
        var head = new HeadTransform(mask, 0, 10, 20, false, false, 150,
                PatternAtlasRange.PLAYER_PRESENTATION.base(), Map.of(), SpritePresentationRenderer.headSatReplay());
        Runnable producer = () -> {
            SpritePresentationRenderer.bindPatternBank(graphics, 9000, new Pattern[]{art});
            SpritePresentation.withSubject(graphics, new Subject("sonic", Part.BODY, false, 150, head),
                    () -> graphics.renderPatternWithId(9000, new PatternDesc(), 10, 20));
        };
        Frame retained = SpritePresentationRenderer.prepare(graphics, 0, 0, producer);
        assertEquals(2, retained.tiles().size(), "composition expands one native tile into body and head");
        var frozen = new Frame(retained.tiles(), retained.primitives(), retained.patternVersions());
        assertEquals(retained, SpritePresentationRenderer.prepare(graphics, 0, 0, producer, retained));
        art.setPixel(0, 0, (byte) 7);
        Frame changed = SpritePresentationRenderer.prepare(graphics, 0, 0, producer, retained);
        assertEquals(SpritePresentationRenderer.prepare(graphics, 0, 0, producer), changed,
                "composed ordinals must never contaminate subsequent native preparation");
        assertNotEquals(retained.patternVersions(), changed.patternVersions());
        assertEquals(frozen, retained);
    }

    @Test void failureRestoresCaptureScopesAndCannotMutatePreviousFrame() {
        Frame original = scene(null, 100, false);
        var snapshot = new Frame(original.tiles(), original.primitives(), original.patternVersions());
        assertThrows(IllegalStateException.class, () -> SpritePresentationRenderer.prepare(graphics, 100, 200, () -> {
            SpritePresentation.withSubject(graphics, seed().subject(), () -> {
                graphics.beginGhostRenderEffect(.25f);
                graphics.setUseSpritePriorityShader(true);
                graphics.setCurrentSpriteTileOcclusionPaletteMask(2);
                graphics.renderPatternWithId(1, new PatternDesc(), 0, 0);
                graphics.beginSpriteSatCollection();
                throw new IllegalStateException("failed preparation");
            });
        }, original));
        assertFalse(SpritePresentation.isPreparing(graphics)); assertFalse(graphics.isSpriteSatCollectionActive());
        assertEquals(Subject.WORLD, SpritePresentation.subject(graphics));
        assertFalse(graphics.isGhostRenderEffectActive()); assertFalse(graphics.isUseSpritePriorityShader());
        assertEquals(15, graphics.getCurrentSpriteTileOcclusionPaletteMask());
        assertEquals(snapshot, original);
        assertEquals(160, reused(original, scene(original, 100, false)));
    }

    private static int reused(Frame first, Frame next) {
        int count = 0;
        for (int i = 0; i < Math.min(first.tiles().size(), next.tiles().size()); i++)
            if (first.tiles().get(i) == next.tiles().get(i)) count++;
        return count;
    }
}
