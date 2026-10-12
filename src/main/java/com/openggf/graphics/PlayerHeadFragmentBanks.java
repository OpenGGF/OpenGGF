package com.openggf.graphics;

import java.util.HashMap;
import java.util.Map;

/** Graphics-owned companion banks. A duplicate native art bank never overwrites another player's
 * queued head fragments; the native bank namespace already separates runtime player slots. */
final class PlayerHeadFragmentBanks {
    static final int BANK_SIZE = 256;
    private final Map<Integer,Integer> banks = new HashMap<>();
    int baseFor(int sourceBank) {
        return banks.computeIfAbsent(sourceBank, ignored -> {
            int base=PatternAtlasRange.PLAYER_PRESENTATION.base()+banks.size()*BANK_SIZE;
            if(base+BANK_SIZE>PatternAtlasRange.PLAYER_PRESENTATION.endExclusive())
                throw new IllegalStateException("Player head companion bank namespace exhausted");
            return base;
        });
    }
}
