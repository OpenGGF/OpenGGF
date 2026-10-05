package slaytherobotnik.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for badniks and bosses. A subclass declares its moves, picks the next one in
 * {@link #chooseMove} (so the player can see the intent a turn ahead) and carries it out in
 * {@link #perform}. This mirrors how Slay the Spire's monsters are written:
 *
 * <pre>{@code
 * final class Rhinobot extends Enemy {
 *     private final Move charge = Move.attack("charge", "Charge", 7);
 *     private final Move revUp = Move.of("rev", "Rev Up", IntentKind.BUFF);
 *
 *     Rhinobot(Rng hp) { super("aiz:rhinobot", "Rhinobot", hp.range(14, 18)); }
 *
 *     protected void chooseMove(Combat c, Rng ai) {
 *         setMove(lastMove("charge") ? revUp : charge);
 *     }
 *
 *     protected void perform(Combat c, Move move) {
 *         if (move == charge) attackPlayer(c, move);
 *         else c.applyPower(this, this, Powers.strength(2));
 *     }
 * }
 * }</pre>
 */
public abstract class Enemy extends Creature {
    private final String id;
    private Move nextMove;
    private final List<String> history = new ArrayList<>();
    private boolean minion;
    private boolean escaped;
    private boolean halfDead;
    private String art;

    protected Enemy(String id, String name, int maxHp) {
        super(name, maxHp, maxHp);
        this.id = id;
        this.art = id;
    }

    @Override
    public boolean isPlayer() {
        return false;
    }

    public String id() { return id; }
    /** Sprite key for the renderer; defaults to the enemy id. */
    public String art() { return art; }
    protected void setArt(String art) { this.art = art; }
    public Move nextMove() { return nextMove; }
    /** Minions flee when the leader dies and do not count toward victory. */
    public boolean isMinion() { return minion; }
    protected void markMinion() { minion = true; }
    public boolean escaped() { return escaped; }
    /** True while dying-but-reviving (used by enemies with a second phase). */
    public boolean halfDead() { return halfDead; }
    protected void setHalfDead(boolean value) { halfDead = value; }

    /** Alive and still in the fight. */
    public boolean isActive() {
        return !isDead() && !escaped;
    }

    /** Called once when the combat starts, after all enemies exist (apply starting powers here). */
    protected void onSpawn(Combat c) {
    }

    /**
     * Called when HP reaches 0, before the enemy is removed. A boss with a second phase can
     * restore HP here (or call {@link #setHalfDead}) to stay in the fight; a leader can make
     * its minions flee.
     */
    protected void onDeath(Combat c) {
    }

    /** Choose the next move with {@link #setMove}; called at combat start and after each turn. */
    protected abstract void chooseMove(Combat c, Rng ai);

    /** Carry out {@code move} (the one chosen last time). */
    protected abstract void perform(Combat c, Move move);

    protected final void setMove(Move move) {
        nextMove = move;
    }

    final void takeTurn(Combat c) {
        Move move = nextMove;
        if (move == null) {
            return;
        }
        history.add(move.id());
        perform(c, move);
    }

    final void rollMove(Combat c, Rng ai) {
        chooseMove(c, ai);
    }

    /** The move used last turn was {@code moveId}. */
    protected final boolean lastMove(String moveId) {
        return !history.isEmpty() && history.get(history.size() - 1).equals(moveId);
    }

    /** The last two moves were both {@code moveId}. */
    protected final boolean lastTwoMoves(String moveId) {
        int n = history.size();
        return n >= 2 && history.get(n - 1).equals(moveId) && history.get(n - 2).equals(moveId);
    }

    /** Moves performed so far this combat, oldest first. */
    public final List<String> history() {
        return List.copyOf(history);
    }

    /** Number of turns this enemy has taken. */
    protected final int turnsTaken() {
        return history.size();
    }

    /** Standard attack: hits the player {@code move.hits()} times for {@code move.baseDamage()}. */
    protected final void attackPlayer(Combat c, Move move) {
        for (int i = 0; i < Math.max(1, move.hits()) && !c.isOver(); i++) {
            c.attack(this, c.player(), move.baseDamage());
        }
    }

    /** Leaves the fight (Thieves in Slay the Spire); counts as defeated for victory. */
    protected final void escape(Combat c) {
        escaped = true;
        c.events().add(new CombatEvent.EnemyEscaped(this));
        c.checkVictory();
    }

    void escapeSilently() {
        escaped = true;
    }

    /**
     * Removes this enemy without a death (it split into others, or transformed). Emits a
     * {@link CombatEvent.Cue} with {@code cue} so the screen can animate it.
     */
    protected final void vanish(Combat c, String cue) {
        escaped = true;
        c.events().add(new CombatEvent.Cue(this, cue));
    }

    /** Sets current HP before the fight starts (used by run effects such as the Jammer bonus). */
    public final void setStartingHp(int value) {
        setHp(value);
    }

    /** Restores HP (a reviving boss); use from {@link #onDeath}. */
    protected final void reviveTo(int newHp) {
        setHp(newHp);
    }

    /** The intent shown to the player, with damage modifiers applied. */
    public Intent intent(Combat c) {
        if (nextMove == null) {
            return new Intent(IntentKind.UNKNOWN, 0, 0);
        }
        if (IntentKind.isAttack(nextMove.intent())) {
            int dmg = c.calculateDamage(this, c.player(), nextMove.baseDamage(), DamageType.ATTACK);
            return new Intent(nextMove.intent(), dmg, Math.max(1, nextMove.hits()));
        }
        return new Intent(nextMove.intent(), 0, 0);
    }

    void heal(int amount) {
        setHp(hp + amount);
    }
}
