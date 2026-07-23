using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;
using OpenGGF.BizHawk.Headless;

namespace BizHawk.Headless.Gpgx
{
    internal sealed class CommandLineOptions
    {
        private CommandLineOptions(
            string romPath,
            string moviePath,
            string outputDirectory,
            int bk2FrameOffset,
            int maxFrames)
        {
            RomPath = romPath;
            MoviePath = moviePath;
            OutputDirectory = outputDirectory;
            Bk2FrameOffset = bk2FrameOffset;
            MaxFrames = maxFrames;
        }

        public string RomPath { get; private set; }
        public string MoviePath { get; private set; }
        public string OutputDirectory { get; private set; }
        public int Bk2FrameOffset { get; private set; }
        public int MaxFrames { get; private set; }

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
                Path.GetFullPath(romPath),
                Path.GetFullPath(moviePath),
                fullOutputDirectory,
                offset,
                maxFrames);
        }

        private static bool IsSupportedArgument(string name)
        {
            return name == "--rom"
                || name == "--movie"
                || name == "--output"
                || name == "--bk2-frame-offset"
                || name == "--max-frames";
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
                string romSha1 = RomIdentity.ValidateSonic1Rev01(
                    File.ReadAllBytes(options.RomPath));
                if (!File.Exists(options.MoviePath))
                {
                    throw new FileNotFoundException(
                        "BK2 movie does not exist.",
                        options.MoviePath);
                }
                Bk2Movie movie = Bk2Reader.Read(options.MoviePath);
                long requiredFrames =
                    (long)options.Bk2FrameOffset + options.MaxFrames;
                if (requiredFrames > movie.FrameCount)
                {
                    throw new InvalidDataException(
                        "Movie contains only " + movie.FrameCount
                        + " frames; capture requires "
                        + requiredFrames + ".");
                }

                int completedFrames;
                using (new NativeStandardOutputSilencer())
                using (IGpgxHost host = openHost(
                    options.RomPath,
                    movie.SyncSettings))
                {
                    new NoReplacePublisher().Publish(
                        options.OutputDirectory,
                        writer => SmokeCaptureRunner.Capture(
                            movie,
                            host,
                            options.Bk2FrameOffset,
                            options.MaxFrames,
                            writer));
                    completedFrames = host.CompletedFrame;
                }

                string finalPath = Path.GetFullPath(Path.Combine(
                    options.OutputDirectory,
                    "smoke.csv"));
                stdout.Write(
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
                    + "Output: " + finalPath + "\n");
                stdout.Flush();
                return 0;
            }
            catch (Exception exception)
            {
                stderr.Write("Error: " + exception.Message + "\n");
                stderr.Flush();
                return 1;
            }
        }
    }
}
