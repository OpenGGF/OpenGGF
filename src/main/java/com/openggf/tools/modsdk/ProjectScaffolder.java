package com.openggf.tools.modsdk;

import com.openggf.game.ModKeySyntax;
import com.openggf.version.AppVersion;

import com.openggf.io.PixelImage;
import com.openggf.io.PngCodec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Creates the canonical, immediately compilable OpenGGF mod starter project. */
public final class ProjectScaffolder {
    private static final String TEMPLATE_ROOT = "META-INF/openggf-mod-sdk/templates/";
    private static final Pattern JAVA_PACKAGE = Pattern.compile(
            "[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)*");

    public Path scaffold(Path outputDirectory, String modId, String javaPackage, String kind) throws IOException {
        if (kind == null || !java.util.Set.of("music", "reskin", "object", "character", "zone", "scene", "standalone").contains(kind))
            throw new IllegalArgumentException("Unknown starter kind: " + kind);
        if (kind.equals("object") || kind.equals("zone")) {
            Path project = scaffold(outputDirectory, modId, javaPackage);
            Path readme = project.resolve("README.md");
            String purpose = kind.equals("object")
                    ? "Object starter: edit SampleBadnik; its test zone already places one visible instance."
                    : "Zone starter: edit src/main/mod/level-source; the sample object demonstrates owned placements.";
            Files.writeString(readme, purpose + "\n\n" + Files.readString(readme), StandardCharsets.UTF_8);
            return project;
        }
        ModKeySyntax.requireManifestId(modId);
        if (javaPackage == null || !JAVA_PACKAGE.matcher(javaPackage).matches()
                || !javax.lang.model.SourceVersion.isName(javaPackage))
            throw new IllegalArgumentException("Invalid Java package: " + javaPackage);
        Path output = outputDirectory.toAbsolutePath().normalize();
        if (output.getParent() == null || Files.exists(output, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Project output already exists or has no parent: " + output);
        Files.createDirectories(output.getParent());
        Path staging = Files.createTempDirectory(output.getParent(), output.getFileName() + ".tmp-");
        try {
            if (kind.equals("character") || kind.equals("standalone")) {
                StarterProjects.copyMaintained(staging, kind, modId, javaPackage);
            } else {
                writeSimpleProject(staging, kind, modId, javaPackage);
            }
            writeText(staging.resolve("README.md"), "# " + displayName(modId) + "\n\n"
                    + "Complete " + kind + " starter for the unpublished Mod API 0.7 candidate.\n\n"
                    + "Use Java 21 and matching engine/SDK jars from the same commit. Build twice after an edit:\n\n"
                    + "```sh\nmvn package -Dopenggf.engine.jar=/absolute/path/engine.jar "
                    + "-Dopenggf.sdk.jar=/absolute/path/sdk.jar\n```\n\n"
                    + "The validated distributable is target/" + modId + "-mod.jar. "
                    + "Run target/classes with ggfmod run, then validate the jar before sharing it.\n"
                    + "See the creator kit handbook's getting-started.md and installing-mods.md.\n");
            Files.move(staging, output);
            return output;
        } catch (IOException | RuntimeException failure) { deleteTree(staging, failure); throw failure; }
    }

    public Path scaffold(Path outputDirectory, String modId, String javaPackage) throws IOException {
        ModKeySyntax.requireManifestId(modId);
        if (javaPackage == null || !JAVA_PACKAGE.matcher(javaPackage).matches()
                || !javax.lang.model.SourceVersion.isName(javaPackage))
            throw new IllegalArgumentException("Invalid Java package: " + javaPackage);
        Path output = Objects.requireNonNull(outputDirectory, "outputDirectory")
                .toAbsolutePath().normalize();
        if (output.getParent() == null || Files.exists(output, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Project output already exists or has no parent: " + output);
        Files.createDirectories(output.getParent());
        Path staging = Files.createTempDirectory(output.getParent(), output.getFileName() + ".tmp-");
        String prefix = classPrefix(modId);
        Map<String, String> variables = Map.of(
                "{{MOD_ID}}", modId,
                "{{PACKAGE}}", javaPackage,
                "{{CLASS_PREFIX}}", prefix,
                "{{DISPLAY_NAME}}", displayName(modId),
                "{{ENGINE_VERSION}}", AppVersion.identity().baseVersion());
        try {
            Map<String, String> files = new LinkedHashMap<>();
            files.put("pom.xml", "pom.xml.template");
            files.put("README.md", "README.md.template");
            String packagePath = javaPackage.replace('.', '/');
            files.put("src/main/java/" + packagePath + "/" + prefix + "Mod.java", "Mod.java.template");
            files.put("src/main/java/" + packagePath + "/SampleBadnik.java", "SampleBadnik.java.template");
            files.put("src/main/java/" + packagePath + "/SampleCharacter.java",
                    "SampleCharacter.java.template");
            files.put("src/main/resources/META-INF/openggf-mod.yaml", "openggf-mod.yaml.template");
            files.put("src/main/mod/sample-sheet.yaml", "sample-sheet.yaml.template");
            files.forEach((relative, template) -> {
                try { writeText(staging.resolve(relative), render(template, variables)); }
                catch (IOException failure) { throw new ScaffoldFailure(failure); }
            });
            writeSamplePng(staging.resolve("src/main/mod/sample.png"));
            writeMinimalLevel(staging.resolve("src/main/mod/level-source"), modId);
            Files.move(staging, output);
            return output;
        } catch (ScaffoldFailure failure) {
            deleteTree(staging, failure.getCause());
            throw failure.getCause();
        } catch (IOException | RuntimeException failure) {
            deleteTree(staging, failure);
            throw failure;
        }
    }

    private static void writeSimpleProject(Path root, String kind, String id, String pkg) throws IOException {
        String conversion = "";
        String manifestExtras = "";
        if (kind.equals("reskin")) {
            writeReskinPng(root.resolve("src/main/mod/sample.png"));
            // The known white cell of normal Sonic/Tails palette is index 6.
            String[] colours = {"#000000", "#240000", "#490000", "#6D0000", "#920000", "#B60000", "#FFFFFF", "#DBDBDB", "#929292", "#494949", "#B69249", "#6D4924", "#FF0000", "#DB0000", "#FFB600", "#FFDB00"};
            String palette = java.util.Arrays.stream(colours).map(c -> "\"" + c + "\"").collect(java.util.stream.Collectors.joining(", "));
            StringBuilder frames = new StringBuilder();
            for (int frame = 0; frame < 6; frame++) frames.append("  - delay: 2\n    pieces:\n      - { sourceX: ")
                    .append(frame * 8).append(", sourceY: 0, widthPixels: 8, heightPixels: 8, xOffset: -4, yOffset: -4, hFlip: false, vFlip: false, paletteIndex: 0, priority: false }\n");
            writeText(root.resolve("src/main/mod/sample-sheet.yaml"), "formatVersion: 1\npaletteLine: 0\npalette: [" + palette + "]\nframes:\n" + frames);
            conversion = "<execution><id>art</id><phase>generate-resources</phase><goals><goal>exec</goal></goals><configuration><executable>java</executable><classpathScope>compile</classpathScope><arguments><argument>-cp</argument><classpath/><argument>com.openggf.tools.modsdk.GgfModCli</argument><argument>convert</argument><argument>art</argument><argument>--image</argument><argument>${project.basedir}/src/main/mod/sample.png</argument><argument>--sheet</argument><argument>${project.basedir}/src/main/mod/sample-sheet.yaml</argument><argument>--out</argument><argument>${project.build.outputDirectory}/art/sample.ggfs</argument></arguments></configuration></execution>";
            manifestExtras = "artOverrides:\n  signpost: art/sample.ggfs\n";
        } else if (kind.equals("music")) {
            writeTone(root.resolve("src/main/resources/audio/theme.wav"));
            writeText(root.resolve("src/main/resources/audio/audio-manifest.yaml"), "formatVersion: 1\ntracks:\n  - id: theme\n    assetPath: audio/theme.wav\n    loop: true\n    loopStartFrame: 0\n    gain: 0.25\n    tempoEffects: true\nsfx: []\n");
            manifestExtras = "artOverrides: {}\n";
        } else {
            String entry = pkg + ".SceneMod";
            manifestExtras = "entrypoint: " + entry + "\nartOverrides: {}\n";
            writeText(root.resolve("src/main/java/" + pkg.replace('.', '/') + "/SceneMod.java"), "package " + pkg + ";\nimport com.openggf.mods.code.GgfMod;\nimport com.openggf.mods.code.ModContext;\npublic final class SceneMod implements GgfMod { public void register(ModContext context) { context.registerStartupScene(StarterScene::new); } }\n");
            writeText(root.resolve("src/main/java/" + pkg.replace('.', '/') + "/StarterScene.java"), "package " + pkg + ";\nimport com.openggf.mods.scene.*;\npublic final class StarterScene implements ModScene { private int ticks; public void enter(SceneContext ctx) { } public void update(SceneContext ctx) { ticks++; if (ctx.buttonPressed(SceneButtons.B)) ctx.exitToGameTitle(); } public void draw(SceneContext ctx, SceneCanvas canvas) { canvas.clear(0x102040); canvas.text(\"MY SCENE \" + ticks, 16, 16, 0xFFFFFFFF); } }\n");
        }
        writeText(root.resolve("src/main/resources/META-INF/openggf-mod.yaml"), "formatVersion: 1\nid: " + id + "\nname: " + displayName(id) + "\nversion: 1.0.0\nauthors: [Mod Author]\ndescription: Original " + kind + " starter.\nengineApiRange: \">=0.7.0 <0.8.0\"\ntype: patch\nbaseGame: s2\ndependencies: []\naudioOverrides: " + (kind.equals("music") ? "{129: theme}" : "{}") + "\n" + manifestExtras);
        String pom = render("simple-pom.xml.template", Map.of("{{MOD_ID}}", id, "{{PACKAGE}}", pkg, "{{CONVERSION}}", conversion));
        writeText(root.resolve("pom.xml"), pom);
    }

    private static void writeTone(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        int frames = 16000;
        java.nio.ByteBuffer wav = java.nio.ByteBuffer.allocate(44 + frames * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + frames * 2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(frames * 2);
        for (int i = 0; i < frames; i++) wav.putShort((short) (3000 * Math.sin(2 * Math.PI * 220 * i / 8000)));
        Files.write(path, wav.array());
    }

    private static String render(String name, Map<String, String> variables) throws IOException {
        try (InputStream input = ProjectScaffolder.class.getClassLoader()
                .getResourceAsStream(TEMPLATE_ROOT + name)) {
            if (input == null) throw new IOException("SDK template is missing: " + name);
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            for (var variable : variables.entrySet()) text = text.replace(variable.getKey(), variable.getValue());
            return text;
        }
    }

    private static void writeText(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static void writeReskinPng(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        PixelImage image = PixelImage.blank(48, 8);
        for (int frame = 0; frame < 6; frame++) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            boolean border = (x == 1 || x == 6) && y >= 1 && y <= 6
                    || (y == 1 || y == 6) && x >= 1 && x <= 6;
            boolean mark = y == 3 && x >= 2 && x <= 5 && ((frame + 1) & (1 << (x - 2))) != 0;
            image.setRGB(frame * 8 + x, y, border || mark ? 0xFFFFFFFF : 0x00000000);
        }
        PngCodec.write(path, image);
    }

    private static void writeSamplePng(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        PixelImage image = PixelImage.blank(8, 8);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            image.setRGB(x, y, ((x + y) & 1) == 0 ? 0xFF000000 : 0xFFFFFFFF);
        PngCodec.write(path, image);
    }

    /** A visible two-pattern sample with an empty upper block and solid lower floor. */
    private static void writeMinimalLevel(Path root, String modId) throws IOException {
        Files.createDirectories(root);
        writeBinary(root.resolve("patterns.bin"), out -> {
            out.writeBytes("GPTN");
            out.writeShort(1);
            out.writeShort(32);
            out.writeInt(2);
            out.write(new byte[32]);
            byte[] visible = new byte[32];
            java.util.Arrays.fill(visible, (byte) 0x11);
            out.write(visible);
        });
        writeBinary(root.resolve("chunks.bin"), out -> {
            out.writeBytes("GCHK");
            out.writeShort(1);
            out.writeShort(8);
            out.writeInt(2);
            for (int i = 0; i < 4; i++) out.writeShort(0);
            for (int i = 0; i < 4; i++) out.writeShort((1 << 13) | 1);
        });
        writeBinary(root.resolve("blocks.bin"), out -> {
            out.writeBytes("GBLK");
            out.writeShort(1);
            out.writeByte(8);
            out.writeByte(0);
            out.writeInt(2);
            for (int i = 0; i < 64; i++) out.writeShort(0);
            for (int i = 0; i < 64; i++) out.writeShort(0x5001); // chunk 1, top-solid on both paths
        });
        writeBinary(root.resolve("fg-map.bin"), out -> {
            out.writeBytes("GMAP");
            out.writeShort(1);
            out.writeShort(2);
            out.writeShort(2);
            out.writeShort(1);
            out.writeInt(4);
            out.write(new byte[]{0, 0, 1, 1});
        });
        for (String name : new String[]{"solid-heights.bin", "solid-widths.bin"})
            writeBinary(root.resolve(name), out -> {
                out.writeBytes(name.startsWith("solid-h") ? "GSHG" : "GSWD");
                out.writeShort(1);
                out.writeShort(16);
                out.writeInt(2);
                out.write(new byte[16]);
                byte[] solid = new byte[16];
                java.util.Arrays.fill(solid, (byte) 16);
                out.write(solid);
            });
        writeBinary(root.resolve("solid-angles.bin"), out -> {
            out.writeBytes("GSAN");
            out.writeShort(1);
            out.writeShort(1);
            out.writeInt(2);
            out.write(new byte[]{0, 0});
        });
        for (int secondary = 0; secondary < 2; secondary++) {
            int flag = secondary;
            writeBinary(root.resolve(secondary == 0 ? "collision-primary.bin" : "collision-secondary.bin"), out -> {
                out.writeBytes("GCOL");
                out.writeShort(1);
                out.writeByte(flag);
                out.writeByte(2);
                out.writeInt(2);
                out.writeShort(0);
                out.writeShort(1);
            });
        }
        writeBinary(root.resolve("palettes.bin"), out -> {
            out.writeBytes("GPAL");
            out.writeShort(1);
            out.writeShort(4);
            out.writeShort(16);
            out.writeShort(0);
            for (int line = 0; line < 4; line++) {
                for (int color = 0; color < 16; color++) {
                    out.writeShort(line == 1 && (color == 1 || color == 15) ? 0x0EEE : 0);
                }
            }
        });
        writeText(root.resolve("level.json"), """
                {"formatVersion":1,"zoneName":"Sample Zone","zoneIndex":64,"levelIndex":1024,
                "blockGridSide":8,"width":2,"height":2,
                "bounds":{"minX":0,"maxX":256,"minY":0,"maxY":256},
                "start":{"x":32,"y":96},"music":{"stockId":129},
                "assets":{"patterns":"patterns.bin","chunks":"chunks.bin","blocks":"blocks.bin",
                "foregroundMap":"fg-map.bin","solidHeights":"solid-heights.bin","solidWidths":"solid-widths.bin",
                "solidAngles":"solid-angles.bin","collisionPrimary":"collision-primary.bin",
                "collisionSecondary":"collision-secondary.bin","palettes":"palettes.bin"},
                "objects":[{"placementId":1,"x":96,"y":96,"objectKey":"%s:sample-badnik",
                "subtype":0,"renderFlags":0,"respawnTracked":false,"rawYWord":96}],"rings":[]}
                """.formatted(modId));
    }

    private static void writeBinary(Path path, BinaryWriter writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) { writer.write(output); }
        Files.write(path, bytes.toByteArray());
    }

    private static String classPrefix(String id) {
        StringBuilder result = new StringBuilder();
        for (String part : id.split("-"))
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        return result.toString();
    }

    private static String displayName(String id) {
        return String.join(" ", java.util.Arrays.stream(id.split("-"))
                .map(p -> Character.toUpperCase(p.charAt(0)) + p.substring(1)).toList());
    }

    private static void deleteTree(Path root, Throwable failure) {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
    }

    @FunctionalInterface private interface BinaryWriter { void write(DataOutputStream output) throws IOException; }
    private static final class ScaffoldFailure extends RuntimeException {
        private final IOException cause;
        ScaffoldFailure(IOException cause) { super(cause); this.cause = cause; }
        @Override public synchronized IOException getCause() { return cause; }
    }
}
