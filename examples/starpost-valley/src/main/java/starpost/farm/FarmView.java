package starpost.farm;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import starpost.art.Anim;
import starpost.art.Art;
import starpost.core.CropDef;
import starpost.core.Farm;
import starpost.core.Farmers;
import starpost.core.Game;
import starpost.core.Item;
import starpost.core.Inventory;
import starpost.core.Kind;
import starpost.core.PlaceableDef;
import starpost.core.Plot;
import starpost.scene.Actor;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Controls;

/**
 * The farm in belt view (design doc §3.4): Green Hill's blocks stand upright as the back wall at
 * the ROM's own size, and the field lies in front in {@link Farm#ROWS} rows. The farmer walks
 * side-on along the field and into and out of the screen; everything standing in the field is
 * drawn back to front. The farmhouse is at the west end, the shipping signpost beside it, and
 * the Star Post gate to the valley at the east end.
 */
public final class FarmView {
    /** Screen row of the back wall's floor line, and of the field's rows (a plot's front edge). */
    public static final int WALL_FLOOR = 128;
    public static final int FIELD_TOP = 136;
    public static final int ROW_Y = 148;
    public static final int ROW_STEP = 14;
    public static final int PLOT_H = 12;
    /** World x of the first column, and of the house door, signpost and gate. */
    public static final int FIELD_X = 176;
    public static final int DOOR_X = 76;
    public static final int SIGNPOST_X = 150;
    public static final int GATE_X = FIELD_X + Farm.COLUMNS * 16 + 56;
    /** The farm pond in the field's front corner below the house: world x span and feet rows. */
    public static final int POND_X0 = 22;
    public static final int POND_X1 = 142;
    public static final int POND_TOP = 182;
    public static final int POND_BOTTOM = 210;
    public static final int WIDTH = 5 * Art.BLOCK;
    private final int[] wall = {13, 45, 60, 60, 53};
    /** Feet range: the back of the field to the bottom of the screen. */
    private static final int DEPTH_MIN = FIELD_TOP + 4;
    private static final int DEPTH_MAX = 208;
    private static final int DASH_CHARGE_TICKS = 18;

    private final Shell shell;
    public final BeltRunner runner = new BeltRunner(DOOR_X + 40, 0);
    private float camera;
    /** A cutscene's camera: world x to ease toward instead of following the farmer (NaN: follow). */
    public float cameraTarget = Float.NaN;
    private int chargeTicks;
    private boolean dashing;
    /** Plots the current spin dash has tilled (Tails's weaker spin stops tilling after three). */
    private int dashTilled;
    /** The action button went down in a menu (closing it): its hold and release are not the farm's. */
    private boolean foreignHold;
    private long lastActionAt = -100;
    private int lastActionRow = -1;
    private int lastActionColumn = -1;
    private final Anim anim = new Anim();
    /** The farm loop (block 53 standing in the back wall): a lap at speed refills Momentum. */
    private static final float LOOP_X = 4 * Art.BLOCK;
    private static final float LOOP_CY = WALL_FLOOR - Art.FLOOR + 111;
    private static final float LOOP_R = 60;
    private boolean looping;
    private float loopAngle;
    private int lastLapAt = -1000;
    /** Set by the play screen: the farm's actors, and the action button offered to them first. */
    public java.util.function.IntFunction<List<Actor>> actors = view -> List.of();
    public java.util.function.BooleanSupplier interact = () -> false;
    /** The action button at the pond's edge without the Water Shield (fishing): true when it was used. */
    public java.util.function.BooleanSupplier pondAction = () -> false;
    /** Placed objects other systems own (machines, roosts), asked before the built-in behaviour. */
    public final List<ObjectHook> objectHooks = new ArrayList<>();

    /** Use and drawing of placed objects another system owns. */
    public interface ObjectHook {
        /** The action button on the object (holding {@code held}, or null); true when handled. */
        boolean use(int row, int column, String id, Item held);

        /** Whether the Fire Shield may knock it loose (a loaded machine may not). */
        default boolean removable(int row, int column, String id) {
            return true;
        }

        /** Draws the object standing at ({@code x}, {@code feet}); true when drawn. Must not change state. */
        boolean draw(SceneCanvas canvas, Art.Seasonal look, SceneDraw style, int row, int column, String id, float x,
                float feet);
    }

