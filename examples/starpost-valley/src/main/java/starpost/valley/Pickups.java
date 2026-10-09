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

        public int index() { return index; }
        public String item() { return item; }

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
                int before = item == null ? 0 : shell.game.inventory.total(item);
                if (owner.collect(index, item, shell.game)) {
                    shell.sfx(item == null ? Sfx.RING : Sfx.GRAB);
                    if (item != null) shell.toast("FOUND " + (shell.game.inventory.total(item) - before > 1 ? "TWO " : "")
                            + shell.game.item(item).name());
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
        return today(game, valley, valley.springX, valley.loopX);
    }

    /** Shared daily placement for a real-level ground seam as well as the scene valley. */
    public List<Pickup> today(Game game, Runner.Ground ground, int springX, int loopX) {
        int day = game.calendar.dayNumber();
        if (day != builtForDay) {
            build(game, ground, springX, loopX);
            builtForDay = day;
        }
        return pickups;
    }

    /** Reproject daily floor placements after an act load without respawning collected items.
     * Scene and act layouts must preserve the daily identity/order and width. */
    public void placeOnGround(Game game, Runner.Ground ground, int springX, int loopX) {
        today(game, ground, springX, loopX);
        List<Pickup> previous = List.copyOf(pickups);
        Snapshot before = capture();
        build(game, ground, springX, loopX);
        boolean same = previous.size() == pickups.size();
        for (int i = 0; same && i < previous.size(); i++) {
            Pickup old = previous.get(i), next = pickups.get(i);
            same = old.x == next.x && java.util.Objects.equals(old.item, next.item);
        }
        if (!same) {
            pickups.clear(); pickups.addAll(previous); restore(before);
            throw new IllegalArgumentException("Scene/act pickup identities differ; preserve layout width and anchors");
        }
        restore(before);
    }

    private void build(Game game, Runner.Ground ground, int springX, int loopX) {
        pickups.clear();
        int n = 0;
        // Ring lines along the path, an arc over the spring and a ring round the loop.
        for (int x = 320; x < (ground.right() - ground.left()) - 200; x += 230) {
            if (Math.abs(x - springX) < 120 || Math.abs(x - loopX - 128) < 160) {
                continue;
            }
            int floor = ground.floorBelow(x, 0);
            for (int i = 0; i < 5; i++) {
                pickups.add(new Pickup(this, n++, x + i * 16, floor - 16, null));
            }
        }
        for (int i = 0; i < 7; i++) {
            double a = Math.PI * i / 6;
            pickups.add(new Pickup(this, n++, springX + 24 + (float) Math.cos(Math.PI - a) * 40,
                    96 - 20 - (float) Math.sin(a) * 60, null));
        }
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            pickups.add(new Pickup(this, n++, loopX + 126 + (float) Math.sin(a) * 58,
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
            int x = 260 + (int) ((seed >>> 33) % ((ground.right() - ground.left()) - 520));
            pickups.add(new Pickup(this, n++, x, ground.floorBelow(x, 0) - 10, forage[(int) ((seed >>> 13) & 1)]));
        }
        if (taken.length != n) {
            taken = new boolean[n];
        }
    }

    public boolean taken(int index) {
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

    /** Rewards are identical in scene and act; a full bag leaves forage available. */
    public boolean collect(int index, String item, Game game) {
        if (index < 0 || index >= taken.length || taken(index)) return false;
        if (item == null) {
            game.rings++;
            game.restoreBySpeed(1);
        } else {
            if (game.inventory.add(game.item(item), 1) != 0) return false;
            if (game.has("gatherer") && game.rng.nextInt(5) == 0) game.inventory.add(game.item(item), 1);
            game.xp(starpost.core.Skills.RANGING, 7);
        }
        take(index);
        return true;
    }

    public record Snapshot(int day, List<Boolean> taken) {
        public Snapshot { taken = List.copyOf(taken); }
    }

    public Snapshot capture() {
        List<Boolean> bits = new ArrayList<>();
        for (boolean bit : taken) bits.add(bit);
        return new Snapshot(builtForDay, bits);
    }

    public void restore(Snapshot snapshot) {
        builtForDay = snapshot.day();
        taken = new boolean[snapshot.taken().size()];
        for (int i = 0; i < taken.length; i++) taken[i] = snapshot.taken().get(i);
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
