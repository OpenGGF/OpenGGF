package typinghog.play;

import com.openggf.game.GameServices;
import com.openggf.mods.ui.LevelOverlayCanvas;
import com.openggf.mods.ui.PixelCanvas;
import typinghog.art.PlateFont;
import typinghog.art.Plates;
import typinghog.core.Scorecard;
import typinghog.core.TypingField;

/**
 * Everything the course draws over the level: word plates and the lock-on reticle, sparks and
 * letter-rings, the score panel, banners, the pause menu and the act tally. Screen-space fills
 * only; presentation timers advance on every presented row (including held ones) so banners and
 * the tally animate while the world is held.
 */
public final class Hud {
    static final int GOLD = 0xFFFFD000;
    static final int WHITE = 0xFFFFFFFF;
    static final int SHADOW = 0xFF000000;
    static final int RING = 0xFFFFC020;
    static final int RING_DARK = 0xFFB07000;
    static final String[] PAUSE_ITEMS = {"RESUME", "RETRY", "QUIT"};
    static final int TALLY_ROWS = 420;

    private final CourseAssets assets;
    private final Plates plates;
    private final PlateFont font;
    private String banner = "";
    private int bannerColour;
    private int bannerRows;
    private int flashColour;
    private int flashRows;
    private int shakeRows;
    private int pauseIndex;
    private Scorecard.Tally tally;
    private int presented;

    Hud(CourseAssets assets) {
        this.assets = assets;
        this.plates = assets.plates();
        this.font = assets.font();
    }

    void banner(String text, int colour) {
        banner = text;
        bannerColour = colour;
        bannerRows = 90;
    }

    void flash(int colour, int rows) {
        flashColour = colour;
        flashRows = rows;
    }

    void shake() { shakeRows = 6; }

    void beginTally(Course course) {
        tally = course.card().tally(Math.max(0, course.rings()));
    }

    boolean tallyDone(int rows) { return rows >= TALLY_ROWS; }

    private final StringBuilder pauseTyped = new StringBuilder();

    /** Arrows or a typed prefix pick an item; Enter chooses it. */
    void pauseInput(Course course, String typed, boolean enter, boolean up, boolean down) {
        if (up) pauseIndex = Math.floorMod(pauseIndex - 1, PAUSE_ITEMS.length);
        if (down) pauseIndex = Math.floorMod(pauseIndex + 1, PAUSE_ITEMS.length);
        pauseTyped.append(typed.strip().toUpperCase());
        if (pauseTyped.length() > 8) pauseTyped.delete(0, pauseTyped.length() - 8);
        for (int i = 0; i < PAUSE_ITEMS.length; i++) {
            if (pauseTyped.length() > 0 && PAUSE_ITEMS[i].startsWith(pauseTyped.toString())) pauseIndex = i;
        }
        if (enter) {
            pauseTyped.setLength(0);
            choose(course);
        }
    }

    private void choose(Course course) {
        switch (pauseIndex) {
            case 1 -> course.retryStand();
            case 2 -> course.quit();
            default -> { }
        }
    }

    // ---- drawing --------------------------------------------------------------------------------

    void draw(Course course) {
        presented++;
        var camera = GameServices.camera();
        int width = camera.getWidth();
        int height = 224;
        PixelCanvas canvas = new LevelOverlayCanvas(GameServices.graphics(), width, height);
        int cameraX = camera.getX(), cameraY = camera.getY();
        Encounter encounter = course.encounter();
        if (encounter != null) {
            drawRings(canvas, encounter.effects(), cameraX, cameraY);
            drawPlates(canvas, course, encounter, cameraX, cameraY);
            drawSparks(canvas, encounter.effects(), cameraX, cameraY);
            if (encounter.bossFight() != null) drawBoss(canvas, course, encounter.bossFight(), width);
        }
        drawPanel(canvas, course, width);
        if (bannerRows > 0) {
            bannerRows--;
            int w = font.width(banner) * 2;
            int x = (width - w) / 2;
            int y = 72 - Math.max(0, bannerRows - 80) * 3;
            canvas.fill(0, y - 4, width, PlateFont.HEIGHT * 2 + 8, 0x90000000);
            drawBig(canvas, banner, x, y, bannerColour);
        }
        if (course.phase() == Course.Phase.TALLY && tally != null) drawTally(canvas, course, width, course.phaseRows());
        if (course.paused()) drawPause(canvas, width, height);
        if (flashRows > 0) {
            flashRows--;
            canvas.fill(0, 0, width, height, (flashColour & 0x00FFFFFF) | ((flashRows * 18) << 24));
        }
        if (shakeRows > 0) shakeRows--;
    }

