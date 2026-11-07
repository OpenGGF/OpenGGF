package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.player.CdPlayerMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static com.openggf.game.soniccd.objects.CdCczEnemyPorts.*;
import static com.openggf.game.soniccd.objects.TestCdCczEnemyFamily.W;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed component controls, never World/pass/asset-ingress evidence.
 * Native N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Byte patches below are
 * literal native stores, independent of support's field helpers and animator result.
 */
class TestCdCczEnemyAdversarial {
    @ParameterizedTest
    @CsvSource({"slot,return", "slot,runtime", "slot,error", "saved,return", "saved,runtime",
            "saved,error", "status,return", "status,runtime", "status,error"})
    void isolatedReadonlyMutationKeepsExactThrowableAndReachedImage(String mutation, String outcome) {
        var h = new TestCdCczEnemyFamily.H(0x2D, 2);
        // Empty constructed layout; subsequent chunk motion changes only cursors/status.
        h.placement.spawnObjects(0x200, 1);
        var before = h.placement.capture();
        Throwable original = outcome.equals("error") ? new AssertionError("original") : new IllegalArgumentException("original");
        var marker = new IllegalStateException("already suppressed"); original.addSuppressed(marker);
        var reached = new ArrayList<CdObjectPlacementRuntime.Snapshot>();
        var ports = new Forward(h) {
            @Override public int global(Global field) {
                assertEquals(Global.GOOD_FUTURE, field);
                switch (mutation) {
                    case "slot" -> { byte[] b = h.pool.state(127).copyBytes(); b[0x22] = 99; write(h, 127, b); }
                    case "saved" -> h.placement.bsetSavedFlag(200, 2, CdObjectPlacementRuntime.SavedBit.LOADED);
                    case "status" -> h.placement.spawnObjects(0x280, 1);
                    default -> fail("unknown mutation");
                }
                var after = h.placement.capture(); reached.add(after);
                if (!mutation.equals("slot")) assertEquals(before.pool().slots(), after.pool().slots());
                if (!mutation.equals("saved")) assertArrayEquals(before.savedFlags().copy(), after.savedFlags().copy());
                if (!mutation.equals("status")) assertEquals(before.status(), after.status());
                else assertNotEquals(before.status(), after.status());
                if (outcome.equals("runtime")) throw (RuntimeException) original;
                if (outcome.equals("error")) throw (Error) original;
                return 0;
            }
        };
        Throwable actual = assertThrows(Throwable.class, () -> new CdGa(h.placement, h, ports).run(W));
        if (outcome.equals("return")) {
            assertEquals(IllegalStateException.class, actual.getClass()); assertEquals(0, actual.getSuppressed().length);
        } else {
            assertSame(original, actual); assertEquals(2, actual.getSuppressed().length);
            assertSame(marker, actual.getSuppressed()[0]); actual = actual.getSuppressed()[1];
            assertEquals(IllegalStateException.class, actual.getClass());
        }
        assertEquals("CC read-only callback changed complete placement cut", actual.getMessage());
        var after = h.placement.capture(); assertEquals(1, reached.size());
        assertEquals(reached.getFirst().pool().slots(), after.pool().slots());
        assertArrayEquals(reached.getFirst().savedFlags().copy(), after.savedFlags().copy());
        assertEquals(reached.getFirst().status(), after.status()); assertEquals(0, h.draws);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void gaSecondDrawSeesReusedPhysicalSlotAndRetainsSecondFaultPrefix(boolean error) {
        var h = new TestCdCczEnemyFamily.H(0x2D, 4);
        byte[] initial = h.s().copyBytes(); word(initial, 0x2A, 2); write(h, W, initial);
        var before = h.placement.capture(); var images = new ArrayList<byte[]>();
        Throwable fault = error ? new AssertionError("second Draw") : new IllegalArgumentException("second Draw");
        byte[] reused = new byte[64]; reused[0] = 0x55; word(reused, 8, 0x1000); reused[0x23] = (byte)254; reused[0x22] = 77;
        var ports = new Forward(h) {
            @Override public void draw(CdObjectPool pool, CdObjectState.Identity identity) {
                assertSame(h.pool, pool); assertEquals(W, identity.wholeIndex()); images.add(pool.state(W).copyBytes());
                if (images.size() == 1) { pool.deleteObject(W); write(h, W, reused); }
                else { byte[] b = pool.state(W).copyBytes(); b[0x22] = 88; write(h, W, b);
                    if (error) throw (Error)fault; throw (RuntimeException)fault; }
            }
        };
        assertSame(fault, assertThrows(Throwable.class, () -> new CdGa(h.placement, h, ports).run(W)));
        byte[] first = initial.clone(); word(first, 0x2A, 1); first[0x1E] = 0;
        assertEquals(2, images.size()); assertArrayEquals(first, images.get(0)); assertArrayEquals(reused, images.get(1));
        byte[] expected = reused.clone(); expected[0x22] = 88; assertArrayEquals(expected, h.s().copyBytes());
        assertEquals(List.of("GGOOD_FUTURE"), h.reads); assertEquals(0, fault.getSuppressed().length);
        assertOthers(h, before, W);
    }

    @Test void selfAliasReloadsNextLowWordThenRepeatedForeignSlotWritesBeforeMalformedLink() {
        var h = new TestCdCczEnemyFamily.H(0x2C, 2); h.links = 4;
        byte[] parent = h.s().copyBytes(); word(parent, 0x0E, 0xDA40);
        word(parent, 0x2A, 0x8100); word(parent, 0x2C, 0); word(parent, 0x2E, 0xDA00);
        word(parent, 0x30, 0xDA80); word(parent, 0x32, 0xDA40); word(parent, 0x34, 0xDA41); write(h, W, parent);
        byte[] foreign = new byte[64]; foreign[0] = 0x55; foreign[0x22] = (byte)0xA5; foreign[0x3F] = 0x37; write(h, 41, foreign);
        byte[] untouched42 = h.pool.state(42).copyBytes();
        var fault = assertThrows(UnsupportedOperationException.class, h::run);
        assertEquals("Reached unowned enemy link $da41", fault.getMessage());
        byte[] expectedParent = parent.clone(); longAt(expectedParent, 0x2E, 0x0774DA40); longAt(expectedParent, 0x2A, 0x02800000);
        assertArrayEquals(expectedParent, h.s().copyBytes());
        byte[] expectedForeign = foreign.clone(); longAt(expectedForeign, 0x2E, 0x0774DA40); longAt(expectedForeign, 0x2A, 0x02A00000);
        // Second +$200000 write was overwritten by third +$300000 write, both to slot41.
        assertArrayEquals(expectedForeign, h.pool.state(41).copyBytes());
        assertArrayEquals(untouched42, h.pool.state(42).copyBytes()); assertEquals(0, h.draws);
    }

    @Test void signedNativeOrbitProductsRetainWrapFractionsAndAllOtherBytes() {
        var h = new TestCdCczEnemyFamily.H(0x2C, 2); h.links = 4;
        byte[] parent = h.s().copyBytes(); parent[1] = (byte)0x84; longAt(parent, 8, 0x80012345); longAt(parent, 12, 0xFFFFABCD);
        word(parent, 0x2A, 0x8000); word(parent, 0x2C, 0x100);
        for (int i = 0; i < 4; i++) word(parent, 0x2E + 2*i, 0xDA40 + 64*i);
        write(h, W, parent);
        var old = new ArrayList<byte[]>();
        for (int i = 0; i < 4; i++) { byte[] b = new byte[64]; b[0x22] = (byte)(0x90+i); b[0x28] = (byte)255; b[0x3F] = 0x42; b[0] = 0x55; write(h, 41+i, b); old.add(b); }
        var ports = new Forward(h) {
            @Override public CdPlayerMath.SineCosine sine(int angle) {
                assertEquals(0x81, angle); return new CdPlayerMath.SineCosine(-6, -255);
            }
        };
        new CdSpikeChain(h.placement, h, ports).run(W);
        // N SineTable[$81]=-6, [$C1]=-255; SWAP/CLR/ASR2/ASR1/ASR1.
        int[] y = {0xFFFF4BCD, 0xFFFEEBCD, 0xFFFE8BCD, 0xFFFE2BCD};
        int[] x = {0x7FF13345, 0x7FE14345, 0x7FD15345, 0x7FC16345};
        for (int i = 0; i < 4; i++) { byte[] expected = old.get(i).clone(); longAt(expected, 0x2E, y[i]); longAt(expected, 0x2A, x[i]);
            assertArrayEquals(expected, h.pool.state(41+i).copyBytes(), "link"+i); }
        byte[] expected = parent.clone(); word(expected, 0x2A, 0x8100); assertArrayEquals(expected, h.s().copyBytes());
        assertEquals(1, h.draws);
    }

    @Test void sickleVelocityReadFaultRetainsOnlyReachedMovementGravityAndParryStores() {
        var h = new TestCdCczEnemyFamily.H(0x25, 4); h.invincible = 1;
        byte[] b = h.s().copyBytes(); word(b, 0x10, 0x100); word(b, 0x12, 0x200); word(b, 0x2A, 0x40);
        b[0x20] = (byte)0x87; b[0x1A] = 1; b[0x34] = 20; write(h, W, b);
        var before = h.placement.capture(); var fault = new AssertionError("velocity read"); var reads = new ArrayList<Player>();
        var ports = new Forward(h) {
            @Override public int player(Player field) { reads.add(field); if (field == Player.X_VELOCITY) throw fault; return h.player(field); }
        };
        assertSame(fault, assertThrows(AssertionError.class, () -> new CdKamaSickle(h.placement, h, ports).run(W)));
        byte[] expected = b.clone(); longAt(expected, 8, 0x02710000); longAt(expected, 12, 0x07760000);
        word(expected, 0x12, 0x240); expected[0x20] = 0; expected[0x1C] = 1;
        assertArrayEquals(expected, h.s().copyBytes()); assertEquals(List.of(Player.X, Player.Y, Player.X_VELOCITY), reads);
        assertEquals(0, h.draws); assertTrue(h.sounds.isEmpty()); assertEquals(0, fault.getSuppressed().length); assertOthers(h, before, W);
    }

    @Test void heldAnimatorNeverReadsAssetsThenReachedReadFaultKeepsResetPrefix() {
        var h = new TestCdCczEnemyFamily.H(0x2D, 4); byte[] b = h.s().copyBytes(); word(b, 0x2A, 3); b[0x1E] = 1; write(h, W, b);
        var failure = new AssertionError("reached animation view"); var lookedUp = new ArrayList<Animation>();
        var assets = new Assets() {
            @Override public MappingView mapping(Mapping m) { throw new AssertionError("unreached mapping"); }
            @Override public int chainLinkCount() { throw new AssertionError("unreached count"); }
            @Override public CdObjectAnimation.AnimationTable animation(Animation a) { lookedUp.add(a); throw failure; }
        };
        var ga = new CdGa(h.placement, assets, h); ga.run(W);
        byte[] held = b.clone(); word(held, 0x2A, 2); held[0x1E] = 0; assertArrayEquals(held, h.s().copyBytes()); assertTrue(lookedUp.isEmpty());
        held[0x1C] = 2; held[0x1D] = 1; held[0x1B] = 7; write(h, W, held);
        assertSame(failure, assertThrows(AssertionError.class, () -> ga.run(W)));
        byte[] expected = held.clone(); word(expected, 0x2A, 1); expected[0x1D] = 2; expected[0x1B] = 0; expected[0x1E] = (byte)255;
        assertArrayEquals(expected, h.s().copyBytes()); assertEquals(List.of(Animation.GA), lookedUp); assertEquals(2, h.draws);
    }

    @Test void signedRelativeAnimationMissingFrameKeepsSpeedWithoutPublishingMapOrFlags() {
        var h = new TestCdCczEnemyFamily.H(0x2D, 4); byte[] b = h.s().copyBytes(); word(b, 0x2A, 2);
        b[0x1E] = 0; b[0x1B] = 127; b[0x1A] = 0x16; b[1] = (byte)0x85; write(h, W, b);
        // Directory base4 + signed -4 points at speed0; frame index127 reaches unavailable128.
        h.animation = new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(new byte[]{7,0,0,0,(byte)255,(byte)252}), 4, 1);
        var fault = assertThrows(UnsupportedOperationException.class, h::run); assertEquals("Reached animation byte 128", fault.getMessage());
        byte[] expected = b.clone(); word(expected, 0x2A, 1); expected[0x1E] = 7;
        assertArrayEquals(expected, h.s().copyBytes()); assertEquals(0, h.draws);
    }

