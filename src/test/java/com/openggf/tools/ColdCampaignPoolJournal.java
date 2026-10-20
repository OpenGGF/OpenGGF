package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Opt-in ColdCampaign pool and typed-phase journal; maintained-driver-call protocol,
 * not a driver. No maintained campaign call blocks are installed, and the property
 * alone runs nothing. The caller preserves original input, step/render, assertions,
 * forward45, two-replay and restore ordering, then stops only after original48276
 * assertions/windows. Use r30Open(session) with explicit before/after/restored calls.
 *
 * Purpose: compare SSZ1 registered ownership/reservation/admission and raw Animal /
 * EggRobo phase boundaries across source epochs without object callbacks or writes.
 * All registered animals/releasers are projected, with no fitted slot/actor filter.
 * Motion-only pose changes do not emit: typed events carry the current and immediately
 * previous actual sample; removals retain the last sampled pose. A counter/birth pair
 * is boundary evidence, not a callback or unique parent link. Raw exec may be stale.
 *
 * Inputs: original BK2 buttons and explicit outer/input/context/cycle/window/load keys;
 * openggf.cold.poolJournal selects one CREATE_NEW file under own target/. Hard bounds:
 * 10MiB, 20k records/samples, 16k Java refs. Absent source-epoch fields are explicit
 * ABSENT_IN_SOURCE_EPOCH, never synthesized POP/zero/null. Java refs are run-local;
 * sparse structural absence does not fill clocks or arbitrary pose. Restore rebinding
 * is not a gameplay birth. Four new launch fields are compared separately from803.
 *
 * Qualification requires positive SSZ activation, animal/releaser samples and typed
 * events, equal sample/passivity counts, the footer, equal per-write/passivity counts,
 * the post-footer R30_POOL_VALIDATED marker and terminal0. PREFIX is not full-route
 * completion; full-route final assertions remain unreached at the opt-in stop.
 *
 * Origin: cold-r16-earlier-pool-producer-source-prep-20261017, ordinal19/20; original
 * protocol c35c3a81 was preserved as8492f006. The16 TEMP R30 blocks retain reversible
 * protocol provenance: strip them and restore the former header to recover8492.
 * R30/R31 typed body f8aebe1b199f497d2e56e03a5a2ae426680dd14e65ad8884ebcff44e9d983b0e
 * compiled/executed identically on BASE8030832d2 (R32/56945) and committed781c296ab
 * (R33/70631), each18603 verified observations. R34 preservation changes this header
 * only relative to that tested body; it installs no campaign instrumentation.
 */
final class ColdCampaignPoolJournal {
    private static final String PROPERTY = "openggf.cold.poolJournal";
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_RECORDS = 20_000;
    private static final int MAX_IDENTITIES = 16_000;
    private static final int MAX_SAMPLES = 20_000;
    private static final int STOP_INPUT = 48_276;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<Class<?>, Map<String, Field>> FIELDS = new HashMap<>();
    private static final Map<String, Field> BASE_FIELDS = new HashMap<>();
    private static ColdCampaignPoolJournal owned;

    // TEMP R30 BEGIN typed-fields
    // Temporary event-only extension. Strip all TEMP R30 blocks to recover8492.
    private static GameplayCaptureSession r30OpeningSession;
    private GameplayCaptureSession r30Session;
    private long r30AnimalSamples, r30ReleaserSamples, r30TypedEvents, r30TypedEventRows;
    private long r30WriteAttempts, r30VerifiedWrites;
    private static final Map<Class<?>, Map<String, Field>> R30_FIELDS = new HashMap<>();
    private static final Map<Class<?>, java.util.Set<String>> R30_ABSENT = new HashMap<>();
    private static final String R30_SNAPSHOTS = "r30/typedSnapshots";
    private static final String R30_PHASE = "r30/phase/";
    // TEMP R30 END typed-fields
    private final OutputStream out;
    private final IdentityHashMap<Object, Integer> javaRefs = new IdentityHashMap<>();
    private TreeMap<String, Object> previous = new TreeMap<>();
    private TreeMap<String, Object> previousBindings = new TreeMap<>();
    private TreeMap<String, Object> previousBoundary = new TreeMap<>();
    private Object previousManager;
    private long bytes, samples, verifiedSamples, completedPairs;
    private long emittedSamples, records, restoreMarkers;
    private int lastOriginalMainAssertions = -1;
    private boolean activationSeen, prefixCompleted;

