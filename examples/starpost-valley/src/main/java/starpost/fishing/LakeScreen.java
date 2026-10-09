package starpost.fishing;

import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import starpost.art.Anim;
import starpost.art.Art;
import starpost.core.Game;
import starpost.core.Inventory;
import starpost.scene.DayEndScreen;
import starpost.scene.InventoryMenu;
import starpost.scene.Music;
import starpost.scene.PlayScreen;
import starpost.scene.Screen;
import starpost.scene.Shell;
import starpost.ui.Text;
import starpost.valley.Runner;

/**
 * Waterfall Lake, behind the valley's {@code lake} doorway: Green Hill's own water in side view.
 * The shore is block 1, Barnaby's jetty is block 51 (the zone's log bridge over its pool) and the
 * far bank is block 52's waterfall, all with their collision; the pool spans the water. The water
 * shimmers with Sonic 1's Green Hill palette cycle (PalCycle_GHZ: Pal_GHZCyc's four steps into
 * palette line 3, colours 8-11, every 6 frames). The farmer walks on the ported controller; with
 * the rod, holding the action button winds up a cast and letting go throws it: the longer the
 * cast, the deeper the water, where the badniks lurk. Walk off the left edge to go back.
 */
public final class LakeScreen implements Screen {
    private static final int SHORE_END = Art.BLOCK;
    private static final int JETTY_END = 2 * Art.BLOCK;
    private static final int WIDTH = 3 * Art.BLOCK;
    private static final int CAM_Y = 32;
    private static final int BARNABY_X = JETTY_END - 30;
    private static final int PAL_GHZ_CYCLE = 0x1B7E;     // Pal_GHZCyc (sonic.lst)
    private static final int CYCLE_FRAMES = 6;
    private static final int CHARGE_PERIOD = 80;
    private static final int FALL_TICKS = 50;
    private static final int SFX_SPLASH = 0x39;
    private static final int SFX_CAST = 0x3C;
    private static final int SFX_PLOP = 0x6C;
    private static final int SFX_STRIKE = 0x4A;
    private static final String LAKE_BRIDGE = "lake_bridge";

    private final Shell shell;
    private final PlayScreen play;
    private final FishingSystem sys;
    private final Runner runner;
    private final Ground ground = new Ground();
    private final Anim anim = new Anim();
    private final Line line = new Line();
    /** The pool's surface: the first water row under block 51's log. */
    private final int poolY;
    private float camX;
    private boolean charging;
    private int charge;
    private String hooked;
    private String forced;
    private long biteAt = -1000;
    private long splashAt = -1000;
    private float splashX;
    private long landedAt = -1000;
    private String landedId;
    private boolean landedFreed;
    private int fallen;
    /** The water's four cycle steps: block 52 whole, and the pool rows of block 51. */
    private final SceneImage[] fall = new SceneImage[4];
    private final SceneImage[] pool = new SceneImage[4];
    private int cycledSeason = -1;
    /** The pool's darkest water colour in this season's tone (Pal_GHZ line 3, colour 8). */
    private int poolBed = 0xFF244992;
    private int lastLight = -1;
    /** Barnaby on his jetty now (his schedule, checked in update). */
    private boolean barnaby;
    /** The Capsule's Reef chamber rebuilt the lake bridge: Green Hill's log bridge out to the falls. */
    private boolean bridge;

    LakeScreen(Shell shell, PlayScreen play, FishingSystem sys) {
        this.shell = shell;
        this.play = play;
        this.sys = sys;
        poolY = poolTop(shell.art.kit.blockImage(51));
        runner = new Runner(40, ground.floorBelow(40, 0));
        snapCamera();
    }

    /** The first row under block 51's log where its water starts (a row a quarter or more drawn). */
    private static int poolTop(SceneImage bridge) {
        for (int y = Art.FLOOR - 50; y < Art.BLOCK - 8; y++) {
            int drawn = 0;
            for (int x = 0; x < Art.BLOCK; x++) {
                if (bridge.pixel(x, y) >>> 24 != 0) {
                    drawn++;
                }
            }
            if (drawn >= Art.BLOCK / 4) {
                return y;
            }
        }
        return Art.FLOOR - 16;
    }

