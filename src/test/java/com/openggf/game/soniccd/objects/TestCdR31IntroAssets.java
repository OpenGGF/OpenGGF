package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class TestCdR31IntroAssets {
    static String sha(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @Test void actualIndependentMediaDataAndOddMappingIncludeMatchLiteralSourcePins() throws Exception {
        var a=TestCdIntroFamily.assets();assertEquals("30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e",a.identity().mmdSha256());
        assertEquals(168,a.introMappingBytes().length());assertEquals("eb38a737cdd24c287684b0aeed7b174d5c5dfca7f7b07e35219976216a8be6d1",sha(a.introMappingBytes().copy()));
        assertEquals(349,a.metalMappingBytes().length());assertEquals("e94e9b5df0e283b702055d4da47da4defc7cca591b0e565913a45ddb76ee0e6c",sha(a.metalMappingBytes().copy()));
        assertEquals("b9e1a38a05ed871f560fcfe4a8328e4d708088efa6829f94a28aa4e734deb893",sha(a.amyMappingBytes().copy()));
        assertEquals("d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc",sha(a.rollMappingBytes().copy()));
        assertEquals("08e5921be13b2edf4c11e4c8011d5bf47c6dc07832bdc6ae4a3d18e66435d83a",sha(a.amyAnimations().bytes().copy()));
        assertEquals("9f5c1780747528bd4b4be6450df80e4f48cd65e8d0da6f341671dca8fbc16bd3",sha(a.metalAnimations().bytes().copy()));
        assertEquals("587dc6989aa0662a6720c7583d4e840d53813bce3d36867fc7e392ff765f36e0",sha(a.exhaustAnimations().bytes().copy()));
        assertEquals("81dbdcc220921b5af9732c786743af92dcb0b8b4e9c12370c9f35cd61db030da",sha(a.amyPalette().copy()));
        assertEquals(0x200,a.shardX(0));assertEquals(-0x200,a.shardY(0));assertEquals(-0x100,a.shardX(1));assertEquals(-0x400,a.shardY(1));
        assertEquals(-0x100,a.shardX(2));assertEquals(-0x200,a.shardY(2));assertEquals(0x200,a.shardX(3));assertEquals(-0x400,a.shardY(3));
        assertEquals(0,a.sine(0));assertEquals(256,a.sine(0x40));assertEquals(-256,a.sine(0xC0));assertEquals(-25,a.sine(0xFC));
    }
}
