package com.openggf.game;

import com.openggf.architecture.CompositionRoot;
import com.openggf.data.RomManager;
import com.openggf.game.nativemenu.NativeExtra;
import com.openggf.game.sonic3k.bluespheres.S3kBlueSpheresExtra;

import java.util.List;

/** The engine's built-in Extras, in display order; assembles the concrete game extras. */
@CompositionRoot
public final class BuiltInNativeExtras {
    private static final List<NativeExtra> ALL = List.of(new S3kBlueSpheresExtra());

    private BuiltInNativeExtras() {
    }

    public static List<NativeExtra> all() {
        return ALL;
    }

    /** The extras whose ROMs are all present. */
    public static List<NativeExtra> available(RomManager roms) {
        if (roms == null) return List.of();
        return ALL.stream().filter(extra -> extra.available(roms)).toList();
    }
}
