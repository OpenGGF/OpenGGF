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
import starpost.core.Game;
import starpost.scene.Sfx;
import starpost.ui.Text;
import starpost.valley.Runner;
import starpost.valley.ValleyView;

/**
 * The Ring Hunt, played in the town itself on the real controller: rings everywhere, sixty
 * seconds on Sonic 1's HUD clock, Sonic 1's Special Stage music, and the champion hunting
 * alongside (Tails flies on his twin tails, $A0 with the tail object's Fly frames $27-$28;
 * Sonic, when Tails farms, rolls). Grab more than the champion to take the title.
 */
final class RingHuntScreen extends FestivalScreen {
    private static final int S1_SPECIAL_STAGE = 0x89;       // bgm_SS
    private static final int INTRO = 170;
    private static final int COUNT = 180;
    /** Map_Tails: the flying body (TailsAni_Fly), and the tail object's Fly1 frames. */
    private static final int TAILS_FLY = 0xA0;
    private static final int TAIL_FLY_A = 0x27;
    private static final int TAIL_FLY_B = 0x28;

    private List<RingHunt.Spot> rings = new ArrayList<>();
    private boolean[] taken = new boolean[0];
    private RingHunt.Champion champion;
    private String championId;
    private int farmer;
    private int phase;
    private int phaseAt;
    private int hunt;
    private final List<float[]> sparkles = new ArrayList<>();
    /** The crowd cheers a ring grabbed within this many pixels of the sign, and at GO and the end. */
    private static final int CHEER_RANGE = 220;
    private long cheerUntil;

    RingHuntScreen(FestivalSystem sys, Festival festival) {
        super(sys, festival);
    }

