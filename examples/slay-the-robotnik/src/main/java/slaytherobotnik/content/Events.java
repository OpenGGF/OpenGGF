package slaytherobotnik.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.CardDef;
import slaytherobotnik.core.CardRarity;
import slaytherobotnik.core.CardType;
import slaytherobotnik.core.Catalog;
import slaytherobotnik.core.EncounterDef;
import slaytherobotnik.core.EventContext;
import slaytherobotnik.core.EventDef;
import slaytherobotnik.core.EventOption;
import slaytherobotnik.core.PotionDef;
import slaytherobotnik.core.Relic;
import slaytherobotnik.core.RelicTier;
import slaytherobotnik.core.Reward;
import slaytherobotnik.core.RunState;

/**
 * "?" room events. Each is a short dialogue written against {@link EventContext}: show a
 * page, and each option changes the run and shows the next page or leaves. The comment above
 * each event names the Slay the Spire event it follows.
 */
public final class Events {
    private Events() {
    }

    public static void register(Catalog c) {
        // Golden Wing family: a gamble for a relic.
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
            ctx.page("A rabbit with a first-aid kit waves from beside a Starpost. \"Patch you up? Or I can take "
                            + "something off your hands. Rings only, sorry!\"",
                    canHeal ? EventOption.of("[Heal]", "Pay " + healCost + " rings. Heal 25% of your Max HP.", () -> {
                        ctx.page("The rabbit counts your rings, then gives the Starpost a spin.");
                        ctx.illustrate("heal", () -> {
                            ctx.run().spendRings(healCost);
                            ctx.run().heal(ctx.run().maxHp() / 4);
                            ctx.page("You feel much better.", ctx.leave());
                        });
                    }) : EventOption.locked("[Heal]", "Requires " + healCost + " rings."),
                    canRemove ? EventOption.of("[Purify]", "Pay " + removeCost + " rings. Remove a card.", () ->
                            ctx.chooseCards("Choose a card to remove.", ctx.run().removableCards(), 1, 1, chosen -> {
                                ctx.page("The rabbit takes your rings and the card, and gives a little hop...");
                                ctx.illustrate("purify:" + named(chosen.get(0)), () -> {
                                    ctx.run().spendRings(removeCost);
                                    for (Card card : chosen) {
                                        ctx.run().removeCard(card);
                                    }
                                    ctx.page("\"There, all lighter now!\"", ctx.leave());
                                });
                            })) : EventOption.locked("[Purify]", "Requires " + removeCost + " rings."),
                    ctx.leave());
        }));

        // Big Fish.
        c.addEvent(new EventDef("event:monitor_row", "Monitor Row", "event:monitor_row", Set.of(1), null, ctx -> {
            int heal = ctx.run().maxHp() / 3;
            ctx.page("Three monitors sit in a neat row on a grassy ledge, as if someone left them out for you. "
                            + "You only have time to break one.",
                    EventOption.of("[Super Ring]", "Heal " + heal + " HP.", () -> {
                        ctx.run().heal(heal);
                        ctx.page("Rings burst out and spin around you. You feel refreshed.", ctx.leave());
                    }),
                    EventOption.of("[1-Up]", "Max HP +5.", () -> {
                        ctx.run().gainMaxHp(5);
                        ctx.page("A little chime plays. You feel sturdier.", ctx.leave());
                    }),
                    EventOption.of("[? Monitor]", "Obtain a random relic. Become <r>Cursed</r>: Robotnik's Laugh.",
                            () -> {
                                ctx.obtainRandomRelic();
                                ctx.obtainCard(CommonCards.ROBOTNIKS_LAUGH, false);
                                ctx.page("Robotnik's face flickers on the screen. You pocket the prize, but the "
                                        + "laughter follows you.", ctx.leave());
                            }));
        }));

        // Wheel of Change.
        c.addEvent(new EventDef("event:slot_machine", "Slot Machine", "event:slot_machine", Set.of(1, 2, 3), null,
                ctx -> ctx.page("A Casino Night slot machine has washed up here, still lit and still humming. "
                                + "Its lever hangs invitingly at its side.",
                        EventOption.of("[Pull the Lever]", "Spin the reels.", () -> spin(ctx)),
                        ctx.leave())));

        // Golden Idol.
        c.addEvent(new EventDef("event:emerald_altar", "Emerald Altar", "event:emerald_altar", Set.of(1),
                run -> !run.hasRelic(Relics.EMERALD_IDOL), ctx ->
                ctx.page("Deep in the ruins, a green idol rests on a stone pedestal. The floor around it is "
                                + "suspiciously well worn.",
                        EventOption.of("[Take]", "Obtain <g>Emerald Idol</g>.", () -> {
                            ctx.page("You lift the idol from its pedestal...");
                            ctx.illustrate("take", () -> emeraldAltarTaken(ctx));
                        }),
                        ctx.leave())));

        // Living Wall.
        c.addEvent(new EventDef("event:hidden_palace_mural", "Hidden Palace Mural", "event:mural", Set.of(1), null,
                ctx -> ctx.page("A mural of a golden hedgehog covers the wall. Three emerald sockets glow beneath "
                                + "it, each beside a carved word.",
                        EventOption.of("[Forget]", "Remove a card from your deck.", () ->
                                ctx.removeCards(1, () -> mural(ctx, "forget", "FORGET",
                                        "The socket dims. You feel lighter."))),
                        EventOption.of("[Change]", "Transform a card in your deck.", () ->
                                ctx.transformCards(1, () -> mural(ctx, "change", "CHANGE",
                                        "The socket flickers through every colour."))),
                        EventOption.of("[Grow]", "Upgrade a card in your deck.", () ->
                                ctx.upgradeCards(1, () -> mural(ctx, "grow", "GROW", "The socket blazes gold."))))));

        // Dead Adventurer.
        c.addEvent(new EventDef("event:badnik_scrapyard", "Badnik Scrapyard", "event:scrapyard", Set.of(1), null,
                Events::scrapyard));

        // Hypnotizing Colored Mushrooms.
        c.addEvent(new EventDef("event:strange_mushrooms", "Strange Mushrooms", "event:mushrooms", Set.of(1), null,
                ctx -> {
                    int heal = ctx.run().maxHp() / 4;
                    ctx.page("Giant mushrooms sway in a clearing, far too bouncy to be natural. Something is "
                                    + "rustling underneath the caps.",
                            EventOption.of("[Stomp]", "<r>Anger the badniks.</r> Win a fight for a relic.", () -> {
                                ctx.addReward(new Reward.RelicReward(Relics.MUSHROOM_CAP));
                                ctx.fight("aiz:wildlife", null);
                            }),
                            EventOption.of("[Eat]", "Heal " + heal + " HP. Become <r>Cursed</r>: Tangled.", () -> {
                                ctx.run().heal(heal);
                                ctx.obtainCard(CommonCards.TANGLED, false);
                                ctx.page("Delicious. Your legs feel strange, though.", ctx.leave());
                            }));
                }));

        // Golden Shrine.
        c.addEvent(new EventDef("event:ring_shrine", "Ring Shrine", "event:ring_shrine", Set.of(1, 2), null, ctx ->
                ctx.page("A shrine of golden rings stands in a quiet glade. The offering bowl is overflowing.",
                        EventOption.of("[Pray]", "Gain 100 rings.", () -> {
                            ctx.run().gainRings(100);
                            ctx.page("A handful of rings rolls into your hands.", ctx.leave());
                        }),
                        EventOption.of("[Desecrate]", "Gain 275 rings. Become <r>Cursed</r>: Lost Rings.", () -> {
                            ctx.run().gainRings(275);
                            ctx.obtainCard(CommonCards.LOST_RINGS, false);
                            ctx.page("You scoop out the whole bowl. Somewhere, rings start slipping through "
                                    + "your fingers.", ctx.leave());
                        }),
                        ctx.leave())));

        // Upgrade Shrine.
        c.addEvent(new EventDef("event:workbench", "Abandoned Workbench", "event:workbench", Set.of(1, 2, 3),
                run -> !run.upgradableCards().isEmpty(), ctx -> {
                    boolean tails = ctx.run().character().id().equals(Characters.TAILS);
                    ctx.page(tails
                                    ? "Your own workbench! You must have left it here last time. The tools are "
                                            + "just where you put them."
                                    : "Someone has left a workbench out here, tools neatly laid out. A note reads: "
                                            + "BACK IN 5 MINUTES. -T.",
                            EventOption.of("[Tinker]", "Upgrade a card.", () -> {
                                List<Card> before = ctx.run().upgradableCards();
                                ctx.upgradeCards(1, () -> {
                                    Card upgraded = firstUpgraded(before);
                                    ctx.page(tails ? "You pick up your spanner and get to work." : "You borrow the tools. "
                                            + "Sparks fly.");
                                    ctx.illustrate("tinker" + (upgraded == null ? "" : ":" + named(upgraded)),
                                            () -> ctx.page("Good as new. Better, even.", ctx.leave()));
                                });
                            }),
                            ctx.leave());
                }));

        // Purifier.
        c.addEvent(new EventDef("event:waterfall", "Purifying Waterfall", "event:waterfall", Set.of(1, 2, 3),
                run -> !run.removableCards().isEmpty(), ctx ->
                ctx.page("A clear waterfall pours into a pool so clean it sparkles. Standing under it, you feel "
                                + "you could wash something away for good.",
                        EventOption.of("[Wash]", "Remove a card from your deck.", () ->
                                ctx.removeCards(1, () -> ctx.page("The water carries it off downstream.",
                                        ctx.leave()))),
                        ctx.leave())));

        // Transmogrifier.
        c.addEvent(new EventDef("event:special_stage", "Special Stage Warp", "event:special_stage", Set.of(1, 2, 3),
                run -> !run.removableCards().isEmpty(), ctx ->
                ctx.page("A ring of shimmering stars hangs in the air. Through it, a checkered sphere spins.",
                        EventOption.of("[Enter]", "Transform a card.", () ->
                                ctx.transformCards(1, () -> ctx.page("Blue sphere, red sphere, blue sphere... you "
                                        + "come out with something different.", ctx.leave()))),
                        ctx.leave())));

        // Duplicator.
        c.addEvent(new EventDef("event:mirror_monitor", "Mirror Monitor", "event:mirror_monitor", Set.of(2, 3), null,
                ctx -> ctx.page("A monitor shows your own reflection. Then a second reflection appears beside the "
                                + "first.",
                        EventOption.of("[Copy]", "Duplicate a card in your deck.", () -> {
                            int before = ctx.run().deck().size();
                            ctx.duplicateCard(() -> {
                                List<Card> deck = ctx.run().deck();
                                Card copy = deck.size() > before ? deck.get(before) : null;
                                ctx.page("You hold the card up to the screen. Your reflection reaches back...");
                                ctx.illustrate("copy" + (copy == null ? "" : ":" + named(copy)),
                                        () -> ctx.page("The screen fizzles out. Your deck feels heavier.", ctx.leave()));
                            });
                        }),
                        ctx.leave())));

        // Scrap Ooze.
        c.addEvent(new EventDef("event:clogged_pipe", "Clogged Pipe", "event:clogged_pipe", Set.of(2), null,
                ctx -> cloggedPipe(ctx, 3, 25, "A thick pipe gurgles in the wall. Something shiny is stuck deep "
                        + "inside, behind a lot of sharp scrap.")));

        // Bonfire Spirits.
        c.addEvent(new EventDef("event:flicky_campfire", "Flicky Campfire", "event:campfire", Set.of(1, 2, 3),
                run -> !run.removableCards().isEmpty(), ctx ->
                ctx.page("A flock of Flickies huddles around a small campfire. They peep, and look meaningfully "
                                + "at your deck.",
                        EventOption.of("[Offer]", "Offer a card to the fire.", () ->
                                ctx.chooseCards("Choose a card to offer.", ctx.run().removableCards(), 1, 1, chosen -> {
                                    Card card = chosen.get(0);
                                    ctx.run().removeCard(card);
                                    ctx.page("You lay the " + card.name() + " on the fire...");
                                    ctx.illustrate(card.rarity() + "|" + card.id() + "|" + (card.upgraded() ? 1 : 0)
                                            + "|" + campfireGain(ctx.run(), card), () ->
                                            ctx.page(campfire(ctx, card), ctx.leave()));
                                })),
                        ctx.leave())));

        // The Library.
        c.addEvent(new EventDef("event:archive_terminal", "Archive Terminal", "event:terminal", Set.of(2), null, ctx -> {
            int heal = ctx.run().maxHp() / 3;
            ctx.page("A terminal in the base's archive still has power. Robotnik's files scroll past: battle "
                            + "plans, schematics, and a surprising number of moustache care tips.",
                    EventOption.of("[Read]", "Choose 1 of 20 cards to add to your deck.", () -> {
                        List<Card> cards = new ArrayList<>();
                        List<String> seen = new ArrayList<>();
                        for (int i = 0; i < 40 && cards.size() < 20; i++) {
                            CardDef def = ctx.randomCard(null);
                            if (def != null && !seen.contains(def.id())) {
                                seen.add(def.id());
                                cards.add(new Card(def));
                            }
                        }
                        ctx.chooseCards("Choose a card to add to your deck.", cards, 1, 1, chosen -> {
                            ctx.page("The file starts to download...");
                            ctx.illustrate("read:" + named(chosen.get(0)), () -> {
                                ctx.obtainCard(chosen.get(0).id(), false);
                                ctx.page("You download the file and close the terminal.", ctx.leave());
                            });
                        });
                    }),
                    EventOption.of("[Rest]", "Heal " + heal + " HP.", () -> {
                        ctx.page("You sit down by the warm, humming servers. Just for a minute...");
                        ctx.illustrate("rest", () -> {
                            ctx.run().heal(heal);
                            ctx.page("The hum of the servers sends you to sleep.", ctx.leave());
                        });
                    }));
        }));

        // The Lab.
        c.addEvent(new EventDef("event:robotnik_lab", "Robotnik's Lab", "event:lab", Set.of(2), null, ctx ->
                ctx.page("Bubbling tubes line the walls of an abandoned laboratory. Most are labelled DO NOT DRINK.",
                        EventOption.of("[Search]", "Find 3 random potions.", () -> {
                            List<PotionDef> found = new ArrayList<>();
                            for (int i = 0; i < 3; i++) {
                                PotionDef potion = randomPotion(ctx);
                                if (potion != null) {
                                    found.add(potion);
                                }
                            }
                            List<String> ids = new ArrayList<>();
                            found.forEach(potion -> ids.add(potion.id()));
                            ctx.page("You pull the drain levers. The tubes gurgle empty...");
                            ctx.illustrate("search:" + String.join(",", ids), () -> {
                                for (PotionDef potion : found) {
                                    ctx.addReward(new Reward.Potion(potion));
                                }
                                ctx.page("You fill your pockets with the least alarming colours.", ctx.leave());
                            });
                        }))));

        // The Woman in Blue.
        c.addEvent(new EventDef("event:chao_cart", "Chao Cart", "event:chao_cart", Set.of(2, 3), null, ctx -> {
            int[][] deals = {{1, 20}, {2, 30}, {3, 40}};
            List<EventOption> options = new ArrayList<>();
            for (int[] deal : deals) {
                String label = "[Buy " + deal[0] + "]";
                if (ctx.run().rings() >= deal[1]) {
                    options.add(EventOption.of(label, "Pay " + deal[1] + " rings. Obtain " + deal[0]
                            + (deal[0] == 1 ? " potion." : " potions."), () -> {
                                ctx.run().spendRings(deal[1]);
                                for (int i = 0; i < deal[0]; i++) {
                                    PotionDef potion = randomPotion(ctx);
                                    if (potion != null) {
                                        ctx.addReward(new Reward.Potion(potion));
                                    }
                                }
                                ctx.page("\"Chao!\" It hands over the bottles with a little bow.", ctx.leave());
                            }));
                } else {
                    options.add(EventOption.locked(label, "Requires " + deal[1] + " rings."));
                }
            }
            options.add(ctx.leave());
            ctx.page("A Chao in a tiny apron waves you over to a cart full of fizzing bottles.",
                    options.toArray(new EventOption[0]));
        }));

        // N'loth.
        c.addEvent(new EventDef("event:collector", "The Collector", "event:collector", Set.of(2, 3),
                run -> tradableRelics(run).size() >= 2, ctx -> {
                    List<Relic> tradable = tradableRelics(ctx.run());
                    if (tradable.size() < 2) {
                        ctx.page("A many-armed robot sits on a heap of trinkets. It looks you up and down, sniffs, "
                                + "and goes back to polishing a monitor.", ctx.leave());
                        return;
                    }
                    Relic first = ctx.rng().pick(tradable);
                    tradable.remove(first);
                    Relic second = ctx.rng().pick(tradable);
                    ctx.page("A many-armed robot sits on a heap of trinkets. \"Trade? Trade! I want... that one. "
                                    + "Or that one.\" It points at your " + first.name() + " and your "
                                    + second.name() + ".",
                            trade(ctx, first), trade(ctx, second), ctx.leave());
                }));

        // Mind Bloom.
        c.addEvent(new EventDef("event:hyper_remote", "The Hyper Remote", "event:hyper_remote", Set.of(3), null,
                ctx -> {
                    List<EncounterDef> bosses = ctx.run().catalog().encounters(1, EncounterDef.BOSS);
                    EncounterDef boss = bosses.isEmpty() ? null : ctx.rng().pick(bosses);
                    ctx.page("In a hidden control room you find one of Robotnik's prize inventions: a remote "
                                    + "marked HYPER. It has three buttons.",
                            boss == null ? EventOption.locked("[Rematch]", "Nothing answers.")
                                    : EventOption.of("[Rematch]", "Fight " + boss.name()
                                            + " again. Obtain a rare relic.", () -> {
                                        ctx.page("You press the red button. Alarms wail, and " + boss.name()
                                                + " fills the screen.");
                                        ctx.illustrate("rematch:" + boss.id(), () -> {
                                            String relic = ctx.run().takeRelicFromPool(RelicTier.RARE);
                                            if (relic != null) {
                                                ctx.addReward(new Reward.RelicReward(relic));
                                            }
                                            ctx.fight(boss.id(), null);
                                        });
                                    }),
                            EventOption.of("[Jackpot]", "Gain 999 rings. Become <r>Cursed</r>: 2 Lost Rings.", () -> {
                                ctx.page("You press the yellow button. Something rumbles in the ceiling...");
                                ctx.illustrate("jackpot", () -> {
                                    ctx.run().gainRings(999);
                                    ctx.obtainCard(CommonCards.LOST_RINGS, false);
                                    ctx.obtainCard(CommonCards.LOST_RINGS, false);
                                    ctx.page("Rings pour from the ceiling until you are standing in a golden sea.",
                                            ctx.leave());
                                });
                            }),
                            EventOption.of("[Overclock]", "Upgrade all cards. Become <r>Cursed</r>: Robotnik's "
                                    + "Laugh.", () -> {
                                        ctx.page("You press the blue button. The remote crackles in your hand.");
                                        ctx.illustrate("overclock", () -> {
                                            for (Card card : ctx.run().upgradableCards()) {
                                                ctx.run().upgradeCard(card);
                                            }
                                            ctx.obtainCard(CommonCards.ROBOTNIKS_LAUGH, false);
                                            ctx.page("Every card in your deck crackles with energy. Somewhere, "
                                                    + "Robotnik is laughing.", ctx.leave());
                                        });
                                    }));
                }));

        // Cursed Tome.
        c.addEvent(new EventDef("event:echidna_tablets", "Echidna Tablets", "event:tablets", Set.of(2),
                run -> !run.hasRelic(Relics.ANCIENT_TABLET), ctx ->
                ctx.page("A hidden chamber is lined with echidna tablets. The carvings seem to move when you look "
                                + "at them.",
                        EventOption.of("[Read]", "Lose 1 HP.", () -> readTablet(ctx, 0, 1, () ->
                                ctx.page("The first tablet tells of the echidnas who guarded the Master Emerald.",
                                        EventOption.of("[Continue]", "Lose 2 HP.", () -> readTablet(ctx, 1, 2, () ->
                                                ctx.page("The second tells of their greed, and of the Chaos that "
                                                                + "answered it.",
                                                        EventOption.of("[Continue]", "Lose 3 HP.", () ->
                                                                readTablet(ctx, 2, 3, () -> lastTablet(ctx))))))))),
                        ctx.leave())));

        // Vampires(?).
        c.addEvent(new EventDef("event:robotnik_offer", "Robotnik's Offer", "event:robotnik_offer", Set.of(2),
                run -> !basicAttacks(run).isEmpty(), ctx -> {
                    int loss = Math.max(1, ctx.run().maxHp() * 30 / 100);
                    ctx.page("Robotnik's voice crackles from a speaker. \"Why struggle? Let me improve you. Metal "
                                    + "arms! No more aching muscles! And a very reasonable warranty.\"",
                            EventOption.of("[Accept]", "Lose " + loss + " Max HP. Replace all basic Attacks with "
                                    + "5 <g>Robo Arms</g>.", () -> {
                                        ctx.page("\"Hold still! This won't hurt a bit.\" Two metal arms swing in...");
                                        ctx.illustrate("accept", () -> {
                                            ctx.run().setMaxHp(ctx.run().maxHp() - loss);
                                            for (Card card : basicAttacks(ctx.run())) {
                                                ctx.run().removeCard(card);
                                            }
                                            for (int i = 0; i < 5; i++) {
                                                ctx.obtainCard(CommonCards.ROBO_ARM, false);
                                            }
                                            ctx.page("\"Excellent! Do come back for the rest of the procedure.\"",
                                                    ctx.leave());
                                        });
                                    }),
                            EventOption.of("[Refuse]", "", () -> {
                                ctx.page("\"Your loss, rodent!\"");
                                ctx.illustrate("refuse", () -> ctx.page("\"Your loss, rodent!\" The speaker clicks "
                                        + "off.", ctx.leave()));
                            }));
                }));
    }

    // ------------------------------------------------------------------ helpers

    /*
     * The Slot Machine bonus stage's reel faces, in ArtUnc_SlotOptions order (the payout table
     * word_4C8A4 lists them the same way): 0 Jackpot, 1 Sonic, 2 Tails, 3 Knuckles, 4 Robotnik,
     * 5 Ring, 6 Bar, 7 Super Sonic.
     */
    private static final int FACE_SONIC = 1;
    private static final int FACE_TAILS = 2;
    private static final int FACE_KNUCKLES = 3;
    private static final int FACE_ROBOTNIK = 4;
    private static final int FACE_RING = 5;
    private static final int FACE_BAR = 6;

    /** Pulls the lever: the reels spin to three of the rolled face, then it pays out. */
    private static void spin(EventContext ctx) {
        int outcome = ctx.rng().nextInt(6);
        int face = switch (outcome) {
            case 0 -> FACE_RING;
            case 1 -> FACE_SONIC;
            case 2 -> FACE_TAILS;
            case 3 -> FACE_ROBOTNIK;
            case 4 -> FACE_KNUCKLES;
            default -> FACE_BAR;
        };
        ctx.page("You pull the lever. The reels spin...");
        ctx.illustrate(Integer.toString(face), () -> payOut(ctx, outcome));
    }

    private static void payOut(EventContext ctx, int outcome) {
        RunState run = ctx.run();
        switch (outcome) {
            case 0 -> {
                int rings = 100 * run.act();
                run.gainRings(rings);
                ctx.page("RING! RING! RING! The machine pays out " + rings + " rings.", ctx.leave());
            }
            case 1 -> {
                String relic = ctx.obtainRandomRelic();
                ctx.page("SONIC! SONIC! SONIC! A prize drops out: " + (relic == null ? "nothing." : relic + "."),
                        ctx.leave());
            }
            case 2 -> {
                run.heal(run.maxHp());
                ctx.page("TAILS! TAILS! TAILS! A burst of rings heals you completely.", ctx.leave());
            }
            case 3 -> {
                ctx.obtainCard(CommonCards.LOST_RINGS, false);
                ctx.page("ROBOTNIK! ROBOTNIK! ROBOTNIK! The machine swallows your rings and spits out a "
                        + "<r>curse</r>.", ctx.leave());
            }
            case 4 -> ctx.removeCards(1, () -> ctx.page("KNUCKLES! KNUCKLES! KNUCKLES! Something in your deck "
                    + "gets punched clean out of it.", ctx.leave()));
            default -> {
                int damage = Math.max(1, run.maxHp() / 10);
                run.loseHp(damage);
                ctx.page("BAR! BAR! BAR! A spike ball drops out of the machine onto your foot. Lose " + damage
                        + " HP.", ctx.leave());
            }
        }
    }

    /** Golden Idol: the idol is yours, and the altar's trap is sprung. */
    private static void emeraldAltarTaken(EventContext ctx) {
        ctx.obtainRelic(Relics.EMERALD_IDOL);
        int damage = ctx.run().maxHp() / 4;
        int maxHpLoss = Math.max(1, ctx.run().maxHp() * 8 / 100);
        ctx.page("The pedestal sinks with a click. A boulder rumbles down the corridor towards you!",
                EventOption.of("[Outrun]", "Become <r>Cursed</r>: Sprained Ankle.", () -> {
                    ctx.page("You run for it!");
                    ctx.illustrate("outrun", () -> {
                        ctx.obtainCard(CommonCards.SPRAINED_ANKLE, false);
                        ctx.page("You make it out, limping.", ctx.leave());
                    });
                }),
                EventOption.of("[Smash]", "Take " + damage + " damage.", () -> {
                    ctx.page("You charge straight at it!");
                    ctx.illustrate("smash:" + damage, () -> {
                        ctx.run().loseHp(damage);
                        ctx.page("You smash straight through it. That hurt.", ctx.leave());
                    });
                }),
                EventOption.of("[Hide]", "Lose " + maxHpLoss + " Max HP.", () -> {
                    ctx.page("You squeeze into a gap in the wall...");
                    ctx.illustrate("hide:" + maxHpLoss, () -> {
                        ctx.run().setMaxHp(ctx.run().maxHp() - maxHpLoss);
                        ctx.page("The boulder scrapes past and takes some of you with it.", ctx.leave());
                    });
                }));
    }

    /** Living Wall, after the card pick: the socket by the chosen word shows what it did. */
    private static void mural(EventContext ctx, String detail, String word, String result) {
        ctx.page("You press your hand to the socket marked " + word + ".");
        ctx.illustrate(detail, () -> ctx.page(result, ctx.leave()));
    }

    /** Cursed Tome: reading tablet {@code tablet} lights its carvings, then takes its toll. */
    private static void readTablet(EventContext ctx, int tablet, int hp, Runnable then) {
        ctx.page("You read the carvings...");
        ctx.illustrate("read:" + tablet + ":" + hp, () -> {
            ctx.run().loseHp(hp);
            then.run();
        });
    }

    private static void lastTablet(EventContext ctx) {
        ctx.page("The last tablet is loose. You could take it, if you can bear what it shows you.",
                EventOption.of("[Take]", "Lose 10 HP. Obtain <g>Ancient Tablet</g>.", () -> {
                    ctx.page("You lift the tablet from its base...");
                    ctx.illustrate("take:10", () -> {
                        ctx.run().loseHp(10);
                        ctx.obtainRelic(Relics.ANCIENT_TABLET);
                        ctx.page("Your head pounds, but the knowledge is yours.", ctx.leave());
                    });
                }),
                EventOption.of("[Stop]", "Lose 3 HP.", () -> {
                    ctx.page("You force yourself to look away...");
                    ctx.illustrate("stop:3", () -> {
                        ctx.run().loseHp(3);
                        ctx.page("You tear your eyes away.", ctx.leave());
                    });
                }));
    }

    /** Dead Adventurer: each search risks waking the elite buried in the pile. */
    private static void scrapyard(EventContext ctx) {
        List<Reward> loot = new ArrayList<>();
        loot.add(new Reward.Rings(30));
        String relic = ctx.run().takeRelicFromPool(ctx.run().rollRelicTier(ctx.rng()));
        if (relic != null) {
            loot.add(new Reward.RelicReward(relic));
        }
        loot.add(null);
        ctx.rng().shuffle(loot);
        List<EncounterDef> elites = ctx.run().catalog().encounters(ctx.run().act(), EncounterDef.ELITE);
        EncounterDef elite = elites.isEmpty() ? null : ctx.rng().pick(elites);
        searchScrap(ctx, loot, elite, 25, "A heap of wrecked badniks smoulders beside the path. Something deep in "
                + "the pile is still twitching, but there may be parts worth having.");
    }

    private static void searchScrap(EventContext ctx, List<Reward> loot, EncounterDef elite, int danger, String text) {
        if (loot.isEmpty()) {
            ctx.page("You've picked the pile clean.", ctx.leave());
            return;
        }
        ctx.page(text,
                EventOption.of("[Search]", "Find loot. <r>" + danger + "%</r> chance the " + (elite == null ? "badnik"
                        : elite.name()) + " wakes up.", () -> {
                            if (elite != null && ctx.rng().nextInt(100) < danger) {
                                ctx.page("You dig into the pile. Something grabs back!");
                                ctx.illustrate("elite:" + elite.id(), () -> {
                                    for (Reward r : loot) {
                                        if (r != null) {
                                            ctx.addReward(r);
                                        }
                                    }
                                    ctx.page("The pile bursts apart. " + elite.name() + " rises from the scrap!",
                                            EventOption.of("[Fight]", "", () -> ctx.fight(elite.id(), null)));
                                });
                                return;
                            }
                            Reward found = loot.remove(0);
                            String detail = found instanceof Reward.Rings rings ? "rings:" + rings.amount()
                                    : found instanceof Reward.RelicReward r ? "relic:" + r.relicId() : "bolts";
                            ctx.page("You dig into the pile...");
                            ctx.illustrate(detail, () -> {
                                String what;
                                if (found instanceof Reward.Rings rings) {
                                    ctx.run().gainRings(rings.amount());
                                    what = "You find " + rings.amount() + " rings.";
                                } else if (found instanceof Reward.RelicReward r) {
                                    ctx.obtainRelic(r.relicId());
                                    what = "You pull out a " + ctx.run().relic(r.relicId()).name() + ".";
                                } else {
                                    what = "Nothing but bolts.";
                                }
                                searchScrap(ctx, loot, elite, danger + 25, what + " The pile shifts ominously.");
                            });
                        }),
                ctx.leave());
    }

    private static void cloggedPipe(EventContext ctx, int damage, int chance, String text) {
        ctx.page(text,
                EventOption.of("[Reach In]", "Lose " + damage + " HP. <g>" + chance + "%</g> chance of a relic.", () -> {
                    // A reach that would be fatal rolls nothing, as when the roll followed the HP loss.
                    boolean fatal = ctx.run().hp() <= damage;
                    boolean prize = !fatal && ctx.rng().nextInt(100) < chance;
                    ctx.page("You reach deep into the pipe...");
                    ctx.illustrate((prize ? "prize:" : "cut:") + damage, () -> {
                        ctx.run().loseHp(damage);
                        if (ctx.run().dead()) {
                            ctx.finish();
                            return;
                        }
                        if (prize) {
                            String relic = ctx.obtainRandomRelic();
                            ctx.page("Your fingers close around something. You pull out "
                                    + (relic == null ? "a soggy ring." : "the " + relic + "!"), ctx.leave());
                        } else {
                            cloggedPipe(ctx, damage + 1, chance + 10, "Ow! Just scrap. But it's closer now...");
                        }
                    });
                }),
                ctx.leave());
    }

    /** What the fire gives back for {@code card}, for its picture: HP healed, or Max HP for a rare card. */
    private static int campfireGain(RunState run, Card card) {
        return switch (card.rarity()) {
            case CardRarity.CURSE, CardRarity.BASIC -> 0;
            case CardRarity.UNCOMMON -> run.maxHp() - run.hp();
            case CardRarity.RARE -> 10;
            default -> Math.min(5, run.maxHp() - run.hp());
        };
    }

    private static String campfire(EventContext ctx, Card card) {
        RunState run = ctx.run();
        switch (card.rarity()) {
            case CardRarity.CURSE -> {
                String relic = ctx.obtainRelicOfTier(RelicTier.COMMON);
                return "The Flickies peck the curse to pieces and leave you a gift"
                        + (relic == null ? "." : ": the " + relic + ".");
            }
            case CardRarity.BASIC -> {
                return "The Flickies look politely disappointed.";
            }
            case CardRarity.UNCOMMON -> {
                run.heal(run.maxHp());
                return "The fire roars. The Flickies sing, and you are healed completely.";
            }
            case CardRarity.RARE -> {
                run.gainMaxHp(10);
                run.heal(run.maxHp());
                return "The fire turns gold. You gain 10 Max HP and are healed completely.";
            }
            default -> {
                run.heal(5);
                return "The fire crackles happily. Heal 5 HP.";
            }
        }
    }

    private static PotionDef randomPotion(EventContext ctx) {
        int roll = ctx.rng().nextInt(100);
        String rarity = roll < 65 ? CardRarity.COMMON : roll < 90 ? CardRarity.UNCOMMON : CardRarity.RARE;
        List<PotionDef> pool = ctx.run().catalog().potions(rarity);
        if (pool.isEmpty()) {
            pool = ctx.run().catalog().potions(CardRarity.COMMON);
        }
        return pool.isEmpty() ? null : ctx.rng().pick(pool);
    }

    private static List<Relic> tradableRelics(RunState run) {
        List<Relic> list = new ArrayList<>();
        for (Relic r : run.relics()) {
            if (!r.tier().equals(RelicTier.STARTER) && !r.tier().equals(RelicTier.BOSS)
                    && !r.id().equals(Relics.COLLECTORS_BADGE)) {
                list.add(r);
            }
        }
        return list;
    }

    private static EventOption trade(EventContext ctx, Relic relic) {
        return EventOption.of("[Trade]", "Lose " + relic.name() + ". Obtain <g>Collector's Badge</g>.", () -> {
            ctx.page("You hold out your " + relic.name() + "...");
            ctx.illustrate("trade:" + relic.id(), () -> {
                ctx.run().loseRelic(relic.id());
                ctx.obtainRelic(Relics.COLLECTORS_BADGE);
                ctx.page("\"Ooh. Shiny. Here, have this. It's a badge. It means we're friends.\"", ctx.leave());
            });
        });
    }

    /** How an event's picture is told a card: its id, with "+" when upgraded. */
    private static String named(Card card) {
        return card.id() + (card.upgraded() ? "+" : "");
    }

    /** The first of {@code before} that has since been upgraded (the card an upgrade choice picked), or null. */
    private static Card firstUpgraded(List<Card> before) {
        for (Card card : before) {
            if (card.upgraded()) {
                return card;
            }
        }
        return null;
    }

    private static List<Card> basicAttacks(RunState run) {
        List<Card> list = new ArrayList<>();
        for (Card card : run.deck()) {
            if (card.rarity().equals(CardRarity.BASIC) && card.type().equals(CardType.ATTACK)) {
                list.add(card);
            }
        }
        return list;
    }
}
