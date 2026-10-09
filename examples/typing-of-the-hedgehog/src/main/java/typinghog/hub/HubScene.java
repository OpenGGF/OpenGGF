package typinghog.hub;

import com.openggf.mods.scene.DebuggableScene;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneLaunch;
import com.openggf.mods.scene.SceneRomArt;
import java.util.List;
import typinghog.hub.model.Handoff;
import typinghog.hub.model.Profile;
import typinghog.hub.model.ResultMerge;
import typinghog.hub.model.SchoolMaterial;
import typinghog.hub.model.StoryBook;
import typinghog.play.CourseAssets;
import typinghog.save.ActResult;
import typinghog.save.LaunchIntent;

/**
 * The hub: Typing of the Hedgehog's startup scene. Title, main menu, the story's chapter map
 * across every supplied ROM, Arcade, Boss Rush and Endless act lists, Tails' Typing School,
 * options and records, and the results screen gameplay returns to.
 *
 * <p>Hand-offs (see {@link Handoff}): starting an act of the running game writes
 * {@code intent.txt} (and the hub's own copy) and calls {@code startStockAct}; an act in another
 * game, or one needing a different team, saves {@code pending.txt} and {@code relaunch}es, and
 * the reopened hub continues straight into it. When the course returns, the hub reads and
 * deletes {@code result.txt}, merges it into {@code profile.txt} and shows the results.
 *
 * <p>Debug commands ({@link #debugJump}): {@code title}, {@code menu}, {@code story[:game]},
 * {@code arcade}, {@code endless}, {@code rush}, {@code school}, {@code drill:NAME},
 * {@code options}, {@code records[:tab]}, {@code results:sample|fail|emerald}, {@code type:TEXT}
 * (typed on the player's behalf next tick), {@code enter}, {@code demo} (a sample profile, all
 * rails treated as built, for screenshots) and {@code save}.
 */
public final class HubScene implements ModScene, DebuggableScene {
    static final String MOD_FOLDER = "saves/mods/typing-of-the-hedgehog/";

    final CourseAssets assets;
    final HubAssets files;

    SceneContext ctx;
    Profile profile = new Profile();
    StoryBook story;
    SchoolMaterial material;
    HubArt art;
    HubFont font;
    HubAudio audio;
    final Controls controls = new Controls();
    String game = "s1";
    List<String> games = List.of();
    SceneLaunch launch;
    Stage stage;
    long ticks;
    long screenTick;
    boolean leaving;
    long leaveTick;
    /** Every story stop is treated as railed (screenshots before the rails land). */
    boolean demoRails;
    private Handoff.Pending pendingStart;
    private long pendingAt;
    private String injected = "";
    private boolean injectedAccept;
    private String toast = "";
    private int toastColour;
    private long toastTick = -1000;

    final TitleScreen title = new TitleScreen();
    final MenuScreen menu = new MenuScreen();
    final StoryScreen storyMap = new StoryScreen();
    final ActListScreen arcade = new ActListScreen(LaunchIntent.Mode.ARCADE);
    final ActListScreen endless = new ActListScreen(LaunchIntent.Mode.ENDLESS);
    final ActListScreen rush = new ActListScreen(LaunchIntent.Mode.BOSS_RUSH);
    final ResultsScreen results = new ResultsScreen();
    final SchoolScreen school = new SchoolScreen();
    final OptionsScreen options = new OptionsScreen();
    final RecordsScreen records = new RecordsScreen();
    Screen screen = title;

    public HubScene(CourseAssets assets, HubAssets files) {
        this.assets = assets;
        this.files = files;
    }

    // ---- lifecycle ----------------------------------------------------------------------------

