package com.openggf.tools.audio.completerun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Immutable tooling-only envelope for one complete-run audio capture.
 *
 * <p>The model deliberately carries no runtime owners or game-specific fields. Profiles provide
 * the strict role and state inventory used to validate a game's records.
 */
public final class CompleteRunAudioTrace {
    public static final String SCHEMA = "complete_run_audio.v1";
    public static final int CHUNK_FRAME_ROWS = 4_096;
    public static final int MAX_CUTOFF_SERVICES = 65_536;
    public static final int MAX_CUTOFF_CHIP_EVENTS = 65_536;
    public static final int MAX_CUTOFF_SNAPSHOT_BYTES = 65_536;
    public static final int MAX_NATIVE_FRAME_EVENTS = 65_536;
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern CRC32 = Pattern.compile("[0-9a-f]{8}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private CompleteRunAudioTrace() {
    }

    public sealed interface Record permits Baseline, Frame, Lifecycle, CutoffFrontier, Terminal {
    }

    public enum HardwareRole { DAC, FM1, FM2, FM3, FM4, FM5, FM6, PSG1, PSG2, PSG3 }

    public enum OwnerClass { NONE, MUSIC, SFX, SPECIAL_SFX, COMMAND }

    /** Numeric owner ordinals are interpreted only within this explicit namespace. */
    public enum OwnerOrigin { NONE, BASELINE, REQUEST }

    /** Complete, profile-owned immutable identity for one pinned ROM/movie/manifest fixture. */
    public record CompleteRunFixture(String romSha1, String romCrc32, String bk2Sha256,
            long bk2RowCount, String runManifestSha256, List<ManifestSegment> segments,
            int firstFrame, int exclusiveEnd) {
        public CompleteRunFixture {
            lowercaseHex(romSha1, SHA1, "ROM SHA-1");
            lowercaseHex(romCrc32, CRC32, "ROM CRC32");
            lowercaseHex(bk2Sha256, SHA256, "BK2 SHA-256");
            lowercaseHex(runManifestSha256, SHA256, "run manifest SHA-256");
            if (bk2RowCount <= 0 || firstFrame < 0 || exclusiveEnd <= firstFrame
                    || exclusiveEnd > bk2RowCount) {
                throw new IllegalArgumentException("fixture comparison interval is outside the BK2 row count");
            }
            segments = List.copyOf(Objects.requireNonNull(segments, "manifest segments"));
            if (segments.isEmpty()) {
                throw new IllegalArgumentException("manifest segments must not be empty");
            }
            int previousEnd = -1;
            Set<String> ids = new LinkedHashSet<>();
            for (ManifestSegment segment : segments) {
                if (!ids.add(segment.id()) || segment.firstFrame() < previousEnd
                        || segment.firstFrame() < firstFrame || segment.exclusiveEnd() > exclusiveEnd) {
                    throw new IllegalArgumentException("manifest segments must be unique, monotonic, and in range");
                }
                previousEnd = segment.exclusiveEnd();
            }
        }
    }

    /** One retained manifest segment; gaps are deliberately represented by absent frame segments. */
    public record ManifestSegment(String id, int firstFrame, int exclusiveEnd) {
        public ManifestSegment {
            requireText(id, "manifest segment ID");
            if (firstFrame < 0 || exclusiveEnd <= firstFrame) {
                throw new IllegalArgumentException("manifest segment must be a non-empty half-open range");
            }
        }
    }

    public enum ProducerKind { REFERENCE, OPENGGF }

    /** Concrete runtime artifacts whose hashes prove which producer executed the capture. */
    public enum RuntimeArtifact {
        BIZHAWK_EXECUTABLE,
        BIZHAWK_CORE_DLL,
        BIZHAWK_COMMON_DLL,
        WATERBOX_HOST,
        GPGX_CORE,
        GPGX_CORE_UNCOMPRESSED,
        GPGX_OBSERVER_PATCH,
        GPGX_OBSERVER_SOURCE_BUNDLE,
        GPGX_OBSERVER_TOOLCHAIN,
        GPGX_OBSERVER_BUILD_RECIPE,
        GPGX_OBSERVER_IDENTITY,
        GPGX_OBSERVER_ADAPTER_SOURCE,
        GPGX_HOST_BRIDGE_SOURCE,
        BIZHAWK_BIZINVOKE_DLL,
        BIZHAWK_BASE_COMMON_DLL,
        TASK8_HARNESS_EXECUTABLE,
        TASK8_COLLECTOR_SOURCE,
        TASK8_HOST_SOURCE,
        GPGX_OBSERVER_CAPABILITY,
        REFERENCE_INSTALLATION_TREE,
        BIZHAWK_OBSERVER_MANAGED_PATCH,
        BIZHAWK_OBSERVER_CORES_DLL,
        OPENGGF_PRODUCER
    }

    /** Managed route selected by the producer distribution; callback capture has no managed adapter. */
    public enum ManagedObserverAdapter { CALLBACK_ONLY, REFLECTION, FIRST_CLASS }

    /** Structured observer identity; no free-form observer label may replace these runtime bounds. */
    public sealed interface ObserverRuntimeIdentity
            permits CallbackObserverIdentity, BufferedNativeObserverIdentity {
    }

    public record CallbackObserverIdentity(String id) implements ObserverRuntimeIdentity {
        public CallbackObserverIdentity {
            requireText(id, "callback observer identity");
            if (id.contains("/") || id.contains("\\")) {
                throw new IllegalArgumentException("callback observer identity must be a logical ID, not a path");
            }
        }
    }

    public record BufferedNativeObserverIdentity(
            String abiName,
            int abiVersion,
            int eventSize,
            int configSize,
            int kindSize,
            int hookSize,
            int rangeSize,
            int capacity,
            String installationId,
            String coreId,
            String coreBuildId,
            String watchMaskSha256,
            String serviceManifestSha256,
            boolean enabled,
            int maximumFrameOccupancy,
            long overflowCount) implements ObserverRuntimeIdentity {
        public BufferedNativeObserverIdentity {
            requireText(abiName, "native observer ABI name");
            if (abiVersion <= 0 || eventSize != 32 || configSize != 64 || kindSize != 16
                    || hookSize != 32 || rangeSize != 16 || capacity <= 0) {
                throw new IllegalArgumentException("native observer ABI bounds must be positive");
            }
            if (!"bizhawk-2.11-gpgx-audio-observer-v3".equals(installationId)
                    || !"gpgx-audio-observer-v3".equals(coreId)) {
                throw new IllegalArgumentException("native observer logical installation/core IDs are not pinned");
            }
            lowercaseHex(coreBuildId, Pattern.compile("[0-9a-f]{16}"), "native observer core BuildID");
            lowercaseHex(watchMaskSha256, SHA256, "native observer watch-mask SHA-256");
            lowercaseHex(serviceManifestSha256, SHA256, "native observer service-manifest SHA-256");
            if (!enabled || maximumFrameOccupancy < 1 || maximumFrameOccupancy > capacity
                    || overflowCount != 0) {
                throw new IllegalArgumentException("native observer must be enabled, in capacity, and overflow-free");
            }
        }
    }

    /**
     * Explicit producer runtime identity; observer labels are intentionally not used as a
     * substitute for executable, core, or artifact identity.
     */
    public record ProducerRuntimeIdentity(String producerName, String producerVersion,
            String emulatorName, String emulatorVersion, String coreName, String coreVersion,
            ManagedObserverAdapter observerAdapter, Map<RuntimeArtifact, String> artifactSha256) {
        public ProducerRuntimeIdentity(String producerName, String producerVersion,
                String emulatorName, String emulatorVersion, String coreName, String coreVersion,
                Map<RuntimeArtifact, String> artifactSha256) {
            this(producerName, producerVersion, emulatorName, emulatorVersion, coreName, coreVersion,
                    ManagedObserverAdapter.CALLBACK_ONLY, artifactSha256);
        }

        public ProducerRuntimeIdentity {
            requireText(producerName, "producer name");
            requireText(producerVersion, "producer version");
            requireText(emulatorName, "emulator name");
            requireText(emulatorVersion, "emulator version");
            requireText(coreName, "core name");
            requireText(coreVersion, "core version");
            Objects.requireNonNull(observerAdapter, "managed observer adapter");
            Objects.requireNonNull(artifactSha256, "runtime artifact SHA-256 values");
            EnumMap<RuntimeArtifact, String> hashes = new EnumMap<>(RuntimeArtifact.class);
            for (Map.Entry<RuntimeArtifact, String> entry : artifactSha256.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "runtime artifact");
                lowercaseHex(entry.getValue(), SHA256, "runtime artifact SHA-256");
                if (entry.getValue().chars().allMatch(value -> value == '0')) {
                    throw new IllegalArgumentException("runtime artifact SHA-256 must not be the zero placeholder");
                }
                hashes.put(entry.getKey(), entry.getValue());
            }
            artifactSha256 = Collections.unmodifiableMap(hashes);
            boolean hasManagedPatch = hashes.containsKey(RuntimeArtifact.BIZHAWK_OBSERVER_MANAGED_PATCH);
            boolean hasManagedCore = hashes.containsKey(RuntimeArtifact.BIZHAWK_OBSERVER_CORES_DLL);
            if (hasManagedPatch != hasManagedCore) {
                throw new IllegalArgumentException("managed observer patch and core DLL hashes must be paired");
            }
            if (observerAdapter == ManagedObserverAdapter.FIRST_CLASS && !hasManagedPatch) {
                throw new IllegalArgumentException("first-class managed adapter requires its paired patch and DLL");
            }
            if (observerAdapter != ManagedObserverAdapter.FIRST_CLASS && hasManagedPatch) {
                throw new IllegalArgumentException("callback/reflection adapters forbid patched managed artifacts");
            }
        }

        /** Validates the required executable/core artifact set for the selected producer. */
        public void validateFor(ProducerKind producerKind) {
            Set<RuntimeArtifact> required = switch (Objects.requireNonNull(producerKind, "producer kind")) {
                case REFERENCE -> Set.of(RuntimeArtifact.BIZHAWK_EXECUTABLE, RuntimeArtifact.BIZHAWK_CORE_DLL,
                        RuntimeArtifact.GPGX_CORE);
                case OPENGGF -> Set.of(RuntimeArtifact.OPENGGF_PRODUCER);
            };
            if (!artifactSha256.keySet().containsAll(required)) {
                throw new IllegalArgumentException("producer runtime identity is missing required artifacts");
            }
        }

