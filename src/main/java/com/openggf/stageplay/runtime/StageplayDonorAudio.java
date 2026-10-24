package com.openggf.stageplay.runtime;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.mods.scene.host.SceneRomAudioSources;
import com.openggf.mods.scene.host.SceneRomLibrary;

import java.util.LinkedHashMap;
import java.util.Map;

/** One disposable world's actor-ability sources, separate from world sounds and authored cue scopes. */
final class StageplayDonorAudio implements AutoCloseable {
    private final SceneRomLibrary library;
    private final AudioManager audio;
    private final String worldGame;
    private final Map<String, SceneRomAudioSources.Lease> sources = new LinkedHashMap<>();
    private boolean closed;

    StageplayDonorAudio(SceneRomLibrary library, AudioManager audio, String worldGame) {
        this.library = library; this.audio = audio; this.worldGame = worldGame;
    }

    void validate(String game, com.openggf.sprites.playable.AbstractPlayableSprite player) {
        var required=java.util.EnumSet.of(GameSound.JUMP,GameSound.ROLLING,GameSound.SKID);
        var rules=player.getGameRules().playerCapability();
        if (rules.spindashEnabled()) required.addAll(java.util.List.of(GameSound.SPINDASH_CHARGE,GameSound.SPINDASH_RELEASE));
        switch (player.getSecondaryAbility()) {
            case INSTA_SHIELD -> {
                if (rules.instaShieldEnabled()) required.add(GameSound.INSTA_SHIELD);
                if (rules.elementalShieldsEnabled()) required.addAll(java.util.List.of(GameSound.FIRE_ATTACK,
                        GameSound.LIGHTNING_ATTACK,GameSound.BUBBLE_ATTACK));
            }
            case FLY -> { if (rules.tailsFlightEnabled()) required.addAll(java.util.List.of(GameSound.TAILS_FLYING,GameSound.TAILS_FLY_TIRED)); }
            case GLIDE -> { if (rules.glideAttacksEnabled()) required.addAll(java.util.List.of(GameSound.GLIDE_LAND,GameSound.GRAB)); }
            case NONE -> { }
        }
        var profile=library.module(game).getAudioProfile();
        for (var sound:required) if (!profile.getSoundMap().containsKey(sound))
            throw new IllegalArgumentException("Native performer ROM is missing required movement sound " + sound + ": " + game);
    }

    void play(String actorGame, GameSound sound) {
        if (closed) throw new IllegalStateException("Native performer audio owner is closed");
        if (audio.isRewindReplaySuppressed()) return;
        if (actorGame.equals(worldGame)) { audio.playSfx(sound); return; }
        var profile = library.module(actorGame).getAudioProfile();
        Integer id = profile.getSoundMap().get(sound);
        if (id == null) throw new IllegalArgumentException("Native donor has no ability sound: " + sound);
        var source = sources.get(actorGame);
        if (source == null || !source.active()) sources.put(actorGame,library.acquireAudioSource(audio,actorGame));
        audio.playDonorSfx(actorGame,id);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { for (var source : sources.values()) source.close(); }
        finally { sources.clear(); }
    }
}
