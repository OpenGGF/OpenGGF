package starpost.core;

/**
 * One kind of thing a player can hold.
 *
 * @param id        stable save key, lower_case
 * @param name      shown name, upper case (the ROM font has no lower case)
 * @param kind      category
 * @param price     what the shipping signpost pays, in rings (0: cannot be shipped)
 * @param momentum  Momentum restored when eaten (0: not edible)
 * @param icon      the icon key {@link starpost.art.ItemIcons} draws
 * @param text      one line of description
 */
public record Item(String id, String name, Kind kind, int price, int momentum, String icon, String text) {
    public boolean edible() {
        return momentum > 0;
    }

    public boolean stackable() {
        return kind != Kind.TOOL;
    }
}
