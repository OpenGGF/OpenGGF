package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import starpost.art.Anim;
import starpost.core.Game;
import starpost.scene.Sfx;
import starpost.ui.Text;

/**
 * Scrap Brain Night's haunted maze, seen from above: Scrap Brain Zone's deck plate and piped
 * walls (cut from its act 1 blocks) in the dark, with only a little light round the farmer. Rings
 * and scrap wait in the dead ends; Caterkiller shadows with red eyes crawl the halls and send the
 * farmer jumping back, dropping rings. At the far end, in the dark, a silhouette: Sonic 3 &amp;
 * Knuckles' Mecha Sonic. Ninety seconds before the lights come up.
 */
final class MazeScreen extends FestivalScreen {
    private static final int S1_SBZ = 0x86;            // bgm_SBZ
    private static final int S3K_BOSS = 0x19;          // Sonic3kMusic BOSS
    private static final int CELL = 40;
    private static final int WALL = 8;
    private static final float WALK = 1.5f;
    private static final float RUN = 2.6f;
    private static final int RADIUS = 7;
    private static final int LIGHT = 58;
    /** Map_MechaSonic: standing, and the lunge. */
    private static final int MECHA_STAND = 9;
    private static final int MECHA_LUNGE = 12;

    private Maze maze;
    private float x;
    private float y;
    private boolean facingLeft;
    private boolean moving;
    private final Anim anim = new Anim();
    private int rings;
    private int scrap;
    private final boolean[] looted = new boolean[Maze.COLUMNS * Maze.ROWS];
    private final List<float[]> lurkers = new ArrayList<>();
    private int scared;
    /** Rings dropped by the last fright (shown over the farmer while it lasts). */
    private int eek;
    private int left = Maze.TICKS;
    private int revealAt = -1;
    private boolean finished;
    private float camX;
    private float camY;

    MazeScreen(FestivalSystem sys, Festival festival) {
        super(sys, festival);
    }

    @Override
    void begin() {
        Game game = shell.game;
        maze = new Maze(game.calendar.year() * 131 + game.farmName.hashCode());
        x = CELL / 2f;
        y = maze.startRow * CELL + CELL / 2f;
        for (int c : maze.lurkers) {
            lurkers.add(new float[] {Maze.column(c) * CELL + CELL / 2f, Maze.row(c) * CELL + CELL / 2f, c, 1});
        }
        buildWalls();
        camX = x - shell.width() / 2f;
        camY = y - 110;
        shell.music.want("s1", S1_SBZ);
    }

    @Override
    void step() {
        if (t == 10) {
            caption("robotnik", "WELCOME TO MY HAUNTED SCRAP BRAIN. NINETY SECONDS TO FIND THE WAY OUT. "
                    + "IT IS VERY DARK. I MAY HAVE OVERDONE IT.");
        }
        if (revealAt >= 0) {
            int since = t - revealAt;
            if (since == 1) {
                shell.music.want("s3k", S3K_BOSS);
                shell.sfx(Sfx.ERROR);
            }
            if (since == 70) {
                caption("robotnik", "HO HO HO! SCARED? IT'S AN ANIMATRONIC. PROBABLY. I'M ALMOST CERTAIN I BUILT IT.");
            }
            if (since == 260) {
                end();
            }
            return;
        }
        if (--left <= 0) {
            caption("robotnik", "TIME'S UP! THE LIGHTS ARE COMING ON. THE THING AT THE END WILL HAVE TO WAIT. FOR YOU.");
            revealAt = t;
            return;
        }
        if (scared > 0) {
            scared--;
        }
        move();
        loot();
        for (float[] l : lurkers) {
            stepLurker(l);
        }
        int exitX = (Maze.COLUMNS - 1) * CELL + CELL / 2, exitY = maze.exitRow * CELL + CELL / 2;
        if (Math.abs(x - exitX) < 22 && Math.abs(y - exitY) < 22) {
            finished = true;
            revealAt = t;
        }
        camX += (x - shell.width() / 2f - camX) * 0.15f;
        camY += (y - 110 - camY) * 0.15f;
    }

    private void move() {
        var in = shell.in;
        float speed = scared > 40 ? 0 : in.jumpHeld ? RUN : WALK;
        float dx = (in.right ? 1 : 0) - (in.left ? 1 : 0), dy = (in.down ? 1 : 0) - (in.up ? 1 : 0);
        moving = (dx != 0 || dy != 0) && speed > 0;
        if (dx != 0) {
            facingLeft = dx < 0;
        }
        x = slide(x, y, dx * speed, true);
        y = slide(y, x, dy * speed, false);
        anim.set(moving ? speed > WALK ? Anim.RUN : Anim.WALK : Anim.WAIT, moving ? 5 : 6);
        anim.tick();
    }