    /** What the farm asks of its screen after an update. */
    public enum Request {
        NONE,
        SLEEP,
        SHIP,
        CHEST,
        TO_VALLEY
    }

    /** The chest opened by the last {@link Request#CHEST}. */
    public Inventory openedChest;

    public FarmView(Shell shell) {
        this.shell = shell;
    }

    /** Puts the farmer at the house door (morning) or at the gate (coming home). */
    public void arrive(boolean atGate) {
        runner.x = atGate ? GATE_X - 30 : DOOR_X + 6;
        runner.depth = atGate ? 40 : 4;
        runner.facingLeft = atGate;
        runner.speed = atGate ? -2 : 0;
        runner.depthSpeed = 0;
        runner.height = 0;
        camera = clampCamera(runner.x - shell.width() / 2f);
    }

    public void snapCamera() {
        camera = clampCamera(runner.x - shell.width() / 2f);
    }

    public float camera() {
        return camera;
    }

    public float feetY() {
        return DEPTH_MIN + runner.depth;
    }

    public Request update(Controls in) {
        Game game = shell.game;
        float previousX = runner.x;
        Request request = Request.NONE;
        // Spin dash: hold the action button to charge, release to roll along the row tilling.
        boolean holdAct = shell.ctx.buttonDown(com.openggf.mods.scene.SceneButtons.B)
                || shell.ctx.keyDown(com.openggf.mods.scene.SceneKeys.X);
        if (holdAct && chargeTicks == 0 && !in.act && !dashing) {
            foreignHold = true;          // held since a menu took the press: closing a menu must not reopen it
        }
        if (!holdAct) {
            foreignHold = false;
        }
        holdAct &= !foreignHold;
        if (holdAct && runner.height == 0 && !dashing) {
            if (++chargeTicks == DASH_CHARGE_TICKS) {
                shell.sfx(Sfx.SPINDASH);
            }
        } else {
            if (chargeTicks >= DASH_CHARGE_TICKS && !holdAct) {
                dashing = true;
                dashTilled = 0;
                runner.speed = (runner.facingLeft ? -1 : 1) * Farmers.dashSpeed(game.farmer);
                runner.rolling = true;
                shell.sfx(Sfx.DASH);
            } else if (chargeTicks > 0 && chargeTicks < DASH_CHARGE_TICKS && !holdAct) {
                request = act(game);
            }
            chargeTicks = 0;
        }
        boolean charged = chargeTicks >= DASH_CHARGE_TICKS;
        float maxDepth = DEPTH_MAX - DEPTH_MIN;
        if (looping) {
            stepLoop(game);
        } else if (dashing) {
            runner.speed -= Math.signum(runner.speed) * 0.125f;
            runner.x = Math.max(16, Math.min(WIDTH - 16, runner.x + runner.speed));
            tillUnderfoot(game);
            if (Math.abs(runner.speed) < 1.5f) {
                dashing = false;
                runner.rolling = false;
                runner.speed = 0;
            }
        } else if (!charged && !(chargeTicks > 0)) {
            float beforeX = runner.x, beforeDepth = runner.depth;
            if (runner.step(16, WIDTH - 16, maxDepth, in.left, in.right, in.up, in.down, in.jump, in.jumpHeld)) {
                shell.sfx(Sfx.JUMP);
            }
            if (runner.height == 0 && inPond(runner.x, feetY())) {
                runner.x = beforeX;          // the pond's edge stops a walk (a jump clears it)
                runner.depth = beforeDepth;
                runner.speed = 0;
                runner.depthSpeed = 0;
            }
        }
        float entry = LOOP_X + 110;
        if (!looping && runner.depth < 10 && runner.height == 0 && runner.speed >= 4
                && previousX < entry && runner.x >= entry) {
            looping = true;
            loopAngle = 0;
            runner.rolling = true;
        }
        // The house door: walk to it and press up at the back of the field.
        if (Math.abs(runner.x - DOOR_X) < 12 && runner.depth < 6 && in.upPressed) {
            request = Request.SLEEP;
        }
        if (runner.x >= GATE_X + 8 && runner.speed > 0) {
            request = Request.TO_VALLEY;
        }
        float target = Float.isNaN(cameraTarget) ? runner.x - shell.width() / 2f + (runner.facingLeft ? -20 : 20)
                : cameraTarget - shell.width() / 2f;
        camera += (clampCamera(target) - camera) * (Float.isNaN(cameraTarget) ? 0.15f : 0.04f);
        animate(charged);
        return request;
    }

