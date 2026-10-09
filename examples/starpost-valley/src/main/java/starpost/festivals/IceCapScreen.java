package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import starpost.art.Anim;
import starpost.art.Art;
import starpost.core.Calendar;
import starpost.core.Game;
import starpost.scene.Sfx;
import starpost.ui.Text;
import starpost.valley.Runner;

/**
 * The Ice Cap Festival's contest. With the fishing system installed ({@link Festivals#fishingContest})
 * the farmer chooses between its fishing contest and the snowboard run; without it, the run.
 *
 * <p>The run: down Green Hill under snow (the valley's winter blocks, each set where the last one
 * ended, see {@link Snowboard#course}), riding the board Sonic rode into Ice Cap Zone (Sonic's
 * own snowboarding frames; Tails and Knuckles crouch on the empty board), to Ice Cap's music.
 * The slope pushes the board along; crests and ledges throw it into the air; jump to hop the
 * rocks, jump again in the air for a spin. Land a spin for a trick; land mid-spin, or hit a
 * rock, and tumble.
 */
final class IceCapScreen extends FestivalScreen {
    private static final int ICZ1 = 0x0B;              // Sonic3kMusic ICZ1
    private static final float GRAVITY = Runner.GRAVITY;
    private static final float PUSH = 0.045f;
    private static final float SLOPE = 0.28f;
    private static final float MAX = 11f;
    private static final int TRICK = 30;
    /** Map_SonicSnowboard: level, downhill, uphill, airborne, and the spin's five frames. */
    private static final int RIDE = 6;
    private static final int RIDE_DOWN = 7;
    private static final int RIDE_UP = 8;
    private static final int AIR = 9;
    /** Map_Snowboard: flat, tilted down to the right, tilted up. */
    private static final int BOARD_FLAT = 1;
    private static final int BOARD_DOWN = 2;
    private static final int BOARD_UP = 4;

    private int[] blocks;
    private int[] offsets;
    private final int[][] tops = new int[64][];
    private float x;
    private float y;
    private float vx;
    private float vy;
    private float lastVy;
    private boolean ground = true;
    private int trick = -1;
    private int tricks;
    private int crash;
    private int rings;
    private final List<float[]> ringSpots = new ArrayList<>();
    private final List<float[]> rocks = new ArrayList<>();
    private int run;
    private int finishAt = -1;
    private int phase;           // 0 choosing / intro, 1 running, 2 done
    private boolean chose;
    private float camX;
    private float camY;
    private int score;
    private final Anim anim = new Anim();

    IceCapScreen(FestivalSystem sys, Festival festival) {
        super(sys, festival);
    }

    @Override
    void begin() {
        Game game = shell.game;
        blocks = Snowboard.course(game.calendar.year());
        offsets = new int[blocks.length];
        for (int i = 1; i < blocks.length; i++) {
            offsets[i] = offsets[i - 1] + top(blocks[i - 1], Art.BLOCK - 1) - top(blocks[i], 0);
        }
        x = 60;
        y = surface(x);
        vx = 2;
        for (int i = 2; i < blocks.length - 2; i++) {
            float bx = i * Art.BLOCK;
            if (i % 3 == 0) {
                for (int k = 0; k < 5; k++) {
                    float rx = bx + 60 + k * 18;
                    ringSpots.add(new float[] {rx, surface(rx) - 40 - (float) Math.sin(Math.PI * k / 4) * 36, 0});
                }
            } else if (i % 3 == 1) {
                // A rock where the block runs level for a stretch (never on a ledge's edge).
                for (int lx = 60; lx < 200; lx += 10) {
                    float rx = bx + lx;
                    if (Math.abs(surface(rx + 12) - surface(rx - 12)) < 3) {
                        rocks.add(new float[] {rx, 0});
                        break;
                    }
                }
            }
        }
        camX = x - 120;
        camY = y - 130;
        shell.music.want("s3k", ICZ1);
    }

    /** The first solid row of a block's column (its surface), cached. */
    private int top(int block, int lx) {
        if (tops[block] == null) {
            int[] col = new int[Art.BLOCK];
            for (int cx = 0; cx < Art.BLOCK; cx++) {
                int yy = 0;
                while (yy < Art.BLOCK && !shell.art.solid(block, cx, yy)) {
                    yy++;
                }
                col[cx] = yy;
            }
            tops[block] = col;
        }
        return tops[block][Math.max(0, Math.min(Art.BLOCK - 1, lx))];
    }

    /** The course's surface y under x. */
    private float surface(float wx) {
        int i = Math.max(0, Math.min(blocks.length - 1, (int) Math.floor(wx / Art.BLOCK)));
        int lx = Math.round(wx) - i * Art.BLOCK;
        return offsets[i] + top(blocks[i], lx);
    }

    private float slope() {
        return (surface(x + 8) - surface(x - 8)) / 16f;
    }

    private int end() {
        return (blocks.length - 1) * Art.BLOCK - 40;
    }

