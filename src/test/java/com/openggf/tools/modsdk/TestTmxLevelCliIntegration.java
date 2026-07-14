package com.openggf.tools.modsdk;

import com.openggf.game.GroundMode;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.mods.code.BakedLevelRef;
import com.openggf.mods.code.ModLevelDefinition;
import com.openggf.mods.code.ModLevelDefinitionParser;
import com.openggf.mods.code.ModZoneLoader;
import com.openggf.mods.code.PreparedModZone;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.physics.SensorResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
class TestTmxLevelCliIntegration {
    private static final String PINNED_AGGREGATE_SHA256 =
            "e2e63cd4323313931253576daacc99bc80e28617fec04f745af740fef1fc61ee";

    @TempDir
    Path temp;

    @AfterEach
    void tearDown() {
        GroundSensor.setLevelManager(null);
        TestEnvironment.resetAll();
    }

    @Test
    void cliTmxConversionIsDeterministicLoadsAndProvidesPrimaryFloorAndSecondaryWallCollision()
            throws Exception {
        Fixture fixture = writeFixture();
        Path first = temp.resolve("first-export");
        Path second = temp.resolve("second-export");

        assertEquals(0, runTmxConversion(fixture, first));
        assertEquals(0, runTmxConversion(fixture, second));
        assertDirectoriesEqual(first, second);
        assertEquals(PINNED_AGGREGATE_SHA256, aggregateSha256(first));

        ModLevelDefinition definition;
        try (ModAssetRoot assets = ModAssetRoot.directory(temp, first,
                ModInputLimits.production(), DirectoryAccess.TEST)) {
            definition = ModLevelDefinitionParser.read(assets, new BakedLevelRef("level.json"));
        }
        assertEquals(new ModLevelDefinition.Bounds(0, 128, 0, 128), definition.bounds());
        assertEquals(new ModLevelDefinition.Start(24, 32), definition.start());
        assertEquals(2, definition.objects().size());
        assertInstanceOf(ModLevelDefinition.StockObjectSpawn.class, definition.objects().getFirst());
        assertEquals(42, ((ModLevelDefinition.StockObjectSpawn) definition.objects().getFirst()).stockObjectId());
        assertInstanceOf(ModLevelDefinition.KeyedObjectSpawn.class, definition.objects().getLast());
        assertEquals("owner:badnik",
                ((ModLevelDefinition.KeyedObjectSpawn) definition.objects().getLast()).objectKey());
        assertEquals(1, definition.rings().size());
        assertEquals(48, definition.rings().getFirst().x());
        assertEquals(64, definition.rings().getFirst().y());

        PreparedModZone prepared = new PreparedModZone("owner", "tmx-zone", "mtz3",
                definition, null, definition.zoneName(), definition.levelIndex(), definition.zoneIndex(),
                definition.start().x(), definition.start().y());
        Level loaded = ModZoneLoader.load(prepared,
                new RingSpriteSheet(new Pattern[0], List.of(), 1, 8, 0, 0));
        assertEquals(0, loaded.getMinX());
        assertEquals(128, loaded.getMaxX());
        assertEquals(0, loaded.getMinY());
        assertEquals(128, loaded.getMaxY());
        assertEquals(2, loaded.getObjects().size());
        assertEquals(42, loaded.getObjects().getFirst().objectId());
        assertNull(loaded.getObjects().getFirst().objectKey());
        assertEquals("owner", loaded.getObjects().getLast().ownerModId());
        assertEquals("owner:badnik", loaded.getObjects().getLast().objectKey());
        assertEquals(1, loaded.getRings().size());

        int blockIndex = loaded.getMap().getValue(0, 0, 0) & 0xFF;
        ChunkDesc primaryFloor = loaded.getBlock(blockIndex).getChunkDesc(1, 6);
        ChunkDesc secondaryWall = loaded.getBlock(blockIndex).getChunkDesc(6, 1);
        assertTrue(primaryFloor.isSolidityBitSet(0x0C));
        assertFalse(primaryFloor.isSolidityBitSet(0x0E));
        assertFalse(secondaryWall.isSolidityBitSet(0x0C));
        assertTrue(secondaryWall.isSolidityBitSet(0x0E));

        var levelManager = TestEnvironment.activeGameplayMode().getLevelManager();
        levelManager.setLevel(loaded);
        GroundSensor.setLevelManager(levelManager);
        AbstractPlayableSprite player = headlessPlayer();

        player.setX((short) 24);
        player.setY((short) 90);
        SensorResult floor = new GroundSensor(player, Direction.DOWN, (byte) 0, (byte) 0, true).scan();
        assertNotNull(floor, "primary collision path must expose the imported floor");
        assertEquals(5, floor.distance());

        player.setLrbSolidBit((byte) 0x0F);
        player.setX((short) 90);
        player.setY((short) 24);
        SensorResult wall = new GroundSensor(player, Direction.RIGHT, (byte) 0, (byte) 0, true).scan();
        assertNotNull(wall, "secondary collision path must expose the imported wall");
        assertEquals(5, wall.distance());
    }