    private ColdCampaignPoolJournal(Path output) throws IOException {
        Path target = Path.of("target").toAbsolutePath().normalize();
        Path path = output.toAbsolutePath().normalize();
        if (!path.startsWith(target) || path.equals(target)) throw new AssertionError("R16 output outside own target");
        Files.createDirectories(path.getParent());
        out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        // TEMP R30 BEGIN header-session
        r30Session = r30OpeningSession;
        if (r30Session == null) throw new AssertionError("R30 header requires original live session");
        // TEMP R30 END header-session
        write(map("type", "HEADER", "schema", "COLD_POOL_R16_v1", "limitBytes", MAX_BYTES,
                "limitRecords", MAX_RECORDS, "limitJavaIdentities", MAX_IDENTITIES, "limitSamples", MAX_SAMPLES,
                "slotTupleColumns", List.of("allocatorState", "registeredRewindIds", "rawExecProjection",
                        "rawBorrowedOwnerProjection", "childReservationParentSources"),
                // TEMP R30 BEGIN header-contract
                "typedSchema", "COLD_POOL_R30_TYPED_PHASE_v1",
                "typedScope", "all registered Animal and EggRobo instances; not live-owner or callback authority",
                "typedEmission", "registration/structural/rebinding/phase transitions only; not first arbitrary motion difference",
                "typedSnapshots", "previous actual sample/current at typed transitions; removal keeps last sampled state",
                "typedAbsent", "ABSENT_IN_SOURCE_EPOCH is not POP, false, zero or null",
                "parentGateInputs", "sampled x/y and raw cameraBounds; not an observed within-dispatch branch",
                "parentUnavailable", "EggRobo has no stored prior-render bit or Animal-style defer/ground profile; never synthesize them",
                "pairingLimit", "counter/birth association may be ambiguous; no persistent stock parent link",
                "footerRule", "require terminal0 and R30_POOL_VALIDATED post-footer write/passivity marker",
                // TEMP R30 END header-contract
                "scope", "SSZ1 first observed activation through original main input 48276; PREFIX ONLY",
                "driver", "original fixed BK2 plus original forward45/two replay windows",
                "movieSha256", "41cc9e73afc5a62a901a7ad3afaedc509b19ac68127673be09ca018054b5d9b5",
                "inputLogSha256", "5237bd5a6ed17d902efe05426012205b741641cb6eacdc2afb8192f7407bb325",
                "limits", List.of("boundary only; no within-pass birth/retire callback",
                        "execOrder may contain stale refs; not current-owner authority",
                        "allocator-free applies only within its managed window",
                        "unknown/unregistered owner remains unknown; no native/all-SST certificate",
                        "Java refs never cross-run identity; rewind restore is not gameplay birth",
                        "new or removed descriptors are boundary observations, not exact creation/deletion callbacks",
                        "between-step intervals can include original assertion/capture/history work")));
    }

    // TEMP R30 BEGIN open-with-session
    static void r30Open(GameplayCaptureSession session) throws IOException {
        if (r30OpeningSession != null) throw new AssertionError("R30 overlapping open");
        r30OpeningSession = session;
        try { open(); } finally { r30OpeningSession = null; }
    }

    // TEMP R30 END open-with-session
    static void open() throws IOException {
        if (owned != null) throw new AssertionError("R16 observer already open");
        String path = System.getProperty(PROPERTY);
        if (path != null) owned = new ColdCampaignPoolJournal(Path.of(path));
    }

    static void before(GameplayCaptureSession session, int outer, int input, String context, int cycle, int step,
                       String label, Bk2FrameInput buttons) {
        observe(session, outer, input, context, cycle, step, label, "BEFORE_STEP", buttons, false);
    }

    static void after(GameplayCaptureSession session, int outer, int input, String context, int cycle, int step,
                      String label, Bk2FrameInput buttons) {
        if (owned == null) return;
        owned.completedPairs++;
        observe(session, outer, input, context, cycle, step, label, "AFTER_STEP_RENDER", buttons, false);
    }

    static void restored(GameplayCaptureSession session, int outer, String label, int cycle, String phase, Bk2FrameInput anchor) {
        if (owned == null) return;
        owned.restoreMarkers++;
        observe(session, outer, outer, "restore", cycle, 0, label, phase, anchor, true);
    }

    static void mainAssertionsCompleted(int input) {
        if (owned != null) owned.lastOriginalMainAssertions = input;
    }

    /** Pure predicate; called only AFTER all original loop assertions and replay-window handling. */
    static boolean stopAfter(int input) {
        return owned != null && input == STOP_INPUT;
    }

    static void prefixComplete() {
        if (owned == null) return;
        if (!owned.activationSeen || owned.samples <= 0)
            throw new AssertionError("R16 prefix completed without observed SSZ activation/samples");
        // TEMP R30 BEGIN positive-typed-prefix
        if (!owned.r30Qualified())
            throw new AssertionError("R30 prefix lacks positive typed observations/events or verified writes");
        // TEMP R30 END positive-typed-prefix
        owned.prefixCompleted = true;
    }

