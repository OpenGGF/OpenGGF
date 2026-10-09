package starpost.ruins;

import com.openggf.mods.state.SnapshotRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import starpost.core.Game;
import starpost.core.Inventory;
import starpost.core.Item;
import starpost.core.Kind;

/**
 * The Ruins' rules, engine-free so they can be tested without a ROM: which band a chamber is in,
 * the per-day chamber seed, the hit and ring-loss rules (ported from Sonic 1's HurtSonic and
 * RingLoss), drowning, fainting, ore yields and Momentum.
 */
public final class RuinsRules {
    public static final int CHAMBERS = 40;
    public static final int MARBLE = 0;
    public static final int LABYRINTH = 1;
    public static final int SCRAP_BRAIN = 2;

    /** HurtSonic: two seconds of flashing after a hit. */
    public static final int FLASH_FRAMES = 2 * 60;
    /** ReactToItem: no ring is collected while more than 90 frames of flashing remain. */
    public static final int RING_COLLECT_FLASH = 90;
    /** RLoss_Count: at most 32 rings scatter. */
    public static final int MAX_SCATTER = 32;
    /** RLoss_Count sets the scattered rings' shared timer to 255 frames (FixBugs=0 resets it per ring). */
    public static final int LOST_RING_FRAMES = 255;
    /** RLoss_Bounce: $18 gravity. */
    public static final float LOST_RING_GRAVITY = 0x18 / 256f;
    /** Drown_Countdown: 30 seconds of air; dings at 25, 20 and 15; the countdown music from 12. */
    public static final int AIR_SECONDS = 30;
    public static final int AIR_MUSIC = 12;
    /** Momentum: a rock broken by rolling, a rock burned by the Fire Shield, a bop, two rings. */
    public static final int ROLL_BREAK_COST = 2;
    public static final int FIRE_BREAK_COST = 3;
    public static final int BOP_MOMENTUM = 3;
    /** Speed a roll needs to break a rock (Sonic 1's smashable walls ask for a fast roll). */
    public static final float BREAK_SPEED = 3f;
    /** Popped badniks drop Scrap this often (percent); always while a Spark Topaz is carried. */
    public static final int SCRAP_CHANCE = 35;
    /** Items lost from up to this many stacks when fainting in the Ruins. */
    public static final int FAINT_STACKS = 3;

    private RuinsRules() {
    }

    /** 1-15 Marble Zone, 16-30 Labyrinth Zone, 31-40 Scrap Brain Zone. */
    public static int band(int chamber) {
        return chamber <= 15 ? MARBLE : chamber <= 30 ? LABYRINTH : SCRAP_BRAIN;
    }

    /** Sonic 1's public zone id for a band's level kit (0 GHZ, 1 MZ, 2 SYZ, 3 LZ, 4 SLZ, 5 SBZ). */
    public static int zone(int band) {
        return switch (band) {
            case MARBLE -> 1;
            case LABYRINTH -> 3;
            default -> 5;
        };
    }

    /** Sonic 1 driver IDs: Marble $83, Labyrinth $82, Scrap Brain $86. */
    public static int music(int band) {
        return switch (band) {
            case MARBLE -> 0x83;
            case LABYRINTH -> 0x82;
            default -> 0x86;
        };
    }

    public static String bandName(int band) {
        return switch (band) {
            case MARBLE -> "MARBLE ZONE";
            case LABYRINTH -> "LABYRINTH ZONE";
            default -> "SCRAP BRAIN ZONE";
        };
    }

    /** Every fifth chamber is a hand-built landmark with a Star Post elevator. */
    public static boolean landmark(int chamber) {
        return chamber % 5 == 0;
    }

    /** The chambers an elevator can start from: 1, then every reached multiple of five. */
    public static List<Integer> starts(int deepest) {
        List<Integer> out = new ArrayList<>();
        out.add(1);
        for (int c = 5; c <= Math.min(CHAMBERS, deepest); c += 5) {
            out.add(c);
        }
        return out;
    }