    @Test
    void levelCliRetainsFromExportAndRejectsAmbiguousOrUnknownFlagInventories() throws Exception {
        Fixture fixture = writeFixture();
        CliRun help = runWithOutput();
        assertEquals(1, help.exit());
        assertTrue(help.output().contains(
                "ggfmod convert level --from-tmx <map.tmx> --palette <GPAL>"
                        + " [--solid-tiles <profile-dir>] [--music <owner:localName>] --out <dir>"));

        Path canonical = temp.resolve("canonical");
        new TmxLevelImporter().importLevel(fixture.tmx(), fixture.palette(), fixture.profiles(), canonical);

        Path copied = temp.resolve("copied");
        assertEquals(0, run("convert", "level", "--from-export", canonical.toString(),
                "--out", copied.toString()));
        assertDirectoriesEqual(canonical, copied);

        CliRun ambiguous = runWithOutput("convert", "level", "--from-export", canonical.toString(),
                "--from-tmx", fixture.tmx().toString(), "--palette", fixture.palette().toString(),
                "--out", temp.resolve("ambiguous").toString());
        assertEquals(1, ambiguous.exit());
        assertTrue(ambiguous.output().contains(
                "Select exactly one level source: --from-export or --from-tmx"));
        assertFalse(Files.exists(temp.resolve("ambiguous")));

        CliRun unknown = runWithOutput("convert", "level", "--from-tmx", fixture.tmx().toString(),
                "--palette", fixture.palette().toString(), "--unknown", "value",
                "--out", temp.resolve("unknown").toString());
        assertEquals(1, unknown.exit());
        assertTrue(unknown.output().contains("Invalid flag for selected conversion mode: --unknown"));
        assertFalse(Files.exists(temp.resolve("unknown")));

        CliRun missingPalette = runWithOutput("convert", "level", "--from-tmx", fixture.tmx().toString(),
                "--out", temp.resolve("missing-palette").toString());
        assertEquals(1, missingPalette.exit());
        assertTrue(missingPalette.output().contains("Missing required flag --palette"));
        assertFalse(Files.exists(temp.resolve("missing-palette")));
    }

    private int runTmxConversion(Fixture fixture, Path output) {
        return run("convert", "level", "--from-tmx", fixture.tmx().toString(),
                "--palette", fixture.palette().toString(), "--solid-tiles", fixture.profiles().toString(),
                "--out", output.toString());
    }

    private static int run(String... arguments) {
        return runWithOutput(arguments).exit();
    }

    private static CliRun runWithOutput(String... arguments) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit = GgfModCli.run(arguments, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return new CliRun(exit, bytes.toString(StandardCharsets.UTF_8));
    }

    private Fixture writeFixture() throws IOException {
        Path sheet = temp.resolve("chunks.png");
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, 0xFF0000FF);
        assertTrue(ImageIO.write(image, "png", sheet.toFile()));