    static void close() {
        ColdCampaignPoolJournal journal = owned;
        owned = null;
        if (journal == null) return;
        try {
            journal.write(map("type", "FOOTER", "samples", journal.samples,
                    "verifiedPassivityChecks", journal.verifiedSamples,
                    "recordsBeforeFooter", journal.records, "bytesBeforeFooter", journal.bytes,
                    "emittedSamples", journal.emittedSamples, "completedStepRenderPairs", journal.completedPairs,
                    "restoreMarkers", journal.restoreMarkers, "activationSeen", journal.activationSeen,
                    "lastOriginalMainAssertions", journal.lastOriginalMainAssertions,
                    "prefixComplete", journal.prefixCompleted,
                    // TEMP R30 BEGIN typed-footer
                    "r30AnimalSamples", journal.r30AnimalSamples,
                    "r30ReleaserSamples", journal.r30ReleaserSamples,
                    "r30TypedEvents", journal.r30TypedEvents, "r30TypedEventRows", journal.r30TypedEventRows,
                    "r30WriteAttemptsBeforeFooter", journal.r30WriteAttempts,
                    "r30VerifiedWritesBeforeFooter", journal.r30VerifiedWrites,
                    "r30TypedQualifiedBeforeFooter", journal.r30Qualified(),
                    "r30FooterNeedsTerminal0AndPostWriteMarker", true,
                    // TEMP R30 END typed-footer
                    "fullRouteFinalAssertionsReached", false,
                    "qualification", journal.activationSeen && journal.samples > 0
                            // TEMP R30 BEGIN typed-footer-qualification
                            && journal.r30Qualified()
                            // TEMP R30 END typed-footer-qualification
                            && journal.samples == journal.verifiedSamples && journal.prefixCompleted
                            ? "COMPLETE_BOUNDED_PREFIX_OBSERVATION" : "INCOMPLETE_DO_NOT_ATTRIBUTE"));
            // TEMP R30 BEGIN post-footer-proof
            Picture r30BeforeMarker = journal.r30ReadWriteProjection();
            System.out.println((journal.prefixCompleted && journal.r30Qualified()
                    ? "R30_POOL_VALIDATED" : "R30_POOL_INCOMPLETE") + " samples=" + journal.samples
                    + " verified=" + journal.verifiedSamples + " typedEvents=" + journal.r30TypedEvents
                    + " writes=" + journal.r30WriteAttempts + " verifiedWrites=" + journal.r30VerifiedWrites
                    + " records=" + journal.records);
            if (!r30BeforeMarker.equals(journal.r30ReadWriteProjection()))
                throw new AssertionError("R30 completion marker changed known projection");
            // TEMP R30 END post-footer-proof
        } finally {
            try { journal.out.close(); } catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }

    private static void observe(GameplayCaptureSession session, int outer, int input, String context, int cycle, int step,
                                String label, String phase, Bk2FrameInput buttons, boolean restore) {
        if (owned == null) return;
        try { owned.sample(session, outer, input, context, cycle, step, label, phase, buttons, restore); }
        catch (ReflectiveOperationException e) { throw new AssertionError("R16 declared raw field contract", e); }
    }

    private void sample(GameplayCaptureSession session, int outer, int input, String context, int cycle, int step,
                        String label, String phase, Bk2FrameInput buttons, boolean restore)
            throws ReflectiveOperationException {
        if (buttons.frameIndex() != input) throw new AssertionError("R16 explicit input key mismatch");
        Picture before = picture(session);
        // No full pool reads outside actual SSZ1. Activation is observed, not fitted to an input number.
        if (!before.ssz()) return;
        if (samples >= MAX_SAMPLES) throw new AssertionError("R16 sample cap; STOP incomplete");
        samples++;
        // TEMP R30 BEGIN typed-positive-counts
        r30CountTypedSample(before.state());
        // TEMP R30 END typed-positive-counts
        var boundary = map("sample", samples, "outerMovieFrameIndex", outer, "inputListIndex", input,
                "bk2FrameIndex", buttons.frameIndex(), "context", context, "cycle", cycle,
                "windowStep", step, "windowLabel", label, "phase", phase,
                "completedStepRenderPairs", completedPairs, "actual", before.metadata());
        boolean activation = !activationSeen || before.manager() != previousManager;
        List<String> removed = new ArrayList<>();
        TreeMap<String, Object> changed = new TreeMap<>();
        for (var entry : before.state().entrySet()) {
            if (!previous.containsKey(entry.getKey()) || !java.util.Objects.equals(previous.get(entry.getKey()), entry.getValue()))
                changed.put(entry.getKey(), entry.getValue());
        }
        for (String key : previous.keySet()) if (!before.state().containsKey(key)) removed.add(key);
        // TEMP R30 BEGIN suppress-motion-only-emission
        // Only the NEW full-pose key is internal; no original structural comparison is dropped.
        changed.remove(R30_SNAPSHOTS);
        removed.remove(R30_SNAPSHOTS);
        // TEMP R30 END suppress-motion-only-emission
        boolean rebound = !before.bindings().equals(previousBindings);
        boolean targetAdmission = changed.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith("object/")
                && !previous.containsKey(entry.getKey()) && targetDescriptor(entry.getValue()));
        if (activation || restore || !changed.isEmpty() || !removed.isEmpty() || rebound) {
            var row = map("type", activation ? "INITIAL_OBSERVED_POOL" : restore ? "RESTORE_BOUNDARY" : "POOL_CHANGE",
                    "sample", samples, "outerMovieFrameIndex", outer, "inputListIndex", input,
                    "bk2FrameIndex", buttons.frameIndex(), "context", context, "cycle", cycle,
                    "windowStep", step, "windowLabel", label, "phase", phase,
                    "inputCursorRole", restore ? "restored-history-anchor-not-new-step" : "actual-original-step-input",
                    "buttons", List.of(buttons.p1InputMask(), buttons.p1ActionMask(), buttons.p1StartPressed(),
                            buttons.p2InputMask(), buttons.p2ActionMask(), buttons.p2StartPressed(),
                            buttons.debugModeTogglePressed(), buttons.debugShiftDown(), buttons.debugControlDown(),
                            buttons.debugAltDown(), buttons.debugSuperDown()),
                    "completedStepRenderPairs", completedPairs, "actual", before.metadata(),
                    "changed", changed, "removed", removed, "javaReferenceBindings", before.bindings(),
                    "referenceMeaning", restore ? "restore/recreation; not gameplay births or retirement"
                            : "run-local reference changes alone are not semantic births",
                    "targetFirstObservedInBoundary", targetAdmission,
                    "previousObservedBoundary", previousBoundary);
            if (activation || restore || targetAdmission) {
                row.put("orderedPool", orderedPool(before.state()));
                row.put("previousOrderedPool", orderedPool(previous));
            }
            if (activation) row.put("fullState", before.state());
            // TEMP R30 BEGIN typed-event-payload
            var typedEvents = r30Transitions(previous, before.state(), previousBindings, before.bindings(),
                    activation || restore);
            row.put("r30TypedTransitions", typedEvents);
            row.put("r30TypedSnapshotMeaning", "last actual sample/current; not callback or first motion split");
            // TEMP R30 END typed-event-payload
            write(row);
            // TEMP R30 BEGIN typed-event-count
            r30TypedEvents += typedEvents.size();
            if (!typedEvents.isEmpty()) r30TypedEventRows++;
            // TEMP R30 END typed-event-count
            emittedSamples++;
        }
        Picture after = picture(session);
        if (!before.equals(after)) throw new AssertionError("R16 observer changed declared primitive/reference picture");
        verifiedSamples++;
        previousBoundary = boundary;
        previous = before.state();
        previousBindings = before.bindings();
        previousManager = before.manager();
        activationSeen = true;
    }

