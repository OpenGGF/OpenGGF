package threeislands;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import threeislands.art.Art;
import threeislands.art.EnemyArt;
import threeislands.art.Font;
import threeislands.art.Heroes;
import threeislands.audio.Audio;
import threeislands.core.EnemyKind;
import threeislands.core.HeroId;
import threeislands.core.Island;
import threeislands.core.Progress;
import threeislands.core.SaveCodec;
import threeislands.core.Story;
import threeislands.core.Zone;
import threeislands.field.Field;
import threeislands.field.FieldPath;
import threeislands.field.Stage;
import threeislands.screen.BattleScreen;
import threeislands.screen.EndingScreen;
import threeislands.screen.FieldScreen;
import threeislands.screen.LoadingScreen;
import threeislands.screen.MapScreen;
import threeislands.screen.Screen;
import threeislands.screen.StoryScreen;
import threeislands.screen.TitleScreen;
import threeislands.view.Controls;
import threeislands.view.Ui;

/**
 * Shared services and the story flow. Screens call back into here to move between the title,
 * island maps, fields, battles and story scenes; this class decides what comes next (who joins,
 * which emerald is returned, which island follows) so the rules live in one place.
 */
public final class Game {
    public static final String SAVE = "save.txt";
    public static final String BACKUP = "save.bak";

    public final SceneContext ctx;
    public final Art art;
    public final Heroes heroes;
    public final EnemyArt enemies;
    public final Ui ui;
    public final Font font;
    public final Audio audio;
    public final Controls controls = new Controls();
    public final Story story;
    public Progress progress;
    private Screen screen;
    private Screen next;
    private int fade;
    private long ticks;
    private final List<String> notices = new ArrayList<>();
    private Stage cachedStage;

    public Game(SceneContext ctx, byte[] font, byte[] script) {
        this.ctx = ctx;
        this.font = new Font(font);
        this.ui = new Ui(this.font);
        this.art = new Art(ctx.art());
        this.heroes = new Heroes(art);
        this.enemies = new EnemyArt(art, heroes);
        this.audio = new Audio(ctx);
        this.story = new Story(script);
        this.progress = new Progress(1);
        controls.bind(ctx);
        for (Island island : Island.values()) {
            if (!art.has(island.game)) notices.add(name(island.game) + " ROM not found: " + island.label + " will be skipped.");
        }
        screen = new TitleScreen(this);
    }

    public static String name(String game) {
        return switch (game) {
            case "s1" -> "Sonic 1";
            case "s2" -> "Sonic 2";
            default -> "Sonic 3 & Knuckles";
        };
    }

    public List<String> notices() { return List.copyOf(notices); }
    public int width() { return ctx.width(); }
    public int height() { return ctx.height(); }
    public long ticks() { return ticks; }
    public Screen screen() { return screen; }

    /** Changes screen with a short fade through black. */
    public void go(Screen target) {
        next = target;
        if (fade > 12) fade = 24 - fade;
    }

    /** Changes screen immediately (overlays such as menus and story over the current view). */
    public void swap(Screen target) {
        screen = target;
        next = null;
        fade = 0;
    }

    public void update() {
        ticks++;
        controls.bind(ctx);
        audio.tick();
        if (next != null) {
            fade = Math.max(1, fade + 1);
            if (fade >= 12) {
                screen = next;
                next = null;
                fade = 12;
            }
            return;
        }
        if (fade > 0 && ++fade >= 24) fade = 0;
        screen.update(this);
    }

    public void draw(SceneCanvas canvas) {
        screen.draw(this, canvas);
        if (fade > 0) {
            int alpha = fade <= 12 ? fade * 255 / 12 : (24 - fade) * 255 / 12;
            canvas.fill(0, 0, canvas.width(), canvas.height(), Math.max(0, Math.min(255, alpha)) << 24);
        }
    }

    public boolean transitioning() {
        return next != null;
    }

    // ------------------------------------------------------------------ saves

    public boolean hasSave() {
        return readSave().isPresent();
    }

    public Optional<Progress> readSave() {
        for (String name : List.of(SAVE, BACKUP)) {
            try {
                Optional<String> text = ctx.storage().read(name);
                if (text.isPresent()) return Optional.of(SaveCodec.decode(text.get()));
            } catch (RuntimeException damaged) {
                // Try the backup next.
            }
        }
        return Optional.empty();
    }

    public void save() {
        String text = SaveCodec.encode(progress);
        try {
            Optional<String> old = ctx.storage().read(SAVE);
            old.ifPresent(previous -> ctx.storage().write(BACKUP, previous));
            ctx.storage().write(SAVE, text);
        } catch (RuntimeException unavailable) {
            // Storage problems never stop play; the next save tries again.
        }
    }

    // ------------------------------------------------------------------ story flow

