package starpost.barn;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import starpost.art.Art;
import starpost.core.Game;
import starpost.core.Inventory;
import starpost.core.Item;
import starpost.core.Machine;
import starpost.core.PlaceableDef;
import starpost.core.Plot;
import starpost.farm.FarmView;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.scene.WorkshopOffer;

/**
 * How the barn plugs in (design doc §14): the Cucky Coop and Pocky Pen stand on the farm's back
 * wall as actors (their menus sell animals, fill hoppers and collect goods), the animals wander
 * the field, truffles wait in the grass, Flickies circle their roosts, the machines and roosts draw
 * and work through the farm's object hooks, the coop, pen and their upgrades are sold at Tails's
 * workshop, and debug commands serve captures.
 */
public final class BarnSystem {
    /** World x of the coop's and pen's middles, on the back wall behind the field. */
    public static final int COOP_X = 616;
    public static final int PEN_X = 846;

    final Shell shell;
    final PlayScreen play;
    final Barn barn;
    final BarnArt art;

    private BarnSystem(Shell shell, PlayScreen play, Barn barn) {
        this.shell = shell;
        this.play = play;
        this.barn = barn;
        this.art = new BarnArt(shell.art, shell.art.season(shell.game.calendar.season()).tone);
    }

    /** The section, added if a save predates the barn. */
    public static Barn section(Game game) {
        Barn barn = game.section(Barn.class);
        if (barn == null) {
            barn = new Barn();
            game.sections.add(barn);
        }
        return barn;
    }

    /** Called from {@code Systems.install} for every new play screen. */
    public static void install(Shell shell, PlayScreen play, List<Actor> actors) {
        BarnSystem sys = new BarnSystem(shell, play, section(shell.game));
        shell.art.icons.addSource("barn", sys.art::icon);
        actors.add(new BuildingActor(sys, Animals.COOP));
        actors.add(new BuildingActor(sys, Animals.PEN));
        for (Animal animal : sys.barn.animals) {
            actors.add(new AnimalActor(sys, animal));
        }
        for (String spot : sys.barn.truffles) {
            String[] rc = spot.split("\\.");
            actors.add(new TruffleActor(sys, Integer.parseInt(rc[0]), Integer.parseInt(rc[1])));
        }
        Game game = shell.game;
        for (int r = 0; r < starpost.core.Farm.ROWS; r++) {
            for (int c = 0; c < starpost.core.Farm.COLUMNS; c++) {
                if (BarnContent.ROOST.equals(game.farm.raw(r, c).object)) {
                    actors.add(new FlickyFlock(sys, r, c));
                }
            }
        }
        play.farm().objectHooks.add(sys.new Objects());
    }

    /** A newly bought animal joins the field at once (called from the building's menu, outside the actor loop). */
    void arrive(Animal animal) {
        play.actors.add(new AnimalActor(this, animal));
    }

    // ------------------------------------------------------------------ the workshop

    /** The coop, the pen and their upgrades, as Tails's workshop offers them. */
    public static List<WorkshopOffer> workshopOffers(Shell shell) {
        List<WorkshopOffer> out = new ArrayList<>();
        Game game = shell.game;
        Barn barn = section(game);
        if (barn.coop == 0) {
            out.add(new WorkshopOffer("CUCKY COOP", "cucky_egg", 2000, inputs("palm_wood", 60, "marble_chip", 20),
                    () -> barn.coop = 1, "A HEN HOUSE ON THE BACK WALL: 4 CUCKIES OR PECKIES."));
        } else if (barn.coop == 1 && game.flags.contains(Barn.BIG)) {
            out.add(new WorkshopOffer("BIG COOP", "cucky_egg", 6000, inputs("palm_wood", 100, "hill_cloth", 3),
                    () -> barn.coop = 2, "ROOM FOR 8, AND COSIER: HAPPIER BIRDS."));
        }
        if (barn.pen == 0) {
            out.add(new WorkshopOffer("POCKY PEN", "pocky_fluff", 4000, inputs("palm_wood", 100, "marble_chip", 40),
                    () -> barn.pen = 1, "A SHELTER AND PADDOCK: 4 POCKIES OR PICKIES."));
        } else if (barn.pen == 1 && game.flags.contains(Barn.BIG)) {
            out.add(new WorkshopOffer("BIG PEN", "pocky_fluff", 8000, inputs("palm_wood", 120, "hill_cloth", 5),
                    () -> barn.pen = 2, "ROOM FOR 8, AND COSIER: HAPPIER HERDS."));
        }
        return out;
    }