    private Picture picture(GameplayCaptureSession session) throws ReflectiveOperationException {
        var level = GameServices.level();
        boolean ssz = level.getCurrentZone() == 10 && level.getCurrentAct() == 0;
        Object manager = level.getObjectManager();
        TreeMap<String, Object> state = new TreeMap<>();
        TreeMap<String, Object> bindings = new TreeMap<>();
        var metadata = map("zone", level.getCurrentZone(), "act", level.getCurrentAct(),
                "completedProductionLoadGeneration", level.getCompletedProductionLoadGeneration(),
                "levelLF", level.getFrameCounter(), "levelJavaRef", ref(level.getCurrentLevel()),
                "managerJavaRef", ref(manager));
        Bk2FrameInput history = (Bk2FrameInput) raw(session, "previousInput");
        metadata.put("actualSessionPreviousInput", history == null ? null : List.of(history.frameIndex(),
                history.p1InputMask(), history.p1ActionMask(), history.p1StartPressed(),
                history.p2InputMask(), history.p2ActionMask(), history.p2StartPressed()));
        if (!ssz) return new Picture(false, manager, state, bindings, metadata);
        if (manager == null) throw new AssertionError("R16 SSZ1 has no actual manager");
        ObjectSlotLayout layout = (ObjectSlotLayout) raw(manager, "slotLayout");
        Object allocator = raw(manager, "slotAllocator");
        BitSet used = bits(raw(allocator, "used"));
        if (used.length() > layout.dynamicSlotCount()) throw new AssertionError("R16 allocator extent");
        Object camera = raw(manager, "camera");
        metadata.put("objectVInt", raw(manager, "vblaCounter"));
        metadata.put("rawObjectManagerFrameCounter", raw(manager, "frameCounter"));
        metadata.put("updating", raw(manager, "updating"));
        metadata.put("currentExecSlot", raw(manager, "currentExecSlot"));
        metadata.put("physicalCameraXY", camera == null ? null : List.of(raw(camera, "x"), raw(camera, "y")));
        metadata.put("copiedCameraXY", camera == null ? null : List.of(raw(camera, "renderCopyX"), raw(camera, "renderCopyY")));
        metadata.put("managerCameraX", raw(manager, "s2LatchedObjectManagerCameraX"));
        metadata.put("managerCameraYCoarse", raw(manager, "twoAxisCameraYCoarse"));
        state.put("idCounter", raw(manager, "dynamicObjectIdCounter"));
        state.put("layout", List.of(layout.firstDynamicSlot(), layout.dynamicSlotCount(), layout.processSlotCount()));
        Map<?, ?> active = (Map<?, ?>) raw(manager, "activeObjects");
        Collection<?> dynamics = (Collection<?>) raw(manager, "dynamicObjects");
        Map<?, ?> ids = (Map<?, ?>) raw(manager, "rewindObjectIds");
        Object[] exec = (Object[]) raw(manager, "execOrder");
        Object retirement = raw(manager, "slotRetirement");
        Object[] borrowed = (Object[]) raw(retirement, "ownersByOwnSlot");
        BitSet pendingReleases = bits(raw(retirement, "pendingChildReleases"));
        BitSet freed = bits(raw(manager, "slotsFreedDuringObjectPass"));
        if (exec.length != layout.processSlotCount() || borrowed.length != exec.length)
            throw new AssertionError("R16 actual process array extent");
        IdentityHashMap<Object, ObjectSpawn> activeSpawns = new IdentityHashMap<>();
        for (var entry : active.entrySet()) activeSpawns.put(entry.getValue(), (ObjectSpawn) entry.getKey());
        IdentityHashMap<Object, Boolean> dynamicSet = new IdentityHashMap<>();
        for (Object object : dynamics) dynamicSet.put(object, true);
        IdentityHashMap<Object, Boolean> union = new IdentityHashMap<>();
        for (Object object : active.values()) union.put(object, true);
        for (Object object : dynamics) union.put(object, true);
        for (Object object : exec) if (object != null) union.put(object, true);
        for (Object object : borrowed) if (object != null) union.put(object, true);
        IdentityHashMap<Object, String> keys = new IdentityHashMap<>();
        for (Object object : union.keySet()) {
            ObjectRefId id = (ObjectRefId) ids.get(object);
            String key = id == null ? "unresolved-run-local:" + ref(object) : id.kind().name() + ":"
                    + id.slotIndex() + ":" + id.generation() + ":" + id.spawnId() + ":" + id.dynamicId();
            if (state.containsKey("object/" + key)) throw new AssertionError("R16 ambiguous simultaneous semantic identity");
            keys.put(object, key);
            ObjectSpawn source = object instanceof AbstractObjectInstance ? (ObjectSpawn) baseRaw(object, "spawn") : null;
            ObjectSpawn managed = object instanceof AbstractObjectInstance ? (ObjectSpawn) baseRaw(object, "dynamicSpawn") : null;
            Object slot = object instanceof AbstractObjectInstance ? baseRaw(object, "slotIndex") : null;
            state.put("object/" + key, map("class", object.getClass().getName(), "refId", id == null ? null
                            : List.of(id.kind().name(), id.slotIndex(), id.generation(), id.spawnId(), id.dynamicId()),
                    "identityScope", id == null ? "unresolved-run-local-only" : "existing-engine-rewind-id",
                    "ownSlot", slot, "activePlacement", spawn(activeSpawns.get(object), true),
                    "dynamicRegistered", dynamicSet.containsKey(object), "baseSource", spawn(source, true),
                    "managedSourceWithoutLiveXY", spawn(managed, false),
                    "rawDestroyed", object instanceof AbstractObjectInstance ? baseRaw(object, "destroyed") : null,
                    "rawDestroyedRespawnable", object instanceof AbstractObjectInstance ? baseRaw(object, "destroyedRespawnable") : null));
            bindings.put(key, ref(object));
        }
        // TEMP R30 BEGIN typed-picture
        r30ProjectActors(state, keys, union, activeSpawns, dynamicSet);
        // TEMP R30 END typed-picture
        TreeMap<Integer, List<String>> reservations = new TreeMap<>();
        Map<?, ?> childSlots = (Map<?, ?>) raw(manager, "reservedChildSlots");
        var childReservations = new ArrayList<Object>();
        for (var entry : childSlots.entrySet()) {
            ObjectSpawn parent = (ObjectSpawn) entry.getKey();
            int[] slots = ((int[]) entry.getValue()).clone();
            String owner = JSON_VALUE(spawn(parent, true));
            childReservations.add(map("parent", spawn(parent, true), "slots", java.util.Arrays.stream(slots).boxed().toList()));
            for (int slot : slots) reservations.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(owner);
        }
        childReservations.sort(java.util.Comparator.comparing(ColdCampaignPoolJournal::JSON_VALUE));
        state.put("childReservations", childReservations);
        state.put("pendingChildReleases", ints(pendingReleases));
        state.put("slotsFreedDuringObjectPass", ints(freed));
        TreeMap<Integer, List<String>> registeredBySlot = new TreeMap<>();
        for (Object object : union.keySet()) if ((activeSpawns.containsKey(object) || dynamicSet.containsKey(object))
                && object instanceof AbstractObjectInstance) {
            int ownSlot = (Integer) baseRaw(object, "slotIndex");
            registeredBySlot.computeIfAbsent(ownSlot, ignored -> new ArrayList<>()).add(keys.get(object));
        }
        for (int slot = 0; slot < exec.length; slot++) {
            List<String> registered = new ArrayList<>(registeredBySlot.getOrDefault(slot, List.of()));
            registered.sort(String::compareTo);
            List<String> reserved = new ArrayList<>(reservations.getOrDefault(slot, List.of()));
            reserved.sort(String::compareTo);
            String allocation = slot >= layout.firstDynamicSlot() && slot < layout.lastDynamicSlotExclusive()
                    ? used.get(slot - layout.firstDynamicSlot()) ? "USED" : "ALLOCATOR_FREE" : "OUTSIDE_ALLOCATOR_WINDOW";
            // exec and borrowed are raw projections, deliberately separate from registration/allocation.
            var slotValue = new ArrayList<Object>();
            slotValue.add(allocation); slotValue.add(registered);
            slotValue.add(exec[slot] == null ? null : keys.get(exec[slot]));
            slotValue.add(borrowed[slot] == null ? null : keys.get(borrowed[slot]));
            slotValue.add(reserved);
            state.put(String.format("slot/%03d", slot), slotValue);
        }
        Object placement = raw(manager, "placement");
        var cursor = map("cursorIndex", raw(placement, "cursorIndex"), "leftCursorIndex", raw(placement, "leftCursorIndex"),
                "lastCameraChunk", raw(placement, "lastCameraChunk"),
                "cursorLoadPassPending", raw(placement, "cursorLoadPassPending"),
                "pendingCursorLoadOrder", new ArrayList<>((Collection<?>) raw(placement, "pendingCursorLoadOrder")),
                "lastScrollBackward", raw(placement, "lastScrollBackward"));
        for (String name : List.of("remembered", "stayActive", "destroyedInWindow", "pendingCursorLoad",
                "deferredVerticalLoad", "dormant")) cursor.put(name, ints(bits(raw(placement, name))));
        var placementActive = new ArrayList<Object>();
        for (Object value : (Collection<?>) raw(placement, "active")) placementActive.add(spawn((ObjectSpawn) value, true));
        placementActive.sort(java.util.Comparator.comparing(ColdCampaignPoolJournal::JSON_VALUE));
        cursor.put("activePlacementRecords", placementActive);
        state.put("placement", cursor);
        metadata.put("placementLastCameraX", raw(placement, "lastCameraX"));
        state.put("pendingLostRingReservations", ringReservations(raw(level, "lostRingSpawnCoordinator")));
        state.put("ringSubsystemMetadata", ringMetadata(raw(level, "ringManager")));
        return new Picture(true, manager, state, bindings, metadata);
    }

