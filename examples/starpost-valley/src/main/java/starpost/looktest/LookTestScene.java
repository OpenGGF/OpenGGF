package starpost.looktest;

import com.openggf.mods.scene.DebuggableScene;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.scene.SceneMusicPlayer;
import com.openggf.mods.scene.SceneMusicPreparation;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;

/**
 * The look test (design doc §13): the same corner of Green Hill farmed in two perspectives, so
 * the choice between them is made from pictures and play rather than argument.
 * <ul>
 *   <li><b>Side view:</b> a row of Green Hill act 1's own blocks with their collision. Plots sit on
 *       the flat grass; tilling turns the grass to the zone's checkered soil.</li>
 *   <li><b>Belt view:</b> the same blocks as an upright back wall, and a field in front of it with
 *       four rows of plots that Sonic walks into and out of, side-on, as in a belt-scroller.</li>
 * </ul>
 * Controls: arrows move, Space/Z (pad A/C) jump, X (pad B) acts on the plot underfoot (till, plant,
 * water, harvest), Tab switches view, 1-4 choose the season, T cycles day/dusk/night, N advances
 * a day, Q picks the seed, M toggles Green Hill's music. Debug commands for the capture tool:
 * {@code view side|belt}, {@code season 0-3}, {@code time 0-2}, {@code x N}, {@code depth N} (a space or underscore separates the argument),
 * {@code grow}, {@code music on|off}, {@code help on|off}.
 */
public final class LookTestScene implements ModScene, DebuggableScene {
    private static final int SFX_JUMP = 0x62;      // S3K sfx_Jump
    private static final int SFX_SPRING = 0xB1;    // S3K sfx_Spring
    private static final int SFX_RING = 0x33;      // S3K sfx_RingRight
    private static final int SFX_TILL = 0x3C;      // S3K sfx_Roll: the spin scraping up the turf
    private static final int SFX_WATER = 0x39;     // S3K sfx_Splash
    private static final int SFX_PLANT = 0x5B;     // S3K sfx_Switch
    private static final int MUSIC_GHZ = 0x81;     // Sonic 1 Green Hill (Sonic1Music)
    private static final int GHZ_FRAMES = 60 * 150;

    private static final int ANIM_WALK = 0x00;
    private static final int ANIM_RUN = 0x01;
    private static final int ANIM_ROLL = 0x02;
    private static final int ANIM_PUSH = 0x04;
    private static final int ANIM_WAIT = 0x05;
    private static final int ANIM_DUCK = 0x08;
    private static final int ANIM_SPINDASH = 0x09;
    private static final int ANIM_SPRING = 0x10;

    private static final int DAY = 0;
    private static final int DUSK = 1;
    private static final int NIGHT = 2;

    private static String timeName(int time) {
        return time == DAY ? "DAY" : time == DUSK ? "DUSK" : "NIGHT";
    }

    /** Dusk and night as a multiply over the world (a palette fade's effect, approximately). */
    private static int timeTint(int time) {
        return time == DAY ? 0xFFFFFFFF : time == DUSK ? 0xFFFFC8A0 : 0xFF6D80C8;
    }

    /** Belt view geometry: the back wall's floor line and the field's rows on screen. */
    private static final int BELT_WALL_FLOOR = 136;
    private static final int BELT_ROWS = 4;
    private static final int BELT_ROW_TOP = 158;
    private static final int BELT_ROW_STEP = 18;
    private static final int BELT_PLOT_W = 16;
    private static final int BELT_PLOT_H = 11;
    private static final int BELT_FIELD_X0 = 480;
    private static final int BELT_FIELD_X1 = 1232;

    private SceneContext ctx;
    private ValleyArt art;
    private String failure;
    private SceneRomArt s1;
    private int season = Tone.SPRING;
    private int time = DAY;
    private boolean belt;
    private boolean help = true;
    private boolean musicWanted = true;
    private int day = 1;
    private int seed = CropArt.RADISH;
    private long ticks;
    private String toast = "";
    private long toastAt = -1000;

    // Side view.
    private Runner runner;
    private float camX;
    private float camY;
    private final List<Plot> sidePlots = new ArrayList<>();
    private final int[] springs = {768 + 20};
    private long springFiredAt = -100;
    private boolean looping;
    private float loopAngle;

    // Belt view.
    private BeltRunner walker;
    private float beltCam;
    private final Plot[][] beltPlots = new Plot[BELT_ROWS][(BELT_FIELD_X1 - BELT_FIELD_X0) / BELT_PLOT_W];

