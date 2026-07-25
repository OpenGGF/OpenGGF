package com.openggf.configuration;

import com.openggf.debug.DebugOverlayToggle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_P;

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
    private static final Path BUNDLED_CONFIG = Path.of("src/main/resources/config.yaml");
    private static final Path SRC_MAIN = Path.of("src/main/java");
    private static final Pattern BINDING_NAME = Pattern.compile("`([A-Z][A-Z0-9_]+)`");
    /** The "or a bare key such as X" example, in either surface's punctuation. */
    private static final Pattern BARE_KEY_EXAMPLE =
            Pattern.compile("bare key such as `?([A-Za-z0-9_]+)`?");

    @TempDir
    Path configDir;

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

    /**
     * The "chord permanently dead" row used to say a chorded value "resolves to
     * a live key code and still never fires". Only the first half is true: the
     * KEY branch of resolveInt keeps the chord's BARE key, so
     * {@code debug.playback.toggleKey: "CTRL+P"} is read as plain {@code P} and
     * an unmodified {@code P} does fire the shortcut. A player who trusted the
     * old wording believed the binding was inert while it was live on a key they
     * never chose. Doc and behaviour are pinned together here.
     */
    @Test
    void aChordOnAChordDeadBindingBindsItsBareKey() throws IOException {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(configDir);
        config.setConfigValue(SonicConfiguration.PLAYBACK_TOGGLE_KEY, "CTRL+P");

        assertEquals(GLFW_KEY_P, config.getInt(SonicConfiguration.PLAYBACK_TOGGLE_KEY),
                "a chord on a chord-unaware binding keeps its bare key");
        assertEquals(KeyChord.of(GLFW_KEY_P, KeyChord.Modifier.CTRL),
                config.getKeyChord(SonicConfiguration.PLAYBACK_TOGGLE_KEY));

        String row = deadChordRow();
        assertTrue(row.contains("binds plain `P`"),
                "the row must say the chord binds its bare key, not that it is inert: " + row);
        assertFalse(row.contains("still never fires"),
                "the old wording claimed the binding is inert; it is not: " + row);
    }

    /**
     * {@code capture.toggleKey} is the one chord-aware binding, so both shipped
     * surfaces show a bare-key example for "no modifier". That example must not
     * be a key {@link DebugOverlayToggle} already claims: those toggles are
     * hardcoded and PERFORMANCE (P) is dispatched above the debug-shortcuts
     * gate, so recommending a bare {@code P} started a recording AND flipped the
     * performance overlay on for its whole duration.
     */
    @Test
    void theRecommendedBareKeyExampleIsNotAHardcodedOverlayToggle() throws IOException {
        for (Path surface : List.of(CONFIGURATION_DOC, BUNDLED_CONFIG)) {
            Matcher matcher = BARE_KEY_EXAMPLE.matcher(Files.readString(surface));
            assertTrue(matcher.find(),
                    surface + " no longer names a bare-key example; re-point this guard");
            do {
                String name = matcher.group(1);
                OptionalInt keyCode = GlfwKeyNameResolver.resolve(name);
                assertTrue(keyCode.isPresent(),
                        surface + " recommends '" + name + "', which resolves to no key");
                for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
                    assertNotEquals(toggle.keyCode(), keyCode.getAsInt(),
                            surface + " recommends '" + name + "', which is the hardcoded "
                                    + toggle.label() + " overlay toggle");
                }
            } while (matcher.find());
        }
    }

    /** The whole "Chord permanently dead" table row. */
    private static String deadChordRow() throws IOException {
        for (String line : Files.readAllLines(CONFIGURATION_DOC)) {
            if (line.startsWith("| Chord permanently dead")) {
                return line;
            }
        }
        return fail("CONFIGURATION.md no longer has a 'Chord permanently dead' row");
    }

    /** The names in the "Chord permanently dead" cell, with the PLAYBACK glob expanded. */
    private static Set<String> deadChordBindings() throws IOException {
        String row = deadChordRow();
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
