package com.openggf.tools.modsdk;

import com.openggf.StockMusicDomains;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.io.PackedModAssetRoot;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.mods.ModApiVersion;
import com.openggf.mods.ModAudioAssetValidation;
import com.openggf.mods.ModAudioSfx;
import com.openggf.mods.ModCatalogEntry;
import com.openggf.mods.ModCatalogValidator;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModFinding;
import com.openggf.mods.ModFindingSeverity;
import com.openggf.mods.ModManifest;
import com.openggf.mods.ModManifestException;
import com.openggf.mods.ModManifestParser;
import com.openggf.mods.ModAudioTrack;
import com.openggf.mods.StockProgressionAnchors;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModLevelDefinition;
import com.openggf.mods.code.ModLevelDefinitionParser;
import com.openggf.mods.validation.ModValidationFinding;
import com.openggf.mods.validation.ModValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Bounded, non-executing validator for one packed mod jar. */
public final class ModJarValidator {
    private static final String MANIFEST_PATH = "META-INF/openggf-mod.yaml";
    private static final Comparator<Finding> FINDING_ORDER = Comparator
            .comparing(Finding::severity)
            .thenComparing(Finding::code)
            .thenComparing(Finding::location)
            .thenComparing(Finding::member)
            .thenComparing(Finding::message);

    private final ModInputLimits limits;
    private final SnapshotHook snapshotHook;
    private final Predicate<com.openggf.mods.VersionRange> apiSupport;
    private final Supplier<String> supportedApiDiagnostic;

    public ModJarValidator() {
        this(ModInputLimits.production(), ignored -> { },
                ModApiVersion::supports, ModApiVersion::supportedContractsDiagnostic);
    }

    ModJarValidator(ModInputLimits limits) {
        this(limits, ignored -> { }, ModApiVersion::supports, ModApiVersion::supportedContractsDiagnostic);
    }

    ModJarValidator(ModInputLimits limits, SnapshotHook snapshotHook) {
        this(limits, snapshotHook, ModApiVersion::supports, ModApiVersion::supportedContractsDiagnostic);
    }

