package typinghog.play;

import com.openggf.mods.objects.AuxiliaryObjectCollection;
import com.openggf.level.objects.ObjectServices;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The course's sprite list: every badnik, explosion, animal, score sprite and boss part the
 * course shows, as plain records in world coordinates. {@link Puppet} objects draw them with
 * the zone's own ROM art; the course alone changes them.
 *
 * <p>The session-owned auxiliary collection reconciles puppets with the current visual list
 * at presentation boundaries, including after checkpoint restore and while the world is held.
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
     * {@link #sprites} is compound: its one puppet draws them back to front in list order,
     * independent of puppet registration or retirement order. Slotless objects otherwise share
     * the same ordering slot, with insertion order breaking ties.
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
    private final AuxiliaryObjectCollection<Visual> puppets = new AuxiliaryObjectCollection<>();
    private int nextId = 1;
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
        puppets.close();
        clearSectionArt();
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
        }
    }

    public void clear() {
        visuals.clear();
    }

    public Visual get(int id) { return visuals.get(id); }

    public List<Visual> all() { return List.copyOf(visuals.values()); }

    /** Reconcile presentation objects without executing native object clocks. */
    public void syncPuppets(ObjectServices services) {
        puppets.synchronize(services, visuals.values(), (injected, visual) -> Puppet.create(visual));
    }
}
