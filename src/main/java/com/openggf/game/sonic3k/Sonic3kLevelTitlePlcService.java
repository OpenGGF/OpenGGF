package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.NemesisPlcQueueSnapshot;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.resources.NemesisPlcPatternCounts;
import com.openggf.level.resources.NemesisPlcServiceQueue;
import com.openggf.level.resources.PlcParser.PlcDefinition;
import com.openggf.level.resources.PlcParser.PlcEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;

/**
 * Logical Nemesis FIFO for fresh-zone Level/loc_62CC and results loc_2E0C4 waits,
 * plus raw event-owned descriptor submission. Title/results assets are decoded by
 * the ROM resource plan; this owner preserves their independent readiness gate.
 * Raw append prepares no art; ordinary publication uses the current frame's
 * explicit physical journal after queued DMA, then arms the next head after animation.
 */
public final class Sonic3kLevelTitlePlcService
        implements PlcLifecycleService, RewindSnapshottable<NemesisPlcQueueSnapshot>,
        com.openggf.game.internal.WorldPlcPatternPublication,
        com.openggf.game.internal.WorldPlcServiceProvider {
    private Rom rom;
    private final com.openggf.game.GameDataSource worldSource;
    private final NemesisPlcServiceQueue queue = new NemesisPlcServiceQueue();
    // Stateless delegate: the facade alone owns/captures this world's FIFO and recipe purposes.
    private final Sonic3kGameOverPlcService event;

    /** Captured recipe semantics, never inferred from a source address or PLC ID. */
    public enum RecipePurpose { READINESS, PHYSICAL_PATTERNS, EVENT_READINESS }

    /**
     * The unchanged public count projection is paired with this engine-private
     * native publication in every world registry. Full restore checks both keys.
     */
    public record Recipes(NemesisPlcQueueSnapshot queue, RecipePurpose activePurpose,
                          List<RecipePurpose> queuedPurposes) {
        public Recipes {
            Objects.requireNonNull(queue, "queue");
            queuedPurposes = List.copyOf(queuedPurposes);
            if ((queue.activeEntry() == null) != (activePurpose == null)
                    || queue.queuedEntries().size() != queuedPurposes.size())
                throw new IllegalArgumentException("Recipe purposes must cover the complete FIFO");
        }
    }

    private RecipePurpose activePurpose;
    private boolean awaitingRecipes; // Count-only restore cannot authorize any recipe service.
    private final ArrayDeque<RecipePurpose> queuedPurposes = new ArrayDeque<>();
    // Immutable original-ROM decode products are a reconstructible cache, not rewind state.
    private final Map<Integer, byte[]> decoded = new HashMap<>();
    private final RewindSnapshottable<Recipes> recipes = new RewindSnapshottable<>() {
        @Override public String key() { return "s3k-nemesis-recipes"; }
        @Override public Recipes capture() {
            return new Recipes(queue.capture(), activePurpose, List.copyOf(queuedPurposes));
        }
        @Override public void restore(Recipes state) {
            Objects.requireNonNull(state, "recipes");
            // The count owner restores immediately before this companion.
            if (!queue.capture().equals(state.queue()))
                throw new IllegalArgumentException("Nemesis recipe/count snapshots disagree");
            activePurpose = state.activePurpose();
            awaitingRecipes = false;
            queuedPurposes.clear();
            queuedPurposes.addAll(state.queuedPurposes());
            decoded.clear(); // Reconstruction must not publish or retain a stale decoder cursor.
        }
        @Override public void resetForMissingSnapshot() {
            if (queue.isBusy())
                throw new IllegalStateException("A nonempty Nemesis FIFO requires its recipe snapshot");
            activePurpose = null;
            awaitingRecipes = false;
            queuedPurposes.clear();
            decoded.clear();
        }
    };

    @Override public RewindSnapshottable<Recipes> recipeState() { return recipes; }

    // BEGIN HCZ1_DIRECT_FF: only the actual fresh/ordinary water producer owns this decision.
    private enum WaterSchedule { UNOWNED, DIRECT_FF }
    private WaterSchedule waterSchedule = WaterSchedule.UNOWNED;
    private java.lang.ref.WeakReference<Sonic3kLevel> waterTarget = new java.lang.ref.WeakReference<>(null);
    private Object waterTargetIdentity;
    private final Object waterSnapshotOwner = new Object();

    // BEGIN HCZ1_DIRECT_FF_SELECTED_HANDOFF: transient selection, never captured authority.
    private record WaterSelection(Object target, int featureZone, int featureAct) { }
    // Admission belongs to the actual load owner, not to a water selection
    // or rewind snapshot. Only the current target/attempt is retained.
    private Object waterLoadAttempt;
    private java.lang.ref.WeakReference<com.openggf.level.LevelManager> waterLoadManager =
            new java.lang.ref.WeakReference<>(null);
    private Object waterTargetLoadAttempt;
    private boolean waterTargetAccepted;

    private static void requireWaterLoadOwner() {
        if (com.openggf.util.EngineCallerAccess.callerOutside(Sonic3kLevelTitlePlcService.class)
                != com.openggf.level.LevelManager.class)
            throw new SecurityException("Water target admission belongs to the actual engine load owner");
    }

    @Override public Object beginLevelTargetLoad(com.openggf.level.LevelManager manager) {
        requireWaterLoadOwner();
        waterTargetAccepted = false;
        clearWaterScheduleSelection(false);
        waterLoadManager = new java.lang.ref.WeakReference<>(manager);
        waterLoadAttempt = new Object();
        return waterLoadAttempt;
    }

    @Override public void levelTargetInstalled(com.openggf.level.LevelManager manager,
                                               Object attempt, com.openggf.level.Level actualLevel) {
        requireWaterLoadOwner();
        // A repeated write of the already accepted exact target is not a
        // replacement. It creates no token and cannot revive failed admission.
        if (attempt == null && waterTargetAccepted && waterLoadManager.get() == manager
                && waterTarget.get() == actualLevel)
            return;
        if (!(actualLevel instanceof Sonic3kLevel level) || attempt == null
                || attempt != waterLoadAttempt || waterLoadManager.get() != manager) {
            waterTargetAccepted = false;
            clearWaterScheduleSelection(false);
            waterTarget.clear();
            waterTargetIdentity = null;
            waterTargetLoadAttempt = null;
            return;
        }
        if (waterTarget.get() != level || waterTargetLoadAttempt != attempt) {
            waterTargetAccepted = false;
            clearWaterScheduleSelection(false);
            waterTarget = new java.lang.ref.WeakReference<>(level);
            waterTargetIdentity = new Object();
            waterTargetLoadAttempt = attempt;
        }
    }

    @Override public void finishLevelTargetLoad(com.openggf.level.LevelManager manager,
                                                Object attempt, com.openggf.level.Level actualLevel,
                                                boolean completed) {
        requireWaterLoadOwner();
        if (!completed) {
            // Failure is durable even when a policy aborts cancellation, and
            // even when a nested load superseded this attempt's token.
            waterTargetAccepted = false;
            clearWaterScheduleSelection(false);
            return;
        }
        if (attempt != null && attempt == waterLoadAttempt && waterLoadManager.get() == manager
                && attempt == waterTargetLoadAttempt && waterTarget.get() == actualLevel)
            waterTargetAccepted = true; // Last operation: no callback can follow acceptance.
    }
    private WaterSelection loadedWaterSelection;
    private WaterSelection ordinaryWaterSelection;

    @Override public void beginWaterScheduleSelection(boolean freshLoad) {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(Sonic3kLevelTitlePlcService.class);
        if (!isWaterSelectionOwner(caller) && !(freshLoad && caller == Sonic3kLevelInitProfile.class))
            throw new SecurityException("Water selection belongs to its actual engine producer");
        clearWaterScheduleSelection(freshLoad);
    }

    private void clearWaterScheduleSelection(boolean freshLoad) {
        waterSchedule = WaterSchedule.UNOWNED;
        loadedWaterSelection = null;
        ordinaryWaterSelection = null;
        // Schedule cancellation is not a target-load outcome. In particular
        // it cannot erase a failed target's admission fence.
    }

    @Override public void retainWaterScheduleSelection(com.openggf.level.Level actualLevel,
                                                       int featureZone, int featureAct, boolean freshLoad) {
        if (!isWaterSelectionOwner(com.openggf.util.EngineCallerAccess.callerOutside(
                Sonic3kLevelTitlePlcService.class)))
            throw new SecurityException("Only the actual water owner can retain its selected identity");
        // Called only after the original water operation returns. InitWater
        // may stage a selection inside a pending assembly, but neither it nor
        // an ordinary producer creates or completes target admission.
        if (!(actualLevel instanceof Sonic3kLevel level) || waterTarget.get() != level
                || waterTargetIdentity == null || (!freshLoad && !waterTargetAccepted)) {
            clearWaterScheduleSelection(false);
            return;
        }
        var selection = new WaterSelection(waterTargetIdentity, featureZone, featureAct);
        if (freshLoad) loadedWaterSelection = selection;
        else ordinaryWaterSelection = selection;
    }

    private static boolean isWaterSelectionOwner(Class<?> caller) {
        // The owner is package-private, so compare its exact loaded engine
        // class identity by loader/name without exposing or discovering it.
        return caller.getClassLoader() == Sonic3kLevelTitlePlcService.class.getClassLoader()
                && caller.getName().equals("com.openggf.level.LevelWaterCoordinator");
    }

    private boolean waterTargetIsComplete(com.openggf.level.LevelManager manager) {
        return waterTargetAccepted && waterTargetIdentity != null
                && waterLoadManager.get() == manager;
    }
    // END HCZ1_DIRECT_FF_SELECTED_HANDOFF

    /** Opaque load/owner tokens retain no disposable manager, context or level. */
    private record WaterScheduleSnapshot(Object owner, Object target, WaterSchedule decision) { }

    private final RewindSnapshottable<WaterScheduleSnapshot> waterScheduleState = new RewindSnapshottable<>() {
        @Override public String key() { return "s3k-nemesis-water-schedule"; }
        @Override public WaterScheduleSnapshot capture() {
            return new WaterScheduleSnapshot(waterSnapshotOwner, waterTargetIdentity, waterSchedule);
        }
        @Override public void restore(WaterScheduleSnapshot state) {
            Objects.requireNonNull(state, "water schedule");
            loadedWaterSelection = null;
            ordinaryWaterSelection = null;
            waterSchedule = WaterSchedule.UNOWNED;
            if (state.owner() != waterSnapshotOwner)
                throw new IllegalArgumentException("Water schedule belongs to another world PLC owner");
            // Restoring never queries a camera, replays a producer or borrows a
            // replacement level's identity. Count restore ran before this key.
            if (waterTargetAccepted && state.target() != null
                    && state.target() == waterTargetIdentity && waterTarget.get() != null)
                waterSchedule = state.decision();
        }
        @Override public void resetForMissingSnapshot() {
            clearWaterScheduleSelection(false);
        }
    };

    @Override public RewindSnapshottable<?> ordinaryScheduleState() { return waterScheduleState; }

    void recordFreshWaterSchedule(com.openggf.level.LevelManager manager, Sonic3kLevel level,
                                  int physicalCameraX) {
        var selection = loadedWaterSelection;
        loadedWaterSelection = null;
        waterSchedule = WaterSchedule.UNOWNED;
        if (selection != null && selection.target() == waterTargetIdentity && waterTarget.get() == level) {
            recordWaterSchedule(manager, level, physicalCameraX, selection.featureZone(), selection.featureAct());
        }
    }

    void recordOrdinaryWaterSchedule(com.openggf.level.LevelManager manager, int physicalCameraX,
                                    int selectedProviderZone) {
        var selection = ordinaryWaterSelection;
        ordinaryWaterSelection = null;
        waterSchedule = WaterSchedule.UNOWNED;
        if (selection != null && selection.target() == waterTargetIdentity
                && selection.featureZone() == selectedProviderZone && waterTargetIsComplete(manager))
            recordWaterSchedule(manager,
                    manager.getCurrentLevel() instanceof Sonic3kLevel level ? level : null,
                    physicalCameraX, selection.featureZone(), selection.featureAct());
    }

    private void recordWaterSchedule(com.openggf.level.LevelManager manager, Sonic3kLevel level,
                                     int physicalCameraX, int selectedFeatureZone, int selectedFeatureAct) {
        waterSchedule = WaterSchedule.UNOWNED;
        var world = manager.getWorldSession();
        var objects = manager.getObjectManager();
        var services = objects != null ? objects.getObjectServices() : null;
        if (level == null || waterTarget.get() != level || world == null || world.getCurrentLevel() != level
                || manager.getCurrentLevel() != level
                || com.openggf.game.session.WorldPlcLifecycleAccess.owned(world) != this
                || services == null || services.worldSession() != world
                || services.levelManager() != manager || services.currentLevel() != level) {
            waterTargetAccepted = false;
            waterTarget.clear();
            waterTargetIdentity = null;
            return;
        }
        // Native044 Level $6182 writes $8AFF; the subsequent actual water
        // producer at $6C52/$6C86 writes $FF for stock $0100 and unsigned
        // physical Camera_X_pos < $900. This branch does not depend on the
        // water height, fullscreen/deform flags or player-death predicates.
        var water = services.waterSystem();
        if (level.hasStockRomZoneIdentity()
                && manager.getCurrentZone() == com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_HCZ
                && manager.getCurrentAct() == 0
                && selectedFeatureZone == manager.getCurrentZone()
                && selectedFeatureAct == manager.getCurrentAct()
                && water != null && water.hasWater(manager.getCurrentZone(), manager.getCurrentAct())
                && water.isLiveWaterFlagSet(manager.getCurrentZone(), manager.getCurrentAct())
                && (physicalCameraX & 0xFFFF) < 0x900)
            waterSchedule = WaterSchedule.DIRECT_FF;
        // Every other reached producer remains UNOWNED. In particular this
        // stores neither a guessed counter nor deferred H-int acceptance.
    }
    // END HCZ1_DIRECT_FF

    private void requireRecipes() {
        if (awaitingRecipes)
            throw new IllegalStateException("Nemesis count restore requires its registered recipe companion");
    }

    private void appendRecipe(PlcDefinition definition, List<Integer> counts, RecipePurpose purpose) {
        requireRecipes();
        queue.append(definition, counts);
        for (int index = 0; index < counts.size(); index++) queuedPurposes.addLast(purpose);
    }

    private void prepare(RecipePurpose purpose) {
        requireRecipes();
        if (queue.activeEntry() == null && queuedPurposes.peekFirst() == purpose) {
            queue.prepareHead();
            activePurpose = queuedPurposes.removeFirst();
        }
    }

    private void consume(int patterns) {
        queue.servicePatterns(patterns);
        if (queue.activeEntry() == null) activePurpose = null;
    }

    public Sonic3kLevelTitlePlcService(Rom rom) {
        this.rom = Objects.requireNonNull(rom);
        this.worldSource = null;
        this.event = null; // Existing detached title/results component owner remains title-only.
    }

    /** Opening a world is ROM-free; the first ROM-dependent submission pins its actual source. */
    Sonic3kLevelTitlePlcService(com.openggf.game.GameDataSource worldSource) {
        this.worldSource = Objects.requireNonNull(worldSource, "worldSource");
        this.event = new Sonic3kGameOverPlcService(this); // No ROM resolution during world opening.
    }

    @Override
    public <T> T worldPlcService(Class<T> type) {
        return type == Sonic3kGameOverPlcService.class ? type.cast(event) : null;
    }

    private Rom requireRom() throws IOException {
        if (rom == null) {
            rom = worldSource.rom().orElseThrow(() -> new IOException("World PLC ROM is unavailable"));
        }
        return rom;
    }

    /** Level: clears the queue, appends the primary level PLC, then the character PLC. */
    public void beginFreshZoneTitle(int zone, int act, String mainCharacter) throws IOException {
        Rom rom = requireRom(); // Fail before even clearing the old queue when the source is absent.
        resetForMissingSnapshot();
        var bootstrap = Sonic3kBootstrapResolver.resolve(zone, act);
        int index = Sonic3k.resolveLevelLoadBlockIndex(zone, act, bootstrap);
        int primary = rom.readByte(Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                + index * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE) & 0xFF;
        if (primary != 0) append(primary);
        // Level/loc_60B4: native Player_mode selects the common HUD/life PLC.
        append("knuckles".equalsIgnoreCase(mainCharacter) ? 5
                : "tails".equalsIgnoreCase(mainCharacter) ? 7 : 1);
    }

    /** SpecialStage_Results: Load_PLC_Raw PLC_SpecialResultsText before loc_2E0C4. */
    public void beginSpecialStageResults() throws IOException {
        Rom rom = requireRom();
        resetForMissingSnapshot();
        var definition = new PlcDefinition(-1,
                List.of(new PlcEntry(
                        Sonic3kConstants.ART_NEM_RING_HUD_TEXT_ADDR,
                        Sonic3kConstants.VRAM_SS_RESULTS_HUD_TEXT)));
        appendRecipe(definition, NemesisPlcPatternCounts.derive(rom, definition), RecipePurpose.READINESS);
    }

    /**
     * Load_PLC_Raw appends the complete ROM definition without replacing either
     * the prepared head or preceding descriptors. Decode validates workload only;
     * no patterns are applied here. Ordinary service/publication remains separate.
     * The logical queue supports at most fifteen occupied entries; the shipped
     * FixBugs=0 retained-sixteenth-slot behavior is not modeled by this owner.
     */
    public void appendRaw(int definitionAddress) throws IOException {
        appendRaw(definitionAddress, RecipePurpose.PHYSICAL_PATTERNS);
    }

    /** Explicit readiness-only component ingress; ordinary raw producers use appendRaw(int). */
    public void appendRaw(int definitionAddress, RecipePurpose purpose) throws IOException {
        Objects.requireNonNull(purpose, "purpose");
        Rom rom = requireRom();
        long size = rom.getSize();
        if (definitionAddress < 0 || (definitionAddress & 1) != 0
                || (long) definitionAddress + 2 > size) {
            throw new IOException("Raw Nemesis PLC definition is outside aligned ROM data");
        }
        int countMinusOne = (short) rom.read16BitAddr(definitionAddress);
        if (countMinusOne < 0) return; // Native signed count: an empty definition appends nothing.
        int count = countMinusOne + 1;
        long end = (long) definitionAddress + 2 + (long) count * 6;
        if (end > size) {
            throw new IOException("Raw Nemesis PLC descriptors exceed ROM data");
        }
        int occupied = queue.queuedEntryCount() + (queue.capture().activeEntry() != null ? 1 : 0);
        if (count > 15 - occupied) {
            throw new IllegalStateException("Raw Nemesis PLC exceeds supported logical queue occupancy");
        }
        var entries = new ArrayList<PlcEntry>(count);
        for (int index = 0; index < count; index++) {
            long address = (long) definitionAddress + 2 + (long) index * 6;
            int source = rom.read32BitAddr(address) & 0x00FFFFFF;
            int destinationBytes = rom.read16BitAddr(address + 4) & 0xFFFF;
            if ((destinationBytes & 31) != 0) {
                throw new IOException("Raw Nemesis PLC destination is not representable as whole tiles");
            }
            entries.add(new PlcEntry(source, destinationBytes / 32));
        }
        // -1 is the existing unindexed/raw definition marker, not a native PLC ID.
        var definition = new PlcDefinition(-1, List.copyOf(entries));
        var patternCounts = NemesisPlcPatternCounts.derive(rom, definition);
        // All descriptor reads, decode/count derivation and capacity checks precede mutation.
        appendRecipe(definition, patternCounts, purpose);
    }

    private void append(int plcId) throws IOException {
        Rom rom = requireRom();
        var definition = Sonic3kPlcLoader.parsePlc(rom, plcId);
        // FixBugs=0 leaves the sixteenth descriptor retained after shifting.
        // Fresh-level recipes must stay within the retail fifteen usable slots.
        if (queue.queuedEntryCount() + definition.entries().size() > 15) {
            throw new IllegalStateException("fresh title PLCs occupy the retained sixteenth slot");
        }
        appendRecipe(definition, NemesisPlcPatternCounts.derive(rom, definition), RecipePurpose.READINESS);
    }

    /** Event Load_PLC_2 clears the same FIFO, including its paired recipe semantics. */
    void beginGameOverEvent() throws IOException {
        Rom rom = requireRom();
        var definition = Sonic3kPlcLoader.parsePlc(rom, 3);
        var counts = NemesisPlcPatternCounts.derive(rom, definition);
        // Complete ROM validation precedes Clear_Nem_Queue. Preserve independent water ownership.
        queue.restore(new NemesisPlcQueueSnapshot(null, List.of()));
        activePurpose = null;
        queuedPurposes.clear();
        awaitingRecipes = false;
        decoded.clear();
        appendRecipe(definition, counts, RecipePurpose.EVENT_READINESS);
    }

    void serviceEventReadiness(int patterns) {
        requireRecipes();
        if (activePurpose == RecipePurpose.READINESS
                || activePurpose == RecipePurpose.EVENT_READINESS) consume(patterns);
    }

    void prepareEventReadiness() {
        prepare(RecipePurpose.EVENT_READINESS);
    }

    public boolean isBusy() {
        return queue.isBusy();
    }

    @Override
    public void serviceVBlank(PlcLifecyclePhase phase) {
        requireRecipes();
        if (activePurpose != RecipePurpose.READINESS
                && activePurpose != RecipePurpose.EVENT_READINESS) return;
        if (phase == PlcLifecyclePhase.LEVEL_TITLE_CARD) {
            // VInt_A_C -> Process_Nem_Queue: six patterns from the prepared
            // head. A VInt_0 lag closure services none and cannot arm a head.
            consume(6);
        } else if (phase == PlcLifecyclePhase.SPECIAL_STAGE_RESULTS) {
            // VInt_1E -> Process_Nem_Queue_2: three patterns per prepared head.
            consume(3);
        } else if (event != null) {
            // Ordinary/pause/fade readiness service shares the same prepared head exactly once.
            event.serviceVBlank(phase);
        } else if (phase == PlcLifecyclePhase.ORDINARY_LEVEL
                || phase == PlcLifecyclePhase.NORMAL_PAUSE) {
            consume(3); // Detached component owners retain the prepared decoder's native budget too.
        } else if (phase == PlcLifecyclePhase.PALETTE_FADE) {
            consume(6);
        }
    }

    @Override
    public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
        return phase == PlcLifecyclePhase.LEVEL_TITLE_CARD
                || phase == PlcLifecyclePhase.SPECIAL_STAGE_RESULTS
                || phase == PlcLifecyclePhase.ORDINARY_LEVEL
                || (event != null && event.hasPreparationBoundary(phase));
    }

    @Override
    public void prepareAfterLoop(PlcLifecyclePhase phase) {
        if (phase == PlcLifecyclePhase.LEVEL_TITLE_CARD
                || phase == PlcLifecyclePhase.SPECIAL_STAGE_RESULTS) {
            // loc_62CC and loc_2E0C4 can arm either readiness producer, never physical patterns.
            prepare(queuedPurposes.peekFirst() == RecipePurpose.EVENT_READINESS
                    ? RecipePurpose.EVENT_READINESS : RecipePurpose.READINESS);
        } else if (phase == PlcLifecyclePhase.ORDINARY_LEVEL
                && (event == null || queuedPurposes.peekFirst() == RecipePurpose.PHYSICAL_PATTERNS)) {
            prepare(RecipePurpose.PHYSICAL_PATTERNS);
        } else if (event != null) {
            // Only explicit EVENT_READINESS can be armed here; generic READINESS cannot be skipped.
            event.prepareAfterLoop(phase);
        }
    }

    /**
     * Native044 VInt_8/Do_Updates: after queued DMA, at most three complete
     * patterns from one prepared head. Water/H-int deferral is not represented
     * by this ordinary-loop capability; no H-int schedule is synthesized here.
     */
    @Override
    public void publishOrdinaryPatterns(com.openggf.level.LevelManager manager,
            com.openggf.level.resources.KosinskiModuleQueue journal) {
        requireRecipes();
        if (activePurpose != RecipePurpose.PHYSICAL_PATTERNS || queue.activeEntry() == null) return;
        if (manager == null || journal == null
                || !(manager.getCurrentLevel() instanceof Sonic3kLevel level)
                || manager.getObjectManager() == null) return;
        var world = manager.getWorldSession();
        var services = manager.getObjectManager().getObjectServices();
        if (world == null || com.openggf.game.session.WorldPlcLifecycleAccess.owned(world) != this
                || world.getCurrentLevel() != level || services.worldSession() != world
                || services.levelManager() != manager || services.currentLevel() != level
                || services.kosinskiModuleQueue() != journal)
            throw new IllegalArgumentException("Physical Nemesis target must belong to its actual world/context");

        // Native044 VInt_8 uses the preceding water producer's retained
        // decision. No publication-time camera/height/deferred-state inference.
        if (services.waterSystem() == null) return;
        if (services.waterSystem().hasWater(manager.getCurrentZone(), manager.getCurrentAct())) {
            if (waterTarget.get() != level) {
                waterSchedule = WaterSchedule.UNOWNED;
                waterTargetAccepted = false;
                waterTarget.clear();
                waterTargetIdentity = null;
                return;
            }
            if (waterSchedule != WaterSchedule.DIRECT_FF || !waterTargetIsComplete(manager)) return;
        }

        var entry = queue.activeEntry();
        final byte[] bytes;
        try {
            byte[] cached = decoded.get(entry.sourceAddress());
            if (cached == null) {
                cached = com.openggf.level.resources.PlcParser.decompressEntryRaw(requireRom(),
                        new PlcEntry(entry.sourceAddress(), entry.destinationTile()));
                if (cached.length != Math.multiplyExact(entry.totalPatterns(), 32))
                    throw new IOException("Nemesis archive differs from its captured pattern count");
                decoded.put(entry.sourceAddress(), cached);
            }
            bytes = cached;
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
        // Capture the head's initial remaining count so finishing it never
        // spends spare budget on a successor, even if another entry is queued.
        int budget = Math.min(3, entry.remainingPatterns());
        int cursor = entry.totalPatterns() - entry.remainingPatterns();
        for (int pattern = 0; pattern < budget; pattern++) {
            int offset = Math.multiplyExact(cursor + pattern, 32);
            byte[] wholePattern = java.util.Arrays.copyOfRange(bytes, offset, offset + 32);
            // Native VDP destination and Nem_decomp_destination are 16-bit.
            int destination = (entry.destinationTile() * 32 + offset) & 0xFFFF;
            if (!Sonic3kPlcLoader.publishConfirmedPattern(journal, services, level,
                    destination, wholePattern)) return;
            consume(1); // Only a confirmed complete physical write retires a pattern.
        }
    }

    @Override
    public String key() { return "s3k-level-title-plc"; }

    @Override
    public NemesisPlcQueueSnapshot capture() { return queue.capture(); }

    @Override
    public void restore(NemesisPlcQueueSnapshot snapshot) {
        queue.restore(snapshot);
        loadedWaterSelection = null;
        ordinaryWaterSelection = null;
        waterSchedule = WaterSchedule.UNOWNED; // Only the separate private schedule key can restore authority.
        // A count projection cannot classify a raw descriptor as readiness.
        // No phase may service/append/prepare until the registered companion
        // validates and restores the complete per-entry recipe state.
        activePurpose = null;
        queuedPurposes.clear();
        awaitingRecipes = queue.isBusy();
        decoded.clear();
    }

    @Override
    public void resetForMissingSnapshot() {
        restore(new NemesisPlcQueueSnapshot(null, List.of()));
    }
}