    @Test void lastOwnedSavedByte765IsClearedBeforeDelete() {
        var h = new TestCdCczEnemyFamily.H(0x2D, 2); h.time = 1;
        h.placement.bsetSavedFlag(254, 1, CdObjectPlacementRuntime.SavedBit.LOADED);
        byte[] b = h.s().copyBytes(); word(b, 8, 0x1000); b[0x23] = (byte)254; word(b, 0x2A, 2); write(h, W, b);
        byte[] expectedBank = h.placement.capture().savedFlags().copy(); assertEquals(766, expectedBank.length);
        assertEquals(128, Byte.toUnsignedInt(expectedBank[765])); expectedBank[765] = 0;
        h.run(); assertArrayEquals(new byte[64], h.s().copyBytes()); assertArrayEquals(expectedBank, h.placement.capture().savedFlags().copy()); assertEquals(1, h.draws);
    }

    static class Forward implements CdCczEnemyPorts {
        final TestCdCczEnemyFamily.H h; Forward(TestCdCczEnemyFamily.H h) { this.h = h; }
        public int global(Global g) { return h.global(g); } public int player(Player p) { return h.player(p); }
        public CdPlayerMath.SineCosine sine(int a) { return h.sine(a); } public void sound(SoundCall c) { h.sound(c); }
        public void primaryAngle(AngleCall c) { h.primaryAngle(c); } public void draw(CdObjectPool p, CdObjectState.Identity i) { h.draw(p,i); }
    }
    static void write(TestCdCczEnemyFamily.H h, int slot, byte[] bytes) {
        var state = h.pool.state(slot);
        for (var f : CdObjectState.LongField.values()) { int a = f.byteOffset(); state = state.moveLong(f, ((bytes[a]&255)<<24) | ((bytes[a+1]&255)<<16) | ((bytes[a+2]&255)<<8) | (bytes[a+3]&255)); }
        for (var f : CdObjectState.WordField.values()) { int a = f.byteOffset(); state = state.moveWord(f, ((bytes[a]&255)<<8) | (bytes[a+1]&255)); }
        for (var f : CdObjectState.ByteField.values()) state = state.moveByte(f, bytes[f.byteOffset()]);
        assertArrayEquals(bytes, state.copyBytes(), "fixture uses only existing owned fields"); h.pool.write(state); }
    static void word(byte[] b, int at, int v) { b[at] = (byte)(v >>> 8); b[at+1] = (byte)v; }
    static void longAt(byte[] b, int at, int v) { word(b, at, v >>> 16); word(b, at+2, v); }
    static void assertOthers(TestCdCczEnemyFamily.H h, CdObjectPlacementRuntime.Snapshot before, int own) {
        for (int i = 32; i < 128; i++) if (i != own) assertEquals(before.pool().slots().get(i-32), h.pool.state(i), "slot"+i);
        assertArrayEquals(before.savedFlags().copy(), h.placement.capture().savedFlags().copy()); assertEquals(before.status(), h.placement.status());
    }
}
