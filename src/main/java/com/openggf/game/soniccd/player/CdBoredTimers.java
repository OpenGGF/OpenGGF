package com.openggf.game.soniccd.player;

import java.util.Objects;

/** Shared active bored-WORD update. N1ecc5f3a Level/Main.asm:985-996,
 * r4/main.asm:1390-1401 and common/main.asm:1075-1086 have the same ordered pair.
 * The enclosing Finish owns old-paused BYTE, warp charge BYTE, clocks and invocation guards.
 * No held/controller predicate belongs to this routine. No timer state or capture owner lives here.
 */
final class CdBoredTimers {
    private CdBoredTimers(){ }
    /** Current source-bound owners only. All methods are required; no missing secondary=zero.
     * The actual Frame caller must enforce its existing source/phase/full-cut guard, and each
     * write must retain its completed native prefix if its owner later throws. These primitive
     * ports grant no invocation, lifetime, source, restore or continuation authority.
     */
    interface TimerPair {
        int readPrimary();
        void writePrimary(int word);
        int readSecondary();
        void writeSecondary(int word);
    }
    /** Explicit actual-read boundary until Root binds the real clear/carry/capture value. */
    static final class UnownedSecondaryTimer extends UnsupportedOperationException {
        UnownedSecondaryTimer(){super("Actual secondary bored timer owner is unbound");}
    }
    /** Primary-only legacy Frame value projection. Zero is inactive, otherwise ADDQ.W wraps.
     * Its real caller retains the existing Finish gate and complete Environment publication.
     */
    static int advanceActiveWord(int value){
        int word=word(value);return word==0?0:(word+1)&0xFFFF;
    }
    /** Native TST primary -> conditional store -> TST secondary -> conditional store.
     * Secondary is deliberately borrowed AFTER the completed primary store, never pre-read.
     * No catches/retry/rollback: the exact original owner exception propagates and prevents
     * the enclosing Finish from reaching its later warp/count calls.
     */
    static void updatePair(TimerPair owner){
        Objects.requireNonNull(owner,"Actual paired bored timer owners");
        int first=word(owner.readPrimary());
        if(first!=0)owner.writePrimary(advanceActiveWord(first));
        int second=word(owner.readSecondary());
        if(second!=0)owner.writeSecondary(advanceActiveWord(second));
    }
    private static int word(int value){
        if((value&~0xFFFF)!=0)throw new IllegalArgumentException("Native unsigned timer WORD required");
        return value;
    }
}
