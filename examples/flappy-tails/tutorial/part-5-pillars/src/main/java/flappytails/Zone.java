package flappytails;

import java.util.List;

/**
 * One stop on the tour: a Sonic 3 &amp; Knuckles act whose pictures the engine can draw
 * ({@code SceneRomArt.hasZonePictures}), its music, and how hard its stretch of the course is.
 *
 * @param zone        the game's zone id (0 Angel Island, 1 Hydrocity, 6 Launch Base, 10 Sky Sanctuary)
 * @param act         0 for act 1, 1 for act 2
 * @param name        the zone's name, for menus and records
 * @param music       the sound driver's music id ({@code mus_AIZ1} and so on in the disassembly)
 * @param backdropTop the backdrop row shown at the top of the screen: where its sky meets its scenery
 * @param speed       how far the course scrolls each frame, in 1/256 pixel
 * @param gap         the opening between each pair of pillars, in pixels
 * @param badniks     whether this stretch sends badniks through the gaps
 * @param ground      which of the act's floor stages to cut the ground strip from, and which
 *                    columns of it: chosen by eye to leave out waterfalls and machinery
 */
record Zone(int zone, int act, String name, int music, int backdropTop, int speed, int gap, boolean badniks,
            Ground ground) {
    /**
     * A ground strip's source: stage {@code stage} of {@code SceneRomArt.levelStages(zone, act,
     * 400, 96, 8)}, columns {@code from} to {@code to} of it.
     */
    record Ground(int stage, int from, int to) { }

    /** Gates per zone: passing ten pillars takes you to the next zone. */
    static final int GATES = 10;
    /** Zones in one lap of the tour. */
    static final int TOUR_LENGTH = 5;

    /**
     * The five acts the engine can picture, in tour order. A new list on every call: mods may not
     * keep static collections.
     */
    static List<Zone> tour() {
        return List.of(
                new Zone(0, 0, "ANGEL ISLAND", 0x01, 220, 0x200, 100, false, new Ground(0, 150, 400)),
                new Zone(0, 1, "ANGEL ISLAND", 0x02, 320, 0x220, 96, false, new Ground(1, 0, 400)),
                new Zone(1, 0, "HYDROCITY", 0x03, 64, 0x240, 92, true, new Ground(5, 0, 360)),
                new Zone(6, 0, "LAUNCH BASE", 0x0D, 8, 0x260, 88, true, new Ground(1, 0, 400)),
                new Zone(10, 0, "SKY SANCTUARY", 0x15, 520, 0x280, 84, true, new Ground(4, 0, 380)));
    }

    /** The zone for a gate count, looping the tour; later laps keep the last zone's pace and tighten. */
    static Zone at(int gates) {
        return tour().get(tourIndex(gates));
    }

    /** Which tour stop gate {@code gates} belongs to. */
    static int tourIndex(int gates) {
        return Math.floorMod(gates / GATES, TOUR_LENGTH);
    }

    /** Laps completed before {@code gates}: each lap is the whole tour. */
    static int lap(int gates) {
        return gates / (GATES * TOUR_LENGTH);
    }

    /** This zone made harder for a later lap: faster and narrower, within what flight can still clear. */
    Zone forLap(int lap) {
        if (lap <= 0) return this;
        return new Zone(zone, act, name, music, backdropTop, speed + 0x20 * Math.min(lap, 4),
                Math.max(72, gap - 4 * lap), badniks, ground);
    }

    String act1Or2() { return act == 0 ? "1" : "2"; }
}