    /** A chamber's layout seed: the same all day, different the next morning and in another save. */
    public static long chamberSeed(long saveSeed, int day, int chamber) {
        long z = saveSeed * 0x9E3779B97F4A7C15L + day * 0xBF58476D1CE4E5B9L + chamber * 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // ------------------------------------------------------------------ hits and rings

    /** What a hit does: scatter this many rings, or faint when none are held (HurtSonic). */
    public static int scatterCount(int ringsInHand) {
        return Math.min(MAX_SCATTER, Math.max(0, ringsInHand));
    }

    public static boolean faintsOnHit(int ringsInHand) {
        return ringsInHand <= 0;
    }

    /**
     * RLoss_Count's spray: velocities ({@code {x, y}} in 8.8 pixels per frame) for {@code count}
     * rings. The spread value starts at $288: its low byte is the angle (CalcSine), its high byte a
     * shift that boosts the speed. Rings come in mirrored pairs; after eight pairs the angle wraps
     * and the boost drops by one, so the second sixteen fly at half speed.
     */
    public static int[][] ringBurst(int count) {
        int n = Math.min(MAX_SCATTER, Math.max(0, count));
        int[][] out = new int[n][];
        int spread = 0x288;
        int x = 0, y = 0;
        for (int i = 0; i < n; i++) {
            if (spread >= 0) {
                int angle = spread & 0xFF;
                int shift = spread >> 8;
                x = sin(angle) << shift;
                y = cos(angle) << shift;
                int low = (spread & 0xFF) + 0x10;
                spread = (spread & 0xFF00) | (low & 0xFF);
                if (low > 0xFF) {
                    spread -= 0x80;
                    if (spread < 0) {
                        spread = 0x288;
                    }
                }
            }
            out[i] = new int[] {x, y};
            x = -x;
            spread = -spread;
        }
        return out;
    }

    /** CalcSine's sine: 256 steps to a turn, 8.8 fixed point (256 is 1.0). */
    public static int sin(int angle) {
        return (int) Math.round(Math.sin((angle & 0xFF) * Math.PI / 128) * 256);
    }

    public static int cos(int angle) {
        return sin(angle + 0x40);
    }

    /**
     * RLoss_Bounce on landing: the fall speed loses a quarter and turns upward
     * ({@code vel -= vel >> 2; vel = -vel}), in 8.8 units.
     */
    public static int bounce(int yVel) {
        return -(yVel - (yVel >> 2));
    }

    /**
     * React_Enemy's bounce after a bop, in 8.8 units: moving up, Sonic slows by $100; moving down
     * and above the badnik, he bounces back up at the same speed; below it, he gains $100 upward.
     */
    public static int bopBounce(int yVel, boolean aboveBadnik) {
        if (yVel < 0) {
            return yVel + 0x100;
        }
        return aboveBadnik ? -yVel : yVel - 0x100;
    }

    // ------------------------------------------------------------------ air

    /** Whether the drowning countdown's warning dings at this many seconds left. */
    public static boolean airWarning(int seconds) {
        return seconds == 25 || seconds == 20 || seconds == 15;
    }

    /** The number bubble shown at this many seconds left (5 at 11-10 down to 0 at 1-0), or -1. */
    public static int airNumber(int seconds) {
        return seconds < AIR_MUSIC ? seconds / 2 : -1;
    }

    /** Whether air runs out at all: the Water Shield (S3K's Bubble Shield) breathes for you. */
    public static boolean breathes(Game game) {
        return "water_shield".equals(game.inventory.selectedId());
    }

    /** Whether lava hurts: not with the Fire Shield held, nor after a Fire Shield Pepper today. */
    public static boolean lavaImmune(Game game) {
        return "fire_shield".equals(game.inventory.selectedId()) || game.flags.contains(RuinsSection.LAVA_FLAG);
    }

    // ------------------------------------------------------------------ fainting

    /** One stack's loss when fainting. */
    public record Loss(String id, int count) {
    }

    /**
     * Fainting in the Ruins: up to {@link #FAINT_STACKS} random stacks lose half their items
     * (rounded up). Tools are never lost. The wallet's ring loss is the night's own (Game.sleep).
     */
    public static List<Loss> faint(Game game, SnapshotRandom rng) {
        Inventory inv = game.inventory;
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) {
            String id = inv.id(i);
            if (id != null && game.catalog.item(id).kind() != Kind.TOOL) {
                slots.add(i);
            }
        }
        List<Loss> lost = new ArrayList<>();
        for (int n = 0; n < FAINT_STACKS && !slots.isEmpty(); n++) {
            int slot = slots.remove(rng.nextInt(slots.size()));
            String id = inv.id(slot);
            int count = (inv.count(slot) + 1) / 2;
            inv.set(slot, id, inv.count(slot) - count);
            lost.add(new Loss(id, count));
        }
        return lost;
    }

