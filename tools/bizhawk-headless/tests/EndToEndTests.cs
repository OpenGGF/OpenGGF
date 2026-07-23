using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using BizHawk.Headless.Gpgx;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class EndToEndTests
    {
        private const string CanonicalBk2Sha256 =
            "dced61b2d3a3346b2ecd62254140497ef2827374c1de8597780f91e39ca0dcea";
        private const string CanonicalPhysicsSha256 =
            "dd0a03bfddefa9570d4b49ee2d4ea5e35e2b8141147e17ab482a3654d311cb66";
        private const int CaptureFrameCount = 1000;

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "Cli requires ROM movie and output arguments",
                CliRequiresArguments));
            tests.Add(new TestMain.TestCase(
                "Cli defaults and validates BK2 frame offset",
                CliValidatesOffset));
            tests.Add(new TestMain.TestCase(
                "Cli defaults and validates capture frame count",
                CliValidatesFrameCount));
            tests.Add(new TestMain.TestCase(
                "Cli rejects unknown and duplicate arguments",
                CliRejectsUnknownAndDuplicateArguments));
            tests.Add(new TestMain.TestCase(
                "Cli rejects an existing final output",
                CliRejectsExistingFinalOutput));
            tests.Add(new TestMain.TestCase(
                "Cli run script invokes only harness Mono with DISPLAY absent",
                RunScriptInvokesHarnessHeadlessly));
            tests.Add(new TestMain.TestCase(
                "EndToEnd production assembly excludes frontend references",
                ProductionAssemblyExcludesFrontendReferences));
            tests.Add(new TestMain.TestCase(
                "EndToEnd",
                CapturesCanonicalRowsDeterministically));
        }

        private static void CliRequiresArguments()
        {
            AssertEx.Throws<ArgumentException>(
                () => CommandLineOptions.Parse(new string[0]),
                "--rom");
            AssertEx.Throws<ArgumentException>(
                () => CommandLineOptions.Parse(new[]
                {
                    "--rom", "game.gen",
                    "--output", "out"
                }),
                "--movie");
            AssertEx.Throws<ArgumentException>(
                () => CommandLineOptions.Parse(new[]
                {
                    "--rom", "game.gen",
                    "--movie", "movie.bk2"
                }),
                "--output");
            AssertEx.Throws<ArgumentException>(
                () => CommandLineOptions.Parse(new[] { "--rom" }),
                "value");
        }

        private static void CliValidatesOffset()
        {
            WithUnusedOutput(
                output =>
                {
                    CommandLineOptions defaults =
                        CommandLineOptions.Parse(RequiredArguments(output));
                    AssertEx.Equal(0, defaults.Bk2FrameOffset);

                    CommandLineOptions zero = CommandLineOptions.Parse(
                        Append(
                            RequiredArguments(output),
                            "--bk2-frame-offset",
                            "0"));
                    AssertEx.Equal(0, zero.Bk2FrameOffset);

                    AssertEx.Throws<ArgumentOutOfRangeException>(
                        () => CommandLineOptions.Parse(
                            Append(
                                RequiredArguments(output),
                                "--bk2-frame-offset",
                                "-1")),
                        "--bk2-frame-offset");
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(
                            Append(
                                RequiredArguments(output),
                                "--bk2-frame-offset",
                                "not-an-integer")),
                        "--bk2-frame-offset");
                });
        }

        private static void CliValidatesFrameCount()
        {
            WithUnusedOutput(
                output =>
                {
                    CommandLineOptions defaults =
                        CommandLineOptions.Parse(RequiredArguments(output));
                    AssertEx.Equal(1000, defaults.MaxFrames);

                    AssertEx.Equal(
                        1,
                        CommandLineOptions.Parse(Append(
                            RequiredArguments(output),
                            "--max-frames",
                            "1")).MaxFrames);
                    AssertEx.Equal(
                        1000,
                        CommandLineOptions.Parse(Append(
                            RequiredArguments(output),
                            "--max-frames",
                            "1000")).MaxFrames);

                    AssertEx.Throws<ArgumentOutOfRangeException>(
                        () => CommandLineOptions.Parse(Append(
                            RequiredArguments(output),
                            "--max-frames",
                            "0")),
                        "--max-frames");
                    AssertEx.Throws<ArgumentOutOfRangeException>(
                        () => CommandLineOptions.Parse(Append(
                            RequiredArguments(output),
                            "--max-frames",
                            "1001")),
                        "--max-frames");
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(Append(
                            RequiredArguments(output),
                            "--max-frames",
                            "1.5")),
                        "--max-frames");
                });
        }

        private static void CliRejectsUnknownAndDuplicateArguments()
        {
            WithUnusedOutput(
                output =>
                {
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(Append(
                            RequiredArguments(output),
                            "--unknown",
                            "value")),
                        "Unknown argument");
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(Append(
                            RequiredArguments(output),
                            "--rom",
                            "other.gen")),
                        "Duplicate argument");
                });
        }

        private static void CliRejectsExistingFinalOutput()
        {
            WithUnusedOutput(
                output =>
                {
                    Directory.CreateDirectory(output);
                    string finalPath = Path.Combine(output, "smoke.csv");
                    File.WriteAllBytes(
                        finalPath,
                        new byte[] { 0xDE, 0xAD, 0xBE, 0xEF });

                    AssertEx.Throws<IOException>(
                        () => CommandLineOptions.Parse(
                            RequiredArguments(output)),
                        "already exists");
                    AssertEx.Equal(
                        "DE-AD-BE-EF",
                        BitConverter.ToString(File.ReadAllBytes(finalPath)));
                });
        }

        private static void RunScriptInvokesHarnessHeadlessly()
        {
            string runScript = Path.Combine(ToolDirectory, "run.sh");
            string scriptText = File.ReadAllText(runScript);
            AssertContains(
                scriptText,
                "source \"$BIZHAWK_TOOL_DIR/common-env.sh\"");
            AssertContains(scriptText, "unset DISPLAY");
            AssertContains(
                scriptText,
                "exec mono \"$HARNESS_EXE\" \"$@\"");
            AssertEx.Equal(
                false,
                scriptText.IndexOf(
                    "EmuHawk",
                    StringComparison.OrdinalIgnoreCase) >= 0);
            AssertEx.Equal(
                false,
                scriptText.IndexOf(
                    "xvfb",
                    StringComparison.OrdinalIgnoreCase) >= 0);

            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-run-script-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(root);
            try
            {
                string fakeMono = Path.Combine(root, "mono");
                string capturedEnvironment = Path.Combine(root, "display.txt");
                string capturedArguments = Path.Combine(root, "arguments.txt");
                File.WriteAllText(
                    fakeMono,
                    "#!/usr/bin/env bash\n"
                    + "set -euo pipefail\n"
                    + "if [[ -v DISPLAY ]]; then\n"
                    + "  printf 'present\\n' > \"$CAPTURE_ENV\"\n"
                    + "else\n"
                    + "  printf 'absent\\n' > \"$CAPTURE_ENV\"\n"
                    + "fi\n"
                    + "printf '%s\\n' \"$@\" > \"$CAPTURE_ARGS\"\n",
                    new UTF8Encoding(false));
                MakeExecutable(fakeMono);

                var start = new ProcessStartInfo
                {
                    FileName = "/bin/bash",
                    Arguments = Quote(runScript) + " --probe value",
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true
                };
                start.EnvironmentVariables["PATH"] =
                    root + ":" + Environment.GetEnvironmentVariable("PATH");
                start.EnvironmentVariables["BIZHAWK_HOME"] =
                    ResolveBizHawkHome();
                start.EnvironmentVariables["DISPLAY"] = ":99";
                start.EnvironmentVariables["CAPTURE_ENV"] =
                    capturedEnvironment;
                start.EnvironmentVariables["CAPTURE_ARGS"] =
                    capturedArguments;

                ProcessResult result = RunProcess(start);
                AssertEx.Equal(0, result.ExitCode);
                AssertEx.Equal(string.Empty, result.StandardOutput);
                AssertEx.Equal(string.Empty, result.StandardError);
                AssertEx.Equal(
                    "absent\n",
                    NormalizeLf(File.ReadAllText(capturedEnvironment)));
                string[] arguments = File.ReadAllLines(capturedArguments);
                AssertEx.Equal(3, arguments.Length);
                AssertEx.Equal(
                    Path.Combine(
                        ToolDirectory,
                        "bin",
                        "Release",
                        "BizHawk.Headless.Gpgx.exe"),
                    arguments[0]);
                AssertEx.Equal("--probe", arguments[1]);
                AssertEx.Equal("value", arguments[2]);
            }
            finally
            {
                Directory.Delete(root, true);
            }
        }

        private static void ProductionAssemblyExcludesFrontendReferences()
        {
            Assembly production = typeof(NoReplacePublisher).Assembly;
            AssertEx.Equal(
                "BizHawk.Headless.Gpgx",
                production.GetName().Name);

            foreach (AssemblyName reference in
                production.GetReferencedAssemblies())
            {
                string name = reference.Name;
                bool forbidden =
                    string.Equals(
                        name,
                        "BizHawk.Client.Common",
                        StringComparison.Ordinal)
                    || string.Equals(
                        name,
                        "System.Windows.Forms",
                        StringComparison.Ordinal)
                    || name.StartsWith(
                        "BizHawk.Client.",
                        StringComparison.Ordinal)
                    || name.IndexOf(
                        "EmuHawk",
                        StringComparison.OrdinalIgnoreCase) >= 0
                    || name.IndexOf(
                        "Bizware",
                        StringComparison.OrdinalIgnoreCase) >= 0
                    || name.IndexOf(
                        "Graphics",
                        StringComparison.OrdinalIgnoreCase) >= 0
                    || name.IndexOf(
                        "Audio",
                        StringComparison.OrdinalIgnoreCase) >= 0;
                if (forbidden)
                {
                    throw new InvalidOperationException(
                        "Production assembly references forbidden frontend "
                        + "assembly " + name + ".");
                }
            }
        }

        private static void CapturesCanonicalRowsDeterministically()
        {
            string romPath = RequireRomPath();
            string bizHawkHome = RequireBizHawkHome();
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(bizHawkHome);
            AssertEx.Equal(new Version(2, 11, 0, 0), installation.ManagedVersion);
            AssertEx.Equal(
                RomIdentity.Sonic1Rev01Sha1,
                RomIdentity.ValidateSonic1Rev01(File.ReadAllBytes(romPath)));

            string traceDirectory = Path.Combine(
                RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s1",
                "ghz1_fullrun");
            string moviePath = Path.Combine(
                traceDirectory,
                "ghz1_fullrun.bk2");
            string physicsPath = Path.Combine(
                traceDirectory,
                "physics.csv");
            string metadataPath = Path.Combine(
                traceDirectory,
                "metadata.json");

            AssertEx.Equal(
                CanonicalBk2Sha256,
                ComputeSha256(moviePath));
            AssertEx.Equal(
                CanonicalPhysicsSha256,
                ComputeSha256(physicsPath));
            int metadataOffset = ParseMetadataOffset(metadataPath);
            AssertEx.Equal(840, metadataOffset);

            Bk2Movie movie = Bk2Reader.Read(moviePath);
            AssertEx.Equal(4806, movie.FrameCount);

            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-end-to-end-" + Guid.NewGuid().ToString("N"));
            string firstOutput = Path.Combine(root, "first", "nested");
            string secondOutput = Path.Combine(root, "second", "nested");
            try
            {
                string firstStdout = Capture(
                    romPath,
                    bizHawkHome,
                    moviePath,
                    firstOutput,
                    metadataOffset);
                string secondStdout = Capture(
                    romPath,
                    bizHawkHome,
                    moviePath,
                    secondOutput,
                    metadataOffset);
                string firstCsv = Path.Combine(firstOutput, "smoke.csv");
                string secondCsv = Path.Combine(secondOutput, "smoke.csv");

                AssertEx.Equal(
                    ExpectedStdout(movie, metadataOffset, firstCsv),
                    firstStdout);
                AssertEx.Equal(
                    ExpectedStdout(movie, metadataOffset, secondCsv),
                    secondStdout);
                AssertEx.Equal(
                    ComputeSha256(firstCsv),
                    ComputeSha256(secondCsv));

                IDictionary<string, IDictionary<string, string>> canonical =
                    ReadRowsByFrame(physicsPath);
                IDictionary<string, IDictionary<string, string>> native =
                    ReadRowsByFrame(firstCsv);
                AssertCanonicalAndNativeRow(
                    "0000",
                    0,
                    new[] { "0000", "0050", "03B0", "0000", "0000" },
                    metadataOffset,
                    movie,
                    canonical,
                    native);
                AssertCanonicalAndNativeRow(
                    "0001",
                    1,
                    new[] { "0000", "0050", "03B0", "0000", "0000" },
                    metadataOffset,
                    movie,
                    canonical,
                    native);
                AssertCanonicalAndNativeRow(
                    "03E7",
                    999,
                    new[] { "0008", "09A5", "02AA", "0272", "FF80" },
                    metadataOffset,
                    movie,
                    canonical,
                    native);
                AssertEx.Equal(1839, metadataOffset + 999);
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private static string Capture(
            string romPath,
            string bizHawkHome,
            string moviePath,
            string output,
            int metadataOffset)
        {
            var start = new ProcessStartInfo
            {
                FileName = "/bin/bash",
                Arguments =
                    Quote(Path.Combine(ToolDirectory, "run.sh"))
                    + " --rom " + Quote(romPath)
                    + " --movie " + Quote(moviePath)
                    + " --output " + Quote(output)
                    + " --bk2-frame-offset "
                    + metadataOffset.ToString(CultureInfo.InvariantCulture)
                    + " --max-frames "
                    + CaptureFrameCount.ToString(CultureInfo.InvariantCulture),
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            start.EnvironmentVariables["BIZHAWK_HOME"] = bizHawkHome;
            start.EnvironmentVariables["S1_ROM_PATH"] = romPath;
            start.EnvironmentVariables["DISPLAY"] = ":99";
            ProcessResult result = RunProcess(start);
            if (result.ExitCode != 0)
            {
                throw new InvalidOperationException(
                    "Capture exited " + result.ExitCode + ". stderr: "
                    + result.StandardError);
            }
            AssertEx.Equal(string.Empty, result.StandardError);
            return result.StandardOutput;
        }

        private static string ExpectedStdout(
            Bk2Movie movie,
            int metadataOffset,
            string finalPath)
        {
            return
                "BizHawk: 2.11.0.0\n"
                + "ROM SHA-1: "
                + RomIdentity.Sonic1Rev01Sha1
                + "\n"
                + "Movie frames: "
                + movie.FrameCount.ToString(CultureInfo.InvariantCulture)
                + "\n"
                + "Requested trace frames: "
                + CaptureFrameCount.ToString(CultureInfo.InvariantCulture)
                + "\n"
                + "Completed GPGX frames: "
                + (metadataOffset + CaptureFrameCount).ToString(
                    CultureInfo.InvariantCulture)
                + "\n"
                + "Output: "
                + Path.GetFullPath(finalPath)
                + "\n";
        }

        private static void AssertCanonicalAndNativeRow(
            string frame,
            int traceRowIndex,
            string[] exactExpected,
            int metadataOffset,
            Bk2Movie movie,
            IDictionary<string, IDictionary<string, string>> canonical,
            IDictionary<string, IDictionary<string, string>> native)
        {
            IDictionary<string, string> canonicalRow = canonical[frame];
            IDictionary<string, string> nativeRow = native[frame];
            string[] canonicalFields =
            {
                "input",
                "player_x",
                "player_y",
                "player_x_speed",
                "player_y_speed"
            };
            string[] nativeFields =
            {
                "input",
                "x",
                "y",
                "x_velocity",
                "y_velocity"
            };
            for (var index = 0; index < canonicalFields.Length; index++)
            {
                AssertEx.Equal(
                    exactExpected[index],
                    canonicalRow[canonicalFields[index]]);
                AssertEx.Equal(
                    canonicalRow[canonicalFields[index]],
                    nativeRow[nativeFields[index]]);
            }

            Bk2Frame mappedFrame = movie.OpenFrameStream()
                .ElementAt(metadataOffset + traceRowIndex);
            AssertEx.Equal(
                canonicalRow["input"],
                mappedFrame.OpenGgfInputMask.ToString(
                    "X4",
                    CultureInfo.InvariantCulture));
        }

        private static IDictionary<string, IDictionary<string, string>>
            ReadRowsByFrame(string path)
        {
            string[] lines = File.ReadAllLines(path);
            string[] headers = lines[0].Split(',');
            AssertEx.Equal(
                headers.Length,
                headers.Distinct(StringComparer.Ordinal).Count());
            int frameIndex = Array.IndexOf(headers, "frame");
            AssertEx.Equal(true, frameIndex >= 0);
            var rows =
                new Dictionary<string, IDictionary<string, string>>(
                    StringComparer.Ordinal);
            for (var lineIndex = 1; lineIndex < lines.Length; lineIndex++)
            {
                string[] values = lines[lineIndex].Split(',');
                AssertEx.Equal(headers.Length, values.Length);
                var row = new Dictionary<string, string>(
                    StringComparer.Ordinal);
                for (var index = 0; index < headers.Length; index++)
                {
                    row.Add(headers[index], values[index]);
                }
                rows.Add(values[frameIndex], row);
            }
            return rows;
        }

        private static int ParseMetadataOffset(string metadataPath)
        {
            JObject metadata;
            using (var reader = new JsonTextReader(
                new StringReader(File.ReadAllText(metadataPath))))
            {
                metadata = JObject.Load(
                    reader,
                    new JsonLoadSettings
                    {
                        DuplicatePropertyNameHandling =
                            DuplicatePropertyNameHandling.Error
                    });
            }
            JToken offset = metadata["bk2_frame_offset"];
            AssertEx.Equal(true, offset != null);
            AssertEx.Equal(JTokenType.Integer, offset.Type);
            return offset.Value<int>();
        }

        private static string RequireRomPath()
        {
            string romPath =
                Environment.GetEnvironmentVariable("S1_ROM_PATH");
            if (string.IsNullOrEmpty(romPath))
            {
                throw new TestMain.SkipTestException(
                    "S1_ROM_PATH is not set");
            }
            if (!File.Exists(romPath))
            {
                throw new InvalidOperationException(
                    "Supplied S1_ROM_PATH does not exist: " + romPath);
            }
            return Path.GetFullPath(romPath);
        }

        private static string RequireBizHawkHome()
        {
            string supplied =
                Environment.GetEnvironmentVariable("BIZHAWK_HOME");
            if (!string.IsNullOrEmpty(supplied))
            {
                if (!Directory.Exists(supplied))
                {
                    throw new InvalidOperationException(
                        "Supplied BIZHAWK_HOME does not exist: " + supplied);
                }
                return Path.GetFullPath(supplied);
            }

            string fallback = Path.Combine(
                RepositoryRoot,
                "docs",
                "BizHawk-2.11-linux-x64");
            if (!Directory.Exists(fallback))
            {
                throw new TestMain.SkipTestException(
                    "BizHawk distribution is not installed");
            }
            return Path.GetFullPath(fallback);
        }

        private static string ResolveBizHawkHome()
        {
            string supplied =
                Environment.GetEnvironmentVariable("BIZHAWK_HOME");
            return string.IsNullOrEmpty(supplied)
                ? Path.Combine(
                    RepositoryRoot,
                    "docs",
                    "BizHawk-2.11-linux-x64")
                : Path.GetFullPath(supplied);
        }

        private static string[] RequiredArguments(string output)
        {
            return new[]
            {
                "--rom", "game.gen",
                "--movie", "movie.bk2",
                "--output", output
            };
        }

        private static string[] Append(
            string[] source,
            params string[] values)
        {
            return source.Concat(values).ToArray();
        }

        private static void WithUnusedOutput(Action<string> body)
        {
            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-cli-" + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "output");
            try
            {
                body(output);
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private static string ComputeSha256(string path)
        {
            using (FileStream stream = File.OpenRead(path))
            using (SHA256 sha256 = SHA256.Create())
            {
                return BitConverter.ToString(sha256.ComputeHash(stream))
                    .Replace("-", "")
                    .ToLowerInvariant();
            }
        }

        private static void MakeExecutable(string path)
        {
            var start = new ProcessStartInfo
            {
                FileName = "/bin/chmod",
                Arguments = "+x " + Quote(path),
                UseShellExecute = false
            };
            using (Process process = Process.Start(start))
            {
                process.WaitForExit();
                AssertEx.Equal(0, process.ExitCode);
            }
        }

        private static ProcessResult RunProcess(ProcessStartInfo start)
        {
            using (Process process = Process.Start(start))
            {
                string stdout = process.StandardOutput.ReadToEnd();
                string stderr = process.StandardError.ReadToEnd();
                process.WaitForExit();
                return new ProcessResult(
                    process.ExitCode,
                    stdout,
                    stderr);
            }
        }

        private static void AssertContains(
            string value,
            string expectedFragment)
        {
            if (value.IndexOf(
                expectedFragment,
                StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Expected text to contain <" + expectedFragment + ">.");
            }
        }

        private static string Quote(string value)
        {
            return "\"" + value.Replace("\\", "\\\\").Replace("\"", "\\\"")
                + "\"";
        }

        private static string NormalizeLf(string value)
        {
            return value.Replace("\r\n", "\n");
        }

        private static string RepositoryRoot
        {
            get
            {
                return Path.GetFullPath(Path.Combine(
                    AppDomain.CurrentDomain.BaseDirectory,
                    "..",
                    "..",
                    "..",
                    ".."));
            }
        }

        private static string ToolDirectory
        {
            get
            {
                return Path.Combine(
                    RepositoryRoot,
                    "tools",
                    "bizhawk-headless");
            }
        }

        private sealed class ProcessResult
        {
            public ProcessResult(
                int exitCode,
                string standardOutput,
                string standardError)
            {
                ExitCode = exitCode;
                StandardOutput = standardOutput;
                StandardError = standardError;
            }

            public int ExitCode { get; private set; }
            public string StandardOutput { get; private set; }
            public string StandardError { get; private set; }
        }
    }
}
