package com.openggf.trace.replay;

import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
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
            return TraceSegmentCarriedState.none("no run manifest names this segment");
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

        unresolved.add("Mean_water_level / Camera_max_Y_pos / Dynamic_resize_routine:"
                + " Saved2_* words are not recorded");
        unresolved.add("Extra_life_flags / Life_count / Timer: not recorded (not compared)");

        String provenance = run.manifest().runId() + " segment " + target
                + " (" + segment.dir() + ")";
        return new TraceSegmentCarriedState(
                provenance, rings, chaos, emeraldStates, superCount, giantRings,
                null, cursor, superSet, unresolved);
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
                base.unresolved());
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
        applyEmeraldState(carried);
        applyConsumedGiantRings(carried);
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
