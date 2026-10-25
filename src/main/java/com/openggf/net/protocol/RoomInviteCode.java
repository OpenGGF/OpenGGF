package com.openggf.net.protocol;

/** Six-symbol base32 locator. It does not grant room admission. */
@com.openggf.game.ModApi
public final class RoomInviteCode {
    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int LENGTH = 6;
    public static final int MAX_RAW_LENGTH = 32;
    private final String canonical;

    private RoomInviteCode(String canonical) { this.canonical = canonical; }

    public static RoomInviteCode parse(String raw) {
        if (raw == null || raw.length() > MAX_RAW_LENGTH) {
            throw new IllegalArgumentException("invalid invite code");
        }
        StringBuilder result = new StringBuilder(LENGTH);
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == ' ' || ch == '-') continue;
            if (ch >= 'a' && ch <= 'z') ch -= 'a' - 'A';
            if (ch == 'O') ch = '0';
            if (ch == 'I' || ch == 'L') ch = '1';
            if (ALPHABET.indexOf(ch) < 0 || result.length() >= LENGTH) {
                throw new IllegalArgumentException("invalid invite code");
            }
            result.append(ch);
        }
        if (result.length() != LENGTH) throw new IllegalArgumentException("invalid invite code");
        return new RoomInviteCode(result.toString());
    }

    public String canonical() { return canonical; }

    public String display() { return canonical.substring(0, 3) + " " + canonical.substring(3); }

    @Override
    public String toString() { return "RoomInviteCode[redacted]"; }

    @Override
    public boolean equals(Object other) {
        return other instanceof RoomInviteCode code && canonical.equals(code.canonical);
    }

    @Override
    public int hashCode() { return canonical.hashCode(); }
}
