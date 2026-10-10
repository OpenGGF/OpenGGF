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
import starpost.valley.ValleyView;
import starpost.ui.Text;

/**
 * Free play: the belt-view farm and the side-view valley, the day's clock, the HUD, and the fold
 * between the two views at the farm gate (design doc §3.4). Menus open as overlays, which stop
 * the clock.
 */
public final class PlayScreen implements Screen {
    private static final int FOLD_TICKS = 28;

    private final FarmView farm;
    private final ValleyView valley;
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
        valley = new ValleyView(shell);
        farm.arrive(false);
        farm.actors = this::actorsIn;
        valley.actors = this::actorsIn;
        farm.interact = () -> interact(shell, Actor.FARM, farm.runner.x, farm.feetY());
        valley.interact = () -> interact(shell, Actor.VALLEY, valley.runner.x, valley.runner.y);
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

    public ValleyView valley() {
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
            valley.runner.x = x;
            valley.runner.y = valley.valley.floorBelow(Math.round(x), 0);
            valley.runner.speed = 0;
            valley.snapCamera();
        }
    }

    /** The soundtrack for the place and hour (design doc §8). */
    void chooseMusic(Shell shell) {
        Game game = shell.game;
        int light = game.calendar.light();
        if (game.weather == Game.SWARM && light == 0) {
            shell.music.want("s1", Music.S1_SBZ);
        } else if (game.raining) {
            shell.music.want("s1", Music.S1_LZ);
        } else if (light == 2) {
            shell.music.want("s1", Music.S1_SLZ);
        } else if (!onFarm && valley.runner.x > 2 * Art.BLOCK && valley.runner.x < 6 * Art.BLOCK) {
            shell.music.want("s1", Music.S1_SYZ);
        } else {
            shell.music.want("s1", Music.S1_GHZ);
        }
    }

    @Override
    public void update(Shell shell) {
        Game game = shell.game;
        if (fold > 0) {
            if (--fold == FOLD_TICKS / 2) {
                onFarm = !foldToValley;
                if (onFarm) {
                    farm.arrive(true);
                } else {
                    valley.arriveFromFarm();
                    if (shell.startTownAct(this)) { fold = 0; return; }
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
        } else {
            switch (valley.update(shell.in)) {
                case TO_FARM -> startFold(shell, false);
                case ENTER -> enterPlace(shell, valley.entered.id());
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

    private void enterPlace(Shell shell, String id) {
        Consumer<Shell> handler = places.get(id);
        if (handler != null) {
            shell.sfx(Sfx.DOOR_OPEN);
            handler.accept(shell);
        }
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
        if (game.raining) {
            drawRain(shell, canvas, game.weather == Game.STORM);
        } else if (game.weather == Game.SNOW) {
            drawSnow(shell, canvas);
        }
        if (game.aurora && light == 2) {
            drawAurora(shell, canvas);
        }
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

    private void drawRain(Shell shell, SceneCanvas canvas, boolean storm) {
        long seed = shell.ticks * 7;
        int drops = storm ? 140 : 70;
        for (int i = 0; i < drops; i++) {
            int x = (int) ((i * 97 + seed * (storm ? 5 : 3)) % (canvas.width() + 40)) - 20;
            int y = (int) ((i * 53 + seed * 9) % (canvas.height() + 20)) - 10;
            canvas.fill(x, y, 1, storm ? 8 : 6, 0x806DB6FF);
        }
        canvas.fill(0, 0, canvas.width(), canvas.height(), storm ? 0x38000820 : 0x20001848);
        // Lightning: a flash every few seconds, and the shield's crackle with it.
        if (storm) {
            long phase = shell.ticks % 400;
            if (phase < 4 || phase >= 10 && phase < 12) {
                canvas.fill(0, 0, canvas.width(), canvas.height(), 0x90FFFFFF);
            }
            if (phase == 0) {
                shell.sfx(Sfx.LIGHTNING_SHIELD);
            }
        }
    }

    private void drawSnow(Shell shell, SceneCanvas canvas) {
        for (int i = 0; i < 60; i++) {
            float x = (i * 131 + shell.ticks * 0.6f + (float) Math.sin((shell.ticks + i * 40) / 30.0) * 8) % (canvas.width() + 20) - 10;
            float y = (i * 71 + shell.ticks * (0.7f + i % 3 * 0.25f)) % (canvas.height() + 10) - 5;
            canvas.fill(Math.round(x), Math.round(y), i % 4 == 0 ? 2 : 1, i % 4 == 0 ? 2 : 1, 0xE0FFFFFF);
        }
    }

    /** The Emerald Aurora: green and cyan curtains rippling over the night sky. */
    private void drawAurora(Shell shell, SceneCanvas canvas) {
        int w = canvas.width();
        for (int x = 0; x < w; x += 4) {
            double wave = Math.sin((x + shell.ticks * 0.8) / 37.0) + Math.sin((x - shell.ticks * 0.5) / 23.0);
            int top = 18 + (int) (wave * 8);
            int height = 40 + (int) (Math.sin((x + shell.ticks) / 51.0) * 14);
            int colour = (x / 4 % 3 == 0 ? 0x5049FF92 : 0x4024DBDB);
            canvas.fill(x, top, 4, height, colour);
        }
    }

    public static void drawHud(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        Calendar cal = game.calendar;
        // Sonic 1's HUD: TIME turns red after midnight, RINGS when there are none (flashing, as in the ROM).
        var hud = shell.art.hud;
        boolean flash = shell.ticks / 8 % 2 == 0;
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
        drawHotbar(shell, canvas);
    }

    public static void drawHotbar(Shell shell, SceneCanvas canvas) {
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
        if (id != null && shell.ticks - shell.hotbarChangedAt < 120) {
            Item item = game.item(id);
            String label = item.name() + (id.equals("water_shield") ? "  " + game.waterCharges + "/" + game.waterCapacity : "");
            Text.centred(canvas, label, y - 14, Text.WHITE);
        }
    }
}
