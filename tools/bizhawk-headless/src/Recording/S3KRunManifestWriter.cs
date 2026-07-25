using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// S3K front-end over the shared <see cref="RunManifestWriter"/>
    /// (tools/bizhawk/s3k_complete_run_recorder.lua write_run_manifest
    /// L1458; spec tools/bizhawk-headless/docs/s3k-run-publication.md §4).
    /// The structural layout is character-identical to the S1 and S2
    /// manifests, so only the literals live here: the game id, the inline
    /// rom_checksum and lua_script_version, and Lua %q quoting for
    /// source_bk2 (the S1 convention; S2 uses json_escape). The one
    /// genuinely new thing — the bonus_stage kind's bonus_stage_type extra
    /// — was added to the shared writer as an additive branch rather than
    /// forked here.
    ///
    /// Two S3K deltas versus S2's front-end:
    ///
    /// - run_id is OPTIONAL. The S2 recorder only has a run mode, so its
    ///   manifest always carries the line; the S3K complete-run recorder
    ///   emits a manifest whenever a detour occurred OR --run-id was given,
    ///   so a detour-ful capture with no run id writes a manifest with no
    ///   run_id line at all.
    /// - game is the literal "s3k", never "sonic3k" — which
    ///   TraceExecutionModel.forGame rejects.
    ///
    /// Emission is CONDITIONAL and is the caller's decision (see
    /// <see cref="ShouldEmit"/>): identity (A), a detour-free complete-run
    /// pass with no run id, correctly publishes no manifest at all.
    /// </summary>
    public static class S3KRunManifestWriter
    {
        public const string Game = "s3k";

        /// <summary>
        /// The Lua's emission gate (L1459):
        /// <c>if #transitions_done == 0 and run_id == nil then return end</c>.
        /// Note the disjunction — a detour-free capture WITH --run-id still
        /// writes a manifest whose transitions array is empty, and a
        /// detour-ful capture WITHOUT one writes a manifest with no run_id
        /// line.
        /// </summary>
        public static bool ShouldEmit(
            IList<RunManifestTransition> transitions, string runId)
        {
            if (transitions == null)
            {
                throw new ArgumentNullException("transitions");
            }
            return transitions.Count > 0 || runId != null;
        }

        /// <summary>
        /// Formats run_manifest.json, including the trailing newline. Call
        /// only when <see cref="ShouldEmit"/> is true.
        /// </summary>
        public static string Format(
            string runId,
            string sourceBk2,
            IList<RunManifestSegment> segments,
            IList<RunManifestTransition> transitions)
        {
            return RunManifestWriter.Format(
                Game,
                runId,
                sourceBk2,
                true,   // string.format('  "source_bk2": %q,\n', ...)
                S3KCompleteRunMetadataWriter.RomChecksum,
                S3KCompleteRunMetadataWriter.LuaScriptVersion,
                segments,
                transitions);
        }
    }
}
