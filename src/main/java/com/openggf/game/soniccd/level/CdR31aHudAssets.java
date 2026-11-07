package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.resources.CdLevelHud;
import java.util.*;

/** Original-USA R31A native HUD metadata, not an invocation, writer or current-pattern owner.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Level/_Objects/HUD and Points/Main.asm:186-717; Level/Main.asm:821-847;
 * Level/Collision Chaos/R3 Compatibility.asm:615-620; r3/data_1a.asm:307-317.
 * The full code view authenticates the source-selected consumer order; no native code is executed.
 */
public final class CdR31aHudAssets {
    public enum Source {
        NUMBERS(0x330BC,1024,"843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42"),
        UPDATE_HUD(0xA940,1118,"c61e027ce588825f96b3bd0109909ffd830e035e1995548292b3b16f2937fddb"),
        RESET_TILES(0xAB86,12,"3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c"),
        DIVISORS(0xACE6,40,"7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea"),
        VINT_CONSUMER(0x1DFA,6,"ba87bed07542537d09aa62734db209ea43d6f78fcdf1a844a9e80c3d8a2a3f60");
        private final int offset,length;private final String sha;
        Source(int offset,int length,String sha){this.offset=offset;this.length=length;this.sha=sha;}
    }
    public record Range(Source source,int fileOffset,int length,String sha256) {
        public Range {Objects.requireNonNull(source);if(fileOffset!=source.offset||length!=source.length||!source.sha.equals(sha256))throw new IllegalArgumentException("Not the fixed original HUD source span");}
        public int nativeAddress(){return 0x200000+fileOffset;}public int endExclusive(){return fileOffset+length;}
    }
    /** Native BYTE destinations, end-exclusive; data proof is not permission to write. */
    public record Destination(int destinationWord,int byteLength) {
        public Destination {if(!((destinationWord==0xB060&&byteLength==0x3C0)||(destinationWord==0xB4A0&&byteLength==64)))throw new IllegalArgumentException("Not a proved R31 native HUD destination");}
    }
    private final CdR31aAssets assets;private final CdAssetBytes[] views;
    private final List<Range> ranges;private final List<Destination> destinations;
    private final CdLevelHud.Assets hud;
    private CdR31aHudAssets(CdR31aAssets assets) {
        this.assets=assets;var source=Objects.requireNonNull(assets.hudSource(),"Actual retained HUD source");
        if(!source.owns(assets))throw new IllegalArgumentException("Foreign retained R31 HUD source");
        views=new CdAssetBytes[]{assets.view(CdR31aAssets.Asset.ART_HUD_NUMBERS),source.update(),source.reset(),source.divisors(),source.vint()};
        var rr=new ArrayList<Range>();for(var id:Source.values()){
            var bytes=Objects.requireNonNull(views[id.ordinal()]);if(bytes.length()!=id.length||!id.sha.equals(CdR11aProfile.sha256(bytes.copy())))throw new IllegalArgumentException("Original R31 HUD bytes differ: "+id);
            rr.add(new Range(id,id.offset,id.length,id.sha));}
        ranges=List.copyOf(rr);validateConsumers();
        hud=new CdLevelHud.Assets(view(Source.NUMBERS),view(Source.RESET_TILES),view(Source.DIVISORS));
        destinations=List.of(new Destination(0xB060,0x3C0),new Destination(0xB4A0,64));
    }
    public static CdR31aHudAssets fromAssets(CdR31aAssets assets){return new CdR31aHudAssets(Objects.requireNonNull(assets,"Actual original R31 aggregate"));}
    public boolean ownsAssets(CdR31aAssets assets){return this.assets==assets;}
    public CdR31aAssets.Identity identity(){return assets.identity();}
    public List<Range> ranges(){return ranges;}
    public CdAssetBytes view(Source source){return views[Objects.requireNonNull(source).ordinal()];}
    public CdLevelHud.Assets hudAssets(){return hud;}
    public List<Destination> destinations(){return destinations;}
    private void validateConsumers() {
        require(view(Source.VINT_CONSUMER),0,"4ef90020a940"); // Selector8 tail JMP UpdateHUD.
        for(int at:new int[]{0xAB58,0xABBA,0xAC36,0xACA6,0xAD4C})requireCode(at,"43f9002330bc");
        requireCode(0xAB32,"45fa005a343c0002");requireCode(0xAB50,"45fa0034343c0006");
        var code=view(Source.UPDATE_HUD);
        if(0xAB34+(short)code.unsignedWord(0xAB34-0xA940)!=0xAB8E||0xAB52+(short)code.unsignedWord(0xAB52-0xA940)!=0xAB86)throw new IllegalArgumentException("R31 PC-relative reset root differs");
        requireCode(0xAB42,"610001d4"); // ResetScore BSR.W reaches the original Lives writer atAD18 first.
        requireCode(0xAB5E,"323c000f"); // Sixteen LONG writes per64-byte reset glyph.
        int[][] roots={{0xABB0,0xACEA},{0xAC2C,0xACE6},{0xA986,0xACF2},{0xAC22,0xACF2},
                {0xAC9C,0xACF6},{0xAD42,0xACF6},{0xAD2E,0xACFA},{0xAD38,0xACFA},{0xAD10,0xACFE}};
        for(var r:roots)requireCode(r[0],"45f9"+String.format(Locale.ROOT,"%08x",0x200000+r[1]));
        requireCode(0xA9AA,"203c70600002");requireCode(0xAB46,"23fc7060000200c00004");
        requireCode(0xA9CE,"203c73600002");requireCode(0xAB28,"23fc7360000200c00004");
        requireCode(0xAA46,"203c72200002");requireCode(0xAA58,"203c72600002");
        requireCode(0xAA6A,"203c72e00002");requireCode(0xAD18,"203c74a00002");
        requireCode(0xA94C,"203c73600002");requireCode(0xAB92,"203c70e00002");requireCode(0xABA2,"203c72600002");
        // Bonus destinations are authenticated consumers, not additions to the dynamic HUD profile.
        requireCode(0xAAB8,"203c47800002");requireCode(0xAAD8,"203c48c00002");
        requireCode(0xAAC8,"203c6d400001");requireCode(0xAAE8,"203c6e800001");
        requireCode(0xAD92,"06800040000051ceffb84e75");
    }
    private void requireCode(int at,String hex){require(view(Source.UPDATE_HUD),at-0xA940,hex);}
    private static void require(CdAssetBytes bytes,int at,String hex){if(!HexFormat.of().formatHex(bytes.slice(at,hex.length()/2).copy()).equals(hex))throw new IllegalArgumentException("Original R31 HUD consumer operand differs");}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
