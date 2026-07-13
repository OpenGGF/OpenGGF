package com.openggf.mods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable resolver indexes that own an open {@link PreparedAudioSession} lease.
 * Close this value only after every resolver using it has been retired.
 */
public final class PreparedModMusic implements AutoCloseable {
    private final int outputRate;
    private PreparedAudioSession session;
    private Map<TrackKey, Integer> trackIndices;
    private Map<SfxKey, Integer> sfxIndices;
    private Map<String, Map<Integer, TrackKey>> overrides;
    private final boolean permanentEmpty;
    private boolean closed;

    private PreparedModMusic(int outputRate, PreparedAudioSession session,
                             Map<TrackKey, Integer> trackIndices,
                             Map<SfxKey, Integer> sfxIndices,
                             Map<String, Map<Integer, TrackKey>> overrides,
                             boolean permanentEmpty) {
        this.outputRate = outputRate;
        this.session = session;
        this.trackIndices = trackIndices;
        this.sfxIndices = sfxIndices;
        this.overrides = overrides;
        this.permanentEmpty = permanentEmpty;
    }

    static PreparedModMusic permanentEmpty() {
        return new PreparedModMusic(0, null, Map.of(), Map.of(), Map.of(), true);
    }

    /**
     * Validates entirely in local state, then takes ownership of {@code session} on success.
     * A failure leaves the caller's session open and owned by the caller.
     */
    public static PreparedModMusic build(EffectiveModCatalog effective, ModTrackRegistry registry,
                                         PreparedAudioSession session, int outputRate) {
        return build(effective, registry, ModSfxRegistry.EMPTY, session, outputRate, null);
    }

    public static PreparedModMusic build(EffectiveModCatalog effective, ModTrackRegistry registry,
                                         ModSfxRegistry sfxRegistry, PreparedAudioSession session,
                                         int outputRate, String exposedOwner) {
        Objects.requireNonNull(effective, "effective");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(sfxRegistry, "sfxRegistry");
        Objects.requireNonNull(session, "session");
        if (outputRate < 8_000 || outputRate > 192_000) {
            throw new IllegalArgumentException("Output rate must be in 8000..192000");
        }
        synchronized (session) {
            if (session.isClosed()) throw new IllegalStateException("Prepared audio session is closed");
            List<PreparedTrack> preparedTracks = session.tracks();
            List<PreparedSfx> preparedSfx = session.sfx();
            List<ModDescriptor> descriptors = effective.orderedEnabled();
            LinkedHashSet<String> owners = validateEffectiveOrder(descriptors);
            for (String failed : session.failedOwners()) {
                if (!owners.contains(failed)) {
                    throw new IllegalArgumentException("Prepared session names unknown failed owner: " + failed);
                }
            }

            LinkedHashMap<TrackKey, Integer> localIndices = new LinkedHashMap<>();
            for (int index = 0; index < preparedTracks.size(); index++) {
                PreparedTrack track = preparedTracks.get(index);
                String owner = track.key().modId();
                if (!owners.contains(owner)) {
                    throw new IllegalArgumentException("Prepared track belongs to non-effective owner: " + owner);
                }
                if (session.failedOwners().contains(owner)) {
                    throw new IllegalArgumentException("Prepared track belongs to failed owner: " + owner);
                }
                if (track.pcm().sampleRate() != outputRate) {
                    throw new IllegalArgumentException("Prepared tracks do not share the fixed output rate");
                }
                if (registry.find(track.key()).isEmpty()) {
                    throw new IllegalArgumentException("Prepared track is absent from the validated registry: " + track.key());
                }
                if (exposedOwner == null || exposedOwner.equals(owner)) localIndices.putIfAbsent(track.key(), index);
                if (localIndices.get(track.key()) != null && !localIndices.get(track.key()).equals(index)) {
                    throw new IllegalArgumentException("Duplicate prepared track key: " + track.key());
                }
            }
            for (ModAudioTrack declaredTrack : registry.tracks()) {
                String owner = declaredTrack.key().modId();
                if (owners.contains(owner) && !session.failedOwners().contains(owner)
                        && (exposedOwner == null || exposedOwner.equals(owner))
                        && !localIndices.containsKey(declaredTrack.key())) {
                    throw new IllegalArgumentException(
                            "Effective registry track was not prepared: " + declaredTrack.key());
                }
            }

            LinkedHashMap<SfxKey, Integer> localSfxIndices = new LinkedHashMap<>();
            for (int index = 0; index < preparedSfx.size(); index++) {
                PreparedSfx value = preparedSfx.get(index);
                String owner = value.key().modId();
                if (!owners.contains(owner) || session.failedOwners().contains(owner)) {
                    throw new IllegalArgumentException("Prepared SFX belongs to ineligible owner: " + owner);
                }
                if (value.pcm().sampleRate() != outputRate || sfxRegistry.find(value.key()).isEmpty()) {
                    throw new IllegalArgumentException("Prepared SFX is inconsistent with the validated registry: " + value.key());
                }
                if (exposedOwner == null || exposedOwner.equals(owner)) {
                    if (localSfxIndices.putIfAbsent(value.key(), index) != null) {
                        throw new IllegalArgumentException("Duplicate prepared SFX key: " + value.key());
                    }
                }
            }
            for (ModAudioSfx declared : sfxRegistry.sfx()) {
                String owner = declared.key().modId();
                if (owners.contains(owner) && !session.failedOwners().contains(owner)
                        && (exposedOwner == null || exposedOwner.equals(owner))
                        && !localSfxIndices.containsKey(declared.key())) {
                    throw new IllegalArgumentException("Effective registry SFX was not prepared: " + declared.key());
                }
            }

            LinkedHashMap<String, LinkedHashMap<Integer, TrackKey>> mutableOverrides = new LinkedHashMap<>();
            for (String game : List.of("s1", "s2", "s3k")) mutableOverrides.put(game, new LinkedHashMap<>());
            for (ModDescriptor descriptor : descriptors) {
                String owner = descriptor.manifest().id();
                if (session.failedOwners().contains(owner)) continue;
                Map<Integer, String> declared = descriptor.manifest().audioOverrides();
                if (declared.isEmpty()) continue;
                if (descriptor.manifest().type() != ModType.PATCH) {
                    throw new IllegalArgumentException("Only patch mods may override stock music");
                }
                String baseGame = requireGameCode(descriptor.manifest().baseGame());
                ArrayList<Map.Entry<Integer, String>> ordered = new ArrayList<>(declared.entrySet());
                ordered.sort(Comparator.comparingInt(Map.Entry::getKey));
                for (Map.Entry<Integer, String> override : ordered) {
                    int musicId = override.getKey();
                    if (musicId < 0) throw new IllegalArgumentException("Stock music id must be nonnegative");
                    TrackKey target = new TrackKey(owner, override.getValue());
                    ModAudioTrack declaredTrack = registry.find(target).orElseThrow(() ->
                            new IllegalArgumentException("Override target is absent from validated registry: " + target));
                    if (!declaredTrack.key().modId().equals(owner)) {
                        throw new IllegalArgumentException("Override target owner differs from manifest owner");
                    }
                    if (!localIndices.containsKey(target)) {
                        throw new IllegalArgumentException("Override target was not prepared: " + target);
                    }
                    mutableOverrides.get(baseGame).put(musicId, target);
                }
            }

            Map<TrackKey, Integer> frozenIndices = immutableLinkedMap(localIndices);
            LinkedHashMap<String, Map<Integer, TrackKey>> frozenOverrides = new LinkedHashMap<>();
            mutableOverrides.forEach((game, values) -> frozenOverrides.put(game, immutableLinkedMap(values)));
            if (session.isClosed()) throw new IllegalStateException("Prepared audio session closed during build");
            return new PreparedModMusic(outputRate, session, frozenIndices,
                    immutableLinkedMap(localSfxIndices),
                    immutableLinkedMap(frozenOverrides), false);
        }
    }

