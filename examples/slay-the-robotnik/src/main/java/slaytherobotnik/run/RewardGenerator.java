package slaytherobotnik.run;

import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardColor;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.CardType;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.RelicTier;
import slaytherobotnik.core.Reward;
import slaytherobotnik.core.Rng;
import slaytherobotnik.core.RoomType;
import slaytherobotnik.core.RunRngs;
import slaytherobotnik.core.RunState;

/**
 * Slay the Spire's reward rules.
 *
 * <ul>
 *   <li>Rings: 10-20 for a fight, 25-35 for an elite, 95-105 for a boss.</li>
 *   <li>Cards: three to choose from. Rarity is rolled with a "rare bonus" that starts at
 *       -5% and grows by 1% for every common shown, resetting when a rare appears. Fights
 *       give rares 3% (+bonus) and uncommons 37%; elites 10% and 40%; bosses only rares.
 *       In act 2 a quarter of non-rare cards come upgraded, in act 3 half.</li>
 *   <li>Potions: dropped with a chance that starts at 40% and moves 10% towards dropping
 *       after every miss (and away after every drop).</li>
 *   <li>Elites also give a relic; bosses give a choice of three boss relics.</li>
 * </ul>
 */
public final class RewardGenerator {
    private final RunState run;

    public RewardGenerator(RunState run) {
        this.run = run;
    }

