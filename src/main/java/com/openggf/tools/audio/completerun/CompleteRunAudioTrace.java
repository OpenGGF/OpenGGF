package com.openggf.tools.audio.completerun;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable tooling-only envelope for one complete-run audio capture.
 *
 * <p>The model deliberately carries no runtime owners or game-specific fields. Profiles provide
 * the strict role and state inventory used to validate a game's records.
 */
public final class CompleteRunAudioTrace {
    public static final String SCHEMA = "complete_run_audio.v1";

    private CompleteRunAudioTrace() {
    }

    public sealed interface Record permits Baseline, Frame, Lifecycle, Terminal {
    }

    public enum HardwareRole { DAC, FM1, FM2, FM3, FM4, FM5, FM6, PSG1, PSG2, PSG3 }

    public enum OwnerClass { NONE, MUSIC, SFX, SPECIAL_SFX, COMMAND }

    public record Metadata(String schema, String profileId, int firstFrame, int exclusiveEnd,
            List<HardwareRole> hardwareRoles, List<String> stateFieldNames) {
        public Metadata {
            requireText(schema, "schema");
            requireText(profileId, "profileId");
            if (!SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("unknown complete-run audio schema: " + schema);
            }
            if (firstFrame < 0 || exclusiveEnd <= firstFrame) {
                throw new IllegalArgumentException("comparison interval must be a non-empty half-open range");
            }
            hardwareRoles = canonicalRoles(hardwareRoles, "metadata hardware roles");
            stateFieldNames = canonicalNames(stateFieldNames, "metadata state fields");
        }

        /** Verifies the terminal's interval-derived frame count and declared exclusive end. */
        public void validateTerminal(Terminal terminal) {
            Objects.requireNonNull(terminal, "terminal");
            if (terminal.exclusiveEnd() != exclusiveEnd
                    || terminal.frameCount() != (long) exclusiveEnd - firstFrame) {
                throw new IllegalArgumentException("terminal does not match the metadata comparison interval");
            }
        }
    }

    public record Baseline(int absoluteFrame, NormalizedState state) implements Record {
        public Baseline {
            if (absoluteFrame < 0) {
                throw new IllegalArgumentException("baseline frame must be non-negative");
            }
            Objects.requireNonNull(state, "state");
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

    public record Lifecycle(long ordinal, int absoluteFrame, String kind, Map<String, Object> details)
            implements Record {
        public Lifecycle {
            nonNegative(ordinal, "lifecycle ordinal");
            if (absoluteFrame < 0) {
                throw new IllegalArgumentException("lifecycle frame must be non-negative");
            }
            requireText(kind, "lifecycle kind");
            details = immutableMap(details, "lifecycle details");
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
            requireText(rootDigest, "terminal root digest");
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

    /** Ownership includes the originating request so same-native-ID retriggers never collapse. */
    public record OwnerRef(OwnerClass ownerClass, String contentKey, int nativeId, long requestOrdinal) {
        public OwnerRef {
            Objects.requireNonNull(ownerClass, "ownerClass");
            requireText(contentKey, "owner content key");
            unsignedByte(nativeId, "owner native ID");
            if (ownerClass == OwnerClass.NONE) {
                if (!"none".equals(contentKey) || nativeId != 0 || requestOrdinal != -1) {
                    throw new IllegalArgumentException("none owner must use the canonical none identity");
                }
            } else {
                nonNegative(requestOrdinal, "owner request ordinal");
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
        return Map.copyOf(copy);
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
            return Map.copyOf(copy);
        }
        throw new IllegalArgumentException("state values must be canonical JSON-compatible values");
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
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
