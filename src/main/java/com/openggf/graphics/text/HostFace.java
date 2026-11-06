package com.openggf.graphics.text;

import java.lang.Character.UnicodeScript;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** One font usable by host text at one role's pixel size. */
abstract class HostFace {
    private static final AtomicInteger IDS = new AtomicInteger(1);
    /**
     * Scripts whose text is correct as one glyph per cluster in logical order. Everything else
     * needs OpenType shaping, so a bitmap face never claims it merely because it has glyphs.
     */
    static final Set<UnicodeScript> SIMPLE_SCRIPTS = Set.of(UnicodeScript.LATIN, UnicodeScript.GREEK,
            UnicodeScript.CYRILLIC, UnicodeScript.ARMENIAN, UnicodeScript.GEORGIAN, UnicodeScript.HAN,
            UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HANGUL, UnicodeScript.BOPOMOFO,
            UnicodeScript.YI, UnicodeScript.ETHIOPIC, UnicodeScript.CHEROKEE, UnicodeScript.CANADIAN_ABORIGINAL,
            UnicodeScript.BRAILLE, UnicodeScript.COMMON, UnicodeScript.INHERITED);

    final int id = IDS.getAndIncrement();
    final String name;

    HostFace(String name) {
        this.name = name;
    }

    /** Pixels above the baseline that this face's line box needs. */
    abstract int ascent();

    /** Pixels below the baseline that this face's line box needs. */
    abstract int descent();

    abstract boolean supportsScript(UnicodeScript script);

    /** Rasterized coverage for a glyph this face produced. */
    abstract GlyphBitmap bitmap(int glyph);

    /** Whether the face can be used at all (its font data and libraries loaded). */
    boolean available() {
        return true;
    }

    @Override
    public String toString() {
        return name;
    }
}
