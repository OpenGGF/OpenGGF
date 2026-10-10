package starpost.people;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import starpost.core.Game;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Shell;
import starpost.ui.Text;

/**
 * A villager in the world. Each tick it walks toward where its schedule says it should be:
 * along the valley floor, through the farm gate to visit the farm, or to a doorway where it goes
 * inside (and is not drawn). The action button nearby starts a conversation. During a heart
 * event the event's script moves it instead.
 */
final class VillagerActor implements Actor {
    final VillagerDef def;
    private final PeopleSystem sys;
    int view = VALLEY;
    float x;
    float feet;
    float depth;
    boolean visible;
    /** The doorway they are behind while not visible, or null when away. */
    String insideAt;
    boolean facingLeft;
    boolean walking;
    /** Animation clock: runs only while the world runs, so walkers freeze under menus. */
    long anim;
    // Scripted by a heart event.
    boolean scripted;
    float goalX = Float.NaN;
    float goalDepth = Float.NaN;
    float scriptSpeed;
    int pose = Bodies.IDLE;
    String emote;
    long emoteUntil;
    long hopAt = -1000;
    long heartAt = -1000;
    boolean heartUp;
    // A pet's wandering.
    private long wanderAt;
    private float wanderDx;
    private float wanderDepth;

    VillagerActor(VillagerDef def, PeopleSystem sys) {
        this.def = def;
        this.sys = sys;
    }

    @Override
    public int view() {
        return view;
    }

    @Override
    public float x() {
        return x;
    }

    @Override
    public float y() {
        return feet;
    }

    @Override
    public float reach() {
        return visible && !scripted && !sys.offstage ? 22 : -1;
    }

    // ------------------------------------------------------------------ the day

    /** Puts the villager where the schedule says, without walking (the morning, a load). */
    void snap() {
        Game game = sys.shell.game;
        Spot spot = sys.people.present(def, game) ? sys.spotFor(def) : null;
        if (spot == null) {
            visible = false;
            insideAt = null;
            return;
        }
        place(spot);
    }

    private void place(Spot spot) {
        view = spot.farm() ? FARM : VALLEY;
        x = sys.spotX(spot);
        depth = spot.farm() ? spot.depth() : 0;
        feet = groundAt(x);
        visible = !spot.inside();
        insideAt = spot.inside() ? spot.anchor() : null;
        walking = false;
    }

    @Override
    public void update(Shell shell, PlayScreen play) {
        anim++;
        if (scripted) {
            return;
        }
        walking = false;
        pose = Bodies.IDLE;
        Game game = shell.game;
        if (!sys.people.present(def, game)) {
            visible = false;
            insideAt = null;
            return;
        }
        Spot spot = sys.spotFor(def);
        if (spot == null) {
            return;
        }
        if (def.isPet() && spot.farm()) {
            wander(spot, shell.ticks);
            return;
        }
        travel(spot);
    }

    /** One step of the walk to a spot, crossing the farm gate or going indoors on the way. */
    private void travel(Spot spot) {
        if (!visible) {
            if (spot.inside() && spot.anchor().equals(insideAt)) {
                return;
            }
            if (insideAt == null) {
                place(spot);            // back from wherever they were (an event, arriving in the valley)
                return;
            }
            view = VALLEY;              // out of the door
            x = sys.anchorX(insideAt, false);
            depth = 0;
            feet = groundAt(x);
            visible = true;
            insideAt = null;
        }
        int wantView = spot.farm() ? FARM : VALLEY;
        float speed = Bodies.speed(def.body());
        if (view != wantView) {
            boolean there = view == VALLEY ? walkTo(Anchors.VALLEY_GATE, 0, speed)
                    : walkTo(Anchors.FARM_GATE, 40, speed);
            if (there) {
                if (view == VALLEY) {
                    view = FARM;
                    x = Anchors.FARM_GATE - 8;
                    depth = 40;
                } else {
                    view = VALLEY;
                    x = Anchors.VALLEY_GATE + 8;
                    depth = 0;
                }
                feet = groundAt(x);
            }
            return;
        }
        if (walkTo(sys.spotX(spot), spot.farm() ? spot.depth() : 0, speed) && spot.inside()) {
            visible = false;
            insideAt = spot.anchor();
        }
    }

    /** The farm pet potters about its spot, stopping now and then. */
    private void wander(Spot spot, long ticks) {
        if (!visible || view != FARM) {
            place(spot);
            wanderAt = ticks;
        }
        if (ticks >= wanderAt) {
            int roll = PeopleSystem.hash(def.id, (int) (ticks / 60));
            wanderDx = roll % 160 - 80;
            wanderDepth = Math.max(4, Math.min(60, spot.depth() + roll / 160 % 40 - 20));
            wanderAt = ticks + 150 + roll % 200;
        }
        walkTo(sys.spotX(spot) + wanderDx, wanderDepth, 0.6f);
    }

