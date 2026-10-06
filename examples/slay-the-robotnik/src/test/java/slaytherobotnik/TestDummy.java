package slaytherobotnik;

import slaytherobotnik.core.Combat;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.IntentKind;
import slaytherobotnik.core.Move;
import slaytherobotnik.core.Rng;

/** A training dummy for rule tests: fixed HP, attacks for a fixed amount (0 = just waits). */
final class TestDummy extends Enemy {
    private final Move hit;
    private final Move idle = Move.of("idle", "Idle", IntentKind.UNKNOWN);

    TestDummy(int hp, int damage) {
        super("test:dummy", "Dummy", hp);
        hit = Move.attack("hit", "Hit", damage);
    }

    @Override
    protected void chooseMove(Combat c, Rng ai) {
        setMove(hit.baseDamage() > 0 ? hit : idle);
    }

    @Override
    protected void perform(Combat c, Move move) {
        if (move == hit) {
            attackPlayer(c, move);
        }
    }
}
