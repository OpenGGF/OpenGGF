package com.openggf.mods;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openggf.io.ModInputLimits;
import com.openggf.game.ModKeySyntax;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CollectionStartEvent;
import org.yaml.snakeyaml.events.CollectionEndEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

public final class ModManifestParser {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "formatVersion", "id", "name", "version", "authors", "description",
            "engineApiRange", "type", "baseGame", "entrypoint", "dependencies",
            "audioOverrides", "artOverrides", "insertAfter", "patternWindows");
    private static final Set<String> DEPENDENCY_FIELDS = Set.of("id", "versionRange");
    private static final Pattern CANONICAL_NONNEGATIVE_INT = Pattern.compile("0|[1-9][0-9]*");
    private static final Pattern NUMERIC_TOKEN = Pattern.compile(
            "[+-]?(?:0[xX][0-9A-Fa-f_]+|0[oO][0-7_]+|0[bB][01_]+|"
                    + "(?:[0-9][0-9_]*|\\.[0-9_]+)(?:\\.[0-9_]*)?(?:[eE][+-]?[0-9_]+)?)");

    private final ModInputLimits limits;
    private final LoaderOptions loaderOptions;
    private final ObjectMapper mapper;

    public ModManifestParser() {
        this(ModInputLimits.production());
    }

    public ModManifestParser(ModInputLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        loaderOptions = loaderOptions(limits);
        StreamReadConstraints readConstraints = StreamReadConstraints.builder()
                .maxNestingDepth(limits.maxYamlDepth())
                .maxDocumentLength(limits.maxDocumentCodePoints())
                .maxTokenCount(Math.max(128L, limits.maxCollectionEntries() * 8L))
                .maxStringLength(limits.maxStringChars())
                .maxNameLength(limits.maxStringChars())
                .maxNumberLength(limits.maxNumericDigits())
                .build();
        YAMLFactory factory = YAMLFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .loaderOptions(loaderOptions)
                .streamReadConstraints(readConstraints)
                .build();
        mapper = new ObjectMapper(factory);
    }

    public ModManifest parse(byte[] yamlBytes) throws ModManifestException {
        Objects.requireNonNull(yamlBytes, "yamlBytes");
        if (yamlBytes.length > limits.maxMetadataBytes()) {
            throw new ModManifestException("Manifest exceeds metadata byte limit");
        }
        try {
            String yaml = decodeUtf8(yamlBytes);
            preflightYaml(yaml);
            JsonNode root;
            try (JsonParser parser = mapper.createParser(yamlBytes)) {
                root = mapper.readTree(parser);
                if (parser.nextToken() != null) {
                    throw new ModManifestException("Trailing YAML document or token is not allowed");
                }
            }
            if (root == null || !root.isObject()) {
                throw new ModManifestException("Manifest root must be a mapping");
            }
            validateTreeLimits(root);
            return toManifest(root);
        } catch (ModManifestException ex) {
            throw ex;
        } catch (IllegalArgumentException | CharacterCodingException ex) {
            throw new ModManifestException("Invalid mod manifest: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ModManifestException("Unable to parse mod manifest: " + ex.getMessage(), ex);
        }
    }

    private ModManifest toManifest(JsonNode root) throws ModManifestException {
        requireOnlyFields(root, ROOT_FIELDS, "manifest");
        int formatVersion = requiredInt(root, "formatVersion");
        if (formatVersion != 1) {
            throw new ModManifestException("Unsupported manifest formatVersion: " + formatVersion);
        }
        String id = ModKeySyntax.requireManifestId(requiredText(root, "id"));
        String name = requiredNonblankText(root, "name");
        SemanticVersion version = SemanticVersion.parse(requiredText(root, "version"));
        List<String> authors = parseAuthors(required(root, "authors"));
        String description = requiredNonblankText(root, "description");
        VersionRange engineApiRange = VersionRange.parse(requiredText(root, "engineApiRange"));
        ModType type = parseType(requiredText(root, "type"));
        String baseGame = optionalText(root, "baseGame");
        String entrypoint = optionalText(root, "entrypoint");
        List<ModDependency> dependencies = parseDependencies(required(root, "dependencies"));
        Map<Integer, String> audioOverrides = parseAudioOverrides(required(root, "audioOverrides"));
        Map<String, String> artOverrides = parseArtOverrides(required(root, "artOverrides"));
        String insertAfter = optionalText(root, "insertAfter");
        OptionalInt patternWindows = parsePatternWindows(root);
        return new ModManifest(formatVersion, id, name, version, authors, description,
                engineApiRange, type, baseGame, entrypoint, dependencies, audioOverrides,
                artOverrides, insertAfter, patternWindows);
    }

    private List<String> parseAuthors(JsonNode node) throws ModManifestException {
        if (!node.isArray() || node.size() < 1 || node.size() > 32) {
            throw new ModManifestException("authors must contain 1 through 32 entries");
        }
        List<String> authors = new ArrayList<>(node.size());
        for (JsonNode author : node) {
            authors.add(nonblankText(author, "author"));
        }
        return List.copyOf(authors);
    }

    private List<ModDependency> parseDependencies(JsonNode node) throws ModManifestException {
        if (!node.isArray()) {
            throw new ModManifestException("dependencies must be a sequence of mappings");
        }
        List<ModDependency> dependencies = new ArrayList<>(node.size());
        Set<String> ids = new HashSet<>();
        for (JsonNode dependency : node) {
            if (!dependency.isObject()) {
                throw new ModManifestException("Each dependency must be a mapping");
            }
            requireOnlyFields(dependency, DEPENDENCY_FIELDS, "dependency");
            String dependencyId = ModKeySyntax.requireManifestId(requiredText(dependency, "id"));
            if (!ids.add(dependencyId)) {
                throw new ModManifestException("Duplicate dependency id: " + dependencyId);
            }
            dependencies.add(new ModDependency(dependencyId,
                    VersionRange.parse(requiredText(dependency, "versionRange"))));
        }
        return List.copyOf(dependencies);
    }

    private Map<Integer, String> parseAudioOverrides(JsonNode node) throws ModManifestException {
        if (!node.isObject()) {
            throw new ModManifestException("audioOverrides must be an explicit mapping");
        }
        Map<Integer, String> overrides = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!CANONICAL_NONNEGATIVE_INT.matcher(field.getKey()).matches()) {
                throw new ModManifestException("audioOverrides keys must be canonical nonnegative integers");
            }
            int musicId;
            try {
                musicId = Integer.parseInt(field.getKey());
            } catch (NumberFormatException ex) {
                throw new ModManifestException("audioOverrides key exceeds integer range", ex);
            }
            String localName = ModKeySyntax.requireLocalName(text(field.getValue(), "audio override value"));
            overrides.put(musicId, localName);
        }
        return Map.copyOf(overrides);
    }

    private Map<String, String> parseArtOverrides(JsonNode node) throws ModManifestException {
        if (!node.isObject()) {
            throw new ModManifestException("artOverrides must be an explicit mapping");
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getKey().isBlank()) {
                throw new ModManifestException("artOverrides keys must be nonblank");
            }
            String path = text(field.getValue(), "art override path");
            String normalized = ModManifest.requireArtPath(path, limits.maxEntryNameBytes());
            overrides.put(field.getKey(), normalized);
        }
        return Map.copyOf(overrides);
    }

    private OptionalInt parsePatternWindows(JsonNode root) throws ModManifestException {
        JsonNode node = root.get("patternWindows");
        if (node == null) {
            return OptionalInt.empty();
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new ModManifestException("patternWindows must be an integer");
        }
        int value = node.intValue();
        if (value < 1 || value > 16) {
            throw new ModManifestException("patternWindows must be in 1..16");
        }
        return OptionalInt.of(value);
    }

    private void validateTreeLimits(JsonNode root) throws ModManifestException {
        validateCollectionSizes(root);
    }

    private void validateCollectionSizes(JsonNode node) throws ModManifestException {
        if (node.isContainerNode() && node.size() > limits.maxCollectionEntries()) {
            throw new ModManifestException("Manifest exceeds collection-entry limit");
        }
        for (JsonNode child : node) {
            validateCollectionSizes(child);
        }
    }

    private void preflightYaml(String yaml) throws ModManifestException {
        int documents = 0;
        Deque<CollectionFrame> collections = new ArrayDeque<>();
        try {
            for (Event event : new Yaml(loaderOptions).parse(new StringReader(yaml))) {
                if (event instanceof DocumentStartEvent && ++documents > 1) {
                    throw new ModManifestException("Trailing YAML documents are not allowed");
                }
                if (event instanceof AliasEvent) {
                    throw new ModManifestException("YAML aliases are not allowed");
                }
                if (event instanceof CollectionStartEvent collection) {
                    if (collection.getAnchor() != null) {
                        throw new ModManifestException("YAML anchors are not allowed");
                    }
                    incrementParent(collections);
                    collections.push(new CollectionFrame(collection instanceof MappingStartEvent));
                    if (collections.size() > limits.maxYamlDepth()) {
                        throw new ModManifestException("YAML exceeds nesting-depth limit");
                    }
                    if (collection.getTag() != null) {
                        throw new ModManifestException("Explicit YAML tags are not allowed");
                    }
                    continue;
                }
                if (event instanceof CollectionEndEvent) {
                    CollectionFrame completed = collections.pop();
                    int entries = completed.mapping ? completed.childNodes / 2 : completed.childNodes;
                    if (entries > limits.maxCollectionEntries()) {
                        throw new ModManifestException("YAML exceeds collection-entry limit");
                    }
                    continue;
                }
                if (event instanceof NodeEvent node && node.getAnchor() != null) {
                    throw new ModManifestException("YAML anchors are not allowed");
                }
                if (event instanceof ScalarEvent scalar) {
                    incrementParent(collections);
                    if (scalar.getTag() != null) {
                        throw new ModManifestException("Explicit YAML tags are not allowed");
                    }
                    if (scalar.isPlain() && scalar.getValue().equals("<<")) {
                        throw new ModManifestException("YAML merge keys are not allowed");
                    }
                    if (scalar.getValue().length() > limits.maxStringChars()) {
                        throw new ModManifestException("YAML scalar exceeds string limit");
                    }
                    if (NUMERIC_TOKEN.matcher(scalar.getValue()).matches()
                            && numericDigits(scalar.getValue()) > limits.maxNumericDigits()) {
                        throw new ModManifestException("YAML numeric token exceeds digit limit");
                    }
                }
            }
        } catch (ModManifestException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ModManifestException("Invalid bounded YAML: " + ex.getMessage(), ex);
        }
    }

    private static void incrementParent(Deque<CollectionFrame> collections) {
        if (!collections.isEmpty()) {
            collections.peek().childNodes++;
        }
    }

    private static int numericDigits(String token) {
        int count = 0;
        boolean hexadecimal = token.indexOf('x') >= 0 || token.indexOf('X') >= 0;
        for (int index = 0; index < token.length(); index++) {
            char value = token.charAt(index);
            if (Character.isDigit(value)
                    || (hexadecimal
                    && ((value >= 'a' && value <= 'f') || (value >= 'A' && value <= 'F')))) {
                count++;
            }
        }
        return count;
    }

    private static final class CollectionFrame {
        private final boolean mapping;
        private int childNodes;

        private CollectionFrame(boolean mapping) {
            this.mapping = mapping;
        }
    }

    private static LoaderOptions loaderOptions(ModInputLimits limits) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setWarnOnDuplicateKeys(false);
        options.setMaxAliasesForCollections(limits.maxYamlAliases());
        options.setNestingDepthLimit(limits.maxYamlDepth());
        options.setCodePointLimit(limits.maxDocumentCodePoints());
        options.setMergeOnCompose(false);
        options.setAllowRecursiveKeys(false);
        return options;
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static void requireOnlyFields(JsonNode node, Set<String> allowed, String context)
            throws ModManifestException {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.equals("<<") || !allowed.contains(name)) {
                throw new ModManifestException("Unknown " + context + " field: " + name);
            }
        }
    }

    private static JsonNode required(JsonNode object, String field) throws ModManifestException {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            throw new ModManifestException("Missing or null required field: " + field);
        }
        return value;
    }

    private static String requiredText(JsonNode object, String field) throws ModManifestException {
        return text(required(object, field), field);
    }

    private static String requiredNonblankText(JsonNode object, String field) throws ModManifestException {
        return nonblankText(required(object, field), field);
    }

    private static String optionalText(JsonNode object, String field) throws ModManifestException {
        JsonNode value = object.get(field);
        if (value == null) {
            return null;
        }
        if (value.isNull()) {
            throw new ModManifestException("Optional field must be absent rather than null: " + field);
        }
        return nonblankText(value, field);
    }

    private static String nonblankText(JsonNode node, String context) throws ModManifestException {
        String value = text(node, context);
        if (value.isBlank()) {
            throw new ModManifestException(context + " must be nonblank");
        }
        return value;
    }

    private static String text(JsonNode node, String context) throws ModManifestException {
        if (!node.isTextual()) {
            throw new ModManifestException(context + " must be a string");
        }
        return node.textValue();
    }

    private static int requiredInt(JsonNode object, String field) throws ModManifestException {
        JsonNode node = required(object, field);
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new ModManifestException(field + " must be an integer");
        }
        return node.intValue();
    }

    private static ModType parseType(String text) throws ModManifestException {
        return switch (text) {
            case "patch" -> ModType.PATCH;
            case "standalone" -> ModType.STANDALONE;
            default -> throw new ModManifestException("type must be patch or standalone");
        };
    }

}
