package com.openggf.game;

import com.openggf.level.objects.ObjectServices;

/**
 * A level runtime whose exit writes the title game mode at once and leaves the title's own entry
 * fade running over the retained level picture: S3K {@code loc_5F314}/{@code Sega_Screen} then
 * {@code Title_Screen}'s {@code Pal_FadeToBlack} (044fa46 sonic3k.asm:125858, 5419-5431, 5074).
 * Engine-internal; not part of the Mod API.
 */
public interface TitleEntryFadeSource {
    /** True from the exit pass until the last entry-fade pass has run. */
    boolean titleEntryFadePending();

    /** One entry-fade pass on the retained palette; true once the last pass has run. */
    boolean stepTitleEntryFade(ObjectServices services);
}