    /** Moves toward a point; true when there. Sets the walking pose and facing. */
    boolean walkTo(float tx, float tDepth, float speed) {
        float dx = tx - x;
        float dd = view == FARM ? tDepth - depth : 0;
        if (Math.abs(dx) < 0.5f && Math.abs(dd) < 0.5f) {
            x = tx;
            depth = view == FARM ? tDepth : depth;
            feet = groundAt(x);
            return true;
        }
        if (Math.abs(dx) >= 0.5f) {
            x += Math.signum(dx) * Math.min(Math.abs(dx), speed);
            facingLeft = dx < 0;
        }
        if (view == FARM && Math.abs(dd) >= 0.5f) {
            depth += Math.signum(dd) * Math.min(Math.abs(dd), speed * 0.5f);
        }
        feet = groundAt(x);
        walking = true;
        return false;
    }

    /** One tick of a scripted walk; true when there (or not walking). */
    boolean scriptStep() {
        anim++;
        walking = false;
        if (Float.isNaN(goalX)) {
            return true;
        }
        boolean there = walkTo(goalX, Float.isNaN(goalDepth) ? depth : goalDepth, scriptSpeed);
        if (there) {
            goalX = Float.NaN;
            goalDepth = Float.NaN;
            walking = false;
        }
        return there;
    }

    float groundAt(float atX) {
        return view == FARM ? PeopleSystem.FARM_FEET + depth : sys.floor(atX);
    }

    @Override
    public boolean interact(Shell shell, PlayScreen play) {
        if (!visible || scripted) {
            return false;
        }
        sys.talk(this);
        return true;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        if (!visible || sys.offstage) {
            return;
        }
        float sx = x - cx, sy = feet - cy;
        if (sx < -60 || sx > canvas.width() + 60) {
            return;
        }
        String body = def.body();
        if (view == FARM) {
            Bodies.shadow(canvas, sx, sy, body.startsWith("animal:") ? 10 : 18);
        }
        int drawPose = walking ? Bodies.WALK : pose;
        boolean hopper = body.startsWith("animal:") && !body.equals("animal:flicky");
        float hop = walking && hopper ? Math.abs((float) Math.sin(anim * 0.18)) * 5 : 0;
        long since = shell.ticks - hopAt;
        if (since >= 0 && since < 24) {
            hop += (float) Math.sin(since / 24.0 * Math.PI) * 14;
        }
        Bodies.draw(sys.art, canvas, body, drawPose, drawPose == Bodies.WALK ? anim : shell.ticks, sx, sy, facingLeft,
                hop, tint);
    }

    @Override
    public void drawOver(Shell shell, SceneCanvas canvas, int cx, int cy) {
        if (!visible || sys.offstage) {
            return;
        }
        float sx = x - cx, sy = feet - cy;
        if (sx < -60 || sx > canvas.width() + 60) {
            return;
        }
        String body = def.body();
        boolean hopper = body.startsWith("animal:") && !body.equals("animal:flicky");
        float hop = walking && hopper ? Math.abs((float) Math.sin(anim * 0.18)) * 5 : 0;
        long since = shell.ticks - hopAt;
        if (since >= 0 && since < 24) {
            hop += (float) Math.sin(since / 24.0 * Math.PI) * 14;
        }
        float top = sy - height() - Bodies.lift(body, shell.ticks) - hop;
        if (emote != null && shell.ticks < emoteUntil) {
            sys.drawEmote(canvas, emote, sx, top);
        } else if (shell.ticks >= heartAt && shell.ticks - heartAt < 48) {
            float rise = (shell.ticks - heartAt) * 0.4f;
            sys.drawEmote(canvas, heartUp ? "heart" : "sad", sx, top - rise);
        }
        if (!scripted && sys.labelled == this) {
            // The name sits on the ground under their feet, clear of the doorway labels above.
            String name = def.name;
            Text.shadow(canvas, name, Math.round(sx) - canvas.textWidth(name) / 2, Math.round(sy) + 3, Text.YELLOW);
        }
    }

    /** The body's height in its idle pose (for bubbles and labels). */
    float height() {
        SceneSprite idle = sys.art.pose(def.body(), Bodies.IDLE, 0);
        if (idle == null) {
            return 24;
        }
        int extra = def.body().equals("eggrobo") ? PeopleArt.EGGROBO_JET_Y + 12 - idle.originY() : 0;
        return idle.height() + Math.max(0, extra);
    }
}
