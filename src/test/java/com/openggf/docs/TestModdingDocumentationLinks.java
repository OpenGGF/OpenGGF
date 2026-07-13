package com.openggf.docs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestModdingDocumentationLinks {
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^]]*]\\(([^)]+)\\)");

    @Test
    void everyRelativeModdingHandbookLinkResolves() throws IOException {
        Path root = Path.of("docs/modding").toAbsolutePath().normalize();
        List<String> dead = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".md")).sorted().toList()) {
                Matcher links = MARKDOWN_LINK.matcher(Files.readString(file));
                while (links.find()) {
                    String value = links.group(1).trim();
                    if (value.startsWith("<") && value.endsWith(">")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    int fragment = value.indexOf('#');
                    String target = fragment < 0 ? value : value.substring(0, fragment);
                    if (target.isBlank() || target.startsWith("#") || target.contains(":")) continue;
                    Path resolved = file.getParent().resolve(target).normalize();
                    if (!Files.exists(resolved)) {
                        dead.add(root.relativize(file).toString().replace('\\', '/') + " -> " + value);
                    }
                }
            }
        }
        assertTrue(dead.isEmpty(), () -> "Dead docs/modding relative links:\n" + String.join("\n", dead));
    }
}
