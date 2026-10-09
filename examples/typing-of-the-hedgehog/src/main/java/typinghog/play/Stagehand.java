package typinghog.play;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The course's sprite list: every badnik, explosion, animal, score sprite and boss part the
 * course shows, as plain records in world coordinates. {@link Puppet} objects draw them with
 * the zone's own ROM art; the course alone changes them.
 *
 * <p>A course checkpoint restore rolls auxiliary objects back but not this list, so the course
 * calls {@link #invalidatePuppets()} after every restore: puppets of an older generation delete
 * themselves, and every visual gets a fresh puppet. A visual removed here makes its puppet
 * delete itself on its next update.
 */
public final class Stagehand {
    /** One drawn thing. Mutable: the course moves and animates it each row. */
    public static final class Visual {
        final int id;
        public String artKey;
        public int frame;
        public int x;
        public int y;
        public boolean flipX;
        public boolean flipY;
        /** Rows left to blink (hit flash or a dying fade); odd rows are hidden. */
        public int blink;
        public boolean visible = true;
        /** 0 back .. 7 front, the engine's render buckets. */
        public int bucket = 4;
        Visual(int id, String artKey) { this.id = id; this.artKey = artKey; }
        public int id() { return id; }
    }

    private final Map<Integer, Visual> visuals = new LinkedHashMap<>();
    private final Set<Integer> puppeted = new HashSet<>();
    private int nextId = 1;
    private int generation = 1;

    public Visual add(String artKey, int x, int y) {
        Visual visual = new Visual(nextId++, artKey);
        visual.x = x;
        visual.y = y;
        visuals.put(visual.id, visual);
        return visual;
    }

    public void remove(Visual visual) {
        if (visual != null) {
            visuals.remove(visual.id);
            puppeted.remove(visual.id);
        }
    }

    public void clear() {
        visuals.clear();
        puppeted.clear();
    }

    public Visual get(int id) { return visuals.get(id); }

    public List<Visual> all() { return List.copyOf(visuals.values()); }

    public int generation() { return generation; }

    /** True while a puppet for {@code id} of {@code puppetGeneration} should keep drawing. */
    public boolean current(int id, int puppetGeneration) {
        return puppetGeneration == generation && visuals.containsKey(id);
    }

    /** Called after a checkpoint restore: every existing puppet is stale. */
    public void invalidatePuppets() {
        generation++;
        puppeted.clear();
    }

    /** Visuals that still need a puppet in this generation; marks them as served. */
    public List<Visual> takeUnpuppeted() {
        List<Visual> out = new ArrayList<>();
        for (Visual visual : visuals.values()) if (puppeted.add(visual.id)) out.add(visual);
        return out;
    }
}
