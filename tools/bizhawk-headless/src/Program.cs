using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;
using OpenGGF.BizHawk.Headless;

namespace BizHawk.Headless.Gpgx
{
    internal enum CaptureMode
    {
        Smoke,
        Trace
    }

    internal sealed class CommandLineOptions
    {
        /// <summary>
        /// Trace-mode output file names, in staging/publication order. The
        /// writer array passed to the trace capture uses the same order.
        /// </summary>
        internal static readonly string[] TraceOutputFileNames =
        {
            "physics.csv",
            "aux_state.jsonl",
            "metadata.json"
        };

        internal const string RunManifestFileName = "run_manifest.json";

        private CommandLineOptions(
            CaptureMode mode,
            string romPath,
            string moviePath,
            string outputDirectory,
            int bk2FrameOffset,
            int maxFrames,
            string traceProfile,
            int? gameplaySegment,
            string runId,
            int effectiveMovieLength)
        {
            Mode = mode;
            RomPath = romPath;
            MoviePath = moviePath;
            OutputDirectory = outputDirectory;
            Bk2FrameOffset = bk2FrameOffset;
            MaxFrames = maxFrames;
            TraceProfile = traceProfile;
            GameplaySegment = gameplaySegment;
            RunId = runId;
            EffectiveMovieLength = effectiveMovieLength;
        }

        public CaptureMode Mode { get; private set; }
        public string RomPath { get; private set; }
        public string MoviePath { get; private set; }
        public string OutputDirectory { get; private set; }
        public int Bk2FrameOffset { get; private set; }
        public int MaxFrames { get; private set; }

        /// <summary>
        /// Recorder-selection trace-mode arguments, mirroring the Lua
        /// recorders' env inputs (null = argument not supplied). With the
        /// Sonic 2 ROM: --trace-profile (OGGF_S2_TRACE_PROFILE, default
        /// "gameplay_unlock"), --gameplay-segment
        /// (OGGF_TRACE_GAMEPLAY_SEGMENT, default 0), and --run-id
        /// (OGGF_TRACE_RUN_ID; absent = plain mode). With the Sonic 1 ROM:
        /// --trace-profile "complete_run" or --run-id select the
        /// complete-run recorder (s1_complete_run_recorder.lua, whose
        /// detour machine is always on; --run-id maps to OGGF_TRACE_RUN_ID)
        /// while --gameplay-segment stays S2-only. --run-id is mutually
        /// exclusive with the other two in both games: the Lua run capture
        /// procedures never set the profile/segment env vars.
        /// </summary>
        public string TraceProfile { get; private set; }
        public int? GameplaySegment { get; private set; }
        public string RunId { get; private set; }

        /// <summary>
        /// --effective-movie-length (run mode only; 0 = absent, use the
        /// movie's own frame count): the capture session's movie-length
        /// signal for the run runner's movie-done guard. The canonical
        /// halfpipe fixture's final level segment was terminated by a
        /// session signal shorter than the committed BK2's row count
        /// (s2-run-mode-behavior.md §2 capture-time caveat), so
        /// byte-identical reproduction must inject that session value.
        /// </summary>
        public int EffectiveMovieLength { get; private set; }

