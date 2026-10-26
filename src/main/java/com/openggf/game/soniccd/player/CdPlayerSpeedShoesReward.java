package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdReleaseVariant;

import java.util.List;
import java.util.Objects;

/**
 * Shared pure native .GiveSpeedShoes primitive, Monitor and Time Post/Main.asm:570-589,
 * source pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Exactly five native stores and
 * ordered SubCPUCmd values; no object, world, powerup, actor motion or playback owner.
 * Region selects a source rule only, never media identity or retail/gameplay admission.
 */
public final class CdPlayerSpeedShoesReward {
    private CdPlayerSpeedShoesReward() { }

    /** Actual speedShoes byte, oPlayerShoes word and shared signed physics words. */
    public record State(int speedShoes, int speedShoesTimer, CdPlayerMovement.Physics physics) {
        public State {
            if ((speedShoes & ~0xFF) != 0 || (speedShoesTimer & ~0xFFFF) != 0) {
                throw new IllegalArgumentException("Speed shoes require an unsigned native byte and timer word");
            }
            Objects.requireNonNull(physics, "physics");
        }
    }

    /** System.inc:144 rsset 1, including the unlabelled byte at :183; commands at :252/:274. */
    public enum SubCpuCommand {
        SCMD_FADEPCM(0x82), SCMD_SHOESMUS(0x6C);
        private final int nativeId;
        SubCpuCommand(int nativeId) { this.nativeId = nativeId; }
        public int nativeId() { return nativeId; }
    }

    /** Plain final values, not an identity, phase, execution or consumption certificate. */
    public record Result(State state, List<SubCpuCommand> commands) {
        public Result {
            Objects.requireNonNull(state, "state");
            commands = List.copyOf(commands);
        }
    }

    /**
     * Native MOVE replaces existing flags/timers/physics, including same-value stores.
     * TST.B uses the whole raw timeZone byte: $80 is nonzero, just like $01/$81.
     * before is checked borrowed input, never retained or used to gate these unconditional stores.
     */
    public static Result give(State before, CdReleaseVariant variant, int rawTimeZone) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(variant, "variant");
        if ((rawTimeZone & ~0xFF) != 0) {
            throw new IllegalArgumentException("Time zone requires an unsigned native byte");
        }
        var state = new State(1, variant == CdReleaseVariant.USA ? 1320 : 1200,
                new CdPlayerMovement.Physics(0xC00, 0x18, 0x80));
        var commands = rawTimeZone == 0
                ? List.of(SubCpuCommand.SCMD_FADEPCM, SubCpuCommand.SCMD_SHOESMUS)
                : List.of(SubCpuCommand.SCMD_SHOESMUS);
        return new Result(state, commands);
    }
}
