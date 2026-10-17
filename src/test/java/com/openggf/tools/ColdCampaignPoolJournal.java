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
 * Opt-in ColdCampaign pool journal; maintained-driver-call protocol, not a driver.
 * Purpose: compare SSZ1 ownership/reservation/admission boundaries across source
 * epochs without object callbacks or gameplay writes. Raw execution projections
 * may be stale; boundary sampling cannot resolve a birth and retirement inside a step.
 * Inputs: explicit calls from the original maintained driver's step/render/restore
 * boundaries, actual outer/input/context/cycle/step/load keys and original BK2 buttons;
 * {@code openggf.cold.poolJournal} selects one CREATE_NEW file under own target/.
 * Preserve every original call/assertion/replay window. Complete a prefix only after
 * original input48276 assertions/windows; positive SSZ activation/samples and equal
 * passivity counts are mandatory. A qualified prefix does not reach full-route finals.
 * No maintained campaign call blocks are installed; the property alone runs nothing.
 * Origin: cold-r16-earlier-pool-producer-source-prep-20261017, ordinal19/20 source;
 * R17/R18 matched8030832d2/f196e95da arms each verified18603 observations. Functional
 * source epoch c35c3a817c40bec8d31a8809170b7d71433c6d56b8fa35aac3cf36791805d164;
 * preservation changes this header only. Rewind ids are semantic, Java refs run-local;
 * neither is native SST identity or cross-run reference authority.
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
        write(map("type", "HEADER", "schema", "COLD_POOL_R16_v1", "limitBytes", MAX_BYTES,
                "limitRecords", MAX_RECORDS, "limitJavaIdentities", MAX_IDENTITIES, "limitSamples", MAX_SAMPLES,
                "slotTupleColumns", List.of("allocatorState", "registeredRewindIds", "rawExecProjection",
                        "rawBorrowedOwnerProjection", "childReservationParentSources"),
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
                    "fullRouteFinalAssertionsReached", false,
                    "qualification", journal.activationSeen && journal.samples > 0
                            && journal.samples == journal.verifiedSamples && journal.prefixCompleted
                            ? "COMPLETE_BOUNDED_PREFIX_OBSERVATION" : "INCOMPLETE_DO_NOT_ATTRIBUTE"));
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
            write(row);
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
        try {
            byte[] encoded = JSON.writeValueAsBytes(row);
            if (records >= MAX_RECORDS || bytes + encoded.length + 1 > MAX_BYTES)
                throw new AssertionError("R16 journal cap; STOP incomplete; never truncate/continue");
            out.write(encoded); out.write('\n'); out.flush(); bytes += encoded.length + 1; records++;
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private record Picture(boolean ssz, Object manager, TreeMap<String, Object> state,
                           TreeMap<String, Object> bindings, TreeMap<String, Object> metadata) { }
}