        public static CommandLineOptions Parse(string[] args)
        {
            if (args == null)
            {
                throw new ArgumentNullException("args");
            }

            var values = new Dictionary<string, string>(
                StringComparer.Ordinal);
            for (var index = 0; index < args.Length; index += 2)
            {
                string name = args[index];
                if (!IsSupportedArgument(name))
                {
                    throw new ArgumentException(
                        "Unknown argument: " + name + ".");
                }
                if (index + 1 >= args.Length)
                {
                    throw new ArgumentException(
                        "Argument " + name + " requires a value.");
                }
                if (values.ContainsKey(name))
                {
                    throw new ArgumentException(
                        "Duplicate argument: " + name + ".");
                }
                if (args[index + 1].Length == 0)
                {
                    throw new ArgumentException(
                        "Argument " + name + " requires a value.");
                }
                values.Add(name, args[index + 1]);
            }

            string romPath = Required(values, "--rom");
            string moviePath = Required(values, "--movie");
            string outputDirectory = Required(values, "--output");
            CaptureMode mode = ParseMode(values);
            if (mode == CaptureMode.Trace)
            {
                return ParseTrace(
                    values,
                    romPath,
                    moviePath,
                    outputDirectory);
            }

            RejectInSmokeMode(values, "--trace-profile");
            RejectInSmokeMode(values, "--gameplay-segment");
            RejectInSmokeMode(values, "--run-id");
            RejectInSmokeMode(values, "--effective-movie-length");
            int offset = ParseInteger(
                values,
                "--bk2-frame-offset",
                0);
            if (offset < 0)
            {
                throw new ArgumentOutOfRangeException(
                    "--bk2-frame-offset",
                    "Argument --bk2-frame-offset must be at least 0.");
            }
            int maxFrames = ParseInteger(
                values,
                "--max-frames",
                1000);
            if (maxFrames < 1 || maxFrames > 1000)
            {
                throw new ArgumentOutOfRangeException(
                    "--max-frames",
                    "Argument --max-frames must be between 1 and 1000.");
            }

            string fullOutputDirectory =
                Path.GetFullPath(outputDirectory);
            string finalPath = Path.Combine(
                fullOutputDirectory,
                "smoke.csv");
            if (LinuxPathEntry.Exists(finalPath))
            {
                throw new IOException(
                    "Final output already exists and will not be replaced: "
                    + finalPath);
            }

            return new CommandLineOptions(
                CaptureMode.Smoke,
                Path.GetFullPath(romPath),
                Path.GetFullPath(moviePath),
                fullOutputDirectory,
                offset,
                maxFrames,
                null,
                null,
                null,
                0);
        }

        private static CommandLineOptions ParseTrace(
            IDictionary<string, string> values,
            string romPath,
            string moviePath,
            string outputDirectory)
        {
            RejectInTraceMode(values, "--bk2-frame-offset");
            RejectInTraceMode(values, "--max-frames");

            string traceProfile;
            values.TryGetValue("--trace-profile", out traceProfile);
            string runId;
            values.TryGetValue("--run-id", out runId);
            int? gameplaySegment = null;
            if (values.ContainsKey("--gameplay-segment"))
            {
                int parsed = ParseInteger(values, "--gameplay-segment", 0);
                if (parsed < 0)
                {
                    throw new ArgumentOutOfRangeException(
                        "--gameplay-segment",
                        "Argument --gameplay-segment must be at least 0.");
                }
                gameplaySegment = parsed;
            }
            if (runId != null && traceProfile != null)
            {
                throw new ArgumentException(
                    "Argument --run-id cannot be combined with"
                    + " --trace-profile: run mode always records"
                    + " gameplay_unlock level segments.");
            }
            if (runId != null && gameplaySegment.HasValue)
            {
                throw new ArgumentException(
                    "Argument --run-id cannot be combined with"
                    + " --gameplay-segment: run mode records every"
                    + " gameplay segment of the movie.");
            }
            var effectiveMovieLength = 0;
            if (values.ContainsKey("--effective-movie-length"))
            {
                if (runId == null)
                {
                    throw new ArgumentException(
                        "Argument --effective-movie-length requires"
                        + " --run-id: only the run runner's movie-done"
                        + " guard consumes the session movie-length"
                        + " signal.");
                }
                effectiveMovieLength = ParseInteger(
                    values, "--effective-movie-length", 0);
                if (effectiveMovieLength < 1)
                {
                    throw new ArgumentOutOfRangeException(
                        "--effective-movie-length",
                        "Argument --effective-movie-length must be at"
                        + " least 1.");
                }
            }

            string fullOutputDirectory =
                Path.GetFullPath(outputDirectory);
            if (runId != null)
            {
                // Run mode writes run_manifest.json at the output root and
                // per-segment subdirectories discovered during capture; the
                // manifest is the only final path known up front. Segment
                // files keep the same no-replace guarantee at publish time.
                string manifestPath = Path.Combine(
                    fullOutputDirectory,
                    RunManifestFileName);
                if (LinuxPathEntry.Exists(manifestPath))
                {
                    throw new IOException(
                        "Final output already exists and will not be"
                        + " replaced: " + manifestPath);
                }
            }
            else
            {
                foreach (string fileName in TraceOutputFileNames)
                {
                    string finalPath = Path.Combine(
                        fullOutputDirectory,
                        fileName);
                    if (LinuxPathEntry.Exists(finalPath))
                    {
                        throw new IOException(
                            "Final output already exists and will not be"
                            + " replaced: " + finalPath);
                    }
                }
            }

            return new CommandLineOptions(
                CaptureMode.Trace,
                Path.GetFullPath(romPath),
                Path.GetFullPath(moviePath),
                fullOutputDirectory,
                0,
                0,
                traceProfile,
                gameplaySegment,
                runId,
                effectiveMovieLength);
        }