    PlayScreen play() {
        return play;
    }

    // ------------------------------------------------------------------ the ground

    /** The shore and the jetty's log stand; the pool and the waterfall do not. */
    private final class Ground implements Runner.Ground {
        @Override
        public boolean solid(int x, int y) {
            if (x < 0 || x >= WIDTH || y < 0) {
                return false;
            }
            int lx = x % Art.BLOCK;
            if (x < SHORE_END) {
                return shell.art.solid(1, lx, y);
            }
            boolean deck = x < JETTY_END || bridge && x < WIDTH - 16;
            return deck && y < poolY && shell.art.solid(51, lx, y);
        }

        @Override
        public int floorBelow(int x, int fromY) {
            for (int y = Math.max(0, fromY); y < Art.BLOCK; y++) {
                if (solid(x, y)) {
                    return y;
                }
            }
            return Art.BLOCK * 2;
        }

        @Override
        public int left() {
            return 0;
        }

        @Override
        public int right() {
            return bridge ? WIDTH - 16 : WIDTH;
        }
    }

    // ------------------------------------------------------------------ the day

    @Override
    public void enter(Shell shell) {
        Game game = shell.game;
        int light = game.calendar.light();
        shell.music.want("s1", game.raining ? Music.S1_LZ : light == 2 ? Music.S1_SLZ : Music.S1_GHZ);
        lastLight = light;
    }

    @Override
    public void update(Shell shell) {
        Game game = shell.game;
        cycle(game.calendar.season());
        barnaby = barnabyHere(game);
        bridge = game.flags.contains(LAKE_BRIDGE);
        hotbar(shell);
        if (shell.in.menu && !charging && !line.out()) {
            shell.push(new InventoryMenu());
            return;
        }
        if (game.calendar.tick() && game.calendar.light() != lastLight) {
            enter(shell);
        }
        if (game.calendar.overtime()) {
            shell.toast(game.farmer.toUpperCase() + " PASSED OUT...");
            shell.go(new DayEndScreen(true));
            return;
        }
        if (fallen > 0) {
            if (--fallen == 0) {
                runner.x = 40;
                runner.y = ground.floorBelow(40, 0);
                runner.speed = 0;
                runner.ySpeed = 0;
                runner.onGround = true;
                runner.facingLeft = false;
                snapCamera();
            }
            return;
        }
        fish(shell, game);
        boolean free = !line.out() && !charging;
        if (runner.step(ground, free && shell.in.left, free && shell.in.right, free && shell.in.down,
                free && shell.in.jump, free && shell.in.jumpHeld, (int) shell.ticks)) {
            shell.sfx(starpost.scene.Sfx.JUMP);
        }
        if (runner.x < 10 && runner.speed <= 0 && free) {
            shell.go(play);
            return;
        }
        if (runner.y > poolY + 6 && runner.x >= SHORE_END) {
            fallen = FALL_TICKS;
            splashX = runner.x;
            splashAt = shell.ticks;
            line.reelIn();
            shell.sfx(SFX_SPLASH);
            shell.toast(game.farmer.toUpperCase() + " CAN'T SWIM!");
            return;
        }
        float look = line.out() ? (runner.x + line.bobberX()) / 2 : runner.x + (runner.facingLeft ? -40 : 60);
        float target = look - shell.width() / 2f;
        camX += (clampX(target) - camX) * 0.12f;
        animate();
    }

