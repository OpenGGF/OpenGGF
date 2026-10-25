package typinghog.play;

import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
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
 * calls {@link #invalidatePuppets()} after every restore. {@link #syncPuppets(ObjectServices)}
 * retires stale puppets through their owning object manager at the next presentation boundary,
 * even while the native world is held, then gives every current visual a fresh puppet.
 */
public final class Stagehand implements AutoCloseable {
    /** One sprite of a compound visual, in world coordinates. */
    public static final class Sprite {
        public String artKey;
        public int frame;
        public int x;
        public int y;
        public boolean flipX;
        public boolean flipY;
        public boolean visible = true;
        /** Native owner's art-word bit 15, independent of this sprite's sheet and palette. */
        public boolean highPriority = true;
        /** Palette line override, or -1 for the sheet's own. */
        public int palette = -1;
        Sprite(String artKey) { this.artKey = artKey; }
    }

    /**
     * One drawn thing. Mutable: the course moves and animates it each row. A visual with
     * {@link #sprites} is compound: its one puppet draws them back to front in list order, which
     * separate puppets in one bucket cannot promise (slotless objects draw in no fixed order).
     */
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
        /** Native owner's art-word bit 15 for a single visual; compound parts own their own bit. */
        public boolean highPriority = true;
        /** The engine's render bucket: 0 front-most .. 7 back (ROM display lists; Sonic is 2). */
        public int bucket = 4;
        /** A compound visual's sprites, back to front; empty for a single sprite. */
        public final List<Sprite> sprites = new ArrayList<>();

        /** Adds a sprite to this compound visual, in front of those already added. */
        public Sprite sprite(String key) {
            Sprite sprite = new Sprite(key);
            sprite.highPriority = highPriority;
            sprites.add(sprite);
            return sprite;
        }
        Visual(int id, String artKey) { this.id = id; this.artKey = artKey; }
        public int id() { return id; }
    }

    private final Map<Integer, Visual> visuals = new LinkedHashMap<>();
    private final Set<Integer> puppeted = new HashSet<>();
    private record OwnedPuppet(Puppet puppet, ObjectManager manager, int id, int generation) { }
    private final List<OwnedPuppet> ownedPuppets = new ArrayList<>();
    private int nextId = 1;
    private int generation = 1;
    private SectionArt sectionArt;

    /** Installs an entry's prepared ROM art and retires the preceding entry's private atlas bank. */
    public void sectionArt(SectionArt art) {
        if (sectionArt == art) return;
        clearSectionArt();
        sectionArt = art;
    }

    public SectionArt sectionArt() { return sectionArt; }

    /** Load/leave/session boundary only; ordinary {@link #clear()} retains prepared art. */
    public void clearSectionArt() {
        if (sectionArt != null) sectionArt.close();
        sectionArt = null;
    }

    @Override public void close() {
        clear();
        retireStalePuppets(null);
        clearSectionArt();
        invalidatePuppets();
    }

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

    /**
     * Presentation/frame-boundary ownership reconciliation. Never executes native object clocks.
     * Track only puppets created here; removal uses the manager that registered each puppet, so
     * unload callbacks, ownership and render caches follow the normal retirement path.
     */
    public void syncPuppets(ObjectServices services) {
        ObjectManager manager = services.objectManager();
        retireStalePuppets(manager);
        for (Visual visual : takeUnpuppeted()) {
            Puppet puppet = Puppet.spawn(services, visual, generation);
            ownedPuppets.add(new OwnedPuppet(puppet, manager, visual.id(), generation));
        }
    }

    private void retireStalePuppets(ObjectManager currentManager) {
        var iterator = ownedPuppets.iterator();
        while (iterator.hasNext()) {
            OwnedPuppet owned = iterator.next();
            if (owned.manager() == currentManager && !owned.puppet().isDestroyed()
                    && current(owned.id(), owned.generation())) continue;
            owned.manager().removeDynamicObject(owned.puppet());
            iterator.remove();
            if (current(owned.id(), owned.generation())) puppeted.remove(owned.id());
        }
    }

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