        private static CaptureMode ParseMode(
            IDictionary<string, string> values)
        {
            string value;
            if (!values.TryGetValue("--mode", out value))
            {
                return CaptureMode.Smoke;
            }
            if (value == "smoke")
            {
                return CaptureMode.Smoke;
            }
            if (value == "trace")
            {
                return CaptureMode.Trace;
            }
            throw new ArgumentException(
                "Argument --mode must be \"smoke\" or \"trace\".");
        }

        private static void RejectInTraceMode(
            IDictionary<string, string> values,
            string name)
        {
            if (values.ContainsKey(name))
            {
                throw new ArgumentException(
                    "Argument " + name + " is not supported in trace mode.");
            }
        }

        private static void RejectInSmokeMode(
            IDictionary<string, string> values,
            string name)
        {
            if (values.ContainsKey(name))
            {
                throw new ArgumentException(
                    "Argument " + name + " is only supported in trace mode.");
            }
        }

        private static bool IsSupportedArgument(string name)
        {
            return name == "--rom"
                || name == "--movie"
                || name == "--output"
                || name == "--mode"
                || name == "--bk2-frame-offset"
                || name == "--max-frames"
                || name == "--trace-profile"
                || name == "--gameplay-segment"
                || name == "--run-id"
                || name == "--effective-movie-length";
        }

        private static string Required(
            IDictionary<string, string> values,
            string name)
        {
            string value;
            if (!values.TryGetValue(name, out value))
            {
                throw new ArgumentException(
                    "Required argument is missing: " + name + ".");
            }
            return value;
        }

        private static int ParseInteger(
            IDictionary<string, string> values,
            string name,
            int defaultValue)
        {
            string value;
            if (!values.TryGetValue(name, out value))
            {
                return defaultValue;
            }

            int parsed;
            if (!int.TryParse(
                value,
                NumberStyles.Integer,
                CultureInfo.InvariantCulture,
                out parsed))
            {
                throw new ArgumentException(
                    "Argument " + name + " must be an integer.");
            }
            return parsed;
        }
    }

    public static class Program
    {
        public static int Main(string[] args)
        {
            return Run(args, Console.Out, Console.Error);
        }

        internal static int Run(
            string[] args,
            TextWriter stdout,
            TextWriter stderr)
        {
            return Run(
                args,
                stdout,
                stderr,
                (romPath, syncSettings) =>
                    GpgxHost.Open(romPath, syncSettings));
        }

