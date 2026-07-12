package com.openggf.mods;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.io.PackedModAssetRoot;
import com.openggf.io.SnapshotModAssetRoot;

import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;
import java.util.Locale;

import static java.security.MessageDigest.getInstance;

/** Launch-only, owner-atomic decode/resample transaction. Performs no playback work. */
public final class ModAudioPreparer {
    private final Path root;
    private final ModInputLimits limits;
    private final ModRuntimeFindingStore findingStore;
    private final FailureStateSink stateSink;
    private final PcmDecoder decoder;
    private final PcmResampler resampler;
    private Map<CacheKey, CachedPcm> cache = Map.of();
    private Map<PrimaryKey, CacheKey> primaryCache = Map.of();
    private final Map<CacheKey, CachedPcm> activePcm = new LinkedHashMap<>();
    private final Map<CacheKey, Integer> activeReferences = new LinkedHashMap<>();

    public ModAudioPreparer(Path normalizedModRoot, ModInputLimits limits,
                            ModRuntimeFindingStore findingStore, FailureStateSink stateSink) {
        this(normalizedModRoot, limits, findingStore, stateSink, new PcmDecoder(), new PcmResampler());
    }

    ModAudioPreparer(Path normalizedModRoot, ModInputLimits limits,
                     ModRuntimeFindingStore findingStore, FailureStateSink stateSink,
                     PcmDecoder decoder, PcmResampler resampler) {
        Objects.requireNonNull(normalizedModRoot, "normalizedModRoot");
        Path normalized = normalizedModRoot.toAbsolutePath().normalize();
        if (!normalized.equals(normalizedModRoot)) throw new IllegalArgumentException("Mod root must be absolute and normalized");
        this.root = normalized;
        this.limits = Objects.requireNonNull(limits, "limits");
        this.findingStore = Objects.requireNonNull(findingStore, "findingStore");
        this.stateSink = Objects.requireNonNull(stateSink, "stateSink");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.resampler = Objects.requireNonNull(resampler, "resampler");
    }