    @Override
    void step() {
        if (phase == 0) {
            if (!chose) {
                chose = true;
                if (festivals.fishingContest != null) {
                    shell.push(new Choose("FROST'S ICE CAP CONTEST", List.of("FISHING CONTEST", "SNOWBOARD RUN"), null,
                            i -> {
                                if (i == 0) {
                                    fishing();
                                }
                            }));
                }
                caption("frost", "WELCOME TO THE ICE CAP FESTIVAL! THE SNOWBOARD RUN! BEAT MY RECORD, "
                        + Snowboard.FROST_RECORD + " POINTS. JUMP THE ROCKS. SPIN IN THE AIR.",
                        "snow", "arrow", "sparkle", "!");
            }
            if (t > 160 || t > 40 && (shell.in.confirm || shell.in.act)) {
                phase = 1;
                shell.sfx(Sfx.STARPOST);
            }
            return;
        }
        if (phase == 2) {
            return;
        }
        run++;
        ride();
        camX += (x - 130 - camX) * 0.2f;
        camY += (y - 120 - camY) * 0.12f;
        if (x >= end() && finishAt < 0) {
            finishAt = run;
            shell.sfx(Sfx.SIGNPOST);
        }
        if (finishAt >= 0 && run - finishAt > 90) {
            phase = 2;
            finishRun();
        }
    }

    private void ride() {
        var in = shell.in;
        if (crash > 0) {
            crash--;
            vx = Math.max(1.5f, vx * 0.96f);
        }
        if (finishAt >= 0) {
            vx = Math.max(0, vx - 0.15f);
        }
        float before = y;
        if (ground) {
            float s = slope();
            if (finishAt < 0 && crash == 0) {
                vx = Math.max(3, Math.min(MAX, vx + PUSH + s * SLOPE));
            }
            x += vx;
            float target = surface(x);
            float dy = target - y;
            if (dy > 6 + vx * 0.5f || lastVy < -1.5f && dy > lastVy + 3) {
                ground = false;           // off a ledge or over a crest: airborne
                vy = lastVy;
            } else {
                y = target;
            }
            if (ground && in.jump && crash == 0 && finishAt < 0) {
                ground = false;
                vy = -6.5f;
                shell.sfx(Sfx.JUMP);
            }
            for (float[] r : rocks) {
                if (ground && r[1] == 0 && Math.abs(r[0] - x) < 14 && crash == 0) {
                    r[1] = 1;
                    tumble();
                }
            }
        } else {
            vy += GRAVITY;
            x += vx;
            y += vy;
            if (trick < 0 && (in.jump || in.act) && crash == 0) {
                trick = 0;
                shell.sfx(Sfx.SPINDASH);
            }
            if (trick >= 0 && ++trick > TRICK) {
                trick = TRICK;
            }
            float floor = surface(x);
            if (y >= floor && vy >= 0) {
                y = floor;
                ground = true;
                vy = 0;
                if (trick >= 0) {
                    if (trick >= TRICK) {
                        tricks++;
                        shell.sfx(Sfx.PERFECT);
                    } else {
                        tumble();
                    }
                }
                trick = -1;
            }
        }
        lastVy = y - before;
        for (float[] r : ringSpots) {
            if (r[2] == 0 && Math.abs(r[0] - x) < 14 && Math.abs(r[1] - (y - 16)) < 20) {
                r[2] = 1;
                rings++;
                shell.sfx(Sfx.RING);
            }
        }
        anim.tick();
    }

    private void tumble() {
        crash = Snowboard.CRASH_TICKS;
        vx = 2;
        trick = -1;
        shell.sfx(Sfx.RING_LOSS);
        rings = Math.max(0, rings - 3);
    }

    private void finishRun() {
        Game game = shell.game;
        score = Snowboard.score(finishAt, rings, tricks);
        boolean won = Snowboard.beatsFrost(score);
        List<String> lines = new ArrayList<>();
        lines.add(score + " POINTS: " + clock(finishAt) + ", " + rings + " RINGS, " + tricks
                + (tricks == 1 ? " TRICK" : " TRICKS"));
        lines.addAll(Snowboard.reward(game, festivals, score, rings, won));
        festivals.recordTime(FestivalBook.ICE_CAP, finishAt);
        finish(won ? "NEW RECORD" : "FROST KEEPS IT", lines);
        caption("frost", won ? "YOU BEAT MY RECORD! IN MY OWN FESTIVAL! I'M SO PROUD I COULD MELT."
                : "MY RECORD STANDS! BUT THAT WAS A LOVELY RUN. LIKE A SNOWFLAKE. A FAST ONE.",
                won ? new String[] {"sparkle", "heart", "snow", "!"} : new String[] {"snow", "heart"});
    }