        internal static int Run(
            string[] args,
            TextWriter stdout,
            TextWriter stderr,
            Func<string, GPGX.GPGXSyncSettings, IGpgxHost> openHost)
        {
            try
            {
                CommandLineOptions options =
                    CommandLineOptions.Parse(args);
                BizHawkInstallation installation =
                    BizHawkInstallation.Validate(
                        Environment.GetEnvironmentVariable("BIZHAWK_HOME"));
                if (!File.Exists(options.RomPath))
                {
                    throw new FileNotFoundException(
                        "ROM file does not exist.",
                        options.RomPath);
                }
                byte[] romBytes = File.ReadAllBytes(options.RomPath);
                string romSha1;
                string traceGame = null;
                if (options.Mode == CaptureMode.Trace)
                {
                    // Trace mode auto-detects the recorder pipeline from
                    // the supplied ROM (s1 or s2); smoke mode below keeps
                    // its original S1-only validation and messages.
                    traceGame = RomIdentity.DetectGame(romBytes);
                    romSha1 = RomIdentity.ComputeSha1(romBytes);
                }
                else
                {
                    romSha1 = RomIdentity.ValidateSonic1Rev01(romBytes);
                }
                if (!File.Exists(options.MoviePath))
                {
                    throw new FileNotFoundException(
                        "BK2 movie does not exist.",
                        options.MoviePath);
                }
                Bk2Movie movie = Bk2Reader.Read(options.MoviePath);
                if (options.Mode == CaptureMode.Trace)
                {
                    if (traceGame == "s1")
                    {
                        if (options.GameplaySegment.HasValue)
                        {
                            throw new ArgumentException(
                                "Argument --gameplay-segment is only"
                                + " supported with the Sonic 2 ROM.");
                        }
                        if (options.TraceProfile != null
                            && options.TraceProfile
                                != S1RunCaptureRunner.LevelTraceProfile)
                        {
                            throw new ArgumentException(
                                "Trace profile \"" + options.TraceProfile
                                + "\" is only supported with the Sonic 2"
                                + " ROM; the Sonic 1 ROM supports only"
                                + " --trace-profile \""
                                + S1RunCaptureRunner.LevelTraceProfile
                                + "\".");
                        }
                        if (options.TraceProfile != null
                            || options.RunId != null)
                        {
                            return RunS1CompleteRun(
                                options,
                                installation,
                                romSha1,
                                movie,
                                stdout,
                                stderr,
                                openHost);
                        }
                        return RunTrace(
                            options,
                            installation,
                            romSha1,
                            movie,
                            stdout,
                            stderr,
                            openHost);
                    }
                    if (options.RunId != null)
                    {
                        return RunS2TraceRun(
                            options,
                            installation,
                            romSha1,
                            movie,
                            stdout,
                            stderr,
                            openHost);
                    }
                    return RunS2Trace(
                        options,
                        installation,
                        romSha1,
                        movie,
                        stdout,
                        stderr,
                        openHost);
                }

                long requiredFrames =
                    (long)options.Bk2FrameOffset + options.MaxFrames;
                if (requiredFrames > movie.FrameCount)
                {
                    throw new InvalidDataException(
                        "Movie contains only " + movie.FrameCount
                        + " frames; capture requires "
                        + requiredFrames + ".");
                }

                string finalPath = Path.GetFullPath(Path.Combine(
                    options.OutputDirectory,
                    "smoke.csv"));
                return RunCapture(
                    options.OutputDirectory,
                    stdout,
                    stderr,
                    () => new NativeStandardOutputSilencer(),
                    () => openHost(
                        options.RomPath,
                        movie.SyncSettings),
                    (host, writer) => SmokeCaptureRunner.Capture(
                        movie,
                        host,
                        options.Bk2FrameOffset,
                        options.MaxFrames,
                        writer),
                    completedFrames =>
                        "BizHawk: " + installation.ManagedVersion + "\n"
                        + "ROM SHA-1: " + romSha1 + "\n"
                        + "Movie frames: "
                        + movie.FrameCount.ToString(
                            CultureInfo.InvariantCulture)
                        + "\n"
                        + "Requested trace frames: "
                        + options.MaxFrames.ToString(
                            CultureInfo.InvariantCulture)
                        + "\n"
                        + "Completed GPGX frames: "
                        + completedFrames.ToString(
                            CultureInfo.InvariantCulture)
                        + "\n"
                        + "Output: " + finalPath + "\n",
                    new NoReplacePublisher());
            }
            catch (Exception exception)
            {
                ReportFailure(stderr, exception);
                return 1;
            }
        }

        private static int RunTrace(
            CommandLineOptions options,
            BizHawkInstallation installation,
            string romSha1,
            Bk2Movie movie,
            TextWriter stdout,
            TextWriter stderr,
            Func<string, GPGX.GPGXSyncSettings, IGpgxHost> openHost)
        {
            string physicsPath = Path.Combine(
                options.OutputDirectory,
                CommandLineOptions.TraceOutputFileNames[0]);
            string auxStatePath = Path.Combine(
                options.OutputDirectory,
                CommandLineOptions.TraceOutputFileNames[1]);
            string metadataPath = Path.Combine(
                options.OutputDirectory,
                CommandLineOptions.TraceOutputFileNames[2]);
            return RunTraceCapture(
                options.OutputDirectory,
                stdout,
                stderr,
                () => new NativeStandardOutputSilencer(),
                () => openHost(
                    options.RomPath,
                    movie.SyncSettings),
                (host, writers) => S1TraceCaptureRunner.Capture(
                    movie,
                    host,
                    DateTime.Now.ToString(
                        "yyyy-MM-dd",
                        CultureInfo.InvariantCulture),
                    writers[0],
                    writers[1],
                    writers[2]),
                result =>
                    "BizHawk: " + installation.ManagedVersion + "\n"
                    + "ROM SHA-1: " + romSha1 + "\n"
                    + "Movie frames: "
                    + movie.FrameCount.ToString(
                        CultureInfo.InvariantCulture)
                    + "\n"
                    + "BK2 frame offset: "
                    + result.Bk2FrameOffset.ToString(
                        CultureInfo.InvariantCulture)
                    + "\n"
                    + "Trace frames: "
                    + result.TraceFrameCount.ToString(
                        CultureInfo.InvariantCulture)
                    + "\n"
                    + "Physics CSV: " + physicsPath + "\n"
                    + "Aux state JSONL: " + auxStatePath + "\n"
                    + "Metadata JSON: " + metadataPath + "\n",
                new NoReplacePublisher());
        }