    // ------------------------------------------------------------------ ore

    /** One kind of find and how many. */
    public record Drop(String id, int count) {
    }

    /**
     * What a broken rock gives. Every rock gives the band's common stone; rarer finds roll per
     * rock and grow a little with depth. The Fire Shield doubles every count. A band's Record can
     * turn up once, rarely, until it is owned.
     */
    public static List<Drop> oreYield(int band, int chamber, boolean fire, SnapshotRandom rng, Set<String> owned) {
        List<Drop> out = new ArrayList<>();
        int depth = Math.max(0, chamber - 1);
        switch (band) {
            case MARBLE -> {
                out.add(new Drop("marble_chip", 2 + rng.nextInt(2)));
                roll(out, rng, 50, "marble_ore", 1);
                roll(out, rng, 6 + depth / 3, "lava_ruby", 1);
                roll(out, rng, 5, "marble_geode", 1);
            }
            case LABYRINTH -> {
                out.add(new Drop("marble_chip", 1 + rng.nextInt(2)));
                roll(out, rng, 40, "marble_ore", 1);
                roll(out, rng, 7 + (depth - 15) / 3, "tide_sapphire", 1);
                roll(out, rng, 5, "tide_geode", 1);
            }
            default -> {
                out.add(new Drop("scrap", 1 + rng.nextInt(2)));
                roll(out, rng, 30, "marble_ore", 1);
                roll(out, rng, 7 + (depth - 30) / 2, "spark_topaz", 1);
                roll(out, rng, 5, "scrap_geode", 1);
            }
        }
        // Emerald shards: rare everywhere, a little less rare deeper (1% to 2%, in tenths of a percent).
        if (rng.nextInt(1000) < 10 + depth / 4) {
            out.add(new Drop("emerald_shard", 1));
        }
        String record = bandRecord(band);
        if (!owned.contains(record) && rng.nextInt(1000) < 8) {
            out.add(new Drop(record, 1));
        }
        if (fire) {
            List<Drop> doubled = new ArrayList<>();
            for (Drop d : out) {
                doubled.add(new Drop(d.id(), d.id().startsWith("record_") ? d.count() : d.count() * 2));
            }
            return doubled;
        }
        return out;
    }

    private static void roll(List<Drop> out, SnapshotRandom rng, int percent, String id, int count) {
        if (rng.nextInt(100) < percent) {
            out.add(new Drop(id, count));
        }
    }

    /** The Record a band's rocks can give. */
    public static String bandRecord(int band) {
        return switch (band) {
            case MARBLE -> "record_marble";
            case LABYRINTH -> "record_drowning";
            default -> "record_scrap_brain";
        };
    }

    /** Whether a popped badnik leaves Scrap behind. */
    public static boolean dropsScrap(Game game, SnapshotRandom rng) {
        return game.inventory.total("spark_topaz") > 0 || rng.nextInt(100) < SCRAP_CHANCE;
    }

    /** Whether an item is a Record (and so found only once). */
    public static boolean isRecord(Item item) {
        return RuinsContent.recordSong(item.id()) >= 0;
    }
}