    /** Moves along one axis unless the farmer's box would overlap a wall (or leave the maze). */
    private float slide(float along, float across, float delta, boolean horizontal) {
        if (delta == 0) {
            return along;
        }
        float next = along + delta;
        float px = horizontal ? next : across, py = horizontal ? across : next;
        if (px < RADIUS || py < RADIUS || px > Maze.COLUMNS * CELL - RADIUS || py > Maze.ROWS * CELL - RADIUS) {
            return along;
        }
        for (int[] w : walls) {
            if (px + RADIUS > w[0] && px - RADIUS < w[0] + w[2] && py + RADIUS > w[1] && py - RADIUS < w[1] + w[3]) {
                return along;
            }
        }
        return next;
    }

    /** Every closed wall as a rectangle {x, y, w, h} in maze pixels (the corner posts come with them). */
    private void buildWalls() {
        for (int r = 0; r < Maze.ROWS; r++) {
            for (int c = 0; c < Maze.COLUMNS; c++) {
                int cell = Maze.cell(c, r);
                int px = c * CELL, py = r * CELL;
                if (!maze.open(cell, Maze.NORTH)) {
                    walls.add(new int[] {px - WALL / 2, py - WALL / 2, CELL + WALL, WALL});
                }
                if (!maze.open(cell, Maze.WEST) && !(c == 0 && r == maze.startRow)) {
                    walls.add(new int[] {px - WALL / 2, py - WALL / 2, WALL, CELL + WALL});
                }
                if (r == Maze.ROWS - 1) {
                    walls.add(new int[] {px - WALL / 2, py + CELL - WALL / 2, CELL + WALL, WALL});
                }
                if (c == Maze.COLUMNS - 1 && r != maze.exitRow) {
                    walls.add(new int[] {px + CELL - WALL / 2, py - WALL / 2, WALL, CELL + WALL});
                }
            }
        }
    }

    private final List<int[]> walls = new ArrayList<>();

    /** Dead ends hold five rings, or scrap in every third. */
    private void loot() {
        int cell = Maze.cell((int) (x / CELL), (int) (y / CELL));
        if (cell >= 0 && cell < looted.length && !looted[cell] && maze.treasures.contains(cell)) {
            looted[cell] = true;
            if (maze.treasures.indexOf(cell) % 3 == 2) {
                scrap += 2;
                shell.sfx(Sfx.GRAB);
            } else {
                rings += 5;
                shell.sfx(Sfx.RING);
            }
        }
    }

    /** A Caterkiller shadow pacing its hall; touching it is a fright and a few rings dropped. */
    private void stepLurker(float[] l) {
        int cell = (int) l[2];
        int dir = maze.open(cell, Maze.EAST) || maze.open(cell, Maze.WEST) ? 0 : 1;
        float centre = dir == 0 ? Maze.column(cell) * CELL + CELL / 2f : Maze.row(cell) * CELL + CELL / 2f;
        float pos = (dir == 0 ? l[0] : l[1]) + l[3] * 0.5f;
        if (Math.abs(pos - centre) > CELL / 2f - 6) {
            l[3] = -l[3];
        }
        if (dir == 0) {
            l[0] = pos;
        } else {
            l[1] = pos;
        }
        if (scared == 0 && Math.abs(l[0] - x) < 14 && Math.abs(l[1] - y) < 14) {
            scared = 70;
            int lost = Math.min(rings, 3);
            rings -= lost;
            shell.sfx(Sfx.RING_LOSS);
            eek = lost;
        }
    }

    /** Debug: a cell from the exit. */
    void debugNearExit() {
        x = (Maze.COLUMNS - 1) * CELL + CELL / 2f;
        y = maze.exitRow * CELL + CELL / 2f;
        x -= maze.open(Maze.cell(Maze.COLUMNS - 1, maze.exitRow), Maze.WEST) ? CELL : 0;
        camX = x - shell.width() / 2f;
        camY = y - 110;
    }

