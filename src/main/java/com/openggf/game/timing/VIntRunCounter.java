package com.openggf.game.timing;

import com.openggf.game.rewind.RewindSnapshottable;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * ROM {@code V_int_run_count}: the longword every V-int handler increments on
 * its way out ({@code VInt_Done: addq.l #1,(V_int_run_count).w},
 * docs/skdisasm/sonic3k.asm:542-543). It lives in {@code CrossResetRAM}
 * (sonic3k.constants.asm:790), so no level load, game-mode change or soft reset
 * clears it: the value an object reads is the number of V-ints serviced since
 * power-on, including every title-screen, menu and load frame.
 *
 * <p>The engine already models the in-level cadence exactly: {@code
 * ObjectManager.vblaCounter} advances once per serviced V-blank while a level is
 * attached, and {@code ObjectServices#resolveVIntRunCount} documents that the
 * value handed to {@code update(...)} is ROM {@code V_int_run_count}. What was
 * missing is the power-on carry: each gameplay session's first object clock
 * started from zero, and frames outside gameplay (title screen, data select,
 * level select, continue screen, ending) advanced nothing. This owner closes
 * that gap with an explicit hand-off:
 *
 * <ul>
 *   <li>While a gameplay session's object clock is {@linkplain #bindObjectClock
 *       bound}, the object clock is the count. {@link #value()} reads it live
 *       and {@link #serviceRepresentedVBlank()} is a no-op, so the validated
 *       per-V-blank tick sites keep sole authority.</li>
 *   <li>When no object clock is bound, {@link #serviceRepresentedVBlank()}
 *       advances the count once per represented V-blank (one per game-loop
 *       iteration in the non-gameplay modes).</li>
 *   <li>A new session seeds its first object clock from
 *       {@link #objectClockSeed()}, and unbinding adopts the object clock's
 *       final value, so the count is continuous across sessions.</li>
 * </ul>
 *
 * <p>Trace replay seeds the object clock once at the segment boundary from the
 * recorded counter (the hardware-relative initial base of
 * docs/architecture/designs/2026-07-27-cross-game-hardware-timing-trace-contract.md
 * section 4) and this owner follows it through the binding; nothing here reads
 * trace data.
 */
public final class VIntRunCounter implements RewindSnapshottable<VIntRunCounterSnapshot> {
    public static final String REWIND_KEY = "v-int-run-count";
    /** Sentinel a bound object-clock supplier returns while no ObjectManager exists yet. */
    public static final long NO_OBJECT_CLOCK = -1L;
    private static final long MASK32 = 0xFFFFFFFFL;

    private long value;
    private LongSupplier objectClock;

    public VIntRunCounter() {
        this(0L);
    }

    public VIntRunCounter(long initialValue) {
        this.value = initialValue & MASK32;
    }

    /** Current {@code V_int_run_count} as the ROM longword (unsigned 32-bit). */
    public long value() {
        long live = liveObjectClock();
        return live != NO_OBJECT_CLOCK ? live : value;
    }

    /** {@code V_int_run_count+2}: the word the Slots reel draw adds (sonic3k.asm:99722). */
    public int lowWord() {
        return (int) (value() & 0xFFFF);
    }

    /** {@code V_int_run_count+3}: the byte most per-frame consumers mask (sonic3k.asm:99646). */
    public int lowByte() {
        return (int) (value() & 0xFF);
    }

    /**
     * Value a freshly created gameplay object clock starts from, so the first
     * level of a session continues the power-on count instead of restarting it.
     */
    public int objectClockSeed() {
        return (int) value();
    }

    /**
     * One represented V-blank outside gameplay. A no-op while an object clock
     * is bound: that clock's own per-V-blank tick sites are the count then.
     */
    public void serviceRepresentedVBlank() {
        if (liveObjectClock() != NO_OBJECT_CLOCK) {
            return;
        }
        value = (value + 1) & MASK32;
    }

    /** Establishes the count directly (power-on reconstruction and tests). */
    public void seed(long newValue) {
        value = newValue & MASK32;
    }

    /**
     * Hands authority to a gameplay session's object clock. The supplier returns
     * the {@code ObjectManager} V-blank counter, or {@link #NO_OBJECT_CLOCK} while
     * the session has not built one yet. Any previously bound clock's final value
     * is adopted first so a re-bind never loses V-ints.
     */
    public void bindObjectClock(LongSupplier clock) {
        Objects.requireNonNull(clock, "clock");
        adoptBoundClock();
        objectClock = clock;
    }

    /** Releases the object clock, carrying its final value forward. */
    public void unbindObjectClock() {
        adoptBoundClock();
        objectClock = null;
    }

    public boolean isObjectClockBound() {
        return objectClock != null;
    }

    private void adoptBoundClock() {
        long live = liveObjectClock();
        if (live != NO_OBJECT_CLOCK) {
            value = live;
        }
    }

    private long liveObjectClock() {
        if (objectClock == null) {
            return NO_OBJECT_CLOCK;
        }
        long live = objectClock.getAsLong();
        return live == NO_OBJECT_CLOCK ? NO_OBJECT_CLOCK : (live & MASK32);
    }

    @Override
    public String key() {
        return REWIND_KEY;
    }

    @Override
    public VIntRunCounterSnapshot capture() {
        return new VIntRunCounterSnapshot(value());
    }

    /**
     * Restores the carried count. While an object clock is bound, the
     * {@code ObjectManager} snapshot restores the live count itself; the value
     * kept here is the one the next unbind or seed would otherwise overwrite.
     */
    @Override
    public void restore(VIntRunCounterSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        value = snapshot.value() & MASK32;
    }

    /** A keyframe without this entry predates the owner; the power-on count is kept. */
    @Override
    public void resetForMissingSnapshot() {
        // Intentionally empty: a missing snapshot must not zero a power-on counter.
    }
}
