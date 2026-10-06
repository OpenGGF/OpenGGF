package slaytherobotnik.run;

import slaytherobotnik.core.Combat;

/**
 * A fight. The scene animates {@link #combat()} and, once {@link Combat#isOver()} and the
 * victory or defeat animation has played, calls {@link #finish()}.
 */
public final class CombatRoom implements Room {
    private final Run run;
    private final Combat combat;
    private final String encounterId;
    private final String encounterName;
    private final Runnable afterVictory;
    private boolean finished;

    CombatRoom(Run run, Combat combat, String encounterId, String encounterName, Runnable afterVictory) {
        this.run = run;
        this.combat = combat;
        this.encounterId = encounterId;
        this.encounterName = encounterName;
        this.afterVictory = afterVictory;
    }

    public Combat combat() { return combat; }
    public String encounterId() { return encounterId; }
    public String encounterName() { return encounterName; }
    /** A {@link slaytherobotnik.core.RoomType} constant: Monster, Elite or Boss. */
    public String roomType() { return combat.roomType(); }

    /** Leaves the fight for the reward screen (or the game-over screen). */
    public void finish() {
        if (finished || !combat.isOver()) {
            return;
        }
        finished = true;
        run.combatFinished(this, afterVictory);
    }
}
