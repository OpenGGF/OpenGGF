package com.openggf.game.sonic2;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic2OneUpPresentation {
    @Test void oneUpHasIndependentFaceAndNativeFallback() {
        byte[] icon=new byte[256]; java.util.Arrays.fill(icon,(byte)2);
        byte[] face=new byte[256]; java.util.Arrays.fill(face,(byte)5);
        var presentation=new CharacterPresentation("MICA",null,
                Map.of(Host.S2,new Art(new Image(16,16,icon),new Image(16,16,face),null,null)),Map.of());
        var tiles=Sonic2CharacterPresentation.oneUp(presentation);
        assertEquals(4,tiles.length);
        for(var tile:tiles) for(int x=0;x<8;x++) for(int y=0;y<8;y++) assertEquals(5,tile.getPixel(x,y));
        assertNull(Sonic2CharacterPresentation.oneUp(new CharacterPresentation("MICA", null,
                Map.of(Host.S2, new Art(null, new Image(8, 8, new byte[64]), null, null)), Map.of())));
        assertNull(Sonic2CharacterPresentation.oneUp(null));
        assertNull(Sonic2CharacterPresentation.oneUp(new CharacterPresentation("MICA")));
    }
}
