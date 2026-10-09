package starpost.people;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import starpost.art.Art;
import starpost.core.Game;
import starpost.core.Item;
import starpost.farm.FarmView;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Shell;
import starpost.scene.Sfx;
import starpost.valley.Valley;

/**
 * The neighbours in play: one {@link VillagerActor} each, plus this director, an invisible actor
 * that runs the morning (letters, Partners' perks), sends the Flicky post to the farmer, and
 * starts heart events when their moment comes. Installed by {@code Systems.install}.
 */
public final class PeopleSystem implements Actor {
    /** The belt-view field's back row (FarmView's DEPTH_MIN): feet rows are this plus depth. */
    static final int FARM_FEET = FarmView.FIELD_TOP + 4;
    private static final int EVENT_CHECK_TICKS = 10;
    private static final int LOOP_BLOCK = 53;

    final Shell shell;
    final PlayScreen play;
    final People people;
    final PeopleArt art;
    final Map<String, VillagerActor> actors = new LinkedHashMap<>();
    private boolean morningDone;
    private final Deque<String> notices = new ArrayDeque<>();
    private long noticeAt = -1000;
    // The Flicky post: 0 idle, 1 flying in.
    private int post;
    private float postX;
    private float postHeight;
    /** The neighbour nearest the farmer, whose name is shown (one at a time). */
    VillagerActor labelled;
    /** A letter left in the box for want of room: not re-delivered until something new arrives. */
    String heldLetter;
    /**
     * While true the neighbours are not drawn or talked to: a festival that stages the valley's
     * cast itself (a parade, a feast) sets it and clears it when it ends.
     */
    public boolean offstage;
    // The farmer's emote during events.
    String farmerEmote;
    long farmerEmoteUntil;

    private PeopleSystem(Shell shell, PlayScreen play, People people, PeopleArt art) {
        this.shell = shell;
        this.play = play;
        this.people = people;
        this.art = art;
        for (VillagerDef v : people.cast.all()) {
            VillagerActor actor = new VillagerActor(v, this);
            actors.put(v.id, actor);
        }
        for (VillagerActor actor : actors.values()) {
            actor.snap();
        }
    }

    /** Adds the neighbours to a new play screen (a no-op for a game without the people section). */
    public static void install(Shell shell, PlayScreen play, List<Actor> list) {
        People people = shell.game.section(People.class);
        if (people == null) {
            return;
        }
        PeopleArt art = new PeopleArt(shell.art);
        art.season = shell.game.calendar.season();
        PeopleSystem system = new PeopleSystem(shell, play, people, art);
        list.add(system);
        list.addAll(system.actors.values());
    }

