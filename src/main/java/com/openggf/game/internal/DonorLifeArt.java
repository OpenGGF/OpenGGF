package com.openggf.game.internal;

/** Cartridge-owned Nemesis life icon location, in the donor's native palette. */
public interface DonorLifeArt {
    int lifeArtAddress(String character);
    java.util.Map<Character, com.openggf.game.CharacterPresentation.Image> nameFont(com.openggf.data.Rom rom)
            throws java.io.IOException;
}
