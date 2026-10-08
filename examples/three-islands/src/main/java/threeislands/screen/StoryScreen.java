package threeislands.screen;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.List;
import threeislands.Game;
import threeislands.art.Font;
import threeislands.art.Heroes;
import threeislands.core.HeroId;
import threeislands.core.Island;
import threeislands.core.Story;
import threeislands.view.Ui;

/**
 * Plays a story scene over whatever was on screen: a dialogue box with the speaker's portrait
 * (drawn from the current island's ROM) and typewriter text. A or C reveals the line, then
 * advances; Start skips the rest of the scene.
 */
public final class StoryScreen implements Screen {
    private static final int BOX_H = 62;

    private final List<Story.Line> lines;
    private final Screen background;
    private final Runnable then;
    private int index;
    private double reveal;
    private boolean done;
    private long ticks;

    public StoryScreen(Game game, List<Story.Line> lines, Screen background, Runnable then) {
        this.lines = lines;
        this.background = background;
        this.then = then;
    }

    @Override
    public String name() {
        return "STORY";
    }

    public int index() {
        return index;
    }

    public int lineCount() {
        return lines.size();
    }

    @Override
    public void update(Game game) {
        ticks++;
        if (done) return;
        if (game.controls.menu()) {
            finish();
            return;
        }
        String text = lines.get(index).text();
        reveal = Math.min(text.length(), reveal + (game.controls.holdAccept() ? 3 : 1.25));
        if (game.controls.accept()) {
            if (reveal < text.length()) {
                reveal = text.length();
            } else {
                index++;
                reveal = 0;
                game.audio.sfx(threeislands.audio.Audio.SFX_CURSOR);
                if (index >= lines.size()) finish();
            }
        }
    }

    private void finish() {
        if (done) return;
        done = true;
        then.run();
    }

    @Override
    public void draw(Game game, SceneCanvas c) {
        if (background != null && !(background instanceof StoryScreen)) background.draw(game, c);
        else c.clear(0x000000);
        int w = c.width();
        int h = c.height();
        if (index >= lines.size()) return;
        Story.Line line = lines.get(index);
        boolean narration = line.speaker() == null;
        c.fill(0, 0, w, h, narration ? 0x90000000 : 0x40000000);
        int y = h - BOX_H - 6;
        game.ui.window(c, 6, y, w - 12, BOX_H);
        int textX = 16;
        if (!narration) {
            game.ui.window(c, 12, y + 6, 50, 50, 0xF0304890, 0xF0102040);
            c.clip(14, y + 8, 46, 46);
            portrait(game, c, line.speaker(), 37, y + 50);
            c.unclip();
            textX = 70;
            game.font.shadowed(c, line.speaker(), textX, y + 6, colour(line.speaker()));
        }
        String visible = line.text().substring(0, (int) Math.min(line.text().length(), reveal));
        List<String> wrapped = game.font.wrap(line.text(), w - textX - 18);
        int ty = narration ? y + 10 : y + 19;
        int shown = 0;
        for (int i = 0; i < wrapped.size() && i < 4; i++) {
            String row = wrapped.get(i);
            int take = Math.max(0, Math.min(row.length(), visible.length() - shown));
            game.font.draw(c, row.substring(0, take), textX, ty + i * Font.LINE, narration ? 0xFFFFF0C0 : Ui.TEXT);
            shown += row.length() + 1;
        }
        if (reveal >= line.text().length() && ticks / 16 % 2 == 0) {
            c.fill(w - 22, y + BOX_H - 12, 7, 3, Ui.GOLD);
            c.fill(w - 21, y + BOX_H - 9, 5, 2, Ui.GOLD);
            c.fill(w - 20, y + BOX_H - 7, 3, 1, Ui.GOLD);
        }
        game.font.draw(c, "Start: skip", w - 66, 4, 0x80FFFFFF);
    }

    private static int colour(String speaker) {
        return switch (speaker) {
            case "Sonic" -> 0xFF60A0FF;
            case "Tails" -> 0xFFFFC040;
            case "Knuckles" -> 0xFFFF6060;
            case "Eggman" -> 0xFFFF9030;
            default -> 0xFFA0FFA0;
        };
    }

    /** Draws the speaker standing in the portrait frame with feet at (x, feetY). */
    private void portrait(Game game, SceneCanvas c, String speaker, int x, int feetY) {
        Island island = game.progress.island();
        long t = ticks;
        switch (speaker) {
            case "Sonic", "Tails", "Knuckles" -> {
                HeroId hero = HeroId.valueOf(speaker.toUpperCase());
                game.heroes.draw(c, game.heroes.gameFor(island.game, hero), hero, Heroes.IDLE, x, feetY, false, t,
                        SceneDraw.plain(), -1);
            }
            case "Eggman" -> {
                if (island == Island.SOUTH && game.art.sprites("s1:eggman") != null) {
                    SceneSpriteSet set = game.art.sprites("s1:eggman");
                    var frame = set.frame(0);
                    c.draw(frame, x, feetY - (frame.height() - frame.originY()), SceneDraw.plain());
                } else {
                    SceneSpriteSet ship = game.art.sprites("s3k:ship");
                    if (ship != null) {
                        // Map_RobotnikShip: head (0/1) sits $1C above the Egg Mobile (5).
                        c.draw(ship.frame((t / 12) % 2 == 0 ? 0 : 1), x, feetY - 16 - 0x1C, SceneDraw.plain());
                        c.draw(ship.frame(5), x, feetY - 16, SceneDraw.plain());
                    }
                }
            }
            default -> {
                String game1 = island.game;
                String key = switch (speaker) {
                    case "Pocky" -> "pocky";
                    case "Ricky" -> game1.equals("s2") ? "tocky" : "ricky";
                    case "Rocky" -> game1.equals("s1") ? "rocky" : "flicky";
                    default -> "flicky";
                };
                SceneSpriteSet set = game.art.sprites(game1 + ":" + key);
                if (set == null) set = game.art.sprites("s3k:flicky");
                if (set != null) {
                    var frame = set.frame((int) (t / 8 % 2));
                    c.draw(frame, x, feetY - 12, SceneDraw.plain().withScale(2));
                }
            }
        }
    }
}
