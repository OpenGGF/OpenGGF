package starpost.core;

/**
 * A crop: what it grows from, when, how fast, and what it yields.
 *
 * @param id         crop key (also the art key)
 * @param seed       the seed item that plants it
 * @param produce    the item harvested
 * @param seasons    bit per season it grows in (1 spring, 2 summer, 4 fall, 8 winter)
 * @param days       days from planting to ripe
 * @param regrow     days to ripen again after a harvest, or 0 when the plant is used up
 * @param yield      items per harvest
 * @param giant      whether a full 3x3 patch can merge into a giant crop
 * @param trellis    whether it blocks walking (beans, grapes, hops)
 */
public record CropDef(String id, String seed, String produce, int seasons, int days, int regrow, int yield,
        boolean giant, boolean trellis) {
    public boolean grows(int season) {
        return (seasons & (1 << season)) != 0;
    }

    /**
     * The growth picture for a plant {@code age} days old: 0 seed, 1 sprout, 2 young, 3 mature,
     * 4 ripe. The stages divide the growing time roughly evenly, ripe only on the last day.
     */
    public int stage(int age) {
        if (age >= days) {
            return 4;
        }
        if (age <= 0) {
            return 0;
        }
        return 1 + Math.min(2, age * 3 / days);
    }
}
