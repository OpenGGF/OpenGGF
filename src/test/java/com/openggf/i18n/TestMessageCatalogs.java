package com.openggf.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestMessageCatalogs {
    private static final String SOURCE = """
            {"@@locale":"en", "item.count":"{count, plural, one {# item} other {# items}}",
             "@item.count":{"description":"Number of selected items","placeholders":{"count":{"type":"int"}}},
             "action.save":"Save", "fallback.count":"{count, plural, one {one fallback} other {many fallbacks}}"}
            """;

    private CatalogRepository source() {
        return CatalogRepository.fromDocuments(Map.of("source/core.arb", SOURCE));
    }

    @Test
    void formatsWholeMessagesAndRejectsWrongCallerArguments() {
        var english = source().view("en", 0);
        assertEquals("1 item", english.text("item.count", Map.of("count", 1)));
        assertEquals("2 items", english.text("item.count", Map.of("count", 2)));
        assertThrows(IllegalArgumentException.class, () -> english.text("item.count"));
        assertThrows(IllegalArgumentException.class,
                () -> english.text("item.count", Map.of("count", "2")));
        assertThrows(IllegalArgumentException.class,
                () -> english.text("action.save", Map.of("unused", 1)));
    }

    @Test
    void criticalTextNeedsCurrentSourceAndExactReviewedTranslation() throws Exception {
        String source = """
                {"@@locale":"en", "legal.notice":"Canonical terms",
                 "@legal.notice":{"x-openggf":{"critical":true}}}
                """;
        String sourceHash = CatalogRepository.fromDocuments(Map.of("source/core.arb", source))
                .sourceHash("legal.notice");
        String reviewHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("Conditions relues".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String target = """
                {"@@locale":"fr", "@@quality":"machine", "legal.notice":"Conditions relues",
                 "@legal.notice":{"x-openggf":{"sourceHash":"%s", "origin":"claude-haiku-5-5",
                 "reviewed":%s, "translationHash":"%s"}}}
                """;
        String draft = target.formatted(sourceHash, false, reviewHash);
        String reviewed = target.formatted(sourceHash, true, reviewHash);
        assertEquals("Canonical terms", CatalogRepository.fromDocuments(Map.of("source/core.arb", source,
                "locales/fr.arb", draft)).view("fr", 0).text("legal.notice"));
        assertEquals("Conditions relues", CatalogRepository.fromDocuments(Map.of("source/core.arb", source,
                "locales/fr.arb", reviewed)).view("fr", 0).text("legal.notice"));
        assertEquals("Canonical terms", CatalogRepository.fromDocuments(Map.of("source/core.arb", source,
                "locales/fr.arb", reviewed.replace("Conditions relues", "Conditions changées")))
                .view("fr", 0).text("legal.notice"));
        assertEquals("Updated canonical terms", CatalogRepository.fromDocuments(Map.of(
                "source/core.arb", source.replace("Canonical terms", "Updated canonical terms"),
                "locales/fr.arb", reviewed)).view("fr", 0).text("legal.notice"));
    }

    @Test
    void rejectsDuplicateKeysAndRenamedArgumentsAtCatalogBoundary() {
        assertThrows(IllegalArgumentException.class, () -> CatalogRepository.fromDocuments(
                Map.of("source/core.arb", "{\"@@locale\":\"en\",\"save\":\"Save\",\"save\":\"Again\"}")));
        assertThrows(IllegalArgumentException.class, () -> CatalogRepository.fromDocuments(Map.of(
                "source/core.arb", SOURCE,
                "locales/fr.arb", "{\"@@locale\":\"fr\",\"item.count\":\"{renamed} articles\"}")));
    }

    @Test
    void targetPluralRulesAndEnglishFallbackKeepTheirOwnLocale() {
        var base = source();
        String hash = base.sourceHash("item.count");
        String arabic = """
                {"@@locale":"ar","@@name":"العربية","@@quality":"machine",
                 "item.count":"{count, plural, zero {لا عناصر} one {عنصر} two {عنصران} few {عناصر قليلة} many {عناصر كثيرة} other {عناصر}}",
                 "@item.count":{"x-openggf":{"sourceHash":"%s","origin":"machine"}}}
                """.formatted(hash);
        var repository = CatalogRepository.fromDocuments(Map.of("source/core.arb", SOURCE,
                "locales/ar.arb", arabic));
        var view = repository.view("ar", 7);
        assertEquals("عنصران", view.text("item.count", Map.of("count", 2)));
        assertEquals("many fallbacks", view.text("fallback.count", Map.of("count", 2)));
        assertEquals(7, view.revision());
        assertEquals("ar", view.locale().getLanguage());
        assertEquals(1, repository.languages().stream().filter(l -> l.tag().equals("ar"))
                .findFirst().orElseThrow().translated());
    }

    @Test
    void sourceChangesMakeTranslationsStaleInsteadOfReusingOldMeaning() {
        String french = """
                {"@@locale":"fr","action.save":"Enregistrer",
                 "@action.save":{"x-openggf":{"sourceHash":"%s","origin":"human"}}}
                """.formatted(source().sourceHash("action.save"));
        var repository = CatalogRepository.fromDocuments(Map.of("source/core.arb",
                SOURCE.replace("\"Save\"", "\"Save all\""), "locales/fr.arb", french));
        assertEquals("Save all", repository.view("fr", 0).text("action.save"));
        assertEquals(1, repository.languages().stream().filter(l -> l.tag().equals("fr"))
                .findFirst().orElseThrow().stale());
    }

    @Test
    void discoveryNeedsOnlyCatalogDataAndNeverCrossesAnExplicitScript() {
        var repository = CatalogRepository.fromDocuments(Map.of("source/core.arb", SOURCE,
                "locales/zh-Hans.arb", "{\"@@locale\":\"zh-Hans\",\"@@name\":\"简体中文\"}",
                "locales/eo.arb", "{\"@@locale\":\"eo\",\"@@name\":\"Esperanto\"}"));
        assertEquals("eo", repository.view("eo", 0).locale().toLanguageTag());
        assertEquals("zh-Hans", repository.view("zh-CN", 0).locale().toLanguageTag());
        assertEquals("en", repository.view("zh-Hant", 0).locale().toLanguageTag());
        assertEquals("en", repository.view("not_a_tag", 0).locale().toLanguageTag());
        assertEquals("en", repository.view("zz", 0).locale().toLanguageTag());
    }

    @Test
    void formattingDoesNotChangeMachineDefaultLocale() {
        Locale before = Locale.getDefault();
        source().view("en", 0).text("item.count", Map.of("count", 1234));
        assertEquals(before, Locale.getDefault());
    }

    @Test
    void rejectsChangedSemanticSelectKeysEvenInAnUnstampedDraft() {
        String source = "{\"@@locale\":\"en\",\"character\":\"{actor, select, sonic {Sonic} tails {Tails} other {Player}}\"}";
        String target = "{\"@@locale\":\"fr\",\"character\":\"{actor, select, sonik {Sonic} tails {Tails} other {Joueur}}\"}";
        assertThrows(IllegalArgumentException.class, () -> CatalogRepository.fromDocuments(
                Map.of("source/core.arb", source, "locales/fr.arb", target)));
    }
    @Test
    void rightToLeftCreatorTextCannotChooseAnEnglishMessageDirection() {
        var repository = CatalogRepository.fromDocuments(Map.of(
                "source/core.arb", "{\"@@locale\":\"en\",\"creator\":\"{name} in this library\"}",
                "locales/ar.arb", "{\"@@locale\":\"ar\",\"@@name\":\"العربية\"}"));
        for (String tag : java.util.List.of("en", "ar")) {
            String value = repository.view(tag, 0).text("creator", Map.of("name", "مرحبا"));
            assertTrue(value.startsWith("\u2068مرحبا\u2069"));
            var line = com.openggf.graphics.text.HostText.shared().shape(value,
                    com.openggf.graphics.text.TextFace.COMPACT,
                    com.openggf.graphics.text.TextLanguage.of(tag));
            assertFalse(line.rightToLeft(), "English fallback keeps its own paragraph direction");
        }
    }
}
