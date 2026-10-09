package flappytails;

import com.openggf.mods.scene.DebuggableScene;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.PaletteAssembly;
import com.openggf.mods.scene.art.SceneArtCache;
import com.openggf.mods.scene.art.StockSceneArt;

/**
 * Flappy Tails: the whole game as one mod scene. It owns four screens (the title with its menu,
 * the flight, the results and the records), turns input into a {@link Run}'s single button, and
 * presents what the run reports: sounds, sparkles, shakes, title cards.
 *
 * <p>Like every scene, it changes state only in {@link #update}; {@link #draw} may be skipped
 * or repeated and only reads. {@link #debugJump} lets tests and capture tools go straight to any
 * screen.
 */
public final class FlappyScene implements ModScene, DebuggableScene {
    enum Screen { TITLE, PLAY, RESULTS, RECORDS }

    /** Palettes in the combined ROM ({@code Pal_SonicTails}, {@code Pal_AIZ}, {@code Pal_HCZ1}, {@code Pal_LBZ1}, {@code Pal_SSZ1}). */
    private static final int PAL_SONIC_TAILS = 0x0A8A3C;
    private static final int PAL_AIZ = 0x0A8B7C;
    private static final int PAL_HCZ1 = 0x0A8D9C;
    private static final int PAL_LBZ1 = 0x0A929C;
    private static final int PAL_SSZ1 = 0x0A973C;
    /** Menu rows on the title. */
    private static final int ITEM_PLAY = 0;
    private static final int ITEM_PRACTICE = 1;
    private static final int ITEM_RECORDS = 2;
    private static final int ITEM_EXIT = 3;
    private static final int ITEMS = 4;

    // ---- art, loaded once in enter(); any of it may be null without a ROM ----
    private SceneRomArt rom;
    private SceneArtCache cache;
    private TailsArt tails;
    private CardFont font = new CardFont();
    private Hud hud;
    private SceneSpriteSet ring;
    private SceneSpriteSet explosion;
    private SceneSpriteSet eggman;
    private SceneSpriteSet flicky;
    private final ZoneArt[] zones = new ZoneArt[Zone.TOUR_LENGTH];
    private final SceneSpriteSet[] badniks = new SceneSpriteSet[Zone.TOUR_LENGTH];
    private final SceneSpriteSet[] cards = new SceneSpriteSet[Zone.TOUR_LENGTH];

    // ---- state ----
    private Screen screen = Screen.TITLE;
    private long ticks;
    private long screenTick;
    private Records records = new Records();
    private Run run;
    private Run demo;
    private final Autopilot pilot = new Autopilot();
    private final Autopilot demoPilot = new Autopilot();
    private final Effects effects = new Effects();
    private boolean autopilot;
    private Mode mode = Mode.CLASSIC;
    private int practiceZone;
    private int cursor;
    private boolean paused;
    private long cardStart;
    private long scorePulseAt = -100;
    private long medalAt = -100;
    private int medalTier;
    private boolean newBest;
    private int resultMedal;
    private int shownScore;
    private long seeds = 0x5EED;
    private String pendingSeed;
    private String banner = "";
    private long bannerAt = -1000;

    @Override
    public void enter(SceneContext ctx) {
        rom = ctx.art().rom();
        int[] base = palette(PAL_AIZ);
        if (rom != null) {
            cache = new SceneArtCache(rom, 16);
            ring = stock(StockSceneArt.S3K_RING, base);
            explosion = stock(StockSceneArt.S3K_EXPLOSION, base);
            eggman = stock(StockSceneArt.S3K_ROBOTNIK_SHIP, base);
            flicky = stock(StockSceneArt.S3K_BLUE_FLICKY, base);
            badniks[2] = stock(StockSceneArt.S3K_BUGGERNAUT, palette(PAL_HCZ1));
            badniks[3] = stock(StockSceneArt.S3K_ORBINAUT, palette(PAL_LBZ1));
            badniks[4] = stock(StockSceneArt.S3K_EGG_ROBO, palette(PAL_SSZ1));
            tails = TailsArt.load(rom);
            font = CardFont.load(rom);
        }
        hud = new Hud(rom, base, ring);
        java.util.List<Zone> tour = Zone.tour();
        for (int i = 0; i < tour.size(); i++) {
            Zone zone = tour.get(i);
            zones[i] = ZoneArt.load(rom, zone);
            if (rom != null && rom.hasTitleCard(zone.zone(), zone.act())) {
                cards[i] = rom.titleCard(zone.zone(), zone.act());
            }
        }
        records = Records.load(ctx.storage());
        openTitle(ctx);
    }

    private SceneSpriteSet stock(StockSceneArt art, int[] palette) {
        try {
            return cache.sprites(art, palette);
        } catch (RuntimeException unsupported) {
            return null;   // another ROM revision: the scene draws stand-ins
        }
    }

    /** Sonic and Tails' palette line, then a zone's three lines, as the game holds them in CRAM. */
    private int[] palette(int zonePalette) {
        if (rom == null) return new int[64];
        return new PaletteAssembly().rom(rom, PAL_SONIC_TAILS, 0, 16).rom(rom, zonePalette, 16, 48).build();
    }

    // =====================================================================================
    // update
    // =====================================================================================

    @Override
    public void update(SceneContext ctx) {
        applyPending(ctx);
        ticks++;
        screenTick++;
        effects.update();
        switch (screen) {
            case TITLE -> updateTitle(ctx);
            case PLAY -> updatePlay(ctx);
            case RESULTS -> updateResults(ctx);
            case RECORDS -> updateRecords(ctx);
        }
    }

