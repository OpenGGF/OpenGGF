package sonicbros.content;

import java.util.ArrayList;
import java.util.List;

/**
 * The ten stages, each a floating island cut from a real Sonic 3 &amp; Knuckles act. The cut
 * positions were chosen with the island probe (a flat floor with clear sky above, solid earth
 * below) and the layouts give every stage its own shape: Battlefield-style platforms, twin
 * ledges, a low triple, moving lifts or none at all.
 */
public final class Stages {
    public enum Layout { BATTLEFIELD, TWIN, TRIPLE, LIFTS, SHUTTLE, NONE }

    /**
     * One stage.
     *
     * @param zone      S3K zone id (0 Angel Island ... 11 Death Egg)
     * @param act       zero-based act
     * @param x         level x of the island's left edge
     * @param floorY    level row near the island's floor
     * @param width     island width in pixels
     * @param music     S3K music id
     * @param bgTop     the backdrop row shown at the top of the screen when the camera is level
     * @param tint      a colour washed over the backdrop for depth (0 for none)
     * @param bgCrop    null to use the zone backdrop as it is, or {@code "x,width;top-bottom,top-bottom"}: the
     *                  columns and row ranges of the backdrop to keep (some zone backgrounds hold unrelated
     *                  scenery or unused tiles beside the part the act shows)
     */
    public record Stage(String name, String subtitle, int zone, int act, int x, int floorY, int width, Layout layout,
            int music, int bgTop, int tint, String bgCrop) {
        public Stage(String name, String subtitle, int zone, int act, int x, int floorY, int width, Layout layout,
                int music, int bgTop, int tint) {
            this(name, subtitle, zone, act, x, floorY, width, layout, music, bgTop, tint, null);
        }
    }

    private final List<Stage> stages = new ArrayList<>();

    public Stages() {
        stages.add(new Stage("ANGEL ISLAND", "Jungle Ledge", 0, 0, 8968, 904, 288, Layout.BATTLEFIELD, 0x01, 210, 0));
        stages.add(new Stage("HYDROCITY", "Ruin Terrace", 1, 0, 6656, 888, 288, Layout.TWIN, 0x03, 40, 0x50000020));
        stages.add(new Stage("MARBLE GARDEN", "Hilltop Shrine", 2, 0, 4032, 560, 304, Layout.TRIPLE, 0x05, 20, 0));
        stages.add(new Stage("CARNIVAL NIGHT", "Bumper Deck", 3, 0, 12800, 760, 288, Layout.LIFTS, 0x07, 220, 0, "0,512;0-296,340-512"));
        stages.add(new Stage("ICECAP", "Crystal Shelf", 5, 0, 20464, 120, 288, Layout.TWIN, 0x0B, 200, 0x70081030));
        stages.add(new Stage("LAUNCH BASE", "Gantry Walk", 6, 0, 5280, 248, 288, Layout.SHUTTLE, 0x0D, 40, 0));
        stages.add(new Stage("MUSHROOM HILL", "Toadstool Glade", 7, 0, 6080, 1600, 288, Layout.BATTLEFIELD, 0x0F, 250, 0));
        stages.add(new Stage("SANDOPOLIS", "Pyramid Steps", 8, 0, 12928, 952, 304, Layout.TRIPLE, 0x11, 60, 0, "0,1024;0-226,262-512"));
        stages.add(new Stage("SKY SANCTUARY", "Temple in the Clouds", 10, 0, 336, 2168, 288, Layout.TWIN, 0x15, 640, 0));
        stages.add(new Stage("DEATH EGG", "Final Platform", 11, 0, 11328, 1080, 320, Layout.NONE, 0x16, 16, 0x30000000, "0,256;0-256"));
    }

    public List<Stage> all() { return stages; }

    public Stage get(int index) { return stages.get(Math.floorMod(index, stages.size())); }

    public int size() { return stages.size(); }
}