        /** Validates adapter/artifact shape against the typed observer selected for this capture. */
        public void validateFor(ProducerKind producerKind, ObserverRuntimeIdentity observerIdentity) {
            validateFor(producerKind);
            Objects.requireNonNull(observerIdentity, "observer runtime identity");
            if (observerIdentity instanceof CallbackObserverIdentity) {
                if (observerAdapter != ManagedObserverAdapter.CALLBACK_ONLY) {
                    throw new IllegalArgumentException("callback observer requires callback-only adapter identity");
                }
                return;
            }
            if (producerKind != ProducerKind.REFERENCE || observerAdapter == ManagedObserverAdapter.CALLBACK_ONLY) {
                throw new IllegalArgumentException("buffered native observation requires a reference managed adapter");
            }
            Set<RuntimeArtifact> nativeRequired = Set.of(
                    RuntimeArtifact.BIZHAWK_COMMON_DLL,
                    RuntimeArtifact.WATERBOX_HOST,
                    RuntimeArtifact.GPGX_CORE_UNCOMPRESSED,
                    RuntimeArtifact.GPGX_OBSERVER_PATCH,
                    RuntimeArtifact.GPGX_OBSERVER_SOURCE_BUNDLE,
                    RuntimeArtifact.GPGX_OBSERVER_TOOLCHAIN,
                    RuntimeArtifact.GPGX_OBSERVER_BUILD_RECIPE);
            if (!artifactSha256.keySet().containsAll(nativeRequired)) {
                throw new IllegalArgumentException("buffered native identity is missing observer artifacts");
            }
            if (observerAdapter == ManagedObserverAdapter.REFLECTION) {
                Set<RuntimeArtifact> reflectionRequired = Set.of(
                        RuntimeArtifact.GPGX_OBSERVER_IDENTITY,
                        RuntimeArtifact.GPGX_OBSERVER_ADAPTER_SOURCE,
                        RuntimeArtifact.GPGX_HOST_BRIDGE_SOURCE,
                        RuntimeArtifact.BIZHAWK_BIZINVOKE_DLL,
                        RuntimeArtifact.BIZHAWK_BASE_COMMON_DLL,
                        RuntimeArtifact.TASK8_HARNESS_EXECUTABLE,
                        RuntimeArtifact.TASK8_COLLECTOR_SOURCE,
                        RuntimeArtifact.TASK8_HOST_SOURCE,
                        RuntimeArtifact.GPGX_OBSERVER_CAPABILITY,
                        RuntimeArtifact.REFERENCE_INSTALLATION_TREE);
                if (!artifactSha256.keySet().containsAll(reflectionRequired)) {
                    throw new IllegalArgumentException(
                            "reflection observer identity is missing proxy or managed dependency artifacts");
                }
            }
        }
    }

    /** Explicitly distinguishes a pinned producer from a task not yet installed. */
    public sealed interface ProducerBinding permits PinnedProducerBinding, UnavailableProducerBinding { }
    public record PinnedProducerBinding(ProducerRuntimeIdentity runtimeIdentity) implements ProducerBinding {
        public PinnedProducerBinding { Objects.requireNonNull(runtimeIdentity, "pinned producer runtime identity"); }
    }
    public record UnavailableProducerBinding(String reason) implements ProducerBinding {
        public UnavailableProducerBinding { requireText(reason, "unavailable producer reason"); }
    }

    /** Pinning proof for the observer profile and every callback domain it used. */
    public record ObserverProof(String observerProfile, String callbackSource,
            List<CallbackProof> callbacks) {
        public ObserverProof {
            requireText(observerProfile, "observer profile");
            requireText(callbackSource, "callback source");
            callbacks = List.copyOf(Objects.requireNonNull(callbacks, "callback proofs"));
            if (callbacks.isEmpty()) {
                throw new IllegalArgumentException("callback proofs must not be empty");
            }
        }
    }

    public record CallbackProof(String callback, long observations) {
        public CallbackProof {
            requireText(callback, "callback proof name");
            if (observations <= 0) {
                throw new IllegalArgumentException("callback proof observations must be positive");
            }
        }
    }

    /** The fixed deterministic chunking and compression contract used by the capture store. */
    public record ChunkPolicy(int frameRows, String compression, int gzipTimestamp) {
        public ChunkPolicy {
            if (frameRows != CHUNK_FRAME_ROWS || !"gzip".equals(compression) || gzipTimestamp != 0) {
                throw new IllegalArgumentException("chunk policy must use 4,096-row deterministic gzip chunks");
            }
        }
    }

    public record Metadata(String schema, String profileId, CompleteRunFixture fixture,
            ProducerKind producerKind, ProducerRuntimeIdentity producerRuntimeIdentity,
            ObserverRuntimeIdentity observerRuntimeIdentity, ObserverProof observerProof, ChunkPolicy chunkPolicy,
            List<HardwareRole> hardwareRoles, StateInventory stateInventory) {
        public Metadata {
            requireText(schema, "schema");
            requireText(profileId, "profileId");
            if (!SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("unknown complete-run audio schema: " + schema);
            }
            Objects.requireNonNull(fixture, "fixture");
            Objects.requireNonNull(producerKind, "producer kind");
            Objects.requireNonNull(producerRuntimeIdentity, "producer runtime identity");
            Objects.requireNonNull(observerRuntimeIdentity, "observer runtime identity");
            producerRuntimeIdentity.validateFor(producerKind, observerRuntimeIdentity);
            Objects.requireNonNull(observerProof, "observer proof");
            Objects.requireNonNull(chunkPolicy, "chunk policy");
            hardwareRoles = canonicalRoles(hardwareRoles, "metadata hardware roles");
            Objects.requireNonNull(stateInventory, "metadata state inventory");
        }

        /** Binds the caller-selected fixture and semantic inventories to their registered profile. */
        public void validateFixtureProfile(CompleteRunAudioProfile profile) {
            Objects.requireNonNull(profile, "profile");
            if (!profileId.equals(profile.id()) || !fixture.equals(profile.fixture())
                    || !hardwareRoles.equals(canonicalRoles(profile.hardwareRoles(), "profile hardware roles"))
                    || !stateInventory.equals(profile.stateInventory())) {
                throw new IllegalArgumentException("metadata fixture does not match the selected profile");
            }
        }

        /** Binds measured producer and observer identities to the registered runtime trust root. */
        public void validateRuntimeProfile(CompleteRunAudioProfile profile) {
            Objects.requireNonNull(profile, "profile");
            ProducerBinding binding = Objects.requireNonNull(profile.producerBindings().get(producerKind),
                    "profile producer binding");
            if (binding instanceof UnavailableProducerBinding unavailable) {
                throw new IllegalArgumentException("profile producer is unavailable: " + unavailable.reason());
            }
            Map<ProducerKind, ProducerRuntimeIdentity> allowedRuntimeIdentities =
                    Objects.requireNonNull(profile.producerRuntimeIdentities(), "profile runtime identities");
            if (!((PinnedProducerBinding) binding).runtimeIdentity()
                    .equals(allowedRuntimeIdentities.get(producerKind))) {
                throw new IllegalArgumentException("pinned producer binding and runtime identity disagree");
            }
            Map<ProducerKind, ObserverProof> allowedObserverProofs =
                    Objects.requireNonNull(profile.observerProofs(), "profile observer proofs");
            Map<ProducerKind, ObserverRuntimeIdentity> allowedObserverRuntimeIdentities =
                    Objects.requireNonNull(profile.observerRuntimeIdentities(), "profile observer runtime identities");
            ProducerRuntimeIdentity allowedRuntime = Objects.requireNonNull(
                    allowedRuntimeIdentities.get(producerKind), "profile runtime identity");
            ObserverRuntimeIdentity allowedObserver = Objects.requireNonNull(
                    allowedObserverRuntimeIdentities.get(producerKind), "profile observer runtime identity");
            ObserverProof allowedProof = Objects.requireNonNull(
                    allowedObserverProofs.get(producerKind), "profile observer proof");
            allowedRuntime.validateFor(producerKind, allowedObserver);
            if (!producerRuntimeIdentity.equals(allowedRuntime)
                    || !observerRuntimeIdentity.equals(allowedObserver)
                    || !observerProof.equals(allowedProof)) {
                throw new IllegalArgumentException("metadata runtime does not match the selected profile");
            }
        }

        /** Full in-process validation retained for callers that do not classify failure causes. */
        public void validateProfile(CompleteRunAudioProfile profile) {
            validateRuntimeProfile(profile);
            validateFixtureProfile(profile);
        }

        /** Verifies the terminal's interval-derived frame count and declared exclusive end. */
        public void validateTerminal(Terminal terminal) {
            Objects.requireNonNull(terminal, "terminal");
            if (terminal.exclusiveEnd() != fixture.exclusiveEnd()
                    || terminal.frameCount() != (long) fixture.exclusiveEnd() - fixture.firstFrame()) {
                throw new IllegalArgumentException("terminal does not match the metadata comparison interval");
            }
        }

        /** Called by streaming storage after independently counting the emitted record stream. */
        public void validateTerminal(Terminal terminal, CaptureCounts observedCounts) {
            validateTerminal(terminal);
            terminal.validateObservedCounts(observedCounts);
        }
    }

    public record Baseline(int absoluteFrame, NormalizedState state, List<RoleOwner> roleOwners,
            BoundaryFrontier frontier)
            implements Record {
        public Baseline(int absoluteFrame, NormalizedState state, List<RoleOwner> roleOwners) {
            this(absoluteFrame, state, roleOwners, BoundaryFrontier.empty());
        }

        public Baseline {
            if (absoluteFrame < 0) {
                throw new IllegalArgumentException("baseline frame must be non-negative");
            }
            Objects.requireNonNull(state, "state");
            roleOwners = canonicalRoleOwners(roleOwners, "baseline role owners");
            Objects.requireNonNull(frontier, "baseline frontier");
            for (int index = 0; index < frontier.activeStack().size(); index++) {
                CutoffService service = frontier.activeStack().get(index);
                if (service.state() != FrontierServiceState.CARRIED_IN_OPEN
                        || service.beginFrame() != absoluteFrame || service.beginOrdinal() != index) {
                    throw new IllegalArgumentException(
                            "baseline active services must be carried-in at contiguous boundary coordinates");
                }
            }
            if (frontier.nativeDiagnostics() != null) {
                List<FrontierService> nativeActive = frontier.nativeDiagnostics().activeStack();
                List<FrontierService> nativePending = frontier.nativeDiagnostics().pendingDescendants();
                if (nativeActive.size() != frontier.activeStack().size()
                        || nativePending.size() != frontier.pendingDescendants().size()) {
                        throw new IllegalArgumentException("baseline native proof does not match carried-in services");
                }
                Map<Long, FrontierService> servicesByToken = new LinkedHashMap<>();
                java.util.stream.Stream.concat(nativeActive.stream(), nativePending.stream())
                        .forEach(service -> servicesByToken.put(service.token(), service));
                Map<Long, ChipEvent> chipsByCoordinate = CutoffFrontier.semanticChipsByCoordinate(
                        frontier.nativeDiagnostics().rawChipInventory());
                Map<Long, SemanticServiceCoordinates> semanticCoordinates = new LinkedHashMap<>(
                        semanticServiceCoordinates(nativeActive, nativePending));
                for (int index = 0; index < nativeActive.size(); index++) {
                    FrontierService nativeService = nativeActive.get(index);
                    CutoffService semantic = frontier.activeStack().get(index);
                    if (nativeService.beginFrame() > absoluteFrame) {
                        throw new IllegalArgumentException(
                                "baseline native carry-in begins after the comparison epoch");
                    }
                    SemanticServiceCoordinates nativeCoordinates = semanticCoordinates.get(
                            nativeService.token());
                    semanticCoordinates.put(nativeService.token(), new SemanticServiceCoordinates(
                            new CutoffCoordinate(semantic.beginFrame(), semantic.beginOrdinal()),
                            nativeCoordinates.end(), nativeCoordinates.transitions()));
                }
                for (int index = 0; index < nativePending.size(); index++) {
                    FrontierService nativeService = nativePending.get(index);
                    CutoffService semantic = frontier.pendingDescendants().get(index);
                    if (nativeService.endFrame() == null || nativeService.endFrame() > absoluteFrame) {
                        throw new IllegalArgumentException(
                                "baseline native pending service ends after the comparison epoch");
                    }
                    semanticCoordinates.put(nativeService.token(), new SemanticServiceCoordinates(
                            new CutoffCoordinate(semantic.beginFrame(), semantic.beginOrdinal()),
                            new CutoffCoordinate(semantic.endFrame(), semantic.endOrdinal()),
                            semanticCoordinates.get(nativeService.token()).transitions()));
                }
                List<CutoffService> projectedActive = nativeActive.stream().map(service -> {
                    CutoffService projected = CutoffService.fromNative(service, servicesByToken,
                            chipsByCoordinate, semanticCoordinates);
                    return new CutoffService(projected.parentFrame(), projected.parentOrdinal(),
                            projected.depth(), projected.kind(), FrontierServiceState.CARRIED_IN_OPEN,
                            projected.beginFrame(), projected.beginOrdinal(), null, null,
                            projected.chipEvents());
                }).toList();
                List<CutoffService> projectedPending = nativePending.stream()
                        .map(service -> CutoffService.fromNative(service, servicesByToken,
                                chipsByCoordinate, semanticCoordinates)).toList();
                if (!frontier.activeStack().equals(projectedActive)
                        || !frontier.pendingDescendants().equals(projectedPending)
                        || !frontier.rawChipEvents().equals(List.copyOf(chipsByCoordinate.values()))) {
                    throw new IllegalArgumentException(
                            "baseline native proof does not project canonically");
                }
            }
        }
    }

    public record NativeResetDiagnostic(long serviceToken, boolean power) {
        public NativeResetDiagnostic {
            if (serviceToken <= 0 || serviceToken > 0xffff) {
                throw new IllegalArgumentException("native reset service token is invalid");
            }
        }
    }

    /** One lossless native reservation for a service begin deferred until its blocker ends. */
    public record NativeDeferredServiceBegin(long blockerToken, long blockerParentToken,
            int blockerKind, int blockerDepth, int targetKind, int hookToken, int sourceCpu, int pc,
            long firstCoordinate, long latestCoordinate, long firstOrdinal, long latestOrdinal,
            int observationCount, boolean consumed, long releasedToken, long releaseCoordinate) {
        public NativeDeferredServiceBegin {
            if (blockerToken <= 0 || blockerToken > 0xffff
                    || blockerParentToken < 0 || blockerParentToken > 0xffff
                    || blockerParentToken == blockerToken
                    || blockerKind <= 0 || blockerKind > 0xff
                    || blockerDepth < 0 || blockerDepth > 7
                    || (blockerParentToken == 0) != (blockerDepth == 0)
                    || targetKind <= 0 || targetKind > 0xff
                    || hookToken <= 0 || hookToken > 0xffff
                    || sourceCpu != 2 || pc < 0 || pc > 0xffffff || (pc & 1) != 0
                    || firstCoordinate < 0 || latestCoordinate < firstCoordinate
                    || firstOrdinal < 0 || firstOrdinal >= MAX_NATIVE_FRAME_EVENTS
                    || latestOrdinal < 0 || latestOrdinal >= MAX_NATIVE_FRAME_EVENTS
                    || observationCount <= 0
                    || (observationCount == 1)
                            != (firstCoordinate == latestCoordinate && firstOrdinal == latestOrdinal)
                    || consumed != (releasedToken > 0)
                    || releasedToken < 0 || releasedToken > 0xffff
                    || consumed && (releasedToken == blockerToken
                            || releaseCoordinate <= latestCoordinate)
                    || !consumed && releaseCoordinate != 0) {
                throw new IllegalArgumentException("native deferred service-begin evidence is invalid");
            }
        }
    }

    /** One native boundary/marker retained by a managed-callback correlation. */
    public record NativeManagedEvent(long coordinate, long ordinal, String sourceCpu, int pc,
            int eventKind, int value, long serviceToken, long parentToken, int serviceKind, int depth,
            int hookToken, int flags, boolean terminal) {
        public NativeManagedEvent(long coordinate, long ordinal, String sourceCpu, int pc,
                int eventKind, int value, long serviceToken, long parentToken, int serviceKind, int depth,
                int hookToken, boolean terminal) {
            this(coordinate, ordinal, sourceCpu, pc, eventKind, value, serviceToken, parentToken,
                    serviceKind, depth, hookToken, 0, terminal);
        }

        public NativeManagedEvent {
            nonNegative(coordinate, "native managed-event coordinate");
            nonNegative(ordinal, "native managed-event ordinal");
            boolean marker = eventKind == 10;
            if (ordinal >= MAX_NATIVE_FRAME_EVENTS || !"M68K".equals(sourceCpu)
                    || pc < 0 || pc > 0xffffff || (pc & 1) != 0
                    || eventKind != 1 && eventKind != 2 && !marker
                    || (marker ? value < 0 || value > 4 : value != 0)
                    || serviceToken < 0 || serviceToken > 0xffff
                    || parentToken < 0 || parentToken > 0xffff || parentToken == serviceToken
                    || serviceKind < 0 || serviceKind > 0xff || depth < 0 || depth > 7
                    || hookToken <= 0 || hookToken > 0xffff || flags != 0
                    || serviceToken == 0 && (parentToken != 0 || serviceKind != 0 || depth != 0)
                    || serviceToken != 0 && serviceKind == 0
                    || (eventKind == 1 || eventKind == 2 || marker && value < 3
                            || marker && value == 4) && serviceToken == 0) {
                throw new IllegalArgumentException("native managed-event identity is invalid");
            }
        }
    }

    /**
     * Exact native event sequence proving one managed callback occurrence. This raw observer
     * provenance participates in the storage root but is excluded from semantic parity.
     */
    public record NativeManagedCorrelation(long managedCorrelationOrdinal,
            List<NativeManagedEvent> events) {
        public NativeManagedCorrelation {
            nonNegative(managedCorrelationOrdinal, "managed correlation ordinal");
            if (managedCorrelationOrdinal >= MAX_NATIVE_FRAME_EVENTS) {
                throw new IllegalArgumentException("managed correlation ordinal exceeds native capacity");
            }
            events = List.copyOf(Objects.requireNonNull(events, "native managed correlation events"));
            if (events.isEmpty() || events.size() > 2) {
                throw new IllegalArgumentException("native managed correlation event count is invalid");
            }
            strictlyIncreasing(events.stream().map(NativeManagedEvent::coordinate).toList(),
                    "native managed-event coordinates");
            strictlyIncreasing(events.stream().map(NativeManagedEvent::ordinal).toList(),
                    "native managed-event ordinals");
            long terminals = events.stream().filter(NativeManagedEvent::terminal).count();
            NativeManagedEvent first = events.getFirst();
            NativeManagedEvent last = events.getLast();
            if (terminals != 1 || !last.terminal()
                    || (first.eventKind() == 10 && first.value() == 1
                            ? events.size() != 2 || first.terminal() || last.eventKind() != 2
                                    || first.serviceToken() != last.serviceToken()
                                    || first.parentToken() != last.parentToken()
                                    || first.serviceKind() != last.serviceKind()
                                    || first.depth() != last.depth() || first.hookToken() != last.hookToken()
                                    || first.pc() != last.pc()
                            : events.size() != 1)) {
                throw new IllegalArgumentException("native managed correlation is incomplete or ambiguous");
            }
        }
    }

    public record FrameNativeDiagnostics(List<FrontierService> services,
            List<FrontierOwnedChip> rawChipInventory,
            List<FrontierOwnedSnapshot> rawSnapshotInventory,
            List<NativeResetDiagnostic> resets,
            List<NativeManagedCorrelation> managedCorrelations,
            List<NativeDeferredServiceBegin> deferredServiceBegins,
            List<FrontierOwnedAncestryTransition> rawAncestryTransitionInventory) {
        public FrameNativeDiagnostics(List<FrontierService> services,
                List<FrontierOwnedChip> rawChipInventory,
                List<FrontierOwnedSnapshot> rawSnapshotInventory) {
            this(services, rawChipInventory, rawSnapshotInventory,
                    List.of(), List.of(), List.of(), List.of());
        }

        public FrameNativeDiagnostics(List<FrontierService> services,
                List<FrontierOwnedChip> rawChipInventory,
                List<FrontierOwnedSnapshot> rawSnapshotInventory,
                List<NativeResetDiagnostic> resets) {
            this(services, rawChipInventory, rawSnapshotInventory,
                    resets, List.of(), List.of(), List.of());
        }

        public FrameNativeDiagnostics(List<FrontierService> services,
                List<FrontierOwnedChip> rawChipInventory,
                List<FrontierOwnedSnapshot> rawSnapshotInventory,
                List<NativeResetDiagnostic> resets,
                List<NativeManagedCorrelation> managedCorrelations) {
            this(services, rawChipInventory, rawSnapshotInventory, resets,
                    managedCorrelations, List.of(), List.of());
        }

        public FrameNativeDiagnostics(List<FrontierService> services,
                List<FrontierOwnedChip> rawChipInventory,
                List<FrontierOwnedSnapshot> rawSnapshotInventory,
                List<NativeResetDiagnostic> resets,
                List<NativeManagedCorrelation> managedCorrelations,
                List<FrontierOwnedAncestryTransition> rawAncestryTransitionInventory) {
            this(services, rawChipInventory, rawSnapshotInventory, resets,
                    managedCorrelations, List.of(), rawAncestryTransitionInventory);
        }

        public FrameNativeDiagnostics {
            services = List.copyOf(Objects.requireNonNull(services, "native frame services"));
            rawChipInventory = List.copyOf(Objects.requireNonNull(rawChipInventory,
                    "native frame chip inventory"));
            rawSnapshotInventory = List.copyOf(Objects.requireNonNull(rawSnapshotInventory,
                    "native frame snapshot inventory"));
            resets = List.copyOf(Objects.requireNonNull(resets, "native frame resets"));
            managedCorrelations = canonicalManagedCorrelations(managedCorrelations);
            deferredServiceBegins = List.copyOf(Objects.requireNonNull(
                    deferredServiceBegins, "native deferred service begins"));
            rawAncestryTransitionInventory = List.copyOf(Objects.requireNonNull(
                    rawAncestryTransitionInventory, "native frame ancestry-transition inventory"));
            if (resets.size() > 8) throw new IllegalArgumentException("native reset bound exceeded");
            if (deferredServiceBegins.size() > 1) {
                throw new IllegalArgumentException("native deferred service-begin bound exceeded");
            }
            if (managedCorrelations.size() > MAX_NATIVE_FRAME_EVENTS
                    || managedCorrelations.stream().mapToLong(value -> value.events().size()).sum()
                            > MAX_NATIVE_FRAME_EVENTS) {
                throw new IllegalArgumentException("native managed-correlation bound exceeded");
            }
            if (services.size() > MAX_CUTOFF_SERVICES
                    || rawAncestryTransitionInventory.size() > MAX_CUTOFF_SERVICES * 7) {
                throw new IllegalArgumentException("native frame service bound exceeded");
            }
            long chipCount = services.stream().mapToLong(service -> service.chipEvents().size()).sum();
            long snapshotBytes = services.stream().flatMap(service -> service.snapshots().stream())
                    .mapToLong(snapshot -> snapshot.bytes().size()).sum();
            if (chipCount > MAX_CUTOFF_CHIP_EVENTS || chipCount != rawChipInventory.size()
                    || snapshotBytes > MAX_CUTOFF_SNAPSHOT_BYTES
                    || services.stream().mapToLong(service -> service.snapshots().size()).sum()
                            != rawSnapshotInventory.size()) {
                throw new IllegalArgumentException("native frame inventories exceed their aggregate bounds");
            }
            Map<Long, FrontierService> byToken = new LinkedHashMap<>();
            int priorFrame = -1;
            long priorOrdinal = -1;
            for (FrontierService service : services) {
                if (service.state() == FrontierServiceState.OPEN
                        || service.beginFrame() < priorFrame
                        || (service.beginFrame() == priorFrame && service.beginOrdinal() <= priorOrdinal)
                        || byToken.putIfAbsent(service.token(), service) != null) {
                    throw new IllegalArgumentException("native frame services are not unique begin order");
                }
                if (service.parentToken() != 0) {
                    FrontierService parent = byToken.get(service.parentToken());
                    boolean releasedParent = parent == null
                            && !service.ancestryTransitions().isEmpty()
                            && service.ancestryTransitions().getFirst().previousParentToken()
                                    == service.parentToken()
                            && service.ancestryTransitions().getFirst().previousDepth() == service.depth();
                    if (!releasedParent && (parent == null || service.depth() != parent.depth() + 1
                                    || !nativeBeginsBefore(parent, service)
                                    || parent.state() != FrontierServiceState.OPEN
                                            && !nativeEndsBefore(service, parent)
                                            && !nativePromotionClosesParent(service, parent))) {
                        throw new IllegalArgumentException("native frame service parent is not earlier");
                    }
                } else if (service.depth() != 0) {
                    throw new IllegalArgumentException("native frame root depth is invalid");
                }
                priorFrame = service.beginFrame();
                priorOrdinal = service.beginOrdinal();
            }
            for (FrontierService service : services) {
                for (NativeAncestryTransition transition : service.ancestryTransitions()) {
                    FrontierService parent = byToken.get(transition.previousParentToken());
                    if (parent != null && !nativePromotionClosesParent(service, parent, transition)) {
                        throw new IllegalArgumentException(
                                "native ancestry transition has no exact completed direct parent");
                    }
                }
            }
            List<FrontierOwnedChip> expectedChips = services.stream()
                    .flatMap(service -> service.chipEvents().stream()
                            .map(event -> new FrontierOwnedChip(service.token(), event)))
                    .sorted(java.util.Comparator.comparingLong(owned -> owned.event().coordinate())).toList();
            List<FrontierOwnedSnapshot> expectedSnapshots = new java.util.ArrayList<>();
            for (FrontierService service : services) {
                for (int index = 0; index < service.snapshots().size(); index++) {
                    expectedSnapshots.add(new FrontierOwnedSnapshot(service.token(), index,
                            service.snapshots().get(index)));
                }
            }
            if (!rawChipInventory.equals(expectedChips) || !rawSnapshotInventory.equals(expectedSnapshots)) {
                throw new IllegalArgumentException("native frame inventories are not exact ownership partitions");
            }
            strictlyIncreasing(rawChipInventory.stream().map(owned -> owned.event().coordinate()).toList(),
                    "native frame raw chip coordinates");
            List<Long> resetTokens = new java.util.ArrayList<>();
            List<FrontierService> resetServices = new java.util.ArrayList<>();
            Set<Long> uniqueResetTokens = new LinkedHashSet<>();
            for (NativeResetDiagnostic reset : resets) {
                FrontierService service = byToken.get(reset.serviceToken());
                if (!uniqueResetTokens.add(reset.serviceToken()) || service == null
                        || !"RESET".equals(service.beginSourceCpu()) || service.beginPc() != 0
                        || service.beginHookToken() != 0 || service.state() != FrontierServiceState.COMPLETED) {
                    throw new IllegalArgumentException("native reset diagnostic has no exact reset-root service");
                }
                resetTokens.add(reset.serviceToken());
                resetServices.add(service);
            }
            List<Long> resetRoots = services.stream().filter(service -> "RESET".equals(service.beginSourceCpu()))
                    .map(FrontierService::token).toList();
            if (!resetTokens.equals(resetRoots)) {
                throw new IllegalArgumentException("native reset-root services require exact typed diagnostics");
            }
            long priorCoordinate = -1;
            long priorNativeOrdinal = -1;
            long expectedManagedOrdinal = 0;
            Map<Long, Long> nativeOrdinalByCoordinate = new TreeMap<>();
            for (FrontierOwnedChip owned : rawChipInventory) {
                nativeOrdinalByCoordinate.put(owned.event().coordinate(), owned.event().ordinal());
            }
            for (FrontierOwnedAncestryTransition owned : rawAncestryTransitionInventory) {
                NativeAncestryTransition transition = owned.event();
                if (nativeOrdinalByCoordinate.putIfAbsent(
                        transition.coordinate(), transition.ordinal()) != null) {
                    throw new IllegalArgumentException(
                            "native ancestry transition inventory overlaps another raw coordinate");
                }
            }
            for (NativeManagedCorrelation correlation : managedCorrelations) {
                if (correlation.managedCorrelationOrdinal() != expectedManagedOrdinal++) {
                    throw new IllegalArgumentException(
                            "native managed-correlation ordinals are not contiguous");
                }
                for (NativeManagedEvent event : correlation.events()) {
                    if (event.coordinate() <= priorCoordinate || event.ordinal() <= priorNativeOrdinal) {
                        throw new IllegalArgumentException(
                                "native managed correlations are not in exact raw order");
                    }
                    if (nativeOrdinalByCoordinate.putIfAbsent(event.coordinate(), event.ordinal()) != null) {
                        throw new IllegalArgumentException(
                                "native managed and chip coordinates overlap");
                    }
                    FrontierService service = byToken.get(event.serviceToken());
                    if (service != null && event.eventKind() == 1
                            && (event.parentToken() != service.parentToken()
                                    || event.depth() != service.depth()
                                    || event.ordinal() != service.beginOrdinal()
                                    || event.pc() != service.beginPc()
                                    || event.hookToken() != service.beginHookToken()
                                    || !event.sourceCpu().equals(service.beginSourceCpu()))) {
                        throw new IllegalArgumentException(
                                "native managed begin does not match its retained service");
                    }
                    if (service != null && event.eventKind() == 2
                            && (service.state() != FrontierServiceState.COMPLETED
                                    || !Objects.equals(event.ordinal(), service.endOrdinal())
                                    || !Objects.equals(event.pc(), service.endPc())
                                    || !Objects.equals(event.hookToken(), service.endHookToken()))) {
                        throw new IllegalArgumentException(
                                "native managed completion does not match its retained service");
                    }
                    priorCoordinate = event.coordinate();
                    priorNativeOrdinal = event.ordinal();
                }
            }
            long combinedOrdinal = -1;
            for (long ordinal : nativeOrdinalByCoordinate.values()) {
                if (ordinal <= combinedOrdinal) {
                    throw new IllegalArgumentException(
                            "native managed and chip records disagree about raw order");
                }
                combinedOrdinal = ordinal;
            }
            validateNativeYmProjection(rawChipInventory, resetServices);
        }

        private static List<NativeManagedCorrelation> canonicalManagedCorrelations(
                List<NativeManagedCorrelation> records) {
            records = List.copyOf(Objects.requireNonNull(records,
                    "native frame managed correlations"));
            if (records.size() > MAX_NATIVE_FRAME_EVENTS) {
                throw new IllegalArgumentException("native managed-correlation record bound exceeded");
            }
            List<NativeManagedCorrelation> result = new ArrayList<>();
            long expected = 0;
            for (NativeManagedCorrelation record : records) {
                if (record.managedCorrelationOrdinal() == expected) {
                    result.add(record);
                    expected++;
                } else if (record.managedCorrelationOrdinal() != expected - 1
                        || !result.getLast().equals(record)) {
                    throw new IllegalArgumentException(
                            "duplicate managed callback records do not carry identical correlations");
                } else if (record.events().stream()
                        .anyMatch(event -> event.eventKind() == 10 && event.value() == 4)) {
                    throw new IllegalArgumentException(
                            "deferred service-begin marker correlation is duplicated");
                }
            }
            return List.copyOf(result);
        }
    }

    public record Frame(int absoluteFrame, String segment, boolean lag, List<Request> requests,
            List<DriverService> services, List<ChipEvent> rawChipEvents,
            FrameNativeDiagnostics nativeDiagnostics) implements Record {
        public Frame(int absoluteFrame, String segment, boolean lag, List<Request> requests,
                List<DriverService> services) {
            this(absoluteFrame, segment, lag, requests, services,
                    services.stream().flatMap(service -> service.chipEvents().stream())
                            .sorted(java.util.Comparator.comparingLong(ChipEvent::ordinal)).toList(), null);
        }

        public Frame {
            if (absoluteFrame < 0) {
                throw new IllegalArgumentException("frame must be non-negative");
            }
            if (segment != null && segment.isBlank()) {
                throw new IllegalArgumentException("segment must be null or non-blank");
            }
            requests = List.copyOf(Objects.requireNonNull(requests, "requests"));
            services = List.copyOf(Objects.requireNonNull(services, "services"));
            rawChipEvents = List.copyOf(Objects.requireNonNull(rawChipEvents, "raw chip events"));
            strictlyIncreasing(requests.stream().map(Request::ordinal).toList(), "request ordinals");
            strictlyIncreasing(services.stream().map(DriverService::ordinal).toList(), "service ordinals");
            strictlyIncreasing(rawChipEvents.stream().map(ChipEvent::ordinal).toList(), "raw chip ordinals");
            List<ChipEvent> owned = services.stream().flatMap(service -> service.chipEvents().stream())
                    .sorted(java.util.Comparator.comparingLong(ChipEvent::ordinal)).toList();
            if (!rawChipEvents.equals(owned)) {
                throw new IllegalArgumentException("raw chip stream is not an exact service ownership partition");
            }
            if (nativeDiagnostics != null) {
                for (FrontierOwnedAncestryTransition ownedTransition
                        : nativeDiagnostics.rawAncestryTransitionInventory()) {
                    if (ownedTransition.event().frame() != absoluteFrame) {
                        throw new IllegalArgumentException(
                                "native ancestry transition is outside its captured frame");
                    }
                }
                if (nativeDiagnostics.services().size() != services.size()) {
                    throw new IllegalArgumentException("native and canonical service counts differ");
                }
                Map<Long, FrontierService> nativeByToken = new LinkedHashMap<>();
                nativeDiagnostics.services().forEach(service -> nativeByToken.put(service.token(), service));
                Map<Long, SemanticServiceCoordinates> serviceCoordinates = semanticServiceCoordinates(
                        nativeDiagnostics.services(), List.of());
                Map<Long, ChipEvent> semanticChips = new LinkedHashMap<>();
                int semanticChipIndex = 0;
                for (FrontierOwnedChip rawOwnedEntry : nativeDiagnostics.rawChipInventory()) {
                    if (rawOwnedEntry.event().data()) {
                        semanticChips.put(rawOwnedEntry.event().coordinate(),
                                rawChipEvents.get(semanticChipIndex++));
                    }
                }
                for (int index = 0; index < services.size(); index++) {
                    DriverService semantic = services.get(index);
                    FrontierService raw = nativeDiagnostics.services().get(index);
                    FrontierServiceState expected = semantic.completion() == ServiceCompletion.COMPLETED
                            ? FrontierServiceState.COMPLETED : FrontierServiceState.RESET_CANCELLED;
                    if (!semantic.kind().equals(raw.kind()) || raw.state() != expected) {
                        throw new IllegalArgumentException("native and canonical service identities differ");
                    }
                    CutoffService projected = CutoffService.fromNative(raw, nativeByToken,
                            semanticChips, serviceCoordinates);
                    if (!semantic.ancestry().equals(projected.ancestry())) {
                        throw new IllegalArgumentException("native and canonical service ancestry differs");
                    }
                    if (!semantic.ancestry().transitions().isEmpty()
                            && (!Objects.equals(semantic.beginCoordinate(),
                                    new ServiceCoordinate(projected.beginFrame(), projected.beginOrdinal()))
                                    || !Objects.equals(semantic.endCoordinate(),
                                            new ServiceCoordinate(projected.endFrame(), projected.endOrdinal())))) {
                        throw new IllegalArgumentException(
                                "native and canonical service lifetime coordinates differ");
                    }
                    List<FrontierChipEvent> rawOwned = raw.chipEvents().stream()
                            .filter(FrontierChipEvent::data).toList();
                    if (rawOwned.size() != semantic.chipEvents().size()) {
                        throw new IllegalArgumentException("native service chip projection count differs");
                    }
                    for (int chip = 0; chip < rawOwned.size(); chip++) {
                        requireSameChipPayload(semantic.chipEvents().get(chip), rawOwned.get(chip));
                    }
                }
                List<FrontierChipEvent> rawOrdered = nativeDiagnostics.rawChipInventory().stream()
                        .map(FrontierOwnedChip::event).filter(FrontierChipEvent::data).toList();
                if (rawOrdered.size() != rawChipEvents.size()) {
                    throw new IllegalArgumentException("native global chip projection count differs");
                }
                for (int chip = 0; chip < rawOrdered.size(); chip++) {
                    requireSameChipPayload(rawChipEvents.get(chip), rawOrdered.get(chip));
                }
            }
        }

        private static void requireSameChipPayload(ChipEvent semantic, FrontierChipEvent raw) {
            boolean matches = semantic instanceof YmWrite ym && raw.eventKind() == 3
                    && raw.port() != null && raw.register() != null && ym.port() == raw.port()
                    && ym.register() == raw.register() && ym.value() == raw.value()
                    || semantic instanceof PsgWrite psg && raw.eventKind() == 4
                            && psg.value() == raw.value();
            if (!matches) throw new IllegalArgumentException("native and canonical chip projections differ");
        }
    }

    public enum ServiceCompletion { COMPLETED, RESET_CANCELLED }

    /** Producer-neutral service-boundary coordinate, independent of native tokens and PCs. */
    public record ServiceCoordinate(int frame, long ordinal) {
        public ServiceCoordinate {
            if (frame < 0) throw new IllegalArgumentException("service-coordinate frame is invalid");
            nonNegative(ordinal, "service-coordinate ordinal");
        }
    }

    /** One canonical effective-ancestry transition, included in semantic comparison. */
    public record ServiceAncestryTransition(ServiceCoordinate previousParent, int previousDepth,
            ServiceCoordinate currentParent, int currentDepth, int transitionFrame, long transitionOrdinal) {
        public ServiceAncestryTransition {
            if (previousDepth <= 0 || previousDepth > 7 || currentDepth != previousDepth - 1
                    || (previousParent == null) || (currentDepth == 0) != (currentParent == null)
                    || transitionFrame < 0 || transitionOrdinal < 0) {
                throw new IllegalArgumentException("semantic ancestry transition is invalid");
            }
        }

        ServiceCoordinate transitionCoordinate() {
            return new ServiceCoordinate(transitionFrame, transitionOrdinal);
        }
    }

    /** Immutable begin ancestry plus the effective ancestry after bounded promotions. */
    public record ServiceAncestry(ServiceCoordinate beginParent, int beginDepth,
            ServiceCoordinate currentParent, int currentDepth,
            List<ServiceAncestryTransition> transitions) {
        public ServiceAncestry {
            transitions = List.copyOf(Objects.requireNonNull(transitions, "service ancestry transitions"));
            if (beginDepth < 0 || beginDepth > 7 || currentDepth < 0 || currentDepth > 7
                    || (beginDepth == 0) != (beginParent == null)
                    || (currentDepth == 0) != (currentParent == null)
                    || transitions.size() > 7) {
                throw new IllegalArgumentException("service ancestry is invalid");
            }
            ServiceCoordinate parent = beginParent;
            int depth = beginDepth;
            ServiceCoordinate priorTransition = null;
            for (ServiceAncestryTransition transition : transitions) {
                if (!Objects.equals(parent, transition.previousParent()) || depth != transition.previousDepth()
                        || priorTransition != null && compareCoordinate(
                                priorTransition, transition.transitionCoordinate()) >= 0) {
                    throw new IllegalArgumentException("service ancestry transition chain is invalid");
                }
                parent = transition.currentParent();
                depth = transition.currentDepth();
                priorTransition = transition.transitionCoordinate();
            }
            if (!Objects.equals(parent, currentParent) || depth != currentDepth) {
                throw new IllegalArgumentException("service effective ancestry is inconsistent");
            }
        }

        public static ServiceAncestry root() {
            return new ServiceAncestry(null, 0, null, 0, List.of());
        }
    }

    public record DriverService(long ordinal, String kind, ServiceCompletion completion, List<Decision> decisions,
            NormalizedState state, List<ChipEvent> chipEvents, Long carriedBoundaryOrdinal,
            ServiceCoordinate beginCoordinate, ServiceCoordinate endCoordinate, ServiceAncestry ancestry) {
        public DriverService(long ordinal, String kind, ServiceCompletion completion, List<Decision> decisions,
                NormalizedState state, List<ChipEvent> chipEvents) {
            this(ordinal, kind, completion, decisions, state, chipEvents, null,
                    null, null, ServiceAncestry.root());
        }

        public DriverService(long ordinal, String kind, ServiceCompletion completion, List<Decision> decisions,
                NormalizedState state, List<ChipEvent> chipEvents, Long carriedBoundaryOrdinal) {
            this(ordinal, kind, completion, decisions, state, chipEvents, carriedBoundaryOrdinal,
                    null, null, ServiceAncestry.root());
        }

        public DriverService(long ordinal, String kind, ServiceCompletion completion, List<Decision> decisions,
                NormalizedState state, List<ChipEvent> chipEvents, Long carriedBoundaryOrdinal,
                ServiceAncestry ancestry) {
            this(ordinal, kind, completion, decisions, state, chipEvents, carriedBoundaryOrdinal,
                    null, null, ancestry);
        }

        public DriverService {
            nonNegative(ordinal, "service ordinal");
            requireText(kind, "service kind");
            Objects.requireNonNull(completion, "service completion");
            decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(ancestry, "service ancestry");
            chipEvents = List.copyOf(Objects.requireNonNull(chipEvents, "chipEvents"));
            if (carriedBoundaryOrdinal != null
                    && (carriedBoundaryOrdinal < 0 || carriedBoundaryOrdinal >= 8)) {
                throw new IllegalArgumentException("carried boundary ordinal is invalid");
            }
            if ((beginCoordinate == null) != (endCoordinate == null)
                    || beginCoordinate != null && compareCoordinate(beginCoordinate, endCoordinate) >= 0) {
                throw new IllegalArgumentException("service lifetime coordinates are invalid");
            }
            validateAncestryLifetime(ancestry, beginCoordinate, endCoordinate,
                    "completed service ancestry");
            strictlyIncreasing(decisions.stream().map(Decision::requestOrdinal).toList(),
                    "decision request ordinals");
            strictlyIncreasing(chipEvents.stream().map(ChipEvent::ordinal).toList(), "chip event ordinals");
        }
    }

    public record Lifecycle(long ordinal, int absoluteFrame, String kind, Map<String, Object> details,
            List<LifecycleOwnership> ownershipTransitions) implements Record {
        public Lifecycle {
            nonNegative(ordinal, "lifecycle ordinal");
            if (absoluteFrame < 0) {
                throw new IllegalArgumentException("lifecycle frame must be non-negative");
            }
            requireText(kind, "lifecycle kind");
            details = immutableMap(details, "lifecycle details");
            ownershipTransitions = canonicalLifecycleOwnership(ownershipTransitions,
                    "lifecycle ownership transitions");
        }
    }

    public enum FrontierServiceState { CARRIED_IN_OPEN, OPEN, COMPLETED, RESET_CANCELLED }

    /** One immutable range snapshot exclusively owned by a cutoff service. */
    public record FrontierSnapshot(int rangeId, String sourceCpu, int pc, List<Integer> bytes) {
        public FrontierSnapshot {
            if (rangeId <= 0 || rangeId > 0xffff) {
                throw new IllegalArgumentException("frontier snapshot range is invalid");
            }
            validateSourcePc(sourceCpu, pc, "frontier snapshot");
            bytes = List.copyOf(Objects.requireNonNull(bytes, "frontier snapshot bytes"));
            if (bytes.isEmpty()) throw new IllegalArgumentException("frontier snapshot must not be empty");
            for (Integer value : bytes) unsignedByte(Objects.requireNonNull(value, "snapshot byte"),
                    "frontier snapshot byte");
        }
    }

    /** Raw bus write plus its deterministic YM/PSG projection at the terminal cutoff. */
    public record FrontierChipEvent(long coordinate, long ordinal, String sourceCpu, int pc,
            int eventKind, int subject, int value, boolean data, Integer port, Integer register) {
        public FrontierChipEvent {
            nonNegative(coordinate, "frontier chip coordinate");
            nonNegative(ordinal, "frontier chip ordinal");
            if (ordinal >= MAX_NATIVE_FRAME_EVENTS) {
                throw new IllegalArgumentException("frontier chip ordinal exceeds native capacity");
            }
            validateSourcePc(sourceCpu, pc, "frontier chip");
            unsignedByte(value, "frontier chip value");
            if (eventKind == 3) {
                if (subject < 0 || subject > 3 || data != (subject == 1 || subject == 3)
                        || port == null || port != subject / 2 || register == null) {
                    throw new IllegalArgumentException("frontier YM projection is incomplete");
                }
                unsignedByte(register, "frontier YM register");
            } else if (eventKind == 4) {
                if (subject != 0 || !data || port != null || register != null) {
                    throw new IllegalArgumentException("frontier PSG projection must not carry YM fields");
                }
            } else {
                throw new IllegalArgumentException("unknown frontier chip kind");
            }
        }
    }

    /** Raw event-11 proof for one effective-ancestry promotion. */
    public record NativeAncestryTransition(long coordinate, int frame, long ordinal,
            long previousParentToken, int previousDepth, long currentParentToken, int currentDepth,
            int hookToken, String sourceCpu, int pc) {
        public NativeAncestryTransition {
            nonNegative(coordinate, "native ancestry-transition coordinate");
            nonNegative(ordinal, "native ancestry-transition ordinal");
            if (frame < 0 || ordinal >= MAX_NATIVE_FRAME_EVENTS || previousParentToken <= 0
                    || previousParentToken > 0xffff || previousDepth <= 0 || previousDepth > 7
                    || currentParentToken < 0 || currentParentToken > 0xffff
                    || currentDepth != previousDepth - 1
                    || (currentDepth == 0) != (currentParentToken == 0)
                    || hookToken <= 0 || hookToken > 0xffff) {
                throw new IllegalArgumentException("native ancestry transition is invalid");
            }
            validateSourcePc(sourceCpu, pc, "native ancestry transition");
        }
    }

    /** One active or withheld completed service; chip and snapshot ownership is exclusive. */
    public record FrontierService(long token, long parentToken, int depth, String kind,
            FrontierServiceState state, int beginFrame, long beginOrdinal, int beginPc,
            int beginHookToken, String beginSourceCpu, Integer endFrame, Long endOrdinal,
            Integer endPc, Integer endHookToken, List<FrontierSnapshot> snapshots,
            List<FrontierChipEvent> chipEvents, long currentParentToken, int currentDepth,
            List<NativeAncestryTransition> ancestryTransitions, ServiceAncestry semanticAncestry) {
        public FrontierService(long token, long parentToken, int depth, String kind,
                FrontierServiceState state, int beginFrame, long beginOrdinal, int beginPc,
                int beginHookToken, String beginSourceCpu, Integer endFrame, Long endOrdinal,
                Integer endPc, Integer endHookToken, List<FrontierSnapshot> snapshots,
                List<FrontierChipEvent> chipEvents) {
            this(token, parentToken, depth, kind, state, beginFrame, beginOrdinal, beginPc,
                    beginHookToken, beginSourceCpu, endFrame, endOrdinal, endPc, endHookToken,
                    snapshots, chipEvents, parentToken, depth, List.of(), null);
        }

        public FrontierService(long token, long parentToken, int depth, String kind,
                FrontierServiceState state, int beginFrame, long beginOrdinal, int beginPc,
                int beginHookToken, String beginSourceCpu, Integer endFrame, Long endOrdinal,
                Integer endPc, Integer endHookToken, List<FrontierSnapshot> snapshots,
                List<FrontierChipEvent> chipEvents, long currentParentToken, int currentDepth,
                List<NativeAncestryTransition> ancestryTransitions) {
            this(token, parentToken, depth, kind, state, beginFrame, beginOrdinal, beginPc,
                    beginHookToken, beginSourceCpu, endFrame, endOrdinal, endPc, endHookToken,
                    snapshots, chipEvents, currentParentToken, currentDepth, ancestryTransitions, null);
        }

        public FrontierService {
            boolean resetRoot = beginHookToken == 0 || "RESET".equals(beginSourceCpu);
            if (token <= 0 || token > 0xffff || parentToken < 0 || parentToken > 0xffff
                    || depth < 0 || depth > 7 || beginFrame < 0
                    || beginPc < 0 || beginPc > 0xffffff || beginHookToken < 0
                    || beginHookToken > 0xffff) {
                throw new IllegalArgumentException("frontier service identity is invalid");
            }
            requireText(kind, "frontier service kind");
            Objects.requireNonNull(state, "frontier service state");
            if (state == FrontierServiceState.CARRIED_IN_OPEN) {
                throw new IllegalArgumentException("native frontier cannot use producer-neutral carried-in state");
            }
            validateSourcePc(beginSourceCpu, beginPc, "frontier service begin");
            if (resetRoot != (beginHookToken == 0 && "RESET".equals(beginSourceCpu))
                    || resetRoot && (parentToken != 0 || depth != 0 || beginPc != 0
                            || state != FrontierServiceState.COMPLETED)) {
                throw new IllegalArgumentException("frontier reset-root identity is invalid");
            }
            nonNegative(beginOrdinal, "frontier service begin ordinal");
            if (beginOrdinal >= MAX_NATIVE_FRAME_EVENTS) {
                throw new IllegalArgumentException("frontier service begin ordinal exceeds native capacity");
            }
            boolean open = state == FrontierServiceState.OPEN;
            if (open != (endFrame == null && endOrdinal == null && endPc == null && endHookToken == null)) {
                throw new IllegalArgumentException("frontier service state and completion identity disagree");
            }
            if (!open && (endFrame == null || endOrdinal == null || endPc == null || endHookToken == null
                    || endFrame < beginFrame || endOrdinal < 0 || endOrdinal >= MAX_NATIVE_FRAME_EVENTS
                    || endPc < 0 || endPc > 0xffffff
                    || endHookToken < 0 || endHookToken > 0xffff)) {
                throw new IllegalArgumentException("frontier service completion identity is invalid");
            }
            if (state == FrontierServiceState.COMPLETED && !resetRoot
                    && endHookToken != null && endHookToken == 0) {
                throw new IllegalArgumentException("completed frontier service requires an exit hook");
            }
            if (state == FrontierServiceState.COMPLETED && !resetRoot) {
                validateSourcePc(beginSourceCpu, endPc, "frontier service completion");
            }
            if (resetRoot && (endHookToken == null || endHookToken != 0 || endPc == null || endPc != 0)) {
                throw new IllegalArgumentException("frontier reset root requires reset completion");
            }
            if (state == FrontierServiceState.RESET_CANCELLED && endHookToken != null
                    && (endHookToken != 0 || endPc != 0)) {
                throw new IllegalArgumentException("reset-cancelled frontier service requires reset completion");
            }
            if (endFrame != null && endFrame == beginFrame && endOrdinal < beginOrdinal) {
                throw new IllegalArgumentException("frontier service ends before it begins");
            }
            snapshots = List.copyOf(Objects.requireNonNull(snapshots, "frontier snapshots"));
            chipEvents = List.copyOf(Objects.requireNonNull(chipEvents, "frontier chip events"));
            ancestryTransitions = List.copyOf(Objects.requireNonNull(ancestryTransitions,
                    "native ancestry transitions"));
            if (currentParentToken < 0 || currentParentToken > 0xffff
                    || currentDepth < 0 || currentDepth > 7
                    || (currentDepth == 0) != (currentParentToken == 0)
                    || ancestryTransitions.size() > 7) {
                throw new IllegalArgumentException("frontier effective ancestry is invalid");
            }
            long effectiveParent = parentToken;
            int effectiveDepth = depth;
            long priorCoordinate = -1;
            long priorOrdinal = -1;
            for (NativeAncestryTransition transition : ancestryTransitions) {
                if (transition.previousParentToken() != effectiveParent
                        || transition.previousDepth() != effectiveDepth
                        || transition.coordinate() <= priorCoordinate
                        || transition.ordinal() <= priorOrdinal
                        || compareFrameOrdinal(beginFrame, beginOrdinal,
                                transition.frame(), transition.ordinal()) >= 0
                        || endFrame != null && compareFrameOrdinal(
                                transition.frame(), transition.ordinal(), endFrame, endOrdinal) >= 0) {
                    throw new IllegalArgumentException("native ancestry transition chain is invalid");
                }
                effectiveParent = transition.currentParentToken();
                effectiveDepth = transition.currentDepth();
                priorCoordinate = transition.coordinate();
                priorOrdinal = transition.ordinal();
            }
            if (effectiveParent != currentParentToken || effectiveDepth != currentDepth) {
                throw new IllegalArgumentException("frontier effective ancestry is inconsistent");
            }
            if (semanticAncestry != null
                    && (semanticAncestry.beginDepth() != depth
                            || semanticAncestry.currentDepth() != currentDepth
                            || semanticAncestry.transitions().size() != ancestryTransitions.size())) {
                throw new IllegalArgumentException("frontier semantic ancestry does not match native shape");
            }
            validateAncestryLifetime(semanticAncestry,
                    new ServiceCoordinate(beginFrame, beginOrdinal),
                    endFrame == null ? null : new ServiceCoordinate(endFrame, endOrdinal),
                    "frontier semantic ancestry");
            if (chipEvents.stream().anyMatch(event -> resetRoot
                    ? !"RESET".equals(event.sourceCpu()) || event.pc() != 0
                    : !beginSourceCpu.equals(event.sourceCpu()))) {
                throw new IllegalArgumentException("frontier chip source does not match its owner");
            }
            Set<Integer> snapshotRanges = new LinkedHashSet<>();
            for (FrontierSnapshot snapshot : snapshots) {
                if (!snapshotRanges.add(snapshot.rangeId())) {
                    throw new IllegalArgumentException("frontier snapshot ownership is duplicated");
                }
            }
            if (chipEvents.size() > MAX_CUTOFF_CHIP_EVENTS
                    || snapshots.stream().mapToLong(snapshot -> snapshot.bytes().size()).sum()
                            > MAX_CUTOFF_SNAPSHOT_BYTES) {
                throw new IllegalArgumentException("frontier service payload exceeds its bound");
            }
            strictlyIncreasing(chipEvents.stream().map(FrontierChipEvent::coordinate).toList(),
                    "frontier chip coordinates");
        }
    }

    /** Producer-neutral service state retained at the exclusive terminal cutoff. */
    private record CutoffCoordinate(int frame, long ordinal) {}
    private record NativeBoundary(long token, int kind, int index, int frame, long ordinal) {}
    private record SemanticServiceCoordinates(CutoffCoordinate begin, CutoffCoordinate end,
            List<CutoffCoordinate> transitions) {}

    public record CutoffService(Integer parentFrame, long parentOrdinal, int depth, String kind,
            FrontierServiceState state, int beginFrame,
            long beginOrdinal, Integer endFrame, Long endOrdinal, List<ChipEvent> chipEvents,
            ServiceAncestry ancestry) {
        public CutoffService(Integer parentFrame, long parentOrdinal, int depth, String kind,
                FrontierServiceState state, int beginFrame,
                long beginOrdinal, Integer endFrame, Long endOrdinal, List<ChipEvent> chipEvents) {
            this(parentFrame, parentOrdinal, depth, kind, state, beginFrame, beginOrdinal,
                    endFrame, endOrdinal, chipEvents,
                    new ServiceAncestry(parentFrame == null ? null
                            : new ServiceCoordinate(parentFrame, parentOrdinal), depth,
                            parentFrame == null ? null : new ServiceCoordinate(parentFrame, parentOrdinal),
                            depth, List.of()));
        }

        public CutoffService {
            requireText(kind, "cutoff service kind");
            Objects.requireNonNull(state, "cutoff service state");
            if (parentOrdinal < -1 || depth < 0 || depth > 7 || beginFrame < 0
                    || (depth == 0) != (parentFrame == null && parentOrdinal == -1)
                    || depth > 0 && (parentFrame == null || parentFrame < 0 || parentOrdinal < 0)) {
                throw new IllegalArgumentException("cutoff service hierarchy is invalid");
            }
            nonNegative(beginOrdinal, "cutoff service begin ordinal");
            boolean open = state == FrontierServiceState.OPEN || state == FrontierServiceState.CARRIED_IN_OPEN;
            if (open != (endFrame == null && endOrdinal == null)
                    || !open && (endFrame < beginFrame || endOrdinal < 0
                            || endFrame == beginFrame && endOrdinal < beginOrdinal)) {
                throw new IllegalArgumentException("cutoff service completion identity is invalid");
            }
            chipEvents = List.copyOf(Objects.requireNonNull(chipEvents, "cutoff chip events"));
            Objects.requireNonNull(ancestry, "cutoff service ancestry");
            ServiceCoordinate declaredParent = parentFrame == null ? null
                    : new ServiceCoordinate(parentFrame, parentOrdinal);
            if (!Objects.equals(declaredParent, ancestry.beginParent()) || depth != ancestry.beginDepth()) {
                throw new IllegalArgumentException("cutoff immutable ancestry is inconsistent");
            }
            validateAncestryLifetime(ancestry, new ServiceCoordinate(beginFrame, beginOrdinal),
                    endFrame == null ? null : new ServiceCoordinate(endFrame, endOrdinal),
                    "cutoff service ancestry");
            strictlyIncreasing(chipEvents.stream().map(ChipEvent::ordinal).toList(),
                    "cutoff semantic chip ordinals");
        }

        static CutoffService fromNative(FrontierService service,
                Map<Long, FrontierService> servicesByToken, Map<Long, ChipEvent> chipsByCoordinate,
                Map<Long, SemanticServiceCoordinates> semanticCoordinates) {
            List<ChipEvent> chips = service.chipEvents().stream().filter(FrontierChipEvent::data)
                    .map(event -> Objects.requireNonNull(chipsByCoordinate.get(event.coordinate()),
                            "native cutoff semantic chip"))
                    .toList();
            FrontierService parent = service.parentToken() == 0 ? null
                    : servicesByToken.get(service.parentToken());
            if (service.parentToken() != 0 && parent == null && service.semanticAncestry() == null) {
                throw new NullPointerException("native cutoff parent service");
            }
            SemanticServiceCoordinates coordinates = Objects.requireNonNull(
                    semanticCoordinates.get(service.token()), "native cutoff semantic coordinates");
            SemanticServiceCoordinates parentCoordinates = parent == null ? null
                    : Objects.requireNonNull(semanticCoordinates.get(parent.token()),
                            "native cutoff parent semantic coordinates");
            List<ServiceAncestryTransition> transitions = new ArrayList<>();
            for (int index = 0; service.semanticAncestry() == null
                    && index < service.ancestryTransitions().size(); index++) {
                NativeAncestryTransition nativeTransition = service.ancestryTransitions().get(index);
                FrontierService previous = Objects.requireNonNull(
                        servicesByToken.get(nativeTransition.previousParentToken()),
                        "native previous ancestry parent");
                FrontierService current = nativeTransition.currentParentToken() == 0 ? null
                        : Objects.requireNonNull(servicesByToken.get(nativeTransition.currentParentToken()),
                                "native current ancestry parent");
                CutoffCoordinate transitionCoordinate = coordinates.transitions().get(index);
                transitions.add(new ServiceAncestryTransition(
                        toServiceCoordinate(semanticCoordinates.get(previous.token()).begin()),
                        nativeTransition.previousDepth(),
                        current == null ? null
                                : toServiceCoordinate(semanticCoordinates.get(current.token()).begin()),
                        nativeTransition.currentDepth(), transitionCoordinate.frame(),
                        transitionCoordinate.ordinal()));
            }
            ServiceAncestry ancestry = service.semanticAncestry();
            if (ancestry == null) {
                ServiceCoordinate beginParent = parentCoordinates == null ? null
                        : toServiceCoordinate(parentCoordinates.begin());
                FrontierService currentParent = service.currentParentToken() == 0 ? null
                        : Objects.requireNonNull(servicesByToken.get(service.currentParentToken()),
                                "native effective ancestry parent");
                ancestry = new ServiceAncestry(beginParent, service.depth(),
                        currentParent == null ? null
                                : toServiceCoordinate(semanticCoordinates.get(currentParent.token()).begin()),
                        service.currentDepth(), transitions);
            }
            ServiceCoordinate ancestryParent = ancestry.beginParent();
            return new CutoffService(ancestryParent == null ? null : ancestryParent.frame(),
                    ancestryParent == null ? -1 : ancestryParent.ordinal(), service.depth(),
                    service.kind(), service.state(), coordinates.begin().frame(), coordinates.begin().ordinal(),
                    coordinates.end() == null ? null : coordinates.end().frame(),
                    coordinates.end() == null ? null : coordinates.end().ordinal(), chips, ancestry);
        }

        private static ServiceCoordinate toServiceCoordinate(CutoffCoordinate coordinate) {
            return new ServiceCoordinate(coordinate.frame(), coordinate.ordinal());
        }
    }

    private static Map<Long, SemanticServiceCoordinates> semanticServiceCoordinates(
            List<FrontierService> active, List<FrontierService> pending) {
        List<FrontierService> services = java.util.stream.Stream.concat(active.stream(), pending.stream()).toList();
        List<NativeBoundary> boundaries = new ArrayList<>();
        for (FrontierService service : services) {
            boundaries.add(new NativeBoundary(service.token(), 0, -1,
                    service.beginFrame(), service.beginOrdinal()));
            if (service.endFrame() != null) {
                boundaries.add(new NativeBoundary(service.token(), 1, -1,
                        service.endFrame(), service.endOrdinal()));
            }
            for (int index = 0; index < service.ancestryTransitions().size(); index++) {
                NativeAncestryTransition transition = service.ancestryTransitions().get(index);
                boundaries.add(new NativeBoundary(service.token(), 2, index,
                        transition.frame(), transition.ordinal()));
            }
        }
        boundaries.sort(java.util.Comparator.comparingInt(NativeBoundary::frame)
                .thenComparingLong(NativeBoundary::ordinal));
        Map<Long, CutoffCoordinate> begins = new LinkedHashMap<>();
        Map<Long, CutoffCoordinate> ends = new LinkedHashMap<>();
        Map<Long, List<CutoffCoordinate>> transitions = new LinkedHashMap<>();
        int frame = -1;
        long semanticOrdinal = 0;
        NativeBoundary previous = null;
        for (NativeBoundary boundary : boundaries) {
            if (previous != null && previous.frame() == boundary.frame()
                    && previous.ordinal() == boundary.ordinal()) {
                throw new IllegalArgumentException("native cutoff service boundary coordinate is duplicated");
            }
            if (boundary.frame() != frame) {
                frame = boundary.frame();
                semanticOrdinal = 0;
            }
            CutoffCoordinate coordinate = new CutoffCoordinate(frame, semanticOrdinal++);
            if (boundary.kind() == 0) begins.put(boundary.token(), coordinate);
            else if (boundary.kind() == 1) ends.put(boundary.token(), coordinate);
            else transitions.computeIfAbsent(boundary.token(), ignored -> new ArrayList<>()).add(coordinate);
            previous = boundary;
        }
        Map<Long, SemanticServiceCoordinates> result = new LinkedHashMap<>();
        for (FrontierService service : services) {
            result.put(service.token(), new SemanticServiceCoordinates(
                    Objects.requireNonNull(begins.get(service.token()), "native cutoff begin coordinate"),
                    ends.get(service.token()), List.copyOf(transitions.getOrDefault(service.token(), List.of()))));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Buffered-native-only token/tree/PC/source inventory; absent for callback and OpenGGF producers. */
    public record CutoffNativeDiagnostics(List<FrontierService> activeStack,
            List<FrontierService> pendingDescendants, List<FrontierOwnedChip> rawChipInventory,
            List<FrontierOwnedSnapshot> rawSnapshotInventory,
            NativeDeferredServiceBegin pendingDeferredServiceBegin, long armEpoch, boolean armed,
            String terminalZ80Digest) {
        public CutoffNativeDiagnostics(List<FrontierService> activeStack,
                List<FrontierService> pendingDescendants, List<FrontierOwnedChip> rawChipInventory,
                List<FrontierOwnedSnapshot> rawSnapshotInventory, long armEpoch, boolean armed,
                String terminalZ80Digest) {
            this(activeStack, pendingDescendants, rawChipInventory, rawSnapshotInventory,
                    null, armEpoch, armed, terminalZ80Digest);
        }

        public CutoffNativeDiagnostics {
            activeStack = List.copyOf(Objects.requireNonNull(activeStack, "native cutoff active stack"));
            pendingDescendants = List.copyOf(Objects.requireNonNull(pendingDescendants,
                    "native cutoff pending descendants"));
            rawChipInventory = List.copyOf(Objects.requireNonNull(rawChipInventory,
                    "native cutoff raw chip inventory"));
            rawSnapshotInventory = List.copyOf(Objects.requireNonNull(rawSnapshotInventory,
                    "native cutoff raw snapshot inventory"));
            if (pendingDeferredServiceBegin != null && pendingDeferredServiceBegin.consumed()) {
                throw new IllegalArgumentException("native cutoff deferred service begin is already consumed");
            }
            nonNegative(armEpoch, "native cutoff arm epoch");
            lowercaseHex(terminalZ80Digest, SHA256, "native cutoff terminal Z80 digest");
            validateNativeFrontier(activeStack, pendingDescendants, rawChipInventory, rawSnapshotInventory);
            if (pendingDeferredServiceBegin != null) {
                FrontierService blocker = activeStack.isEmpty() ? null : activeStack.getLast();
                if (blocker == null || blocker.token() != pendingDeferredServiceBegin.blockerToken()
                        || blocker.currentParentToken()
                                != pendingDeferredServiceBegin.blockerParentToken()
                        || blocker.kind().isBlank()
                        || blocker.currentDepth() != pendingDeferredServiceBegin.blockerDepth()
                        || blocker.state() != FrontierServiceState.OPEN) {
                    throw new IllegalArgumentException(
                            "native cutoff deferred begin has no exact active blocker");
                }
            }
        }
    }

    private static void validateNativeFrontier(List<FrontierService> activeStack,
            List<FrontierService> pendingDescendants, List<FrontierOwnedChip> rawChipInventory,
            List<FrontierOwnedSnapshot> rawSnapshotInventory) {
        if (activeStack.size() > 8 || activeStack.size() + (long) pendingDescendants.size()
                > MAX_CUTOFF_SERVICES) {
            throw new IllegalArgumentException("native cutoff frontier exceeds its service bound");
        }
        Set<Long> tokens = new LinkedHashSet<>();
        Map<Long, FrontierService> servicesByToken = new LinkedHashMap<>();
        for (int index = 0; index < activeStack.size(); index++) {
            FrontierService service = activeStack.get(index);
            if (service.state() != FrontierServiceState.OPEN || service.currentDepth() != index
                    || (index == 0 ? service.currentParentToken() != 0
                            : service.currentParentToken() != activeStack.get(index - 1).token())
                    || index > 0 && !nativeBeginsBefore(activeStack.get(index - 1), service)
                    || !tokens.add(service.token())) {
                throw new IllegalArgumentException("native cutoff active stack is not outer-to-inner");
            }
            servicesByToken.put(service.token(), service);
        }
        if (!pendingDescendants.isEmpty() && activeStack.isEmpty()) {
            throw new IllegalArgumentException("native pending descendants require an active ancestor");
        }
        int priorFrame = -1;
        long priorOrdinal = -1;
        for (FrontierService service : pendingDescendants) {
            if (service.state() == FrontierServiceState.OPEN || service.beginFrame() < priorFrame
                    || service.beginFrame() == priorFrame && service.beginOrdinal() <= priorOrdinal
                    || !tokens.add(service.token())) {
                throw new IllegalArgumentException("native pending services are not unique begin order");
            }
            FrontierService parent = servicesByToken.get(service.currentParentToken());
            if (parent == null || service.currentDepth() != parent.currentDepth() + 1
                    || !nativeBeginsBefore(parent, service)
                    || parent.state() != FrontierServiceState.OPEN && !nativeEndsBefore(service, parent)) {
                throw new IllegalArgumentException("native pending descendant has no earlier parent");
            }
            servicesByToken.put(service.token(), service);
            priorFrame = service.beginFrame();
            priorOrdinal = service.beginOrdinal();
        }
        long chipCount = java.util.stream.Stream.concat(activeStack.stream(), pendingDescendants.stream())
                .mapToLong(service -> service.chipEvents().size()).sum();
        long snapshotCount = java.util.stream.Stream.concat(activeStack.stream(), pendingDescendants.stream())
                .mapToLong(service -> service.snapshots().size()).sum();
        long snapshotBytes = java.util.stream.Stream.concat(activeStack.stream(), pendingDescendants.stream())
                .flatMap(service -> service.snapshots().stream()).mapToLong(value -> value.bytes().size()).sum();
        if (chipCount > MAX_CUTOFF_CHIP_EVENTS || chipCount != rawChipInventory.size()
                || snapshotBytes > MAX_CUTOFF_SNAPSHOT_BYTES || snapshotCount != rawSnapshotInventory.size()) {
            throw new IllegalArgumentException("native cutoff inventories exceed aggregate bounds");
        }
        List<FrontierOwnedChip> expectedChips = java.util.stream.Stream
                .concat(activeStack.stream(), pendingDescendants.stream())
                .flatMap(service -> service.chipEvents().stream()
                        .map(event -> new FrontierOwnedChip(service.token(), event)))
                .sorted(java.util.Comparator.comparingLong(owned -> owned.event().coordinate())).toList();
        List<FrontierOwnedSnapshot> expectedSnapshots = new java.util.ArrayList<>();
        for (FrontierService service : java.util.stream.Stream.concat(
                activeStack.stream(), pendingDescendants.stream()).toList()) {
            for (int index = 0; index < service.snapshots().size(); index++) {
                expectedSnapshots.add(new FrontierOwnedSnapshot(service.token(), index,
                        service.snapshots().get(index)));
            }
        }
        if (!rawChipInventory.equals(expectedChips) || !rawSnapshotInventory.equals(expectedSnapshots)) {
            throw new IllegalArgumentException("native cutoff inventories are not exact ownership partitions");
        }
        strictlyIncreasing(rawChipInventory.stream().map(owned -> owned.event().coordinate()).toList(),
                "native cutoff raw chip coordinates");
        validateNativeYmProjection(rawChipInventory);
    }

    private static void validateNativeYmProjection(List<FrontierOwnedChip> inventory) {
        validateNativeYmProjection(inventory, List.of());
    }

    private static void validateNativeYmProjection(List<FrontierOwnedChip> inventory,
            List<FrontierService> resetServices) {
        Integer[] latches = new Integer[2];
        int resetIndex = 0;
        for (FrontierOwnedChip owned : inventory) {
            FrontierChipEvent event = owned.event();
            while (resetIndex < resetServices.size()
                    && resetServices.get(resetIndex).beginOrdinal() < event.ordinal()) {
                latches[0] = 0;
                latches[1] = 0;
                resetIndex++;
            }
            if (event.eventKind() != 3) continue;
            int port = event.port();
            if (latches[port] != null && event.register() != latches[port]) {
                throw new IllegalArgumentException("native YM write does not match its prior address latch");
            }
            if (!event.data()) latches[port] = event.value();
        }
    }

    private static boolean nativeBeginsBefore(FrontierService parent, FrontierService child) {
        return parent.beginFrame() < child.beginFrame()
                || parent.beginFrame() == child.beginFrame() && parent.beginOrdinal() < child.beginOrdinal();
    }

    private static boolean nativeEndsBefore(FrontierService child, FrontierService parent) {
        return child.endFrame() < parent.endFrame()
                || child.endFrame().equals(parent.endFrame()) && child.endOrdinal() < parent.endOrdinal();
    }

    private static boolean nativePromotionClosesParent(FrontierService child, FrontierService parent) {
        if (parent.endFrame() == null || child.ancestryTransitions().isEmpty()) return false;
        NativeAncestryTransition transition = child.ancestryTransitions().getFirst();
        return nativePromotionClosesParent(child, parent, transition);
    }

    private static boolean nativePromotionClosesParent(FrontierService child, FrontierService parent,
            NativeAncestryTransition transition) {
        if (parent.endFrame() == null) return false;
        return transition.previousParentToken() == parent.token()
                && transition.previousDepth() == child.depth()
                && transition.currentParentToken() == parent.currentParentToken()
                && transition.currentDepth() == parent.currentDepth()
                && transition.frame() == parent.endFrame()
                && transition.ordinal() == parent.endOrdinal() + 1
                && transition.hookToken() == parent.endHookToken()
                && transition.sourceCpu().equals(parent.beginSourceCpu())
                && transition.pc() == parent.endPc();
    }

    /** One globally ordered raw chip callback and its exclusive frontier owner. */
    public record FrontierOwnedChip(long ownerToken, FrontierChipEvent event) {
        public FrontierOwnedChip {
            if (ownerToken <= 0 || ownerToken > 0xffff) {
                throw new IllegalArgumentException("frontier chip owner token is invalid");
            }
            Objects.requireNonNull(event, "frontier owned chip event");
        }
    }

    /** One raw event-11 promotion and the child whose effective ancestry it changes. */
    public record FrontierOwnedAncestryTransition(long ownerToken, NativeAncestryTransition event) {
        public FrontierOwnedAncestryTransition {
            if (ownerToken <= 0 || ownerToken > 0xffff) {
                throw new IllegalArgumentException("native ancestry-transition owner token is invalid");
            }
            Objects.requireNonNull(event, "native ancestry-transition event");
            if (ownerToken == event.previousParentToken()) {
                throw new IllegalArgumentException("native ancestry transition cannot promote its parent");
            }
        }
    }

    /** One service-local snapshot group and its exclusive frontier owner. */
    public record FrontierOwnedSnapshot(long ownerToken, int serviceIndex, FrontierSnapshot snapshot) {
        public FrontierOwnedSnapshot {
            if (ownerToken <= 0 || ownerToken > 0xffff || serviceIndex < 0) {
                throw new IllegalArgumentException("frontier snapshot owner is invalid");
            }
            Objects.requireNonNull(snapshot, "frontier owned snapshot");
        }
    }

    public record FrontierSnapshotRule(int rangeId, String sourceCpu, int pc, int byteLength) {
        public FrontierSnapshotRule {
            if (rangeId <= 0 || rangeId > 0xffff || byteLength < 0
                    || byteLength > MAX_CUTOFF_SNAPSHOT_BYTES) {
                throw new IllegalArgumentException("frontier snapshot rule is invalid");
            }
            validateSourcePc(sourceCpu, pc, "frontier snapshot rule");
        }
    }

    /** One relational service-manifest rule; independent field sets are deliberately insufficient. */
    public record FrontierServiceRule(String kind, FrontierServiceState state, int beginHookToken,
            String beginSourceCpu, int beginPc, Integer endHookToken, Integer endPc,
            List<FrontierSnapshotRule> snapshots) {
        public FrontierServiceRule {
            requireText(kind, "frontier service-rule kind");
            Objects.requireNonNull(state, "frontier service-rule state");
            boolean resetRoot = beginHookToken == 0 || "RESET".equals(beginSourceCpu);
            if (beginHookToken < 0 || beginHookToken > 0xffff) {
                throw new IllegalArgumentException("frontier service-rule begin hook is invalid");
            }
            validateSourcePc(beginSourceCpu, beginPc, "frontier service rule");
            if (resetRoot != (beginHookToken == 0 && "RESET".equals(beginSourceCpu))
                    || resetRoot && (state != FrontierServiceState.COMPLETED || beginPc != 0
                            || !Objects.equals(endHookToken, 0) || !Objects.equals(endPc, 0))) {
                throw new IllegalArgumentException("frontier reset-root service rule is invalid");
            }
            if (state == FrontierServiceState.OPEN) {
                if (endHookToken != null || endPc != null) {
                    throw new IllegalArgumentException("open frontier service rule has completion identity");
                }
            } else if (endHookToken == null || endPc == null
                    || (state == FrontierServiceState.COMPLETED
                            && !resetRoot && (endHookToken <= 0 || endHookToken > 0xffff
                                    || endPc < 0 || endPc > 0xffffff))
                    || (state == FrontierServiceState.RESET_CANCELLED && (endHookToken != 0 || endPc != 0))) {
                throw new IllegalArgumentException("frontier service-rule completion is invalid");
            }
            if (state == FrontierServiceState.COMPLETED && !resetRoot) {
                validateSourcePc(beginSourceCpu, endPc, "frontier service-rule completion");
            }
            snapshots = List.copyOf(Objects.requireNonNull(snapshots, "frontier service-rule snapshots"));
        }

        boolean matches(FrontierService service) {
            return kind.equals(service.kind()) && state == service.state()
                    && beginHookToken == service.beginHookToken() && beginSourceCpu.equals(service.beginSourceCpu())
                    && beginPc == service.beginPc() && Objects.equals(endHookToken, service.endHookToken())
                    && Objects.equals(endPc, service.endPc())
                    && snapshots.equals(service.snapshots().stream()
                            .map(snapshot -> new FrontierSnapshotRule(snapshot.rangeId(), snapshot.sourceCpu(),
                                    snapshot.pc(), snapshot.bytes().size())).toList());
        }

        boolean acceptsChipSources(FrontierService service) {
            boolean reset = "RESET".equals(beginSourceCpu);
            return service.chipEvents().stream().allMatch(event -> reset
                    ? "RESET".equals(event.sourceCpu()) && event.pc() == 0
                    : beginSourceCpu.equals(event.sourceCpu()));
        }
    }

    /** Producer-neutral semantic service frontier shared by both comparison boundaries. */
    public record BoundaryFrontier(List<CutoffService> activeStack,
            List<CutoffService> pendingDescendants, List<ChipEvent> rawChipEvents,
            CutoffNativeDiagnostics nativeDiagnostics, int ymPort0Latch, int ymPort1Latch) {
        public BoundaryFrontier {
            activeStack = List.copyOf(Objects.requireNonNull(activeStack, "boundary active stack"));
            pendingDescendants = List.copyOf(Objects.requireNonNull(
                    pendingDescendants, "boundary pending descendants"));
            rawChipEvents = List.copyOf(Objects.requireNonNull(rawChipEvents, "boundary raw chip events"));
            if (activeStack.size() > 8
                    || activeStack.size() + (long) pendingDescendants.size() > MAX_CUTOFF_SERVICES) {
                throw new IllegalArgumentException("boundary frontier exceeds its service bound");
            }
            unsignedByte(ymPort0Latch, "boundary YM port-zero latch");
            unsignedByte(ymPort1Latch, "boundary YM port-one latch");
            for (int index = 0; index < activeStack.size(); index++) {
                CutoffService service = activeStack.get(index);
                boolean active = service.state() == FrontierServiceState.OPEN
                        || service.state() == FrontierServiceState.CARRIED_IN_OPEN;
                ServiceCoordinate effectiveParent = service.ancestry().currentParent();
                if (!active || service.ancestry().currentDepth() != index
                        || index == 0 && effectiveParent != null
                        || index > 0 && !Objects.equals(effectiveParent,
                                new ServiceCoordinate(activeStack.get(index - 1).beginFrame(),
                                        activeStack.get(index - 1).beginOrdinal()))) {
                    throw new IllegalArgumentException("boundary active stack is not outer-to-inner");
                }
            }
            if (!pendingDescendants.isEmpty() && activeStack.isEmpty()) {
                throw new IllegalArgumentException("boundary pending descendants require an active ancestor");
            }
            if (pendingDescendants.stream().anyMatch(service -> service.state() == FrontierServiceState.OPEN
                    || service.state() == FrontierServiceState.CARRIED_IN_OPEN)) {
                throw new IllegalArgumentException("boundary pending descendants must be completed");
            }
            Map<CutoffCoordinate, CutoffService> services = new LinkedHashMap<>();
            for (CutoffService service : activeStack) {
                services.put(new CutoffCoordinate(service.beginFrame(), service.beginOrdinal()), service);
            }
            int previousFrame = -1;
            long previousOrdinal = -1;
            for (CutoffService service : pendingDescendants) {
                ServiceCoordinate effectiveParent = service.ancestry().currentParent();
                CutoffService parent = effectiveParent == null ? null
                        : services.get(new CutoffCoordinate(effectiveParent.frame(), effectiveParent.ordinal()));
                if (parent == null || service.ancestry().currentDepth() != parent.ancestry().currentDepth() + 1
                        || !boundaryBeginsBefore(parent, service)
                        || parent.state() != FrontierServiceState.OPEN
                                && parent.state() != FrontierServiceState.CARRIED_IN_OPEN
                                && !boundaryEndsBefore(service, parent)
                        || service.beginFrame() < previousFrame
                        || service.beginFrame() == previousFrame && service.beginOrdinal() <= previousOrdinal
                        || services.putIfAbsent(new CutoffCoordinate(
                                service.beginFrame(), service.beginOrdinal()), service) != null) {
                    throw new IllegalArgumentException("boundary pending descendant has no earlier parent");
                }
                previousFrame = service.beginFrame();
                previousOrdinal = service.beginOrdinal();
            }
            List<CutoffCoordinate> boundaries = new ArrayList<>();
            for (CutoffService service : java.util.stream.Stream.concat(
                    activeStack.stream(), pendingDescendants.stream()).toList()) {
                boundaries.add(new CutoffCoordinate(service.beginFrame(), service.beginOrdinal()));
                if (service.endFrame() != null) {
                    boundaries.add(new CutoffCoordinate(service.endFrame(), service.endOrdinal()));
                }
                for (ServiceAncestryTransition transition : service.ancestry().transitions()) {
                    boundaries.add(new CutoffCoordinate(
                            transition.transitionFrame(), transition.transitionOrdinal()));
                }
            }
            boundaries.sort(java.util.Comparator.comparingInt(CutoffCoordinate::frame)
                    .thenComparingLong(CutoffCoordinate::ordinal));
            Set<CutoffCoordinate> uniqueBoundaries = new LinkedHashSet<>();
            for (CutoffCoordinate coordinate : boundaries) {
                if (!uniqueBoundaries.add(coordinate)) {
                    throw new IllegalArgumentException(
                            "boundary semantic service coordinate is duplicated");
                }
            }
            long chipCount = java.util.stream.Stream.concat(activeStack.stream(), pendingDescendants.stream())
                    .mapToLong(service -> service.chipEvents().size()).sum();
            if (chipCount > MAX_CUTOFF_CHIP_EVENTS || chipCount != rawChipEvents.size()) {
                throw new IllegalArgumentException("boundary chip inventory exceeds its bound");
            }
            List<ChipEvent> owned = java.util.stream.Stream.concat(activeStack.stream(),
                    pendingDescendants.stream()).flatMap(service -> service.chipEvents().stream())
                    .sorted(java.util.Comparator.comparingLong(ChipEvent::ordinal)).toList();
            if (!rawChipEvents.equals(owned)) {
                throw new IllegalArgumentException("boundary raw chips are not an ownership partition");
            }
            for (int index = 0; index < rawChipEvents.size(); index++) {
                if (rawChipEvents.get(index).ordinal() != index) {
                    throw new IllegalArgumentException("boundary semantic chip ordinals are not contiguous");
                }
            }
        }

        public static BoundaryFrontier empty() {
            return new BoundaryFrontier(List.of(), List.of(), List.of(), null, 0, 0);
        }

        private static boolean boundaryBeginsBefore(CutoffService parent, CutoffService child) {
            return parent.beginFrame() < child.beginFrame()
                    || parent.beginFrame() == child.beginFrame()
                            && parent.beginOrdinal() < child.beginOrdinal();
        }

        private static boolean boundaryEndsBefore(CutoffService child, CutoffService parent) {
            return child.endFrame() < parent.endFrame()
                    || child.endFrame().equals(parent.endFrame())
                            && child.endOrdinal() < parent.endOrdinal();
        }
    }

    /** Profile-owned exact manifest/capability contract and hard bounds for a cutoff frontier. */
    public record CutoffFrontierPolicy(List<FrontierServiceRule> serviceRules, int expectedActive,
            int expectedPending, int expectedSemanticChipEvents, int expectedNativeChipEvents,
            int expectedSnapshotBytes,
            int expectedYmPort0Latch, int expectedYmPort1Latch, long expectedArmEpoch,
            boolean expectedArmed, String expectedTerminalZ80Digest,
            String expectedSemanticCapabilityDigest, String expectedNativeCapabilityDigest) {
        public CutoffFrontierPolicy {
            serviceRules = List.copyOf(Objects.requireNonNull(serviceRules, "frontier service rules"));
            if (serviceRules.stream().anyMatch(Objects::isNull) || expectedActive < 0 || expectedActive > 8
                    || expectedPending < 0 || expectedActive + (long) expectedPending > MAX_CUTOFF_SERVICES
                    || expectedSemanticChipEvents < 0 || expectedSemanticChipEvents > MAX_CUTOFF_CHIP_EVENTS
                    || expectedNativeChipEvents < 0 || expectedNativeChipEvents > MAX_CUTOFF_CHIP_EVENTS
                    || expectedSnapshotBytes < 0 || expectedSnapshotBytes > MAX_CUTOFF_SNAPSHOT_BYTES) {
                throw new IllegalArgumentException("frontier policy is outside canonical bounds");
            }
            unsignedByte(expectedYmPort0Latch, "expected frontier YM port-zero latch");
            unsignedByte(expectedYmPort1Latch, "expected frontier YM port-one latch");
            nonNegative(expectedArmEpoch, "expected frontier arm epoch");
            lowercaseHex(expectedTerminalZ80Digest, SHA256, "expected terminal-Z80 digest");
            lowercaseHex(expectedSemanticCapabilityDigest, SHA256,
                    "expected semantic frontier-capability digest");
            if (expectedNativeCapabilityDigest != null) lowercaseHex(expectedNativeCapabilityDigest, SHA256,
                    "expected native frontier-capability digest");
        }

        public void validate(CutoffFrontier frontier) {
            Objects.requireNonNull(frontier, "cutoff frontier");
            if (frontier.activeStack().size() != expectedActive
                    || frontier.pendingDescendants().size() != expectedPending
                    || frontier.activeStack().stream().mapToLong(service -> service.chipEvents().size()).sum()
                            + frontier.pendingDescendants().stream()
                                    .mapToLong(service -> service.chipEvents().size()).sum()
                                    != expectedSemanticChipEvents
                    || frontier.nativeDiagnostics() != null
                            && frontier.nativeDiagnostics().rawSnapshotInventory().stream()
                                    .mapToLong(owned -> owned.snapshot().bytes().size()).sum()
                                    != expectedSnapshotBytes
                    || frontier.ymPort0Latch() != expectedYmPort0Latch
                    || frontier.ymPort1Latch() != expectedYmPort1Latch
                    || frontier.nativeDiagnostics() != null
                            && (frontier.nativeDiagnostics().armEpoch() != expectedArmEpoch
                                    || frontier.nativeDiagnostics().armed() != expectedArmed
                                    || !frontier.nativeDiagnostics().terminalZ80Digest()
                                            .equals(expectedTerminalZ80Digest))
                    || !capabilityDigest(frontier)
                            .equals(expectedSemanticCapabilityDigest)) {
                throw new IllegalArgumentException("cutoff frontier exceeds its profile-owned limit");
            }
            if (frontier.nativeDiagnostics() != null) {
                if (frontier.nativeDiagnostics().rawChipInventory().size() != expectedNativeChipEvents
                        || expectedNativeCapabilityDigest == null
                        || !nativeCapabilityDigest(frontier.nativeDiagnostics())
                                .equals(expectedNativeCapabilityDigest)) {
                    throw new IllegalArgumentException("native cutoff frontier differs from its capability");
                }
                for (FrontierService service : java.util.stream.Stream.concat(
                        frontier.nativeDiagnostics().activeStack().stream(),
                        frontier.nativeDiagnostics().pendingDescendants().stream()).toList()) {
                    if (serviceRules.stream().noneMatch(rule -> rule.matches(service)
                            && rule.acceptsChipSources(service))) {
                        throw new IllegalArgumentException("cutoff frontier is outside its service manifest");
                    }
                }
            }
        }

        public static String capabilityDigest(CutoffFrontier frontier) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(CompleteRunAudioJson.frontierCapabilityProjection(frontier)
                                .getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException impossible) {
                throw new AssertionError(impossible);
            }
        }

        public static String nativeCapabilityDigest(CutoffNativeDiagnostics diagnostics) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(CompleteRunAudioJson.writeNativeCutoffDiagnostics(diagnostics)
                                .getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException impossible) {
                throw new AssertionError(impossible);
            }
        }
    }

    /** Required terminal semantic record, serialized before observer cleanup and terminal. */
    public record CutoffFrontier(List<CutoffService> activeStack,
            List<CutoffService> pendingDescendants, List<ChipEvent> rawChipEvents,
            CutoffNativeDiagnostics nativeDiagnostics,
            int ymPort0Latch, int ymPort1Latch,
            NormalizedState terminalState) implements Record {
        public CutoffFrontier(BoundaryFrontier frontier, NormalizedState terminalState) {
            this(Objects.requireNonNull(frontier, "cutoff boundary frontier").activeStack(),
                    frontier.pendingDescendants(), frontier.rawChipEvents(), frontier.nativeDiagnostics(),
                    frontier.ymPort0Latch(), frontier.ymPort1Latch(), terminalState);
        }

        public CutoffFrontier {
            activeStack = List.copyOf(Objects.requireNonNull(activeStack, "frontier active stack"));
            pendingDescendants = List.copyOf(Objects.requireNonNull(pendingDescendants,
                    "frontier pending descendants"));
            rawChipEvents = List.copyOf(Objects.requireNonNull(rawChipEvents, "cutoff raw chip events"));
            if (activeStack.size() > 8 || activeStack.size() + (long) pendingDescendants.size()
                    > MAX_CUTOFF_SERVICES) {
                throw new IllegalArgumentException("cutoff frontier exceeds its service bound");
            }
            unsignedByte(ymPort0Latch, "frontier YM port-zero latch");
            unsignedByte(ymPort1Latch, "frontier YM port-one latch");
            Objects.requireNonNull(terminalState, "frontier terminal state");
            int previousBeginFrame = -1;
            long previousBeginOrdinal = -1;
            for (int i = 0; i < activeStack.size(); i++) {
                CutoffService service = activeStack.get(i);
                ServiceCoordinate effectiveParent = service.ancestry().currentParent();
                if (service.state() != FrontierServiceState.OPEN || service.ancestry().currentDepth() != i
                        || i == 0 && effectiveParent != null
                        || i > 0 && (!Objects.equals(effectiveParent,
                                    new ServiceCoordinate(activeStack.get(i - 1).beginFrame(),
                                            activeStack.get(i - 1).beginOrdinal()))
                                || !beginsBefore(activeStack.get(i - 1), service))) {
                    throw new IllegalArgumentException("frontier active stack is not outer-to-inner");
                }
            }
            if (!pendingDescendants.isEmpty() && activeStack.isEmpty()) {
                throw new IllegalArgumentException("pending cutoff descendants require an active ancestor");
            }
            List<CutoffCoordinate> semanticBoundaries = new ArrayList<>();
            for (CutoffService service : java.util.stream.Stream.concat(
                    activeStack.stream(), pendingDescendants.stream()).toList()) {
                semanticBoundaries.add(new CutoffCoordinate(service.beginFrame(), service.beginOrdinal()));
                if (service.endFrame() != null) {
                    semanticBoundaries.add(new CutoffCoordinate(service.endFrame(), service.endOrdinal()));
                }
                for (ServiceAncestryTransition transition : service.ancestry().transitions()) {
                    semanticBoundaries.add(new CutoffCoordinate(
                            transition.transitionFrame(), transition.transitionOrdinal()));
                }
            }
            semanticBoundaries.sort(java.util.Comparator.comparingInt(CutoffCoordinate::frame)
                    .thenComparingLong(CutoffCoordinate::ordinal));
            Set<CutoffCoordinate> uniqueSemanticBoundaries = new LinkedHashSet<>();
            for (CutoffCoordinate coordinate : semanticBoundaries) {
                if (!uniqueSemanticBoundaries.add(coordinate)) {
                    throw new IllegalArgumentException(
                            "cutoff semantic service boundary is duplicated");
                }
            }
            Map<CutoffCoordinate, CutoffService> semanticByOrdinal = new LinkedHashMap<>();
            for (CutoffService service : activeStack) {
                if (semanticByOrdinal.putIfAbsent(new CutoffCoordinate(service.beginFrame(),
                        service.beginOrdinal()), service) != null) {
                    throw new IllegalArgumentException("frontier service begin coordinate is duplicated");
                }
            }
            for (CutoffService service : pendingDescendants) {
                if (service.state() == FrontierServiceState.OPEN
                        || service.beginFrame() < previousBeginFrame
                        || (service.beginFrame() == previousBeginFrame
                                && service.beginOrdinal() <= previousBeginOrdinal)) {
                    throw new IllegalArgumentException("frontier pending services must be completed in begin order");
                }
                ServiceCoordinate effectiveParent = service.ancestry().currentParent();
                CutoffService parent = effectiveParent == null ? null
                        : semanticByOrdinal.get(new CutoffCoordinate(
                                effectiveParent.frame(), effectiveParent.ordinal()));
                if (parent == null || service.ancestry().currentDepth() != parent.ancestry().currentDepth() + 1
                        || !beginsBefore(parent, service)
                        || parent.state() != FrontierServiceState.OPEN && !endsBefore(service, parent)
                        || semanticByOrdinal.putIfAbsent(new CutoffCoordinate(service.beginFrame(),
                                service.beginOrdinal()), service) != null) {
                    throw new IllegalArgumentException("frontier pending service has no earlier semantic parent");
                }
                previousBeginFrame = service.beginFrame();
                previousBeginOrdinal = service.beginOrdinal();
            }
            long chipCount = java.util.stream.Stream.concat(activeStack.stream(), pendingDescendants.stream())
                    .mapToLong(service -> service.chipEvents().size()).sum();
            if (chipCount > MAX_CUTOFF_CHIP_EVENTS) {
                throw new IllegalArgumentException("cutoff frontier exceeds its chip-event bound");
            }
            strictlyIncreasing(rawChipEvents.stream().map(ChipEvent::ordinal).toList(),
                    "cutoff raw chip ordinals");
            for (int index = 0; index < rawChipEvents.size(); index++) {
                if (rawChipEvents.get(index).ordinal() != index) {
                    throw new IllegalArgumentException("cutoff semantic chip ordinals are not contiguous");
                }
            }
            List<ChipEvent> ownedChips = java.util.stream.Stream.concat(
                    activeStack.stream(), pendingDescendants.stream())
                    .flatMap(service -> service.chipEvents().stream())
                    .sorted(java.util.Comparator.comparingLong(ChipEvent::ordinal)).toList();
            if (!rawChipEvents.equals(ownedChips)) {
                throw new IllegalArgumentException("cutoff raw chips are not an exact ownership partition");
            }
            if (nativeDiagnostics != null) {
                Map<Long, FrontierService> servicesByToken = new LinkedHashMap<>();
                java.util.stream.Stream.concat(nativeDiagnostics.activeStack().stream(),
                        nativeDiagnostics.pendingDescendants().stream())
                        .forEach(service -> servicesByToken.put(service.token(), service));
                Map<Long, ChipEvent> chipsByCoordinate = semanticChipsByCoordinate(
                        nativeDiagnostics.rawChipInventory());
                Map<Long, SemanticServiceCoordinates> semanticCoordinates = new LinkedHashMap<>(
                        semanticServiceCoordinates(nativeDiagnostics.activeStack(),
                                nativeDiagnostics.pendingDescendants()));
                // An active service carried through the baseline keeps its producer-neutral
                // baseline coordinate at cutoff. Only the native sidecar retains the true
                // pre-epoch coordinate, so project native parents through the semantic stack.
                for (int index = 0; index < nativeDiagnostics.activeStack().size(); index++) {
                    FrontierService nativeService = nativeDiagnostics.activeStack().get(index);
                    CutoffService semanticService = activeStack.get(index);
                    SemanticServiceCoordinates nativeCoordinates = semanticCoordinates.get(
                            nativeService.token());
                    semanticCoordinates.put(nativeService.token(), new SemanticServiceCoordinates(
                            new CutoffCoordinate(semanticService.beginFrame(), semanticService.beginOrdinal()),
                            nativeCoordinates.end(), nativeCoordinates.transitions()));
                }
                List<CutoffService> nativeActive = nativeDiagnostics.activeStack().stream()
                        .map(service -> CutoffService.fromNative(service, servicesByToken,
                                chipsByCoordinate, semanticCoordinates)).toList();
                List<CutoffService> nativePending = nativeDiagnostics.pendingDescendants().stream()
                        .map(service -> CutoffService.fromNative(service, servicesByToken,
                                chipsByCoordinate, semanticCoordinates)).toList();
                if (!activeStack.equals(nativeActive) || !pendingDescendants.equals(nativePending)) {
                    throw new IllegalArgumentException("native cutoff diagnostics do not project canonically");
                }
            }
        }

        public BoundaryFrontier frontier() {
            return new BoundaryFrontier(activeStack, pendingDescendants, rawChipEvents,
                    nativeDiagnostics, ymPort0Latch, ymPort1Latch);
        }

        private static boolean beginsBefore(CutoffService parent, CutoffService child) {
            return parent.beginFrame() < child.beginFrame()
                    || (parent.beginFrame() == child.beginFrame()
                            && parent.beginOrdinal() < child.beginOrdinal());
        }

        private static boolean endsBefore(CutoffService child, CutoffService parent) {
            return child.endFrame() < parent.endFrame()
                    || child.endFrame().equals(parent.endFrame()) && child.endOrdinal() < parent.endOrdinal();
        }

        public static CutoffFrontier empty(NormalizedState terminalState) {
            return new CutoffFrontier(List.of(), List.of(), List.of(), null, 0, 0, terminalState);
        }

        public static CutoffFrontier fromNative(List<FrontierService> active,
                List<FrontierService> pending, List<FrontierOwnedChip> rawChips,
                List<FrontierOwnedSnapshot> rawSnapshots, int ymPort0Latch, int ymPort1Latch,
                long armEpoch, boolean armed, NormalizedState terminalState,
                String terminalStateDigest) {
            CutoffNativeDiagnostics diagnostics = new CutoffNativeDiagnostics(active, pending,
                    rawChips, rawSnapshots, armEpoch, armed, terminalStateDigest);
            Map<Long, FrontierService> servicesByToken = new LinkedHashMap<>();
            java.util.stream.Stream.concat(active.stream(), pending.stream())
                    .forEach(service -> servicesByToken.put(service.token(), service));
            Map<Long, ChipEvent> chipsByCoordinate = semanticChipsByCoordinate(rawChips);
            Map<Long, SemanticServiceCoordinates> semanticCoordinates = semanticServiceCoordinates(active, pending);
            List<CutoffService> semanticActive = active.stream()
                    .map(service -> CutoffService.fromNative(service, servicesByToken, chipsByCoordinate,
                            semanticCoordinates)).toList();
            List<CutoffService> semanticPending = pending.stream()
                    .map(service -> CutoffService.fromNative(service, servicesByToken, chipsByCoordinate,
                            semanticCoordinates)).toList();
            List<ChipEvent> semanticChips = List.copyOf(chipsByCoordinate.values());
            return new CutoffFrontier(semanticActive, semanticPending, semanticChips, diagnostics,
                    ymPort0Latch, ymPort1Latch, terminalState);
        }

        private static Map<Long, ChipEvent> semanticChipsByCoordinate(List<FrontierOwnedChip> rawChips) {
            Map<Long, ChipEvent> result = new LinkedHashMap<>();
            long ordinal = 0;
            for (FrontierOwnedChip owned : rawChips) {
                FrontierChipEvent event = owned.event();
                if (!event.data()) continue;
                ChipEvent semantic = event.eventKind() == 3
                        ? new YmWrite(ordinal++, event.port(), event.register(), event.value())
                        : new PsgWrite(ordinal++, event.value());
                result.put(event.coordinate(), semantic);
            }
            return Collections.unmodifiableMap(result);
        }
    }

    private static void validateSourcePc(String sourceCpu, int pc, String label) {
        requireText(sourceCpu, label + " source CPU");
        if (!(sourceCpu.equals("Z80") && pc >= 0 && pc <= 0xffff)
                && !(sourceCpu.equals("M68K") && pc >= 0 && pc <= 0xffffff)
                && !(sourceCpu.equals("RESET") && pc == 0)) {
            throw new IllegalArgumentException(label + " source and PC are invalid");
        }
    }

    /** One request-independent lifecycle ownership change. */
    public record LifecycleOwnership(HardwareRole role, OwnerRef displacedOwner, OwnerRef finalOwner) {
        public LifecycleOwnership {
            Objects.requireNonNull(role, "lifecycle ownership role");
            Objects.requireNonNull(displacedOwner, "lifecycle displaced owner");
            Objects.requireNonNull(finalOwner, "lifecycle final owner");
        }
    }

    /** Generic request-independent ownership action selected by a lifecycle rule. */
    public enum LifecycleOwnershipAction {
        NONE,
        SAVE_CURRENT,
        RESTORE_SAVED,
        RELEASE_TO_NONE
    }

    /**
     * Profile-owned exact lifecycle kind, field inventory, ownership action, and finite role sets.
     * Finite alternatives model boundaries whose complete affected set depends on prior driver state;
     * validation always matches one whole ordered set and never accepts an arbitrary subset.
     */
    public record LifecycleRule(String kind, List<String> detailFields,
            LifecycleOwnershipAction ownershipAction, List<List<HardwareRole>> ownershipRoleSets) {
        public LifecycleRule(String kind, List<String> detailFields,
                LifecycleOwnershipAction ownershipAction) {
            this(kind, detailFields, ownershipAction,
                    ownershipAction == LifecycleOwnershipAction.NONE ? List.of(List.of()) : List.of());
        }

        public LifecycleRule {
            requireText(kind, "lifecycle rule kind");
            detailFields = List.copyOf(Objects.requireNonNull(detailFields, "lifecycle rule fields"));
            Objects.requireNonNull(ownershipAction, "lifecycle ownership action");
            String previous = null;
            for (String field : detailFields) {
                requireText(field, "lifecycle rule field");
                if (previous != null && field.compareTo(previous) <= 0) {
                    throw new IllegalArgumentException("lifecycle rule fields must be unique and sorted");
                }
                previous = field;
            }
            List<List<HardwareRole>> roleSets = new ArrayList<>();
            for (List<HardwareRole> roles : Objects.requireNonNull(ownershipRoleSets,
                    "lifecycle ownership role sets")) {
                List<HardwareRole> exact = List.copyOf(Objects.requireNonNull(roles,
                        "lifecycle ownership role set"));
                if (!exact.isEmpty()) {
                    exact = canonicalRoles(exact, "lifecycle ownership role set");
                }
                if (roleSets.contains(exact)) {
                    throw new IllegalArgumentException("lifecycle ownership role sets must be unique");
                }
                roleSets.add(exact);
            }
            ownershipRoleSets = List.copyOf(roleSets);
            if (ownershipAction == LifecycleOwnershipAction.NONE) {
                if (!ownershipRoleSets.equals(List.of(List.of()))) {
                    throw new IllegalArgumentException("no-transition lifecycle must declare exactly the empty role set");
                }
            } else if (ownershipRoleSets.isEmpty() || ownershipRoleSets.stream().anyMatch(List::isEmpty)) {
                throw new IllegalArgumentException("ownership lifecycle must declare nonempty exact role sets");
            }
        }
    }

    /** Generic role-owner transition selected by a profile from a decision reason. */
    public enum OwnershipTransition {
        ACQUIRE_REQUEST,
        REJECT_PRESERVE,
        RELEASE_TO_NONE,
        SAVE_AND_ACQUIRE_REQUEST
    }

    /** Profile-declared hard bounds for unresolved request state. */
    public record PendingRequestPolicy(int maximumPending, int maximumAtTerminal,
            String terminalAllowanceReason) {
        public static final int HARD_MAXIMUM_PENDING = 256;

        public PendingRequestPolicy {
            if (maximumPending <= 0 || maximumPending > HARD_MAXIMUM_PENDING
                    || maximumAtTerminal < 0 || maximumAtTerminal > maximumPending) {
                throw new IllegalArgumentException("pending request bounds are invalid");
            }
            if (maximumAtTerminal == 0) {
                if (terminalAllowanceReason != null) {
                    throw new IllegalArgumentException("zero terminal allowance must not carry a rationale");
                }
            } else {
                requireText(terminalAllowanceReason, "terminal pending request allowance rationale");
            }
        }
    }

    /** Exact number of saved owners deliberately permitted for one role at terminal. */
    public record SavedOwnerDepth(HardwareRole role, int depth) {
        public SavedOwnerDepth {
            Objects.requireNonNull(role, "saved-owner role");
            if (depth <= 0) {
                throw new IllegalArgumentException("saved-owner terminal depth must be positive");
            }
        }
    }

    /** Profile-owned per-role stack bound and exact terminal saved-owner inventory. */
    public record RestoreStackPolicy(int maximumDepth, List<SavedOwnerDepth> terminalDepths,
            String terminalAllowanceReason) {
        public static final int HARD_MAXIMUM_DEPTH = 16;

        public RestoreStackPolicy {
            if (maximumDepth < 0 || maximumDepth > HARD_MAXIMUM_DEPTH) {
                throw new IllegalArgumentException("restore stack depth must be between zero and sixteen");
            }
            terminalDepths = List.copyOf(Objects.requireNonNull(terminalDepths,
                    "terminal restore stack depths"));
            HardwareRole previous = null;
            for (SavedOwnerDepth terminalDepth : terminalDepths) {
                Objects.requireNonNull(terminalDepth, "terminal restore stack depth");
                if (previous != null && terminalDepth.role().ordinal() <= previous.ordinal()) {
                    throw new IllegalArgumentException(
                            "terminal restore stack roles must be unique and in canonical order");
                }
                if (terminalDepth.depth() > maximumDepth) {
                    throw new IllegalArgumentException("terminal restore stack depth exceeds its maximum");
                }
                previous = terminalDepth.role();
            }
            if (terminalDepths.isEmpty()) {
                if (terminalAllowanceReason != null) {
                    throw new IllegalArgumentException("empty terminal restore stack must not carry a rationale");
                }
            } else {
                requireText(terminalAllowanceReason, "terminal restore stack allowance rationale");
            }
        }
    }

    /** Full native capability vector reduced to independently pinned digests and literal bounds. */
    public record NativeCapabilitySummary(long eventCount, int maximumFrameOccupancy,
            String eventDigest, String vectorDigest) {
        public NativeCapabilitySummary {
            if (eventCount <= 0 || maximumFrameOccupancy <= 0) {
                throw new IllegalArgumentException("native complete-run capability must be positive");
            }
            lowercaseHex(eventDigest, SHA256, "native event digest");
            lowercaseHex(vectorDigest, SHA256, "native capability-vector digest");
        }
    }

    public record Terminal(int exclusiveEnd, long frameCount, long requestCount, long serviceCount,
            long decisionCount, long ymCount, long psgCount, long lifecycleCount,
            long cutoffActiveCount, long cutoffPendingCount, NativeCapabilitySummary nativeCapability,
            String rootDigest, String semanticDigest)
            implements Record {
        public Terminal(int exclusiveEnd, long frameCount, long requestCount, long serviceCount,
                long decisionCount, long ymCount, long psgCount, long lifecycleCount, String rootDigest) {
            this(exclusiveEnd, frameCount, requestCount, serviceCount, decisionCount, ymCount, psgCount,
                    lifecycleCount, 0, 0, null, rootDigest, rootDigest);
        }
        public Terminal(int exclusiveEnd, long frameCount, long requestCount, long serviceCount,
                long decisionCount, long ymCount, long psgCount, long lifecycleCount,
                long cutoffActiveCount, long cutoffPendingCount, String rootDigest) {
            this(exclusiveEnd, frameCount, requestCount, serviceCount, decisionCount, ymCount, psgCount,
                    lifecycleCount, cutoffActiveCount, cutoffPendingCount, null, rootDigest, rootDigest);
        }
        public Terminal(int exclusiveEnd, long frameCount, long requestCount, long serviceCount,
                long decisionCount, long ymCount, long psgCount, long lifecycleCount,
                long cutoffActiveCount, long cutoffPendingCount, String rootDigest, String semanticDigest) {
            this(exclusiveEnd, frameCount, requestCount, serviceCount, decisionCount, ymCount, psgCount,
                    lifecycleCount, cutoffActiveCount, cutoffPendingCount, null, rootDigest, semanticDigest);
        }
        public Terminal {
            if (exclusiveEnd < 0) {
                throw new IllegalArgumentException("terminal exclusive end must be non-negative");
            }
            nonNegative(frameCount, "terminal frame count");
            nonNegative(requestCount, "terminal request count");
            nonNegative(serviceCount, "terminal service count");
            nonNegative(decisionCount, "terminal decision count");
            nonNegative(ymCount, "terminal YM count");
            nonNegative(psgCount, "terminal PSG count");
            nonNegative(lifecycleCount, "terminal lifecycle count");
            nonNegative(cutoffActiveCount, "terminal cutoff active count");
            nonNegative(cutoffPendingCount, "terminal cutoff pending count");
            lowercaseHex(rootDigest, SHA256, "terminal root digest");
            lowercaseHex(semanticDigest, SHA256, "terminal semantic digest");
            counts().total();
        }

        public CaptureCounts counts() {
            return new CaptureCounts(frameCount, requestCount, serviceCount, decisionCount, ymCount,
                    psgCount, lifecycleCount, cutoffActiveCount, cutoffPendingCount);
        }

        /** Compares terminal declarations with independently accumulated streaming counts. */
        public void validateObservedCounts(CaptureCounts observedCounts) {
            if (!counts().equals(Objects.requireNonNull(observedCounts, "observed capture counts"))) {
                throw new IllegalArgumentException("terminal counts do not match independently observed counts");
            }
        }
    }

    /** Aggregate counts maintained by the store while it streams records, not by the producer. */
    public record CaptureCounts(long frameCount, long requestCount, long serviceCount, long decisionCount,
            long ymCount, long psgCount, long lifecycleCount, long cutoffActiveCount,
            long cutoffPendingCount) {
        public CaptureCounts(long frameCount, long requestCount, long serviceCount, long decisionCount,
                long ymCount, long psgCount, long lifecycleCount) {
            this(frameCount, requestCount, serviceCount, decisionCount, ymCount, psgCount,
                    lifecycleCount, 0, 0);
        }
        public CaptureCounts {
            nonNegative(frameCount, "captured frame count");
            nonNegative(requestCount, "captured request count");
            nonNegative(serviceCount, "captured service count");
            nonNegative(decisionCount, "captured decision count");
            nonNegative(ymCount, "captured YM count");
            nonNegative(psgCount, "captured PSG count");
            nonNegative(lifecycleCount, "captured lifecycle count");
            nonNegative(cutoffActiveCount, "captured cutoff active count");
            nonNegative(cutoffPendingCount, "captured cutoff pending count");
        }

        /** Uses exact arithmetic so an impossible aggregate cannot silently wrap. */
        public long total() {
            long total = Math.addExact(frameCount, requestCount);
            total = Math.addExact(total, serviceCount);
            total = Math.addExact(total, decisionCount);
            total = Math.addExact(total, ymCount);
            total = Math.addExact(total, psgCount);
            total = Math.addExact(total, lifecycleCount);
            total = Math.addExact(total, cutoffActiveCount);
            return Math.addExact(total, cutoffPendingCount);
        }
    }

    /** Raw request identity before a driver accepts, rejects, or transforms it. */
    public record Request(long ordinal, OwnerClass ownerClass, String contentKey, int nativeId,
            String queueSource, Integer queueSlot) {
        public Request {
            nonNegative(ordinal, "request ordinal");
            Objects.requireNonNull(ownerClass, "ownerClass");
            requireText(contentKey, "request content key");
            unsignedByte(nativeId, "request native ID");
            requireText(queueSource, "request queue source");
            if (queueSlot != null && queueSlot < 0) {
                throw new IllegalArgumentException("request queue slot must be non-negative when present");
            }
        }
    }

    /** Profile-facing raw request identity before canonical ROM-backed content resolution. */
    public record RawAudioRequest(OwnerClass ownerClass, int nativeId, String queueSource,
            Integer queueSlot) {
        public RawAudioRequest {
            Objects.requireNonNull(ownerClass, "raw request owner class");
            unsignedByte(nativeId, "raw request native ID");
            requireText(queueSource, "raw request queue source");
            if (queueSlot != null && queueSlot < 0) {
                throw new IllegalArgumentException("raw request queue slot must be non-negative when present");
            }
        }
    }

    /** Canonical native/content identity resolved by a profile from a raw request. */
    public record NativeSoundIdentity(OwnerClass ownerClass, String contentKey, int nativeId) {
        public NativeSoundIdentity {
            Objects.requireNonNull(ownerClass, "native sound owner class");
            requireText(contentKey, "native sound content key");
            unsignedByte(nativeId, "native sound ID");
        }
    }

    /** Driver-side result for one raw request, retaining any per-role ownership changes. */
    public record Decision(long requestOrdinal, int resolvedNativeId, String resolvedContentKey,
            boolean accepted, String reason, Integer priorityBefore, Integer priorityAfter,
            List<HardwareRole> requestedRoles, List<RoleDecision> roleDecisions) {
        public Decision {
            nonNegative(requestOrdinal, "decision request ordinal");
            unsignedByte(resolvedNativeId, "decision resolved native ID");
            requireText(resolvedContentKey, "decision resolved content key");
            requireText(reason, "decision reason");
            requestedRoles = canonicalRoles(requestedRoles, "decision requested roles");
            roleDecisions = List.copyOf(Objects.requireNonNull(roleDecisions, "roleDecisions"));
            if (roleDecisions.size() != requestedRoles.size()) {
                throw new IllegalArgumentException("every requested role requires one role decision");
            }
            for (int index = 0; index < roleDecisions.size(); index++) {
                if (roleDecisions.get(index).role() != requestedRoles.get(index)) {
                    throw new IllegalArgumentException("role decisions must follow requested role order");
                }
            }
        }
    }

    public record RoleDecision(HardwareRole role, OwnerRef displacedOwner, OwnerRef finalOwner) {
        public RoleDecision {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(displacedOwner, "displacedOwner");
            Objects.requireNonNull(finalOwner, "finalOwner");
        }
    }

    /** One hardware role's live owner at the comparison epoch. */
    public record RoleOwner(HardwareRole role, OwnerRef owner) {
        public RoleOwner {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(owner, "owner");
            if (owner.origin() != OwnerOrigin.NONE && owner.origin() != OwnerOrigin.BASELINE) {
                throw new IllegalArgumentException("baseline role owner must use NONE or BASELINE origin");
            }
        }
    }

    /** Ownership includes the originating request so same-native-ID retriggers never collapse. */
    public record OwnerRef(OwnerClass ownerClass, String contentKey, int nativeId,
            OwnerOrigin origin, long originOrdinal) {
        public OwnerRef {
            Objects.requireNonNull(ownerClass, "ownerClass");
            requireText(contentKey, "owner content key");
            unsignedByte(nativeId, "owner native ID");
            Objects.requireNonNull(origin, "owner origin");
            if (ownerClass == OwnerClass.NONE) {
                if (!"none".equals(contentKey) || nativeId != 0 || origin != OwnerOrigin.NONE
                        || originOrdinal != -1) {
                    throw new IllegalArgumentException("none owner must use the canonical none identity");
                }
            } else {
                if (origin == OwnerOrigin.NONE) {
                    throw new IllegalArgumentException("live owner must declare a baseline or request origin");
                }
                nonNegative(originOrdinal, "owner origin ordinal");
            }
        }
    }

    /** Strictly ordered normalized state, with role-local fields held separately from global fields. */
    public record NormalizedState(List<StateField> fields, List<RoleState> roles) {
        public NormalizedState {
            fields = List.copyOf(Objects.requireNonNull(fields, "state fields"));
            roles = List.copyOf(Objects.requireNonNull(roles, "state roles"));
            uniqueNames(fields, "state fields");
            Set<HardwareRole> seenRoles = new LinkedHashSet<>();
            for (RoleState role : roles) {
                if (!seenRoles.add(role.role())) {
                    throw new IllegalArgumentException("state roles must not contain duplicates");
                }
            }
        }
    }

    public record StateField(String name, Object value) {
        public StateField {
            requireText(name, "state field name");
            value = immutableValue(Objects.requireNonNull(value, "state field value"));
        }
    }

    public record RoleState(HardwareRole role, boolean active, List<StateField> fields) {
        public RoleState {
            Objects.requireNonNull(role, "role state role");
            fields = List.copyOf(Objects.requireNonNull(fields, "role state fields"));
            uniqueNames(fields, "role state fields");
        }
    }

    /** Profile-declared canonical global and active-role field inventories. */
    public record StateInventory(List<String> globalFields, List<String> activeRoleFields) {
        public StateInventory {
            globalFields = canonicalNames(globalFields, "global state inventory");
            activeRoleFields = canonicalNames(activeRoleFields, "active-role state inventory");
        }
    }

    public sealed interface ChipEvent permits YmWrite, PsgWrite {
        long ordinal();
    }

    public record YmWrite(long ordinal, int port, int register, int value) implements ChipEvent {
        public YmWrite {
            nonNegative(ordinal, "YM ordinal");
            if (port < 0 || port > 1) {
                throw new IllegalArgumentException("YM port must be zero or one");
            }
            unsignedByte(register, "YM register");
            unsignedByte(value, "YM value");
        }
    }

    public record PsgWrite(long ordinal, int value) implements ChipEvent {
        public PsgWrite {
            nonNegative(ordinal, "PSG ordinal");
            unsignedByte(value, "PSG value");
        }
    }

    static List<HardwareRole> canonicalRoles(List<HardwareRole> roles, String name) {
        roles = List.copyOf(Objects.requireNonNull(roles, name));
        if (roles.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        HardwareRole previous = null;
        for (HardwareRole role : roles) {
            Objects.requireNonNull(role, name + " contains null");
            if (previous != null && role.ordinal() <= previous.ordinal()) {
                throw new IllegalArgumentException(name + " must be unique and in canonical order");
            }
            previous = role;
        }
        return roles;
    }

    private static List<RoleOwner> canonicalRoleOwners(List<RoleOwner> roleOwners, String name) {
        roleOwners = List.copyOf(Objects.requireNonNull(roleOwners, name));
        if (roleOwners.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        HardwareRole previous = null;
        for (RoleOwner roleOwner : roleOwners) {
            HardwareRole role = Objects.requireNonNull(roleOwner, name + " contains null").role();
            if (previous != null && role.ordinal() <= previous.ordinal()) {
                throw new IllegalArgumentException(name + " must be unique and in canonical order");
            }
            previous = role;
        }
        return roleOwners;
    }

    private static List<LifecycleOwnership> canonicalLifecycleOwnership(
            List<LifecycleOwnership> transitions, String name) {
        transitions = List.copyOf(Objects.requireNonNull(transitions, name));
        HardwareRole previous = null;
        for (LifecycleOwnership transition : transitions) {
            HardwareRole role = Objects.requireNonNull(transition, name + " contains null").role();
            if (previous != null && role.ordinal() <= previous.ordinal()) {
                throw new IllegalArgumentException(name + " must be unique and in canonical order");
            }
            previous = role;
        }
        return transitions;
    }

    static List<String> canonicalNames(List<String> names, String name) {
        names = List.copyOf(Objects.requireNonNull(names, name));
        if (names.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String field : names) {
            requireText(field, name + " entry");
            if (!seen.add(field)) {
                throw new IllegalArgumentException(name + " must not contain duplicates");
            }
        }
        return names;
    }

    private static void strictlyIncreasing(List<Long> ordinals, String name) {
        long previous = -1;
        for (Long ordinal : ordinals) {
            if (ordinal == null || ordinal <= previous) {
                throw new IllegalArgumentException(name + " must be strictly increasing");
            }
            previous = ordinal;
        }
    }

    private static void uniqueNames(List<StateField> fields, String name) {
        Set<String> seen = new LinkedHashSet<>();
        for (StateField field : fields) {
            if (!seen.add(field.name())) {
                throw new IllegalArgumentException(name + " must not contain duplicate names");
            }
        }
    }

    private static Map<String, Object> immutableMap(Map<String, Object> map, String name) {
        Objects.requireNonNull(map, name);
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            requireText(entry.getKey(), name + " key");
            copy.put(entry.getKey(), immutableValue(Objects.requireNonNull(entry.getValue(), name + " value")));
        }
        return immutableSortedMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof String || value instanceof Boolean || value instanceof Integer
                || value instanceof Long) {
            return value;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(immutableValue(Objects.requireNonNull(item, "state list value")));
            }
            return List.copyOf(copy);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("state map keys must be strings");
                }
                requireText(key, "state map key");
                copy.put(key, immutableValue(Objects.requireNonNull(entry.getValue(), "state map value")));
            }
            return immutableSortedMap(copy);
        }
        throw new IllegalArgumentException("state values must be canonical JSON-compatible values");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
    }

    private static Map<String, Object> immutableSortedMap(Map<String, Object> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    private static void lowercaseHex(String value, Pattern pattern, String name) {
        requireText(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be canonical lowercase hexadecimal");
        }
    }

    private static void nonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static int compareCoordinate(ServiceCoordinate left, ServiceCoordinate right) {
        int frame = Integer.compare(left.frame(), right.frame());
        return frame != 0 ? frame : Long.compare(left.ordinal(), right.ordinal());
    }

    private static int compareFrameOrdinal(int leftFrame, long leftOrdinal,
            int rightFrame, long rightOrdinal) {
        int frame = Integer.compare(leftFrame, rightFrame);
        return frame != 0 ? frame : Long.compare(leftOrdinal, rightOrdinal);
    }

    private static void validateAncestryLifetime(ServiceAncestry ancestry,
            ServiceCoordinate begin, ServiceCoordinate end, String label) {
        if (ancestry == null || ancestry.transitions().isEmpty()) return;
        if (begin == null || compareCoordinate(begin, ancestry.transitions().getFirst().transitionCoordinate()) >= 0
                || end != null && compareCoordinate(
                        ancestry.transitions().getLast().transitionCoordinate(), end) >= 0) {
            throw new IllegalArgumentException(label + " is outside its owner lifetime");
        }
    }

    private static void unsignedByte(int value, String name) {
        if (value < 0 || value > 0xff) {
            throw new IllegalArgumentException(name + " must be an unsigned byte");
        }
    }
}
