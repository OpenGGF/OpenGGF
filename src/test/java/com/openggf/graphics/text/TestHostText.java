package com.openggf.graphics.text;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-free and GL-free checks of the shared host text owner. */
class TestHostText {
    private static final HostText TEXT = HostText.shared();
    private static final TextLanguage EN = TextLanguage.of("en");

    @Test
    void printableAsciiKeepsTheNativeFixedCellsInBothRoles() {
        ShapedLine compact = TEXT.shape("OpenGGF 1.0", TextFace.COMPACT, EN);
        ShapedLine label = TEXT.shape("OpenGGF 1.0", TextFace.LABEL, EN);
        assertEquals(66, compact.width());
        assertEquals(99, label.width());
        assertEquals(10, compact.glyphs().size(), "Spaces draw nothing");
        for (PlacedGlyph glyph : compact.glyphs()) {
            assertEquals(PlacedGlyph.Kind.NATIVE, glyph.kind());
            assertEquals(glyph.cluster() * 6, glyph.x());
        }
        assertEquals(new LineMetrics(8, 7, 7, 1), TEXT.lineMetrics(TextFace.COMPACT, EN));
        assertEquals(8, TEXT.lineMetrics(TextFace.COMPACT, EN).lineHeight());
        assertEquals(10, TEXT.lineMetrics(TextFace.LABEL, EN).lineHeight());
        assertEquals("OpenGGF...", TEXT.fit("OpenGGF settings", 60, TextFace.COMPACT, EN),
                "ASCII fitting matches the historical cell arithmetic");
        assertEquals("..", TEXT.fit("OpenGGF", 12, TextFace.COMPACT, EN));
    }

    @Test
    void europeanAndCyrillicTextStaysInTheAuthoredMenuLettering() {
        Map<String, String> samples = Map.of(
                "es", "Configuración ¿Guardar? ¡Sí! Año",
                "fr", "Paramètres « vidéo » Œuvre déjà été",
                "de", "Übernehmen Größe Ärger Öl",
                "pt-BR", "Configurações ação não Ônibus",
                "pt-PT", "Definições gravação",
                "it", "Proprietà città perché è",
                "pl", "Zażółć gęślą jaźń ŁÓDŹ",
                "id", "Pengaturan suara",
                "ru", "Настройки звука Ёлка щука ЭЮЯ");
        samples.forEach((tag, sample) -> {
            for (TextFace role : TextFace.values()) {
                ShapedLine line = TEXT.shape(sample, role, TextLanguage.of(tag));
                assertTrue(line.complete(), tag + " " + line.missing());
                assertTrue(line.glyphs().stream().allMatch(g -> g.kind() == PlacedGlyph.Kind.NATIVE),
                        () -> tag + " must not leave the native lettering: " + fonts(line));
                int cell = role == TextFace.LABEL ? 9 : 6;
                assertEquals(Graphemes.count(sample) * cell, line.width(), tag);
                assertEquals(0, line.above(), tag + " accents fit the native cell");
            }
        });
        ShapedLine decomposed = TEXT.shape("é", TextFace.COMPACT, EN);
        assertEquals(TEXT.shape("é", TextFace.COMPACT, EN).glyphs().getFirst().glyph(),
                decomposed.glyphs().getFirst().glyph(), "Decomposed input normalizes to the same native letter");
        assertEquals(6, decomposed.width());
    }

    @Test
    void hanGlyphFormsFollowTheLanguageAndHangulAndKanaAreCovered() {
        String bone = "骨"; // 骨 differs between Simplified, Traditional and Japanese forms
        String hans = fonts(TEXT.shape(bone, TextFace.COMPACT, TextLanguage.of("zh-Hans")));
        String hant = fonts(TEXT.shape(bone, TextFace.COMPACT, TextLanguage.of("zh-TW")));
        String ja = fonts(TEXT.shape(bone, TextFace.COMPACT, TextLanguage.of("ja")));
        assertTrue(hans.contains("zh-hans"), hans);
        assertTrue(hant.contains("zh-hant"), hant);
        assertTrue(ja.contains("-ja"), ja);
        for (String[] sample : new String[][] {{"ja", "設定を保存しました。カタカナ"}, {"ko", "설정 저장 완료"},
                {"zh-Hans", "保存设置（需要重启）"}, {"zh-Hant", "儲存設定（需要重新啟動）"}}) {
            ShapedLine line = TEXT.shape(sample[1], TextFace.COMPACT, TextLanguage.of(sample[0]));
            assertTrue(line.complete(), sample[0] + " " + line.missing());
            assertTrue(line.width() > 0);
        }
        LineMetrics ja13 = TEXT.lineMetrics(TextFace.COMPACT, TextLanguage.of("ja"));
        assertTrue(ja13.lineHeight() >= 13, "Ten-pixel CJK keeps a legible pitch: " + ja13);
    }