    // Sonic's animation (speed-driven, as Sonic_Animate).
    private int anim = ANIM_WAIT;
    private int animCursor;
    private int animTimer;

    // Music.
    private SceneMusicPreparation preparing;
    private SceneMusicPlayer player;

    /** One farm plot: grass, tilled, or planted with a crop at a growth stage. */
    static final class Plot {
        final int x;
        final int floor;
        boolean tilled;
        int crop = -1;
        int stage;
        boolean wet;
        long harvestedAt = -1000;

        Plot(int x, int floor) {
            this.x = x;
            this.floor = floor;
        }

        Plot plant(int crop, int stage) {
            tilled = true;
            this.crop = crop;
            this.stage = stage;
            return this;
        }
    }

    @Override
    public void enter(SceneContext ctx) {
        this.ctx = ctx;
        s1 = ctx.art().rom("s1");
        SceneRomArt s3k = ctx.art().rom();
        if (s1 == null || s3k == null) {
            failure = s1 == null ? "STARPOST VALLEY NEEDS YOUR SONIC 1 ROM" : "SONIC 3 & KNUCKLES ART IS UNAVAILABLE";
            return;
        }
        try {
            art = new ValleyArt(s1, s3k);
        } catch (RuntimeException e) {
            failure = "GREEN HILL COULD NOT BE LOADED";
            return;
        }
        runner = new Runner(256 + 120, ValleyArt.FLOOR);
        walker = new BeltRunner(560, 30);
        buildSidePlots();
        buildBeltPlots();
        startMusic();
    }

    private void buildSidePlots() {
        // Block 60 at column 2, the ledge of block 3 at column 3, block 60 again at column 4.
        for (int x = 512 + 16; x < 768 - 16; x += 16) {
            sidePlots.add(new Plot(x, ValleyArt.FLOOR));
        }
        for (int x = 768 + 48; x < 768 + 208; x += 16) {
            sidePlots.add(new Plot(x, 96).plant(CropArt.SUNFLOWER, 3));
        }
        for (int x = 1024 + 16; x < 1280 - 16; x += 16) {
            sidePlots.add(new Plot(x, ValleyArt.FLOOR));
        }
        int[][] preset = {
            {0, CropArt.RADISH, 3}, {1, CropArt.RADISH, 3}, {2, CropArt.RADISH, 2}, {3, CropArt.RADISH, 1},
            {4, CropArt.RADISH, 0}, {6, -1, 0}, {7, -1, 0},
            {24, CropArt.MELON, 3}, {25, CropArt.MELON, 2}, {26, CropArt.MELON, 3}, {28, CropArt.PUMPKIN, 3},
            {29, CropArt.PUMPKIN, 2}, {30, CropArt.PUMPKIN, 3}, {32, CropArt.SUNFLOWER, 2}, {33, CropArt.SUNFLOWER, 3},
        };
        for (int[] p : preset) {
            Plot plot = sidePlots.get(p[0]);
            if (p[1] < 0) {
                plot.tilled = true;
            } else {
                plot.plant(p[1], p[2]);
            }
        }
        sidePlots.get(2).wet = true;
        sidePlots.get(3).wet = true;
    }

    private void buildBeltPlots() {
        for (int row = 0; row < BELT_ROWS; row++) {
            for (int col = 0; col < beltPlots[row].length; col++) {
                Plot plot = new Plot(BELT_FIELD_X0 + col * BELT_PLOT_W, row);
                beltPlots[row][col] = plot;
                int bed = col / 8;              // beds of eight with a path between
                if (col % 8 == 7) {
                    continue;
                }
                switch (bed) {
                    case 0 -> plot.plant(CropArt.RADISH, Math.min(3, 1 + (row + col) % 3));
                    case 1 -> plot.plant(CropArt.SUNFLOWER, row < 2 ? 3 : 2);
                    case 2 -> plot.tilled = row % 2 == 0;
                    case 3 -> plot.plant(row < 2 ? CropArt.MELON : CropArt.PUMPKIN, 2 + (col + row) % 2);
                    default -> {
                    }
                }
                plot.wet = bed == 0 && row > 1;
            }
        }
    }

    // ---------------------------------------------------------------- update

