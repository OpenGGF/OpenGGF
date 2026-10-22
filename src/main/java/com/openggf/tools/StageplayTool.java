package com.openggf.tools;

import static com.openggf.stageplay.StageplayWorldPolicies.usesNativeWorld;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.data.Rom;
import com.openggf.game.BuiltInRomDetectors;
import com.openggf.game.GameId;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.runtime.StageplayResources;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.StageplayTimeline;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agent-facing directing CLI. JSON on stdout, diagnostics on stderr; no interactive engine. */
public final class StageplayTool {
    private static final ObjectMapper JSON = new ObjectMapper();
    private StageplayTool() { }

    public static void main(String[] args) {
        PrintStream result = System.out;
        // Engine libraries may log to stdout. Preserve one machine-readable result stream.
        System.setOut(System.err);
        int status = execute(args, result);
        if (status != 0) System.exit(status);
    }

    static int execute(String[] args, PrintStream output) {
        try {
            Object result = run(args);
            output.println(JSON.writeValueAsString(Map.of("ok", true, "result", result)));
            return 0;
        } catch (Exception failure) {
            try { output.println(JSON.writeValueAsString(Map.of("ok", false, "error", failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage()))); }
            catch (IOException impossible) { throw new IllegalStateException(impossible); }
            return 2;
        }
    }

