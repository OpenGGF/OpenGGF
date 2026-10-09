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
        cardUntil = ticks + CARD_TICKS;
        begin();
    }

    /** Set up the event (called on entering). */
    abstract void begin();

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
        canvas.draw(secondsLeft < 10 && flash ? hud.timeRed : hud.time, 16, 6, SceneDraw.plain());
        hud.number(canvas, Integer.toString(Math.max(0, secondsLeft)), 66, 2);
        canvas.draw(hud.rings, 120, 6, SceneDraw.plain());
        hud.number(canvas, Integer.toString(rings), 170, 2);
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
        this.lines = lines;
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

    /** Puts the play screen back as it was (called on leaving; subclasses add their own). */
    void restore() {
        play.clockStopped = false;
        play.hudHidden = false;
        if (heldTown) {
            heldTown = false;
            play.places.putAll(heldPlaces);
            play.valley().interact = heldInteract;
            play.valley().labels = true;
            play.actors.addAll(heldPickups);
        }
    }

    private boolean heldTown;
    private final java.util.Map<String, java.util.function.Consumer<Shell>> heldPlaces = new java.util.LinkedHashMap<>();
    private java.util.function.BooleanSupplier heldInteract;
    private final List<starpost.scene.Actor> heldPickups = new ArrayList<>();

    /**
     * For events played in the valley itself: doorways and talking are off, doorway labels hidden,
     * and the day's rings and forage put aside (an event must not change the save by the way),
     * all restored on leaving. The farmer stands at {@code x}.
     */
    void holdTown(float x) {
        heldTown = true;
        heldPlaces.putAll(play.places);
        play.places.clear();
        heldInteract = play.valley().interact;
        play.valley().interact = () -> false;
        play.valley().labels = false;
        for (starpost.scene.Actor a : play.actors) {
            if (a instanceof starpost.valley.Pickups.Pickup) {
                heldPickups.add(a);
            }
        }
        play.actors.removeAll(heldPickups);
        if (play.onFarm() || Math.abs(play.valley().runner.x - x) > 1) {
            play.placeInValley(x);
        }
    }

    /** The valley's update with the farmer kept east of the farm gate (no folding away mid-event). */
    void stepTown() {
        var runner = play.valley().runner;
        if (runner.x < 200) {
            runner.x = 200;
            runner.speed = Math.max(0, runner.speed);
        }
        play.update(shell);
    }

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
        if (caption != null && ticks - captionAt < captionTicks) {
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

    private void drawCaption(SceneCanvas canvas) {
        int w = canvas.width(), h = canvas.height();
        int boxH = 40, y = h - BAR_BOTTOM - boxH - 4, x = 8, bw = w - 16;
        Text.panel(canvas, x, y, bw, boxH);
        int textX = x + 8;
        if (captionWho != null) {
            SceneSprite face = sys.peopleArt().portrait(bodyOf(captionWho), ticks);
            if (face != null) {
                boolean flip = PeopleArt.facesLeft(bodyOf(captionWho));
                canvas.draw(face, x + 20, y + boxH - 4 - (face.height() - face.originY()), SceneDraw.plain().withFlipX(flip));
                textX = x + 40;
            }
        }
        if (captionPics != null) {
            People people = people();
            int shown = (int) Math.min(captionPics.length, (ticks - captionAt) / 16 + 1);
            float px = textX;
            for (int i = 0; i < shown; i++) {
                int tw = sys.peopleArt().drawToken(canvas, captionPics[i], px, y + 4, boxH - 8, shell.catalog,
                        people.cast, shell.game.farmer, SceneDraw.plain(), true);
                px += tw + 6;
            }
            return;
        }
        List<String> wrapped = Text.wrap(canvas, caption, bw - (textX - x) - 8);
        for (int i = 0; i < Math.min(3, wrapped.size()); i++) {
            Text.shadow(canvas, wrapped.get(i), textX, y + 6 + i * 11, Text.WHITE);
        }
    }

    private void drawResults(SceneCanvas canvas) {
        int w = canvas.width();
        int pw = 376, ph = 46 + lines.size() * 12 + 18, px = (w - pw) / 2, py = 40;
        Text.panel(canvas, px, py, pw, ph);
        shell.art.cardFont.centred(canvas, headline, py + 8, SceneDraw.plain());
        for (int i = 0; i < lines.size(); i++) {
            Text.centred(canvas, Text.fit(canvas, lines.get(i), pw - 16), py + 40 + i * 12,
                    i == 0 ? Text.YELLOW : Text.WHITE);
        }
        if (ticks - resultsAt > 40 && ticks / 20 % 2 == 0) {
            Text.centred(canvas, "PRESS JUMP", py + ph - 14, Text.GREY);
        }
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

    /** "0:42" in Sonic 1's HUD digits' terms (minutes and seconds). */
    static String clock(int ticks) {
        int s = Math.max(0, ticks / 60);
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    /** "1ST", "2ND"... */
    static String ordinal(int place) {
        return place + switch (place) {
            case 1 -> "ST";
            case 2 -> "ND";
            case 3 -> "RD";
            default -> "TH";
        };
    }

    /** A seconds clock from ticks: "42.5". */
    static String seconds(int ticks) {
        int tenths = ticks / 6;
        return tenths / 10 + "." + tenths % 10;
    }
}