    /** The rod: wind up, cast, wait, strike. */
    private void fish(Shell shell, Game game) {
        boolean holdAct = shell.ctx.buttonDown(SceneButtons.B) || shell.ctx.keyDown(SceneKeys.X);
        if (line.out()) {
            if (shell.in.act) {
                if (line.state == Line.BITE && hooked != null && line.strike()) {
                    shell.sfx(SFX_STRIKE);
                    strike(shell, hooked);
                } else if (line.state != Line.FIGHT) {
                    line.reelIn();
                }
                return;
            }
            if (line.state != Line.FIGHT && (shell.in.left || shell.in.right || shell.in.jump)) {
                line.reelIn();
                return;
            }
            step(shell, game);
        } else if (charging) {
            if (holdAct) {
                charge++;
            } else {
                charging = false;
                cast(shell, game);
            }
        } else if (shell.in.act && runner.onGround) {
            if (Fishing.holdingRod(game)) {
                charging = true;
                charge = 0;
            } else {
                shell.toast(game.inventory.total("fishing_rod") > 0 ? "HOLD THE FISHING ROD TO CAST"
                        : "TAILS COULD BUILD YOU A ROD");
            }
        }
    }

    /** The wind-up's power, 0-1 and back. */
    private float power() {
        float p = (charge % CHARGE_PERIOD) / (CHARGE_PERIOD / 2f);
        return p > 1 ? 2 - p : p;
    }

    private void cast(Shell shell, Game game) {
        float dir = runner.facingLeft ? -1 : 1;
        float tipX = runner.x + dir * 19, tipY = runner.y - 33;
        float tx = tipX + dir * (30 + power() * 230);
        if (tx < SHORE_END + 8) {
            shell.toast("THE LINE LANDS ON THE BANK");
            return;
        }
        tx = Math.min(WIDTH - 16, tx);
        int depth = Math.round((tx - SHORE_END) * 100 / (WIDTH - 16 - SHORE_END));
        line.cast(tipX, tipY, tx, poolY + 1, depth, Fishing.level(game));
        hooked = null;
        shell.sfx(SFX_CAST);
    }

    private void step(Shell shell, Game game) {
        switch (line.step(game.rng)) {
            case Line.SPLASH -> {
                splashX = line.toX;
                splashAt = shell.ticks;
                shell.sfx(SFX_PLOP);
            }
            case Line.BITE_NOW -> {
                FishingSection section = Fishing.section(game);
                hooked = forced != null ? forced : sys.table.choose(FishTable.Waters.of(game, FishDef.LAKE,
                        line.depth, section.landedOnce()), game.rng);
                forced = null;
                biteAt = shell.ticks;
                splashX = line.toX;
                splashAt = shell.ticks;
                shell.sfx(SFX_PLOP);
            }
            case Line.MISSED -> {
                hooked = null;
                shell.toast("IT GOT AWAY...");
            }
            default -> {
            }
        }
    }

