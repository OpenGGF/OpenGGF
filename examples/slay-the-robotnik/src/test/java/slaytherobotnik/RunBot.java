package slaytherobotnik;

import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardTarget;
import slaytherobotnik.core.Combat;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Reward;
import slaytherobotnik.core.RestOption;
import slaytherobotnik.core.Rng;
import slaytherobotnik.map.MapNode;
import slaytherobotnik.run.CombatRoom;
import slaytherobotnik.run.DeckChoice;
import slaytherobotnik.run.EventRoom;
import slaytherobotnik.run.MapRoom;
import slaytherobotnik.run.RestRoom;
import slaytherobotnik.run.RewardRoom;
import slaytherobotnik.run.Run;
import slaytherobotnik.run.ShopRoom;
import slaytherobotnik.run.StartRoom;
import slaytherobotnik.run.TreasureRoom;

/**
 * A simple player that drives a whole run through the public room API: it plays the most
 * expensive affordable card at a random target, takes every reward, buys what it can afford,
 * and picks random event options. It is not smart; its job is to visit every code path and
 * prove a run always ends without getting stuck.
 */
final class RunBot {
    private final Run run;
    private final Rng rng;
    int steps;
    /** Strength given at the start of every fight (sturdy runs use it so fights can't stalemate). */
    int strengthBoost;

    RunBot(Run run, long seed) {
        this.run = run;
        this.rng = new Rng(seed);
    }

    /** Plays until the run ends or {@code maxSteps} actions; returns true if the run ended. */
    boolean play(int maxSteps) {
        while (!run.over() && steps < maxSteps) {
            steps++;
            if (run.deckChoice() != null) {
                answer(run.deckChoice());
                continue;
            }
            switch (run.room()) {
                case StartRoom start -> {
                    if (!start.done()) {
                        start.choose(rng.nextInt(start.options().size()));
                    } else {
                        start.proceed();
                    }
                }
                case MapRoom map -> {
                    List<MapNode> next = run.reachableNodes();
                    if (!next.isEmpty()) {
                        run.travelTo(rng.pick(next));
                    } else if (run.bossReachable()) {
                        run.travelToBoss();
                    } else {
                        throw new IllegalStateException("Stuck on the map at " + run.state().actFloor());
                    }
                }
                case CombatRoom combat -> fight(combat);
                case RewardRoom rewards -> collect(rewards);
                case EventRoom event -> {
                    List<Integer> enabled = new ArrayList<>();
                    for (int i = 0; i < event.options().size(); i++) {
                        if (event.options().get(i).enabled()) {
                            enabled.add(i);
                        }
                    }
                    if (enabled.isEmpty()) {
                        throw new IllegalStateException("Event with no enabled options: " + event.def().id());
                    }
                    event.choose(rng.pick(enabled));
                }
                case ShopRoom shop -> {
                    for (int i = 0; i < shop.items().size(); i++) {
                        if (rng.nextBoolean()) {
                            shop.buy(i);
                        }
                    }
                    if (rng.nextBoolean() && shop.startRemoval()) {
                        continue;
                    }
                    shop.leave();
                }
                case RestRoom rest -> {
                    if (rest.chosen() != null) {
                        rest.proceed();
                    } else {
                        String pick = run.state().hp() * 2 < run.state().maxHp() ? "rest" : "smith";
                        boolean ok = rest.choose(pick);
                        if (!ok) {
                            for (RestOption option : rest.options()) {
                                if (option.enabled() && rest.choose(option.id())) {
                                    ok = true;
                                    break;
                                }
                            }
                        }
                        if (!ok) {
                            rest.proceed();
                        }
                    }
                }
                case TreasureRoom treasure -> treasure.open();
                default -> throw new IllegalStateException("Unexpected room " + run.room());
            }
        }
        return run.over();
    }

    private void answer(DeckChoice choice) {
        List<Card> options = new ArrayList<>(choice.options());
        rng.shuffle(options);
        int n = Math.min(options.size(), Math.max(choice.min(), 1));
        n = Math.min(n, choice.max());
        if (!run.resolveDeckChoice(options.subList(0, n))) {
            throw new IllegalStateException("Deck choice rejected: " + choice.prompt());
        }
    }

    private void collect(RewardRoom rewards) {
        List<Reward> list = rewards.rewards();
        for (int i = 0; i < list.size(); i++) {
            if (rewards.claimed(i)) {
                continue;
            }
            switch (list.get(i)) {
                case Reward.CardChoice c -> {
                    if (!c.cards().isEmpty() && rng.nextInt(4) != 0) {
                        rewards.pickCard(i, rng.pick(c.cards()));
                    } else {
                        rewards.skip(i);
                    }
                }
                case Reward.BossRelicChoice b -> rewards.pickBossRelic(i, rng.pick(b.relicIds()));
                default -> {
                    if (!rewards.claim(i)) {
                        rewards.skip(i);
                    }
                }
            }
        }
        rewards.proceed();
    }

    private void fight(CombatRoom room) {
        Combat c = room.combat();
        if (strengthBoost > 0 && c.turn() == 1) {
            c.applyPower(c.player(), c.player(), slaytherobotnik.core.Powers.strength(strengthBoost));
        }
        int actions = 0;
        while (!c.isOver() && actions++ < 400) {
            if (c.hasPendingChoice()) {
                var request = c.pendingChoice();
                List<Card> options = new ArrayList<>(c.pendingOptions());
                rng.shuffle(options);
                int n = Math.min(options.size(), Math.max(request.min(), Math.min(1, request.max())));
                if (!c.resolveChoice(options.subList(0, n))) {
                    throw new IllegalStateException("Combat choice rejected: " + request.prompt());
                }
                continue;
            }
            if (rng.nextInt(6) == 0 && !run.state().potions().isEmpty()) {
                int slot = rng.nextInt(run.state().potions().size());
                PotionDef potion = run.state().potionAt(slot);
                Enemy target = CardTarget.needsChoice(potion.target()) ? c.randomEnemy() : null;
                if (c.usePotion(slot, target)) {
                    continue;
                }
            }
            Card best = null;
            for (Card card : c.player().hand()) {
                if (c.canPlay(card) && (best == null || c.effectiveCost(card) > c.effectiveCost(best))) {
                    best = card;
                }
            }
            if (best == null || rng.nextInt(10) == 0) {
                if (!c.endTurn()) {
                    throw new IllegalStateException("Could not end turn");
                }
                continue;
            }
            Enemy target = CardTarget.needsChoice(best.target()) ? pickTarget(c) : null;
            if (!c.playCard(best, target)) {
                throw new IllegalStateException("Playable card refused: " + best);
            }
        }
        if (!c.isOver()) {
            throw new IllegalStateException("Combat did not finish: " + room.encounterId() + " turn " + c.turn());
        }
        room.finish();
    }

    private Enemy pickTarget(Combat c) {
        List<Enemy> alive = c.activeEnemies();
        return alive.get(rng.nextInt(alive.size()));
    }
}
