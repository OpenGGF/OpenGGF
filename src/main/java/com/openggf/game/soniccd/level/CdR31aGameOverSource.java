package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.objects.CdGameOver;
import java.util.Objects;

/** Finite original USA R31A DATA, not a parser, art product or invocation token.
 * N1ecc5f3a: Collision Chaos/R31A Legacy -> R3 Legacy Act 1 Shared (R3_SEMANTIC_GAME_OVER=1)
 * -> Level/_Objects/Game Over/Main.asm:27-112 and Data/Mappings.asm:1-58.
 * Full programme authentication precedes the independent physical dispatch and STORE operands. */
public final class CdR31aGameOverSource {
    enum View {
        CODE(0xF17E,0xE4), MAPPINGS(0xF262,56);
        final int offset,length;View(int offset,int length){this.offset=offset;this.length=length;}
    }
    private final CdR31aAssets owner;private final CdGameOver.Assets data;
    private CdR31aGameOverSource(CdR31aAssets owner){
        this.owner=Objects.requireNonNull(owner);owner.requireAuthenticated();
        var code=owner.internalReader().view(View.CODE);
        if(owner.view(CdR31aAssets.Asset.OBJECT_INDEX).unsignedLong((0x3B-1)*4)!=0x20F17E
                ||code.unsignedWord(14)!=4||code.unsignedWord(16)!=0xBC
                ||code.unsignedWord(0x12)!=0x303C||code.unsignedWord(0x14)!=0x82
                ||code.unsignedWord(0x38)!=0x217C||code.unsignedLong(0x3A)!=0x20F262||code.unsignedWord(0x3E)!=4
                ||code.unsignedWord(0xC0)!=0x303C||code.unsignedWord(0xC2)!=0x6E||code.unsignedWord(0xC4)!=0x4EF9||code.unsignedWord(0xCA)!=0x7008
                ||code.unsignedWord(0x5A)!=0x217C||code.unsignedLong(0x5C)!=0x20F266||code.unsignedWord(0x60)!=4)
            throw new IllegalArgumentException("Original R31 Game Over dispatch/command/mapping operands differ");
        data=new CdGameOver.Assets(owner.internalReader().view(View.MAPPINGS),0x20F262);
    }
    public static CdR31aGameOverSource fromAssets(CdR31aAssets owner){return new CdR31aGameOverSource(owner);}
    public boolean owns(CdR31aAssets candidate){return owner==candidate;}
    public CdGameOver.Assets data(){return data;}
}
