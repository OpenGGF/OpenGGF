package starpost.orchard;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import starpost.art.Art;
import starpost.core.Game;
import starpost.core.Item;
import starpost.core.Plot;
import starpost.core.Sneakers;
import starpost.farm.FarmView;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.scene.WorkshopOffer;

/**
 * How the orchard and the sneakers plug in (design doc §14): trees draw and are picked through
 * the farm's object hook, the Ring Fruit Tree's shaken rings fly as an actor until caught or
 * gone, Tails's workshop offers the next pair of sneakers, and debug commands serve captures.
 */
public final class OrchardSystem {
    /** Height above the feet the Ring Fruit Tree's rings burst from (its canopy). */
    static final int CANOPY = 44;

    final Shell shell;
    final PlayScreen play;
    final Orchard orchard;
    final TreeArt art;
    final List<RingBurst> bursts = new ArrayList<>();

    private OrchardSystem(Shell shell, PlayScreen play, Orchard orchard) {
        this.shell = shell;
        this.play = play;
        this.orchard = orchard;
        this.art = new TreeArt(shell.art);
    }

    /** The section, added if a save predates the orchard. */
    public static Orchard section(Game game) {
        Orchard orchard = game.section(Orchard.class);
        if (orchard == null) {
            orchard = new Orchard();
            game.sections.add(orchard);
        }
        return orchard;
    }

    /** Called from {@code Systems.install} for every new play screen. */
    public static void install(Shell shell, PlayScreen play, List<Actor> actors) {
        OrchardSystem sys = new OrchardSystem(shell, play, section(shell.game));
        shell.art.icons.addSource("orchard", sys.art::icon);
        actors.add(sys.new Rings());
        play.farm().objectHooks.add(sys.new Trees());
    }

    // ------------------------------------------------------------------ trees on the field

    /** The farm's hook for trees: picking, drawing, and roots that hold after the first night. */
    final class Trees implements FarmView.ObjectHook {
        @Override
        public boolean use(int row, int column, String id, Item held) {
            if (!Orchard.isTree(id)) {
                return false;
            }
            Game game = shell.game;
            Orchard.Tree tree = orchard.tree(game, row, column);
            String name = TreeArt.name(id);
            if (!tree.grown()) {
                int left = Orchard.days(id) - tree.age;
                shell.toast(name + ": " + left + (left == 1 ? " DAY" : " DAYS") + " TO GROW");
                shell.sfx(Sfx.ERROR);
                return true;
            }
            Orchard.Pick pick = orchard.pick(game, row, column);
            if (pick == null) {
                shell.toast(tree.fruit > 0 ? "NO ROOM IN YOUR MONITORS" : idleText(id));
                shell.sfx(Sfx.ERROR);
            } else if (pick.rings()) {
                float x = FarmView.FIELD_X + column * 16 + 8;
                float feet = FarmView.ROW_Y + row * FarmView.ROW_STEP - 2;
                bursts.add(new RingBurst(pick.count(), x, feet, CANOPY, FarmView.FIELD_TOP + 4, FarmView.FIELD_TOP + 4 + 68));
                shell.sfx(Sfx.RING_LOSS);
                shell.toast(pick.count() + " RINGS! CATCH THEM!");
                game.xp(starpost.core.Skills.FARMING, 2);
            } else {
                shell.sfx(Sfx.GRAB);
                shell.toast("+" + pick.count() + " " + game.item(pick.item()).name());
                game.xp(starpost.core.Skills.RANGING, 3 * pick.count());
            }
            return true;
        }

        @Override
        public boolean removable(int row, int column, String id) {
            if (!Orchard.isTree(id)) {
                return true;
            }
            Orchard.Tree tree = orchard.tree(shell.game, row, column);
            return tree == null || tree.age == 0;          // rooted after its first night
        }

        @Override
        public String lockedText(int row, int column, String id) {
            return Orchard.isTree(id) ? "ITS ROOTS ARE TOO DEEP TO MOVE" : null;
        }

