package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Calendar;
import starpost.core.Game;
import starpost.people.Bodies;
import starpost.people.People;
import starpost.people.PeopleArt;
import starpost.people.PeopleSystem;
import starpost.people.Pictures;
import starpost.people.VillagerDef;
import starpost.scene.PlayScreen;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * What every festival event shares: it runs as its own screen over the day's {@link PlayScreen}
 * (the clock stopped, the HUD hidden behind letterbox bars), opens on the festival's title card,
 * plays, and closes on a results panel. Leaving applies the time the festival took and goes back
 * to the same play screen, whose {@code enter} chooses the place's music again.
 */
abstract class FestivalScreen implements Screen {
    static final int CARD_TICKS = 110;
    static final int BAR_TOP = 28;
    static final int BAR_BOTTOM = 24;

    final Shell shell;
    final PlayScreen play;
    final FestivalSystem sys;
    final Festival festival;
    final Festivals festivals;
    /** Ticks since the screen opened, and since the event proper began (after the title card). */
    long ticks;
    int t;
    private long cardUntil;
    private boolean results;
    private long resultsAt;
    private String headline = "";
    private List<String> lines = new ArrayList<>();
    private String caption;
    private String captionWho;
    private long captionAt;
    private boolean left;

    FestivalScreen(FestivalSystem sys, Festival festival) {
        this.sys = sys;
        this.shell = sys.shell;
        this.play = sys.play;
        this.festival = festival;
        this.festivals = sys.festivals;
    }

    @Override
    public void enter(Shell shell) {
        play.clockStopped = true;
        play.hudHidden = true;
        if (entered) {
            resume();
            return;
        }
        entered = true;
        cardUntil = ticks + CARD_TICKS;
        begin();
    }

    /** Whether the screen has been entered once (later entries come back from a handed-over contest). */
    private boolean entered;

    /** Set up the event (called on entering). */
    abstract void begin();

    /** Back from another screen the festival handed the day to (the Ice Cap Festival's lake); nothing by default. */
    void resume() {
    }

    /** One tick of the event itself (after the title card, before results). */
    abstract void step();

    /** Draws the event (the results panel and bars go over it). */
    abstract void paint(SceneCanvas canvas);

    /** Draws over the letterbox bars (a HUD, a countdown); nothing by default. */
    void paintOver(SceneCanvas canvas) {
    }

    /** Sonic 1's HUD in the top bar: TIME as seconds left (red under ten), and a ring count. */
    void hud(SceneCanvas canvas, int secondsLeft, int rings) {
        var hud = shell.art.hud;
        boolean flash = shell.ticks / 8 % 2 == 0;
        hud.row(canvas, secondsLeft < 10 && flash ? hud.timeRed : hud.time, Integer.toString(Math.max(0, secondsLeft)),
                16, 6, 0x28, 0);
        hud.row(canvas, hud.rings, Integer.toString(rings), 120, 6, 0x30, 3);
    }

    /** "3", "2", "1", "GO!" in the middle of the screen for a countdown of {@code ticksLeft}. */
    void countdown(SceneCanvas canvas, int ticksLeft, int total) {
        if (ticksLeft > 0) {
            int n = (ticksLeft + total / 3 - 1) / (total / 3);
            shell.art.hud.number(canvas, Integer.toString(Math.min(3, n)), canvas.width() / 2f - 12, 70, 3);
        } else if (ticksLeft > -45) {
            shell.art.cardFont.centred(canvas, "GO!", 74, SceneDraw.plain());
        }
    }

    @Override
    public final void update(Shell shell) {
        ticks++;
        if (results) {
            if (ticks - resultsAt > 40 && (shell.in.confirm || shell.in.act) && !left) {
                leave();
            }
            return;
        }
        if (ticks < cardUntil) {
            if (ticks > 30 && (shell.in.confirm || shell.in.act)) {
                cardUntil = ticks;
            }
            return;
        }
        t++;
        step();
    }

    boolean carding() {
        return ticks < cardUntil;
    }

    boolean showingResults() {
        return results;
    }

    /**
     * A line from someone at the bottom of the screen for a while. Animals speak in pictures until
     * the Chirp Translator, read from the words as the neighbours' own speech is.
     */
    void caption(String who, String text) {
        caption(who, text, (String[]) null);
    }

