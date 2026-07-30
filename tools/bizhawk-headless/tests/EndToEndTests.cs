using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;
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
                "Cli rejects a dangling final-output symlink before host construction",
                CliRejectsDanglingFinalOutputBeforeHostConstruction));
            tests.Add(new TestMain.TestCase(
                "Cli restores native descriptors after partial failures",
                NativeDescriptorSilencerRestoresAfterPartialFailures));
            tests.Add(new TestMain.TestCase(
                "Cli host disposal failure leaves no final output",
                CliHostDisposalFailureLeavesNoFinalOutput));
            tests.Add(new TestMain.TestCase(
                "Cli descriptor restore failure leaves no final output",
                CliDescriptorRestoreFailureLeavesNoFinalOutput));
            tests.Add(new TestMain.TestCase(
                "Cli reporting failure leaves no final output",
                CliReportingFailureLeavesNoFinalOutput));
            tests.Add(new TestMain.TestCase(
                "Cli publication is the final non-failing commit point",
                CliPublicationIsFinalNonFailingCommitPoint));
            tests.Add(new TestMain.TestCase(
                "Cli run script invokes only harness Mono with DISPLAY absent",
                RunScriptInvokesHarnessHeadlessly));
            tests.Add(new TestMain.TestCase(
                "EndToEnd shell validates an explicit ROM before missing BizHawk skip",
                TestScriptValidatesExplicitRomBeforeMissingBizHawkSkip));
            tests.Add(new TestMain.TestCase(
                "EndToEnd dependency checks validate present inputs before skipping",
                DependencyChecksValidatePresentInputsBeforeSkipping));
            tests.Add(new TestMain.TestCase(
                "EndToEnd child runner drains both output pipes concurrently",
                ChildRunnerDrainsBothPipesConcurrently));
            tests.Add(new TestMain.TestCase(
                "EndToEnd child runner kills a timed-out process",
                ChildRunnerKillsTimedOutProcess));
            tests.Add(new TestMain.TestCase(
                "EndToEnd production assembly excludes frontend references",
                ProductionAssemblyExcludesFrontendReferences));
            // The only ROM-backed capture outside the Differential
            // classes: it replays 1000 GHZ1 frames and hashes the rows.
            tests.Add(new TestMain.TestCase(
                "EndToEnd",
                CapturesCanonicalRowsDeterministically,
                game: "s1",
                movie: "ghz1_fullrun",
                kind: TestKind.Gate,
                estimatedSeconds: 2.0));
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

        private static void CliRejectsDanglingFinalOutputBeforeHostConstruction()
        {
            WithUnusedOutput(
                output =>
                {
                    Directory.CreateDirectory(output);
                    string finalPath = Path.Combine(output, "smoke.csv");
                    CreateSymbolicLink(
                        Path.Combine(output, "missing-target.csv"),
                        finalPath);
                    var hostConstructionCount = 0;
                    var stdout = new StringWriter(CultureInfo.InvariantCulture);
                    var stderr = new StringWriter(CultureInfo.InvariantCulture);

                    int exitCode = Program.Run(
                        RequiredArguments(output),
                        stdout,
                        stderr,
                        (romPath, syncSettings) =>
                        {
                            hostConstructionCount++;
                            return null;
                        });

                    AssertEx.Equal(1, exitCode);
                    AssertEx.Equal(0, hostConstructionCount);
                    AssertContains(stderr.ToString(), "already exists");
                    AssertEx.Equal(true, LinuxPathEntry.Exists(finalPath));
                });
        }

        private static void NativeDescriptorSilencerRestoresAfterPartialFailures()
        {
            var setupFailure = new FakeNativeDescriptorApi();
            setupFailure.FailRedirectCall = 2;
            AssertEx.Throws<IOException>(
                () => new NativeStandardOutputSilencer(setupFailure),
                "suppress");
            AssertEx.Equal(1, setupFailure.DescriptorTarget(1));
            AssertEx.Equal(2, setupFailure.DescriptorTarget(2));
            AssertEx.Equal(true, setupFailure.AllOpenedDescriptorsClosed);

            var restoreFailure = new FakeNativeDescriptorApi();
            NativeStandardOutputSilencer silencer =
                new NativeStandardOutputSilencer(restoreFailure);
            restoreFailure.FailRedirectCall = 3;
            AssertEx.Throws<IOException>(
                () => silencer.Dispose(),
                "restore");
            AssertEx.Equal(1, restoreFailure.DescriptorTarget(1));
            AssertEx.Equal(2, restoreFailure.DescriptorTarget(2));
            AssertEx.Equal(true, restoreFailure.AllOpenedDescriptorsClosed);
        }

        private static void CliHostDisposalFailureLeavesNoFinalOutput()
        {
            WithUnusedOutput(
                output =>
                {
                    var silencerDisposed = false;
                    var stdout = new StringWriter(CultureInfo.InvariantCulture);
                    var stderr = new StringWriter(CultureInfo.InvariantCulture);

                    int exitCode = Program.RunCapture(
                        output,
                        stdout,
                        stderr,
                        () => new CallbackDisposable(
                            () => silencerDisposed = true),
                        () => new LifecycleHost(
                            () => { throw new IOException("host disposal"); }),
                        (host, writer) => writer.WriteLine("staged"),
                        completed => "success\n",
                        new NoReplacePublisher());

                    AssertEx.Equal(1, exitCode);
                    AssertEx.Equal(true, silencerDisposed);
                    AssertEx.Equal(
                        false,
                        LinuxPathEntry.Exists(Path.Combine(output, "smoke.csv")));
                    AssertContains(stderr.ToString(), "host disposal");
                });
        }

        private static void CliDescriptorRestoreFailureLeavesNoFinalOutput()
        {
            WithUnusedOutput(
                output =>
                {
                    var stderr = new StringWriter(CultureInfo.InvariantCulture);

                    int exitCode = Program.RunCapture(
                        output,
                        new StringWriter(CultureInfo.InvariantCulture),
                        stderr,
                        () => new CallbackDisposable(
                            () =>
                            {
                                throw new IOException("descriptor restore");
                            }),
                        () => new LifecycleHost(null),
                        (host, writer) => writer.WriteLine("staged"),
                        completed => "success\n",
                        new NoReplacePublisher());

                    AssertEx.Equal(1, exitCode);
                    AssertEx.Equal(
                        false,
                        LinuxPathEntry.Exists(Path.Combine(output, "smoke.csv")));
                    AssertContains(stderr.ToString(), "descriptor restore");
                });
        }

        private static void CliReportingFailureLeavesNoFinalOutput()
        {
            WithUnusedOutput(
                output =>
                {
                    var stderr = new StringWriter(CultureInfo.InvariantCulture);

                    int exitCode = Program.RunCapture(
                        output,
                        new FailingFlushWriter(),
                        stderr,
                        () => new CallbackDisposable(null),
                        () => new LifecycleHost(null),
                        (host, writer) => writer.WriteLine("staged"),
                        completed => "success\n",
                        new NoReplacePublisher());

                    AssertEx.Equal(1, exitCode);
                    AssertEx.Equal(
                        false,
                        LinuxPathEntry.Exists(Path.Combine(output, "smoke.csv")));
                    AssertContains(stderr.ToString(), "stdout flush");
                });
        }

        private static void CliPublicationIsFinalNonFailingCommitPoint()
        {
            WithUnusedOutput(
                output =>
                {
                    var hostDisposed = false;
                    var silencerDisposed = false;
                    var stdout = new TrackingWriter();
                    var link = new OrderedLinkOperation(
                        () => hostDisposed
                            && silencerDisposed
                            && stdout.WasFlushed);
                    var publisher = new NoReplacePublisher(
                        link,
                        path =>
                        {
                            throw new IOException("post-link cleanup");
                        });

                    int exitCode = Program.RunCapture(
                        output,
                        stdout,
                        new StringWriter(CultureInfo.InvariantCulture),
                        () => new CallbackDisposable(
                            () => silencerDisposed = true),
                        () => new LifecycleHost(
                            () => hostDisposed = true),
                        (host, writer) => writer.WriteLine("staged"),
                        completed => "exact success\n",
                        publisher);

                    AssertEx.Equal(0, exitCode);
                    AssertEx.Equal(true, link.Created);
                    AssertEx.Equal("exact success\n", stdout.ToString());
                    AssertEx.Equal(
                        "staged\n",
                        File.ReadAllText(Path.Combine(output, "smoke.csv")));
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

        private static void TestScriptValidatesExplicitRomBeforeMissingBizHawkSkip()
        {
            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-test-script-" + Guid.NewGuid().ToString("N"));
            string toolDirectory = Path.Combine(
                root,
                "tools",
                "bizhawk-headless");
            Directory.CreateDirectory(toolDirectory);
            try
            {
                string copiedScript = Path.Combine(toolDirectory, "test.sh");
                File.Copy(
                    Path.Combine(ToolDirectory, "test.sh"),
                    copiedScript);
                string invalidRom = Path.Combine(root, "invalid.gen");
                File.WriteAllText(
                    invalidRom,
                    "not the canonical Sonic 1 ROM",
                    new UTF8Encoding(false));
                var start = new ProcessStartInfo
                {
                    FileName = "/bin/bash",
                    Arguments = Quote(copiedScript) + " --filter EndToEnd",
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true
                };
                start.EnvironmentVariables.Remove("BIZHAWK_HOME");
                start.EnvironmentVariables["S1_ROM_PATH"] = invalidRom;

                ProcessResult result = RunProcess(start, 5000);

                AssertEx.Equal(1, result.ExitCode);
                AssertContains(result.StandardError, "S1_ROM_PATH SHA-1");
                AssertContains(
                    result.StandardError,
                    RomIdentity.Sonic1Rev01Sha1);
                AssertEx.Equal(
                    false,
                    result.StandardOutput.IndexOf(
                        "SKIP",
                        StringComparison.Ordinal) >= 0);
            }
            finally
            {
                Directory.Delete(root, true);
            }
        }

        private static void DependencyChecksValidatePresentInputsBeforeSkipping()
        {
            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-dependencies-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(root);
            try
            {
                string rom = Path.Combine(root, "supplied.gen");
                File.WriteAllBytes(rom, new byte[] { 1, 2, 3 });
                string bizHawk = Path.Combine(root, "supplied-bizhawk");
                Directory.CreateDirectory(bizHawk);
                string missingFallback = Path.Combine(root, "missing-bizhawk");
                var romValidations = 0;
                var bizHawkValidations = 0;

                AssertEx.Throws<InvalidOperationException>(
                    () => ResolveEndToEndDependencies(
                        rom,
                        null,
                        missingFallback,
                        path =>
                        {
                            romValidations++;
                            throw new InvalidOperationException(
                                "invalid ROM identity");
                        },
                        path => bizHawkValidations++),
                    "invalid ROM identity");
                AssertEx.Equal(1, romValidations);
                AssertEx.Equal(0, bizHawkValidations);

                AssertEx.Throws<InvalidOperationException>(
                    () => ResolveEndToEndDependencies(
                        null,
                        bizHawk,
                        missingFallback,
                        path => romValidations++,
                        path =>
                        {
                            bizHawkValidations++;
                            throw new InvalidOperationException(
                                "invalid BizHawk layout");
                        }),
                    "invalid BizHawk layout");
                AssertEx.Equal(1, romValidations);
                AssertEx.Equal(1, bizHawkValidations);

                romValidations = 0;
                bizHawkValidations = 0;
                AssertEx.Throws<InvalidOperationException>(
                    () => ResolveEndToEndDependencies(
                        rom,
                        bizHawk,
                        missingFallback,
                        path =>
                        {
                            romValidations++;
                            throw new InvalidOperationException("bad ROM");
                        },
                        path =>
                        {
                            bizHawkValidations++;
                            throw new InvalidOperationException("bad BizHawk");
                        }),
                    "bad ROM");
                AssertEx.Equal(1, romValidations);
                AssertEx.Equal(1, bizHawkValidations);

                romValidations = 0;
                AssertEx.Throws<TestMain.SkipTestException>(
                    () => ResolveEndToEndDependencies(
                        rom,
                        null,
                        missingFallback,
                        path => romValidations++,
                        path => { }),
                    "BizHawk");
                AssertEx.Equal(1, romValidations);
            }
            finally
            {
                Directory.Delete(root, true);
            }
        }

        private static void ChildRunnerDrainsBothPipesConcurrently()
        {
            var start = new ProcessStartInfo
            {
                FileName = "/bin/bash",
                Arguments = "-c " + Quote(
                    "for i in {1..20000}; do "
                    + "printf 'stderr-%05d-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\\n' "
                    + "\"$i\" >&2; done; printf 'stdout-complete\\n'"),
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };

            ProcessResult result = RunProcess(start, 5000);

            AssertEx.Equal(0, result.ExitCode);
            AssertEx.Equal("stdout-complete\n", result.StandardOutput);
            AssertContains(result.StandardError, "stderr-20000-");
        }

        private static void ChildRunnerKillsTimedOutProcess()
        {
            var start = new ProcessStartInfo
            {
                FileName = "/bin/bash",
                Arguments = "-c " + Quote("sleep 30"),
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };

            AssertEx.Throws<TimeoutException>(
                () => RunProcess(start, 100),
                "100");
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
            EndToEndDependencies dependencies =
                ResolveEndToEndDependencies(
                    Environment.GetEnvironmentVariable("S1_ROM_PATH"),
                    Environment.GetEnvironmentVariable("BIZHAWK_HOME"),
                    Path.Combine(
                        RepositoryRoot,
                        "docs",
                        "BizHawk-2.11-linux-x64"),
                    path => RomIdentity.ValidateSonic1Rev01(
                        File.ReadAllBytes(path)),
                    path => BizHawkInstallation.Validate(path));
            string romPath = dependencies.RomPath;
            string bizHawkHome = dependencies.BizHawkHome;
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
                "physics.csv.gz");
            string metadataPath = Path.Combine(
                traceDirectory,
                "metadata.json");

            AssertEx.Equal(
                CanonicalBk2Sha256,
                ComputeSha256(moviePath));
            AssertEx.Equal(
                CanonicalPhysicsSha256,
                ComputeGzipPayloadSha256(physicsPath));
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
                AssertEx.Equal(
                    CaptureFrameCount + 1,
                    File.ReadLines(firstCsv).Count());

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
            var lineList = new List<string>();
            using (FileStream source = File.OpenRead(path))
            using (Stream payload = path.EndsWith(
                ".gz", StringComparison.Ordinal)
                    ? (Stream)new GZipStream(
                        source, CompressionMode.Decompress)
                    : source)
            using (var reader = new StreamReader(payload))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    lineList.Add(line);
                }
            }
            string[] lines = lineList.ToArray();
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

        internal static EndToEndDependencies ResolveEndToEndDependencies(
            string suppliedRomPath,
            string suppliedBizHawkHome,
            string fallbackBizHawkHome,
            Action<string> validateRom,
            Action<string> validateBizHawk)
        {
            var failures = new List<string>();
            string romPath = null;
            string bizHawkHome = null;
            bool romMissing = string.IsNullOrEmpty(suppliedRomPath);
            bool bizHawkMissing = false;

            if (!romMissing)
            {
                romPath = Path.GetFullPath(suppliedRomPath);
                if (!File.Exists(romPath))
                {
                    failures.Add(
                        "Supplied S1_ROM_PATH does not exist: "
                        + romPath + ".");
                }
                else
                {
                    TryValidate(
                        "S1_ROM_PATH",
                        romPath,
                        validateRom,
                        failures);
                }
            }

            if (!string.IsNullOrEmpty(suppliedBizHawkHome))
            {
                bizHawkHome = Path.GetFullPath(suppliedBizHawkHome);
                if (!Directory.Exists(bizHawkHome))
                {
                    failures.Add(
                        "Supplied BIZHAWK_HOME does not exist: "
                        + bizHawkHome + ".");
                }
                else
                {
                    TryValidate(
                        "BIZHAWK_HOME",
                        bizHawkHome,
                        validateBizHawk,
                        failures);
                }
            }
            else if (Directory.Exists(fallbackBizHawkHome))
            {
                bizHawkHome = Path.GetFullPath(fallbackBizHawkHome);
                TryValidate(
                    "BizHawk fallback",
                    bizHawkHome,
                    validateBizHawk,
                    failures);
            }
            else
            {
                bizHawkMissing = true;
            }

            if (failures.Count != 0)
            {
                throw new InvalidOperationException(
                    string.Join(" ", failures.ToArray()));
            }
            if (romMissing || bizHawkMissing)
            {
                var missing = new List<string>();
                if (romMissing)
                {
                    missing.Add("S1_ROM_PATH is not set");
                }
                if (bizHawkMissing)
                {
                    missing.Add("BizHawk distribution is not installed");
                }
                throw new TestMain.SkipTestException(
                    string.Join("; ", missing.ToArray()));
            }

            return new EndToEndDependencies(romPath, bizHawkHome);
        }

        private static void TryValidate(
            string name,
            string path,
            Action<string> validate,
            ICollection<string> failures)
        {
            try
            {
                validate(path);
            }
            catch (Exception exception)
            {
                failures.Add(name + " is invalid: " + exception.Message);
            }
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

        internal static string ComputeSha256(string path)
        {
            using (FileStream stream = File.OpenRead(path))
            using (SHA256 sha256 = SHA256.Create())
            {
                return BitConverter.ToString(sha256.ComputeHash(stream))
                    .Replace("-", "")
                    .ToLowerInvariant();
            }
        }

        private static string ComputeGzipPayloadSha256(string path)
        {
            using (FileStream stream = File.OpenRead(path))
            using (var gzip = new GZipStream(
                stream, CompressionMode.Decompress))
            using (SHA256 sha256 = SHA256.Create())
            {
                return BitConverter.ToString(sha256.ComputeHash(gzip))
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
            return RunProcess(start, 120000);
        }

        internal static ProcessResult RunProcess(
            ProcessStartInfo start,
            int timeoutMilliseconds)
        {
            using (Process process = Process.Start(start))
            {
                Task<string> stdout =
                    process.StandardOutput.ReadToEndAsync();
                Task<string> stderr =
                    process.StandardError.ReadToEndAsync();
                if (!process.WaitForExit(timeoutMilliseconds))
                {
                    try
                    {
                        process.Kill();
                    }
                    finally
                    {
                        process.WaitForExit();
                        Task.WaitAll(stdout, stderr);
                    }
                    throw new TimeoutException(
                        "Child process exceeded "
                        + timeoutMilliseconds.ToString(
                            CultureInfo.InvariantCulture)
                        + " ms and was killed.");
                }
                process.WaitForExit();
                Task.WaitAll(stdout, stderr);
                return new ProcessResult(
                    process.ExitCode,
                    stdout.Result,
                    stderr.Result);
            }
        }

        private static void CreateSymbolicLink(
            string target,
            string linkPath)
        {
            var start = new ProcessStartInfo
            {
                FileName = "/bin/ln",
                Arguments = "-s " + Quote(target) + " " + Quote(linkPath),
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            ProcessResult result = RunProcess(start, 5000);
            if (result.ExitCode != 0)
            {
                throw new InvalidOperationException(
                    "Unable to create test symlink: "
                    + result.StandardError);
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

        /// <summary>
        /// Every ROM-backed gate captures into a temp directory and compares
        /// RAW payload bytes against a fixture, so it opts out of the
        /// publish-time compression default. That default exists for
        /// captures that get INSTALLED as fixtures and therefore committed —
        /// a full complete-run aux stream is past GitHub's per-file limit
        /// uncompressed — and gate output is never committed, so nothing
        /// here depends on it.
        /// </summary>
        internal const string NoCompressArgument = " --no-compress";

        internal static string Quote(string value)
        {
            return "\"" + value.Replace("\\", "\\\\").Replace("\"", "\\\"")
                + "\"";
        }

        private static string NormalizeLf(string value)
        {
            return value.Replace("\r\n", "\n");
        }

        internal static string RepositoryRoot
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

        internal static string ToolDirectory
        {
            get
            {
                return Path.Combine(
                    RepositoryRoot,
                    "tools",
                    "bizhawk-headless");
            }
        }

        internal sealed class ProcessResult
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

        internal sealed class EndToEndDependencies
        {
            public EndToEndDependencies(
                string romPath,
                string bizHawkHome)
            {
                RomPath = romPath;
                BizHawkHome = bizHawkHome;
            }

            public string RomPath { get; private set; }
            public string BizHawkHome { get; private set; }
        }

        private sealed class CallbackDisposable : IDisposable
        {
            private readonly Action dispose;

            public CallbackDisposable(Action dispose)
            {
                this.dispose = dispose;
            }

            public void Dispose()
            {
                if (dispose != null)
                {
                    dispose();
                }
            }
        }

        private sealed class LifecycleHost : IGpgxHost
        {
            private readonly Action dispose;

            public LifecycleHost(Action dispose)
            {
                this.dispose = dispose;
            }

            public int CompletedFrame
            {
                get { return 7; }
            }

            public bool IsLagged
            {
                get { return false; }
            }

            public int LagCount
            {
                get { return 0; }
            }

            public void ClearButtons()
            {
            }

            public void SetButton(string name, bool pressed)
            {
            }

            public IDisposable RegisterExecuteCallback(
                uint address, Action callback)
            {
                return NoOpCallbackRegistration.Instance;
            }

            public void Advance()
            {
            }

            public byte ReadMainRamByte(int offset)
            {
                return 0;
            }

            public void Dispose()
            {
                if (dispose != null)
                {
                    dispose();
                }
            }
        }

        private sealed class FailingFlushWriter : StringWriter
        {
            public override void Flush()
            {
                throw new IOException("stdout flush");
            }
        }

        private sealed class TrackingWriter : StringWriter
        {
            public bool WasFlushed { get; private set; }

            public override void Flush()
            {
                WasFlushed = true;
                base.Flush();
            }
        }

        private sealed class OrderedLinkOperation : ILinkOperation
        {
            private readonly Func<bool> prerequisitesComplete;

            public OrderedLinkOperation(Func<bool> prerequisitesComplete)
            {
                this.prerequisitesComplete = prerequisitesComplete;
            }

            public bool Created { get; private set; }

            public void Create(string temporary, string finalPath)
            {
                if (!prerequisitesComplete())
                {
                    throw new InvalidOperationException(
                        "Publication preceded fallible lifecycle work.");
                }
                LibcLinkOperation.Instance.Create(temporary, finalPath);
                Created = true;
            }
        }

        private sealed class FakeNativeDescriptorApi
            : INativeDescriptorApi
        {
            private readonly IDictionary<int, int> descriptors =
                new Dictionary<int, int>
                {
                    { 1, 1 },
                    { 2, 2 }
                };
            private readonly ISet<int> opened = new HashSet<int>();
            private readonly ISet<int> closed = new HashSet<int>();
            private int nextDescriptor = 10;
            private int redirectCallCount;

            public int FailRedirectCall { get; set; }

            public bool AllOpenedDescriptorsClosed
            {
                get { return opened.SetEquals(closed); }
            }

            public int DescriptorTarget(int descriptor)
            {
                return descriptors[descriptor];
            }

            public int Duplicate(int descriptor)
            {
                int duplicate = nextDescriptor++;
                descriptors.Add(duplicate, descriptors[descriptor]);
                opened.Add(duplicate);
                return duplicate;
            }

            public int OpenNull()
            {
                int descriptor = nextDescriptor++;
                descriptors.Add(descriptor, -1);
                opened.Add(descriptor);
                return descriptor;
            }

            public int Redirect(int oldDescriptor, int newDescriptor)
            {
                redirectCallCount++;
                if (redirectCallCount == FailRedirectCall)
                {
                    return -1;
                }
                descriptors[newDescriptor] = descriptors[oldDescriptor];
                return newDescriptor;
            }

            public int Close(int descriptor)
            {
                closed.Add(descriptor);
                descriptors.Remove(descriptor);
                return 0;
            }

            public int FlushAll()
            {
                return 0;
            }
        }
    }
}