    ModJarValidator(ModInputLimits limits, SnapshotHook snapshotHook,
                    Predicate<com.openggf.mods.VersionRange> apiSupport,
                    Supplier<String> supportedApiDiagnostic) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.snapshotHook = Objects.requireNonNull(snapshotHook, "snapshotHook");
        this.apiSupport = Objects.requireNonNull(apiSupport, "apiSupport");
        this.supportedApiDiagnostic = Objects.requireNonNull(
                supportedApiDiagnostic, "supportedApiDiagnostic");
    }

    public Report validate(Path jarPath) {
        Objects.requireNonNull(jarPath, "jarPath");
        List<Finding> findings = new ArrayList<>();
        Path jar = jarPath.toAbsolutePath().normalize();
        Path root = jar.getParent();
        if (root == null || Files.isSymbolicLink(jar)
                || !Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS)) {
            findings.add(error("MOD_JAR_INVALID", jar.toString(), "", "Mod jar must be a regular non-symlink file"));
            return report(findings);
        }

        ModManifest manifest;
        ModDescriptor descriptor;
        List<String> inventory;
        try (PackedModAssetRoot assets = ModAssetRoot.jar(root, jar, limits, Files.size(jar))) {
            Path snapshotPath = assets.immutableContentPath();
            snapshotHook.afterSnapshot(snapshotPath);
            inventory = assets.validatedEntryNames();
            if (!inventory.contains(MANIFEST_PATH)) {
                findings.add(error("MANIFEST_MISSING", MANIFEST_PATH, "", "Required manifest is missing"));
                return report(findings);
            }
            try {
                manifest = new ModManifestParser(limits)
                        .parse(assets.readBounded(MANIFEST_PATH, limits.maxMetadataBytes()));
            } catch (ModManifestException | IllegalArgumentException failure) {
                findings.add(error("MANIFEST_INVALID", MANIFEST_PATH, "", safeMessage(failure)));
                return report(findings);
            }
            descriptor = new ModDescriptor(snapshotPath, manifest, assets.immutableSha256(),
                    inventory.stream().anyMatch(name -> name.endsWith(".class")), List.of());
            validateManifest(manifest, findings);
            validateDeclaredArt(assets, inventory, manifest, findings);
            validateLevels(assets, inventory, manifest.id(), findings);

            ModCatalogValidator.PackedValidation audio = new ModCatalogValidator(
                    snapshotPath.getParent(), limits, StockMusicDomains::containsSupported)
                    .validatePacked(descriptor, assets);
            for (ModCatalogEntry entry : audio.eligibility().entries()) {
                if (entry instanceof ModDescriptor validated) {
                    validated.findings().forEach(finding -> findings.add(fromCatalog(finding)));
                }
            }
            validateAudioAssets(assets, descriptor, audio.declaredTracks(), findings);
            validateSfxAssets(assets, descriptor, audio.declaredSfx(), findings);
            validateCompiledCode(snapshotPath, descriptor, findings);
        } catch (IOException | SecurityException failure) {
            String message = safeMessage(failure);
            String code = message.startsWith("Duplicate entry name: ") && message.endsWith(".class")
                    ? "DUPLICATE_CLASS" : "MOD_JAR_INVALID";
            findings.add(error(code, jar.toString(), "", message));
            return report(findings);
        }
        return report(findings);
    }

    private void validateManifest(ModManifest manifest, List<Finding> findings) {
        if (!apiSupport.test(manifest.engineApiRange())) {
            findings.add(error("ENGINE_API_INCOMPATIBLE", MANIFEST_PATH, "engineApiRange",
                    "Requires engine API " + manifest.engineApiRange() + "; supported contracts "
                            + supportedApiDiagnostic.get()));
        }
        int windows = manifest.patternWindows().orElse(1);
        if (windows < 1 || windows > 16) {
            findings.add(error("PATTERN_WINDOW_BUDGET_EXCEEDED", MANIFEST_PATH, "patternWindows",
                    "A single mod may request 1 through 16 pattern windows"));
        }
        if (manifest.insertAfter() != null
                && (manifest.baseGame() == null
                || !StockProgressionAnchors.contains(manifest.baseGame(), manifest.insertAfter()))) {
            findings.add(error("INSERT_AFTER_STOCK_ANCHOR_INVALID", MANIFEST_PATH, "insertAfter",
                    "insertAfter is not a results-driven stock boundary for " + manifest.baseGame()));
        }
    }

    private void validateDeclaredArt(PackedModAssetRoot assets, List<String> inventory,
                                     ModManifest manifest, List<Finding> findings) {
        manifest.artOverrides().values().stream().sorted().forEach(path -> {
            if (!inventory.contains(path)) {
                findings.add(error("ASSET_MISSING", path, "", "Declared art asset is missing"));
                return;
            }
            try {
                BakedSheetReader.read(assets.readBounded(path, limits.maxAssetBytes()), limits);
            } catch (IOException | IllegalArgumentException failure) {
                findings.add(error("ASSET_FORMAT_INVALID", path, "", safeMessage(failure)));
            }
        });
    }

    private static void validateLevels(PackedModAssetRoot assets, List<String> inventory,
                                       String manifestId, List<Finding> findings) {
        inventory.stream().filter(name -> name.equals("level.json") || name.endsWith("/level.json"))
                .sorted().forEach(path -> {
                    try {
                        ModLevelDefinition level = ModLevelDefinitionParser.read(assets, new BakedLevelRef(path));
                        for (ModLevelDefinition.ObjectEntry object : level.objects()) {
                            if (object instanceof ModLevelDefinition.KeyedObjectSpawn keyed
                                    && !keyed.objectKey().startsWith(manifestId + ":")) {
                                findings.add(error("LEVEL_OWNER_MISMATCH", path,
                                        "object:" + keyed.placementId(),
                                        "objectKey must be owned by declaring mod " + manifestId));
                            }
                        }
                        if (level.music() instanceof ModLevelDefinition.TrackMusic track
                                && !track.trackKey().modId().equals(manifestId)) {
                            findings.add(error("LEVEL_OWNER_MISMATCH", path, "music.trackKey",
                                    "trackKey must be owned by declaring mod " + manifestId));
                        }
                    } catch (IOException | IllegalArgumentException failure) {
                        findings.add(error("LEVEL_FORMAT_INVALID", path, "", safeMessage(failure)));
                    }
                });
    }

    private void validateAudioAssets(PackedModAssetRoot assets, ModDescriptor descriptor,
                                     List<ModAudioTrack> tracks,
                                     List<Finding> findings) {
        if (tracks.isEmpty()) {
            return;
        }
        try {
            for (ModAudioTrack track : tracks.stream()
                    .filter(value -> value.key().modId().equals(descriptor.manifest().id()))
                    .sorted(Comparator.comparing(value -> value.key().toString())).toList()) {
                try {
                    byte[] encoded = assets.readBounded(track.assetPath(), limits.maxAssetBytes());
                    ModAudioAssetValidation.decodeAndValidate(track, encoded, limits);
                } catch (IOException | IllegalArgumentException | ArithmeticException failure) {
                    String code = failure.getMessage() != null
                            && failure.getMessage().startsWith("Loop metadata")
                            ? "AUDIO_LOOP_INVALID" : "AUDIO_ASSET_INVALID";
                    findings.add(error(code, track.assetPath(), track.key().toString(), safeMessage(failure)));
                }
            }
        } catch (SecurityException failure) {
            findings.add(error("MOD_JAR_INVALID", descriptor.jarPath().toString(), "", safeMessage(failure)));
        }
    }

    private void validateSfxAssets(PackedModAssetRoot assets, ModDescriptor descriptor,
                                   List<ModAudioSfx> sfx,
                                   List<Finding> findings) {
        if (sfx.isEmpty()) {
            return;
        }
        try {
            for (ModAudioSfx value : sfx.stream()
                    .filter(item -> item.key().modId().equals(descriptor.manifest().id()))
                    .sorted(Comparator.comparing(item -> item.key().toString())).toList()) {
                try {
                    byte[] encoded = assets.readBounded(value.assetPath(), limits.maxAssetBytes());
                    ModAudioAssetValidation.decodeAndValidate(value, encoded, limits);
                } catch (IOException | IllegalArgumentException | ArithmeticException failure) {
                    findings.add(error("AUDIO_ASSET_INVALID", value.assetPath(),
                            value.key().toString(), safeMessage(failure)));
                }
            }
        } catch (SecurityException failure) {
            findings.add(error("MOD_JAR_INVALID", descriptor.jarPath().toString(), "", safeMessage(failure)));
        }
    }

    private static void validateCompiledCode(Path jar, ModDescriptor descriptor, List<Finding> findings) {
        String entrypoint = descriptor.manifest().entrypoint();
        if (entrypoint == null) {
            if (descriptor.containsCode()) {
                findings.add(error("ENTRYPOINT_MISSING", MANIFEST_PATH, "entrypoint",
                        "A jar containing classes must declare an entrypoint"));
            }
            return;
        }
        for (ModValidationFinding finding : new ModValidator().validate(jar, entrypoint).findings()) {
            Severity severity = finding.severity() == ModValidationFinding.Severity.ERROR
                    ? Severity.ERROR : Severity.WARNING;
            findings.add(new Finding(severity, finding.code(), finding.className(), finding.member(), finding.message()));
        }
    }

    private static Finding fromCatalog(ModFinding finding) {
        return new Finding(finding.severity() == ModFindingSeverity.ERROR ? Severity.ERROR : Severity.WARNING,
                finding.code(), finding.assetPath() == null ? "" : finding.assetPath(), "", finding.message());
    }

    private static Report report(List<Finding> findings) {
        return new Report(findings.stream().distinct().sorted(FINDING_ORDER).toList());
    }

    private static Finding error(String code, String location, String member, String message) {
        return new Finding(Severity.ERROR, code, location, member, message);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    public enum Severity { ERROR, WARNING }

    public record Finding(Severity severity, String code, String location, String member, String message) {
        public Finding {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(code, "code");
            location = location == null ? "" : location;
            member = member == null ? "" : member;
            Objects.requireNonNull(message, "message");
        }
    }

    public record Report(List<Finding> findings) {
        public Report {
            findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        }

        public boolean valid() {
            return findings.stream().noneMatch(finding -> finding.severity() == Severity.ERROR);
        }

        public List<String> numberedLines() {
            List<String> lines = new ArrayList<>(findings.size());
            for (int index = 0; index < findings.size(); index++) {
                Finding finding = findings.get(index);
                String where = finding.location().isEmpty() ? "" : " [" + finding.location()
                        + (finding.member().isEmpty() ? "" : "#" + finding.member()) + "]";
                lines.add((index + 1) + ". " + finding.severity() + " " + finding.code()
                        + where + " " + finding.message());
            }
            return List.copyOf(lines);
        }
    }

    @FunctionalInterface
    interface SnapshotHook {
        void afterSnapshot(Path immutableSnapshot) throws IOException;
    }
}
