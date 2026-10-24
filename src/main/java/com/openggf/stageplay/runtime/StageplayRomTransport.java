package com.openggf.stageplay.runtime;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.openggf.data.PhysicalImage;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomFingerprint;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.stageplay.StageplayDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal process transport for exact catalogue-selected ROM views. Only bounded
 * file descriptors cross the process boundary: no ROM is written, joined or copied
 * to another file. Child readers retain the catalogue's window/concatenation seams.
 */
public final class StageplayRomTransport {
    public static final int MAX_DESCRIPTOR_CHARS = 128 * 1024;
    private static final long MAX_IMAGE_BYTES = 64L * 1024 * 1024;
    private static final int MAX_PARTS = 8;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxDocumentLength(MAX_DESCRIPTOR_CHARS)
                    .maxStringLength(4096).maxNameLength(32).maxNestingDepth(8)
                    .maxTokenCount(4096).maxNumberLength(20).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private StageplayRomTransport() { }

    /** All source games consumed by sets, cast and ROM audio, in stable first-use order. */
    public static List<String> requiredGames(StageplayDocument document) {
        var games = new LinkedHashSet<String>();
        games.add(document.set().gameId());
        document.sceneChanges().forEach(change -> games.add(change.set().gameId()));
        document.actors().forEach(actor -> games.add(actor.gameId()));
        document.objectActors().forEach(actor -> games.add(actor.gameId()));
        document.audio().stream().filter(cue -> cue.kind() == StageplayDocument.AudioKind.ROM_MUSIC
                || cue.kind() == StageplayDocument.AudioKind.ROM_SFX).forEach(cue -> games.add(cue.gameId()));
        return List.copyOf(games);
    }

    public static String encode(Map<String, RomImageCatalogue.Resolution> resolutions) throws IOException {
        var root = JSON.createObjectNode().put("version", 1);
        var games = root.putObject("games");
        if (resolutions.isEmpty() || resolutions.size() > 3) throw invalid("Supply one to three stock ROMs");
        for (var entry : resolutions.entrySet()) {
            var resolution = entry.getValue();
            if (resolution.rom() != identity(entry.getKey())) throw invalid("Game/ROM identity mismatch");
            if (resolution.parts().isEmpty() || resolution.parts().size() > MAX_PARTS) throw invalid("Invalid part count");
            if (resolution.parts().stream().mapToLong(RomImageCatalogue.Part::length).sum() > MAX_IMAGE_BYTES)
                throw invalid("Logical ROM exceeds limit");
            var game = games.putObject(entry.getKey()).put("rom", resolution.rom().name());
            var parts = game.putArray("parts");
            for (var part : resolution.parts()) {
                var path = part.image().path().toAbsolutePath().normalize();
                checkWindow(path, part.image().size(), part.offset(), part.length());
                parts.addObject().put("rom", part.rom().name()).put("path", path.toString())
                        .put("size", part.image().size()).put("offset", part.offset()).put("length", part.length())
                        .put("sha1", part.image().fingerprint(part.offset(), part.length()).sha1Hex());
            }
        }
        String encoded = JSON.writeValueAsString(root);
        if (encoded.length() > MAX_DESCRIPTOR_CHARS) throw invalid("Descriptor exceeds limit");
        return encoded;
    }