    static Object run(String[] args) throws IOException {
        String command = args.length == 0 ? "help" : args[0];
        if (command.equals("render")) {
            var parsed = StageplayCaptureTool.Arguments.parse(Arrays.copyOfRange(args, 1, args.length));
            Path movie = StageplayCaptureTool.run(parsed);
            return Map.of("movie", movie.toString(), "scene", summary(StageplayStore.read(parsed.scene())));
        }
        Map<String, String> flags = flags(args);
        return switch (command) {
            case "help" -> {
                allow(flags);
                yield Map.of("commands", List.of(
                        "init --out screenplay.json",
                        "compile --script screenplay.json --out film.stageplay",
                        "validate --scene film.stageplay",
                        "inspect --scene film.stageplay [--frame N]",
                        "timeline --scene film.stageplay --out authored.jsonl [--every N]",
                        "simulate --scene film.stageplay --out state.jsonl --s2-rom PATH [--s1-rom PATH --s3k-rom PATH --every N]",
                        "level --game s3k --rom PATH --zone 11 --act 1",
                        "catalog --game s2 --rom /absolute/game.gen [--character tails] [--zone 0 --act 0]",
                        "render --scene film.stageplay --out film.mkv --s1-rom PATH --s2-rom PATH --s3k-rom PATH"),
                        "screenplayVersion", 4, "stageplayVersion", StageplayDocument.VERSION,
                        "fps", 60, "indices", "Frames, zones and acts are zero-based; every shot uses local frames.",
                        "outputs", "JSON result on stdout; exit 0 success, 2 invalid input/failure. Outputs are never overwritten.",
                        "resources", "catalog and simulate need supplied ROMs; simulate resolves native puppet physics without a window, graphics or audio playback. Render preflights resources and uses offline audio.");
            }
            case "init" -> {
                allow(flags, "out"); Path out = path(flags, "out");
                publish(out, scaffold().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                yield Map.of("screenplay", out.toAbsolutePath().toString());
            }
            case "compile" -> {
                allow(flags, "script", "out"); Path input = path(flags, "script"), out = path(flags, "out");
                StageplayDocument document = StageplayScriptCompiler.compile(read(input));
                // Relative resources retain their meaning only beside the source recipe.
                boolean relative = !document.set().levelProject().isEmpty()
                        || document.sceneChanges().stream().anyMatch(c -> !c.set().levelProject().isEmpty())
                        || document.audio().stream().anyMatch(c -> !c.asset().isEmpty())
                        || document.dialogueSkins().stream().anyMatch(s -> !s.decorationAsset().isEmpty())
                        || document.dialogue().stream().anyMatch(c -> !c.portraitAsset().isEmpty());
                if (relative && !input.toAbsolutePath().normalize().getParent().toRealPath()
                        .equals(out.toAbsolutePath().normalize().getParent().toRealPath()))
                    throw new IOException("Compile scenes with relative assets beside their screenplay");
                publish(out, StageplayStore.encode(document));
                yield summary(document);
            }
            case "validate", "inspect" -> {
                allow(flags, "scene", "frame");
                var document = StageplayStore.read(path(flags, "scene"));
                var report = summary(document);
                report.put("validation", "document structure; render preflights ROMs, sets, poses, dialogue layout and audio/image assets");
                if (flags.containsKey("frame")) {
                    int frame = Integer.parseInt(flags.get("frame"));
                    if (frame < 0 || frame >= document.durationFrames()) throw new IOException("Frame lies outside scene");
                    report.put("sample", StageplayTimeline.sample(document, frame));
                }
                yield report;
            }
            case "timeline" -> {
                allow(flags, "scene", "out", "every");
                var document = StageplayStore.read(path(flags, "scene"));
                int every = Integer.parseInt(flags.getOrDefault("every", "1"));
                if (every < 1 || every > document.durationFrames()) throw new IOException("Invalid timeline sampling interval");
                Path out = path(flags, "out");
                publish(out, staged -> {
                    try (var writer = Files.newBufferedWriter(staged, java.nio.charset.StandardCharsets.UTF_8)) {
                        for (int frame = 0; frame < document.durationFrames(); frame += every) {
                            int at = frame;
                            writer.write(JSON.writeValueAsString(Map.of("frame", frame, "seconds", frame / 60.0,
                                    "sample", StageplayTimeline.sample(document, frame),
                                    "cues", document.audio().stream().filter(cue -> cue.frame() == at).toList())));
                            writer.newLine();
                        }
                    }
                });
                yield Map.of("timeline", out.toAbsolutePath().toString(), "every", every,
                        "samples", (document.durationFrames() + every - 1) / every,
                        "kind", "authored directing state; not native gameplay simulation");
            }
            case "simulate" -> simulate(flags);
            case "catalog" -> catalog(flags);
            case "level" -> inspectLevel(flags);
            default -> throw new IOException("Unknown command: " + command + "; use help");
        };
    }

    private static Object simulate(Map<String, String> flags) throws IOException {
        allow(flags, "scene", "out", "every", "s1-rom", "s2-rom", "s3k-rom");
        Path scene = path(flags, "scene").toAbsolutePath().normalize(), out = path(flags, "out");
        var document = StageplayStore.read(scene);
        int every = Integer.parseInt(flags.getOrDefault("every", "1"));
        if (every < 1 || every > document.durationFrames()) throw new IOException("Invalid simulation sampling interval");
        var paths = new LinkedHashMap<String, Path>();
        for (String game : List.of("s1", "s2", "s3k")) {
            if (flags.containsKey(game + "-rom")) paths.put(game, path(flags, game + "-rom"));
        }
        if (paths.isEmpty()) throw new IOException("Simulation requires supplied ROM paths (--s1-rom, --s2-rom or --s3k-rom)");
        try (var library = SceneRomLibrary.open(paths);
             var resources = new StageplayResources(library, false)) {
            resources.setProjectRoot(scene.getParent());
            resources.validate(document);
            if (usesLiveWorld(document)) resources.setLiveWorld(com.openggf.stageplay.runtime.StageplayLiveWorld.openOffline(library));
            publish(out, staged -> {
                try (var writer = Files.newBufferedWriter(staged, java.nio.charset.StandardCharsets.UTF_8)) {
                    for (int frame = 0; frame < document.durationFrames(); frame++) {
                        if (Thread.currentThread().isInterrupted())
                            throw new java.io.InterruptedIOException("Stageplay simulation cancelled");
                        var actual = resources.resolveFrame(document, StageplayTimeline.sample(document, frame));
                        if (frame % every != 0) continue;
                        int at = frame;
                        writer.write(JSON.writeValueAsString(Map.of("frame", frame, "seconds", frame / 60.0,
                                "sample", actual,
                                "cues", document.audio().stream().filter(cue -> cue.frame() == at).toList())));
                        writer.newLine();
                    }
                }
            });
        }
        return Map.of("timeline", out.toAbsolutePath().toString(), "every", every,
                "samples", (document.durationFrames() + every - 1) / every,
                "kind", usesLiveWorld(document) ? "native shared live-world gameplay" : "native player physics against detached terrain; no live gameplay objects or events");
    }

    static boolean usesLiveWorld(StageplayDocument document) {
        return usesNativeWorld(document.set().worldPolicy())
                || document.sceneChanges().stream().anyMatch(c -> usesNativeWorld(c.set().worldPolicy()));
    }

    private static Object inspectLevel(Map<String, String> flags) throws IOException {
        allow(flags, "game", "rom", "zone", "act");
        String game = required(flags, "game");
        int zone = Integer.parseInt(required(flags, "zone")), act = Integer.parseInt(required(flags, "act"));
        try (var library = SceneRomLibrary.open(Map.of(game, path(flags, "rom")))) {
            var level = library.level(game, zone, act);
            return Map.of("gameId", game, "zone", zone, "act", act, "romSha1", library.rom(game).romSha1(),
                    "objects", level.getObjects(), "rings", level.getRings(),
                    "identity", "Use layoutIndex, objectId and subtype together; dynamic slot IDs are not placement identities");
        }
    }

    private static Object catalog(Map<String, String> flags) throws IOException {
        allow(flags, "game", "rom", "character", "zone", "act");
        String code = required(flags, "game");
        var detector = BuiltInRomDetectors.forGame(GameId.fromCode(code));
        try (Rom rom = new Rom()) {
            if (!rom.open(path(flags, "rom").toAbsolutePath().toString()) || !detector.canHandle(rom))
                throw new IOException("ROM does not match requested game " + code);
            var module = detector.createModule();
            var art = SceneRomArtFactory.forModule(module, rom);
            var registry = module.getZoneRegistry();
            var report = JSON.createObjectNode();
            report.put("gameId", code).put("romSha1", art.romSha1());
            var zones = report.putArray("sets");
            for (int zone = 0; zone < registry.getZoneCount(); zone++) {
                for (int act = 0; act < registry.getActCount(zone); act++) {
                    var item = zones.addObject();
                    item.put("zone", zone).put("act", act).put("name", registry.getZoneName(zone));
                    item.set("start", JSON.valueToTree(registry.getStartPosition(zone, act)));
                    item.put("musicId", registry.getMusicId(zone, act));
                }
            }
            report.put("setAvailability", "Registry lists native acts; a detached set must also decode. Use --zone/--act to check one.");
            if (flags.containsKey("zone") || flags.containsKey("act")) {
                int zone = Integer.parseInt(required(flags, "zone")), act = Integer.parseInt(required(flags, "act"));
                if (zone < 0 || zone >= registry.getZoneCount() || act < 0 || act >= registry.getActCount(zone))
                    throw new IOException("Unknown zone/act");
                var kit = art.levelKit(zone, act);
                if (kit == null) throw new IOException("No detached set for this act");
                report.set("selectedSet", JSON.valueToTree(Map.of("zone", zone, "act", act,
                        "playableArea", kit.playableArea(), "columns", kit.columns(), "rows", kit.rows(), "blockSize", kit.blockSize())));
            }
            String character = flags.getOrDefault("character", "sonic");
            var sprites = art.character(character);
            report.put("character", character).put("mappingFrameCount", sprites.frameCount());
            var capabilities = report.putArray("intents");
            for (String intent : List.of("MOVE_TO", "STOP", "JUMP", "WAIT", "RELEASE", "CROUCH", "LOOK_UP", "ROLL")) capabilities.add(intent);
            var definition = module.getPlayableCharacterRegistry().find(com.openggf.game.CharacterKey.parsePersisted(character)).orElseThrow();
            if (definition.secondaryAbility() == com.openggf.sprites.playable.SecondaryAbility.FLY && module.getRules().playerCapability().tailsFlightEnabled()) {
                capabilities.add("FLY_TO"); capabilities.add("HOVER");
            }
            if (definition.secondaryAbility() == com.openggf.sprites.playable.SecondaryAbility.GLIDE && module.getRules().playerCapability().glideAttacksEnabled()) capabilities.add("GLIDE_TO");
            var animations = report.putArray("animations");
            // Stock animation bytecode indexes are bytes; absent scripts return an empty array.
            for (int animation = 0; animation < 256; animation++) {
                int[] frames = sprites.animationFrames(animation);
                if (frames.length == 0) continue;
                var entry = animations.addObject();
                entry.put("id", animation).put("delay", sprites.animationDelay(animation));
                entry.set("mappingFrames", JSON.valueToTree(frames));
            }
            return report;
        }
    }

    private static LinkedHashMap<String, Object> summary(StageplayDocument document) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", document.name()); result.put("version", document.version());
        result.put("frames", document.durationFrames()); result.put("seconds", document.durationFrames() / 60.0);
        result.put("width", document.width()); result.put("height", document.height());
        result.put("initialSet", document.set()); result.put("changes", document.sceneChanges());
        result.put("cast", document.actors().stream().map(a -> Map.of("id", a.id(), "name", a.name(),
                "gameId", a.gameId(), "character", a.character(), "keys", a.keys().size(),
                "mode", document.puppets().stream().anyMatch(p -> p.actorId().equals(a.id())) ? "PUPPET" : "POSER")).toList());
        result.put("cameraKeys", document.camera().size()); result.put("cameraEffects", document.cameraEffects());
        result.put("captions", document.captions()); result.put("audio", document.audio());
        result.put("dialogueTypers", document.dialogueTypers()); result.put("dialogueSkins", document.dialogueSkins());
        result.put("dialogue", document.dialogue());
        result.put("puppets", document.puppets());
        return result;
    }