    @Override public void enter(SceneContext ctx) {
        this.ctx = ctx;
        profile = Profile.decode(ctx.storage().read(Profile.FILE).orElse(null));
        try {
            story = StoryBook.parse(files.story());
        } catch (RuntimeException damaged) {
            story = StoryBook.parse("");
        }
        material = new SchoolMaterial(assets.lexicon(), files.school());
        art = new HubArt(ctx, files);
        font = new HubFont(files.plateFont(), files.hubGlyphs());
        launch = ctx.currentLaunch();
        SceneRomArt running = ctx.art().rom();
        game = launch != null ? launch.gameId() : running != null ? running.gameId() : "s1";
        games = ctx.art().availableGames();
        audio = new HubAudio(ctx.audio(), game, assets.sounds(game));
        stage = art.titleStage(game, (int) (profile.launches() % 97), ctx.width(), ctx.height());

        ActResult result = ctx.storage().read(ActResult.FILE).map(HubScene::decodeResult).orElse(null);
        ctx.storage().delete(ActResult.FILE);
        Handoff.Pending pending = ctx.storage().read(Handoff.PENDING).map(Handoff.Pending::decode).orElse(null);
        ctx.storage().delete(Handoff.PENDING);
        if (result != null) {
            LaunchIntent launched = ctx.storage().read(Handoff.LAUNCHED).map(HubScene::decodeIntent).orElse(null);
            ctx.storage().delete(Handoff.LAUNCHED);
            ResultMerge.Outcome outcome = ResultMerge.merge(profile, result, launched);
            Handoff.Rush rushState = ctx.storage().read(Handoff.RUSH).map(Handoff.Rush::decode).orElse(null);
            save();
            results.show(this, outcome, rushState);
            go(results);
        } else if (pending != null && pending.game().equals(game)) {
            // We relaunched for this act: open straight into it after a short card.
            pendingStart = pending;
            pendingAt = 50;
            title.skipIntro();
            go(title);
        } else {
            go(title);
        }
    }

    @Override public void update(SceneContext ctx) {
        this.ctx = ctx;
        ticks++;
        controls.begin(ctx, injected, injectedAccept);
        injected = "";
        injectedAccept = false;
        if (pendingStart != null) {
            if (ticks >= pendingAt) {
                Handoff.Pending p = pendingStart;
                pendingStart = null;
                start(p.mode(), p.game(), p.zone(), p.act(), profile.stars(p.game(), p.zone(), p.act()) + 1, false);
            }
        } else if (!leaving) {
            screen.update(this);
        }
        audio.tick(ticks);
    }

    @Override public void draw(SceneContext ctx, SceneCanvas c) {
        screen.draw(this, c);
        if (pendingStart != null) drawCard(c, "NOW ENTERING", stopTitle(pendingStart.game(), pendingStart.zone(),
                pendingStart.act()));
        long since = ticks - screenTick;
        if (since < 10) Gfx.fade(c, 1 - since / 10f);
        if (leaving) Gfx.fade(c, Math.min(1, (ticks - leaveTick) / 30f));
        drawToast(c);
    }

    @Override public void exit(SceneContext ctx) {
        if (audio != null && !leaving) audio.dispose();
        save();
    }

    // ---- navigation ---------------------------------------------------------------------------

    void go(Screen next) {
        screen = next;
        screenTick = ticks;
        controls.consumeTyped();
        next.open(this);
    }

    /** The screen's stable name, for tests and tools. */
    public String screen() { return screen.name(); }

    void save() {
        if (ctx != null && !ctx.storage().write(Profile.FILE, profile.encode())) {
            toast("COULD NOT SAVE YOUR PROFILE", Gfx.RED);
        }
    }

    void toast(String text, int colour) {
        toast = text;
        toastColour = colour;
        toastTick = ticks;
    }

    // ---- facts the screens share --------------------------------------------------------------

    int width() { return ctx.width(); }
    int height() { return ctx.height(); }

    boolean available(String g) { return games.contains(g); }

    /** True when an act has a rail (or the demo pretends every act does). */
    boolean railed(String g, int zone, int act) {
        return demoRails || assets.rail(g, zone, act) != null;
    }

    boolean railed(StoryBook.Stop stop) { return railed(stop.game(), stop.zone(), stop.act()); }