        /// <summary>
        /// S1 complete-run capture (--trace-profile complete_run or
        /// --run-id): one movie pass through S1RunCaptureRunner — the full
        /// recorder whose giant-ring detour machine is always on, exactly
        /// like the Lua — publishing every discovered segment directory
        /// plus (when emitted) run_manifest.json as one all-or-nothing
        /// no-replace set. Segments stage incrementally as the capture
        /// streams them, so a 19-segment pass never buffers more than one
        /// segment's contents; the manifest stages last, so it can never
        /// exist without its segment files. run_manifest.json is emitted
        /// iff any special-stage transition occurred OR --run-id was
        /// supplied (Lua gate) — a stage-free pass without --run-id
        /// publishes exactly the per-level directories.
        ///
        /// Line-ending policy follows the canonical fixture set each
        /// invocation reproduces (per-fixture-set, see
        /// docs/s1-complete-run-behavior.md section 8 and
        /// docs/s1-run-mode-behavior.md section 9): --run-id captures get
        /// the Windows text-mode CRLF expansion of the canonical
        /// runs/s1-ghz-maze-roundtrip capture; --trace-profile complete_run
        /// captures stay LF like the 19 *_completerun fixtures. The
        /// session's version stamp is the current Lua's
        /// (S1CompleteRunMetadataWriter.LuaScriptVersion); the Lua's
        /// OGGF_TRACE_SOURCE_BK2 env input is derived from the movie file
        /// itself.
        /// </summary>
        private static int RunS1CompleteRun(
            CommandLineOptions options,
            BizHawkInstallation installation,
            string romSha1,
            Bk2Movie movie,
            TextWriter stdout,
            TextWriter stderr,
            Func<string, GPGX.GPGXSyncSettings, IGpgxHost> openHost)
        {
            bool expandNewlines = options.RunId != null;
            NoReplacePublisher.IncrementalStagingSession session = null;
            NoReplacePublisher.StagedPublicationSet staged = null;
            try
            {
                string manifestPath = Path.Combine(
                    options.OutputDirectory,
                    CommandLineOptions.RunManifestFileName);
                if (options.RunId == null
                    && LinuxPathEntry.Exists(manifestPath))
                {
                    // Parse() preflights the manifest for --run-id; the
                    // complete_run profile reaches here without that check,
                    // yet may still emit the manifest when the movie takes
                    // a giant-ring detour — and even a manifest-free fresh
                    // capture next to a stale manifest would corrupt the
                    // run layout the stale manifest describes.
                    throw new IOException(
                        "Final output already exists and will not be"
                        + " replaced: " + manifestPath);
                }

                S1RunCaptureResult result;
                session = new NoReplacePublisher().OpenSession(
                    options.OutputDirectory);
                NoReplacePublisher.IncrementalStagingSession sink = session;
                using (new NativeStandardOutputSilencer())
                using (IGpgxHost host = openHost(
                    options.RomPath,
                    movie.SyncSettings))
                {
                    result = S1RunCaptureRunner.Capture(
                        movie,
                        host,
                        options.RunId,
                        Path.GetFileName(options.MoviePath),
                        DateTime.Now.ToString(
                            "yyyy-MM-dd",
                            CultureInfo.InvariantCulture),
                        S1CompleteRunMetadataWriter.LuaScriptVersion,
                        0,
                        segment =>
                        {
                            sink.StageFile(
                                segment.DirToken + "/"
                                + CommandLineOptions.TraceOutputFileNames[0],
                                ExpandNewlinesIf(
                                    expandNewlines,
                                    segment.PhysicsCsv));
                            sink.StageFile(
                                segment.DirToken + "/"
                                + CommandLineOptions.TraceOutputFileNames[1],
                                ExpandNewlinesIf(
                                    expandNewlines,
                                    segment.AuxStateJsonl));
                            sink.StageFile(
                                segment.DirToken + "/"
                                + CommandLineOptions.TraceOutputFileNames[2],
                                ExpandNewlinesIf(
                                    expandNewlines,
                                    segment.MetadataJson));
                        });
                }
                if (result.RunManifestJson != null)
                {
                    session.StageFile(
                        CommandLineOptions.RunManifestFileName,
                        ExpandNewlinesIf(
                            expandNewlines,
                            result.RunManifestJson));
                }
                staged = session.Complete();
                session = null;

                var summary = new StringBuilder();
                summary.Append("BizHawk: ")
                    .Append(installation.ManagedVersion).Append('\n');
                summary.Append("ROM SHA-1: ").Append(romSha1).Append('\n');
                summary.Append("Movie frames: ")
                    .Append(movie.FrameCount.ToString(
                        CultureInfo.InvariantCulture))
                    .Append('\n');
                if (options.RunId != null)
                {
                    summary.Append("Run ID: ").Append(options.RunId)
                        .Append('\n');
                }
                else
                {
                    summary.Append("Trace profile: ")
                        .Append(S1RunCaptureRunner.LevelTraceProfile)
                        .Append('\n');
                }
                summary.Append("Segments: ")
                    .Append(result.Segments.Count.ToString(
                        CultureInfo.InvariantCulture))
                    .Append('\n');
                summary.Append("Transitions: ")
                    .Append(result.Transitions.Count.ToString(
                        CultureInfo.InvariantCulture))
                    .Append('\n');
                foreach (RunManifestSegment entry in result.Segments)
                {
                    summary.Append("Segment ").Append(entry.Dir)
                        .Append(": kind=").Append(entry.Kind)
                        .Append(", BK2 frame offset=")
                        .Append(entry.Bk2FrameOffset.ToString(
                            CultureInfo.InvariantCulture))
                        .Append(", trace frames=")
                        .Append(entry.TraceFrameCount.ToString(
                            CultureInfo.InvariantCulture))
                        .Append('\n');
                }
                if (result.RunManifestJson != null)
                {
                    summary.Append("Run manifest: ")
                        .Append(manifestPath)
                        .Append('\n');
                }
                stdout.Write(summary.ToString());
                stdout.Flush();

                staged.Publish();
                staged = null;
                return 0;
            }
            catch (Exception exception)
            {
                if (session != null)
                {
                    session.Dispose();
                }
                if (staged != null)
                {
                    staged.Dispose();
                }
                ReportFailure(stderr, exception);
                return 1;
            }
        }

