package starpost.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import starpost.art.Art;
import starpost.core.Calendar;
import starpost.core.Game;
import starpost.core.Inventory;
import starpost.core.Item;
import starpost.farm.FarmView;
import starpost.scene.TownBackdrop;
import starpost.ui.Text;

/**
 * Free play: the belt-view farm and the side-view valley, the day's clock, the HUD, and the fold
 * between the two views at the farm gate (design doc §3.4). Menus open as overlays, which stop
 * the clock.
 */
public final class PlayScreen implements Screen {
    private static final int FOLD_TICKS = 28;

    private final FarmView farm;
    private final TownBackdrop valley;
    private boolean onFarm = true;
    /** The fold between views: counts down; the view switches half way. */
    private int fold;
    private boolean foldToValley;
    /** Cutscenes stop the clock (and its overnight faint), hide the HUD and hold the gate shut. */
    public boolean clockStopped;
    public boolean hudHidden;
    public boolean gateLocked;
    /** Everything else in the world, and what each valley doorway does. */
    public final List<Actor> actors = new ArrayList<>();
    public final Map<String, Consumer<Shell>> places = new LinkedHashMap<>();

    public PlayScreen(Shell shell) {
        farm = new FarmView(shell);
        valley = new TownBackdrop(shell);
        farm.arrive(false);
        farm.actors = this::actorsIn;
        valley.actors = this::actorsIn;
        farm.interact = () -> interact(shell, Actor.FARM, farm.runner.x, farm.feetY());
        places.put("seed_stall", s -> s.push(new ShopMenu()));
        places.put("inn", s -> s.push(new InnMenu(this)));
        places.put("workshop", s -> s.push(new WorkshopMenu()));
        places.put("robomart", s -> {
            if (s.game.calendar.dayNumber() < 4) {
                s.toast("ROBOMART: GRAND OPENING SPRING 5!");
            } else {
                s.push(new RobomartMenu());
            }
        });
        places.put("capsule", s -> s.push(new CapsuleMenu()));
        places.put("ruins", s -> s.toast("THE RUINS ARE DARK. PUD WON'T GO IN."));
        places.put("lake", s -> s.toast("BARNABY'S JETTY. NO ROD YET."));
        Systems.install(shell, this, actors, places);
    }

    private List<Actor> actorsIn(int view) {
        List<Actor> out = new ArrayList<>();
        for (Actor actor : actors) {
            if (actor.view() == view) {
                out.add(actor);
            }
        }
        return out;
    }

