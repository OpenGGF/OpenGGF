package com.openggf.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openggf.game.GameId;
import com.openggf.game.GameModuleRegistry;
import com.openggf.stageplay.StageplayObjectCatalog;
import com.openggf.stageplay.StageplayObjectCatalog.Catalog;
import com.openggf.stageplay.StageplayObjectCatalog.Context;
import com.openggf.stageplay.StageplayObjectCatalog.Entry;
import com.openggf.stageplay.StageplayDocument.ObjectMode;
import com.openggf.stageplay.StageplayDocument.ObjectSourceKind;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the complete Stageplay native-object ID/context inventory without
 * opening a ROM, constructing objects or touching a gameplay session.
 * Inputs: optional stock game, output format/path, tested source revision and
 * strictly typed, manually reviewed supplied-ROM sample observations.
 * Origin: Stageplay complete-cast task, 2026-10-22.
 */
public final class StageplayObjectCoverageTool {
    private static final ObjectMapper JSON = new ObjectMapper().addMixIn(Entry.class, EntryJson.class);
    private static final ObjectMapper OBSERVATIONS_JSON = JsonMapper.builder(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private StageplayObjectCoverageTool() { }

    /** Include the human-readable provider restriction without changing catalog API serialization. */
    private abstract static class EntryJson {
        @JsonProperty public abstract String freeRestriction();
    }

    public enum SampleResult { CAPTURED, EXCLUDED, FAILED }

    /** A result applies only to this supplied-ROM scene/frame/source tuple, never every subtype of an ID. */
    public record SampleObservation(String gameId, int zone, int act, ObjectSourceKind sourceKind,
                                    int objectId, int subtype, ObjectMode mode, String scene,
                                    String sceneSha256, int frame, String sourceRevision, String romSha1,
                                    List<String> evidencePaths, SampleResult result, List<String> limitations) {
        public SampleObservation {
            if (!List.of("s1", "s2", "s3k").contains(gameId)) throw new IllegalArgumentException("Sample gameId must be s1, s2 or s3k");
            if (zone < 0 || act < 0 || frame < 0) throw new IllegalArgumentException("Sample zone, act and frame must be non-negative");
            if (objectId < 0 || objectId > 255 || subtype < 0 || subtype > 255)
                throw new IllegalArgumentException("Sample objectId and subtype must be bytes");
            java.util.Objects.requireNonNull(sourceKind, "Sample sourceKind");
            java.util.Objects.requireNonNull(mode, "Sample mode");
            java.util.Objects.requireNonNull(result, "Sample result");
            if (sourceKind == ObjectSourceKind.RELEASED_ANIMAL && (objectId != 0 || subtype != 0))
                throw new IllegalArgumentException("Released-animal samples use objectId 0 and subtype 0, not a layout ID");
            requireText(scene, "scene");
            requireDigest(sceneSha256, 64, "sceneSha256");
            requireDigest(sourceRevision, 40, "sourceRevision");
            requireDigest(romSha1, 40, "romSha1");
            evidencePaths = List.copyOf(evidencePaths);
            limitations = List.copyOf(limitations);
            if (evidencePaths.isEmpty()) throw new IllegalArgumentException("Sample evidencePaths must not be empty");
            for (var path : evidencePaths) { requireText(path, "evidence path"); Path.of(path); }
            for (var limitation : limitations) requireText(limitation, "limitation");
            if (result != SampleResult.CAPTURED && limitations.isEmpty())
                throw new IllegalArgumentException("Excluded/failed samples require an explicit limitation");
        }
    }

    public record ObservationFile(int version, List<SampleObservation> observations) {
        public ObservationFile {
            if (version != 1) throw new IllegalArgumentException("Unsupported sample observation version");
            observations = List.copyOf(observations);
            if (observations.size() > 10_000) throw new IllegalArgumentException("Too many sample observations");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Sample " + name + " must not be blank or contain NUL");
    }

    private static void requireDigest(String value, int length, String name) {
        if (value == null || !value.matches("[0-9a-fA-F]{" + length + "}"))
            throw new IllegalArgumentException("Sample " + name + " must be a " + length + "-digit hexadecimal digest");
    }

    public static List<SampleObservation> readObservations(Path path) throws IOException {
        return OBSERVATIONS_JSON.readValue(Files.readAllBytes(path), ObservationFile.class).observations();
    }

    /** Equal metadata is stored once, with every affected native source context listed explicitly. */
    public record Group(String gameId, List<Context> contexts, List<Entry> entries,
                        List<StageplayObjectCatalog.AuxiliaryEntry> auxiliaryEntries) {
        public Group { contexts = List.copyOf(contexts); entries = List.copyOf(entries); auxiliaryEntries = List.copyOf(auxiliaryEntries); }
    }

    public record Report(int version, String sourceRevision, String evidence, int sourceContexts,
                         long objectIdContextRows, Map<String, String> reasons, List<String> limits,
                         List<Group> groups, List<SampleObservation> observations) {
        public Report {
            reasons = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(reasons));
            limits = List.copyOf(limits); groups = List.copyOf(groups); observations = List.copyOf(observations);
        }
    }

    private record GroupKey(String gameId, List<Entry> entries, List<StageplayObjectCatalog.AuxiliaryEntry> auxiliaryEntries) { }

    public static List<Catalog> collect(List<GameId> games) {
        var result = new ArrayList<Catalog>();
        for (var game : games) result.addAll(StageplayObjectCatalog.inspect(GameModuleRegistry.createStockModule(game)));
        return List.copyOf(result);
    }

    public static Report report(List<Catalog> catalogs, String sourceRevision) {
        return report(catalogs, sourceRevision, List.of());
    }

    public static Report report(List<Catalog> catalogs, String sourceRevision, List<SampleObservation> observations) {
        observations = List.copyOf(observations);
        for (var sample : observations) {
            var context = catalogs.stream().filter(c -> c.gameId().equals(sample.gameId())
                    && c.context().zone() == sample.zone() && c.context().act() == sample.act()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Sample context is outside the selected catalog: "
                            + sample.gameId() + "/" + sample.zone() + "/" + sample.act()));
            if (sample.sourceKind() == ObjectSourceKind.RELEASED_ANIMAL && context.auxiliaryEntries().isEmpty())
                throw new IllegalArgumentException("Sample context has no released-animal native factory");
        }
        var grouped = new LinkedHashMap<GroupKey, List<Context>>();
        boolean observed = false;
        for (var catalog : catalogs) {
            grouped.computeIfAbsent(new GroupKey(catalog.gameId(), catalog.entries(), catalog.auxiliaryEntries()), ignored -> new ArrayList<>())
                    .add(catalog.context());
            observed |= catalog.entries().stream().anyMatch(entry -> entry.free().readiness() == StageplayObjectCatalog.Readiness.AVAILABLE);
        }
        var groups = grouped.entrySet().stream()
                .map(entry -> new Group(entry.getKey().gameId(), entry.getValue(), entry.getKey().entries(), entry.getKey().auxiliaryEntries())).toList();
        var reasons = new LinkedHashMap<String, String>();
        for (var reason : StageplayObjectCatalog.Reason.values()) reasons.put(reason.name(), reason.description());
        return new Report(2, sourceRevision, !observations.isEmpty() ? "REGISTRY_METADATA_WITH_SAMPLE_OBSERVATIONS"
                        : observed ? "REGISTRY_METADATA_WITH_LOADED_OBSERVATIONS" : "REGISTRATION_ONLY",
                catalogs.size(), Math.multiplyExact((long) catalogs.size(), 256), reasons, List.of(
                        "AVAILABLE, NEEDS_PREFLIGHT and UNAVAILABLE are distinct; registration alone never certifies a castable object.",
                        "Registered factories with unobserved visual graphs need preflight, not exclusion: capture a constructor-ready visual, or initialize Free in its native set before capturing the complete appearance. Puppet/Pose never execute those routines.",
                        "All byte IDs 0..255 are included per source context. Dynamic/event-only routines are not falsely declared absent from the ROM.",
                        "Native released-animal factories are separate auxiliary entries, never invented layout IDs. Their native-zone/art-variant tables list provider choices, not proof that all variants are loaded in the selected source.",
                        "Logical source zones/acts follow each native module's existing zone registry. This does not certify that every source context loads.",
                        "Factory compatibility can include constructor/subtype checks; no factory or object routine is executed by enumeration.",
                        StageplayObjectCatalog.NATIVE_ARENA_LABEL + "; resolved arena restrictions carry their owning routine citations.",
                        StageplayObjectCatalog.VISUAL_ONLY_LABEL + "; unresolved or deferred child appearances require declared visual metadata.",
                        "ROM script animation and state-machine appearances are separate. Undeclared/state-machine appearances stay held or explicitly authored.",
                        "Art availability, native renderer readiness and graph completeness require actual supplied-ROM observations or provider-owned visual metadata.",
                        "Sample observations are manually reviewed scene/frame/mode results with their own source revision and ROM/scene digests. They do not upgrade registry readiness or certify other frames, subtypes, contexts, appearances, animal variants or native initialization states.",
                        "Evidence paths identify review artifacts; importing observations validates their schema, not their contents or the claimed review."
                ), groups, observations);
    }

