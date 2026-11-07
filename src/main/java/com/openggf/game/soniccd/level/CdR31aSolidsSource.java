package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.objects.CdObjectAnimation;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import java.util.*;

/** Finite original USA R31A P2 binding, never a decoder/reader or live invocation authority.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: CC seven family Main.asm inputs.
 * Independent full programme identity, literal seven STORE operands and finite reader hulls.
 * B/C/D require their own authenticated definitions; equal retained data authorizes no dispatch. */
public final class CdR31aSolidsSource {
    enum View {
        SPIKESPROPERTIES(0xBFCC,12,"e5b3d605ae262ad11056d606032003ac92f701e206fb92a591939b65a3f8ee01"),
        BLOCKMOTION(0xC2BC,256,"13f07f0c707ef5b260da81e92fbd6864586776fd2a5f0f48afe5916f9eed8e1e"),
        METALRIGHT(0xC5A6,256,"049202409e9569db8f8104dd6ccc4b52af0bcbb5cad2de8b93b816a4bc066920"),
        METALLEFT(0xC5C6,256,"223d662145a23aaab6ca59712168f12fdb5700550145bc06fe79b80b1a2e75d9"),
        METALUP(0xC662,256,"59ae0591e037530e341d9c0d8dcfe05e6dcffd5fd65c05843e2fa084d7a00768"),
        METALDOWN(0xC682,256,"d6b3cb514fb7034df4f96505136216e28b26d10c7785f562260431f40a9ec4e1"),
        METALDROP5(0xC77E,256,"a4fd8a40414b66fe210bf170f6b7ddb0a92a624df882546c14956185646af1f8"),
        METALDROP6(0xC78A,256,"b5e0eec8f6c155acf2475a22f1500af2d4b8ef512460754558df004c181b28ef"),
        METALDROP7(0xC796,256,"d47fbda6136f301a26966bc23c5fdd911f903aa369b4bbf4ddc9c8e23a00b222"),
        DOORDELAYS(0xC7EA,16,"fe68fd543f36ce9695eae8f8b16648f03bc69819eb6c4fa7d809ccc18c3c11ff"),
        DRUMMOTION(0xCED8,258,"18ea9bf0756635ac360dc154be24fe641def3ad23db64eeff3b72ed1c31d40d1"),
        DRUMPRIORITIES(0xCF1C,256,"35f2542451a20acc58a52974d4af8d1d9b421a176f83fdbc0e6dda3804ba4119"),
        FIREANIMATION(0xCC66,34,"ef47c2fc2ea6ecefe0947e9e15204fc975c6d5c6e359c70b542b136e1c34242b"),
        MAP_SPIKES(0xC176,32,null),
        MAP_BLOCK(0xC2CC,8,null),
        MAP_HIDDEN(0xC434,16,null),
        MAP_METAL(0xC7A2,13,null),
        MAP_DOOR(0xC8C6,13,null),
        MAP_FIRE(0xCC88,122,null),
        MAP_DRUM(0xCF2E,161,null),
        STORE_SPIKES(0xBFFE,8,null),
        STORE_BLOCK(0xC1E6,8,null),
        STORE_HIDDEN(0xC320,8,null),
        STORE_METAL(0xC46E,8,null),
        STORE_DOOR(0xC800,8,null),
        STORE_FIRE(0xC934,8,null),
        STORE_DRUM(0xCDA6,8,null);
        final int offset,length;final String sha;
        View(int offset,int length,String sha){this.offset=offset;this.length=length;this.sha=sha;}
    }
    private final CdR31aAssets owner;
    private final CdR31SolidHazardAssets data;
    private CdR31aSolidsSource(CdR31aAssets owner){
        this.owner=Objects.requireNonNull(owner);owner.requireAuthenticated();
        if(!java.util.HexFormat.of().formatHex(read(View.STORE_SPIKES).copy()).equals("217c0020c1760004"))throw new IllegalArgumentException("Original R31 SPIKES mapping STORE differs");
        if(!java.util.HexFormat.of().formatHex(read(View.STORE_BLOCK).copy()).equals("217c0020c2cc0004"))throw new IllegalArgumentException("Original R31 BLOCK mapping STORE differs");
        if(!java.util.HexFormat.of().formatHex(read(View.STORE_HIDDEN).copy()).equals("217c0020c4340004"))throw new IllegalArgumentException("Original R31 HIDDEN mapping STORE differs");
        if(!java.util.HexFormat.of().formatHex(read(View.STORE_METAL).copy()).equals("217c0020c7a20004"))throw new IllegalArgumentException("Original R31 METAL mapping STORE differs");
        if(!java.util.HexFormat.of().formatHex(read(View.STORE_DOOR).copy()).equals("217c0020c8c60004"))throw new IllegalArgumentException("Original R31 DOOR mapping STORE differs");
        if(!java.util.HexFormat.of().formatHex(read(View.STORE_FIRE).copy()).equals("217c0020cc880004"))throw new IllegalArgumentException("Original R31 FIRE mapping STORE differs");
        if(!java.util.HexFormat.of().formatHex(read(View.STORE_DRUM).copy()).equals("217c0020cf2e0004"))throw new IllegalArgumentException("Original R31 DRUM mapping STORE differs");
        data=new CdR31SolidHazardAssets(0x20C176,0x20C2CC,0x20C434,0x20C7A2,0x20C8C6,0x20CC88,0x20CF2E,
read(View.SPIKESPROPERTIES),read(View.BLOCKMOTION),read(View.METALRIGHT),read(View.METALLEFT),read(View.METALUP),read(View.METALDOWN),read(View.METALDROP5),read(View.METALDROP6),read(View.METALDROP7),read(View.DOORDELAYS),read(View.DRUMMOTION),read(View.DRUMPRIORITIES),new CdObjectAnimation.AnimationTable(read(View.FIREANIMATION),0,5));
    }
    public static CdR31aSolidsSource fromAssets(CdR31aAssets owner){return new CdR31aSolidsSource(owner);}
    public boolean owns(CdR31aAssets candidate){return owner==candidate;}
    public CdR31SolidHazardAssets data(){return data;}
    private CdAssetBytes read(View v){return owner.internalReader().view(v);}
    public List<CdObjectMappings.DeclaredTable> mappings(){return List.of(
        new CdObjectMappings.DeclaredTable(0x20C176,read(View.MAP_SPIKES),0,4),
        new CdObjectMappings.DeclaredTable(0x20C2CC,read(View.MAP_BLOCK),0,1),
        new CdObjectMappings.DeclaredTable(0x20C434,read(View.MAP_HIDDEN),0,2),
        new CdObjectMappings.DeclaredTable(0x20C7A2,read(View.MAP_METAL),0,1),
        new CdObjectMappings.DeclaredTable(0x20C8C6,read(View.MAP_DOOR),0,1),
        new CdObjectMappings.DeclaredTable(0x20CC88,read(View.MAP_FIRE),0,7),
        new CdObjectMappings.DeclaredTable(0x20CF2E,read(View.MAP_DRUM),0,12));}
}
