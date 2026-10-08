package com.openggf.mods.scene.art;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** A scene-owned, bounded sprite-bank cache. Closing drops references; image lifetime belongs to the host. */
@com.openggf.game.ModApi
public final class SceneArtCache implements AutoCloseable {
    private SceneRomArt rom;
    private final int capacity;
    private final LinkedHashMap<Key, SceneSpriteSet> sprites = new LinkedHashMap<>();
    public SceneArtCache(SceneRomArt rom, int capacity) {
        this.rom = Objects.requireNonNull(rom, "ROM art");
        if (capacity < 1 || capacity > 128) throw new IllegalArgumentException("Cache capacity must be 1..128 banks");
        this.capacity = capacity;
    }
    public SceneSpriteSet sprites(StockSceneArt recipe, int[] palette) {
        requireOpen();
        return sprites(Objects.requireNonNull(recipe, "recipe").request(rom), palette);
    }
    /** Raw requests remain available for expert art that has no named recipe. */
    public SceneSpriteSet sprites(RomSpriteRequest request, int[] palette) {
        requireOpen();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(palette, "palette");
        if (palette.length < 1 || palette.length > 64) throw new IllegalArgumentException("Palette must contain 1..64 colours");
        List<Integer> colors = new ArrayList<>(64);
        for (int index = 0; index < palette.length; index++) colors.add(palette[index]);
        Key key = new Key(request, List.copyOf(colors));
        SceneSpriteSet found = sprites.get(key);
        if (found != null) return found;
        SceneSpriteSet loaded = Objects.requireNonNull(rom.sprites(request, palette.clone()), "decoded sprites");
        if (sprites.size() == capacity) sprites.remove(sprites.keySet().iterator().next());
        sprites.put(key, loaded);
        return loaded;
    }
    public int size() { return sprites.size(); }
    private void requireOpen() { if (rom == null) throw new IllegalStateException("Scene art cache is closed"); }
    @Override public void close() { sprites.clear(); rom = null; }
    private record Key(RomSpriteRequest request, List<Integer> palette) { }
}