    @Override
    public void update(SceneContext ctx) {
        ticks++;
        if (failure != null) {
            if (ctx.buttonPressed(SceneButtons.START)) {
                ctx.exitToGameTitle();
            }
            return;
        }
        updateMusic();
        if (ctx.keyPressed(SceneKeys.TAB)) {
            setView(!belt);
        }
        for (int s = 0; s < 4; s++) {
            if (ctx.keyPressed(SceneKeys.DIGIT_1 + s)) {
                setSeason(s);
            }
        }
        if (ctx.keyPressed(SceneKeys.T)) {
            time = (time + 1) % 3;
            say(timeName(time));
        }
        if (ctx.keyPressed(SceneKeys.N)) {
            grow();
        }
        if (ctx.keyPressed(SceneKeys.Q)) {
            seed = (seed + 1) % 4;
            say("SEED: " + CropArt.name(seed));
        }
        if (ctx.keyPressed(SceneKeys.M)) {
            musicWanted = !musicWanted;
            if (musicWanted) {
                startMusic();
            } else {
                stopMusic();
            }
        }
        if (ctx.keyPressed(SceneKeys.H)) {
            help = !help;
        }
        boolean left = ctx.buttonDown(SceneButtons.LEFT);
        boolean right = ctx.buttonDown(SceneButtons.RIGHT);
        boolean up = ctx.buttonDown(SceneButtons.UP);
        boolean down = ctx.buttonDown(SceneButtons.DOWN);
        // The keyboard's default pad mapping binds only A (Space), so Z jumps and X farms too.
        boolean jumpPressed = ctx.buttonPressed(SceneButtons.A | SceneButtons.C) || ctx.keyPressed(SceneKeys.Z);
        boolean jumpHeld = ctx.buttonDown(SceneButtons.A | SceneButtons.C) || ctx.keyDown(SceneKeys.Z);
        boolean act = ctx.buttonPressed(SceneButtons.B) || ctx.keyPressed(SceneKeys.X);
        if (belt) {
            updateBelt(left, right, up, down, jumpPressed, jumpHeld, act);
        } else {
            updateSide(left, right, down, jumpPressed, jumpHeld, act);
        }
    }

    private void updateSide(boolean left, boolean right, boolean down, boolean jumpPressed, boolean jumpHeld,
            boolean act) {
        if (looping) {
            stepLoop();
        } else {
            Runner.Ground ground = new Runner.Ground() {
                @Override public boolean solid(int x, int y) { return art.solid(x, y); }
                @Override public int floorBelow(int x, int fromY) { return art.floorBelow(x, fromY); }
                @Override public int left() { return 0; }
                @Override public int right() { return art.worldWidth(); }
            };
            if (act && runner.onGround && !runner.rolling) {
                Plot plot = sidePlotUnder(runner.x, runner.y);
                if (plot != null) {
                    farm(plot);
                    act = false;
                }
            }
            boolean jumped = runner.step(ground, left, right, down, jumpPressed, jumpHeld, (int) ticks);
            if (jumped) {
                ctx.audio().playSfx(SFX_JUMP);
            }
            for (int s : springs) {
                if (Math.abs(runner.x - s) < 12 && runner.y >= ValleyArt.FLOOR - 2 && (runner.onGround || runner.ySpeed > 0)) {
                    runner.spring(10);
                    springFiredAt = ticks;
                    ctx.audio().playSfx(SFX_SPRING);
                }
            }
            maybeEnterLoop();
        }
        animateSide();
        float targetX = runner.x - ctx.width() / 2f + (runner.facingLeft ? -24 : 24);
        camX += (targetX - camX) * 0.18f;
        camX = Math.max(0, Math.min(art.worldWidth() - ctx.width(), camX));
        float targetY = runner.y - 150;
        camY += (targetY - camY) * 0.15f;
        camY = Math.max(-64, Math.min(ValleyArt.BLOCK - ctx.height(), camY));
    }

    /**
     * The loop (block 53, column 6). Its primary-path collision is only the entry ramp, the right
     * inner wall and the top (Sonic 1 swaps paths to run the rest), so at speed Sonic rolls round
     * it on a scripted circle as a ball and leaves beyond its right foot. Too slow, and the inner
     * wall stops him, as a real loop would.
     */
    private static final float LOOP_X = 6 * 256;
    private static final float LOOP_CX = LOOP_X + 126;
    private static final float LOOP_CY = 111;
    private static final float LOOP_R = 60;     // the ball's centre, inside the 75-pixel surface

    private void maybeEnterLoop() {
        if (runner.onGround && runner.speed >= 4 && runner.x >= LOOP_X + 96 && runner.x <= LOOP_X + 124) {
            looping = true;
            loopAngle = -0.35f;
        }
    }

    private void stepLoop() {
        loopAngle += Math.max(0.07f, runner.speed / LOOP_R);
        if (loopAngle >= (float) (Math.PI * 2) - 0.1f) {
            looping = false;
            runner.x = LOOP_X + 184;
            runner.y = ValleyArt.FLOOR;
            runner.onGround = true;
            return;
        }
        runner.x = LOOP_CX + (float) Math.sin(loopAngle) * LOOP_R;
        runner.y = LOOP_CY + (float) Math.cos(loopAngle) * LOOP_R;  // the ball's centre while looping
    }

