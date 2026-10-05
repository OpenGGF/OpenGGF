package slaytherobotnik.run;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.Reward;
import slaytherobotnik.core.RunState;
import slaytherobotnik.core.RunStats;

/**
 * Saves a run as plain {@code key=value} text (Java {@link Properties}) and restores it.
 *
 * <p>Only ids and numbers are stored; cards, relics and potions are rebuilt from the
 * {@link Catalog}. Maps are not stored at all: they are regenerated from the seed. A run
 * saved on entering a room is resumed by entering the room again with the same random
 * streams, so the same fight or shop appears.
 */
public final class SaveCodec {
    /** Bump when the format changes incompatibly; older saves are then ignored. */
    public static final int VERSION = 1;

    private SaveCodec() {
    }

    public static String encode(Run run) {
        RunState s = run.state();
        Properties p = new Properties();
        p.setProperty("version", Integer.toString(VERSION));
        p.setProperty("seed", Long.toString(s.seed()));
        p.setProperty("character", s.character().id());
        p.setProperty("ascension", Integer.toString(s.ascension()));
        p.setProperty("hp", Integer.toString(s.hp()));
        p.setProperty("maxHp", Integer.toString(s.maxHp()));
        p.setProperty("rings", Integer.toString(s.rings()));
        p.setProperty("act", Integer.toString(s.act()));
        p.setProperty("floor", Integer.toString(s.floor()));
        p.setProperty("actFloor", Integer.toString(s.actFloor()));
        p.setProperty("nodeX", Integer.toString(s.nodeX()));
        p.setProperty("deck", joinCards(s.deck()));
        List<String> relics = new ArrayList<>();
        for (Relic r : s.relics()) {
            relics.add(r.id() + "|" + r.counter() + "|" + (r.usedUp() ? 1 : 0));
        }
        p.setProperty("relics", String.join(";", relics));
        List<String> potions = new ArrayList<>();
        for (PotionDef potion : s.potions()) {
            potions.add(potion.id());
        }
        p.setProperty("potions", String.join(";", potions));
        s.rngs().states().forEach((k, v) -> p.setProperty("rng." + k, Long.toString(v)));
        s.relicPools().forEach((tier, pool) -> p.setProperty("pool." + tier, String.join(";", pool)));
        p.setProperty("cardRareBonus", Integer.toString(s.cardRareBonus()));
        p.setProperty("potionChance", Integer.toString(s.potionChance()));
        p.setProperty("shopRemovals", Integer.toString(s.shopRemovals()));
        p.setProperty("seenEvents", String.join(";", s.seenEvents()));
        p.setProperty("monsterQueue", String.join(";", s.monsterQueue()));
        p.setProperty("eliteQueue", String.join(";", s.eliteQueue()));
        p.setProperty("boss", s.boss() == null ? "" : s.boss());
        p.setProperty("unknown", s.unknownMonsterChance() + ";" + s.unknownShopChance() + ";"
                + s.unknownTreasureChance());
        s.counters().forEach((k, v) -> p.setProperty("counter." + k, Integer.toString(v)));
        writeStats(p, s.stats());
        p.setProperty("savePoint", run.savePoint());
        p.setProperty("bossRewards", Boolean.toString(run.bossRewards()));
        if (run.room() instanceof RewardRoom rewards && run.savePoint().equals(Run.SAVE_REWARDS)) {
            p.setProperty("rewards.title", rewards.title());
            List<Reward> list = rewards.rewards();
            p.setProperty("rewards.count", Integer.toString(list.size()));
            for (int i = 0; i < list.size(); i++) {
                p.setProperty("rewards." + i, encodeReward(list.get(i)));
                p.setProperty("rewards." + i + ".claimed", Boolean.toString(rewards.claimed(i)));
            }
        }
        StringWriter out = new StringWriter();
        try {
            p.store(out, "Slay the Robotnik run save");
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return out.toString();
    }

    /** Restores a run, or returns null when the text is not a compatible save. */
    public static Run decode(Catalog catalog, String text) {
        Properties p = new Properties();
        try {
            p.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException bad) {
            return null;
        }
        if (!Integer.toString(VERSION).equals(p.getProperty("version"))) {
            return null;
        }
        try {
            return restore(catalog, p);
        } catch (RuntimeException incompatible) {
            // A save naming content that no longer exists is treated as absent.
            return null;
        }
    }

    private static Run restore(Catalog catalog, Properties p) {
        RunState s = new RunState(catalog, catalog.character(p.getProperty("character")),
                Long.parseLong(p.getProperty("seed")));
        s.setAscension(intOf(p, "ascension"));
        s.setMaxHp(intOf(p, "maxHp"));
        s.setHp(intOf(p, "hp"));
        s.loseRings(s.rings());
        s.gainRings(intOf(p, "rings"));
        for (String entry : list(p, "deck")) {
            String[] parts = entry.split("\\|");
            Card card = new Card(catalog.card(parts[0]), parts.length > 1 && parts[1].equals("1"));
            if (parts.length > 2) {
                card.setMisc(Integer.parseInt(parts[2]));
            }
            s.deck().add(card);
        }
        for (String entry : list(p, "relics")) {
            String[] parts = entry.split("\\|");
            Relic relic = catalog.newRelic(parts[0]);
            relic.setCounter(Integer.parseInt(parts[1]));
            relic.setUsedUp(parts[2].equals("1"));
            s.relics().add(relic);
        }
        for (String id : list(p, "potions")) {
            s.potions().add(catalog.potion(id));
        }
        Map<String, Long> states = new LinkedHashMap<>();
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("rng.")) {
                states.put(key.substring(4), Long.parseLong(p.getProperty(key)));
            } else if (key.startsWith("pool.")) {
                Deque<String> pool = new ArrayDeque<>(list(p, key));
                s.relicPools().put(key.substring(5), pool);
            } else if (key.startsWith("counter.")) {
                s.setCounter(key.substring(8), Integer.parseInt(p.getProperty(key)));
            }
        }
        s.rngs().restore(states);
        s.setCardRareBonus(intOf(p, "cardRareBonus"));
        s.setPotionChance(intOf(p, "potionChance"));
        s.setShopRemovals(intOf(p, "shopRemovals"));
        s.seenEvents().addAll(list(p, "seenEvents"));
        s.monsterQueue().addAll(list(p, "monsterQueue"));
        s.eliteQueue().addAll(list(p, "eliteQueue"));
        String boss = p.getProperty("boss", "");
        s.setBoss(boss.isEmpty() ? null : boss);
        List<String> unknown = list(p, "unknown");
        s.setUnknownChances(Integer.parseInt(unknown.get(0)), Integer.parseInt(unknown.get(1)),
                Integer.parseInt(unknown.get(2)));
        readStats(p, s.stats());
        s.setPosition(intOf(p, "act"), intOf(p, "floor"), intOf(p, "actFloor"), intOf(p, "nodeX"));

