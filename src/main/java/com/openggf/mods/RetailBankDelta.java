package com.openggf.mods;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.openggf.mods.SmpsPackageReader.BANK_LIMIT;
import static com.openggf.mods.SmpsPackageReader.check;

/** Big-endian positional delta against a verified retail bank. */
final class RetailBankDelta {
    static final String PATH = "track.delta";
    static final int MAX_BYTES = 2 + BANK_LIMIT + 4 * (BANK_LIMIT / 2);

    private RetailBankDelta() {}

    static int targetLength(byte[] delta) throws IOException {
        return parse(delta, null).length();
    }

    static byte[] apply(byte[] delta, byte[] base) throws IOException {
        return parse(delta, base).bank();
    }

    private record Parsed(int length, byte[] bank) {}

    private static Parsed parse(byte[] delta, byte[] base) throws IOException {
        check(delta.length >= 2 && delta.length <= MAX_BYTES, "Invalid track.delta length");
        var input = ByteBuffer.wrap(delta);
        int length = Short.toUnsignedInt(input.getShort());
        check(length >= 10 && length <= BANK_LIMIT, "track.delta target length must be in 10..32768");
        byte[] target = base == null ? null : Arrays.copyOf(base, length);
        int previousEnd = -1;
        while (input.hasRemaining()) {
            check(input.remaining() >= 4, "track.delta has a truncated run header");
            int offset = Short.toUnsignedInt(input.getShort());
            int count = Short.toUnsignedInt(input.getShort());
            int end = offset + count;
            check(count > 0 && end <= length, "track.delta run is out of range at " + offset);
            check(offset > previousEnd, "track.delta runs must be sorted and separated by an omitted byte");
            check(input.remaining() >= count, "track.delta has a truncated literal run at " + offset);
            if (target == null) {
                input.position(input.position() + count);
            } else {
                check(offset <= base.length, "track.delta is missing extension bytes");
                for (int i = offset; i < end; i++) {
                    byte literal = input.get();
                    if (i < base.length && literal == base[i]) {
                        throw new IOException("track.delta has an unchanged literal at " + i);
                    }
                    target[i] = literal;
                }
            }
            previousEnd = end;
        }
        if (target != null && length > base.length) {
            check(previousEnd == length, "track.delta is missing extension bytes");
        }
        return new Parsed(length, target);
    }
}