    /** The action button near an actor: the nearest one within reach gets it. */
    private boolean interact(Shell shell, int view, float x, float y) {
        Actor best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Actor actor : actors) {
            if (actor.view() != view) {
                continue;
            }
            float dx = Math.abs(actor.x() - x), dy = Math.abs(actor.y() - y);
            float distance = dx + dy;
            if (dx <= actor.reach() && dy <= (view == Actor.FARM ? 10 : 32) && distance < bestDistance) {
                best = actor;
                bestDistance = distance;
            }
        }
        return best != null && best.interact(shell, this);
    }

    public boolean onFarm() {
        return onFarm;
    }

    public boolean folding() {
        return fold > 0;
    }

    public FarmView farm() {
        return farm;
    }

    public TownBackdrop valley() {
        return valley;
    }

    @Override
    public void enter(Shell shell) {
        chooseMusic(shell);
    }

    /** Act hand-back to the existing farm view, retaining this day's screen and actors. */
    public void returnToFarm() { debugPlace(true, starpost.people.Anchors.FARM_GATE - 24, 40); }

    /** Stands the farmer in the valley at x, still (festivals start from their place; debug). */
    public void placeInValley(float x) {
        debugPlace(false, x, 0);
    }

    /** Debug: stand at a spot on the farm (x, depth) or in the valley (x). */
    void debugPlace(boolean toFarm, float x, float depth) {
        onFarm = toFarm;
        fold = 0;
        if (toFarm) {
            farm.arrive(false);
            farm.runner.x = x;
            farm.runner.depth = depth;
            farm.snapCamera();
        } else {
            valley.arriveFromFarm();
            valley.pose.x = x;
            valley.pose.y = valley.valley.floorBelow(Math.round(x), 0);
            valley.snapCamera();
        }
    }

    /** The soundtrack for the place and hour (design doc §8). */
    public void chooseMusic(Shell shell) {
        chooseMusic(shell, onFarm, valley.pose.x);
    }

    public void chooseValleyMusic(Shell shell, int x) {
        chooseMusic(shell, false, x);
    }

    private void chooseMusic(Shell shell, boolean onFarm, float x) {
        Game game = shell.game;
        int light = game.calendar.light();
        if (game.weather == Game.SWARM && light == 0) {
            shell.music.want("s1", Music.S1_SBZ);
        } else if (game.raining) {
            shell.music.want("s1", Music.S1_LZ);
        } else if (light == 2) {
            shell.music.want("s1", Music.S1_SLZ);
        } else if (!onFarm && x > 2 * Art.BLOCK && x < 6 * Art.BLOCK) {
            shell.music.want("s1", Music.S1_SYZ);
        } else {
            shell.music.want("s1", Music.S1_GHZ);
        }
    }

    @Override
    public void update(Shell shell) {
        if (shell.game.weather == Game.STORM && shell.ticks % 400 == 0) shell.sfx(Sfx.LIGHTNING_SHIELD);
        Game game = shell.game;
        if (fold > 0) {
            if (--fold == FOLD_TICKS / 2) {
                onFarm = !foldToValley;
                if (onFarm) {
                    farm.arrive(true);
                } else {
                    valley.arriveFromFarm();
                    shell.startTownAct(this); fold=0; return;
                }
                chooseMusic(shell);
            }
            return;
        }
        hotbar(shell);
        if (shell.in.menu) {
            shell.push(new InventoryMenu());
            return;
        }
        if (!clockStopped && game.calendar.tick()) {
            chooseMusic(shell);
        }
        if (!clockStopped && game.calendar.overtime()) {
            shell.toast(game.farmer.toUpperCase() + " PASSED OUT...");
            shell.go(new DayEndScreen(true));
            return;
        }
        for (Actor actor : actors) {
            actor.update(shell, this);
        }
        if (onFarm) {
            switch (farm.update(shell.in)) {
                case SLEEP -> shell.push(new ConfirmMenu("GO TO BED FOR THE NIGHT?", () -> shell.go(new DayEndScreen(false))));
                case SHIP -> shell.push(new ShipMenu());
                case CHEST -> shell.push(new ChestMenu(farm.openedChest));
                case TO_VALLEY -> {
                    if (!gateLocked) {
                        startFold(shell, true);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void startFold(Shell shell, boolean toValley) {
        fold = FOLD_TICKS;
        foldToValley = toValley;
        shell.sfx(Sfx.STARPOST);
    }

    private void hotbar(Shell shell) {
        Inventory inv = shell.game.inventory;
        int before = inv.selected();
        if (shell.in.hotbarKey >= 0) {
            inv.select(shell.in.hotbarKey);
        }
        if (shell.in.nextTool) {
            inv.select(inv.selected() + 1);
        }
        if (shell.in.prevTool) {
            inv.select(inv.selected() - 1);
        }
        if (inv.selected() != before) {
            shell.hotbarChangedAt = shell.ticks;
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        Art.Seasonal look = shell.art.season(game.calendar.season());
        int light = game.calendar.light();
        SceneDraw tint = lightTint(light);
        if (onFarm) {
            farm.draw(canvas, look, light, tint);
        } else {
            valley.draw(canvas, look, light, tint);
        }
        WorldWeather.draw(game, shell.ticks, canvas);
        drawFold(canvas);
        if (!hudHidden) {
            drawHud(shell, canvas);
        }
    }

    /** The world's tint at a light (0 day, 1 dusk, 2 night), for anything drawn into the valley from outside it. */
    public static SceneDraw lightTint(int light) {
        return SceneDraw.plain().withTint(light == 0 ? 0xFFFFFFFF : light == 1 ? 0xFFFFC8A0 : 0xFF6D80C8);
    }

    /** The fold: Green Hill's checker closes from top and bottom like a shutter, then opens on the other view. */
    private void drawFold(SceneCanvas canvas) {
        if (fold <= 0) {
            return;
        }
        int half = FOLD_TICKS / 2;
        float closed = fold > half ? (FOLD_TICKS - fold) / (float) half : fold / (float) half;
        int bar = Math.round(canvas.height() / 2f * closed);
        canvas.fill(0, 0, canvas.width(), bar, 0xFF240000);
        canvas.fill(0, canvas.height() - bar, canvas.width(), bar, 0xFF240000);
        if (bar > 2) {
            canvas.fill(0, bar - 2, canvas.width(), 2, 0xFFB66D24);
            canvas.fill(0, canvas.height() - bar, canvas.width(), 2, 0xFFB66D24);
        }
    }

    public static void drawHud(Shell shell, SceneCanvas canvas) { drawHud(shell,canvas,shell.ticks); }
    public static void drawHud(Shell shell,SceneCanvas canvas,long ticks) {
        Game game = shell.game;
        Calendar cal = game.calendar;
        // Sonic 1's HUD: TIME turns red after midnight, RINGS when there are none (flashing, as in the ROM).
        var hud = shell.art.hud;
        boolean flash = ticks / 8 % 2 == 0;
        boolean late = cal.minutes() >= 24 * 60;
        hud.timeRow(canvas, late && flash, cal.minutes() / 60 % 24, cal.minutes() % 60, 8);
        hud.ringsRow(canvas, game.rings == 0 && flash, game.rings, 24);
        String date = Calendar.seasonName(cal.season()) + " " + cal.day() + " " + Calendar.weekdayName(cal.weekday());
        Text.right(canvas, date, canvas.width() - 8, 6, Text.WHITE);
        // Momentum: a bar that drains with chores and refills with Sonic things.
        int bw = 72, bx = canvas.width() - 8 - bw, by = 19;
        canvas.fill(bx - 1, by - 1, bw + 2, 8, 0xFF000000);
        int fillW = Math.round(bw * game.momentum / (float) game.maxMomentum);
        int colour = game.momentum < game.maxMomentum / 5 ? 0xFFFF4924 : 0xFF24B6FF;
        canvas.fill(bx, by, fillW, 6, colour);
        canvas.fill(bx, by, fillW, 2, 0x60FFFFFF);
        Text.right(canvas, "MOMENTUM", bx - 4, 18, Text.YELLOW);
        drawHotbar(shell, canvas,ticks);
    }

    public static void drawHotbar(Shell shell,SceneCanvas canvas) { drawHotbar(shell,canvas,shell.ticks); }
    private static void drawHotbar(Shell shell,SceneCanvas canvas,long ticks) {
        Game game = shell.game;
        Inventory inv = game.inventory;
        int slot = 18, x0 = (canvas.width() - slot * Inventory.HOTBAR) / 2, y = canvas.height() - 18;
        canvas.fill(x0 - 2, y - 2, slot * Inventory.HOTBAR + 4, 20, 0xB0000818);
        for (int i = 0; i < Inventory.HOTBAR; i++) {
            int x = x0 + i * slot;
            boolean sel = i == inv.selected();
            canvas.fill(x, y, 17, 17, sel ? 0xFFB66D24 : 0xFF203060);
            canvas.fill(x + 1, y + 1, 15, 15, sel ? 0xFF493010 : 0xFF101838);
            String id = inv.id(i);
            if (id != null) {
                Item item = game.item(id);
                shell.art.icons.draw(canvas, item, x + 0.5f, y + 0.5f, SceneDraw.plain());
                int count = inv.count(i);
                if (count > 1) {
                    String n = Integer.toString(count);
                    canvas.text(n, x + 17 - canvas.textWidth(n), y + 9, Text.WHITE);
                }
            }
        }
        String id = inv.selectedId();
        if (id != null && ticks - shell.hotbarChangedAt < 120) {
            Item item = game.item(id);
            String label = item.name() + (id.equals("water_shield") ? "  " + game.waterCharges + "/" + game.waterCapacity : "");
            Text.centred(canvas, label, y - 14, Text.WHITE);
        }
    }
}
