package com.openggf.game.sonic3k.specialstage;

import com.openggf.trace.TraceFiles;
import com.openggf.trace.TraceRunManifest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code Collected_emeralds_array} at a run segment's entry, folded through
 * the ROM's own update rules over the special stages the run recorded before
 * it. No recorder export holds the array; every input below is a recorder
 * observation, and every step is the ROM rule that consumes it:
 *
 * <ol>
 *   <li><b>Layout set.</b> {@code SK_special_stage_flag} is written 0 by a giant
 *       ring ({@code loc_61892}, sonic3k.asm:128461-128467) and 1 only by the
 *       Hidden Palace pedestal ({@code loc_90926}, sonic3k.asm:197832-197846).
 *       A giant ring never opens a special stage once seven Chaos Emeralds are
 *       held -- {@code loc_6170A} pays 50 rings on an S3 level and
 *       {@code SSEntryFlash_GoSS} routes an S&amp;K level to the shrine instead
 *       ({@code loc_618AC}) -- so a stage armed with the recorded
 *       {@code emeralds_before == 7} is the Super Emerald set.</li>
 *   <li><b>Placement.</b> The first shrine visit places every Chaos Emerald
 *       (state 1 to 2, {@code loc_90A94}/{@code loc_90ED0},
 *       sonic3k.asm:197953-197975, 198378-198384) before any pedestal can arm a
 *       Super stage, so the first Super-set arm implies all seven placed.</li>
 *   <li><b>Selection.</b> {@link S3kSpecialStageSelection} from the recorded
 *       {@code special_stage_index} ({@code Current_special_stage} sampled at
 *       the arm, before {@code sub_85B0} searches).</li>
 *   <li><b>Outcome.</b> The stage's final recorded {@code clear_routine}
 *       ({@code Special_stage_clear_routine}) is non-zero only after the clear
 *       path {@code loc_9CE6}; {@code loc_9CCE} then sets bit 0 of the played
 *       stage's entry unless the set's counter is already 7
 *       (sonic3k.asm:12686-12700). For the Chaos set the manifest's
 *       {@code emeralds_after} must agree; a disagreement makes the ledger
 *       inconsistent rather than silently chosen.</li>
 * </ol>
 */
public final class S3kRunEmeraldLedger {

    public record Result(boolean consistent, String note, List<Integer> states,
                  int chaosEmeraldCount, int superEmeraldCount,
                  Boolean targetSuperEmeraldSet) {
    }

    private S3kRunEmeraldLedger() {
    }

    public static Result fold(TraceRunManifest manifest, Path runDir, int target) {
        int[] states = new int[7];
        int chaos = 0;
        int superCount = 0;
        List<TraceRunManifest.Segment> segments = manifest.segments();
        Boolean targetSuperSet = null;
        for (int i = 0; i <= target && i < segments.size(); i++) {
            TraceRunManifest.Segment segment = segments.get(i);
            if (!"special_stage".equals(segment.kind())) {
                continue;
            }
            int segmentIndex = i;
            TraceRunManifest.Transition in = manifest.transitions().stream()
                    .filter(t -> t.toSegment() == segmentIndex).findFirst().orElse(null);
            if (in == null || in.emeraldsBefore() == null || segment.specialStageIndex() == null) {
                return inconsistent("special stage " + segment.dir() + " has no recorded arm state");
            }
            if (in.emeraldsBefore() != chaos) {
                return inconsistent("special stage " + segment.dir() + " armed with "
                        + in.emeraldsBefore() + " Chaos Emeralds; ledger holds " + chaos);
            }
            boolean superSet = chaos >= 7;
            if (superSet) {
                for (int e = 0; e < states.length; e++) {
                    if (states[e] == S3kSpecialStageSelection.CHAOS_EMERALD) {
                        states[e] = S3kSpecialStageSelection.EMERALD_IN_SHRINE;
                    }
                }
            }
            if (i == target) {
                targetSuperSet = superSet;
                break;
            }
            int stage = S3kSpecialStageSelection.selectStage(
                    segment.specialStageIndex(), superSet, boxed(states), chaos, superCount);
            Integer clearRoutine = finalClearRoutine(runDir.resolve(segment.dir()));
            if (clearRoutine == null) {
                return inconsistent("special stage " + segment.dir() + " has no recorded outcome");
            }
            boolean cleared = clearRoutine != 0;
            if (!superSet) {
                TraceRunManifest.Transition out = manifest.transitions().stream()
                        .filter(t -> t.fromSegment() == segmentIndex).findFirst().orElse(null);
                if (out == null || out.emeraldsAfter() == null) {
                    return inconsistent("special stage " + segment.dir() + " has no recorded exit count");
                }
                if (cleared != (out.emeraldsAfter() > chaos)) {
                    return inconsistent("special stage " + segment.dir() + " clear_routine "
                            + clearRoutine + " disagrees with emeralds " + chaos + "->"
                            + out.emeraldsAfter());
                }
            }
            if (cleared && (superSet ? superCount : chaos) < 7) {
                states[stage] |= 1;
                if (superSet) {
                    superCount++;
                } else {
                    chaos++;
                }
            }
        }
        return new Result(true, null, boxed(states), chaos, superCount, targetSuperSet);
    }

    private static Result inconsistent(String note) {
        return new Result(false, note, null, 0, 0, null);
    }

    private static List<Integer> boxed(int[] states) {
        return Arrays.stream(states).boxed().toList();
    }

    /** {@code clear_routine} on a special-stage segment's last recorded row. */
    private static Integer finalClearRoutine(Path segmentDir) {
        Path physics = TraceFiles.resolve(segmentDir, "physics.csv");
        if (physics == null) {
            return null;
        }
        try (BufferedReader reader = TraceFiles.openReader(physics)) {
            String header = reader.readLine();
            if (header == null) {
                return null;
            }
            int column = new ArrayList<>(List.of(header.split(","))).indexOf("clear_routine");
            if (column < 0) {
                return null;
            }
            String last = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    last = line;
                }
            }
            if (last == null) {
                return null;
            }
            String[] cells = last.split(",");
            return column < cells.length ? Integer.parseInt(cells[column].trim(), 16) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
