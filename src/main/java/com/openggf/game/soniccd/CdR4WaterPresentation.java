package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import java.util.Objects;

/** Value-only TT selected display metadata. No renderer, water bank, source admission or live service.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff r4/main.asm VBlankStage:1078-1157,
 * HBlank:1316-1365 and screen setup:188. Root binds the real producer and retained current images.
 */
public final class CdR4WaterPresentation {
    private CdR4WaterPresentation() { }
    /** Borrowed output of B's UpdateWater. split is the complete low BYTE, including dirty $E0..$FF.
     * This is neither a water state owner nor proof that UpdateWater was invoked. */
    public record WaterProjection(int waterYWord,int fullWaterByte,int splitByte) {
        public WaterProjection {word(waterYWord);octet(fullWaterByte);octet(splitByte);}
    }
    public enum Screen { LEVEL(32), WARP(0); private final int backdrop;Screen(int b){backdrop=b;}public int backdropIndex(){return backdrop;} }
    /** Both terrain and sprites use these same selected band references. No palette tint/RGB fallback.
     * Disabled display exposes no scene pixels; it does not make unknown pattern bytes known. */
    public record PaletteBands(CdAssetBytes first,CdAssetBytes afterSplit,int switchAfterLines,
                               boolean displayEnabled,Screen screen) {
        public PaletteBands {exact(first,128);exact(afterSplit,128);Objects.requireNonNull(screen);if(switchAfterLines<0||switchAfterLines>256)throw new IllegalArgumentException("Finite band boundary required");}
        public CdAssetBytes atLine(int line){if(line<0||line>=224)throw new IllegalArgumentException("Native visible line required");return line<switchAfterLines?first:afterSplit;}
        public int backdropIndex(){return screen.backdropIndex();}
        public boolean planesVisible(){return displayEnabled;} public boolean spritesVisible(){return displayEnabled;}
    }
    /** Semantic counter boundary, no beam or hardware model. $DF suppresses HBlank entirely.
     * HBlank selects water after split+1 lines; full-water changes the initial selection as well.
     * Normal and water remain different aliases even when equal-valued. */
    public static PaletteBands palettes(CdAssetBytes normal,CdAssetBytes water,WaterProjection borrowed,
                                       boolean displayEnabled,Screen screen) {
        Objects.requireNonNull(borrowed);exact(normal,128);exact(water,128);
        var first=borrowed.fullWaterByte()!=0?water:normal;
        return new PaletteBands(first,water,borrowed.splitByte()==0xDF?256:borrowed.splitByte()+1,displayEnabled,screen);
    }
    /** Root's selected image seam: reuse shared prefix/packing, retain selected tail verbatim. */
    public static CdAssetBytes selectHscroll(CdAssetBytes previous,CdAssetBytes live){return CdSelectedHscroll.copyNativePrefix(previous,live);}
    public static CdAssetBytes carriedHscroll(int[] packed256){return CdSelectedHscroll.fromCarriedWords(packed256);}
    /** Restore/rebind invariant only. It neither adopts an image nor runs services. */
    public static void requirePaletteAliases(PaletteBands saved,CdAssetBytes selectedNormal,CdAssetBytes selectedWater,WaterProjection savedWater){
        Objects.requireNonNull(saved);Objects.requireNonNull(savedWater);
        if(saved.first()!=(savedWater.fullWaterByte()!=0?selectedWater:selectedNormal)||saved.afterSplit()!=selectedWater
                ||saved.switchAfterLines()!=(savedWater.splitByte()==0xDF?256:savedWater.splitByte()+1))
            throw new IllegalArgumentException("Palette/source aliases or split changed during rebind");
    }
    private static void exact(CdAssetBytes b,int size){if(Objects.requireNonNull(b).length()!=size)throw new IllegalArgumentException("Complete native image required");}
    private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD required");}
    private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
}