    private void updateBelt(boolean left, boolean right, boolean up, boolean down, boolean jumpPressed,
            boolean jumpHeld, boolean act) {
        if (act && walker.height == 0) {
            Plot plot = beltPlotUnder();
            if (plot != null) {
                farm(plot);
            }
        }
        boolean jumped = walker.step(ValleyArt.BLOCK + 16, art.worldWidth() - 16, beltDepth(), left, right, up, down,
                jumpPressed, jumpHeld);
        if (jumped) {
            ctx.audio().playSfx(SFX_JUMP);
        }
        animateBelt();
        float target = walker.x - ctx.width() / 2f;
        beltCam += (target - beltCam) * 0.18f;
        beltCam = Math.max(0, Math.min(art.worldWidth() - ctx.width(), beltCam));
    }

    private float beltDepth() {
        return (BELT_ROWS - 1) * BELT_ROW_STEP + 14;
    }

    private void farm(Plot plot) {
        if (!plot.tilled) {
            plot.tilled = true;
            ctx.audio().playSfx(SFX_TILL);
            say("TILLED");
        } else if (plot.crop < 0) {
            plot.crop = seed;
            plot.stage = 0;
            ctx.audio().playSfx(SFX_PLANT);
            say("PLANTED " + CropArt.name(seed));
        } else if (plot.stage >= CropArt.STAGES - 1) {
            plot.crop = -1;
            plot.stage = 0;
            plot.harvestedAt = ticks;
            ctx.audio().playSfx(SFX_RING);
            say("HARVESTED");
        } else if (!plot.wet) {
            plot.wet = true;
            ctx.audio().playSfx(SFX_WATER);
            say("WATERED");
        }
    }

    private void grow() {
        day++;
        for (Plot plot : sidePlots) {
            growPlot(plot);
        }
        for (Plot[] row : beltPlots) {
            for (Plot plot : row) {
                growPlot(plot);
            }
        }
        say(Tone.seasonName(season) + " " + day);
    }

    private static void growPlot(Plot plot) {
        if (plot.crop >= 0 && plot.wet && plot.stage < CropArt.STAGES - 1) {
            plot.stage++;
        }
        plot.wet = false;
    }

    private Plot sidePlotUnder(float x, float y) {
        for (Plot plot : sidePlots) {
            if (x >= plot.x && x < plot.x + 16 && Math.abs(y - plot.floor) < 4) {
                return plot;
            }
        }
        return null;
    }

    private Plot beltPlotUnder() {
        int col = (int) Math.floor((walker.x - BELT_FIELD_X0) / BELT_PLOT_W);
        int row = Math.round((walker.depth - 7) / BELT_ROW_STEP);
        if (col < 0 || col >= beltPlots[0].length || row < 0 || row >= BELT_ROWS) {
            return null;
        }
        return beltPlots[row][col];
    }

    // ------------------------------------------------------------- animation

    private void animateSide() {
        int next;
        int delay;
        float speed = Math.abs(runner.speed);
        if (looping || runner.rolling || !runner.onGround && !runner.sprung) {
            next = ANIM_ROLL;
            delay = Math.max(0, 4 - (int) Math.max(speed, looping ? 6 : 0));
        } else if (runner.sprung) {
            next = ANIM_SPRING;
            delay = 2;
        } else if (runner.dashing) {
            next = ANIM_SPINDASH;
            delay = 0;
        } else if (runner.ducking) {
            next = ANIM_DUCK;
            delay = 6;
        } else if (runner.pushing) {
            next = ANIM_PUSH;
            delay = 8;
        } else if (speed > 0.05f) {
            next = speed >= Runner.TOP ? ANIM_RUN : ANIM_WALK;
            delay = Math.max(0, 8 - (int) speed);
        } else {
            next = ANIM_WAIT;
            delay = Math.max(0, art.sonic.animationDelay(ANIM_WAIT));
        }
        advanceAnimation(next, delay);
    }

    private void animateBelt() {
        float speed = Math.max(Math.abs(walker.speed), Math.abs(walker.depthSpeed) * 2);
        if (walker.rolling) {
            advanceAnimation(ANIM_ROLL, 1);
        } else if (speed > 0.05f) {
            advanceAnimation(speed >= Runner.TOP ? ANIM_RUN : ANIM_WALK, Math.max(0, 8 - (int) speed));
        } else {
            advanceAnimation(ANIM_WAIT, Math.max(0, art.sonic.animationDelay(ANIM_WAIT)));
        }
    }