        /// <summary>
        /// Applies the per-fixture-set line-ending policy: identity for
        /// LF-only fixture sets, ExpandRunNewlines for the CRLF ones.
        /// </summary>
        private static string ExpandNewlinesIf(bool expand, string content)
        {
            return expand ? ExpandRunNewlines(content) : content;
        }

        /// <summary>
        /// S2 plain trace mode (profiles gameplay_unlock /
        /// level_gated_reset_aware): the S1 publication pipeline with the
        /// S2 capture runner. The Lua's OGGF_BK2_BASENAME env input is
        /// derived from the movie file itself.
        /// </summary>
        private static int RunS2Trace(
            CommandLineOptions options,
            BizHawkInstallation installation,
            string romSha1,
            Bk2Movie movie,
            TextWriter stdout,
            TextWriter stderr,
            Func<string, GPGX.GPGXSyncSettings, IGpgxHost> openHost)
        {
            string traceProfile = options.TraceProfile
                ?? S2TraceCaptureRunner.GameplayUnlockProfile;
            int targetGameplaySegment = options.GameplaySegment ?? 0;
            string physicsPath = Path.Combine(
                options.OutputDirectory,
                CommandLineOptions.TraceOutputFileNames[0]);
            string auxStatePath = Path.Combine(
                options.OutputDirectory,
                CommandLineOptions.TraceOutputFileNames[1]);
            string metadataPath = Path.Combine(
                options.OutputDirectory,
                CommandLineOptions.TraceOutputFileNames[2]);
            return RunTraceCapture(
                options.OutputDirectory,
                stdout,
                stderr,
                () => new NativeStandardOutputSilencer(),
                () => openHost(
                    options.RomPath,
                    movie.SyncSettings),
                (host, writers) => S2TraceCaptureRunner.Capture(
                    movie,
                    host,
                    traceProfile,
                    targetGameplaySegment,
                    Path.GetFileName(options.MoviePath),
                    DateTime.Now.ToString(
                        "yyyy-MM-dd",
                        CultureInfo.InvariantCulture),
                    writers[0],
                    writers[1],
                    writers[2]),
                result =>
                    "BizHawk: " + installation.ManagedVersion + "\n"
                    + "ROM SHA-1: " + romSha1 + "\n"
                    + "Movie frames: "
                    + movie.FrameCount.ToString(
                        CultureInfo.InvariantCulture)
                    + "\n"
                    + "Trace profile: " + traceProfile + "\n"
                    + "Gameplay segment: "
                    + result.GameplaySegment.ToString(
                        CultureInfo.InvariantCulture)
                    + "\n"
                    + "BK2 frame offset: "
                    + result.Bk2FrameOffset.ToString(
                        CultureInfo.InvariantCulture)
                    + "\n"
                    + "Trace frames: "
                    + result.TraceFrameCount.ToString(
                        CultureInfo.InvariantCulture)
                    + "\n"
                    + "Physics CSV: " + physicsPath + "\n"
                    + "Aux state JSONL: " + auxStatePath + "\n"
                    + "Metadata JSON: " + metadataPath + "\n",
                new NoReplacePublisher());
        }