    /**
     * Round the loop as a ball (screen coordinates: the loop stands in the back wall), then a
     * Momentum bonus: a full 30 once an hour of game time, a token 5 for laps in between.
     */
    private void stepLoop(Game game) {
        loopAngle += Math.max(0.08f, Math.abs(runner.speed) / LOOP_R);
        if (loopAngle >= (float) (Math.PI * 2)) {
            looping = false;
            runner.rolling = false;
            runner.x = LOOP_X + 136;
            runner.depth = 2;
            int now = game.calendar.minutes();
            int bonus = Farmers.lapBonus(game.farmer, now - lastLapAt >= 60);
            lastLapAt = now;
            game.restoreBySpeed(bonus);
            shell.toast(game.stamina ? "NICE LAP!" : "LAP! +" + bonus + " MOMENTUM");
            shell.sfx(Sfx.RING);
        }
    }

    private float loopX() {
        return LOOP_X + 126 + (float) Math.sin(loopAngle) * LOOP_R;
    }

    private float loopY() {
        return LOOP_CY + (float) Math.cos(loopAngle) * LOOP_R;
    }

    /** The action button: work the plot underfoot, or use the signpost. */
    private Request act(Game game) {
        // The signpost first: a neighbour standing beside it must not stop the shipping.
        if (Math.abs(runner.x - SIGNPOST_X) < 18 && runner.depth < 22) {
            return Request.SHIP;
        }
        if (interact.getAsBoolean()) {
            return Request.NONE;
        }
        if (nearPond()) {
            Item held = game.inventory.selectedId() == null ? null : game.item(game.inventory.selectedId());
            if (held != null && held.id().equals("water_shield")) {
                game.waterCharges = game.waterCapacity;
                shell.sfx(Sfx.BUBBLE_SHIELD);
                shell.toast("WATER SHIELD FULL: " + game.waterCapacity);
            } else if (!pondAction.getAsBoolean()) {
                shell.toast("THE FARM POND. HOLD THE WATER SHIELD OR A ROD.");
            }
            return Request.NONE;
        }
        int[] rc = plotUnderfoot();
        if (rc == null) {
            return Request.NONE;
        }
        Plot plot = game.farm.plot(rc[0], rc[1]);
        if (plot == null) {
            shell.toast("THIS LAND IS STILL OVERGROWN");
            shell.sfx(Sfx.ERROR);
            return Request.NONE;
        }
        if (plot.object != null) {
            return useObject(game, plot, rc[0], rc[1]);
        }
        work(game, plot, rc[0], rc[1]);
        return Request.NONE;
    }

    /** A placed object underfoot: the Fire Shield knocks it loose; otherwise it is used (chests open). */
    private Request useObject(Game game, Plot plot, int row, int column) {
        PlaceableDef def = game.catalog.placeable(plot.object);
        Item held = game.inventory.selectedId() == null ? null : game.item(game.inventory.selectedId());
        Inventory chest = def != null && def.slots() > 0 ? game.farm.chest(row, column, def.slots()) : null;
        if (held != null && held.id().equals("fire_shield")) {
            boolean locked = false;
            for (ObjectHook hook : objectHooks) {
                locked |= !hook.removable(row, column, plot.object);
            }
            if (chest != null && !empty(chest) || locked) {
                shell.toast("EMPTY IT FIRST");
                shell.sfx(Sfx.ERROR);
            } else if (game.inventory.add(game.item(plot.object), 1) == 0) {
                game.farm.chests.remove(row + "." + column);
                plot.object = null;
                shell.sfx(Sfx.BREAK);
                marked(row, column);
            }
            return Request.NONE;
        }
        for (ObjectHook hook : objectHooks) {
            if (hook.use(row, column, plot.object, held)) {
                return Request.NONE;
            }
        }
        if (chest != null) {
            openedChest = chest;
            shell.sfx(Sfx.DOOR_OPEN);
            return Request.CHEST;
        }
        shell.toast(game.item(plot.object).text());
        return Request.NONE;
    }

