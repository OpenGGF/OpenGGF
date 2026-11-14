package com.openggf.game.presentation;

import com.openggf.game.*;
import com.openggf.sprites.playable.SecondaryAbility;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestReelCharacterPresentation {
    private static CharacterDefinition character(String id, PlayerCharacter route, boolean powered) {
        byte[] pixels = new byte[1024]; Arrays.fill(pixels, (byte)2);
        var image = new CharacterPresentation.Image(32,32,pixels);
        int[] rgb = new int[16]; rgb[2] = 0x24926D;
        var face = new CharacterPresentation.ReelFace(image, powered ? image : null, rgb, powered ? rgb : null);
        var presentation = new CharacterPresentation(id).withReelFaces(Map.of(
                CharacterPresentation.Host.S2, face, CharacterPresentation.Host.S3K, face));
        return new CharacterDefinition(CharacterKey.mod("reel-test",id), id, (code,x,y)->null,
                null,route, SecondaryAbility.NONE,false,code->null,null,Set.of(),null,code->presentation,null);
    }
    @Test void nativeFallbackAndDimensions() {
        assertTrue(ReelCharacterPresentation.resolve(CharacterPresentation.Host.S2,null,null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new CharacterPresentation.ReelFace(
                new CharacterPresentation.Image(16,16,new byte[256]),null,new int[16],null));
    }
    @Test void teamAndSoloKeepSymbolIdsAndPoweredFallback() {
        var mica = character("mica",PlayerCharacter.SONIC_AND_TAILS,true);
        var talon = character("talon",PlayerCharacter.TAILS_ALONE,false);
        assertEquals(Set.of(0,1),ReelCharacterPresentation.resolve(CharacterPresentation.Host.S2,mica,talon).keySet());
        assertEquals(Set.of(1,2,7),ReelCharacterPresentation.resolve(CharacterPresentation.Host.S3K,mica,talon).keySet());
        assertEquals(Set.of(0,1),ReelCharacterPresentation.resolve(CharacterPresentation.Host.S2,talon,null).keySet());
        assertEquals(Set.of(1,2),ReelCharacterPresentation.resolve(CharacterPresentation.Host.S3K,talon,null).keySet());
        assertEquals(Set.of(1),ReelCharacterPresentation.resolve(CharacterPresentation.Host.S3K,
                character("no-powered",PlayerCharacter.SONIC_AND_TAILS,false),null).keySet());
    }
    @Test void palettesAreDefensivelyCopied() {
        var face=character("mica",PlayerCharacter.SONIC_AND_TAILS,true).presentationSupplier();
        assertDoesNotThrow(() -> {
            var r=face.load("reel-test:mica").reelFaces().get(CharacterPresentation.Host.S2);
            r.rgb()[2]=0; r.poweredRgb()[2]=0;
            assertEquals(0x24926D,r.rgb()[2]); assertEquals(0x24926D,r.poweredRgb()[2]);
        });
    }
}
