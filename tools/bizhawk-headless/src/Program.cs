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
            string runId)
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
        }

        public CaptureMode Mode { get; private set; }
        public string RomPath { get; private set; }
        public string MoviePath { get; private set; }
        public string OutputDirectory { get; private set; }
        public int Bk2FrameOffset { get; private set; }
        public int MaxFrames { get; private set; }

        /// <summary>
        /// S2 trace-mode arguments, mirroring the Lua recorder's env inputs
        /// (null = argument not supplied): --trace-profile
        /// (OGGF_S2_TRACE_PROFILE, default "gameplay_unlock"),
        /// --gameplay-segment (OGGF_TRACE_GAMEPLAY_SEGMENT, default 0), and
        /// --run-id (OGGF_TRACE_RUN_ID; absent = plain mode). --run-id is
        /// mutually exclusive with the other two: the Lua run capture
        /// procedure never sets the profile/segment env vars, so run mode
        /// always records gameplay_unlock level segments with no segment
        /// skipping.
        /// </summary>
        public string TraceProfile { get; private set; }
        public int? GameplaySegment { get; private set; }
        public string RunId { get; private set; }

        public bool HasS2Arguments
        {
            get
            {
                return TraceProfile != null
                    || GameplaySegment.HasValue
                    || RunId != null;
            }
        }

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
                null);
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
                runId);
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
                || name == "--run-id";
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
                        if (options.HasS2Arguments)
                        {
                            throw new ArgumentException(
                                "Arguments --trace-profile,"
                                + " --gameplay-segment and --run-id are"
                                + " only supported with the Sonic 2 ROM.");
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
                        0);
                }

                var fileNames = new List<string>();
                var contents = new List<string>();
                foreach (S2RunSegmentOutput segment in result.Segments)
                {
                    fileNames.Add(segment.DirToken + "/"
                        + CommandLineOptions.TraceOutputFileNames[0]);
                    contents.Add(segment.PhysicsCsv);
                    fileNames.Add(segment.DirToken + "/"
                        + CommandLineOptions.TraceOutputFileNames[1]);
                    contents.Add(segment.AuxStateJsonl);
                    fileNames.Add(segment.DirToken + "/"
                        + CommandLineOptions.TraceOutputFileNames[2]);
                    contents.Add(segment.MetadataJson);
                }
                fileNames.Add(CommandLineOptions.RunManifestFileName);
                contents.Add(result.RunManifestJson);

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
                    S2RunManifestSegment entry = segment.ManifestEntry;
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
