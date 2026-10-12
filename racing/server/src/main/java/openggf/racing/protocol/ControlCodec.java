package openggf.racing.protocol;

import com.openggf.net.protocol.ControlJsonCodec;
import com.openggf.net.protocol.ControlMessage;

/**
 * Static facade over one shared {@link ControlJsonCodec} for server-side callers
 * (master, relay broker, operator tools) and tests.
 *
 * <p>This operator-only convenience facade is packaged with the dedicated server.
 * Shared engine room hosts and clients own a {@link ControlJsonCodec}; mods can use
 * that public instance codec without depending on the operator artifact.
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
