package com.openggf.game.sonic3k.audio;

import com.openggf.audio.AbstractAudioProfile;
import com.openggf.audio.AudioManager;
import com.openggf.audio.GameMusic;
import com.openggf.audio.GameSound;
import com.openggf.audio.SegaPcmSpec;
import com.openggf.game.audio.SegaPcmRomReader;
import com.openggf.game.audio.RomSoundCatalog;
import com.openggf.game.audio.RomSoundCatalog.Entry;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.session.SmpsPhysicalPolicy;
import com.openggf.audio.session.SmpsStatefulCommandPolicy;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.game.sonic3k.audio.smps.Sonic3kCoordFlagHandler;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Sonic 3 &amp; Knuckles audio profile.
 *
 * <p>Key differences from S1/S2:
 * <ul>
 *   <li>Speed shoes use FRAME_MULTIPLY mode (Z80 RAM 0x1C08) instead of tempo swap.</li>
 *   <li>S3K coordination flags are entirely different from S2 (pluggable handler).</li>
 *   <li>Music data is bank-switched raw data (not Saxman compressed).</li>
 *   <li>DAC samples use DPCM compression with bank switching.</li>
 * </ul>
 */
public class Sonic3kAudioProfile extends AbstractAudioProfile implements com.openggf.audio.NativeAudioEffectCues,
        com.openggf.audio.NativeMailboxMusicRoutes {
    // mus_AIZ1 through mus_Ending are native $01-$32
    // (sonic3k.constants.asm:1463-1512). Engine-only S3 driver variants are excluded.
    // mus_CreditsK (sonic3k.constants.asm:1691) is native $DC; $33 belongs to
    // sfx_RingRight (sonic3k.constants.asm:1521), even though the engine uses $33 for credits.
    // zPlaySoundByIndex (Sound/Z80 Sound Driver.asm:1641-1651) dispatches credits before SFX.
    private static final RomSoundCatalog.Catalog NATIVE_SOUNDS = new RomSoundCatalog.Catalog(
            Arrays.stream(Sonic3kMusic.values())
                    .filter(music -> music.id < Sonic3kMusic.S3_MUSIC_ID_BASE)
                    .map(music -> new Entry(music.name(),
                            music == Sonic3kMusic.MINIBOSS_S3 ? "Mini-Boss (S&K)" : music.displayName,
                            music == Sonic3kMusic.CREDITS_SK ? 0xDC : music.id, music.id)).toList(),
            Arrays.stream(Sonic3kSfx.values()).map(sfx ->
                    new Entry(sfx.name(), sfx.displayName, sfx.id, sfx.id)).toList());

    /** Native request IDs and names, independent of ROM loading and live playback. */
    public static RomSoundCatalog.Catalog nativeSounds() { return NATIVE_SOUNDS; }

    /** {@code mus_CreditsK}, placed after the SFX ids (sonic3k.constants.asm:1691). */
    private static final int MUS_CREDITS_K = 0xDC;

    private final Sonic3kAudioResourceProfile resources;

    /**
     * {@code zPlaySoundByIndex} (Sound/Z80 Sound Driver.asm:1641-1644) tests the native
     * credits request before every other range and jumps to the credits routine.
     * {@code zPlayMusicCredits} (Sound/Z80 Sound Driver.asm:1709-1712) loads the last
     * music-table entry, {@code $32}, and continues as ordinary music. Engine music
     * ids are one-based, so that entry is {@link Sonic3kMusic#CREDITS_SK}.
     */
    @Override public int musicForNativeRequest(int nativeRequestId) {
        if (resources == Sonic3kAudioResourceProfile.SONIC_3) {
            return nativeRequestId >= 1 && nativeRequestId <= 0x32
                    ? resources.musicId(nativeRequestId) : -1;
        }
        return nativeRequestId == MUS_CREDITS_K ? Sonic3kMusic.CREDITS_SK.id : -1;
    }
    @Override public boolean permitsTemporaryMusic(int musicId) {
        // The locked-on driver table's first 23 entries are ordinary zone music, not priority jingles.
        return musicId >= resources.musicId(Sonic3kMusic.AIZ1.id)
                && musicId <= resources.musicId(Sonic3kMusic.DEZ2.id);
    }
    @Override public boolean permitsLandingCue(int sfxId) { return sfxId == Sonic3kSfx.THUMP.id; }
    @Override public int groundSlideSfxId() { return Sonic3kSfx.GROUND_SLIDE.id; }
    @Override public boolean isWarningCue(int sfxId) {
        return sfxId == Sonic3kSfx.AIR_DING.id || sfxId == Sonic3kSfx.ALARM.id
                || sfxId == Sonic3kSfx.ROBOTNIK_SIREN.id || sfxId == Sonic3kSfx.DEATH.id
                || sfxId == Sonic3kSfx.SPIKE_HIT.id || sfxId == Sonic3kSfx.DROWN.id
                || sfxId == Sonic3kSfx.RING_LOSS.id;
    }
    @Override
    public String presentationGameId() {
        return "s3k";
    }

    @Override
    public void configurePresentationCoordFlagHandlers(
            SmpsCoordFlagHandlerOwner owner) {
        owner.register("s3k", Sonic3kCoordFlagHandler::new);
    }

    private static final Map<GameSound, Integer> SOUND_MAP;
    private static final Map<GameMusic, Integer> MUSIC_MAP;

    static {
        Map<GameSound, Integer> map = new EnumMap<>(GameSound.class);
        map.put(GameSound.JUMP, Sonic3kSfx.JUMP.id);
        map.put(GameSound.RING_LEFT, Sonic3kSfx.RING_LEFT.id);
        map.put(GameSound.RING_RIGHT, Sonic3kSfx.RING_RIGHT.id);
        map.put(GameSound.RING_SPILL, Sonic3kSfx.RING_LOSS.id);
        map.put(GameSound.SPINDASH_CHARGE, Sonic3kSfx.SPINDASH.id);
        map.put(GameSound.SPINDASH_RELEASE, Sonic3kSfx.DASH.id);
        map.put(GameSound.SKID, Sonic3kSfx.SKID.id);
        map.put(GameSound.HURT, Sonic3kSfx.DEATH.id);
        map.put(GameSound.HURT_SPIKE, Sonic3kSfx.SPIKE_HIT.id);
        map.put(GameSound.BADNIK_HIT, Sonic3kSfx.BOSS_HIT.id);
        map.put(GameSound.CHECKPOINT, Sonic3kSfx.STARPOST.id);
        map.put(GameSound.SPRING, Sonic3kSfx.SPRING.id);
        map.put(GameSound.BUMPER, Sonic3kSfx.BUMPER.id);
        map.put(GameSound.ROLLING, Sonic3kSfx.ROLL.id);
        map.put(GameSound.SPLASH, Sonic3kSfx.SPLASH.id);
        map.put(GameSound.DROWN, Sonic3kSfx.DROWN.id);
        map.put(GameSound.AIR_DING, Sonic3kSfx.AIR_DING.id);
        map.put(GameSound.FIRE_SHIELD, Sonic3kSfx.FIRE_SHIELD.id);
        map.put(GameSound.LIGHTNING_SHIELD, Sonic3kSfx.LIGHTNING_SHIELD.id);
        map.put(GameSound.BUBBLE_SHIELD, Sonic3kSfx.BUBBLE_SHIELD.id);
        map.put(GameSound.FIRE_ATTACK, Sonic3kSfx.FIRE_ATTACK.id);
        map.put(GameSound.LIGHTNING_ATTACK, Sonic3kSfx.ELECTRIC_ATTACK.id);
        map.put(GameSound.BUBBLE_ATTACK, Sonic3kSfx.BUBBLE_ATTACK.id);
        map.put(GameSound.INSTA_SHIELD, Sonic3kSfx.INSTA_SHIELD.id);
        map.put(GameSound.TAILS_FLYING, Sonic3kSfx.FLYING.id);
        map.put(GameSound.TAILS_FLY_TIRED, Sonic3kSfx.FLY_TIRED.id);
        map.put(GameSound.GRAB, Sonic3kSfx.GRAB.id);
        map.put(GameSound.GLIDE_LAND, Sonic3kSfx.GLIDE_LAND.id);
        SOUND_MAP = Collections.unmodifiableMap(map);

        Map<GameMusic, Integer> music = new EnumMap<>(GameMusic.class);
        music.put(GameMusic.ACT_CLEAR, Sonic3kMusic.ACT_CLEAR.id);
        music.put(GameMusic.DROWNING, Sonic3kMusic.DROWNING.id);
        music.put(GameMusic.EMERALD, Sonic3kMusic.EMERALD.id);
        music.put(GameMusic.EXTRA_LIFE, Sonic3kMusic.EXTRA_LIFE.id);
        music.put(GameMusic.INVINCIBILITY, Sonic3kMusic.INVINCIBILITY.id);
        music.put(GameMusic.SPECIAL_STAGE, Sonic3kMusic.SPECIAL_STAGE.id);
        music.put(GameMusic.SUPER, Sonic3kMusic.INVINCIBILITY.id);
        MUSIC_MAP = Collections.unmodifiableMap(music);
    }

    public Sonic3kAudioProfile() {
        this(Sonic3kAudioResourceProfile.LOCKED_ON);
    }

    public Sonic3kAudioProfile(Sonic3kAudioResourceProfile resources) {
        super(SOUND_MAP, musicMap(resources));
        this.resources = java.util.Objects.requireNonNull(resources, "resources");
    }

    public Sonic3kAudioResourceProfile resourceProfile() {
        return resources;
    }

    private static Map<GameMusic, Integer> musicMap(Sonic3kAudioResourceProfile resources) {
        java.util.Objects.requireNonNull(resources, "resources");
        if (resources != Sonic3kAudioResourceProfile.SONIC_3) return MUSIC_MAP;
        Map<GameMusic, Integer> music = new EnumMap<>(GameMusic.class);
        MUSIC_MAP.forEach((cue, nativeId) -> music.put(cue, resources.musicId(nativeId)));
        return Collections.unmodifiableMap(music);
    }

    @Override
    public SmpsLoader createSmpsLoader(Rom rom) {
        return new Sonic3kSmpsLoader(rom, resources);
    }

    @Override
    public SmpsSequencerConfig getSequencerConfig() {
        return Sonic3kSmpsSequencerConfig.CONFIG;
    }

    @Override
    public SmpsPhysicalPolicy smpsPhysicalPolicy() {
        return Sonic3kSmpsPhysicalPolicy.INSTANCE;
    }

    @Override
    public SmpsStatefulCommandPolicy smpsStatefulCommandPolicy() {
        return Sonic3kStatefulCommandPolicy.INSTANCE;
    }

    @Override
    public int getSpeedShoesOnCommandId() {
        return -1;
    }

    @Override
    public int getSpeedShoesOffCommandId() {
        return -1;
    }

    @Override
    public int getInvincibilityMusicId() {
        return resources.musicId(Sonic3kMusic.INVINCIBILITY.id);
    }

    @Override
    public int getExtraLifeMusicId() {
        return resources.musicId(Sonic3kMusic.EXTRA_LIFE.id);
    }

    @Override
    public int getDrowningMusicId() {
        return resources.musicId(Sonic3kMusic.DROWNING.id);
    }

    @Override
    public boolean blocksSfxDuringMusicRestoreFadeIn() {
        return false;
    }

    @Override
    public int getSuperSonicMusicId() {
        return resources.musicId(Sonic3kMusic.INVINCIBILITY.id);
    }

    @Override
    protected int getFadeOutCommandId() {
        return Sonic3kSmpsConstants.CMD_FADE_OUT;
    }

    @Override
    protected int getAlternateFadeOutCommandId() {
        return Sonic3kSmpsConstants.CMD_FADE_OUT_ALT;
    }

    @Override
    protected int getStopAllCommandId() {
        return Sonic3kSmpsConstants.CMD_STOP_ALL;
    }

    @Override
    protected int getStopCommandId() {
        return Sonic3kSmpsConstants.CMD_STOP;
    }

    @Override
    protected int getPsgSilenceCommandId() {
        return Sonic3kSmpsConstants.CMD_PSG_SILENCE;
    }

    @Override
    protected int getStopSfxCommandId() {
        return Sonic3kSmpsConstants.CMD_STOP_SFX;
    }

    @Override
    protected int getSegaCommandId() {
        return Sonic3kSmpsConstants.CMD_SEGA;
    }

    @Override
    protected int getStopSegaCommandId() {
        return Sonic3kSmpsConstants.CMD_STOP_SEGA;
    }

    @Override
    public SegaPcmSpec getSegaPcmSpec() {
        return new SegaPcmSpec(
                Sonic3kSmpsConstants.SEGA_SOUND_ADDR,
                Sonic3kSmpsConstants.SEGA_SOUND_SIZE,
                Sonic3kSmpsConstants.SEGA_SOUND_SAMPLE_RATE);
    }

    /**
     * Reads the chant through the runtime-layer ROM reader so the audio layer
     * never depends on {@code com.openggf.data} for this sample.
     */
    @Override
    public byte[] loadSegaPcm(Object rom) throws java.io.IOException {
        return SegaPcmRomReader.read(rom, getSegaPcmSpec());
    }

    /**
     * {@code zFadeOutMusic} sets {@code zFadeOutTimeout} to 28h and both
     * {@code zFadeDelayTimeout} and {@code zFadeDelay} to 6
     * (Sound/Z80 Sound Driver.asm:2306-2311), so S3K takes 240 frames to
     * silence where S1 and S2 take 120.
     */
    @Override
    public void fadeOutMusic(AudioManager manager) {
        manager.fadeOutMusic(0x28, 6);
    }

    /**
     * {@code Player_ResetAirTimer} (sonic3k.asm:33703-33726) loads
     * {@code Current_music}, then overrides it in three tests taken in order:
     * {@code Status_Invincible} and {@code Super_Sonic_Knux_flag} both select
     * {@code mus_Invincibility} ($2C), and {@code Boss_flag} selects
     * {@code mus_MinibossK} ($18). The boss test comes last, so a boss fight
     * wins over an invincible or Super player.
     */
    @Override
    public int resolveAirResetMusic(int levelMusicId, boolean invincible,
            boolean superForm, boolean bossActive) {
        if (bossActive) {
            return resources.musicId(Sonic3kMusic.MINIBOSS.id);
        }
        if (invincible || superForm) {
            return resources.musicId(Sonic3kMusic.INVINCIBILITY.id);
        }
        return levelMusicId;
    }

    @Override
    public boolean isContinuousSfx(int sfxId) {
        // ROM: sfx__FirstContinuous = 0xBC (sfx_SlideSkidLoud)
        return sfxId >= Sonic3kSfx.SLIDE_SKID_LOUD.id;
    }

    @Override
    public SpeedMode getSpeedMode() {
        return SpeedMode.FRAME_MULTIPLY;
    }

    @Override
    public int getSpeedMultiplierValue() {
        return Sonic3kSmpsConstants.SPEED_MULTIPLIER_ON;
    }
}