    private void show(Screen next) {
        screen = next;
        screenTick = 0;
    }

    // ---- title ----

    private void openTitle(SceneContext ctx) {
        show(Screen.TITLE);
        paused = false;
        effects.clear();
        demo = new Run(Mode.CLASSIC, nextSeed(), 0, true);
        demoPilot.reseed(demo.seed);
        demoPilot.setDaring(35);
        ctx.audio().playMusic(Sounds.MUS_TITLE);
    }

    private void updateTitle(SceneContext ctx) {
        // The attract flight behind the menu: the autopilot, silent, forever.
        demo.tick(demoPilot.decide(demo));
        for (Run.Event event : demo.events()) {
            if (event.type() == Run.Type.RING) effects.sparkle(ring, event.x(), event.y(), demo.speed() / 256f);
        }
        if (demo.phase() == Run.Phase.OVER) {
            demo = new Run(Mode.CLASSIC, nextSeed(), 0, true);
            demoPilot.reseed(demo.seed);
        }
        if (screenTick < 30) return;    // let the logo arrive before the menu takes input

        if (ctx.buttonRepeated(SceneButtons.UP)) moveCursor(ctx, -1);
        if (ctx.buttonRepeated(SceneButtons.DOWN)) moveCursor(ctx, 1);
        int sideways = ctx.buttonRepeated(SceneButtons.LEFT) ? -1 : ctx.buttonRepeated(SceneButtons.RIGHT) ? 1 : 0;
        if (sideways != 0) changeOption(ctx, sideways);
        int hovered = menuItemAt(ctx.mouse().x(), ctx.mouse().y(), ctx.width());
        if (hovered >= 0 && ctx.mouse().lastInputWasMouse()) cursor = hovered;
        boolean accept = ctx.input().menuAccept() || ctx.keyPressed(SceneKeys.SPACE)
                || ctx.keyPressed(SceneKeys.ENTER) || (ctx.mouse().leftPressed() && hovered >= 0);
        if (accept) choose(ctx);
    }

    private void moveCursor(SceneContext ctx, int step) {
        cursor = Math.floorMod(cursor + step, ITEMS);
        ctx.audio().playSfx(Sounds.SFX_SWITCH);
    }

    private void changeOption(SceneContext ctx, int step) {
        if (cursor == ITEM_PLAY) {
            mode = Mode.values()[Math.floorMod(mode.ordinal() + step, Mode.values().length)];
            ctx.audio().playSfx(Sounds.SFX_SWITCH);
        } else if (cursor == ITEM_PRACTICE) {
            int unlocked = records.furthestZone() + 1;
            practiceZone = Math.floorMod(practiceZone + step, unlocked);
            ctx.audio().playSfx(unlocked > 1 ? Sounds.SFX_SWITCH : Sounds.SFX_ERROR);
        }
    }

    private void choose(SceneContext ctx) {
        switch (cursor) {
            case ITEM_PLAY -> startRun(ctx, 0, false);
            case ITEM_PRACTICE -> startRun(ctx, practiceZone * Zone.GATES, practiceZone > 0);
            case ITEM_RECORDS -> {
                ctx.audio().playSfx(Sounds.SFX_SWITCH);
                show(Screen.RECORDS);
            }
            default -> ctx.exitToGameTitle();
        }
    }

    // ---- flight ----

    private void startRun(SceneContext ctx, int firstGate, boolean practice) {
        long seed = pendingSeed != null ? Long.decode(pendingSeed) : nextSeed();
        run = new Run(mode, seed, firstGate, practice);
        pilot.reseed(seed);
        effects.clear();
        paused = false;
        shownScore = 0;
        cardStart = 0;
        medalAt = -1000;
        scorePulseAt = -1000;
        show(Screen.PLAY);
        ctx.audio().playMusic(run.zoneShown().music());
    }

    private void updatePlay(SceneContext ctx) {
        if (ctx.buttonPressed(SceneButtons.START) || ctx.keyPressed(SceneKeys.P)) {
            paused = !paused;
            ctx.audio().playSfx(Sounds.SFX_SWITCH);
        }
        if (paused) {
            screenTick--;                       // the clock stops while paused
            if (ctx.buttonPressed(SceneButtons.B)) openTitle(ctx);
            return;
        }
        boolean flap = autopilot ? pilot.decide(run)
                : ctx.buttonPressed(SceneButtons.ACTIONS) || ctx.keyPressed(SceneKeys.SPACE) || ctx.mouse().leftPressed();
        run.tick(flap);
        present(ctx, run);
        if (run.phase() == Run.Phase.OVER && run.phaseTicks() > 10) {
            finishRun(ctx);
        }
    }