    public static byte[] json(Report report) throws IOException {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
    }

    public static String markdown(Report report) {
        var text = new StringBuilder("# Stageplay native object coverage\n\n");
        text.append("Source: `").append(escape(report.sourceRevision())).append("`. Evidence: `")
                .append(report.evidence()).append("`. ").append(report.sourceContexts())
                .append(" source contexts; ").append(report.objectIdContextRows()).append(" ID/context rows.\n\n");
        for (var limit : report.limits()) text.append("- ").append(limit).append('\n');
        if (!report.observations().isEmpty()) {
            text.append("\n## Supplied-ROM sample observations\n\n")
                    .append("| Source / subtype | Mode / frame / result | Scene / source / ROM | Evidence / limitations |\n")
                    .append("| --- | --- | --- | --- |\n");
            for (var sample : report.observations()) {
                text.append('|').append(sample.gameId()).append('/').append(sample.zone()).append('/').append(sample.act())
                        .append(" / ").append(sample.sourceKind()).append(" / ")
                        .append(sample.sourceKind() == ObjectSourceKind.RELEASED_ANIMAL ? "released-animal" : String.format("%02X", sample.objectId()))
                        .append(" / ").append(sample.subtype()).append('|').append(sample.mode()).append(" / ")
                        .append(sample.frame()).append(" / ").append(sample.result()).append('|').append(escape(sample.scene()))
                        .append(" / SHA256 ").append(sample.sceneSha256()).append(" / revision ").append(sample.sourceRevision())
                        .append(" / ROM SHA1 ").append(sample.romSha1()).append('|')
                        .append(escape(String.join("; ", sample.evidencePaths()))).append(" / ")
                        .append(escape(String.join("; ", sample.limitations()))).append("|\n");
            }
        }
        text.append("\n## Reason codes\n\n| Code | Meaning |\n| --- | --- |\n");
        report.reasons().forEach((code, description) -> text.append('|').append(code).append('|')
                .append(escape(description)).append("|\n"));
        int number = 0;
        for (var group : report.groups()) {
            text.append("\n## ").append(group.gameId()).append(" context group ").append(++number).append("\n\n");
            for (var context : group.contexts()) text.append("- Zone ").append(context.zone()).append(", act ")
                    .append(context.act()).append(": ").append(escape(context.name())).append('\n');
            for (var auxiliary : group.auxiliaryEntries()) {
                text.append("\nAuxiliary native routine `").append(auxiliary.key()).append("` (no layout ID): ")
                        .append(capability(auxiliary.free())).append(" Free; ").append(capability(auxiliary.puppet()))
                        .append(" Puppet; ").append(capability(auxiliary.pose())).append(" Pose. ")
                        .append(escape(String.join("; ", auxiliary.routineCitations()))).append(".\n\n")
                        .append("| Native zone | Art variant | Provider animal type |\n| --- | --- | --- |\n");
                for (var variant : auxiliary.animalVariants()) text.append('|').append(variant.nativeZone()).append('|')
                        .append(variant.artVariant()).append('|').append(variant.type()).append("|\n");
            }
            text.append("\n| ID | Native name | Pointer table / factory | Free | Puppet | Pose | Art / animation / citations |\n")
                    .append("| --- | --- | --- | --- | --- | --- | --- |\n");
            for (var entry : group.entries()) {
                text.append('|').append(String.format("%02X", entry.objectId())).append('|').append(escape(entry.name()))
                        .append('|').append(entry.objectTable()).append(" / ").append(entry.factoryStatus())
                        .append(" / ").append(entry.factorySelection()).append('|')
                        .append(capability(entry.free()));
                if (entry.free().reasons().contains(StageplayObjectCatalog.Reason.NATIVE_ARENA_POSITION))
                    text.append("; ").append(escape(entry.freeRestriction()));
                text.append('|').append(capability(entry.puppet())).append('|')
                        .append(capability(entry.pose())).append('|')
                        .append(escape(String.join(", ", entry.artKeys()))).append(" / ").append(entry.animation());
                if (!entry.previewArtKey().isEmpty()) text.append(" / preview: ").append(escape(entry.previewArtKey()));
                if (!entry.routineCitations().isEmpty()) text.append(" / ").append(escape(String.join("; ", entry.routineCitations())));
                if (!entry.appearanceExclusions().isEmpty()) text.append(" / EXCLUSIONS: ")
                        .append(escape(String.join("; ", entry.appearanceExclusions())));
                text.append("|\n");
            }
        }
        return text.toString();
    }

