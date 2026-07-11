package com.openggf.mods;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.io.PackedModAssetRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Performs bounded static audio validation before eligibility freezes. */
public final class ModCatalogValidator {
    public static final String AUDIO_MANIFEST_PATH = "audio/audio-manifest.yaml";
    private final Path root;
    private final ModInputLimits limits;
    private final StockMusicDomain stockMusicDomain;
    private final PreflightHook preflightHook;

    public ModCatalogValidator(Path normalizedModRoot, ModInputLimits limits,
                               StockMusicDomain stockMusicDomain) {
        this(normalizedModRoot, limits, stockMusicDomain, PreflightHook.NONE);
    }

    ModCatalogValidator(Path normalizedModRoot, ModInputLimits limits,
                        StockMusicDomain stockMusicDomain, PreflightHook preflightHook) {
        Objects.requireNonNull(normalizedModRoot, "normalizedModRoot");
        Path normalized = normalizedModRoot.toAbsolutePath().normalize();
        if (!normalizedModRoot.equals(normalized)) {
            throw new IllegalArgumentException("Mod root must be absolute and normalized");
        }
        this.root = normalizedModRoot;
        this.limits = Objects.requireNonNull(limits, "limits");
        this.stockMusicDomain = Objects.requireNonNull(stockMusicDomain, "stockMusicDomain");
        this.preflightHook = Objects.requireNonNull(preflightHook, "preflightHook");
    }

