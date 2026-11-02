package com.openggf.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Strict manifest codec: unknown keys, missing keys and wrong types are rejected. */
final class RouteJson {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper COMPACT = new ObjectMapper();

    private RouteJson() {
    }

    static String write(RouteManifest manifest, String scriptName) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("format", RouteManifest.FORMAT);
        root.set("launch", launch(manifest.launch()));
        ObjectNode tape = root.putObject("tape");
        tape.put("script", scriptName);
        tape.put("rows", manifest.rowCount());
        tape.put("sha256", manifest.tapeHash().sha256());
        ObjectNode provenance = root.putObject("provenance");
        provenance.put("producer", manifest.provenance().producer());
        provenance.put("engine", manifest.provenance().engine());
        provenance.put("source", manifest.provenance().source());
        ArrayNode notes = provenance.putArray("notes");
        manifest.provenance().notes().forEach(notes::add);
        ArrayNode stamps = root.putArray("qualification");
        for (QualificationStamp stamp : manifest.qualification()) {
            ObjectNode node = stamps.addObject();
            node.put("scope", stamp.scope().name());
            node.put("verdict", stamp.verdict());
            node.put("content", stamp.content().sha256());
            node.put("environment", stamp.environment());
            node.put("rows", stamp.rows());
        }
        ObjectNode nativeNode = root.putObject("native");
        manifest.nativeFacts().entry().ifPresent(entry -> {
            ObjectNode node = nativeNode.putObject("entry");
            node.put("movie", entry.movie());
            node.put("prefixRows", entry.prefixRows());
            node.put("inputHash", entry.inputHash().sha256());
        });
        ArrayNode lag = nativeNode.putArray("lag");
        for (NativeFacts.LagFact fact : manifest.nativeFacts().lag()) {
            ObjectNode node = lag.addObject();
            node.put("beforeRow", fact.beforeRow());
            node.put("harness", fact.harness());
            node.put("content", fact.content().sha256());
        }
        try {
            return MAPPER.writeValueAsString(root) + "\n";
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Parsed manifest plus the script file name it binds. */
    record Parsed(RouteManifest manifest, String scriptName) { }

    static Parsed read(String text) throws IOException {
        JsonNode root = COMPACT.readTree(text);
        object(root, "manifest", Set.of("format", "launch", "tape", "provenance", "qualification", "native"),
                Set.of("format", "launch", "tape"));
        if (!RouteManifest.FORMAT.equals(text(root, "format"))) {
            throw new IOException("unsupported route format: " + root.get("format"));
        }
        LaunchContract launch = launch(root.get("launch"));
        JsonNode tape = root.get("tape");
        object(tape, "tape", Set.of("script", "rows", "sha256"), Set.of("script", "rows", "sha256"));
        Provenance provenance = new Provenance("unknown", "", "", List.of());
        if (root.has("provenance")) {
            JsonNode node = root.get("provenance");
            object(node, "provenance", Set.of("producer", "engine", "source", "notes"), Set.of("producer"));
            var notes = new ArrayList<String>();
            if (node.has("notes")) for (JsonNode note : array(node.get("notes"), "notes")) notes.add(note.asText());
            provenance = new Provenance(text(node, "producer"), optionalText(node, "engine"),
                    optionalText(node, "source"), notes);
        }
        var stamps = new ArrayList<QualificationStamp>();
        if (root.has("qualification")) {
            for (JsonNode node : array(root.get("qualification"), "qualification")) {
                object(node, "qualification stamp", Set.of("scope", "verdict", "content", "environment", "rows"),
                        Set.of("scope", "verdict", "content", "environment", "rows"));
                stamps.add(new QualificationStamp(ReplayScope.valueOf(text(node, "scope")), text(node, "verdict"),
                        new ContentHash(text(node, "content")), text(node, "environment"), integer(node, "rows")));
            }
        }
        NativeFacts facts = NativeFacts.NONE;
        if (root.has("native")) {
            JsonNode node = root.get("native");
            object(node, "native", Set.of("entry", "lag"), Set.of());
            Optional<NativeFacts.NativeEntry> entry = Optional.empty();
            if (node.has("entry")) {
                JsonNode e = node.get("entry");
                object(e, "native entry", Set.of("movie", "prefixRows", "inputHash"), Set.of("movie", "prefixRows", "inputHash"));
                entry = Optional.of(new NativeFacts.NativeEntry(text(e, "movie"), integer(e, "prefixRows"),
                        new ContentHash(text(e, "inputHash"))));
            }
            var lag = new ArrayList<NativeFacts.LagFact>();
            if (node.has("lag")) {
                for (JsonNode l : array(node.get("lag"), "lag")) {
                    object(l, "lag fact", Set.of("beforeRow", "harness", "content"), Set.of("beforeRow", "harness", "content"));
                    lag.add(new NativeFacts.LagFact(integer(l, "beforeRow"), text(l, "harness"),
                            new ContentHash(text(l, "content"))));
                }
            }
            facts = new NativeFacts(entry, lag);
        }
        var manifest = new RouteManifest(launch, new ContentHash(text(tape, "sha256")), integer(tape, "rows"),
                provenance, stamps, facts);
        return new Parsed(manifest, text(tape, "script"));
    }

    static ContentHash contentHash(LaunchContract launch, InputTape tape) {
        try {
            String canonical = COMPACT.writeValueAsString(launch(launch)) + "\n" + tape.hash().sha256() + "\n" + tape.size();
            return ContentHash.of(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ObjectNode launch(LaunchContract launch) {
        ObjectNode node = MAPPER.createObjectNode();
        ObjectNode rom = node.putObject("rom");
        rom.put("game", launch.rom().game());
        rom.put("crc32", launch.rom().crc32Hex());
        rom.put("sha1", launch.rom().sha1());
        ObjectNode inlet = node.putObject("inlet");
        switch (launch.inlet()) {
            case Inlet.ColdLevel cold -> {
                inlet.put("kind", "cold-level");
                inlet.put("zone", cold.level().zone());
                inlet.put("act", cold.level().act());
                inlet.put("showTitleCard", cold.showTitleCard());
            }
        }
        ObjectNode roster = node.putObject("roster");
        roster.put("main", launch.roster().main());
        roster.put("sidekick", launch.roster().sidekick());
        node.put("width", launch.width());
        node.put("donor", launch.donor());
        ObjectNode initial = node.putObject("initial");
        initial.put("vIntStart", launch.initial().vIntStart());
        Seeds seeds = launch.initial().seeds();
        if (!seeds.none()) {
            ObjectNode s = initial.putObject("seeds");
            if (seeds.startX() != null) { s.put("startX", seeds.startX()); s.put("startY", seeds.startY()); }
            if (seeds.starPost()) s.put("starPost", true);
            if (seeds.rings() != null) s.put("rings", seeds.rings());
            if (seeds.emeralds() != null) s.put("emeralds", seeds.emeralds());
            if (seeds.reverseGravity()) s.put("reverseGravity", true);
            if (seeds.endOfLevel()) s.put("endOfLevel", true);
            if (seeds.cameraXSub() != null) s.put("cameraXSub", seeds.cameraXSub());
        }
        return node;
    }

    private static LaunchContract launch(JsonNode node) throws IOException {
        object(node, "launch", Set.of("rom", "inlet", "roster", "width", "donor", "initial"),
                Set.of("rom", "inlet", "roster", "width", "initial"));
        JsonNode rom = node.get("rom");
        object(rom, "rom", Set.of("game", "crc32", "sha1"), Set.of("game", "crc32", "sha1"));
        String game = text(rom, "game");
        var identity = new RomIdentity(game, Long.parseLong(text(rom, "crc32"), 16), text(rom, "sha1"));
        JsonNode inlet = node.get("inlet");
        Inlet parsedInlet = switch (text(inlet, "kind")) {
            case "cold-level" -> {
                object(inlet, "inlet", Set.of("kind", "zone", "act", "showTitleCard"), Set.of("kind", "zone", "act"));
                yield new Inlet.ColdLevel(new LevelId(game, integer(inlet, "zone"), integer(inlet, "act")),
                        inlet.has("showTitleCard") && bool(inlet, "showTitleCard"));
            }
            default -> throw new IOException("unknown inlet kind: " + inlet.get("kind"));
        };
        JsonNode roster = node.get("roster");
        object(roster, "roster", Set.of("main", "sidekick"), Set.of("main"));
        JsonNode initial = node.get("initial");
        object(initial, "initial", Set.of("vIntStart", "seeds"), Set.of("vIntStart"));
        Seeds seeds = Seeds.NONE;
        if (initial.has("seeds")) {
            JsonNode s = initial.get("seeds");
            object(s, "seeds", Set.of("startX", "startY", "starPost", "rings", "emeralds", "reverseGravity",
                    "endOfLevel", "cameraXSub"), Set.of());
            seeds = new Seeds(optionalInt(s, "startX"), optionalInt(s, "startY"), s.has("starPost") && bool(s, "starPost"),
                    optionalInt(s, "rings"), s.has("emeralds") ? text(s, "emeralds") : null,
                    s.has("reverseGravity") && bool(s, "reverseGravity"), s.has("endOfLevel") && bool(s, "endOfLevel"),
                    optionalInt(s, "cameraXSub"));
        }
        return new LaunchContract(identity, parsedInlet, new Roster(text(roster, "main"), optionalText(roster, "sidekick")),
                integer(node, "width"), node.has("donor") ? text(node, "donor") : "off",
                new InitialState(integer(initial, "vIntStart"), seeds));
    }

    private static void object(JsonNode node, String what, Set<String> allowed, Set<String> required) throws IOException {
        if (node == null || !node.isObject()) throw new IOException(what + " must be an object");
        for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!allowed.contains(name)) throw new IOException("unknown " + what + " key: " + name);
        }
        for (String name : required) {
            if (!node.has(name) || node.get(name).isNull()) throw new IOException(what + " is missing " + name);
        }
    }

    private static ArrayNode array(JsonNode node, String what) throws IOException {
        if (!(node instanceof ArrayNode array)) throw new IOException(what + " must be an array");
        return array;
    }

    private static String text(JsonNode node, String key) throws IOException {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual()) throw new IOException(key + " must be a string");
        return value.asText();
    }

    private static String optionalText(JsonNode node, String key) throws IOException {
        return node.has(key) ? text(node, key) : "";
    }

    private static int integer(JsonNode node, String key) throws IOException {
        JsonNode value = node.get(key);
        if (value == null || !value.canConvertToInt() || !value.isIntegralNumber()) throw new IOException(key + " must be an integer");
        return value.asInt();
    }

    private static Integer optionalInt(JsonNode node, String key) throws IOException {
        return node.has(key) ? integer(node, key) : null;
    }

    private static boolean bool(JsonNode node, String key) throws IOException {
        JsonNode value = node.get(key);
        if (value == null || !value.isBoolean()) throw new IOException(key + " must be a boolean");
        return value.asBoolean();
    }
}
