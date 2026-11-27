package com.openggf.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStockGameCatalogGuard {
    @Test
    void productionCodeDoesNotDuplicateStockGameLists() throws IOException {
        requireCatalogueUse(Path.of("src/main/java"));
    }

    @Test
    void rejectsAStockGameList(@TempDir Path sourceRoot) throws IOException {
        Files.writeString(sourceRoot.resolve("Duplicate.java"), """
                class Duplicate {
                    Object games = java.util.List.of("s1", "s2", "s3k", "scd");
                }
                """);
        assertThrows(AssertionFailedError.class, () -> requireCatalogueUse(sourceRoot));
    }

    private static void requireCatalogueUse(Path sourceRoot) throws IOException {
        var ids = Arrays.stream(GameId.values()).filter(id -> id != GameId.STANDALONE).toList();
        String codes = ids.stream().map(id -> Pattern.quote(id.code())).collect(Collectors.joining("|"));
        String names = ids.stream().map(GameId::name).collect(Collectors.joining("|"));
        String game = "(?:\"(?:" + codes + ")\"|GameId\\.(?:" + names + "))";
        Pattern list = Pattern.compile("\\b(?:List|Set|EnumSet)\\.of\\(\\s*(?:\"off\"\\s*,\\s*)?" + game
                + "(?:\\s*,\\s*" + game + ")+\\s*(?:,\\s*\"any\")?\\s*\\)");
        var violations = new ArrayList<String>();
        if (!ids.stream().map(GameId::code).toList().equals(com.openggf.catalog.StockGameCatalog.stockCodes())) {
            violations.add("StockGameCatalog must match the public GameId codes");
        }
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).sorted().toList()) {
                var matches = list.matcher(Files.readString(path));
                while (matches.find()) violations.add(path + " " + matches.group());
            }
        }
        assertTrue(violations.isEmpty(), () -> "Use StockGameCatalog for stock game lists:\n"
                + String.join("\n", violations));
    }
}