    /** A line with its picture version (shown instead, for an animal before the translator). */
    void caption(String who, String text, String... pictures) {
        captionWho = who;
        caption = People.words(text, shell.game);
        captionAt = ticks;
        captionPics = null;
        People people = people();
        VillagerDef v = people == null || who == null ? null : people.cast.get(who);
        if (v != null && people.speaksInPictures(v, shell.game)) {
            captionPics = pictures != null ? pictures : Pictures.fromText(text, shell.catalog, people.cast);
        }
    }

    private String[] captionPics;
    /** How long a caption stays up. */
    int captionTicks = 200;

    People people() {
        return shell.game.section(People.class);
    }

    /** Whether a neighbour is in the valley today (arrived, and not the one farming). */
    boolean present(String id) {
        People people = people();
        VillagerDef v = people == null ? null : people.cast.get(id);
        return v != null && people.present(v, shell.game);
    }

    /** A neighbour's body key (the People lane's), with a fallback for a game without them. */
    String bodyOf(String id) {
        People people = people();
        VillagerDef v = people == null ? null : people.cast.get(id);
        return v == null ? body(id) : v.body();
    }

    /** Everyone in the valley today who can come to a festival: not pets, not the totem. */
    List<String> crowd() {
        List<String> out = new ArrayList<>();
        People people = people();
        if (people == null) {
            return out;
        }
        for (VillagerDef v : people.cast.all()) {
            if (people.present(v, shell.game) && !v.isPet() && !v.body().equals("totem")) {
                out.add(v.id);
            }
        }
        return out;
    }

    /** Draws a neighbour standing at screen (x, feet) in a pose ({@code Bodies.IDLE}...). */
    void drawVillager(SceneCanvas canvas, String id, float x, float feet, int pose, boolean facingLeft, float hop,
            SceneDraw tint) {
        Bodies.draw(sys.peopleArt(), canvas, bodyOf(id), pose, pose == Bodies.WALK ? ticks : shell.ticks, x, feet,
                facingLeft, hop, tint);
    }

    /**
     * The crowd where {@link Festivals#spot} gathers it by the festival's sign, drawn by the
     * festival itself (the neighbours are {@link #offstage}) so that one of them, {@code except},
     * can play a part elsewhere. They face the farmer; {@code cheering} has them celebrate.
     */
    void drawCrowd(SceneCanvas canvas, int cx, int cy, String except, boolean cheering) {
        People people = people();
        if (people == null) {
            return;
        }
        int ax = sys.anchorX(festival);
        float farmerX = play.valley().pose.x;
        int i = 0;
        for (VillagerDef v : people.cast.all()) {
            starpost.people.Spot spot = v.id.equals(except) ? null : festivals.spot(v, shell.game);
            if (spot == null || spot.inside() || spot.farm()) {
                continue;
            }
            int x = ax + spot.dx();
            boolean hopper = v.body().startsWith("animal:");
            float hop = cheering && hopper ? Math.abs((float) Math.sin((shell.ticks + i * 7) * 0.2)) * 4 : 0;
            drawVillager(canvas, v.id, x - cx, sys.floor(x) - cy, cheering ? Bodies.HAPPY : Bodies.IDLE, farmerX < x,
                    hop, SceneDraw.plain());
            i++;
        }
    }

    /** Takes the neighbours off the valley's stage (this festival draws them itself), or puts them back. */
    void offstage(boolean value) {
        PeopleSystem people = PeopleSystem.of(play);
        if (people != null) {
            people.offstage = value;
        }
    }

    /** Ends the event: the results panel, then back to the day. */
    void finish(String headline, List<String> lines) {
        this.headline = headline;
        this.lines = new ArrayList<>(lines);
        this.lines.removeIf("+0 RINGS"::equals);          // nothing paid: nothing to say
        results = true;
        resultsAt = ticks;
        shell.sfx(Sfx.PERFECT);
    }

    /** Back to the day: the clock moves on by the festival's length; the play screen takes over. */
    void leave() {
        left = true;
        Game game = shell.game;
        Calendar c = game.calendar;
        c.set(c.year(), c.season(), c.day(), festival.after(c.minutes()));
        restore();
        sys.afterFestival(festival);
        shell.go(play);
    }