    private static boolean empty(Inventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.id(i) != null) {
                return false;
            }
        }
        return true;
    }

    private void work(Game game, Plot plot, int row, int column) {
        Item held = game.inventory.selectedId() == null ? null : game.item(game.inventory.selectedId());
        if (game.farm.ripe(game.catalog, plot)) {
            CropDef crop = game.farm.harvest(game.catalog, plot);
            Item produce = game.item(crop.produce());
            int count = crop.yield();
            if (game.has("supergrower") && game.rng.nextInt(10) == 0) {
                count *= 2;
                shell.toast("A DOUBLE HARVEST!");
            }
            game.xp(starpost.core.Skills.FARMING, 1 + produce.price() / 10);
            int leftover = game.inventory.add(produce, count);
            if (leftover > 0) {
                shell.toast("NO ROOM FOR " + produce.name());
            }
            shell.sfx(Sfx.GRAB);
            marked(row, column);
            return;
        }
        if (plot.crop != null && plot.dead) {
            plot.clearCrop();
            shell.sfx(Sfx.GRAB);
            marked(row, column);
            return;
        }
        if (plot.cover != Plot.GRASS) {
            clearCover(game, plot, held, row, column);
            return;
        }
        if (held != null && held.kind() == Kind.PLACEABLE) {
            if (plot.crop != null) {
                shell.toast("SOMETHING IS GROWING THERE");
                shell.sfx(Sfx.ERROR);
            } else {
                plot.object = held.id();
                game.inventory.useOne(game.inventory.selected());
                shell.sfx(Sfx.SWITCH);
                marked(row, column);
            }
            return;
        }
        if (held != null && held.kind() == Kind.SEED) {
            CropDef crop = game.catalog.cropFromSeed(held.id());
            if (!plot.tilled || plot.crop != null) {
                shell.toast(plot.tilled ? "SOMETHING IS ALREADY GROWING" : "TILL THE SOIL FIRST");
                shell.sfx(Sfx.ERROR);
            } else if (!crop.grows(game.calendar.season())
                    && !(game.flags.contains("capsule_garden") && row < 2 && column < 12)) {
                shell.toast(held.name() + " WON'T GROW THIS SEASON");
                shell.sfx(Sfx.ERROR);
            } else {
                plot.crop = crop.id();
                plot.age = 0;
                plot.dead = false;
                game.inventory.useOne(game.inventory.selected());
                shell.sfx(Sfx.SWITCH);
                marked(row, column);
            }
            return;
        }
        if (held != null && held.id().equals("water_shield")) {
            if (!plot.tilled) {
                shell.sfx(Sfx.ERROR);
            } else if (game.waterCharges <= 0) {
                shell.toast("THE WATER SHIELD IS EMPTY. REFILL AT WATER.");
                shell.sfx(Sfx.ERROR);
            } else if (!plot.watered && spend(game, 1)) {
                plot.watered = true;
                game.waterCharges--;
                // Tails's two tails fan the same charge over the next plot on.
                for (int i = 1; i < Farmers.waterReach(game.farmer); i++) {
                    Plot next = game.farm.plot(row, column + (runner.facingLeft ? -i : i));
                    if (next != null && next.tilled && next.object == null) {
                        next.watered = true;
                    }
                }
                shell.sfx(Sfx.SPLASH);
                marked(row, column);
            }
            return;
        }
        if (!plot.tilled && spend(game, 2)) {
            plot.tilled = true;
            shell.sfx(Sfx.GROUND_SLIDE);
            marked(row, column);
            dugUp(game);
        }
    }

    /** Knuckles digs rather than tills, and sometimes turns something up. */
    private void dugUp(Game game) {
        String found = Farmers.dig(game);
        if (found == null) {
            return;
        }
        if (found.startsWith("rings:")) {
            int n = Integer.parseInt(found.substring(6));
            game.rings += n;
            shell.toast("BURIED RINGS! +" + n);
            shell.sfx(Sfx.RING);
        } else if (game.inventory.add(game.item(found), 1) == 0) {
            shell.toast("DUG UP: " + game.item(found).name());
            shell.sfx(Sfx.GRAB);
        }
    }

    private void clearCover(Game game, Plot plot, Item held, int row, int column) {
        boolean fire = held != null && held.id().equals("fire_shield");
        boolean punch = plot.cover == Plot.ROCK && Farmers.punchesRocks(game.farmer);
        switch (plot.cover) {
            case Plot.WEED -> {
                if (spend(game, 1)) {
                    plot.cover = Plot.GRASS;
                    game.inventory.add(game.item("fibre"), 1);
                    game.xp(starpost.core.Skills.RANGING, 1);
                    shell.sfx(fire ? Sfx.FIRE_SHIELD : Sfx.GRAB);
                    marked(row, column);
                }
            }
            case Plot.ROCK, Plot.STUMP -> {
                if (!fire && !punch) {
                    shell.toast(plot.cover == Plot.ROCK ? "A FIRE SHIELD WOULD BREAK THIS ROCK"
                            : "A FIRE SHIELD WOULD CLEAR THIS STUMP");
                    shell.sfx(Sfx.ERROR);
                } else if (spend(game, 4)) {
                    boolean rock = plot.cover == Plot.ROCK;
                    int wood = 5 + (game.has("forester") ? 1 : 0);
                    if (game.has("lumberjack")) {
                        wood *= 2;
                    }
                    game.inventory.add(game.item(rock ? "marble_chip" : "palm_wood"), rock ? 3 : wood);
                    game.xp(rock ? starpost.core.Skills.SCRAPPING : starpost.core.Skills.RANGING, rock ? 3 : 5);
                    shell.sfx(plot.cover == Plot.ROCK ? Sfx.BREAK : Sfx.CHOP_TREE);
                    plot.cover = Plot.GRASS;
                    marked(row, column);
                }
            }
            default -> {
            }
        }
    }

    private boolean spend(Game game, int cost) {
        if (game.spend(cost)) {
            return true;
        }
        shell.toast("OUT OF MOMENTUM - TAKE A LAP!");
        shell.sfx(Sfx.ERROR);
        return false;
    }

    /** The spin dash tills the grass plots it rolls over in its row (1 Momentum each; Tails's, three at most). */
    private void tillUnderfoot(Game game) {
        int[] rc = plotUnderfoot();
        if (rc == null) {
            return;
        }
        Plot plot = game.farm.plot(rc[0], rc[1]);
        if (plot != null && !plot.tilled && plot.cover != Plot.ROCK && plot.cover != Plot.STUMP
                && dashTilled < Farmers.dashTills(game.farmer) && game.spend(1)) {
            plot.cover = Plot.GRASS;
            plot.tilled = true;
            dashTilled++;
            marked(rc[0], rc[1]);
        }
    }

    private void marked(int row, int column) {
        lastActionAt = shell.ticks;
        lastActionRow = row;
        lastActionColumn = column;
    }

    /** Whether a point of the field is in the pond's water. */
    private static boolean inPond(float x, float feet) {
        float cx = (POND_X0 + POND_X1) / 2f, cy = (POND_TOP + POND_BOTTOM) / 2f;
        float rx = (POND_X1 - POND_X0) / 2f - 4, ry = (POND_BOTTOM - POND_TOP) / 2f - 2;
        float dx = (x - cx) / rx, dy = (feet - cy) / ry;
        return dx * dx + dy * dy < 1;
    }

    /** Whether the farmer stands at the pond's edge. */
    private boolean nearPond() {
        float feet = feetY();
        return runner.x > POND_X0 - 10 && runner.x < POND_X1 + 10 && feet > POND_TOP - 14 && feet < POND_BOTTOM + 8;
    }

    /** {row, column} of the plot under the farmer's feet, or null outside the field. */
    public int[] plotUnderfoot() {
        int column = (int) Math.floor((runner.x - FIELD_X) / 16f);
        int row = Math.round((feetY() - ROW_Y) / (float) ROW_STEP);
        if (column < 0 || column >= Farm.COLUMNS || row < 0 || row >= Farm.ROWS) {
            return null;
        }
        if (Math.abs(feetY() - (ROW_Y + row * ROW_STEP)) > 7) {
            return null;
        }
        return new int[] {row, column};
    }

    private float clampCamera(float x) {
        return Math.max(0, Math.min(WIDTH - shell.width(), x));
    }

    // ------------------------------------------------------------------ animation

    private void animate(boolean charged) {
        float speed = Math.max(Math.abs(runner.speed), Math.abs(runner.depthSpeed) * 2);
        if (looping || dashing || runner.rolling) {
            anim.set(Anim.ROLL, Math.max(0, 4 - (int) speed));
        } else if (charged || chargeTicks > 6) {
            anim.set(Anim.SPINDASH, 0);
        } else if (speed > 0.05f) {
            anim.set(speed >= 6 ? Anim.RUN : Anim.WALK, Math.max(0, 8 - (int) speed));
        } else {
            anim.set(Anim.WAIT, 6);
        }
        anim.tick();
    }

    // ------------------------------------------------------------------ drawing

    public void draw(SceneCanvas canvas, Art.Seasonal look, int light, SceneDraw tint) {
        Game game = shell.game;
        int w = canvas.width(), h = canvas.height();
        int cx = Math.round(camera);
        canvas.drawBackdrop(look.backdrop(light), 0, 0, w, WALL_FLOOR + 8, 40, cx, shell.ticks);
        for (int column = 0; column < wall.length; column++) {
            int x = column * Art.BLOCK - cx;
            if (x > w || x + Art.BLOCK < 0) {
                continue;
            }
            SceneImage block = look.block(wall[column]);
            int rows = Art.FLOOR + 8;
            canvas.drawRegion(block, 0, 0, Art.BLOCK, rows, x, WALL_FLOOR - Art.FLOOR, Art.BLOCK, rows, tint);
        }
        drawHouse(canvas, look, cx, tint);
        // The field.
        for (int y = FIELD_TOP; y < h; y += 64) {
            for (int x = -Math.floorMod(cx, 64); x < w; x += 64) {
                canvas.drawRegion(look.field, 0, 0, 64, Math.min(64, h - y), x, y, 64, Math.min(64, h - y), tint);
            }
        }
        for (int band = 0; band < 6; band++) {
            canvas.fill(0, FIELD_TOP + band * 4, w, 4, (0x48 - band * 0x0C) << 24);
        }
        drawPond(canvas, look, cx, tint);
        // Land still to clear: darker.
        int lockedX = FIELD_X + game.farm.open() * 16 - cx;
        if (lockedX < w) {
            canvas.fill(Math.max(0, lockedX), FIELD_TOP, w - Math.max(0, lockedX), h - FIELD_TOP, 0x40002400);
        }
        // Soil first (it lies flat), then everything standing, back to front.
        for (int row = 0; row < Farm.ROWS; row++) {
            int y = ROW_Y + row * ROW_STEP;
            for (int c = Math.max(0, (cx - FIELD_X) / 16 - 1); c < Farm.COLUMNS; c++) {
                int x = FIELD_X + c * 16 - cx;
                if (x > w) {
                    break;
                }
                Plot plot = game.farm.raw(row, c);
                if (plot.tilled) {
                    SceneImage soil = plot.watered ? look.tilledWet : look.tilledDry;
                    canvas.drawRegion(soil, (c & 1) * 16, 0, 16, PLOT_H, x, y - PLOT_H + 2, 16, PLOT_H, tint);
                }
            }
        }
        if (game.flags.contains("capsule_garden")) {
            drawGarden(canvas, cx);
        }
        drawCursor(canvas, cx);
        List<float[]> order = new ArrayList<>();       // {screen y, kind, row, column}
        for (int row = 0; row < Farm.ROWS; row++) {
            for (int c = Math.max(0, (cx - FIELD_X) / 16 - 2); c < Farm.COLUMNS; c++) {
                if (FIELD_X + c * 16 - cx > w + 8) {
                    break;
                }
                Plot plot = game.farm.raw(row, c);
                if (plot.crop != null || plot.cover != Plot.GRASS || plot.object != null) {
                    order.add(new float[] {ROW_Y + row * ROW_STEP - 2, 0, row, c});
                }
            }
        }
        order.add(new float[] {feetY(), 1, 0, 0});
        order.add(new float[] {FIELD_TOP + 10, 2, 0, 0});    // signpost
        order.add(new float[] {FIELD_TOP + 46, 3, 0, 0});    // gate Star Post
        List<Actor> here = actors.apply(Actor.FARM);
        for (int i = 0; i < here.size(); i++) {
            order.add(new float[] {here.get(i).y(), 4, i, 0});
        }
        order.sort((a, b) -> Float.compare(a[0], b[0]));
        for (float[] item : order) {
            switch ((int) item[1]) {
                case 0 -> drawPlotThing(canvas, look, tint, game, (int) item[2], (int) item[3], item[0], cx);
                case 1 -> drawFarmer(canvas, tint, cx);
                case 2 -> drawSprite(canvas, shell.art.signpost, 0, SIGNPOST_X - cx, item[0], tint, false);
                case 3 -> drawSprite(canvas, shell.art.starpost, 0, GATE_X - cx, item[0], tint, false);
                default -> here.get((int) item[2]).draw(shell, canvas, cx, 0, tint);
            }
        }
        for (Actor actor : here) {
            actor.drawOver(shell, canvas, cx, 0);
        }
    }

    private void drawHouse(SceneCanvas canvas, Art.Seasonal look, int cx, SceneDraw tint) {
        // The farmhouse stands at the back of the field; its door (x 56-76 in the picture) is DOOR_X.
        SceneImage house = look.farmhouse;
        canvas.draw(house, DOOR_X - 66 - cx, FIELD_TOP + 4 - house.height(), tint);
    }

    /** The pond: Green Hill's lake water in a rounded bed with a checker rim. */
    private void drawPond(SceneCanvas canvas, Art.Seasonal look, int cx, SceneDraw tint) {
        int x0 = POND_X0 - cx, w = POND_X1 - POND_X0, top = POND_TOP, h = POND_BOTTOM - POND_TOP;
        for (int row = 0; row < h; row++) {
            float t = (row + 0.5f) / h * 2 - 1;
            int inset = Math.round((1 - (float) Math.sqrt(Math.max(0, 1 - t * t))) * w / 2f);
            int rw = w - inset * 2;
            if (rw <= 0) {
                continue;
            }
            int src = (int) ((shell.ticks / 6 + row * 7) % Math.max(1, look.water.width() - rw));
            canvas.drawRegion(look.water, src, row % look.water.height(), Math.min(rw, look.water.width()), 1,
                    x0 + inset, top + row, rw, 1, tint);
            canvas.fill(x0 + inset - 2, top + row, 2, 1, 0xFF6D2400);
            canvas.fill(x0 + inset + rw, top + row, 2, 1, 0xFF6D2400);
        }
        canvas.fill(x0 + w / 4, top - 1, w / 2, 2, 0xFF6D2400);
    }

    /** The Capsule Garden: the restored capsule's glass over the first twelve columns of the back two rows. */
    private void drawGarden(SceneCanvas canvas, int cx) {
        int x = FIELD_X - cx, y = ROW_Y - PLOT_H - 6, w = 12 * 16, h = ROW_STEP + PLOT_H + 8;
        canvas.fill(x, y, w, h, 0x286DB6FF);
        canvas.fill(x, y, w, 1, 0xA0FFFFFF);
        canvas.fill(x, y + h - 1, w, 1, 0x80B6DBFF);
        for (int i = 0; i <= 12; i += 3) {
            canvas.fill(x + i * 16, y, 1, h, 0x80FFFFFF);
        }
        drawSprite(canvas, shell.art.capsule, 0, x + w / 2f, WALL_FLOOR + 4, SceneDraw.plain(), false);
    }

    private void drawCursor(SceneCanvas canvas, int cx) {
        int[] rc = plotUnderfoot();
        if (rc == null || shell.game.farm.plot(rc[0], rc[1]) == null) {
            return;
        }
        int x = FIELD_X + rc[1] * 16 - cx, y = ROW_Y + rc[0] * ROW_STEP - PLOT_H + 2;
        int pulse = (int) (shell.ticks / 8 % 2) == 0 ? 0xC0FFFFFF : 0x80FFFFFF;
        canvas.fill(x, y, 16, 1, pulse);
        canvas.fill(x, y + PLOT_H - 1, 16, 1, pulse);
        canvas.fill(x, y, 1, PLOT_H, pulse);
        canvas.fill(x + 15, y, 1, PLOT_H, pulse);
    }

    private void drawPlotThing(SceneCanvas canvas, Art.Seasonal look, SceneDraw tint, Game game, int row, int c,
            float feet, int cx) {
        Plot plot = game.farm.raw(row, c);
        float x = FIELD_X + c * 16 - cx;
        boolean pop = lastActionRow == row && lastActionColumn == c && shell.ticks - lastActionAt < 8;
        SceneDraw style = pop ? tint.withScale(1.12f) : tint;
        if (plot.object != null) {
            for (ObjectHook hook : objectHooks) {
                if (hook.draw(canvas, look, style, row, c, plot.object, x + 8, feet)) {
                    return;
                }
            }
            drawObject(canvas, look, style, plot.object, x + 8, feet);
            return;
        }
        if (plot.crop != null) {
            CropDef crop = game.catalog.crop(plot.crop);
            int stage = crop == null ? 0 : crop.stage(plot.age);
            SceneImage image = look.crops.stage(plot.crop, stage, plot.dead);
            canvas.draw(image, x + (16 - image.width()) / 2f, feet - image.height() + 1, style);
            return;
        }
        switch (plot.cover) {
            case Plot.WEED -> canvas.draw(look.plant, x + 8 - look.plant.width() / 4f, feet - look.plant.height() / 2f,
                    style.withScale(0.5f));
            case Plot.ROCK -> drawSprite(canvas, shell.art.purpleRock, 0, x + 8, feet, style.withScale(0.5f), false);
            case Plot.STUMP -> {
                canvas.fill(Math.round(x) + 3, Math.round(feet) - 8, 10, 8, 0xFF924900);
                canvas.fill(Math.round(x) + 3, Math.round(feet) - 9, 10, 2, 0xFFDB9249);
            }
            default -> {
            }
        }
    }

    /** Placed objects in ROM art: Buzz Bombers hover over their plot, a Caterkiller lies along it. */
    private void drawObject(SceneCanvas canvas, Art.Seasonal look, SceneDraw style, String id, float x, float feet) {
        Art art = shell.art;
        int wing = (int) (shell.ticks / 2 % 2);
        switch (id) {
            case "buzz_waterer" -> drawSprite(canvas, art.buzzBomber, wing, x, feet - 14, style, true);
            case "buzz_waterer_mk2" -> drawSprite(canvas, art.buzzBomber, wing, x, feet - 14,
                    style.withTint(0xFFFFDB49), true);
            case "caterkiller_crawler" -> drawSprite(canvas, art.caterkiller, (int) (shell.ticks / 12 % 2), x, feet,
                    style, false);
            case "sonic_scarecrow" -> canvas.draw(look.totem, x - look.totem.width() / 2f, feet - look.totem.height(), style);
            case "item_monitor" -> drawSprite(canvas, art.monitor, 0, x, feet, style, false);
            case "star_post" -> drawSprite(canvas, art.starpost, 0, x, feet, style, false);
            default -> canvas.fill(Math.round(x) - 4, Math.round(feet) - 8, 8, 8, 0xFFB6B6B6);
        }
    }

    private void drawFarmer(SceneCanvas canvas, SceneDraw tint, int cx) {
        if (looping) {
            SceneSprite ball = anim.pose(shell.art.farmer(shell.game.farmer));
            canvas.draw(ball, loopX() - cx, loopY(), tint.withFlipX(false));
            return;
        }
        float x = runner.x - cx, ground = feetY();
        canvas.fill(Math.round(x) - 9, Math.round(ground) - 2, 18, 4, 0x60000000);
        SceneSpriteSet set = shell.art.farmer(shell.game.farmer);
        SceneSprite pose = anim.pose(set);
        SceneDraw style = tint.withFlipX(runner.facingLeft);
        float feet = ground - runner.height;
        float originY = anim.id() == Anim.ROLL ? feet - 15 : feet - (pose.height() - pose.originY());
        if (shell.game.farmer.equals("tails")) {
            Anim.drawTails(canvas, shell.art.tailsTails, anim.id(), shell.ticks, x, originY, style);
        }
        canvas.draw(pose, x, originY, style);
    }

    static void drawSprite(SceneCanvas canvas, SceneSpriteSet set, int frame, float x, float feet, SceneDraw style,
            boolean flip) {
        if (set == null || frame >= set.frameCount()) {
            return;
        }
        SceneSprite sprite = set.frame(frame);
        float scale = style.scaleY();
        canvas.draw(sprite, x, feet - (sprite.height() - sprite.originY()) * scale, style.withFlipX(flip));
    }
}
