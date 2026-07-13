package com.openggf.tools.modsdk;

import com.openggf.io.ModInputLimits;
import com.openggf.io.ModAssetRoot;
import com.openggf.game.ModKeySyntax;
import com.openggf.mods.TrackKey;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Imports the finite 16x16 Tiled subset into the canonical level-export directory. */
public final class TmxLevelImporter {
    private static final long H_FLIP = 0x8000_0000L;
    private static final long V_FLIP = 0x4000_0000L;
    private static final long DIAGONAL_FLIP = 0x2000_0000L;
    private static final long HEX_ROTATION = 0x1000_0000L;
    private static final long GID_MASK = 0x0FFF_FFFFL;
    private final ModInputLimits limits;

    public TmxLevelImporter() {
        this(ModInputLimits.production());
    }

    public TmxLevelImporter(ModInputLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public Result importLevel(Path tmxFile, Path paletteFile, Path solidProfileDirectory,
                              Path outputDirectory) throws IOException {
        return importLevel(tmxFile, paletteFile, solidProfileDirectory, outputDirectory, null);
    }

    /**
     * Same as the four-argument overload, but lets a standalone TMX level declare a namespaced
     * streamed music track (see {@code ModZoneLoader#loadStandalone}, which requires every
     * standalone level to carry a {@code TrackMusic} owned by the declaring mod). Pass
     * {@code music == null} for the default {@code StockMusic(0)} placeholder used by patch-type
     * TMX levels that reference stock zone music through a different mechanism.
     */
    public Result importLevel(Path tmxFile, Path paletteFile, Path solidProfileDirectory,
                              Path outputDirectory, TrackKey music) throws IOException {
        Objects.requireNonNull(tmxFile, "tmxFile");
        Objects.requireNonNull(paletteFile, "paletteFile");
        Path tmx = tmxFile.toRealPath();
        Path root = Objects.requireNonNull(tmx.getParent(), "TMX parent").toRealPath();
        Parsed parsed = parse(tmx, root);
        Palette palette = readPalette(paletteFile);
        Profiles profiles = new TmxSolidProfileReader(limits).read(solidProfileDirectory);
        BufferedImage image = new TmxPngReader().read(parsed.tileset().image(),
                parsed.tileset().imageWidth(), parsed.tileset().imageHeight(), limits);
        Compiled compiled = compile(parsed, image, palette, profiles.count());
        Path output = new TmxLevelExportPublisher().publish(outputDirectory, parsed, palette, profiles, compiled, music);
        List<String> warnings = parsed.start() == null
                ? List.of("TMX has no start marker; using 128,128") : List.of();
        return new Result(output, compiled.patterns().size(), compiled.chunks().size(),
                compiled.blocks().size(), warnings);
    }

    private Parsed parse(Path tmx, Path root) throws IOException {
        validateXmlFileLimits(tmx, "TMX");
        XMLInputFactory factory = XMLInputFactory.newFactory();
        set(factory, XMLInputFactory.SUPPORT_DTD, false);
        set(factory, "javax.xml.stream.isSupportingExternalEntities", false);
        set(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        set(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        set(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (var input = Files.newInputStream(tmx)) {
            XMLStreamReader xml = factory.createXMLStreamReader(input);
            int width = -1, height = -1;
            Tileset tileset = null;
            Map<String, long[]> layers = new LinkedHashMap<>();
            ObjectGroup objects = null;
            boolean mapSeen = false;
            int depth = 0;
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw new IOException("DTD and entity content are forbidden in TMX");
                }
                if (event == XMLStreamConstants.END_ELEMENT) { depth--; continue; }
                if (event != XMLStreamConstants.START_ELEMENT) continue;
                depth++;
                validateXmlNode(xml, depth);
                switch (xml.getLocalName()) {
                    case "map" -> {
                        if (mapSeen || depth != 1) throw new IOException("TMX must contain exactly one map root");
                        mapSeen = true;
                        width = integer(xml, "width");
                        height = integer(xml, "height");
                        if (!"orthogonal".equals(attribute(xml, "orientation"))
                                || integer(xml, "tilewidth") != 16 || integer(xml, "tileheight") != 16
                                || integer(xml, "infinite") != 0 || width % 8 != 0 || height % 8 != 0
                                || width < 1 || height < 1 || width > limits.maxMapWidth()
                                || height > limits.maxMapHeight() || (long) width * height > limits.maxMapCells()) {
                            throw new IOException("TMX must be finite orthogonal 16x16 and block-aligned");
                        }
                    }
                    case "tileset" -> {
                        if (tileset != null) throw new IOException("TMX must contain exactly one tileset");
                        if (integer(xml, "firstgid") != 1) throw new IOException("TMX tileset firstgid must be 1");
                        String source = optional(xml, "source");
                        if (source == null) {
                            tileset = parseTileset(xml, root, root, depth);
                            depth--;
                        }
                        else {
                            Path tsx = contained(root, root, source);
                            validateXmlFileLimits(tsx, "TSX");
                            tileset = parseExternalTileset(tsx, root);
                        }
                    }
                    case "layer" -> {
                        if (width <= 0 || height <= 0) throw new IOException("TMX map must precede its layers");
                        Layer layer = parseLayer(xml, width, height, depth);
                        depth--;
                        String key = layer.name().toUpperCase(java.util.Locale.ROOT);
                        if (!java.util.Set.of("FG", "BG", "COLLISION", "COLLISION_ALT").contains(key)) {
                            throw new IOException("Unknown TMX tile layer: " + layer.name());
                        }
                        if (layers.putIfAbsent(key, layer.gids()) != null) {
                            throw new IOException("Duplicate TMX tile layer: " + layer.name());
                        }
                    }
                    case "objectgroup" -> {
                        if (objects != null) throw new IOException("TMX may contain only one OBJECTS group");
                        if (width <= 0 || height <= 0) throw new IOException("TMX map must precede OBJECTS");
                        objects = parseObjectGroup(xml, width * 16, height * 16, depth);
                        depth--;
                    }
                    case "image", "tileoffset", "transformations", "tile" ->
                            throw new IOException("Tileset-only element appears outside the tileset");
                    default -> throw new IOException("Unsupported TMX element: " + xml.getLocalName());
                }
            }
            xml.close();
            if (width <= 0 || height <= 0 || tileset == null || !layers.containsKey("FG")) {
                throw new IOException("TMX is missing map, image, or FG data");
            }
            if (objects == null) objects = new ObjectGroup(List.of(), List.of(), null);
            return new Parsed(width, height, tileset, Map.copyOf(layers),
                    objects.objects(), objects.rings(), objects.start());
        } catch (XMLStreamException | NumberFormatException error) {
            throw new IOException("Invalid TMX XML", error);
        }
    }

    private void validateXmlFileLimits(Path path, String label) throws IOException {
        if (Files.size(path) > limits.maxMetadataBytes()) throw new IOException(label + " exceeds metadata byte limit");
        String text = Files.readString(path);
        if (text.codePointCount(0, text.length()) > limits.maxDocumentCodePoints()) {
            throw new IOException(label + " exceeds document code-point limit");
        }
    }

    private ObjectGroup parseObjectGroup(XMLStreamReader xml, int pixelWidth, int pixelHeight, int initialDepth)
            throws IOException, XMLStreamException {
        if (!"OBJECTS".equalsIgnoreCase(attribute(xml, "name"))
                || !decimalZero(xml, "offsetx") || !decimalZero(xml, "offsety")
                || !decimalZero(xml, "x") || !decimalZero(xml, "y")) {
            throw new IOException("Only one zero-offset OBJECTS group is supported");
        }
        List<ObjectMarker> objects = new ArrayList<>();
        List<Point> rings = new ArrayList<>();
        Point start = null;
        int depth = initialDepth;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                if (depth == initialDepth && "objectgroup".equals(xml.getLocalName())) break;
                depth--; continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            depth++; validateXmlNode(xml, depth);
            if (!"object".equals(xml.getLocalName()) || depth != initialDepth + 1) {
                throw new IOException("OBJECTS group may contain only point objects");
            }
            Marker marker = parseMarker(xml, pixelWidth, pixelHeight, depth);
            depth--;
            switch (marker.kind()) {
                case "object" -> {
                    if (objects.size() >= limits.maxLevelObjects()) throw new IOException("TMX object count exceeds configured limit");
                    objects.add(marker.object());
                }
                case "ring" -> {
                    if (rings.size() >= limits.maxLevelRings()) throw new IOException("TMX ring count exceeds configured limit");
                    rings.add(marker.point());
                }
                case "start" -> {
                    if (start != null) throw new IOException("TMX contains multiple start markers");
                    start = marker.point();
                }
                default -> throw new IOException("Unknown marker class/type: " + marker.kind());
            }
        }
        return new ObjectGroup(List.copyOf(objects), List.copyOf(rings), start);
    }

    private Marker parseMarker(XMLStreamReader xml, int pixelWidth, int pixelHeight, int initialDepth)
            throws IOException, XMLStreamException {
        if (optional(xml, "gid") != null || optional(xml, "template") != null) throw new IOException("Tile/template objects are forbidden");
        int x = integralCoordinate(xml, "x"), y = integralCoordinate(xml, "y");
        if (x < 0 || x >= pixelWidth || y < 0 || y >= pixelHeight) throw new IOException("Marker is outside map pixel bounds");
        if (!decimalZero(xml, "width") || !decimalZero(xml, "height") || !decimalZero(xml, "rotation")) {
            throw new IOException("Marker width, height, and rotation must be zero");
        }
        String modern = optional(xml, "class"), legacy = optional(xml, "type");
        modern = modern == null ? "" : modern;
        legacy = legacy == null ? "" : legacy;
        if (!modern.isEmpty() && !legacy.isEmpty() && !asciiEqualsIgnoreCase(modern, legacy)) {
            throw new IOException("Marker class and legacy type conflict");
        }
        String kind = !modern.isEmpty() ? asciiLower(modern) : asciiLower(legacy);
        if (!java.util.Set.of("object", "ring", "start").contains(kind)) throw new IOException("Unknown or empty marker class/type");
        Map<String, Property> properties = Map.of();
        boolean propertiesSeen = false;
        boolean point = false;
        int depth = initialDepth;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                if (depth == initialDepth && "object".equals(xml.getLocalName())) break;
                depth--; continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            depth++; validateXmlNode(xml, depth);
            switch (xml.getLocalName()) {
                case "point" -> {
                    if (point || depth != initialDepth + 1) throw new IOException("Marker must contain exactly one point");
                    point = true;
                }
                case "properties" -> {
                    if (propertiesSeen || depth != initialDepth + 1) throw new IOException("Duplicate marker properties");
                    propertiesSeen = true;
                    properties = parseProperties(xml, depth);
                    depth--;
                }
                default -> throw new IOException("Marker must be a point, not " + xml.getLocalName());
            }
        }
        if (!point) throw new IOException("Marker must contain a Tiled point element");
        Point position = new Point(x, y);
        if (!"object".equals(kind)) {
            if (!properties.isEmpty()) throw new IOException("Ring/start markers cannot have properties");
            return new Marker(kind, position, null);
        }
        java.util.Set<String> allowed = java.util.Set.of("stockObjectId", "objectKey", "subtype", "respawnTracked");
        if (!allowed.containsAll(properties.keySet())) throw new IOException("Unknown object marker property");
        boolean hasStock = properties.containsKey("stockObjectId"), hasKey = properties.containsKey("objectKey");
        if (hasStock == hasKey) throw new IOException("Object marker needs exactly one stockObjectId or objectKey");
        Integer stock = hasStock ? typedInteger(properties.get("stockObjectId"), "stockObjectId", 0, 255) : null;
        String key = null;
        if (hasKey) {
            Property property = properties.get("objectKey");
            if (property.type() != null && !property.type().equals("string")) throw new IOException("objectKey must be a string property");
            try { key = ModKeySyntax.requireDisplayKey(property.value()); }
            catch (IllegalArgumentException error) { throw new IOException("Invalid objectKey", error); }
        }
        int subtype = properties.containsKey("subtype")
                ? typedInteger(properties.get("subtype"), "subtype", 0, 255) : 0;
        boolean respawn = properties.containsKey("respawnTracked")
                && typedBoolean(properties.get("respawnTracked"), "respawnTracked");
        return new Marker(kind, position, new ObjectMarker(position, stock, key, subtype, respawn));
    }

