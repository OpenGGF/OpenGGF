package com.openggf.i18n;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class TestBundledUiCatalogs {
    @Test
    void mixedDirectionArgumentsAndPunctuationKeepTheirGlyphsAndBaseDirection() {
        var text = com.openggf.graphics.text.HostText.shared();
        var language = com.openggf.graphics.text.TextLanguage.of("ar");
        var line = text.shape("\u2068Sonic\u2069 فتح (إعدادات) / + ← →", com.openggf.graphics.text.TextFace.COMPACT, language);
        assertTrue(line.complete(), () -> line.missing().toString());
        assertTrue(line.rightToLeft(), "An isolated Latin argument must not choose the paragraph direction");
        assertTrue(text.shape("123", com.openggf.graphics.text.TextFace.COMPACT, language).rightToLeft(),
                "Neutral labels use the interface direction");
        assertEquals(0, text.shape("", com.openggf.graphics.text.TextFace.COMPACT, language).width(),
                "An empty RTL paragraph has no bidi runs to reorder");
    }
    @Test
    void bundledIndexLoadsTheCombinedInitialLanguageSetWithoutJavaRegistration() {
        var repository = CatalogRepository.bundled();
        Set<String> required = Set.of("en", "zh-Hans", "zh-Hant", "hi", "es", "es-419", "ar", "fr", "bn",
                "pt-BR", "pt-PT", "id", "ur", "ja", "it", "de", "ru", "pl", "ko", "th");
        var actual = repository.languages().stream().map(UiLanguage::tag).collect(Collectors.toSet());
        assertTrue(actual.containsAll(required), () -> "Missing initial languages: " + required.stream().filter(t -> !actual.contains(t)).toList());
        for (UiLanguage language : repository.languages()) {
            var view = repository.view(language.tag(), 0);
            assertFalse(view.text("language.picker.title").isBlank());
            assertFalse(view.text("settings.unsaved_count", Map.of("count", 2)).isBlank());
        }
    }

    @Test
    void everyResolvedMessageFormatsAndShapesWithoutMissingGlyphs() {
        var repository = CatalogRepository.bundled();
        var text = com.openggf.graphics.text.HostText.shared();
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (UiLanguage language : repository.languages()) {
            var view = repository.view(language.tag(), 0);
            var context = com.openggf.graphics.text.TextLanguage.of(view.locale());
            for (String id : repository.ids()) {
                java.util.Map<String, Object> arguments = new java.util.HashMap<>();
                repository.contract(id).forEach((name, kind) ->
                        arguments.put(name, kind == CatalogPattern.Kind.NUMBER ? 2 : "Sonic"));
                String message = view.text(id, arguments);
                for (String line : com.openggf.graphics.text.HostText.lines(message)) {
                    var shaped = text.shape(line, com.openggf.graphics.text.TextFace.COMPACT, context);
                    if (!shaped.complete() && missing.size() < 20)
                        missing.add(language.tag() + ": " + id + ": " + shaped.missing());
                }
            }
        }
        assertTrue(missing.isEmpty(), () -> "Unrenderable translated messages: " + missing);
    }
}
