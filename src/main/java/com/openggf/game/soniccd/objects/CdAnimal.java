package com.openggf.game.soniccd.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ObjAnimal ($24): Level/Palmtree Panic/Objects/Animal/Main.asm:7-19 entering CheckAnimalPrescence
 * (Level/Object Time Check.asm:55-84) at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, executed over
 * the live dynamic pool and saved bank of one CdObjectPlacementRuntime. One {@link #run} is one RunObjects
 * dispatch of the slot.
 *
 * <p>Admitted source paths read no asset bytes. With oSubtype bit 7 clear, a signed timeZone byte below 2 and
 * projDestroyed zero, CheckAnimalPrescence discards the body's return address (ADDQ.L #4,SP) and jumps to
 * CheckObjDespawn: Init never runs, nothing is drawn, no map/tile/flag/routine byte is written, and the slot
 * lives until its current-X chunk leaves the $280 window. With a signed timeZone of 2 or more and goodFuture
 * zero it jumps to DeleteObject. Every path that would run an Animal body (hologram subtype, destroyed
 * projector, good future) stops at its predicate before any write.</p>
 *
 * <p>No private queue, renderer, audio owner, pool snapshot or player mutation exists here, and nothing is keyed
 * on a fixture, frame or route coordinate.</p>
 */
public final class CdAnimal {
    public static final int OBJECT_ID = 0x24;

    public enum NativeCall { CHECK_ANIMAL_PRESENCE, CHECK_OBJ_DESPAWN, DELETE_OBJECT }
    /** Which source branch entered DespawnObject (Object Spawner.asm:385-446). */
    public enum DespawnEntry { CURRENT_X_OFFSCREEN, TIME_WARP }

    /** Ordered return metadata. Pool/bank effects describe completed writes and must not be replayed. */
    public sealed interface Effect permits CallReturned, Despawn, Deleted { }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /**
     * DespawnObject executed: for a nonzero saved ID the saved bank's loaded bit 7 was cleared at the selected
     * native period (-1 means no bank write), then the slot was fully deleted.
     */
    public record Despawn(DespawnEntry entry, int savedId, int period) implements Effect {
        public Despawn {
            Objects.requireNonNull(entry, "entry");
            byteValue(savedId, "saved ID");
            if (period < -1 || period > 2 || (savedId == 0) != (period == -1)) {
                throw new IllegalArgumentException("Despawn period must be -1 exactly when the saved ID is 0");
            }
        }
    }
    /** .ChkGoodFuture with goodFuture zero: JMP DeleteObject, the 64-byte delete without any saved-bank read or write. */
    public record Deleted() implements Effect { }

    /**
     * Explicit immutable globals at the call cut: timeZone (bit 7 is the warp flag), goodFuture, projDestroyed
     * (_Include/Main CPU Variables.inc:122) and timeWarpDir bytes, plus the pre-scroll cameraX word.
     * The time zone's low seven bits must name a native period.
     */
    public record Globals(int timeZone, int goodFuture, int projDestroyed, int timeWarpDir, int cameraXWord) {
        public Globals {
            byteValue(timeZone, "timeZone");
            byteValue(goodFuture, "goodFuture");
            byteValue(projDestroyed, "projDestroyed");
            byteValue(timeWarpDir, "timeWarpDir");
            wordValue(cameraXWord, "cameraX");
            if ((timeZone & 0x7F) > 2) {
                throw new IllegalArgumentException("Native CD period must be 0, 1 or 2");
            }
        }
    }

    /** Final live image of the slot after the whole dispatch (fully zero after a despawn or delete). */
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(e -> e instanceof Despawn || e instanceof Deleted); }
    }

    public enum UnsupportedBoundary {
        /** TST.B oSubtype / BMI .End (Object Time Check.asm:56-57): the projector's hologram body (Animal/Main.asm:52-56,86-105,151-153,182-191). */
        HOLOGRAM_BODY,
        /** TST.B projDestroyed / BNE .End (:60-61): the freed Present/Past roaming body. */
        PROJECTOR_DESTROYED_BODY,
        /** .ChkGoodFuture TST.B goodFuture / BNE .End (:75-76): the good-future roaming body. */
        GOOD_FUTURE_BODY
    }

    /** Thrown before the boundary's writes; the pool and bank hold the last committed source state. */
    public static final class UnsupportedAnimal extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;

        private UnsupportedAnimal(UnsupportedBoundary boundary, int wholeIndex, String detail) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + detail);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    private final CdObjectPlacementRuntime runtime;

    /** The runtime supplies the single live pool and saved bank. No admitted branch consumes Animal assets. */
    public CdAnimal(CdObjectPlacementRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /** One ObjAnimal dispatch for the whole-pool dynamic slot currently holding ID $24. */
    public Result run(int wholeIndex, Globals globals) {
        Objects.requireNonNull(globals, "globals");
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not ObjAnimal $24");
        }
        var effects = new ArrayList<Effect>();
        // CheckAnimalPrescence (Object Time Check.asm:55-84)
        if ((state.subtype() & 0x80) != 0) {                                   // TST.B oSubtype(a0) / BMI.S .End
            throw new UnsupportedAnimal(UnsupportedBoundary.HOLOGRAM_BODY, wholeIndex,
                    "oSubtype $" + Integer.toHexString(state.subtype()) + " selects the hologram body");
        }
        if ((byte) globals.timeZone() >= 2) {                                  // CMPI.B #2,timeZone / BGE.S .ChkGoodFuture (signed)
            if (globals.goodFuture() != 0) {                                   // TST.B goodFuture / BNE.S .End
                throw new UnsupportedAnimal(UnsupportedBoundary.GOOD_FUTURE_BODY, wholeIndex,
                        "timeZone $" + Integer.toHexString(globals.timeZone()) + " with goodFuture set runs the roaming body");
            }
            effects.add(new CallReturned(NativeCall.CHECK_ANIMAL_PRESENCE));
            pool.deleteObject(wholeIndex);                                     // ADDQ.L #4,SP / JMP DeleteObject
            effects.add(new Deleted());
            effects.add(new CallReturned(NativeCall.DELETE_OBJECT));
            return new Result(pool.state(wholeIndex), effects);
        }
        if (globals.projDestroyed() != 0) {                                    // TST.B projDestroyed / BNE.S .End
            throw new UnsupportedAnimal(UnsupportedBoundary.PROJECTOR_DESTROYED_BODY, wholeIndex,
                    "projDestroyed $" + Integer.toHexString(globals.projDestroyed()) + " runs the freed body");
        }
        effects.add(new CallReturned(NativeCall.CHECK_ANIMAL_PRESENCE));
        // ADDQ.L #4,SP / JMP CheckObjDespawn: MOVE.W oX(a0),d0 then the shared window (Object Spawner.asm:385-399).
        checkObjDespawn(state, globals, effects);
        effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));
        return new Result(pool.state(wholeIndex), effects);
    }

    /** CheckObjDespawn / CheckObjDespawn2 / DespawnObjTimeWarp (Object Spawner.asm:385-399,442-446) with d0 = oX. */
    private void checkObjDespawn(CdObjectState state, Globals globals, List<Effect> effects) {
        if ((state.spriteFlags() & 0x80) == 0) {                               // TST.B oSprFlags / BMI DespawnObjTimeWarp
            int d0 = state.centreX() & 0xFF80;                                 // ANDI.W #$FF80,d0
            int d1 = ((globals.cameraXWord() - 0x80) & 0xFFFF) & 0xFF80;       // SUBI.W #$80 / ANDI.W #$FF80
            d0 = (d0 - d1) & 0xFFFF;                                           // SUB.W d1,d0
            if (d0 > 0x80 + (320 + 0x40) + 0x80) {                             // CMPI.W #$280 / BLS: unsigned, equality keeps
                despawnObject(state, DespawnEntry.CURRENT_X_OFFSCREEN, globals, effects);
                return;
            }
        }
        if ((globals.timeZone() & 0x80) != 0) {                                // DespawnObjTimeWarp: BTST #7,timeZone
            despawnObject(state, DespawnEntry.TIME_WARP, globals, effects);
        }
    }

    /** DespawnObject (Object Spawner.asm:403-438): bank loaded bit, then the full 64-byte delete. */
    private void despawnObject(CdObjectState state, DespawnEntry entry, Globals globals, List<Effect> effects) {
        int savedId = state.savedFlagsId();                                    // MOVE.B oSavedFlagsID / BEQ .Delete
        int period = -1;
        if (savedId != 0) {
            period = despawnPeriod(globals);
            runtime.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED); // BCLR #7,2(a1,d0.w)
        }
        runtime.pool().deleteObject(state.identity().wholeIndex());            // JSR DeleteObject
        effects.add(new Despawn(entry, savedId, period));
    }

    /** Object Spawner.asm:413-431: the old period during a warp, clamped to Past..Future. */
    private static int despawnPeriod(Globals globals) {
        int d1 = globals.timeZone() & 0x7F;                                    // MOVE.B timeZone,d1 / BCLR #7,d1
        if ((globals.timeZone() & 0x80) == 0) { return d1; }                   // BEQ .MarkUnloaded
        d1 = (short) ((d1 - (byte) globals.timeWarpDir()) & 0xFFFF);           // EXT.W / NEG.W d2 / ADD.W d2,d1
        if (d1 < 0) { return 0; }                                              // BPL .CheckFuture; MOVEQ #TIME_PAST
        return d1 < 3 ? d1 : 2;                                                // CMPI.W #TIME_FUTURE+1 / BCS; MOVEQ #TIME_FUTURE
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }

    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native word"); }
    }
}
