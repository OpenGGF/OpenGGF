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
import starpost.core.Game;
import starpost.core.Item;
import starpost.core.Kind;
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
    public static final int WIDTH = 5 * Art.BLOCK;
    private final int[] wall = {13, 45, 60, 60, 45};
    /** Feet range: the back of the field to the bottom of the screen. */
    private static final int DEPTH_MIN = FIELD_TOP + 4;
    private static final int DEPTH_MAX = 208;
    private static final int DASH_CHARGE_TICKS = 18;

    private final Shell shell;
    public final BeltRunner runner = new BeltRunner(DOOR_X + 40, 0);
    private float camera;
    private int chargeTicks;
    private boolean dashing;
    private long lastActionAt = -100;
    private int lastActionRow = -1;
    private int lastActionColumn = -1;
    private final Anim anim = new Anim();
    /** Set by the play screen: the farm's actors, and the action button offered to them first. */
    public java.util.function.IntFunction<List<Actor>> actors = view -> List.of();
    public java.util.function.BooleanSupplier interact = () -> false;

    /** What the farm asks of its screen after an update. */
    public enum Request {
        NONE,
        SLEEP,
        SHIP,
        TO_VALLEY
    }

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

    public float feetY() {
        return DEPTH_MIN + runner.depth;
    }

    public Request update(Controls in) {
        Game game = shell.game;
        Request request = Request.NONE;
        // Spin dash: hold the action button to charge, release to roll along the row tilling.
        boolean holdAct = shell.ctx.buttonDown(com.openggf.mods.scene.SceneButtons.B)
                || shell.ctx.keyDown(com.openggf.mods.scene.SceneKeys.X);
        if (holdAct && runner.height == 0 && !dashing) {
            if (++chargeTicks == DASH_CHARGE_TICKS) {
                shell.sfx(Sfx.SPINDASH);
            }
        } else {
            if (chargeTicks >= DASH_CHARGE_TICKS && !holdAct) {
                dashing = true;
                runner.speed = (runner.facingLeft ? -1 : 1) * 9;
                runner.rolling = true;
                shell.sfx(Sfx.DASH);
            } else if (chargeTicks > 0 && chargeTicks < DASH_CHARGE_TICKS && !holdAct) {
                request = act(game);
            }
            chargeTicks = 0;
        }
        boolean charged = chargeTicks >= DASH_CHARGE_TICKS;
        float maxDepth = DEPTH_MAX - DEPTH_MIN;
        if (dashing) {
            runner.speed -= Math.signum(runner.speed) * 0.125f;
            runner.x = Math.max(16, Math.min(WIDTH - 16, runner.x + runner.speed));
            tillUnderfoot(game);
            if (Math.abs(runner.speed) < 1.5f) {
                dashing = false;
                runner.rolling = false;
                runner.speed = 0;
            }
        } else if (!charged && !(chargeTicks > 0)) {
            if (runner.step(16, WIDTH - 16, maxDepth, in.left, in.right, in.up, in.down, in.jump, in.jumpHeld)) {
                shell.sfx(Sfx.JUMP);
            }
        }
        // The house door: walk to it and press up at the back of the field.
        if (Math.abs(runner.x - DOOR_X) < 12 && runner.depth < 6 && in.upPressed) {
            request = Request.SLEEP;
        }
        if (runner.x >= GATE_X + 8 && runner.speed > 0) {
            request = Request.TO_VALLEY;
        }
        camera += (clampCamera(runner.x - shell.width() / 2f + (runner.facingLeft ? -20 : 20)) - camera) * 0.15f;
        animate(charged);
        return request;
    }

    /** The action button: work the plot underfoot, or use the signpost. */
    private Request act(Game game) {
        if (interact.getAsBoolean()) {
            return Request.NONE;
        }
        if (Math.abs(runner.x - SIGNPOST_X) < 18 && runner.depth < 22) {
            return Request.SHIP;
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
        work(game, plot, rc[0], rc[1]);
        return Request.NONE;
    }

    private void work(Game game, Plot plot, int row, int column) {
        Item held = game.inventory.selectedId() == null ? null : game.item(game.inventory.selectedId());
        if (game.farm.ripe(game.catalog, plot)) {
            CropDef crop = game.farm.harvest(game.catalog, plot);
            Item produce = game.item(crop.produce());
            int leftover = game.inventory.add(produce, crop.yield());
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
        if (held != null && held.kind() == Kind.SEED) {
            CropDef crop = game.catalog.cropFromSeed(held.id());
            if (!plot.tilled || plot.crop != null) {
                shell.toast(plot.tilled ? "SOMETHING IS ALREADY GROWING" : "TILL THE SOIL FIRST");
                shell.sfx(Sfx.ERROR);
            } else if (!crop.grows(game.calendar.season())) {
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
                shell.sfx(Sfx.SPLASH);
                marked(row, column);
            }
            return;
        }
        if (!plot.tilled && spend(game, 2)) {
            plot.tilled = true;
            shell.sfx(Sfx.GROUND_SLIDE);
            marked(row, column);
        }
    }

    private void clearCover(Game game, Plot plot, Item held, int row, int column) {
        boolean fire = held != null && held.id().equals("fire_shield");
        switch (plot.cover) {
            case Plot.WEED -> {
                if (spend(game, 1)) {
                    plot.cover = Plot.GRASS;
                    game.inventory.add(game.item("fibre"), 1);
                    shell.sfx(fire ? Sfx.FIRE_SHIELD : Sfx.GRAB);
                    marked(row, column);
                }
            }
            case Plot.ROCK, Plot.STUMP -> {
                if (!fire) {
                    shell.toast(plot.cover == Plot.ROCK ? "A FIRE SHIELD WOULD BREAK THIS ROCK"
                            : "A FIRE SHIELD WOULD CLEAR THIS STUMP");
                    shell.sfx(Sfx.ERROR);
                } else if (spend(game, 4)) {
                    game.inventory.add(game.item(plot.cover == Plot.ROCK ? "marble_chip" : "palm_wood"),
                            plot.cover == Plot.ROCK ? 3 : 5);
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

    /** The spin dash tills every grass plot it rolls over in its row (1 Momentum each). */
    private void tillUnderfoot(Game game) {
        int[] rc = plotUnderfoot();
        if (rc == null) {
            return;
        }
        Plot plot = game.farm.plot(rc[0], rc[1]);
        if (plot != null && !plot.tilled && plot.cover != Plot.ROCK && plot.cover != Plot.STUMP && game.spend(1)) {
            plot.cover = Plot.GRASS;
            plot.tilled = true;
            marked(rc[0], rc[1]);
        }
    }

    private void marked(int row, int column) {
        lastActionAt = shell.ticks;
        lastActionRow = row;
        lastActionColumn = column;
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
        if (dashing || runner.rolling) {
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
        // Land still to clear: darker, behind a row of GHZ bridge-log fence posts.
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
        drawCursor(canvas, cx);
        List<float[]> order = new ArrayList<>();       // {screen y, kind, row, column}
        for (int row = 0; row < Farm.ROWS; row++) {
            for (int c = Math.max(0, (cx - FIELD_X) / 16 - 2); c < Farm.COLUMNS; c++) {
                if (FIELD_X + c * 16 - cx > w + 8) {
                    break;
                }
                Plot plot = game.farm.raw(row, c);
                if (plot.crop != null || plot.cover != Plot.GRASS) {
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
    }

    private void drawHouse(SceneCanvas canvas, Art.Seasonal look, int cx, SceneDraw tint) {
        // The farmhouse stands at the back of the field; its door (x 56-76 in the picture) is DOOR_X.
        SceneImage house = look.farmhouse;
        canvas.draw(house, DOOR_X - 66 - cx, FIELD_TOP + 4 - house.height(), tint);
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

    private void drawFarmer(SceneCanvas canvas, SceneDraw tint, int cx) {
        float x = runner.x - cx, ground = feetY();
        canvas.fill(Math.round(x) - 9, Math.round(ground) - 2, 18, 4, 0x60000000);
        SceneSpriteSet set = shell.art.farmer(shell.game.farmer);
        SceneSprite pose = anim.pose(set);
        SceneDraw style = tint.withFlipX(runner.facingLeft);
        float feet = ground - runner.height;
        if (anim.id() == Anim.ROLL) {
            canvas.draw(pose, x, feet - 15, style);
        } else {
            canvas.draw(pose, x, feet - (pose.height() - pose.originY()), style);
        }
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