    @Test
    void arabicAndUrduAreJoinedRightToLeftByTheShaper() {
        TextLanguage ar = TextLanguage.of("ar");
        String word = "سلام";
        ShapedLine joined = TEXT.shape(word, TextFace.COMPACT, ar);
        assertTrue(joined.complete());
        assertTrue(fonts(joined).contains("arabic"), fonts(joined));
        List<Integer> isolated = new ArrayList<>();
        for (int i = 0; i < word.length(); i++) {
            isolated.add(TEXT.shape(word.substring(i, i + 1), TextFace.COMPACT, ar).glyphs().getFirst().glyph());
        }
        List<Integer> contextual = joined.glyphs().stream().map(PlacedGlyph::glyph).collect(Collectors.toList());
        java.util.Collections.reverse(contextual); // visual order is right to left
        assertNotEquals(isolated, contextual, "Joining must select contextual forms, not isolated glyphs");
        PlacedGlyph first = joined.glyphs().stream().filter(g -> g.cluster() == 0).findFirst().orElseThrow();
        assertTrue(joined.glyphs().stream().allMatch(g -> g.x() <= first.x()),
                "The first logical letter is the rightmost in right-to-left text");
        ShapedLine urdu = TEXT.shape("ٹھیک ہے، ڈرائیو اور ڑ ں ے", TextFace.COMPACT, TextLanguage.of("ur"));
        assertTrue(urdu.complete(), urdu.missing().toString());
        ShapedLine mixed = TEXT.shape("حفظ ROM 123", TextFace.COMPACT, ar);
        assertTrue(mixed.complete());
        assertTrue(fonts(mixed).contains("menu"), "Latin and digits inside Arabic keep the native lettering");
    }

    @Test
    void indicConjunctsAndReorderingComeFromTheShaper() {
        for (String[] sample : new String[][] {{"hi", "कि", "क्ष"}, {"bn", "কি", "ক্ষ"}}) {
            TextLanguage language = TextLanguage.of(sample[0]);
            ShapedLine reordered = TEXT.shape(sample[1], TextFace.COMPACT, language);
            assertTrue(reordered.complete());
            assertEquals(2, reordered.glyphs().size());
            // The pre-base vowel sign is the second character but the first glyph.
            assertTrue(reordered.glyphs().get(0).glyph() != TEXT.shape(sample[1].substring(0, 1),
                    TextFace.COMPACT, language).glyphs().getFirst().glyph(), sample[0] + " i-matra precedes its consonant");
            ShapedLine conjunct = TEXT.shape(sample[2], TextFace.COMPACT, language);
            assertTrue(conjunct.complete());
            assertTrue(conjunct.glyphs().size() < sample[2].length(), sample[0] + " conjunct is a ligature");
            assertEquals(1, Graphemes.count(sample[2]), "Conjuncts are one grapheme for editing and fitting");
        }
        ShapedLine thai = TEXT.shape("น้ำ การตั้งค่า", TextFace.COMPACT, TextLanguage.of("th"));
        assertTrue(thai.complete());
        assertTrue(fonts(thai).contains("thai"));
        int widthOfBase = TEXT.shape("น", TextFace.COMPACT, TextLanguage.of("th")).width();
        assertTrue(thai.glyphs().stream().anyMatch(g -> g.advance() == 0), "Thai marks stack with zero advance");
        assertTrue(widthOfBase > 0);
    }