    private void advanceAnimation(int next, int delay) {
        if (next != anim) {
            anim = next;
            animCursor = 0;
            animTimer = delay;
            return;
        }
        if (--animTimer < 0) {
            animTimer = delay;
            animCursor++;
        }
    }

    private SceneSprite sonicPose() {
        int[] frames = art.sonic.animationFrames(anim);
        if (frames == null || frames.length == 0) {
            return art.sonic.frame(0);
        }
        return art.sonic.frame(frames[animCursor % frames.length]);
    }

    // ------------------------------------------------------------------ draw

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        if (failure != null) {
            canvas.clear(0x000000);
            centred(canvas, failure, 100, 0xFFFFFFFF);
            centred(canvas, "START: THE STOCK TITLE SCREEN", 124, 0xFFB6B6B6);
            return;
        }
        ValleyArt.Seasonal look = art.season(season);
        SceneDraw tint = SceneDraw.plain().withTint(timeTint(time));
        if (belt) {
            drawBelt(canvas, look, tint);
        } else {
            drawSide(canvas, look, tint);
        }
        drawHud(canvas);
    }

    private void drawSide(SceneCanvas canvas, ValleyArt.Seasonal look, SceneDraw tint) {
        int w = canvas.width(), h = canvas.height();
        canvas.clear(0x2449DB);
        // Green Hill's background, its own parallax: the stock act shows rows near 0x100 at this height.
        drawSky(canvas, look, 0, 0, w, h, Math.max(0, Math.min(32, Math.round(8 + camY * 0.1f))), camX);
        int cx = Math.round(camX), cy = Math.round(camY);
        int first = Math.max(0, cx / ValleyArt.BLOCK);
        int last = Math.min(art.sideRow.length - 1, (cx + w) / ValleyArt.BLOCK);
        for (int column = first; column <= last; column++) {
            drawSideBlock(canvas, look, tint, column, cx, cy);
        }
        // Plots: tilled soil replaces the grass lip; crops stand on the floor.
        for (Plot plot : sidePlots) {
            int sx = plot.x - cx, sy = plot.floor - cy;
            if (sx < -32 || sx > w + 16) {
                continue;
            }
            if (plot.tilled) {
                SceneImage soil = plot.wet ? look.tilledWet : look.tilledDry;
                canvas.drawRegion(soil, plot.x & 16, 0, 16, art.lipDepth, sx, sy - 1, 16, art.lipDepth, tint);
            }
            if (plot.crop >= 0) {
                SceneImage crop = look.crops.stage(plot.crop, plot.stage);
                canvas.draw(crop, sx + (16 - crop.width()) / 2f, sy - crop.height() + 1, tint);
            }
            drawHarvestSparkle(canvas, plot, sx + 8, sy - 12);
        }
        // Objects: the valley's Star Post at home, the signpost shipping bin, a monitor, springs.
        drawSprite(canvas, art.starpost, 0, 256 + 64 - cx, sideFloor(256 + 64) - cy, tint, false);
        drawSprite(canvas, art.signpost, (int) (ticks / 8 % 4) == 0 ? 0 : 0, 256 + 176 - cx, sideFloor(256 + 176) - cy, tint, false);
        drawSprite(canvas, art.monitor, 0, 1280 + 40 - cx, sideFloor(1280 + 40) - cy, tint, false);
        for (int s : springs) {
            int frame = ticks - springFiredAt < 12 ? 1 : 0;
            drawSprite(canvas, art.spring, frame, s - cx, sideFloor(s) - cy, tint, false);
        }
        drawAnimals(canvas, tint, cx, cy);
        drawSonicSide(canvas, tint, cx, cy);
    }

    private void drawSky(SceneCanvas canvas, ValleyArt.Seasonal look, int x, int y, int w, int h, int top, float scroll) {
        canvas.drawBackdrop(look.backdrop(time), x, y, w, h, top, scroll, ticks);
    }

    private int sideFloor(int x) {
        return art.floorBelow(x, 0);
    }

    private void drawSideBlock(SceneCanvas canvas, ValleyArt.Seasonal look, SceneDraw tint, int column, int cx, int cy) {
        SceneImage block = look.block(art.sideRow[column]);
        if (block == null) {
            return;
        }
        int bx = column * ValleyArt.BLOCK;
        // Draw in vertical slices so a tilled plot's column leaves out its grass (the soil
        // takes its place); everything else is the block exactly as the ROM draws it.
        int run = 0;
        for (int x = 0; x <= ValleyArt.BLOCK; x += 16) {
            Plot plot = x < ValleyArt.BLOCK ? tilledPlotAt(bx + x) : null;
            if (plot == null && x < ValleyArt.BLOCK) {
                continue;
            }
            if (x > run) {
                canvas.drawRegion(block, run, 0, x - run, ValleyArt.BLOCK, bx + run - cx, -cy, x - run, ValleyArt.BLOCK, tint);
            }
            if (plot != null) {
                int top = plot.floor + art.lipDepth;
                canvas.drawRegion(block, x, top, 16, ValleyArt.BLOCK - top, bx + x - cx, top - cy, 16,
                        ValleyArt.BLOCK - top, tint);
            }
            run = x + 16;
        }
        // Below the block row: more soil, so a camera looking down never sees an edge.
        canvas.drawRegion(block, 0, ValleyArt.BLOCK - 32, ValleyArt.BLOCK, 32, bx - cx, ValleyArt.BLOCK - cy,
                ValleyArt.BLOCK, 32, tint);
    }

    private Plot tilledPlotAt(int worldX) {
        for (Plot plot : sidePlots) {
            if (plot.tilled && plot.x == worldX) {
                return plot;
            }
        }
        return null;
    }

    private void drawAnimals(SceneCanvas canvas, SceneDraw tint, int cx, int cy) {
        // A Cucky pecking beside the first field, a Pocky hopping past the second, a Motobug at
        // the meadow's edge (the pest), and a Flicky circling the waterfall.
        float cuckyX = 512 + 40 + (float) Math.sin(ticks / 90.0) * 30;
        drawSprite(canvas, art.cucky, (int) (ticks / 10 % 2), cuckyX - cx, ValleyArt.FLOOR - cy, tint, Math.cos(ticks / 90.0) < 0);
        float hop = Math.abs((float) Math.sin(ticks / 9.0)) * 10;
        float pockyX = 1024 + 120 + (float) Math.sin(ticks / 160.0) * 100;
        drawSprite(canvas, art.pocky, hop > 4 ? 1 : 0, pockyX - cx, ValleyArt.FLOOR - hop - cy, tint, Math.cos(ticks / 160.0) > 0);
        float motoX = 1280 + 150 + (float) Math.sin(ticks / 200.0) * 70;
        drawSprite(canvas, art.motobug, (int) (ticks / 8 % 2), motoX - cx, sideFloor(Math.round(motoX)) - cy, tint, Math.cos(ticks / 200.0) > 0);
        float fx = 128 + (float) Math.cos(ticks / 70.0) * 70, fy = 110 + (float) Math.sin(ticks / 35.0) * 20;
        drawSprite(canvas, art.flicky, (int) (ticks / 4 % 2), fx - cx, fy - cy, tint, Math.sin(ticks / 70.0) > 0);
    }

    private void drawSonicSide(SceneCanvas canvas, SceneDraw tint, int cx, int cy) {
        SceneSprite pose = sonicPose();
        SceneDraw style = tint.withFlipX(runner.facingLeft);
        float feet = runner.y - cy;
        if (looping) {
            canvas.draw(pose, runner.x - cx, feet, style);
        } else if (anim == ANIM_ROLL) {
            canvas.draw(pose, runner.x - cx, feet - 15, style);
        } else {
            canvas.draw(pose, runner.x - cx, feet - (pose.height() - pose.originY()), style);
        }
    }

    private void drawBelt(SceneCanvas canvas, ValleyArt.Seasonal look, SceneDraw tint) {
        int w = canvas.width(), h = canvas.height();
        canvas.clear(0x2449DB);
        drawSky(canvas, look, 0, 0, w, BELT_WALL_FLOOR + 8, 40, beltCam);
        int cx = Math.round(beltCam);
        // The back wall: Green Hill's blocks standing upright behind the field, at the ROM's own
        // size, with their floor line on BELT_WALL_FLOOR.
        for (int column = 0; column < art.sideRow.length; column++) {
            int x = column * ValleyArt.BLOCK - cx;
            if (x > w || x + ValleyArt.BLOCK < 0) {
                continue;
            }
            SceneImage block = look.block(art.sideRow[column]);
            if (block != null) {
                int rows = ValleyArt.FLOOR + 8;
                canvas.drawRegion(block, 0, 0, ValleyArt.BLOCK, rows, x, BELT_WALL_FLOOR - ValleyArt.FLOOR,
                        ValleyArt.BLOCK, rows, tint);
            }
        }
        // The field: grass seen from above, in rows that move with the camera.
        int fieldTop = BELT_WALL_FLOOR + 8;
        for (int y = fieldTop; y < h; y += 64) {
            for (int x = -Math.floorMod(cx, 64); x < w; x += 64) {
                canvas.drawRegion(look.field, 0, 0, 64, Math.min(64, h - y), x, y, 64, Math.min(64, h - y), tint);
            }
        }
        // Depth: the far edge of the field sits in the wall's shadow and fades toward the viewer.
        for (int band = 0; band < 6; band++) {
            canvas.fill(0, fieldTop + band * 4, w, 4, (0x48 - band * 0x0C) << 24);
        }
        // Gather everything that stands in the field and draw it back to front.
        List<float[]> order = new ArrayList<>();   // {screenY, kind, index, row}
        for (int row = 0; row < BELT_ROWS; row++) {
            int rowY = BELT_ROW_TOP + row * BELT_ROW_STEP;
            for (int col = 0; col < beltPlots[row].length; col++) {
                Plot plot = beltPlots[row][col];
                int sx = plot.x - cx;
                if (sx < -24 || sx > w + 8) {
                    continue;
                }
                if (plot.tilled) {
                    SceneImage soil = plot.wet ? look.tilledWet : look.tilledDry;
                    canvas.drawRegion(soil, 0, 0, 16, 16, sx, rowY - BELT_PLOT_H, BELT_PLOT_W, BELT_PLOT_H, tint);
                }
                if (plot.crop >= 0 || ticks - plot.harvestedAt < 30) {
                    order.add(new float[] {rowY - 3, 0, col, row});
                }
            }
        }
        float sonicY = BELT_ROW_TOP - 7 + walker.depth;
        order.add(new float[] {sonicY, 1, 0, 0});
        // Field props: palms and plants along the back, the Star Post and signpost by the path.
        for (int i = 0; i < 6; i++) {
            order.add(new float[] {fieldTop + 4 + (i % 2) * 6, 2, i, 0});
        }
        order.sort((a, b) -> Float.compare(a[0], b[0]));
        for (float[] item : order) {
            switch ((int) item[1]) {
                case 0 -> {
                    Plot plot = beltPlots[(int) item[3]][(int) item[2]];
                    float sx = plot.x - cx;
                    if (plot.crop >= 0) {
                        SceneImage crop = look.crops.stage(plot.crop, plot.stage);
                        canvas.draw(crop, sx + (16 - crop.width()) / 2f, item[0] - crop.height() + 1, tint);
                    }
                    drawHarvestSparkle(canvas, plot, sx + 8, item[0] - 12);
                }
                case 1 -> drawSonicBelt(canvas, tint, cx, sonicY);
                default -> drawBeltProp(canvas, look, tint, (int) item[2], cx, item[0]);
            }
        }
    }

    private void drawBeltProp(SceneCanvas canvas, ValleyArt.Seasonal look, SceneDraw tint, int index, int cx, float y) {
        float[] xs = {300, 420, 1300, 1420, 1600, 1760};
        float x = xs[index] - cx;
        switch (index) {
            case 0 -> drawSprite(canvas, art.starpost, 0, x, y, tint, false);
            case 1 -> drawSprite(canvas, art.signpost, 0, x, y, tint, false);
            case 2, 4 -> canvas.draw(look.palm, x - look.palm.width() / 2f, y - look.palm.height(), tint);
            case 3 -> canvas.draw(look.totem, x - look.totem.width() / 2f, y - look.totem.height(), tint);
            default -> canvas.draw(look.plant, x - look.plant.width() / 2f, y - look.plant.height(), tint);
        }
    }

    private void drawSonicBelt(SceneCanvas canvas, SceneDraw tint, int cx, float groundY) {
        float x = walker.x - cx;
        canvas.fill(Math.round(x) - 9, Math.round(groundY) - 2, 18, 4, 0x60000000);
        SceneSprite pose = sonicPose();
        SceneDraw style = tint.withFlipX(walker.facingLeft);
        float feet = groundY - walker.height;
        if (walker.rolling) {
            canvas.draw(pose, x, feet - 15, style);
        } else {
            canvas.draw(pose, x, feet - (pose.height() - pose.originY()), style);
        }
    }

    private void drawHarvestSparkle(SceneCanvas canvas, Plot plot, float x, float y) {
        long age = ticks - plot.harvestedAt;
        if (age >= 0 && age < 24) {
            canvas.draw(art.ring.frame(4 + (int) (age / 6)), x, y - age / 2f, SceneDraw.plain());
        }
    }

    private static void drawSprite(SceneCanvas canvas, SceneSpriteSet set, int frame, float x, float feet,
            SceneDraw style, boolean flip) {
        if (set == null || frame >= set.frameCount()) {
            return;
        }
        SceneSprite sprite = set.frame(frame);
        canvas.draw(sprite, x, feet - (sprite.height() - sprite.originY()), style.withFlipX(flip));
    }

    private void drawHud(SceneCanvas canvas) {
        int w = canvas.width();
        int yellow = 0xFFFFDB00, white = 0xFFFFFFFF;
        canvas.text(Tone.seasonName(season) + " " + day, 8, 8, yellow);
        canvas.text(timeName(time), 8, 20, white);
        String view = belt ? "BELT VIEW" : "SIDE VIEW";
        canvas.text(view, w - 8 - canvas.textWidth(view), 8, yellow);
        String seedName = "SEED " + CropArt.name(seed);
        canvas.text(seedName, w - 8 - canvas.textWidth(seedName), 20, white);
        if (ticks - toastAt < 90) {
            centred(canvas, toast, 36, white);
        }
        if (help) {
            canvas.fill(0, canvas.height() - 26, w, 26, 0xA0000000);
            centred(canvas, "ARROWS MOVE  SPACE JUMP  X FARM  TAB VIEW  1-4 SEASON", canvas.height() - 24, white);
            centred(canvas, "T TIME  N NEXT DAY  Q SEED  M MUSIC  H HIDE", canvas.height() - 12, white);
        }
        if (ticks < 150) {
            int alpha = (int) Math.min(255, Math.max(0, (150 - ticks) * 4));
            canvas.fill(0, 88, w, 30, alpha * 2 / 3 << 24);
            centred(canvas, "STARPOST VALLEY  -  LOOK TEST", 92, alpha << 24 | 0xFFDB00);
            centred(canvas, "SIDE VIEW OR BELT VIEW?", 104, alpha << 24 | 0xFFFFFF);
        }
    }

    private void centred(SceneCanvas canvas, String text, int y, int argb) {
        canvas.text(text, (canvas.width() - canvas.textWidth(text)) / 2, y, argb);
    }

    // ----------------------------------------------------------------- music

    private void startMusic() {
        if (player != null || preparing != null) {
            return;
        }
        try {
            preparing = ctx.music().prepareAsync("s1", MUSIC_GHZ, GHZ_FRAMES);
        } catch (RuntimeException e) {
            preparing = null;
        }
    }

    private void updateMusic() {
        if (preparing != null) {
            switch (preparing.state()) {
                case READY -> {
                    if (musicWanted) {
                        player = ctx.music().start(preparing.prepared(), 0, 0, false, 0);
                    }
                    preparing = null;
                }
                case FAILED -> preparing = null;
                default -> {
                }
            }
        }
        if (player != null && player.finished() && musicWanted) {
            player.stop();
            player = null;
            startMusic();
        }
    }

    private void stopMusic() {
        if (preparing != null) {
            preparing.cancel();
            preparing = null;
        }
        if (player != null) {
            player.stop();
            player = null;
        }
    }

    // ----------------------------------------------------------------- misc

    private void setView(boolean toBelt) {
        belt = toBelt;
        anim = -1;
        say(belt ? "BELT VIEW" : "SIDE VIEW");
    }

    private void setSeason(int value) {
        season = value;
        say(Tone.seasonName(season));
    }

    private void say(String text) {
        toast = text;
        toastAt = ticks;
    }

    @Override
    public void exit(SceneContext ctx) {
        stopMusic();
    }

    @Override
    public boolean debugJump(String command) {
        String[] parts = command.trim().split("[\\s_]+");
        try {
            switch (parts[0]) {
                case "view" -> setView(parts[1].equals("belt"));
                case "season" -> setSeason(Integer.parseInt(parts[1]) & 3);
                case "time" -> time = Integer.parseInt(parts[1]) % 3;
                case "x" -> {
                    float x = Float.parseFloat(parts[1]);
                    if (belt) {
                        walker.x = x;
                        beltCam = x - 200;
                    } else {
                        runner.x = x;
                        runner.y = art.floorBelow(Math.round(x), 0);
                        camX = x - 200;
                        camY = runner.y - 150;
                    }
                }
                case "depth" -> walker.depth = Float.parseFloat(parts[1]);
                case "grow" -> grow();
                case "music" -> {
                    musicWanted = parts[1].equals("on");
                    if (musicWanted) {
                        startMusic();
                    } else {
                        stopMusic();
                    }
                }
                case "help" -> help = parts[1].equals("on");
                default -> {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