    private List<Object> ringReservations(Object coordinator) throws ReflectiveOperationException {
        var result = new ArrayList<Object>();
        if (coordinator == null) { result.add("COORDINATOR_UNAVAILABLE"); return result; }
        for (Object pending : (Collection<?>) raw(coordinator, "pending")) result.add(map(
                "ringCount", raw(pending, "ringCount"), "beyondNativeLimit", raw(pending, "beyondNativeLimit"),
                "preallocatedSlots", java.util.Arrays.stream((int[]) raw(pending, "preallocatedSlots")).boxed().toList(),
                "slotsFullyReserved", raw(pending, "slotsFullyReserved"),
                "deferOwnerRingClear", raw(pending, "deferOwnerRingClear"), "queuedFrameCounter", raw(pending, "frameCounter")));
        return result;
    }

    private List<Object> ringMetadata(Object manager) throws ReflectiveOperationException {
        var result = new ArrayList<Object>();
        if (manager == null) { result.add("RING_MANAGER_UNAVAILABLE"); return result; }
        Object pool = raw(manager, "lostRings");
        Object[] rings = (Object[]) raw(pool, "ringPool");
        for (int i = 0; i < rings.length; i++) if (Boolean.TRUE.equals(raw(rings[i], "active"))) result.add(map(
                "kind", "lost-ring-mirror-not-registration", "poolIndex", i,
                "slot", raw(rings[i], "slotIndex"), "collected", raw(rings[i], "collected")));
        Object[] attracted = (Object[]) raw(manager, "attractedRings");
        for (int i = 0; i < attracted.length; i++) if (Boolean.TRUE.equals(raw(attracted[i], "active"))) result.add(map(
                "kind", "attracted-ring-metadata", "poolIndex", i, "sourceIndex", raw(attracted[i], "sourceIndex"),
                "slot", raw(attracted[i], "objectSlotIndex"), "collected", raw(attracted[i], "collected"),
                "slotAllocationPending", raw(attracted[i], "slotAllocationPending")));
        return result;
    }