    /** Ceremonies use a stationary ROM stage; menus and timed gestures need no platforming. */
    void restore() { play.clockStopped=false; play.hudHidden=false; }
    void holdTown(float x) { play.placeInValley(x); }
    void stepTown() { }

    /** Scripted stillness: no buttons this tick (a countdown, a speech). */
    void freezeInput() {
        var in = shell.in;
        in.consume();
        in.left = in.right = in.up = in.down = in.jumpHeld = false;
    }

    @Override
    public final void draw(Shell shell, SceneCanvas canvas) {
        paint(canvas);
        int w = canvas.width(), h = canvas.height();
        canvas.fill(0, 0, w, BAR_TOP, 0xFF000000);
        canvas.fill(0, h - BAR_BOTTOM, w, BAR_BOTTOM, 0xFF000000);
        if (!carding()) {
            paintOver(canvas);
        }
        if (caption != null && !captionHidden()) {
            drawCaption(canvas);
        }
        if (carding()) {
            drawCard(canvas);
        }
        if (results) {
            drawResults(canvas);
        }
    }

    /**
     * The festival's title card, after the morning card: black, the red banner dropping in on the
     * left and the name sliding in from the right in the title-card lettering; then it opens on
     * the event.
     */
    private void drawCard(SceneCanvas canvas) {
        long age = ticks;
        long out = cardUntil - ticks;
        int w = canvas.width(), h = canvas.height();
        int alpha = (int) Math.min(255, Math.min(age * 24, out * 16));
        canvas.fill(0, 0, w, h, alpha << 24);
        int in = (int) Math.max(0, 320 - age * 16) + (int) Math.max(0, 16 - out) * 24;
        if (shell.art.cardBanner != null) {
            canvas.draw(shell.art.cardBanner, w / 2f - 130, -in * 0.7f, SceneDraw.plain().withAlpha(alpha / 255f));
        }
        var font = shell.art.cardFont;
        String[] words = festival.name.split(" ");
        String top = words.length > 2 ? words[0] + " " + words[1] : words[0];
        String bottom = festival.name.substring(top.length()).trim();
        font.draw(canvas, top, w / 2f - 50 + in, 66, SceneDraw.plain());
        if (!bottom.isEmpty()) {
            font.draw(canvas, bottom, w / 2f - 30 + in * 1.4f, 96, SceneDraw.plain());
        }
        if (age > 24 && out > 12) {
            Text.shadow(canvas, festival.where, (int) (w / 2f - 50 + in), 132, Text.WHITE);
            Text.shadow(canvas, Calendar.seasonName(festival.season) + " " + festival.day, (int) (w / 2f - 50 + in),
                    146, Text.YELLOW);
        }
    }

    /** Whether the caption is kept out of sight now (a booth's own screen is open over the fair); no by default. */
    boolean captionHidden() {
        return false;
    }

    /** Takes the caption down (the fair's welcome, once the farmer is at a booth). */
    void clearCaption() {
        caption = null;
    }

