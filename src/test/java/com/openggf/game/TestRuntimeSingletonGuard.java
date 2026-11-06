package com.openggf.game;

import com.openggf.tests.ObjectGuardSourceScanner;
import com.openggf.tests.ObjectGuardSourceScanner.SourceText;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Source-text guard for strict {@link GameServices} accessor null checks.
 * Direct runtime manager {@code getInstance()} calls are enforced by ArchUnit.
 */
public class TestRuntimeSingletonGuard {

    private static final Pattern STRICT_GAME_SERVICES_NULL_CHECK = Pattern.compile(
            "(?:com\\.openggf\\.game\\.)?GameServices\\.(camera|level|gameState|timers|rng|fade|sprites|collision|terrainCollision|parallax|water|bonusStage)\\(\\)\\s*(==|!=)\\s*null");

    private static final Pattern STRICT_GAME_SERVICES_LOCAL_NULL_CHECK = Pattern.compile(
            "(?:[\\w.<>?]+\\s+)?(\\w+)\\s*=\\s*(?:com\\.openggf\\.game\\.)?GameServices\\."
                    + "(camera|level|gameState|timers|rng|fade|sprites|collision|terrainCollision|parallax|water|bonusStage)\\(\\)\\s*;\\s*"
                    + "if\\s*\\(\\s*\\1\\s*(==|!=)\\s*null\\s*\\)");

    @Test
    public void productionCodeDoesNotNullCheckStrictGameServicesAccessors() throws IOException {
        Path srcMain = findSourceRoot();
        if (srcMain == null) {
            return;
        }

        List<String> violations = new ArrayList<>();

        Files.walkFileTree(srcMain, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (!file.toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }

                violations.addAll(findViolations(srcMain.relativize(file).toString(), Files.readAllLines(file)));

                return FileVisitResult.CONTINUE;
            }
        });

        if (!violations.isEmpty()) {
            fail("Found " + violations.size() + " null check(s) against strict GameServices accessors.\n"
                    + "Use GameServices.<name>OrNull() or remove the null check:\n  "
                    + violations.stream().collect(Collectors.joining("\n  ")));
        }
    }

    private Path findSourceRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path srcMain = cwd.resolve("src/main/java");
        if (Files.isDirectory(srcMain)) {
            return srcMain;
        }
        srcMain = cwd.getParent().resolve("src/main/java");
        if (Files.isDirectory(srcMain)) {
            return srcMain;
        }
        return null;
    }

    /** Each match requires this literal; unrelated files need no source map or regex scan. */
    static List<String> findViolations(String relativePath, List<String> lines) {
        if (lines.stream().noneMatch(line -> line.contains("GameServices."))) {
            return List.of();
        }
        SourceText source = ObjectGuardSourceScanner.sourceWithoutCommentOnlyLines(lines);
        List<String> violations = new ArrayList<>();
        Matcher matcher = STRICT_GAME_SERVICES_NULL_CHECK.matcher(source.text());
        while (matcher.find()) {
            violations.add(String.format(
                    "%s:%d - GameServices.%s() %s null",
                    relativePath, source.lineAt(matcher.start()), matcher.group(1), matcher.group(2)));
        }
        matcher = STRICT_GAME_SERVICES_LOCAL_NULL_CHECK.matcher(source.text());
        while (matcher.find()) {
            violations.add(String.format(
                    "%s:%d - %s = GameServices.%s(); if (%s %s null)",
                    relativePath, source.lineAt(matcher.start()), matcher.group(1), matcher.group(2),
                    matcher.group(1), matcher.group(3)));
        }
        return violations;
    }

    @Test
    void scannerPreservesHistoricalLineNumbersAndDiagnosticOrder() {
        assertEquals(List.of(
                "Fixture.java:2 - GameServices.camera() != null",
                "Fixture.java:5 - GameServices.rng() == null",
                "Fixture.java:6 - GameServices.fade() != null",
                "Fixture.java:3 - level = GameServices.level(); if (level == null)",
                "Fixture.java:10 - managers = GameServices.sprites(); if (managers != null)"),
                findViolations("Fixture.java", List.of(
                        "// GameServices.camera() == null",
                        "if (GameServices.camera() != null) {}",
                        "LevelManager level = com.openggf.game.GameServices.level();",
                        "if (level == null) {}",
                        "String text = \"GameServices.rng() == null\";",
                        "int i = 0; // GameServices.fade() != null",
                        "if (GameServices.levelOrNull() == null) {}",
                        "GameServices",
                        ".camera() == null",
                        "var managers = GameServices.sprites();",
                        "/* comment-only line */",
                        "if (managers != null) {}")));
    }

    @Test
    void scannerKeepsEveryStrictAccessorAndBothNullComparisons() {
        for (String accessor : List.of("camera", "level", "gameState", "timers", "rng", "fade",
                "sprites", "collision", "terrainCollision", "parallax", "water", "bonusStage")) {
            for (String operator : List.of("==", "!=")) {
                assertEquals(List.of("Access.java:2 - GameServices." + accessor + "() " + operator + " null",
                                "Access.java:3 - value = GameServices." + accessor + "(); if (value " + operator + " null)"),
                        findViolations("Access.java", List.of(
                                "* omitted comment",
                                "com.openggf.game.GameServices." + accessor + "() " + operator + " null",
                                "var value = GameServices." + accessor + "();",
                                "if (value " + operator + " null) {}")));
            }
        }
    }

    @Test
    void prefilterKeepsCommentsAndUnrelatedSourceHarmless() {
        assertEquals(List.of(), findViolations("NoAccess.java", List.of("z".repeat(80_000), "", "class Other {}")));
        assertEquals(List.of(), findViolations("Comments.java", List.of("// GameServices.camera() == null",
                "/* GameServices.level() != null", "* GameServices.rng() == null")));
        assertEquals(List.of(), findViolations("Optional.java", List.of("GameServices.cameraOrNull() == null")));
        assertEquals(List.of(), findViolations("Split.java", List.of("GameServices", ".camera() == null")));
    }

    @Test
    void prefilterRetainsWhitespaceFormsAcceptedByTheExistingRegexes() {
        assertEquals(List.of("Whitespace.java:2 - GameServices.camera() != null"),
                findViolations("Whitespace.java", List.of(" /* ignored comment */",
                        "\tcom.openggf.game.GameServices.camera()", "\t!=\t", "null")));
        assertEquals(List.of("Whitespace.java:2 - value = GameServices.level(); if (value == null)"),
                findViolations("Whitespace.java", List.of("// ignored comment",
                        "\tManager value\t=\tGameServices.level()\t;",
                        "\tif \t( \tvalue\t==\tnull\t)")));
        // Inline comments inside required tokens are not stripped by the old scanner.
        assertEquals(List.of(), findViolations("Inline.java", List.of("GameServices./* comment */camera() == null")));
    }
}
