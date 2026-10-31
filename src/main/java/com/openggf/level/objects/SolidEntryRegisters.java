package com.openggf.level.objects;

import com.openggf.game.solid.SolidCall;

/**
 * The {@code d4} register an object routine hands its solid helper: the
 * object's x position when the routine was entered, before the routine moved
 * its body ({@link SolidCall}). Captured by {@link ObjectExecutionController}
 * immediately before the routine runs.
 *
 * <p>Folded multi-slot providers fold several ROM SST slots into one engine
 * instance; each folded piece keeps the x it had on entry, because each ROM
 * slot computes its own {@code d4}.
 *
 * <p>{@code call} is null until a call site supplies registers; the declared
 * call ({@link #call()}) reads the provider's current {@code d1}-{@code d3},
 * which a routine may change by state before its call site.
 */
record SolidEntryRegisters(
        ObjectManager objects,
        ObjectInstance instance,
        SolidObjectProvider provider,
        int entryX,
        int[] pieceEntryX,
        SolidCall explicitCall) {

    static SolidEntryRegisters capture(ObjectManager objects, ObjectInstance instance,
            SolidObjectProvider provider) {
        int x = objects.callObjectCallback(instance, instance::getX);
        int[] pieces = null;
        if (provider instanceof MultiPieceSolidProvider multi) {
            int count = objects.callObjectCallback(instance, multi::getPieceCount);
            pieces = new int[Math.max(0, count)];
            for (int i = 0; i < pieces.length; i++) {
                int piece = i;
                pieces[i] = objects.callObjectCallback(instance, () -> multi.getPieceX(piece));
            }
        }
        return new SolidEntryRegisters(objects, instance, provider, x, pieces, null);
    }

    /**
     * The routine-entry call: the explicit call, else the provider's declared
     * d1-d3 with d4 = entry x. Reads the provider's params; checkpoint
     * resolution itself reads them once at the helper site instead.
     */
    SolidCall call() {
        if (explicitCall != null || provider == null) {
            return explicitCall;
        }
        SolidObjectParams params = objects.callObjectCallback(instance, provider::getSolidParams);
        if (params == null) {
            return null;
        }
        return new SolidCall(params.halfWidth(), params.airHalfHeight(), params.groundHalfHeight(), entryX);
    }

    /** The AUTO tail call's registers: the provider's declared tail d4 ({@link SolidObjectProvider#tailCallD4}). */
    SolidEntryRegisters forTailCall() {
        if (provider == null) {
            return this;
        }
        int d4 = objects.callObjectCallback(instance, () -> provider.tailCallD4(entryX));
        return d4 == entryX ? this : new SolidEntryRegisters(objects, instance, provider, d4, pieceEntryX, explicitCall);
    }

    SolidEntryRegisters withCall(SolidCall call) {
        return call == null || call == explicitCall
                ? this
                : new SolidEntryRegisters(objects, instance, provider, entryX, pieceEntryX, call);
    }

    /** The helper's d1-d3 applied over the provider's anchor offsets. */
    SolidObjectParams params(SolidObjectParams declared) {
        SolidCall call = explicitCall;
        if (call == null || declared == null
                || (declared.halfWidth() == call.d1HalfWidth()
                    && declared.airHalfHeight() == call.d2AirHalfHeight()
                    && declared.groundHalfHeight() == call.d3GroundHalfHeight())) {
            return declared;
        }
        return SolidObjectParams.of(call.d1HalfWidth(), call.d2AirHalfHeight(), call.d3GroundHalfHeight(),
                declared.offsetX(), declared.offsetY());
    }

    /** {@code d4} for a folded piece's own slot, or the object's {@code d4}. */
    int d4(int pieceIndex) {
        if (pieceIndex >= 0 && pieceEntryX != null && pieceIndex < pieceEntryX.length) {
            return pieceEntryX[pieceIndex];
        }
        return explicitCall != null ? explicitCall.d4CarryX() : entryX;
    }
}