    private static String capability(StageplayObjectCatalog.Capability capability) {
        return capability.readiness() + " (" + String.join(",", capability.reasons().stream().map(Enum::name).toList()) + ")";
    }

    private static String escape(String value) { return value.replace("|", "\\|").replace('\n', ' ').replace('\r', ' '); }

    public static void main(String[] args) {
        PrintStream output = System.out;
        System.setOut(System.err);
        int status = execute(args, output);
        if (status != 0) System.exit(status);
    }

    static int execute(String[] args, PrintStream output) {
        try {
            var flags = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 >= args.length || !args[i].startsWith("--")) throw new IOException("Expected --name value");
                String name = args[i].substring(2);
                if (!List.of("game", "out", "format", "source-revision", "observations").contains(name)) throw new IOException("Unknown flag: " + args[i]);
                if (flags.putIfAbsent(name, args[i + 1]) != null) throw new IOException("Duplicate flag: " + args[i]);
            }
            if (!flags.containsKey("out")) throw new IOException("Supply --out PATH");
            String selected = flags.getOrDefault("game", "all");
            var games = selected.equals("all") ? List.of(GameId.S1, GameId.S2, GameId.S3K) : List.of(GameId.fromCode(selected));
            String format = flags.getOrDefault("format", "json");
            if (!format.equals("json") && !format.equals("markdown")) throw new IOException("Format must be json or markdown");
            var observations = flags.containsKey("observations") ? readObservations(Path.of(flags.get("observations"))) : List.<SampleObservation>of();
            Report report = report(collect(games), flags.getOrDefault("source-revision", "unrecorded"), observations);
            Path destination = Path.of(flags.get("out"));
            StageplayTool.publish(destination, format.equals("json") ? json(report) : markdown(report).getBytes(StandardCharsets.UTF_8));
            output.println(JSON.writeValueAsString(Map.of("ok", true, "report", destination.toAbsolutePath().toString(),
                    "evidence", report.evidence(), "contexts", report.sourceContexts(), "rows", report.objectIdContextRows())));
            return 0;
        } catch (Exception failure) {
            try { output.println(JSON.writeValueAsString(Map.of("ok", false, "error",
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()))); }
            catch (IOException impossible) { throw new IllegalStateException(impossible); }
            return 2;
        }
    }

    /** Structured command delegation for StageplayTool; engine logs remain with the caller's stderr policy. */
    public static int run(String[] args, PrintStream output) { return execute(args, output); }
}
