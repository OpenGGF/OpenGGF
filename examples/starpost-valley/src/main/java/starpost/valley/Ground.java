package starpost.valley;

/** Read-only geometry queries for placement and generation; native player sensors own gameplay. */
public interface Ground {
        boolean solid(int x, int y);

        /** The first solid row at or below {@code fromY}, or a large value when there is none. */
        int floorBelow(int x, int fromY);

        int left();

        int right();

        /** World-space origin of the authored block geometry (the act may add sky rows). */
        default int originY() { return 0; }

        /** Whether a ceiling at this pixel stops a head moving up into it (the valley has none). */
        default boolean ceiling(int x, int y) {
            return false;
        }
    }