    public void newGame() {
        progress = new Progress(System.nanoTime() ^ 0x7131A5D5L);
        playStory("prologue", null, () -> arrive(Island.SOUTH));
    }

    public void continueGame(Progress loaded) {
        progress = loaded;
        if (progress.resumeZone() >= 0) {
            Zone zone = Zone.values()[progress.resumeZone()];
            if (art.has(zone.game) && progress.isOpen(zone) && !progress.isCleared(zone)) {
                enterZone(zone, progress.resumeX());
                return;
            }
        }
        go(new MapScreen(this));
    }

    /**
     * Arrives on an island. Islands whose ROM is missing are skipped: their zones count as
     * cleared and their emeralds are handed over, so the story still adds up.
     */
    public void arrive(Island island) {
        while (!art.has(island.game) && island.ordinal() < Island.values().length - 1) {
            for (Zone zone : Zone.of(island)) {
                progress.clear(zone);
                progress.addEmerald(zone.emerald);
                for (var hero : progress.party()) hero.setLevel(Math.max(hero.level(), zone.level + 1));
            }
            joinFor(island);
            island = Island.values()[island.ordinal() + 1];
        }
        progress.setIsland(island);
        progress.setResume(null, 0);
        Island arrived = island;
        save();
        playStory(island.key() + "-arrive", null, () -> {
            joinFor(arrived);
            save();
            go(new MapScreen(this));
        });
    }

    /** Tails joins on West Side Island; Knuckles joins after the Angel Island rivalry. */
    private void joinFor(Island island) {
        if (island == Island.WEST) progress.join(HeroId.TAILS);
        if (island.ordinal() > Island.ANGEL.ordinal()) {
            progress.join(HeroId.TAILS);
            progress.join(HeroId.KNUCKLES);
        }
    }

    /**
     * Plays a story scene over {@code background} (or the current screen), then runs
     * {@code then}. Scenes already seen are not repeated; missing scenes are skipped.
     */
    public void playStory(String scene, Screen background, Runnable then) {
        if (!story.has(scene) || progress.seen(scene)) {
            then.run();
            return;
        }
        progress.markSeen(scene);
        swap(new StoryScreen(this, story.scene(scene), background == null ? screen : background, then));
    }

    /** Plays a scene even when already seen (village talk). */
    public void replayStory(String scene, Screen background, Runnable then) {
        if (!story.has(scene)) {
            then.run();
            return;
        }
        swap(new StoryScreen(this, story.scene(scene), background == null ? screen : background, then));
    }

    /** Loads a zone (level kit, route, block pictures and foes) behind a loading screen. */
    public void enterZone(Zone zone, int resumeX) {
        go(new LoadingScreen(this, zone, stage -> {
            Field field = new Field(zone, stage.path);
            if (resumeX > 0) field.skipTo(resumeX);
            FieldScreen fieldScreen = new FieldScreen(this, stage, field);
            swap(fieldScreen);
            playStory(zone.key + "-enter", fieldScreen, () -> swap(fieldScreen));
        }));
    }

    /** The stage for a zone, reusing the last one built. */
    public Stage stage(Zone zone) {
        if (cachedStage != null && cachedStage.zone == zone) return cachedStage;
        var kit = art.kit(zone);
        if (kit == null) return null;
        FieldPath path = Stage.route(zone, kit);
        cachedStage = new Stage(zone, kit, path);
        return cachedStage;
    }

    /** A boss is beaten: return its emerald, play the clear scene, then move the story on. */
    public void zoneCleared(Zone zone, Screen background) {
        boolean emerald = zone.emerald >= 0 && (progress.emeralds() & (1 << zone.emerald)) == 0;
        progress.addEmerald(zone.emerald);
        progress.clear(zone);
        progress.setResume(null, 0);
        if (zone == Zone.ANGEL_ISLAND) progress.join(HeroId.KNUCKLES);
        progress.restAll();
        if (emerald) audio.jingle(threeislands.audio.Audio.MUS_EMERALD);
        save();
        playStory(zone.key + "-clear", background, () -> {
            List<Zone> zones = Zone.of(zone.island());
            boolean last = zones.get(zones.size() - 1) == zone;
            if (zone == Zone.DEATH_EGG) {
                progress.setFinished(true);
                save();
                playStory("ending", background, () -> go(new EndingScreen(this)));
            } else if (last) {
                arrive(Island.values()[zone.island().ordinal() + 1]);
            } else {
                go(new MapScreen(this));
            }
        });
    }

    /** Opens a battle against {@code group} where the party stands. */
    public void battle(FieldScreen field, Field.Spot spot, List<EnemyKind> group) {
        swap(new BattleScreen(this, field, spot, group));
    }

    public void title() {
        audio.fadeOut();
        go(new TitleScreen(this));
    }
}