    private void drawPlates(PixelCanvas canvas, Course course, Encounter encounter, int cameraX, int cameraY) {
        TypingField field = course.field();
        boolean punctuation = course.intent().punctuation();
        for (Enemy enemy : encounter.enemies()) {
            for (Enemy.Part part : enemy.parts()) {
                var target = part.targetId() >= 0 ? field.target(part.targetId()) : null;
                if (target == null || !target.typeable()) continue;
                int sx = part.visual.x - cameraX;
                int sy = part.visual.y - cameraY - enemy.species().halfHeight() - 4;
                Plates.Style style = encounter.styleFor(course, enemy, part);
                boolean locked = field.locked() == part.targetId();
                plates.draw(canvas, target.prompt(), target.progress(), style, sx, sy, punctuation,
                        locked && shakeRows > 0 ? (shakeRows % 2 == 0 ? 2 : -2) : 0);
                if (locked) Plates.reticle(canvas, sx, part.visual.y - cameraY,
                        Math.max(enemy.species().halfWidth(), enemy.species().halfHeight()) + 4, presented, GOLD);
            }
        }
    }

    private void drawBoss(PixelCanvas canvas, Course course, Boss boss, int width) {
        TypingField field = course.field();
        boolean punctuation = course.intent().punctuation();
        int[] targets = boss.openTargets();
        if (!boss.question().isEmpty()) {
            int w = font.width(boss.question());
            canvas.fill((width - w) / 2 - 6, 30, w + 12, PlateFont.HEIGHT + 8, 0xC0201040);
            font.shadowed(canvas, boss.question(), (width - w) / 2, 34, WHITE, SHADOW);
        }
        for (int i = 0; i < targets.length; i++) {
            var target = field.target(targets[i]);
            if (target == null) continue;
            int cx = targets.length == 1 ? boss.x() - GameServices.camera().getX()
                    : width * (i + 1) / (targets.length + 1);
            int bottom = targets.length == 1 ? boss.y() - GameServices.camera().getY() - 28 : 120;
            boolean locked = field.locked() == targets[i];
            plates.draw(canvas, target.prompt(), target.progress(), locked ? Plates.Style.LOCKED : Plates.Style.BOSS,
                    cx, bottom, punctuation, 0);
        }
        if (boss.windowRows() > 0 && targets.length > 0) {
            int bar = Math.min(width - 40, boss.windowRows() / 2);
            canvas.fill(20, 214, bar, 3, boss.windowRows() < 90 ? 0xFFFF4040 : GOLD);
        }
        for (int i = 0; i < Boss.HITS; i++) {
            canvas.fill(width - 12 - i * 8, 20, 6, 6, i < Boss.HITS - boss.hits() ? 0xFFFF4060 : 0x60FFFFFF);
        }
    }

    private void drawSparks(PixelCanvas canvas, Effects effects, int cameraX, int cameraY) {
        for (Effects.Spark spark : effects.sparks) {
            int t = spark.age;
            int x = spark.x0 + (spark.x1 - spark.x0) * t / Effects.SPARK_ROWS - cameraX;
            int y = spark.y0 + (spark.y1 - spark.y0) * t / Effects.SPARK_ROWS - cameraY;
            canvas.fill(x - 1, y - 1, 3, 3, RING);
            canvas.fill(x, y, 1, 1, WHITE);
        }
    }

    private void drawRings(PixelCanvas canvas, Effects effects, int cameraX, int cameraY) {
        for (Effects.LetterRing ring : effects.rings) {
            if (ring.life < 60 && (ring.life & 4) != 0) continue;
            int x = ring.px() - cameraX, y = ring.py() - cameraY;
            canvas.fill(x - 4, y - 7, 8, 1, RING);
            canvas.fill(x - 4, y + 6, 8, 1, RING_DARK);
            canvas.fill(x - 6, y - 5, 1, 10, RING);
            canvas.fill(x + 5, y - 5, 1, 10, RING_DARK);
            canvas.fill(x - 5, y - 6, 10, 12, 0xA0000000);
            font.glyph(canvas, ring.letter, x - 2, y - 5, WHITE);
        }
    }

