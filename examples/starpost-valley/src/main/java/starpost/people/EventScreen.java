package starpost.people;

import com.openggf.mods.scene.SceneCanvas;
import java.util.LinkedHashSet;
import java.util.Set;
import starpost.core.Game;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;

/**
 * A heart event playing over the world (an overlay: the clock stops and the farmer waits). It
 * steps through the event's script: villagers walk, turn, emote and speak in the side view (or
 * on the farm), items and flags are given, music changes; letterbox bars hide the HUD. When it
 * ends, everyone goes back to their schedule and the place's music returns.
 */
final class EventScreen implements Screen {
    private static final int BAR_TOP = 30;    // covers the HUD's TIME and RINGS rows
    private static final int BAR_BOTTOM = 24; // covers the hotbar
    private static final int FADE_TICKS = 24;

    private final PeopleSystem sys;
    private final HeartEvent event;
    private final Set<String> cast = new LinkedHashSet<>();
    private float anchorX;
    private float anchorDepth;
    private int index;
    private boolean started;
    private int timer;
    private Speech speech;
    private int fade;          // 0 clear ... FADE_TICKS black
    private int fadeTarget;
    private long opened;
    private boolean musicChanged;

    /** Runs when the scene ends (another system's scene), or null. */
    private final Runnable after;

    EventScreen(PeopleSystem sys, HeartEvent event) {
        this(sys, event, null);
    }

    EventScreen(PeopleSystem sys, HeartEvent event, Runnable after) {
        this.sys = sys;
        this.event = event;
        this.after = after;
    }

    @Override
    public boolean overlay() {
        return true;
    }

    @Override
    public void enter(Shell shell) {
        opened = shell.ticks;
        anchorX = sys.farmerX();
        anchorDepth = sys.play.onFarm() ? sys.play.farm().runner.depth : 0;
        for (Step step : event.steps()) {
            if (step.who() != null && sys.actors.containsKey(step.who())) {
                cast.add(step.who());
            }
        }
        cast.add(event.villager);
        for (String id : cast) {
            VillagerActor actor = sys.actors.get(id);
            actor.scripted = true;
            actor.goalX = Float.NaN;
            actor.pose = Bodies.IDLE;
        }
    }

    @Override
    public void update(Shell shell) {
        for (String id : cast) {
            sys.actors.get(id).scriptStep();
        }
        if (fade != fadeTarget) {
            fade += Integer.signum(fadeTarget - fade);
        }
        Game game = shell.game;
        while (index < event.steps().size()) {
            Step step = event.steps().get(index);
            if (step.farmer() != null && !step.farmer().equals(game.farmer)) {
                index++;
                continue;
            }
            if (!started) {
                start(shell, step);
                started = true;
            }
            if (!running(shell, step)) {
                index++;
                started = false;
                continue;
            }
            return;
        }
        finish(shell);
    }

    private VillagerActor actor(String who) {
        return who == null ? null : sys.actors.get(who);
    }

