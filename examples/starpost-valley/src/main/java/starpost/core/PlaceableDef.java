package starpost.core;

/**
 * Something that stands on a farm plot once placed: what it does and how far it reaches.
 *
 * @param id      the item id it is placed from (and returns to when knocked loose)
 * @param role    what it does
 * @param reach   rows/columns of effect (sprinklers, scarecrows) or capacity (chests, in slots)
 */
public record PlaceableDef(String id, Role role, int reach) {
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
        /** Anything decorative. */
        DECOR
    }
}
