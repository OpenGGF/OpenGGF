package com.openggf.editor.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.game.GameId;
import com.openggf.level.Pattern;
import com.openggf.level.Chunk;
import com.openggf.level.Block;
import com.openggf.level.SolidTile;
import java.util.Arrays;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class TestEditorProjectValidation {
    static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path directory;

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidPayloads")
    void rejectsMalformedProjectWithoutChangingItsFile(String name, Consumer<ObjectNode> corrupt) throws Exception {
        Path file = directory.resolve("invalid.ogfproject");
        EditorProjectStore store = new EditorProjectStore();
        store.save(file, document());
        ObjectNode envelope = (ObjectNode) JSON.readTree(file.toFile());
        corrupt.accept((ObjectNode) envelope.get("payload"));
        resign(envelope);
        byte[] invalid = JSON.writeValueAsBytes(envelope);
        Files.write(file, invalid);
        assertThrows(IOException.class, () -> store.open(file), name);
        assertArrayEquals(invalid, Files.readAllBytes(file), "opening must never quarantine or rewrite projects");
    }

    static Stream<Object[]> invalidPayloads() {
        return Stream.of(
                invalid("missing pattern reference", p -> ((ArrayNode) p.path("level").path("chunks").get(0)).set(0, JSON.getNodeFactory().numberNode(2))),
                invalid("missing chunk reference", p -> ((ArrayNode) p.path("level").path("blocks").get(0)).set(0, JSON.getNodeFactory().numberNode(2))),
                invalid("missing solid reference", p -> ((ArrayNode) p.path("level").path("chunks").get(0)).set(4, JSON.getNodeFactory().numberNode(2))),
                invalid("negative descriptor", p -> ((ArrayNode) p.path("level").path("chunks").get(0)).set(0, JSON.getNodeFactory().numberNode(-1))),
                invalid("bad pattern pixel", p -> ((ArrayNode) p.path("level").path("patterns")).set(0, JSON.getNodeFactory().binaryNode(invalidPixel()))),
                invalid("bad map length", p -> ((ObjectNode) p.get("level")).put("map", new byte[1])),
                invalid("missing map block", p -> { byte[] cells = new byte[32]; cells[0] = 2; ((ObjectNode) p.get("level")).put("map", cells); }),
                invalid("bad solid widths", p -> ((ObjectNode) p.path("level").path("solids").get(0)).put("widths", new byte[15])),
                invalid("incomplete palette bank", p -> ((ArrayNode) p.path("level").path("palettes")).remove(3)),
                invalid("metadata/level bounds disagree", p -> ((ObjectNode) p.get("level")).put("maxX", 510)),
                invalid("bad palette RGB length", p -> ((ArrayNode) p.path("level").path("palettes")).set(0, JSON.getNodeFactory().binaryNode(new byte[47]))),
                invalid("duplicate object id", p -> ((ObjectNode) p.path("level").path("objects").get(1)).put("placementId", 7)),
                invalid("out-of-range coordinate before ObjectSpawn normalization", p -> ((ObjectNode) p.path("level").path("objects").get(1)).put("x", 65536)),
                invalid("object key owner mismatch", p -> ((ObjectNode) p.path("level").path("objects").get(1)).put("ownerModId", "other-owner")),
                invalid("missing backing object", p -> ((ObjectNode) p.path("level").path("ringBacking").get(0)).put("objectPlacementId", 50)),
                invalid("missing backing ring", p -> ((ObjectNode) p.path("level").path("ringBacking").get(0)).putArray("ringPlacementIds").add(500)),
                invalid("duplicate ring id", p -> ((ArrayNode) p.path("level").path("rings")).add(p.path("level").path("rings").get(0).deepCopy())),
                invalid("missing required field", p -> ((ObjectNode) p.get("level")).remove("layers")),
                invalid("oversized dimensions", p -> ((ObjectNode) p.get("level")).put("width", 513)),
                invalid("geometry/game mismatch", p -> ((ObjectNode) p.get("metadata")).put("gameCode", "s1")),
                invalid("invalid start", p -> ((ObjectNode) p.get("metadata")).put("startX", 1000)),
                invalid("conflicting music identity", p -> ((ObjectNode) p.path("metadata").path("music")).put("owner", "music-owner")),
                invalid("unknown payload field", p -> p.put("unexpected", true)),
                invalid("oversized pattern count", p -> { ArrayNode patterns = ((ObjectNode) p.get("level")).putArray("patterns");
                    for (int i = 0; i < 8193; i++) patterns.add(new byte[64]); })
        );
    }
    static byte[] invalidPixel() { byte[] pixels = new byte[64]; pixels[7] = 16; return pixels; }
    static Object[] invalid(String name, Consumer<ObjectNode> change) { return new Object[] {name, change}; }

    @Test
    void checksumVersionAndDuplicateFieldsAreRejected() throws Exception {
        Path file = directory.resolve("envelope.ogfproject");
        EditorProjectStore store = new EditorProjectStore(); store.save(file, document());
        byte[] valid = Files.readAllBytes(file);
        ObjectNode envelope = (ObjectNode) JSON.readTree(valid);
        ((ObjectNode) envelope.path("payload").path("metadata")).put("name", "Tampered");
        Files.write(file, JSON.writeValueAsBytes(envelope));
        assertThrows(IOException.class, () -> store.open(file));
        envelope = (ObjectNode) JSON.readTree(valid); envelope.put("version", 2);
        Files.write(file, JSON.writeValueAsBytes(envelope));
        assertThrows(IOException.class, () -> store.open(file));
        Files.writeString(file, "{\"version\":1,\"version\":1,\"format\":\"OpenGGFEditorProject\"}");
        assertThrows(IOException.class, () -> store.open(file));
        Files.writeString(file, new String(valid, java.nio.charset.StandardCharsets.UTF_8) + " {}");
        assertThrows(IOException.class, () -> store.open(file));
    }

    @Test
    void failedAtomicPublicationPreservesPreviousProjectAndDirtyDocument() throws Exception {
        EditorProjectDocument document = document(); Path file = directory.resolve("keep.ogfproject");
        EditorProjectStore good = new EditorProjectStore(); good.save(file, document);
        byte[] previous = Files.readAllBytes(file);
        document.level().getPattern(0).setPixel(0, 0, (byte) 7);
        EditorProjectStore failing = new EditorProjectStore((temporary, target) -> {
            assertTrue(Files.size(temporary) > 0, "failure occurs at actual filesystem publication boundary");
            throw new java.nio.file.AtomicMoveNotSupportedException(temporary.toString(), target.toString(), "unsupported provider");
        });
        assertThrows(IOException.class, () -> failing.save(file, document));
        assertArrayEquals(previous, Files.readAllBytes(file));
        assertTrue(document.isModified());
        assertEquals(file.toAbsolutePath(), document.path());
        try (var files = Files.list(directory)) { assertEquals(List.of(file), files.toList()); }
        assertEquals(0, good.open(file).level().getPattern(0).getPixel(0, 0));
    }

    @Test
    void invalidCurrentReferencesCannotReplacePreviousProject() throws Exception {
        EditorProjectDocument document = document(); Path file = directory.resolve("keep.ogfproject");
        EditorProjectStore store = new EditorProjectStore(); store.save(file, document);
        byte[] previous = Files.readAllBytes(file);
        document.level().getChunk(0).setSolidTileIndex(900);
        assertThrows(IllegalArgumentException.class, () -> store.save(file, document));
        assertArrayEquals(previous, Files.readAllBytes(file));
        assertTrue(document.isModified());
    }

    @Test
    void readRejectsOversizedFileBeforeDeserialization() throws Exception {
        Path file = directory.resolve("huge.ogfproject");
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(EditorProjectStore.MAX_BYTES); channel.write(ByteBuffer.wrap(new byte[] {0}));
        }
        IOException error = assertThrows(IOException.class, () -> new EditorProjectStore().open(file));
        assertTrue(error.getMessage().contains("64 MiB"));
        assertTrue(Files.exists(file));
    }

    @Test
    void namedMusicAndUnquantizedPalettesAreStoredWithoutPortableExportRestrictions() throws Exception {
        EditorProjectDocument document = document();
        document.setMetadata(document.metadata().withMusic(new FullLevelExporter.ExportMusic.Track("absent-mod", "theme")));
        document.level().getPalette(3).getColor(2).g = (byte) 19;
        Path file = directory.resolve("music.ogfproject");
        EditorProjectStore store = new EditorProjectStore(); store.save(file, document);
        EditorProjectDocument reopened = store.open(file);
        assertEquals(new FullLevelExporter.ExportMusic.Track("absent-mod", "theme"), reopened.metadata().music());
        assertEquals(19, reopened.level().getPalette(3).getColor(2).g);
    }

    @Test
    void fullPermittedPlacementTablesCanSaveAndReopenTogether() throws Exception {
        TestEditorProjects.Fixture source = new TestEditorProjects.Fixture(GameId.S2) {
            {
                patterns = Arrays.copyOf(patterns, 8192); patternCount = patterns.length;
                for (int i = 2; i < patterns.length; i++) patterns[i] = new Pattern();
                chunks = Arrays.copyOf(chunks, 4096); chunkCount = chunks.length;
                for (int i = 2; i < chunks.length; i++) chunks[i] = new Chunk();
                blocks = Arrays.copyOf(blocks, 256); blockCount = blocks.length;
                for (int i = 2; i < blocks.length; i++) blocks[i] = new Block(8);
                solidTiles = Arrays.copyOf(solidTiles, 1024); solidTileCount = solidTiles.length;
                for (int i = 2; i < solidTiles.length; i++) solidTiles[i] = new SolidTile(i, new byte[16], new byte[16], (byte) 0);
            }
        };
        EditorProjectDocument document = EditorProjectDocument.create(TestEditorProjects.metadata(GameId.S2, source), source);
        List<ObjectSpawn> objects = IntStream.range(0, 65535).mapToObj(i -> new ObjectSpawn(i, 64, 3, 0, 0, false, 64, i)).toList();
        List<RingSpawn> rings = IntStream.range(0, 65535).mapToObj(i -> new RingSpawn(i, 80, i)).toList();
        document.level().replaceSpawnsPersisted(objects, rings, java.util.Map.of());
        Path file = directory.resolve("maximum.ogfproject");
        EditorProjectStore store = new EditorProjectStore(); store.save(file, document);
        EditorProjectDocument reopened = store.open(file);
        assertEquals(65535, reopened.level().getObjects().size());
        assertEquals(65535, reopened.level().getRings().size());
        assertEquals(65534, reopened.level().getObjects().getLast().layoutIndex());
        assertEquals(65534, reopened.level().getRings().getLast().placementId());
    }

    static EditorProjectDocument document() {
        TestEditorProjects.Fixture source = new TestEditorProjects.Fixture(GameId.S2);
        return EditorProjectDocument.create(TestEditorProjects.metadata(GameId.S2, source), source);
    }
    static void resign(ObjectNode envelope) throws Exception {
        byte[] payload = JSON.writeValueAsBytes(envelope.get("payload"));
        envelope.put("checksum", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)));
    }
}