    public synchronized PreparedAudioSession prepare(EffectiveModCatalog effective,
                                                     ModTrackRegistry registry, int outputRate) {
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(registry, "registry");
        if (!activeReferences.isEmpty()) {
            throw new IllegalStateException("Close the active prepared audio session before preparing another");
        }
        if (outputRate < limits.minAudioSampleRate() || outputRate > limits.maxAudioSampleRate()) {
            throw new IllegalArgumentException("Output sample rate is outside configured limits");
        }
        requireDependencyFirstOrder(effective);
        Map<String, List<ModAudioTrack>> tracksByOwner = new LinkedHashMap<>();
        for (ModDescriptor descriptor : effective.orderedEnabled()) {
            tracksByOwner.put(descriptor.manifest().id(), new ArrayList<>());
        }
        for (ModAudioTrack track : registry.tracks()) {
            List<ModAudioTrack> ownerTracks = tracksByOwner.get(track.key().modId());
            if (ownerTracks != null) ownerTracks.add(track);
        }

        LinkedHashSet<String> failed = new LinkedHashSet<>();
        Map<String, List<ModFinding>> ownerFindings = new LinkedHashMap<>();
        Map<String, List<PreparedTrack>> preparedByOwner = new LinkedHashMap<>();
        Map<String, Set<CacheKey>> keysByOwner = new LinkedHashMap<>();
        LinkedHashMap<CacheKey, CachedPcm> nextCache = new LinkedHashMap<>();
        LinkedHashMap<PrimaryKey, CacheKey> nextPrimaryCache = new LinkedHashMap<>();
        long retainedBytes = activeBytes();
        for (ModDescriptor descriptor : effective.orderedEnabled()) {
            String owner = descriptor.manifest().id();
            List<ModAudioTrack> ownerTracks = tracksByOwner.get(owner);
            String failedDependencyBeforeIo = descriptor.manifest().dependencies().stream()
                    .map(ModDependency::id).filter(failed::contains).findFirst().orElse(null);
            if (failedDependencyBeforeIo != null) {
                failed.add(owner);
                ownerFindings.put(owner, List.of(error("AUDIO_DEPENDENCY_FAILED",
                        "Dependency failed audio preparation: " + failedDependencyBeforeIo)));
                continue;
            }
            if (ownerTracks == null || ownerTracks.isEmpty()) {
                ownerFindings.put(owner, List.of());
                continue;
            }
            List<PreparedTrack> ownerPrepared = new ArrayList<>();
            LinkedHashMap<CacheKey, CachedPcm> ownerCache = new LinkedHashMap<>();
            LinkedHashMap<PrimaryKey, CacheKey> ownerPrimaryCache = new LinkedHashMap<>();
            long provisionalBytes = 0;
            try {
            SnapshotModAssetRoot sourceAssets = descriptor.retainedSource() != null
                    ? descriptor.retainedSource() : ModAssetRoot.jar(root,descriptor.jarPath(),limits);
            try (ModAssetRoot assets = descriptor.retainedSource() != null
                    ? ModAssetRoot.nonClosingView(sourceAssets) : sourceAssets) {
                if (!sourceAssets.immutableSha256().equals(descriptor.sha256())) {
                    throw new ChangedJar("Packed mod digest changed after eligibility freeze");
                }
                for (ModAudioTrack track : ownerTracks) {
                    String codec = codec(track.assetPath());
                    PrimaryKey primaryKey = new PrimaryKey(descriptor.sha256(), track.assetPath(), codec, outputRate);
                    CacheKey cacheKey = primaryCache.get(primaryKey);
                    if (cacheKey == null) cacheKey = nextPrimaryCache.get(primaryKey);
                    if (cacheKey == null) cacheKey = ownerPrimaryCache.get(primaryKey);
                    CachedPcm cached = cacheKey == null ? null : cache.get(cacheKey);
                    if (cached == null && cacheKey != null) cached = nextCache.get(cacheKey);
                    if (cached == null && cacheKey != null) cached = ownerCache.get(cacheKey);
                    if (cached == null && cacheKey != null) cached = activePcm.get(cacheKey);
                    String sourceSha;
                    byte[] encoded = null;
                    if (cached == null) {
                        evictUnselectedCache(nextCache.keySet(), ownerCache.keySet());
                        long remainingBeforeRead = limits.maxAudioCacheBytes() - retainedBytes - provisionalBytes;
                        long encodedCap = Math.min(limits.maxAssetBytes(), remainingBeforeRead / 2L);
                        if (encodedCap <= 0) throw new IOException("No working budget remains for encoded audio");
                        encoded = assets.readBounded(track.assetPath(), encodedCap);
                        sourceSha = sha256(encoded);
                        cacheKey = new CacheKey(sourceSha, outputRate, codec);
                        cached = cache.get(cacheKey);
                        if (cached == null) cached = nextCache.get(cacheKey);
                        if (cached == null) cached = ownerCache.get(cacheKey);
                        if (cached == null) cached = activePcm.get(cacheKey);
                    } else {
                        sourceSha = cacheKey.sourceSha256();
                    }
                    boolean firstOwnerUse = !ownerCache.containsKey(cacheKey);
                    if (cached == null) {
                        long remaining = limits.maxAudioCacheBytes() - retainedBytes - provisionalBytes;
                        ModInputLimits workingLimits = limits.withMaxAudioCacheBytes(remaining);
                        PcmData source = decoder.decode(track.assetPath(), encoded, workingLimits);
                        int sourceRate = source.sampleRate();
                        int sourceFrames = source.frameCount();
                        long resampleRemaining = remaining - encoded.length;
                        if (resampleRemaining <= 0) throw new IOException("No cache budget remains for resampling");
                        PcmData output = resampler.resample(source, outputRate,
                                workingLimits.withMaxAudioCacheBytes(resampleRemaining));
                        cached = new CachedPcm(output, sourceRate, sourceFrames);
                    }
                    ownerPrimaryCache.put(primaryKey, cacheKey);
                    ownerCache.putIfAbsent(cacheKey, cached);
                    if (firstOwnerUse && !nextCache.containsKey(cacheKey)
                            && !activePcm.containsKey(cacheKey)) {
                        provisionalBytes = Math.addExact(provisionalBytes, cached.pcm().byteSize());
                    }
                    long start = 0;
                    long end = 0;
                    if (track.loop()) {
                        ModAudioAssetValidation.validateLoop(track, cached.sourceFrames());
                        long sourceEnd = track.loopEndFrame().orElse(cached.sourceFrames());
                        start = resampler.convertFrame(track.loopStartFrame(), cached.sourceRate(), outputRate);
                        end = sourceEnd == cached.sourceFrames() ? cached.pcm().frameCount()
                                : resampler.convertFrame(sourceEnd, cached.sourceRate(), outputRate);
                    }
                    ownerPrepared.add(new PreparedTrack(track.key(), cached.pcm(), start, end,
                            track.gain(), track.tempoEffects(), sourceSha));
                }
                long ownerNewBytes = provisionalBytes;
                if (Math.addExact(retainedBytes, ownerNewBytes) > limits.maxAudioCacheBytes()) {
                    throw new IOException("Prepared audio cache budget exceeded");
                }
                retainedBytes += ownerNewBytes;
                nextCache.putAll(ownerCache);
                nextPrimaryCache.putAll(ownerPrimaryCache);
                preparedByOwner.put(owner, List.copyOf(ownerPrepared));
                keysByOwner.put(owner, Set.copyOf(ownerCache.keySet()));
                ownerFindings.put(owner, List.of());
            }} catch (ChangedJar error) {
                failOwner(owner, "MOD_JAR_CHANGED", error.getMessage(), failed, ownerFindings);
            } catch (IOException | IllegalArgumentException | ArithmeticException | SecurityException error) {
                failOwner(owner, "AUDIO_PREPARATION_FAILED", safeMessage(error), failed, ownerFindings);
            }
        }

        List<ModFinding> sessionFindings = new ArrayList<>();
        for (ModDescriptor descriptor : effective.orderedEnabled()) {
            String owner = descriptor.manifest().id();
            List<ModFinding> findings = ownerFindings.getOrDefault(owner, List.of());
            findingStore.replaceOwner(owner, findings);
            sessionFindings.addAll(findings);
        }
        if (!failed.isEmpty()) {
            ModStateSaveResult save;
            try {
                save = Objects.requireNonNull(stateSink.disableAndSave(
                        Collections.unmodifiableSet(new LinkedHashSet<>(failed))), "state save result");
            } catch (RuntimeException error) {
                save = new ModStateSaveResult.Failed(safeMessage(error));
            }
            if (save instanceof ModStateSaveResult.Failed failure) {
                ModFinding finding = error("MOD_STATE_SAVE_FAILED", failure.message());
                sessionFindings.add(finding);
                for (String owner : failed) {
                    List<ModFinding> updated = new ArrayList<>(findingStore.findingsFor(owner));
                    updated.add(finding);
                    findingStore.replaceOwner(owner, updated);
                }
            }
        }
        List<PreparedTrack> prepared = new ArrayList<>();
        LinkedHashSet<CacheKey> leaseKeys = new LinkedHashSet<>();
        for (ModDescriptor descriptor : effective.orderedEnabled()) {
            if (!failed.contains(descriptor.manifest().id())) {
                prepared.addAll(preparedByOwner.getOrDefault(descriptor.manifest().id(), List.of()));
                leaseKeys.addAll(keysByOwner.getOrDefault(descriptor.manifest().id(), Set.of()));
            }
        }
        cache = Collections.unmodifiableMap(new LinkedHashMap<>(nextCache));
        primaryCache = Collections.unmodifiableMap(new LinkedHashMap<>(nextPrimaryCache));
        registerActive(leaseKeys);
        return new PreparedAudioSession(prepared, sessionFindings, failed,
                () -> releaseActive(leaseKeys));
    }

