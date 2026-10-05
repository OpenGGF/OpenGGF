package slaytherobotnik.scene;

import slaytherobotnik.core.Power;
import slaytherobotnik.core.Powers;

/** Which icon a power shows; character and enemy powers fall back to a generic buff/debuff badge. */
final class PowerIcons {
    private PowerIcons() {
    }

    static String icon(String powerId, Power live) {
        return switch (powerId) {
            case Powers.STRENGTH, "aiz:enrage", Powers.STRENGTH_DOWN_AT_END -> "pow_strength";
            case Powers.DEXTERITY, Powers.DEXTERITY_DOWN_AT_END -> "pow_dexterity";
            case Powers.FOCUS, "sonic:focus_down", "sonic:super_sonic", "sonic:spindash_charge" -> "pow_focus";
            case Powers.VULNERABLE -> "pow_vulnerable";
            case Powers.WEAK -> "pow_weak";
            case Powers.FRAIL -> "pow_frail";
            case Powers.ARTIFACT -> "pow_artifact";
            case Powers.THORNS -> "pow_thorns";
            case Powers.METALLICIZE, Powers.PLATED_ARMOR, Powers.BARRICADE -> "pow_metallicize";
            case Powers.INTANGIBLE -> "pow_intangible";
            case Powers.RITUAL -> "pow_ritual";
            case Powers.REGEN -> "pow_regen";
            case Powers.ENERGIZED -> "pow_energy";
            case Powers.DRAW_NEXT_TURN, "sonic:gotta_juice" -> "pow_card";
            default -> live != null && live.isDebuff() ? "pow_debuff" : "pow_buff";
        };
    }
}
