package com.openggf.mods;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.PackageStockResolver;
import com.openggf.audio.smps.PackageStockResolver.Locator;
import com.openggf.audio.smps.SmpsMusicParameters;
import com.openggf.catalog.StockGameCatalog;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic1.audio.smps.Sonic1SmpsData;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.smps.Sonic2PackageEnvelopes;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Reads schema 4 from a packed mod's validated, immutable entry inventory. */
final class SmpsPackageReader {
    static final int BANK_LIMIT = 32 * 1024;
    private static final int VOICE_SIZE = 25;
    private static final int DAC_LIMIT = 512 * 1024;
    private static final int ENTRY_LIMIT = 482;
    private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Set<String> DRIVERS = Set.copyOf(StockGameCatalog.cartridgeCodes());
    private static final Map<String, String> WARNINGS = Map.of(
            "RETAIL_SONG_DATA", "Contains retail song data. For personal use only. Do not redistribute.",
            "ROM_DERIVED_DATA", "Contains ROM-derived data. For personal use only. Do not redistribute.",
            "DERIVED_FROM_RETAIL_SONG", "The composition derives from a retail song.",
            "UNDECLARED_STOCK_CONVERTED", "The authoring tool converted embedded stock bytes to a reference.",
            "SOURCE_AUDIO", "Contains material from a source recording.",
            "THIRD_PARTY", "Contains third-party material. Check its license.",
            "UNKNOWN_ROM_IMAGE", "The ROM image is unknown. Asset hashes determine compatibility.",
            "DPCM_INEXACT", "A disassembly build will change a sample when encoding it to DPCM.");

    private enum Kind {
        FM_VOICE("fm", 32768 / VOICE_SIZE, VOICE_SIZE),
        PSG_VOLUME("psg", 255, 256),
        MODULATION("mod", 127, 256),
        DAC_SAMPLE("dac", 95, 65535);

        final String prefix;
        final int count;
        final int size;
        Kind(String prefix, int count, int size) {
            this.prefix = prefix;
            this.count = count;
            this.size = size;
        }

        String path(int ordinal) {
            return switch (this) {
                case FM_VOICE -> "voices/global.bin";
                case PSG_VOLUME, MODULATION -> "%s/asset-%03d.bin".formatted(prefix, ordinal + 1);
                case DAC_SAMPLE -> "dac/asset-%03d.pcm".formatted(ordinal);
            };
        }
    }

    record Result(AbstractSmpsData data, DacData dac, long retainedBytes) {}
    private record Asset(Kind kind, int ordinal, byte[] bytes, boolean reference) {}

    private final ModAssetRoot root;
    private final String directory;
    private final Set<String> remaining;
    private final ModInputLimits limits;
    private final Map<String, Asset> assets = new LinkedHashMap<>();
    private final PackageStockResolver resolver;
    private final Consumer<String> warnings;
    private long remainingBytes;
    private long transformWork = 32L << 20;
    private byte[] rawBank;
    private final Set<Integer> classicVoiceOffsets = new HashSet<>();
    private byte[] globalTable;
    private List<JsonNode> globalKeys = List.of();

    SmpsPackageReader(ModAssetRoot root, Collection<String> inventory, String directory,
                      ModInputLimits limits, PackageStockResolver resolver, Consumer<String> warnings) throws IOException {
        this.root = root;
        this.directory = directory;
        this.limits = limits;
        this.resolver = resolver;
        this.warnings = warnings;
        remaining = new TreeSet<>();
        for (String name : inventory) {
            if (name.startsWith(directory) && !remaining.add(name.substring(directory.length()))) {
                throw invalid("Duplicate package entry " + name);
            }
        }
        check(remaining.size() <= Math.min(ENTRY_LIMIT, limits.maxJarEntries()), "Too many package entries");
        remainingBytes = Math.min(limits.maxModValidationBytes(),
                remaining.contains("source.smps.json") ? 20L << 20 : 2L << 20);
    }

    /** A null stock resolver validates all ROM-independent fields and bytes. */
    Result read(String baseGame) throws IOException {
        return read(baseGame, 1.0f, true);
    }

