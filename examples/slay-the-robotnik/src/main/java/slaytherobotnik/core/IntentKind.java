package slaytherobotnik.core;

/**
 * Intent icons shown above enemies. See {@link CardType} for why these are String constants.
 */
public final class IntentKind {
    public static final String ATTACK = "Attack";
    public static final String ATTACK_BUFF = "AttackBuff";
    public static final String ATTACK_DEBUFF = "AttackDebuff";
    public static final String ATTACK_DEFEND = "AttackDefend";
    public static final String BUFF = "Buff";
    public static final String DEBUFF = "Debuff";
    public static final String STRONG_DEBUFF = "StrongDebuff";
    public static final String DEFEND = "Defend";
    public static final String DEFEND_BUFF = "DefendBuff";
    public static final String DEFEND_DEBUFF = "DefendDebuff";
    public static final String ESCAPE = "Escape";
    public static final String SLEEP = "Sleep";
    public static final String STUN = "Stun";
    public static final String UNKNOWN = "Unknown";

    private IntentKind() {
    }

    public static boolean isAttack(String kind) {
        return kind.equals(ATTACK) || kind.equals(ATTACK_BUFF) || kind.equals(ATTACK_DEBUFF)
                || kind.equals(ATTACK_DEFEND);
    }
}
