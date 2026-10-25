package com.openggf.game.sonic2.audio;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for Sonic 2 music names, engine IDs and native ROM IDs.
 * <p>Engine playback IDs retain the existing sparse numbering. Native request IDs
 * follow MusID__First through MusID__End (s2.constants.asm:832-864).
 */
public enum Sonic2Music {
    // ContinueScreen / zMusIDPtr_Continue (s2.constants.asm:860), not the empty mailbox byte.
    CONTINUE(0x9C, 0x9C, "Continue"),
    CASINO_NIGHT_2P(0x80, 0x88, "Casino Night Zone (2P)"),
    EMERALD_HILL(0x81, 0x82, "Emerald Hill Zone"),
    METROPOLIS(0x82, 0x85, "Metropolis Zone"),
    CASINO_NIGHT(0x83, 0x89, "Casino Night Zone"),
    MYSTIC_CAVE(0x84, 0x8B, "Mystic Cave Zone"),
    MYSTIC_CAVE_2P(0x85, 0x83, "Mystic Cave Zone (2P)"),
    AQUATIC_RUIN(0x86, 0x87, "Aquatic Ruin Zone"),
    DEATH_EGG(0x87, 0x8A, "Death Egg Zone"),
    SPECIAL_STAGE(0x88, 0x92, "Special Stage"),
    OPTIONS(0x89, 0x91, "Option Screen"),
    ENDING(0x8A, 0x95, "Ending"),
    FINAL_BOSS(0x8B, 0x94, "Final Battle"),
    CHEMICAL_PLANT(0x8C, 0x8E, "Chemical Plant Zone"),
    BOSS(0x8D, 0x93, "Boss"),
    SKY_CHASE(0x8E, 0x8D, "Sky Chase Zone"),
    OIL_OCEAN(0x8F, 0x84, "Oil Ocean Zone"),
    WING_FORTRESS(0x90, 0x8F, "Wing Fortress Zone"),
    EMERALD_HILL_2P(0x91, 0x8C, "Emerald Hill Zone (2P)"),
    RESULTS_2P(0x92, 0x81, "2P Results Screen"),
    SUPER_SONIC(0x93, 0x96, "Super Sonic"),
    HILL_TOP(0x94, 0x86, "Hill Top Zone"),
    TITLE(0x96, 0x99, "Title Screen"),
    ACT_CLEAR(0x97, 0x9A, "Stage Clear"),
    INVINCIBILITY(0x99, 0x97, "Invincibility"),
    HIDDEN_PALACE(0x9B, 0x90, "Hidden Palace Zone"),
    // zMusIDPtr_ExtraLife / MusID_ExtraLife in the shipped REV01 table
    // (s2.constants.asm:856); 0xB5 is the distinct right-ring SFX.
    EXTRA_LIFE(0x98, 0x98, "1-Up"),
    GAME_OVER(0xB8, 0x9B, "Game Over"),
    GOT_EMERALD(0xBA, 0x9D, "Got an Emerald"),
    CREDITS(0xBD, 0x9E, "Credits"),
    UNDERWATER(0xDC, 0x9F, "Underwater Timing");

    /** Existing engine playback ID; also used by {@link #fromId(int)} and {@link #titleMap()}. */
    public final int id;

    /** Shipped ROM request ID from zMasterPlaylist ordering. */
    public final int nativeId;

    /** Human-readable display name. */
    public final String displayName;

    private static final Map<Integer, String> TITLE_MAP;
    private static final Map<Integer, Sonic2Music> BY_ID;

    static {
        Map<Integer, String> titles = new LinkedHashMap<>();
        Map<Integer, Sonic2Music> lookup = new HashMap<>();
        for (Sonic2Music music : values()) {
            titles.put(music.id, music.displayName);
            lookup.put(music.id, music);
        }
        TITLE_MAP = Collections.unmodifiableMap(titles);
        BY_ID = Collections.unmodifiableMap(lookup);
    }

    Sonic2Music(int id, int nativeId, String displayName) {
        this.id = id;
        this.nativeId = nativeId;
        this.displayName = displayName;
    }

    /** Engine-ID-to-name map for the SoundTestCatalog. */
    public static Map<Integer, String> titleMap() {
        return TITLE_MAP;
    }

    /** Look up enum constant by engine playback ID, or null if not a music ID. */
    public static Sonic2Music fromId(int id) {
        return BY_ID.get(id);
    }
}
