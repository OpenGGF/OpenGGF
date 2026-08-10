package com.openggf.tools.audio.completerun;

import static com.openggf.tools.audio.completerun.CompleteRunAudioTrace.*;

import com.openggf.tools.audio.completerun.CompleteRunAudioReport.Context;
import com.openggf.tools.audio.completerun.CompleteRunAudioReport.Kind;
import com.openggf.tools.audio.completerun.CompleteRunAudioReport.RecordView;
import com.openggf.tools.audio.completerun.CompleteRunAudioReport.Side;
import com.openggf.tools.audio.completerun.CompleteRunAudioReport.SourceIdentity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Validation-first, two-pass, no-realignment comparator for canonical complete-run captures. */
public final class CompleteRunAudioComparator {
    private static final int CONTEXT_LIMIT = 8;
    private static final FileIdentityProvider PLATFORM_FILE_IDENTITIES = (path, attributes) -> {
        Object key = attributes.fileKey();
        if (key == null || key.toString().isBlank()) {
            throw new PublicationIdentityUnavailableException(
                    "filesystem publication identity is unavailable for " + path);
        }
        return key.toString();
    };

    private CompleteRunAudioComparator() {
    }

    public static CompleteRunAudioReport compare(Path reference, Path engine) {
        return compare(reference, engine, () -> { }, PLATFORM_FILE_IDENTITIES);
    }

    /** Package-visible seam exercises real source replacement between the validation and comparison passes. */
    static CompleteRunAudioReport compare(Path reference, Path engine, PassBoundary boundary) {
        return compare(reference, engine, boundary, PLATFORM_FILE_IDENTITIES);
    }

    /** Package-visible seam proves that unavailable filesystem identities fail closed. */
    static CompleteRunAudioReport compare(Path reference, Path engine, PassBoundary boundary,
            FileIdentityProvider fileIdentities) {
        Objects.requireNonNull(reference, "reference capture");
        Objects.requireNonNull(engine, "engine capture");
        Objects.requireNonNull(boundary, "pass boundary");
        Objects.requireNonNull(fileIdentities, "file identity provider");
        Path referenceSource = reference.toAbsolutePath().normalize();
        Path engineSource = engine.toAbsolutePath().normalize();
        CompleteRunAudioCaptureStore store = new CompleteRunAudioCaptureStore();
        Snapshot referenceSnapshot = null;
        Snapshot engineSnapshot = null;
        try {
            referenceSnapshot = validate(store, referenceSource, Side.REFERENCE, ProducerKind.REFERENCE,
                    fileIdentities);
            engineSnapshot = validate(store, engineSource, Side.ENGINE, ProducerKind.OPENGGF,
                    fileIdentities);
            boundary.run();
            return compareValidated(store, referenceSnapshot, engineSnapshot, fileIdentities);
        } catch (ValidationException failure) {
            return failure(referenceSnapshot, engineSnapshot, failure, referenceSource, engineSource);
        } catch (IOException failure) {
            return failure(referenceSnapshot, engineSnapshot,
                    new ValidationException(ValidationException.Kind.IO_FAILURE, Side.REFERENCE,
                            "between-pass action failed", failure), referenceSource, engineSource);
        }
    }

    @FunctionalInterface
    interface PassBoundary {
        void run() throws IOException;
    }

    @FunctionalInterface
    interface FileIdentityProvider {
        String fileKey(Path path, BasicFileAttributes attributes) throws IOException;
    }

    /** Package-visible bounded-state evidence for adversarial semantic streams. */
    record ValidationDiagnostics(int peakPendingRequests, int terminalPendingRequests,
            int peakSavedOwners, int liveRoleOwners, long completedRequests) { }

