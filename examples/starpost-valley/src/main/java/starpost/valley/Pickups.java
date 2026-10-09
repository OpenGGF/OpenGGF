package starpost.valley;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import starpost.core.Calendar;
import starpost.core.Catalog;
import starpost.core.Game;
import starpost.core.SaveSection;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Sfx;
import starpost.scene.Shell;

/**
 * Rings and forage along the valley path, back every morning. Rings come in Sonic's lines and
 * arcs (over the spring, round the loop); each one is a ring of money and a point of Momentum, so
 * running the valley is how a tired farmer recovers. Forage follows the season (design doc §6.3).
 */
public final class Pickups implements SaveSection {
    /** One thing to pick up: a ring (item null) or a forage item. */
    public static final class Pickup implements Actor {
        final float x;
        final float y;
        final String item;
        final int index;
        private final Pickups owner;

        Pickup(Pickups owner, int index, float x, float y, String item) {
            this.owner = owner;
            this.index = index;
            this.x = x;
            this.y = y;
            this.item = item;
        }

        @Override
        public int view() {
            return VALLEY;
        }

        @Override
        public float x() {
            return x;
        }

        @Override
        public float y() {
            return y;
        }

        @Override
        public void update(Shell shell, PlayScreen play) {
            if (owner.taken(index) || play.onFarm()) {
                return;
            }
            Runner r = play.valley().runner;
            if (Math.abs(r.x - x) < 12 && Math.abs(r.y - 16 - y) < 22) {
                owner.take(index);
                Game game = shell.game;
                if (item == null) {
                    game.rings++;
                    game.restore(1);
                    shell.sfx(Sfx.RING);
                } else if (game.inventory.add(game.item(item), 1) == 0) {
                    boolean pair = game.has("gatherer") && game.rng.nextInt(5) == 0;
                    if (pair) {
                        game.inventory.add(game.item(item), 1);
                    }
                    game.xp(starpost.core.Skills.RANGING, 7);
                    shell.sfx(Sfx.GRAB);
                    shell.toast("FOUND " + (pair ? "TWO " : "") + game.item(item).name());
                } else {
                    owner.untake(index);
                }
            }
        }

        @Override
        public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
            if (owner.taken(index)) {
                return;
            }
            if (item == null) {
                int frame = (int) (shell.ticks / 8 % 4);
                canvas.draw(shell.art.ring.frame(frame), x - cx, y - cy, SceneDraw.plain());
            } else {
                float bob = (float) Math.sin((shell.ticks + index * 13) / 20.0) * 1.5f;
                shell.art.icons.draw(canvas, shell.game.item(item), x - cx - 8, y - cy - 8 + bob, tint);
            }
        }
    }

    private final List<Pickup> pickups = new ArrayList<>();
    private boolean[] taken = new boolean[0];
    private int builtForDay = -1;

    @Override
    public String prefix() {
        return "pickups";
    }

    /** The day's pickups, built once per morning from the valley's layout and the season. */
    public List<Pickup> today(Game game, Valley valley) {
        int day = game.calendar.dayNumber();
        if (day != builtForDay) {
            build(game, valley);
            builtForDay = day;
        }
        return pickups;
    }

    private void build(Game game, Valley valley) {
        pickups.clear();
        int n = 0;
        // Ring lines along the path, an arc over the spring and a ring round the loop.
        for (int x = 320; x < valley.width() - 200; x += 230) {
            if (Math.abs(x - valley.springX) < 120 || Math.abs(x - valley.loopX - 128) < 160) {
                continue;
            }
            int floor = valley.floorBelow(x, 0);
            for (int i = 0; i < 5; i++) {
                pickups.add(new Pickup(this, n++, x + i * 16, floor - 16, null));
            }
        }
        for (int i = 0; i < 7; i++) {
            double a = Math.PI * i / 6;
            pickups.add(new Pickup(this, n++, valley.springX + 24 + (float) Math.cos(Math.PI - a) * 40,
                    96 - 20 - (float) Math.sin(a) * 60, null));
        }
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            pickups.add(new Pickup(this, n++, valley.loopX + 126 + (float) Math.sin(a) * 58,
                    111 + (float) Math.cos(a) * 58, null));
        }
        // Forage: a few seasonal finds at spots that change each day.
        String[] forage = switch (game.calendar.season()) {
            case Calendar.SPRING -> new String[] {"totem_leek", "hill_daffodil"};
            case Calendar.SUMMER -> new String[] {"loop_berry", "hill_daffodil"};
            case Calendar.FALL -> new String[] {"palm_coconut", "loop_berry"};
            default -> new String[] {"snow_spud", "frost_ring"};
        };
        long seed = game.calendar.dayNumber() * 2654435761L + 17;
        for (int i = 0; i < 6; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int x = 260 + (int) ((seed >>> 33) % (valley.width() - 520));
            pickups.add(new Pickup(this, n++, x, valley.floorBelow(x, 0) - 10, forage[(int) ((seed >>> 13) & 1)]));
        }
        if (taken.length != n) {
            taken = new boolean[n];
        }
    }

    boolean taken(int index) {
        return index < taken.length && taken[index];
    }

    void take(int index) {
        if (index < taken.length) {
            taken[index] = true;
        }
    }

    void untake(int index) {
        if (index < taken.length) {
            taken[index] = false;
        }
    }

    @Override
    public void save(Map<String, String> out) {
        // Picked-up state only matters within a day, and the game saves at night.
    }

    @Override
    public void load(Map<String, String> in, Catalog catalog) {
    }

    @Override
    public void nextDay(Game game) {
        taken = new boolean[taken.length];
        builtForDay = -1;
    }
}