    /** The system of the current play screen, or null. */
    public static PeopleSystem of(PlayScreen play) {
        for (Actor actor : play.actors) {
            if (actor instanceof PeopleSystem system) {
                return system;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ where things are

    Spot spotFor(VillagerDef v) {
        return people.spotFor(v, shell.game);
    }

    /** An anchor's x: a valley doorway's own position when the valley has it, else the fallback. */
    float anchorX(String anchor, boolean farm) {
        if (farm) {
            return Anchors.farmX(anchor);
        }
        for (Valley.Place place : play.valley().valley.places) {
            if (place.id().equals(anchor)) {
                return place.x();
            }
        }
        int x = Anchors.valleyX(anchor);
        return x < 0 ? Anchors.valleyX("plaza") : x;
    }

    float spotX(Spot spot) {
        return anchorX(spot.anchor(), spot.farm()) + spot.dx();
    }

    /** The valley floor under x (inside the loop's block, the floor line at its foot). */
    float floor(float x) {
        Valley valley = play.valley().valley;
        int ix = Math.round(x);
        if (valley.blockAt(ix) == LOOP_BLOCK) {
            return Art.FLOOR;
        }
        int y = valley.floorBelow(ix, 0);
        return y >= Art.BLOCK ? Art.FLOOR : y;
    }

    float farmerX() {
        return play.onFarm() ? play.farm().runner.x : play.valley().runner.x;
    }

    float farmerFeet() {
        return play.onFarm() ? play.farm().feetY() : play.valley().runner.y;
    }

    int farmerView() {
        return play.onFarm() ? FARM : VALLEY;
    }

    /** The visible neighbour closest to the farmer within talking distance, or null. */
    private VillagerActor nearest() {
        VillagerActor best = null;
        float bestDistance = Float.MAX_VALUE;
        for (VillagerActor actor : actors.values()) {
            float d = Math.abs(actor.x - farmerX());
            if (actor.visible && !actor.scripted && nearFarmer(actor) && d < bestDistance) {
                best = actor;
                bestDistance = d;
            }
        }
        return best;
    }

    boolean nearFarmer(VillagerActor actor) {
        return actor.view == farmerView() && Math.abs(actor.x - farmerX()) < 40
                && Math.abs(actor.feet - farmerFeet()) < (actor.view == FARM ? 14 : 40);
    }

    // ------------------------------------------------------------------ the director

    @Override
    public int view() {
        return farmerView();
    }

    @Override
    public float x() {
        return farmerX();
    }

    @Override
    public float y() {
        return farmerFeet() + 1;     // drawn just in front of the farmer on the farm
    }

    @Override
    public float reach() {
        return -1;                   // never talked to
    }

    @Override
    public void update(Shell shell, PlayScreen play) {
        Game game = shell.game;
        art.season = game.calendar.season();
        labelled = nearest();
        if (!morningDone) {
            morningDone = true;
            notices.addAll(people.morning(game));
        }
        if (!notices.isEmpty() && shell.ticks - noticeAt > 130) {
            shell.toast(notices.poll());
            noticeAt = shell.ticks;
        }
        String next = people.nextLetter();
        // The post waits while a cutscene or festival holds the clock (the opening intro ends on the farm).
        if (post == 0 && play.onFarm() && next != null && !next.equals(heldLetter) && !shell.transitioning()
                && !play.clockStopped) {
            post = 1;
            postX = farmerX() + 230;
            postHeight = 110;
        }
        if (post == 1) {
            if (!play.onFarm() || people.nextLetter() == null || play.clockStopped) {
                post = 0;
            } else {
                float target = farmerX() + 14;
                postX -= Math.max(1.5f, (postX - target) * 0.06f);
                postHeight = Math.max(30, postHeight - 1.6f);
                if (postX <= target + 2) {
                    post = 0;
                    if (people.nextLetter() != null) {
                        shell.sfx(Sfx.GRAB);
                        shell.push(new LetterScreen(this));
                    }
                }
            }
            return;
        }
        // No heart events while a cutscene or festival holds the clock.
        if (shell.ticks % EVENT_CHECK_TICKS == 0 && !shell.transitioning() && !play.clockStopped && readyForEvent()) {
            HeartEvent event = people.dueEvent(game, play.onFarm(), farmerX(), id -> Math.round(anchorX(id, false)));
            if (event != null) {
                startEvent(event);
            }
        }
    }

    /** The farmer is standing about (not mid-jump, mid-loop or mid-dash). */
    private boolean readyForEvent() {
        if (play.onFarm()) {
            return play.farm().runner.height == 0 && Math.abs(play.farm().runner.speed) < 7;
        }
        return play.valley().runner.onGround && Math.abs(play.valley().runner.speed) < 7;
    }

    void startEvent(HeartEvent event) {
        people.begin(event, shell.game);
        if (play.onFarm()) {
            play.farm().runner.speed = 0;
            play.farm().runner.depthSpeed = 0;
        } else {
            play.valley().runner.speed = 0;
        }
        shell.push(new EventScreen(this, event));
    }

    /**
     * Plays a scene from another system (see {@link HeartEvent#scene}) over the world, then runs
     * {@code after}. Its villagers are scripted for the scene and go back to their day after.
     */
    public void playScene(HeartEvent scene, Runnable after) {
        shell.push(new EventScreen(this, scene, after));
    }

    /** Whether a villager is in the valley today (arrived, and not the one farming). */
    public boolean present(String id) {
        VillagerDef v = people.cast.get(id);
        return v != null && people.present(v, shell.game);
    }

    void talk(VillagerActor actor) {
        actor.facingLeft = farmerX() < actor.x;
        Game game = shell.game;
        String held = game.inventory.selectedId();
        Item item = held == null ? null : game.item(held);
        shell.push(new DialogueScreen(this, actor, item));
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        if (post == 1) {
            SceneSprite flicky = art.art.flicky.frame((int) (shell.ticks / 4 % 2));
            float fx = postX - cx, fy = farmerFeet() - cy - postHeight;
            canvas.draw(flicky, fx, fy, tint.withFlipX(false));
            // The letter it carries.
            canvas.fill(Math.round(fx) - 4, Math.round(fy) + 6, 9, 6, 0xFFFFFFFF);
            canvas.fill(Math.round(fx) - 4, Math.round(fy) + 6, 9, 1, 0xFFB6B6B6);
        }
        if (farmerEmote != null && shell.ticks < farmerEmoteUntil) {
            drawEmote(canvas, farmerEmote, farmerX() - cx, farmerFeet() - cy - 40);
        }
    }

    /** A speech bubble with one glyph, its tail on (x, bottom). */
    void drawEmote(SceneCanvas canvas, String token, float x, float bottom) {
        int w = 22, h = 18;
        int bx = Math.round(x) - w / 2, by = Math.round(bottom) - h - 5;
        canvas.fill(bx + 1, by, w - 2, h, 0xFF000000);
        canvas.fill(bx, by + 1, w, h - 2, 0xFF000000);
        canvas.fill(bx + 1, by + 1, w - 2, h - 2, 0xFFFFFFFF);
        canvas.fill(Math.round(x) - 2, by + h, 5, 2, 0xFF000000);
        canvas.fill(Math.round(x) - 1, by + h - 1, 3, 2, 0xFFFFFFFF);
        canvas.fill(Math.round(x) - 1, by + h + 2, 3, 1, 0xFF000000);
        int tw = art.tokenWidth(canvas, token, shell.catalog, people.cast, shell.game.farmer);
        art.drawToken(canvas, token, bx + (w - tw) / 2f, by + 1, h - 2, shell.catalog, people.cast, shell.game.farmer,
                SceneDraw.plain());
    }

    /** A stable hash for ambient choices (a pet's wandering) without touching the game's random numbers. */
    static int hash(String name, int salt) {
        return People.mix(name, salt, 11);
    }
}