    public synchronized int outputRate() {
        requireOpen();
        return outputRate;
    }

    synchronized PreparedTrack resolve(TrackKey key) {
        requireOpen();
        Integer index = trackIndices.get(key);
        if (index == null) return null;
        List<PreparedTrack> liveTracks = session.tracks();
        if (index < 0 || index >= liveTracks.size()) throw new IllegalStateException("Prepared track index is stale");
        PreparedTrack resolved = liveTracks.get(index);
        if (!resolved.key().equals(key)) throw new IllegalStateException("Prepared track index/key mismatch");
        return resolved;
    }

    synchronized ResolvedMusic resolveStockOverride(String gameCode, int musicId) {
        requireOpen();
        Map<Integer, TrackKey> byId = overrides.get(gameCode);
        TrackKey key = byId == null ? null : byId.get(musicId);
        if (key == null) return null;
        PreparedTrack track = resolve(key);
        if (track == null) throw new IllegalStateException("Override target index disappeared");
        return new ResolvedMusic(musicId, track);
    }

    public synchronized java.util.Optional<PreparedSfx> resolveSfx(SfxKey key) {
        requireOpen();
        Integer index = sfxIndices.get(key);
        if (index == null) return java.util.Optional.empty();
        List<PreparedSfx> live = session.sfx();
        if (index < 0 || index >= live.size()) throw new IllegalStateException("Prepared SFX index is stale");
        PreparedSfx resolved = live.get(index);
        if (!resolved.key().equals(key)) throw new IllegalStateException("Prepared SFX index/key mismatch");
        return java.util.Optional.of(resolved);
    }

    synchronized Map<TrackKey, Integer> trackIndexSnapshot() {
        requireOpen();
        return trackIndices;
    }

    synchronized Map<String, Map<Integer, TrackKey>> overrideSnapshot() {
        requireOpen();
        return overrides;
    }

    public synchronized boolean isClosed() { return closed; }

    @Override
    public synchronized void close() {
        if (permanentEmpty || closed) return;
        closed = true;
        trackIndices = Map.of();
        sfxIndices = Map.of();
        overrides = Map.of();
        PreparedAudioSession owned = session;
        session = null;
        owned.close();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Prepared mod music is closed");
    }

    private static LinkedHashSet<String> validateEffectiveOrder(List<ModDescriptor> descriptors) {
        LinkedHashSet<String> owners = new LinkedHashSet<>();
        for (ModDescriptor descriptor : descriptors) {
            String owner = descriptor.manifest().id();
            if (!owners.add(owner)) throw new IllegalArgumentException("Duplicate effective owner: " + owner);
        }
        Set<String> seen = new LinkedHashSet<>();
        for (ModDescriptor descriptor : descriptors) {
            for (ModDependency dependency : descriptor.manifest().dependencies()) {
                if (!owners.contains(dependency.id())) {
                    throw new IllegalArgumentException("Effective dependency is missing: " + dependency.id());
                }
                if (!seen.contains(dependency.id())) {
                    throw new IllegalArgumentException("Effective catalog is not dependency-first");
                }
            }
            seen.add(descriptor.manifest().id());
        }
        return owners;
    }

    private static String requireGameCode(String gameCode) {
        if (!"s1".equals(gameCode) && !"s2".equals(gameCode) && !"s3k".equals(gameCode)) {
            throw new IllegalArgumentException("Base game must be exactly s1, s2, or s3k");
        }
        return gameCode;
    }

    private static <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
