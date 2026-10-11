package com.openggf.level.render;

import com.openggf.level.Pattern;
import java.util.List;

/**
 * A stock game's zone pictures for presentation (mod scenes' {@code SceneRomArt} zone pictures
 * and title cards, which convert these values to the API types). Engine-internal.
 * Implementations read only the ROM, on the calling thread, and never touch the running level,
 * graphics, session or settings. Results are not cached here.
 */
public interface ZonePictureSource {
    /**
     * How a game module offers its zone pictures: {@code getGameService(ZonePictureSource.Factory.class)}
     * returns one for games that have them, null otherwise. Zone indices follow the
     * module's public zone registry; a provider may support only selected acts.
     */
    @FunctionalInterface
    interface Factory {
        /** A picture source reading {@code rom}. */
        ZonePictureSource create(com.openggf.data.Rom rom);
    }

    /** Whether {@link #backdrop}, {@link #overview}, {@link #stages} and {@link #foreground} picture the act. */
    boolean supports(int zone, int act);

    /** The zone's background with its parallax bands, or null when unsupported. */
    Backdrop backdrop(int zone, int act);

    /**
     * The act's foreground over its background, cropped to the playable area and shrunk so it
     * is at most {@code maxHeight} pixels tall and 4096 wide, or null when unsupported.
     */
    Picture overview(int zone, int act, int maxHeight);

    /**
     * Runs of the act's floor at least {@code width} wide with {@code headroom} clear rows above
     * and at most {@code maxRise} rows between their highest and lowest floor, inside the
     * playable area, left to right ({@link LevelFloorScanner#stages}); empty when unsupported.
     */
    List<LevelFloorScanner.Stage> stages(int zone, int act, int width, int headroom, int maxRise);

    /**
     * The act's foreground plane for a world rectangle, unscaled, transparent where the plane
     * shows what is behind it (and outside the playable foreground), or null when unsupported.
     */
    Picture foreground(int zone, int act, int x, int y, int width, int height);

    /**
     * Whether {@link #kit} has the act. The default builds the kit to find out, so a provider
     * should cache what it builds.
     */
    default boolean hasKit(int zone, int act) {
        return kit(zone, act) != null;
    }

    /**
     * The act's building blocks (layout, block pictures, block collision, background and
     * palette) for presentations that assemble their own terrain, or null when unsupported.
     */
    default DetachedLevelKit kit(int zone, int act) {
        return null;
    }

    /** Independent native-scrolling presentation, or null when this act has no profile. */
    default DetachedBackground background(int zone, int act) { return null; }

    /** Whether {@link #titleCard} has the act's title card. */
    boolean hasTitleCard(int zone, int act);

    /** The act's title card elements, or null when it has none. */
    Sprites titleCard(int zone, int act);

    /** {@code 0xAARRGGBB} pixels, row by row from the top; opaque except in {@link #foreground}. */
    record Picture(int width, int height, int[] argb) {
    }

    /**
     * Picture rows {@code top .. top + height - 1} scrolling at {@code speed} times the camera
     * plus {@code drift} pixels per frame.
     */
    record Band(int top, int height, double speed, double drift) {
    }

    /** A background picture whose rows repeat at its width, with bands covering every row. */
    record Backdrop(Picture picture, List<Band> bands) {
    }

    /**
     * Sprite frames over a private tile copy: each frame's piece tile indices index
     * {@code tiles}, every piece uses palette line 0, and {@code paletteWords} holds that line
     * as 16 Mega Drive colour words (32 bytes).
     */
    record Sprites(Pattern[] tiles, List<SpriteMappingFrame> frames, byte[] paletteWords) {
    }
}