    /** Turns what the run reports into sound and spectacle. */
    private void present(SceneContext ctx, Run r) {
        float drift = r.speed() / 256f;
        for (Run.Event event : r.events()) {
            switch (event.type()) {
                case SFX -> ctx.audio().playSfx(event.value());
                case MUSIC -> ctx.audio().playMusic(event.value());
                case GATE -> scorePulseAt = screenTick;
                case RING -> effects.sparkle(ring, event.x(), event.y(), drift);
                case ZONE_CLEAR -> {
                    int tier = Hud.medal(event.value());
                    if (tier > Hud.medal(event.value() - 1) || event.value() % Zone.GATES == 0) {
                        medalTier = Math.max(1, Math.min(5, event.value() / Zone.GATES));
                        medalAt = screenTick;
                        ctx.audio().playSfx(Sounds.SFX_BIG_RING);
                    }
                }
                case ZONE -> {
                    cardStart = screenTick;
                    effects.flash(0xFFFFFF, 24);
                }
                case SPILL -> {
                    effects.shake(10, 2);
                    effects.flash(0xFF8040, 8);
                }
                case HIT -> effects.popup("OUCH!", event.x(), event.y() - 24, Hud.ORANGE);
                case CRASH -> {
                    effects.shake(24, 5);
                    effects.flash(0xFFFFFF, 14);
                    effects.explode(explosion, event.x() + 6, event.y(), 0);
                    ctx.audio().fadeOutMusic();
                }
                case FLAP, SUPER_END -> { }
                case CLOSE_CALL -> {
                    effects.popup(event.value() <= 0 ? "BY A WHISKER!" : "CLOSE!", event.x() + 24, event.y() - 20, Hud.CYAN);
                    effects.sparkle(ring, event.x() + 8, event.y() - 10, 0);
                }
                case SUPER -> {
                    medalAt = -1000;                   // the banner announces this lap, not the toast
                    effects.flash(0xFFE060, 30);
                    effects.shake(16, 3);
                    banner = "SUPER TAILS";
                    bannerAt = screenTick;
                }
                case SMASH -> {
                    effects.shake(8, 3);
                    for (int i = -1; i <= 1; i++) {
                        effects.explode(explosion, event.x() + i * 10, event.y() + (event.value() < 0 ? i * 8 : i * 18), drift);
                    }
                }
            }
        }
    }

    private void finishRun(SceneContext ctx) {
        newBest = records.finish(run);
        records.save(ctx.storage());
        resultMedal = run.practice ? 0 : Hud.medal(run.score());
        shownScore = 0;
        show(Screen.RESULTS);
        ctx.audio().playMusic(Sounds.MUS_GAME_OVER);
    }

    // ---- results ----

    private void updateResults(SceneContext ctx) {
        long t = screenTick;
        if (t >= 70 && shownScore < run.score()) {
            if (t % 3 == 0) {
                shownScore++;
                if (shownScore % 2 == 0) ctx.audio().playSfx(Sounds.SFX_SWITCH);
                if (shownScore == run.score()) ctx.audio().playSfx(Sounds.SFX_REGISTER);
            }
        }
        boolean tallied = t >= 70 && shownScore >= run.score();
        if (resultMedal > 0 && t == tallyEnd() + 10) {
            ctx.audio().playSfx(Sounds.SFX_BIG_RING);            // the medal stamps down
        }
        if (newBest && t == tallyEnd() + 30) {
            ctx.audio().playMusic(Sounds.MUS_EXTRA_LIFE);
        }
        if (t < 60) return;
        boolean retry = ctx.input().menuAccept() || ctx.keyPressed(SceneKeys.SPACE) || ctx.mouse().leftPressed();
        if (retry && !tallied) {
            shownScore = run.score();          // a press finishes the tally first
            return;
        }
        if (retry) {
            startRun(ctx, run.firstGate, run.practice);
        } else if (ctx.input().menuBack() || ctx.keyPressed(SceneKeys.BACKSPACE)) {
            openTitle(ctx);
        }
    }

    private long tallyEnd() {
        return 70 + 3L * run.score();
    }

    // ---- records ----

    private void updateRecords(SceneContext ctx) {
        demo.tick(demoPilot.decide(demo));
        if (demo.phase() == Run.Phase.OVER) demo = new Run(Mode.CLASSIC, nextSeed(), 0, true);
        if (screenTick > 10 && (ctx.input().menuBack() || ctx.input().menuAccept()
                || ctx.keyPressed(SceneKeys.BACKSPACE) || ctx.mouse().leftPressed())) {
            ctx.audio().playSfx(Sounds.SFX_SWITCH);
            show(Screen.TITLE);
        }
    }

    private long nextSeed() {
        seeds = seeds * 6364136223846793005L + 1442695040888963407L;
        return seeds >>> 16;
    }

