package starpost.fishing;

import starpost.core.Game;
import starpost.core.Item;
import starpost.core.Skills;

/**
 * Fishing's rules beyond the bar, engine-free: what a strike hooks, how big the bubble is, and
 * what landing each catch gives. Every catch that says it does something does it here.
 */
public final class Fishing {
    /** The flag Barnaby's two-heart lesson leaves: a bigger bubble. */
    public static final String LESSON = "barnaby_fishing_lesson";
    public static final String RED_CHOPPER = "red_chopper_shell";
    public static final String HAT = "barnabys_hat";
    public static final String RED_CHOPPER_CAUGHT = "red_chopper_caught";
    public static final int POND_DEPTH = 50;

    private Fishing() {
    }

    /** What landing a catch did. */
    public record Landed(String id, String message, boolean freed, boolean kept) {
    }

    /** The section, added if a save predates fishing. */
    public static FishingSection section(Game game) {
        FishingSection section = game.section(FishingSection.class);
        if (section == null) {
            section = new FishingSection();
            game.sections.add(section);
        }
        return section;
    }

    public static int level(Game game) {
        Skills skills = game.section(Skills.class);
        return skills == null ? 0 : skills.level(Skills.FISHING);
    }

    /** The slack bubble's half height for this farmer. */
    public static float bubbleHalf(Game game) {
        return BubbleBar.bubbleHalf(level(game), game.flags.contains(LESSON));
    }

    /** Whether the farmer is holding the rod. */
    public static boolean holdingRod(Game game) {
        return "fishing_rod".equals(game.inventory.selectedId());
    }

    /**
     * Lands a catch: into the monitors (or lost for want of room), the collection, experience,
     * and the catch's own rule. A badnik pops: an animal goes free and its shell is kept.
     */
    public static Landed land(Game game, String id, boolean perfect) {
        FishingSection section = section(game);
        FishTable table = section.table();
        Item item = game.item(id);
        boolean kept = game.inventory.add(item, 1) == 0;
        section.record(id);
        FishDef def = table.get(id);
        if (def == null) {
            game.xp(Skills.FISHING, 1);
            return new Landed(id, kept ? "A CAN OF ROBO COLA. SOMEBODY LITTERED." : "NO ROOM FOR THE CAN.", false, kept);
        }
        int xp = (def.isBadnik() ? 12 : 3) + def.difficulty() / 5;
        game.xp(Skills.FISHING, perfect ? xp * 3 / 2 : xp);
        String extra = "";
        switch (id) {
            case "bubble_bass" -> {
                game.waterCharges = Math.min(game.waterCapacity, game.waterCharges + 5);
                extra = " +5 WATER";
            }
            case "ring_carp" -> {
                game.rings += 5;
                extra = " +5 RINGS";
            }
            case "scrap_sucker" -> {
                game.inventory.add(game.item("scrap"), 1);
                extra = " +1 SCRAP";
            }
            case "emerald_koi" -> {
                game.restore(30);
                extra = " +30 MOMENTUM";
            }
            default -> {
            }
        }
        boolean freed = false;
        if (def.isBadnik()) {
            freed = game.free();
            extra = freed ? "! AN ANIMAL IS FREE!" : "! POPPED!";
            if (id.equals(RED_CHOPPER) && game.flags.add(RED_CHOPPER_CAUGHT) && game.catalog.hasItem(HAT)) {
                game.inventory.add(game.item(HAT), 1);
                return new Landed(id, "THE RED CHOPPER! AND BARNABY'S HAT!", freed, kept);
            }
        }
        String name = (perfect ? "PERFECT! " : "") + def.name();
        return new Landed(id, kept ? name + extra : "NO ROOM FOR THE " + def.name(), freed, kept);
    }
}
