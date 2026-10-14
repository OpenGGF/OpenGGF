package com.openggf.game.sonic3k.mutators;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.ScopedPaletteGraphicsAccess;
import com.openggf.level.Palette;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Detached ROM donor colours for the two native encounters; never publishes a live level palette. */
final class NativeEncounterPalettes {
    // sonic3k.lst Pal_CNZ $A8FBC / Pal_S0Z2 $A955C: each is three lines loaded at native palette line 1.
    static final int CNZ_PALETTE=0xA8FBC, SOZ2_PALETTE=0xA955C;
    private record Bank(Object owner,GraphicsManager graphics,ScopedPaletteGraphicsAccess.Bank handle) { }
    private final Object worldOwner;
    private GraphicsManager lastHost;
    NativeEncounterPalettes() { this(new Object()); }
    NativeEncounterPalettes(Object worldOwner) { this.worldOwner=worldOwner; }
    private final Map<String,Palette[]> data=new HashMap<>();
    private final Map<String,Bank> banks=new HashMap<>();

    static Palette[] load(Rom rom,String key) throws IOException {
        int address=switch(key) {
            case Sonic3kObjectArtKeys.CNZ_BUMPER -> CNZ_PALETTE;
            case Sonic3kObjectArtKeys.SOZ_GHOSTS -> SOZ2_PALETTE;
            default -> throw new IllegalArgumentException("No qualified native encounter palette for "+key);
        };
        var reader=RomByteReader.fromRom(rom);Palette[] lines=new Palette[4];
        for(int line=0;line<4;line++) {
            lines[line]=new Palette();
            lines[line].fromSegaFormat(reader.slice(line==0?Sonic3kConstants.PAL_SONIC_TAILS_ADDR:address+(line-1)*32,32));
        }
        // Hyudoro_init_tbl uses art_tile palette 1. AnPal_SOZ2 lighting only rewrites lines 2/3;
        // darkness changes the ghost's animation/attack state, never its palette-1 colours (sonic3k.asm:3527..3545).
        return lines;
    }
    void prepare(Rom rom,GraphicsManager graphics,Set<String> needed) {
        if(lastHost!=null && lastHost!=graphics)ScopedPaletteGraphicsAccess.leaveAssembly(lastHost,worldOwner);
        lastHost=graphics;
        for(String key:Set.copyOf(banks.keySet())) if(!needed.contains(key) || banks.get(key).graphics()!=graphics) release(key);
        if(rom==null || graphics==null) return;
        if(!needed.isEmpty())ScopedPaletteGraphicsAccess.bindWorld(graphics,worldOwner);
        for(String key:needed) {
            var bank=banks.get(key);
            if(bank!=null && ScopedPaletteGraphicsAccess.valid(bank.handle(),graphics,bank.owner())) continue;
            if(bank!=null)release(key);
            try {
                var palette=data.get(key);
                if(palette==null) { palette=load(rom,key);data.put(key,palette); }
                Object owner=new Object();var handle=ScopedPaletteGraphicsAccess.acquire(graphics,owner,palette);
                banks.put(key,new Bank(owner,graphics,handle));
            } catch(IOException|IllegalStateException failure) {
                // Missing ROM data or a full bounded bank cannot borrow host colours.
            }
        }
    }
    int base(String key,GraphicsManager graphics) {
        var bank=banks.get(key);
        return bank!=null && ScopedPaletteGraphicsAccess.valid(bank.handle(),graphics,bank.owner())
                ?ScopedPaletteGraphicsAccess.base(bank.handle(),graphics,bank.owner()):-1;
    }
    private void release(String key) {
        var bank=banks.remove(key);if(bank!=null)ScopedPaletteGraphicsAccess.release(bank.handle(),bank.owner());
    }
    void leaveAssembly() {
        ScopedPaletteGraphicsAccess.leaveAssembly(lastHost,worldOwner);
    }
    void clear() { for(String key:Set.copyOf(banks.keySet()))release(key); }
}
