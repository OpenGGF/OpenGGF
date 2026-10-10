package starpost.museum;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import starpost.core.Game;
import starpost.core.Plot;
import starpost.farm.FarmView;
import starpost.orchard.Orchard;
import starpost.people.HeartEvent;
import starpost.people.People;
import starpost.people.PeopleSystem;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;
import starpost.valley.Valley;

/**
 * How the museum plugs in (design doc §14): its annex stands beside Tails's workshop with the
 * {@code museum} doorway, glinting spots wait to be dug on the farm and along the valley (and the
 * Star Post Cap under the palms while it is missing), returning the cap plays Hazel's scene, and
 * debug commands serve captures.
 */
public final class MuseumSystem {
    final Shell shell;
    final PlayScreen play;
    final Museum museum;
    final MuseumArt art;
    /** The collections this catalogue fills (built once a day, not every frame). */
    final List<Exhibits.Exhibit> exhibits;

    private MuseumSystem(Shell shell, PlayScreen play, Museum museum) {
        this.shell = shell;
        this.play = play;
        this.museum = museum;
        this.art = new MuseumArt(shell.art);
        this.exhibits = Exhibits.all(shell.catalog);
    }

    /** The section, added if a save predates the museum. */
    public static Museum section(Game game) {
        Museum museum = game.section(Museum.class);
        if (museum == null) {
            museum = new Museum();
            game.sections.add(museum);
        }
        return museum;
    }

    /** Called from {@code Systems.install} for every new play screen. */
    public static void install(Shell shell, PlayScreen play, List<Actor> actors, Map<String, Consumer<Shell>> places) {
        MuseumSystem sys = new MuseumSystem(shell, play, section(shell.game));
        shell.art.icons.addSource("museum", sys.art::icon);
        actors.add(0, sys.new Annex());                    // first, so neighbours walk in front of it
        Game game = shell.game;
        for (int[] rc : DigSpots.farm(game)) {
            actors.add(new DigSpot(sys, "farm." + rc[0] + "." + rc[1], Actor.FARM,
                    FarmView.FIELD_X + rc[1] * 16 + 8, FarmView.ROW_Y + rc[0] * FarmView.ROW_STEP - 2, rc[0], rc[1], false));
        }
        Valley valley = play.valley().valley;
        int vx = DigSpots.valleyX(game, valley.width());
        actors.add(new DigSpot(sys, "valley." + vx, Actor.VALLEY, vx, valley.floorBelow(vx, 0), -1, -1, false));
        actors.add(new DigSpot(sys, "cap", Actor.VALLEY, DigSpots.CAP_X, valley.floorBelow(DigSpots.CAP_X, 0), -1, -1, true));
        places.put("museum", s -> s.push(new MuseumScreen(sys)));
    }

    /** The doorway's x in the valley (Valley.places "museum"), or the fallback beside the workshop. */
    float doorX() {
        for (Valley.Place place : play.valley().valley.places) {
            if (place.id().equals("museum")) {
                return place.x();
            }
        }
        return 4 * 256 + 259;
    }

    // ------------------------------------------------------------------ Hazel's Star Post Cap

    /**
     * The cap is back on the shelf: Hazel comes running, works out who buried it, and gives the
     * farmer a palm she grew from one of those coconuts. Without the neighbours installed, the
     * thanks and the sapling arrive anyway.
     */
    void capReturned() {
        Game game = shell.game;
        PeopleSystem people = PeopleSystem.of(play);
        People section = game.section(People.class);
        Runnable after = () -> {
            game.flags.add(Museum.RETURNED);
            if (section != null && section.cast.get("hazel") != null) {
                section.add("hazel", People.POINTS_PER_HEART);   // a heart for finding it
            }
        };
        if (people == null || section == null || section.cast.get("hazel") == null) {
            game.inventory.add(game.item(Orchard.PALM), 1);
            after.run();
            shell.toast("HAZEL GAVE YOU A PALM SAPLING");
            shell.sfx(Sfx.PERFECT);
            return;
        }
        HeartEvent scene = HeartEvent.scene("hazel_cap", "hazel")
                .enter("hazel", -1, 200, 30)
                .emote("hazel", "!")
                .say("hazel", "{FARMER}! IS THAT... THE STAR POST CAP?! YOU FOUND IT! WHERE WAS IT?",
                        "item:" + MuseumContent.CAP, "!", "?")
                .narrate("YOU POINT EAST, TO THE PALMS. THE PALMS WHERE HAZEL BURIES HER COCONUTS.")
                .emote("hazel", "sweat")
                .say("hazel", "...OH. I BURIED IT. WITH THE COCONUTS. FOR WINTER. I'M A VERY FAST FORGETTER.",
                        "item:palm_coconut", "house", "sweat")
                .say("hazel", "I'M SORRY! THANK YOU! ONE OF THOSE COCONUTS GREW. IT'S YOURS. PLANT IT!",
                        "heart", "item:" + Orchard.PALM, "!")
                .give(Orchard.PALM, 1)
                .hop("hazel")
                .say("hazel", "I'LL DUST IT TWICE AS FAST NOW. THREE TIMES!", "sparkle", "heart")
                .leave("hazel", 220);
        people.playScene(scene, after);
    }

    // ------------------------------------------------------------------ the annex

    /** The museum's annex beside the workshop, with its sign; drawn behind the neighbours. */
    final class Annex implements Actor {
        MuseumSystem owner() {
            return MuseumSystem.this;
        }

        @Override
        public int view() {
            return VALLEY;
        }

        @Override
        public float x() {
            return doorX();
        }

