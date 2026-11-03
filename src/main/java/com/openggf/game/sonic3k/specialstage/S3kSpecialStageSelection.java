package com.openggf.game.sonic3k.specialstage;

import java.util.List;
import java.util.Objects;

/**
 * The locked-on ROM's special-stage layout choice, {@code sub_85B0}
 * ({@code loc_85E4} through {@code loc_8630}, loc_85E4 (sonic3k.asm:10861-10895)).
 *
 * <p>The stage that loads is a pure function of carried run state:
 * {@code SK_special_stage_flag} picks the layout set and the emerald counter
 * that bounds the search; {@code Current_special_stage} is where the search
 * starts; {@code Collected_emeralds_array} is what it skips. A Chaos search
 * ({@code d2 = 0}) takes the first stage whose entry is still {@code 0}; a
 * Super search ({@code d2 = 2}) takes the first entry still {@code 2} (a
 * Chaos Emerald placed in the Hidden Palace shrine whose Super Emerald is not
 * yet won). With the bounding counter already at 7 there is no search
 * ({@code cmpi.b #7,d3 / bhs.s loc_8630}). The search wraps at 7 and gives up
 * after six further passes, leaving 7 ({@code moveq #7,d0}); either way the
 * result is masked to three bits ({@code andi.w #7,d0}).
 *
 * <p>The SK-alone and debug ({@code Sound_test_sound} with A held) arms are
 * not represented: locked-on gameplay without the debug cheat never reaches
 * them.
 */
public final class S3kSpecialStageSelection {
    /** {@code Collected_emeralds_array}: emerald not collected. */
    public static final int EMERALD_ABSENT = 0;
    /** Chaos Emerald collected ({@code bset #0} at {@code loc_9CCE}). */
    public static final int CHAOS_EMERALD = 1;
    /** Chaos Emerald placed in the shrine ({@code addq.b #1} at {@code loc_90ED0}). */
    public static final int EMERALD_IN_SHRINE = 2;
    /** Super Emerald collected (bit 0 set again on a placed emerald). */
    public static final int SUPER_EMERALD = 3;

    private static final int STAGE_COUNT = 7;

    private S3kSpecialStageSelection() {
    }

    /**
     * @param currentSpecialStage {@code Current_special_stage} before the search
     * @param superEmeraldSet {@code SK_special_stage_flag} non-zero
     * @param collectedEmeralds {@code Collected_emeralds_array}, seven entries 0-3
     * @param chaosEmeraldCount {@code Chaos_emerald_count}
     * @param superEmeraldCount {@code Super_emerald_count}
     * @return the stage {@code loc_8630} stores back into {@code Current_special_stage}
     */
    public static int selectStage(int currentSpecialStage,
                                  boolean superEmeraldSet,
                                  List<Integer> collectedEmeralds,
                                  int chaosEmeraldCount,
                                  int superEmeraldCount) {
        Objects.requireNonNull(collectedEmeralds, "collectedEmeralds");
        if (collectedEmeralds.size() != STAGE_COUNT) {
            throw new IllegalArgumentException("Collected_emeralds_array has seven entries");
        }
        int d0 = currentSpecialStage & 0xFF;
        // loc_85E4: the Super set bounds the search with Super_emerald_count.
        int d3 = superEmeraldSet ? superEmeraldCount : chaosEmeraldCount;
        if (d3 >= 7) {
            return d0 & 7;
        }
        // add.b d2,d2: SK_special_stage_flag (1) becomes the searched value 2.
        int wanted = superEmeraldSet ? EMERALD_IN_SHRINE : EMERALD_ABSENT;
        for (int wrapsLeft = 5; wrapsLeft >= 0; wrapsLeft--) {
            // loc_861A: cmp.b (a1,d0.w),d2 / beq.s loc_8630.
            while (d0 < STAGE_COUNT) {
                if (collectedEmeralds.get(d0) == wanted) {
                    return d0 & 7;
                }
                d0++;
            }
            // cmpi.w #7,d0 / blo.s fell through: moveq #0,d0 / dbf d1.
            d0 = 0;
        }
        return 7;
    }
}
