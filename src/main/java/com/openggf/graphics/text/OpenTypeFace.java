package com.openggf.graphics.text;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.harfbuzz.hb_glyph_info_t;
import org.lwjgl.util.harfbuzz.hb_glyph_position_t;

import java.io.IOException;
import java.io.InputStream;
import java.lang.Character.UnicodeScript;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.util.freetype.FreeType.*;
import static org.lwjgl.util.harfbuzz.HarfBuzz.*;

/**
 * A bundled OpenType font shaped by HarfBuzz and rasterized by FreeType at one pixel size.
 * Both libraries come from the LWJGL FreeType natives, whose build also exports the HarfBuzz
 * API (including the FreeType integration that the standalone LWJGL HarfBuzz library lacks).
 * All native calls share one lock: FreeType objects are not thread-safe.
 */
final class OpenTypeFace extends HostFace {
    private static final Logger LOG = Logger.getLogger(OpenTypeFace.class.getName());
    static final Object NATIVE_LOCK = new Object();
    private static long library;
    private static Throwable libraryFailure;

    private final String resource;
    private final int pixels;
    private final Set<UnicodeScript> scripts;
    private final Map<Integer, GlyphBitmap> bitmaps = new HashMap<>();
    private final Map<Integer, Boolean> nominal = new HashMap<>();
    private boolean opened;
    private Throwable failure;
    private ByteBuffer data;
    private FT_Face face;
    private long font;
    private int ascent;
    private int descent;

    OpenTypeFace(String name, String resource, int pixels, Set<UnicodeScript> scripts) {
        super(name);
        this.resource = resource;
        this.pixels = pixels;
        this.scripts = Set.copyOf(scripts);
    }

