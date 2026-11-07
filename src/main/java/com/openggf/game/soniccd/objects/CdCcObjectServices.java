package com.openggf.game.soniccd.objects;

/**
 * Reached semantic calls only; no defaults. A component mutates its caller's isolated staging pool.
 * This port and a completed call are DATA, never World publication or replay authority. Root must
 * bind original operation/current invocation/exact source and pool, validate full Frame State and
 * Environment (including counterpart), prepare every fallible reference before adoption, and retain
 * the stopped staging prefix on failure. Never invoke these bodies against a live World pool.
 * Contact must execute the existing CdSolidObject plain/Top/GetOff kernel with the same primary
 * throughout the call pair; commit its reached pool/player/angle prefix before returning or throwing.
 */
public interface CdCcObjectServices {
    int act();
    int timeZone();
    int goodFuture();
    int cameraX();
    int animationArtTimer();
    int primaryX();
    int primaryY();
    int timeWarp();
    int invincible();
    int primaryRoutine();
    int primaryHurt();
    /** Source-selected shared Solid; return native d0, not a guessed contact boolean. */
    int contact(CdObjectPool staging, int wholeIndex, CdSolidObject.Entry entry);
    /** Spikes :118-124: exact MOVE.L y-(sign-extended yVel << 8), before direct Hurt. */
    void rewindPrimaryYBeforeHurt();
    /** Execute existing direct HurtPlayer/ring-spill owner, retaining its reached failure prefix. */
    void hurtPrimary(CdObjectPool staging, int sourceWholeIndex);
    /** Fire Shooter/Main.asm:328-333: yVel=-$700, set air, clear stand, animation=$10; same primary as Solid. */
    void launchPrimaryFromFlame();
    int sine(int angleByte);
    void playFm(int soundWord);
    void draw(CdObjectPool staging, int wholeIndex);
    /** DespawnObjectR3, including reached current-period saved-bank store before DeleteObject. */
    void despawnR3(CdObjectPool staging, int wholeIndex);
    /** Native CheckObjectDespawn; projectile uses current X rather than its anchor. */
    void checkProjectileDespawn(CdObjectPool staging, int wholeIndex);
    /** Reached sign-extended low-WORD parent dereference, including reserved-slot ownership fences. */
    int parentX(int rawLowWord, CdObjectPool staging);
    int parentY(int rawLowWord, CdObjectPool staging);
    /** Execute shared CdObjectAnimation with its reached native failure prefix retained. */
    void animate(CdObjectPool staging, int wholeIndex, CdObjectAnimation.AnimationTable table);
}

/** Private slot-storage convenience, not an instruction/register/hardware model. */
final class CdCcBodyCursor {
    final CdObjectPool pool;
    final int index;
    final CdCcObjectServices services;
    CdCcBodyCursor(CdObjectPool pool, int index, int id, CdCcObjectServices services) {
        this.pool = java.util.Objects.requireNonNull(pool); this.index = index;
        this.services = java.util.Objects.requireNonNull(services);
        if (state().id() != id) throw new IllegalArgumentException("Wrong CC family ID");
    }
    CdObjectState state() { return pool.state(index); }
    int b(int at) { return state().copyBytes()[at] & 255; }
    int w(int at) { return (b(at) << 8) | b(at + 1); }
    void b(int at, int value) {
        for (var field : CdObjectState.ByteField.values()) if (field.byteOffset() == at) {
            pool.write(state().moveByte(field, value)); return;
        }
        // Family scratch bytes lacking a central enum alias: preserve the adjacent byte exactly.
        int aligned = at & ~1, prior = w(aligned);
        w(aligned, (at & 1) == 0 ? ((value & 255) << 8) | (prior & 255) : (prior & 0xFF00) | (value & 255));
    }
    void w(int at, int value) {
        for (var field : CdObjectState.WordField.values()) if (field.byteOffset() == at) {
            pool.write(state().moveWord(field, value)); return;
        }
        throw new IllegalArgumentException("Unowned CC word " + at);
    }
    void map(int address) { pool.write(state().moveLong(CdObjectState.LongField.MAPPINGS, address)); }
    void moveX(int velocity) { pool.write(state().addLong(CdObjectState.LongField.X_POSITION, (short) velocity << 8)); }
    void moveY(int velocity) { pool.write(state().addLong(CdObjectState.LongField.Y_POSITION, (short) velocity << 8)); }
    int contact(CdSolidObject.Entry entry) { return services.contact(pool, index, entry); }
    void draw() { services.draw(pool, index); }
    void tail(int anchor) {
        int delta = ((w(anchor) & 0xFF80) - ((services.cameraX() - 0x80) & 0xFF80)) & 0xFFFF;
        if (delta > 0x280) services.despawnR3(pool, index);
    }
    void routine(int maximum) {
        if ((b(0x24) & 1) != 0 || b(0x24) > maximum) throw new UnsupportedOperationException("CC routine byte " + b(0x24));
    }
    void animate(CdObjectAnimation.AnimationTable table) {
        services.animate(pool, index, table);
    }
    void childByte(int child, int at, int value) { new CdCcBodyCursor(pool, child, pool.state(child).id(), services).b(at, value); }
    void childWord(int child, int at, int value) { new CdCcBodyCursor(pool, child, pool.state(child).id(), services).w(at, value); }
    int lowAddress() { return state().identity().nativeAddress24() & 0xFFFF; }
}
