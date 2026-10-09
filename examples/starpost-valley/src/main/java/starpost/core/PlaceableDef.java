package starpost.core;

/**
 * Something that stands on a farm plot once placed: what it does and how far it reaches.
 *
 * @param id      the item id it is placed from (and returns to when knocked loose)
 * @param role    what it does
 * @param reach   rows/columns of effect (sprinklers, scarecrows, roosts) or capacity (chests, in slots)
 */
public record PlaceableDef(String id, Role role, int reach) {
    /** A roost's basket, in slots. */
    public static final int ROOST_BASKET = 12;

    /** Storage slots the object holds: a chest's {@code reach}, a roost's basket, otherwise none. */
    public int slots() {
        return role == Role.CHEST ? reach : role == Role.ROOST ? ROOST_BASKET : 0;
    }

    /** The roles a placed object can play. Constrained: no fields, so the validator accepts it. */
    public enum Role {
        /** Waters every plot within {@code reach} rows and columns each morning. */
        SPRINKLER,
        /** Waters its whole row, out to {@code reach} columns each side (the Caterkiller Crawler). */
        ROW_SPRINKLER,
        /** Keeps pests off crops within {@code reach}. */
        SCARECROW,
        /** Stores {@code reach} slots of items. */
        CHEST,
        /** Sets where the farmer wakes after fainting in the valley. */
        CHECKPOINT,
        /** Processes one loaded item overnight (artisan machines); its work is a {@link Machine}. */
        MACHINE,
        /** Harvests ripe crops within {@code reach} columns of its row into its basket each morning. */
        ROOST,
        /** Anything decorative. */
        DECOR
    }
}
