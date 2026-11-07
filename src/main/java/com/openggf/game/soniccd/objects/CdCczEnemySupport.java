package com.openggf.game.soniccd.objects;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import static com.openggf.game.soniccd.objects.CdCczEnemyPorts.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** Shared finite CC enemy cuts; no scheduler, lifecycle, residency or source ownership.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, common/object_spawn.asm:316ff,
 * common/object_block_collide.asm, Level/Object Animate.asm, Level/Object Time Check.asm.
 * Raw offsets below select existing overlapping STORAGE aliases, not foreign semantic owners.
 */
abstract class CdCczEnemySupport {
    final CdObjectPlacementRuntime placement;
    final CdObjectPool pool;
    final Assets assets;
    final CdCczEnemyPorts ports;
    final CdPlayerTerrain terrain;
    CdObjectState s;
    boolean running;
    AngleCall currentAngle;
    SoundCall currentSound;
    CdCczEnemySupport(CdObjectPlacementRuntime placement, Assets assets, CdPlayerTerrain terrain, CdCczEnemyPorts ports) {
        this.placement = Objects.requireNonNull(placement); pool = placement.pool();
        this.assets = Objects.requireNonNull(assets); this.terrain = terrain; this.ports = Objects.requireNonNull(ports);
    }
    public final void run(int wholeIndex) {
        if (running) throw new IllegalStateException("CC enemy synchronous reentry");
        running = true;
        try { s = pool.state(wholeIndex); if (s.id() != id()) throw new IllegalArgumentException("Wrong enemy ID"); body(); }
        finally { currentAngle = null; currentSound = null; running = false; s = null; }
    }
    abstract int id();
    abstract void body();
    final void put(CdObjectState value) { s = value; pool.write(s); }
    final int b(int offset) { return Byte.toUnsignedInt(s.copyBytes()[offset]); }
    final int w(int offset) { var a = s.copyBytes(); return (Byte.toUnsignedInt(a[offset]) << 8) | Byte.toUnsignedInt(a[offset + 1]); }
    final int l(int offset) { return (w(offset) << 16) | w(offset + 2); }
    static CdObjectState byteAt(CdObjectState v, int at, int value) {
        for (var f : CdObjectState.ByteField.values()) if (f.byteOffset() == at) return v.moveByte(f, value);
        throw new IllegalArgumentException("No owned byte alias " + at);
    }
    static CdObjectState wordAt(CdObjectState v, int at, int value) {
        for (var f : CdObjectState.WordField.values()) if (f.byteOffset() == at) return v.moveWord(f, value);
        throw new IllegalArgumentException("No owned word alias " + at);
    }
    static CdObjectState longAt(CdObjectState v, int at, int value) {
        for (var f : CdObjectState.LongField.values()) if (f.byteOffset() == at) return v.moveLong(f, value);
        throw new IllegalArgumentException("No owned long alias " + at);
    }
    final void b(int at, int value) { put(byteAt(s, at, value)); }
    final void w(int at, int value) { put(wordAt(s, at, value)); }
    final void l(int at, int value) { put(longAt(s, at, value)); }
    final void next() { put(s.addByte(ROUTINE, 2)); }
    final boolean tick(int at) { w(at, w(at) - 1); return w(at) == 0; }
    final int g(Global field) { return read(() -> ports.global(field)); }
    final int p(Player field) { return read(() -> ports.player(field)); }
    final void mapping(Mapping kind) { put(s.moveLong(MAPPINGS, read(() -> assets.mapping(kind)).address())); }
    final void sound(Sound sound) {
        var call = new SoundCall(this, sound); currentSound = call;
        try { read(() -> { ports.sound(call); return 0; }); }
        finally { currentSound = null; }
    }
    /** Full96 x64 images, actual placement status and every saved-bank byte on return AND failure.
     * Preserve the original Throwable, attaching a postcheck failure as suppressed.
     * Never roll back an illicit write and thereby conceal a violation.
     */
    final <T> T read(Supplier<T> call) {
        var before = placement.capture(); Throwable original = null;
        try { return call.get(); }
        catch (RuntimeException | Error failure) { original = failure; throw failure; }
        finally {
            try {
                var after = placement.capture();
                placement.validateRestore(before);
                if (!before.pool().slots().equals(after.pool().slots())
                        || !Arrays.equals(before.savedFlags().copy(), after.savedFlags().copy())
                        || !placementStatus(before).equals(placementStatus(after)))
                    throw new IllegalStateException("CC read-only callback changed complete placement cut");
            } catch (RuntimeException | Error violation) {
                if (original != null) { if (violation != original) original.addSuppressed(violation); }
                else throw violation;
            }
        }
    }
    private static Object placementStatus(CdObjectPlacementRuntime.Snapshot cut) { return cut.status(); }
    final void angle(int value) {
        var call = new AngleCall(this, value); currentAngle = call;
        try { read(() -> { ports.primaryAngle(call); return 0; }); }
        finally { currentAngle = null; }
    }
    final void draw() { var identity = s.identity(); ports.draw(pool, identity); s = pool.state(identity.wholeIndex()); }
    final void delete() { int i = s.identity().wholeIndex(); pool.deleteObject(i); s = pool.state(i); }
    final boolean goodFuture() {
        if (g(Global.GOOD_FUTURE) == 0) return false;
        if (g(Global.TIME_ZONE) == 1 && s.subtype() == 0) return false;
        int x = s.centreX(), y = s.centreY(); delete();
        put(s.moveWord(X, x).moveWord(Y, y).moveByte(ID, 0x18));
        // Post-clear sprite flags are zero. Native sound branch is unreachable.
        return true;
    }
    final void despawn(int anchor) {
        if ((s.spriteFlags() & 0x80) == 0) {
            int camera = g(Global.CAMERA_X);
            if ((((anchor & 0xFF80) - ((camera - 0x80) & 0xFF80)) & 0xFFFF) > 0x280) { unload(); return; }
        }
        if ((g(Global.TIME_ZONE) & 0x80) != 0) unload();
    }
    private void unload() {
        int saved = s.savedFlagsId();
        if (saved != 0) {
            int raw = g(Global.TIME_ZONE), period = raw & 0x7F;
            if ((raw & 0x80) != 0) {
                period = (short) (period - (byte) g(Global.WARP_DIRECTION));
                period = period < 0 ? 0 : period >= 3 ? 2 : period;
            }
            int index = 2 + (short) ((saved * 3 + period) & 0xFFFF);
            if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES)
                throw new UnsupportedOperationException("Reached saved bank address " + index);
            placement.bclrSavedFlag((index - 2) / 3, (index - 2) % 3, CdObjectPlacementRuntime.SavedBit.LOADED);
        }
        delete();
    }
    final void move() {
        put(s.addLong(X_POSITION, s.signedWord(X_VELOCITY) << 8));
        put(s.addLong(Y_POSITION, s.signedWord(Y_VELOCITY) << 8));
    }
    final int floor() {
        return read(() -> CdObjectCollisionCheck.objGetFloorDist(Objects.requireNonNull(terrain), s, this::angle)).distance();
    }
    final int floorAt(int x) {
        return read(() -> {
            angle(0);
            return Objects.requireNonNull(terrain).floor(x & 0xFFFF,
                    (s.centreY() + s.signedByte(Y_RADIUS)) & 0xFFFF, 13, 0, 16, s.spriteFlags(), this::angle).distance();
        });
    }
    /** Player wall probes use +/-10 and no prezero; object probes add unsigned caller d3,
     * including the retail left entry's unmirrored positive radius (do not repair it).
     */
    final int wall(boolean left, boolean playerProbe) {
        return read(() -> {
            int x = s.centreX();
            if (playerProbe) x = left ? ((x - 10) & 0xFFFF) ^ 15 : (x + 10) & 0xFFFF;
            else { x = (x + b(0x17)) & 0xFFFF; angle(0); }
            return Objects.requireNonNull(terrain).wallWithPrefix(x, s.centreY(), 14,
                    left ? 0x800 : 0, left ? -16 : 16, s.spriteFlags(), this::angle).distance();
        });
    }
    final CdObjectState linked(int lowWord) {
        int address = (short) lowWord;
        int delta = address - (short) 0xD000;
        if ((delta & 63) != 0 || delta < 32 * 64 || delta >= 128 * 64)
            throw new UnsupportedOperationException("Reached unowned enemy link $" + Integer.toHexString(lowWord));
        return pool.state(delta / 64);
    }
    final CdObjectState allocate(boolean after) {
        var found = after ? pool.findNextObjSlotAfterWholeIndex(s.identity().wholeIndex()) : pool.findObjSlot();
        return found.isPresent() ? pool.state(found.getAsInt()) : null;
    }
    final void animate(Animation kind) {
        var original = s;
        int anim = b(0x1C);
        if (anim != b(0x1D)) { put(s.moveByte(PREVIOUS_ANIMATION, anim).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0)); }
        put(s.addByte(ANIMATION_TIME, -1));
        if (s.signedByte(ANIMATION_TIME) >= 0) return; // no pointer/table read on held timer
        var table = read(() -> assets.animation(kind));
        int base = table.tableOffset();
        if (anim >= table.scriptCount()) throw new UnsupportedOperationException("Reached animation directory " + anim);
        int script = base + (short) table.bytes().unsignedWord(base + 2 * anim);
        int speed = animationByte(table, script); put(s.moveByte(ANIMATION_TIME, speed));
        int index = b(0x1B), frame = animationByte(table, script + 1 + index);
        if (frame == 0xFF) { put(s.moveByte(ANIMATION_FRAME, 0)); animationByte(table, script + 1); }
        else if (frame == 0xFE) {
            int back = animationByte(table, script + 2 + index); index = (index - back) & 255;
            put(s.moveByte(ANIMATION_FRAME, index)); animationByte(table, script + 1 + index);
        } else if (frame == 0xFD) animationByte(table, script + 2 + index);
        var result = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION),
                original.unsignedByte(PREVIOUS_ANIMATION), original.unsignedByte(ANIMATION_FRAME),
                original.unsignedByte(ANIMATION_TIME), original.unsignedByte(MAP_FRAME), original.spriteFlags(),
                original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)), table);
        put(s.moveByte(ANIMATION, result.animationId()).moveByte(PREVIOUS_ANIMATION, result.previousId())
                .moveByte(ANIMATION_FRAME, result.scriptIndex()).moveByte(ANIMATION_TIME, result.timer())
                .moveByte(MAP_FRAME, result.mapFrame()).moveByte(SPRITE_FLAGS, result.spriteFlags())
                .moveByte(FLAGS, result.statusFlags()).moveByte(ROUTINE, result.routine()).moveByte(ROUTINE_2, result.routine2()));
    }
    private static int animationByte(CdObjectAnimation.AnimationTable table, int at) {
        if (at < 0 || at >= table.bytes().length()) throw new UnsupportedOperationException("Reached animation byte " + at);
        return table.bytes().unsignedByte(at);
    }
    final void unsupported() { throw new UnsupportedOperationException("Reached enemy routine $" + Integer.toHexString(s.routine())); }
}