    @Test
    void shapedGlyphsMatchAnIndependentLayoutEngineUsingTheSameFonts() throws Exception {
        String[][] samples = {
                {"ar", "fonts/NotoSansArabicUI-Regular.ttf", "11", "الإعدادات"},
                {"ur", "fonts/NotoSansArabicUI-Regular.ttf", "11", "ترتیبات ٹھیک ہے"},
                {"hi", "fonts/NotoSansDevanagariUI-Regular.ttf", "12", "सेटिंग्स क्षमा कि"},
                {"bn", "fonts/NotoSansBengaliUI-Regular.ttf", "12", "সেটিংস ক্ষমা কি"},
                {"th", "fonts/NotoSansThai-Regular.ttf", "11", "การตั้งค่า น้ำ"}};
        FontRenderContext context = new FontRenderContext(null, false, false);
        for (String[] sample : samples) {
            Font font;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(sample[1])) {
                font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(Float.parseFloat(sample[2]));
            }
            char[] chars = sample[3].toCharArray();
            boolean rtl = sample[0].equals("ar") || sample[0].equals("ur");
            GlyphVector reference = font.layoutGlyphVector(context, chars, 0, chars.length,
                    rtl ? Font.LAYOUT_RIGHT_TO_LEFT : Font.LAYOUT_LEFT_TO_RIGHT);
            List<Integer> expected = new ArrayList<>();
            for (int i = 0; i < reference.getNumGlyphs(); i++) expected.add(reference.getGlyphCode(i));
            List<Integer> actual = TEXT.shape(sample[3], TextFace.COMPACT, TextLanguage.of(sample[0])).glyphs().stream()
                    .map(PlacedGlyph::glyph).toList();
            assertEquals(expected, actual, sample[0] + " glyph sequence must match the JDK layout engine");
        }
    }

    @Test
    void scriptsWithoutABundledShaperAreReportedMissingInsteadOfDrawnAsIsolatedGlyphs() {
        // Unifont has Tamil and Gujarati glyphs, but only an OpenType shaper can order and join them.
        for (String sample : new String[] {"தமிழ்", "ગુજરાતી"}) {
            ShapedLine line = TEXT.shape(sample, TextFace.COMPACT, EN);
            assertFalse(line.complete(), sample);
            assertTrue(line.glyphs().stream().allMatch(g -> g.kind() == PlacedGlyph.Kind.MISSING), fonts(line));
        }
        ShapedLine emoji = TEXT.shape("A👨‍👩‍👧B", TextFace.COMPACT, EN);
        assertEquals(List.of("👨‍👩‍👧"), emoji.missing(), "A ZWJ family is one missing cluster");
        assertEquals(18, emoji.width());
        ShapedLine controls = TEXT.shape("⁨ROM⁩‍", TextFace.COMPACT, EN);
        assertTrue(controls.complete(), "Bidi isolates and joiners are invisible, not missing");
        assertEquals(18, controls.width());
    }

    @Test
    void fittingNeverSplitsAGraphemeAndStaysWithinTheWidth() {
        String[][] samples = {{"en", "Zoé 👨‍👩‍👧 family name"}, {"hi", "क्षमा क्षमा क्षमा क्षमा"},
                {"ar", "الإعدادات المتقدمة للعبة"}, {"ja", "設定を保存しました。ゲームを再起動"}, {"th", "การตั้งค่าเสียงและภาพ"}};
        for (String[] sample : samples) {
            TextLanguage language = TextLanguage.of(sample[0]);
            for (int max = 20; max < 120; max += 7) {
                String fitted = TEXT.fit(sample[1], max, TextFace.COMPACT, language);
                assertTrue(TEXT.width(fitted, TextFace.COMPACT, language) <= max, fitted + " @" + max);
                if (fitted.endsWith("...") && fitted.length() > 3) {
                    String kept = fitted.substring(0, fitted.length() - 3);
                    assertTrue(sample[1].startsWith(kept));
                    assertTrue(Graphemes.isBoundary(sample[1], kept.length()), sample[0] + " split at " + kept.length());
                }
            }
        }
    }

    @Test
    void wrappingUsesLanguageBreaksAndEveryLineFits() {
        String[][] samples = {{"th", "การตั้งค่าเสียงและภาพสำหรับเกมนี้"}, {"ja", "設定を保存しました。ゲームを再起動してください"},
                {"zh-Hans", "保存设置后需要重新启动游戏才能生效"}, {"ar", "حفظ الإعدادات الآن من فضلك قبل الخروج"},
                {"hi", "सेटिंग्स सहेजने के लिए खेल को फिर से शुरू करें"}, {"de", "Donaudampfschifffahrtsgesellschaftskapitän fährt"},
                {"fr", "Les paramètres vidéo seront appliqués au prochain démarrage"}};
        for (String[] sample : samples) {
            TextLanguage language = TextLanguage.of(sample[0]);
            List<String> lines = TEXT.wrap(sample[1], 80, TextFace.COMPACT, language);
            assertTrue(lines.size() > 1, sample[0]);
            for (String line : lines) {
                assertTrue(TEXT.width(line, TextFace.COMPACT, language) <= 80, sample[0] + ": " + line);
            }
            assertEquals(sample[1].replace(" ", ""), String.join("", lines).replace(" ", ""), "Wrapping loses no text");
            for (String line : lines) assertFalse(line.isEmpty());
        }
        List<String> thai = TEXT.wrap("การตั้งค่าเสียงและภาพ", 60, TextFace.COMPACT, TextLanguage.of("th"));
        assertTrue(thai.stream().allMatch(line -> Graphemes.isBoundary(line, 0)));
        assertEquals(List.of("", "A", ""), TEXT.wrap("\nA\n", 40, TextFace.COMPACT, EN));
    }

    @Test
    void lineMetricsReserveTallerScriptsWithoutShrinkingThem() {
        for (String tag : new String[] {"ar", "ur", "hi", "bn", "th"}) {
            LineMetrics compact = TEXT.lineMetrics(TextFace.COMPACT, TextLanguage.of(tag));
            assertTrue(compact.lineHeight() >= 15, tag + " " + compact);
            assertEquals(7, compact.baseline(), "Callers' cell baseline is unchanged");
            ShapedLine line = TEXT.shape(switch (tag) {
                case "ar" -> "الإعدادات"; case "ur" -> "ترتیبات"; case "hi" -> "सेटिंग्स";
                case "bn" -> "সেটিংস"; default -> "การตั้งค่า"; }, TextFace.COMPACT, TextLanguage.of(tag));
            assertTrue(line.metrics().lineHeight() <= compact.lineHeight(), "The reserved pitch covers real lines");
        }
        assertEquals(8, TEXT.lineMetrics(TextFace.COMPACT, TextLanguage.of("ru")).lineHeight());
    }

    @Test
    void caretAndHitTestingUseTheShapedPositions() {
        ShapedLine line = TEXT.shape("Zoé!", TextFace.COMPACT, EN);
        assertEquals(0, line.caretX(0));
        assertEquals(12, line.caretX(2));
        assertEquals(18, line.caretX(3), "Inside a cluster takes the following boundary");
        assertEquals(18, line.caretX(4));
        assertEquals(24, line.caretX(5));
        assertEquals(5, line.hitIndex(100));
        assertEquals(4, line.hitIndex(19), "Hit-testing returns grapheme boundaries only");
    }

    @Test
    void activatingTheContextChangesItsRevisionOnlyOnRealChanges() {
        TextLanguage before = HostTextContext.current();
        try {
            HostTextContext.activate(TextLanguage.of("fr"));
            long revision = HostTextContext.revision();
            HostTextContext.activate(TextLanguage.of("fr"));
            assertEquals(revision, HostTextContext.revision());
            HostTextContext.activate(java.util.Locale.forLanguageTag("ar"));
            assertEquals(revision + 1, HostTextContext.revision());
            assertEquals("ar", HostTextContext.current().tag());
        } finally {
            HostTextContext.activate(before);
        }
    }

    @Test
    void addedFontScriptsUseCanonicalIsoTagsInsteadOfGuessedNamePrefixes() throws Exception {
        var method = OpenTypeFace.class.getDeclaredMethod("scriptTag", Character.UnicodeScript.class);
        method.setAccessible(true);
        var aliases = Map.of(
                Character.UnicodeScript.MALAYALAM, "Mlym",
                Character.UnicodeScript.TAMIL, "Taml",
                Character.UnicodeScript.GURMUKHI, "Guru",
                Character.UnicodeScript.GUJARATI, "Gujr",
                Character.UnicodeScript.KANNADA, "Knda",
                Character.UnicodeScript.SYRIAC, "Syrc",
                Character.UnicodeScript.TAI_THAM, "Lana",
                Character.UnicodeScript.CANADIAN_ABORIGINAL, "Cans",
                Character.UnicodeScript.MIAO, "Plrd");
        for (var alias : aliases.entrySet())
            assertEquals(alias.getValue(), method.invoke(null, alias.getKey()),
                    "HarfBuzz needs the ISO 15924 tag for " + alias.getKey());
    }

    private static String fonts(ShapedLine line) {
        return line.glyphs().stream().map(PlacedGlyph::fontName).distinct().collect(Collectors.joining(","));
    }
}
