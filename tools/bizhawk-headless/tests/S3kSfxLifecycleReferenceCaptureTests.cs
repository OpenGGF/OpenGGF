using System;
using System.Collections.Generic;
using System.IO;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using OpenGGF.BizHawk.Headless;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S3kSfxLifecycleReferenceCaptureTests
    {
        private const ushort ExplodeStartPointer = 0xF687;
        private const int SfxTrackFirst = 0x1DF0;
        private const int SfxTrackCount = 7;
        private const int TrackSize = 0x30;
        private static readonly ushort[] CollapseStartPointers =
            { 0xE4B6, 0xE4C1, 0xE4BD, 0, 0, 0, 0xE4CB };

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            if (Environment.GetEnvironmentVariable(
                    "OPENGGF_GPGX_S3K_LIFECYCLE_PREFLIGHT") == "1")
            {
                tests.Add(new TestMain.TestCase(
                    "S3kSfxLifecycleReferenceCaptureTests preflight retained gameplay movie",
                    CapturePreflight,
                    game: "s3k", serial: true, estimatedSeconds: 180.0));
            }
        }

        private static void CapturePreflight()
        {
            string rom = RequiredEnvironment("S3K_ROM_PATH");
            string moviePath = RequiredEnvironment("OPENGGF_S3K_LIFECYCLE_MOVIE");
            string output = RequiredEnvironment("OPENGGF_S3K_LIFECYCLE_PREFLIGHT_OUTPUT");
            if (File.Exists(output) || Directory.Exists(output))
                throw new IOException("Preflight output already exists: " + output);

            Bk2Movie movie = Bk2Reader.Read(moviePath);
            var explodeRestarts = new List<int>();
            var collapseFrames = new List<int>();
            var intersectingFrames = new List<int>();
            bool collapseSeen = false;
            bool collapseCompleted = false;
            int firstCollapseFrame = -1;
            int lastCollapseFrame = -1;
            int previousExplodeFrame = -1;
            uint ordinaryEvents = 0;
            int[] priorPointers = new int[SfxTrackCount];

            using (var host = GpgxHost.Open(rom, movie.SyncSettings))
            {
                IGpgxAudioTraceApi trace = host.CreateAudioTraceApi();
                GpgxAudioServiceManifest.Load(Path.Combine(
                    EndToEndTests.ToolDirectory,
                    "fixtures/gpgx-audio-service-manifests-v1.json"),
                    "s3k", trace);
                using (IEnumerator<Bk2Frame> rows =
                    movie.OpenFrameStream().GetEnumerator())
                {
                    for (int frame = 0; frame < movie.FrameCount; frame++)
                    {
                        AssertEx.Equal(true, rows.MoveNext());
                        S1TraceCaptureRunner.ApplyFrame(rows.Current, host);
                        AssertEx.Equal(0, trace.BeginFrame());
                        host.Advance();
                        AssertEx.Equal(0, trace.EndFrame());

                        uint count, overflow, copied;
                        int[] pointers = new int[SfxTrackCount];
                        bool[] active = new bool[SfxTrackCount];
                        for (int i = 0; i < SfxTrackCount; i++)
                        {
                            int track = SfxTrackFirst + i * TrackSize;
                            active[i] = (host.ReadZ80RamByte(track) & 0x80) != 0;
                            pointers[i] = host.ReadZ80RamByte(track + 3)
                                | host.ReadZ80RamByte(track + 4) << 8;
                        }
                        if (active[2] && pointers[2] == ExplodeStartPointer
                            && (priorPointers[2] != ExplodeStartPointer
                                || previousExplodeFrame != frame - 1))
                        {
                            explodeRestarts.Add(frame);
                            previousExplodeFrame = frame;
                        }
                        bool frameStartsCollapse = true;
                        int[] collapseRoles = { 0, 1, 2, 6 };
                        for (int i = 0; i < collapseRoles.Length; i++)
                        {
                            int role = collapseRoles[i];
                            if (!active[role]
                                || pointers[role] != CollapseStartPointers[role])
                                frameStartsCollapse = false;
                        }
                        if (!collapseCompleted && frameStartsCollapse)
                        {
                            collapseSeen = true;
                            if (firstCollapseFrame < 0) firstCollapseFrame = frame;
                        }
                        bool collapsePsgActive = active[6]
                            && pointers[6] >= 0xE4CB && pointers[6] < 0xE4DD;
                        if (collapseSeen && !collapseCompleted && collapsePsgActive)
                        {
                            collapseFrames.Add(frame);
                            lastCollapseFrame = frame;
                            bool changedOtherRole = false;
                            for (int i = 0; i < SfxTrackCount - 1; i++)
                                if (active[i] && pointers[i] != priorPointers[i]
                                    && !IsCollapsePointer(pointers[i]))
                                    changedOtherRole = true;
                            if (changedOtherRole) intersectingFrames.Add(frame);
                        }
                        else if (collapseSeen && !collapsePsgActive)
                        {
                            collapseCompleted = true;
                        }
                        pointers.CopyTo(priorPointers, 0);

                        AssertEx.Equal(0, trace.EventCount(out count, out overflow));
                        AssertEx.Equal(0u, overflow);
                        var ordinary = count == 0 ? null
                            : new GpgxAudioTraceEvent[checked((int)count)];
                        AssertEx.Equal(0, trace.Drain(ordinary, count, out copied));
                        AssertEx.Equal(count, copied);
                        ordinaryEvents += copied;
                    }
                    AssertEx.Equal(false, rows.MoveNext());
                }
                AssertEx.Equal(0, trace.Disable());
            }

            var result = new JObject
            {
                ["schema"] = "openggf.s3k-sfx-lifecycle-preflight.v1",
                ["movie"] = Path.GetFileName(moviePath),
                ["movie_frames"] = movie.FrameCount,
                ["explode_restart_frames"] = new JArray(explodeRestarts),
                ["explode_restart_count"] = explodeRestarts.Count,
                ["collapse_first_frame"] = firstCollapseFrame,
                ["collapse_last_write_frame"] = lastCollapseFrame,
                ["collapse_write_frames"] = new JArray(collapseFrames),
                ["intersecting_sfx_frames"] = new JArray(intersectingFrames),
                ["ordinary_events"] = ordinaryEvents
            };
            File.WriteAllText(output, result.ToString(Formatting.Indented) + "\n");
            if (explodeRestarts.Count < 4)
                throw new InvalidDataException(
                    "Movie does not contain a maximal repeated Explosion window.");
            if (firstCollapseFrame < 0)
                throw new InvalidDataException(
                    "Movie does not contain a Collapse residence.");
            if (intersectingFrames.Count == 0)
                throw new InvalidDataException(
                    "Movie has no later SFX traffic intersecting Collapse.");
        }

        private static string RequiredEnvironment(string name)
        {
            string value = Environment.GetEnvironmentVariable(name);
            if (string.IsNullOrEmpty(value))
                throw new InvalidOperationException(name + " is required.");
            return value;
        }

        private static bool IsCollapsePointer(int pointer)
        {
            return pointer >= 0xE4B6 && pointer < 0xE4DD;
        }
    }
}
