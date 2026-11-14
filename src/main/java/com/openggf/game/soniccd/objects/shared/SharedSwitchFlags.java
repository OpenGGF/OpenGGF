package com.openggf.game.soniccd.objects.shared;

/**
 * {@code switch_flags}: one byte per switch number, written by the switches and read by doors
 * (objects/r6/switch.asm:14-18, 24-46; objects/r6/door.asm:53-58). Bit 7: held this frame; bit 6:
 * pressed since the level began; bit 5: toggled on each press. Level RAM, cleared with the level.
 *
 * <p>The game module provides one as a game service ({@code services().gameService(SharedSwitchFlags.class)}),
 * clears it at each level start ({@code LevelStart}'s camera-RAM clear covers {@code switchFlags},
 * Level/Main.asm:210-216; Level/_Definitions.inc:237, 336) and registers it for rewind.
 */
public final class SharedSwitchFlags implements com.openggf.game.rewind.RewindSnapshottable<SharedSwitchFlags.Snapshot> {
    /** Rewind capture: a copy of the flag bytes. */
    public record Snapshot(byte[] flags) {
        public Snapshot {
            flags = flags.clone();
        }
    }

    private final byte[] flags = new byte[0x100];

    public int get(int index) { return flags[index & 0xFF] & 0xFF; }

    public void set(int index, int value) { flags[index & 0xFF] = (byte) value; }

    public void clear() { java.util.Arrays.fill(flags, (byte) 0); }

    @Override public String key() { return "soniccd.switchflags"; }

    @Override public Snapshot capture() { return new Snapshot(flags); }

    @Override public void restore(Snapshot snapshot) { System.arraycopy(snapshot.flags(), 0, flags, 0, flags.length); }

    /** A snapshot taken before the flags were registered: the level-start state. */
    @Override public void resetForMissingSnapshot() { clear(); }
}
