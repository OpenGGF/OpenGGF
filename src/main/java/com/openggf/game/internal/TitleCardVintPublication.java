package com.openggf.game.internal;

import com.openggf.game.TitleCardProvider;

/**
 * A title card built from sprite objects whose pieces reach the screen through the
 * V-int sprite-table upload, like every other sprite. The host calls
 * {@link #prepareTitleCardPresentation()} where the frame's sprite table is built
 * ({@code Render_Sprites}) and {@link #publishTitleCardPresentation()} at the V-int
 * publication that uploads it; the card draws the published generation.
 */
public interface TitleCardVintPublication {
    /** Records the card pieces' current positions and frames as this frame's sprite-table entries. */
    void prepareTitleCardPresentation();

    /** Publishes the last prepared generation as the displayed one. */
    void publishTitleCardPresentation();

    static void prepare(TitleCardProvider card) {
        if (card instanceof TitleCardVintPublication publication) {
            publication.prepareTitleCardPresentation();
        }
    }

    static void publish(TitleCardProvider card) {
        if (card instanceof TitleCardVintPublication publication) {
            publication.publishTitleCardPresentation();
        }
    }
}
