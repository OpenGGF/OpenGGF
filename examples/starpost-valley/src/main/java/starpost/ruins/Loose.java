package starpost.ruins;

/**
 * The small moving things of a chamber: scattered rings, freed animals, finds flying to Sonic,
 * air bubbles, and short effects (explosions, sparkles, floating words). Engine-free.
 */
final class Loose {
    private Loose() {
    }

    /** A ring knocked loose by a hit (RingLoss): bounces, and vanishes when the shared timer runs out. */
    static final class LostRing {
        float x;
        float y;
        int vx;       // 8.8
        int vy;
        int xSub;
        int ySub;
        final int index;
        boolean alive = true;

        LostRing(float x, float y, int vx, int vy, int index) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.index = index;
        }

        /**
         * RLoss_Bounce: SpeedToPos, $18 gravity, and every fourth frame (offset by the ring's slot)
         * a floor check that bounces it back up a quarter slower.
         */
        void update(Chamber chamber, long tick) {
            int fx = Math.round(x * 256) + vx;
            int fy = Math.round(y * 256) + vy;
            x = fx / 256f;
            y = fy / 256f;
            vy += 0x18;
            if (vy >= 0 && ((tick + index) & 3) == 0) {
                int floor = chamber.floorBelow(Math.round(x), Math.round(y) - 8);
                if (y + 8 >= floor && floor < chamber.height) {
                    y = floor - 8;
                    vy = RuinsRules.bounce(vy);
                }
            }
            if (y > chamber.height + 32) {
                alive = false;
            }
        }
    }

    /**
     * A freed animal (Sonic 1's Obj28): pops up at -$400, falls to the floor, then hops away to the
     * left at its species' speeds; the chicken and the Flicky fall slower ($18).
     */
    static final class Animal {
        final String name;
        float x;
        float y;          // feet
        float vx;
        float vy = -0x400 / 256f;
        final float hopX;
        final float hopY;
        final boolean slow;
        boolean hopping;
        int age;
        boolean alive = true;

        Animal(String name, float x, float y) {
            this.name = name;
            this.x = x;
            this.y = y;
            float[] speeds = speeds(name);
            hopX = speeds[0];
            hopY = speeds[1];
            slow = name.equals("cucky") || name.equals("flicky");
        }

        /** Anml_Variables: (x, y) speeds per species, in pixels per frame. */
        static float[] speeds(String name) {
            int[] v = switch (name) {
                case "pocky" -> new int[] {-0x200, -0x400};
                case "cucky" -> new int[] {-0x200, -0x300};
                case "pecky" -> new int[] {-0x180, -0x300};
                case "rocky" -> new int[] {-0x140, -0x180};
                case "picky" -> new int[] {-0x1C0, -0x300};
                case "flicky" -> new int[] {-0x300, -0x400};
                default -> new int[] {-0x280, -0x380};   // ricky
            };
            return new float[] {v[0] / 256f, v[1] / 256f};
        }

        /** Frame 2 when released, 1 rising, 0 falling (Anml_Main, Anml_NormalGravity). */
        int frame() {
            return !hopping ? 2 : vy < 0 ? 1 : 0;
        }

        void update(Chamber chamber) {
            age++;
            x += vx;
            y += vy;
            vy += hopping && slow ? 0x18 / 256f : 0x38 / 256f;
            if (vy >= 0) {
                int floor = chamber.floorBelow(Math.round(x), Math.round(y - vy) - 2);
                if (y >= floor && floor < chamber.height) {
                    y = floor;
                    hopping = true;
                    vx = hopX;
                    vy = hopY;
                }
            }
            if (x < -24 || y > chamber.height + 32 || age > 600) {
                alive = false;
            }
        }
    }

    /** A find popping out of a rock or monitor, then flying into Sonic's hands. */
    static final class Pickup {
        final String id;
        final int count;
        float x;
        float y;
        float vx;
        float vy;
        int age;
        boolean alive = true;

        Pickup(String id, int count, float x, float y, float vx, float vy) {
            this.id = id;
            this.count = count;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }

        void update(Chamber chamber, float sx, float sy) {
            age++;
            if (age < 26) {
                x += vx;
                y += vy;
                vy += 0.2f;
                if (vy > 0 && chamber.floor(Math.round(x), Math.round(y) + 8)) {
                    vy = -vy * 0.4f;
                    vx *= 0.6f;
                }
                return;
            }
            float dx = sx - x, dy = sy - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float speed = Math.min(d, 3 + (age - 26) * 0.4f);
            if (d > 0) {
                x += dx / d * speed;
                y += dy / d * speed;
            }
        }
    }

    /** An air bubble rising from a vent (Sonic 1's LZ bubbles): only the large ones are breathed. */
    static final class Bubble {
        float x;
        float y;
        final boolean large;
        int age;
        boolean alive = true;
        final float wobble;

        Bubble(float x, float y, boolean large, float wobble) {
            this.x = x;
            this.y = y;
            this.large = large;
            this.wobble = wobble;
        }

        /** Bubbles rise at -$88 and sway; they burst at the surface. */
        void update(Chamber chamber) {
            age++;
            y -= 0x88 / 256f;
            x += (float) Math.sin((age + wobble) / 10.0) * 0.5f;
            if (chamber.waterY == Chamber.NO_WATER || y < chamber.waterY + 4 || chamber.solid(Math.round(x), Math.round(y) - 6)
                    || age > 900) {
                alive = false;
            }
        }

        /** The bubble's Map_Bub frame: growing for its first frames, then full size (6 large, 2 small). */
        int frame() {
            int grown = Math.min(age / 14, large ? 4 : 2);
            return large ? 2 + grown : grown;
        }
    }

    /** A short effect: an explosion, a sparkle, or a line of floating text. */
    static final class Puff {
        static final int EXPLOSION = 0;
        static final int SPARKLE = 1;
        static final int TEXT = 2;
        static final int SPLASH = 3;
        final int kind;
        final float x;
        float y;
        final String text;
        int age;

        Puff(int kind, float x, float y, String text) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.text = text;
        }

        int life() {
            return kind == TEXT ? 70 : kind == EXPLOSION ? 30 : 16;
        }

        boolean alive() {
            return age < life();
        }

        void update() {
            age++;
            if (kind == TEXT) {
                y -= 0.5f;
            }
        }
    }
}