        Path palette = temp.resolve("palette.gpal");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(palette))) {
            out.writeBytes("GPAL"); out.writeShort(1); out.writeShort(1); out.writeShort(16); out.writeShort(0);
            out.writeShort(0); out.writeShort(7 << 9);
            for (int index = 2; index < 16; index++) out.writeShort(0);
        }

        Path profiles = temp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.write(profiles.resolve("solid-heights.bin"), fullProfile("GSHG", 16));
        Files.write(profiles.resolve("solid-widths.bin"), fullProfile("GSWD", 16));
        Files.write(profiles.resolve("solid-angles.bin"), fullProfile("GSAN", 1));

        long[] fg = filledCells(1);
        long[] primary = new long[64];
        long[] secondary = new long[64];
        for (int x = 0; x < 8; x++) primary[6 * 8 + x] = 1;
        for (int y = 0; y < 8; y++) secondary[y * 8 + 6] = 1;
        Path tmx = temp.resolve("fixture.tmx");
        Files.writeString(tmx, """
                <?xml version="1.0" encoding="UTF-8"?>
                <map version="1.10" tiledversion="1.11.2" orientation="orthogonal"
                     renderorder="right-down" width="8" height="8" tilewidth="16" tileheight="16"
                     infinite="0" nextlayerid="6" nextobjectid="5">
                  <tileset firstgid="1" name="chunks" tilewidth="16" tileheight="16"
                           tilecount="1" columns="1" margin="0" spacing="0">
                    <image source="chunks.png" width="16" height="16"/>
                  </tileset>
                """ + layer("FG", fg) + layer("COLLISION", primary) + layer("COLLISION_ALT", secondary) + """
                  <objectgroup name="OBJECTS" offsetx="0" offsety="0">
                    <object id="1" class="object" x="16" y="32"><properties>
                      <property name="stockObjectId" type="int" value="42"/>
                    </properties><point/></object>
                    <object id="2" class="object" x="32" y="48"><properties>
                      <property name="objectKey" value="owner:badnik"/>
                    </properties><point/></object>
                    <object id="3" class="ring" x="48" y="64"><point/></object>
                    <object id="4" class="start" x="24" y="32"><point/></object>
                  </objectgroup>
                </map>
                """);
        return new Fixture(tmx, palette, profiles);
    }

    private static String layer(String name, long[] gids) {
        return "<layer name=\"" + name + "\" width=\"8\" height=\"8\" offsetx=\"0\" offsety=\"0\">"
                + "<data encoding=\"csv\">" + Arrays.stream(gids).mapToObj(Long::toUnsignedString)
                .collect(Collectors.joining(",")) + "</data></layer>";
    }

    private static long[] filledCells(long gid) {
        long[] cells = new long[64];
        Arrays.fill(cells, gid);
        return cells;
    }

    private static byte[] fullProfile(String magic, int recordSize) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBytes(magic); out.writeShort(1); out.writeShort(recordSize); out.writeInt(2);
            for (int index = 0; index < recordSize; index++) out.writeByte(0);
            for (int index = 0; index < recordSize; index++) out.writeByte(recordSize == 1 ? 0 : 16);
        }
        return bytes.toByteArray();
    }

    private static AbstractPlayableSprite headlessPlayer() {
        AbstractPlayableSprite player = new AbstractPlayableSprite("sonic", (short) 0, (short) 0) {
            @Override protected void defineSpeeds() {}
            @Override protected void createSensorLines() {}
            @Override public void draw() {}
        };
        player.setGroundMode(GroundMode.GROUND);
        player.setLayer((byte) 0);
        player.setWidth(0);
        player.setHeight(0);
        return player;
    }

    private static void assertDirectoriesEqual(Path first, Path second) throws IOException {
        List<Path> files;
        try (var paths = Files.list(first)) { files = paths.sorted().toList(); }
        try (var paths = Files.list(second)) {
            assertEquals(files.stream().map(path -> path.getFileName().toString()).toList(),
                    paths.sorted().map(path -> path.getFileName().toString()).toList());
        }
        for (Path file : files) assertArrayEquals(Files.readAllBytes(file),
                Files.readAllBytes(second.resolve(file.getFileName())), file.getFileName().toString());
    }

    private static String aggregateSha256(Path directory) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<Path> files;
        try (var paths = Files.list(directory)) { files = paths.sorted().toList(); }
        for (Path file : files) {
            digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private record Fixture(Path tmx, Path palette, Path profiles) {}
    private record CliRun(int exit, String output) {}
}
