package com.openggf.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.openggf.configuration.KeyChord.Modifier.ALT;
import static com.openggf.configuration.KeyChord.Modifier.CTRL;
import static com.openggf.configuration.KeyChord.Modifier.META;
import static com.openggf.configuration.KeyChord.Modifier.SHIFT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_O;

class TestKeyChord {

    /**
     * Existing config.yaml files hold bare key codes and names. They must keep
     * their meaning exactly, as chords with no modifiers.
     */
    @ParameterizedTest
    @ValueSource(strings = {"O", "GLFW_KEY_O", "79"})
    void theFormsThatWorkedBeforeStillParseToTheSameUnmodifiedKey(String configured) {
        KeyChord chord = KeyChord.parse(configured);

        assertEquals(GLFW_KEY_O, chord.keyCode());
        assertTrue(chord.modifiers().isEmpty(), configured + " declares no modifier");
    }

    @Test
    void anIntegerConfigValueParsesWithoutGoingThroughText() {
        assertEquals(GLFW_KEY_O, KeyChord.parse(GLFW_KEY_O).keyCode());
    }

    @Test
    void modifiersParseInAnyOrderAndCase() {
        KeyChord expected = KeyChord.of(GLFW_KEY_O, CTRL, SHIFT);

        assertEquals(expected, KeyChord.parse("CTRL+SHIFT+O"));
        assertEquals(expected, KeyChord.parse("shift+ctrl+O"));
        assertEquals(expected, KeyChord.parse(" Ctrl + Shift + O "));
    }

    @Test
    void theNamesPlayersActuallyUseForEachModifierAreAccepted() {
        assertEquals(KeyChord.of(GLFW_KEY_O, CTRL), KeyChord.parse("CONTROL+O"));
        assertEquals(KeyChord.of(GLFW_KEY_O, ALT), KeyChord.parse("OPTION+O"));
        // GLFW calls it SUPER; macOS calls it Command; Windows calls it Win.
        for (String meta : new String[] {"META", "SUPER", "CMD", "COMMAND", "WIN"}) {
            assertEquals(KeyChord.of(GLFW_KEY_O, META), KeyChord.parse(meta + "+O"), meta);
        }
    }

    @Test
    void aMultiWordKeyNameSurvivesChording() {
        assertEquals(KeyChord.of(GLFW_KEY_LEFT_BRACKET, META),
                KeyChord.parse("META+LEFT_BRACKET"));
    }

    @Test
    void formattingRoundTripsThroughParsing() {
        for (String text : new String[] {"O", "CTRL+O", "CTRL+SHIFT+O",
                "CTRL+SHIFT+ALT+META+LEFT_BRACKET"}) {
            KeyChord chord = KeyChord.parse(text);
            assertEquals(text, chord.format(), "canonical form of " + text);
            assertEquals(chord, KeyChord.parse(chord.format()));
        }
    }

    @Test
    void formattingUsesOneCanonicalModifierOrderRegardlessOfInput() {
        assertEquals("CTRL+SHIFT+O", KeyChord.parse("shift+ctrl+O").format());
    }

    /**
     * A plain binding must not fire while a modifier is held, or it would steal
     * the chord a modified binding has claimed.
     */
    @Test
    void aPlainBindingDoesNotFireWhileAModifierIsHeld() {
        KeyChord plain = KeyChord.parse("O");

        assertTrue(plain.matchesModifiers(false, false, false, false));
        assertFalse(plain.matchesModifiers(true, false, false, false), "shift held");
        assertFalse(plain.matchesModifiers(false, true, false, false), "ctrl held");
        assertFalse(plain.matchesModifiers(false, false, true, false), "alt held");
        assertFalse(plain.matchesModifiers(false, false, false, true), "meta held");
    }

    @Test
    void aChordRequiresItsModifiersAndRejectsExtras() {
        KeyChord chord = KeyChord.parse("CTRL+SHIFT+O");

        assertTrue(chord.matchesModifiers(true, true, false, false));
        assertFalse(chord.matchesModifiers(true, false, false, false), "ctrl missing");
        assertFalse(chord.matchesModifiers(false, true, false, false), "shift missing");
        assertFalse(chord.matchesModifiers(true, true, true, false), "alt held as well");
    }

    @Test
    void unresolvableBindingsReportAsUnboundRatherThanThrowing() {
        for (Object configured : new Object[] {null, "", "   ", "NOT_A_KEY",
                "CTRL+NOT_A_KEY", "NOTAMODIFIER+O", "CTRL+"}) {
            KeyChord chord = KeyChord.parse(configured);
            assertFalse(chord.isBound(), "should be unbound: " + configured);
            assertEquals(KeyChord.NO_KEY, chord.keyCode());
        }
    }

    @Test
    void aChordIsValueEqualRegardlessOfHowItsModifierSetWasBuilt() {
        assertEquals(KeyChord.of(GLFW_KEY_O, SHIFT, CTRL),
                KeyChord.of(GLFW_KEY_O, CTRL, SHIFT));
        assertEquals(KeyChord.of(GLFW_KEY_O, SHIFT, CTRL).hashCode(),
                KeyChord.of(GLFW_KEY_O, CTRL, SHIFT).hashCode());
    }
}
