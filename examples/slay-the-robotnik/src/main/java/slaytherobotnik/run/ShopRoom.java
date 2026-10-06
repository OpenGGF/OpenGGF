package slaytherobotnik.run;

import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.CardType;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.RelicTier;
import slaytherobotnik.core.Rng;
import slaytherobotnik.core.RunRngs;
import slaytherobotnik.core.RunState;

/**
 * The Egg Robo's shop, stocked as in Slay the Spire: five cards of the player's colour (two
 * Attacks, two Skills, one Power, one of them half price), two colourless cards, three relics
 * (the last always a shop relic), three potions and one card removal whose price rises each
 * time it is used.
 */
public final class ShopRoom implements Room {
    public static final String CARD = "Card";
    public static final String COLORLESS = "Colorless";
    public static final String RELIC = "Relic";
    public static final String POTION = "Potion";

    /** One thing on the counter. Exactly one of card/relicId/potion is set. */
    public record Item(String kind, Card card, String relicId, PotionDef potion, int price, boolean onSale) {
    }

    private final Run run;
    private final List<Item> items = new ArrayList<>();
    private final List<Boolean> sold = new ArrayList<>();
    private boolean removalUsed;
    private String lastLine;

    ShopRoom(Run run) {
        this.run = run;
        stock();
    }

    private void stock() {
        RunState state = run.state();
        Rng rng = state.rngs().stream(RunRngs.MERCHANT);
        RewardGenerator gen = new RewardGenerator(state);
        List<String> used = new ArrayList<>();
        String[] types = {CardType.ATTACK, CardType.ATTACK, CardType.SKILL, CardType.SKILL, CardType.POWER};
        int saleSlot = rng.nextInt(types.length);
        for (int i = 0; i < types.length; i++) {
            String rarity = shopRarity(rng);
            CardDef def = gen.randomCardOfType(types[i], rarity, used, rng);
            if (def == null) {
                continue;
            }
            used.add(def.id());
            int price = jitter(cardPrice(def.rarity()), rng);
            boolean sale = i == saleSlot;
            add(new Item(CARD, new Card(def), null, null, sale ? price / 2 : price, sale));
        }
        for (String rarity : new String[] {CardRarity.UNCOMMON, CardRarity.RARE}) {
            CardDef def = gen.randomColorless(rarity, used, rng);
            if (def != null) {
                used.add(def.id());
                add(new Item(COLORLESS, new Card(def), null, null,
                        jitter(Math.round(cardPrice(def.rarity()) * 1.2f), rng), false));
            }
        }
        for (int i = 0; i < 3; i++) {
            String tier = i == 2 ? RelicTier.SHOP : state.rollRelicTier(rng);
            String id = state.takeRelicFromPool(tier);
            if (id != null) {
                Relic sample = state.catalog().newRelic(id);
                add(new Item(RELIC, null, id, null, jitter(relicPrice(sample.tier()), rng), false));
            }
        }
        for (int i = 0; i < 3; i++) {
            PotionDef potion = gen.randomPotion();
            if (potion != null) {
                add(new Item(POTION, null, null, potion, jitter(potionPrice(potion.rarity()), rng), false));
            }
        }
    }

    private void add(Item item) {
        items.add(new Item(item.kind(), item.card(), item.relicId(), item.potion(), applyRelics(item.price()),
                item.onSale()));
        sold.add(false);
    }

    private int applyRelics(int price) {
        for (Relic relic : run.state().relics()) {
            price = relic.modifyShopPrice(run.state(), price);
        }
        return Math.max(0, price);
    }

    private static String shopRarity(Rng rng) {
        int roll = rng.nextInt(100);
        return roll < 9 ? CardRarity.RARE : roll < 46 ? CardRarity.UNCOMMON : CardRarity.COMMON;
    }

    static int cardPrice(String rarity) {
        return switch (rarity) {
            case CardRarity.RARE -> 150;
            case CardRarity.UNCOMMON -> 75;
            default -> 50;
        };
    }

    static int relicPrice(String tier) {
        return switch (tier) {
            case RelicTier.RARE -> 300;
            case RelicTier.UNCOMMON -> 250;
            default -> 150;
        };
    }

    static int potionPrice(String rarity) {
        return switch (rarity) {
            case CardRarity.RARE -> 100;
            case CardRarity.UNCOMMON -> 75;
            default -> 50;
        };
    }

    private static int jitter(int price, Rng rng) {
        return Math.round(price * (0.9f + rng.nextFloat() * 0.2f));
    }

    // ----- player actions -----

    public List<Item> items() { return List.copyOf(items); }
    public boolean sold(int index) { return sold.get(index); }

    /** Price of the card removal service. */
    public int removalPrice() {
        return applyRelics(75 + 25 * run.state().shopRemovals());
    }

    public boolean removalUsed() { return removalUsed; }

    /** The Egg Robo's latest line, for the speech bubble. */
    public String lastLine() { return lastLine; }

    public void say(String line) {
        lastLine = line;
    }

    /** Buys an item; returns false when it is sold out, unaffordable, or the potion belt is full. */
    public boolean buy(int index) {
        if (sold.get(index)) {
            return false;
        }
        Item item = items.get(index);
        RunState state = run.state();
        if (state.rings() < item.price()) {
            return false;
        }
        if (item.potion() != null && state.potionsFull()) {
            return false;
        }
        state.spendRings(item.price());
        switch (item.kind()) {
            case CARD, COLORLESS -> state.addCard(item.card());
            case RELIC -> state.obtainRelic(item.relicId());
            default -> state.addPotion(item.potion());
        }
        sold.set(index, true);
        for (Relic relic : state.relics()) {
            relic.onShopPurchase(state);
        }
        return true;
    }

    /** Opens the deck grid to remove a card; the rings are paid when a card is chosen. */
    public boolean startRemoval() {
        RunState state = run.state();
        if (removalUsed || state.rings() < removalPrice() || state.removableCards().isEmpty()) {
            return false;
        }
        run.openDeckChoice(new DeckChoice("Choose a card to remove.", state.removableCards(), 0, 1,
                DeckChoice.REMOVE, chosen -> {
                    if (chosen.isEmpty()) {
                        return;
                    }
                    state.spendRings(removalPrice());
                    state.removeCard(chosen.get(0));
                    state.setShopRemovals(state.shopRemovals() + 1);
                    removalUsed = true;
                }));
        return true;
    }

    public void leave() {
        run.leaveRoom();
    }
}
