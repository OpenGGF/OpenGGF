package com.openggf.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static com.openggf.configuration.KeyChord.Modifier.ALT;
import static com.openggf.configuration.KeyChord.Modifier.CTRL;
import static com.openggf.configuration.KeyChord.Modifier.SHIFT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_O;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_P;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Q;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

/**
 * {@code getKeyChord} and {@code getInt} must agree on the key code for every
 * value form, or a binding read through one accessor points at a different key
 * than the same binding read through the other.
 */
class TestConfigKeyChordResolution {

    @TempDir
    Path tempDir;
    private SonicConfigurationService configService;

    @BeforeEach
    void setUp() {
        configService = SonicConfigurationService.createStandalone(tempDir);
    }

    @ParameterizedTest
    @ValueSource(strings = {"O", "GLFW_KEY_O", "79"})
    void anUnmodifiedBindingReadsTheSameThroughBothAccessors(String configured) {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, configured);

        assertEquals(GLFW_KEY_O, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
        assertEquals(KeyChord.of(GLFW_KEY_O),
                configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
    }

    /** getInt keeps returning the bare key so the unconverted bindings are untouched. */
    @Test
    void aChordedBindingKeepsItsBareKeyCodeForGetInt() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "CTRL+SHIFT+O");

        assertEquals(GLFW_KEY_O, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
        assertEquals(KeyChord.of(GLFW_KEY_O, CTRL, SHIFT),
                configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
    }

    @Test
    void aDigitBindingMeansTheNumberRowKeyThroughBothAccessors() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "1");

        assertEquals(GLFW_KEY_1, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
        assertEquals(GLFW_KEY_1,
                configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY).keyCode());
    }

    /**
     * JUMP is DERIVED and falls back to P1_A when unset; a chord accessor built on
     * getConfigValue alone would return NO_KEY for it.
     */
    @Test
    void aDerivedBindingFallsBackToItsSourceBinding() {
        configService.setConfigValue(SonicConfiguration.P1_A, "SPACE");

        assertEquals(GLFW_KEY_SPACE, configService.getInt(SonicConfiguration.JUMP));
        assertEquals(KeyChord.of(GLFW_KEY_SPACE),
                configService.getKeyChord(SonicConfiguration.JUMP));
    }

    /**
     * resolveInt logs a warning and returns the registered default, not unbound.
     * getKeyChord reconciles at the accessor so both agree.
     */
    @Test
    void anUnresolvableValueFallsBackToTheRegisteredDefault() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "NOT_A_KEY");

        assertEquals(GLFW_KEY_Q, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
        assertEquals(KeyChord.of(GLFW_KEY_Q),
                configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
    }

    /**
     * A default that is itself unbound stays unbound rather than resolving to -1
     * as a live key code. All nine PLAYBACK_* keys ship this way.
     */
    @Test
    void anUnboundDefaultReportsAsUnbound() {
        assertFalse(configService.getKeyChord(SonicConfiguration.PLAYBACK_TOGGLE_KEY).isBound());
    }

    /**
     * Deliberately unbinding a shortcut must unbind it through BOTH accessors.
     * resolveInt's default fallback is gated on a non-empty value, so an
     * explicitly empty value returns -1 without consulting the default. A chord
     * accessor that falls back unconditionally would re-bind the shortcut, and
     * for capture.toggleKey that means an unbound binding silently firing on
     * SHIFT+O. FRAME_STEP_KEY is used rather than a PLAYBACK_* key because its
     * registered default is bound (Q), so the two paths actually differ.
     */
    @Test
    void anExplicitlyEmptyValueStaysUnboundThroughBothAccessors() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "");

        assertEquals(-1, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
        assertFalse(configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY).isBound());
    }

    /**
     * The other half of the same gate, and the one that catches "improving" on
     * resolveInt. getString does not trim and resolveInt's gate is
     * {@code !str.isEmpty()} on that untrimmed value, so a whitespace-only value
     * is NOT the unbind form -- it is an unresolvable non-empty value, and both
     * accessors must fall back to the registered default. A
     * {@code trim().isEmpty()} gate in getKeyChord reports it unbound and the two
     * accessors disagree again, silently, because "" and " " look the same in a
     * review.
     */
    @Test
    void aWhitespaceOnlyValueFallsBackToTheDefaultThroughBothAccessors() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "   ");

        assertEquals(GLFW_KEY_Q, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
        assertEquals(KeyChord.of(GLFW_KEY_Q),
                configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
    }

    /**
     * {@code -1} is the other spelling of "deliberately unbound", and the one
     * the shipped config actually uses: CONFIGURATION.md documents it as the
     * default for P1_B, P1_C, P2_B and P2_C. resolveInt returns it verbatim --
     * an Integer goes straight through sanitizeIntValue, and the string form
     * parses to -1 at step 1, before the default lookup is ever reached -- so
     * getKeyChord must report unbound too. Substituting the registered default
     * here means a player who writes {@code capture.toggleKey: -1} has getInt
     * report the shortcut off while SHIFT+O keeps starting recordings.
     */
    @ParameterizedTest
    @ValueSource(strings = {"string", "integer"})
    void anExplicitMinusOneStaysUnboundThroughBothAccessors(String form) {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY,
                "integer".equals(form) ? Integer.valueOf(-1) : "-1");

        assertEquals(-1, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
        assertFalse(configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY).isBound());
    }

    @Test
    void aSessionOverrideWinsOverThePersistedValue() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "O");
        configService.setSessionOverride(SonicConfiguration.FRAME_STEP_KEY, "CTRL+P");

        assertEquals(KeyChord.of(GLFW_KEY_P, CTRL),
                configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));

        configService.clearSessionOverrides();

        assertEquals(KeyChord.of(GLFW_KEY_O),
                configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
    }

    /**
     * The chord cache must be invalidated wherever intCache is, or a rebind is
     * visible through getInt and stale through getKeyChord.
     */
    @Test
    void rebindingIsVisibleThroughBothAccessorsImmediately() {
        assertEquals(KeyChord.of(GLFW_KEY_Q),
                configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));

        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "ALT+P");

        assertEquals(GLFW_KEY_P, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
        assertEquals(KeyChord.of(GLFW_KEY_P, ALT),
                configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
    }

    @Test
    void aConvertedAndAnUnconvertedBindingCoexist() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "SHIFT+O");
        configService.setConfigValue(SonicConfiguration.PAUSE_KEY, "ENTER");

        assertEquals(KeyChord.of(GLFW_KEY_O, SHIFT),
                configService.getKeyChord(SonicConfiguration.FRAME_STEP_KEY));
        assertEquals(GLFW_KEY_ENTER, configService.getInt(SonicConfiguration.PAUSE_KEY));
    }
}
