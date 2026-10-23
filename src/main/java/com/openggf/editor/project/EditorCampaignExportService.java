package com.openggf.editor.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.game.GameModule;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.ModKeySyntax;
import com.openggf.mods.ModApiVersion;
import com.openggf.mods.SemanticVersion;
import com.openggf.mods.code.ModCampaignDefinition;
import com.openggf.mods.code.ModLevelExportStagingValidator;
import com.openggf.tools.modsdk.JarPackager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Packages saved projects as bounded data-only acts through the existing ggfmod publisher. */
public final class EditorCampaignExportService {
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Request(Path outputJar, String modId, String name, String version, String author, String localKey) {
        public Request {
            Objects.requireNonNull(outputJar, "outputJar");
            modId = ModKeySyntax.requireManifestId(modId);
            localKey = ModKeySyntax.requireLocalName(localKey);
            SemanticVersion.parse(version);
            if (name == null || name.isBlank() || name.length() > 256
                    || author == null || author.isBlank() || author.length() > 256)
                throw new IllegalArgumentException("Campaign name and author must contain 1..256 characters");
            if (outputJar.getFileName() == null || !outputJar.getFileName().toString().endsWith(".jar"))
                throw new IllegalArgumentException("Campaign output must be a mod .jar");
        }
    }

    public record Result(Path outputJar, int levels) { }

    /** Reopens the supplied saved files; callers save pending editor changes before invoking this. */
    public Result export(List<Path> orderedProjects, Request request, GameModule host) throws IOException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(request, "request");
        List<Path> inputs = List.copyOf(orderedProjects);
        if (inputs.isEmpty() || inputs.size() > ModCampaignDefinition.MAX_LEVELS)
            throw new IllegalArgumentException("Campaign needs 1..256 saved projects");
        Path output = request.outputJar().toAbsolutePath().normalize();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Campaign output already exists: " + output);
        var unique = new HashSet<Path>();
        for (Path input : inputs) {
            if (Files.isSymbolicLink(input) || !Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Campaign project must be a regular saved file: " + input);
            if (!unique.add(input.toRealPath())) throw new IOException("Duplicate campaign project: " + input);
        }
        Path parent = Objects.requireNonNull(output.getParent(), "Campaign output parent");
        Files.createDirectories(parent);
        Path staging = Files.createTempDirectory(parent, ".editor-campaign-");
        Throwable failure = null;
        try {
            var campaign = JSON.createObjectNode();
            campaign.put("name", request.name());
            campaign.put("game", host.getGameId().code());
            campaign.put("localKey", request.localKey());
            var levels = campaign.putArray("levels");
            var store = new EditorProjectStore();
            var exporter = new FullLevelExporter(new ModLevelExportStagingValidator(host));
            var hostKeys = com.openggf.level.objects.HostObjectKeyRegistry.trusted(host.createObjectRegistry());
            GameplayLaunchTeam requiredTeam = null;
            for (int index = 0; index < inputs.size(); index++) {
                var document = store.open(inputs.get(index));
                var metadata = document.metadata();
                if (metadata.gameId() != host.getGameId())
                    throw new IOException("Campaign projects must all use " + host.getGameId().code() + ": " + inputs.get(index));
                if (index > 0 && !Objects.equals(requiredTeam, metadata.settings().team()))
                    throw new IOException("Campaign acts must have the same required team (or all be unrestricted)");
                requiredTeam = metadata.settings().team();
                com.openggf.game.modzone.ModLevelSettingsValidation.validate(host, metadata.settings());
                if (!(metadata.music() instanceof FullLevelExporter.ExportMusic.Stock)
                        || document.level().getObjects().stream().anyMatch(object -> object.authoredProperties()
                            instanceof com.openggf.level.objects.authored.AuthoredObjectProperties.MusicZone music
                            && music.music() instanceof com.openggf.game.MusicReference.Namespaced)
                        || document.level().getObjects().stream().anyMatch(object -> object.objectKey() != null
                            && (hostKeys == null || !hostKeys.hasHostObjectKey(object.objectKey()))))
                    throw new IOException("Data-only campaign export supports stock music and host-owned objects; external keyed assets need their owning mod package");
                String id = "act-" + (index + 1);
                String directory = "levels/" + id;
                if (document.level().getObjects().stream().map(com.openggf.level.objects.ObjectSpawn::authoredProperties)
                        .anyMatch(properties -> properties instanceof com.openggf.level.objects.authored.AuthoredObjectProperties.StageplayTrigger trigger
                                && !trigger.blocking()))
                    throw new IOException("Authored Stageplay triggers require blocking playback before campaign export");
                exporter.export(document.level(), EditorProjectRuntimeModule.request(document,
                        staging.resolve(directory), metadata.startX(), metadata.startY()));
                var scenes = document.level().getObjects().stream()
                        .map(com.openggf.level.objects.ObjectSpawn::authoredProperties)
                        .filter(com.openggf.level.objects.authored.AuthoredObjectProperties.StageplayTrigger.class::isInstance)
                        .map(com.openggf.level.objects.authored.AuthoredObjectProperties.StageplayTrigger.class::cast)
                        .map(com.openggf.level.objects.authored.AuthoredObjectProperties.StageplayTrigger::resource)
                        .distinct().toList();
                EditorCampaignResources.copy(document.path().getParent(), staging, scenes);
                long stagedBytes = 0;
                try (var files = Files.walk(staging)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) stagedBytes += Files.size(file);
                }
                if (stagedBytes > com.openggf.io.ModInputLimits.production().maxModValidationBytes())
                    throw new IOException("Campaign exported assets exceed the bounded package validation budget");
                var entry = levels.addObject();
                entry.put("id", id);
                entry.put("level", directory + "/level.json");
                if (index + 1 < inputs.size()) entry.put("next", "act-" + (index + 2));
                else entry.putNull("next");
            }
            Files.write(staging.resolve("campaign.json"), JSON.writeValueAsBytes(campaign));
            var manifest = JSON.createObjectNode();
            manifest.put("formatVersion", 1);
            manifest.put("id", request.modId()); manifest.put("name", request.name());
            manifest.put("version", request.version()); manifest.putArray("authors").add(request.author());
            manifest.put("description", "Ordered authored campaign: " + request.name());
            manifest.put("engineApiRange", ModApiVersion.CURRENT.toString());
            manifest.put("type", "patch"); manifest.put("baseGame", host.getGameId().code());
            manifest.putArray("dependencies"); manifest.putObject("audioOverrides"); manifest.putObject("artOverrides");
            manifest.put("campaign", "campaign.json");
            Path metadata = staging.resolve("META-INF/openggf-mod.yaml");
            Files.createDirectories(metadata.getParent());
            // JSON is a strict YAML subset; the existing bounded manifest reader remains authoritative.
            Files.write(metadata, JSON.writeValueAsBytes(manifest));
            JarPackager.packageDirectory(staging, output);
            return new Result(output, inputs.size());
        } catch (IOException | RuntimeException | Error rejected) {
            failure = rejected;
            throw rejected;
        } finally {
            try (var files = Files.walk(staging)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            } catch (IOException cleanup) {
                if (failure != null) failure.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }
}