    /** Any story act cleared in any game (Arcade, Boss Rush and Endless open with the first). */
    boolean anyCleared() {
        for (String g : List.of("s1", "s2", "s3k")) if (!profile.cleared(g).isEmpty()) return true;
        return false;
    }

    String stopTitle(String g, int zone, int act) {
        StoryBook.Stop stop = story.find(g, zone, act);
        return stop != null ? stop.title() : "ZONE " + zone;
    }

    static String gameName(String g) {
        return switch (g) {
            case "s1" -> "SONIC 1";
            case "s2" -> "SONIC 2";
            case "s3k" -> "SONIC 3 & KNUCKLES";
            default -> g.toUpperCase();
        };
    }

    static String gameShort(String g) {
        return switch (g) {
            case "s1" -> "S1";
            case "s2" -> "S2";
            default -> "S3K";
        };
    }

    // ---- hand-offs ----------------------------------------------------------------------------

    /**
     * Starts an act: here when the running game and team fit, otherwise through a relaunch into
     * {@code g} that continues into the act. Returns false (with a notice) when it cannot.
     */
    boolean play(LaunchIntent.Mode mode, String g, int zone, int act, int bossStars) {
        return start(mode, g, zone, act, bossStars, true);
    }

    /** As {@link #play}; a hub reopened by a relaunch never relaunches again for the same act. */
    private boolean start(LaunchIntent.Mode mode, String g, int zone, int act, int bossStars, boolean mayRelaunch) {
        if (!available(g)) {
            refuse("SUPPLY THE " + gameName(g) + " ROM FIRST");
            return false;
        }
        if (!g.equals(game) || !teamFits(g)) {
            if (!mayRelaunch) {
                refuse("COULD NOT SWITCH TO " + gameName(g) + " WITH THE RIGHT TEAM");
                return false;
            }
            ctx.storage().write(Handoff.PENDING, new Handoff.Pending(mode, g, zone, act).encode());
            save();
            if (ctx.relaunch(new SceneLaunch(g, Handoff.MAIN_CHARACTER, Handoff.sidekicks(g)))) {
                leave();
                return true;
            }
            ctx.storage().delete(Handoff.PENDING);
            refuse("COULD NOT SWITCH TO " + gameName(g));
            return false;
        }
        long seed = Handoff.seed(profile.nextLaunch(), ticks);
        LaunchIntent intent = Handoff.intent(mode, g, zone, act, profile, bossStars, seed);
        ctx.storage().write(LaunchIntent.FILE, intent.encode());
        ctx.storage().write(Handoff.LAUNCHED, intent.encode());
        save();
        if (ctx.startStockAct(zone, act)) {
            leave();
            return true;
        }
        ctx.storage().delete(LaunchIntent.FILE);
        ctx.storage().delete(Handoff.LAUNCHED);
        refuse("THIS ACT CANNOT START HERE");
        return false;
    }

    /** Starts a Boss Rush through {@code bosses} ({@code zone:act}) of {@code g}. */
    boolean startRush(String g, List<String> bosses) {
        if (bosses.isEmpty()) return false;
        Handoff.Rush state = new Handoff.Rush(g, bosses, 0, 0);
        ctx.storage().write(Handoff.RUSH, state.encode());
        boolean ok = play(LaunchIntent.Mode.BOSS_RUSH, g, state.nextZone(), state.nextAct(),
                profile.stars(g, state.nextZone(), state.nextAct()) + 1);
        if (!ok) ctx.storage().delete(Handoff.RUSH);
        return ok;
    }

    private boolean teamFits(String g) {
        return launch == null || Handoff.teamMatches(g, launch.mainCharacter(), launch.sidekicks());
    }

    private void leave() {
        int id = audio.sfx(HubAudio.Cue.START);
        if (id >= 0) ctx.audio().playSfx(id);
        audio.leaving();
        leaving = true;
        leaveTick = ticks;
    }

