package com.openggf.game.soniccd.ending;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.fmv.CdFmvAssets;
import com.openggf.game.soniccd.fmv.CdFmvStream;
import com.openggf.game.soniccd.fmv.CdFmvStreamer;
import com.openggf.game.soniccd.media.CdProgramAssets;

/** Builds the ending program as its loader does. */
public final class CdEndings {
    private CdEndings() { }

    /**
     * {@code SPCmd_LoadGoodEnd}/{@code SPCmd_LoadBadEnd} (SPX.asm:1418-1468): ENDING.MMD with the sub file that streams
     * the matching STM (the good ending's loader names BADEND.BIN, which streams GOODEND.STM; SPX.asm:239-242,
     * Sub (Ending).asm:25-33).
     */
    public static CdEndingProgram program(CdFmvAssets assets, CdProgramAssets disc, boolean good, CdSubCpuAudio.ProgramPcm pcm) {
        return program(assets, disc, good, pcm, null);
    }

    /** As above, the stream's read paced by {@code timing}'s load-time profile ({@link CdFmvStreamer#seekFor}). */
    public static CdEndingProgram program(CdFmvAssets assets, CdProgramAssets disc, boolean good, CdSubCpuAudio.ProgramPcm pcm,
                                          com.openggf.game.timing.HardwareTimingService timing) {
        CdNativeFile file = good ? CdNativeFile.GOODENDSTM : CdNativeFile.BADENDSTM;
        CdFmvStreamer streamer = new CdFmvStreamer(new CdFmvStream(disc, file),
                CdFmvStream.Layout.SOUND, pcm, assets.from(good ? "pcmRegValuesGoodEnding" : "pcmRegValuesBadEnding", 14),
                CdFmvStreamer.seekFor(timing, com.openggf.game.soniccd.fmv.CdFmvScreen.entry(disc, file)));
        return new CdEndingProgram(assets, streamer, good);
    }
}
