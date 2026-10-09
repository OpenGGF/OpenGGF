package starpost.people;

/**
 * Where a villager stands: a named anchor in the side-view valley (a doorway id from
 * {@code Valley.places} or one of {@link Anchors}' spots) or on the belt-view farm, offset by
 * {@code dx} pixels; or inside a building, where they are not drawn.
 *
 * @param anchor the anchor's name
 * @param dx     pixels east of the anchor
 * @param depth  on the farm, how far into the field (0 at the back wall)
 * @param inside indoors at the anchor's doorway: not drawn and not talkable
 * @param farm   on the farm (belt view) rather than in the valley
 */
public record Spot(String anchor, int dx, int depth, boolean inside, boolean farm) {
    public static Spot valley(String anchor, int dx) {
        return new Spot(anchor, dx, 0, false, false);
    }

    public static Spot indoors(String place) {
        return new Spot(place, 0, 0, true, false);
    }

    public static Spot onFarm(String anchor, int dx, int depth) {
        return new Spot(anchor, dx, depth, false, true);
    }

    /** The same spot for comparison when only the place matters (walking targets). */
    public boolean samePlace(Spot other) {
        return other != null && anchor.equals(other.anchor) && dx == other.dx && depth == other.depth
                && inside == other.inside && farm == other.farm;
    }
}