    // TEMP R30 BEGIN typed-raw-protocol
    private boolean r30Qualified() {
        return r30AnimalSamples > 0 && r30ReleaserSamples > 0 && r30TypedEvents > 0
                && samples == verifiedSamples && r30WriteAttempts == r30VerifiedWrites
                && r30WriteAttempts == records;
    }

    private void r30CountTypedSample(TreeMap<String, Object> state) {
        boolean animal = false, releaser = false;
        for (Object value : r30Snapshots(state).values()) {
            Map<?, ?> snapshot = (Map<?, ?>) value;
            animal |= "ANIMAL".equals(snapshot.get("kind"));
            releaser |= "EGG_ROBO".equals(snapshot.get("kind"))
                    && "ANIMAL_RELEASER".equals(snapshot.get("mode"));
        }
        if (animal) r30AnimalSamples++;
        if (releaser) r30ReleaserSamples++;
    }

    private void r30ProjectActors(TreeMap<String, Object> state, IdentityHashMap<Object, String> keys,
                                 IdentityHashMap<Object, Boolean> union,
                                 IdentityHashMap<Object, ObjectSpawn> activeSpawns,
                                 IdentityHashMap<Object, Boolean> dynamics) throws ReflectiveOperationException {
        var snapshots = new TreeMap<String, Object>();
        for (Object object : union.keySet()) {
            // Registration only; raw exec may be stale. Never query actor liveness.
            if (!activeSpawns.containsKey(object) && !dynamics.containsKey(object)) continue;
            TreeMap<String, Object> snapshot, phase;
            if (object instanceof com.openggf.level.objects.AnimalObjectInstance) {
                Class<?> owner = com.openggf.level.objects.AnimalObjectInstance.class;
                snapshot = map("kind", "ANIMAL");
                for (String name : List.of("currentX", "currentY", "xSubpixel", "ySubpixel", "xVelocity",
                        "yVelocity", "groundXVelocity", "groundYVelocity", "animFrameTimer", "animFrame",
                        "mappingSetIndex", "artVariant", "pointsValue"))
                    snapshot.put(name, r30Scalar(object, owner, name, "int", false));
                for (String name : List.of("firstDisplayFrame", "spawnedPoints", "deferArtVariantRng", "romRenderOnScreen"))
                    snapshot.put(name, r30Scalar(object, owner, name, "boolean", false));
                snapshot.put("state", r30Scalar(object, owner, "state", owner.getName() + "$State", false));
                snapshot.put("definition", r30Scalar(object, owner, "definition", "com.openggf.level.objects.AnimalType", false));
                snapshot.put("launchMode", r30Scalar(object, owner, "launchMode", owner.getName() + "$LaunchMode", true));
                for (String name : List.of("launchTypeA", "launchTypeB"))
                    snapshot.put(name, r30Scalar(object, owner, name, "com.openggf.level.objects.AnimalType", true));
                snapshot.put("launchParentFlipX", r30Scalar(object, owner, "launchParentFlipX", "boolean", true));
                phase = r30Pick(snapshot, List.of("kind", "state", "definition", "artVariant", "mappingSetIndex",
                        "firstDisplayFrame", "deferArtVariantRng", "romRenderOnScreen", "launchMode",
                        "launchTypeA", "launchTypeB", "launchParentFlipX"));
            } else if (object instanceof com.openggf.game.sonic3k.objects.badniks.EggRoboBadnikInstance) {
                Class<?> owner = com.openggf.game.sonic3k.objects.badniks.EggRoboBadnikInstance.class;
                snapshot = map("kind", "EGG_ROBO");
                for (String name : List.of("group", "x", "y", "flyX", "flyY", "xVelocity", "yVelocity",
                        "animalsLeft", "previousY", "previousPreviousY", "swingMaximum", "swingAcceleration"))
                    snapshot.put(name, r30Scalar(object, owner, name, "int", false));
                for (String name : List.of("childrenSpawned", "lowPriorityArt", "swingDescending", "gunArmed"))
                    snapshot.put(name, r30Scalar(object, owner, name, "boolean", false));
                // Entry/render ownership changed across source epochs. Preserve explicit
                // absence for both the legacy gate and its independent replacement fields.
                for (String name : List.of("flyByGateChecked", "waitingForOnscreen", "renderedOnscreen",
                        "drawSubmitted", "initialized"))
                    snapshot.put(name, r30Scalar(object, owner, name, "boolean", true));
                snapshot.put("mode", r30Scalar(object, owner, "mode", owner.getName() + "$Mode", false));
                snapshot.put("state", r30Scalar(object, owner, "state", owner.getName() + "$State", false));
                snapshot.put("facingLeft", r30Scalar(object, com.openggf.level.objects.AbstractBadnikInstance.class,
                        "facingLeft", "boolean", false));
                Class<?> mappingOwner = com.openggf.game.sonic3k.objects.badniks.EggRoboBadnikInstance.class.getSuperclass();
                if (mappingOwner == null || !mappingOwner.getName().equals(
                        "com.openggf.game.sonic3k.objects.badniks.AbstractS3kBadnikInstance"))
                    throw new AssertionError("R31 EggRobo superclass field-owner contract");
                snapshot.put("mappingFrame", r30Scalar(object, mappingOwner, "mappingFrame", "int", false));
                // MoveSprite2 uses the superclass's position fractions; these remain pose
                // samples, not sparse phase-event triggers.
                for (String name : List.of("xSubpixel", "ySubpixel"))
                    snapshot.put(name, r30Scalar(object, mappingOwner, name, "int", false));
                Object bounds = baseRaw(null, "cameraBounds");
                if (!(bounds instanceof com.openggf.level.objects.CameraBounds))
                    throw new AssertionError("R30 cameraBounds declared type");
                var gate = new TreeMap<String, Object>();
                for (String name : List.of("left", "top", "right", "bottom", "verticalWrapRange"))
                    gate.put(name, r30Scalar(bounds, com.openggf.level.objects.CameraBounds.class, name, "int", false));
                snapshot.put("sampledParentGateCameraBounds", gate);
                phase = r30Pick(snapshot, List.of("kind", "mode", "state", "animalsLeft", "childrenSpawned",
                        "lowPriorityArt", "flyByGateChecked", "waitingForOnscreen", "renderedOnscreen",
                        "drawSubmitted", "initialized", "facingLeft"));
            } else continue;
            String key = keys.get(object);
            if (key == null) throw new AssertionError("R30 registered actor missing semantic key");
            snapshot.put("registration", state.get("object/" + key));
            snapshots.put(key, snapshot);
            state.put(R30_PHASE + key, phase);
        }
        state.put(R30_SNAPSHOTS, snapshots);
    }

