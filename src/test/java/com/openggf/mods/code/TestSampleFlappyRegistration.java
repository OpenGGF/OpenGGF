package com.openggf.mods.code;

import com.openggf.control.InputActionMasks;
import com.openggf.control.PlayerInputState;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.ZoneKey;
import com.openggf.io.DirectoryAccess;
import com.openggf.io.ModAssetRoot;
import com.openggf.io.ModInputLimits;
import com.openggf.level.objects.HudLabel;
import com.openggf.level.objects.HudMetric;
import com.openggf.level.objects.HudProfile;
import com.openggf.level.objects.HudRow;
import com.openggf.level.objects.HudWarningPolicy;
import com.openggf.mods.ModManifest;
import com.openggf.mods.ModManifestParser;
import com.openggf.mods.ModType;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSampleFlappyRegistration {
    private static final Path SAMPLE = Path.of(
            "src/test/resources/mods/sample-flappy-src/project");
    private static final ZoneKey.Mod FLAPPY = new ZoneKey.Mod(
            "sample-flappy", "flappy-garden");

    @TempDir Path temp;

    @Test
    void flappyIsAnAnchorlessS3kApiZeroSevenGameStartWithoutRomArt() throws Exception {
        ModManifest manifest = new ModManifestParser().parse(Files.readAllBytes(
                SAMPLE.resolve("src/main/resources/META-INF/openggf-mod.yaml")));
        assertEquals(ModType.PATCH, manifest.type());
        assertEquals("s3k", manifest.baseGame());
        assertEquals(">=0.7.0 <0.8.0", manifest.engineApiRange().toString());

        ModRegistrationPlan plan = compileAndRegister();
        ModZoneContribution zone = plan.zones().getFirst();
        assertTrue(zone.gameStart());
        assertNull(zone.insertAfter());
        assertTrue(plan.romObjectArt().isEmpty());
        GameplayLaunchTeam launchTeam = plan.launchTeams().get(FLAPPY);
        assertEquals(CharacterKey.TAILS, launchTeam.main());
        assertTrue(launchTeam.sidekicks().isEmpty());
        assertEquals(1, plan.inputFilters().size());
        assertEquals(1, plan.hudProfiles().size());

        PlayerInputState raw = PlayerInputState.of(
                AbstractPlayableSprite.INPUT_UP | AbstractPlayableSprite.INPUT_LEFT
                        | AbstractPlayableSprite.INPUT_RIGHT,
                AbstractPlayableSprite.INPUT_DOWN | AbstractPlayableSprite.INPUT_LEFT
                        | AbstractPlayableSprite.INPUT_RIGHT,
                InputActionMasks.ACTION_A | InputActionMasks.ACTION_C,
                InputActionMasks.ACTION_B,
                true,
                true);
        PlayerInputState filtered = plan.inputFilters().get(FLAPPY).filter(raw);
        assertEquals(AbstractPlayableSprite.INPUT_UP | AbstractPlayableSprite.INPUT_JUMP,
                filtered.heldMask());
        assertEquals(AbstractPlayableSprite.INPUT_DOWN | AbstractPlayableSprite.INPUT_JUMP,
                filtered.pressedMask());
        assertEquals(raw.actionHeldMask(), filtered.actionHeldMask());
        assertEquals(raw.actionPressedMask(), filtered.actionPressedMask());
        assertEquals(raw.startHeld(), filtered.startHeld());
        assertEquals(raw.startPressed(), filtered.startPressed());

        HudProfile hud = plan.hudProfiles().get(FLAPPY);
        HudRow stockScore = hud.rows().stream()
                .filter(row -> row.metric() == HudMetric.SCORE).findFirst().orElseThrow();
        assertEquals(HudLabel.SCORE, stockScore.label());
        assertFalse(stockScore.visible());
        HudRow ringScore = hud.rows().stream()
                .filter(row -> row.metric() == HudMetric.RINGS).findFirst().orElseThrow();
        assertTrue(ringScore.visible());
        assertEquals(HudLabel.SCORE, ringScore.label());
        assertEquals(HudWarningPolicy.NONE, ringScore.warning());
    }

    private ModRegistrationPlan compileAndRegister() throws Exception {
        Path classes = temp.resolve("classes");
        Files.createDirectories(classes);
        List<String> arguments = new ArrayList<>(List.of(
                "--release", "21", "-classpath",
                Path.of("target/classes").toAbsolutePath().toString(),
                "-d", classes.toString()));
        try (var sources = Files.walk(SAMPLE.resolve("src/main/java"))) {
            sources.filter(path -> path.toString().endsWith(".java")).sorted()
                    .map(Path::toString).forEach(arguments::add);
        }
        int exit = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, arguments.toArray(String[]::new));
        assertEquals(0, exit, "sample source must compile against the current SDK surface");

        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            GgfMod mod = Class.forName("example.flappysample.FlappySampleMod", true, loader)
                    .asSubclass(GgfMod.class).getConstructor().newInstance();
            Path assetsDirectory = materializeAssets();
            try (ModAssetRoot assets = ModAssetRoot.snapshotDirectory(
                    assetsDirectory, assetsDirectory, ModInputLimits.production(),
                    DirectoryAccess.TEST)) {
                ModContext context = new ModContext("sample-flappy", "s3k", assets);
                mod.register(context);
                return context.freeze();
            }
        }
    }

    private Path materializeAssets() throws Exception {
        Path assets = temp.resolve("assets");
        assertCli("convert", "art", "--image",
                SAMPLE.resolve("src/main/mod/pipe.png").toString(), "--sheet",
                SAMPLE.resolve("src/main/mod/pipe-sheet.yaml").toString(), "--out",
                assets.resolve("art/pipe.ggfs").toString());

        Path source = SAMPLE.resolve("src/main/mod/level-source");
        Path export = temp.resolve("level-export");
        Files.createDirectories(export);
        Files.copy(source.resolve("level.json"), export.resolve("level.json"));
        Properties properties = new Properties();
        try (var input = Files.newInputStream(source.resolve("binary-assets.properties"))) {
            properties.load(input);
        }
        for (String name : properties.stringPropertyNames()) {
            Files.write(export.resolve(name), Base64.getDecoder().decode(properties.getProperty(name)));
        }
        assertCli("convert", "level", "--from-export", export.toString(), "--out",
                assets.resolve("levels/flappy").toString());
        return assets;
    }

    private static void assertCli(String... arguments) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit = GgfModCli.run(arguments, new PrintStream(bytes));
        assertEquals(0, exit, bytes.toString(StandardCharsets.UTF_8));
    }
}
