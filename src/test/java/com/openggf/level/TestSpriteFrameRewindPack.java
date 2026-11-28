package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.SpritePresentation.Layer;
import com.openggf.graphics.SpritePresentation.Subject;
import com.openggf.graphics.SpritePresentation.Tile;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The packed rewind form of the CPU sprite tables decodes to frames equal to the captured ones. */
class TestSpriteFrameRewindPack {
    private static final Subject LEADER = new Subject("player:1", SpritePresentation.Part.BODY, false);
    // Equal by value to LEADER, but a distinct identity that must survive a round trip.
    private static final Subject LEADER_TWIN = new Subject("player:1", SpritePresentation.Part.BODY, false);
    private static final Subject BADNIK = new Subject("object:7", SpritePresentation.Part.OBJECT_BODY, true, 150);
    private static final SpritePresentation.Geometry GEOMETRY = (x, y) -> null;

    @Test void roundTripReproducesEveryTileFieldSubjectIdentityAndPatternVersion() {
        var frame = richFrame(0);
        var decoded = new SpriteFrameRewindPack().pack(frame).decode();
        assertEquals(frame, decoded);
        assertSubjectsSame(frame, decoded);
        assertEquals(frame.patternVersions(), decoded.patternVersions());
        assertSame(frame.primitives().getFirst().primitive(), decoded.primitives().getFirst().primitive());
        assertEquals(SpritePresentation.Frame.empty(), new SpriteFrameRewindPack().pack(
                SpritePresentation.Frame.empty()).decode());
    }