    private void strike(Shell shell, String id) {
        sys.strike(shell, id, landed -> {
            landedAt = shell.ticks;
            landedId = landed.id();
            landedFreed = landed.freed();
            shell.toast(landed.message());
        }, line::reelIn);
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

    private float clampX(float x) {
        return Math.max(0, Math.min(WIDTH - shell.width(), x));
    }

    private void snapCamera() {
        camX = clampX(runner.x - shell.width() / 2f + 60);
    }

    private void animate() {
        float speed = Math.abs(runner.speed);
        if (runner.rolling || !runner.onGround) {
            anim.set(Anim.ROLL, Math.max(0, 4 - (int) speed));
        } else if (runner.ducking) {
            anim.set(Anim.DUCK, 6);
        } else if (speed > 0.05f) {
            anim.set(speed >= Runner.TOP ? Anim.RUN : Anim.WALK, Math.max(0, 8 - (int) speed));
        } else {
            anim.set(Anim.WAIT, 6);
        }
        anim.tick();
    }

    /** Builds the water's cycle frames for the season (in update: never in draw). */
    private void cycle(int season) {
        if (season == cycledSeason) {
            return;
        }
        cycledSeason = season;
        Art art = shell.art;
        Art.Seasonal look = art.season(season);
        int[] base = new int[4];
        System.arraycopy(art.ghzPalette, 32 + 8, base, 0, 4);
        int[] steps;
        try {
            steps = art.s1.palette(PAL_GHZ_CYCLE, 16);
        } catch (RuntimeException e) {
            steps = new int[16];
            for (int i = 0; i < 16; i++) {
                steps[i] = base[i % 4];
            }
        }
        poolBed = look.tone.apply(base[0] | 0xFF000000);
        SceneImage waterfall = art.kit.blockImage(52);
        SceneImage bridge = art.kit.blockImage(51).crop(0, poolY, Art.BLOCK, Art.BLOCK - poolY);
        for (int s = 0; s < 4; s++) {
            fall[s] = look.tone.apply(recolour(waterfall, base, steps, s));
            pool[s] = look.tone.apply(twice(recolour(bridge, base, steps, s)));
        }
    }

    /** A colour as the light's tint would draw it (fills are not tinted). */
    private static int multiply(int argb, int tint) {
        int r = (argb >>> 16 & 255) * (tint >>> 16 & 255) / 255;
        int g = (argb >>> 8 & 255) * (tint >>> 8 & 255) / 255;
        int b = (argb & 255) * (tint & 255) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The pool's rows twice over: one picture across the jetty's block and the waterfall's. */
    private static SceneImage twice(SceneImage image) {
        int w = image.width(), h = image.height();
        int[] px = new int[w * 2 * h];
        int[] src = image.pixels();
        for (int y = 0; y < h; y++) {
            System.arraycopy(src, y * w, px, y * w * 2, w);
            System.arraycopy(src, y * w, px, y * w * 2 + w, w);
        }
        return new SceneImage(w * 2, h, px);
    }

    private static SceneImage recolour(SceneImage image, int[] base, int[] steps, int step) {
        int[] px = image.pixels();
        for (int i = 0; i < px.length; i++) {
            for (int k = 0; k < 4; k++) {
                if (px[i] >>> 24 != 0 && (px[i] & 0xFFFFFF) == (base[k] & 0xFFFFFF)) {
                    px[i] = steps[step * 4 + k] | 0xFF000000;
                    break;
                }
            }
        }
        return new SceneImage(image.width(), image.height(), px);
    }

    // ------------------------------------------------------------------ debug

    void debugAt(float x) {
        runner.x = x;
        runner.y = ground.floorBelow(Math.round(x), 0);
        runner.speed = 0;
        runner.onGround = true;
        snapCamera();
    }

    void debugLand(String id) {
        Fishing.Landed landed = Fishing.land(shell.game, id, false);
        landedAt = shell.ticks;
        landedId = landed.id();
        landedFreed = landed.freed();
        shell.toast(landed.message());
    }

    boolean debugBite(String id) {
        if (line.state != Line.WAITING) {
            return false;
        }
        line.timer = 1;
        forced = id;
        return true;
    }

    boolean debugFight(String id) {
        if (!line.out()) {
            charge = CHARGE_PERIOD / 2;
            cast(shell, shell.game);
        }
        if (!line.out()) {
            return false;
        }
        line.state = Line.BITE;
        line.strike();
        strike(shell, id);
        return true;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        Game game = shell.game;
        Art.Seasonal look = shell.art.season(game.calendar.season());
        int light = game.calendar.light();
        SceneDraw tint = SceneDraw.plain().withTint(light == 0 ? 0xFFFFFFFF : light == 1 ? 0xFFFFC8A0 : 0xFF6D80C8);
        int w = canvas.width();
        int cx = Math.round(camX), cy = CAM_Y;
        canvas.drawBackdrop(look.backdrop(light), 0, 0, w, canvas.height(), 20, camX, shell.ticks);
        int step = (int) (shell.ticks / CYCLE_FRAMES % 4);
        SceneImage shore = look.block(1);
        if (shore != null) {
            canvas.draw(shore, -cx, -cy, tint);
        }
        SceneImage jetty = look.block(51);
        if (jetty != null) {
            canvas.drawRegion(jetty, 0, 0, Art.BLOCK, poolY, SHORE_END - cx, -cy, Art.BLOCK, poolY, tint);
        }
        if (fall[step] != null) {
            // The waterfall down to the pool only: block 52's own ground would show through the
            // pool's see-through stripes (Green Hill's water is drawn over the background).
            canvas.drawRegion(fall[step], 0, 0, Art.BLOCK, poolY, JETTY_END - cx, -cy, Art.BLOCK, poolY, tint);
        }
        if (pool[step] != null) {
            // Green Hill's water is see-through stripes over the background; the lake gets a bed of
            // its own darkest water colour beneath them so the background's hills do not show.
            canvas.fill(SHORE_END - cx, poolY - cy, WIDTH - SHORE_END, Art.BLOCK - poolY, multiply(poolBed, tint.tint()));
            canvas.draw(pool[step], SHORE_END - cx, poolY - cy, tint);
        }
        drawShadows(shell, canvas, cx, cy);
        canvas.fill(SHORE_END - cx, poolY - cy, WIDTH - SHORE_END, 1, 0x60FFFFFF);
        if (bridge) {
            drawBridge(shell, canvas, cx, cy, tint);
        }
        drawBarnaby(shell, canvas, cx, cy, tint);
        if (fallen == 0) {
            drawFarmer(shell, canvas, cx, cy, tint);
        }
        drawLine(shell, canvas, cx, cy);
        drawSplash(shell, canvas, cx, cy);
        drawLanded(shell, canvas, cx, cy);
        if (game.raining) {
            for (int i = 0; i < 70; i++) {
                int x = (int) ((i * 97 + shell.ticks * 3 * 7) % (w + 40)) - 20;
                int y = (int) ((i * 53 + shell.ticks * 7 * 9) % (canvas.height() + 20)) - 10;
                canvas.fill(x, y, 1, 6, 0x806DB6FF);
            }
        }
        PlayScreen.drawHud(shell, canvas);
        if (charging) {
            drawPower(canvas, runner.x - cx, runner.y - cy - 52);
        } else if (!line.out() && Fishing.holdingRod(game) && runner.onGround && runner.x > SHORE_END - 60) {
            Text.centred(canvas, "HOLD X TO CAST", canvas.height() - 36, Text.GREY);
        }
    }

    /**
     * The lake bridge, once the Reef chamber is restored: Sonic 1's Green Hill bridge logs
     * (Nem_Bridge, Map_Bri frame 0, each log end 16 pixels) from the jetty's end to the falls.
     */
    private void drawBridge(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        SceneSpriteSet logs = shell.art.bridge;
        if (logs == null || logs.frameCount() == 0) {
            return;
        }
        int top = ground.floorBelow(JETTY_END - 8, 0);
        SceneSprite log = logs.frame(0);
        for (int x = JETTY_END; x < WIDTH - 16; x += 16) {
            canvas.draw(log, x + 8 - cx, top - cy + log.originY(), tint);
        }
    }

    /** Fish shadows under the surface (more in the deep), drifting and turning. */
    private void drawShadows(Shell shell, SceneCanvas canvas, int cx, int cy) {
        SceneDraw shade = SceneDraw.plain().withFlash(0xFF002448).withAlpha(0.35f);
        String[] kinds = {"bubble_bass", "loop_pike", "ring_carp", "checker_perch"};
        for (int i = 0; i < 4; i++) {
            SceneImage fish = sys.art.picture(kinds[i]);
            if (fish == null) {
                continue;
            }
            double t = (shell.ticks + i * 400) / (220.0 + i * 37);
            float x = SHORE_END + 60 + i * 110 + (float) Math.sin(t) * 50;
            float y = poolY + 22 + i * 9 + (float) Math.sin(t * 2.3) * 4;
            boolean left = Math.cos(t) > 0;
            canvas.draw(fish, x - cx, y - cy, shade.withFlipX(!left));
        }
    }

    /** Barnaby the Rocky on the end of his jetty when his day puts him there, line in the water. */
    private void drawBarnaby(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        if (!barnaby) {
            return;
        }
        SceneSpriteSet seal = shell.art.animal("rocky");
        if (seal == null || seal.frameCount() < 3) {
            return;
        }
        float feet = ground.floorBelow(BARNABY_X, 0);
        long cycle = shell.ticks % 1500;
        boolean landing = cycle > 1400;
        SceneSprite body = seal.frame(landing ? 1 : 0);
        float hop = landing ? -Math.abs((float) Math.sin((cycle - 1400) / 8.0)) * 6 : 0;
        canvas.draw(body, BARNABY_X - cx, feet - cy + hop - (body.height() - body.originY()), tint.withFlipX(true));
        float tipX = BARNABY_X + 16, tipY = feet - 22;
        PondLine.drawLine(canvas, BARNABY_X + 4 - cx, feet - 8 - cy, tipX - cx, tipY - cy, 0xFF924900, 0);
        float bobX = tipX + 40, bobY = poolY + (float) Math.sin(shell.ticks / 15.0) * 0.8f;
        if (landing) {
            SceneImage fish = sys.art.picture("bubble_bass");
            if (fish != null) {
                canvas.draw(fish, tipX - cx - 4, tipY - cy - 6 + hop, tint);
            }
        } else {
            PondLine.drawLine(canvas, tipX - cx, tipY - cy, bobX - cx, bobY - cy, 0xC0FFFFFF, 8);
            canvas.fill(Math.round(bobX - cx) - 1, Math.round(bobY - cy) - 3, 3, 3, 0xFFDB2400);
        }
    }

    /** Whether Barnaby's schedule has him on the jetty now (the People system's own plan). */
    private static boolean barnabyHere(Game game) {
        starpost.people.People people = game.section(starpost.people.People.class);
        if (people == null) {
            return false;
        }
        starpost.people.VillagerDef barnaby = people.cast.get("barnaby");
        if (barnaby == null || !people.present(barnaby, game)) {
            return false;
        }
        starpost.people.Spot spot = barnaby.schedule().resolve(game.calendar.season(), game.calendar.weekday(),
                game.raining, game.calendar.minutes(), game.flags);
        return spot != null && !spot.inside() && spot.anchor().equals("jetty");
    }

    private void drawFarmer(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        var set = shell.art.farmer(shell.game.farmer);
        SceneSprite pose = anim.pose(set);
        SceneDraw style = tint.withFlipX(runner.facingLeft);
        float feet = runner.y - cy;
        float originY = anim.id() == Anim.ROLL ? feet - 15 : feet - (pose.height() - pose.originY());
        if (shell.game.farmer.equals("tails")) {
            Anim.drawTails(canvas, shell.art.tailsTails, anim.id(), shell.ticks, runner.x - cx, originY, style);
        }
        canvas.draw(pose, runner.x - cx, originY, style);
        if (Fishing.holdingRod(shell.game) && (line.out() || charging)) {
            float dir = runner.facingLeft ? -1 : 1;
            boolean pulling = line.state == Line.BITE || line.state == Line.FIGHT;
            float back = charging ? power() * 10 : 0;
            float bend = pulling ? (float) Math.sin(shell.ticks / 2.0) * 2 + 6 : 0;
            PondLine.drawLine(canvas, runner.x - cx + dir * 6, feet - 17, runner.x - cx + dir * (19 - back - bend / 2),
                    feet - 33 - back / 2 + bend, 0xFF924900, 0);
        }
    }

    private void drawLine(Shell shell, SceneCanvas canvas, int cx, int cy) {
        if (!line.out()) {
            return;
        }
        float dir = runner.facingLeft ? -1 : 1;
        boolean pulling = line.state == Line.BITE || line.state == Line.FIGHT;
        float bend = pulling ? (float) Math.sin(shell.ticks / 2.0) * 2 + 6 : 0;
        float tipX = runner.x - cx + dir * (19 - bend / 2), tipY = runner.y - cy - 33 + bend;
        float bx = line.bobberX() - cx, by = line.bobberY() - cy;
        PondLine.drawLine(canvas, tipX, tipY, bx, by, 0xC0FFFFFF, pulling ? 0 : 10);
        if (line.state == Line.WAITING || line.state == Line.FLYING) {
            int x = Math.round(bx) - 2, y = Math.round(by) - 5;
            canvas.fill(x, y, 5, 6, 0xFF240000);
            canvas.fill(x + 1, y + 1, 3, 2, 0xFFDB2400);
            canvas.fill(x + 1, y + 3, 3, 2, 0xFFFFFFFF);
        }
        if (line.state == Line.BITE) {
            float hop = Math.abs((float) Math.sin((shell.ticks - biteAt) / 4.0)) * 4;
            Text.shadow(canvas, "!", Math.round(runner.x - cx) - 2, Math.round(runner.y - cy - 58 - hop), Text.YELLOW);
        }
    }

    private void drawSplash(Shell shell, SceneCanvas canvas, int cx, int cy) {
        long age = shell.ticks - splashAt;
        SceneSpriteSet set = sys.art.splash;
        if (age < 24 && set != null && set.frameCount() > 2) {
            int frame = fallen > 0 ? Math.min(2, (int) (age / 8)) : age < 8 ? 0 : 1;
            canvas.draw(set.frame(frame), splashX - cx, poolY - cy + 2, SceneDraw.plain());
        }
    }

    /** The catch held up overhead; a popped badnik's animal hops off toward the shore. */
    private void drawLanded(Shell shell, SceneCanvas canvas, int cx, int cy) {
        long age = shell.ticks - landedAt;
        if (landedId == null || age >= 110) {
            return;
        }
        FishDef def = sys.table.get(landedId);
        float x = runner.x - cx, feet = runner.y - cy;
        if (def != null && def.isBadnik() && age < 20 && shell.art.explosion.frameCount() > 0) {
            canvas.draw(shell.art.explosion.frame(Math.min(shell.art.explosion.frameCount() - 1, (int) (age / 4))),
                    x, feet - 50, SceneDraw.plain());
        }
        float rise = Math.min(1, age / 10f);
        if (def == null) {
            shell.art.icons.draw(canvas, shell.game.item(landedId), x - 8, feet - 44 - 16 * rise, SceneDraw.plain());
        } else if (!(def.isBadnik() && age < 20)) {
            boolean red = def.id().equals(Fishing.RED_CHOPPER);
            SceneImage picture = def.isBadnik() ? sys.art.badnikFrame(def.badnik(), (int) (age / 4 % 2))
                    : sys.art.picture(def.id());
            if (picture != null && red) {
                // The giant: held up beside the farmer at twice the size, its tail on the jetty.
                float dir = runner.facingLeft ? -1 : 1;
                canvas.draw(picture, x + dir * 34 - picture.width(), feet - picture.height() * 2 * rise,
                        SceneDraw.plain().withScale(2));
            } else if (picture != null) {
                canvas.draw(picture, x - picture.width() / 2f, feet - 44 - picture.height() * rise, SceneDraw.plain());
            }
        }
        if (landedFreed && age >= 12) {
            SceneSpriteSet animal = shell.art.animal(landedAt % 2 == 0 ? "pocky" : "flicky");
            if (animal != null && animal.frameCount() > 1) {
                float ax = x - 14 - (age - 12) * 1.3f, hop = -Math.abs((float) Math.sin((age - 12) / 6.0)) * 10;
                SceneSprite a = animal.frame(hop < -4 ? 1 : 0);
                canvas.draw(a, ax, feet + hop - (a.height() - a.originY()), SceneDraw.plain());
            }
        }
    }

    /** The wind-up: a little meter over the farmer's head. */
    private void drawPower(SceneCanvas canvas, float x, float y) {
        int w = 40, bx = Math.round(x) - w / 2, by = Math.round(y);
        canvas.fill(bx - 1, by - 1, w + 2, 6, 0xFF240000);
        int fill = Math.round(w * power());
        canvas.fill(bx, by, fill, 4, power() > 0.85f ? 0xFF49DB49 : 0xFFFFDB00);
        canvas.fill(bx, by, fill, 1, 0x80FFFFFF);
    }
}
