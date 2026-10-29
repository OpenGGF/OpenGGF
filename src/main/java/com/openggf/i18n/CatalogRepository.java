package com.openggf.i18n;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.icu.util.LocaleMatcher;
import com.ibm.icu.util.ULocale;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Strict catalog boundary, independent of configuration, graphics and network services. */
final class CatalogRepository {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private record Source(String pattern, JsonNode metadata, String hash, CatalogPattern compiled) { }
    private record Pack(String tag, String name, String quality, List<String> parents,
                        Map<String, CatalogPattern> messages, int stale) { }
    private final Map<String, Source> source;
    private final Map<String, Pack> packs;
    private final List<UiLanguage> languages;

    private CatalogRepository(Map<String, Source> source, Map<String, Pack> packs) {
        this.source = Map.copyOf(source);
        this.packs = Map.copyOf(packs);
        List<UiLanguage> options = new ArrayList<>();
        options.add(new UiLanguage("en", "English", "source", source.size(), source.size(), 0));
        packs.values().stream().sorted(Comparator.comparing(Pack::tag)).forEach(pack ->
                options.add(new UiLanguage(pack.tag(), pack.name(), pack.quality(), pack.messages().size(),
                        source.size(), pack.stale())));
        languages = List.copyOf(options);
    }

    static CatalogRepository bundled() {
        ClassLoader loader = CatalogRepository.class.getClassLoader();
        try (InputStream index = loader.getResourceAsStream("i18n/catalogs.list")) {
            if (index == null) throw new IOException("Missing generated i18n/catalogs.list; run Maven compile");
            Map<String, String> documents = new TreeMap<>();
            for (String item : new String(index.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                String name = item.strip().replace('\\', '/');
                if (name.isEmpty()) continue;
                if (!(name.startsWith("source/") || name.startsWith("locales/")) || name.contains(".."))
                    throw new IOException("Invalid catalog index entry: " + name);
                try (InputStream resource = loader.getResourceAsStream("i18n/" + name)) {
                    if (resource == null) throw new IOException("Missing catalog: " + name);
                    documents.put(name, StandardCharsets.UTF_8.newDecoder().decode(
                            java.nio.ByteBuffer.wrap(resource.readAllBytes())).toString());
                }
            }
            return fromDocuments(documents);
        } catch (IOException e) { throw new UncheckedIOException("Unable to load UI catalogs", e); }
    }

    static CatalogRepository directory(Path root) throws IOException {
        Map<String, String> documents = new TreeMap<>();
        try (var files = Files.walk(root)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".arb")).sorted().toList())
                documents.put(root.relativize(path).toString().replace('\\', '/'), Files.readString(path));
        }
        return fromDocuments(documents);
    }

    static CatalogRepository fromDocuments(Map<String, String> documents) {
        Map<String, JsonNode> parsed = new TreeMap<>();
        documents.forEach((path, document) -> {
            try {
                JsonNode node = JSON.readTree(document);
                if (node == null || !node.isObject()) throw new IllegalArgumentException("Catalog must be an object: " + path);
                parsed.put(path, node);
            } catch (IOException e) { throw new IllegalArgumentException("Invalid JSON catalog " + path, e); }
        });
        Map<String, Source> source = new LinkedHashMap<>();
        parsed.forEach((path, document) -> {
            if (!path.startsWith("source/")) return;
            if (!document.path("@@locale").asText().equals("en"))
                throw new IllegalArgumentException("Source must be English: " + path);
            document.fields().forEachRemaining(entry -> {
                String id = entry.getKey();
                if (id.startsWith("@")) return;
                requireMessage(id, entry.getValue());
                JsonNode metadata = document.path("@" + id);
                String pattern = entry.getValue().asText();
                CatalogPattern compiled = checkedPattern(path, id, pattern, metadata, ULocale.ENGLISH, null);
                Source old = source.putIfAbsent(id, new Source(pattern, metadata, fingerprint(pattern, metadata), compiled));
                if (old != null) throw new IllegalArgumentException("Duplicate source message " + id);
            });
        });
        if (source.isEmpty()) throw new IllegalArgumentException("No English source messages");
        Map<String, Pack> packs = new LinkedHashMap<>();
        parsed.forEach((path, document) -> {
            if (!path.startsWith("locales/")) return;
            String tag = canonicalTag(document.path("@@locale").asText());
            if (tag.equals("en") || !path.equals("locales/" + tag + ".arb"))
                throw new IllegalArgumentException("Locale tag must match filename: " + path);
            List<String> parents = new ArrayList<>();
            JsonNode parentNode = document.path("@@parents");
            if (!parentNode.isMissingNode() && !parentNode.isArray()) throw new IllegalArgumentException("@@parents must be an array: " + path);
            parentNode.forEach(parent -> parents.add(canonicalTag(parent.asText())));
            if (!parents.contains("en")) parents.add("en");
            Map<String, CatalogPattern> messages = new LinkedHashMap<>();
            int[] stale = {0};
            document.fields().forEachRemaining(entry -> {
                String id = entry.getKey();
                if (id.startsWith("@")) return;
                Source original = source.get(id);
                if (original == null) throw new IllegalArgumentException("Unknown message " + id + " in " + path);
                requireMessage(id, entry.getValue());
                String pattern = entry.getValue().asText();
                if (pattern.isEmpty()) return;
                CatalogPattern compiled = checkedPattern(path, id, pattern, JSON.createObjectNode(),
                        ULocale.forLanguageTag(tag), original.compiled().arguments());
                try { compiled.validateTranslationOf(original.compiled()); }
                catch (IllegalArgumentException e) { throw new IllegalArgumentException(path + ": " + id + ": " + e.getMessage(), e); }
                JsonNode stamp = document.path("@" + id).path("x-openggf");
                if (!original.hash().equals(stamp.path("sourceHash").asText())) { stale[0]++; return; }
                if (pattern.equals(original.pattern())) return; // English copies do not inflate translated coverage.
                boolean critical = original.metadata().path("x-openggf").path("critical").asBoolean(false);
                boolean reviewed = stamp.path("reviewed").asBoolean(false)
                        && hash(pattern).equals(stamp.path("translationHash").asText());
                if (!critical || reviewed) messages.put(id, compiled);
            });
            Pack pack = new Pack(tag, document.path("@@name").asText(tag),
                    document.path("@@quality").asText("community draft"), List.copyOf(parents), Map.copyOf(messages), stale[0]);
            if (packs.putIfAbsent(tag, pack) != null) throw new IllegalArgumentException("Duplicate locale " + tag);
        });
        for (Pack pack : packs.values()) validateParents(pack.tag(), packs, new HashSet<>());
        return new CatalogRepository(source, packs);
    }

    private static void requireMessage(String id, JsonNode value) {
        if (!id.matches("[A-Za-z][A-Za-z0-9_.-]*") || !value.isTextual()) throw new IllegalArgumentException("Invalid message " + id);
    }

    private static CatalogPattern checkedPattern(String path, String id, String pattern, JsonNode metadata,
                                                  ULocale locale, Map<String, CatalogPattern.Kind> contract) {
        try { return new CatalogPattern(pattern, metadata, locale, contract); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException(path + ": " + id + ": " + e.getMessage(), e); }
    }

    private static void validateParents(String tag, Map<String, Pack> packs, Set<String> seen) {
        if (tag.equals("en")) return;
        if (!seen.add(tag)) throw new IllegalArgumentException("Locale fallback cycle: " + seen);
        Pack pack = packs.get(tag);
        if (pack == null) throw new IllegalArgumentException("Missing fallback locale " + tag);
        for (String parent : pack.parents()) {
            if (!parent.equals("en") && (!Locale.forLanguageTag(parent).getLanguage().equals(
                    Locale.forLanguageTag(tag).getLanguage()) || !script(parent).equals(script(tag))))
                throw new IllegalArgumentException("Fallback crosses language/script: " + tag + " -> " + parent);
            validateParents(parent, packs, new HashSet<>(seen));
        }
    }

    LocaleView view(String requested, long revision) {
        String tag = negotiate(requested);
        Map<String, CatalogPattern> resolved = new LinkedHashMap<>();
        source.forEach((id, original) -> resolved.put(id, resolve(tag, id)));
        return new LocaleView(Locale.forLanguageTag(tag), revision, resolved);
    }

    private CatalogPattern resolve(String tag, String id) {
        if (tag.equals("en")) return source.get(id).compiled();
        Pack pack = packs.get(tag);
        CatalogPattern local = pack.messages().get(id);
        if (local != null) return local;
        for (String parent : pack.parents()) {
            if (!parent.equals("en") && hasMessage(parent, id)) return resolve(parent, id);
        }
        return source.get(id).compiled();
    }

    private boolean hasMessage(String tag, String id) {
        if (tag.equals("en")) return true;
        Pack pack = packs.get(tag);
        return pack.messages().containsKey(id) || pack.parents().stream().filter(p -> !p.equals("en")).anyMatch(p -> hasMessage(p, id));
    }

    private String negotiate(String requested) {
        String tag;
        try { tag = canonicalTag(requested); } catch (IllegalArgumentException e) { return "en"; }
        if (tag.equals("en") || packs.containsKey(tag)) return tag;
        ULocale wanted = ULocale.forLanguageTag(tag);
        List<ULocale> candidates = packs.keySet().stream()
                .filter(candidate -> Locale.forLanguageTag(candidate).getLanguage().equals(wanted.getLanguage()))
                .filter(candidate -> script(candidate).equals(script(tag)))
                .sorted().map(ULocale::forLanguageTag).toList();
        if (candidates.isEmpty()) return "en";
        return LocaleMatcher.builder().setSupportedULocales(candidates).build().getBestMatch(wanted).toLanguageTag();
    }

    static String canonicalTag(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing language tag");
        Locale locale;
        try { locale = new Locale.Builder().setLanguageTag(value.strip()).build(); }
        catch (IllformedLocaleException e) { throw new IllegalArgumentException("Invalid language tag: " + value, e); }
        if (locale.getLanguage().isEmpty() || locale.hasExtensions() || locale.getLanguage().equals("und"))
            throw new IllegalArgumentException("Unsupported language tag: " + value);
        return locale.toLanguageTag();
    }

    private static String script(String tag) { return ULocale.addLikelySubtags(ULocale.forLanguageTag(tag)).getScript(); }
    String sourceHash(String id) {
        Source item = source.get(id);
        if (item == null) throw new IllegalArgumentException("Unknown message " + id);
        return item.hash();
    }
    Set<String> ids() { return source.keySet(); }
    Map<String, CatalogPattern.Kind> contract(String id) { return source.get(id).compiled().arguments(); }
    List<UiLanguage> languages() { return languages; }

    static String fingerprint(String pattern, JsonNode metadata) {
        List<String> parts = new ArrayList<>();
        metadata.path("placeholders").fields().forEachRemaining(e -> parts.add(e.getKey() + ":" + e.getValue().path("type").asText("String")));
        parts.sort(String::compareTo);
        return hash(pattern + "\n" + metadata.path("description").asText() + "\n" + String.join("\n", parts));
    }
    static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
