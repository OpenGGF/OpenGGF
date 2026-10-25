package com.openggf.net.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TestProfanityFilter {
    @Test
    void masksWholeTokensFromEveryStarterPackRegardlessOfCaseOrWidth() {
        for (String word : new String[] { "FUCK", "ＦＵＣＫ", "cunt", "merde", "mierda", "scheiße", "scheisse",
                "vaffanculo", "caralho", "kurwa", "блядь", "siktir", "くたばれ", "씨발", "傻逼" }) {
            assertEquals("***", ProfanityFilter.censor(word), word);
        }
    }

    @Test
    void preservesBenignSubstringsNamesAndAmbiguousShortWordsExactly() {
        String benign = "Scunthorpe assassin classic Dickinson O’Connor con pan bitte I am ready ass dick";
        assertSame(benign, ProfanityFilter.censor(benign));
        assertEquals("fuckery afuck fuck123 fuck_name", ProfanityFilter.censor("fuckery afuck fuck123 fuck_name"));
    }

    @Test
    void masksOriginalCodePointSpansWithoutChangingEmojiPunctuationOrUnmatchedNormalization() {
        String text = "😀ＦＵＣＫ! cafe\u0301 / encule\u0301 🏁 mierDA.";
        assertEquals("😀***! cafe\u0301 / *** 🏁 ***.", ProfanityFilter.censor(text));
        assertEquals("‘***’ / '***'", ProfanityFilter.censor("‘FUCK’ / 'merde'"));
    }

    @Test
    void keepsAccentsCombiningMarksAndInternalApostrophesSignificant() {
        String unchanged = "encule encule\u0300 fúck f'uck f’uck f＇uck O'Connor O’Connor";
        assertSame(unchanged, ProfanityFilter.censor(unchanged));
        assertEquals("***", ProfanityFilter.censor("encule\u0301"));
        assertEquals("fuck\u0301", ProfanityFilter.censor("fuck\u0301"));
    }

    @Test
    @ResourceLock("java.util.Locale.default")
    void lowercasingAlwaysUsesRootLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("*** ***", ProfanityFilter.censor("SIKTIR FUCKING"));
            assertEquals("SİKTİR", ProfanityFilter.censor("SİKTİR"), "combining dots are not stripped");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void documentsConservativeWholeRunAndEvasionLimits() {
        String evasions = "你是傻逼啊 씨발이야 f.u.c.k f4ck fuсk"; // Last c is Cyrillic.
        assertSame(evasions, ProfanityFilter.censor(evasions));
        assertEquals("你是 *** 啊", ProfanityFilter.censor("你是 傻逼 啊"));
    }

    @Test
    void dictionaryStaysWithinBudgetHasUniqueNormalizedKeysAndOnlyReachableEntries() throws Exception {
        byte[] data;
        try (var stream = getClass().getResourceAsStream("/net/chat/profanity-starter.txt")) {
            assertNotNull(stream);
            data = stream.readAllBytes();
        }
        assertTrue(data.length <= 64 * 1024, "UTF-8 resource budget");
        var keys = new HashSet<String>();
        for (String line : new String(data, StandardCharsets.UTF_8).lines().toList()) {
            String word = line.strip();
            if (word.isEmpty() || word.startsWith("#")) continue;
            String normalized = Normalizer.normalize(Normalizer.normalize(word, Normalizer.Form.NFKC)
                    .toLowerCase(Locale.ROOT), Normalizer.Form.NFKC);
            assertTrue(keys.add(normalized), "duplicate normalized key: " + word);
            assertEquals("***", ProfanityFilter.censor(word), "unreachable dictionary entry: " + word);
        }
        assertFalse(keys.isEmpty());
        assertTrue(keys.size() <= 1024, "normalized key budget");
        for (String ambiguous : new String[] { "con", "bitte", "am", "ass", "dick" }) {
            assertFalse(keys.contains(ambiguous), ambiguous);
        }
    }

    @Test
    void emptyAndLongInputsRemainBoundedAndFilteringIsIdempotent() {
        assertEquals("", ProfanityFilter.censor(""));
        assertThrows(NullPointerException.class, () -> ProfanityFilter.censor(null));
        String text = "😀 merde! ".repeat(500);
        String filtered = ProfanityFilter.censor(text);
        assertEquals("😀 ***! ".repeat(500), filtered);
        assertSame(filtered, ProfanityFilter.censor(filtered));
    }
}