    private void drawPanel(PixelCanvas canvas, Course course, int width) {
        Scorecard card = course.card();
        label(canvas, "SCORE", 12, 8);
        number(canvas, Long.toString(card.score()), 60, 8);
        label(canvas, "RINGS", 12, 20);
        number(canvas, Integer.toString(Math.max(0, course.rings())),
                60, 20, course.rings() <= 0 && (presented & 16) != 0 ? 0xFFFF4040 : WHITE);
        label(canvas, "WPM", 12, 32);
        number(canvas, Integer.toString((int) Math.round(card.wpm())), 60, 32);
        String chain = card.chain() >= 2 ? card.chain() + " CHAIN  x" + String.format("%.2f", Scorecard.multiplier(card.chain())) : "";
        if (!chain.isEmpty()) font.shadowed(canvas, chain, width - font.width(chain) - 12, 8, GOLD, SHADOW);
        String lives = "SONIC x" + Math.max(0, course.lives());
        font.shadowed(canvas, lives, 12, 206, WHITE, SHADOW);
        String accuracy = Math.round(card.accuracy() * 100) + "%";
        font.shadowed(canvas, accuracy, width - font.width(accuracy) - 12, 206, WHITE, SHADOW);
    }

    private void label(PixelCanvas canvas, String text, int x, int y) {
        font.shadowed(canvas, text, x, y, GOLD, SHADOW);
    }

    private void number(PixelCanvas canvas, String text, int right, int y) {
        number(canvas, text, right, y, WHITE);
    }

    private void number(PixelCanvas canvas, String text, int x, int y, int colour) {
        font.shadowed(canvas, text, x, y, colour, SHADOW);
    }

    /** Text at double scale for banners and the tally title. */
    private void drawBig(PixelCanvas canvas, String text, int x, int y, int colour) {
        var big = new PixelCanvas() {
            int ink;
            @Override public int width() { return canvas.width(); }
            @Override public int height() { return canvas.height(); }
            @Override public void fill(int fx, int fy, int w, int h, int argb) {
                canvas.fill(x + (fx - x) * 2, y + (fy - y) * 2, w * 2, h * 2, ink);
            }
        };
        big.ink = SHADOW;
        font.draw(big, text, x + 1, y + 1, SHADOW);
        big.ink = colour;
        font.draw(big, text, x, y, colour);
    }

    private void drawTally(PixelCanvas canvas, Course course, int width, int rows) {
        canvas.fill(0, 40, width, 140, 0xB0000020);
        String title = "SONIC TYPED THROUGH";
        drawBig(canvas, title, (width - font.width(title) * 2) / 2, 50, GOLD);
        String[] labels = {"SPEED BONUS", "ACCURACY BONUS", "RING BONUS", "RESCUE BONUS", "PERFECT BONUS"};
        long[] values = {tally.speedBonus(), tally.accuracyBonus(), tally.ringBonus(), tally.rescueBonus(), tally.perfectBonus()};
        for (int i = 0; i < labels.length; i++) {
            if (rows < 40 + i * 30) break;
            int y = 80 + i * 14;
            font.shadowed(canvas, labels[i], width / 2 - 110, y, GOLD, SHADOW);
            long shown = Math.min(values[i], Math.max(0, (rows - 40 - i * 30)) * Math.max(1, values[i] / 30));
            String v = Long.toString(shown);
            font.shadowed(canvas, v, width / 2 + 110 - font.width(v), y, WHITE, SHADOW);
        }
        if (rows > 220) {
            String grade = "RANK " + tally.grade();
            drawBig(canvas, grade, (width - font.width(grade) * 2) / 2, 156, tally.grade().equals("S") ? GOLD : WHITE);
        }
    }

    private void drawPause(PixelCanvas canvas, int width, int height) {
        canvas.fill(0, 0, width, height, 0x90000000);
        drawBig(canvas, "PAUSED", (width - font.width("PAUSED") * 2) / 2, 70, WHITE);
        for (int i = 0; i < PAUSE_ITEMS.length; i++) {
            String item = (i == pauseIndex ? "> " : "  ") + PAUSE_ITEMS[i];
            font.shadowed(canvas, item, width / 2 - 30, 104 + i * 14, i == pauseIndex ? GOLD : WHITE, SHADOW);
        }
        font.shadowed(canvas, "TYPE A WORD, THEN ENTER", width / 2 - 66, 160, 0xFF9090A0, SHADOW);
    }
}