    /**
     * The speaker's line at the foot of the screen, three lines at a time: a longer line turns
     * its pages every {@value #CAPTION_PAGE} ticks rather than losing its end. Picture speech
     * shows its pictures one by one.
     */
    private void drawCaption(SceneCanvas canvas) {
        int w = canvas.width(), h = canvas.height();
        int boxH = 40, y = h - BAR_BOTTOM - boxH - 4, x = 8, bw = w - 16;
        long age = ticks - captionAt;
        SceneSprite face = captionWho == null ? null : sys.peopleArt().portrait(bodyOf(captionWho), ticks);
        int textX = face != null ? x + 40 : x + 8;
        List<String> wrapped = captionPics != null ? List.of() : Text.wrap(canvas, caption, bw - (textX - x) - 8);
        int pages = Math.max(1, (wrapped.size() + 2) / 3);
        int shownFor = captionPics != null ? Math.max(captionTicks, captionPics.length * 16 + 120)
                : Math.max(captionTicks, pages * CAPTION_PAGE);
        if (age >= shownFor) {
            return;
        }
        solidPanel(canvas, x, y, bw, boxH);
        if (face != null) {
            boolean flip = PeopleArt.facesLeft(bodyOf(captionWho));
            canvas.draw(face, x + 20, y + boxH - 4 - (face.height() - face.originY()), SceneDraw.plain().withFlipX(flip));
        }
        if (captionPics != null) {
            People people = people();
            int shown = (int) Math.min(captionPics.length, age / 16 + 1);
            float px = textX;
            for (int i = 0; i < shown; i++) {
                int tw = sys.peopleArt().drawToken(canvas, captionPics[i], px, y + 4, boxH - 8, shell.catalog,
                        people.cast, shell.game.farmer, SceneDraw.plain(), true);
                px += tw + 6;
            }
            return;
        }
        int page = (int) Math.min(pages - 1, age / CAPTION_PAGE);
        for (int i = page * 3; i < Math.min(page * 3 + 3, wrapped.size()); i++) {
            Text.shadow(canvas, wrapped.get(i), textX, y + 6 + (i - page * 3) * 11, Text.WHITE);
        }
        if (page < pages - 1 && ticks / 12 % 2 == 0) {
            canvas.fill(x + bw - 14, y + boxH - 9, 6, 2, Text.YELLOW);       // more to come
            canvas.fill(x + bw - 13, y + boxH - 7, 4, 1, Text.YELLOW);
            canvas.fill(x + bw - 12, y + boxH - 6, 2, 1, Text.YELLOW);
        }
    }

    /** Ticks each page of three caption lines stays up. */
    private static final int CAPTION_PAGE = 170;

    /**
     * The results: the headline in the title-card lettering, then each line wrapped to the panel
     * (the first in yellow), over everything the event drew.
     */
    private void drawResults(SceneCanvas canvas) {
        int w = canvas.width();
        int pw = 376, px = (w - pw) / 2, py = 36;
        List<String> rows = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            for (String row : Text.wrap(canvas, lines.get(i), pw - 20)) {
                rows.add(row);
                colours.add(i == 0 ? Text.YELLOW : Text.WHITE);
            }
        }
        int ph = 44 + rows.size() * 12 + 16;
        solidPanel(canvas, px, py, pw, ph);
        shell.art.cardFont.centred(canvas, headline, py + 8, SceneDraw.plain());
        for (int i = 0; i < rows.size(); i++) {
            Text.centred(canvas, rows.get(i), py + 40 + i * 12, colours.get(i));
        }
        if (ticks - resultsAt > 40 && ticks / 20 % 2 == 0) {
            Text.centred(canvas, "PRESS JUMP", py + ph - 14, Text.GREY);
        }
    }

    /**
     * A panel that hides what lies under it. {@code Text.panel} lets the world show through, which
     * ghosts signs and labels behind a festival's text; its own colour, made solid, does not.
     */
    static void solidPanel(SceneCanvas canvas, int x, int y, int w, int h) {
        canvas.fill(x, y, w, h, 0xFF000000 | Text.PANEL);
        Text.panel(canvas, x, y, w, h);
    }

    /** The festival's name in the top bar, for events without a HUD of their own. */
    void title(SceneCanvas canvas) {
        Text.shadow(canvas, festival.name, 16, 10, Text.YELLOW);
    }

    /** The People lane's body key for a villager id (the hero bodies are "hero:" + id). */
    static String body(String who) {
        return switch (who) {
            case "sonic", "tails", "knuckles" -> "hero:" + who;
            case "robotnik" -> "robotnik";
            case "rusty" -> "eggrobo";
            case "dandel" -> "animal:pocky";
            case "clementine" -> "animal:cucky";
            case "pud" -> "animal:picky";
            case "barnaby" -> "animal:rocky";
            case "frost" -> "animal:pecky";
            case "hazel" -> "animal:ricky";
            case "pip" -> "animal:flicky";
            default -> who;
        };
    }

    /** "FIRST", "SECOND"... (the title-card lettering has no digits). */
    static String placeWord(int place) {
        return switch (place) {
            case 1 -> "FIRST";
            case 2 -> "SECOND";
            case 3 -> "THIRD";
            case 4 -> "FOURTH";
            default -> "FIFTH";
        };
    }

    static String clock(int ticks) {
        return Festivals.watch(ticks);
    }

    static String ordinal(int place) {
        return Festivals.ordinal(place);
    }

    static String seconds(int ticks) {
        return Festivals.seconds(ticks);
    }
}
