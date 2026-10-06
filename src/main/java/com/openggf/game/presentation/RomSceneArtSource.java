package com.openggf.game.presentation;

import com.openggf.level.Pattern;
import java.util.Map;

/** Engine-owned locally decoded ROM recipes, independent of runtime publication/animation clocks. */
public interface RomSceneArtSource {
    Map<String, Pattern[]> sceneArtRecipes();
}