        @Override
        public boolean draw(SceneCanvas canvas, Art.Seasonal look, SceneDraw style, int row, int column, String id,
                float x, float feet) {
            if (!Orchard.isTree(id)) {
                return false;
            }
            Orchard.Tree tree = orchard.tree(shell.game, row, column);
            if (tree != null) {
                art.draw(canvas, look, style, tree, x, feet, shell.ticks, shell.game.calendar.season());
            }
            return true;
        }
    }

    private static String idleText(String kind) {
        return switch (kind) {
            case Orchard.PALM -> "NO COCONUTS TODAY. SUMMER AND FALL, OR A STORM.";
            case Orchard.RING_FRUIT -> "NO RINGS YET. THEY GROW SPRING TO FALL.";
            default -> "NO CHERRIES TODAY. FREE MORE ANIMALS!";
        };
    }

    // ------------------------------------------------------------------ shaken rings

    /** The Ring Fruit Tree's rings in flight: caught by running through them, drawn over the field. */
    final class Rings implements Actor {
        OrchardSystem owner() {
            return OrchardSystem.this;
        }

        @Override
        public int view() {
            return FARM;
        }

        @Override
        public float x() {
            return -1000;           // never the nearest actor to the action button
        }

        @Override
        public float y() {
            return 0;
        }

        @Override
        public void update(Shell shell, PlayScreen play) {
            if (!play.onFarm()) {
                bursts.clear();
                return;
            }
            FarmView farm = play.farm();
            Game game = shell.game;
            boolean lightning = "lightning_shield".equals(game.inventory.selectedId());
            int caught = 0;
            for (RingBurst burst : bursts) {
                burst.step();
                caught += burst.collect(farm.runner.x, farm.feetY(), farm.runner.height, lightning);
            }
            bursts.removeIf(RingBurst::done);
            if (caught > 0) {
                game.rings += caught;
                game.restoreBySpeed(caught);
                shell.sfx(Sfx.RING);
            }
        }

        @Override
        public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        }

