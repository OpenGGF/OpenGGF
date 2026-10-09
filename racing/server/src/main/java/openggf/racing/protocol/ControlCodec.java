package openggf.racing.protocol;

/**
 * Static facade over one shared {@link ControlJsonCodec} for server-side callers
 * (master, relay broker, operator tools) and tests.
 *
 * <p>Not part of the mod-bound racing library: its shared codec is static state the mod
 * validator rejects. Room hosts and clients own a {@link ControlJsonCodec} instead, and
 * {@code TestRacingLibraryStaticState} checks that no mod-bound class references this
 * facade. It moves to the server artifact with the master when the split lands.
 */
public final class ControlCodec {
    private static final ControlJsonCodec SHARED = new ControlJsonCodec();

    public record DecodedControl(String token, ControlMessage message) {
    }

    private ControlCodec() {
    }

    public static String encode(String tokenOrNull, ControlMessage message) {
        return SHARED.encode(tokenOrNull, message);
    }

    public static DecodedControl decode(String text) {
        return wrap(SHARED.decode(text));
    }

    /** Room traffic cannot use the broker's slotless master-admission marker. */
    public static DecodedControl decodeRoom(String text) {
        return wrap(SHARED.decodeRoom(text));
    }

    /** Decodes under an explicit transport cap; master tunnel wrappers use the larger cap. */
    public static DecodedControl decode(String text, int maxBytes) {
        return wrap(SHARED.decode(text, maxBytes));
    }

    private static DecodedControl wrap(ControlJsonCodec.Decoded decoded) {
        return new DecodedControl(decoded.token(), decoded.message());
    }
}
