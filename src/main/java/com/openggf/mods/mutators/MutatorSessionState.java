package com.openggf.mods.mutators;

import com.openggf.mods.code.ModFaultBoundary;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One gameplay session's configuration authority. All calls belong to the session
 * thread. Candidate admission and policy preparation finish before one publication;
 * no callback can access world state through this contract.
 */
public final class MutatorSessionState implements AutoCloseable {
    private static final int MAX_CONFIGURATION_EVENTS = 1024;
    private final long generation;
    private final Map<String, OwnedMutator> definitions;
    private final Set<MutatorCapability> supported;
    private final ModFaultBoundary faults;
    private final Predicate<String> ownerAvailable;
    /** Saved player preferences; deliberately never restored by rewind. */
    private final Map<String, Configuration> requested = new LinkedHashMap<>();
    /** Historical configuration events awaiting a boundary, separate from saved preferences. */
    private Map<String, Configuration> targets;
    private Map<String, Configuration> admitted;
    private Effective effective = new Effective(0, Map.of());
    /** Immutable publications, keyed by the forward tick they precede; retained across rewind. */
    private final java.util.NavigableMap<Long, RecordedRevision> events = new java.util.TreeMap<>();
    private long tick;
    private long nextRevision = 1;
    private boolean closed;
    private boolean aborted;
    private boolean launchAdmitted;

    public MutatorSessionState(long generation, List<OwnedMutator> contributions,
                               Set<MutatorCapability> supported, ModFaultBoundary faults,
                               Predicate<String> ownerAvailable) {
        this(generation, contributions, supported, faults, ownerAvailable, Map.of());
    }

    public MutatorSessionState(long generation, List<OwnedMutator> contributions,
                               Set<MutatorCapability> supported, ModFaultBoundary faults,
                               Predicate<String> ownerAvailable,
                               Map<String, Configuration> preferences) {
        if (generation < 1) throw new IllegalArgumentException("Session generation must be positive");
        this.generation = generation;
        this.supported = Set.copyOf(supported);
        this.faults = Objects.requireNonNull(faults, "faults");
        this.ownerAvailable = Objects.requireNonNull(ownerAvailable, "ownerAvailable");
        if (contributions.size() > 128) throw new IllegalArgumentException("Too many mutators in a session");
        Map<String, OwnedMutator> catalog = new LinkedHashMap<>();
        for (OwnedMutator contribution : contributions) {
            if (catalog.putIfAbsent(contribution.key(), contribution) != null) {
                throw new IllegalArgumentException("Duplicate mutator: " + contribution.key());
            }
        }
        validateCatalog(catalog);
        definitions = frozen(catalog);
        Map<String, Configuration> initial = new LinkedHashMap<>();
        definitions.forEach((key, owned) -> {
            Configuration defaults = new Configuration(false, owned.definition().defaults());
            initial.put(key, defaults);
            Configuration preference = preferences.getOrDefault(key, defaults);
            validateConfiguration(owned.definition(), preference);
            requested.put(key, preference);
        });
        for (String key : preferences.keySet()) {
            if (!definitions.containsKey(key)) throw new IllegalArgumentException("Unknown mutator preference: " + key);
        }
        admitted = frozen(initial);
        targets = frozen(requested);
    }

    public long generation() { return generation; }
    public List<OwnedMutator> definitions() { return List.copyOf(definitions.values()); }
    public Map<String, Configuration> requested() { return frozen(requested); }
    public Map<String, Configuration> admitted() { return admitted; }

    public void requestEnabled(String key, boolean enabled) {
        requireOpen();
        requireDefinition(key);
        branchHistory();
        Configuration saved = requested.get(key);
        requested.put(key, new Configuration(enabled, saved.options()));
        Configuration historical = targets.get(key);
        replaceTarget(key, new Configuration(enabled, historical.options()));
    }

    public void requestOption(String key, String optionId, Object value) {
        requireOpen();
        MutatorDefinition definition = requireDefinition(key).definition();
        definition.option(optionId).validate(value);
        branchHistory();
        requested.put(key, withOption(requested.get(key), optionId, value));
        replaceTarget(key, withOption(targets.get(key), optionId, value));
    }

