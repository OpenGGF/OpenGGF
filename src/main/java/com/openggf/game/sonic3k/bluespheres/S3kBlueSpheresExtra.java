package com.openggf.game.sonic3k.bluespheres;

import com.openggf.data.RomByteReader;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomManager;
import com.openggf.game.nativemenu.NativeExtra;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Blue Spheres, the game S&amp;K starts when Sonic 1 is locked on, offered as
 * an S3K extra. The locked-on S3&amp;K image supplies every asset; the Sonic 1
 * image is read only for the header serial that unlocks the full game.
 */
public final class S3kBlueSpheresExtra implements NativeExtra {
    @Override
    public String id() {
        return "blue-spheres";
    }

    @Override
    public String labelKey() {
        return "hub.extras.blue_spheres";
    }

    @Override
    public String hostGameId() {
        return "s3k";
    }

    @Override
    public boolean available(RomManager roms) {
        return roms.isLogicalRomAvailable(RomIdentity.S3K) && S3kBlueSpheresLockOn.sonic1Available(roms);
    }

    @Override
    public NativeMenuScreen open(NativeMenuHostContext context) {
        var engine = context.engine();
        if (!(context.rootModule().getSpecialStageProvider() instanceof Sonic3kSpecialStageProvider stages)) {
            throw new IllegalStateException("Blue Spheres needs the S3K special stage");
        }
        try {
            var rom = engine.roms().getRom();
            RomByteReader sk = RomByteReader.fromRom(rom);
            if (!S3kBlueSpheresLockOn.unlocksFullGame(engine.roms(), sk)) {
                throw new IllegalStateException("The Sonic 1 image's serial does not unlock Blue Spheres");
            }
            return new S3kBlueSpheresScreen(rom, new S3kBlueSpheresAssets(sk), stages,
                    engine.audio(), engine.graphics());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open Blue Spheres", e);
        }
    }
}
