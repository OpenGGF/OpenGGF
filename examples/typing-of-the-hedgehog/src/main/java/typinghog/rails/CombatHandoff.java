package typinghog.rails;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The fight hand-off of the stock badniks on screen: which native bodies a fight takes over, and
 * their conversion into typing targets. The course and the rail builder both call it, at the same
 * rail boundary, so the native world after a fight is the world the builder validated.
 *
 * <p>A pool is the visible, eligible, layout-placed badnik bodies in native slot order. Identity
 * is native slot, class and layout index: a course checkpoint restore rebuilds the same objects
 * in the same slots, so after the victory restore the pool is reacquired by identity and
 * converted again. A body that is missing or different is a rail fault, never a guess. A body
 * without a layout index (spawned by another object) stays out of the pool.
 *
 * <p>Conversion deletes each body through the native lifecycle ({@link ObjectLifetimeOps#deleteNoRespawn}):
 * the object manager unloads it, releases its slot and retires its children on the next native
 * step, latching the placement exactly as a destroyed placement. No native destruction effect
 * runs (no explosion, animal, score or ring), so the conversion draws nothing from the native
 * RNG and allocates nothing.
 */
public final class CombatHandoff {
    /** Pixels beyond the view a body's centre may sit and still be on screen (part of it shows). */
    public static final int VIEW_MARGIN = 16;

    private CombatHandoff() { }

    /** One pooled native body: identity for reacquisition, and its pose when the pool was taken. */
    public record Body(int slot, String className, int layoutIndex, int x, int y, boolean facingRight) { }

    /** The pool no longer matches the native world: a rail fault. */
    public static final class Mismatch extends IllegalStateException {
        public Mismatch(String message) { super(message); }
    }

    /**
     * The visible, eligible native badnik bodies now, in native slot order. {@code eligible}
     * takes a class simple name ({@code Cast::convertible}).
     */
    public static List<Body> pool(Predicate<String> eligible) {
        var camera = GameServices.camera();
        int left = camera.getX() - VIEW_MARGIN, right = camera.getX() + camera.getWidth() + VIEW_MARGIN;
        int top = camera.getY() - VIEW_MARGIN, bottom = camera.getY() + camera.getHeight() + VIEW_MARGIN;
        var player = camera.getFocusedSprite();
        int playerX = player == null ? camera.getX() + camera.getWidth() / 2 : player.getCentreX();
        List<Body> out = new ArrayList<>();
        for (AbstractObjectInstance body : bodies(eligible).values()) {
            int x = body.getX(), y = body.getY();
            if (x < left || x > right || y < top || y > bottom) continue;
            // Facing towards Sonic: converted bodies turn to advance on him in the fight anyway.
            out.add(new Body(body.getSlotIndex(), body.getClass().getSimpleName(), body.getSpawn().layoutIndex(),
                    x, y, x < playerX));
        }
        out.sort(Comparator.comparingInt(Body::slot).thenComparingInt(Body::layoutIndex)
                .thenComparing(Body::className));
        return List.copyOf(out);
    }

    /**
     * Deletes every pooled body through the native lifecycle. All bodies are checked first, so a
     * mismatch converts nothing; it throws {@link Mismatch} naming the first body that differs.
     */
    public static void convert(List<Body> pool) {
        Map<Integer, AbstractObjectInstance> live = bodies(name -> true);
        List<AbstractObjectInstance> found = new ArrayList<>(pool.size());
        for (Body body : pool) {
            AbstractObjectInstance object = live.get(body.slot());
            if (object == null) throw new Mismatch("no live badnik in slot " + body.slot() + " for " + body);
            if (!object.getClass().getSimpleName().equals(body.className())
                    || object.getSpawn().layoutIndex() != body.layoutIndex()) {
                throw new Mismatch("slot " + body.slot() + " holds " + object.getClass().getSimpleName()
                        + " (layout " + object.getSpawn().layoutIndex() + "), pool has " + body);
            }
            found.add(object);
        }
        for (AbstractObjectInstance object : found) ObjectLifetimeOps.deleteNoRespawn(object);
    }

    /** Stock boss machinery that keeps an arena working (camera, bounds, capsule): never retired. */
    private static final String ARENA_MACHINERY =
            "(?i).*(capsule|scrollcontrol|boundary|extender|arenahelper|cutscene|controller|falsefloor|block).*";
    private static final String BOSS_NAME = "(?i).*(boss|eggman|robotnik).*";

    /**
     * Retires the stock boss family at a forward rail's endpoint (the boss object, its parts and
     * shots), so the typing boss does not share the arena with a frozen stock one. Nothing native
     * is validated after the endpoint; the course lets two native rows run for the unload, then
     * holds the world. Returns what it retired ({@code Class@slot}).
     */
    public static List<String> retireBoss() {
        List<String> retired = new ArrayList<>();
        for (ObjectInstance object : List.copyOf(GameServices.level().getObjectManager().getActiveObjects())) {
            if (!(object instanceof AbstractObjectInstance body) || body.isDestroyed()) continue;
            Class<?> type = object.getClass();
            String name = type.getName();
            String simple = type.getSimpleName();
            if (!name.startsWith("com.openggf.game.")) continue;
            boolean boss = name.contains(".bosses.") || simple.matches(BOSS_NAME);
            if (!boss || simple.matches(ARENA_MACHINERY)) continue;
            ObjectLifetimeOps.deleteNoRespawn(body);
            retired.add(simple + "@" + body.getSlotIndex());
        }
        return retired;
    }

    /** Live, undestroyed, layout-placed badnik bodies the predicate accepts, by native slot. */
    private static Map<Integer, AbstractObjectInstance> bodies(Predicate<String> eligible) {
        Map<Integer, AbstractObjectInstance> out = new HashMap<>();
        for (ObjectInstance object : List.copyOf(GameServices.level().getObjectManager().getActiveObjects())) {
            if (!(object instanceof AbstractObjectInstance body) || body.isDestroyed()) continue;
            String name = object.getClass().getSimpleName();
            // Badnik bodies only: the badniks packages also hold their projectiles and child parts.
            if (!name.endsWith("BadnikInstance") || !eligible.test(name)) continue;
            if (body.getSpawn() == null || body.getSpawn().layoutIndex() < 0 || body.getSlotIndex() < 0) continue;
            out.put(body.getSlotIndex(), body);
        }
        return out;
    }
}