    private void start(Shell shell, Step step) {
        VillagerActor a = actor(step.who());
        Game game = shell.game;
        switch (step.op()) {
            case PLACE -> {
                if (a != null) {
                    a.view = sys.farmerView();
                    a.depth = Math.max(0, anchorDepth + step.b());
                    a.x = anchorX + step.a();
                    a.feet = a.groundAt(a.x);
                    a.visible = true;
                    a.insideAt = null;
                    a.facingLeft = a.x > sys.farmerX();
                }
            }
            case WALK, MOVE, LEAVE -> {
                if (a != null) {
                    a.goalX = anchorX + step.a();
                    a.goalDepth = a.depth;
                    // Scenes keep moving: a little brisker than the daily stroll unless the script says.
                    a.scriptSpeed = step.b() > 0 && step.op() != Step.Op.LEAVE ? step.b() / 10f
                            : Math.max(1.2f, Bodies.speed(a.def.body()) * 1.5f);
                    if (!a.visible) {
                        a.visible = true;
                        a.view = sys.farmerView();
                        a.feet = a.groundAt(a.x);
                    }
                }
            }
            case FACE -> {
                if (a != null) {
                    a.facingLeft = step.a() == 0 ? sys.farmerX() < a.x : step.a() < 0;
                } else if ("farmer".equals(step.who())) {
                    faceFarmer(step.a() < 0);
                }
            }
            case TURN_FARMER -> {
                if (a != null) {
                    faceFarmer(a.x < sys.farmerX());
                }
            }
            case EMOTE -> {
                timer = step.a();
                if (a != null) {
                    a.emote = step.text();
                    a.emoteUntil = shell.ticks + step.a();
                } else if ("farmer".equals(step.who())) {
                    sys.farmerEmote = step.text();
                    sys.farmerEmoteUntil = shell.ticks + step.a();
                }
                if (step.text().equals("!")) {
                    shell.sfx(Sfx.SWITCH);
                }
            }
            case SAY -> {
                String[] pictures = null;
                VillagerDef v = sys.people.cast.get(step.who());
                if (v != null && sys.people.speaksInPictures(v, game)) {
                    pictures = step.pics() != null ? step.pics()
                            : Pictures.fromText(step.text(), shell.catalog, sys.people.cast);
                }
                speech = new Speech(sys, step.who(), step.text(), pictures);
                if (a != null && a.pose == Bodies.IDLE) {
                    a.facingLeft = sys.farmerX() < a.x;
                }
            }
            case WAIT -> timer = step.a();
            case GIVE -> {
                if (shell.catalog.hasItem(step.text())) {
                    int left = game.inventory.add(game.item(step.text()), Math.max(1, step.a()));
                    shell.toast(left == 0 ? "GOT " + game.item(step.text()).name() + "!" : "NO ROOM IN YOUR MONITORS");
                    shell.sfx(Sfx.PERFECT);
                }
            }
            case FLAG -> {
                game.flags.add(step.text());
                if (step.text().equals(People.TRANSLATOR)) {
                    shell.toast("CHIRP TRANSLATOR: ANIMALS NOW TALK!");
                    shell.sfx(Sfx.PERFECT);
                }
            }
            case MUSIC -> {
                shell.music.want(step.text(), step.a());
                musicChanged = true;
            }
            case SFX -> shell.sfx(step.a());
            case POSE -> {
                if (a != null) {
                    a.pose = Bodies.pose(step.text());
                }
            }
            case HOP -> {
                timer = 24;
                if (a != null) {
                    a.hopAt = shell.ticks;
                    shell.sfx(Sfx.JUMP);
                }
            }
            case FADE -> fadeTarget = step.a() == 1 ? FADE_TICKS : 0;
            default -> {
            }
        }
    }

    /** Whether a blocking step is still going (and ticks it). */
    private boolean running(Shell shell, Step step) {
        VillagerActor a = actor(step.who());
        return switch (step.op()) {
            case WALK -> a != null && !Float.isNaN(a.goalX);
            case LEAVE -> {
                if (a != null && !Float.isNaN(a.goalX)) {
                    yield true;
                }
                if (a != null) {
                    a.visible = false;
                    a.insideAt = null;
                }
                yield false;
            }
            case EMOTE, WAIT, HOP -> --timer > 0;
            case SAY -> !speech.update(shell.in) || clearSpeech(shell);
            case FADE -> fade != fadeTarget;
            default -> false;
        };
    }

    private boolean clearSpeech(Shell shell) {
        speech = null;
        shell.in.consume();     // one press finishes one line
        return false;
    }

    private void faceFarmer(boolean left) {
        if (sys.play.onFarm()) {
            sys.play.farm().runner.facingLeft = left;
        } else {
            sys.play.valley().runner.facingLeft = left;
        }
    }

    private void finish(Shell shell) {
        sys.people.finish(event);
        for (String id : cast) {
            VillagerActor a = sys.actors.get(id);
            a.scripted = false;
            a.pose = Bodies.IDLE;
            a.emote = null;
            a.goalX = Float.NaN;
        }
        sys.farmerEmote = null;
        if (event.isPartner()) {
            shell.toast(sys.people.cast.get(event.villager).name + " IS NOW YOUR PARTNER!");
            shell.sfx(Sfx.PERFECT);
        }
        shell.pop();
        if (musicChanged) {
            sys.play.enter(shell);   // the place's own music again
        }
        if (after != null) {
            after.run();
        }
    }

    @Override
    public void draw(Shell shell, SceneCanvas canvas) {
        int w = canvas.width(), h = canvas.height();
        long age = shell.ticks - opened;
        int top = (int) Math.min(BAR_TOP, age * 3), bottom = (int) Math.min(BAR_BOTTOM, age * 3);
        canvas.fill(0, 0, w, top, 0xFF000000);
        canvas.fill(0, h - bottom, w, bottom, 0xFF000000);
        if (fade > 0) {
            canvas.fill(0, 0, w, h, Math.min(255, fade * 255 / FADE_TICKS) << 24);
        }
        if (speech != null) {
            speech.draw(sys, canvas, age);
        }
    }
}