    static ValidationDiagnostics validateSemanticsForDiagnostics(Metadata metadata,
            CompleteRunAudioProfile profile, Side side, java.util.Iterator<CompleteRunAudioTrace.Record> records)
            throws ValidationException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(records, "records");
        try {
            metadata.validateProfile(profile);
        } catch (RuntimeException failure) {
            throw new ValidationException(ValidationException.Kind.METADATA_PROFILE_MISMATCH, side,
                    "diagnostic stream metadata does not match its profile", failure);
        }
        StreamValidator validator = new StreamValidator(metadata, profile, side);
        while (records.hasNext()) validator.accept(records.next());
        validator.finish();
        return validator.diagnostics();
    }

    /** Typed validation categories are the sole classification source for capture failures. */
    public static final class ValidationException extends Exception {
        public enum Kind {
            IO_FAILURE,
            CAPTURE_INVALID,
            PROFILE_UNKNOWN,
            METADATA_PROFILE_MISMATCH,
            PRODUCER_KIND_MISMATCH,
            ORDINAL_INVALID,
            STATE_INVALID,
            REQUEST_IDENTITY_INVALID,
            ROLE_INVALID,
            DECISION_REFERENCE_INVALID,
            RESOLUTION_INVALID,
            OWNER_INVALID,
            OWNERSHIP_TRANSITION_INVALID,
            PENDING_CAPACITY_INVALID,
            PENDING_UNRESOLVED,
            SEGMENT_INVALID,
            LIFECYCLE_INVALID,
            PUBLICATION_IDENTITY_UNAVAILABLE,
            SOURCE_REPLACED
        }

        private final Kind kind;
        private final Side side;

        ValidationException(Kind kind, Side side, String message) {
            super(message);
            this.kind = Objects.requireNonNull(kind, "validation kind");
            this.side = Objects.requireNonNull(side, "validation side");
        }

        ValidationException(Kind kind, Side side, String message, Throwable cause) {
            super(message, cause);
            this.kind = Objects.requireNonNull(kind, "validation kind");
            this.side = Objects.requireNonNull(side, "validation side");
        }

        public Kind kind() { return kind; }
        public Side side() { return side; }
    }

    private static Snapshot validate(CompleteRunAudioCaptureStore store, Path source, Side side,
            ProducerKind expectedProducer, FileIdentityProvider fileIdentities) throws ValidationException {
        Path normalized = source.toAbsolutePath().normalize();
        try {
            PublicationIdentity publication = PublicationIdentity.capture(normalized, fileIdentities);
            Metadata metadata;
            Terminal terminal = null;
            try (CompleteRunAudioCaptureStore.Reader reader = store.read(publication.realSource())) {
                metadata = reader.metadata();
                CompleteRunAudioProfile profile = validateMetadata(metadata, expectedProducer, side);
                StreamValidator validator = new StreamValidator(metadata, profile, side);
                while (reader.hasNext()) {
                    CompleteRunAudioTrace.Record record = reader.next();
                    validator.accept(record);
                    if (record instanceof Terminal value) terminal = value;
                }
                validator.finish();
            }
            if (!publication.equals(PublicationIdentity.capture(normalized, fileIdentities))) {
                throw new ValidationException(ValidationException.Kind.SOURCE_REPLACED, side,
                        "capture publication changed during validation pass");
            }
            if (terminal == null) {
                throw new ValidationException(ValidationException.Kind.CAPTURE_INVALID, side,
                        "validated capture has no terminal record");
            }
            return new Snapshot(normalized, metadata, terminal.rootDigest(), publication,
                    sha256(CompleteRunAudioJson.writeMetadata(metadata).getBytes(StandardCharsets.UTF_8)));
        } catch (ValidationException failure) {
            throw failure;
        } catch (PublicationIdentityUnavailableException failure) {
            throw new ValidationException(ValidationException.Kind.PUBLICATION_IDENTITY_UNAVAILABLE, side,
                    failure.getMessage(), failure);
        } catch (IOException failure) {
            throw new ValidationException(ValidationException.Kind.IO_FAILURE, side,
                    "capture could not be read", failure);
        } catch (RuntimeException failure) {
            throw new ValidationException(ValidationException.Kind.CAPTURE_INVALID, side,
                    "capture failed strict store validation", failure);
        }
    }

    private static CompleteRunAudioProfile validateMetadata(Metadata metadata,
            ProducerKind expectedProducer, Side side) throws ValidationException {
        if (metadata.producerKind() != expectedProducer) {
            throw new ValidationException(ValidationException.Kind.PRODUCER_KIND_MISMATCH, side,
                    "capture is assigned to the wrong comparison side");
        }
        CompleteRunAudioProfile profile;
        try {
            profile = CompleteRunAudioProfiles.require(metadata.profileId());
        } catch (RuntimeException failure) {
            throw new ValidationException(ValidationException.Kind.PROFILE_UNKNOWN, side,
                    "capture profile is not registered", failure);
        }
        try {
            metadata.validateProfile(profile);
        } catch (RuntimeException failure) {
            throw new ValidationException(ValidationException.Kind.METADATA_PROFILE_MISMATCH, side,
                    "capture metadata is not the exact registered profile identity", failure);
        }
        return profile;
    }

    private static CompleteRunAudioReport compareValidated(CompleteRunAudioCaptureStore store,
            Snapshot reference, Snapshot engine, FileIdentityProvider fileIdentities)
            throws ValidationException {
        Difference first = metadataDifference(reference.metadata, engine.metadata);
        ContextCollector context = new ContextCollector(first != null);
        try (PassStream left = PassStream.open(store, reference, Side.REFERENCE, ProducerKind.REFERENCE,
                    fileIdentities);
                PassStream right = PassStream.open(store, engine, Side.ENGINE, ProducerKind.OPENGGF,
                    fileIdentities)) {
            while (true) {
                Entry expected = left.next();
                Entry actual = right.next();
                if (expected == null && actual == null) break;
                RecordView expectedView = view(expected);
                RecordView actualView = view(actual);
                if (first == null) {
                    Difference found = difference(expected == null ? null : expected.record,
                            actual == null ? null : actual.record);
                    if (found == null) {
                        context.before(expectedView, actualView);
                    } else {
                        first = found;
                        context.mismatch(expectedView, actualView);
                    }
                } else {
                    context.after(expectedView, actualView);
                }
            }
            left.verifyStable(fileIdentities);
            right.verifyStable(fileIdentities);
        } catch (ValidationException failure) {
            throw failure;
        }
        SourceIdentity referenceIdentity = reference.identity(Side.REFERENCE);
        SourceIdentity engineIdentity = engine.identity(Side.ENGINE);
        if (first == null) {
            Context empty = emptyContext();
            return new CompleteRunAudioReport(Kind.MATCH, referenceIdentity, engineIdentity, -1,
                    null, null, null, empty, empty, null, null, null, null);
        }
        return new CompleteRunAudioReport(first.kind, referenceIdentity, engineIdentity, first.frame,
                first.location, first.referenceValue, first.engineValue, context.reference(),
                context.engine(), null, null, null, null);
    }

    private static CompleteRunAudioReport failure(Snapshot reference, Snapshot engine,
            ValidationException failure, Path referenceSource, Path engineSource) {
        Context empty = emptyContext();
        String source = (failure.side() == Side.REFERENCE ? referenceSource : engineSource).toString();
        return new CompleteRunAudioReport(Kind.CAPTURE_FAILURE,
                reference == null ? null : reference.identity(Side.REFERENCE),
                engine == null ? null : engine.identity(Side.ENGINE), -1, null, null, null,
                empty, empty, failure.side(), source, failure.kind(), failure.getMessage());
    }

    private static Context emptyContext() {
        return new Context(List.of(), null, List.of());
    }

    private static Difference metadataDifference(Metadata reference, Metadata engine) {
        if (!reference.schema().equals(engine.schema())) {
            return diff(Kind.METADATA_IDENTITY, -1, "metadata.schema", reference.schema(), engine.schema());
        }
        if (!reference.profileId().equals(engine.profileId())) {
            return diff(Kind.METADATA_IDENTITY, -1, "metadata.profile_id",
                    reference.profileId(), engine.profileId());
        }
        if (!reference.fixture().equals(engine.fixture())) {
            return diff(Kind.METADATA_IDENTITY, -1, "metadata.fixture",
                    reference.fixture(), engine.fixture());
        }
        if (!reference.chunkPolicy().equals(engine.chunkPolicy())) {
            return diff(Kind.METADATA_IDENTITY, -1, "metadata.chunk_policy",
                    reference.chunkPolicy(), engine.chunkPolicy());
        }
        if (!reference.hardwareRoles().equals(engine.hardwareRoles())) {
            return diff(Kind.METADATA_IDENTITY, -1, "metadata.hardware_roles",
                    reference.hardwareRoles(), engine.hardwareRoles());
        }
        if (!reference.stateInventory().equals(engine.stateInventory())) {
            return diff(Kind.METADATA_IDENTITY, -1, "metadata.state_inventory",
                    reference.stateInventory(), engine.stateInventory());
        }
        return null;
    }

    /** Pure record classifier used by the streaming pass and defensive impossible-state tests. */
    static Difference difference(CompleteRunAudioTrace.Record reference,
            CompleteRunAudioTrace.Record engine) {
        if (reference == null || engine == null) return missingRecord(reference, engine);
        if (reference instanceof Baseline expected && engine instanceof Baseline actual) {
            if (expected.absoluteFrame() != actual.absoluteFrame()) {
                return frameCoordinate(expected.absoluteFrame(), actual.absoluteFrame(), "baseline.absolute_frame");
            }
            Difference state = stateDifference(expected.state(), actual.state(), expected.absoluteFrame(),
                    "baseline.state");
            if (state != null) return state;
            if (!expected.roleOwners().equals(actual.roleOwners())) {
                return diff(Kind.OWNER, expected.absoluteFrame(), "baseline.role_owners",
                        expected.roleOwners(), actual.roleOwners());
            }
            return null;
        }
        if (reference instanceof Frame expected && engine instanceof Frame actual) {
            return frameDifference(expected, actual);
        }
        if (reference instanceof Lifecycle expected && engine instanceof Lifecycle actual) {
            if (expected.ordinal() != actual.ordinal()) {
                return diff(Kind.LIFECYCLE_ORDER, Math.min(expected.absoluteFrame(), actual.absoluteFrame()),
                        "lifecycle.ordinal", expected.ordinal(), actual.ordinal());
            }
            if (expected.absoluteFrame() != actual.absoluteFrame() || !expected.kind().equals(actual.kind())
                    || !expected.details().equals(actual.details())) {
                return diff(Kind.LIFECYCLE_VALUE, Math.min(expected.absoluteFrame(), actual.absoluteFrame()),
                        "lifecycle[" + expected.ordinal() + "]", expected, actual);
            }
            return null;
        }
        if (reference instanceof Terminal expected && engine instanceof Terminal actual) {
            if (expected.exclusiveEnd() != actual.exclusiveEnd()
                    || expected.frameCount() != actual.frameCount()
                    || expected.requestCount() != actual.requestCount()
                    || expected.serviceCount() != actual.serviceCount()
                    || expected.decisionCount() != actual.decisionCount()
                    || expected.ymCount() != actual.ymCount()
                    || expected.psgCount() != actual.psgCount()
                    || expected.lifecycleCount() != actual.lifecycleCount()) {
                return diff(Kind.TERMINAL_COUNT, Math.min(expected.exclusiveEnd(), actual.exclusiveEnd()),
                        "terminal.counts", expected.counts(), actual.counts());
            }
            return null; // Root digests bind source stability; prior records own semantic differences.
        }
        if (reference instanceof Lifecycle lifecycle) {
            return diff(Kind.LIFECYCLE_MISSING, lifecycle.absoluteFrame(), "lifecycle",
                    lifecycle, engine);
        }
        if (engine instanceof Lifecycle lifecycle) {
            return diff(Kind.LIFECYCLE_EXTRA, lifecycle.absoluteFrame(), "lifecycle",
                    reference, lifecycle);
        }
        if (reference instanceof Frame frame) {
            return diff(Kind.FRAME_MISSING, frame.absoluteFrame(), "frame", frame, engine);
        }
        if (engine instanceof Frame frame) {
            return diff(Kind.FRAME_EXTRA, frame.absoluteFrame(), "frame", reference, frame);
        }
        return diff(Kind.RECORD_SHAPE, recordFrame(reference, engine), "record.type",
                reference.getClass().getSimpleName(), engine.getClass().getSimpleName());
    }

    private static Difference missingRecord(CompleteRunAudioTrace.Record reference,
            CompleteRunAudioTrace.Record engine) {
        CompleteRunAudioTrace.Record present = reference == null ? engine : reference;
        boolean extra = reference == null;
        if (present instanceof Frame frame) {
            return diff(extra ? Kind.FRAME_EXTRA : Kind.FRAME_MISSING, frame.absoluteFrame(), "frame",
                    reference, engine);
        }
        if (present instanceof Lifecycle lifecycle) {
            return diff(extra ? Kind.LIFECYCLE_EXTRA : Kind.LIFECYCLE_MISSING, lifecycle.absoluteFrame(),
                    "lifecycle", reference, engine);
        }
        return diff(Kind.RECORD_SHAPE, recordFrame(reference, engine), "record.eof", reference, engine);
    }

    private static Difference frameDifference(Frame reference, Frame engine) {
        if (reference.absoluteFrame() != engine.absoluteFrame()) {
            return frameCoordinate(reference.absoluteFrame(), engine.absoluteFrame(), "frame.absolute_frame");
        }
        int frame = reference.absoluteFrame();
        if (!Objects.equals(reference.segment(), engine.segment()) || reference.lag() != engine.lag()) {
            return diff(Kind.FRAME_VALUE, frame, "frame.coordinates",
                    new FrameCoordinatesPayload(reference.segment(), reference.lag()),
                    new FrameCoordinatesPayload(engine.segment(), engine.lag()));
        }
        Difference requests = requestDifference(reference.requests(), engine.requests(), frame);
        if (requests != null) return requests;
        return serviceDifference(reference.services(), engine.services(), frame);
    }

    private static Difference frameCoordinate(int reference, int engine, String location) {
        return diff(reference < engine ? Kind.FRAME_MISSING : Kind.FRAME_EXTRA,
                Math.min(reference, engine), location, reference, engine);
    }

    private static Difference requestDifference(List<Request> reference, List<Request> engine, int frame) {
        if (reference.size() != engine.size()) {
            int index = commonPrefix(reference, engine);
            return diff(reference.size() > engine.size() ? Kind.REQUEST_MISSING : Kind.REQUEST_EXTRA,
                    frame, "frame.requests[" + index + "]", at(reference, index), at(engine, index));
        }
        if (isPermutation(reference, engine, CompleteRunAudioComparator::requestPayload)) {
            return diff(Kind.REQUEST_ORDER, frame, "frame.requests", reference, engine);
        }
        for (int index = 0; index < reference.size(); index++) {
            Request expected = reference.get(index);
            Request actual = engine.get(index);
            if (expected.ordinal() != actual.ordinal()) {
                return diff(Kind.REQUEST_ORDER, frame, "frame.requests[" + index + "].ordinal",
                        expected.ordinal(), actual.ordinal());
            }
            if (!requestPayload(expected).equals(requestPayload(actual))) {
                return diff(Kind.REQUEST_VALUE, frame, "frame.requests[" + index + "]", expected, actual);
            }
        }
        return null;
    }

    private static Difference serviceDifference(List<DriverService> reference,
            List<DriverService> engine, int frame) {
        if (reference.size() != engine.size()) {
            int index = commonPrefix(reference, engine);
            return diff(reference.size() > engine.size() ? Kind.SERVICE_MISSING : Kind.SERVICE_EXTRA,
                    frame, "frame.services[" + index + "]", at(reference, index), at(engine, index));
        }
        if (isPermutation(reference, engine, CompleteRunAudioComparator::servicePayload)) {
            return diff(Kind.SERVICE_ORDER, frame, "frame.services", reference, engine);
        }
        for (int index = 0; index < reference.size(); index++) {
            DriverService expected = reference.get(index);
            DriverService actual = engine.get(index);
            String location = "frame.services[" + index + "]";
            if (expected.ordinal() != actual.ordinal()) {
                return diff(Kind.SERVICE_ORDER, frame, location + ".ordinal",
                        expected.ordinal(), actual.ordinal());
            }
            if (!expected.kind().equals(actual.kind())) {
                return diff(Kind.SERVICE_VALUE, frame, location + ".kind", expected.kind(), actual.kind());
            }
            Difference decisions = decisionDifference(expected.decisions(), actual.decisions(), frame,
                    location + ".decisions");
            if (decisions != null) return decisions;
            Difference state = stateDifference(expected.state(), actual.state(), frame, location + ".state");
            if (state != null) return state;
            Difference chips = chipDifference(expected.chipEvents(), actual.chipEvents(), frame,
                    location + ".chip_events");
            if (chips != null) return chips;
        }
        return null;
    }

    private static Difference decisionDifference(List<Decision> reference, List<Decision> engine,
            int frame, String location) {
        if (reference.size() != engine.size()) {
            int index = commonPrefix(reference, engine);
            return diff(reference.size() > engine.size() ? Kind.DECISION_MISSING : Kind.DECISION_EXTRA,
                    frame, location + "[" + index + "]", at(reference, index), at(engine, index));
        }
        if (isPermutation(reference, engine, CompleteRunAudioComparator::decisionOrderPayload)) {
            return diff(Kind.DECISION_ORDER, frame, location, reference, engine);
        }
        for (int index = 0; index < reference.size(); index++) {
            Decision expected = reference.get(index);
            Decision actual = engine.get(index);
            String item = location + "[" + index + "]";
            if (expected.requestOrdinal() != actual.requestOrdinal()) {
                return diff(Kind.DECISION_ORDER, frame, item + ".request_ordinal",
                        expected.requestOrdinal(), actual.requestOrdinal());
            }
            if (!Objects.equals(expected.priorityBefore(), actual.priorityBefore())
                    || !Objects.equals(expected.priorityAfter(), actual.priorityAfter())) {
                return diff(Kind.PRIORITY, frame, item + ".priority",
                        new PriorityPayload(expected.priorityBefore(), expected.priorityAfter()),
                        new PriorityPayload(actual.priorityBefore(), actual.priorityAfter()));
            }
            if (expected.roleDecisions().size() == actual.roleDecisions().size()) {
                for (int role = 0; role < expected.roleDecisions().size(); role++) {
                    RoleDecision expectedRole = expected.roleDecisions().get(role);
                    RoleDecision actualRole = actual.roleDecisions().get(role);
                    if (!expectedRole.displacedOwner().equals(actualRole.displacedOwner())
                            || !expectedRole.finalOwner().equals(actualRole.finalOwner())) {
                        return diff(Kind.OWNER, frame, item + ".roles[" + role + "].owner",
                                expectedRole, actualRole);
                    }
                }
            }
            if (!decisionPayload(expected).equals(decisionPayload(actual))) {
                return diff(Kind.DECISION_VALUE, frame, item, expected, actual);
            }
        }
        return null;
    }

    private static Difference chipDifference(List<ChipEvent> reference, List<ChipEvent> engine,
            int frame, String location) {
        if (reference.size() != engine.size()) {
            int index = commonPrefix(reference, engine);
            return diff(reference.size() > engine.size() ? Kind.CHIP_EVENT_MISSING : Kind.CHIP_EVENT_EXTRA,
                    frame, location + "[" + index + "]", at(reference, index), at(engine, index));
        }
        if (isPermutation(reference, engine, CompleteRunAudioComparator::chipPayload)) {
            return diff(Kind.CHIP_EVENT_ORDER, frame, location, reference, engine);
        }
        for (int index = 0; index < reference.size(); index++) {
            ChipEvent expected = reference.get(index);
            ChipEvent actual = engine.get(index);
            if (expected.ordinal() != actual.ordinal()) {
                return diff(Kind.CHIP_EVENT_ORDER, frame, location + "[" + index + "].ordinal",
                        expected.ordinal(), actual.ordinal());
            }
            if (!chipPayload(expected).equals(chipPayload(actual))) {
                return diff(Kind.CHIP_EVENT_VALUE, frame, location + "[" + index + "]", expected, actual);
            }
        }
        return null;
    }

    private static Difference stateDifference(NormalizedState reference, NormalizedState engine,
            int frame, String location) {
        Difference global = fieldsDifference(reference.fields(), engine.fields(), frame, location + ".fields");
        if (global != null) return global;
        if (reference.roles().size() != engine.roles().size()) {
            return diff(Kind.STATE_FIELD_NAME, frame, location + ".roles.size",
                    reference.roles().size(), engine.roles().size());
        }
        for (int index = 0; index < reference.roles().size(); index++) {
            RoleState expected = reference.roles().get(index);
            RoleState actual = engine.roles().get(index);
            String role = location + ".roles[" + index + "]";
            if (expected.role() != actual.role()) {
                return diff(Kind.STATE_FIELD_NAME, frame, role + ".role", expected.role(), actual.role());
            }
            if (expected.active() != actual.active()) {
                return diff(Kind.STATE_FIELD_VALUE, frame, role + ".active", expected.active(), actual.active());
            }
            Difference fields = fieldsDifference(expected.fields(), actual.fields(), frame, role + ".fields");
            if (fields != null) return fields;
        }
        return null;
    }

    private static Difference fieldsDifference(List<StateField> reference, List<StateField> engine,
            int frame, String location) {
        if (reference.size() != engine.size()) {
            return diff(Kind.STATE_FIELD_NAME, frame, location + ".size", reference.size(), engine.size());
        }
        for (int index = 0; index < reference.size(); index++) {
            StateField expected = reference.get(index);
            StateField actual = engine.get(index);
            if (!expected.name().equals(actual.name())) {
                return diff(Kind.STATE_FIELD_NAME, frame, location + "[" + index + "].name",
                        expected.name(), actual.name());
            }
            if (!expected.value().equals(actual.value())) {
                return diff(Kind.STATE_FIELD_VALUE, frame, location + "[" + index + "].value",
                        expected.value(), actual.value());
            }
        }
        return null;
    }

    private static RequestPayload requestPayload(Request request) {
        return new RequestPayload(request.ownerClass(), request.contentKey(), request.nativeId(),
                request.queueSource(), request.queueSlot());
    }

    private static ServicePayload servicePayload(DriverService service) {
        return new ServicePayload(service.kind(), service.decisions().stream()
                .map(CompleteRunAudioComparator::decisionOrderPayload).toList(), service.state(),
                service.chipEvents().stream().map(CompleteRunAudioComparator::chipPayload).toList());
    }

    private static DecisionPayload decisionPayload(Decision decision) {
        return new DecisionPayload(decision.resolvedNativeId(), decision.resolvedContentKey(),
                decision.accepted(), decision.reason(), decision.priorityBefore(), decision.priorityAfter(),
                decision.requestedRoles(), decision.roleDecisions());
    }

    private static DecisionOrderPayload decisionOrderPayload(Decision decision) {
        return new DecisionOrderPayload(decision.resolvedNativeId(), decision.resolvedContentKey(),
                decision.accepted(), decision.reason(), decision.priorityBefore(), decision.priorityAfter(),
                decision.requestedRoles(), decision.roleDecisions().stream()
                        .map(role -> new RoleOrderPayload(role.role(), ownerPayload(role.displacedOwner()),
                                ownerPayload(role.finalOwner())))
                        .toList());
    }

    private static OwnerPayload ownerPayload(OwnerRef owner) {
        return new OwnerPayload(owner.ownerClass(), owner.contentKey(), owner.nativeId(), owner.origin());
    }

    private static Object chipPayload(ChipEvent event) {
        if (event instanceof YmWrite ym) return new YmPayload(ym.port(), ym.register(), ym.value());
        PsgWrite psg = (PsgWrite) event;
        return new PsgPayload(psg.value());
    }

    private static <T, P> boolean isPermutation(List<T> reference, List<T> engine,
            Function<T, P> payload) {
        if (reference.size() < 2 || reference.size() != engine.size()) return false;
        List<P> expected = reference.stream().map(payload).toList();
        List<P> actual = engine.stream().map(payload).toList();
        return !expected.equals(actual) && frequencies(expected).equals(frequencies(actual));
    }

    private static <T> Map<T, Integer> frequencies(List<T> values) {
        Map<T, Integer> counts = new HashMap<>();
        for (T value : values) counts.merge(value, 1, Integer::sum);
        return counts;
    }

    private static int commonPrefix(List<?> reference, List<?> engine) {
        int limit = Math.min(reference.size(), engine.size());
        int index = 0;
        while (index < limit && reference.get(index).equals(engine.get(index))) index++;
        return index;
    }

    private static Object at(List<?> values, int index) {
        return index < values.size() ? values.get(index) : null;
    }

    private static Difference diff(Kind kind, int frame, String location,
            Object reference, Object engine) {
        return new Difference(kind, frame, location, describe(reference), describe(engine));
    }

    private static String describe(Object value) {
        return value == null ? "<absent>" : value.toString();
    }

    private static int recordFrame(CompleteRunAudioTrace.Record reference,
            CompleteRunAudioTrace.Record engine) {
        CompleteRunAudioTrace.Record[] records = {reference, engine};
        for (CompleteRunAudioTrace.Record record : records) {
            if (record instanceof Frame frame) return frame.absoluteFrame();
            if (record instanceof Lifecycle lifecycle) return lifecycle.absoluteFrame();
            if (record instanceof Baseline baseline) return baseline.absoluteFrame();
            if (record instanceof Terminal terminal) return terminal.exclusiveEnd();
        }
        return -1;
    }

    private static RecordView view(Entry entry) {
        if (entry == null) return null;
        try {
            return new RecordView(entry.index, recordFrame(entry.record, entry.record),
                    entry.record.getClass().getSimpleName().toLowerCase(),
                    CompleteRunAudioJson.writeRecord(entry.record));
        } catch (IOException failure) {
            throw new AssertionError("validated record could not be canonicalized", failure);
        }
    }

    record Difference(Kind kind, int frame, String location, String referenceValue,
            String engineValue) {
        Difference {
            Objects.requireNonNull(kind, "difference kind");
            Objects.requireNonNull(location, "difference location");
            Objects.requireNonNull(referenceValue, "reference value");
            Objects.requireNonNull(engineValue, "engine value");
        }
    }

    private record RequestPayload(OwnerClass ownerClass, String contentKey, int nativeId,
            String queueSource, Integer queueSlot) { }
    private record ServicePayload(String kind, List<DecisionOrderPayload> decisions, NormalizedState state,
            List<Object> chips) { }
    private record DecisionPayload(int nativeId, String contentKey, boolean accepted, String reason,
            Integer priorityBefore, Integer priorityAfter, List<HardwareRole> requestedRoles,
            List<RoleDecision> roles) { }
    private record DecisionOrderPayload(int nativeId, String contentKey, boolean accepted, String reason,
            Integer priorityBefore, Integer priorityAfter, List<HardwareRole> requestedRoles,
            List<RoleOrderPayload> roles) { }
    private record RoleOrderPayload(HardwareRole role, OwnerPayload displaced, OwnerPayload finalOwner) { }
    private record OwnerPayload(OwnerClass ownerClass, String contentKey, int nativeId,
            OwnerOrigin origin) { }
    private record YmPayload(int port, int register, int value) { }
    private record PsgPayload(int value) { }
    private record FrameCoordinatesPayload(String segment, boolean lag) { }
    private record PriorityPayload(Integer before, Integer after) { }

    private record Snapshot(Path source, Metadata metadata, String rootDigest,
            PublicationIdentity publication, String metadataSha256) {
        private SourceIdentity identity(Side side) {
            return new SourceIdentity(side, source.toString(), publication.realSource().toString(), metadata,
                    metadataSha256, rootDigest, publication.manifestSha256(), publication.digest(),
                    publication.sourceFileKey(), publication.chunks());
        }
    }

    private record PublicationIdentity(Path realSource, String sourceFileKey, String manifestSha256,
            List<CompleteRunAudioReport.ChunkIdentity> chunks, String digest) {
        private PublicationIdentity {
            Objects.requireNonNull(realSource, "resolved source");
            Objects.requireNonNull(sourceFileKey, "source file key");
            Objects.requireNonNull(manifestSha256, "manifest SHA-256");
            chunks = List.copyOf(chunks);
            Objects.requireNonNull(digest, "publication digest");
        }

        static PublicationIdentity capture(Path source, FileIdentityProvider fileIdentities)
                throws IOException {
            Path realSource = source.toRealPath();
            BasicFileAttributes sourceAttributes = Files.readAttributes(realSource, BasicFileAttributes.class);
            if (!sourceAttributes.isDirectory()) {
                throw new IOException("capture publication is not a directory: " + source);
            }
            String sourceFileKey = requireFileKey(realSource,
                    fileIdentities.fileKey(realSource, sourceAttributes));
            String manifestSha256 = sha256(realSource.resolve("manifest.json"));
            Path chunksDirectory = realSource.resolve("chunks");
            List<Path> paths;
            try (var listed = Files.list(chunksDirectory)) {
                paths = listed.sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .limit(CompleteRunAudioCaptureStore.MAX_CAPTURE_CHUNKS + 1L).toList();
            }
            if (paths.isEmpty() || paths.size() > CompleteRunAudioCaptureStore.MAX_CAPTURE_CHUNKS) {
                throw new IOException("capture publication has an invalid chunk count");
            }
            List<CompleteRunAudioReport.ChunkIdentity> chunks = new ArrayList<>(paths.size());
            for (Path path : paths) {
                Path realPath = path.toRealPath();
                BasicFileAttributes attributes = Files.readAttributes(realPath, BasicFileAttributes.class);
                if (!attributes.isRegularFile()) {
                    throw new IOException("capture chunk is not a regular file: " + path);
                }
                chunks.add(new CompleteRunAudioReport.ChunkIdentity(path.getFileName().toString(),
                        sha256(realPath), realPath.toString(), requireFileKey(realPath,
                                fileIdentities.fileKey(realPath, attributes))));
            }
            MessageDigest digest = sha256Digest();
            digestField(digest, realSource.toString());
            digestField(digest, sourceFileKey);
            digestField(digest, manifestSha256);
            for (CompleteRunAudioReport.ChunkIdentity chunk : chunks) {
                digestField(digest, chunk.file());
                digestField(digest, chunk.compressedSha256());
                digestField(digest, chunk.realPath());
                digestField(digest, chunk.fileKey());
            }
            return new PublicationIdentity(realSource, sourceFileKey, manifestSha256, chunks,
                    HexFormat.of().formatHex(digest.digest()));
        }
    }

    private static String requireFileKey(Path path, String fileKey)
            throws PublicationIdentityUnavailableException {
        if (fileKey == null || fileKey.isBlank()) {
            throw new PublicationIdentityUnavailableException(
                    "filesystem publication identity is unavailable for " + path);
        }
        return fileKey;
    }

    private static final class PublicationIdentityUnavailableException extends IOException {
        private PublicationIdentityUnavailableException(String message) {
            super(message);
        }
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(byte[] bytes) {
        MessageDigest digest = sha256Digest();
        digest.update(bytes);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void digestField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private record Entry(long index, CompleteRunAudioTrace.Record record) { }

    private static final class PassStream implements AutoCloseable {
        private final CompleteRunAudioCaptureStore.Reader reader;
        private final Snapshot expected;
        private final Side side;
        private final StreamValidator validator;
        private long index;
        private Terminal terminal;
        private boolean finished;

        private PassStream(CompleteRunAudioCaptureStore.Reader reader, Snapshot expected, Side side,
                StreamValidator validator) {
            this.reader = reader;
            this.expected = expected;
            this.side = side;
            this.validator = validator;
        }

        static PassStream open(CompleteRunAudioCaptureStore store, Snapshot expected, Side side,
                ProducerKind producer, FileIdentityProvider fileIdentities) throws ValidationException {
            CompleteRunAudioCaptureStore.Reader reader;
            try {
                PublicationIdentity current = PublicationIdentity.capture(expected.source, fileIdentities);
                if (!current.equals(expected.publication)) {
                    throw new ValidationException(ValidationException.Kind.SOURCE_REPLACED, side,
                            "capture publication changed between passes");
                }
                reader = store.read(current.realSource());
            } catch (PublicationIdentityUnavailableException failure) {
                throw new ValidationException(ValidationException.Kind.PUBLICATION_IDENTITY_UNAVAILABLE, side,
                        failure.getMessage(), failure);
            } catch (IOException failure) {
                throw new ValidationException(ValidationException.Kind.IO_FAILURE, side,
                        "comparison pass could not reopen capture", failure);
            } catch (RuntimeException failure) {
                throw new ValidationException(ValidationException.Kind.CAPTURE_INVALID, side,
                        "comparison pass could not parse capture metadata", failure);
            }
            try {
                Metadata metadata = reader.metadata();
                if (!metadata.equals(expected.metadata)) {
                    reader.close();
                    throw new ValidationException(ValidationException.Kind.SOURCE_REPLACED, side,
                            "capture metadata changed between passes");
                }
                CompleteRunAudioProfile profile = validateMetadata(metadata, producer, side);
                return new PassStream(reader, expected, side, new StreamValidator(metadata, profile, side));
            } catch (ValidationException failure) {
                try { reader.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            } catch (IOException failure) {
                throw new ValidationException(ValidationException.Kind.IO_FAILURE, side,
                        "comparison pass could not close changed capture", failure);
            }
        }

        Entry next() throws ValidationException {
            if (finished) return null;
            try {
                if (!reader.hasNext()) {
                    validator.finish();
                    finished = true;
                    return null;
                }
                CompleteRunAudioTrace.Record record = reader.next();
                validator.accept(record);
                if (record instanceof Terminal value) terminal = value;
                return new Entry(index++, record);
            } catch (ValidationException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new ValidationException(ValidationException.Kind.CAPTURE_INVALID, side,
                        "capture no longer passes strict validation in comparison pass", failure);
            }
        }

        void verifyStable(FileIdentityProvider fileIdentities) throws ValidationException {
            if (!finished || terminal == null || !terminal.rootDigest().equals(expected.rootDigest)) {
                throw new ValidationException(ValidationException.Kind.SOURCE_REPLACED, side,
                        "capture root digest changed between passes");
            }
            try {
                if (!PublicationIdentity.capture(expected.source, fileIdentities).equals(expected.publication)) {
                    throw new ValidationException(ValidationException.Kind.SOURCE_REPLACED, side,
                            "capture publication changed during comparison pass");
                }
            } catch (PublicationIdentityUnavailableException failure) {
                throw new ValidationException(ValidationException.Kind.PUBLICATION_IDENTITY_UNAVAILABLE, side,
                        failure.getMessage(), failure);
            } catch (IOException failure) {
                throw new ValidationException(ValidationException.Kind.IO_FAILURE, side,
                        "capture publication could not be verified after comparison", failure);
            }
        }

        @Override
        public void close() throws ValidationException {
            try {
                reader.close();
            } catch (IOException failure) {
                throw new ValidationException(ValidationException.Kind.IO_FAILURE, side,
                        "comparison pass could not close capture", failure);
            }
        }
    }

    private static final class StreamValidator {
        private final Metadata metadata;
        private final CompleteRunAudioProfile profile;
        private final Side side;
        private final Set<HardwareRole> hardwareRoles;
        private final Map<NativeSoundIdentity, List<NativeSoundIdentity>> decisionResolutions;
        private final Map<String, OwnershipTransition> ownershipTransitions;
        private final PendingRequestPolicy pendingPolicy;
        private final Map<Long, NativeSoundIdentity> pendingRequests = new LinkedHashMap<>();
        private final Map<HardwareRole, OwnerRef> liveOwners = new EnumMap<>(HardwareRole.class);
        private final Map<HardwareRole, ArrayDeque<OwnerRef>> savedOwners =
                new EnumMap<>(HardwareRole.class);
        private long requestOrdinal;
        private long serviceOrdinal;
        private long chipOrdinal;
        private long lifecycleOrdinal;
        private int segmentIndex;
        private int previousSourceFrame = -1;
        private int peakPendingRequests;
        private int peakSavedOwners;
        private long completedRequests;
        private boolean stateObservationRequired;
        private boolean baseline;
        private boolean terminal;

        private StreamValidator(Metadata metadata, CompleteRunAudioProfile profile, Side side) {
            this.metadata = metadata;
            this.profile = profile;
            this.side = side;
            hardwareRoles = Set.copyOf(profile.hardwareRoles());
            decisionResolutions = profile.decisionResolutions();
            ownershipTransitions = profile.ownershipTransitions();
            pendingPolicy = profile.pendingRequestPolicy();
        }

        void accept(CompleteRunAudioTrace.Record record) throws ValidationException {
            if (terminal) ordinal("record follows terminal");
            if (record instanceof Baseline value) {
                if (baseline || value.absoluteFrame() != metadata.fixture().firstFrame()) {
                    ordinal("baseline is not the unique comparison-epoch baseline");
                }
                baseline = true;
                previousSourceFrame = value.absoluteFrame();
                baselineOwners(value);
                state(value.state());
            } else if (record instanceof Frame frame) {
                if (!baseline) ordinal("frame precedes baseline");
                sourceCoordinate(frame.absoluteFrame());
                segment(frame);
                for (Request request : frame.requests()) {
                    if (request.ordinal() != requestOrdinal++) ordinal("request ordinal is not globally contiguous");
                    if (pendingRequests.size() == pendingPolicy.maximumPending()) {
                        throw new ValidationException(ValidationException.Kind.PENDING_CAPACITY_INVALID, side,
                                "unresolved requests exceed the profile-owned bound");
                    }
                    pendingRequests.put(request.ordinal(), requestIdentity(request));
                    peakPendingRequests = Math.max(peakPendingRequests, pendingRequests.size());
                }
                for (DriverService service : frame.services()) {
                    if (service.ordinal() != serviceOrdinal++) ordinal("service ordinal is not globally contiguous");
                    for (Decision decision : service.decisions()) {
                        decision(decision);
                    }
                    state(service.state());
                    for (ChipEvent event : service.chipEvents()) {
                        if (event.ordinal() != chipOrdinal++) ordinal("chip-event ordinal is not globally contiguous");
                    }
                }
            } else if (record instanceof Lifecycle lifecycle) {
                if (!baseline || lifecycle.ordinal() != lifecycleOrdinal++) {
                    ordinal("lifecycle ordinal is not globally contiguous after baseline");
                }
                lifecycle(lifecycle);
            } else if (record instanceof Terminal) {
                if (pendingRequests.size() > pendingPolicy.maximumAtTerminal()) {
                    throw new ValidationException(ValidationException.Kind.PENDING_UNRESOLVED, side,
                            "capture terminates with unresolved requests outside the profile allowance");
                }
                if (stateObservationRequired) {
                    throw new ValidationException(ValidationException.Kind.STATE_INVALID, side,
                            "capture terminates before a lifecycle ownership change is observed in state");
                }
                terminal = true;
            }
        }

        void finish() throws ValidationException {
            if (!baseline || !terminal) ordinal("capture ended without baseline or terminal");
        }

        private void state(NormalizedState state) throws ValidationException {
            try {
                profile.validateState(state);
            } catch (RuntimeException failure) {
                throw new ValidationException(ValidationException.Kind.STATE_INVALID, side,
                        "normalized state does not match the exact profile inventory", failure);
            }
            for (RoleState role : state.roles()) {
                OwnerRef owner = liveOwners.get(role.role());
                if (owner == null || role.active() != (owner.origin() != OwnerOrigin.NONE)) {
                    throw new ValidationException(ValidationException.Kind.STATE_INVALID, side,
                            "normalized role activity does not match its exact live owner");
                }
            }
            stateObservationRequired = false;
        }

        private void baselineOwners(Baseline value) throws ValidationException {
            if (!value.roleOwners().equals(profile.baselineRoleOwners())) {
                throw new ValidationException(ValidationException.Kind.OWNER_INVALID, side,
                        "baseline role owners do not match the exact profile baseline");
            }
            for (RoleOwner roleOwner : value.roleOwners()) {
                liveOwners.put(roleOwner.role(), roleOwner.owner());
                savedOwners.put(roleOwner.role(), new ArrayDeque<>());
            }
        }

        private void segment(Frame frame) throws ValidationException {
            List<ManifestSegment> segments = metadata.fixture().segments();
            while (segmentIndex < segments.size()
                    && frame.absoluteFrame() >= segments.get(segmentIndex).exclusiveEnd()) {
                segmentIndex++;
            }
            String expected = null;
            if (segmentIndex < segments.size()) {
                ManifestSegment segment = segments.get(segmentIndex);
                if (frame.absoluteFrame() >= segment.firstFrame()) expected = segment.id();
            }
            if (!Objects.equals(expected, frame.segment())) {
                throw new ValidationException(ValidationException.Kind.SEGMENT_INVALID, side,
                        "frame segment does not exactly match the fixture interval");
            }
        }

        private void lifecycle(Lifecycle lifecycle) throws ValidationException {
            if (lifecycle.absoluteFrame() < metadata.fixture().firstFrame()
                    || lifecycle.absoluteFrame() >= metadata.fixture().exclusiveEnd()
                    || lifecycle.absoluteFrame() < previousSourceFrame) {
                throw new ValidationException(ValidationException.Kind.LIFECYCLE_INVALID, side,
                        "lifecycle coordinates are outside or regress within the fixture interval");
            }
            try {
                profile.validateLifecycle(lifecycle);
            } catch (RuntimeException failure) {
                throw new ValidationException(ValidationException.Kind.LIFECYCLE_INVALID, side,
                        "lifecycle does not match the exact profile rule", failure);
            }
            LifecycleOwnershipAction action = profile.lifecycleRules().get(lifecycle.kind()).ownershipAction();
            boolean changed = false;
            for (LifecycleOwnership ownership : lifecycle.ownershipTransitions()) {
                changed |= lifecycleTransition(ownership, action);
            }
            stateObservationRequired |= changed;
            previousSourceFrame = lifecycle.absoluteFrame();
        }

        private boolean lifecycleTransition(LifecycleOwnership transition,
                LifecycleOwnershipAction action) throws ValidationException {
            OwnerRef current = liveOwners.get(transition.role());
            if (current == null || !current.equals(transition.displacedOwner())) {
                throw new ValidationException(ValidationException.Kind.OWNER_INVALID, side,
                        "lifecycle displaced owner is not the role's current live owner");
            }
            ArrayDeque<OwnerRef> saved = savedOwners.get(transition.role());
            OwnerRef expectedFinal = switch (action) {
                case SAVE_CURRENT -> current;
                case RESTORE_SAVED -> {
                    if (saved.isEmpty()) {
                        throw new ValidationException(ValidationException.Kind.OWNER_INVALID, side,
                                "lifecycle restore has no saved owner");
                    }
                    yield saved.peekLast();
                }
                case RELEASE_TO_NONE -> noneOwner();
                case NONE -> throw new ValidationException(ValidationException.Kind.LIFECYCLE_INVALID, side,
                        "no-transition lifecycle carries ownership changes");
            };
            if (!expectedFinal.equals(transition.finalOwner())) {
                throw new ValidationException(ValidationException.Kind.OWNER_INVALID, side,
                        "lifecycle final owner does not match the profile-owned action");
            }
            if (action == LifecycleOwnershipAction.SAVE_CURRENT) {
                if (saved.size() == profile.maximumRestoreDepth()) {
                    throw new ValidationException(ValidationException.Kind.OWNER_INVALID, side,
                            "saved owner stack exceeds the profile-owned bound");
                }
                saved.addLast(current);
            }
            if (action == LifecycleOwnershipAction.RESTORE_SAVED) saved.removeLast();
            liveOwners.put(transition.role(), expectedFinal);
            peakSavedOwners = Math.max(peakSavedOwners,
                    savedOwners.values().stream().mapToInt(ArrayDeque::size).sum());
            return !current.equals(expectedFinal);
        }

        private void sourceCoordinate(int absoluteFrame) throws ValidationException {
            if (absoluteFrame < previousSourceFrame) {
                throw new ValidationException(ValidationException.Kind.LIFECYCLE_INVALID, side,
                        "frame coordinates regress behind a lifecycle marker in source order");
            }
            previousSourceFrame = absoluteFrame;
        }

        private NativeSoundIdentity requestIdentity(Request request) throws ValidationException {
            try {
                NativeSoundIdentity expected = profile.resolveRequest(new RawAudioRequest(
                        request.ownerClass(), request.nativeId(), request.queueSource(), request.queueSlot()));
                if (expected.ownerClass() != request.ownerClass()
                        || expected.nativeId() != request.nativeId()
                        || !expected.contentKey().equals(request.contentKey())) {
                    throw new IllegalArgumentException("resolved request identity differs");
                }
                return expected;
            } catch (RuntimeException failure) {
                throw new ValidationException(ValidationException.Kind.REQUEST_IDENTITY_INVALID, side,
                        "request does not match the exact profile-resolved native identity", failure);
            }
        }

        private void decision(Decision decision) throws ValidationException {
            for (HardwareRole role : decision.requestedRoles()) {
                if (!hardwareRoles.contains(role)) {
                    throw new ValidationException(ValidationException.Kind.ROLE_INVALID, side,
                            "decision requests a role outside the profile hardware inventory");
                }
            }
            for (RoleDecision role : decision.roleDecisions()) {
                if (!hardwareRoles.contains(role.role())) {
                    throw new ValidationException(ValidationException.Kind.ROLE_INVALID, side,
                            "role decision is outside the profile hardware inventory");
                }
            }
            NativeSoundIdentity requested = pendingRequests.get(decision.requestOrdinal());
            if (requested == null) {
                throw new ValidationException(ValidationException.Kind.DECISION_REFERENCE_INVALID, side,
                        "decision does not reference one unique captured request");
            }
            NativeSoundIdentity resolved = new NativeSoundIdentity(requested.ownerClass(),
                    decision.resolvedContentKey(), decision.resolvedNativeId());
            List<NativeSoundIdentity> allowed = decisionResolutions.get(requested);
            if (allowed == null || !allowed.contains(resolved)) {
                throw new ValidationException(ValidationException.Kind.RESOLUTION_INVALID, side,
                        "decision resolution is outside the profile-owned transformation contract");
            }
            OwnershipTransition transition = ownershipTransitions.get(decision.reason());
            if (transition == null || decision.accepted() != (transition != OwnershipTransition.REJECT_PRESERVE)) {
                throw new ValidationException(ValidationException.Kind.OWNERSHIP_TRANSITION_INVALID, side,
                        "decision acceptance and reason do not select an exact profile transition");
            }
            OwnerRef admitted = new OwnerRef(resolved.ownerClass(), resolved.contentKey(), resolved.nativeId(),
                    OwnerOrigin.REQUEST, decision.requestOrdinal());
            for (RoleDecision role : decision.roleDecisions()) {
                transition(role, admitted, transition);
            }
            pendingRequests.remove(decision.requestOrdinal());
            completedRequests++;
        }

        private void transition(RoleDecision decision, OwnerRef admitted,
                OwnershipTransition transition) throws ValidationException {
            OwnerRef current = liveOwners.get(decision.role());
            if (current == null || !current.equals(decision.displacedOwner())) {
                throw new ValidationException(ValidationException.Kind.OWNER_INVALID, side,
                        "displaced owner is not the role's current live owner");
            }
            ArrayDeque<OwnerRef> saved = savedOwners.get(decision.role());
            OwnerRef expectedFinal = switch (transition) {
                case ACQUIRE_REQUEST -> admitted;
                case REJECT_PRESERVE -> current;
                case RELEASE_TO_NONE -> noneOwner();
                case SAVE_AND_ACQUIRE_REQUEST -> {
                    if (saved.size() == profile.maximumRestoreDepth()) {
                        throw new ValidationException(ValidationException.Kind.OWNER_INVALID, side,
                                "saved owner stack exceeds the profile-owned bound");
                    }
                    yield admitted;
                }
            };
            if (!expectedFinal.equals(decision.finalOwner())) {
                throw new ValidationException(ValidationException.Kind.OWNER_INVALID, side,
                        "final owner does not match the profile-owned transition");
            }
            if (transition == OwnershipTransition.SAVE_AND_ACQUIRE_REQUEST) saved.addLast(current);
            liveOwners.put(decision.role(), expectedFinal);
            peakSavedOwners = Math.max(peakSavedOwners,
                    savedOwners.values().stream().mapToInt(ArrayDeque::size).sum());
        }

        private static OwnerRef noneOwner() {
            return new OwnerRef(OwnerClass.NONE, "none", 0, OwnerOrigin.NONE, -1);
        }

        private ValidationDiagnostics diagnostics() {
            return new ValidationDiagnostics(peakPendingRequests, pendingRequests.size(), peakSavedOwners,
                    liveOwners.size(), completedRequests);
        }

        private void ordinal(String message) throws ValidationException {
            throw new ValidationException(ValidationException.Kind.ORDINAL_INVALID, side, message);
        }
    }

    private static final class ContextCollector {
        private final ArrayDeque<RecordView> referenceBefore = new ArrayDeque<>();
        private final ArrayDeque<RecordView> engineBefore = new ArrayDeque<>();
        private final List<RecordView> referenceAfter = new ArrayList<>(CONTEXT_LIMIT);
        private final List<RecordView> engineAfter = new ArrayList<>(CONTEXT_LIMIT);
        private RecordView referenceCurrent;
        private RecordView engineCurrent;
        private boolean mismatched;

        private ContextCollector(boolean metadataMismatch) {
            mismatched = metadataMismatch;
        }

        void before(RecordView reference, RecordView engine) {
            appendBounded(referenceBefore, reference);
            appendBounded(engineBefore, engine);
        }

        void mismatch(RecordView reference, RecordView engine) {
            mismatched = true;
            referenceCurrent = reference;
            engineCurrent = engine;
        }

        void after(RecordView reference, RecordView engine) {
            if (!mismatched) throw new IllegalStateException("context has no mismatch");
            appendAfter(referenceAfter, reference);
            appendAfter(engineAfter, engine);
        }

        Context reference() {
            return new Context(List.copyOf(referenceBefore), referenceCurrent, List.copyOf(referenceAfter));
        }

        Context engine() {
            return new Context(List.copyOf(engineBefore), engineCurrent, List.copyOf(engineAfter));
        }

        private static void appendBounded(ArrayDeque<RecordView> values, RecordView value) {
            if (value == null) return;
            if (values.size() == CONTEXT_LIMIT) values.removeFirst();
            values.addLast(value);
        }

        private static void appendAfter(List<RecordView> values, RecordView value) {
            if (value != null && values.size() < CONTEXT_LIMIT) values.add(value);
        }
    }
}
