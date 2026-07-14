package com.openggf.tools.modsdk;

import com.openggf.io.ModInputLimits;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.TrackKey;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModLevelDefinitionParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTmxLevelImporter {
    private static final Set<String> PHASE_2_INVENTORY = Set.of(
            "level.json", "patterns.bin", "chunks.bin", "blocks.bin", "fg-map.bin",
            "solid-heights.bin", "solid-widths.bin", "solid-angles.bin",
            "collision-primary.bin", "collision-secondary.bin", "palettes.bin");

    @TempDir
    Path temp;

    @Test
    void minimalEmbeddedTilesetReservesBlankHierarchyAndEmitsExactPhase2Inventory() throws Exception {
        Path sheet = temp.resolve("chunks.png");
        writeSolidChunk(sheet, 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("minimal.tmx");
        Files.writeString(tmx, """
                <?xml version="1.0" encoding="UTF-8"?>
                <map version="1.10" tiledversion="1.11.2" orientation="orthogonal"
                     renderorder="right-down" width="8" height="8" tilewidth="16" tileheight="16"
                     infinite="0" nextlayerid="2" nextobjectid="1">
                  <tileset firstgid="1" name="chunks" tilewidth="16" tileheight="16"
                           tilecount="1" columns="1" margin="0" spacing="0">
                    <image source="chunks.png" width="16" height="16"/>
                  </tileset>
                  <layer id="1" name="FG" width="8" height="8" offsetx="0" offsety="0">
                    <data encoding="csv">1,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0</data>
                  </layer>
                </map>
                """);
        Path output = temp.resolve("out");

        new TmxLevelImporter().importLevel(tmx, palette, null, output);

        try (var files = Files.list(output)) {
            assertEquals(PHASE_2_INVENTORY,
                    files.map(path -> path.getFileName().toString()).collect(Collectors.toSet()));
        }
        assertEquals(2, recordCount(output.resolve("patterns.bin")));
        assertEquals(2, recordCount(output.resolve("chunks.bin")));
        assertEquals(2, recordCount(output.resolve("blocks.bin")));
        assertAllZero(output.resolve("patterns.bin"), 12, 32);
        assertAllZero(output.resolve("chunks.bin"), 12, 8);
        assertAllZero(output.resolve("blocks.bin"), 12, 8 * 8 * 2);
        assertTrue(Files.readString(output.resolve("level.json")).contains("\"blockGridSide\":8"));
    }

    @Test
    void containedExternalTsxAndImageAreAccepted() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        Files.writeString(temp.resolve("chunks.tsx"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <tileset version="1.10" tiledversion="1.11.2" name="chunks"
                         tilewidth="16" tileheight="16" tilecount="1" columns="1"
                         margin="0" spacing="0">
                  <image source="chunks.png" width="16" height="16"/>
                </tileset>
                """);
        Path tmx = temp.resolve("external.tmx");
        Files.writeString(tmx, mapWithTilesets("<tileset firstgid=\"1\" source=\"chunks.tsx\"/>"));

        var result = new TmxLevelImporter().importLevel(tmx, palette, null, temp.resolve("external-out"));

        assertEquals(2, result.patternCount());
        assertEquals(2, result.chunkCount());
        assertEquals(2, result.blockCount());
    }

    @Test
    void rejectsExternalPathEscapeAbsolutePathAndSymlinkEscape() throws Exception {
        Path outside = Files.createTempDirectory("tmx-outside-");
        try {
            writeSolidChunk(outside.resolve("outside.png"), 0xFF0000FF);
            Files.writeString(outside.resolve("outside.tsx"), tileset("outside.png", ""));
            assertImportRejected(mapWithTilesets("<tileset firstgid=\"1\" source=\"../"
                    + outside.getFileName() + "/outside.tsx\"/>"), "dot-dot");
            String absolute = outside.resolve("outside.tsx").toString().replace('\\', '/');
            assertImportRejected(mapWithTilesets("<tileset firstgid=\"1\" source=\""
                    + absolute + "\"/>"), "absolute");

            Path link = temp.resolve("linked.tsx");
            boolean linked;
            try {
                Files.createSymbolicLink(link, outside.resolve("outside.tsx"));
                linked = true;
            } catch (IOException | UnsupportedOperationException denied) {
                linked = false;
            }
            assumeTrue(linked, "symbolic links unavailable on this host");
            assertImportRejected(mapWithTilesets("<tileset firstgid=\"1\" source=\"linked.tsx\"/>"),
                    "symlink escape");
        } finally {
            try (var paths = Files.walk(outside)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void rejectsDtdEntitiesXIncludeAndExternalSchemaHints() throws Exception {
        assertImportRejected("""
                <?xml version="1.0"?>
                <!DOCTYPE map [<!ENTITY leak SYSTEM "file:///definitely-not-readable">]>
                """ + embeddedMap("&leak;"), "DTD/entity");
        assertImportRejected(embeddedMap("<xi:include xmlns:xi=\"http://www.w3.org/2001/XInclude\" href=\"chunks.tsx\"/>") ,
                "XInclude");
        assertImportRejected(embeddedMap("<schema xmlns=\"http://www.w3.org/2001/XMLSchema\"/>"),
                "external schema");
    }

    @Test
    void rejectsTilesetAndImageGeometryContradictions() throws Exception {
        String[] invalidTilesets = {
                embeddedTileset("").replace("margin=\"0\"", "margin=\"1\""),
                embeddedTileset("").replace("spacing=\"0\"", "spacing=\"1\""),
                embeddedTileset("").replace("columns=\"1\"", "columns=\"2\""),
                embeddedTileset("").replace("tilecount=\"1\"", "tilecount=\"2\""),
                embeddedTileset("").replace("width=\"16\"", "width=\"32\""),
                embeddedTileset("objectalignment=\"center\""),
                embeddedTileset("<tileoffset x=\"0\" y=\"0\"/>"),
                embeddedTileset("<transformations hflip=\"1\" vflip=\"0\" rotate=\"0\" preferuntransformed=\"0\"/>"),
                embeddedTileset("<tile id=\"0\"><image source=\"chunks.png\" width=\"16\" height=\"16\"/></tile>")
        };
        for (int i = 0; i < invalidTilesets.length; i++) {
            assertImportRejected(mapWithTilesets(invalidTilesets[i]), "geometry " + i);
        }
        assertImportRejected(mapWithTilesets(embeddedTileset("") + embeddedTileset("")), "multiple tilesets");
        assertImportRejected(mapWithTilesets(embeddedTileset("").replace("firstgid=\"1\"", "firstgid=\"2\"")),
                "firstgid");
    }

    @Test
    void injectedXmlDepthAndAttributeLimitsAreEnforced() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("limits.tmx");
        Files.writeString(tmx, embeddedMap(""));

        var shallow = ModInputLimits.loweringBuilder().maxXmlDepth(1).build();
        assertThrows(IOException.class, () -> new TmxLevelImporter(shallow).importLevel(
                tmx, palette, null, temp.resolve("depth-out")));
        var fewAttributes = ModInputLimits.loweringBuilder().maxXmlAttributes(2).build();
        assertThrows(IOException.class, () -> new TmxLevelImporter(fewAttributes).importLevel(
                tmx, palette, null, temp.resolve("attributes-out")));
    }

    @Test
    void layersFlipsCollisionIdentityAndCustomProfilesCompileToExactDescriptors() throws Exception {
        writeTwoChunkSheet(temp.resolve("chunks.png"));
        Path palette = temp.resolve("palette.bin");
        writePalette(palette, new int[][]{{0, 7 << 9, 7 << 5}, {0, 7 << 9, 7 << 5}});
        Path profiles = temp.resolve("profiles");
        writeProfiles(profiles, 3);
        long[] fg = cells(0x80000001L, 0x40000001L, 2);
        long[] bg = cells(0xC0000002L);
        long[] collision = cells(1, 0, 2);
        long[] alt = cells(0, 1, 2);
        Path tmx = temp.resolve("layers.tmx");
        Files.writeString(tmx, completeMapWithTileCount(2, fg,
                tileLayer("BG", bg, ""), tileLayer("COLLISION", collision, ""),
                tileLayer("COLLISION_ALT", alt, "")));

        new TmxLevelImporter().importLevel(tmx, palette, profiles, temp.resolve("layers-out"));

        Path out = temp.resolve("layers-out");
        assertEquals(3, recordCount(out.resolve("patterns.bin")));
        assertEquals(5, recordCount(out.resolve("chunks.bin")));
        assertEquals(3, recordCount(out.resolve("blocks.bin")));
        assertEquals(0x3401, unsignedShort(out.resolve("blocks.bin"), 12 + 128));
        assertEquals(0xC802, unsignedShort(out.resolve("blocks.bin"), 12 + 128 + 2));
        assertEquals(0xF003, unsignedShort(out.resolve("blocks.bin"), 12 + 128 + 4));
        assertEquals(0x0C04, unsignedShort(out.resolve("blocks.bin"), 12 + 2 * 128));
        assertEquals(1, unsignedShort(out.resolve("collision-primary.bin"), 12 + 2));
        assertEquals(0, unsignedShort(out.resolve("collision-secondary.bin"), 12 + 2));
        assertEquals(0, unsignedShort(out.resolve("collision-primary.bin"), 12 + 4));
        assertEquals(1, unsignedShort(out.resolve("collision-secondary.bin"), 12 + 4));
        assertEquals(2, unsignedShort(out.resolve("collision-primary.bin"), 12 + 6));
        assertEquals(2, unsignedShort(out.resolve("collision-secondary.bin"), 12 + 6));
        assertEquals(3, recordCount(out.resolve("solid-heights.bin")));
        assertEquals(3, recordCount(out.resolve("solid-widths.bin")));
        assertEquals(3, recordCount(out.resolve("solid-angles.bin")));
    }

    @Test
    void missingAltCopiesPrimaryAndInvisibleCollisionGetsNonzeroBlankPatternChunk() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("invisible.tmx");
        Files.writeString(tmx, completeMap(cells(), tileLayer("COLLISION", cells(1), "")));

        new TmxLevelImporter().importLevel(tmx, palette, null, temp.resolve("invisible-out"));

        Path out = temp.resolve("invisible-out");
        assertEquals(1, recordCount(out.resolve("patterns.bin")));
        assertEquals(2, recordCount(out.resolve("chunks.bin")));
        assertEquals(2, recordCount(out.resolve("blocks.bin")));
        assertEquals(0xF001, unsignedShort(out.resolve("blocks.bin"), 12 + 128));
        assertEquals(1, unsignedShort(out.resolve("collision-primary.bin"), 12 + 2));
        assertEquals(1, unsignedShort(out.resolve("collision-secondary.bin"), 12 + 2));
        assertAllZero(out.resolve("chunks.bin"), 12 + 8, 8);
    }

    @Test
    void rejectsUnknownDuplicateMissingMisSizedOffsetAndUnsupportedLayerData() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        String[] invalid = {
                completeMap(cells(1), tileLayer("UNKNOWN", cells(), "")),
                completeMap(cells(1), tileLayer("fg", cells(), "")),
                completeMap(cells(1)).replace("name=\"FG\"", "name=\"NO_FG\""),
                completeMap(cells(1)).replace("width=\"8\" height=\"8\" offsetx", "width=\"7\" height=\"8\" offsetx"),
                completeMap(cells(1)).replace("offsetx=\"0\"", "offsetx=\"1\""),
                completeMap(cells(1)).replace("offsety=\"0\"", "offsety=\"1\""),
                completeMap(cells(1)).replace("encoding=\"csv\"", "encoding=\"base64\""),
                completeMap(cells(1)).replaceFirst(",0,0,0,0,0,0,0", "")
        };
        for (int i = 0; i < invalid.length; i++) assertImportRejected(invalid[i], "layer-rule-" + i);
    }

    @Test
    void rejectsDiagonalHexAndEveryCollisionFlipWhileAllowingOnlyFgBgHv() throws Exception {
        long[] forbidden = {0x20000001L, 0x10000001L, 0x80000001L, 0x40000001L};
        for (long gid : forbidden) {
            assertImportRejected(completeMap(cells(1), tileLayer("COLLISION", cells(gid), "")),
                    "collision-flip-" + Long.toHexString(gid));
        }
        for (long gid : new long[]{0x20000001L, 0x10000001L}) {
            assertImportRejected(completeMap(cells(gid)), "fg-diagonal-" + Long.toHexString(gid));
            assertImportRejected(completeMap(cells(1), tileLayer("BG", cells(gid), "")),
                    "bg-diagonal-" + Long.toHexString(gid));
        }
    }

    @Test
    void paletteUsesLowestWholePatternLineAndLowestDuplicateIndexAndRejectsBadAlpha() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, 0xFF0000FF);
        assertTrue(ImageIO.write(image, "png", temp.resolve("chunks.png").toFile()));
        Path palette = temp.resolve("palette.bin");
        writePalette(palette, new int[][]{{7 << 9, 0}, {0, 0, 7 << 9, 7 << 9}});
        Path tmx = temp.resolve("palette-lines.tmx");
        Files.writeString(tmx, completeMap(cells(1)));

        new TmxLevelImporter().importLevel(tmx, palette, null, temp.resolve("palette-out"));

        assertEquals(0x2001, unsignedShort(temp.resolve("palette-out/chunks.bin"), 12 + 8));
        assertEquals(0x22, Byte.toUnsignedInt(Files.readAllBytes(temp.resolve("palette-out/patterns.bin"))[44]));

        image.setRGB(0, 0, 0x800000FF);
        assertTrue(ImageIO.write(image, "png", temp.resolve("chunks.png").toFile()));
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(
                tmx, palette, null, temp.resolve("partial-alpha-out")));
    }

    @Test
    void defaultProfilesRestrictCollisionToZeroOrOneAndCustomCountsMustMatch() throws Exception {
        assertImportRejected(completeMap(cells(1), tileLayer("COLLISION", cells(2), "")),
                "default-profile-gid");
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        Path profiles = temp.resolve("bad-profiles");
        writeProfiles(profiles, 3);
        Files.write(profiles.resolve("solid-angles.bin"), profileBytes("GSAN", 1, 2));
        Path tmx = temp.resolve("bad-profile-map.tmx");
        Files.writeString(tmx, completeMap(cells(1)));
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(
                tmx, palette, profiles, temp.resolve("bad-profile-out")));
    }

    @Test
    void typedPointMarkersProduceOrderedObjectsRingsAndExactStartMetadata() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        String markers = """
                <objectgroup name="OBJECTS" offsetx="0" offsety="0">
                  <object id="9" class="object" type="OBJECT" x="16" y="32" width="0" height="0" rotation="0">
                    <properties>
                      <property name="stockObjectId" type="int" value="42"/>
                      <property name="subtype" type="int" value="7"/>
                      <property name="respawnTracked" type="bool" value="true"/>
                    </properties><point/>
                  </object>
                  <object id="10" type="object" x="24" y="40">
                    <properties><property name="objectKey" value="owner:badnik"/></properties><point/>
                  </object>
                  <object id="11" class="ring" x="48" y="64"><point/></object>
                  <object id="12" class="start" x="80" y="96"><point/></object>
                </objectgroup>
                """;
        Path tmx = temp.resolve("objects.tmx");
        Files.writeString(tmx, completeMap(cells(1), markers));

        var result = new TmxLevelImporter().importLevel(tmx, palette, null, temp.resolve("objects-out"));

        assertTrue(result.warnings().isEmpty());
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                Files.readAllBytes(temp.resolve("objects-out/level.json")));
        assertEquals(128, json.path("bounds").path("maxX").asInt());
        assertEquals(128, json.path("bounds").path("maxY").asInt());
        assertEquals(80, json.path("start").path("x").asInt());
        assertEquals(96, json.path("start").path("y").asInt());
        assertEquals(2, json.path("objects").size());
        assertEquals(0, json.path("objects").get(0).path("placementId").asInt());
        assertEquals(16, json.path("objects").get(0).path("x").asInt());
        assertEquals(32, json.path("objects").get(0).path("y").asInt());
        assertEquals(42, json.path("objects").get(0).path("stockObjectId").asInt());
        assertEquals(7, json.path("objects").get(0).path("subtype").asInt());
        assertTrue(json.path("objects").get(0).path("respawnTracked").asBoolean());
        assertEquals(0x8020, json.path("objects").get(0).path("rawYWord").asInt());
        assertEquals(1, json.path("objects").get(1).path("placementId").asInt());
        assertEquals("owner:badnik", json.path("objects").get(1).path("objectKey").asText());
        assertEquals(0, json.path("objects").get(1).path("subtype").asInt());
        assertEquals(1, json.path("rings").size());
        assertEquals(0, json.path("rings").get(0).path("placementId").asInt());
        assertEquals(48, json.path("rings").get(0).path("x").asInt());
        assertEquals(64, json.path("rings").get(0).path("y").asInt());
    }

    @Test
    void namespacedMusicKeyImportsAsTrackMusicInsteadOfStockPlaceholder() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("music.tmx");
        Files.writeString(tmx, completeMap(cells(1)));

        new TmxLevelImporter().importLevel(tmx, palette, null, temp.resolve("music-out"),
                new TrackKey("sample-platformer", "zone-theme"));

        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                Files.readAllBytes(temp.resolve("music-out/level.json")));
        assertEquals("sample-platformer", json.path("music").path("trackKey").path("modId").asText());
        assertEquals("zone-theme", json.path("music").path("trackKey").path("name").asText());
        assertTrue(json.path("music").path("stockId").isMissingNode(),
                "A namespaced track must replace, not accompany, the stock-id placeholder");
    }

    @Test
    void absentStartUsesExact128CoordinatesAndReturnsWarning() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("default-start.tmx");
        Files.writeString(tmx, completeMap(cells(1)));

        var result = new TmxLevelImporter().importLevel(tmx, palette, null, temp.resolve("default-start-out"));

        assertEquals(List.of("TMX has no start marker; using 128,128"), result.warnings());
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                Files.readAllBytes(temp.resolve("default-start-out/level.json")));
        assertEquals(128, json.path("start").path("x").asInt());
        assertEquals(128, json.path("start").path("y").asInt());
    }

    @Test
    void rejectsMarkerClassConflictIdentityAndTypedPropertyErrors() throws Exception {
        String[] invalidObjects = {
                "<object class=\"object\" type=\"ring\" x=\"1\" y=\"1\"><properties><property name=\"stockObjectId\" type=\"int\" value=\"1\"/></properties><point/></object>",
                "<object class=\"object\" x=\"1\" y=\"1\"><point/></object>",
                "<object class=\"object\" x=\"1\" y=\"1\"><properties><property name=\"stockObjectId\" type=\"int\" value=\"1\"/><property name=\"objectKey\" value=\"owner:key\"/></properties><point/></object>",
                "<object class=\"object\" x=\"1\" y=\"1\"><properties><property name=\"stockObjectId\" value=\"1\"/></properties><point/></object>",
                "<object class=\"object\" x=\"1\" y=\"1\"><properties><property name=\"respawnTracked\" type=\"int\" value=\"1\"/><property name=\"stockObjectId\" type=\"int\" value=\"1\"/></properties><point/></object>",
                "<object class=\"object\" x=\"1\" y=\"1\"><properties><property name=\"unknown\" value=\"x\"/><property name=\"stockObjectId\" type=\"int\" value=\"1\"/></properties><point/></object>",
                "<object class=\"object\" x=\"1\" y=\"1\"><properties><property name=\"stockObjectId\" type=\"int\" value=\"1\"/><property name=\"stockObjectId\" type=\"int\" value=\"2\"/></properties><point/></object>",
                "<object class=\"unknown\" x=\"1\" y=\"1\"><point/></object>"
        };
        for (int i = 0; i < invalidObjects.length; i++) {
            assertImportRejected(completeMap(cells(1), objectGroup(invalidObjects[i])), "object-property-" + i);
        }
    }

    @Test
    void rejectsNonPointFractionalSizedRotatedTileAndOutOfBoundsMarkers() throws Exception {
        String[] invalidObjects = {
                "<object class=\"ring\" x=\"1\" y=\"1\"/>",
                "<object class=\"ring\" x=\"1.5\" y=\"1\"><point/></object>",
                "<object class=\"ring\" x=\"1\" y=\"1\" width=\"1\"><point/></object>",
                "<object class=\"ring\" x=\"1\" y=\"1\" height=\"1\"><point/></object>",
                "<object class=\"ring\" x=\"1\" y=\"1\" rotation=\"1\"><point/></object>",
                "<object class=\"ring\" gid=\"1\" x=\"1\" y=\"1\"><point/></object>",
                "<object class=\"ring\" x=\"1\" y=\"1\"><polygon points=\"0,0 1,1\"/></object>",
                "<object class=\"ring\" x=\"128\" y=\"1\"><point/></object>",
                "<object class=\"ring\" x=\"-1\" y=\"1\"><point/></object>"
        };
        for (int i = 0; i < invalidObjects.length; i++) {
            assertImportRejected(completeMap(cells(1), objectGroup(invalidObjects[i])), "object-geometry-" + i);
        }
        String twoStarts = objectGroup("<object class=\"start\" x=\"1\" y=\"1\"><point/></object>"
                + "<object class=\"start\" x=\"2\" y=\"2\"><point/></object>");
        assertImportRejected(completeMap(cells(1), twoStarts), "multiple-starts");
        assertImportRejected(completeMap(cells(1), "<objectgroup name=\"OTHER\"/>"), "unknown-object-group");
        assertImportRejected(completeMap(cells(1), objectGroup("") + objectGroup("")), "duplicate-object-group");
        assertImportRejected(completeMap(cells(1), "<objectgroup name=\"OBJECTS\" offsetx=\"1\"/>"), "object-group-offset");
    }

    @Test
    void repeatedImportIsByteIdenticalPinnedAndAcceptedByPhase2Parser() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("golden.tmx");
        Files.writeString(tmx, completeMap(cells(1), tileLayer("COLLISION", cells(1), ""),
                objectGroup("<object class=\"ring\" x=\"32\" y=\"48\"><point/></object>"
                        + "<object class=\"start\" x=\"64\" y=\"80\"><point/></object>")));

        Path first = temp.resolve("golden-one"), second = temp.resolve("golden-two");
        new TmxLevelImporter().importLevel(tmx, palette, null, first);
        new TmxLevelImporter().importLevel(tmx, palette, null, second);

        assertDirectoriesEqual(first, second);
        assertEquals("6f719f42e77af5949c9a0ca6c45676de07d2a304ad903bbd2df6cb8bbee94e88",
                aggregateSha256(first));
        try (ModAssetRoot assets = ModAssetRoot.directory(temp, first,
                ModInputLimits.production(), DirectoryAccess.TEST)) {
            var definition = ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json"));
            assertEquals(2, definition.patternCount());
            assertEquals(2, definition.chunkCount());
            assertEquals(2, definition.blockCount());
            assertEquals(1, definition.rings().size());
            assertEquals(128, definition.bounds().maxX());
            assertEquals(128, definition.bounds().maxY());
        }
    }

    @Test
    void emptyFgAndPresentEmptyBgUseOnlyReservedHierarchyWhileMissingBgIsOmitted() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        Path withBg = temp.resolve("empty-bg.tmx");
        Files.writeString(withBg, completeMap(cells(), tileLayer("BG", cells(), "")));

        new TmxLevelImporter().importLevel(withBg, palette, null, temp.resolve("empty-bg-out"));

        Path output = temp.resolve("empty-bg-out");
        assertEquals(1, recordCount(output.resolve("patterns.bin")));
        assertEquals(1, recordCount(output.resolve("chunks.bin")));
        assertEquals(1, recordCount(output.resolve("blocks.bin")));
        assertTrue(Files.exists(output.resolve("bg-map.bin")));
        assertEquals(0, Byte.toUnsignedInt(Files.readAllBytes(output.resolve("bg-map.bin"))[16]));
        Path missing = temp.resolve("missing-bg.tmx");
        Files.writeString(missing, completeMap(cells()));
        new TmxLevelImporter().importLevel(missing, palette, null, temp.resolve("missing-bg-out"));
        assertTrue(Files.notExists(temp.resolve("missing-bg-out/bg-map.bin")));
    }

    @Test
    void injectedMapObjectRingPatternAndImageLimitsIncludeReservedEntries() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("bounded.tmx");
        Files.writeString(tmx, completeMap(cells(1)));
        assertThrows(IOException.class, () -> new TmxLevelImporter(ModInputLimits.loweringBuilder()
                .maxMapWidth(7).build()).importLevel(tmx, palette, null, temp.resolve("width-limit")));
        assertThrows(IOException.class, () -> new TmxLevelImporter(ModInputLimits.loweringBuilder()
                .maxMapCells(63).build()).importLevel(tmx, palette, null, temp.resolve("cell-limit")));
        assertThrows(IOException.class, () -> new TmxLevelImporter(ModInputLimits.loweringBuilder()
                .maxSheetPatterns(1).build()).importLevel(tmx, palette, null, temp.resolve("pattern-limit")));
        assertThrows(IOException.class, () -> new TmxLevelImporter(ModInputLimits.loweringBuilder()
                .maxImageWidth(8).build()).importLevel(tmx, palette, null, temp.resolve("image-limit")));

        String rings = objectGroup("<object class=\"ring\" x=\"1\" y=\"1\"><point/></object>"
                + "<object class=\"ring\" x=\"2\" y=\"2\"><point/></object>");
        Path ringMap = temp.resolve("ring-limit.tmx"); Files.writeString(ringMap, completeMap(cells(1), rings));
        assertThrows(IOException.class, () -> new TmxLevelImporter(ModInputLimits.loweringBuilder()
                .maxLevelRings(1).build()).importLevel(ringMap, palette, null, temp.resolve("ring-limit")));
        String objects = objectGroup(stockObject(1, 1) + stockObject(2, 2));
        Path objectMap = temp.resolve("object-limit.tmx"); Files.writeString(objectMap, completeMap(cells(1), objects));
        assertThrows(IOException.class, () -> new TmxLevelImporter(ModInputLimits.loweringBuilder()
                .maxLevelObjects(1).build()).importLevel(objectMap, palette, null, temp.resolve("object-limit")));
    }

    @Test
    void rejectsOpaqueIndexZeroOnlyUnmatchedAndMoreThanFifteenOpaqueColors() throws Exception {
        Path tmx = temp.resolve("palette-reject.tmx"); Files.writeString(tmx, completeMap(cells(1)));
        Path palette = temp.resolve("palette.bin");
        writePalette(palette, new int[][]{{7 << 9}});
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(
                tmx, palette, null, temp.resolve("index-zero-only")));

        writePalette(palette, new int[][]{{0, 7 << 5}});
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(
                tmx, palette, null, temp.resolve("unmatched")));

        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 16; i++) image.setRGB(i % 8, i / 8, 0xFF000000 | (i * 0x010101));
        assertTrue(ImageIO.write(image, "png", temp.resolve("chunks.png").toFile()));
        int[] line = new int[16];
        for (int i = 1; i < 16; i++) line[i] = genesisGray(i);
        writePalette(palette, new int[][]{line});
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(
                tmx, palette, null, temp.resolve("sixteen-colors")));
    }

    @Test
    void noClobberPreservesExistingOutputAndInvalidInputPublishesNothing() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin"); writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("publish.tmx"); Files.writeString(tmx, completeMap(cells(1)));
        Path existing = temp.resolve("existing"); Files.createDirectory(existing);
        Files.writeString(existing.resolve("owner.txt"), "keep");
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(tmx, palette, null, existing));
        assertEquals("keep", Files.readString(existing.resolve("owner.txt")));

        Path invalid = temp.resolve("invalid-publish.tmx");
        Files.writeString(invalid, completeMap(cells(1)).replace("orientation=\"orthogonal\"", "orientation=\"isometric\""));
        Path target = temp.resolve("invalid-target");
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(invalid, palette, null, target));
        assertTrue(Files.notExists(target));
        try (var children = Files.list(temp)) {
            assertTrue(children.noneMatch(path -> path.getFileName().toString().startsWith("invalid-target.tmp-")));
        }
    }

    @Test
    void fixedChunkCapIncludesReservedZeroAndFailurePublishesNothing() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin"); writePaletteWithBlueAtIndexOne(palette);
        Path profiles = temp.resolve("many-profiles"); writeProfiles(profiles, 256);
        int chunkWidth = 128, chunkHeight = 8;
        long[] blank = new long[chunkWidth * chunkHeight];
        long[] primary = new long[blank.length], secondary = new long[blank.length];
        for (int i = 0; i < blank.length; i++) {
            primary[i] = 1 + i / 255;
            secondary[i] = 1 + i % 255;
        }
        Path chunks = temp.resolve("too-many-chunks.tmx");
        Files.writeString(chunks, arbitraryMap(chunkWidth, chunkHeight, blank,
                arbitraryLayer("COLLISION", chunkWidth, chunkHeight, primary),
                arbitraryLayer("COLLISION_ALT", chunkWidth, chunkHeight, secondary)));
        Path target = temp.resolve("too-many-chunks-out");
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(chunks, palette, profiles, target));
        assertTrue(Files.notExists(target));
        assertNoTemporaryOutput("too-many-chunks-out");
    }

    @Test
    void fixedBlockCapIncludesReservedZeroAndFailurePublishesNothing() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin"); writePaletteWithBlueAtIndexOne(palette);
        int blockWidth = 8 * 257, blockHeight = 8;
        long[] varied = new long[blockWidth * blockHeight];
        for (int block = 0; block < 257; block++) {
            int local = block % 64;
            long flags = ((block / 64) & 1) != 0 ? 0x80000000L : 0;
            if (((block / 64) & 2) != 0) flags |= 0x40000000L;
            varied[(local / 8) * blockWidth + block * 8 + local % 8] = flags | 1;
        }
        varied[256 * 8] = 1;
        varied[256 * 8 + 1] = 1;
        Path blocks = temp.resolve("too-many-blocks.tmx");
        Files.writeString(blocks, arbitraryMap(blockWidth, blockHeight, varied));
        Path target = temp.resolve("too-many-blocks-out");
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(blocks, palette, null, target));
        assertTrue(Files.notExists(target));
        assertNoTemporaryOutput("too-many-blocks-out");
    }

    private void assertNoTemporaryOutput(String prefix) throws IOException {
        try (var children = Files.list(temp)) {
            assertTrue(children.noneMatch(path -> path.getFileName().toString().startsWith(prefix + ".tmp-")));
        }
    }

    @Test
    void injectedDocumentAndNumericTokenLimitsAreEnforced() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin"); writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("token-limits.tmx"); Files.writeString(tmx, completeMap(cells(1)));
        assertThrows(IOException.class, () -> new TmxLevelImporter(ModInputLimits.loweringBuilder()
                .maxDocumentCodePoints(100).build()).importLevel(tmx, palette, null, temp.resolve("document-limit")));
        assertThrows(IOException.class, () -> new TmxLevelImporter(ModInputLimits.loweringBuilder()
                .maxNumericDigits(1).build()).importLevel(tmx, palette, null, temp.resolve("numeric-limit")));
    }

    @Test
    void canonicalRoundedGenesisIntermediateComponentMatchesPngExactly() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF000049);
        Path palette = temp.resolve("palette.bin");
        writePalette(palette, new int[][]{{0, 2 << 9}});
        Path tmx = temp.resolve("rounded-component.tmx"); Files.writeString(tmx, completeMap(cells(1)));

        new TmxLevelImporter().importLevel(tmx, palette, null, temp.resolve("rounded-component-out"));

        assertEquals(2, recordCount(temp.resolve("rounded-component-out/patterns.bin")));
    }

    @Test
    void rejectsDotDoubleAndTrailingSegmentsEvenWhenExternalTargetWouldResolve() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin"); writePaletteWithBlueAtIndexOne(palette);
        Files.writeString(temp.resolve("chunks.tsx"), tileset("chunks.png", ""));
        Path nested = temp.resolve("a"); Files.createDirectory(nested);
        writeSolidChunk(nested.resolve("chunks.png"), 0xFF0000FF);
        Files.writeString(nested.resolve("b.tsx"), tileset("chunks.png", ""));
        String[] sources = {"./chunks.tsx", "a//b.tsx", "a/b.tsx/"};
        for (int i = 0; i < sources.length; i++) {
            Path tmx = temp.resolve("raw-path-" + i + ".tmx");
            Files.writeString(tmx, mapWithTilesets("<tileset firstgid=\"1\" source=\"" + sources[i] + "\"/>"));
            assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(
                    tmx, palette, null, temp.resolve(tmx.getFileName() + "-out")), sources[i]);
        }
    }

    @Test
    void rejectsVisualCsvGidAboveUnsigned32BitInsteadOfMaskingItBlank() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin"); writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("wide-gid.tmx"); Files.writeString(tmx, completeMap(cells(4_294_967_296L)));
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(
                tmx, palette, null, temp.resolve("wide-gid-out")));
    }

    @Test
    void rejectsNonPngPayloadAndUnsupportedMapRootContent() throws Exception {
        BufferedImage jpeg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        assertTrue(ImageIO.write(jpeg, "jpg", temp.resolve("chunks.png").toFile()));
        Path palette = temp.resolve("palette.bin"); writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("jpeg-payload.tmx"); Files.writeString(tmx, completeMap(cells(1)));
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(
                tmx, palette, null, temp.resolve("jpeg-payload-out")));
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        assertImportRejected(completeMap(cells(1)).replace("</map>", "<properties/></map>"), "map-properties");
        assertImportRejected("<root>" + completeMap(cells(1)).replaceFirst("<\\?xml[^>]*>", "") + "</root>", "nested-map");
    }

    @Test
    void injectedXmlStringLimitBoundsOtherwiseInertMetadataWithSpecificFinding() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin"); writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve("string-limit.tmx");
        Files.writeString(tmx, completeMap(cells(1)).replace("orientation=\"orthogonal\"",
                "orientation=\"orthogonal\" backgroundcolor=\"#123456789012345678901\""));
        IOException failure = assertThrows(IOException.class, () -> new TmxLevelImporter(
                ModInputLimits.loweringBuilder().maxStringChars(20).build()).importLevel(
                tmx, palette, null, temp.resolve("string-limit-out")));
        assertTrue(failure.getMessage().contains("XML string exceeds configured limit"));
    }

    @Test
    void injectedNumericLimitAlsoBoundsMarkerCoordinatesAndDecimalOffsets() throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin"); writePaletteWithBlueAtIndexOne(palette);
        var twoDigits = ModInputLimits.loweringBuilder().maxNumericDigits(2).build();
        Path marker = temp.resolve("marker-number-limit.tmx");
        Files.writeString(marker, completeMap(cells(1),
                objectGroup("<object class=\"ring\" x=\"100\" y=\"1\"><point/></object>")));
        assertThrows(IOException.class, () -> new TmxLevelImporter(twoDigits).importLevel(
                marker, palette, null, temp.resolve("marker-number-limit-out")));
        Path offset = temp.resolve("offset-number-limit.tmx");
        Files.writeString(offset, completeMap(cells(1)).replace("offsetx=\"0\"", "offsetx=\"0.00\""));
        assertThrows(IOException.class, () -> new TmxLevelImporter(twoDigits).importLevel(
                offset, palette, null, temp.resolve("offset-number-limit-out")));
    }

    @Test
    void rejectsSecondPropertiesContainerEvenWhenFirstIsEmpty() throws Exception {
        String object = "<object class=\"object\" x=\"1\" y=\"1\"><properties/><properties>"
                + "<property name=\"stockObjectId\" type=\"int\" value=\"1\"/>"
                + "</properties><point/></object>";
        assertImportRejected(completeMap(cells(1), objectGroup(object)), "duplicate-empty-properties");
    }

    private void assertImportRejected(String xml, String label) throws Exception {
        writeSolidChunk(temp.resolve("chunks.png"), 0xFF0000FF);
        Path palette = temp.resolve("palette.bin");
        writePaletteWithBlueAtIndexOne(palette);
        Path tmx = temp.resolve(label.replaceAll("[^A-Za-z0-9]", "-") + ".tmx");
        Files.writeString(tmx, xml);
        assertThrows(IOException.class, () -> new TmxLevelImporter().importLevel(
                tmx, palette, null, temp.resolve(tmx.getFileName() + "-out")), label);
    }

    private static String mapWithTilesets(String tilesets) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <map version="1.10" tiledversion="1.11.2" orientation="orthogonal"
                     renderorder="right-down" width="8" height="8" tilewidth="16" tileheight="16"
                     infinite="0" nextlayerid="2" nextobjectid="1">
                """ + tilesets + """
                  <layer id="1" name="FG" width="8" height="8" offsetx="0" offsety="0">
                    <data encoding="csv">1,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0</data>
                  </layer>
                </map>
                """;
    }

    private static String embeddedMap(String extra) {
        return mapWithTilesets(embeddedTileset("") + extra);
    }

    private static String embeddedTileset(String extra) {
        String attributes = extra.contains("=\"") && !extra.startsWith("<") ? " " + extra : "";
        String child = extra.startsWith("<") ? extra : "";
        return "<tileset firstgid=\"1\" name=\"chunks\" tilewidth=\"16\" tileheight=\"16\""
                + " tilecount=\"1\" columns=\"1\" margin=\"0\" spacing=\"0\"" + attributes + ">"
                + "<image source=\"chunks.png\" width=\"16\" height=\"16\"/>" + child + "</tileset>";
    }

    private static String tileset(String image, String extra) {
        return "<tileset version=\"1.10\" tiledversion=\"1.11.2\" name=\"chunks\""
                + " tilewidth=\"16\" tileheight=\"16\" tilecount=\"1\" columns=\"1\" margin=\"0\" spacing=\"0\">"
                + "<image source=\"" + image + "\" width=\"16\" height=\"16\"/>" + extra + "</tileset>";
    }

    private static String completeMap(long[] fg, String... extraLayers) {
        return completeMapWithTileCount(1, fg, extraLayers);
    }

    private static String completeMapWithTileCount(int tileCount, long[] fg, String... extraLayers) {
        String layers = tileLayer("FG", fg, "") + String.join("", extraLayers);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <map version="1.10" tiledversion="1.11.2" orientation="orthogonal"
                     renderorder="right-down" width="8" height="8" tilewidth="16" tileheight="16"
                     infinite="0" nextlayerid="9" nextobjectid="1">
                """ + "<tileset firstgid=\"1\" name=\"chunks\" tilewidth=\"16\" tileheight=\"16\""
                + " tilecount=\"" + tileCount + "\" columns=\"" + tileCount + "\" margin=\"0\" spacing=\"0\">"
                + "<image source=\"chunks.png\" width=\"" + (tileCount * 16) + "\" height=\"16\"/></tileset>"
                + layers + "</map>";
    }

    private static String arbitraryMap(int width, int height, long[] fg, String... extraLayers) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><map orientation=\"orthogonal\" width=\""
                + width + "\" height=\"" + height + "\" tilewidth=\"16\" tileheight=\"16\" infinite=\"0\">"
                + embeddedTileset("") + arbitraryLayer("FG", width, height, fg)
                + String.join("", extraLayers) + "</map>";
    }

    private static String arbitraryLayer(String name, int width, int height, long[] gids) {
        return "<layer name=\"" + name + "\" width=\"" + width + "\" height=\"" + height
                + "\" offsetx=\"0\" offsety=\"0\"><data encoding=\"csv\">" + csv(gids) + "</data></layer>";
    }

    private static String tileLayer(String name, long[] gids, String extraAttributes) {
        return "<layer name=\"" + name + "\" width=\"8\" height=\"8\" offsetx=\"0\" offsety=\"0\" "
                + extraAttributes + "><data encoding=\"csv\">" + csv(gids) + "</data></layer>";
    }

    private static String objectGroup(String objects) {
        return "<objectgroup name=\"OBJECTS\" offsetx=\"0\" offsety=\"0\">" + objects + "</objectgroup>";
    }

    private static String stockObject(int x, int y) {
        return "<object class=\"object\" x=\"" + x + "\" y=\"" + y + "\"><properties>"
                + "<property name=\"stockObjectId\" type=\"int\" value=\"1\"/>"
                + "</properties><point/></object>";
    }

    private static int genesisGray(int index) {
        int component = Math.min(7, index & 7);
        return (component << 9) | (component << 5) | (component << 1);
    }

    private static void assertDirectoriesEqual(Path first, Path second) throws IOException {
        List<Path> files;
        try (var paths = Files.list(first)) { files = paths.sorted().toList(); }
        try (var paths = Files.list(second)) {
            assertEquals(files.stream().map(path -> path.getFileName().toString()).toList(),
                    paths.sorted().map(path -> path.getFileName().toString()).toList());
        }
        for (Path file : files) assertEquals(java.nio.ByteBuffer.wrap(Files.readAllBytes(file)),
                java.nio.ByteBuffer.wrap(Files.readAllBytes(second.resolve(file.getFileName()))), file.getFileName().toString());
    }

    private static String aggregateSha256(Path directory) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<Path> files;
        try (var paths = Files.list(directory)) { files = paths.sorted().toList(); }
        for (Path file : files) {
            digest.update(file.getFileName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0); digest.update(Files.readAllBytes(file));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static long[] cells(long... initial) {
        long[] cells = new long[64];
        System.arraycopy(initial, 0, cells, 0, initial.length);
        return cells;
    }

    private static String csv(long[] cells) {
        return java.util.Arrays.stream(cells).mapToObj(Long::toUnsignedString).collect(Collectors.joining(","));
    }

    private static void writeTwoChunkSheet(Path target) throws IOException {
        BufferedImage image = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 32; x++) {
            image.setRGB(x, y, x < 16 ? 0xFF0000FF : 0xFF00FF00);
        }
        assertTrue(ImageIO.write(image, "png", target.toFile()));
    }

    private static void writePalette(Path target, int[][] lines) throws IOException {
        try (var out = new java.io.DataOutputStream(Files.newOutputStream(target))) {
            out.writeBytes("GPAL"); out.writeShort(1); out.writeShort(lines.length);
            out.writeShort(16); out.writeShort(0);
            for (int[] line : lines) for (int index = 0; index < 16; index++) {
                out.writeShort(index < line.length ? line[index] : 0);
            }
        }
    }

    private static void writeProfiles(Path directory, int count) throws IOException {
        Files.createDirectories(directory);
        Files.write(directory.resolve("solid-heights.bin"), profileBytes("GSHG", 16, count));
        Files.write(directory.resolve("solid-widths.bin"), profileBytes("GSWD", 16, count));
        Files.write(directory.resolve("solid-angles.bin"), profileBytes("GSAN", 1, count));
    }

    private static byte[] profileBytes(String magic, int recordSize, int count) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(bytes)) {
            out.writeBytes(magic); out.writeShort(1); out.writeShort(recordSize); out.writeInt(count);
            for (int index = 0; index < count * recordSize; index++) out.writeByte(index);
        }
        return bytes.toByteArray();
    }

    private static int unsignedShort(Path binary, int offset) throws IOException {
        byte[] bytes = Files.readAllBytes(binary);
        return (Byte.toUnsignedInt(bytes[offset]) << 8) | Byte.toUnsignedInt(bytes[offset + 1]);
    }

    private static int recordCount(Path binary) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(binary))) {
            input.skipNBytes(8);
            return input.readInt();
        }
    }

    private static void assertAllZero(Path binary, int offset, int length) throws IOException {
        byte[] bytes = Files.readAllBytes(binary);
        for (int i = offset; i < offset + length; i++) {
            assertEquals(0, bytes[i], binary.getFileName() + " byte " + i);
        }
    }

    private static void writeSolidChunk(Path target, int argb) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) image.setRGB(x, y, argb);
        }
        assertTrue(ImageIO.write(image, "png", target.toFile()));
    }

    private static void writePaletteWithBlueAtIndexOne(Path target) throws IOException {
        try (var out = new java.io.DataOutputStream(Files.newOutputStream(target))) {
            out.writeBytes("GPAL");
            out.writeShort(1);
            out.writeShort(1);
            out.writeShort(16);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(7 << 9);
            for (int index = 2; index < 16; index++) out.writeShort(0);
        }
    }
}
