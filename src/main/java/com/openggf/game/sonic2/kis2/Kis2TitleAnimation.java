package com.openggf.game.sonic2.kis2;

/** Obj0E's KiS2 branch: position table, hand gestures, emblem drop and banner bounce. */
public final class Kis2TitleAnimation {
    private static final int[][] POSITIONS = {{116,80},{120,64},{124,50},{128,38},{132,30},{126,24},{120,20},{116,18}};
    private static final int[] LOWER = {0,-1,-3,-6,-10,-16,-24,-20,-18,-14,-13,-12,-13,-14,-16,-20,-24,-22,-21,-22,-24};
    private static final int[] HAND = {5,6,7,7,6,5};
    private int phase, counter, index, handPhase = -1, handCounter, handIndex;
    private int lowerIndex = -1, lowerCounter, handBaseY;
    private int bannerFixedY, bannerVelocity;
    public int frame, x = 144, y = 96, bodyFrame, handY, handFrame = 5, scroll, bannerY = -24;
    public boolean skipped, complete, bannerVisible, bannerSettled;

    public void tick(boolean skip) {
        frame++;
        if (skip && !complete) { skip(); return; }
        switch (phase) {
            case 0 -> { if (frame >= 56) phase++; }
            case 1 -> { if (frame >= 128) phase++; }
            case 2 -> {
                if (++counter % 4 == 0) {
                    if (index == POSITIONS.length) { phase++; counter = 0; index = 0; }
                    else { x = POSITIONS[index][0]; y = POSITIONS[index++][1]; }
                }
            }
            case 3 -> {
                // Ani_obj0E_Knuckles: duration 3, frames 0,1,2,3,$FA.
                if (counter-- <= 0) {
                    counter = 3;
                    if (index == 4) { phase++; }
                    else bodyFrame = index++;
                }
            }
            case 4 -> { bodyFrame = 4; handPhase = 0; phase++; }
            case 5 -> {
                if (!complete) y = 18 - scroll;
                if (frame >= 288) complete = true;
            }
            default -> { }
        }
        boolean hadBanner = bannerVisible;
        updateHand();
        if (lowerIndex >= 0 && lowerIndex < LOWER.length && lowerCounter-- <= 0) {
            scroll = LOWER[lowerIndex++]; lowerCounter = 1;
        }
        if (hadBanner && !bannerSettled) {
            // Obj0E_Banner_Move: 16.16 position; signed 8.8 gravity and rebound.
            bannerFixedY += bannerVelocity << 8;
            bannerVelocity += 0x38;
            bannerY = bannerFixedY >> 16;
            if (bannerVelocity >= 0 && bannerY >= 36) {
                bannerY = 36;
                // Shipped fixBugs=0 leaves the fractional accumulator unclamped.
                bannerVelocity = -(bannerVelocity >> 2);
                if (bannerVelocity >= -0x100) bannerSettled = true;
            }
        }
    }

    private void updateHand() {
        switch (handPhase) {
            case 0 -> { handY = y + 48; handCounter = 3; handPhase++; }
            case 1 -> { handY -= 2; if (--handCounter < 0) { handPhase++; handCounter = 0; handIndex = 0; } }
            case 2, 5 -> {
                if (handCounter-- <= 0) {
                    handCounter = 3;
                    if (handIndex == HAND.length) { handPhase++; handCounter = 3; }
                    else handFrame = HAND[handIndex++];
                }
            }
            case 3 -> {
                handY += 2;
                if (--handCounter < 0) { handBaseY = handY; handPhase++; lowerIndex = 0; }
            }
            case 4 -> {
                if (!complete) handY = handBaseY - scroll;
                if (frame == 293) { handPhase++; handIndex = 0; handCounter = 0; }
            }
            case 6 -> { handPhase++; bannerVisible = true; bannerFixedY = -24 << 16; bannerVelocity = 0x200; }
            default -> { }
        }
    }

    /** TitleScreen_SetFinalState intentionally places the skipped banner at 32, natural bounce at 36. */
    public void skip() {
        skipped = true; complete = true; phase = 5; bodyFrame = 4; x = 116; y = 42;
        handPhase = 7; handFrame = 5; handY = 90;
        lowerIndex = LOWER.length; scroll = -24;
        bannerVisible = true; bannerSettled = true; bannerY = 32;
    }
    public boolean handVisible() { return handPhase >= 0; }
}
