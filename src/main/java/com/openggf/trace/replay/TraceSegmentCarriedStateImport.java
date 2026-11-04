package com.openggf.trace.replay;

import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.NativeGameStateOps;
import com.openggf.game.ShieldType;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.specialstage.S3kSpecialStageSelection;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceFiles;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceRunManifest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The single, named mechanism that imports a run segment's carried run state
 * into the engine at segment entry.
 *
 * <h2>Rule</h2>
 * State may be carried from the previous segment into a new one; state is
 * never invented mid-run (user ruling, 2026-11-03). {@link #resolve} turns
 * recorder observations into a {@link TraceSegmentCarriedState};
 * {@link #importAtSegmentEntry} writes it once, before the first recorded row
 * is driven, into the same engine owners the ROM's own restore writes
 * ({@code Load_Starpost_Settings} loc_2D2C2, sonic3k.asm:61835-61856, and the
 * {@code SpawnLevelMainSprites} power-up restore loc_69E0, sonic3k.asm:8311-8355).
 * After entry the engine computes everything; no row is consulted again.
 *
 * <h2>Sources (all recorder-observed, none hand-authored)</h2>
 * <ul>
 *   <li>Rings and Chaos Emerald count: the run manifest's transition records,
 *       sampled at the mode change that leads into this segment.</li>
 *   <li>{@code Collected_emeralds_array}: folded through the ROM's own update
 *       rules over the manifest's earlier special stages (recorded
 *       {@code special_stage_index}, recorded outcome) -- see
 *       {@link S3kRunEmeraldLedger}.</li>
 *   <li>{@code Collected_special_ring_array}: the giant ring each earlier
 *       giant-ring-ended segment of this zone visit was recorded touching.</li>
 *   <li>Player 1's shield and both players' path bits: this segment's own
 *       recorded entry row. The ROM restores them during the level load
 *       ({@code Saved2_solid_bits}, {@code Saved_status_secondary}); no
 *       transition record samples the saved words themselves.</li>
 * </ul>
 * Variables a recording cannot establish are listed in
 * {@link TraceSegmentCarriedState#unresolved()} instead of being guessed.
 *
 * <p>Only cold standalone entries call this: run-chain continuations carry
 * their state in the live engine and never come here.
 */
public final class TraceSegmentCarriedStateImport {

    // status_secondary bits: Status_FireShield 4, Status_LtngShield 5,
    // Status_BublShield 6 (loc_69E0 masks exactly these three).
    private static final int FIRE_SHIELD = 1 << 4;
    private static final int LIGHTNING_SHIELD = 1 << 5;
    private static final int BUBBLE_SHIELD = 1 << 6;
    private static final int SAVED_SHIELD_MASK = FIRE_SHIELD | LIGHTNING_SHIELD | BUBBLE_SHIELD;

    private TraceSegmentCarriedStateImport() {
    }

    /**
     * Resolves and imports the carried run state for a cold standalone segment
     * entry. Called once, from the declared-position entry path, before the
     * camera, level events and first driven row.
     *
     * @return the state that was imported (for reports and probes)
     */
    public static TraceSegmentCarriedState importAtSegmentEntry(
            TraceData trace, AbstractPlayableSprite player) {
        Objects.requireNonNull(trace, "trace");
        // Saved_solid_bits / Saved2_solid_bits (Load_Starpost_Settings,
        // sonic3k.asm:61815/61847; S2 Obj79_LoadData s2.asm:44827) put the star
        // post's path pair back on Player 1 at the reload.
        applyRecordedSolidBits(trace, player);
        TraceSegmentCarriedState carried = resolve(trace);
        applyRunState(carried, player);
        return carried;
    }

    /**
     * Imports a special-stage segment's carried emerald state and returns the
     * stage the ROM's {@code sub_85B0} selects from it.
     */
    public static SpecialStageEntry importAtSpecialStageEntry(
            TraceSegmentCarriedState carried) {
        Objects.requireNonNull(carried, "carried");
        if (carried.specialStageCursor() == null
                || carried.superEmeraldStageSet() == null
                || carried.emeraldStates() == null) {
            throw new IllegalStateException(
                    "special-stage segment has no recorded selection state: "
                            + carried.unresolved());
        }
        applyEmeraldState(carried);
        int stage = S3kSpecialStageSelection.selectStage(
                carried.specialStageCursor(),
                carried.superEmeraldStageSet(),
                carried.emeraldStates(),
                carried.chaosEmeraldCount() == null ? 0 : carried.chaosEmeraldCount(),
                carried.superEmeraldCount() == null ? 0 : carried.superEmeraldCount());
        return new SpecialStageEntry(stage, carried.superEmeraldStageSet());
    }

    /** The stage {@code sub_85B0} loads and whether it is the Super Emerald set. */
    public record SpecialStageEntry(int stageIndex, boolean superEmeraldSet) {
    }

    // ------------------------------------------------------------------ resolve

    /** Resolves the carried state of a trace loaded from disk. */
    public static TraceSegmentCarriedState resolve(TraceData trace) {
        Optional<Path> dir = trace.sourceDirectory();
        if (dir.isEmpty()) {
            return TraceSegmentCarriedState.none("in-memory trace: no run context");
        }
        TraceSegmentCarriedState base = resolve(dir.get(), trace.metadata());
        if (!"s3k".equals(trace.metadata().game())) {
            return base;
        }
        return withPlayer1Shield(base, recordedEntryShield(trace));
    }

    /**
     * Resolves the run-level part of a segment's carried state from its run
     * manifest. Pure: reads committed fixtures only.
     */
    public static TraceSegmentCarriedState resolve(Path traceDir, TraceMetadata meta) {
        Objects.requireNonNull(traceDir, "traceDir");
        Objects.requireNonNull(meta, "meta");
        Optional<RunContext> located = locateRun(traceDir, meta);
        if (located.isEmpty()) {
            return new TraceSegmentCarriedState(
                    null, null, null, null, null, List.of(), null, null, null,
                    meta.lastStarPostHit(), null, null, null, null, List.of(),
                    List.of("no run manifest names this segment"));
        }
        RunContext run = located.get();
        List<String> unresolved = new ArrayList<>();
        List<TraceRunManifest.Segment> segments = run.manifest().segments();
        int target = run.segmentIndex();
        TraceRunManifest.Segment segment = segments.get(target);
        TraceRunManifest.Transition inbound = inboundTransition(run.manifest(), target);

        Integer rings = null;
        if (inbound != null && "s3k".equals(run.manifest().game())) {
            // S3K only: a return reloads through Load_Starpost_Settings, whose
            // Special_bonus_entry_flag arm restores Saved2_ring_count (loc_2D2C2);
            // stage_exit records that Ring_count. Into a bonus stage the ROM keeps
            // the level's Ring_count across the Restart_level_flag reload, recorded
            // as rings_before. Other entries (S2's level_advance, star-post
            // returns) clear or rebuild rings in the ROM, so nothing is carried.
            if ("stage_exit".equals(inbound.entryKind())) {
                rings = inbound.ringsAfter();
            } else if ("bonus_stage".equals(segment.kind())) {
                rings = inbound.ringsBefore();
            }
        }

        Integer chaos = latestChaosEmeraldCount(run.manifest(), target);
        S3kRunEmeraldLedger.Result ledger = "s3k".equals(run.manifest().game())
                ? S3kRunEmeraldLedger.fold(run.manifest(), run.runDir(), target)
                : null;
        List<Integer> emeraldStates = null;
        Integer superCount = null;
        Integer cursor = null;
        Boolean superSet = null;
        if (ledger != null && ledger.consistent()) {
            if (chaos != null && chaos != ledger.chaosEmeraldCount()) {
                unresolved.add("Collected_emeralds_array: ledger chaos count "
                        + ledger.chaosEmeraldCount() + " disagrees with manifest " + chaos);
            } else {
                emeraldStates = ledger.states();
                superCount = ledger.superEmeraldCount();
                if (chaos == null && ledger.chaosEmeraldCount() == 0) {
                    emeraldStates = null;
                    superCount = null;
                }
            }
            if ("special_stage".equals(segment.kind())) {
                cursor = segment.specialStageIndex();
                superSet = ledger.targetSuperEmeraldSet();
                emeraldStates = ledger.states();
                superCount = ledger.superEmeraldCount();
            }
        } else if (ledger != null) {
            unresolved.add("Collected_emeralds_array: " + ledger.note());
        }

        List<TraceSegmentCarriedState.ConsumedGiantRing> giantRings =
                "level".equals(segment.kind()) && "s3k".equals(run.manifest().game())
                        ? consumedGiantRingsThisZoneVisit(run, target, unresolved)
                        : List.of();

        // level_entry_ram: the recorder's PreLevel sample of the saved blocks and
        // carried counters. Recorded values replace the derived ones; any
        // difference is reported, because one of the two is then wrong.
        java.util.Map<String, Integer> ram = meta.levelEntryRam();
        List<String> disagreements = new ArrayList<>();
        TraceSegmentCarriedState.LevelEntryRestore restore = null;
        Integer lives = null;
        Integer ringArray = null;
        Integer recordedCursor = null;
        if (ram != null && "s3k".equals(run.manifest().game())) {
            lives = ram.get("life_count");
            ringArray = ram.get("collected_special_ring_array");
            recordedCursor = ram.get("current_special_stage");
            List<Integer> recordedEmeralds = recordedEmeraldStates(ram);
            if (recordedEmeralds != null) {
                if (emeraldStates != null && !emeraldStates.equals(recordedEmeralds)) {
                    disagreements.add("Collected_emeralds_array: derived " + emeraldStates
                            + " recorded " + recordedEmeralds);
                }
                emeraldStates = recordedEmeralds;
                Integer recordedChaos = ram.get("chaos_emerald_count");
                Integer recordedSuper = ram.get("super_emerald_count");
                if (recordedChaos != null && chaos != null && !recordedChaos.equals(chaos)) {
                    disagreements.add("Chaos_emerald_count: manifest " + chaos
                            + " recorded " + recordedChaos);
                }
                if (recordedSuper != null && superCount != null && !recordedSuper.equals(superCount)) {
                    disagreements.add("Super_emerald_count: derived " + superCount
                            + " recorded " + recordedSuper);
                }
                if (recordedChaos != null) {
                    chaos = recordedChaos;
                }
                if (recordedSuper != null) {
                    superCount = recordedSuper;
                }
            }
            Integer starPost = meta.lastStarPostHit();
            if ("level".equals(segment.kind()) && starPost != null && starPost != 0) {
                restore = recordedRestore(ram, starPost);
                if (restore != null) {
                    if (rings != null && rings != restore.ringCount()) {
                        disagreements.add("Ring_count: manifest " + rings + " restored "
                                + restore.ringCount() + " (" + restore.block() + ")");
                    }
                    rings = restore.ringCount();
                }
            } else if ("bonus_stage".equals(segment.kind()) && ram.get("ring_count") != null) {
                // A bonus stage keeps the live Ring_count across its reload.
                int live = ram.get("ring_count");
                if (rings != null && rings != live) {
                    disagreements.add("Ring_count: manifest " + rings + " recorded " + live);
                }
                rings = live;
            }
            unresolved.removeIf(note -> note.startsWith("Collected_special_ring_array"));
        } else {
            unresolved.add("level_entry_ram not recorded: Saved_*/Saved2_* restore blocks,"
                    + " Life_count and the carried counters are absent");
        }

        String provenance = run.manifest().runId() + " segment " + target
                + " (" + segment.dir() + ")";
        return new TraceSegmentCarriedState(
                provenance, rings, chaos, emeraldStates, superCount,
                ringArray != null ? List.of() : giantRings,
                null, cursor, superSet, meta.lastStarPostHit(), restore, lives,
                ringArray, recordedCursor, disagreements, unresolved);
    }

    private static List<Integer> recordedEmeraldStates(java.util.Map<String, Integer> ram) {
        List<Integer> states = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Integer state = ram.get("collected_emerald_" + i);
            if (state == null) {
                return null;
            }
            states.add(state);
        }
        return List.copyOf(states);
    }

    /**
     * {@code Load_Starpost_Settings} (sonic3k.asm:61800-61856) from the recorded
     * blocks. {@code Special_bonus_entry_flag} selects {@code Saved2_*}
     * (loc_2D2C2); otherwise {@code Saved_*}, whose rings and extra-life flags
     * are cleared unless {@code Respawn_table_keep} is set. Either way the
     * timer is reloaded with {@code Timer_frame = 59} and {@code Timer_second}
     * stepped back one. The star-post checkpoint is the {@code Saved_*} block.
     */
    private static TraceSegmentCarriedState.LevelEntryRestore recordedRestore(
            java.util.Map<String, Integer> ram, int lastStarPostHit) {
        Integer flag = ram.get("special_bonus_entry_flag");
        if (flag == null) {
            return null;
        }
        String block = flag != 0 ? "saved2" : "saved";
        Integer[] w = {ram.get(block + "_ring_count"), ram.get(block + "_extra_life_flags"),
                ram.get(block + "_timer"), ram.get(block + "_camera_max_y_pos"),
                ram.get(block + "_dynamic_resize_routine"), ram.get(block + "_mean_water_level"),
                ram.get(block + "_water_full_screen_flag"), ram.get(block + "_solid_bits")};
        for (Integer word : w) {
            if (word == null) {
                return null;
            }
        }
        boolean ringsKept = flag != 0 || ram.getOrDefault("respawn_table_keep", 0) != 0;
        TraceSegmentCarriedState.Checkpoint checkpoint = recordedCheckpoint(ram, lastStarPostHit);
        return new TraceSegmentCarriedState.LevelEntryRestore(
                flag != 0 ? "Saved2" : "Saved",
                ringsKept ? w[0] : 0, ringsKept ? w[1] : 0,
                restoredTimerFrames(w[2]), w[3], w[4], w[5], w[6] != 0,
                (w[7] >> 8) & 0xFF, w[7] & 0xFF, checkpoint);
    }

    private static TraceSegmentCarriedState.Checkpoint recordedCheckpoint(
            java.util.Map<String, Integer> ram, int lastStarPostHit) {
        String[] keys = {"saved_x_pos", "saved_y_pos", "saved_camera_x_pos", "saved_camera_y_pos",
                "saved_ring_count", "saved_extra_life_flags", "saved_timer",
                "saved_camera_max_y_pos", "saved_dynamic_resize_routine", "saved_solid_bits"};
        int[] v = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            Integer word = ram.get(keys[i]);
            if (word == null) {
                return null;
            }
            v[i] = word;
        }
        return new TraceSegmentCarriedState.Checkpoint(lastStarPostHit & 0x7F,
                v[0], v[1], v[2], v[3], v[4], v[5], timerFrames(v[6]), v[7], v[8],
                (v[9] >> 8) & 0xFF, v[9] & 0xFF);
    }

    /** {@code Timer} longword (minute word, second, frame) as engine frames. */
    private static long timerFrames(int timer) {
        int minute = (timer >> 16) & 0xFF;
        int second = (timer >> 8) & 0xFF;
        int frame = timer & 0xFF;
        return ((long) minute * 60 + second) * 60 + frame;
    }

    /** The restore forces {@code Timer_frame = 59} and steps the second back one. */
    private static long restoredTimerFrames(int timer) {
        int minute = (timer >> 16) & 0xFF;
        int second = (timer >> 8) & 0xFF;
        return Math.max(0L, ((long) minute * 60 + second) * 60 - 1);
    }

    private record RunContext(TraceRunManifest manifest, Path runDir, int segmentIndex) {
    }

    /**
     * A run segment is named by its own metadata ({@code run_id},
     * {@code segment_index}). The manifest sits beside the segment directory
     * for a run checkout, or under {@code runs/<run_id>/} for a standalone copy;
     * either way the manifest segment must agree on the BK2 offset and length.
     */
    private static Optional<RunContext> locateRun(Path traceDir, TraceMetadata meta) {
        Integer index = meta.segmentIndex();
        if (index == null || index < 0) {
            return Optional.empty();
        }
        List<Path> candidates = new ArrayList<>();
        Path parent = traceDir.toAbsolutePath().getParent();
        if (parent != null) {
            candidates.add(parent);
            if (meta.runId() != null && !meta.runId().isBlank()) {
                candidates.add(parent.resolve("runs").resolve(meta.runId()));
            }
        }
        for (Path runDir : candidates) {
            Path manifestPath = runDir.resolve("run_manifest.json");
            if (!Files.isRegularFile(manifestPath)) {
                continue;
            }
            TraceRunManifest manifest;
            try {
                manifest = TraceRunManifest.load(manifestPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (meta.runId() != null && !meta.runId().equals(manifest.runId())) {
                continue;
            }
            if (index >= manifest.segments().size()) {
                continue;
            }
            TraceRunManifest.Segment segment = manifest.segments().get(index);
            if (segment.bk2FrameOffset() != meta.bk2FrameOffset()
                    || (meta.traceFrameCount() > 0
                            && segment.traceFrameCount() != meta.traceFrameCount())) {
                continue;
            }
            return Optional.of(new RunContext(manifest, runDir, index));
        }
        return Optional.empty();
    }

    static TraceRunManifest.Transition inboundTransition(TraceRunManifest manifest, int target) {
        for (TraceRunManifest.Transition t : manifest.transitions()) {
            if (t.toSegment() == target) {
                return t;
            }
        }
        return null;
    }

    static TraceRunManifest.Transition outboundTransition(TraceRunManifest manifest, int source) {
        for (TraceRunManifest.Transition t : manifest.transitions()) {
            if (t.fromSegment() == source) {
                return t;
            }
        }
        return null;
    }

    /**
     * {@code Chaos_emerald_count} at the latest recorded transition into or
     * before this segment. Level play cannot change it, so the most recent
     * boundary sample is the carried value.
     */
    private static Integer latestChaosEmeraldCount(TraceRunManifest manifest, int target) {
        Integer latest = null;
        int latestTo = -1;
        for (TraceRunManifest.Transition t : manifest.transitions()) {
            if (t.toSegment() > target || t.toSegment() < latestTo) {
                continue;
            }
            Integer value = t.emeraldsAfter() != null ? t.emeraldsAfter() : t.emeraldsBefore();
            if (value != null) {
                latest = value;
                latestTo = t.toSegment();
            }
        }
        return latest;
    }

    /**
     * Giant rings consumed earlier in this zone visit. The array survives
     * everything except a full {@code SaveGame} ({@code loc_C4CC}), which the
     * act-2 results run before the next zone (loc_2DCB6, sonic3k.asm:62716-62718).
     * Walking back from the target, the visit therefore spans special and
     * bonus stages, the Hidden Palace excursions a giant ring opens (a level
     * of another zone the run left through a special stage), and the level
     * segments of the target's zone; a level of another zone that the run
     * left by advancing to the next level ends it.
     */
    private static List<TraceSegmentCarriedState.ConsumedGiantRing> consumedGiantRingsThisZoneVisit(
            RunContext run, int target, List<String> unresolved) {
        List<TraceRunManifest.Segment> segments = run.manifest().segments();
        Integer zone = segments.get(target).zoneId();
        List<TraceSegmentCarriedState.ConsumedGiantRing> rings = new ArrayList<>();
        for (int i = target - 1; i >= 0; i--) {
            TraceRunManifest.Segment s = segments.get(i);
            if (!"level".equals(s.kind())) {
                continue;
            }
            if (!Objects.equals(s.zoneId(), zone)) {
                TraceRunManifest.Segment next = segments.get(i + 1);
                if ("level".equals(next.kind())) {
                    break;
                }
                continue;
            }
            rings.addAll(recordedGiantRingTouches(run.runDir().resolve(s.dir()), s.dir()));
        }
        unresolved.add("Collected_special_ring_array: rings spent for 50 rings with seven"
                + " emeralds (loc_61794) leave no flash, so no recording establishes them");
        return rings;
    }

    /**
     * Giant-ring touches the recorder observed anywhere in a segment. A touch
     * leaves a signature nothing else at a giant-ring position has:
     * {@code SSEntryFlash_Init} copies the ring's position into
     * {@code Obj_SSEntryFlash}, allocated in another SST slot, so two slots hold
     * the ring's position and subtype on the same frame until the flash deletes
     * the ring at its fourth frame ({@code SSEntryFlash_Main}). The flash's
     * subtype is the bit {@code loc_618D0} sets (sonic3k.asm:128477-128481). An
     * idle ring, or one deleted off screen ({@code loc_61928} to
     * {@code loc_6196A}), is only ever one slot. At import each touch is
     * confirmed against the loaded level's giant-ring placements (same position
     * and subtype). Measured over every committed S3K run segment, the
     * signature at a giant-ring position occurs exactly once per segment that
     * entered a special stage or the shrine through a giant ring, and nowhere else.
     */
    static List<TraceSegmentCarriedState.ConsumedGiantRing> recordedGiantRingTouches(
            Path segmentDir, String dirName) {
        Path aux = TraceFiles.resolve(segmentDir, "aux_state.jsonl");
        if (aux == null) {
            return List.of();
        }
        java.util.Map<String, TraceSegmentCarriedState.ConsumedGiantRing> touches =
                new java.util.LinkedHashMap<>();
        java.util.Map<String, Set<Integer>> slotsThisFrame = new java.util.HashMap<>();
        int currentFrame = Integer.MIN_VALUE;
        try (BufferedReader reader = TraceFiles.openReader(aux)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains("\"object_state\"")) {
                    continue;
                }
                int frame = leadingFrame(line);
                if (frame != currentFrame) {
                    slotsThisFrame.clear();
                    currentFrame = frame;
                }
                int slot = parseHex(stringValue(line, "slot"));
                int x = parseHex(stringValue(line, "x"));
                int y = parseHex(stringValue(line, "y"));
                int subtype = parseHex(stringValue(line, "subtype"));
                if (slot < 0 || x < 0 || y < 0 || subtype < 0) {
                    continue;
                }
                String key = x + ":" + y + ":" + subtype;
                Set<Integer> slots = slotsThisFrame.computeIfAbsent(key, k -> new LinkedHashSet<>());
                if (slots.add(slot) && slots.size() == 2) {
                    touches.putIfAbsent(key, new TraceSegmentCarriedState.ConsumedGiantRing(
                            dirName, x, y, subtype, frame));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(touches.values());
    }

    /** A top-level scalar of one aux JSON line, unquoted, or {@code null}. */
    private static String stringValue(String line, String field) {
        String key = "\"" + field + "\":";
        int at = line.indexOf(key);
        if (at < 0) {
            return null;
        }
        int i = at + key.length();
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        boolean quoted = i < line.length() && line.charAt(i) == '"';
        int start = quoted ? i + 1 : i;
        int end = start;
        while (end < line.length()) {
            char c = line.charAt(end);
            if (quoted ? c == '"' : (c == ',' || c == '}' || c == ' ')) {
                break;
            }
            end++;
        }
        return line.substring(start, end);
    }

    /** The {@code "frame"} value an aux line opens with, without a full parse. */
    private static int leadingFrame(String line) {
        int key = line.indexOf("\"frame\":");
        if (key < 0) {
            return Integer.MIN_VALUE;
        }
        int i = key + 8;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        int sign = 1;
        if (i < line.length() && line.charAt(i) == '-') {
            sign = -1;
            i++;
        }
        int value = 0;
        boolean any = false;
        while (i < line.length() && Character.isDigit(line.charAt(i))) {
            value = value * 10 + (line.charAt(i) - '0');
            i++;
            any = true;
        }
        return any ? sign * value : Integer.MIN_VALUE;
    }

    private static int parseHex(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        try {
            String t = text.trim();
            return t.startsWith("0x") || t.startsWith("0X")
                    ? Integer.parseInt(t.substring(2), 16)
                    : Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Integer recordedEntryShield(TraceData trace) {
        if (trace.frameCount() == 0) {
            return null;
        }
        String main = trace.metadata().mainCharacter();
        for (TraceEvent event : trace.getEventsForFrame(0)) {
            if (event instanceof TraceEvent.InteractState state
                    && main != null && main.equalsIgnoreCase(state.character())) {
                return state.statusSecondary() & SAVED_SHIELD_MASK;
            }
        }
        return null;
    }

    private static TraceSegmentCarriedState withPlayer1Shield(
            TraceSegmentCarriedState base, Integer shield) {
        return new TraceSegmentCarriedState(
                base.provenance(), base.ringCount(), base.chaosEmeraldCount(),
                base.emeraldStates(), base.superEmeraldCount(), base.consumedGiantRings(),
                shield, base.specialStageCursor(), base.superEmeraldStageSet(),
                base.lastStarPostHit(), base.restore(), base.lifeCount(),
                base.collectedSpecialRingArray(), base.recordedSpecialStageCursor(),
                base.disagreements(), base.unresolved());
    }

    // -------------------------------------------------------------------- apply

    /**
     * Path bits ({@code top_solid_bit}/{@code lrb_solid_bit}) Player 1 and each
     * sidekick hold at entry. ROM: Load_Starpost_Settings restores
     * {@code Saved_solid_bits}/{@code Saved2_solid_bits} onto Player 1
     * (sonic3k.asm:61815, :61847); S2 {@code Obj79_LoadData} restores the pair
     * across a star post and special-stage return (s2.asm:44827), and the
     * fresh-start default applies only when {@code Last_star_pole_hit} is zero
     * (s2.asm:36192-36199). No boundary record samples the saved word, so the
     * value is this segment's own recorded entry row ({@code state_snapshot},
     * row 0). Only the ROM's two legal pairs ($C/$D, $E/$F; s2.constants.asm:70-71)
     * are accepted; anything else leaves the engine default.
     */
    private static void applyRecordedSolidBits(TraceData trace, AbstractPlayableSprite player) {
        if (trace.frameCount() == 0) {
            return;
        }
        applyRecordedSolidBits(trace, player, trace.metadata().mainCharacter());
        var sprites = GameServices.spritesOrNull();
        if (sprites == null) {
            return;
        }
        for (AbstractPlayableSprite sidekick : sprites.getRegisteredSidekicks()) {
            applyRecordedSolidBits(trace, sidekick, sprites.getSidekickCharacterName(sidekick));
        }
    }

    private static void applyRecordedSolidBits(TraceData trace,
                                               AbstractPlayableSprite playable,
                                               String characterLabel) {
        if (playable == null || characterLabel == null || characterLabel.isBlank()) {
            return;
        }
        TraceEvent.StateSnapshot snapshot = null;
        for (TraceEvent event : trace.getEventsForFrame(0)) {
            if (event instanceof TraceEvent.StateSnapshot candidate
                    && characterLabel.equalsIgnoreCase(
                            String.valueOf(candidate.fields().get("character")))
                    && candidate.fields().containsKey("top_solid_bit")) {
                snapshot = candidate;
                break;
            }
        }
        if (snapshot == null) {
            return;
        }
        int top = unsignedByteField(snapshot, "top_solid_bit");
        int lrb = unsignedByteField(snapshot, "lrb_solid_bit");
        boolean primary = top == 0x0C && lrb == 0x0D;
        boolean secondary = top == 0x0E && lrb == 0x0F;
        if (!primary && !secondary) {
            return;
        }
        playable.setTopSolidBit((byte) top);
        playable.setLrbSolidBit((byte) lrb);
    }

    private static int unsignedByteField(TraceEvent.StateSnapshot snapshot, String field) {
        Object raw = snapshot.fields().get(field);
        if (raw instanceof Number number) {
            return number.intValue() & 0xFF;
        }
        return raw == null ? -1 : parseHex(raw.toString()) & 0xFF;
    }

    private static void applyRunState(TraceSegmentCarriedState carried,
                                      AbstractPlayableSprite player) {
        var level = GameServices.levelOrNull();
        if (carried.ringCount() != null && level != null) {
            level.getLevelGamestate().setRings(carried.ringCount());
        }
        TraceSegmentCarriedState.LevelEntryRestore restore = carried.restore();
        if (restore != null && level != null) {
            level.getLevelGamestate().setRingExtraLifeFlags(restore.extraLifeFlags());
            level.getLevelGamestate().setTimerFrames(restore.timerFrames());
            if (player != null && isLegalPathPair(restore.topSolidBit(), restore.lrbSolidBit())) {
                player.setTopSolidBit((byte) restore.topSolidBit());
                player.setLrbSolidBit((byte) restore.lrbSolidBit());
            }
        }
        GameStateManager gameState = GameServices.gameStateOrNull();
        if (gameState != null && carried.lifeCount() != null) {
            NativeGameStateOps.restoreLives(gameState, carried.lifeCount());
        }
        applyEmeraldState(carried);
        if (gameState != null && carried.collectedSpecialRingArray() != null) {
            NativeGameStateOps.restoreSpecialStageSaveProgress(gameState,
                    carried.collectedSpecialRingArray(),
                    carried.recordedSpecialStageCursor() != null
                            ? carried.recordedSpecialStageCursor()
                            : gameState.getCurrentSpecialStageIndex());
        } else {
            applyConsumedGiantRings(carried);
        }
        if (restore != null && restore.checkpoint() != null) {
            applyRecordedCheckpoint(restore.checkpoint(), restore);
        } else {
            applyLastStarPostHit(carried);
        }
        Integer shield = carried.player1ShieldStatus();
        if (player != null && shield != null && shield != 0) {
            // loc_6A02 (sonic3k.asm:8326-8355) tests fire, then lightning,
            // then bubble, and gives Player 1 that shield.
            ShieldType type = (shield & FIRE_SHIELD) != 0 ? ShieldType.FIRE
                    : (shield & LIGHTNING_SHIELD) != 0 ? ShieldType.LIGHTNING
                    : ShieldType.BUBBLE;
            player.giveShield(type);
        }
    }

    private static boolean isLegalPathPair(int top, int lrb) {
        return (top == 0x0C && lrb == 0x0D) || (top == 0x0E && lrb == 0x0F);
    }

    /**
     * The star-post checkpoint as {@code Last_star_post_hit} and the recorded
     * {@code Saved_*} block hold it at this load. The index is the engine's
     * {@code Last_star_post_hit} (read by {@code tst.b} gates such as
     * SSZ ScreenInit), the activation mark is the loc_2D008 comparison, and the
     * saved block is what a death reload restores through
     * {@code Load_Starpost_Settings} (sonic3k.asm:61800-61834).
     */
    private static void applyRecordedCheckpoint(TraceSegmentCarriedState.Checkpoint saved,
                                                TraceSegmentCarriedState.LevelEntryRestore restore) {
        var level = GameServices.levelOrNull();
        var checkpoint = level == null ? null : level.getCheckpointState();
        if (checkpoint == null || saved.index() == 0) {
            return;
        }
        checkpoint.restoreFromSaved(saved.x(), saved.y(), saved.cameraX(), saved.cameraY(),
                saved.index());
        checkpoint.restoreStarPostActivationMark(
                Math.max(checkpoint.getStarPostActivationMark(), saved.index()));
        if (checkpoint instanceof com.openggf.game.CheckpointState cs) {
            cs.saveRingState(saved.rings(), saved.extraLifeFlags());
            cs.saveActTimer(saved.timerFrames());
            // The engine's entry camera clamps to an active checkpoint's saved
            // bounds, which models loc_1BE46 (sonic3k.asm:38187-38193) calling
            // Load_Starpost_Settings before the camera is placed. Publish
            // the bounds this restore loads (Saved2_* on a special-stage return)
            // for that calculation; importAfterLevelInit then records the
            // Saved_* star-post bounds a death reload uses.
            cs.saveS3kRuntimeState(restore.cameraMaxY(), restore.dynamicResizeRoutine());
            if (isLegalPathPair(saved.topSolidBit(), saved.lrbSolidBit())) {
                cs.saveSolidBits((byte) saved.topSolidBit(), (byte) saved.lrbSolidBit());
            }
        }
    }

    /**
     * The part of the entry restore that lands after the level-size and event
     * initialisation it overrides. In the ROM {@code Load_Starpost_Settings}
     * runs after {@code Get_LevelSizeStart} has set the bounds, and replaces
     * {@code Camera_max_Y_pos}, {@code Camera_target_max_Y_pos},
     * {@code Dynamic_resize_routine} and, when the level has water,
     * {@code Mean_water_level} and {@code Water_full_screen_flag}
     * (sonic3k.asm:61822-61833, 61848-61856). The engine's equivalents are set
     * by its camera and level-event initialisation, so the declared-position
     * entry calls this once, after both and before the first row.
     */
    public static void importAfterLevelInit(TraceSegmentCarriedState carried) {
        if (carried == null || carried.restore() == null) {
            return;
        }
        TraceSegmentCarriedState.LevelEntryRestore restore = carried.restore();
        var camera = GameServices.cameraOrNull();
        if (camera != null) {
            camera.setMaxY((short) restore.cameraMaxY());
            camera.setMaxYTarget((short) restore.cameraMaxY());
        }
        var events = GameServices.module().getLevelEventProvider();
        if (events instanceof com.openggf.game.AbstractLevelEventManager eventManager) {
            eventManager.restoreEventRoutineState(restore.dynamicResizeRoutine(), 0);
        }
        var level = GameServices.levelOrNull();
        TraceSegmentCarriedState.Checkpoint saved = restore.checkpoint();
        if (saved != null && saved.index() != 0 && level != null
                && level.getCheckpointState() instanceof com.openggf.game.CheckpointState cs) {
            // Saved_camera_max_Y_pos / Saved_dynamic_resize_routine, which a
            // death reload restores. A special-stage return entered with the
            // Saved2_* bounds above, so they cannot be published before the
            // entry camera.
            cs.saveS3kRuntimeState(saved.cameraMaxY(), saved.dynamicResizeRoutine());
        }
        var water = GameServices.waterOrNull();
        if (water != null && level != null) {
            int zone = level.getFeatureZoneId();
            int act = level.getFeatureActId();
            if (water.hasWater(zone, act)) {
                water.setWaterLevelDirect(zone, act, restore.meanWaterLevel());
                water.setWaterLevelTarget(zone, act, restore.meanWaterLevel());
                water.setFullScreenFlag(zone, act, restore.waterFullScreen());
            }
        }
    }

    /**
     * {@code Last_star_post_hit} survives the reload that starts the segment
     * (a star-post bonus exit restores it from {@code Saved_last_star_post_hit},
     * loc_4A024, sonic3k.asm:96716-96726; a special-stage return sets bit 7,
     * loc_9D02, sonic3k.asm:12708-12712). Each star post's init compares its own
     * subtype with it and starts already hit when the byte is not lower
     * (loc_2CFC0 to loc_2D008, sonic3k.asm:61613-61633). The engine models that
     * comparison through the checkpoint's activation mark, so the recorded byte,
     * masked to the subtype's seven bits, becomes that mark. A zero byte (a
     * start-table entry) sets nothing.
     */
    private static void applyLastStarPostHit(TraceSegmentCarriedState carried) {
        Integer recorded = carried.lastStarPostHit();
        if (recorded == null || (recorded & 0x7F) == 0) {
            return;
        }
        var level = GameServices.levelOrNull();
        var checkpoint = level == null ? null : level.getCheckpointState();
        if (checkpoint == null) {
            return;
        }
        checkpoint.restoreStarPostActivationMark(
                Math.max(checkpoint.getStarPostActivationMark(), recorded & 0x7F));
    }

    private static void applyEmeraldState(TraceSegmentCarriedState carried) {
        GameStateManager gameState = GameServices.gameStateOrNull();
        if (gameState == null || carried.emeraldStates() == null) {
            return;
        }
        boolean placed = carried.emeraldStates().stream()
                .anyMatch(s -> s >= S3kSpecialStageSelection.EMERALD_IN_SHRINE);
        gameState.restoreS3kEmeraldProgress(carried.emeraldStates(), placed);
    }

    private static void applyConsumedGiantRings(TraceSegmentCarriedState carried) {
        GameStateManager gameState = GameServices.gameStateOrNull();
        var level = GameServices.levelOrNull();
        if (gameState == null || level == null || level.getCurrentLevel() == null) {
            return;
        }
        for (TraceSegmentCarriedState.ConsumedGiantRing ring : carried.consumedGiantRings()) {
            boolean onGiantRingPlacement = false;
            for (ObjectSpawn spawn : level.getCurrentLevel().getObjects()) {
                if (spawn.objectId() == Sonic3kObjectIds.SS_ENTRY_RING
                        && spawn.x() == ring.x() && spawn.y() == ring.y()
                        && (spawn.subtype() & 0xFF) == ring.subtype()) {
                    onGiantRingPlacement = true;
                    break;
                }
            }
            if (!onGiantRingPlacement) {
                continue;
            }
            // Obj_SSEntryRing tests bit subtype (btst d0,d1 on a longword, so
            // mod 32) before it builds itself (sonic3k.asm:128271-128275).
            gameState.markSpecialRingCollected(ring.subtype() & 0x1F);
        }
    }
}
