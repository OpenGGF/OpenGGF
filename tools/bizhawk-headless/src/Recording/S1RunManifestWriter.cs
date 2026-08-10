using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S1 complete-run recorder's run_manifest.json
    /// emitter (tools/bizhawk/s1_complete_run_recorder.lua
    /// write_run_manifest L785-849; spec s1-run-mode-behavior.md §6),
    /// delegating the structural layout to the shared
    /// <see cref="RunManifestWriter"/> core. S1 deltas versus S2: the game
    /// literal is "s1", rom_checksum is the inline "AFE05EEE" literal (S1
    /// World REV01 CRC32 — not computed at runtime), source_bk2 is rendered
    /// with Lua %q rather than json_escape, and the run_id line is emitted
    /// only when OGGF_TRACE_RUN_ID was set (the S1 detour machine is always
    /// on, so a manifest can exist without a run id whenever a detour
    /// occurred — pass null to omit the line). expected_movie_end_mode is
    /// likewise nullable: true movie completion records an authoritative
    /// mapped endpoint, while hard-stop and unknown endpoints omit it.
    /// </summary>
    public static class S1RunManifestWriter
    {
        public const string RomChecksum = "AFE05EEE";

        public static string Format(
            string runId,
            string sourceBk2,
            string expectedMovieEndMode,
            IList<RunManifestSegment> segments,
            IList<RunManifestTransition> transitions,
            IList<DynamicArtGapTransition> dynamicArtGapTransitions = null)
        {
            return RunManifestWriter.Format(
                "s1",
                runId,
                sourceBk2,
                true,
                RomChecksum,
                expectedMovieEndMode,
                segments,
                transitions,
                dynamicArtGapTransitions);
        }
    }
}