        @Override
        public void drawOver(Shell shell, SceneCanvas canvas, int cx, int cy) {
            FarmView farm = play.farm();
            var splash = farm.onWater ? art.splash() : null;
            if (splash != null && splash.frameCount() > 2) {
                // Chaos Sneakers on the pond: Labyrinth's splash kicked up behind the farmer's feet.
                int frame = (int) (shell.ticks / 4 % 3);
                float behind = farm.runner.facingLeft ? 10 : -10;
                var sprite = splash.frame(frame);
                canvas.draw(sprite, farm.runner.x - cx + behind, farm.feetY() - (sprite.height() - sprite.originY()) + 2,
                        SceneDraw.plain().withFlipX(farm.runner.facingLeft));
            }
            var ring = shell.art.ring;
            if (ring == null || ring.frameCount() < 4) {
                return;
            }
            for (RingBurst burst : bursts) {
                // Scattered rings spin fast and blink in their last second.
                if (burst.timer() < 60 && (shell.ticks / 3) % 2 == 0) {
                    continue;
                }
                int frame = (int) (shell.ticks / 2 % 4);
                for (RingBurst.Ring r : burst.rings()) {
                    float sx = r.x - cx, sy = r.depth - r.height;
                    canvas.fill(Math.round(sx) - 3, Math.round(r.depth) - 1, 6, 2, 0x50000000);
                    canvas.draw(ring.frame(frame), sx - 8, sy - 16, SceneDraw.plain());
                }
            }
        }
    }

    // ------------------------------------------------------------------ the workshop

    /** Tails's next pair of sneakers, priced along the Water Shield tank's ladder; the last needs an Emerald Shard. */
    public static List<WorkshopOffer> workshopOffers(Shell shell) {
        List<WorkshopOffer> out = new ArrayList<>();
        Game game = shell.game;
        int next = Sneakers.tier(game) + 1;
        if (next > Sneakers.CHAOS) {
            return out;
        }
        Map<String, Integer> inputs = new LinkedHashMap<>();
        inputs.put(Sneakers.material(next), Sneakers.materialCount(next));
        Item item = game.item(Sneakers.item(next));
        out.add(new WorkshopOffer(item.name(), item.id(), Sneakers.price(next), inputs, () -> Sneakers.grant(game, next), item.text()));
        return out;
    }

    // ------------------------------------------------------------------ debug

    /**
     * Debug ({@code orchard ...}): {@code plant palm|ring|cherry ROW COL [AGE [FRUIT]]} (a tree on a
     * cleared plot), {@code night} (tonight's tree work now), {@code clear} (the open field's weeds and rocks gone), {@code shake ROW COL} (pick it as the
     * action button would), {@code sneakers 0-3} (set the tier), {@code stand ROW COL} (the farmer
     * at a plot, facing it).
     */
    public static boolean debug(Shell shell, String[] p) {
        if (shell.game == null || p.length < 2) {
            return false;
        }
        Game game = shell.game;
        Orchard orchard = section(game);
        switch (p[1]) {
            case "plant" -> {
                String kind = switch (p[2]) {
                    case "palm" -> Orchard.PALM;
                    case "ring" -> Orchard.RING_FRUIT;
                    default -> Orchard.CHAOS_CHERRY;
                };
                int row = Integer.parseInt(p[3]), col = Integer.parseInt(p[4]);
                game.farm.open(col + 1);                       // debug: clear the land it needs
                Plot plot = game.farm.plot(row, col);
                if (plot == null) {
                    return false;
                }
                plot.cover = Plot.GRASS;
                plot.tilled = false;
                plot.clearCrop();
                plot.object = kind;
                Orchard.Tree tree = orchard.tree(game, row, col);
                tree.age = p.length > 5 ? Math.min(Orchard.days(kind), Integer.parseInt(p[5])) : 0;
                tree.fruit = p.length > 6 ? Math.min(Orchard.cap(kind, true), Integer.parseInt(p[6])) : 0;
            }
            case "night" -> orchard.nextDay(game);
            case "clear" -> {
                // Debug: the open field cleared of weeds, rocks and stumps (for clean pictures).
                for (int r = 0; r < starpost.core.Farm.ROWS; r++) {
                    for (int c = 0; c < game.farm.open(); c++) {
                        game.farm.plot(r, c).cover = Plot.GRASS;
                    }
                }
            }
            case "shake" -> {
                OrchardSystem sys = of(shell);
                if (sys == null) {
                    return false;
                }
                int row = Integer.parseInt(p[2]), col = Integer.parseInt(p[3]);
                return sys.new Trees().use(row, col, game.farm.plot(row, col).object, null);
            }
            case "sneakers" -> {
                for (int t = Sneakers.POWER; t <= Sneakers.CHAOS; t++) {
                    game.flags.remove(Sneakers.flag(t));
                }
                Sneakers.grant(game, Integer.parseInt(p[2]));
            }
            case "stand" -> {
                if (!(shell.screen() instanceof PlayScreen play)) {
                    return false;
                }
                int row = Integer.parseInt(p[2]), col = Integer.parseInt(p[3]);
                FarmView farm = play.farm();
                farm.runner.x = FarmView.FIELD_X + col * 16 + 8;
                farm.runner.depth = FarmView.ROW_Y + row * FarmView.ROW_STEP - (FarmView.FIELD_TOP + 4);
                farm.snapCamera();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The orchard system of the current play screen, or null. */
    static OrchardSystem of(Shell shell) {
        if (shell.screen() instanceof PlayScreen play) {
            for (Actor actor : play.actors) {
                if (actor instanceof Rings rings) {
                    return rings.owner();
                }
            }
        }
        return null;
    }
}