    /** Hands the day to the fishing system's contest; its score comes back here. */
    private void fishing() {
        restore();
        FishingContest contest = festivals.fishingContest;
        contest.start(shell, play, 120, catchScore -> {
            Game game = shell.game;
            boolean won = catchScore > contest.recordToBeat();
            List<String> notices = Snowboard.reward(game, festivals, catchScore, 0, won);
            Calendar c = game.calendar;
            c.set(c.year(), c.season(), c.day(), festival.after(c.minutes()));
            shell.toast(String.join(". ", notices));
        });
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void paint(SceneCanvas canvas) {
        int w = canvas.width(), h = canvas.height();
        Art.Seasonal look = shell.art.season(Calendar.WINTER);
        int cx = Math.round(camX), cy = Math.round(camY);
        canvas.drawBackdrop(look.backdrop(0), 0, 0, w, h, 8, camX * 0.5, shell.ticks);
        SceneDraw plain = SceneDraw.plain();
        for (int i = Math.max(0, cx / Art.BLOCK - 1); i <= Math.min(blocks.length - 1, (cx + w) / Art.BLOCK + 1); i++) {
            SceneImage block = look.block(blocks[i]);
            if (block == null) {
                continue;
            }
            int bx = i * Art.BLOCK - cx, by = offsets[i] - cy;
            canvas.draw(block, bx, by, plain);
            // The hill goes on down under each block: its bottom rows, repeated.
            for (int extra = Art.BLOCK; by + extra < h; extra += 32) {
                canvas.drawRegion(block, 0, Art.BLOCK - 32, Art.BLOCK, 32, bx, by + extra, Art.BLOCK, 32, plain);
            }
        }
        for (float[] r : rocks) {
            if (r[1] == 0) {
                FestivalSystem.standSprite(canvas, shell.art.purpleRock, 0, r[0] - cx, surface(r[0]) - cy + 2, plain);
            }
        }
        for (float[] r : ringSpots) {
            if (r[2] == 0) {
                canvas.draw(shell.art.ring.frame((int) (ticks / 8 % 4)), r[0] - cx - 8, r[1] - cy - 8, plain);
            }
        }
        int line = end();
        FestivalSystem.standSprite(canvas, shell.art.signpost, finishAt >= 0 && run - finishAt < 60
                ? 1 + (int) (run / 3 % 3) : 4, line - cx, surface(line) - cy, plain);
        drawRider(canvas, x - cx, y - cy);
        // Snow falling.
        for (int i = 0; i < 50; i++) {
            float sx = (i * 131 + ticks * 0.6f - camX * 0.3f) % (w + 20);
            float sy = (i * 71 + ticks * (0.8f + i % 3 * 0.3f)) % (h + 10);
            canvas.fill(Math.round(Math.floorMod((int) sx, w + 20) - 10), Math.round(sy) - 5, i % 4 == 0 ? 2 : 1,
                    i % 4 == 0 ? 2 : 1, 0xE0FFFFFF);
        }
    }

    private void drawRider(SceneCanvas canvas, float sx, float feet) {
        float s = slope();
        SceneDraw style = SceneDraw.plain();
        String farmer = shell.game.farmer;
        SceneSpriteSet sonic = sys.art.sonicBoard();
        if (farmer.equals("sonic") && sonic != null && sonic.frameCount() > AIR && crash == 0) {
            int frame = trick >= 0 ? 1 + trick / 6 % 5 : !ground ? AIR : s > 0.25f ? RIDE_DOWN : s < -0.25f ? RIDE_UP : RIDE;
            SceneSprite sp = sonic.frame(frame);
            canvas.draw(sp, sx, feet - (sp.height() - sp.originY()) + 2, style);
            return;
        }
        SceneSpriteSet set = shell.art.farmer(farmer);
        int animId = crash > 0 ? Anim.HURT : trick >= 0 ? Anim.ROLL : !ground ? Anim.SPRING : Anim.DUCK;
        anim.set(animId, 3);
        SceneSprite pose = anim.pose(set);
        float originY = animId == Anim.ROLL ? feet - 22 : feet - 4 - (pose.height() - pose.originY());
        if (farmer.equals("tails")) {
            Anim.drawTails(canvas, shell.art.tailsTails, animId, shell.ticks, sx, originY, style);
        }
        canvas.draw(pose, sx, originY, style);
        SceneSpriteSet board = sys.art.snowboard();
        if (board != null && board.frameCount() > BOARD_UP && crash == 0) {
            int frame = s > 0.25f ? BOARD_DOWN : s < -0.25f ? BOARD_UP : BOARD_FLAT;
            SceneSprite b = board.frame(frame);
            canvas.draw(b, sx, feet - (b.height() - b.originY()), style);
        }
    }

    @Override
    void paintOver(SceneCanvas canvas) {
        var hud = shell.art.hud;
        canvas.draw(hud.time, 16, 6, SceneDraw.plain());
        hud.number(canvas, clock(finishAt >= 0 ? finishAt : run), 66, 2);
        canvas.draw(hud.rings, 130, 6, SceneDraw.plain());
        hud.number(canvas, Integer.toString(rings), 180, 2);
        Text.right(canvas, "TRICKS " + tricks, canvas.width() - 10, 10, Text.WHITE);
        if (phase == 1 && run < 240) {
            Text.centred(canvas, "JUMP: HOP. AGAIN IN THE AIR: SPIN", canvas.height() - 16, Text.YELLOW);
        } else if (!showingResults()) {
            Text.centred(canvas, "FROST'S RECORD: " + Snowboard.FROST_RECORD + " POINTS", canvas.height() - 16, Text.WHITE);
        }
    }
}
