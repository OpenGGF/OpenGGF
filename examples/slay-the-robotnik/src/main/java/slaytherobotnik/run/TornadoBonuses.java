package slaytherobotnik.run;

import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.RelicTier;
import slaytherobotnik.core.Rng;
import slaytherobotnik.core.RunRngs;
import slaytherobotnik.core.RunState;

/**
 * The cargo-hold bonuses offered on the Tornado at the start of each act.
 *
 * <p>Act 1 follows Neow's four rows in Slay the Spire: a small bonus, another small bonus, a
 * big bonus with a drawback, and trading the starting relic for a random boss relic. Later
 * acts offer three smaller bonuses, like the act transitions in Slay the Spire 2.
 */
final class TornadoBonuses {
    /** Run counter: fights left in which enemies start with 1 HP. */
    static final String WEAK_ENEMIES = "start:weakEnemies";

    private TornadoBonuses() {
    }

    static List<StartRoom.Option> forAct(Run run) {
        RunState state = run.state();
        Rng rng = state.rngs().stream(RunRngs.START);
        return state.act() == 1 ? firstAct(run, rng) : laterAct(run, rng);
    }

    private static List<StartRoom.Option> firstAct(Run run, Rng rng) {
        RunState state = run.state();
        List<StartRoom.Option> options = new ArrayList<>();
        int smallHp = Math.max(1, Math.round(state.maxHp() * 0.1f));

        List<StartRoom.Option> rowA = List.of(
                new StartRoom.Option("[Spare Gear]", "Choose a card to obtain.", () -> chooseCards(run, null, 3)),
                new StartRoom.Option("[Field Rations]", "Max HP +" + smallHp + ".", () -> state.gainMaxHp(smallHp)),
                new StartRoom.Option("[Jammer]", "Enemies in your next 3 fights have 1 HP.",
                        () -> state.setCounter(WEAK_ENEMIES, 3)));
        options.add(rng.pick(rowA));

        List<StartRoom.Option> rowB = List.of(
                new StartRoom.Option("[Jettison]", "Remove a card from your deck.", () -> removeCards(run, 1)),
                new StartRoom.Option("[Tune-Up]", "Upgrade a card.", () -> upgradeCard(run)),
                new StartRoom.Option("[Rewire]", "Transform a card.", () -> transformCards(run, 1)),
                new StartRoom.Option("[Lucky Find]", "Obtain a random common relic.",
                        () -> obtainRelic(state, RelicTier.COMMON)),
                new StartRoom.Option("[Item Monitors]", "Obtain 3 random potions.", () -> potions(run, 3)));
        options.add(rng.pick(rowB));

        String[] drawbacks = {"maxhp", "rings", "curse", "damage"};
        String drawback = drawbacks[rng.nextInt(drawbacks.length)];
        int bigHp = Math.max(1, Math.round(state.maxHp() * 0.2f));
        List<StartRoom.Option> rewards = new ArrayList<>();
        rewards.add(new StartRoom.Option("", "Remove 2 cards.", () -> removeCards(run, 2)));
        rewards.add(new StartRoom.Option("", "Transform 2 cards.", () -> transformCards(run, 2)));
        rewards.add(new StartRoom.Option("", "Choose a rare card to obtain.",
                () -> chooseCards(run, CardRarity.RARE, 3)));
        rewards.add(new StartRoom.Option("", "Obtain a random rare relic.", () -> obtainRelic(state, RelicTier.RARE)));
        if (!drawback.equals("rings")) {
            rewards.add(new StartRoom.Option("", "Gain 250 rings.", () -> state.gainRings(250)));
        }
        if (!drawback.equals("maxhp")) {
            rewards.add(new StartRoom.Option("", "Max HP +" + bigHp + ".", () -> state.gainMaxHp(bigHp)));
        }
        StartRoom.Option reward = rng.pick(rewards);
        String cost;
        Runnable pay;
        switch (drawback) {
            case "maxhp" -> {
                int loss = Math.max(1, Math.round(state.maxHp() * 0.1f));
                cost = "Lose " + loss + " Max HP.";
                pay = () -> state.setMaxHp(state.maxHp() - loss);
            }
            case "rings" -> {
                cost = "Lose all rings.";
                pay = () -> state.loseRings(state.rings());
            }
            case "curse" -> {
                CardDef curse = randomCurse(state, rng);
                cost = "Obtain a curse" + (curse == null ? "." : " (" + curse.name() + ").");
                pay = () -> {
                    if (curse != null) {
                        state.addCard(new Card(curse));
                    }
                };
            }
            default -> {
                int dmg = Math.max(1, state.hp() * 3 / 10);
                cost = "Take " + dmg + " damage.";
                pay = () -> state.loseHp(dmg);
            }
        }
        options.add(new StartRoom.Option("[Overload the Engines]", cost + " " + reward.detail(), () -> {
            pay.run();
            reward.apply().run();
        }));

        options.add(new StartRoom.Option("[Trade Spare Parts]",
                "Lose your starting relic. Obtain a random boss relic.", () -> {
                    state.relics().removeIf(r -> r.id().equals(state.character().startingRelic()));
                    obtainRelic(state, RelicTier.BOSS);
                }));
        return options;
    }

