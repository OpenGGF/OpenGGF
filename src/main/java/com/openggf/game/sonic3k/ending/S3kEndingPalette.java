package com.openggf.game.sonic3k.ending;

import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.level.objects.ObjectServices;
import java.io.IOException;
import java.io.UncheckedIOException;

/** DecColor_Obj / IncColor_Obj component steps, through the palette owner. */
public final class S3kEndingPalette {
    private S3kEndingPalette() { }
    public static void targetLine(ObjectServices services, int line, int address) {
        try {
            byte[] bytes = services.rom().readBytes(address, 32);
            if (services.paletteOwnershipRegistryOrNull() != null)
                services.paletteOwnershipRegistryOrNull().applyTargetPatch("s3k-ending", line, 0, bytes);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    public static void loadLine(ObjectServices services, int line, int address) {
        try { writeLine(services, line, services.rom().readBytes(address, 32)); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    public static void blackLine(ObjectServices services, int line) { writeLine(services, line, new byte[32]); }
    /** Copies consecutive ROM words into the listed colours of one line; the others keep their values. */
    public static void loadColors(ObjectServices services, int line, int address, int... colors) {
        try {
            byte[] source = services.rom().readBytes(address, colors.length * 2), bytes = new byte[32];
            for (int color = 0; color < 16; color++) {
                int current = word(services, line, color);
                bytes[color * 2] = (byte) (current >>> 8); bytes[color * 2 + 1] = (byte) current;
            }
            for (int i = 0; i < colors.length; i++) {
                bytes[colors[i] * 2] = source[i * 2]; bytes[colors[i] * 2 + 1] = source[i * 2 + 1];
            }
            writeLine(services, line, bytes);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    /** Level loc_64DC: Pal_FillBlack selects Pal_FillWhite for D01, offset20/count2F. */
    public static void fillInitialSceneWhite(ObjectServices services) {
        byte[] white=new byte[32];
        for(int color=0;color<16;color++){white[color*2]=0xE;white[color*2+1]=(byte)0xEE;}
        for(int line=1;line<4;line++)writeLine(services,line,white);
    }
    /** Animate_Palette/Pal_FromWhite, before Process_Sprites; FACE was cleared by Level. */
    public static void advanceInitialSceneFromWhite(ObjectServices services) {
        var ram=((com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState)services.zoneRuntimeState()).screen();
        ram.initialPaletteDelay=(short)(ram.initialPaletteDelay-1);
        if(ram.initialPaletteDelay>=0)return;
        ram.initialPaletteDelay=2;
        for(int line=1;line<4;line++) {
            byte[] bytes=services.paletteOwnershipRegistryOrNull().targetSegaData(line,0,16);
            for(int color=0;color<16;color++) {
                int target=(bytes[color*2]&255)<<8|(bytes[color*2+1]&255);
                int next=fromWhiteWord(word(services,line,color),target);
                bytes[color*2]=(byte)(next>>>8);bytes[color*2+1]=(byte)next;
            }
            writeLine(services,line,bytes);
        }
    }
    /** sub_85EB4 (sonic3k.asm:180701): each component of the line rises one level, up to $E. */
    public static void towardsWhite(ObjectServices services, int line) {
        byte[] bytes = new byte[32];
        for (int color = 0; color < 16; color++) {
            int current = word(services, line, color);
            int blue = (current >>> 8) & 0xE, green = current & 0xE0, red = current & 0xE;
            if (blue < 0xE) blue += 2;
            if (green < 0xE0) green += 0x20;
            if (red < 0xE) red += 2;
            int next = blue << 8 | green | red;
            bytes[color * 2] = (byte) (next >>> 8); bytes[color * 2 + 1] = (byte) next;
        }
        writeLine(services, line, bytes);
    }
    /** sub_85F2A (sonic3k.asm:180757): components above Target_palette fall one level. */
    public static void downTowardsActualTarget(ObjectServices services, int line) {
        byte[] bytes = services.paletteOwnershipRegistryOrNull().targetSegaData(line, 0, 16);
        for (int color = 0; color < 16; color++) {
            int target = (bytes[color * 2] & 255) << 8 | (bytes[color * 2 + 1] & 255);
            int next = fromWhiteWord(word(services, line, color), target);
            bytes[color * 2] = (byte) (next >>> 8); bytes[color * 2 + 1] = (byte) next;
        }
        writeLine(services, line, bytes);
    }
    /** Pal_DecColor2 masks each component and skips decrements at/below target (BLS). */
    static int fromWhiteWord(int current,int target) {
        int blue=(current>>>8)&0xE;
        if(blue>((target>>>8)&0xE))blue-=2;
        int green=current&0xE0,red=current&0xE;
        if(green>(target&0xE0))green-=0x20;
        if(red>(target&0xE))red-=2;
        return blue<<8|green|red;
    }

    /** Pal_ToBlack / Pal_DecColor consume red, then green, then blue on separate passes. */
    public static void toBlackAll(ObjectServices services) {
        for (int line=0;line<4;line++) {
            byte[] bytes=new byte[32];
            for(int color=0;color<16;color++) {
                int next=toBlackWord(word(services,line,color));
                bytes[color*2]=(byte)(next>>>8);bytes[color*2+1]=(byte)next;
            }
            writeLine(services,line,bytes);
        }
    }
    static int toBlackWord(int current) {
        return (current&0xE)!=0?current-2:(current&0xE0)!=0?current-0x20:(current&0xE00)!=0?current-0x200:current;
    }
    /** Pal_FromBlack consumes the actual Target_palette, including scene patches. */
    public static void fromBlackAll(ObjectServices services) {
        for(int line=0;line<4;line++) {
            byte[] bytes=services.paletteOwnershipRegistryOrNull().targetSegaData(line,0,16);
            for(int color=0;color<16;color++) {
                int target=(bytes[color*2]&255)<<8|(bytes[color*2+1]&255);
                int next=fromBlackWord(word(services,line,color),target);
                bytes[color*2]=(byte)(next>>>8);bytes[color*2+1]=(byte)next;
            }
            writeLine(services,line,bytes);
        }
    }
    static int fromBlackWord(int current,int target) {
        if(current==target)return current;
        if(current+0x200<=target)return current+0x200;
        if(current+0x20<=target)return current+0x20;
        return current+2;
    }
    /** DecColor_Obj is an independent-component object fade, unlike Pal_ToBlack. */
    public static void decreaseAll(ObjectServices services) {
        for (int line = 0; line < 4; line++) {
            byte[] bytes = new byte[32];
            for (int color = 0; color < 16; color++) {
                int current = word(services, line, color);
                int next = Math.max(0, (current & 0xE) - 2)
                        | Math.max(0, (current & 0xE0) - 0x20)
                        | Math.max(0, (current & 0xE00) - 0x200);
                bytes[color * 2] = (byte) (next >>> 8); bytes[color * 2 + 1] = (byte) next;
            }
            writeLine(services, line, bytes);
        }
    }
    public static void towardsTarget(ObjectServices services, int line, int address) {
        try {
            byte[] bytes = services.rom().readBytes(address, 32);
            for (int color = 0; color < 16; color++) {
                int target = ((bytes[color * 2] & 0xFF) << 8) | (bytes[color * 2 + 1] & 0xFF);
                int current = word(services, line, color);
                int next = componentStep(current & 0xE, target & 0xE, 2)
                        | componentStep(current & 0xE0, target & 0xE0, 0x20)
                        | componentStep(current & 0xE00, target & 0xE00, 0x200);
                bytes[color * 2] = (byte) (next >>> 8); bytes[color * 2 + 1] = (byte) next;
            }
            writeLine(services, line, bytes);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    /** Obj_FadeSelectedFromBlack reads the live Target_palette on every pass. */
    public static void towardsActualTarget(ObjectServices services,int line) {
        byte[] bytes=services.paletteOwnershipRegistryOrNull().targetSegaData(line,0,16);
        for(int color=0;color<16;color++) {
            int target=(bytes[color*2]&255)<<8|(bytes[color*2+1]&255);
            int current=word(services,line,color);
            int next=componentStep(current&0xE,target&0xE,2)
                    |componentStep(current&0xE0,target&0xE0,0x20)
                    |componentStep(current&0xE00,target&0xE00,0x200);
            bytes[color*2]=(byte)(next>>>8);bytes[color*2+1]=(byte)next;
        }
        writeLine(services,line,bytes);
    }
    private static int componentStep(int current, int target, int step) { return current < target ? current + step : current; }
    private static int word(ObjectServices services, int line, int color) {
        var c = services.currentLevel().getPalette(line).getColor(color);
        return component(c.r) << 1 | component(c.g) << 5 | component(c.b) << 9;
    }
    private static int component(byte value) { return ((value & 0xFF) * 7 + 127) / 255; }
    private static void writeLine(ObjectServices services, int line, byte[] bytes) {
        S3kPaletteWriteSupport.applyLine(services.paletteOwnershipRegistryOrNull(), services.currentLevel(),
                services.graphicsManager(), "s3k-ending", S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE, line, bytes, true);
    }
}