    static byte[] read(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > StageplayStore.MAX_BYTES)
            throw new IOException("Input must be a regular file no larger than 8 MiB");
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(StageplayStore.MAX_BYTES + 1);
            if (bytes.length > StageplayStore.MAX_BYTES) throw new IOException("Input exceeds 8 MiB");
            return bytes;
        }
    }

    /** Same-directory staging + fail-if-existing hard-link publication; never truncates an output. */
    static void publish(Path path, byte[] bytes) throws IOException {
        publish(path, staged -> Files.write(staged, bytes));
    }
    @FunctionalInterface private interface OutputWriter { void write(Path path) throws IOException; }
    private static void publish(Path path, OutputWriter writer) throws IOException {
        Path out = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(out.getParent())) throw new IOException("Output directory must already exist");
        Path temporary = Files.createTempFile(out.getParent(), ".stageplay-tool-", ".tmp");
        try {
            writer.write(temporary);
            Files.createLink(out, temporary);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static Map<String, String> flags(String[] args) throws IOException {
        var result = new LinkedHashMap<String, String>();
        for (int i = 1; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 == args.length || result.put(args[i].substring(2), args[i + 1]) != null)
                throw new IOException("Expected unique --flag value pairs");
        }
        return result;
    }
    private static void allow(Map<String, String> flags, String... names) throws IOException {
        for (String name : flags.keySet()) if (!List.of(names).contains(name)) throw new IOException("Unknown flag: --" + name);
    }
    private static String required(Map<String, String> flags, String name) throws IOException {
        String value = flags.get(name);
        if (value == null || value.isBlank()) throw new IOException("--" + name + " is required");
        return value;
    }
    private static Path path(Map<String, String> flags, String name) throws IOException { return Path.of(required(flags, name)); }

    static String scaffold() {
        return """
                {
                  "version": 4, "name": "First take", "width": 400, "height": 224,
                  "cast": [{"id":"tails","name":"Tails","gameId":"s2","character":"tails"}],
                  "shots": [{
                    "id":"opening", "durationFrames":300,
                    "set":{"gameId":"s2","zone":0,"act":0},
                    "camera":[{"frame":0,"x":0,"y":500},{"frame":299,"x":80,"y":500,"zoom":1}],
                    "actors":{"tails":[{"frame":0,"x":80,"y":655}]},
                    "actions":{"tails":[{"frame":0,"kind":"MOVE_TO","x":180,"y":648,"urgency":0.5},
                      {"frame":30,"kind":"JUMP","holdFrames":12}]},
                    "dialogue":[{"startFrame":75,"endFrame":260,"speakerActor":"tails",
                      "text":"Ready for our first take!"}],
                    "audio":[], "cameraEffects":[]
                  }]
                }
                """;
    }
}