    private static TreeMap<String, Object> r30Pick(Map<String, Object> values, List<String> names) {
        var result = new TreeMap<String, Object>();
        for (String name : names) result.put(name, values.get(name));
        return result;
    }

    private static Object r30Scalar(Object object, Class<?> owner, String name, String expectedType,
                                    boolean optionalInOlderEpoch) throws ReflectiveOperationException {
        var absentNames = R30_ABSENT.computeIfAbsent(owner, ignored -> new java.util.HashSet<>());
        if (optionalInOlderEpoch && absentNames.contains(name)) return "ABSENT_IN_SOURCE_EPOCH";
        var fields = R30_FIELDS.computeIfAbsent(owner, ignored -> new HashMap<>());
        Field field = fields.get(name);
        if (field == null) {
            try { field = owner.getDeclaredField(name); }
            catch (NoSuchFieldException absent) {
                if (optionalInOlderEpoch) { absentNames.add(name); return "ABSENT_IN_SOURCE_EPOCH"; }
                throw absent;
            }
            if (!field.getType().getName().equals(expectedType))
                throw new AssertionError("R30 declared type " + owner.getName() + "." + name + ": " + field.getType());
            field.setAccessible(true);
            fields.put(name, field);
        }
        Object value = field.get(object);
        if (value == null) return null;
        if (value instanceof Integer || value instanceof Boolean) return value;
        if (value instanceof Enum<?> item) return item.name();
        throw new AssertionError("R30 non-scalar field " + owner.getName() + "." + name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> r30Snapshots(TreeMap<String, Object> state) {
        Object value = state.get(R30_SNAPSHOTS);
        return value == null ? java.util.Collections.emptyMap() : (Map<String, Object>) value;
    }

    private static TreeMap<String, Object> r30Transitions(TreeMap<String, Object> previous,
                                                         TreeMap<String, Object> current,
                                                         TreeMap<String, Object> previousBindings,
                                                         TreeMap<String, Object> currentBindings,
                                                         boolean fullBoundary) {
        Map<String, Object> before = r30Snapshots(previous), after = r30Snapshots(current);
        var keys = new java.util.TreeSet<String>(); keys.addAll(before.keySet()); keys.addAll(after.keySet());
        var events = new TreeMap<String, Object>();
        for (String key : keys) {
            var reasons = new ArrayList<String>();
            if (!before.containsKey(key)) reasons.add("REGISTERED_TYPED_APPEARANCE_NOT_CALLBACK");
            if (!after.containsKey(key)) reasons.add("REGISTERED_TYPED_REMOVAL_NOT_DELETE_INSTRUCTION");
            if (!java.util.Objects.equals(previous.get(R30_PHASE + key), current.get(R30_PHASE + key)))
                reasons.add("TYPED_PHASE_CHANGED");
            if (!java.util.Objects.equals(previous.get("object/" + key), current.get("object/" + key)))
                reasons.add("STRUCTURAL_DESCRIPTOR_CHANGED");
            if (!java.util.Objects.equals(previousBindings.get(key), currentBindings.get(key)))
                reasons.add("RUN_LOCAL_REFERENCE_REBOUND_NOT_GAMEPLAY_BIRTH");
            if (fullBoundary) reasons.add("INITIAL_OR_RESTORE_BOUNDARY");
            if (!reasons.isEmpty()) events.put(key, map("reasons", reasons,
                    "previousActualSample", before.get(key), "currentActualSample", after.get(key)));
        }
        return events;
    }

    private Picture r30ReadWriteProjection() {
        if (r30Session == null) throw new AssertionError("R30 write without original session");
        try { return picture(r30Session); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("R30 write projection contract", failure); }
    }

    // TEMP R30 END typed-raw-protocol
    private static List<Object> orderedPool(TreeMap<String, Object> state) {
        var result = new ArrayList<Object>();
        for (var entry : state.entrySet()) if (entry.getKey().startsWith("slot/"))
            result.add(List.of(entry.getKey(), entry.getValue()));
        return result;
    }

    private static boolean targetDescriptor(Object value) {
        Map<?, ?> descriptor = (Map<?, ?>) value;
        Object active = descriptor.get("activePlacement");
        if (!(active instanceof Map<?, ?> placement)) return false;
        Object index = placement.get("layoutIndex");
        return Integer.valueOf(204).equals(index) || Integer.valueOf(206).equals(index);
    }

    private static Object spawn(ObjectSpawn spawn, boolean sourceXY) {
        if (spawn == null) return null;
        var result = map("layoutIndex", spawn.layoutIndex(), "id", spawn.objectId(), "subtype", spawn.subtype(),
                "renderFlags", spawn.renderFlags(), "respawnTracked", spawn.respawnTracked(),
                "rawYFlags", spawn.rawYWord() & 0xF000, "ownerModId", spawn.ownerModId(), "objectKey", spawn.objectKey());
        if (sourceXY) { result.put("sourceX", spawn.x()); result.put("sourceY", spawn.y()); result.put("sourceRawYWord", spawn.rawYWord()); }
        return result;
    }

    private int ref(Object object) {
        if (object == null) return 0; // explicit null reference, never an engine clock/value.
        Integer known = javaRefs.get(object);
        if (known != null) return known;
        if (javaRefs.size() >= MAX_IDENTITIES) throw new AssertionError("R16 identity cap; STOP incomplete");
        int next = javaRefs.size() + 1;
        javaRefs.put(object, next);
        return next;
    }

    private static Object baseRaw(Object object, String name) throws ReflectiveOperationException {
        Field field = BASE_FIELDS.get(name);
        if (field == null) {
            field = AbstractObjectInstance.class.getDeclaredField(name);
            field.setAccessible(true); BASE_FIELDS.put(name, field);
        }
        return field.get(object);
    }

    private static Object raw(Object object, String name) throws ReflectiveOperationException {
        if (object == null) throw new AssertionError("R16 null declared field owner: " + name);
        Map<String, Field> byName = FIELDS.computeIfAbsent(object.getClass(), ignored -> new HashMap<>());
        Field field = byName.get(name);
        if (field == null) {
            for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
                try { field = type.getDeclaredField(name); break; } catch (NoSuchFieldException ignored) { }
            }
            if (field == null) throw new NoSuchFieldException(object.getClass().getName() + "." + name);
            field.setAccessible(true); byName.put(name, field);
        }
        return field.get(object);
    }

    private static BitSet bits(Object value) { return (BitSet) ((BitSet) value).clone(); }
    private static List<Integer> ints(BitSet bits) { return bits.stream().boxed().toList(); }
    private static TreeMap<String, Object> map(Object... fields) {
        var result = new TreeMap<String, Object>();
        for (int i = 0; i < fields.length; i += 2) result.put((String) fields[i], fields[i + 1]);
        return result;
    }
    private static String JSON_VALUE(Object value) {
        try { return JSON.writeValueAsString(value); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private void write(Object row) {
        // TEMP R30 BEGIN write-before-projection
        Picture r30BeforeWrite = r30ReadWriteProjection();
        r30WriteAttempts++;
        // TEMP R30 END write-before-projection
        try {
            byte[] encoded = JSON.writeValueAsBytes(row);
            if (records >= MAX_RECORDS || bytes + encoded.length + 1 > MAX_BYTES)
                throw new AssertionError("R16 journal cap; STOP incomplete; never truncate/continue");
            out.write(encoded); out.write('\n'); out.flush(); bytes += encoded.length + 1; records++;
            // TEMP R30 BEGIN write-after-projection
            Picture r30AfterWrite = r30ReadWriteProjection();
            if (!r30BeforeWrite.equals(r30AfterWrite))
                throw new AssertionError("R30 output changed declared primitive/reference projection");
            r30VerifiedWrites++;
            // TEMP R30 END write-after-projection
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private record Picture(boolean ssz, Object manager, TreeMap<String, Object> state,
                           TreeMap<String, Object> bindings, TreeMap<String, Object> metadata) { }
}
