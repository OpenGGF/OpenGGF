package com.openggf.game.sonic2.competition.state;

/** Native owners participate in one synchronous swap; these ports never manufacture RAM banks. */
public final class Sonic2TeleportPorts {
    private Sonic2TeleportPorts() { }
    /** Lane A1: swap owned BG/FG fractional longs, copies, coarse X and block/scroll flags.
     * Loader-owned last-X, address/counter/block interests and Lifecycle's diffs are swapped separately.
     */
    public interface BackgroundBanks {
        void swapPlayers();
        BackgroundPosition primaryPosition();
    }
    public record BackgroundPosition(int x, int y, int x2, int y2, int x3, int y3) { }
    /** Lane A2-R: the live tile-plane scroll caches must follow the exchanged native camera banks. */
    @FunctionalInterface public interface PlaneScroll { void swapPlayers(); }
    public interface ObjectFields {
        boolean operational();
        ObjectPlayerFields fields(com.openggf.level.objects.AbstractObjectInstance object);
    }
    public static final ObjectFields UNBOUND_OBJECT_FIELDS = new ObjectFields() {
        public boolean operational() { return false; }
        public ObjectPlayerFields fields(com.openggf.level.objects.AbstractObjectInstance object) {
            throw new IllegalStateException("Corrected native object teleport fields are not installed");
        }
    };
    /** Lane C object instances: only the per-player fields named by swap_loop_objects. */
    public interface ObjectPlayerFields { void swapPlayers(); }
}
