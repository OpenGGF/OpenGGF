package com.openggf.game.soniccd.audio;

import com.openggf.audio.AbstractAudioProfile;
import com.openggf.audio.GameMusic;
import com.openggf.audio.GameSound;
import com.openggf.audio.RomlessSmpsSource;
import com.openggf.audio.presentation.AudioRequestService;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdStagePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Sonic CD's audio on the shared owners.
 *
 * <ul>
 *   <li>FM effects play through the shared SMPS sequencer and YM2612 synthesis from the driver
 *       bank on the disc ({@link CdFmSmpsLoader}), configured by {@link CdFmSequencerConfig}
 *       and {@link CdFmCoordFlagHandler}, admitted by {@link CdFmSoundRequestService}.</li>
 *   <li>Music and voice requests are sub-CPU commands ({@link CdSubCpuAudio}): disc audio
 *       tracks through {@link CdCddaPlayer}, mixed into the shared producer.</li>
 * </ul>
 */
public final class SonicCdAudioProfile extends AbstractAudioProfile implements RomlessSmpsSource {
    public static final String PRESENTATION_GAME_ID = "soniccd";
    /** {@code SCMD_INVINCMUS} (_Include/System.inc:253); command values come from subcpu-audio-commands.tsv. */
    public static final int INVINCIBILITY_COMMAND = 0x6D;

    private static final Map<GameSound, Integer> SOUND_MAP;
    private static final Map<GameMusic, Integer> MUSIC_MAP;
    private static final int[][] STAGE_MUSIC = loadStageMusic();

    static {
        // IDs from FM/_Sound IDs.inc:9-89 at the stage call sites cited.
        Map<GameSound, Integer> map = new EnumMap<>(GameSound.class);
        map.put(GameSound.JUMP, 0x92);              // FM_JUMP, Level/_Objects/Sonic/Main.asm:1835
        map.put(GameSound.RING, 0x95);              // FM_RING, Level/_Objects/Ring/Main.asm:293
        map.put(GameSound.RING_RIGHT, 0x95);        // the driver's own swap picks the side
        map.put(GameSound.RING_LEFT, 0xA8);         // FM_RINGL
        map.put(GameSound.RING_SPILL, 0x94);        // FM_RINGLOSS, Level/_Objects/Ring/Main.asm:404
        map.put(GameSound.SKID, 0x90);              // FM_SKID, Level/_Objects/Sonic/Main.asm:1281
        map.put(GameSound.HURT, 0x93);              // FM_HURT, Palmtree Panic/Player Object Collision.asm:293
        map.put(GameSound.HURT_SPIKE, 0x93);        // spikes hurt through the same routine
        map.put(GameSound.BADNIK_HIT, 0x96);        // FM_DESTROY, Palmtree Panic/Player Object Collision.asm:174
        map.put(GameSound.SPRING, 0x98);            // FM_SPRING, Level/_Objects/Spring/Main.asm:275
        map.put(GameSound.CHECKPOINT, 0xAE);        // FM_CHECKPOINT, Level/_Objects/Checkpoint/Main.asm:139
        map.put(GameSound.ROLLING, 0x9C);           // FM_9C "roll", Level/_Objects/Sonic/Main.asm:2598
        map.put(GameSound.SPINDASH_CHARGE, 0x9C);   // FM_9C "charge", Level/_Objects/Sonic/Main.asm:1023
        map.put(GameSound.SPINDASH_RELEASE, 0x91);  // FM_91 "charge release", Level/_Objects/Sonic/Main.asm:1041
        map.put(GameSound.CHARGE_STOP, 0xAB);       // FM_CHARGESTOP, Level/_Objects/Sonic/Main.asm:908, :1033, :1413
        SOUND_MAP = Collections.unmodifiableMap(map);
        Map<GameMusic, Integer> music = new EnumMap<>(GameMusic.class);
        music.put(GameMusic.INVINCIBILITY, INVINCIBILITY_COMMAND);
        MUSIC_MAP = Collections.unmodifiableMap(music);
    }

    private final Supplier<CdDiscDataSource> disc;
    private final CdSubCpuAudio subCpu;
    private final CdFmDriverState fmDriver = new CdFmDriverState();
    private CdDiscDataSource assetsDisc;
    private CdFmSoundAssets assets;

    public SonicCdAudioProfile(Supplier<CdDiscDataSource> disc) {
        super(SOUND_MAP, MUSIC_MAP);
        this.disc = Objects.requireNonNull(disc, "disc");
        this.subCpu = new CdSubCpuAudio(this::trackSource, this::readDiscFile);
    }

