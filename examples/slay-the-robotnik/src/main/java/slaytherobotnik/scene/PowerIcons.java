package slaytherobotnik.scene;

import slaytherobotnik.core.Power;
import slaytherobotnik.core.Powers;

/** Which icon a power shows; character and enemy powers fall back to a generic buff/debuff badge. */
final class PowerIcons {
    private PowerIcons() {
    }

    static String icon(String powerId, Power live) {
        return switch (powerId) {
            case Powers.STRENGTH, "aiz:enrage", Powers.STRENGTH_DOWN_AT_END, "knuckles:battle_scars",
                    "knuckles:hyper_knuckles", "lbz:power_surge" -> "pow_strength";
            case Powers.DEXTERITY, Powers.DEXTERITY_DOWN_AT_END -> "pow_dexterity";
            case Powers.FOCUS, "sonic:focus_down", "sonic:super_sonic", "sonic:spindash_charge",
                    "sonic:speed_lines" -> "pow_focus";
            case Powers.VULNERABLE, "aiz:spore_cloud" -> "pow_vulnerable";
            case Powers.WEAK -> "pow_weak";
            case Powers.FRAIL -> "pow_frail";
            case Powers.ARTIFACT -> "pow_artifact";
            case Powers.THORNS, "knuckles:spiked_knuckles", "hcz:painful_bites" -> "pow_thorns";
            case Powers.METALLICIZE, Powers.PLATED_ARMOR, Powers.BARRICADE, "aiz:mode_shift" -> "pow_metallicize";
            case Powers.INTANGIBLE, "hcz:slippery", "hcz:dart" -> "pow_intangible";
            case Powers.RITUAL -> "pow_ritual";
            case Powers.REGEN -> "pow_regen";
            case Powers.ENERGIZED, "knuckles:tricked_again", "sonic:boost_mode", "lbz:slow" -> "pow_energy";
            case Powers.DRAW_NEXT_TURN, "sonic:gotta_juice", "sonic:peel_out" -> "pow_card";
            case "tails:blast_radius", "tails:remote_robot", "tails:recycler" -> "pow_bomb";
            case "tails:holding_pattern" -> "pow_retain";
            case "tails:lock_on", "sonic:sharp_eye" -> "pow_lockon";
            case "tails:jammed" -> "pow_jammed";
            case "tails:double_damage" -> "pow_double";
            case "tails:workbench" -> "pow_wrench";
            case "tails:super_tails" -> "pow_super";
            case "knuckles:fired_up", "ssz:overdrive" -> "pow_fire";
            case "ssz:invincible", "sonic:afterimage" -> "pow_shield";
            case "aiz:asleep" -> "pow_sleep";
            default -> live != null && live.isDebuff() ? "pow_debuff" : "pow_buff";
        };
    }
}
