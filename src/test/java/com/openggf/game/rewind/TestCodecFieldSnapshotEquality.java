package com.openggf.game.rewind;

import com.openggf.game.rewind.snapshot.GenericObjectSnapshot;
import com.openggf.level.objects.PlayerControlOwners;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Pure capture values only; no actor, source, runtime or admission is installed. */
class TestCodecFieldSnapshotEquality {
    private static final class Owner {
        final PlayerControlOwners controls = new PlayerControlOwners();
    }
    @Test void unchangedCodecBackedCaptureHasValueEquality() {
        var owner = new Owner();
        var first = GenericFieldCapturer.capture(owner);
        var second = GenericFieldCapturer.capture(owner);
        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
    @Test void scalarAndOpaqueContentBothParticipateInEquality() throws Exception {
        var original = snapshot(new byte[] {1, 2}, new Object[] {"role", new int[] {3, 4}});
        var equal = snapshot(new byte[] {1, 2}, new Object[] {"role", new int[] {3, 4}});
        assertEquals(original, equal);
        assertEquals(original.hashCode(), equal.hashCode());
        assertNotEquals(original, snapshot(new byte[] {1, 9}, new Object[] {"role", new int[] {3, 4}}));
        assertNotEquals(original, snapshot(new byte[] {1, 2}, new Object[] {"role", new int[] {3, 9}}));
    }
    @Test void snapshotConstructorRetainsItsOwnInputArrays() throws Exception {
        byte[] scalar = {1, 2}; Object[] opaque = {"role"};
        var captured = snapshot(scalar, opaque);
        scalar[0] = 9; opaque[0] = "changed";
        assertEquals(snapshot(new byte[] {1, 2}, new Object[] {"role"}), captured);
    }
    private static GenericObjectSnapshot snapshot(byte[] scalar, Object[] opaque) throws Exception {
        Class<?> codec = Class.forName(GenericFieldCapturer.class.getName() + "$CodecFieldSnapshot");
        var constructor = codec.getDeclaredConstructor(byte[].class, Object[].class);
        constructor.setAccessible(true);
        Object value = constructor.newInstance(scalar, opaque);
        return new GenericObjectSnapshot(Owner.class,
                List.of(new FieldKey(Owner.class.getName(), "controls")), new Object[] {value});
    }
}