    private Map<String, Property> parseProperties(XMLStreamReader xml, int initialDepth)
            throws IOException, XMLStreamException {
        Map<String, Property> result = new LinkedHashMap<>();
        int depth = initialDepth;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                if (depth == initialDepth && "properties".equals(xml.getLocalName())) break;
                depth--; continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            depth++; validateXmlNode(xml, depth);
            if (!"property".equals(xml.getLocalName()) || depth != initialDepth + 1) throw new IOException("Invalid property element");
            String name = attribute(xml, "name"), value = optional(xml, "value"), type = optional(xml, "type");
            if (value == null || result.putIfAbsent(name, new Property(type, value)) != null) throw new IOException("Duplicate or valueless object property");
        }
        return Map.copyOf(result);
    }

    private static int typedInteger(Property property, String name, int min, int max) throws IOException {
        if (!"int".equals(property.type())) throw new IOException(name + " must be a typed integer property");
        try {
            if (property.value().length() > 10) throw new NumberFormatException();
            int value = Integer.parseInt(property.value());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) { throw new IOException(name + " is out of range", error); }
    }

    private static boolean typedBoolean(Property property, String name) throws IOException {
        if (!"bool".equals(property.type()) || !(property.value().equals("true") || property.value().equals("false"))) {
            throw new IOException(name + " must be a typed boolean property");
        }
        return Boolean.parseBoolean(property.value());
    }

    private int integralCoordinate(XMLStreamReader xml, String name) throws IOException {
        String value = attribute(xml, name);
        try {
            requireNumericLength(value);
            double number = Double.parseDouble(value);
            if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw new NumberFormatException();
            }
            return (int) number;
        } catch (NumberFormatException error) { throw new IOException("Marker " + name + " must be finite and integral", error); }
    }

    private static boolean asciiEqualsIgnoreCase(String left, String right) throws IOException {
        return asciiLower(left).equals(asciiLower(right));
    }

    private static String asciiLower(String value) throws IOException {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'A' && c <= 'Z') result.append((char) (c + ('a' - 'A')));
            else if (c >= 'a' && c <= 'z') result.append(c);
            else throw new IOException("Marker class/type must contain ASCII letters only");
        }
        return result.toString();
    }

    private Layer parseLayer(XMLStreamReader xml, int mapWidth, int mapHeight, int initialDepth)
            throws IOException, XMLStreamException {
        String name = attribute(xml, "name");
        if (integer(xml, "width") != mapWidth || integer(xml, "height") != mapHeight
                || decimalZero(xml, "offsetx") == false || decimalZero(xml, "offsety") == false
                || decimalZero(xml, "x") == false || decimalZero(xml, "y") == false) {
            throw new IOException("TMX layer dimensions and offsets must match the map at zero offset");
        }
        long[] gids = null;
        int depth = initialDepth;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                if (depth == initialDepth && "layer".equals(xml.getLocalName())) break;
                depth--; continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            depth++; validateXmlNode(xml, depth);
            if (!"data".equals(xml.getLocalName()) || gids != null || depth != initialDepth + 1
                    || !"csv".equals(optional(xml, "encoding")) || optional(xml, "compression") != null) {
                throw new IOException("TMX layer must contain exactly one CSV data element");
            }
            gids = parseCsv(xml.getElementText(), mapWidth * mapHeight);
            depth--;
        }
        if (gids == null) throw new IOException("TMX layer has no CSV data");
        return new Layer(name, gids);
    }

    private boolean decimalZero(XMLStreamReader xml, String name) throws IOException {
        String value = optional(xml, name);
        if (value == null) return true;
        try { requireNumericLength(value); return Double.parseDouble(value) == 0.0; }
        catch (NumberFormatException error) { throw new IOException("Invalid layer offset", error); }
    }

    private Tileset parseExternalTileset(Path tsx, Path root) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        set(factory, XMLInputFactory.SUPPORT_DTD, false);
        set(factory, "javax.xml.stream.isSupportingExternalEntities", false);
        set(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        set(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        set(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (var input = Files.newInputStream(tsx)) {
            XMLStreamReader xml = factory.createXMLStreamReader(input);
            int depth = 0;
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                    throw new IOException("DTD and entity content are forbidden in TSX");
                }
                if (event == XMLStreamConstants.END_ELEMENT) { depth--; continue; }
                if (event != XMLStreamConstants.START_ELEMENT) continue;
                depth++;
                validateXmlNode(xml, depth);
                if (depth != 1 || !"tileset".equals(xml.getLocalName())) {
                    throw new IOException("External TSX root must be tileset");
                }
                if (optional(xml, "source") != null || optional(xml, "firstgid") != null) {
                    throw new IOException("External TSX cannot nest or declare firstgid");
                }
                Tileset result = parseTileset(xml, root, tsx.getParent().toRealPath(), depth);
                while (xml.hasNext()) {
                    int trailing = xml.next();
                    if (trailing == XMLStreamConstants.START_ELEMENT || trailing == XMLStreamConstants.DTD
                            || trailing == XMLStreamConstants.ENTITY_REFERENCE
                            || (trailing == XMLStreamConstants.CHARACTERS && !xml.isWhiteSpace())) {
                        throw new IOException("External TSX has trailing content");
                    }
                }
                xml.close();
                return result;
            }
            throw new IOException("External TSX has no tileset root");
        } catch (XMLStreamException | NumberFormatException error) {
            throw new IOException("Invalid TSX XML", error);
        }
    }

    /** Consumes through the matching tileset end element. */
    private Tileset parseTileset(XMLStreamReader xml, Path root, Path base, int initialDepth)
            throws IOException, XMLStreamException {
        int tileWidth = integer(xml, "tilewidth"), tileHeight = integer(xml, "tileheight");
        int tileCount = integer(xml, "tilecount"), columns = integer(xml, "columns");
        int margin = integer(xml, "margin"), spacing = integer(xml, "spacing");
        if (tileWidth != 16 || tileHeight != 16 || tileCount <= 0 || columns <= 0
                || margin != 0 || spacing != 0 || optional(xml, "objectalignment") != null) {
            throw new IOException("Tileset must be exact 16x16 zero-margin/spacing geometry");
        }
        Path image = null;
        int imageWidth = -1, imageHeight = -1, depth = initialDepth;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                throw new IOException("DTD and entity content are forbidden in tilesets");
            }
            if (event == XMLStreamConstants.END_ELEMENT) {
                if (depth == initialDepth && "tileset".equals(xml.getLocalName())) break;
                depth--;
                continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) continue;
            depth++;
            validateXmlNode(xml, depth);
            if (!"image".equals(xml.getLocalName()) || image != null || depth != initialDepth + 1) {
                throw new IOException("Tileset must contain exactly one root image and no tile metadata");
            }
            String format = optional(xml, "format");
            if (format != null && !"png".equalsIgnoreCase(format)) throw new IOException("Tileset image must be PNG");
            image = contained(root, base, attribute(xml, "source"));
            if (!image.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                throw new IOException("Tileset image must be PNG");
            }
            if (Files.size(image) > limits.maxAssetBytes()) throw new IOException("Tileset image exceeds asset byte limit");
            imageWidth = integer(xml, "width"); imageHeight = integer(xml, "height");
        }
        if (image == null || imageWidth <= 0 || imageHeight <= 0 || imageWidth % 16 != 0 || imageHeight % 16 != 0
                || imageWidth != columns * 16 || tileCount != columns * (imageHeight / 16)) {
            throw new IOException("Tileset declaration contradicts image geometry");
        }
        return new Tileset(image, imageWidth, imageHeight, columns, tileCount);
    }

    private void validateXmlNode(XMLStreamReader xml, int depth) throws IOException {
        if (depth > limits.maxXmlDepth()) throw new IOException("XML nesting exceeds configured limit");
        if (xml.getAttributeCount() > limits.maxXmlAttributes()) throw new IOException("XML attribute count exceeds configured limit");
        requireXmlString(xml.getLocalName());
        if (xml.getNamespaceURI() != null && !xml.getNamespaceURI().isEmpty()) {
            throw new IOException("Namespaces, XInclude, and schema elements are forbidden in TMX/TSX");
        }
        for (int i = 0; i < xml.getAttributeCount(); i++) {
            requireXmlString(xml.getAttributeLocalName(i));
            requireXmlString(xml.getAttributeValue(i));
            String namespace = xml.getAttributeNamespace(i);
            if (namespace != null && !namespace.isEmpty()) throw new IOException("External schema attributes are forbidden");
        }
    }

    private void requireXmlString(String value) throws IOException {
        if (value == null || value.codePointCount(0, value.length()) > limits.maxStringChars()) {
            throw new IOException("XML string exceeds configured limit");
        }
    }

    private Compiled compile(Parsed parsed, BufferedImage image, Palette palette, int profileCount) throws IOException {
        List<byte[]> patterns = new ArrayList<>();
        patterns.add(new byte[32]);
        Map<Bytes, Integer> patternIds = new LinkedHashMap<>();
        patternIds.put(new Bytes(patterns.getFirst()), 0);
        int[][] tileDescriptors = new int[parsed.tileset().tileCount()][4];
        boolean[] compiledTiles = new boolean[tileDescriptors.length];
        for (String layerName : List.of("FG", "BG")) {
            long[] layer = parsed.layers().get(layerName);
            if (layer == null) continue;
            for (long raw : layer) {
                long gid = raw & GID_MASK;
                if (gid > parsed.tileset().tileCount()) throw new IOException("Visual GID is outside the tileset");
                if (gid != 0 && !compiledTiles[(int) gid - 1]) {
                    compileTile((int) gid - 1, parsed, image, palette, patterns, patternIds, tileDescriptors);
                    compiledTiles[(int) gid - 1] = true;
                }
            }
        }
        List<ChunkRecord> chunks = new ArrayList<>();
        chunks.add(new ChunkRecord(new int[4], 0, 0));
        Map<ChunkKey, Integer> chunkIds = new LinkedHashMap<>();
        chunkIds.put(new ChunkKey(new int[4], 0, 0), 0);
        List<int[]> blocks = new ArrayList<>();
        blocks.add(new int[64]);
        Map<Ints, Integer> blockIds = new LinkedHashMap<>();
        blockIds.put(new Ints(blocks.getFirst()), 0);
        int mapWidth = parsed.width() / 8, mapHeight = parsed.height() / 8;
        byte[] fgMap = compileLayer(parsed, "FG", true, tileDescriptors, profileCount,
                chunks, chunkIds, blocks, blockIds, mapWidth, mapHeight);
        byte[] bgMap = parsed.layers().containsKey("BG")
                ? compileLayer(parsed, "BG", false, tileDescriptors, profileCount,
                chunks, chunkIds, blocks, blockIds, mapWidth, mapHeight) : null;
        return new Compiled(patterns, chunks, blocks, fgMap, bgMap, mapWidth, mapHeight);
    }

    private void compileTile(int tile, Parsed parsed, BufferedImage image, Palette palette,
                             List<byte[]> patterns, Map<Bytes, Integer> patternIds,
                             int[][] tileDescriptors) throws IOException {
            int tileX = (tile % parsed.tileset().columns()) * 16;
            int tileY = (tile / parsed.tileset().columns()) * 16;
            for (int part = 0; part < 4; part++) {
                Pattern pattern = encodePattern(image, tileX + (part & 1) * 8, tileY + (part >>> 1) * 8, palette);
                Integer id = patternIds.get(new Bytes(pattern.bytes()));
                if (id == null) {
                    id = patterns.size(); patterns.add(pattern.bytes()); patternIds.put(new Bytes(pattern.bytes()), id);
                    if (patterns.size() > 2048 || patterns.size() > limits.maxSheetPatterns()) {
                        throw new IOException("TMX pattern count exceeds configured/export limits");
                    }
                }
                tileDescriptors[tile][part] = id | (pattern.paletteLine() << 13);
            }
    }

    private byte[] compileLayer(Parsed parsed, String layerName, boolean foreground, int[][] tileDescriptors,
                                int profileCount, List<ChunkRecord> chunks, Map<ChunkKey, Integer> chunkIds,
                                List<int[]> blocks, Map<Ints, Integer> blockIds, int mapWidth, int mapHeight)
            throws IOException {
        long[] visual = parsed.layers().get(layerName);
        long[] primaryLayer = parsed.layers().get("COLLISION");
        long[] altLayer = parsed.layers().get("COLLISION_ALT");
        byte[] map = new byte[mapWidth * mapHeight];
        for (int by = 0; by < mapHeight; by++) for (int bx = 0; bx < mapWidth; bx++) {
            int[] block = new int[64];
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                int cell = (by * 8 + y) * parsed.width() + bx * 8 + x;
                long raw = visual[cell];
                if ((raw & (DIAGONAL_FLIP | HEX_ROTATION)) != 0) throw new IOException("Diagonal/hex GID flags are forbidden");
                boolean h = (raw & H_FLIP) != 0, v = (raw & V_FLIP) != 0;
                long gid = raw & GID_MASK;
                if (gid > parsed.tileset().tileCount()) throw new IOException("Visual GID is outside the tileset");
                int primary = foreground ? collisionGid(primaryLayer, cell, profileCount) : 0;
                int secondary = foreground ? (altLayer == null ? primary : collisionGid(altLayer, cell, profileCount)) : 0;
                int[] descriptors = gid == 0 ? new int[4] : tileDescriptors[(int) gid - 1];
                ChunkKey key = new ChunkKey(descriptors, primary, secondary);
                Integer chunkId = chunkIds.get(key);
                if (chunkId == null) {
                    chunkId = chunks.size(); chunks.add(new ChunkRecord(descriptors.clone(), primary, secondary));
                    chunkIds.put(key, chunkId);
                    if (chunks.size() > 1024) throw new IOException("TMX chunk count exceeds export limit");
                }
                int primaryMode = primary == 0 ? 0 : 3, secondaryMode = secondary == 0 ? 0 : 3;
                block[y * 8 + x] = chunkId | (h ? 0x400 : 0) | (v ? 0x800 : 0)
                        | (primaryMode << 12) | (secondaryMode << 14);
            }
            Integer id = blockIds.get(new Ints(block));
            if (id == null) {
                id = blocks.size(); blocks.add(block); blockIds.put(new Ints(block), id);
                if (blocks.size() > 256) throw new IOException("TMX block count exceeds export limit");
            }
            map[by * mapWidth + bx] = (byte) (int) id;
        }
        return map;
    }

    private static int collisionGid(long[] layer, int cell, int profileCount) throws IOException {
        if (layer == null) return 0;
        long gid = layer[cell];
        if ((gid & ~GID_MASK) != 0) throw new IOException("Every collision-layer GID flip/rotation flag is forbidden");
        if (gid >= profileCount) throw new IOException("Collision GID has no solid profile");
        return (int) gid;
    }

    private Pattern encodePattern(BufferedImage image, int ox, int oy, Palette palette) throws IOException {
        int selectedLine = -1;
        int[] selected = null;
        for (int line = 0; line < palette.lines(); line++) {
            int[] indices = new int[64];
            boolean matches = true;
            for (int y = 0; y < 8 && matches; y++) for (int x = 0; x < 8; x++) {
                int argb = image.getRGB(ox + x, oy + y);
                int alpha = argb >>> 24;
                if (alpha == 0) indices[y * 8 + x] = 0;
                else if (alpha == 255) {
                    int index = palette.indexOf(line, argb & 0xFFFFFF);
                    if (index < 1) { matches = false; break; }
                    indices[y * 8 + x] = index;
                } else throw new IOException("Tileset image alpha must be 0 or 255");
            }
            if (matches) { selectedLine = line; selected = indices; break; }
        }
        if (selectedLine < 0) throw new IOException("Pattern does not match a palette line at indices 1..15");
        byte[] bytes = new byte[32];
        for (int i = 0; i < 64; i += 2) bytes[i / 2] = (byte) ((selected[i] << 4) | selected[i + 1]);
        return new Pattern(bytes, selectedLine);
    }

    private Palette readPalette(Path file) throws IOException {
        Path path = file.toRealPath();
        long size = Files.size(path);
        if (size > limits.maxAssetBytes() || size > 12 + 4 * 32) throw new IOException("Palette exceeds exact GPAL/asset byte limit");
        byte[] bytes = Files.readAllBytes(path);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (!"GPAL".equals(new String(in.readNBytes(4), java.nio.charset.StandardCharsets.US_ASCII))
                    || in.readUnsignedShort() != 1) throw new IOException("Palette must be exact GPAL v1");
            int lines = in.readUnsignedShort();
            if (lines < 1 || lines > 4 || in.readUnsignedShort() != 16 || in.readUnsignedShort() != 0
                    || bytes.length != 12 + lines * 32) throw new IOException("Palette must be exact GPAL v1");
            int[][] rgb = new int[lines][16];
            for (int line = 0; line < lines; line++) for (int index = 0; index < 16; index++) {
                int word = in.readUnsignedShort();
                int r = (((word >>> 1) & 7) * 255 + 3) / 7;
                int g = (((word >>> 5) & 7) * 255 + 3) / 7;
                int b = (((word >>> 9) & 7) * 255 + 3) / 7;
                rgb[line][index] = (r << 16) | (g << 8) | b;
            }
            return new Palette(bytes, rgb);
        }
    }

    private static Path contained(Path root, Path base, String value) throws IOException {
        try { ModAssetRoot.requireNormalizedEntry(value); }
        catch (IllegalArgumentException invalid) { throw new IOException("External TMX path must be normalized and relative", invalid); }
        Path relative = Path.of(value);
        Path target = base.resolve(relative).normalize().toRealPath();
        if (!target.startsWith(root) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("External TMX path escapes import root or is not a regular file");
        }
        return target;
    }

    private long[] parseCsv(String csv, int count) throws IOException {
        String[] tokens = csv.trim().split("\\s*,\\s*");
        if (tokens.length != count) throw new IOException("TMX CSV cell count does not match map dimensions");
        long[] result = new long[count];
        for (int i = 0; i < count; i++) {
            requireNumericLength(tokens[i]);
            result[i] = Long.parseUnsignedLong(tokens[i]);
            if (Long.compareUnsigned(result[i], 0xFFFF_FFFFL) > 0) throw new IOException("TMX GID exceeds unsigned 32-bit range");
        }
        return result;
    }

    private static String attribute(XMLStreamReader xml, String name) throws IOException {
        String value = optional(xml, name);
        if (value == null) throw new IOException("Missing TMX attribute " + name);
        return value;
    }

    private static String optional(XMLStreamReader xml, String name) {
        return xml.getAttributeValue(null, name);
    }

    private int integer(XMLStreamReader xml, String name) throws IOException {
        String value = attribute(xml, name);
        requireNumericLength(value);
        return Integer.parseInt(value);
    }

    private void requireNumericLength(String value) throws IOException {
        long digits = value.chars().filter(Character::isDigit).count();
        if (digits == 0 || digits > limits.maxNumericDigits()) throw new IOException("XML numeric token exceeds configured limit");
    }

    private static void set(XMLInputFactory factory, String property, Object value) throws IOException {
        try { factory.setProperty(property, value); }
        catch (IllegalArgumentException unsupported) { throw new IOException("XML implementation cannot enforce " + property, unsupported); }
    }

    public record Result(Path output, int patternCount, int chunkCount, int blockCount, List<String> warnings) { }
    record Parsed(int width, int height, Tileset tileset, Map<String, long[]> layers,
                          List<ObjectMarker> objects, List<Point> rings, Point start) { }
    private record Tileset(Path image, int imageWidth, int imageHeight, int columns, int tileCount) { }
    private record Layer(String name, long[] gids) { }
    record Point(int x, int y) { }
    record ObjectMarker(Point position, Integer stockObjectId, String objectKey,
                                int subtype, boolean respawnTracked) { }
    private record Marker(String kind, Point point, ObjectMarker object) { }
    private record ObjectGroup(List<ObjectMarker> objects, List<Point> rings, Point start) { }
    private record Property(String type, String value) { }
    record Palette(byte[] bytes, int[][] rgb) {
        int lines() { return rgb.length; }
        int indexOf(int line, int color) {
            for (int index = 1; index < 16; index++) if (rgb[line][index] == color) return index;
            return -1;
        }
    }
    private record Pattern(byte[] bytes, int paletteLine) { }
    record ChunkRecord(int[] descriptors, int primary, int secondary) { }
    record Compiled(List<byte[]> patterns, List<ChunkRecord> chunks, List<int[]> blocks,
                            byte[] fgMap, byte[] bgMap, int mapWidth, int mapHeight) { }
    record Profiles(byte[] heights, byte[] widths, byte[] angles, int count) { }
    private record Bytes(byte[] value) {
        @Override public boolean equals(Object other) { return other instanceof Bytes bytes && Arrays.equals(value, bytes.value); }
        @Override public int hashCode() { return Arrays.hashCode(value); }
    }
    private record Ints(int[] value) {
        @Override public boolean equals(Object other) { return other instanceof Ints ints && Arrays.equals(value, ints.value); }
        @Override public int hashCode() { return Arrays.hashCode(value); }
    }
    private record ChunkKey(int[] descriptors, int primary, int secondary) {
        @Override public boolean equals(Object other) {
            return other instanceof ChunkKey key && primary == key.primary && secondary == key.secondary
                    && Arrays.equals(descriptors, key.descriptors);
        }
        @Override public int hashCode() { return 31 * (31 * Arrays.hashCode(descriptors) + primary) + secondary; }
    }
}
