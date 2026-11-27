package com.openggf.control;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGamepadMappings {

    /** Xbox Wireless Controller over Bluetooth with firmware 0x0523, absent from GLFW 3.4.1's table. */
    private static final String XBOX_BT_0523 = "050000005e040000130b000023050000";

    @Test
    void bundledTableMapsTheBluetoothXboxFirmwareGlfwDoesNotKnow() {
        assertTrue(GamepadMappings.bundled().lines()
                .anyMatch(line -> line.startsWith(XBOX_BT_0523 + ",") && line.contains("platform:Linux")));
    }

    @Test
    void unknownFirmwareBorrowsTheNearestVersionOfTheSameDeviceAndPlatform() {
        String table = String.join("\n",
                "# comment",
                "050000005e040000130b000001050000,Old,a:b9,platform:Linux,",
                "050000005e040000130b000022050000,Near,a:b0,platform:Linux,",
                "050000005e040000130b000030050000,Mac,a:b1,platform:Mac OS X,",
                "050000005e040000140b000023050000,OtherProduct,a:b2,platform:Linux,");

        Optional<String> mapping = GamepadMappings.fallbackFor("050000005E040000130B000024050000", "Linux", table);

        assertEquals(Optional.of("050000005e040000130b000024050000,Near,a:b0,platform:Linux,"), mapping);
    }

    @Test
    void nameCrcBytesDoNotPreventAMatch() {
        String table = "0500abcd5e040000130b000022050000,Crc,a:b0,platform:Linux";

        assertEquals(Optional.of(XBOX_BT_0523 + ",Crc,a:b0,platform:Linux"),
                GamepadMappings.fallbackFor(XBOX_BT_0523, "Linux", table));
    }

    @Test
    void unrelatedOrMalformedGuidsGetNoMapping() {
        String table = GamepadMappings.bundled();

        assertEquals(Optional.empty(), GamepadMappings.fallbackFor("03000000adde0000efbe000001000000", "Linux", table));
        assertEquals(Optional.empty(), GamepadMappings.fallbackFor("not-a-guid", "Linux", table));
        assertEquals(Optional.empty(), GamepadMappings.fallbackFor(null, "Linux", table));
    }
}
