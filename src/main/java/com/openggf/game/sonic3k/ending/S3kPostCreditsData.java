package com.openggf.game.sonic3k.ending;

import com.openggf.data.RomByteReader;
import com.openggf.game.PlayerCharacter;

/** Obj_5EF68/off_5EFB6: linked-on player/progress dispatch, read from the ROM. */
public final class S3kPostCreditsData {
    private S3kPostCreditsData() { }
    public record Dispatch(int bannerInitializer, int afterBanner) {
        /**
         * The loc_5F20A subtype each locked-on initializer leaves (sonic3k.asm:125542-125599):
         * loc_5F05C writes 4, loc_5F0C4 writes 8, and loc_5F0A6 writes none, so the
         * slot AllocateObject cleared keeps 0.
         */
        public int bannerSubtype() {
            return switch (bannerInitializer) {
                case 0x5F05C -> 4;
                case 0x5F0A6 -> 0;
                case 0x5F0C4 -> 8;
                default -> throw new IllegalStateException("Unknown native banner initializer $"
                        + Integer.toHexString(bannerInitializer));
            };
        }
        /** loc_5F0A6 alone also allocates loc_5F480, the Knuckles end pose. */
        public boolean knucklesPose() { return bannerInitializer == 0x5F0A6; }
    }
    public static Dispatch resolve(RomByteReader rom, PlayerCharacter character, int progress) {
        if(progress!=-2&&progress!=0&&progress!=2)throw new IllegalArgumentException("Native ending progress");
        int characterOffset=character==PlayerCharacter.KNUCKLES?0x60:character==PlayerCharacter.TAILS_ALONE?0x30:0;
        int offset=characterOffset+0x18+(progress<0?0:progress==0?8:16);
        int address=S3kEndingResources.POST_CREDITS_DISPATCH+offset;
        return new Dispatch(rom.readU32BE(address),rom.readU32BE(address+4));
    }
    /** sub_5FF1C (sonic3k.asm:126931): the subtype indexes word_5F260's x/y pairs. */
    public static int bannerX(RomByteReader rom, Dispatch dispatch) {
        return rom.readU16BE(S3kEndingResources.S3K_LOGO_POSITIONS + dispatch.bannerSubtype());
    }
    public static int bannerY(RomByteReader rom, Dispatch dispatch) {
        return rom.readU16BE(S3kEndingResources.S3K_LOGO_POSITIONS + dispatch.bannerSubtype() + 2);
    }
}