    @Override
    void begin() {
        Game game = shell.game;
        int ax = sys.anchorX(festival);
        holdTown(ax - 30);
        ValleyView view = play.valley();
        int ledge = 0;
        int[] blocks = view.valley.blocks;
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == 3) {
                ledge = i * Art.BLOCK;
            }
        }
        rings = RingHunt.layout(game.calendar.year(), x -> sys.floor(x), view.valley.springX, ledge + 32, ledge + 224, 96);
        taken = new boolean[rings.size()];
        championId = RingHunt.champion(game);
        champion = new RingHunt.Champion(ax + 40, sys.floor(ax) - 36);
        // The champion hunts in person, so the festival draws the crowd itself (one champion, not two).
        offstage(true);
        shell.music.want("s1", S1_SPECIAL_STAGE);
        phaseAt = t;
    }

    @Override
    void step() {
        switch (phase) {
            case 0 -> {
                boolean skip = (shell.in.confirm || shell.in.act) && t - phaseAt > 30;
                if (t - phaseAt == 1) {
                    caption(championId, championId.equals("tails")
                            ? "THE RING HUNT! SIXTY SECONDS, EVERY RING IN TOWN. I'VE WON IT EVERY YEAR, {FARMER}!"
                            : "RING HUNT, BUDDY! SIXTY SECONDS. I'M THE CHAMP. TRY TO KEEP UP!");
                }
                freezeInput();
                play.update(shell);
                if (t - phaseAt > INTRO || skip) {
                    phase = 1;
                    phaseAt = t;
                }
            }
            case 1 -> {
                freezeInput();
                play.update(shell);
                if ((t - phaseAt) % 60 == 0) {
                    shell.sfx(Sfx.SWITCH);
                }
                if (t - phaseAt >= COUNT) {
                    phase = 2;
                    phaseAt = t;
                    cheerUntil = ticks + 60;
                    shell.sfx(Sfx.STARPOST);
                }
            }
            case 2 -> {
                hunt++;
                stepTown();
                collect();
                int got = champion.step(rings, taken);
                if (got >= 0) {
                    RingHunt.Spot s = rings.get(got);
                    sparkles.add(new float[] {s.x(), s.y(), ticks});
                }
                if (hunt >= RingHunt.TICKS || allTaken()) {
                    end();
                }
            }
            default -> play.update(shell);
        }
        sparkles.removeIf(s -> ticks - s[2] > 24);
    }

    private boolean allTaken() {
        for (boolean t : taken) {
            if (!t) {
                return false;
            }
        }
        return true;
    }

    /** The farmer picks up rings as Pickups does: within 12 pixels across and 22 of the body's middle. */
    private void collect() {
        Runner r = play.valley().runner;
        for (int i = 0; i < rings.size(); i++) {
            RingHunt.Spot s = rings.get(i);
            if (!taken[i] && Math.abs(r.x - s.x()) < 12 && Math.abs(r.y - 16 - s.y()) < 22) {
                taken[i] = true;
                farmer++;
                shell.sfx(Sfx.RING);
                if (Math.abs(s.x() - sys.anchorX(festival)) < CHEER_RANGE) {
                    cheerUntil = ticks + 40;
                }
                sparkles.add(new float[] {s.x(), s.y(), ticks});
            }
        }
    }

    private void end() {
        phase = 3;
        cheerUntil = ticks + 1_000_000;
        Game game = shell.game;
        boolean won = RingHunt.farmerWins(farmer, champion.score);
        List<String> lines = new ArrayList<>();
        String me = starpost.people.People.words("{FARMER}", game);
        String champ = BoardScreen.name(championId, game);
        lines.add(me + " " + farmer + " RINGS  -  " + champ + " " + champion.score + " RINGS");
        lines.addAll(RingHunt.reward(game, festivals, farmer, champion.score));
        Prizes.friendship(game, championId, won ? 60 : 30);
        finish(won ? "NEW CHAMPION!" : champ + " WINS", lines);
        caption(championId, won ? (championId.equals("tails") ? "YOU BEAT ME! FAIR AND SQUARE. ...BEST OF THREE?"
                : "HUH. NOT BAD. NOT BAD AT ALL.")
                : (championId.equals("tails") ? "STILL THE CHAMPION! THE RING DETECTOR WORKS! IT'S MOSTLY A METAL DETECTOR."
                : "TOO SLOW! JUST KIDDING. YOU WERE GREAT."));
    }

    @Override
    void paint(SceneCanvas canvas) {
        play.draw(shell, canvas);
        ValleyView view = play.valley();
        int cx = Math.round(view.cameraX()), cy = Math.round(view.cameraY());
        drawCrowd(canvas, cx, cy, championId, ticks < cheerUntil);
        SceneSpriteSet ring = shell.art.ring;
        int frame = (int) (ticks / 8 % 4);
        for (int i = 0; i < rings.size(); i++) {
            if (!taken[i]) {
                RingHunt.Spot s = rings.get(i);
                canvas.draw(ring.frame(frame), s.x() - cx, s.y() - cy, SceneDraw.plain());
            }
        }
        for (float[] s : sparkles) {
            int f = 4 + (int) Math.min(3, (ticks - s[2]) / 6);       // Map_Ring 4-7: the sparkle
            if (f < ring.frameCount()) {
                canvas.draw(ring.frame(f), s[0] - cx, s[1] - cy, SceneDraw.plain());
            }
        }
        drawChampion(canvas, cx, cy);
    }

    private void drawChampion(SceneCanvas canvas, int cx, int cy) {
        float x = champion.x - cx, y = champion.y - cy;
        SceneDraw style = SceneDraw.plain().withFlipX(champion.facingLeft);
        SceneSpriteSet body = shell.art.farmer(championId);
        if (championId.equals("tails")) {
            if (TAILS_FLY < body.frameCount()) {
                SceneSprite s = body.frame(TAILS_FLY);
                float originY = y + 16 - (s.height() - s.originY());
                SceneSpriteSet tails = shell.art.tailsTails;
                int tf = ticks / 2 % 2 == 0 ? TAIL_FLY_A : TAIL_FLY_B;
                if (tails != null && tf < tails.frameCount()) {
                    canvas.draw(tails.frame(tf), x, originY, style);
                }
                canvas.draw(s, x, originY, style);
            }
            return;
        }
        int[] frames = body.animationFrames(champion.counting() ? Anim.WAIT : Anim.ROLL);
        int f = frames == null || frames.length == 0 ? 0 : frames[(int) (ticks / 3 % frames.length)];
        SceneSprite s = body.frame(f);
        canvas.draw(s, x, y + 16 - (s.height() - s.originY()), style);
    }

    @Override
    void paintOver(SceneCanvas canvas) {
        int left = phase >= 2 ? (RingHunt.TICKS - hunt + 59) / 60 : RingHunt.SECONDS;
        hud(canvas, left, farmer);
        // The champion's count, with their face.
        SceneImage face = sys.peopleArt().head(body(championId));
        int w = canvas.width();
        if (face != null) {
            int fh = Math.min(20, face.height());
            canvas.drawRegion(face, 0, 0, face.width(), fh, w - 96, 4, face.width(), fh, SceneDraw.plain());
        }
        shell.art.hud.number(canvas, Integer.toString(champion.score), w - 60, 2);
        if (phase == 1) {
            countdown(canvas, COUNT - (t - phaseAt), COUNT);
        } else if (phase == 2 && hunt < 45) {
            countdown(canvas, -hunt, COUNT);
        }
        if (phase == 2 && hunt < 200) {
            Text.centred(canvas, "GRAB MORE RINGS THAN " + BoardScreen.name(championId, shell.game) + "!",
                    canvas.height() - 16, Text.YELLOW);
        }
    }

    @Override
    void restore() {
        super.restore();
        offstage(false);
        shell.music.stop();
    }

}
