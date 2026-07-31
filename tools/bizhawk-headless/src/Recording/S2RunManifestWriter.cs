using System;
using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S2 Lua run-mode run_manifest.json emitter
    /// (tools/bizhawk/s2_trace_recorder.lua v9.13-s2, write_run_manifest;
    /// spec s2-run-mode-behavior.md §6, §11). Written exactly once at
    /// run termination to the run root. rom_checksum is the inline literal
    /// "7B905383" (S2 World REV01 CRC32) and lua_script_version the
    /// "9.13-s2" constant — neither is computed at runtime. String fields
    /// that the Lua renders with %q (run_id, dir, kind, trace_profile,
    /// entry_kind) go through the %q-faithful quoting in the shared
    /// <see cref="RunManifestWriter"/> core; source_bk2 goes through the
    /// shared json_escape helper instead. S2 run mode only exists when
    /// OGGF_TRACE_RUN_ID is set, so run_id is mandatory here (the S1
    /// front-end is the one with an optional run_id line).
    /// </summary>
    public static class S2RunManifestWriter
    {
        public static string Format(
            string runId,
            string sourceBk2,
            IList<RunManifestSegment> segments,
            IList<RunManifestTransition> transitions,
            IList<DynamicArtGapTransition> dynamicArtGapTransitions = null)
        {
            if (runId == null)
            {
                throw new ArgumentNullException("runId");
            }
            return RunManifestWriter.Format(
                "s2",
                runId,
                sourceBk2,
                false,
                "7B905383",
                "9.13-s2",
                null,
                segments,
                transitions,
                dynamicArtGapTransitions);
        }
    }
}
