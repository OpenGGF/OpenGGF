package com.openggf.graphics.text;

import java.lang.Character.UnicodeScript;
import java.text.Normalizer;

/** A face that maps each grapheme cluster to its own glyphs without OpenType shaping. */
abstract class BitmapFaceBase extends HostFace {
    BitmapFaceBase(String name) {
        super(name);
    }

    @Override
    boolean supportsScript(UnicodeScript script) {
        return SIMPLE_SCRIPTS.contains(script);
    }

    /** Glyphs for one cluster, or null when this face cannot draw it faithfully. */
    abstract ClusterGlyphs cluster(String cluster);

    static boolean ignorable(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.FORMAT || type == Character.CONTROL
                || codePoint >= 0xFE00 && codePoint <= 0xFE0F;
    }

    /** NFC form with default-ignorable format characters removed. */
    static String visible(String cluster) {
        String composed = Normalizer.normalize(cluster, Normalizer.Form.NFC);
        StringBuilder kept = null;
        for (int i = 0; i < composed.length(); ) {
            int codePoint = composed.codePointAt(i);
            int next = i + Character.charCount(codePoint);
            if (ignorable(codePoint)) {
                if (kept == null) kept = new StringBuilder(composed.substring(0, i));
            } else if (kept != null) kept.appendCodePoint(codePoint);
            i = next;
        }
        return kept == null ? composed : kept.toString();
    }
}
