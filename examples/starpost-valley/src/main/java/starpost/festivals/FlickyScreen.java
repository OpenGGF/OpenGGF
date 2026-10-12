package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.people.Bodies;
import starpost.ui.Text;
import starpost.scene.TownBackdrop;

/**
 * The Night of the Flickies, after Sonic 3 &amp; Knuckles' ending: the valley at night on the
 * meadow by the lake, the neighbours sitting up to watch, and wave after wave of Flickies flying
 * south over the water in V formations (Sonic 1's freed Flickies and Sonic 3 &amp; Knuckles' blue
 * ones together), as many as the animals the farmer has freed. Pip leads the first wave. Waving
 * (the action button) under a passing wave brings one Flicky down to circle the farmer. Quiet:
 * the ending's own music, no score.
 */
final class FlickyScreen extends FestivalScreen {
    private static final int S3K_ENDING = 0x32;          // Sonic3kMusic ENDING
    private static final float SPEED = 1.05f;
    private static final int CIRCLE = 200;

    private List<FlickyNight.Bird> flock = new ArrayList<>();
    private int count;
    private int waves;
    private final List<String> watchers = new ArrayList<>();
    private boolean pipLeads;
    /** A Flicky come down to the farmer: its bird index, and when. */
    private int visitor = -1;
    private int visitAt;
    private final boolean[] wavedAt = new boolean[16];
    private int waved;
    private boolean ended;

    FlickyScreen(FestivalSystem sys, Festival festival) {
        super(sys, festival);
    }

    @Override
    void begin() {
        Game game = shell.game;
        int ax = sys.anchorX(festival);
        holdTown(ax + 40);
        offstage(true);
        count = FlickyNight.count(game);
        flock = FlickyNight.flock(count);
        waves = FlickyNight.waves(count);
        for (String id : crowd()) {
            if (!id.equals("pip")) {
                watchers.add(id);
            }
        }
        pipLeads = present("pip");
        moonSky();
        shell.music.want("s3k", S3K_ENDING);
    }

    private int ax() {
        return sys.anchorX(festival);
    }

    @Override
    void step() {
        boolean wave = shell.in.act;
        stepTown();
        if (t == 30) {
            if (pipLeads) {
                caption("pip", "HERE THEY COME! I'M AT THE FRONT! WAVE WHEN WE FLY OVER!", "flicky", "arrow", "heart", "!");
            } else {
                caption(Festivals.host(festival, shell.game), "HERE THEY COME. WAVE WHEN THEY FLY OVER.");
            }
        }
        if (wave && visitor < 0) {
            for (int w = 0; w < Math.min(waves, wavedAt.length); w++) {
                if (FlickyNight.overhead(w, t) && !wavedAt[w]) {
                    wavedAt[w] = true;
                    waved++;
                    visitor = firstOf(w) + 1 + w % 3;
                    visitor = Math.min(visitor, flock.size() - 1);
                    visitAt = t;
                    shell.sfx(starpost.scene.Sfx.SWITCH);
                    break;
                }
            }
        }
        if (visitor >= 0 && t - visitAt > CIRCLE) {
            visitor = -1;
        }
        int lastWave = FlickyNight.waveStart(waves - 1) + FlickyNight.CROSSING + 160;
        if (!ended && t > Math.min(FlickyNight.TICKS, lastWave)) {
            ended = true;
            end();
        }
    }

    private int firstOf(int wave) {
        for (int i = 0; i < flock.size(); i++) {
            if (flock.get(i).wave() == wave) {
                return i;
            }
        }
        return 0;
    }

    private void end() {
        Game game = shell.game;
        List<String> lines = new ArrayList<>();
        lines.add(waved == 0 ? "YOU WATCHED THEM GO" : waved + (waved == 1 ? " FLOCK" : " FLOCKS") + " WAVED BACK");
        lines.addAll(FlickyNight.reward(game, festivals, waved));
        finish("GOODNIGHT, FLICKIES", lines);
        if (pipLeads) {
            caption("pip", "SEE YOU IN SPRING! FLICKIES ALWAYS COME BACK. THAT'S THE WHOLE POINT OF A FLICKY.",
                    "flicky", "sun", "house", "heart");
        }
    }

    @Override
    void restore() {
        super.restore();
        offstage(false);
        play.valley().sky = null;
    }

