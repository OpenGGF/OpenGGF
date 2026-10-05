package slaytherobotnik.content;

import java.util.Set;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.EventDef;
import slaytherobotnik.core.EventOption;

/** "?" room events. Each is a short dialogue written against {@link slaytherobotnik.core.EventContext}. */
public final class Events {
    private Events() {
    }

    public static void register(Catalog c) {
        // Golden Shrine / Wing Statue family: a gamble for a relic.
        c.addEvent(new EventDef("event:giant_ring", "Giant Ring", "event:giant_ring", Set.of(1, 2, 3), null, ctx ->
                ctx.page("A Giant Ring spins slowly above the treetops, humming with the power of the Chaos "
                                + "Emeralds. Something glitters on the other side.",
                        EventOption.of("[Jump In]", "Lose 8 HP. Obtain a random relic.", () -> {
                            ctx.run().loseHp(8);
                            String relic = ctx.obtainRandomRelic();
                            ctx.page("The world spins in blue and white. You tumble back out clutching "
                                    + (relic == null ? "nothing at all." : "the " + relic + "."), ctx.leave());
                        }),
                        ctx.leave())));

        // The Cleric: pay to heal or to remove a card.
        c.addEvent(new EventDef("event:starpost_medic", "Wandering Medic", "event:medic", Set.of(1, 2), null, ctx -> {
            int healCost = 35;
            int removeCost = 50;
            boolean canHeal = ctx.run().rings() >= healCost;
            boolean canRemove = ctx.run().rings() >= removeCost && !ctx.run().removableCards().isEmpty();
            ctx.page("A rabbit in a medic's cap waves from beside a Starpost. \"Patch you up? Or I can take "
                            + "something off your hands. Rings only, sorry!\"",
                    canHeal ? EventOption.of("[Heal]", "Pay " + healCost + " rings. Heal 25% of your Max HP.", () -> {
                        ctx.run().spendRings(healCost);
                        ctx.run().heal(ctx.run().maxHp() / 4);
                        ctx.page("You feel much better.", ctx.leave());
                    }) : EventOption.locked("[Heal]", "Requires " + healCost + " rings."),
                    canRemove ? EventOption.of("[Purify]", "Pay " + removeCost + " rings. Remove a card.", () ->
                            ctx.chooseCards("Choose a card to remove.", ctx.run().removableCards(), 1, 1, chosen -> {
                                ctx.run().spendRings(removeCost);
                                for (Card card : chosen) {
                                    ctx.run().removeCard(card);
                                }
                                ctx.page("\"There, all lighter now!\"", ctx.leave());
                            })) : EventOption.locked("[Purify]", "Requires " + removeCost + " rings."),
                    ctx.leave());
        }));
    }
}