    void refuse(String why) {
        audio.cue(HubAudio.Cue.DENIED);
        toast(why, Gfx.RED);
    }

    void quit() {
        audio.leaving();
        leaving = true;
        leaveTick = ticks;
        save();
        ctx.exitToMasterTitle();
    }

    // ---- shared drawing -----------------------------------------------------------------------

    /** The title's set behind a menu, dimmed. */
    void backdrop(SceneCanvas c, float dim) {
        stage.draw(c, ticks);
        c.fill(0, 0, c.width(), c.height(), Gfx.alpha(0xFF060C24, dim));
    }

    /** The heading strip every menu screen shares. */
    void header(SceneCanvas c, String title, String right) {
        Gfx.gradient(c, 0, 0, c.width(), 22, 0xF0183C9C, 0xF00C1E5C);
        c.fill(0, 22, c.width(), 1, Gfx.GOLD);
        c.fill(0, 23, c.width(), 1, 0x80000000);
        font.outline(c, title, 8, 3, Gfx.WHITE, 0xFF0A1440, 2);
        if (right != null && !right.isEmpty()) font.right(c, right, c.width() - 8, 7, Gfx.GOLD, 1);
    }

    /** The bottom strip: what the player has typed, and a hint. */
    void footer(SceneCanvas c, String typed, String hint) {
        int y = c.height() - 16;
        Gfx.gradient(c, 0, y, c.width(), 16, 0xE00C1E5C, 0xF0060C24);
        c.fill(0, y, c.width(), 1, 0xFF5A8CF0);
        String shown = "> " + typed + ((ticks / 16) % 2 == 0 ? "_" : " ");
        font.shadow(c, shown, 6, y + 4, Gfx.TYPED, 1);
        if (hint != null) font.right(c, hint, c.width() - 6, y + 4, Gfx.DIM, 1);
    }

    void emeralds(SceneCanvas c, int x, int y, int count) {
        var gem = art.image("emerald");
        for (int i = 0; i < Profile.EMERALDS; i++) {
            int colour = i < count ? HubArt.emeraldColour(i) : 0x60404858;
            if (gem != null) {
                float sparkle = i < count && (ticks / 6 + i * 7) % 40 == 0 ? 1 : 0;
                c.draw(gem, x + i * 11, y, com.openggf.mods.scene.SceneDraw.plain().withTint(colour)
                        .withFlash(sparkle > 0 ? 0x80FFFFFF : 0));
            } else {
                c.fill(x + i * 11 + 2, y + 2, 6, 6, colour);
            }
        }
    }

    void ring(SceneCanvas c, int x, int y, long t) {
        var set = art.ring(game);
        if (set == null || set.frameCount() == 0) {
            Gfx.outline(c, x - 4, y - 4, 8, 8, Gfx.GOLD);
            return;
        }
        int frames = Math.min(4, set.frameCount());
        c.draw(set.frame((int) ((t / 6) % frames)), x, y, com.openggf.mods.scene.SceneDraw.plain());
    }

    private void drawCard(SceneCanvas c, String top, String name) {
        c.fill(0, 0, c.width(), c.height(), 0xC0000010);
        int y = c.height() / 2 - 24;
        Gfx.gradient(c, 0, y, c.width(), 48, 0xF02050D0, 0xF0102870);
        c.fill(0, y, c.width(), 2, Gfx.GOLD);
        c.fill(0, y + 46, c.width(), 2, Gfx.GOLD);
        font.centred(c, top, c.width() / 2, y + 8, Gfx.GOLD, 1);
        font.centred(c, name.toUpperCase(), c.width() / 2, y + 22, Gfx.WHITE, 2);
    }

    private void drawToast(SceneCanvas c) {
        long age = ticks - toastTick;
        if (toast.isEmpty() || age > 150) return;
        float a = age < 120 ? 1 : 1 - (age - 120) / 30f;
        int w = font.width(toast, 1) + 16;
        int x = (c.width() - w) / 2, y = 30;
        Gfx.panel(c, x, y, w, 17, Gfx.alpha(0xF0101830, a), Gfx.alpha(toastColour, a));
        font.draw(c, toast, x + 8, y + 4, Gfx.alpha(Gfx.WHITE, a), 1);
    }