    public ValidationResult validate(List<? extends ModCatalogEntry> catalog) {
        List<ModCatalogEntry> entries = new ArrayList<>(Objects.requireNonNull(catalog, "catalog"));
        Preflight preflight = preflight(entries);
        if (preflight.globalFailure() != null) {
            return globalFailure(entries, preflight.globalFailure());
        }
        try {
            preflightHook.afterPreflight(preflight.expectedSizes());
        } catch (IOException | SecurityException error) {
            ModFinding failure = error("REPOSITORY_PREFLIGHT_FAILED", safeMessage(error), null);
            return globalFailure(entries, failure);
        }
        List<ModAudioManifest> parsed = new ArrayList<>(java.util.Collections.nCopies(entries.size(), null));
        for (int index = 0; index < entries.size(); index++) {
            if (!(entries.get(index) instanceof ModDescriptor descriptor)) continue;
            List<ModFinding> findings = new ArrayList<>(descriptor.findings());
            ModFinding pathFailure = preflight.pathFailures().get(descriptor.jarPath());
            ModAudioManifest audio = pathFailure == null
                    ? validateDescriptor(descriptor, findings,
                    preflight.expectedSizes().get(descriptor.jarPath())) : null;
            if (pathFailure != null) findings.add(pathFailure);
            parsed.set(index, audio);
            entries.set(index, copy(descriptor, findings));
        }
        addOverrideCollisions(entries);
        List<ModAudioTrack> tracks = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index) instanceof ModDescriptor descriptor
                    && !descriptor.hasErrors() && parsed.get(index) != null) {
                tracks.addAll(parsed.get(index).tracks());
            }
        }
        return new ValidationResult(entries, tracks.isEmpty()
                ? ModTrackRegistry.EMPTY : new ModTrackRegistry(tracks));
    }

    private ValidationResult globalFailure(List<ModCatalogEntry> entries, ModFinding failure) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index) instanceof ModDescriptor descriptor) {
                List<ModFinding> findings = new ArrayList<>(descriptor.findings());
                findings.add(failure);
                entries.set(index, copy(descriptor, findings));
            }
        }
        entries.add(new RepositoryScanFailure(root, List.of(failure)));
        return new ValidationResult(entries, ModTrackRegistry.EMPTY);
    }

    private ModAudioManifest validateDescriptor(ModDescriptor descriptor, List<ModFinding> findings,
                                                long expectedBytes) {
        try (PackedModAssetRoot assets = ModAssetRoot.jar(
                root, descriptor.jarPath(), limits, expectedBytes)) {
            if (!assets.immutableSha256().equals(descriptor.sha256())) {
                findings.add(error("MOD_JAR_CHANGED", "Packed mod digest changed after discovery", null));
                return null;
            }
            List<String> names = assets.validatedEntryNames();
            if (!names.contains(AUDIO_MANIFEST_PATH)) {
                if (!descriptor.manifest().audioOverrides().isEmpty()) {
                    findings.add(error("AUDIO_MANIFEST_MISSING",
                            "audioOverrides require audio/audio-manifest.yaml", AUDIO_MANIFEST_PATH));
                }
                return null;
            }
            byte[] bytes = assets.readBounded(AUDIO_MANIFEST_PATH, limits.maxMetadataBytes());
            ModAudioManifest audio = new ModAudioManifestParser(
                    descriptor.manifest().id(), limits).parse(bytes);
            Set<String> inventory = new HashSet<>(names);
            for (ModAudioTrack track : audio.tracks()) {
                if (!inventory.contains(track.assetPath())) {
                    findings.add(error("AUDIO_ASSET_MISSING",
                            "Missing audio asset: " + track.assetPath(), track.assetPath()));
                }
            }
            for (ModAudioSfx sfx : audio.sfx()) {
                if (!inventory.contains(sfx.assetPath())) {
                    findings.add(error("AUDIO_ASSET_MISSING",
                            "Missing audio asset: " + sfx.assetPath(), sfx.assetPath()));
                }
            }
            if (!audio.sfx().isEmpty()) {
                findings.add(error("SFX_UNSUPPORTED_PHASE1",
                        "Streamed SFX are parsed but unsupported in Phase 1", AUDIO_MANIFEST_PATH));
            }
            validateOverrides(descriptor, audio, findings);
            return audio;
        } catch (ModManifestException error) {
            findings.add(error("AUDIO_MANIFEST_INVALID", safeMessage(error), AUDIO_MANIFEST_PATH));
            return null;
        } catch (IOException | SecurityException error) {
            findings.add(error("MOD_JAR_CHANGED", safeMessage(error), descriptor.jarPath().getFileName().toString()));
            return null;
        }
    }

    private Preflight preflight(List<ModCatalogEntry> entries) {
        List<ModDescriptor> descriptors = entries.stream().filter(ModDescriptor.class::isInstance)
                .map(ModDescriptor.class::cast).toList();
        if (descriptors.size() > limits.maxModJars()) {
            return Preflight.global(error("REPOSITORY_JAR_LIMIT_EXCEEDED",
                    "Catalog contains more than " + limits.maxModJars() + " mod jars", null));
        }
        Map<Path, Long> sizes = new LinkedHashMap<>();
        Map<Path, ModFinding> failures = new HashMap<>();
        long total = 0;
        for (ModDescriptor descriptor : descriptors) {
            Path path = descriptor.jarPath();
            try {
                BasicFileAttributes attributes = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (Files.isSymbolicLink(path) || !attributes.isRegularFile()) {
                    failures.put(path, error("MOD_JAR_INVALID",
                            "Mod jar is not a regular non-symlink file", path.getFileName().toString()));
                    continue;
                }
                if (attributes.size() > limits.maxJarBytes()) {
                    failures.put(path, error("MOD_JAR_INVALID",
                            "Mod jar exceeds packed-file byte limit", path.getFileName().toString()));
                    continue;
                }
                total = Math.addExact(total, attributes.size());
                sizes.put(path, attributes.size());
            } catch (IOException | SecurityException error) {
                failures.put(path, error("MOD_JAR_INVALID", safeMessage(error),
                        path.getFileName().toString()));
                continue;
            } catch (ArithmeticException error) {
                return Preflight.global(error("REPOSITORY_VALIDATION_BYTES_EXCEEDED",
                        "Repository byte count overflow", null));
            }
            if (total > limits.maxRepositoryValidationBytes()) {
                return Preflight.global(error("REPOSITORY_VALIDATION_BYTES_EXCEEDED",
                        "Repository exceeds validation byte budget", null));
            }
        }
        return new Preflight(Map.copyOf(sizes), Map.copyOf(failures), null);
    }

    private void validateOverrides(ModDescriptor descriptor, ModAudioManifest audio,
                                   List<ModFinding> findings) {
        Map<Integer, String> overrides = descriptor.manifest().audioOverrides();
        if (!overrides.isEmpty() && descriptor.manifest().type() != ModType.PATCH) {
            findings.add(error("STANDALONE_AUDIO_OVERRIDE",
                    "Standalone manifests cannot override stock music ids", AUDIO_MANIFEST_PATH));
        }
        Set<String> tracks = new HashSet<>();
        audio.tracks().forEach(track -> tracks.add(track.key().localName()));
        for (Map.Entry<Integer, String> override : overrides.entrySet()) {
            if (!tracks.contains(override.getValue())) {
                findings.add(error("AUDIO_OVERRIDE_TRACK_MISSING",
                        "Override references undeclared track: " + override.getValue(), AUDIO_MANIFEST_PATH));
            }
            if (!validMusicId(descriptor.manifest().baseGame(), override.getKey())) {
                findings.add(error("AUDIO_OVERRIDE_ID_INVALID",
                        "Stock music id is outside the declared base-game music domain: " + override.getKey(),
                        AUDIO_MANIFEST_PATH));
            }
        }
    }

    private boolean validMusicId(String baseGame, int id) {
        return baseGame != null && stockMusicDomain.contains(baseGame, id);
    }

    private static void addOverrideCollisions(List<ModCatalogEntry> entries) {
        Map<OverrideKey, List<String>> owners = new LinkedHashMap<>();
        for (ModCatalogEntry entry : entries) {
            if (entry instanceof ModDescriptor descriptor && descriptor.manifest().baseGame() != null) {
                descriptor.manifest().audioOverrides().keySet().forEach(id -> owners
                        .computeIfAbsent(new OverrideKey(descriptor.manifest().baseGame(), id), ignored -> new ArrayList<>())
                        .add(descriptor.manifest().id()));
            }
        }
        for (Map.Entry<OverrideKey, List<String>> collision : owners.entrySet()) {
            if (collision.getValue().size() < 2) continue;
            String message = "Audio override conflict between owners " + String.join(", ", collision.getValue())
                    + "; later eligible order wins";
            for (int index = 0; index < entries.size(); index++) {
                if (entries.get(index) instanceof ModDescriptor descriptor
                        && collision.getValue().contains(descriptor.manifest().id())
                        && descriptor.manifest().baseGame().equals(collision.getKey().baseGame)
                        && descriptor.manifest().audioOverrides().containsKey(collision.getKey().musicId)) {
                    List<ModFinding> findings = new ArrayList<>(descriptor.findings());
                    findings.add(new ModFinding(ModFindingSeverity.WARNING,
                            "AUDIO_OVERRIDE_CONFLICT", message, AUDIO_MANIFEST_PATH));
                    entries.set(index, copy(descriptor, findings));
                }
            }
        }
    }

    private static ModDescriptor copy(ModDescriptor descriptor, List<ModFinding> findings) {
        return new ModDescriptor(descriptor.jarPath(), descriptor.manifest(), descriptor.sha256(),
                descriptor.containsCode(), findings);
    }
    private static ModFinding error(String code,String message,String path){return new ModFinding(ModFindingSeverity.ERROR,code,message,path);}
    private static String safeMessage(Throwable error){String message=error.getMessage();return message==null||message.isBlank()?error.getClass().getSimpleName():message;}
    private record OverrideKey(String baseGame,int musicId){}
    private record Preflight(Map<Path, Long> expectedSizes, Map<Path, ModFinding> pathFailures,
                             ModFinding globalFailure) {
        private static Preflight global(ModFinding finding) {
            return new Preflight(Map.of(), Map.of(), finding);
        }
    }

    @FunctionalInterface
    public interface StockMusicDomain {
        boolean contains(String baseGame, int musicId);
    }

    @FunctionalInterface
    interface PreflightHook {
        PreflightHook NONE = ignored -> { };
        void afterPreflight(Map<Path, Long> expectedSizes) throws IOException;
    }

    public record ValidationResult(List<ModCatalogEntry> entries, ModTrackRegistry registry) {
        public ValidationResult {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            Objects.requireNonNull(registry, "registry");
        }
    }
}