    // =====================================================================================
    // draw
    // =====================================================================================

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        switch (screen) {
            case TITLE -> drawTitle(ctx, canvas);
            case PLAY -> drawPlay(ctx, canvas);
            case RESULTS -> drawResults(ctx, canvas);
            case RECORDS -> drawRecords(ctx, canvas);
        }
        effects.drawFlash(canvas);
    }

    /** The course, Tails and everything in the air: shared by the flight, the title and the results. */
    private void drawWorld(SceneCanvas canvas, Run r, int width) {
        int sx = effects.shakeX();
        int sy = effects.shakeY();
        ZoneArt shown = zones[r.tourIndex()];
        Zone zone = r.zoneShown();
        if (shown.backdrop != null) {
            canvas.drawBackdrop(shown.backdrop, sx, sy, width, canvas.height(), zone.backdropTop(), r.sceneryX(), ticks);
        } else {
            canvas.clear(0x3070D0);
        }
        int scroll = r.course.scrollX();
        for (Course.Gate gate : r.course.gates()) {
            if (!gate.smashed) drawGate(canvas, gate, gate.x - scroll + sx, sy);
        }
        for (Course.Badnik badnik : r.course.badniks()) {
            if (badnik.destroyedAt >= 0) continue;
            drawBadnik(canvas, badnik, badnik.x - scroll + sx, badnik.y(r.ticks()) + sy, r.ticks());
        }
        for (Course.Ring item : r.course.rings()) {
            if (item.takenAt < 0) drawRing(canvas, item.x - scroll + sx, item.y + sy, r.ticks());
        }
        drawGround(canvas, shown, r.sceneryX(), width, sx, sy);
        for (Run.Spill spill : r.spills()) {
            if (spill.takenAt >= 0) continue;
            long age = r.ticks() - spill.born;
            if (age > 0xC0 && (age & 2) == 0) continue;                  // blinking out
            drawRing(canvas, (spill.x >> 8) - scroll + sx, (spill.y >> 8) + sy, r.ticks() * 2);
        }
        if (r.tailsVisible()) {
            if (r.isSuper()) drawSuperAura(canvas, Run.TAILS_X + sx, r.tailsY() + sy, r.ticks(), r.superTicks());
            drawTails(canvas, r.tailsAnimation(), Run.TAILS_X + sx, r.tailsY() + sy, r.ticks(),
                    r.phase() == Run.Phase.CRASHED && r.phaseTicks() < 12, r.isSuper());
            if (r.isSuper()) drawFlickies(canvas, Run.TAILS_X + sx, r.tailsY() + sy, r.ticks(), true);
        }
        effects.draw(canvas);
    }

    private void drawGate(SceneCanvas canvas, Course.Gate gate, int x, int sy) {
        ZoneArt art = zones[gate.tourIndex];
        if (art.pillar == null) {
            int color = 0xFF2E9A3A;
            canvas.fill(x, -8, ZoneArt.PILLAR_WIDTH, gate.top() + 8 + sy, color);
            canvas.fill(x, gate.bottom() + sy, ZoneArt.PILLAR_WIDTH, Course.GROUND_Y - gate.bottom(), color);
            return;
        }
        int lip = 12;   // rows of the pillar picture above its surface line
        canvas.draw(art.pillar, x, gate.bottom() - lip + sy, SceneDraw.plain());
        canvas.draw(art.pillar, x, gate.top() + lip - ZoneArt.PILLAR_HEIGHT + sy, SceneDraw.plain().withFlipY(true));
    }

    private void drawGround(SceneCanvas canvas, ZoneArt art, double scenery, int width, int sx, int sy) {
        int top = Course.GROUND_Y - 12 + sy;
        if (art.ground == null) {
            canvas.fill(0, Course.GROUND_Y + sy, width, canvas.height(), 0xFF3C8C24);
            canvas.fill(0, Course.GROUND_Y + sy, width, 3, 0xFF8CDA48);
            return;
        }
        int tile = art.ground.width();
        int offset = Math.floorMod((int) Math.floor(scenery), tile);
        for (int x = -offset + sx - tile; x < width + tile; x += tile) {
            canvas.draw(art.ground, x, top, SceneDraw.plain());
        }
        if (top + art.ground.height() < canvas.height()) {
            canvas.fill(0, top + art.ground.height(), width, canvas.height(), 0xFF000000 | Hud.INK);
        }
    }

    private void drawRing(SceneCanvas canvas, int x, int y, long t) {
        if (ring == null) {
            canvas.fill(x - 4, y - 4, 8, 8, 0xFFFFD020);
            return;
        }
        canvas.draw(ring.frame((int) ((t / 8) % 4)), x, y, SceneDraw.plain());
    }

    /**
     * The zone's badnik: Hydrocity's Buggernaut cycles its three wing frames a tick apart
     * ({@code AniRaw_Buggernaut}); Launch Base's Orbinaut circles four spike balls round its body;
     * Sky Sanctuary's Egg Robo flickers its jet flame.
     */
    private void drawBadnik(SceneCanvas canvas, Course.Badnik badnik, int x, int y, long t) {
        SceneSpriteSet art = badniks[badnik.tourIndex];
        if (art == null) {
            canvas.fill(x - 10, y - 10, 20, 20, 0xFFD03030);
            canvas.fill(x - 6, y - 6, 12, 12, 0xFFFFE080);
            return;
        }
        switch (badnik.tourIndex) {
            case 2 -> canvas.draw(art.frame((int) (t % 3)), x, y, SceneDraw.plain());
            case 3 -> {
                canvas.draw(art.frame(0), x, y, SceneDraw.plain());
                for (int i = 0; i < 4; i++) {
                    double angle = t * 0.06 + i * Math.PI / 2;
                    canvas.draw(art.frame(1), x + (float) Math.cos(angle) * 16, y + (float) Math.sin(angle) * 16,
                            SceneDraw.plain());
                }
            }
            default -> canvas.draw(art.frame((t / 2) % 2 == 0 ? 2 : 0), x, y, SceneDraw.plain().withFlipX(true));
        }
    }

    private void drawTails(SceneCanvas canvas, int animation, float x, float y, long t, boolean hitFlash,
            boolean superForm) {
        if (tails == null) {
            canvas.fill(Math.round(x) - 10, Math.round(y) - 12, 20, 24, 0xFFFFA020);
            return;
        }
        SceneDraw style = SceneDraw.plain();
        if (hitFlash) {
            style = style.withFlash(0xC0FFFFFF);
        } else if (superForm) {
            int glow = 70 + (int) Math.round(Math.abs(Math.sin(t / 6.0)) * 90);
            style = style.withFlash(glow << 24 | 0xFFF070);
        }
        tails.draw(canvas, animation, t, x, y, style);
    }

    /** A pulsing golden halo and a trail of sparkles behind Super Tails. */
    private void drawSuperAura(SceneCanvas canvas, float x, float y, long t, int remaining) {
        if (remaining < 120 && (t / 4) % 2 == 0) return;                // flickering out
        if (ring != null) {
            for (int i = 1; i <= 4; i++) {
                float trailX = x - i * 12;
                float trailY = y + (float) Math.sin((t - i * 5) / 6.0) * 4;
                canvas.draw(ring.frame(4 + (int) ((t / 3 + i) % 4)), trailX, trailY,
                        SceneDraw.plain().withAlpha(1f - i * 0.2f));
            }
        }
    }

    /**
     * Super Tails' Flickies ({@code Obj_SuperTailsBirds} in the game): four birds circling him,
     * drawn from the ROM's blue Flicky washed gold.
     */
    private void drawFlickies(SceneCanvas canvas, float x, float y, long t, boolean gold) {
        if (flicky == null) return;
        for (int i = 0; i < 4; i++) {
            double angle = t * 0.09 + i * Math.PI / 2;
            float fx = x + (float) Math.cos(angle) * 26;
            float fy = y + (float) Math.sin(angle) * 14;
            SceneDraw style = SceneDraw.plain().withFlipX(Math.sin(angle) < 0);
            if (gold) style = style.withFlash(0xB8FFE040);
            canvas.draw(flicky.frame((int) ((t / 4 + i) % 2)), fx, fy, style);
        }
    }

    // ---- flight screen ----

    private void drawPlay(SceneContext ctx, SceneCanvas canvas) {
        int width = ctx.width();
        drawWorld(canvas, run, width);
        drawHud(canvas, run, width, false);
        Zone zone = run.zoneShown();
        long cardTicks = screenTick - cardStart;
        if (cardTicks < TitleCard.duration(width)) {
            TitleCard.draw(canvas, cards[run.tourIndex()], cardTicks, (width - 320) / 2, null,
                    zone.zone() != 10);
        }
        if (run.phase() == Run.Phase.READY) {
            drawReady(canvas, width);
        }
        if (screenTick - medalAt < 120) {
            drawMedalToast(canvas, width, screenTick - medalAt);
        }
        long sinceBanner = screenTick - bannerAt;
        if (sinceBanner < 110 && !banner.isEmpty()) {
            // Slides in from the right, holds, then leaves left, clear of the zone's title card.
            float x = sinceBanner < 16 ? width / 2f + (16 - sinceBanner) * 24 : sinceBanner > 94
                    ? width / 2f - (sinceBanner - 94) * 32 : width / 2f;
            cardWord(canvas, banner, x, 30, 1.5f, 99);
        }
        if (paused) {
            canvas.fill(0, 0, width, canvas.height(), 0x90000010);
            cardWord(canvas, "PAUSE", width / 2f, 80, 1.5f, 99);
            Hud.centred(canvas, "START: CARRY ON    B: BACK TO THE TITLE", 140, 1, Hud.WHITE);
        }
    }

    private void drawHud(SceneCanvas canvas, Run r, int width, boolean demoMode) {
        long sincePulse = screenTick - scorePulseAt;
        float pulse = demoMode || sincePulse > 8 ? 0 : (8 - sincePulse) / 8f * 0.5f;
        if (r.phase() != Run.Phase.READY || demoMode) {
            hud.score(canvas, r.score(), width / 2f, 10 - pulse * 8, 2 + pulse);
        }
        if (demoMode) return;
        hud.rings(canvas, r.rings(), 8, 8, r.mode.sonicRules && r.rings() == 0, r.ticks());
        if (r.mode.sonicRules) {
            int energy = r.flight.timer();
            int barWidth = 64;
            int filled = barWidth * energy / Flight.FULL_TIMER;
            int color = energy > Flight.FULL_TIMER / 2 ? 0xFF50E070 : energy > Flight.FULL_TIMER / 5 ? 0xFFF0D040 : 0xFFF04030;
            if (energy == 0 && (r.ticks() / 6) % 2 == 0) color = 0xFF802020;
            Hud.outlined(canvas, "FLIGHT", 10, 30, 1, 0xFFFFFFFF);
            canvas.fill(48, 30, barWidth + 2, 7, 0xC0000010);
            canvas.fill(49, 31, filled, 5, color);
        }
        String best = "BEST " + records.best(r.mode);
        Hud.outlined(canvas, best, width - 10 - Hud.width(best, 1), 10, 1, 0xFFFFFFFF);
        Hud.outlined(canvas, r.mode.label, width - 10 - Hud.width(r.mode.label, 1), 20, 1, 0xC0FFFFFF);
        if (r.lap() > 0) {
            String lap = "LAP " + (r.lap() + 1);
            Hud.outlined(canvas, lap, width - 10 - Hud.width(lap, 1), 30, 1, 0xFF000000 | Hud.GOLD);
        }
        if (r.practice) Hud.outlined(canvas, "PRACTICE", 10, 212, 1, 0xC0FFFFFF);
    }

    private void drawReady(SceneCanvas canvas, int width) {
        long t = run.ticks();
        if (t > TitleCard.arrival() + 20) {
            cardWord(canvas, "GET READY", width / 2f, 132, 1f, (int) ((t - TitleCard.arrival() - 20) / 3));
        }
        if ((t / 20) % 2 == 0 && t > 40) {
            Hud.centred(canvas, "A, B, C, SPACE OR CLICK TO FLAP", 168, 1, Hud.WHITE);
        }
        // A finger tapping, drawn: the universal "press here".
        int bob = (t / 15) % 2 == 0 ? 0 : 3;
        canvas.fill(Run.TAILS_X - 3, Run.START_Y + 26 + bob, 6, 10, 0xFFFFFFFF);
        canvas.fill(Run.TAILS_X - 5, Run.START_Y + 34 + bob, 10, 8, 0xFFFFFFFF);
        canvas.fill(Run.TAILS_X - 2, Run.START_Y + 22 + bob, 4, 4, 0xFFFFE0C0);
    }

    private void drawMedalToast(SceneCanvas canvas, int width, long age) {
        float slide = age < 10 ? (10 - age) * 6 : age > 100 ? (age - 100) * 6 : 0;
        int y = 46 - Math.round(slide);
        String text = medalTier >= 5 ? "SUPER RING!" : Hud.medalName(medalTier) + " RING!";
        Hud.panel(canvas, width / 2 - 70, y, 140, 30, 0.85f);
        hud.medal(canvas, medalTier, width / 2f - 50, y + 15, ticks, 0.5f);
        Hud.outlined(canvas, text, width / 2 - 34, y + 8, 1, 0xFF000000 | Hud.medalColor(medalTier, ticks));
        Hud.outlined(canvas, (medalTier * Zone.GATES) + " GATES", width / 2 - 34, y + 18, 1, 0xFFFFFFFF);
    }

    /** A word in title-card lettering centred on {@code centreX}, or the menu font if a letter is missing. */
    private void cardWord(SceneCanvas canvas, String word, float centreX, float y, float scale, int reveal) {
        if (font.canSpell(word)) {
            font.draw(canvas, word, centreX - font.width(word) * scale / 2, y, scale, reveal, SceneDraw.plain());
        } else {
            int s = Math.max(1, Math.round(scale * 2));
            Hud.outlined(canvas, word, Math.round(centreX - Hud.width(word, s) / 2f), Math.round(y), s, 0xFFFFFFFF);
        }
    }

    // ---- title screen ----

    private void drawTitle(SceneContext ctx, SceneCanvas canvas) {
        int width = ctx.width();
        drawWorld(canvas, demo, width);
        canvas.fill(0, 0, width, canvas.height(), 0x30000020);
        drawLogo(canvas, width, screenTick);
        if (eggman != null) drawEggman(canvas, width);
        if (screenTick < 30) return;
        float fade = Math.min(1f, (screenTick - 30) / 20f);
        int top = MENU_TOP;
        int centre = menuCentre(width);
        Hud.panel(canvas, centre - 110, top - 6, 220, ITEMS * 14 + 10, 0.8f * fade);
        for (int i = 0; i < ITEMS; i++) {
            String label = menuLabel(i);
            int y = top + i * 14;
            boolean on = i == cursor;
            int color = on ? ((ticks / 8) % 2 == 0 ? Hud.GOLD : 0xFFF4A0) : Hud.WHITE;
            int argb = Math.round(fade * 255) << 24 | color;
            int x = centre - Hud.width(label, 1) / 2;
            Hud.outlined(canvas, label, x, y, 1, argb);
            if (on && tails != null) {
                SceneSprite cursorTails = tails.frame(TailsArt.ANIM_FLY, ticks);
                canvas.draw(cursorTails, x - 18, y + 3, SceneDraw.plain().withScale(0.5f));
            }
        }
        String hint = cursor == ITEM_PLAY ? mode.blurb
                : cursor == ITEM_PRACTICE ? "PRACTICE FROM ANY ZONE YOU HAVE REACHED. NO RECORDS"
                : cursor == ITEM_RECORDS ? "BESTS, MEDALS AND TOTALS" : "BACK TO SONIC 3 & KNUCKLES";
        Hud.outlined(canvas, hint, centre - Hud.width(hint, 1) / 2, top + ITEMS * 14 + 10, 1,
                Math.round(fade * 255) << 24 | Hud.GREY);
        String bests = "BEST  CLASSIC " + records.best(Mode.CLASSIC) + "   SONIC RULES " + records.best(Mode.SONIC);
        Hud.outlined(canvas, bests, centre - Hud.width(bests, 1) / 2, 212, 1, Math.round(fade * 255) << 24 | Hud.WHITE);
    }

    private String menuLabel(int item) {
        return switch (item) {
            case ITEM_PLAY -> "PLAY   < " + mode.label + " >";
            case ITEM_PRACTICE -> "PRACTICE   < " + Zone.tour().get(practiceZone).name()
                    + (practiceZone == 1 ? " 2" : "") + " >";
            case ITEM_RECORDS -> "RECORDS";
            default -> "EXIT";
        };
    }

    /** The menu sits right of Tails' flight line and left-of-centre text would hide him. */
    private static final int MENU_TOP = 124;

    private static int menuCentre(int width) {
        return width / 2 + 34;
    }

    private int menuItemAt(int mx, int my, int width) {
        int top = MENU_TOP;
        int centre = menuCentre(width);
        if (mx < centre - 110 || mx > centre + 110) return -1;
        int item = Math.floorDiv(my - top + 3, 14);
        return item >= 0 && item < ITEMS ? item : -1;
    }

    /**
     * "FLAPPY TAILS ZONE" in the game's own title-card pieces: the red banner drops in at the
     * left, the name (spelled from the cards' letters) and the card's "ZONE" slide in from the
     * right at the card's 16 pixels a frame.
     */
    private void drawLogo(SceneCanvas canvas, int width, long t) {
        float bob = (float) Math.sin(ticks / 30.0) * 2;
        String title = "FLAPPY TAILS";
        float scale = 2f;
        float nameWidth = font.width(title) * scale;
        float restX = width - 18 - nameWidth;
        float nameX = Math.max(restX, width + 8 - (t - 4) * 16f);
        SceneSpriteSet card = cards[0];
        if (card != null) {
            float bannerY = Math.min(112, -112 + t * 16f);
            canvas.draw(card.frame(0), 38, bannerY, SceneDraw.plain());
        }
        if (font.canSpell(title)) {
            font.draw(canvas, title, nameX, 14 + bob, scale, 99, SceneDraw.plain());
        } else {
            cardWord(canvas, title, nameX + nameWidth / 2, 14 + bob, scale, 99);
        }
        if (card != null) {
            SceneSprite zone = card.frame(2);
            float zoneX = Math.max(width - 18 - zone.width() + zone.originX(), width + 40 - (t - 10) * 16f);
            canvas.draw(zone, zoneX, 66 + bob, SceneDraw.plain());
        }
        if (t > 24) {
            float alpha = Math.min(1f, (t - 24) / 20f);
            String tag = "THE ROM'S OWN FLIGHT. ONE BUTTON. NO MERCY.";
            Hud.outlined(canvas, tag, width - 18 - Hud.width(tag, 1), 98, 1,
                    Math.round(alpha * 255) << 24 | Hud.GOLD);
        }
    }

    /** Eggman, who put the pillars up, crossing the sky now and then in his Egg Mobile. */
    private void drawEggman(SceneCanvas canvas, int width) {
        long cycle = ticks % 1200;
        if (cycle > 420) return;
        float x = width + 40 - cycle * 1.2f;
        float y = 150 + (float) Math.sin(cycle / 24.0) * 6;
        SceneDraw small = SceneDraw.plain().withScale(0.75f);
        if ((ticks / 2) % 2 == 0) canvas.draw(eggman.frame(6), x + 24, y - 6, small);           // exhaust flame
        canvas.draw(eggman.frame((ticks / 16) % 2 == 0 ? 0 : 1), x, y - 21, small);             // Robotnik
        canvas.draw(eggman.frame(5), x, y, small);                                              // the Egg Mobile
    }

    // ---- results ----

    private void drawResults(SceneContext ctx, SceneCanvas canvas) {
        int width = ctx.width();
        long t = screenTick;
        drawWorld(canvas, run, width);
        canvas.fill(0, 0, width, canvas.height(), Math.min(150, (int) t * 4) << 24 | 0x000010);
        // GAME from the left, OVER from the right, meeting in the middle, as the S3K game over does.
        float meet = Math.min(1f, t / 24f);
        if (font.canSpell("GAME OVER")) {
            float gameW = font.width("GAME") * 1.5f;
            float leftX = -gameW + (width / 2f - 6 - gameW + gameW) * meet;
            float rightX = width + (width / 2f + 6 - width) * meet;
            font.draw(canvas, "GAME", leftX, 22, 1.5f, 99, SceneDraw.plain());
            font.draw(canvas, "OVER", rightX, 22, 1.5f, 99, SceneDraw.plain());
        } else {
            cardWord(canvas, "GAME OVER", width / 2f, 22, 1.5f, 99);
        }
        if (t < 40) return;
        float rise = Math.max(0, 30 - (t - 40)) * 4;
        int px = width / 2 - 120;
        int py = 70 + Math.round(rise);
        Hud.panel(canvas, px, py, 240, 112, 0.9f);
        Hud.outlined(canvas, "SCORE", px + 96, py + 10, 1, 0xFF000000 | Hud.GOLD);
        hud.score(canvas, shownScore, px + 170, py + 6, 2);
        Hud.outlined(canvas, "BEST", px + 96, py + 46, 1, 0xFF000000 | Hud.GOLD);
        String best = run.practice ? "-" : Integer.toString(records.best(run.mode));
        Hud.outlined(canvas, best, px + 170 - Hud.width(best, 2) / 2, py + 42, 2, 0xFFFFFFFF);
        Hud.outlined(canvas, "RINGS " + run.ringsTaken(), px + 96, py + 70, 1, 0xFFFFFFFF);
        Zone reached = Zone.at(Math.max(0, run.firstGate + run.score()));
        Hud.outlined(canvas, "FELL IN " + reached.name() + (reached.act() == 1 ? " 2" : ""), px + 96, py + 82, 1, 0xFFC0C8E0);
        Hud.outlined(canvas, "HIT " + run.crashCause(), px + 96, py + 94, 1, 0xFFC0C8E0);
        // The medal, stamped once the tally is done.
        Hud.outlined(canvas, "MEDAL", px + 14, py + 10, 1, 0xFF000000 | Hud.GOLD);
        boolean stamped = shownScore >= run.score() && t >= tallyEnd() + 10;
        int tier = stamped ? resultMedal : 0;
        float stamp = stamped ? Math.max(1f, 2.2f - (t - tallyEnd() - 10) * 0.12f) : 1f;
        hud.medal(canvas, tier, px + 44, py + 52, ticks, stamp);
        Hud.outlined(canvas, run.practice ? "PRACTICE" : Hud.medalName(tier), px + 44 - Hud.width(run.practice ? "PRACTICE" : Hud.medalName(tier), 1) / 2,
                py + 86, 1, 0xFF000000 | Hud.medalColor(tier, ticks));
        if (newBest && stamped && (ticks / 10) % 2 == 0) {
            Hud.centred(canvas, "NEW BEST!", py + 120, 2, Hud.GOLD);
        }
        if (t > 60) {
            Hud.centred(canvas, "A: FLY AGAIN     B: TITLE", 208, 1, Hud.WHITE);
        }
    }

    // ---- records ----

    private void drawRecords(SceneContext ctx, SceneCanvas canvas) {
        int width = ctx.width();
        drawWorld(canvas, demo, width);
        canvas.fill(0, 0, width, canvas.height(), 0x90000010);
        cardWord(canvas, "RECORDS", width / 2f, 14, 1.25f, 99);
        int px = width / 2 - 150;
        int py = 56;
        Hud.panel(canvas, px, py, 300, 130, 0.9f);
        int row = py + 12;
        for (Mode m : Mode.values()) {
            int best = records.best(m);
            int tier = Hud.medal(best);
            hud.medal(canvas, tier, px + 26, row + 10, ticks, 0.5f);
            Hud.outlined(canvas, m.label, px + 48, row + 2, 1, 0xFF000000 | Hud.GOLD);
            Hud.outlined(canvas, "BEST " + best + "   " + Hud.medalName(tier), px + 48, row + 14, 1, 0xFFFFFFFF);
            row += 34;
        }
        Zone furthest = Zone.tour().get(records.furthestZone());
        Hud.outlined(canvas, "FURTHEST  " + furthest.name() + (furthest.act() == 1 ? " 2" : ""), px + 20, row + 2, 1, 0xFFFFFFFF);
        Hud.outlined(canvas, "FLIGHTS " + records.flights() + "   GATES " + records.gates() + "   RINGS " + records.rings(),
                px + 20, row + 16, 1, 0xFFFFFFFF);
        Hud.outlined(canvas, "MEDALS: A RING FOR EVERY TEN GATES, UP TO SUPER AT FIFTY", px + 20, row + 34, 1, 0xFFA8B0C8);
        Hud.centred(canvas, "B: BACK", 208, 1, Hud.WHITE);
    }

    // =====================================================================================

    @Override
    public void exit(SceneContext ctx) {
        ctx.audio().stopMusic();
        if (cache != null) cache.close();
    }

    /**
     * Debug commands, for tests and capture tools:
     * <ul>
     *   <li>{@code title}, {@code records};</li>
     *   <li>{@code play} or {@code play:<mode>} ({@code classic}, {@code sonic}) or
     *       {@code play:<mode>:<zone>} with a tour stop 0-4, starting a run there (practice
     *       when past the first);</li>
     *   <li>{@code seed:<number>}: the next run's course seed (decimal or 0x hex);</li>
     *   <li>{@code autopilot:on}, {@code autopilot:off}, {@code daring:<percent>};</li>
     *   <li>{@code rings:<count>}: set the current run's rings;</li>
     *   <li>{@code results}: end the current run now.</li>
     * </ul>
     */
    @Override
    public boolean debugJump(String command) {
        String[] parts = command.trim().toLowerCase(java.util.Locale.ROOT).split(":");
        switch (parts[0]) {
            case "title" -> {
                show(Screen.TITLE);
                screenTick = 60;
                return true;
            }
            case "records" -> {
                show(Screen.RECORDS);
                return true;
            }
            case "seed" -> {
                if (parts.length < 2) return false;
                try {
                    Long.decode(parts[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
                pendingSeed = parts[1];
                return true;
            }
            case "autopilot" -> {
                autopilot = parts.length < 2 || parts[1].equals("on");
                return true;
            }
            case "daring" -> {
                if (parts.length < 2) return false;
                try {
                    pilot.setDaring(Integer.parseInt(parts[1]));
                } catch (NumberFormatException e) {
                    return false;
                }
                return true;
            }
            case "rings" -> {
                if (run == null || parts.length < 2) return false;
                try {
                    run.debugSetRings(Integer.parseInt(parts[1]));
                } catch (NumberFormatException e) {
                    return false;
                }
                return true;
            }
            case "results" -> {
                if (run == null) return false;
                screen = Screen.PLAY;
                pendingResults = true;
                return true;
            }
            case "play" -> {
                Mode chosen = mode;
                if (parts.length >= 2) {
                    if (parts[1].equals("classic")) chosen = Mode.CLASSIC;
                    else if (parts[1].equals("sonic")) chosen = Mode.SONIC;
                    else return false;
                }
                int zone = 0;
                if (parts.length >= 3) {
                    try {
                        zone = Integer.parseInt(parts[2]);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (zone < 0 || zone >= Zone.TOUR_LENGTH) return false;
                }
                mode = chosen;
                pendingPlay = zone;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // Debug requests that need the scene context are carried out on the next update.
    private int pendingPlay = -1;
    private boolean pendingResults;

    /** Applies debug requests that need audio or storage; called at the top of every update. */
    private void applyPending(SceneContext ctx) {
        if (pendingPlay >= 0) {
            int zone = pendingPlay;
            pendingPlay = -1;
            startRun(ctx, zone * Zone.GATES, zone > 0);
        }
        if (pendingResults && run != null) {
            pendingResults = false;
            finishRun(ctx);
        }
    }
}
