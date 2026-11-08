package com.openggf.mods.assets;

import com.openggf.io.ModAssetRoot;
import com.openggf.io.PngCodec;

import com.openggf.game.ModApi;
import com.openggf.game.CharacterPresentation;

import java.io.*;

/** Bounded indexed-PNG reader for character-owned packaged presentation assets. */
@ModApi
public final class PresentationImages {
    private PresentationImages() { }
    public static CharacterPresentation.Image read(ModAssetRoot root, String entry) throws IOException {
        return decode(root, entry).image();
    }
    public static CharacterPresentation.Backing backing(ModAssetRoot root, String entry, int paletteLine)
            throws IOException {
        var decoded = decode(root, entry);
        return new CharacterPresentation.Backing(decoded.image(), paletteLine, decoded.rgb());
    }
    private record Decoded(CharacterPresentation.Image image, int[] rgb) { }
    private static Decoded decode(ModAssetRoot root, String entry) throws IOException {
        byte[] data = root.readBounded(entry, Math.min(262144, root.limits().maxAssetBytes()));
        var decoded = PngCodec.decodeIndexed(data, 256);
        return new Decoded(new CharacterPresentation.Image(decoded.width(), decoded.height(), decoded.indices()), decoded.rgb());
    }
}
