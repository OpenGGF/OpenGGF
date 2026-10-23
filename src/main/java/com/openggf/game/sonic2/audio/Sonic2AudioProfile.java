package com.openggf.game.sonic2.audio;

import com.openggf.audio.AbstractAudioProfile;
import com.openggf.audio.GameMusic;
import com.openggf.audio.GameSound;
import com.openggf.audio.NativeMailboxMusicRoutes;
import com.openggf.audio.SegaPcmSpec;
import com.openggf.game.audio.SegaPcmRomReader;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.session.SmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsStatefulCommandPolicy;
import com.openggf.audio.presentation.AudioRequestService;
import com.openggf.data.Rom;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class Sonic2AudioProfile extends AbstractAudioProfile implements NativeMailboxMusicRoutes {

    /**
     * Retail requests index {@code zMasterPlaylist}, not the engine's historical music ids.
     * {@code zBGMLoad} subtracts $81, reads the playlist metadata and follows its bank/pointer
     * index (s2.sounddriver.asm:1736-1779). The playlist order is owned by
     * {@code zMasterPlaylist} (s2.sounddriver.asm:3823-3855). Keep existing engine ids stable;
     * in particular, request $91 is Options while engine id $91 is EHZ 2P.
     */
    private static final Sonic2Music[] NATIVE_MUSIC_ROUTES = {
            Sonic2Music.RESULTS_2P, Sonic2Music.EMERALD_HILL,
            Sonic2Music.MYSTIC_CAVE_2P, Sonic2Music.OIL_OCEAN,
            Sonic2Music.METROPOLIS, Sonic2Music.HILL_TOP,
            Sonic2Music.AQUATIC_RUIN, Sonic2Music.CASINO_NIGHT_2P,
            Sonic2Music.CASINO_NIGHT, Sonic2Music.DEATH_EGG,
            Sonic2Music.MYSTIC_CAVE, Sonic2Music.EMERALD_HILL_2P,
            Sonic2Music.SKY_CHASE, Sonic2Music.CHEMICAL_PLANT,
            Sonic2Music.WING_FORTRESS, Sonic2Music.HIDDEN_PALACE,
            Sonic2Music.OPTIONS, Sonic2Music.SPECIAL_STAGE,
            Sonic2Music.BOSS, Sonic2Music.FINAL_BOSS,
            Sonic2Music.ENDING, Sonic2Music.SUPER_SONIC,
            Sonic2Music.INVINCIBILITY, Sonic2Music.EXTRA_LIFE,
            Sonic2Music.TITLE, Sonic2Music.ACT_CLEAR,
            Sonic2Music.GAME_OVER, Sonic2Music.CONTINUE,
            Sonic2Music.GOT_EMERALD, Sonic2Music.CREDITS,
            Sonic2Music.UNDERWATER
    };

    @Override
    public int musicForNativeRequest(int nativeRequestId) {
        int index = nativeRequestId - 0x81;
        return index >= 0 && index < NATIVE_MUSIC_ROUTES.length
                ? NATIVE_MUSIC_ROUTES[index].id : -1;
    }

    private final Consumer<Sonic2SoundRequestService.Event> requestObserver;

    @Override
    public SmpsStatefulCommandPolicy smpsStatefulCommandPolicy() {
        return Sonic2StatefulCommandPolicy.INSTANCE;
    }

    @Override
    public AudioRequestService createAudioRequestService() {
        Sonic2SoundRequestService service = new Sonic2SoundRequestService();
        service.addObserver(requestObserver);
        return service;
    }

    private static final Map<GameSound, Integer> SOUND_MAP;
    private static final Map<GameMusic, Integer> MUSIC_MAP;

    static {
        Map<GameSound, Integer> map = new EnumMap<>(GameSound.class);
        map.put(GameSound.JUMP, Sonic2Sfx.JUMP.id);
        map.put(GameSound.RING_LEFT, Sonic2Sfx.RING_LEFT.id);
        map.put(GameSound.RING_RIGHT, Sonic2Sfx.RING_RIGHT.id);
        map.put(GameSound.RING_SPILL, Sonic2Sfx.RING_SPILL.id);
        map.put(GameSound.SPINDASH_CHARGE, Sonic2Sfx.SPINDASH_CHARGE.id);
        map.put(GameSound.SPINDASH_RELEASE, Sonic2Sfx.SPINDASH_RELEASE.id);
        map.put(GameSound.SKID, Sonic2Sfx.SKIDDING.id);
        map.put(GameSound.HURT, Sonic2Sfx.HURT.id);
        map.put(GameSound.HURT_SPIKE, Sonic2Sfx.HURT_BY_SPIKES.id);
        map.put(GameSound.DROWN, Sonic2Sfx.DROWN.id);
        map.put(GameSound.BADNIK_HIT, Sonic2Sfx.EXPLOSION.id);
        map.put(GameSound.CHECKPOINT, Sonic2Sfx.CHECKPOINT.id);
        map.put(GameSound.SPRING, Sonic2Sfx.SPRING.id);
        map.put(GameSound.BUMPER, Sonic2Sfx.BUMPER.id);
        map.put(GameSound.BONUS_BUMPER, Sonic2Sfx.BONUS_BUMPER.id);
        map.put(GameSound.LARGE_BUMPER, Sonic2Sfx.LARGE_BUMPER.id);
        map.put(GameSound.FLIPPER, Sonic2Sfx.FLIPPER.id);
        map.put(GameSound.CNZ_LAUNCH, Sonic2Sfx.CNZ_LAUNCH.id);
        map.put(GameSound.CNZ_ELEVATOR, Sonic2Sfx.CNZ_ELEVATOR.id);
        map.put(GameSound.ROLLING, Sonic2Sfx.ROLL.id);
        map.put(GameSound.ERROR, Sonic2Sfx.ERROR.id);
        map.put(GameSound.SPLASH, Sonic2Sfx.SPLASH.id);
        map.put(GameSound.AIR_DING, Sonic2Sfx.WATER_WARNING.id);
        map.put(GameSound.SLOW_SMASH, Sonic2Sfx.SLOW_SMASH.id);
        map.put(GameSound.CASINO_BONUS, Sonic2Sfx.CASINO_BONUS.id);
        map.put(GameSound.OIL_SLIDE, Sonic2Sfx.OIL_SLIDE.id);
        SOUND_MAP = Collections.unmodifiableMap(map);

        Map<GameMusic, Integer> music = new EnumMap<>(GameMusic.class);
        music.put(GameMusic.ACT_CLEAR, Sonic2Music.ACT_CLEAR.id);
        music.put(GameMusic.DROWNING, Sonic2Music.UNDERWATER.id);
        music.put(GameMusic.EMERALD, Sonic2Music.GOT_EMERALD.id);
        music.put(GameMusic.EXTRA_LIFE, Sonic2Music.EXTRA_LIFE.id);
        music.put(GameMusic.INVINCIBILITY, Sonic2Music.INVINCIBILITY.id);
        music.put(GameMusic.SPECIAL_STAGE, Sonic2Music.SPECIAL_STAGE.id);
        music.put(GameMusic.SUPER, Sonic2Music.SUPER_SONIC.id);
        MUSIC_MAP = Collections.unmodifiableMap(music);
    }

    public Sonic2AudioProfile() {
        this(ignored -> { });
    }

    /** Installs an output-only observer on this profile's production request service. */
    public Sonic2AudioProfile(
            Consumer<Sonic2SoundRequestService.Event> requestObserver) {
        super(SOUND_MAP, MUSIC_MAP);
        this.requestObserver = Objects.requireNonNull(
                requestObserver, "requestObserver");
    }

    @Override
    public SmpsLoader createSmpsLoader(Rom rom) {
        return new Sonic2SmpsLoader(rom);
    }

    @Override
    public SmpsSequencerConfig getSequencerConfig() {
        return Sonic2SmpsSequencerConfig.CONFIG;
    }

    @Override
    public SmpsPhysicalPolicy smpsPhysicalPolicy() {
        return Sonic2SmpsCompatibilityPolicy.INSTANCE;
    }

    @Override
    public int getSpeedShoesOnCommandId() {
        return Sonic2SmpsConstants.CMD_SPEED_UP;
    }

    @Override
    public int getSpeedShoesOffCommandId() {
        return Sonic2SmpsConstants.CMD_SLOW_DOWN;
    }

    @Override
    public int getInvincibilityMusicId() {
        return Sonic2Music.INVINCIBILITY.id;
    }

    @Override
    public int getExtraLifeMusicId() {
        return Sonic2Music.EXTRA_LIFE.id;
    }

    @Override
    public int getDrowningMusicId() {
        return Sonic2Music.UNDERWATER.id;
    }

    @Override
    public int getSuperSonicMusicId() {
        return Sonic2Music.SUPER_SONIC.id;
    }

    @Override
    protected int getSegaCommandId() {
        return Sonic2SmpsConstants.CMD_SEGA;
    }

    @Override
    public SegaPcmSpec getSegaPcmSpec() {
        return new SegaPcmSpec(
                Sonic2SmpsConstants.SEGA_SOUND_ADDR,
                Sonic2SmpsConstants.SEGA_SOUND_SIZE,
                Sonic2SmpsConstants.SEGA_SOUND_SAMPLE_RATE);
    }

    /**
     * Reads the chant through the runtime-layer ROM reader so the audio layer
     * never depends on {@code com.openggf.data} for this sample.
     */
    @Override
    public byte[] loadSegaPcm(Object rom) throws java.io.IOException {
        return SegaPcmRomReader.read(rom, getSegaPcmSpec());
    }

    @Override
    public int getSfxPriority(int soundId) {
        return Sonic2SmpsConstants.getSfxPriority(soundId);
    }

    /**
     * {@code ResumeMusic} (s2disasm s2.asm:42296-42318) loads
     * {@code Level_Music}, then overrides it in three tests taken in order:
     * {@code status_secondary.invincible} selects {@code MusID_Invincible},
     * {@code Super_Sonic_flag} selects {@code MusID_SuperSonic}, and
     * {@code Current_Boss_ID} selects {@code MusID_Boss}. Each later test
     * overwrites the earlier one, so a boss wins over Super, which wins over
     * invincibility. Unlike S3K, Sonic 2 gives Super its own track rather than
     * reusing the invincibility theme.
     */
    @Override
    public int resolveAirResetMusic(int levelMusicId, boolean invincible,
            boolean superForm, boolean bossActive) {
        if (bossActive) {
            return Sonic2Music.BOSS.id;
        }
        if (superForm) {
            return Sonic2Music.SUPER_SONIC.id;
        }
        if (invincible) {
            return Sonic2Music.INVINCIBILITY.id;
        }
        return levelMusicId;
    }
}
