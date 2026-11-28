package com.openggf.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The setup guide's translated corpus has a current (non-stale) translation in every shipped locale,
 * directly or through a declared same-language parent. Newly authored source-only copy is tracked separately
 * while it uses the standard fallback. Coverage is structural only: the drafts are
 * machine translations with {@code reviewed: false}; this does not certify fluency or native review.
 */
class TestSetupGuideLocaleCompleteness {
    private static final Path ROOT = Path.of("src/main/resources/i18n");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ID = Pattern.compile("\"((?:menu|hub|settings|launch|crash|editor)\\.[A-Za-z0-9_.]+)\"");

    // New source-only copy uses the catalog's English fallback until drafts are available.
    private static final Set<String> SOURCE_ONLY = Set.of(
            "menu.setup.games.unverified", "menu.setup.games.unverified_help",
            "menu.onboarding.compressed_archive", "menu.onboarding.interleaved_smd");

    static TreeSet<String> guideMessageIds() throws Exception {
        TreeSet<String> ids = new TreeSet<>();
        Matcher m = ID.matcher(Files.readString(Path.of("src/main/java/com/openggf/game/MenuSetupGuide.java")));
        while (m.find()) ids.add(m.group(1));
        ids.addAll(SOURCE_ONLY);
        ids.add("menu.setup.extras.entry");
        ids.add("settings.key.setup_guide.label");
        ids.add("settings.key.setup_guide.help");
        return ids;
    }

    private static Map<String, JsonNode> source() throws Exception {
        Map<String, JsonNode> source = new HashMap<>();
        try (Stream<Path> files = Files.list(ROOT.resolve("source"))) {
            for (Path file : files.toList()) {
                JsonNode doc = JSON.readTree(file.toFile());
                doc.fieldNames().forEachRemaining(name -> { if (!name.startsWith("@")) source.put(name, doc); });
            }
        }
        return source;
    }

    private static boolean current(Map<String, JsonNode> locales, String tag, String id, Map<String, JsonNode> source) {
        JsonNode doc = locales.get(tag);
        if (doc == null) return false;
        JsonNode value = doc.get(id);
        if (value != null && !value.asText().isBlank()) {
            JsonNode sourceDoc = source.get(id);
            String expected = CatalogRepository.fingerprint(sourceDoc.get(id).asText(), sourceDoc.path("@" + id));
            if (expected.equals(doc.path("@" + id).path("x-openggf").path("sourceHash").asText())) return true;
        }
        for (JsonNode parent : doc.path("@@parents"))
            if (!parent.asText().equals("en") && current(locales, parent.asText(), id, source)) return true;
        return false;
    }

    @Test void everyTranslatedGuideMessageIsCurrentInEveryShippedLocale() throws Exception {
        Map<String, JsonNode> source = source();
        TreeSet<String> ids = guideMessageIds();
        assertTrue(ids.size() > 100, "the guide's messages were found: " + ids.size());
        for (String id : ids) assertTrue(source.containsKey(id), "unknown message " + id);
        Map<String, JsonNode> locales = new HashMap<>();
        try (Stream<Path> files = Files.list(ROOT.resolve("locales"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".arb")).toList())
                locales.put(file.getFileName().toString().replace(".arb", ""), JSON.readTree(file.toFile()));
        }
        assertEquals(19, locales.size(), "shipped locale catalogs");
        List<String> missing = new ArrayList<>();
        for (String tag : new TreeSet<>(locales.keySet()))
            for (String id : ids) if (!SOURCE_ONLY.contains(id) && !current(locales, tag, id, source)) missing.add(tag + ": " + id);
        assertTrue(missing.isEmpty(), "falls back to English: " + missing);
    }

    @Test void theNewSourceCopyResolvesInEveryShippedLocale() {
        var repository = CatalogRepository.bundled();
        var release = Map.of("release", "Sonic 2 World REV01");
        for (var language : repository.languages()) {
            var view = repository.view(language.tag(), 0);
            assertEquals("Unrecognised version", view.text("menu.setup.games.unverified"), language.tag());
            assertEquals("This version may not work. Try Sonic 2 World REV01.",
                    view.text("menu.setup.games.unverified_help", release), language.tag());
            assertTrue(view.text("menu.onboarding.compressed_archive").contains(".gen"), language.tag());
            assertTrue(view.text("menu.onboarding.interleaved_smd").contains(".bin"), language.tag());
        }
    }

    @Test void theGuidesDraftsAreMarkedAsUnreviewedMachineDrafts() throws Exception {
        List<String> wrong = new ArrayList<>();
        try (Stream<Path> files = Files.list(ROOT.resolve("locales"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".arb")).toList()) {
                JsonNode doc = JSON.readTree(file.toFile());
                doc.fieldNames().forEachRemaining(name -> {
                    if (!name.startsWith("menu.setup.") && !name.startsWith("settings.key.setup_guide.")) return;
                    JsonNode stamp = doc.path("@" + name).path("x-openggf");
                    if (!stamp.path("origin").asText().equals("claude-opus-5-5") || stamp.path("reviewed").asBoolean(true))
                        wrong.add(file.getFileName() + ": " + name);
                });
            }
        }
        assertTrue(wrong.isEmpty(), "drafts must not claim review: " + wrong);
    }
}
