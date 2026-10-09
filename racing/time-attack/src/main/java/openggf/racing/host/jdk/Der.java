package openggf.racing.host.jdk;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Minimal DER (X.690) writer for the room's self-signed certificate.
 *
 * <p>The JDK has no public certificate builder, and Bouncy Castle cannot ship in a
 * validated mod, so the handful of ASN.1 shapes an X.509 v3 certificate needs are
 * encoded here. Holds no state.
 */
final class Der {
    static final int TAG_INTEGER = 0x02;
    static final int TAG_BIT_STRING = 0x03;
    static final int TAG_OID = 0x06;
    static final int TAG_UTF8_STRING = 0x0C;
    static final int TAG_UTC_TIME = 0x17;
    static final int TAG_GENERALIZED_TIME = 0x18;
    static final int TAG_SEQUENCE = 0x30;
    static final int TAG_SET = 0x31;
    static final int TAG_CONTEXT_CONSTRUCTED = 0xA0;

    private Der() {
    }

    static byte[] sequence(byte[]... parts) {
        return tagged(TAG_SEQUENCE, concat(parts));
    }

    static byte[] set(byte[]... parts) {
        return tagged(TAG_SET, concat(parts));
    }

    /** Explicitly tagged context-specific constructed value, e.g. {@code [0] EXPLICIT}. */
    static byte[] explicit(int tagNumber, byte[] content) {
        if (tagNumber < 0 || tagNumber > 30) {
            throw new IllegalArgumentException("context tag out of range: " + tagNumber);
        }
        return tagged(TAG_CONTEXT_CONSTRUCTED | tagNumber, content);
    }

    static byte[] integer(BigInteger value) {
        // BigInteger.toByteArray() is already the minimal two's-complement form DER requires.
        return tagged(TAG_INTEGER, value.toByteArray());
    }

    static byte[] utf8String(String value) {
        return tagged(TAG_UTF8_STRING, value.getBytes(StandardCharsets.UTF_8));
    }

    /** BIT STRING with zero unused bits. */
    static byte[] bitString(byte[] content) {
        byte[] body = new byte[content.length + 1];
        System.arraycopy(content, 0, body, 1, content.length);
        return tagged(TAG_BIT_STRING, body);
    }

    static byte[] oid(String dotted) {
        String[] arcs = dotted.split("\\.");
        if (arcs.length < 2) {
            throw new IllegalArgumentException("object identifier needs two arcs: " + dotted);
        }
        long first = Long.parseLong(arcs[0]);
        long second = Long.parseLong(arcs[1]);
        if (first > 2 || (first < 2 && second > 39) || second < 0) {
            throw new IllegalArgumentException("invalid object identifier: " + dotted);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        base128(out, first * 40 + second);
        for (int i = 2; i < arcs.length; i++) {
            long arc = Long.parseLong(arcs[i]);
            if (arc < 0) {
                throw new IllegalArgumentException("invalid object identifier: " + dotted);
            }
            base128(out, arc);
        }
        return tagged(TAG_OID, out.toByteArray());
    }

    /** RFC 5280 §4.1.2.5: UTCTime through 2049, GeneralizedTime from 2050, both in whole seconds. */
    static byte[] time(Instant instant) {
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        int year = utc.getYear();
        if (year >= 1950 && year < 2050) {
            String text = String.format(Locale.ROOT, "%02d%02d%02d%02d%02d%02dZ",
                    year % 100, utc.getMonthValue(), utc.getDayOfMonth(),
                    utc.getHour(), utc.getMinute(), utc.getSecond());
            return tagged(TAG_UTC_TIME, text.getBytes(StandardCharsets.US_ASCII));
        }
        if (year < 0 || year > 9999) {
            throw new IllegalArgumentException("certificate time out of range: " + instant);
        }
        String text = String.format(Locale.ROOT, "%04d%02d%02d%02d%02d%02dZ",
                year, utc.getMonthValue(), utc.getDayOfMonth(),
                utc.getHour(), utc.getMinute(), utc.getSecond());
        return tagged(TAG_GENERALIZED_TIME, text.getBytes(StandardCharsets.US_ASCII));
    }

    static byte[] tagged(int tag, byte[] content) {
        byte[] length = length(content.length);
        byte[] encoded = new byte[1 + length.length + content.length];
        encoded[0] = (byte) tag;
        System.arraycopy(length, 0, encoded, 1, length.length);
        System.arraycopy(content, 0, encoded, 1 + length.length, content.length);
        return encoded;
    }

    private static byte[] length(int length) {
        if (length < 0x80) {
            return new byte[] {(byte) length};
        }
        int bytes = (Integer.SIZE - Integer.numberOfLeadingZeros(length) + 7) / 8;
        byte[] encoded = new byte[1 + bytes];
        encoded[0] = (byte) (0x80 | bytes);
        for (int i = 0; i < bytes; i++) {
            encoded[bytes - i] = (byte) (length >>> (8 * i));
        }
        return encoded;
    }

    private static void base128(ByteArrayOutputStream out, long value) {
        int groups = 1;
        for (long rest = value >>> 7; rest != 0; rest >>>= 7) {
            groups++;
        }
        for (int group = groups - 1; group >= 0; group--) {
            int septet = (int) ((value >>> (7 * group)) & 0x7F);
            out.write(group == 0 ? septet : septet | 0x80);
        }
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