    // ---- storage helpers ----------------------------------------------------------------------

    private static ActResult decodeResult(String text) {
        try {
            return ActResult.decode(text);
        } catch (RuntimeException damaged) {
            return null;
        }
    }

    private static LaunchIntent decodeIntent(String text) {
        try {
            return LaunchIntent.decode(text);
        } catch (RuntimeException damaged) {
            return null;
        }
    }

    /** A user word list's size, for the options screen. */
    int userWords() { return assets.lexicon().entries("user").size(); }

    // ---- debug --------------------------------------------------------------------------------

    @Override public boolean debugJump(String command) {
        String[] parts = command.split(":", 2);
        String arg = parts.length > 1 ? parts[1] : "";
        switch (parts[0]) {
            case "title" -> go(title);
            case "menu" -> go(menu);
            case "story" -> {
                if (!arg.isEmpty()) storyMap.chapter(this, arg);
                go(storyMap);
            }
            case "arcade" -> go(arcade);
            case "endless" -> go(endless);
            case "rush" -> go(rush);
            case "school" -> go(school);
            case "drill" -> {
                go(school);
                try {
                    school.start(this, typinghog.hub.model.Drill.valueOf(arg));
                } catch (IllegalArgumentException unknown) {
                    return false;
                }
            }
            case "options" -> go(options);
            case "records" -> {
                if (!arg.isEmpty()) records.tab(arg);
                go(records);
            }
            case "results" -> {
                results.sample(this, arg);
                go(results);
            }
            case "type" -> injected += arg;
            case "enter" -> injectedAccept = true;
            case "demo" -> demo();
            case "save" -> save();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** A sample profile for screenshots: early progress in every chapter, grades and key stats. */
    private void demo() {
        demoRails = true;
        Profile p = new Profile();
        String[][] cleared = {{"s1", "0:2", "1:2", "2:2"}, {"s2", "0:1", "1:1"}, {"s3k", "0:1", "1:1", "2:1", "3:1"}};
        for (String[] row : cleared) {
            for (int i = 1; i < row.length; i++) {
                String[] za = row[i].split(":");
                int z = Integer.parseInt(za[0]), a = Integer.parseInt(za[1]);
                p.clear(row[0], z, a);
                p.awardEmerald(row[0], z, a);
                for (LaunchIntent.Difficulty d : List.of(LaunchIntent.Difficulty.NORMAL)) {
                    for (int k = 0; k < 6; k++) {
                        p.file(Profile.actTable(row[0], z, a, d),
                                new Profile.Entry(42000 - k * 4100 - i * 900, "SABBCD".substring(k, k + 1), 64 - k * 5.5));
                    }
                }
                p.raiseStars(row[0], z, a);
                if (i == 1) p.raiseStars(row[0], z, a);
            }
        }
        p.recordGrade("HOME_ROW", "A");
        p.recordGrade("TOP_ROW", "B");
        p.recordGrade("BOTTOM_ROW", "C");
        p.recordGrade("RING_RUSH", "B");
        p.recordGrade("SUDDEN_DEATH", "D");
        p.recordGrade("REFLEX", "A");
        p.recordGrade("PHRASES", "C");
        p.keyHits().addEncoded("a:420,s:380,d:360,f:400,g:150,h:170,j:390,k:300,l:310,e:600,t:520,o:480,i:450,"
                + "n:440,r:410,u:220,c:180,m:160,w:140,y:130,p:120,b:90,v:70,k:40,x:20,q:15,z:12,_:900");
        p.keyMisses().addEncoded("q:9,z:7,x:6,p:14,b:8,v:6,y:5,u:3,e:4,t:2,m:7,c:4,w:5");
        p.addTyping(5200, 260, 1040, 24.5);
        profile = p;
    }
}
