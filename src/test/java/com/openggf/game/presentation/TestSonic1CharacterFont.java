package com.openggf.game.presentation;
import com.openggf.game.sonic1.Sonic1CharacterFont;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.util.PatternDecompressor;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
@RequiresRom(SonicGame.SONIC_1)
class TestSonic1CharacterFont {
 @Test void micaPixelsComeFromNativeLettersWithoutSupplements() throws Exception {
  var rom=TestEnvironment.currentRom();
  var font=Sonic1CharacterFont.title(rom);
  var result=CharacterNameComposer.compose("MICA",font,Map.of(),0);
  assertTrue(result.complete());
  assertEquals(Set.of('M','I','C','A'),result.nativeGlyphs());
  assertTrue(result.supplementaryGlyphs().isEmpty());
  var tiles=PatternDecompressor.nemesis(rom,Sonic1Constants.ART_NEM_TITLE_CARD_ADDR);
  int x=0; byte[] pixels=result.image().pixels();
  int[] offsets={42,32,8,0};
  for(int i=0;i<4;i++) {
   char c="MICA".charAt(i); int width=font.get(c).width();
   for(int y=0;y<16;y++) for(int px=0;px<width;px++)
    assertEquals(tiles[offsets[i]+px/8*2+y/8].getPixel(px%8,y%8),pixels[y*result.image().width()+x+px]);
   x+=width;
  }
 }
}
