package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdProgramAssets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Developer inspection of supplied CUE media. No gameplay or firmware certification. */
public final class SonicCdMediaInspectTool {
    private SonicCdMediaInspectTool() { }

    /**
     * --cue PATH --output NEW_JSON [--requested-variant JAPAN|USA|EUROPE] [--container PATH].
     * Output must be new and outside the input directory. Prefer an external directory such as Downloads.
     * Within an OpenGGF source checkout, only generated target/ output is allowed. Output parents are
     * resolved before checking the nearest enclosing checkout, so symlinks cannot expose source directories.
     */
    public static void main(String[] arguments) throws IOException {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < arguments.length; i += 2) {
            String key = arguments[i];
            if (i + 1 >= arguments.length || !java.util.Set.of("--cue", "--output", "--requested-variant", "--container")
                    .contains(key) || options.putIfAbsent(key, arguments[i + 1]) != null) {
                throw new IllegalArgumentException("Use --cue PATH --output NEW_JSON "
                        + "[--requested-variant JAPAN|USA|EUROPE] [--container PATH]. "
                        + "Output must be new and outside the media input directory. Prefer an external directory "
                        + "such as Downloads; only generated target/ output is allowed inside OpenGGF checkouts "
                        + "after resolving symbolic parents.");
            }
        }
        if (!options.containsKey("--cue") || !options.containsKey("--output")) {
            throw new IllegalArgumentException("Both --cue and --output are required");
        }
        Path cue = Path.of(options.get("--cue")).toRealPath();
        Path requestedOutput = Path.of(options.get("--output")).toAbsolutePath().normalize();
        Path output = requestedOutput.getParent().toRealPath().resolve(requestedOutput.getFileName());
        if (output.startsWith(cue.getParent())) {
            throw new IOException("Inspection output must be outside the media input directory");
        }
        for (Path parent = output.getParent(); parent != null; parent = parent.getParent()) {
            if (Files.isRegularFile(parent.resolve("pom.xml"))
                    && Files.isDirectory(parent.resolve("src/main/java/com/openggf"))) {
                if (!output.startsWith(parent.resolve("target"))) {
                    throw new IOException("Inspection output inside an OpenGGF source checkout must be under target/");
                }
                break;
            }
        }
        if (Files.exists(output)) {
            throw new IOException("Inspection output must be a new file");
        }
        Optional<CdReleaseVariant> requested = Optional.ofNullable(options.get("--requested-variant"))
                .map(value -> CdReleaseVariant.valueOf(value.toUpperCase(Locale.ROOT)));
        Optional<Path> container = Optional.ofNullable(options.get("--container")).map(Path::of);
        try (CdProgramAssets assets = CdProgramAssets.open(cue, requested, container)) {
            byte[] json = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writerWithDefaultPrettyPrinter().writeValueAsBytes(facts(assets));
            Files.write(output, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
    }

    public static Map<String, Object> facts(CdProgramAssets assets) throws IOException {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("status", "observed_uncertified_media_assets");
        facts.put("limits", java.util.List.of("No retail revision or firmware certification",
                "No gameplay implementation or parity claim", "Copy tails beyond EOF are unresolved Word RAM content"));
        facts.put("header", assets.header());
        facts.put("observedSourceVariant", assets.header().observedVariant().map(Enum::name).orElse("UNKNOWN"));
        var identity = assets.identity();
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("cue", identity.cue());
        provenance.put("binaryFiles", identity.binaryFiles());
        provenance.put("originalContainer", identity.originalContainer().orElse(null));
        facts.put("provenance", provenance);
        facts.put("normalizedIdentityDefinition", identity.definition());
        facts.put("normalizedSha256", identity.normalizedSha256());
        facts.put("tracks", identity.tracks());
        facts.put("complete35TrackShape", assets.hasExpectedTrackShape());
        facts.put("isoFiles", assets.files());
        var programs = new ArrayList<Map<String, Object>>();
        var dependencies = new ArrayList<Map<String, Object>>();
        if (assets.catalogue().isPresent()) {
            var catalogue = assets.catalogue().get();
            for (var file : catalogue.files()) {
                switch (file.kind()) {
                    case STAGE_PROGRAM, MAIN_CPU_PROGRAM, UNUSED_COPY -> {
                        var mmd = assets.mmd(file);
                        programs.add(Map.of("file", file, "header", mmd.header(), "fileBytes", mmd.fileBytes(),
                                "fileBackedCopyBytes", mmd.fileBackedCopyBytes(),
                                "unresolvedCopyBytes", mmd.unresolvedCopyBytes()));
                    }
                    default -> { }
                }
            }
            for (var command : catalogue.commands()) {
                if (catalogue.disposition(command) != com.openggf.game.soniccd.CdProgramCatalogue.CommandDisposition.INVALID) {
                    var components = assets.components(command);
                    if (!components.isEmpty()) {
                        dependencies.add(Map.of("command", command, "components", components));
                    }
                }
            }
        }
        facts.put("mmdPrograms", programs);
        facts.put("potentialDependencies", dependencies);
        return facts;
    }
}