    private static void failOwner(String owner, String code, String message,
                                  Set<String> failed, Map<String, List<ModFinding>> findings) {
        failed.add(owner);
        findings.put(owner, List.of(error(code, message)));
    }

    private static ModFinding error(String code, String message) {
        return new ModFinding(ModFindingSeverity.ERROR, code, message, null);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String codec(String assetPath) throws IOException {
        String lower = assetPath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".wav")) return "wav";
        if (lower.endsWith(".ogg")) return "ogg";
        throw new IOException("Unsupported audio asset extension");
    }

    private static void requireDependencyFirstOrder(EffectiveModCatalog effective) {
        Set<String> owners = new LinkedHashSet<>();
        for (ModDescriptor descriptor : effective.orderedEnabled()) {
            if (!owners.add(descriptor.manifest().id())) {
                throw new IllegalArgumentException("Duplicate effective owner: " + descriptor.manifest().id());
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        for (ModDescriptor descriptor : effective.orderedEnabled()) {
            for (ModDependency dependency : descriptor.manifest().dependencies()) {
                if (!owners.contains(dependency.id())) {
                    throw new IllegalArgumentException("Effective dependency is missing: " + dependency.id());
                }
                if (!seen.contains(dependency.id())) {
                    throw new IllegalArgumentException("Effective catalog is not dependency-first: "
                            + descriptor.manifest().id() + " before " + dependency.id());
                }
            }
            seen.add(descriptor.manifest().id());
        }
    }

    private long activeBytes() {
        long bytes = 0;
        for (CachedPcm value : activePcm.values()) bytes = Math.addExact(bytes, value.pcm().byteSize());
        return bytes;
    }

    private void evictUnselectedCache(Set<CacheKey> selected, Set<CacheKey> ownerSelected) {
        LinkedHashSet<CacheKey> retained = new LinkedHashSet<>(activePcm.keySet());
        retained.addAll(selected);
        retained.addAll(ownerSelected);
        LinkedHashMap<CacheKey, CachedPcm> retainedCache = new LinkedHashMap<>();
        for (var entry : cache.entrySet()) {
            if (retained.contains(entry.getKey())) retainedCache.put(entry.getKey(), entry.getValue());
        }
        cache = Collections.unmodifiableMap(retainedCache);
        LinkedHashMap<PrimaryKey, CacheKey> retainedPrimary = new LinkedHashMap<>();
        for (var entry : primaryCache.entrySet()) {
            if (retained.contains(entry.getValue())) retainedPrimary.put(entry.getKey(), entry.getValue());
        }
        primaryCache = Collections.unmodifiableMap(retainedPrimary);
    }

    private void registerActive(Set<CacheKey> keys) {
        for (CacheKey key : keys) {
            CachedPcm pcm = cache.get(key);
            if (pcm == null) pcm = activePcm.get(key);
            if (pcm == null) throw new IllegalStateException("Prepared PCM missing from ownership ledger");
            activePcm.putIfAbsent(key, pcm);
            activeReferences.merge(key, 1, Integer::sum);
        }
    }

    private synchronized void releaseActive(Set<CacheKey> keys) {
        for (CacheKey key : keys) {
            Integer references = activeReferences.get(key);
            if (references == null) continue;
            if (references == 1) {
                activeReferences.remove(key);
                activePcm.remove(key);
            } else {
                activeReferences.put(key, references - 1);
            }
        }
    }

    public interface FailureStateSink {
        ModStateSaveResult disableAndSave(Set<String> owners);

        static FailureStateSink pending(PendingModStateEditor editor) {
            Objects.requireNonNull(editor, "editor");
            return owners -> {
                editor.setEnabledCascade(owners, false);
                return editor.save();
            };
        }
    }

    private record CacheKey(String sourceSha256, int outputRate, String codec) { }
    private record PrimaryKey(String jarSha256, String assetPath, String codec, int outputRate) { }
    private record CachedPcm(PcmData pcm, int sourceRate, int sourceFrames) { }
    private static final class ChangedJar extends IOException {
        private ChangedJar(String message) { super(message); }
    }
}
