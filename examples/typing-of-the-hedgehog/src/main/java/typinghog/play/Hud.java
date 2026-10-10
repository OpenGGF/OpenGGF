package typinghog.play;

import com.openggf.game.GameServices;
import com.openggf.mods.ui.LevelOverlayCanvas;
import com.openggf.mods.ui.PixelCanvas;
import typinghog.art.KeyboardPack;
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
    private final String[] pauseItems = {"RESUME", "RETRY", "QUIT"};
    static final int TALLY_ROWS = 420;

    private final CourseAssets assets;
    private final Plates plates;
    private final KeyboardPack keyboard = new KeyboardPack();
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
        rankCalled = false;
        course.say(typinghog.audio.VoiceLine.ANNOUNCER_ACT_CLEAR);
    }

    private boolean rankCalled;

    boolean tallyDone(int rows) { return rows >= TALLY_ROWS; }

    private final StringBuilder pauseTyped = new StringBuilder();

    /** Arrows or a typed prefix pick an item; Enter chooses it. */
    void pauseInput(Course course, String typed, boolean enter, boolean up, boolean down) {
        if (up) pauseIndex = Math.floorMod(pauseIndex - 1, pauseItems.length);
        if (down) pauseIndex = Math.floorMod(pauseIndex + 1, pauseItems.length);
        pauseTyped.append(typed.strip().toUpperCase());
        if (pauseTyped.length() > 8) pauseTyped.delete(0, pauseTyped.length() - 8);
        for (int i = 0; i < pauseItems.length; i++) {
            if (pauseTyped.length() > 0 && pauseItems[i].startsWith(pauseTyped.toString())) pauseIndex = i;
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
        drawBackpack(canvas, course, cameraX, cameraY);
        if (course.phase() == Course.Phase.RUN) drawRunTags(canvas, course, cameraX, cameraY);
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
        drawCountdown(canvas, course, width);
        if (course.paused()) drawPause(canvas, width, height);
        if (course.intent().demo() && (presented / 32) % 2 == 0) {
            String demo = "DEMO - PRESS ANY KEY";
            font.shadowed(canvas, demo, (width - font.width(demo)) / 2, 206, GOLD, SHADOW);
        }
        if (flashRows > 0) {
            flashRows--;
            canvas.fill(0, 0, width, height, (flashColour & 0x00FFFFFF) | ((flashRows * 18) << 24));
        }
        if (shakeRows > 0) shakeRows--;
    }

    private void drawPlates(PixelCanvas canvas, Course course, Encounter encounter, int cameraX, int cameraY) {
        TypingField field = course.field();
        boolean punctuation = course.intent().punctuation();
        for (Pickup pickup : encounter.pickups()) {
            var target = field.target(pickup.targetId);
            if (target == null) continue;
            boolean locked = field.locked() == pickup.targetId;
            Plates.Style style = locked ? Plates.Style.LOCKED : switch (pickup.kind) {
                case DECOY -> Plates.Style.DECOY;
                case RESCUE -> Plates.Style.RESCUE;
                default -> Plates.Style.POWER;
            };
            int sx = pickup.visual.x - cameraX, sy = pickup.visual.y - cameraY - 18;
            if (pickup.kind.monitor() && !pickup.visual.visible) {
                // No ROM monitor art mapped for this game yet: a plain box stands in.
                canvas.fill(sx - 8, sy + 2, 16, 16, 0xFF808898);
                canvas.fill(sx - 6, sy + 4, 12, 10, style.rimColour());
            }
            plates.draw(canvas, target.prompt(), target.progress(), style, sx, sy, punctuation, 0);
        }
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

    /**
     * Sonic's keyboard backpack (the Dreamcast nod), worn on his back while he stands or runs and
     * hidden while he is a spinning ball. Drawn as runs of fills from the shipped pixel art.
     */
    private void drawBackpack(PixelCanvas canvas, Course course, int cameraX, int cameraY) {
        keyboard.tick();
        if (!(GameServices.camera().getFocusedSprite() instanceof com.openggf.sprites.playable.AbstractPlayableSprite sonic)) return;
        if (sonic.getRolling() || sonic.getAir() || sonic.isHidden()) return;
        boolean left = sonic.getDirection() == com.openggf.physics.Direction.LEFT;
        // Strapped along his back, its top at his head, its bottom near his knees, with a strap
        // across his chest.
        int cx = sonic.getCentreX() - cameraX, cy = sonic.getCentreY() - cameraY;
        int x = cx + (left ? 10 : -10 - KeyboardPack.WIDTH);
        int y = cy - 20;
        keyboard.draw(canvas, x, y, left);
        int strapTop = left ? x : x + KeyboardPack.WIDTH - 1;
        for (int i = 0; i < 12; i++) {
            int sx = strapTop + (left ? -i : i);
            canvas.fill(sx, y + 3 + i, 1, 1, 0xFF201810);
        }
    }

    /** The "3, 2, 1" start, each count shrinking from large as its beat runs, then START! into the run. */
    private void drawCountdown(PixelCanvas canvas, Course course, int width) {
        if (course.phase() == Course.Phase.COUNTDOWN) {
            int beat = course.phaseRows() / Course.COUNTDOWN_BEAT;
            int into = course.phaseRows() % Course.COUNTDOWN_BEAT;
            if (beat > 2) return;
            String count = Integer.toString(3 - beat);
            int scale = into < 6 ? 8 - into / 2 : 6;
            int w = font.width(count) * scale, h = PlateFont.HEIGHT * scale;
            drawScaled(canvas, count, (width - w) / 2, (224 - h) / 2 - 16, scale, into < 6 ? WHITE : GOLD);
        } else if (startRows > 0) {
            startRows--;
            int scale = 4;
            int w = font.width("START!") * scale, h = PlateFont.HEIGHT * scale;
            if ((startRows / 4) % 2 == 0 || startRows > 24) {
                drawScaled(canvas, "START!", (width - w) / 2, (224 - h) / 2 - 16, scale, GOLD);
            }
        }
    }

    /** Shows START! as the run begins. */
    void start() { startRows = 50; }

    private int startRows;

    /** A key the player pressed lights on Sonic's keyboard. */
    void keyPressed(int codePoint, boolean hit) { keyboard.press(codePoint, hit); }


    private void drawRunTags(PixelCanvas canvas, Course course, int cameraX, int cameraY) {
        TypingField field = course.field();
        boolean punctuation = course.intent().punctuation();
        for (RunTags.Tag tag : course.runTags().tags()) {
            int sx = tag.x - cameraX, sy = tag.y - cameraY;
            if (tag.tagged) {
                int pulse = (presented / 4) % 2;
                canvas.fill(sx - 1, sy - 26 - pulse, 3, 3, GOLD);
                canvas.fill(sx - 3, sy - 25 - pulse, 7, 1, GOLD);
                continue;
            }
            var target = field.target(tag.targetId);
            if (target == null) continue;
            boolean locked = field.locked() == tag.targetId;
            plates.draw(canvas, target.prompt(), target.progress(), locked ? Plates.Style.LOCKED : Plates.Style.RESCUE,
                    sx, sy - 18, punctuation, 0);
        }
        for (int[] burst : course.runTags().bursts()) {
            int r = burst[2], sx = burst[0] - cameraX, sy = burst[1] - cameraY;
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                canvas.fill(sx + (int) (Math.cos(a) * r), sy + (int) (Math.sin(a) * r), 2, 2, GOLD);
            }
            if (r < 24) font.shadowed(canvas, "+" + RunTags.SMASH_BONUS, sx - 9, sy - 20 - r / 2, GOLD, SHADOW);
        }
    }

    private void drawBoss(PixelCanvas canvas, Course course, Boss boss, int width) {
        TypingField field = course.field();
        boolean punctuation = course.intent().punctuation();
        int[] targets = boss.openTargets();
        if (!boss.question().isEmpty()) {
            // Below the score panel, wrapped to the screen: trivia runs longer than 320 pixels.
            java.util.List<String> lines = wrap(boss.question(), width - 32);
            int lineHeight = PlateFont.HEIGHT + 3;
            int top = 46;
            canvas.fill(10, top - 4, width - 20, lines.size() * lineHeight + 6, 0xC0201040);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                font.shadowed(canvas, line, (width - font.width(line)) / 2, top + i * lineHeight, WHITE, SHADOW);
            }
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

    /** Greedy word wrap of {@code text} to lines no wider than {@code max} pixels. */
    private java.util.List<String> wrap(String text, int max) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(candidate) > max) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
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
        drawScaled(canvas, text, x, y, 2, colour);
    }

    /** {@code text} at {@code scale} times the plate font, top-left at {@code x, y}, with a drop shadow. */
    private void drawScaled(PixelCanvas canvas, String text, int x, int y, int scale, int colour) {
        var big = new PixelCanvas() {
            int ink;
            @Override public int width() { return canvas.width(); }
            @Override public int height() { return canvas.height(); }
            @Override public void fill(int fx, int fy, int w, int h, int argb) {
                canvas.fill(x + (fx - x) * scale, y + (fy - y) * scale, w * scale, h * scale, ink);
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
            if (!rankCalled) {
                rankCalled = true;
                course.say(switch (tally.grade()) {
                    case "S" -> typinghog.audio.VoiceLine.ANNOUNCER_RANK_S;
                    case "A" -> typinghog.audio.VoiceLine.ANNOUNCER_RANK_A;
                    case "B" -> typinghog.audio.VoiceLine.ANNOUNCER_RANK_B;
                    case "C" -> typinghog.audio.VoiceLine.ANNOUNCER_RANK_C;
                    case "D" -> typinghog.audio.VoiceLine.ANNOUNCER_RANK_D;
                    default -> typinghog.audio.VoiceLine.ANNOUNCER_RANK_E;
                });
                if (tally.perfectBonus() > 0) course.say(typinghog.audio.VoiceLine.ANNOUNCER_PERFECT);
            }
            String grade = "RANK " + tally.grade();
            drawBig(canvas, grade, (width - font.width(grade) * 2) / 2, 156, tally.grade().equals("S") ? GOLD : WHITE);
        }
    }

    private void drawPause(PixelCanvas canvas, int width, int height) {
        canvas.fill(0, 0, width, height, 0x90000000);
        drawBig(canvas, "PAUSED", (width - font.width("PAUSED") * 2) / 2, 70, WHITE);
        for (int i = 0; i < pauseItems.length; i++) {
            String item = (i == pauseIndex ? "> " : "  ") + pauseItems[i];
            font.shadowed(canvas, item, width / 2 - 30, 104 + i * 14, i == pauseIndex ? GOLD : WHITE, SHADOW);
        }
        font.shadowed(canvas, "TYPE A WORD, THEN ENTER", width / 2 - 66, 160, 0xFF9090A0, SHADOW);
    }
}