    /** Rewards for winning a fight in a room of {@code roomType}. */
    public List<Reward> combatRewards(String roomType) {
        List<Reward> rewards = new ArrayList<>();
        Rng treasure = run.rngs().stream(RunRngs.TREASURE);
        int rings = switch (roomType) {
            case RoomType.ELITE -> treasure.range(25, 35);
            case RoomType.BOSS -> treasure.range(95, 105);
            default -> treasure.range(10, 20);
        };
        for (Relic relic : run.relics()) {
            rings = relic.modifyCombatRings(run, rings);
        }
        rewards.add(new Reward.Rings(rings));
        if (!roomType.equals(RoomType.BOSS)) {
            PotionDef potion = rollPotionDrop();
            if (potion != null) {
                rewards.add(new Reward.Potion(potion));
            }
        }
        if (roomType.equals(RoomType.ELITE)) {
            String relic = run.takeRelicFromPool(run.rollRelicTier(run.rngs().stream(RunRngs.RELICS)));
            if (relic != null) {
                rewards.add(new Reward.RelicReward(relic));
            }
        }
        rewards.add(new Reward.CardChoice(cardChoices(roomType)));
        for (Relic relic : run.relics()) {
            relic.addCombatRewards(run, roomType, rewards);
        }
        if (roomType.equals(RoomType.BOSS)) {
            List<String> bossRelics = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                String id = run.takeRelicFromPool(RelicTier.BOSS);
                if (id != null) {
                    bossRelics.add(id);
                }
            }
            if (!bossRelics.isEmpty()) {
                rewards.add(new Reward.BossRelicChoice(bossRelics));
            }
        }
        return rewards;
    }

    /** Rolls whether a potion drops, adjusting the running chance. */
    public PotionDef rollPotionDrop() {
        Rng rng = run.rngs().stream(RunRngs.POTIONS);
        int chance = run.potionChance();
        if (rng.nextInt(100) < chance) {
            run.setPotionChance(chance - 10);
            return randomPotion();
        }
        run.setPotionChance(chance + 10);
        return null;
    }

    /** A random potion: 65% common, 25% uncommon, 10% rare. */
    public PotionDef randomPotion() {
        Rng rng = run.rngs().stream(RunRngs.POTIONS);
        int roll = rng.nextInt(100);
        String rarity = roll < 65 ? CardRarity.COMMON : roll < 90 ? CardRarity.UNCOMMON : CardRarity.RARE;
        List<PotionDef> pool = run.catalog().potions(rarity);
        if (pool.isEmpty()) {
            pool = run.catalog().potions(CardRarity.COMMON);
        }
        return pool.isEmpty() ? null : rng.pick(pool);
    }

    /** Three (or more, with relics) distinct cards for a card reward. */
    public List<Card> cardChoices(String roomType) {
        int count = 3;
        for (Relic relic : run.relics()) {
            count = relic.modifyCardRewardSize(run, count);
        }
        Rng rng = run.rngs().stream(RunRngs.CARDS);
        List<Card> picks = new ArrayList<>();
        List<String> used = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String rarity = rollRarity(roomType, rng);
            CardDef def = randomCardOf(run.character().color(), rarity, used, rng);
            if (def == null) {
                continue;
            }
            used.add(def.id());
            boolean upgraded = !rarity.equals(CardRarity.RARE) && rng.nextInt(100) < upgradeChance();
            picks.add(new Card(def, upgraded));
        }
        return picks;
    }

    private int upgradeChance() {
        return switch (run.act()) {
            case 1 -> 0;
            case 2 -> 25;
            default -> 50;
        };
    }

    /** Rolls a card rarity for a reward, updating the rare bonus as Slay the Spire does. */
    public String rollRarity(String roomType, Rng rng) {
        if (roomType.equals(RoomType.BOSS)) {
            return CardRarity.RARE;
        }
        int rareChance = roomType.equals(RoomType.ELITE) ? 10 : 3;
        int uncommonChance = roomType.equals(RoomType.ELITE) ? 40 : 37;
        // Lower rolls are rarer. The bonus starts at -5 (rares slightly less likely than
        // printed) and rises with every common, so a long dry spell makes a rare likely.
        int adjusted = rng.nextInt(100) - run.cardRareBonus();
        if (adjusted < rareChance) {
            run.setCardRareBonus(-5);
            return CardRarity.RARE;
        }
        if (adjusted < rareChance + uncommonChance) {
            return CardRarity.UNCOMMON;
        }
        run.setCardRareBonus(Math.min(40, run.cardRareBonus() + 1));
        return CardRarity.COMMON;
    }

    /** A random reward card of {@code color} and {@code rarity} not in {@code exclude}. */
    public CardDef randomCardOf(String color, String rarity, List<String> exclude, Rng rng) {
        List<CardDef> pool = new ArrayList<>();
        for (CardDef def : run.catalog().cards(color, rarity)) {
            if (!exclude.contains(def.id())) {
                pool.add(def);
            }
        }
        if (pool.isEmpty() && !rarity.equals(CardRarity.COMMON)) {
            return randomCardOf(color, CardRarity.COMMON, exclude, rng);
        }
        return pool.isEmpty() ? null : rng.pick(pool);
    }

    /** A random card of the player's colour of a given type (shop slots use this). */
    public CardDef randomCardOfType(String type, String rarity, List<String> exclude, Rng rng) {
        List<CardDef> pool = new ArrayList<>();
        for (CardDef def : run.catalog().cards(run.character().color(), rarity)) {
            if (def.type().equals(type) && !exclude.contains(def.id())) {
                pool.add(def);
            }
        }
        if (pool.isEmpty()) {
            return randomCardOf(run.character().color(), rarity, exclude, rng);
        }
        return rng.pick(pool);
    }

    /** Transform: a random card of the same colour (any non-basic rarity) other than the original. */
    public CardDef transformTarget(Card original, Rng rng) {
        String color = original.color().equals(CardColor.CURSE) || original.color().equals(CardColor.STATUS)
                ? run.character().color() : original.color();
        List<CardDef> pool = new ArrayList<>();
        for (String rarity : new String[] {CardRarity.COMMON, CardRarity.UNCOMMON, CardRarity.RARE}) {
            for (CardDef def : run.catalog().cards(color, rarity)) {
                if (!def.id().equals(original.id())) {
                    pool.add(def);
                }
            }
        }
        if (pool.isEmpty()) {
            return original.def();
        }
        return rng.pick(pool);
    }

    /** Random colorless card of a rarity. */
    public CardDef randomColorless(String rarity, List<String> exclude, Rng rng) {
        return randomCardOf(CardColor.COLORLESS, rarity, exclude, rng);
    }

    /** True for cards that may appear in rewards and shops. */
    public static boolean rewardable(CardDef def) {
        return !def.type().equals(CardType.STATUS) && !def.type().equals(CardType.CURSE)
                && !def.rarity().equals(CardRarity.BASIC) && !def.rarity().equals(CardRarity.SPECIAL);
    }
}
