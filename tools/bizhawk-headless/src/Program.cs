using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text;
using System.Security.Cryptography;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;
using OpenGGF.BizHawk.Headless;

namespace BizHawk.Headless.Gpgx
{
    internal enum CaptureMode
    {
        Smoke,
        Trace,
        LoadTime
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

        internal static readonly string[] S3kTraceOutputFileNames =
        {
            "physics.csv",
            "aux_state.jsonl",
            "hardware_timing.jsonl",
            "metadata.json"
        };

        internal const string RunManifestFileName = "run_manifest.json";
        internal const string LoadTimeFileName = "load_time_measurements.jsonl";

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
            int effectiveMovieLength,
            bool loadQueueState,
            bool compress,
            long compressThresholdBytes)
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
            LoadQueueState = loadQueueState;
            Compress = compress;
            CompressThresholdBytes = compressThresholdBytes;
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
        public bool LoadQueueState { get; private set; }

        /// <summary>
        /// Trace-mode payload compression, ON by default: physics.csv and
        /// aux_state.jsonl are gzipped inside the publication once they
        /// reach --compress-threshold (default 1 MiB, inherited from
        /// tools/traces/compress-traces.ps1).
        ///
        /// The default is on because the risk being managed is a commit, not
        /// disk usage: a full S3K complete-run aux stream measures ~254 MB
        /// raw against ~12 MB gzipped, so an uncompressed one is past
        /// GitHub's 100 MB per-file hard limit — unpushable, not merely
        /// large — and the failure mode of an opt-in flag is precisely that
        /// a human installing a fixture forgets it. Pairing the default with
        /// the 1 MiB threshold makes the harness and the repo's own commit
        /// policy agree by construction: the policy
        /// (.githooks/validate-policy.sh) rejects exactly the same two name
        /// patterns at exactly the same size, so a default capture can never
        /// produce a file that policy would refuse, while small captures
        /// keep their plain names.
        ///
        /// --no-compress opts out, for consumers that read a capture by its
        /// uncompressed name and never commit it: the ROM-backed
        /// differential gates, which capture into a temp directory and
        /// compare raw bytes against a fixture. --compress states the
        /// default explicitly and is mutually exclusive with it.
        /// </summary>
        public bool Compress { get; private set; }
        public long CompressThresholdBytes { get; private set; }

        /// <summary>
        /// The compressor for this invocation, or null under --no-compress.
        /// One instance per capture, so its report covers every payload the
        /// publication considered.
        /// </summary>
        public TracePayloadCompressor CreateCompressor()
        {
            return Compress
                ? new TracePayloadCompressor(CompressThresholdBytes)
                : null;
        }

