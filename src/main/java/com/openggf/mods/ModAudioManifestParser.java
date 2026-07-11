package com.openggf.mods;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.openggf.io.ModInputLimits;
import com.openggf.io.ModKeySyntax;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.*;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public final class ModAudioManifestParser {
    private static final Set<String> ROOT = Set.of("formatVersion", "tracks", "sfx");
    private static final Set<String> TRACK = Set.of("id", "assetPath", "loop", "loopStartFrame",
            "loopEndFrame", "gain", "tempoEffects");
    private static final Set<String> SFX = Set.of("id", "assetPath", "gain");
    private static final Pattern NUMERIC_TOKEN = Pattern.compile(
            "[+-]?(?:0[xX][0-9A-Fa-f_]+|0[oO][0-7_]+|0[bB][01_]+|"
                    + "(?:[0-9][0-9_]*|\\.[0-9_]+)(?:\\.[0-9_]*)?(?:[eE][+-]?[0-9_]+)?)");
    private final String owner;
    private final ModInputLimits limits;
    private final LoaderOptions loaderOptions;
    private final ObjectMapper mapper;

    public ModAudioManifestParser(String declaringOwnerId) {
        this(declaringOwnerId, ModInputLimits.production());
    }

    public ModAudioManifestParser(String declaringOwnerId, ModInputLimits limits) {
        this.owner = ModKeySyntax.requireManifestId(declaringOwnerId);
        this.limits = Objects.requireNonNull(limits, "limits");
        loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setMaxAliasesForCollections(0);
        loaderOptions.setNestingDepthLimit(limits.maxYamlDepth());
        loaderOptions.setCodePointLimit(limits.maxDocumentCodePoints());
        loaderOptions.setMergeOnCompose(false);
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNestingDepth(limits.maxYamlDepth())
                .maxDocumentLength(limits.maxDocumentCodePoints())
                .maxTokenCount(Math.addExact(Math.multiplyExact(
                        24L, limits.maxCollectionEntries()), 64L))
                .maxStringLength(limits.maxStringChars()).maxNameLength(limits.maxStringChars())
                .maxNumberLength(limits.maxNumericDigits()).build();
        YAMLFactory factory = YAMLFactory.builder().loaderOptions(loaderOptions)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(constraints).build();
        mapper = new ObjectMapper(factory);
    }

    public ModAudioManifest parse(byte[] bytes) throws ModManifestException {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > limits.maxMetadataBytes()) {
            throw new ModManifestException("Audio manifest exceeds metadata byte limit");
        }
        try {
            String yaml = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            preflight(yaml);
            JsonNode root;
            try (JsonParser parser = mapper.createParser(bytes)) {
                root = mapper.readTree(parser);
                if (parser.nextToken() != null) throw new ModManifestException("Trailing YAML is not allowed");
            }
            if (root == null || !root.isObject()) throw new ModManifestException("Audio manifest root must be a mapping");
            only(root, ROOT, "audio manifest");
            int version = integer(required(root, "formatVersion"), "formatVersion");
            if (version != 1) throw new ModManifestException("Unsupported audio formatVersion: " + version);
            List<ModAudioTrack> tracks = parseTracks(required(root, "tracks"));
            JsonNode sfxNode = root.get("sfx");
            List<ModAudioSfx> sfx = sfxNode == null ? List.of() : parseSfx(nonNull(sfxNode, "sfx"));
            return new ModAudioManifest(1, tracks, sfx);
        } catch (ModManifestException error) {
            throw error;
        } catch (IllegalArgumentException | CharacterCodingException error) {
            throw new ModManifestException("Invalid audio manifest: " + error.getMessage(), error);
        } catch (java.io.IOException error) {
            throw new ModManifestException("Unable to parse audio manifest: " + error.getMessage(), error);
        }
    }

    public byte[] writeCanonical(ModAudioManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        StringBuilder out = new StringBuilder("formatVersion: 1\ntracks:");
        if (manifest.tracks().isEmpty()) out.append(" []\n");
        else {
            out.append('\n');
            for (ModAudioTrack track : manifest.tracks()) {
                requireOwner(track.key().modId());
                ModAudioValidation.requireAudioPath(track.assetPath(), limits.maxEntryNameBytes());
                out.append("  - id: ").append(yamlId(track.key().localName())).append('\n')
                        .append("    assetPath: ").append(track.assetPath()).append('\n')
                        .append("    loop: ").append(track.loop()).append('\n')
                        .append("    loopStartFrame: ").append(track.loopStartFrame()).append('\n');
                track.loopEndFrame().ifPresent(value -> out.append("    loopEndFrame: ").append(value).append('\n'));
                out.append("    gain: ").append(Float.toString(track.gain())).append('\n')
                        .append("    tempoEffects: ").append(track.tempoEffects()).append('\n');
            }
        }
        out.append("sfx:");
        if (manifest.sfx().isEmpty()) out.append(" []\n");
        else {
            out.append('\n');
            for (ModAudioSfx value : manifest.sfx()) {
                requireOwner(value.key().modId());
                ModAudioValidation.requireAudioPath(value.assetPath(), limits.maxEntryNameBytes());
                out.append("  - id: ").append(yamlId(value.key().localName())).append('\n')
                        .append("    assetPath: ").append(value.assetPath()).append('\n')
                        .append("    gain: ").append(Float.toString(value.gain())).append('\n');
            }
        }
        try {
            preflight(out.toString());
        } catch (ModManifestException error) {
            throw new IllegalArgumentException("Canonical audio manifest exceeds injected limits", error);
        }
        byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limits.maxMetadataBytes()) throw new IllegalArgumentException("Canonical audio manifest exceeds metadata limit");
        return bytes;
    }

    private List<ModAudioTrack> parseTracks(JsonNode node) throws ModManifestException {
        if (!node.isArray() || node.size() > limits.maxCollectionEntries()) throw new ModManifestException("tracks must be a bounded sequence");
        List<ModAudioTrack> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) throw new ModManifestException("Each track must be a mapping");
            only(item, TRACK, "track");
            String id = text(required(item, "id"), "track id");
            String path = ModAudioValidation.requireAudioPath(text(required(item, "assetPath"), "assetPath"), limits.maxEntryNameBytes());
            boolean loop = bool(required(item, "loop"), "loop");
            long start = longInteger(required(item, "loopStartFrame"), "loopStartFrame");
            JsonNode endNode = item.get("loopEndFrame");
            OptionalLong end = endNode == null ? OptionalLong.empty() : OptionalLong.of(longInteger(nonNull(endNode, "loopEndFrame"), "loopEndFrame"));
            float gain = finiteFloat(required(item, "gain"), "gain");
            boolean tempo = bool(required(item, "tempoEffects"), "tempoEffects");
            values.add(new ModAudioTrack(new TrackKey(owner, id), path, loop, start, end, gain, tempo));
        }
        return List.copyOf(values);
    }

    private List<ModAudioSfx> parseSfx(JsonNode node) throws ModManifestException {
        if (!node.isArray() || node.size() > limits.maxCollectionEntries()) throw new ModManifestException("sfx must be a bounded sequence");
        List<ModAudioSfx> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) throw new ModManifestException("Each SFX must be a mapping");
            only(item, SFX, "SFX");
            String id = text(required(item, "id"), "SFX id");
            String path = ModAudioValidation.requireAudioPath(text(required(item, "assetPath"), "assetPath"), limits.maxEntryNameBytes());
            values.add(new ModAudioSfx(new SfxKey(owner, id), path, finiteFloat(required(item, "gain"), "gain")));
        }
        return List.copyOf(values);
    }

    private void preflight(String yaml) throws ModManifestException {
        Deque<Frame> stack = new ArrayDeque<>();
        int documents = 0;
        try {
            for (Event event : new Yaml(loaderOptions).parse(new StringReader(yaml))) {
                if (event instanceof DocumentStartEvent && ++documents > 1) throw new ModManifestException("Multiple YAML documents are not allowed");
                if (event instanceof AliasEvent) throw new ModManifestException("YAML aliases are not allowed");
                if (event instanceof CollectionStartEvent collection) {
                    if (collection.getAnchor() != null) throw new ModManifestException("YAML anchors are not allowed");
                    increment(stack); stack.push(new Frame(collection instanceof MappingStartEvent));
                    if (stack.size() > limits.maxYamlDepth()) throw new ModManifestException("YAML depth exceeds limit");
                    if (collection.getTag() != null) throw new ModManifestException("Explicit YAML tags are not allowed");
                } else if (event instanceof CollectionEndEvent) {
                    Frame frame = stack.pop(); int entries = frame.mapping ? frame.nodes / 2 : frame.nodes;
                    if (entries > limits.maxCollectionEntries()) throw new ModManifestException("YAML collection exceeds limit");
                } else if (event instanceof ScalarEvent scalar) {
                    increment(stack);
                    if (scalar.getAnchor() != null) throw new ModManifestException("YAML anchors are not allowed");
                    if (scalar.getTag() != null) throw new ModManifestException("Explicit YAML tags are not allowed");
                    if (scalar.isPlain() && scalar.getValue().equals("<<")) throw new ModManifestException("YAML merge keys are not allowed");
                    if (scalar.getValue().length() > limits.maxStringChars()) throw new ModManifestException("YAML string exceeds limit");
                    if (NUMERIC_TOKEN.matcher(scalar.getValue()).matches()
                            && numericDigits(scalar.getValue()) > limits.maxNumericDigits()) {
                        throw new ModManifestException("YAML numeric token exceeds digit limit");
                    }
                }
            }
        } catch (ModManifestException error) { throw error; }
        catch (YAMLException error) { throw new ModManifestException("Invalid bounded YAML: " + error.getMessage(), error); }
    }

    private static void increment(Deque<Frame> stack) { if (!stack.isEmpty()) stack.peek().nodes++; }
    private static String yamlId(String id) {
        if (Character.isDigit(id.charAt(0)) || Set.of(
                "null", "true", "false", "yes", "no", "on", "off").contains(id)) {
            return "'" + id + "'";
        }
        return id;
    }
    private static int numericDigits(String token) {
        boolean hex = token.indexOf('x') >= 0 || token.indexOf('X') >= 0;
        int count = 0;
        for (int index = 0; index < token.length(); index++) {
            char value = token.charAt(index);
            if (Character.isDigit(value) || (hex && ((value >= 'a' && value <= 'f')
                    || (value >= 'A' && value <= 'F')))) count++;
        }
        return count;
    }
    private static final class Frame { final boolean mapping; int nodes; Frame(boolean mapping) { this.mapping = mapping; } }
    private void requireOwner(String value) { if (!owner.equals(value)) throw new IllegalArgumentException("Audio key owner differs from declaring manifest"); }
    private static JsonNode required(JsonNode node, String name) throws ModManifestException { JsonNode value=node.get(name); if(value==null||value.isNull()) throw new ModManifestException("Missing or null field: "+name); return value; }
    private static JsonNode nonNull(JsonNode value,String name)throws ModManifestException{if(value.isNull())throw new ModManifestException(name+" must not be null");return value;}
    private static void only(JsonNode node,Set<String> allowed,String context)throws ModManifestException{Iterator<String> names=node.fieldNames();while(names.hasNext()){String name=names.next();if(!allowed.contains(name))throw new ModManifestException("Unknown "+context+" field: "+name);}}
    private static String text(JsonNode node,String field)throws ModManifestException{if(!node.isTextual())throw new ModManifestException(field+" must be a string");return node.textValue();}
    private static boolean bool(JsonNode node,String field)throws ModManifestException{if(!node.isBoolean())throw new ModManifestException(field+" must be a boolean");return node.booleanValue();}
    private static int integer(JsonNode node,String field)throws ModManifestException{long value=longInteger(node,field);if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new ModManifestException(field+" exceeds integer range");return(int)value;}
    private static long longInteger(JsonNode node,String field)throws ModManifestException{if(!node.isIntegralNumber()||!node.canConvertToLong())throw new ModManifestException(field+" must be an integer");return node.longValue();}
    private static float finiteFloat(JsonNode node,String field)throws ModManifestException{if(!node.isNumber())throw new ModManifestException(field+" must be a number");double value=node.doubleValue();if(!Double.isFinite(value)||value<0||value>4)throw new ModManifestException(field+" must be finite and in 0..4");return(float)value;}
}
