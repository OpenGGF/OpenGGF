package com.openggf.configuration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CONFIGURATION.md's <em>Modifier support per binding</em> table tells a player
 * which bindings act on a chord, and its third state -- "chord permanently
 * dead" -- is invisible from the config file, so the table is the only place it
 * is written down. That makes a stale row a real defect: a player reads the row,
 * believes a shortcut is inert once it carries a modifier, and the bare key
 * keeps firing.
 *
 * <p>The row is therefore checked against the call sites rather than trusted.
 */
class TestModifierSupportDocumentation {

    private static final Path CONFIGURATION_DOC = Path.of("CONFIGURATION.md");
    private static final Path SRC_MAIN = Path.of("src/main/java");
    private static final Pattern BINDING_NAME = Pattern.compile("`([A-Z][A-Z0-9_]+)`");

    /**
     * Every binding the table calls permanently dead must be read only through
     * the "no modifier held" check. A single plain {@code isKeyPressed} read
     * elsewhere makes the binding dual-state instead, which the table describes
     * separately.
     */
    @Test
    void everyBindingDocumentedAsChordDeadIsOnlyReadThroughTheUnmodifiedCheck() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String name : deadChordBindings()) {
            List<String> reads = readSitesOf(name);
            if (reads.isEmpty()) {
                violations.add(name + " is documented as chord-dead but is never read in src/main"
                        + "; the table names a binding that no longer exists as a shortcut");
                continue;
            }
            for (String read : reads) {
                if (!read.contains("isUnmodifiedDebugKeyPressed(")
                        && !read.contains("isKeyPressedWithoutModifiers(")) {
                    violations.add(name + " is documented as chord-dead but is named outside an "
                            + "unmodified check (a hoisted lookup needs the row re-verified by "
                            + "hand): " + read.trim());
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("CONFIGURATION.md's 'Chord permanently dead' row disagrees with the call sites:\n  "
                    + String.join("\n  ", violations));
        }
    }

    /**
     * DEBUG_MODE_KEY is the one binding in both states at once: GameLoop reads
     * it through the unmodified check, while SpriteManager (via the debugModeKey
     * field) and LiveRewindInputSource read it with a plain isKeyPressed. The
     * table's dual-state paragraph must say so, or the modifiers it silently
     * drops read as a documented "dead chord".
     */
    @Test
    void theDualStateParagraphNamesDebugModeKey() throws IOException {
        String doc = Files.readString(CONFIGURATION_DOC);
        int paragraph = doc.indexOf("are in **two** states at once");
        assertTrue(paragraph >= 0, "the dual-state paragraph was reworded; re-point this guard");
        String sentence = doc.substring(Math.max(0, paragraph - 400), paragraph);

        assertTrue(sentence.contains("DEBUG_MODE_KEY"),
                "DEBUG_MODE_KEY has both an unmodified read and a plain isKeyPressed read, so the "
                        + "dual-state paragraph must name it");
        assertFalse(deadChordBindings().contains("DEBUG_MODE_KEY"),
                "DEBUG_MODE_KEY cannot also be listed as permanently dead");
    }

    /** The names in the "Chord permanently dead" cell, with the PLAYBACK glob expanded. */
    private static Set<String> deadChordBindings() throws IOException {
        String row = null;
        for (String line : Files.readAllLines(CONFIGURATION_DOC)) {
            if (line.startsWith("| Chord permanently dead")) {
                row = line;
                break;
            }
        }
        assertTrue(row != null, "CONFIGURATION.md no longer has a 'Chord permanently dead' row");

        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = BINDING_NAME.matcher(row);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        if (row.contains("`PLAYBACK_*` keys")) {
            names.remove("PLAYBACK_");
            List<String> playbackKeys = Stream.of(SonicConfiguration.values())
                    .map(Enum::name)
                    .filter(name -> name.startsWith("PLAYBACK_") && name.endsWith("_KEY"))
                    .toList();
            assertEquals(9, playbackKeys.size(),
                    "the row says nine PLAYBACK_* keys; the enum now has " + playbackKeys.size());
            names.addAll(playbackKeys);
        }
        // `"CTRL+P"` and the like appear in the cell's prose, not as binding names.
        names.removeIf(name -> Stream.of(SonicConfiguration.values())
                .noneMatch(key -> key.name().equals(name)));
        return names;
    }

    /** Every line in src/main that names the binding, outside the config plumbing. */
    private static List<String> readSitesOf(String name) throws IOException {
        Pattern reference = Pattern.compile("SonicConfiguration\\." + name + "(?![A-Z0-9_])");
        List<String> reads = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(SRC_MAIN)) {
            List<Path> files = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/')
                            .contains("com/openggf/configuration/"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                for (String line : Files.readAllLines(file)) {
                    if (reference.matcher(line).find()) {
                        reads.add(file + ": " + line);
                    }
                }
            }
        }
        return reads;
    }
}
