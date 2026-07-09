package com.openggf.game.timeattack.mp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestMenuTextField {
    @Test
    void acceptsAlnumAndAllowedExtras() {
        MenuTextField field = new MenuTextField(64, ".:-");
        for (char c : "192.168.1.5:27888".toCharArray()) {
            field.feedChar(c);
        }
        assertEquals("192.168.1.5:27888", field.text());
    }

    @Test
    void rejectsDisallowedCharsAndEnforcesMaxLength() {
        MenuTextField field = new MenuTextField(3, "");
        for (char c : "a!bcd".toCharArray()) {
            field.feedChar(c);
        }
        assertEquals("abc", field.text());
    }

    @Test
    void backspaceAndSetTextFilterAndClamp() {
        MenuTextField field = new MenuTextField(10, ".");
        field.setText("10.0.0.1");
        field.backspace();
        assertEquals("10.0.0.", field.text());
        field.setText("this-is-way-too-long!!");
        assertEquals("thisiswayt", field.text());
    }
}