    /** Reset is an explicit edit; each default still waits for its declared boundary. */
    public void resetDefaults() {
        requireOpen();
        branchHistory();
        definitions.forEach((key, owned) -> {
            Configuration defaults = new Configuration(false, owned.definition().defaults());
            requested.put(key, defaults);
            replaceTarget(key, defaults);
        });
    }

    /** LIVE means explicit configuration Resume; LAUNCH means opening this new session. */
    public Admission boundary(MutatorScope scope) {
        if (scope == MutatorScope.LOAD) throw new IllegalArgumentException("LOAD requires an explicit cause");
        return boundary(generation, scope, null);
    }

    public Admission boundary(MutatorScope scope, LoadCause cause) {
        return boundary(generation, scope, cause);
    }

    public Admission boundary(long expectedGeneration, MutatorScope scope, LoadCause cause) {
        String invalid = validateBoundary(expectedGeneration, scope, cause);
        if (invalid != null) return new Admission(false, effective.revision(), invalid, pending());
        Map<String, Configuration> candidate = candidate(scope);
        invalid = validateCandidate(candidate, scope);
        if (invalid != null) return new Admission(false, effective.revision(), invalid, pending());
        if (candidate.equals(admitted)) {
            if (scope == MutatorScope.LAUNCH) launchAdmitted = true;
            recordEvent(false);
            return new Admission(true, effective.revision(), "No changes due", pending());
        }
        Map<String, List<MutatorPolicy>> policies = new LinkedHashMap<>(effective.policies());
        try {
            for (Map.Entry<String, OwnedMutator> entry : definitions.entrySet()) {
                String key = entry.getKey();
                OwnedMutator owned = entry.getValue();
                Configuration config = candidate.get(key);
                if (!config.enabled()) {
                    policies.remove(key);
                } else if (!config.equals(admitted.get(key))) {
                    List<MutatorPolicy> prepared = faults.call(owned.ownerModId(), () -> {
                        List<MutatorPolicy> result = List.copyOf(owned.definition().factory()
                                .prepare(new MutatorOptions(config.options())));
                        if (result.isEmpty() || result.size() > MutatorCapability.values().length
                                || result.stream().map(MutatorPolicy::capability).distinct().count() != result.size()
                                || !result.stream().map(MutatorPolicy::capability)
                                .collect(java.util.stream.Collectors.toSet()).equals(owned.definition().capabilities())) {
                            throw new IllegalArgumentException("Policy result does not match declared capabilities");
                        }
                        return result;
                    });
                    policies.put(key, prepared);
                }
            }
        } catch (ModFaultBoundary.CallbackAborted failure) {
            aborted = true;
            throw failure;
        }
        // No state above this line was published. Immutable policy and admission change together.
        Effective published = new Effective(nextRevision++, policies);
        admitted = frozen(candidate);
        effective = published;
        if (scope == MutatorScope.LAUNCH) launchAdmitted = true;
        recordEvent(true);
        return new Admission(true, published.revision(), "Applied", pending());
    }

    /** Host menu preflight. Does not run policy factories, publish state or consume LAUNCH. */
    public Admission previewBoundary(MutatorScope scope, LoadCause cause) {
        String invalid = validateBoundary(generation, scope, cause);
        if (invalid == null) invalid = validateCandidate(candidate(scope), scope);
        return new Admission(invalid == null, effective.revision(),
                invalid == null ? "Configuration is eligible" : invalid, pending());
    }

    private String validateBoundary(long expectedGeneration, MutatorScope scope, LoadCause cause) {
        requireOpen();
        if (expectedGeneration != generation) throw new IllegalStateException("Stale mutator session generation");
        Objects.requireNonNull(scope, "scope");
        if (scope == MutatorScope.LAUNCH && (launchAdmitted || tick != 0)) {
            throw new IllegalStateException("LAUNCH requires a new game session");
        }
        if (scope == MutatorScope.LOAD && (cause == null || !cause.qualifies())) {
            return "This load does not admit configuration";
        }
        if (scope != MutatorScope.LOAD && cause != null) throw new IllegalArgumentException("Load cause requires LOAD");
        return null;
    }

