package com.openggf.route;

/**
 * Declared setup written after a cold boot, before row 0. Absence means the native entry's own
 * value; every present field is an explicit declaration, never an inheritance from an earlier
 * session. These are the capture host's existing declared-setup fields.
 */
public record Seeds(Integer startX, Integer startY, boolean starPost, Integer rings, String emeralds,
                    boolean reverseGravity, boolean endOfLevel, Integer cameraXSub) {
    public static final Seeds NONE = new Seeds(null, null, false, null, null, false, false, null);

    public Seeds {
        if ((startX == null) != (startY == null)) {
            throw new IllegalArgumentException("a positioned entry declares both start x and y");
        }
        if (emeralds != null && !emeralds.matches("[0-3]{7}")) {
            throw new IllegalArgumentException("emeralds are seven digits 0-3: " + emeralds);
        }
    }

    public boolean positioned() {
        return startX != null;
    }

    public boolean none() {
        return equals(NONE);
    }
}