    /** Opens FreeType once per process; HarfBuzz must bind to the same library first. */
    private static long library() {
        if (library != 0 || libraryFailure != null) return library;
        try {
            Configuration.HARFBUZZ_LIBRARY_NAME.set("freetype");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer pointer = stack.mallocPointer(1);
                check(FT_Init_FreeType(pointer), "FT_Init_FreeType");
                library = pointer.get(0);
            }
        } catch (Throwable failure) {
            libraryFailure = failure;
            LOG.log(Level.WARNING, "FreeType/HarfBuzz unavailable; complex-script host text will show "
                    + "missing-glyph boxes", failure);
        }
        return library;
    }

    private boolean open() {
        if (opened) return failure == null;
        opened = true;
        try {
            long lib = library();
            if (lib == 0) throw new IllegalStateException("FreeType unavailable", libraryFailure);
            byte[] bytes;
            try (InputStream in = OpenTypeFace.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) throw new IOException("missing resource " + resource);
                bytes = in.readAllBytes();
            }
            // FreeType reads the font in place for the life of the face, which is the process.
            data = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer pointer = stack.mallocPointer(1);
                check(FT_New_Memory_Face(lib, data, 0, pointer), "FT_New_Memory_Face " + resource);
                face = FT_Face.create(pointer.get(0));
            }
            check(FT_Set_Pixel_Sizes(face, 0, pixels), "FT_Set_Pixel_Sizes");
            font = hb_ft_font_create_referenced(face.address());
            hb_ft_font_set_load_flags(font, loadFlags());
            ascent = (int) Math.ceil(face.size().metrics().ascender() / 64.0);
            descent = (int) Math.ceil(-face.size().metrics().descender() / 64.0);
            return true;
        } catch (Throwable thrown) {
            failure = thrown;
            LOG.log(Level.WARNING, "Host font " + resource + " is unavailable", thrown);
            return false;
        }
    }

    private static int loadFlags() {
        return FT_LOAD_DEFAULT | FT_FT_LOAD_TARGET_LIGHT;
    }

    private static void check(int error, String call) {
        if (error != 0) throw new IllegalStateException(call + " failed with FreeType error " + error);
    }

    @Override
    boolean available() {
        synchronized (NATIVE_LOCK) {
            return open();
        }
    }

    @Override
    int ascent() {
        synchronized (NATIVE_LOCK) {
            return open() ? ascent : 0;
        }
    }

    @Override
    int descent() {
        synchronized (NATIVE_LOCK) {
            return open() ? descent : 0;
        }
    }

    @Override
    boolean supportsScript(UnicodeScript script) {
        return scripts.contains(script) || script == UnicodeScript.COMMON || script == UnicodeScript.INHERITED;
    }

    /** Whether the font maps every non-ignorable codepoint of {@code cluster}. */
    boolean covers(String cluster) {
        synchronized (NATIVE_LOCK) {
            if (!open()) return false;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer glyph = stack.mallocInt(1);
                for (int i = 0; i < cluster.length(); ) {
                    int codePoint = cluster.codePointAt(i);
                    i += Character.charCount(codePoint);
                    if (BitmapFaceBase.ignorable(codePoint)) continue;
                    Boolean known = nominal.get(codePoint);
                    if (known == null) {
                        known = hb_font_get_nominal_glyph(font, codePoint, glyph) && glyph.get(0) != 0;
                        nominal.put(codePoint, known);
                    }
                    if (!known) return false;
                }
                return true;
            }
        }
    }

    /** HarfBuzz output for one run; positions are 26.6 fixed point, clusters are UTF-16 indices. */
    record Run(int[] glyphs, int[] clusters, int[] advances, int[] xOffsets, int[] yOffsets) { }

    Run shape(String text, int start, int end, boolean rtl, UnicodeScript script, Locale language) {
        synchronized (NATIVE_LOCK) {
            if (!open()) return null;
            long buffer = hb_buffer_create();
            try {
                hb_buffer_set_cluster_level(buffer, HB_BUFFER_CLUSTER_LEVEL_MONOTONE_CHARACTERS);
                hb_buffer_add_utf16(buffer, text.subSequence(0, text.length()), start, end - start);
                hb_buffer_set_direction(buffer, rtl ? HB_DIRECTION_RTL : HB_DIRECTION_LTR);
                hb_buffer_set_script(buffer, hb_script_from_string(scriptTag(script)));
                String tag = language.toLanguageTag();
                if (!"und".equals(tag)) hb_buffer_set_language(buffer, hb_language_from_string(tag));
                hb_buffer_guess_segment_properties(buffer);
                hb_shape(font, buffer, null);
                int count = hb_buffer_get_length(buffer);
                hb_glyph_info_t.Buffer infos = hb_buffer_get_glyph_infos(buffer);
                hb_glyph_position_t.Buffer positions = hb_buffer_get_glyph_positions(buffer);
                Run run = new Run(new int[count], new int[count], new int[count], new int[count], new int[count]);
                for (int i = 0; i < count; i++) {
                    run.glyphs()[i] = infos.get(i).codepoint();
                    run.clusters()[i] = infos.get(i).cluster();
                    run.advances()[i] = positions.get(i).x_advance();
                    run.xOffsets()[i] = positions.get(i).x_offset();
                    run.yOffsets()[i] = positions.get(i).y_offset();
                }
                return run;
            } finally {
                hb_buffer_destroy(buffer);
            }
        }
    }

    private static String scriptTag(UnicodeScript script) {
        return switch (script) {
            case ARABIC -> "Arab";
            case DEVANAGARI -> "Deva";
            case BENGALI -> "Beng";
            case THAI -> "Thai";
            case HEBREW -> "Hebr";
            default -> {
                // ISO 15924 codes are the first four letters of most Unicode script names.
                String name = script.name();
                yield name.length() < 4 ? "Zyyy"
                        : name.charAt(0) + name.substring(1, 4).toLowerCase(Locale.ROOT);
            }
        };
    }

    @Override
    GlyphBitmap bitmap(int glyph) {
        synchronized (NATIVE_LOCK) {
            GlyphBitmap cached = bitmaps.get(glyph);
            if (cached != null) return cached;
            GlyphBitmap rendered = GlyphBitmap.EMPTY;
            if (open() && FT_Load_Glyph(face, glyph, loadFlags()) == 0) {
                FT_GlyphSlot slot = face.glyph();
                if (slot != null && FT_Render_Glyph(slot, FT_RENDER_MODE_NORMAL) == 0) {
                    FT_Bitmap bitmap = slot.bitmap();
                    int width = bitmap.width();
                    int height = bitmap.rows();
                    if (width > 0 && height > 0) {
                        ByteBuffer pixels = bitmap.buffer(Math.abs(bitmap.pitch()) * height);
                        byte[] alpha = new byte[width * height];
                        for (int y = 0; y < height; y++) {
                            int row = bitmap.pitch() >= 0 ? y : height - 1 - y;
                            for (int x = 0; x < width; x++) {
                                alpha[y * width + x] = pixels.get(row * Math.abs(bitmap.pitch()) + x);
                            }
                        }
                        rendered = new GlyphBitmap(width, height, slot.bitmap_left(), -slot.bitmap_top(), alpha);
                    }
                }
            }
            bitmaps.put(glyph, rendered);
            return rendered;
        }
    }
}