        Run run = new Run(s);
        String savePoint = p.getProperty("savePoint", Run.SAVE_MAP);
        boolean bossRewards = Boolean.parseBoolean(p.getProperty("bossRewards", "false"));
        run.restore(run.buildMap(), new MapRoom(), savePoint, bossRewards);
        switch (savePoint) {
            case Run.SAVE_START -> run.enterStart();
            case Run.SAVE_ROOM -> run.reenterCurrentRoom();
            case Run.SAVE_REWARDS -> {
                int count = intOf(p, "rewards.count");
                List<Reward> rewards = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    rewards.add(decodeReward(catalog, p.getProperty("rewards." + i)));
                }
                RewardRoom room = new RewardRoom(run, p.getProperty("rewards.title", "Rewards"), rewards);
                for (int i = 0; i < count; i++) {
                    room.claimedFlags()[i] = Boolean.parseBoolean(p.getProperty("rewards." + i + ".claimed"));
                }
                run.setRoom(room);
            }
            default -> { }
        }
        return run;
    }

    private static String joinCards(List<Card> cards) {
        List<String> out = new ArrayList<>();
        for (Card c : cards) {
            out.add(c.id() + "|" + (c.upgraded() ? 1 : 0) + "|" + c.misc());
        }
        return String.join(";", out);
    }

    private static String encodeReward(Reward reward) {
        return switch (reward) {
            case Reward.Rings r -> "rings:" + r.amount();
            case Reward.Potion pot -> "potion:" + pot.potion().id();
            case Reward.RelicReward r -> "relic:" + r.relicId();
            case Reward.CardChoice c -> "cards:" + joinCards(c.cards());
            case Reward.BossRelicChoice b -> "boss:" + String.join(";", b.relicIds());
        };
    }

    private static Reward decodeReward(Catalog catalog, String text) {
        int colon = text.indexOf(':');
        String kind = text.substring(0, colon);
        String body = text.substring(colon + 1);
        return switch (kind) {
            case "rings" -> new Reward.Rings(Integer.parseInt(body));
            case "potion" -> new Reward.Potion(catalog.potion(body));
            case "relic" -> new Reward.RelicReward(body);
            case "boss" -> new Reward.BossRelicChoice(splitList(body));
            default -> {
                List<Card> cards = new ArrayList<>();
                for (String entry : splitList(body)) {
                    String[] parts = entry.split("\\|");
                    cards.add(new Card(catalog.card(parts[0]), parts[1].equals("1")));
                }
                yield new Reward.CardChoice(cards);
            }
        };
    }

    private static void writeStats(Properties p, RunStats st) {
        p.setProperty("stats", st.enemiesDefeated + ";" + st.elitesDefeated + ";" + st.bossesDefeated + ";"
                + st.ringsCollected + ";" + st.ringsSpent + ";" + st.cardsAdded + ";" + st.cardsRemoved + ";"
                + st.cardsUpgraded + ";" + st.damageTaken + ";" + st.floorsClimbed + ";" + st.potionsUsed + ";"
                + st.eventsSeen + ";" + st.restsTaken + ";" + st.smiths + ";" + st.maxDamageInOneHit);
    }

    private static void readStats(Properties p, RunStats st) {
        List<String> v = list(p, "stats");
        if (v.size() < 15) {
            return;
        }
        int i = 0;
        st.enemiesDefeated = Integer.parseInt(v.get(i++));
        st.elitesDefeated = Integer.parseInt(v.get(i++));
        st.bossesDefeated = Integer.parseInt(v.get(i++));
        st.ringsCollected = Integer.parseInt(v.get(i++));
        st.ringsSpent = Integer.parseInt(v.get(i++));
        st.cardsAdded = Integer.parseInt(v.get(i++));
        st.cardsRemoved = Integer.parseInt(v.get(i++));
        st.cardsUpgraded = Integer.parseInt(v.get(i++));
        st.damageTaken = Integer.parseInt(v.get(i++));
        st.floorsClimbed = Integer.parseInt(v.get(i++));
        st.potionsUsed = Integer.parseInt(v.get(i++));
        st.eventsSeen = Integer.parseInt(v.get(i++));
        st.restsTaken = Integer.parseInt(v.get(i++));
        st.smiths = Integer.parseInt(v.get(i++));
        st.maxDamageInOneHit = Integer.parseInt(v.get(i));
    }

    private static int intOf(Properties p, String key) {
        return Integer.parseInt(p.getProperty(key, "0").trim());
    }

    private static List<String> list(Properties p, String key) {
        return splitList(p.getProperty(key, ""));
    }

    private static List<String> splitList(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return out;
        }
        for (String part : value.split(";")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }
}