    public static Map<GameSound, Integer> soundMap() {
        return SOUND_MAP;
    }

    public CdSubCpuAudio subCpuAudio() {
        return subCpu;
    }

    /**
     * {@code PlayLevelMusic}: {@code MusicPlayCmds[zone * 4 + timeZone + goodFuture]}
     * (Level/Main.asm:555-583), with the payload's period standing for the time zone
     * (past 0, present 1, future 2, plus 1 for a good future).
     */
    public static int stageMusicCommand(CdStagePayload payload) {
        int timeZone = switch (payload.stage().period()) {
            case PAST -> 0;
            case PRESENT -> 1;
            case BAD_FUTURE -> 2;
            case GOOD_FUTURE -> 3;
        };
        return STAGE_MUSIC[payload.stage().zone().ordinal()][timeZone];
    }

    @Override
    public SmpsLoader createRomlessSmpsLoader() {
        CdFmSoundAssets bank = assets();
        return bank == null ? null : new CdFmSmpsLoader(bank);
    }

    @Override
    public AudioRequestService createAudioRequestService() {
        return new CdFmSoundRequestService(id -> {
            CdFmSoundAssets bank = assets();
            if (bank == null) throw new IllegalStateException("FM priorities need the loaded disc");
            return bank.nativePriorityByte(id);
        }, subCpu, fmDriver);
    }

    @Override
    public String presentationGameId() {
        return PRESENTATION_GAME_ID;
    }

    @Override
    public void configurePresentationCoordFlagHandlers(SmpsCoordFlagHandlerOwner owner) {
        owner.register(PRESENTATION_GAME_ID, ignored -> new CdFmCoordFlagHandler(fmDriver));
    }

    @Override
    public SmpsSequencerConfig getSequencerConfig() {
        return CdFmSequencerConfig.standalone();
    }

    /**
     * Every admitted effect replaces the tracks on its channels: {@code PlaySFX} copies the new
     * header over the slot without a priority test (FM/_Driver.asm:803-856). Bit 7 makes the
     * shared per-channel lock treat each effect as replaceable; the native whole-request
     * priority runs in {@link CdFmSoundRequestService}.
     */
    @Override
    public int getSfxPriority(int soundId) {
        return 0x80;
    }

    /**
     * The engine's music fade is Sonic CD's {@code SCMD_FADECDA} ({@code FadeOutMusic},
     * Level/Sub CPU.asm:11-12): the CD track fades; FM effects have no music to fade.
     */
    @Override
    public void fadeOutMusic(com.openggf.audio.AudioManager manager) {
        subCpu.submit(CdSubCpuAudio.commandId("SCMD_FADECDA"));
    }

    @Override public int getSpeedShoesOnCommandId() { return -1; }
    @Override public int getSpeedShoesOffCommandId() { return -1; }
    @Override public int getInvincibilityMusicId() { return INVINCIBILITY_COMMAND; }
    @Override public int getExtraLifeMusicId() { return -1; }
    @Override public int getDrowningMusicId() { return -1; }

    private synchronized CdFmSoundAssets assets() {
        CdDiscDataSource current = disc.get();
        if (current == null) return null;
        if (current != assetsDisc) {
            try {
                assets = CdFmSoundAssets.fromPrograms(current.assets());
            } catch (IOException e) {
                throw new UncheckedIOException("Sonic CD FM bank could not be read", e);
            }
            assetsDisc = current;
        }
        return assets;
    }

    private byte[] readDiscFile(com.openggf.game.soniccd.CdNativeFile file) {
        CdDiscDataSource current = disc.get();
        if (current == null) throw new IllegalStateException("PCM bank " + file + " needs the loaded disc");
        try {
            return current.readFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Sonic CD PCM bank could not be read", e);
        }
    }

    private CdCddaPlayer.TrackSource trackSource() {
        CdDiscDataSource current = disc.get();
        if (current == null) return null;
        var programs = current.assets();
        return new CdCddaPlayer.TrackSource() {
            @Override public long programFrames(int track) { return programs.audioTrackFrames(track); }
            @Override public byte[] readFrames(int track, long firstFrame, int count) throws IOException {
                return programs.readAudioFrames(track, firstFrame, count);
            }
        };
    }

    private static int[][] loadStageMusic() {
        int[][] table = new int[7][4];
        try (InputStream in = SonicCdAudioProfile.class.getResourceAsStream("/soniccd/stage-music.tsv")) {
            if (in == null) throw new IllegalStateException("stage-music.tsv is missing");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                table[Integer.parseInt(f[0])][Integer.parseInt(f[1])] = Integer.parseInt(f[2], 16);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return table;
    }
}