        /// <summary>
        /// S2 run mode (--run-id): capture completes fully in memory, then
        /// every per-segment file plus run_manifest.json is staged and
        /// published as one all-or-nothing no-replace set — the manifest is
        /// linked last, so it can never exist without its segment files.
        /// </summary>
        private static int RunS2TraceRun(
            CommandLineOptions options,
            BizHawkInstallation installation,
            string romSha1,
            Bk2Movie movie,
            TextWriter stdout,
            TextWriter stderr,
            Func<string, GPGX.GPGXSyncSettings, IGpgxHost> openHost)
        {
            NoReplacePublisher.StagedPublicationSet staged = null;
            try
            {
                S2RunCaptureResult result;
                using (new NativeStandardOutputSilencer())
                using (IGpgxHost host = openHost(
                    options.RomPath,
                    movie.SyncSettings))
                {
                    result = S2RunCaptureRunner.Capture(
                        movie,
                        host,
                        options.RunId,
                        Path.GetFileName(options.MoviePath),
                        DateTime.Now.ToString(
                            "yyyy-MM-dd",
                            CultureInfo.InvariantCulture),
                        options.EffectiveMovieLength);
                }

                var fileNames = new List<string>();
                var contents = new List<string>();
                foreach (S2RunSegmentOutput segment in result.Segments)
                {
                    fileNames.Add(segment.DirToken + "/"
                        + CommandLineOptions.TraceOutputFileNames[0]);
                    contents.Add(ExpandRunNewlines(segment.PhysicsCsv));
                    fileNames.Add(segment.DirToken + "/"
                        + CommandLineOptions.TraceOutputFileNames[1]);
                    contents.Add(ExpandRunNewlines(segment.AuxStateJsonl));
                    fileNames.Add(segment.DirToken + "/"
                        + CommandLineOptions.TraceOutputFileNames[2]);
                    contents.Add(ExpandRunNewlines(segment.MetadataJson));
                }
                fileNames.Add(CommandLineOptions.RunManifestFileName);
                contents.Add(ExpandRunNewlines(result.RunManifestJson));

                staged = new NoReplacePublisher().StageAll(
                    options.OutputDirectory,
                    fileNames.ToArray(),
                    writers =>
                    {
                        for (var index = 0; index < contents.Count; index++)
                        {
                            writers[index].Write(contents[index]);
                        }
                    });

                var summary = new StringBuilder();
                summary.Append("BizHawk: ")
                    .Append(installation.ManagedVersion).Append('\n');
                summary.Append("ROM SHA-1: ").Append(romSha1).Append('\n');
                summary.Append("Movie frames: ")
                    .Append(movie.FrameCount.ToString(
                        CultureInfo.InvariantCulture))
                    .Append('\n');
                if (options.EffectiveMovieLength != 0)
                {
                    summary.Append("Effective movie length: ")
                        .Append(options.EffectiveMovieLength.ToString(
                            CultureInfo.InvariantCulture))
                        .Append('\n');
                }
                summary.Append("Run ID: ").Append(options.RunId)
                    .Append('\n');
                summary.Append("Segments: ")
                    .Append(result.Segments.Count.ToString(
                        CultureInfo.InvariantCulture))
                    .Append('\n');
                summary.Append("Transitions: ")
                    .Append(result.Transitions.Count.ToString(
                        CultureInfo.InvariantCulture))
                    .Append('\n');
                foreach (S2RunSegmentOutput segment in result.Segments)
                {
                    RunManifestSegment entry = segment.ManifestEntry;
                    summary.Append("Segment ").Append(entry.Dir)
                        .Append(": kind=").Append(entry.Kind)
                        .Append(", BK2 frame offset=")
                        .Append(entry.Bk2FrameOffset.ToString(
                            CultureInfo.InvariantCulture))
                        .Append(", trace frames=")
                        .Append(entry.TraceFrameCount.ToString(
                            CultureInfo.InvariantCulture))
                        .Append('\n');
                }
                summary.Append("Run manifest: ")
                    .Append(Path.Combine(
                        options.OutputDirectory,
                        CommandLineOptions.RunManifestFileName))
                    .Append('\n');
                stdout.Write(summary.ToString());
                stdout.Flush();

                staged.Publish();
                staged = null;
                return 0;
            }
            catch (Exception exception)
            {
                if (staged != null)
                {
                    staged.Dispose();
                }
                ReportFailure(stderr, exception);
                return 1;
            }
        }