    Result read(String baseGame, float gain, boolean tempoEffects) throws IOException {
        ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(limits.maxYamlDepth())
                        .maxStringLength(limits.maxStringChars()).maxNameLength(limits.maxStringChars())
                        .maxNumberLength(limits.maxNumericDigits()).maxTokenCount(262144).build())
                .build());
        byte[] json = readEntry("manifest.json", Math.min(1L << 20, limits.maxMetadataBytes()));
        String manifest = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(json)).toString();
        check(manifest.length() <= limits.maxDocumentCodePoints(), "Manifest exceeds document limit");
        JsonNode document;
        try (var parser = mapper.createParser(manifest)) {
            document = mapper.readTree(parser);
            check(parser.nextToken() == null, "Trailing JSON is not allowed");
        }
        fields(document, "driver?", "schemaVersion", "profile", "package", "generator", "provenance",
                "requires", "driverOptions", "bank", "assets", "envelopeBindings", "dacBindings",
                "globalVoices", "source");
        number(document, "schemaVersion", 4, 4);
        literal(document, "profile", "portable");
        String driver = document.has("driver") ? string(document, "driver") : "s3k";
        check(DRIVERS.contains(driver), "Unknown driver " + driver);
        check(driver.equals(baseGame), "SMPS driver " + driver + " requires a patch with baseGame " + driver);
        boolean classic = !driver.equals("s3k");
        JsonNode options = document.get("driverOptions");
        Integer speedUpTempo = null;
        if (classic) {
            fields(options, "speedUpTempo");
            if (!options.get("speedUpTempo").isNull()) {
                speedUpTempo = number(options, "speedUpTempo", 0, 255);
            }
        } else {
            fields(options);
        }
        metadata(document);
        JsonNode bank = document.get("bank");
        fields(bank, "form", "path", "sha256", "assembledSha256", "base", "headerOffset",
                "localVoiceCount", "previewFrames", "relocations", "relocationsComplete", "fills");
        String form = string(bank, "form");
        check(form.equals("embedded"), "Unsupported bank form " + form + "; only embedded is supported");
        literal(bank, "path", "track.bin");
        rawBank = hashedEntry(bank, BANK_LIMIT);
        int base = number(bank, "base", 0, 32768);
        check(base == 0 || (!driver.equals("s1") && base == 32768), "Invalid " + driver + " bank base");
        check(rawBank.length >= 8, "Truncated music bank");
        int header = number(bank, "headerOffset", 0, classic ? 0 : rawBank.length - 8);
        int voiceCount = number(bank, "localVoiceCount", 0, classic ? 128 : BANK_LIMIT / VOICE_SIZE);
        number(bank, "previewFrames", 0, Integer.MAX_VALUE);
        bool(bank, "relocationsComplete");
        validateRelocations(bank);
        String assembledHash = hashField(bank, "assembledSha256");

        JsonNode globals = document.get("globalVoices");
        if (!globals.isNull()) {
            check(!classic, "Global FM voices are only supported for s3k");
            fields(globals, "path", "sha256", "voices");
            literal(globals, "path", "voices/global.bin");
            globalKeys = array(globals, "voices", 256);
            globalTable = hashedEntry(globals, 256L * VOICE_SIZE);
            check(globalTable.length == globalKeys.size() * VOICE_SIZE, "Global voice table length mismatch");
        }

        int dacBytes = readAssets(document, driver);
        byte[] assembled = fillBank(bank, header, classic);
        if (assembled != null) verifyHash("assembled track.bin", assembledHash, assembled);
        byte[] voices = fillGlobalVoices();
        Map<Integer, byte[]> psg = new HashMap<>();
        Map<Integer, byte[]> modulation = new HashMap<>();
        envelopeBindings(document, psg, modulation);
        Map<Integer, DacData.DacEntry> bindings = new HashMap<>();
        DacData dac = dacBindings(document, driver, bindings);
        readSource(document);
        check(remaining.isEmpty(), "Unexpected package entry " + remaining.stream().findFirst().orElse(""));
        if (classic) {
            boolean bigEndian = driver.equals("s1");
            ClassicPackageBank.validate(rawBank, bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN,
                    bigEndian ? 2 : 1, base, voiceCount, classicVoiceOffsets, psg.keySet(), bindings.keySet());
        } else {
            validateHeader(assembled == null ? rawBank : assembled, base, header, voiceCount);
        }
        if (assembled == null || (resolver == null && assets.values().stream().anyMatch(Asset::reference))) {
            return null;
        }
        var parameters = new SmpsMusicParameters(gain, tempoEffects, speedUpTempo);
        AbstractSmpsData data = switch (driver) {
            case "s1" -> new S1PackageData(assembled, psg, parameters);
            case "s2" -> new S2PackageData(assembled, base, psg, parameters);
            default -> {
                var s3k = new PackageData(Arrays.copyOfRange(assembled, header, assembled.length),
                        base + header, parameters);
                s3k.setBankData(assembled, base);
                s3k.setPsgEnvelopes(psg);
                s3k.setModEnvelopes(modulation);
                if (voices != null) s3k.setGlobalVoiceData(voices);
                yield s3k;
            }
        };
        long resourceBytes = 2L * assembled.length + dacBytes + (voices == null ? 0 : voices.length);
        resourceBytes += psg.values().stream().mapToInt(value -> value.length).sum();
        resourceBytes += modulation.values().stream().mapToInt(value -> value.length).sum();
        return new Result(data, dac, resourceBytes);
    }

    private void metadata(JsonNode document) throws IOException {
        JsonNode info = document.get("package");
        fields(info, "id", "name", "version", "authors", "license", "rights");
        check(string(info, "id").matches("[a-z0-9][a-z0-9-]{0,63}"), "Invalid package id");
        check(string(info, "version").matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"),
                "Invalid package version");
        string(info, "name");
        for (JsonNode author : array(info, "authors", 32)) {
            check(author.isTextual() && !author.textValue().isBlank(), "Invalid package author");
        }
        nullableString(info, "license");
        JsonNode rights = info.get("rights");
        if (rights.isObject()) fields(rights, "composition?", "notes?");
        JsonNode generator = document.get("generator");
        fields(generator, "tool", "version", "openggfAudio");
        string(generator, "tool");
        string(generator, "version");
        string(generator, "openggfAudio");

        JsonNode provenance = document.get("provenance");
        fields(provenance, "containsRomDerivedData", "containsRetailSongData", "derivedFromRetailSong", "warnings");
        Set<String> codes = new TreeSet<>(sortedStrings(provenance, "warnings", WARNINGS.keySet()));
        if (bool(provenance, "containsRomDerivedData")) codes.add("ROM_DERIVED_DATA");
        if (bool(provenance, "containsRetailSongData")) codes.add("RETAIL_SONG_DATA");
        JsonNode retail = provenance.get("derivedFromRetailSong");
        if (!retail.isNull()) {
            fields(retail, "game", "songId", "bankSha256");
            check(DRIVERS.contains(string(retail, "game")), "Invalid retail song game");
            number(retail, "songId", 0, 0xffff);
            hashField(retail, "bankSha256");
        }
        for (String code : codes) warn(code);
    }

    private int readAssets(JsonNode document, String driver) throws IOException {
        EnumMap<Kind, Integer> counts = new EnumMap<>(Kind.class);
        int dacBytes = 0;
        boolean stock = false;
        for (JsonNode entry : array(document, "assets", Math.min(2048, limits.maxCollectionEntries()))) {
            fields(entry, "key", "kind", "id", "name", "origin", "path?", "sha256?", "bankOffset?",
                    "ref?", "transforms?", "resultSha256?");
            String key = string(entry, "key");
            Kind kind;
            try {
                kind = Kind.valueOf(string(entry, "kind"));
            } catch (IllegalArgumentException failure) {
                throw invalid("Unknown asset kind for " + key);
            }
            check(driver.equals("s3k") || kind != Kind.MODULATION, "Modulation envelopes require s3k");
            int ordinal = counts.merge(kind, 1, Integer::sum) - 1;
            check(ordinal < kind.count && key.equals(kind.prefix + "/" + ordinal), "Invalid asset ordinal: " + key);
            string(entry, "id");
            string(entry, "name");
            String origin = origin(entry.get("origin"));
            boolean ref = entry.has("ref");
            boolean offset = entry.has("bankOffset");
            boolean path = entry.has("path");
            check((ref ? 1 : 0) + (offset ? 1 : 0) + (path ? 1 : 0) == 1
                    && path == entry.has("sha256") && ref == entry.has("transforms")
                    && (ref || !entry.has("resultSha256")), "Invalid payload declaration for " + key);
            byte[] bytes;
            if (ref) {
                check(origin.equals("stock"), "Stock locator requires stock origin for " + key);
                stock = true;
                bytes = reference(entry, kind, driver, key);
            } else if (offset) {
                check(kind == Kind.FM_VOICE, "Only FM voices can use bankOffset");
                int start = number(entry, "bankOffset", 0, rawBank.length - VOICE_SIZE);
                if (!driver.equals("s3k")) {
                    check(classicVoiceOffsets.add(start), "Duplicate local FM asset offset " + start);
                }
                bytes = Arrays.copyOfRange(rawBank, start, start + VOICE_SIZE);
            } else {
                literal(entry, "path", kind.path(ordinal));
                if (kind == Kind.FM_VOICE) {
                    int index = globalSlot(key);
                    bytes = voiceAt(globalTable, index);
                    verifyHash(key, hashField(entry, "sha256"), bytes);
                } else {
                    bytes = hashedEntry(entry, kind.size);
                }
            }
            if (bytes != null) {
                validateAsset(kind, bytes, driver);
                if (kind == Kind.DAC_SAMPLE) {
                    dacBytes += bytes.length;
                    check(dacBytes <= DAC_LIMIT, "DAC sample bytes exceed 512 KiB");
                }
            }
            if (origin.equals("stock") && !ref) warn("ROM_DERIVED_DATA");
            assets.put(key, new Asset(kind, ordinal, bytes, ref));
        }
        JsonNode requires = document.get("requires");
        fields(requires, "stockAssets", "games");
        check(bool(requires, "stockAssets") == stock
                        && sortedStrings(requires, "games", DRIVERS).equals(stock ? Set.of(driver) : Set.of()),
                "requires must describe the stock references");
        return dacBytes;
    }

    private String origin(JsonNode node) throws IOException {
        String type = string(node, "type");
        switch (type) {
            case "original", "stock" -> fields(node, "type");
            case "source-audio" -> {
                fields(node, "type");
                warn("SOURCE_AUDIO");
            }
            case "third-party" -> {
                fields(node, "type", "license", "attribution");
                string(node, "license");
                string(node, "attribution");
                warn("THIRD_PARTY");
            }
            default -> throw invalid("Unknown asset origin " + type);
        }
        return type;
    }

    private byte[] reference(JsonNode entry, Kind kind, String driver, String key) throws IOException {
        JsonNode ref = entry.get("ref");
        literal(ref, "game", driver);
        String table = string(ref, "table");
        String expected = hashField(ref, "sha256");
        boolean classic = !driver.equals("s3k");
        Locator locator = switch (table) {
            case "global-fm" -> {
                fields(ref, "game", "table", "sha256", "slot");
                check(!classic && kind == Kind.FM_VOICE, "global-fm requires s3k FM_VOICE");
                yield new Locator(table, number(ref, "slot", 0, 255), 0);
            }
            case "local-fm" -> {
                fields(ref, "game", "table", "sha256", "slot", "songId");
                check(kind == Kind.FM_VOICE, "local-fm requires FM_VOICE");
                int songId = number(ref, "songId", 1, 0x1ff);
                if (classic) {
                    var ids = driver.equals("s1") ? Sonic1Music.titleMap() : Sonic2Music.titleMap();
                    check(ids.containsKey(songId), "Unknown " + driver + " engine music ID " + songId);
                }
                yield new Locator(table, number(ref, "slot", 0, 255), songId);
            }
            case "psg", "mod" -> {
                fields(ref, "game", "table", "sha256", "index");
                check(kind.prefix.equals(table), "Wrong kind for table " + table);
                int last = switch (driver) {
                    case "s1" -> 9;
                    case "s2" -> Sonic2PackageEnvelopes.COUNT;
                    default -> 255;
                };
                yield new Locator(table, number(ref, "index", classic ? 1 : 0, last), 0);
            }
            case "dac" -> {
                fields(ref, "game", "table", "sha256", "note");
                check(kind == Kind.DAC_SAMPLE, "dac requires DAC_SAMPLE");
                int last = switch (driver) {
                    case "s1" -> 0x8b;
                    case "s2" -> 0x91;
                    default -> 0xc4;
                };
                int note = number(ref, "note", 0x81, last);
                check(!driver.equals("s1") || note <= 0x83 || note >= 0x88, "Invalid s1 stock DAC note " + note);
                yield new Locator(table, note, 0);
            }
            default -> throw invalid("Unknown stock table " + table);
        };
        List<JsonNode> transforms = array(entry, "transforms", limits.maxCollectionEntries());
        check(transforms.isEmpty() != entry.has("resultSha256"), "resultSha256 must accompany transforms for " + key);
        String context = key + " " + locator;
        byte[] bytes = null;
        if (resolver != null) {
            try {
                bytes = resolver.resolve(locator);
            } catch (IOException error) {
                throw new IOException(context + ": expected SHA-256 " + expected
                        + ", actual unavailable (" + error.getMessage() + ")", error);
            }
            check(bytes != null, context + ": expected SHA-256 " + expected + ", actual unavailable");
            verifyHash(context, expected, bytes);
            // One bounded copy, even when a package declares many patch operations.
            check(bytes.length > 0 && bytes.length <= kind.size, "Stock payload exceeds kind limits: " + context);
            bytes = bytes.clone();
        }
        for (JsonNode transform : transforms) {
            if (bytes != null) {
                transformWork -= bytes.length;
                check(transformWork >= 0, "Stock transform work limit exceeded");
            }
            bytes = transform(kind, bytes, transform);
        }
        if (!transforms.isEmpty()) {
            String expectedResult = hashField(entry, "resultSha256");
            if (bytes != null) verifyHash("transformed " + context, expectedResult, bytes);
        }
        return bytes;
    }

    private byte[] transform(Kind kind, byte[] bytes, JsonNode node) throws IOException {
        switch (string(node, "op")) {
            case "gain" -> {
                fields(node, "op", "factor");
                check(kind == Kind.DAC_SAMPLE, "gain requires DAC_SAMPLE");
                String factor = string(node, "factor");
                double gain;
                try {
                    gain = Double.parseDouble(factor);
                } catch (NumberFormatException error) {
                    throw invalid("Invalid DAC gain");
                }
                check(Double.isFinite(gain) && Double.toHexString(gain).equals(factor), "Invalid hexadecimal DAC gain");
                if (bytes != null) {
                    for (int i = 0; i < bytes.length; i++) {
                        long rounded = Math.round(((bytes[i] & 255) - 128) * gain);
                        bytes[i] = (byte) Math.clamp(rounded + 128, 0, 255);
                    }
                }
            }
            case "trim" -> {
                fields(node, "op", "start", "end");
                check(kind == Kind.DAC_SAMPLE, "trim requires DAC_SAMPLE");
                int limit = bytes == null ? kind.size : bytes.length;
                int start = number(node, "start", 0, limit - 1);
                int end = number(node, "end", start + 1, limit);
                if (bytes != null) return Arrays.copyOfRange(bytes, start, end);
            }
            case "patch" -> {
                fields(node, "op", "bytes");
                check(kind != Kind.DAC_SAMPLE, "patch requires an FM voice or envelope");
                int previous = -1;
                for (JsonNode edit : array(node, "bytes", kind.size)) {
                    fields(edit, "offset", "value");
                    int offset = number(edit, "offset", previous + 1,
                            (bytes == null ? kind.size : bytes.length) - 1);
                    int value = number(edit, "value", 0, 255);
                    if (bytes != null) bytes[offset] = (byte) value;
                    previous = offset;
                }
            }
            default -> throw invalid("Unknown transform operation " + string(node, "op"));
        }
        return bytes;
    }

    private void validateRelocations(JsonNode bank) throws IOException {
        int previous = -1;
        for (JsonNode entry : array(bank, "relocations", limits.maxCollectionEntries())) {
            int offset = integer(entry, "relocation", previous + 1, rawBank.length - 2);
            previous = offset;
        }
    }

    private byte[] fillBank(JsonNode bank, int header, boolean classic) throws IOException {
        byte[] result = rawBank.clone();
        boolean resolved = true;
        int end = 0;
        for (JsonNode fill : array(bank, "fills", rawBank.length / VOICE_SIZE)) {
            fields(fill, "offset", "length", "asset");
            int offset = number(fill, "offset", end, rawBank.length - VOICE_SIZE);
            number(fill, "length", VOICE_SIZE, VOICE_SIZE);
            Asset asset = asset(fill, Kind.FM_VOICE);
            check(asset.reference(), "Bank fill requires a referenced FM voice");
            int headerEnd = header + 6 + (rawBank[header + 2] & 255) * 4 + (rawBank[header + 3] & 255) * 6;
            check(offset + VOICE_SIZE <= header || offset >= headerEnd, "Bank fill overlaps the header");
            if (classic) check(classicVoiceOffsets.add(offset), "Duplicate local FM asset offset " + offset);
            checkZero(rawBank, offset);
            if (asset.bytes() == null) resolved = false;
            else System.arraycopy(asset.bytes(), 0, result, offset, VOICE_SIZE);
            end = offset + VOICE_SIZE;
        }
        return resolved ? result : null;
    }

    private int globalSlot(String key) throws IOException {
        for (int slot = 0; slot < globalKeys.size(); slot++) {
            if (key.equals(globalKeys.get(slot).textValue())) return slot;
        }
        throw invalid("FM payload has no global voice slot: " + key);
    }

    private byte[] fillGlobalVoices() throws IOException {
        if (globalTable == null) return null;
        byte[] result = globalTable.clone();
        for (int slot = 0; slot < globalKeys.size(); slot++) {
            Asset asset = assets.get(globalKeys.get(slot).textValue());
            check(asset != null && asset.kind() == Kind.FM_VOICE, "Invalid global FM voice at slot " + slot);
            int offset = slot * VOICE_SIZE;
            if (asset.reference()) {
                checkZero(globalTable, offset);
                if (asset.bytes() != null) System.arraycopy(asset.bytes(), 0, result, offset, VOICE_SIZE);
            } else {
                check(Arrays.equals(asset.bytes(), voiceAt(globalTable, slot)), "Global voice mismatch at slot " + slot);
            }
        }
        return result;
    }

    private static byte[] voiceAt(byte[] table, int slot) {
        if (table == null || slot < 0 || (slot + 1) * VOICE_SIZE > table.length) return null;
        return Arrays.copyOfRange(table, slot * VOICE_SIZE, (slot + 1) * VOICE_SIZE);
    }

    private static void checkZero(byte[] bytes, int offset) throws IOException {
        for (int i = offset; i < offset + VOICE_SIZE; i++) check(bytes[i] == 0, "Nonzero FM hole at " + offset);
    }

    private void envelopeBindings(JsonNode document, Map<Integer, byte[]> psg, Map<Integer, byte[]> mod)
            throws IOException {
        for (JsonNode binding : array(document, "envelopeBindings", 382)) {
            fields(binding, "asset", "slot");
            Asset asset = asset(binding, Kind.PSG_VOLUME, Kind.MODULATION);
            int slot = number(binding, "slot", 1, asset.kind().count);
            Map<Integer, byte[]> target = asset.kind() == Kind.PSG_VOLUME ? psg : mod;
            check(!target.containsKey(slot), "Duplicate " + asset.kind() + " slot " + slot);
            target.put(slot, asset.bytes());
        }
    }

    private DacData dacBindings(JsonNode document, String driver, Map<Integer, DacData.DacEntry> bindings)
            throws IOException {
        Map<Integer, byte[]> samples = new HashMap<>();
        for (Asset asset : assets.values()) {
            if (asset.kind() == Kind.DAC_SAMPLE && asset.bytes() != null) {
                samples.put(asset.ordinal(), asset.bytes());
            }
        }
        int previous = 0x80;
        for (JsonNode binding : array(document, "dacBindings", 95)) {
            fields(binding, "note", "asset", "rate");
            int note = number(binding, "note", previous + 1, 0xdf);
            Asset asset = asset(binding, Kind.DAC_SAMPLE);
            bindings.put(note, new DacData.DacEntry(asset.ordinal(), number(binding, "rate", 0, 255)));
            previous = note;
        }
        int baseCycles = switch (driver) {
            case "s1" -> 301;
            case "s2" -> 295;
            default -> 297;
        };
        return new DacData(samples, bindings, baseCycles);
    }

    private Asset asset(JsonNode binding, Kind... kinds) throws IOException {
        String key = string(binding, "asset");
        Asset asset = assets.get(key);
        check(asset != null && Arrays.asList(kinds).contains(asset.kind()), "Wrong or missing asset " + key);
        return asset;
    }

    private void readSource(JsonNode document) throws IOException {
        JsonNode source = document.get("source");
        if (!source.isNull()) {
            fields(source, "path", "sha256", "redacted");
            literal(source, "path", "source.smps.json");
            bool(source, "redacted");
            hashedEntry(source, 16L << 20);
        }
        if (remaining.contains("LICENSE.txt")) {
            check(!document.get("package").get("license").isNull(), "LICENSE.txt requires package.license");
            readEntry("LICENSE.txt", limits.maxMetadataBytes());
        }
    }

    private static void validateHeader(byte[] bank, int base, int header, int voiceCount) throws IOException {
        int fm = bank[header + 2] & 255;
        int psg = bank[header + 3] & 255;
        check(fm <= 7 && psg <= 3 && fm + psg > 0, "Invalid S3K channel counts");
        int fmEnd = header + 6 + fm * 4;
        check(fmEnd + psg * 6 <= bank.length, "Truncated S3K music header");
        for (int offset = header + 6; offset < fmEnd; offset += 4) checkPointer(bank, base, offset);
        for (int offset = fmEnd; offset < fmEnd + psg * 6; offset += 6) checkPointer(bank, base, offset);
        if (voiceCount > 0) {
            int offset = read16(bank, header) - base;
            check(read16(bank, header) != 0 && offset >= 0 && offset + voiceCount * VOICE_SIZE <= bank.length,
                    "Local voice table lies outside the bank");
        }
    }

    private static void checkPointer(byte[] bank, int base, int offset) throws IOException {
        int pointer = read16(bank, offset);
        check(pointer == 0 || (pointer >= base && pointer - base < bank.length),
                "Channel pointer outside bank at offset " + offset);
    }

    static int read16(byte[] bytes, int offset) {
        return (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8);
    }

    private static void validateAsset(Kind kind, byte[] bytes, String driver) throws IOException {
        check(bytes.length >= 1 && bytes.length <= kind.size, "Invalid " + kind + " byte length");
        switch (kind) {
            case FM_VOICE -> check(bytes.length == VOICE_SIZE, "FM voice must have 25 bytes");
            case PSG_VOLUME -> {
                if (driver.equals("s3k")) {
                    validateEnvelope(bytes, false);
                } else {
                    check(bytes.length >= 2 && bytes[bytes.length - 1] == (byte) 0x80,
                            "Classic PSG envelope requires an 80 hold terminator");
                    for (int i = 0; i < bytes.length - 1; i++) {
                        check(bytes[i] >= 0, "Classic PSG envelope requires attenuation before the hold terminator");
                    }
                }
            }
            case MODULATION -> validateEnvelope(bytes, true);
            case DAC_SAMPLE -> { }
        }
    }

    private static void validateEnvelope(byte[] bytes, boolean modulation) throws IOException {
        if (!modulation) {
            for (byte value : bytes) {
                int command = value & 255;
                check(command <= 0x81 || command == 0x83, "Unsupported PSG envelope command " + command);
            }
        }
        int[] visited = new int[bytes.length];
        Arrays.fill(visited, -1);
        int cursor = 0;
        int writes = 0;
        while (cursor < bytes.length) {
            if (visited[cursor] >= 0) {
                check(visited[cursor] != writes, "Zero-time envelope control cycle");
                return;
            }
            visited[cursor] = writes;
            int command = bytes[cursor] & 255;
            switch (command) {
                case 0x81, 0x83 -> { return; }
                case 0x80 -> cursor = 0;
                case 0x82, 0x84 -> {
                    check(modulation && cursor + 1 < bytes.length, "Truncated envelope command");
                    cursor = command == 0x82 ? bytes[cursor + 1] & 255 : (cursor + 2) & 255;
                }
                default -> {
                    cursor = modulation ? (cursor + 1) & 255 : cursor + 1;
                    writes++;
                }
            }
        }
        throw invalid("Envelope runs outside its playback bytes");
    }

    private byte[] hashedEntry(JsonNode node, long cap) throws IOException {
        String path = string(node, "path");
        String expected = hashField(node, "sha256");
        byte[] bytes = readEntry(path, cap);
        verifyHash(path, expected, bytes);
        return bytes;
    }

    private byte[] readEntry(String path, long cap) throws IOException {
        check(remaining.remove(path), "Missing or repeated package entry " + path);
        check(remainingBytes > 0, "Package byte limit exceeded");
        byte[] bytes = root.readBounded(directory + path, Math.min(Math.min(cap, remainingBytes), limits.maxAssetBytes()));
        remainingBytes -= bytes.length;
        return bytes;
    }

    private void warn(String code) {
        warnings.accept(code + ": " + WARNINGS.get(code));
    }

    private static String hashField(JsonNode node, String name) throws IOException {
        String hash = string(node, name);
        check(HASH.matcher(hash).matches(), "Invalid SHA-256 for " + name);
        return hash;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void verifyHash(String asset, String expected, byte[] bytes) throws IOException {
        String actual = sha256(bytes);
        check(expected.equalsIgnoreCase(actual), asset + ": expected SHA-256 " + expected + ", actual " + actual);
    }

    private static void fields(JsonNode node, String... fields) throws IOException {
        check(node != null && node.isObject(), "Expected JSON object");
        Set<String> allowed = new HashSet<>();
        for (String name : fields) {
            if (name.endsWith("?")) allowed.add(name.substring(0, name.length() - 1));
            else {
                check(node.has(name), "Missing package field " + name);
                allowed.add(name);
            }
        }
        for (var field : node.properties()) {
            check(allowed.contains(field.getKey()), "Unknown package field " + field.getKey());
        }
    }

    private List<JsonNode> array(JsonNode node, String name, int max) throws IOException {
        max = Math.min(max, limits.maxCollectionEntries());
        JsonNode value = node.path(name);
        check(value.isArray() && value.size() <= max, name + " must be an array with at most " + max + " entries");
        List<JsonNode> entries = new ArrayList<>(value.size());
        value.forEach(entries::add);
        return entries;
    }

    private Set<String> sortedStrings(JsonNode node, String name, Set<String> allowed) throws IOException {
        Set<String> result = new HashSet<>();
        String previous = "";
        for (JsonNode entry : array(node, name, allowed.size())) {
            String value = entry.textValue();
            check(value != null && allowed.contains(value) && previous.compareTo(value) < 0,
                    "Invalid, repeated or unsorted " + name + " entry");
            result.add(value);
            previous = value;
        }
        return result;
    }

    private static String string(JsonNode node, String name) throws IOException {
        String text = node.path(name).textValue();
        check(text != null && !text.isBlank(), name + " must be a nonblank string");
        return text;
    }

    private static void nullableString(JsonNode node, String name) throws IOException {
        if (!node.get(name).isNull()) string(node, name);
    }

    private static boolean bool(JsonNode node, String name) throws IOException {
        JsonNode value = node.path(name);
        check(value.isBoolean(), name + " must be a boolean");
        return value.booleanValue();
    }

    private static void literal(JsonNode node, String name, String expected) throws IOException {
        check(expected.equals(string(node, name)), "Unsupported " + name + " (expected " + expected + ")");
    }

    private static int number(JsonNode node, String name, int min, int max) throws IOException {
        return integer(node.path(name), name, min, max);
    }

    private static int integer(JsonNode node, String name, int min, int max) throws IOException {
        check(node.isIntegralNumber() && node.canConvertToInt() && node.intValue() >= min && node.intValue() <= max,
                name + " must be an integer in " + min + ".." + max);
        return node.intValue();
    }

    static void check(boolean condition, String message) throws IOException {
        if (!condition) throw invalid(message);
    }

    private static final class S1PackageData extends Sonic1SmpsData implements SmpsMusicParameters.Source {
        private final SmpsMusicParameters parameters;

        private S1PackageData(byte[] bank, Map<Integer, byte[]> psg, SmpsMusicParameters parameters) {
            super(bank);
            this.parameters = parameters;
            byte[][] slots = new byte[psg.keySet().stream().mapToInt(Integer::intValue).max().orElse(0)][];
            psg.forEach((slot, bytes) -> slots[slot - 1] = bytes);
            setPsgEnvelopes(slots);
        }

        @Override public SmpsMusicParameters musicParameters() { return parameters; }
    }

    private static final class S2PackageData extends Sonic2SmpsData implements SmpsMusicParameters.Source {
        private final SmpsMusicParameters parameters;
        private final Map<Integer, byte[]> psg;

        private S2PackageData(byte[] bank, int base, Map<Integer, byte[]> psg, SmpsMusicParameters parameters) {
            super(bank, base);
            this.parameters = parameters;
            this.psg = Map.copyOf(psg);
        }

        @Override public SmpsMusicParameters musicParameters() { return parameters; }
        @Override public byte[] getPsgEnvelope(int slot) { return psg.get(slot); }
    }

    private static final class PackageData extends Sonic3kSmpsData implements SmpsMusicParameters.Source {
        private final SmpsMusicParameters parameters;

        private PackageData(byte[] header, int start, SmpsMusicParameters parameters) {
            super(header, start);
            this.parameters = parameters;
        }

        @Override public SmpsMusicParameters musicParameters() { return parameters; }

        @Override
        public byte[] getVoice(int voiceId) {
            // The indexed bank includes local voices before a nonzero header offset.
            if (getVoicePtr() == 0) return super.getVoice(voiceId);
            int offset = getVoicePtr() - getBankZ80Base() + voiceId * VOICE_SIZE;
            byte[] bank = getBankData();
            if (offset < 0 || offset + VOICE_SIZE > bank.length) return super.getVoice(voiceId);
            return Arrays.copyOfRange(bank, offset, offset + VOICE_SIZE);
        }
    }

    private static IOException invalid(String message) {
        return new IOException(message);
    }
}