    /** Opens only the supplied views, without consulting configuration or rescanning directories. */
    public static Map<String, RomByteReader> open(String encoded) throws IOException {
        if (encoded == null || encoded.length() > MAX_DESCRIPTOR_CHARS) throw invalid("Missing/big descriptor");
        JsonNode root = JSON.readTree(encoded);
        fields(root, Set.of("version", "games"));
        if (!root.path("version").isInt() || root.path("version").intValue() != 1) throw invalid("Unknown version");
        JsonNode games = root.get("games");
        if (!games.isObject() || games.isEmpty() || games.size() > 3) throw invalid("Invalid games");
        var images = new LinkedHashMap<Path, PhysicalImage>();
        var readers = new LinkedHashMap<String, RomByteReader>();
        var entries = games.fields();
        while (entries.hasNext()) {
            var entry = entries.next();
            var game = entry.getValue();
            fields(game, Set.of("rom", "parts"));
            if (!text(game.get("rom")).equals(identity(entry.getKey()).name())) throw invalid("Game/ROM identity mismatch");
            var parts = game.get("parts");
            if (!parts.isArray() || parts.isEmpty() || parts.size() > MAX_PARTS) throw invalid("Invalid part count");
            var views = new ArrayList<RomByteReader>();
            long total = 0;
            for (var part : parts) {
                fields(part, Set.of("rom", "path", "size", "offset", "length", "sha1"));
                try { RomIdentity.valueOf(text(part.get("rom"))); }
                catch (IllegalArgumentException failure) { throw invalid("Unknown part identity"); }
                Path path;
                try { path = Path.of(text(part.get("path"))); }
                catch (IllegalArgumentException failure) { throw invalid("Invalid ROM path"); }
                if (!part.get("size").isIntegralNumber() || !part.get("size").canConvertToLong()
                        || !part.get("offset").isInt() || !part.get("length").isInt()) throw invalid("Invalid window numbers");
                long size = part.get("size").longValue();
                int offset = part.get("offset").intValue(), length = part.get("length").intValue();
                checkWindow(path, size, offset, length);
                total += length;
                if (total > MAX_IMAGE_BYTES) throw invalid("Logical ROM exceeds limit");
                var image = images.get(path);
                if (image == null) {
                    if (!Files.isRegularFile(path)) throw invalid("Missing supplied ROM image: " + path);
                    image = PhysicalImage.probe(path);
                    images.put(path, image);
                }
                if (image.size() != size || image.reader().size() != size) throw invalid("Supplied ROM size changed: " + path);
                var view = image.reader().window(offset, length);
                String sha1 = text(part.get("sha1"));
                if (!sha1.matches("[0-9A-Fa-f]{40}")
                        || !sha1.equalsIgnoreCase(RomFingerprint.of(view.slice(0, length)).sha1Hex())) {
                    throw invalid("Supplied ROM bytes changed: " + path);
                }
                views.add(view);
            }
            readers.put(entry.getKey(), RomByteReader.concat(views.toArray(RomByteReader[]::new)));
        }
        return Map.copyOf(readers);
    }

    /** Reads a local metadata descriptor with a stream cap, including when the file grows after opening. */
    public static Map<String, RomByteReader> openFile(Path descriptor) throws IOException {
        if (!Files.isRegularFile(descriptor)) throw invalid("Descriptor must be a regular file");
        int maximumBytes = MAX_DESCRIPTOR_CHARS * 4;
        try (var input = Files.newInputStream(descriptor)) {
            byte[] bytes = input.readNBytes(maximumBytes + 1);
            if (bytes.length > maximumBytes) throw invalid("Descriptor exceeds limit");
            return open(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
        }
    }

    private static void checkWindow(Path path, long size, int offset, int length) throws IOException {
        if (!path.isAbsolute() || path.toString().length() > 4096 || size <= 0 || size > MAX_IMAGE_BYTES
                || offset < 0 || length <= 0 || (long) offset + length > size) throw invalid("Invalid ROM window");
    }

    private static RomIdentity identity(String game) throws IOException {
        return switch (game) {
            case "s1" -> RomIdentity.S1;
            case "s2" -> RomIdentity.S2;
            case "s3k" -> RomIdentity.S3K;
            default -> throw invalid("Unknown stock game: " + game);
        };
    }

    private static void fields(JsonNode node, Set<String> expected) throws IOException {
        if (node == null || !node.isObject() || node.size() != expected.size()) throw invalid("Invalid descriptor fields");
        var names = node.fieldNames();
        while (names.hasNext()) if (!expected.contains(names.next())) throw invalid("Unknown descriptor field");
    }

    private static String text(JsonNode node) throws IOException {
        if (node == null || !node.isTextual()) throw invalid("Expected descriptor text");
        return node.textValue();
    }

    private static IOException invalid(String message) {
        return new IOException("Stageplay ROM transport: " + message);
    }
}