    private Map<String, Configuration> candidate(MutatorScope scope) {
        Map<String, Configuration> candidate = new LinkedHashMap<>();
        definitions.forEach((key, owned) -> {
            MutatorDefinition definition = owned.definition();
            Configuration old = admitted.get(key);
            Configuration target = targets.get(key);
            Map<String, Object> values = new LinkedHashMap<>(old.options());
            definition.options().forEach(option -> {
                if (scope.admits(definition.optionScope(option.id()))) {
                    values.put(option.id(), target.options().get(option.id()));
                }
            });
            MutatorScope actionScope = target.enabled() ? definition.enableScope() : definition.disableScope();
            candidate.put(key, new Configuration(scope.admits(actionScope) ? target.enabled() : old.enabled(), values));
        });
        return candidate;
    }

    private String validateCandidate(Map<String, Configuration> candidate, MutatorScope scope) {
        String invalid = validateGraph(candidate, scope);
        if (invalid != null) return invalid;
        if (!events.containsKey(tick + 1) && events.size() >= MAX_CONFIGURATION_EVENTS) {
            return "Configuration history limit reached; start a new session";
        }
        return null;
    }

    /**
     * Called exactly once before each forward simulation tick. Historical configuration
     * events replay without invoking creators or reading present-day saved preferences.
     */
    public void onForwardTick() {
        requireOpen();
        RecordedRevision event = events.get(++tick);
        if (event != null) {
            requireAvailable(event.effective());
            admitted = event.admitted();
            targets = event.targets();
            effective = event.effective();
        }
        requireAvailable(effective);
    }

    /** Pure scheduling preview: historical policies precede their next native tick.
     * Zero-tick presentations may inspect this repeatedly without moving the journal
     * cursor, running creators, admitting saved preferences or publishing policies. */
    Effective effectiveBeforeNextForwardTick() {
        requireOpen();
        RecordedRevision event = events.get(tick + 1);
        Effective next = event == null ? effective : event.effective();
        requireAvailable(next);
        return next;
    }

    public Effective effective() {
        requireOpen();
        requireAvailable(effective);
        return effective;
    }

    public List<Pending> pending() {
        List<Pending> result = new ArrayList<>();
        definitions.forEach((key, owned) -> {
            Configuration saved = requested.get(key);
            Configuration current = admitted.get(key);
            Configuration historical = targets.get(key);
            MutatorDefinition definition = owned.definition();
            if (saved.enabled() != current.enabled()) {
                result.add(new Pending(key, null, saved.enabled() ? definition.enableScope() : definition.disableScope(),
                        saved.enabled() != historical.enabled() ? "Saved choice differs from restored history; edit to apply"
                                : "Waiting for " + (saved.enabled() ? definition.enableScope() : definition.disableScope())));
            }
            definition.options().forEach(option -> {
                Object value = saved.options().get(option.id());
                if (!value.equals(current.options().get(option.id()))) {
                    result.add(new Pending(key, option.id(), definition.optionScope(option.id()),
                            !value.equals(historical.options().get(option.id()))
                                    ? "Saved choice differs from restored history; edit to apply"
                                    : "Waiting for " + definition.optionScope(option.id())));
                }
            });
        });
        return List.copyOf(result);
    }

    public Snapshot snapshot() {
        requireOpen();
        requireAvailable(effective);
        Map<String, Integer> versions = new LinkedHashMap<>();
        definitions.forEach((key, owned) -> versions.put(key, owned.definition().schemaVersion()));
        return new Snapshot(generation, tick, versions, admitted, targets, effective);
    }