        public static CommandLineOptions Parse(string[] args)
        {
            if (args == null)
            {
                throw new ArgumentNullException("args");
            }

            var values = new Dictionary<string, string>(
                StringComparer.Ordinal);
            var index = 0;
            while (index < args.Length)
            {
                string name = args[index];
                if (!IsSupportedArgument(name))
                {
                    throw new ArgumentException(
                        "Unknown argument: " + name + ".");
                }
                if (IsValuelessArgument(name))
                {
                    // Switches carry no value and consume one slot; every
                    // other argument keeps the original strict name/value
                    // pairing, including its rejection order.
                    if (values.ContainsKey(name))
                    {
                        throw new ArgumentException(
                            "Duplicate argument: " + name + ".");
                    }
                    values.Add(name, string.Empty);
                    index += 1;
                    continue;
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
                index += 2;
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
            if (mode == CaptureMode.LoadTime)
            {
                return ParseLoadTime(
                    values, romPath, moviePath, outputDirectory);
            }

            RejectInSmokeMode(values, "--trace-profile");
            RejectInSmokeMode(values, "--gameplay-segment");
            RejectInSmokeMode(values, "--run-id");
            RejectInSmokeMode(values, "--effective-movie-length");
            RejectInSmokeMode(values, "--load-queue-state");
            // Smoke mode publishes smoke.csv, which is not a trace payload,
            // so every compression argument would silently do nothing.
            RejectInSmokeMode(values, "--compress");
            RejectInSmokeMode(values, "--no-compress");
            RejectInSmokeMode(values, "--compress-threshold");
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
                0,
                false,
                false,
                TracePayloadCompressor.DefaultThresholdBytes);
        }

        private static CommandLineOptions ParseLoadTime(
            IDictionary<string, string> values,
            string romPath,
            string moviePath,
            string outputDirectory)
        {
            string[] unsupported =
            {
                "--bk2-frame-offset", "--max-frames", "--trace-profile",
                "--gameplay-segment", "--run-id",
                "--effective-movie-length", "--compress",
                "--no-compress", "--compress-threshold",
                "--load-queue-state"
            };
            foreach (string name in unsupported)
            {
                if (values.ContainsKey(name))
                {
                    throw new ArgumentException(
                        "Argument " + name
                        + " is not supported in load-time mode.");
                }
            }
            string fullOutputDirectory = Path.GetFullPath(outputDirectory);
            string finalPath = Path.Combine(
                fullOutputDirectory, LoadTimeFileName);
            if (LinuxPathEntry.Exists(finalPath))
            {
                throw new IOException(
                    "Final output already exists and will not be replaced: "
                    + finalPath);
            }
            return new CommandLineOptions(
                CaptureMode.LoadTime,
                Path.GetFullPath(romPath),
                Path.GetFullPath(moviePath),
                fullOutputDirectory,
                0, 0, null, null, null, 0, false,
                false,
                TracePayloadCompressor.DefaultThresholdBytes);
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
                if (runId == null
                    && !string.Equals(
                        traceProfile,
                        "complete_run",
                        StringComparison.Ordinal))
                {
                    throw new ArgumentException(
                        "Argument --effective-movie-length requires"
                        + " --run-id or --trace-profile complete_run:"
                        + " only the complete-run runner's movie-done"
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

            if (values.ContainsKey("--compress")
                && values.ContainsKey("--no-compress"))
            {
                throw new ArgumentException(
                    "Argument --compress cannot be combined with"
                    + " --no-compress.");
            }
            bool compress = !values.ContainsKey("--no-compress");
            long compressThresholdBytes =
                TracePayloadCompressor.DefaultThresholdBytes;
            if (values.ContainsKey("--compress-threshold"))
            {
                if (!compress)
                {
                    throw new ArgumentException(
                        "Argument --compress-threshold cannot be combined"
                        + " with --no-compress: it only retunes the size"
                        + " floor above which a trace payload is"
                        + " compressed.");
                }
                compressThresholdBytes = ParseLong(
                    values, "--compress-threshold", compressThresholdBytes);
                if (compressThresholdBytes < 0)
                {
                    throw new ArgumentOutOfRangeException(
                        "--compress-threshold",
                        "Argument --compress-threshold must be at least 0.");
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
                    // Whether a payload lands compressed depends on its size,
                    // which is unknown until capture ends, so --compress
                    // preflights BOTH names it could publish under.
                    if (!compress
                        || !TracePayloadCompressor.IsTracePayloadName(
                            fileName))
                    {
                        continue;
                    }
                    string compressedPath =
                        finalPath + TracePayloadCompressor.GzipExtension;
                    if (LinuxPathEntry.Exists(compressedPath))
                    {
                        throw new IOException(
                            "Final output already exists and will not be"
                            + " replaced: " + compressedPath);
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
                effectiveMovieLength,
                values.ContainsKey("--load-queue-state"),
                compress,
                compressThresholdBytes);
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
            if (value == "load-time")
            {
                return CaptureMode.LoadTime;
            }
            throw new ArgumentException(
                "Argument --mode must be \"smoke\", \"trace\","
                + " or \"load-time\".");
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
                || name == "--effective-movie-length"
                || name == "--load-queue-state"
                || name == "--compress"
                || name == "--no-compress"
                || name == "--compress-threshold";
        }

        private static bool IsValuelessArgument(string name)
        {
            return name == "--compress" || name == "--no-compress"
                || name == "--load-queue-state";
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

        /// <summary>
        /// Byte counts are parsed as 64-bit: a single complete-run
        /// aux_state.jsonl segment already reaches hundreds of megabytes, so
        /// a threshold above int range is a reasonable thing to ask for.
        /// </summary>
        private static long ParseLong(
            IDictionary<string, string> values,
            string name,
            long defaultValue)
        {
            string value;
            if (!values.TryGetValue(name, out value))
            {
                return defaultValue;
            }

            long parsed;
            if (!long.TryParse(
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
                if (options.Mode != CaptureMode.Smoke)
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
                if (options.Mode != CaptureMode.Smoke)
                {
                    if (options.Mode == CaptureMode.LoadTime)
                    {
                        if (traceGame != "s3k")
                        {
                            throw new ArgumentException(
                                "Load-time measurement requires the"
                                + " Sonic 3&K locked-on ROM.");
                        }
                        return RunLoadTime(
                            options,
                            installation,
                            romSha1,
                            romBytes,
                            movie,
                            stdout,
                            stderr,
                            openHost);
                    }
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
                    if (traceGame == "s3k")
                    {
                        if (options.GameplaySegment.HasValue)
                        {
                            throw new ArgumentException(
                                "Argument --gameplay-segment is only"
                                + " supported with the Sonic 2 ROM.");
                        }
                        if (options.RunId != null
                            || options.TraceProfile
                                == S3KCompleteRunSegmenter.LevelTraceProfile)
                        {
                            // s3k_complete_run_recorder.lua: --run-id is the
                            // Lua's OGGF_TRACE_RUN_ID (identities (B)/(C));
                            // --trace-profile complete_run is the same
                            // recorder with run_id unset (identity (A)),
                            // matching the S1 complete-run CLI shape. The
                            // Lua hard-pins TRACE_PROFILE to "complete_run",
                            // so no other profile string can select it.
                            RejectUnmodeledS3kCompleteRunEnvironment();
                            return RunS3kCompleteRun(
                                options,
                                installation,
                                romSha1,
                                romBytes,
                                movie,
                                stdout,
                                stderr,
                                openHost);
                        }
                        RejectUnmodeledS3kEnvironment();
                        return RunS3kTrace(
                            options,
                            installation,
                            romSha1,
                            romBytes,
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

        private static int RunLoadTime(
            CommandLineOptions options,
            BizHawkInstallation installation,
            string romSha1,
            byte[] rom,
            Bk2Movie movie,
            TextWriter stdout,
            TextWriter stderr,
            Func<string, GPGX.GPGXSyncSettings, IGpgxHost> openHost)
        {
            string outputPath = Path.Combine(
                options.OutputDirectory,
                CommandLineOptions.LoadTimeFileName);
            string fixture = Path.GetFileName(options.MoviePath);
            string movieSha256 = ComputeSha256(options.MoviePath);
            return RunTraceCapture(
                options.OutputDirectory,
                stdout,
                stderr,
                () => new NativeStandardOutputSilencer(),
                () => openHost(options.RomPath, movie.SyncSettings),
                (host, writers) =>
                {
                    LoadTimeMeasurementCaptureRunner.Capture(
                        movie, host, rom, fixture, movieSha256, romSha1,
                        writers[0]);
                    return movie;
                },
                ignored =>
                    "BizHawk: " + installation.ManagedVersion + "\n"
                    + "ROM SHA-1: " + romSha1 + "\n"
                    + "Movie frames: " + movie.FrameCount.ToString(
                        CultureInfo.InvariantCulture) + "\n"
                    + "Measurements: " + outputPath + "\n",
                new NoReplacePublisher(),
                new[] { CommandLineOptions.LoadTimeFileName });
        }

        private static string ComputeSha256(string path)
        {
            using (SHA256 sha = SHA256.Create())
            using (FileStream input = File.OpenRead(path))
            {
                return BitConverter.ToString(sha.ComputeHash(input))
                    .Replace("-", "").ToLowerInvariant();
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
                    writers[2],
                    options.LoadQueueState
                        ? File.ReadAllBytes(options.RomPath)
                        : null),
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
                new NoReplacePublisher(options.CreateCompressor()));
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
            NoReplacePublisher publisher = null;
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
                publisher = new NoReplacePublisher(
                    options.CreateCompressor());
                session = publisher.OpenSession(
                    options.OutputDirectory);
                using (var sink = new StagedRunSegmentSink(
                    session, expandNewlines))
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
                        sink,
                        options.LoadQueueState
                            ? File.ReadAllBytes(options.RomPath)
                            : null);
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
                WriteCompressionReport(stdout, publisher);
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
                    writers[2],
                    options.LoadQueueState
                        ? File.ReadAllBytes(options.RomPath)
                        : null),
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
                new NoReplacePublisher(options.CreateCompressor()));
        }

        /// <summary>
        /// The Lua S3K recorder takes its entire diagnostic surface from
        /// the ENVIRONMENT, never from CLI flags, so "the native CLI does
        /// not expose that switch" is no protection at all: a variable
        /// still exported by a frontier-investigation shell silently
        /// changes what the Lua would have produced from the same movie,
        /// and the native capture — which models none of it — would be
        /// committed as canonical with no diagnostic. Every such variable
        /// is a loud refusal instead. Three classes, all output-affecting:
        ///
        /// (a) <c>OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1</c> plus the two
        ///     variables that ARM a hook-driven aux family the port defers
        ///     entirely (docs/s3k-profiles-and-hooks.md §2.4). Arming adds
        ///     the family to metadata's aux_schema_extras and emits events
        ///     the port can never produce.
        /// (b) The frame-window overrides for aux families the port DOES
        ///     implement. These are poll-driven families whose emission
        ///     depends only on the window (never on a hook hit), and the
        ///     port pins their windows as the Lua defaults in
        ///     <see cref="S3KAuxEventEngine"/>'s *Window* constants — so a
        ///     set override yields a genuinely different aux_state.jsonl
        ///     with hooks off. Applied unconditionally at Lua script load,
        ///     independent of the hook switch.
        /// (c) The Lua's two early-stop variables, which finalize the
        ///     recording before the movie/zone stop and therefore truncate
        ///     both physics.csv and aux_state.jsonl.
        ///
        /// The window/stop overrides for families the port defers (e.g.
        /// OGGF_S3K_POSITION_WRITE_RANGE, OGGF_S3K_SOLID_CONT_RANGE,
        /// OGGF_S3K_AIZ_BOUNDARY_RANGE) are deliberately NOT rejected:
        /// their flush paths are gated on a hook-set `state.seen`/hit
        /// list, so with the hook switch off — the only mode the port
        /// targets, and itself a refusal when set — they change no byte of
        /// the Lua's own output either.
        ///
        /// Rejection keys on non-emptiness, not on parseability: the Lua
        /// warns and ignores a malformed range, but an operator who
        /// exported one intended to change the capture and must not be
        /// handed a silently canonical file. Scoped to the S3K trace
        /// branch only — the S1/S2 pipelines never consumed these
        /// variables.
        /// </summary>
        private static void RejectUnmodeledS3kEnvironment()
        {
            if (Environment.GetEnvironmentVariable(
                "OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS") == "1")
            {
                throw new InvalidOperationException(
                    "OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1 requests the"
                    + " Lua recorder's M68K exec/memory-write diagnostic"
                    + " hooks, which the native S3K recorder does not"
                    + " implement (deferred; see"
                    + " tools/bizhawk-headless/docs/"
                    + "s3k-profiles-and-hooks.md section 2.4). Unset it"
                    + " or capture with the Lua recorder.");
            }
            foreach (string[] entry in UnmodeledS3kEnvironmentVariables)
            {
                if (!string.IsNullOrEmpty(
                    Environment.GetEnvironmentVariable(entry[0])))
                {
                    throw new InvalidOperationException(
                        entry[0] + " " + entry[1] + " Unset it or capture"
                        + " with the Lua recorder.");
                }
            }
        }

        /// <summary>
        /// {variable name, reason} for every Lua S3K recorder environment
        /// variable that changes recorder output and that the native port
        /// does not model. See
        /// <see cref="RejectUnmodeledS3kEnvironment"/> for the three
        /// classes and for why the deferred families' own window
        /// overrides are absent.
        /// </summary>
        private static readonly string[][]
            UnmodeledS3kEnvironmentVariables =
        {
            // (a) Arms a hook-driven family the port defers entirely.
            new[]
            {
                "OGGF_S3K_CNZ_EVENT_RAM_RANGE",
                HookArmingRejectionReason
            },
            new[]
            {
                "OGGF_S3K_RNG_CALL_RANGE",
                HookArmingRejectionReason
            },
            // (b) Retunes a poll-driven family the port implements with
            // the Lua's default window baked in.
            //   s3k_trace_recorder.lua:3730 -> V628_AIZ_FIRE
            //   (S3KAuxEventEngine.AizFireWindowStart/End).
            new[]
            {
                "OGGF_S3K_AIZ_FIRE_RANGE",
                PolledWindowRejectionReason
            },
            //   :3932 v613_apply_aiz_wall_range -> V613_AIZ_WALL
            //   (TerrainWallWindowStart/End).
            new[]
            {
                "OGGF_S3K_AIZ_WALL_SENSOR_RANGE",
                PolledWindowRejectionReason
            },
            //   :4186 v615_apply_range -> V615_CRL's end-of-frame poll,
            //   which emits whenever in_window() regardless of whether
            //   Touch_Process was hooked (CrlWindowStart/End).
            new[]
            {
                "OGGF_S3K_CRL_RANGE",
                PolledWindowRejectionReason
            },
            //   :2596 -> V67_CNZ.emit_cnz_cylinder_state_per_frame
            //   (CnzCylinderWindowStart/End).
            new[]
            {
                "OGGF_S3K_CNZ_CYLINDER_RANGE",
                PolledWindowRejectionReason
            },
            //   :3553-3555 -> V69_AIZ.flush_aiz_handoff_terrain_state,
            //   whose window gate runs before the hook-state check
            //   (AizHandoffWindowStart/End).
            new[]
            {
                "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START",
                PolledWindowRejectionReason
            },
            new[]
            {
                "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_END",
                PolledWindowRejectionReason
            },
            // (c) Truncates the capture (lua :274-275, honored at
            // :4514-4528).
            new[]
            {
                "OGGF_TRACE_STOP_FRAME",
                EarlyStopRejectionReason
            },
            new[]
            {
                "OGGF_BK2_FRAME_COUNT",
                EarlyStopRejectionReason
            }
        };

        /// <summary>
        /// The COMPLETE-RUN recorder's own environment surface
        /// (s3k_complete_run_recorder.lua; spec
        /// tools/bizhawk-headless/docs/s3k-run-publication.md §8.1). It is
        /// deliberately NOT the standard recorder's table: that recorder
        /// honors OGGF_S3K_TRACE_PROFILE, while this one hard-pins
        /// TRACE_PROFILE to "complete_run" at L341, which makes three
        /// otherwise plausible refusals FALSE refusals. Every entry below
        /// was justified against HEAD rather than inherited.
        ///
        /// Refused — nine variables, eight entries:
        ///
        /// - OGGF_TRACE_STOP_FRAME / OGGF_BK2_FRAME_COUNT truncate both
        ///   published streams. The modeled equivalent of the latter is the
        ///   existing --effective-movie-length option, exactly as in the
        ///   S1/S2 run ports.
        /// - OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1 arms all 13 hook families
        ///   (which the port defers entirely) AND removes the capture_mode
        ///   metadata line.
        /// - OGGF_S3K_CNZ_EVENT_RAM_RANGE is the ONLY gate on the
        ///   frame-polled cnz_event_ram emit (V622_CNZ_EVENT_RAM.write
        ///   L3310, called unconditionally per frame at L5699; .enabled
        ///   defaults false at L3250 and is set only here at L3255), so
        ///   setting it injects aux lines no fixture has.
        /// - OGGF_S3K_AIZ_WALL_SENSOR_RANGE, OGGF_S3K_CRL_RANGE,
        ///   OGGF_S3K_CNZ_CYLINDER_RANGE and the
        ///   OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START/_END pair retune
        ///   frame-polled families that emit purely on window + zone with no
        ///   hit-list guard — and which ARE present in the fixtures
        ///   (terrain_wall_sensor 12 and aiz_handoff_terrain_state 9 in
        ///   aiz_completerun; cnz_cylinder_state 23 and
        ///   collision_response_list_end_of_frame 7 in cnz_completerun). The
        ///   port pins their windows as S3KAuxEventEngine constants.
        ///
        /// NOT refused, and pinned as such by a test so this guard can never
        /// degrade into a blanket OGGF_* ban:
        ///
        /// - OGGF_S3K_AIZ_FIRE_RANGE. V628_AIZ_FIRE.write() returns at
        ///   L3393 on !is_aiz_end_to_end_profile(), which is
        ///   TRACE_PROFILE == "aiz_end_to_end" — impossible under L341. The
        ///   family can never be emitted by this recorder. The STANDARD
        ///   recorder's table refuses it; carrying that over would be a
        ///   false refusal.
        /// - OGGF_S3K_RNG_CALL_RANGE. Both consumers are dead here: the
        ///   aux_schema_extras append is at L1422 inside the `else` arm of
        ///   `if TRACE_PROFILE == "complete_run"` (L1392), and rng_call
        ///   lines come only from V625_RNG_CALLS.flush() (L3042), which
        ///   early-returns on an empty hit list populated only by hooks
        ///   registered inside `if not LIGHTWEIGHT_REGEN` (L5816) — i.e.
        ///   only under the already-refused hook switch. Same "no with
        ///   hooks off" class as the seven window variables below.
        /// - OGGF_S3K_POSITION_WRITE_RANGE, OGGF_S3K_VELOCITY_WRITE_RANGE,
        ///   OGGF_S3K_SOLID_CONT_RANGE, OGGF_S3K_AIZ_SHIP_LOOP_RANGE,
        ///   OGGF_S3K_AIZ_BOUNDARY_RANGE (+ its legacy
        ///   _FRAME_START/_END form) and
        ///   OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_START/_END: each only
        ///   widens a window consulted by a flush that early-returns on an
        ///   empty hit list, and those lists stay empty while L5816 leaves
        ///   the hooks unregistered.
        /// - OGGF_TRACE_QUIET only replaces `print` with a no-op; it
        ///   changes stdout, never a published file.
        /// - OGGF_TRACE_LIGHTWEIGHT no longer exists (removed by
        ///   192d9c976). A stale export of it is silently ignored by HEAD,
        ///   so refusing it would be a false refusal.
        /// - OGGF_TRACE_OUTPUT_DIR, OGGF_BK2_BASENAME and
        ///   OGGF_TRACE_RUN_ID are MODELED (--output, the movie file name,
        ///   --run-id).
        /// </summary>
        private static void RejectUnmodeledS3kCompleteRunEnvironment()
        {
            if (Environment.GetEnvironmentVariable(
                "OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS") == "1")
            {
                throw new InvalidOperationException(
                    "OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1 requests the"
                    + " Lua complete-run recorder's M68K exec/memory-write"
                    + " diagnostic hooks, which the native S3K complete-run"
                    + " recorder does not implement (deferred; see"
                    + " tools/bizhawk-headless/docs/"
                    + "s3k-run-publication.md section 8.2). It also removes"
                    + " the capture_mode metadata line. Unset it or capture"
                    + " with the Lua recorder.");
            }
            foreach (string[] entry in
                UnmodeledS3kCompleteRunEnvironmentVariables)
            {
                if (!string.IsNullOrEmpty(
                    Environment.GetEnvironmentVariable(entry[0])))
                {
                    throw new InvalidOperationException(
                        entry[0] + " " + entry[1] + " Unset it or capture"
                        + " with the Lua recorder.");
                }
            }
        }

        /// <summary>
        /// {variable name, reason} for every complete-run recorder
        /// environment variable that changes published output and that the
        /// native port does not model. See
        /// <see cref="RejectUnmodeledS3kCompleteRunEnvironment"/> for the
        /// per-variable justification and for the deliberate omissions.
        /// </summary>
        private static readonly string[][]
            UnmodeledS3kCompleteRunEnvironmentVariables =
        {
            // Arms a frame-polled family absent from every fixture.
            new[]
            {
                "OGGF_S3K_CNZ_EVENT_RAM_RANGE",
                CompleteRunHookArmingRejectionReason
            },
            // Retunes frame-polled families the port implements with the
            // Lua's default window pinned as a constant.
            new[]
            {
                "OGGF_S3K_AIZ_WALL_SENSOR_RANGE",
                PolledWindowRejectionReason
            },
            new[]
            {
                "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START",
                PolledWindowRejectionReason
            },
            new[]
            {
                "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_END",
                PolledWindowRejectionReason
            },
            new[]
            {
                "OGGF_S3K_CRL_RANGE",
                PolledWindowRejectionReason
            },
            new[]
            {
                "OGGF_S3K_CNZ_CYLINDER_RANGE",
                PolledWindowRejectionReason
            },
            // Truncates the capture (lua :342-343).
            new[]
            {
                "OGGF_TRACE_STOP_FRAME",
                CompleteRunEarlyStopRejectionReason
            },
            new[]
            {
                "OGGF_BK2_FRAME_COUNT",
                CompleteRunEarlyStopRejectionReason
            }
        };

        private const string CompleteRunHookArmingRejectionReason =
            "is the only gate on the frame-polled cnz_event_ram aux event"
            + " family, which the native S3K complete-run recorder does not"
            + " implement and which appears in no canonical fixture (see"
            + " tools/bizhawk-headless/docs/s3k-run-publication.md"
            + " section 8.1).";

        private const string CompleteRunEarlyStopRejectionReason =
            "finalizes the Lua complete-run recorder's capture early and"
            + " truncates physics.csv and aux_state.jsonl; the native"
            + " recorder models the movie-length signal through"
            + " --effective-movie-length instead (see"
            + " tools/bizhawk-headless/docs/s3k-run-publication.md"
            + " section 8.1).";

        private const string HookArmingRejectionReason =
            "arms a hook-driven aux event family that the native S3K"
            + " recorder does not implement (deferred; see"
            + " tools/bizhawk-headless/docs/s3k-profiles-and-hooks.md"
            + " section 2.4).";

        private const string PolledWindowRejectionReason =
            "retunes the frame window of a poll-driven aux event family"
            + " that the native S3K recorder implements with the Lua's"
            + " default window pinned as a constant, so honoring it would"
            + " require rebuilding the port rather than re-running it"
            + " (see tools/bizhawk-headless/docs/s3k-aux-events.md).";

        private const string EarlyStopRejectionReason =
            "finalizes the Lua recorder's capture before the movie/zone"
            + " stop and truncates physics.csv and aux_state.jsonl, which"
            + " the native S3K recorder does not model (see"
            + " tools/bizhawk-headless/docs/s3k-profiles-and-hooks.md"
            + " section 3).";

        /// <summary>
        /// S3K standard trace mode (profiles gameplay_unlock /
        /// aiz_end_to_end / level_gated_reset_aware): the shared trace
        /// publication pipeline with the S3K capture runner. Any other
        /// --trace-profile string is passed through with gameplay_unlock
        /// semantics and written verbatim into metadata.trace_profile,
        /// exactly like the Lua's OGGF_S3K_TRACE_PROFILE handling.
        /// </summary>
        private static int RunS3kTrace(
            CommandLineOptions options,
            BizHawkInstallation installation,
            string romSha1,
            byte[] romBytes,
            Bk2Movie movie,
            TextWriter stdout,
            TextWriter stderr,
            Func<string, GPGX.GPGXSyncSettings, IGpgxHost> openHost)
        {
            string traceProfile = options.TraceProfile
                ?? S3KTraceCaptureRunner.GameplayUnlockProfile;
            string physicsPath = Path.Combine(
                options.OutputDirectory,
                CommandLineOptions.TraceOutputFileNames[0]);
            string auxStatePath = Path.Combine(
                options.OutputDirectory,
                CommandLineOptions.TraceOutputFileNames[1]);
            string metadataPath = Path.Combine(
                options.OutputDirectory,
                CommandLineOptions.S3kTraceOutputFileNames[3]);
            string hardwareTimingPath = Path.Combine(
                options.OutputDirectory,
                CommandLineOptions.S3kTraceOutputFileNames[2]);
            return RunTraceCapture(
                options.OutputDirectory,
                stdout,
                stderr,
                () => new NativeStandardOutputSilencer(),
                () => openHost(
                    options.RomPath,
                    movie.SyncSettings),
                (host, writers) => S3KTraceCaptureRunner.Capture(
                    movie,
                    host,
                    traceProfile,
                    DateTime.Now.ToString(
                        "yyyy-MM-dd",
                        CultureInfo.InvariantCulture),
                    romBytes,
                    writers[0],
                    writers[1],
                    writers[2],
                    writers[3],
                    options.LoadQueueState),
                result =>
                    "BizHawk: " + installation.ManagedVersion + "\n"
                    + "ROM SHA-1: " + romSha1 + "\n"
                    + "Movie frames: "
                    + movie.FrameCount.ToString(
                        CultureInfo.InvariantCulture)
                    + "\n"
                    + "Trace profile: " + traceProfile + "\n"
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
                    + "Hardware timing JSONL: "
                    + hardwareTimingPath + "\n"
                    + "Metadata JSON: " + metadataPath + "\n",
                new NoReplacePublisher(options.CreateCompressor()),
                CommandLineOptions.S3kTraceOutputFileNames);
        }

        /// <summary>
        /// S3K complete-run mode (--run-id, or --trace-profile
        /// complete_run): one movie pass through
        /// <see cref="S3KCompleteRunCaptureRunner"/> — the per-zone
        /// auto-segmenting recorder whose bonus/special-stage detour
        /// machine is always on, exactly like the Lua — publishing every
        /// discovered segment directory plus (when emitted)
        /// run_manifest.json as one all-or-nothing no-replace set.
        ///
        /// Segments stage incrementally AND stream: physics.csv and
        /// aux_state.jsonl are written straight into their staged
        /// temporaries as the capture produces them, so a 19-segment,
        /// 1.4 GB pass never holds a segment in memory (spec
        /// docs/s3k-run-publication.md §9 invariant 12). The manifest
        /// stages last, so it can never exist without its segment files.
        ///
        /// run_manifest.json is emitted iff any transition was recorded OR
        /// --run-id was supplied (the Lua's disjunction) — which is exactly
        /// why the seven canonical *_completerun fixtures, a detour-free
        /// pass with no run id, have none.
        ///
        /// Line endings are LF in BOTH modes.
        /// <see cref="ExpandRunNewlines"/> is deliberately NOT applied on
        /// this path: the canonical run-mode S3K fixture set (identity (C),
        /// captured under Linux/Mono on 2026-07-23) is LF, so the S1/S2
        /// "run mode implies CRLF" rule would corrupt it (spec §6).
        /// </summary>
        private static int RunS3kCompleteRun(
            CommandLineOptions options,
            BizHawkInstallation installation,
            string romSha1,
            byte[] romBytes,
            Bk2Movie movie,
            TextWriter stdout,
            TextWriter stderr,
            Func<string, GPGX.GPGXSyncSettings, IGpgxHost> openHost)
        {
            NoReplacePublisher publisher = null;
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
                    // yet may still emit a manifest when the movie takes a
                    // bonus/giant-ring detour — and even a manifest-free
                    // capture next to a stale manifest would corrupt the run
                    // layout that stale manifest describes.
                    throw new IOException(
                        "Final output already exists and will not be"
                        + " replaced: " + manifestPath);
                }

                S3KCompleteRunCaptureResult result;
                publisher = new NoReplacePublisher(
                    options.CreateCompressor());
                session = publisher.OpenSession(
                    options.OutputDirectory);
                using (var sink = new S3KStagedSegmentSink(session))
                using (new NativeStandardOutputSilencer())
                using (IGpgxHost host = openHost(
                    options.RomPath,
                    movie.SyncSettings))
                {
                    result = S3KCompleteRunCaptureRunner.Capture(
                        movie,
                        host,
                        options.RunId,
                        Path.GetFileName(options.MoviePath),
                        DateTime.Now.ToString(
                            "yyyy-MM-dd",
                            CultureInfo.InvariantCulture),
                        options.EffectiveMovieLength,
                        romBytes,
                        sink,
                        options.LoadQueueState);
                }
                if (result.RunManifestJson != null)
                {
                    session.StageFile(
                        CommandLineOptions.RunManifestFileName,
                        result.RunManifestJson);
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
                if (options.EffectiveMovieLength != 0)
                {
                    summary.Append("Effective movie length: ")
                        .Append(options.EffectiveMovieLength.ToString(
                            CultureInfo.InvariantCulture))
                        .Append('\n');
                }
                if (options.RunId != null)
                {
                    summary.Append("Run ID: ").Append(options.RunId)
                        .Append('\n');
                }
                else
                {
                    summary.Append("Trace profile: ")
                        .Append(S3KCompleteRunSegmenter.LevelTraceProfile)
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
                WriteCompressionReport(stdout, publisher);
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
        /// S2 run mode (--run-id): every per-segment file plus
        /// run_manifest.json is staged and published as one all-or-nothing
        /// no-replace set — the manifest is linked last, so it can never
        /// exist without its segment files.
        ///
        /// Segments stage incrementally AND stream: physics.csv and
        /// aux_state.jsonl are written straight into their staged
        /// temporaries as the capture produces them, so the complete-emeralds
        /// pass (35 segments, ~375 MB of output) never holds a segment in
        /// memory. The manifest stages last.
        ///
        /// Line endings are the canonical run capture's CRLF
        /// (<see cref="ExpandRunNewlines"/>), applied to the streams by the
        /// sink and to the manifest here.
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
            NoReplacePublisher publisher = null;
            NoReplacePublisher.IncrementalStagingSession session = null;
            NoReplacePublisher.StagedPublicationSet staged = null;
            try
            {
                S2RunCaptureResult result;
                publisher = new NoReplacePublisher(
                    options.CreateCompressor());
                session = publisher.OpenSession(options.OutputDirectory);
                using (var sink = new StagedRunSegmentSink(session, true))
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
                        options.EffectiveMovieLength,
                        sink,
                        options.LoadQueueState
                            ? File.ReadAllBytes(options.RomPath)
                            : null);
                }
                session.StageFile(
                    CommandLineOptions.RunManifestFileName,
                    ExpandRunNewlines(result.RunManifestJson));
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
                summary.Append("Run manifest: ")
                    .Append(Path.Combine(
                        options.OutputDirectory,
                        CommandLineOptions.RunManifestFileName))
                    .Append('\n');
                stdout.Write(summary.ToString());
                stdout.Flush();

                staged.Publish();
                staged = null;
                WriteCompressionReport(stdout, publisher);
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
        internal static string ExpandRunNewlines(string content)
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
            return RunTraceCapture(
                outputDirectory,
                stdout,
                stderr,
                silenceNativeOutput,
                openHost,
                capture,
                formatSuccess,
                publisher,
                CommandLineOptions.TraceOutputFileNames);
        }

        internal static int RunTraceCapture<TResult>(
            string outputDirectory,
            TextWriter stdout,
            TextWriter stderr,
            Func<IDisposable> silenceNativeOutput,
            Func<IGpgxHost> openHost,
            Func<IGpgxHost, TextWriter[], TResult> capture,
            Func<TResult, string> formatSuccess,
            NoReplacePublisher publisher,
            string[] outputFileNames)
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
                        outputFileNames,
                        writers => { result = capture(host, writers); });
                }

                stdout.Write(formatSuccess(result));
                stdout.Flush();

                // link(2) publication is the last commit point; the set
                // rolls back any partially linked finals on failure.
                staged.Publish();
                staged = null;
                WriteCompressionReport(stdout, publisher);
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
                WriteCompressionReport(stdout, publisher);
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
        /// Reports what --compress did, AFTER publication commits, so every
        /// line names a file that exists. The pre-publish summary prints each
        /// payload under its uncompressed name because whether a payload
        /// clears the threshold is only known once it has been written; these
        /// lines reconcile that. A no-op when compression is off.
        /// </summary>
        private static void WriteCompressionReport(
            TextWriter stdout,
            NoReplacePublisher publisher)
        {
            if (publisher == null || publisher.Compressor == null)
            {
                return;
            }
            foreach (string line in publisher.Compressor.Report)
            {
                stdout.Write(line + "\n");
            }
            stdout.Flush();
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