    private void end() {
        Game game = shell.game;
        List<String> lines = new ArrayList<>();
        lines.add(finished ? "OUT IN " + clock(Maze.TICKS - left) + " WITH " + rings + " RINGS" : "LOST IN THE DARK. "
                + rings + " RINGS FOUND");
        lines.addAll(Maze.reward(game, festivals, rings, scrap, finished, Maze.TICKS - left));
        finish(finished ? "YOU MADE IT OUT" : "LIGHTS UP", lines);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void paint(SceneCanvas canvas) {
        int w = canvas.width(), h = canvas.height();
        canvas.fill(0, 0, w, h, 0xFF000000);
        int cx = Math.round(camX), cy = Math.round(camY);
        SceneImage[] steel = sys.art.scrapBrainSteel();
        // The deck plate, then the walls between cells.
        for (int r = 0; r < Maze.ROWS; r++) {
            for (int c = 0; c < Maze.COLUMNS; c++) {
                int px = c * CELL - cx, py = r * CELL - cy;
                if (px < -CELL || px > w || py < -CELL || py > h) {
                    continue;
                }
                if (steel != null) {
                    canvas.drawRegion(steel[0], 0, 0, 32, 32, px, py, 32, 32, SceneDraw.plain());
                    canvas.drawRegion(steel[0], 0, 0, CELL - 32, 32, px + 32, py, CELL - 32, 32, SceneDraw.plain());
                    canvas.drawRegion(steel[0], 0, 0, 32, CELL - 32, px, py + 32, 32, CELL - 32, SceneDraw.plain());
                    canvas.drawRegion(steel[0], 0, 0, CELL - 32, CELL - 32, px + 32, py + 32, CELL - 32, CELL - 32,
                            SceneDraw.plain());
                } else {
                    canvas.fill(px, py, CELL, CELL, 0xFF243C3C);
                }
            }
        }
        for (int[] wl : walls) {
            if (wl[0] - cx < w && wl[0] + wl[2] - cx > 0 && wl[1] - cy < h && wl[1] + wl[3] - cy > 0) {
                wall(canvas, steel, wl[0] - cx, wl[1] - cy, wl[2], wl[3]);
            }
        }
        // Treasures in the dead ends.
        for (int i = 0; i < maze.treasures.size(); i++) {
            int cell = maze.treasures.get(i);
            if (looted[cell]) {
                continue;
            }
            float tx = Maze.column(cell) * CELL + CELL / 2f - cx, ty = Maze.row(cell) * CELL + CELL / 2f - cy;
            if (i % 3 == 2) {
                shell.art.icons.draw(canvas, shell.game.item("scrap"), tx - 8, ty - 8, SceneDraw.plain());
            } else {
                canvas.draw(shell.art.ring.frame((int) (ticks / 8 % 4)), tx - 8, ty - 8, SceneDraw.plain());
            }
        }
        // The lurkers: Caterkiller shadows with red eyes.
        SceneSpriteSet cat = shell.art.caterkiller;
        for (float[] l : lurkers) {
            float lx = l[0] - cx, ly = l[1] - cy;
            if (cat != null && cat.frameCount() > 0) {
                canvas.draw(cat.frame((int) (ticks / 8 % Math.min(2, cat.frameCount()))), lx, ly + 6,
                        SceneDraw.plain().withFlash(0xFF080808).withFlipX(l[3] > 0));
            }
            canvas.fill(Math.round(lx) + (l[3] > 0 ? 4 : -6), Math.round(ly) - 2, 2, 2, 0xFFFF0000);
        }
        // Mecha Sonic at the far end, a silhouette until he lunges.
        drawMecha(canvas, cx, cy);
        // The farmer, side-on as on the belt-view farm.
        SceneSpriteSet set = shell.art.farmer(shell.game.farmer);
        SceneSprite pose = scared > 40 ? set.frame(firstFrame(set, Anim.HURT)) : anim.pose(set);
        SceneDraw style = SceneDraw.plain().withFlipX(facingLeft);
        float feet = y - cy + 10;
        canvas.fill(Math.round(x - cx) - 8, Math.round(feet) - 2, 16, 3, 0x60000000);
        if (shell.game.farmer.equals("tails")) {
            Anim.drawTails(canvas, shell.art.tailsTails, anim.id(), shell.ticks, x - cx, feet - (pose.height() - pose.originY()),
                    style);
        }
        canvas.draw(pose, x - cx, feet - (pose.height() - pose.originY()), style);
        darkness(canvas, x - cx, y - cy - 8);
        if (scared > 20) {
            String text = eek > 0 ? "EEK! -" + eek : "EEK!";
            canvas.text(text, Math.round(x - cx) - canvas.textWidth(text) / 2, Math.round(feet) - 56 - (70 - scared) / 3,
                    0xFFFF4949);
        }
    }

    private static int firstFrame(SceneSpriteSet set, int animation) {
        int[] frames = set.animationFrames(animation);
        return frames == null || frames.length == 0 ? 0 : frames[0];
    }

    private void drawMecha(SceneCanvas canvas, int cx, int cy) {
        SceneSpriteSet mecha = sys.art.mechaSonic();
        float mx = (Maze.COLUMNS - 1) * CELL + CELL / 2f - cx + 6, my = maze.exitRow * CELL + CELL / 2f - cy + 14;
        if (mecha == null || mecha.frameCount() <= MECHA_LUNGE) {
            return;
        }
        boolean lunging = revealAt >= 0 && finished && t - revealAt < 90;
        boolean lit = revealAt >= 0;
        SceneSprite s = mecha.frame(lunging && t / 6 % 2 == 0 ? MECHA_LUNGE : MECHA_STAND);
        float shake = lunging ? (float) Math.sin(t * 1.7) * 3 : 0;
        SceneDraw style = lit ? SceneDraw.plain().withFlipX(true) : SceneDraw.plain().withFlash(0xFF000000).withFlipX(true);
        canvas.draw(s, mx + shake, my - (s.height() - s.originY()), style);
        if (!lit || lunging) {
            boolean blink = ticks % 120 < 6;
            if (!blink) {
                canvas.fill(Math.round(mx + shake) - 6, Math.round(my) - 34, 3, 2, 0xFFFF2424);
                canvas.fill(Math.round(mx + shake) - 1, Math.round(my) - 34, 3, 2, 0xFFFF2424);
            }
        }
    }

    /** The dark: black everywhere but a flickering pool of light round the farmer; it lifts at the end. */
    private void darkness(SceneCanvas canvas, float lx, float ly) {
        int w = canvas.width(), h = canvas.height();
        int lifted = revealAt >= 0 ? Math.min(255, (t - revealAt) * 3) : 0;
        int alpha = Math.max(0, 0xF4 - lifted);
        if (alpha == 0) {
            return;
        }
        float radius = LIGHT + (float) Math.sin(ticks / 7.0) * 2 + (ticks % 97 < 3 ? -8 : 0);
        for (int yy = 0; yy < h; yy += 2) {
            float dy = yy - ly;
            float half = dy * dy < radius * radius ? (float) Math.sqrt(radius * radius - dy * dy) : -1;
            if (half < 0) {
                canvas.fill(0, yy, w, 2, alpha << 24);
                continue;
            }
            int a = Math.round(lx - half), b = Math.round(lx + half);
            canvas.fill(0, yy, Math.max(0, a), 2, alpha << 24);
            canvas.fill(b, yy, Math.max(0, w - b), 2, alpha << 24);
            // A soft rim.
            int rim = Math.max(0, alpha - 0x60);
            canvas.fill(a, yy, 6, 2, rim << 24);
            canvas.fill(b - 6, yy, 6, 2, rim << 24);
        }
    }

    private static void wall(SceneCanvas canvas, SceneImage[] steel, int x, int y, int w, int h) {
        if (steel == null) {
            canvas.fill(x, y, w, h, 0xFF6D6D6D);
            return;
        }
        for (int yy = 0; yy < h; yy += 32) {
            for (int xx = 0; xx < w; xx += 32) {
                int tw = Math.min(32, w - xx), th = Math.min(32, h - yy);
                canvas.drawRegion(steel[1], 0, 0, tw, th, x + xx, y + yy, tw, th, SceneDraw.plain());
            }
        }
        canvas.fill(x, y, w, 1, 0xFFB6DBDB);
        canvas.fill(x, y + h - 1, w, 1, 0xFF243C3C);
    }

    @Override
    void paintOver(SceneCanvas canvas) {
        var hud = shell.art.hud;
        canvas.draw(left < 600 && shell.ticks / 8 % 2 == 0 ? hud.timeRed : hud.time, 16, 6, SceneDraw.plain());
        hud.number(canvas, clock(left), 66, 2);
        canvas.draw(hud.rings, 130, 6, SceneDraw.plain());
        hud.number(canvas, Integer.toString(rings), 180, 2);
        Text.right(canvas, "HOLD JUMP TO RUN", canvas.width() - 10, 10, Text.GREY);
    }
}