        /// <summary>
        /// The canonical run captures (S2 halfpipe and S1
        /// s1-ghz-maze-roundtrip) ran under Windows EmuHawk, where the
        /// Lua's text-mode io.open expanded every logical "\n" to "\r\n" in
        /// every run-mode file it wrote (docs/s2-run-mode-behavior.md §9,
        /// docs/s1-run-mode-behavior.md §9). Run-mode publication
        /// reproduces that text-mode encoding so the published bytes match
        /// the canonical run fixture sets; plain trace mode and the S1
        /// complete-run profile remain LF-only per their own canonical
        /// fixtures. Since v9.13-s2 the S2 special-stage aux_state.jsonl
        /// carries the §11.3 event stream and is expanded like every other
        /// run-mode file (the S1 ss aux remains empty and passes through
        /// unchanged).
        /// </summary>
        private static string ExpandRunNewlines(string content)
        {
            return content.Replace("\n", "\r\n");
        }

        internal static int RunTraceCapture<TResult>(
            string outputDirectory,
            TextWriter stdout,
            TextWriter stderr,
            Func<IDisposable> silenceNativeOutput,
            Func<IGpgxHost> openHost,
            Func<IGpgxHost, TextWriter[], TResult> capture,
            Func<TResult, string> formatSuccess,
            NoReplacePublisher publisher)
            where TResult : class
        {
            NoReplacePublisher.StagedPublicationSet staged = null;
            try
            {
                TResult result = null;
                using (silenceNativeOutput())
                using (IGpgxHost host = openHost())
                {
                    staged = publisher.StageAll(
                        outputDirectory,
                        CommandLineOptions.TraceOutputFileNames,
                        writers => { result = capture(host, writers); });
                }

                stdout.Write(formatSuccess(result));
                stdout.Flush();

                // link(2) publication is the last commit point; the set
                // rolls back any partially linked finals on failure.
                staged.Publish();
                staged = null;
                return 0;
            }
            catch (Exception exception)
            {
                if (staged != null)
                {
                    staged.Dispose();
                }
                ReportFailure(stderr, exception);
                return 1;
            }
        }

        internal static int RunCapture(
            string outputDirectory,
            TextWriter stdout,
            TextWriter stderr,
            Func<IDisposable> silenceNativeOutput,
            Func<IGpgxHost> openHost,
            Action<IGpgxHost, TextWriter> capture,
            Func<int, string> formatSuccess,
            NoReplacePublisher publisher)
        {
            NoReplacePublisher.StagedPublication staged = null;
            try
            {
                int completedFrames;
                using (silenceNativeOutput())
                using (IGpgxHost host = openHost())
                {
                    staged = publisher.Stage(
                        outputDirectory,
                        writer => capture(host, writer));
                    completedFrames = host.CompletedFrame;
                }

                stdout.Write(formatSuccess(completedFrames));
                stdout.Flush();

                // link(2) publication is the last commit point. Publish()
                // absorbs only cleanup failures that happen after the link.
                staged.Publish();
                staged = null;
                return 0;
            }
            catch (Exception exception)
            {
                if (staged != null)
                {
                    staged.Dispose();
                }
                ReportFailure(stderr, exception);
                return 1;
            }
        }

        private static void ReportFailure(
            TextWriter stderr,
            Exception exception)
        {
            try
            {
                stderr.Write("Error: " + exception.Message + "\n");
                stderr.Flush();
            }
            catch (Exception)
            {
                // Reporting is best-effort. The failure status remains 1,
                // and no final output has been published.
            }
        }
    }
}
