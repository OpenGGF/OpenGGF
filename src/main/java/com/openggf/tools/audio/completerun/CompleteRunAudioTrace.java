package com.openggf.tools.audio.completerun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Immutable tooling-only envelope for one complete-run audio capture.
 *
 * <p>The model deliberately carries no runtime owners or game-specific fields. Profiles provide
 * the strict role and state inventory used to validate a game's records.
 */
public final class CompleteRunAudioTrace {
    public static final String SCHEMA = "complete_run_audio.v1";
    public static final int CHUNK_FRAME_ROWS = 4_096;
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern CRC32 = Pattern.compile("[0-9a-f]{8}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private CompleteRunAudioTrace() {
    }

    public sealed interface Record permits Baseline, Frame, Lifecycle, Terminal {
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
    public enum RuntimeArtifact { BIZHAWK_EXECUTABLE, BIZHAWK_CORE_DLL, GPGX_CORE, OPENGGF_PRODUCER }

    /**
     * Explicit producer runtime identity; observer labels are intentionally not used as a
     * substitute for executable, core, or artifact identity.
     */
    public record ProducerRuntimeIdentity(String producerName, String producerVersion,
            String emulatorName, String emulatorVersion, String coreName, String coreVersion,
            Map<RuntimeArtifact, String> artifactSha256) {
        public ProducerRuntimeIdentity {
            requireText(producerName, "producer name");
            requireText(producerVersion, "producer version");
            requireText(emulatorName, "emulator name");
            requireText(emulatorVersion, "emulator version");
            requireText(coreName, "core name");
            requireText(coreVersion, "core version");
            Objects.requireNonNull(artifactSha256, "runtime artifact SHA-256 values");
            EnumMap<RuntimeArtifact, String> hashes = new EnumMap<>(RuntimeArtifact.class);
            for (Map.Entry<RuntimeArtifact, String> entry : artifactSha256.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "runtime artifact");
                lowercaseHex(entry.getValue(), SHA256, "runtime artifact SHA-256");
                hashes.put(entry.getKey(), entry.getValue());
            }
            artifactSha256 = Collections.unmodifiableMap(hashes);
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
            ObserverProof observerProof, ChunkPolicy chunkPolicy,
            List<HardwareRole> hardwareRoles, StateInventory stateInventory) {
        public Metadata {
            requireText(schema, "schema");
            requireText(profileId, "profileId");
            if (!SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("unknown complete-run audio schema: " + schema);
            }
            Objects.requireNonNull(fixture, "fixture");
            Objects.requireNonNull(producerKind, "producer kind");
            Objects.requireNonNull(producerRuntimeIdentity, "producer runtime identity").validateFor(producerKind);
            Objects.requireNonNull(observerProof, "observer proof");
            Objects.requireNonNull(chunkPolicy, "chunk policy");
            hardwareRoles = canonicalRoles(hardwareRoles, "metadata hardware roles");
            Objects.requireNonNull(stateInventory, "metadata state inventory");
        }

        /** Binds capture metadata to every fixture and inventory selected by its profile. */
        public void validateProfile(CompleteRunAudioProfile profile) {
            Objects.requireNonNull(profile, "profile");
            Map<ProducerKind, ProducerRuntimeIdentity> allowedRuntimeIdentities =
                    Objects.requireNonNull(profile.producerRuntimeIdentities(), "profile runtime identities");
            Map<ProducerKind, ObserverProof> allowedObserverProofs =
                    Objects.requireNonNull(profile.observerProofs(), "profile observer proofs");
            if (!allowedRuntimeIdentities.keySet().containsAll(EnumSet.allOf(ProducerKind.class))) {
                throw new IllegalArgumentException("profile must declare an allowed runtime identity for every producer");
            }
            if (!allowedObserverProofs.keySet().containsAll(EnumSet.allOf(ProducerKind.class))) {
                throw new IllegalArgumentException("profile must declare an observer proof for every producer");
            }
            for (ProducerKind kind : ProducerKind.values()) {
                Objects.requireNonNull(allowedRuntimeIdentities.get(kind), "profile runtime identity").validateFor(kind);
                Objects.requireNonNull(allowedObserverProofs.get(kind), "profile observer proof");
            }
            if (!profileId.equals(profile.id()) || !fixture.equals(profile.fixture())
                    || !hardwareRoles.equals(canonicalRoles(profile.hardwareRoles(), "profile hardware roles"))
                    || !stateInventory.equals(profile.stateInventory())
                    || !producerRuntimeIdentity.equals(allowedRuntimeIdentities.get(producerKind))
                    || !observerProof.equals(allowedObserverProofs.get(producerKind))) {
                throw new IllegalArgumentException("metadata does not match the selected complete-run audio profile");
            }
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

    public record Baseline(int absoluteFrame, NormalizedState state, List<RoleOwner> roleOwners)
            implements Record {
        public Baseline {
            if (absoluteFrame < 0) {
                throw new IllegalArgumentException("baseline frame must be non-negative");
            }
            Objects.requireNonNull(state, "state");
            roleOwners = canonicalRoleOwners(roleOwners, "baseline role owners");
        }
    }

    public record Frame(int absoluteFrame, String segment, boolean lag, List<Request> requests,
            List<DriverService> services) implements Record {
        public Frame {
            if (absoluteFrame < 0) {
                throw new IllegalArgumentException("frame must be non-negative");
            }
            if (segment != null && segment.isBlank()) {
                throw new IllegalArgumentException("segment must be null or non-blank");
            }
            requests = List.copyOf(Objects.requireNonNull(requests, "requests"));
            services = List.copyOf(Objects.requireNonNull(services, "services"));
            strictlyIncreasing(requests.stream().map(Request::ordinal).toList(), "request ordinals");
            strictlyIncreasing(services.stream().map(DriverService::ordinal).toList(), "service ordinals");
        }
    }

    public record DriverService(long ordinal, String kind, List<Decision> decisions,
            NormalizedState state, List<ChipEvent> chipEvents) {
        public DriverService {
            nonNegative(ordinal, "service ordinal");
            requireText(kind, "service kind");
            decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
            Objects.requireNonNull(state, "state");
            chipEvents = List.copyOf(Objects.requireNonNull(chipEvents, "chipEvents"));
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

    public record Terminal(int exclusiveEnd, long frameCount, long requestCount, long serviceCount,
            long decisionCount, long ymCount, long psgCount, long lifecycleCount, String rootDigest)
            implements Record {
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
            lowercaseHex(rootDigest, SHA256, "terminal root digest");
            counts().total();
        }

        public CaptureCounts counts() {
            return new CaptureCounts(frameCount, requestCount, serviceCount, decisionCount, ymCount,
                    psgCount, lifecycleCount);
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
            long ymCount, long psgCount, long lifecycleCount) {
        public CaptureCounts {
            nonNegative(frameCount, "captured frame count");
            nonNegative(requestCount, "captured request count");
            nonNegative(serviceCount, "captured service count");
            nonNegative(decisionCount, "captured decision count");
            nonNegative(ymCount, "captured YM count");
            nonNegative(psgCount, "captured PSG count");
            nonNegative(lifecycleCount, "captured lifecycle count");
        }

        /** Uses exact arithmetic so an impossible aggregate cannot silently wrap. */
        public long total() {
            long total = Math.addExact(frameCount, requestCount);
            total = Math.addExact(total, serviceCount);
            total = Math.addExact(total, decisionCount);
            total = Math.addExact(total, ymCount);
            total = Math.addExact(total, psgCount);
            return Math.addExact(total, lifecycleCount);
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

    private static void unsignedByte(int value, String name) {
        if (value < 0 || value > 0xff) {
            throw new IllegalArgumentException(name + " must be an unsigned byte");
        }
    }
}
