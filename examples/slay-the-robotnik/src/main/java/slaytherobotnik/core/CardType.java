package slaytherobotnik.core;

/**
 * The Slay the Spire card types. Status and Curse cards are clutter added by enemies and
 * events.
 *
 * <p>Why String constants rather than an enum: OpenGGF's mod validator rejects classes
 * with static initialisers ({@code STATIC_STATE_UNSUPPORTED}) and an enum always has one.
 * Literal {@code static final String} constants are compiled into the class file and are
 * allowed, and switching on them needs no hidden static tables. Every "kind" in this mod
 * follows the same pattern.
 */
public final class CardType {
    public static final String ATTACK = "Attack";
    public static final String SKILL = "Skill";
    public static final String POWER = "Power";
    public static final String STATUS = "Status";
    public static final String CURSE = "Curse";

    private CardType() {
    }
}