    @Test void everyFieldIsPacked() {
        // One tile per varied field: a field the packing drops collapses two distinct frames.
        var base = tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 8, false, 0, false, 1f,
                0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL);
        var variants = List.of(
                tile(Layer.HUD_COUNTERS, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 8, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1235, 2, false, false, false, 10.5f, -3f, 8, 8, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 3, false, false, false, 10.5f, -3f, 8, 8, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, true, false, false, 10.5f, -3f, 8, 8, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, true, false, 10.5f, -3f, 8, 8, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, true, 10.5f, -3f, 8, 8, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.25f, -3f, 8, 8, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, 3f, 8, 8, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 16, 8, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 4, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 8, true, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 8, false, 9, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 8, false, 0, true, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 8, false, 0, false, .5f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 8, false, 0, false, 1f, 2, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 8, false, 0, false, 1f, 0, 6, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 8, false, 0, false, 1f, 0, 8, BADNIK, false, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 8, false, 0, false, 1f, 0, 8, LEADER, true, SpriteSatEntry.PixelOperation.NORMAL),
                tile(Layer.PLAYER, 0x1234, 2, false, false, false, 10.5f, -3f, 8, 8, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.SHADOW_15));
        for (var variant : variants) {
            assertNotEquals(base, variant);
            var frame = new SpritePresentation.Frame(List.of(base, variant));
            var decoded = new SpriteFrameRewindPack().pack(frame).decode();
            assertEquals(frame, decoded, () -> "packing lost a field of " + variant);
        }
        // Raw float bits: negative zero is a distinct record value.
        var negativeZero = new SpritePresentation.Frame(List.of(tile(Layer.OBJECT, 1, 0, false, false, false,
                -0f, 0f, 8, 8, false, 0, false, 1f, 0, 8, LEADER, false, SpriteSatEntry.PixelOperation.NORMAL)));
        assertEquals(negativeZero, new SpriteFrameRewindPack().pack(negativeZero).decode());
    }

    @Test void tablesRestoreEqualFramesAndKeepAliasesAndSubjects() {
        var tables = new LevelSpritePresentation.Tables();
        var first = richFrame(0);
        var second = richFrame(1);
        var counters = richFrame(2);
        tables.prepare(first);
        tables.publish();
        tables.prepare(second);
        tables.publishCounters(counters);
        tables.heldPicture = new LevelSpritePresentation.HeldPicture(richFrame(3), null);
        var saved = tables.capture();
        tables.prepare(richFrame(4));
        tables.publish();
        tables.publishCounters(richFrame(5));
        tables.heldPicture = null;
        tables.restore(saved);
        assertEquals(second, tables.prepared());
        assertEquals(first, tables.published());
        assertEquals(counters, tables.counters());
        assertEquals(richFrame(3), tables.heldPicture.sprites());
        assertSubjectsSame(first, tables.published());
        assertSubjectsSame(second, tables.prepared());
        assertEquals(saved, tables.capture(), "a restored generation captures equal to its snapshot");
        // A lag frame publishes nothing new: the prepared and displayed tables are one frame.
        tables.publish();
        var aliased = tables.capture();
        assertSame(aliased.preparedTiles(), aliased.publishedTiles());
        tables.prepare(richFrame(6));
        tables.publish();
        tables.restore(aliased);
        assertSame(tables.prepared(), tables.published(), "restore keeps the alias between tables");
    }

    @Test void heldCheckpointsAreUnaffectedByLaterLiveChangesAndShareUnchangedPieces() {
        var tables = new LevelSpritePresentation.Tables();
        List<LevelSpritePresentation.Tables.State> held = new ArrayList<>();
        List<SpritePresentation.Frame[]> expected = new ArrayList<>();
        for (int step = 0; step < 24; step++) {
            tables.publish();
            tables.prepare(richFrame(step));
            // HUD counters change rarely: an equal, newly built frame must share its storage.
            tables.publishCounters(richFrame(100 + step / 6));
            held.add(tables.capture());
            expected.add(new SpritePresentation.Frame[] {
                    tables.prepared(), tables.published(), tables.counters()});
        }
        assertTrue(held.get(1).counterTiles().sharesStorageWith(held.get(0).counterTiles()),
                "an unchanged table shares its packed arrays with the previous checkpoint");
        assertNotSame(held.get(1).counterTiles(), held.get(0).counterTiles());
        assertSame(held.get(1).publishedTiles(), held.get(0).preparedTiles(),
                "a published table packed as the previous prepared one is not packed again");
        // Rewind to each checkpoint in turn, replaying forward between restores.
        for (int i = held.size() - 1; i >= 0; i -= 5) {
            tables.restore(held.get(i));
            assertEquals(expected.get(i)[0], tables.prepared());
            assertEquals(expected.get(i)[1], tables.published());
            assertEquals(expected.get(i)[2], tables.counters());
            assertSubjectsSame(expected.get(i)[0], tables.prepared());
            tables.publish();
            tables.prepare(richFrame(1000 + i));
            tables.publishCounters(richFrame(100));
            tables.capture();
        }
        for (int i = 0; i < held.size(); i++) {
            assertEquals(expected.get(i)[0], held.get(i).prepared(), "checkpoint " + i);
            assertEquals(expected.get(i)[1], held.get(i).published(), "checkpoint " + i);
            assertEquals(expected.get(i)[2], held.get(i).counters(), "checkpoint " + i);
        }
    }

    @Nested
    @RequiresRom(SonicGame.SONIC_3K)
    class RealS3kTables {
        @Test void aizRewindRestoresEqualTablesWithSubjectsAndPatternVersions() throws Exception {
            var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
            var tables = GameServices.level().spritePresentationRenderer().spriteTables;
            for (int i = 0; i < 240; i++) fixture.stepFrame(false, false, false, i % 90 < 70, i % 45 == 0);
            SpritePresentation.Frame prepared = tables.prepared(), published = tables.published(),
                    counters = tables.counters();
            assertFalse(published.tiles().isEmpty());
            var registry = com.openggf.tests.TestEnvironment.activeGameplayMode().getRewindRegistry();
            var saved = registry.capture();
            for (int i = 0; i < 60; i++) {
                fixture.stepFrame(false, false, false, true, i % 20 == 0);
                if (i % 10 == 0) registry.capture();
            }
            registry.restore(saved);
            assertEquals(prepared, tables.prepared());
            assertEquals(published, tables.published());
            assertEquals(counters, tables.counters());
            assertEquals(published.patternVersions(), tables.published().patternVersions());
            assertSubjectsSame(published, tables.published());
            assertSubjectsSame(prepared, tables.prepared());
        }
    }

    private static void assertSubjectsSame(SpritePresentation.Frame expected, SpritePresentation.Frame actual) {
        assertEquals(expected.tiles().size(), actual.tiles().size());
        for (int i = 0; i < expected.tiles().size(); i++) {
            assertSame(expected.tiles().get(i).subject(), actual.tiles().get(i).subject(), "tile " + i);
        }
    }

    private static Tile tile(Layer layer, int pattern, int palette, boolean hFlip, boolean vFlip, boolean priority,
                             float x, float y, float width, float height, boolean priorityShader, int mask,
                             boolean ghost, float alpha, int rowStart, int rowEnd, Subject subject,
                             boolean ghostColour, SpriteSatEntry.PixelOperation operation) {
        return new Tile(layer, pattern, palette, hFlip, vFlip, priority, x, y, width, height, priorityShader, mask,
                ghost, alpha, rowStart, rowEnd, subject, ghostColour, operation);
    }

    /** A frame shaped like a real one: runs of tiles per subject, repeated styles, unsorted versions. */
    private static SpritePresentation.Frame richFrame(int seed) {
        List<Tile> tiles = new ArrayList<>();
        Subject[] subjects = {LEADER, LEADER_TWIN, BADNIK, Subject.WORLD};
        Layer[] layers = Layer.values();
        for (int i = 0; i < 180; i++) {
            Subject subject = subjects[(i / 12) % subjects.length];
            tiles.add(tile(layers[(i / 30) % layers.length], 0x6000 + i * 7 + seed, (i / 40) & 3,
                    (i & 1) != 0, (i & 8) != 0, (i & 16) != 0, i * 1.5f - 40 + seed, (i % 13) * 8 - .25f,
                    8, i % 50 == 0 ? 4 : 8, (i & 32) != 0, i % 60 == 0 ? 0x5 : 0, i % 70 == 69,
                    i % 70 == 69 ? .375f : 1f, i % 33 == 0 ? 3 : 0, i % 33 == 0 ? 7 : 8, subject,
                    i % 140 == 139, i % 90 == 89 ? SpriteSatEntry.PixelOperation.SHADOW_15
                            : SpriteSatEntry.PixelOperation.NORMAL));
        }
        Map<Integer, SpritePresentation.PatternVersion> versions = new LinkedHashMap<>();
        for (int id : new int[] {0x6100, 0x6005, 0x6377, 0x6010, 0x6200 + seed}) {
            versions.put(id, new SpritePresentation.PatternVersion(id * 31L + seed, -id, Long.MIN_VALUE + id, seed));
        }
        var primitives = List.of(new SpritePresentation.Primitive(3, Layer.OBJECT, GEOMETRY, BADNIK));
        return new SpritePresentation.Frame(tiles, primitives, versions);
    }
}
