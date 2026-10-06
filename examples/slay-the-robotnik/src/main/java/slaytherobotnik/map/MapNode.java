package slaytherobotnik.map;

import java.util.ArrayList;
import java.util.List;

/**
 * One room on an act map. {@code x} is the column (0-6) and {@code y} the floor within the
 * act (0-14). Edges point upwards to nodes on the next floor.
 */
public final class MapNode {
    private final int x;
    private final int y;
    private String room;
    private final List<MapNode> children = new ArrayList<>();
    private final List<MapNode> parents = new ArrayList<>();
    private boolean connectsToBoss;
    /** Small random offset so the map doesn't look like a grid (in map pixels). */
    private int offsetX;
    private int offsetY;

    MapNode(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() { return x; }
    public int y() { return y; }
    /** A {@link slaytherobotnik.core.RoomType} constant, or null for unused grid cells. */
    public String room() { return room; }
    public List<MapNode> children() { return List.copyOf(children); }
    public List<MapNode> parents() { return List.copyOf(parents); }
    public boolean connectsToBoss() { return connectsToBoss; }
    public int offsetX() { return offsetX; }
    public int offsetY() { return offsetY; }

    /** Part of at least one path. */
    public boolean used() {
        return !children.isEmpty() || connectsToBoss;
    }

    void setRoom(String room) { this.room = room; }
    void setConnectsToBoss(boolean value) { connectsToBoss = value; }
    void setOffset(int dx, int dy) { offsetX = dx; offsetY = dy; }

    void addChild(MapNode child) {
        if (!children.contains(child)) {
            children.add(child);
            children.sort((a, b) -> Integer.compare(a.x, b.x));
        }
        if (!child.parents.contains(this)) {
            child.parents.add(this);
        }
    }

    void removeChild(MapNode child) {
        children.remove(child);
        child.parents.remove(this);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + (room == null ? "" : " " + room) + ")";
    }
}