        @Override
        public float y() {
            return play.valley().valley.floorBelow(Math.round(doorX()), 0);
        }

        @Override
        public float reach() {
            return 0;
        }

        @Override
        public void update(Shell shell, PlayScreen play) {
        }

        @Override
        public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
            drawAt(canvas, x() - cx, y() - cy, tint);
        }

        void drawAt(SceneCanvas canvas, float x, float y, SceneDraw tint) {
            Game game = shell.game;
            SceneImage building = art.annex(game.calendar.season());
            canvas.draw(building, x - building.width() / 2f, y - building.height() + 2, tint);
            // A finished collection shows its prize in a window: restoration you can see.
            for (int i = 0; i < exhibits.size(); i++) {
                Exhibits.Exhibit e = exhibits.get(i);
                if (!museum.complete(e)) {
                    continue;
                }
                int[] r = MuseumArt.window(i);
                String trophy = switch (e.id()) {
                    case Exhibits.MINERALS -> "emerald_shard";
                    case Exhibits.SCRAP -> "motobug_shell";
                    default -> MuseumContent.CAP;
                };
                if (game.catalog.hasItem(trophy)) {
                    float wx = x - building.width() / 2f + r[0] + (r[2] - 16) / 2f;
                    float wy = y - building.height() + 2 + r[1] + (r[3] - 16) / 2f;
                    shell.art.icons.draw(canvas, game.item(trophy), wx, wy, tint);
                }
            }
            String label = "WORKSHOP MUSEUM";
            int sw = canvas.textWidth(label) + 8;
            int sy = Math.round(y) - building.height() - 8;
            canvas.fill(Math.round(x) - sw / 2, sy, sw, 12, 0xFF240000);
            canvas.fill(Math.round(x) - sw / 2 + 1, sy + 1, sw - 2, 10, 0xFF6D2400);
            canvas.text(label, Math.round(x) - sw / 2 + 4, sy + 2, Text.YELLOW);
        }
    }

    /** Reuses the annex and visible restoration windows at an engine act's floor. */
    public static void drawTownAnnex(PlayScreen play, SceneCanvas canvas, int x, int floor) {
        for (Actor actor : play.actors) {
            if (actor instanceof Annex annex) {
                annex.drawAt(canvas, x, floor, SceneDraw.plain());
                return;
            }
        }
    }

    // ------------------------------------------------------------------ debug

    /**
     * Debug ({@code museum ...}): {@code open [minerals|scrap|relics|sound]} (the collection page),
     * {@code donate ID...} (put items straight on the shelves), {@code fill minerals|scrap|relics N}
     * (the first N of a collection), {@code missing} (Hazel's four-heart flag), {@code cap} (the
     * farmer at the buried cap), {@code returned} (play Hazel's scene now), {@code spot} (the
     * farmer at today's farm spot), {@code vspot} (at today's valley spot).
     */
    public static boolean debug(Shell shell, String[] p) {
        if (shell.game == null || p.length < 2) {
            return false;
        }
        Game game = shell.game;
        Museum museum = section(game);
        MuseumSystem sys = of(shell);
        switch (p[1]) {
            case "open" -> {
                if (sys == null) {
                    return false;
                }
                MuseumScreen screen = new MuseumScreen(sys);
                screen.tab = p.length > 2 ? switch (p[2]) {
                    case "scrap" -> 1;
                    case "relics" -> 2;
                    case "sound" -> 3;
                    default -> 0;
                } : 0;
                shell.push(screen);
            }
            case "donate" -> {
                String id = String.join("_", java.util.Arrays.copyOfRange(p, 2, p.length));
                museum.debugDonate(game, id);
            }
            case "fill" -> {
                int n = Integer.parseInt(p[3]);
                for (Exhibits.Exhibit e : Exhibits.all(game.catalog)) {
                    if (e.id().equals(p[2])) {
                        for (int i = 0; i < Math.min(n, e.items().size()); i++) {
                            museum.debugDonate(game, e.items().get(i));
                        }
                    }
                }
            }
            case "missing" -> game.flags.add(Museum.MISSING);
            case "cap", "vspot" -> {
                if (sys == null) {
                    return false;
                }
                float x = p[1].equals("cap") ? DigSpots.CAP_X : DigSpots.valleyX(game, sys.play.valley().valley.width());
                sys.play.placeInValley(x - 18);
            }
            case "spot" -> {
                if (sys == null) {
                    return false;
                }
                for (Actor actor : sys.play.actors) {
                    if (actor instanceof DigSpot spot && spot.view() == Actor.FARM && spot.live(game)) {
                        FarmView farm = sys.play.farm();
                        farm.runner.x = spot.x();
                        farm.runner.depth = spot.y() + 2 - (FarmView.FIELD_TOP + 4);
                        farm.snapCamera();
                        return true;
                    }
                }
                return false;
            }
            case "returned" -> {
                if (sys == null) {
                    return false;
                }
                museum.debugDonate(game, MuseumContent.CAP);
                sys.capReturned();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The museum system of the current play screen, or null. */
    static MuseumSystem of(Shell shell) {
        if (shell.screen() instanceof PlayScreen play) {
            for (Actor actor : play.actors) {
                if (actor instanceof Annex annex) {
                    return annex.owner();
                }
            }
        }
        return null;
    }

    /** Whether a farm plot is still open grass a spot can glint on. */
    static boolean diggable(Plot plot) {
        return plot != null && !plot.tilled && plot.cover == Plot.GRASS && plot.crop == null && plot.object == null;
    }
}