    /** Where a bird is on screen at a tick (NaN when not in the sky), as {x, y}. */
    private float[] birdAt(int index, SceneCanvas canvas) {
        FlickyNight.Bird b = flock.get(index);
        int age = t - FlickyNight.waveStart(b.wave());
        if (age < 0 || age > FlickyNight.CROSSING + 200) {
            return null;
        }
        float lead = canvas.width() + 30 - age * SPEED;
        // A V on its side: the leader in front (west), the arms trailing up and down behind.
        float x = lead + b.rank() * 22 + b.phase() % 3;
        // High over the meadow (between the letterbox and the watchers' heads).
        float y = 62 + b.wave() % 3 * 9 + b.side() * b.rank() * 6
                + (float) Math.sin((t + b.wave() * 40) / 37.0) * 5 + (float) Math.sin((t + b.phase() * 9) / 11.0);
        if (index == visitor) {
            // Down to the farmer, round once, and back up to its place.
            int v = t - visitAt;
            TownBackdrop view = play.valley();
            float fx = view.pose.x - view.cameraX(), fy = view.pose.y - view.cameraY() - 46;
            float a = v / (float) CIRCLE * (float) Math.PI * 2;
            float pull = (float) Math.sin(Math.min(1f, v / (float) CIRCLE) * Math.PI);
            x += (fx + (float) Math.cos(a * 2) * 22 - x) * pull;
            y += (fy + (float) Math.sin(a * 2) * 10 - y) * pull;
        }
        return new float[] {x, y};
    }

    @Override
    void paint(SceneCanvas canvas) {
        play.draw(shell, canvas);
        int w = canvas.width();
        TownBackdrop view = play.valley();
        int cx = Math.round(view.cameraX()), cy = Math.round(view.cameraY());
        // The watchers along the meadow, looking up.
        int ax = ax();
        for (int i = 0; i < watchers.size(); i++) {
            int x = ax - 150 + i * 30 + (i % 2) * 6;
            int f = sys.floor(x);
            drawVillager(canvas, watchers.get(i), x - cx, f - cy, Bodies.LOOK_UP, i % 2 == 0, 0,
                    starpost.scene.PlayScreen.lightTint(shell.game.calendar.light()));
        }
        SceneSpriteSet blue = shell.art.flicky;
        SceneSpriteSet s1 = shell.art.animal("flicky");
        for (int i = 0; i < flock.size(); i++) {
            float[] p = birdAt(i, canvas);
            if (p == null || p[0] < -20 || p[0] > w + 40) {
                continue;
            }
            FlickyNight.Bird b = flock.get(i);
            boolean leaderPip = pipLeads && i == 0;
            SceneSpriteSet set = leaderPip || i % 2 == 0 ? s1 : blue;
            if (set == null) {
                continue;
            }
            int frame = (int) ((ticks / 4 + b.phase()) % 2);
            canvas.draw(set.frame(Math.min(frame, set.frameCount() - 1)), p[0], p[1], SceneDraw.plain());
            if (leaderPip) {
                Text.shadow(canvas, "PIP", Math.round(p[0]) - 8, Math.round(p[1]) - 18, Text.YELLOW);
            }
        }
        if (visitor >= 0 && t - visitAt > 60 && t - visitAt < CIRCLE - 20) {
            SceneImage heart = sys.peopleArt().glyph("heart");
            if (heart != null) {
                canvas.draw(heart, view.pose.x - cx - heart.width() / 2f, view.pose.y - cy - 62, SceneDraw.plain());
            }
        }
    }

    /**
     * The moon rises over the lake: the night backdrop with the moon painted where it shows just
     * under the letterbox, a little right of centre, from where the camera stands for the night
     * (the backdrop's top row on screen is TownBackdrop's {@code 8 + camY / 10}, and its slowest
     * rows scroll at 0.03 of the camera).
     */
    private void moonSky() {
        TownBackdrop view = play.valley();
        var night = shell.art.season(shell.game.calendar.season()).backdrop(2);
        int top = Math.max(0, Math.min(32, Math.round(8 + view.cameraY() * 0.1f)));
        int w = night.image().width();
        int moonX = Math.floorMod((int) Math.floor(view.cameraX() * 0.03) + shell.width() / 2 + 30, w);
        view.sky = FestivalArt.moonlit(night, moonX, top + 48);
    }

    @Override
    void paintOver(SceneCanvas canvas) {
        Text.shadow(canvas, count + " FLICKIES", 16, 10, Text.WHITE);
        for (int w = 0; w < Math.min(waves, wavedAt.length); w++) {
            if (FlickyNight.overhead(w, t) && !wavedAt[w] && !showingResults()) {
                Text.centred(canvas, "B / X: WAVE!", canvas.height() - 16, Text.YELLOW);
                break;
            }
        }
    }
}
