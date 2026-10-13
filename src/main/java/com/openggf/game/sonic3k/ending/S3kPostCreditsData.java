package com.openggf.game.sonic3k.ending;

import com.openggf.data.RomByteReader;
import com.openggf.game.PlayerCharacter;

/** Obj_5EF68/off_5EFB6: linked-on player/progress dispatch, read from the ROM. */
public final class S3kPostCreditsData {
    private S3kPostCreditsData() { }
    public record Dispatch(int bannerInitializer, int afterBanner) { }
    public static Dispatch resolve(RomByteReader rom, PlayerCharacter character, int progress) {
        if(progress!=-2&&progress!=0&&progress!=2)throw new IllegalArgumentException("Native ending progress");
        int characterOffset=character==PlayerCharacter.KNUCKLES?0x60:character==PlayerCharacter.TAILS_ALONE?0x30:0;
        int offset=characterOffset+0x18+(progress<0?0:progress==0?8:16);
        int address=S3kEndingResources.POST_CREDITS_DISPATCH+offset;
        return new Dispatch(rom.readU32BE(address),rom.readU32BE(address+4));
    }
}