    /** Restore before gameplay recreation. Host quarantine is never rolled back. */
    public void restore(Snapshot snapshot) {
        requireOpen();
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.generation() != generation || !snapshot.admitted().keySet().equals(definitions.keySet())
                || !snapshot.targets().keySet().equals(definitions.keySet())
                || !snapshot.schemaVersions().keySet().equals(definitions.keySet())) {
            throw new IllegalArgumentException("Snapshot belongs to a different session/catalog");
        }
        definitions.forEach((key, owned) -> {
            if (snapshot.schemaVersions().get(key) != owned.definition().schemaVersion()) {
                throw new IllegalArgumentException("Snapshot schema mismatch");
            }
            validateConfiguration(owned.definition(), snapshot.admitted().get(key));
            validateConfiguration(owned.definition(), snapshot.targets().get(key));
        });
        requireAvailable(snapshot.effective());
        admitted = snapshot.admitted();
        targets = snapshot.targets();
        effective = snapshot.effective();
        tick = snapshot.tick();
        nextRevision = Math.max(nextRevision, effective.revision() + 1);
    }

    @Override public void close() { closed = true; }
    public boolean isClosed() { return closed; }

    private String validateGraph(Map<String, Configuration> candidate, MutatorScope boundary) {
        // Effective prerequisite disablement cascades only at a boundary safe for every dependent.
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, OwnedMutator> entry : definitions.entrySet()) {
                String key = entry.getKey();
                MutatorDefinition definition = entry.getValue().definition();
                if (!candidate.get(key).enabled()) continue;
                if (!ownerAvailable.test(entry.getValue().ownerModId())) return "Owner is unavailable: " + key;
                if (!supported.containsAll(definition.capabilities())) return "Capability unavailable: " + key;
                for (String prerequisite : definition.prerequisites()) {
                    String required = entry.getValue().ownerModId() + ":" + prerequisite;
                    if (!candidate.get(required).enabled()) {
                        if (!admitted.get(key).enabled()) return "Enable prerequisite first: " + required;
                        if (!boundary.admits(definition.disableScope())) return "Dependent disable waits for " + definition.disableScope();
                        Configuration value = candidate.get(key);
                        candidate.put(key, new Configuration(false, value.options()));
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
        for (Map.Entry<String, OwnedMutator> entry : definitions.entrySet()) {
            if (!candidate.get(entry.getKey()).enabled()) continue;
            for (String conflict : entry.getValue().definition().conflicts()) {
                if (candidate.get(entry.getValue().ownerModId() + ":" + conflict).enabled()) {
                    return "Conflicting mutators: " + entry.getKey() + " and " + conflict;
                }
            }
        }
        return null;
    }

    public static void validateCatalog(Map<String, OwnedMutator> catalog) {
        for (OwnedMutator owned : catalog.values()) {
            for (String local : union(owned.definition().prerequisites(), owned.definition().conflicts())) {
                if (!catalog.containsKey(owned.ownerModId() + ":" + local)) {
                    throw new IllegalArgumentException("Unknown mutator dependency/conflict: " + local);
                }
            }
            visit(owned.key(), catalog, new java.util.HashSet<>(), new java.util.HashSet<>());
        }
    }

    private static void visit(String key, Map<String, OwnedMutator> catalog, Set<String> path, Set<String> done) {
        if (done.contains(key)) return;
        if (!path.add(key)) throw new IllegalArgumentException("Mutator prerequisite cycle");
        OwnedMutator owned = catalog.get(key);
        for (String local : owned.definition().prerequisites()) visit(owned.ownerModId() + ":" + local, catalog, path, done);
        path.remove(key);
        done.add(key);
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new java.util.HashSet<>(first);
        result.addAll(second);
        return result;
    }

    private static void validateConfiguration(MutatorDefinition definition, Configuration value) {
        if (!value.options().keySet().equals(definition.defaults().keySet())) {
            throw new IllegalArgumentException("Unknown or missing options for " + definition.localId());
        }
        definition.options().forEach(option -> option.validate(value.options().get(option.id())));
    }

    private void requireAvailable(Effective value) {
        for (String key : value.policies().keySet()) {
            if (!definitions.containsKey(key) || !ownerAvailable.test(definitions.get(key).ownerModId())) {
                aborted = true;
                throw new IllegalStateException("Historical policy owner is quarantined; start a new session");
            }
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Mutator session is closed");
        if (aborted) throw new IllegalStateException("Mutator session aborted; start a new session");
    }

    private OwnedMutator requireDefinition(String key) {
        OwnedMutator owned = definitions.get(key);
        if (owned == null) throw new IllegalArgumentException("Unknown mutator: " + key);
        return owned;
    }

    private void replaceTarget(String key, Configuration value) {
        Map<String, Configuration> next = new LinkedHashMap<>(targets);
        next.put(key, value);
        targets = frozen(next);
    }

    private void branchHistory() { events.tailMap(tick + 1, true).clear(); }

    private void recordEvent(boolean published) {
        RecordedRevision event = new RecordedRevision(admitted, targets, effective);
        if (published) {
            events.put(tick + 1, event);
            return;
        }
        // A no-edit Resume cannot overwrite a recorded future revision after rewind.
        RecordedRevision previous = events.lowerEntry(tick + 1) == null ? null : events.lowerEntry(tick + 1).getValue();
        if (previous == null || !previous.equals(event)) events.putIfAbsent(tick + 1, event);
    }

    private static Configuration withOption(Configuration old, String key, Object value) {
        Map<String, Object> values = new LinkedHashMap<>(old.options());
        values.put(key, value);
        return new Configuration(old.enabled(), values);
    }

    private static <K, V> Map<K, V> frozen(Map<K, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    public record Configuration(boolean enabled, Map<String, Object> options) {
        public Configuration { options = new MutatorOptions(options).values(); }
    }
    public record Pending(String key, String optionId, MutatorScope scope, String reason) { }
    public record Admission(boolean accepted, long revision, String message, List<Pending> pending) {
        public Admission { pending = List.copyOf(pending); }
    }
    public record Snapshot(long generation, long tick, Map<String, Integer> schemaVersions,
                           Map<String, Configuration> admitted, Map<String, Configuration> targets,
                           Effective effective) {
        public Snapshot {
            if (tick < 0) throw new IllegalArgumentException("Negative mutator tick");
            schemaVersions = frozen(schemaVersions);
            admitted = frozen(admitted);
            targets = frozen(targets);
            Objects.requireNonNull(effective, "effective");
        }
    }

    private record RecordedRevision(Map<String, Configuration> admitted,
                                    Map<String, Configuration> targets, Effective effective) { }

    /** Host composition retains contributor provenance; gravity multiplies once then clamps. */
    public record Effective(long revision, Map<String, List<MutatorPolicy>> policies) {
        public Effective {
            if (revision < 0) throw new IllegalArgumentException("Negative policy revision");
            Map<String, List<MutatorPolicy>> copied = new LinkedHashMap<>();
            policies.forEach((key, values) -> copied.put(key, List.copyOf(values)));
            policies = frozen(copied);
        }
        public int gravityPercent() {
            BigInteger numerator = BigInteger.valueOf(100);
            BigInteger denominator = BigInteger.ONE;
            for (List<MutatorPolicy> contributions : policies.values()) {
                for (MutatorPolicy policy : contributions) {
                    if (policy instanceof MutatorPolicy.DrySonicGravity gravity) {
                        numerator = numerator.multiply(BigInteger.valueOf(gravity.percent()));
                        denominator = denominator.multiply(BigInteger.valueOf(100));
                    }
                }
            }
            return numerator.divide(denominator).max(BigInteger.valueOf(25)).min(BigInteger.valueOf(200)).intValueExact();
        }
        public List<MutatorPolicy.PlayerStealth> stealthPolicies() {
            return contributions(MutatorPolicy.PlayerStealth.class);
        }

        /** Configured size; a ring-scaled contribution counts at its 100-ring maximum. */
        public int headScalePercent(boolean leader) {
            return headScalePercent(leader, RING_SCALE_FULL);
        }

        /**
         * Size for a target holding {@code rings} native rings. Ring-scaled contributions grow
         * linearly from 100% at zero to their percent at 100 rings, truncated, then clamped.
         */
        public int headScalePercent(boolean leader, int rings) {
            int held = Math.clamp(rings, 0, RING_SCALE_FULL);
            return multiplyPercent(contributions(MutatorPolicy.BigHead.class).stream()
                    .filter(value -> leader || value.target() == MutatorPolicy.Target.ALL_TEAM)
                    .map(value -> value.scaleWithRings() ? 100 + (value.percent() - 100) * held / RING_SCALE_FULL
                            : value.percent()).toList(), 100, 200);
        }

        /** Ring-scaled heads reach their configured size at this many rings. */
        public static final int RING_SCALE_FULL = 100;

        public com.openggf.game.mutators.LevelMutatorPolicy levelPolicy(Set<MutatorCapability> available) {
            var removed = java.util.EnumSet.noneOf(com.openggf.game.mutators.MonitorContent.class);
            if (available.contains(MutatorCapability.MONITOR_FILTER))
                contributions(MutatorPolicy.MonitorFilter.class).forEach(value -> removed.addAll(value.removedContents()));
            return new com.openggf.game.mutators.LevelMutatorPolicy(removed,
                    active(available, MutatorCapability.NO_RINGS),
                    active(available, MutatorCapability.NO_CHECKPOINTS),
                    active(available, MutatorCapability.NO_SPECIAL_STAGES),
                    active(available, MutatorCapability.NO_BONUS_STAGES));
        }

        public com.openggf.game.mutators.GameplayMutatorPolicy gameplayPolicy(Set<MutatorCapability> available) {
            var spills = available.contains(MutatorCapability.RINGFALL)
                    ? contributions(MutatorPolicy.Ringfall.class) : List.<MutatorPolicy.Ringfall>of();
            var rebounds = available.contains(MutatorCapability.DEFEAT_KNOCKBACK)
                    ? contributions(MutatorPolicy.DefeatKnockback.class) : List.<MutatorPolicy.DefeatKnockback>of();
            var speeds = available.contains(MutatorCapability.GAMEPLAY_SPEED)
                    ? contributions(MutatorPolicy.GameplaySpeed.class) : List.<MutatorPolicy.GameplaySpeed>of();
            int cap = spills.stream().mapToInt(MutatorPolicy.Ringfall::hardCap).filter(value -> value > 0).min().orElse(0);
            int verticalCap = rebounds.stream().mapToInt(MutatorPolicy.DefeatKnockback::verticalSpeedCap).min().orElse(0xC00);
            return new com.openggf.game.mutators.GameplayMutatorPolicy(
                    multiplyPercent(spills.stream().map(MutatorPolicy.Ringfall::percent).toList(), 10, 100), cap,
                    multiplyPercent(rebounds.stream().map(MutatorPolicy.DefeatKnockback::percent).toList(), 100, 300), verticalCap,
                    multiplyPercent(speeds.stream().map(MutatorPolicy.GameplaySpeed::percent).toList(), 25, 400),
                    speeds.stream().anyMatch(MutatorPolicy.GameplaySpeed::audioFollowsSpeed));
        }

        private boolean active(Set<MutatorCapability> available, MutatorCapability capability) {
            return available.contains(capability) && policies.values().stream().flatMap(List::stream)
                    .anyMatch(value -> value.capability() == capability);
        }

        private <P extends MutatorPolicy> List<P> contributions(Class<P> type) {
            return policies.values().stream().flatMap(List::stream).filter(type::isInstance).map(type::cast).toList();
        }

        private static int multiplyPercent(List<Integer> values, int minimum, int maximum) {
            BigInteger numerator = BigInteger.valueOf(100), denominator = BigInteger.ONE;
            for (int value : values) {
                numerator = numerator.multiply(BigInteger.valueOf(value));
                denominator = denominator.multiply(BigInteger.valueOf(100));
            }
            return numerator.divide(denominator).max(BigInteger.valueOf(minimum))
                    .min(BigInteger.valueOf(maximum)).intValueExact();
        }
    }

    public enum LoadCause {
        FULL_LEVEL_ASSEMBLY(true), FULL_RESTART(true), FULL_DEATH_RELOAD(true),
        STAGE_RETURN_FULL_ASSEMBLY(true), CHECKPOINT_RESTORE(false), PREVIEW(false),
        EDITOR_SWAP(false), SEAMLESS_HANDOFF(false);
        private final boolean qualifies;
        LoadCause(boolean qualifies) { this.qualifies = qualifies; }
        public boolean qualifies() { return qualifies; }
    }
}