    private static Map<String, Integer> inputs(String a, int na, String b, int nb) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(a, na);
        m.put(b, nb);
        return m;
    }

    // ------------------------------------------------------------------ machines and roosts on the field

    /** The farm's hook for the barn's placed objects. */
    final class Objects implements FarmView.ObjectHook {
        @Override
        public boolean use(int row, int column, String id, Item held) {
            if (!Artisan.machine(id)) {
                return false;
            }
            Game game = shell.game;
            Machine before = game.farm.machine(row, column);
            String message = Artisan.use(game, row, column, id, held == null ? null : held.id());
            Machine after = game.farm.machine(row, column);
            shell.toast(message);
            shell.sfx(after != before ? (after == null ? Sfx.GRAB : Sfx.SWITCH) : Sfx.ERROR);
            return true;
        }

        @Override
        public boolean removable(int row, int column, String id) {
            return shell.game.farm.machine(row, column) == null;
        }

        @Override
        public boolean draw(SceneCanvas canvas, Art.Seasonal look, SceneDraw style, int row, int column, String id,
                float x, float feet) {
            Game game = shell.game;
            Machine work = game.farm.machine(row, column);
            boolean ready = work != null && work.ready(game.calendar);
            long t = shell.ticks;
            switch (id) {
                case Artisan.JAR -> drawJar(canvas, style, x, feet, work, ready);
                case Artisan.KEG -> {
                    // Its spring (S3K Map_Spring 13, the yellow one) bounces when the fizz is ready.
                    stand(canvas, art.keg, x, feet, style, 0);
                    drawSprite(canvas, shell.art.spring, 13, x, feet - art.keg.height() + 3, style,
                            ready ? (float) -Math.abs(Math.sin(t / 5.0)) * 4 : 0);
                }
                case Artisan.LOOM -> {
                    stand(canvas, art.loom, x, feet, style, 0);
                    if (work != null && !ready) {
                        int shuttle = (int) (Math.abs((t / 2) % 20 - 10));
                        canvas.fill(Math.round(x - 6 + shuttle), Math.round(feet - 16), 3, 2, 0xFFFFFFFF);
                    }
                }
                case Artisan.PRESS -> stand(canvas, art.press, x, feet, style,
                        work != null && !ready ? (float) Math.abs(Math.sin(t / 10.0)) * 2 : 0);
                case BarnContent.ROOST -> drawRoost(canvas, style, row, column, x, feet);
                default -> {
                    return false;
                }
            }
            if (ready && !id.equals(Artisan.JAR) && !id.equals(BarnContent.ROOST)) {
                sparkle(canvas, x, feet - 30);
            }
            return true;
        }
    }

    private void stand(SceneCanvas canvas, SceneImage image, float x, float feet, SceneDraw style, float lift) {
        canvas.draw(image, x - image.width() / 2f, feet - image.height() + 1 - lift, style);
    }

    private void drawJar(SceneCanvas canvas, SceneDraw style, float x, float feet, Machine work, boolean ready) {
        var monitor = shell.art.monitor;
        if (monitor == null || monitor.frameCount() == 0) {
            return;
        }
        // Map_Monitor 0-2 are the static screens; the loaded item shows through the static.
        int frame = (int) (shell.ticks / 4 % 3);
        drawSprite(canvas, monitor, work == null ? frame : 0, x, feet, style, 0);
        if (work != null) {
            Item shown = shell.game.item(ready ? work.output() : work.input());
            shell.art.icons.draw(canvas, shown, x - 8, feet - 28, ready ? style : style.withAlpha(0.6f));
            if (ready) {
                sparkle(canvas, x, feet - 36);
            }
        }
    }

    private void drawRoost(SceneCanvas canvas, SceneDraw style, int row, int column, float x, float feet) {
        stand(canvas, art.roost, x, feet, style, 0);
        Inventory basket = shell.game.farm.chests.get(row + "." + column);
        if (basket != null) {
            int shown = 0;
            for (int i = 0; i < basket.size() && shown < 3; i++) {
                if (basket.id(i) != null) {
                    shell.art.icons.draw(canvas, shell.game.item(basket.id(i)), x - 14 + shown * 7, feet - 14, style);
                    shown++;
                }
            }
        }
    }

    /** The ring's sparkle (S3K Map_Ring 4-7) over something ready. */
    void sparkle(SceneCanvas canvas, float x, float y) {
        var ring = shell.art.ring;
        if (ring != null && ring.frameCount() > 7) {
            canvas.draw(ring.frame(4 + (int) (shell.ticks / 6 % 4)), x, y, SceneDraw.plain());
        }
    }

    private static void drawSprite(SceneCanvas canvas, com.openggf.mods.scene.SceneSpriteSet set, int frame, float x,
            float feet, SceneDraw style, float lift) {
        if (set == null || frame >= set.frameCount()) {
            return;
        }
        SceneSprite sprite = set.frame(frame);
        canvas.draw(sprite, x, feet + lift - (sprite.height() - sprite.originY()), style);
    }

    // ------------------------------------------------------------------ debug

    /**
     * Debug ({@code barn ...}): {@code build coop|pen [big]}, {@code buy KIND [N]}, {@code feed N},
     * {@code love N} (every animal's affection), {@code night} (run tonight's barn work now),
     * {@code harvest} (the roosts pick now and their flocks fly it in), {@code truffle ROW COL},
     * {@code rocky ID} (what Rocky is holding), {@code pet [KIND]} (stand by an animal and pet it),
     * {@code goods} (a day's goods in both buildings), {@code place ID ROW COL [READY]} (a placed
     * object, a machine loaded with its first input and READY days to go), {@code menu coop|pen}.
     */
    public static boolean debug(Shell shell, String[] p) {
        if (shell.game == null || p.length < 2) {
            return false;
        }
        Game game = shell.game;
        Barn barn = section(game);
        switch (p[1]) {
            case "build" -> {
                int level = p.length > 3 && p[3].equals("big") ? 2 : 1;
                if (p[2].equals("coop")) {
                    barn.coop = level;
                } else {
                    barn.pen = level;
                }
            }
            case "buy" -> {
                int n = p.length > 3 ? Integer.parseInt(p[3]) : 1;
                BarnSystem sys = of(shell);
                for (int i = 0; i < n; i++) {
                    game.rings += Animals.price(p[2]);
                    if (barn.buy(game, p[2]) != null) {
                        return false;
                    }
                    if (sys != null) {
                        sys.arrive(barn.animals.get(barn.animals.size() - 1));
                    }
                }
            }
            case "feed" -> {
                barn.coopFeed = Math.min(Barn.MAX_FEED, Integer.parseInt(p[2]));
                barn.penFeed = barn.coopFeed;
            }
            case "love" -> {
                for (Animal a : barn.animals) {
                    a.affection = Math.max(0, Math.min(Animal.MAX_AFFECTION, Integer.parseInt(p[2])));
                    a.age = Math.max(a.age, 9);
                }
            }
            case "night" -> barn.nextDay(game);
            case "truffle" -> {
                barn.truffles.add(Integer.parseInt(p[2]) + "." + Integer.parseInt(p[3]));
                BarnSystem sys = of(shell);
                if (sys != null) {
                    sys.play.actors.add(new TruffleActor(sys, Integer.parseInt(p[2]), Integer.parseInt(p[3])));
                }
            }
            case "pet" -> {
                BarnSystem sys = of(shell);
                if (sys == null) {
                    return false;
                }
                for (Actor actor : sys.play.actors) {
                    if (actor instanceof AnimalActor a && a.animal.kind.equals(p.length > 2 ? p[2] : a.animal.kind)
                            && a.visible(game)) {
                        sys.play.farm().runner.x = a.x();
                        sys.play.farm().runner.depth = a.y() - starpost.farm.FarmView.FIELD_TOP - 4;
                        return a.interact(shell, sys.play);
                    }
                }
                return false;
            }
            case "rocky" -> {
                String id = String.join("_", java.util.Arrays.copyOfRange(p, 2, p.length));
                for (Animal a : barn.animals) {
                    if (a.kind.equals("rocky") && game.catalog.hasItem(id)) {
                        a.holding = id;
                    }
                }
            }
            case "harvest" -> {
                // The roosts' morning harvest now, with fresh flocks to fly it in.
                BarnSystem sys = of(shell);
                if (sys == null) {
                    return false;
                }
                barn.harvested.clear();
                barn.harvestRoosts(game);
                sys.play.actors.removeIf(actor -> actor instanceof FlickyFlock);
                for (int r = 0; r < starpost.core.Farm.ROWS; r++) {
                    for (int c = 0; c < starpost.core.Farm.COLUMNS; c++) {
                        if (roostAt(game, r, c)) {
                            sys.play.actors.add(new FlickyFlock(sys, r, c));
                        }
                    }
                }
            }
            case "goods" -> {
                barn.coopGoods.merge("cucky_egg", 3, Integer::sum);
                barn.penGoods.merge("pocky_fluff", 1, Integer::sum);
            }
            case "place" -> {
                int first = 2;
                while (first < p.length && !p[first].matches("\\d+")) {
                    first++;
                }
                String id = String.join("_", java.util.Arrays.copyOfRange(p, 2, first));
                int row = Integer.parseInt(p[first]);
                int col = Integer.parseInt(p[first + 1]);
                boolean loaded = p.length > first + 2;
                Plot plot = game.farm.plot(row, col);
                if (plot == null || game.catalog.placeable(id) == null) {
                    return false;
                }
                plot.cover = Plot.GRASS;
                plot.clearCrop();
                plot.object = id;
                if (loaded && Artisan.machine(id)) {
                    String input = switch (id) {
                        case Artisan.JAR -> "ring_radish";
                        case Artisan.KEG -> "spring_yard_hops";
                        case Artisan.LOOM -> "pocky_fluff";
                        default -> "sunflower";
                    };
                    Artisan.Job job = Artisan.job(game.catalog, id, input);
                    game.farm.machines.put(row + "." + col, new Machine(input, job.output(), job.count(),
                            game.calendar.dayNumber() + Integer.parseInt(p[p.length - 1])));
                }
            }
            case "menu" -> {
                BarnSystem sys = of(shell);
                if (sys == null) {
                    return false;
                }
                shell.push(new BarnMenu(sys, p[2].equals("pen") ? Animals.PEN : Animals.COOP));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The barn system of the current play screen, or null. */
    private static BarnSystem of(Shell shell) {
        if (shell.screen() instanceof PlayScreen play) {
            for (Actor actor : play.actors) {
                if (actor instanceof BuildingActor b) {
                    return b.sys;
                }
            }
        }
        return null;
    }

    /** Whether a plot holds a roost (for the flock). */
    static boolean roostAt(Game game, int row, int column) {
        Plot plot = game.farm.raw(row, column);
        PlaceableDef def = plot.object == null ? null : game.catalog.placeable(plot.object);
        return def != null && def.role() == PlaceableDef.Role.ROOST;
    }
}
