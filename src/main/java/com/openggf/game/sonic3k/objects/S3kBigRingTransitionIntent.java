package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.identity.ObjectRefId;

/**
 * Immutable identity copied from Obj_SSEntryRing into Obj_SSEntryFlash.
 *
 * <p>Saved2 values and routing are intentionally not stored here: the ROM
 * reads both at SSEntryFlash_GoSS, after the animation and $20-frame wait.
 */
record S3kBigRingTransitionIntent(int rawSubtype, ObjectRefId parentRingId) {

    int ringBit() {
        return rawSubtype & 0x1F;
    }
}