    private static List<StartRoom.Option> laterAct(Run run, Rng rng) {
        RunState state = run.state();
        int heal = Math.max(1, Math.round(state.maxHp() * 0.25f));
        List<StartRoom.Option> pool = new ArrayList<>(List.of(
                new StartRoom.Option("[Patch Up]", "Heal " + heal + " HP.", () -> state.heal(heal)),
                new StartRoom.Option("[Tune-Up]", "Upgrade a card.", () -> upgradeCard(run)),
                new StartRoom.Option("[Supply Drop]", "Obtain 2 random potions.", () -> potions(run, 2)),
                new StartRoom.Option("[Cargo]", "Gain 100 rings.", () -> state.gainRings(100)),
                new StartRoom.Option("[Jettison]", "Remove a card from your deck.", () -> removeCards(run, 1)),
                new StartRoom.Option("[Spare Gear]", "Choose a card to obtain.", () -> chooseCards(run, null, 3))));
        rng.shuffle(pool);
        return new ArrayList<>(pool.subList(0, 3));
    }

    // ----- shared actions -----

    static void chooseCards(Run run, String rarity, int count) {
        RunState state = run.state();
        Rng rng = state.rngs().stream(RunRngs.CARDS);
        RewardGenerator gen = new RewardGenerator(state);
        List<Card> offered = new ArrayList<>();
        List<String> used = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String r = rarity != null ? rarity : (rng.nextInt(100) < 70 ? CardRarity.COMMON : CardRarity.UNCOMMON);
            CardDef def = gen.randomCardOf(state.character().color(), r, used, rng);
            if (def != null) {
                used.add(def.id());
                offered.add(new Card(def));
            }
        }
        run.openDeckChoice(new DeckChoice("Choose a card to obtain.", offered, 1, 1, DeckChoice.PICK,
                chosen -> chosen.forEach(state::addCard)));
    }

    static void removeCards(Run run, int count) {
        RunState state = run.state();
        run.openDeckChoice(new DeckChoice("Choose " + (count == 1 ? "a card" : count + " cards") + " to remove.",
                state.removableCards(), count, count, DeckChoice.REMOVE, chosen -> chosen.forEach(state::removeCard)));
    }

    static void upgradeCard(Run run) {
        RunState state = run.state();
        run.openDeckChoice(new DeckChoice("Choose a card to upgrade.", state.upgradableCards(), 1, 1,
                DeckChoice.UPGRADE, chosen -> chosen.forEach(state::upgradeCard)));
    }

    static void transformCards(Run run, int count) {
        RunState state = run.state();
        run.openDeckChoice(new DeckChoice("Choose " + (count == 1 ? "a card" : count + " cards") + " to transform.",
                state.removableCards(), count, count, DeckChoice.TRANSFORM, chosen -> {
                    Rng rng = state.rngs().stream(RunRngs.CARDS);
                    RewardGenerator gen = new RewardGenerator(state);
                    for (Card c : chosen) {
                        state.removeCard(c);
                        state.addCard(new Card(gen.transformTarget(c, rng)));
                    }
                }));
    }

    static void obtainRelic(RunState state, String tier) {
        String id = state.takeRelicFromPool(tier);
        if (id != null) {
            state.obtainRelic(id);
        }
    }

    static void potions(Run run, int count) {
        RunState state = run.state();
        RewardGenerator gen = new RewardGenerator(state);
        for (int i = 0; i < count; i++) {
            PotionDef p = gen.randomPotion();
            if (p != null) {
                state.addPotion(p);
            }
        }
    }

    static CardDef randomCurse(RunState state, Rng rng) {
        List<CardDef> curses = state.catalog().cards(CardColor.CURSE, CardRarity.CURSE);
        return curses.isEmpty() ? null : rng.pick(curses);
    }
}
