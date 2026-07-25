using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Command line for the test runner. Every selector is combinable and
    /// they intersect: a test runs only when it satisfies all of them.
    ///
    /// The two contracts callers depend on are preserved exactly:
    /// <c>--jobs 1</c> executes in registration order with unbuffered
    /// output, and the exit codes stay 0 (all passed), 1 (a failure) and 2
    /// (nothing matched). A malformed command line is the one new code,
    /// <see cref="UsageExitCode"/>, chosen so that "your flags are wrong"
    /// is distinguishable from "your selection was empty".
    /// </summary>
    internal sealed class TestOptions
    {
        /// <summary>
        /// Default worker count. Comfortably inside the 32 cores of the
        /// development box, and well above the point where more workers
        /// stop helping: one gate is ~6 minutes on its own and sets the
        /// floor for a full run. Memory is not a consideration — a
        /// capture is a flat ~231 MB whatever the movie length.
        /// </summary>
        internal const int DefaultJobs = 8;

        /// <summary>
        /// Number of entries in the slowest-test report when a parallel
        /// run does not say otherwise.
        /// </summary>
        internal const int DefaultSlowest = 10;

        /// <summary>
        /// Exit code for a malformed command line. Distinct from 2, which
        /// stays reserved for a well-formed selection that matched
        /// nothing.
        /// </summary>
        internal const int UsageExitCode = 3;

        private TestOptions()
        {
            Jobs = DefaultJobs;
            Slowest = -1;
        }

        public string Filter { get; private set; }

        public string Game { get; private set; }

        public string Movie { get; private set; }

        public int Jobs { get; private set; }

        public bool GatesOnly { get; private set; }

        public bool NoGates { get; private set; }

        public bool UpdateTimings { get; private set; }

        public string TimingsPath { get; private set; }

        public bool ShowHelp { get; private set; }

        /// <summary>
        /// Requested slowest-report size, or -1 when the flag was absent.
        /// Use <see cref="ResolvedSlowest"/> rather than reading this.
        /// </summary>
        private int Slowest { get; set; }

        /// <summary>
        /// How many slowest entries to print. A serial run prints none
        /// unless asked, because <c>--jobs 1</c> must reproduce the
        /// pre-parallel output byte for byte.
        /// </summary>
        public int ResolvedSlowest
        {
            get
            {
                if (Slowest >= 0)
                {
                    return Slowest;
                }

                return Jobs > 1 ? DefaultSlowest : 0;
            }
        }

        internal static string Usage
        {
            get
            {
                var usage = new StringBuilder();
                usage.AppendLine(
                    "Usage: BizHawk.Headless.Gpgx.Tests.exe [options]");
                usage.AppendLine();
                usage.AppendLine("Selection (combinable; all must match):");
                usage.AppendLine(
                    "  --filter <substr>   Case-insensitive substring of"
                    + " the test name.");
                usage.AppendLine(
                    "  --game s1|s2|s3k    Tests tagged with that game."
                    + " Untagged tests are EXCLUDED.");
                usage.AppendLine(
                    "  --movie <substr>    Tests replaying a matching BK2"
                    + " movie. Untagged tests are EXCLUDED.");
                usage.AppendLine(
                    "  --gates-only        Only ROM-backed differential"
                    + " gates.");
                usage.AppendLine(
                    "  --no-gates          Everything except the gates"
                    + " (the fast iteration tier).");
                usage.AppendLine();
                usage.AppendLine("Execution:");
                usage.AppendLine(
                    "  --jobs <n>          Worker threads (default "
                    + DefaultJobs.ToString(CultureInfo.InvariantCulture)
                    + "). 1 = sequential, registration order.");
                usage.AppendLine(
                    "  --slowest <n>       Slowest-test report size (0"
                    + " disables; default "
                    + DefaultSlowest.ToString(CultureInfo.InvariantCulture)
                    + " when --jobs > 1, else 0).");
                usage.AppendLine(
                    "  --timings <path>    Recorded timings file"
                    + " (default tests/test-timings.tsv).");
                usage.AppendLine(
                    "  --update-timings    Rewrite the timings file from"
                    + " this run's passing tests.");
                usage.AppendLine(
                    "  --help              Print this text.");
                usage.AppendLine();
                usage.AppendLine(
                    "Exit codes: 0 all passed, 1 a test failed, 2 nothing"
                    + " matched, "
                    + UsageExitCode.ToString(CultureInfo.InvariantCulture)
                    + " bad command line.");
                return usage.ToString().TrimEnd('\n');
            }
        }

        internal static bool TryParse(
            string[] args,
            out TestOptions options,
            out string error)
        {
            options = null;
            error = null;
            var parsed = new TestOptions();
            var seen = new HashSet<string>(StringComparer.Ordinal);

            for (var i = 0; i < args.Length; i++)
            {
                string argument = args[i];
                if (argument == "--help" || argument == "-h")
                {
                    parsed.ShowHelp = true;
                    continue;
                }

                if (!argument.StartsWith("--", StringComparison.Ordinal))
                {
                    error = "Unexpected argument: " + argument;
                    return false;
                }

                if (!seen.Add(argument))
                {
                    error = "Duplicate argument: " + argument;
                    return false;
                }

                switch (argument)
                {
                    case "--gates-only":
                        parsed.GatesOnly = true;
                        continue;
                    case "--no-gates":
                        parsed.NoGates = true;
                        continue;
                    case "--update-timings":
                        parsed.UpdateTimings = true;
                        continue;
                }

                if (i + 1 >= args.Length)
                {
                    error = "Missing value for " + argument;
                    return false;
                }

                string value = args[++i];
                switch (argument)
                {
                    case "--filter":
                        parsed.Filter = value;
                        break;
                    case "--game":
                        if (!IsKnownGame(value))
                        {
                            error = "Unknown --game value: " + value
                                + " (expected s1, s2 or s3k)";
                            return false;
                        }

                        parsed.Game = value.ToLowerInvariant();
                        break;
                    case "--movie":
                        parsed.Movie = value;
                        break;
                    case "--timings":
                        parsed.TimingsPath = value;
                        break;
                    case "--jobs":
                        if (!TryParseCount(value, 1, out int jobs))
                        {
                            error = "--jobs must be a positive integer: "
                                + value;
                            return false;
                        }

                        parsed.Jobs = jobs;
                        break;
                    case "--slowest":
                        if (!TryParseCount(value, 0, out int slowest))
                        {
                            error = "--slowest must be a non-negative"
                                + " integer: " + value;
                            return false;
                        }

                        parsed.Slowest = slowest;
                        break;
                    default:
                        error = "Unknown argument: " + argument;
                        return false;
                }
            }

            if (parsed.GatesOnly && parsed.NoGates)
            {
                error = "--gates-only and --no-gates are mutually"
                    + " exclusive.";
                return false;
            }

            options = parsed;
            return true;
        }

        /// <summary>
        /// True when the test satisfies every selector that was supplied.
        /// Each selector reads exactly one metadata dimension, so a test
        /// that leaves an unrelated dimension untagged is never dropped
        /// by a selector that does not name it.
        /// </summary>
        public bool Matches(TestMain.TestCase test)
        {
            if (Filter != null
                && test.Name.IndexOf(
                    Filter, StringComparison.OrdinalIgnoreCase) < 0)
            {
                return false;
            }

            if (Game != null
                && !string.Equals(
                    test.Game, Game, StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }

            if (Movie != null
                && (test.Movie == null
                    || test.Movie.IndexOf(
                        Movie, StringComparison.OrdinalIgnoreCase) < 0))
            {
                return false;
            }

            if (GatesOnly && test.Kind != TestKind.Gate)
            {
                return false;
            }

            if (NoGates && test.Kind == TestKind.Gate)
            {
                return false;
            }

            return true;
        }

        /// <summary>
        /// How the empty-selection message names what was asked for. With
        /// only <c>--filter</c> supplied this is the filter string itself,
        /// which keeps the pre-existing "No tests matched filter: x"
        /// message byte-identical.
        /// </summary>
        public string DescribeSelection()
        {
            var parts = new List<string>();
            if (Filter != null)
            {
                parts.Add("--filter " + Filter);
            }

            if (Game != null)
            {
                parts.Add("--game " + Game);
            }

            if (Movie != null)
            {
                parts.Add("--movie " + Movie);
            }

            if (GatesOnly)
            {
                parts.Add("--gates-only");
            }

            if (NoGates)
            {
                parts.Add("--no-gates");
            }

            if (parts.Count == 1 && Filter != null)
            {
                return Filter;
            }

            if (parts.Count == 0)
            {
                return "(none)";
            }

            return string.Join(" ", parts.ToArray());
        }

        private static bool IsKnownGame(string value)
        {
            return string.Equals(value, "s1", StringComparison.OrdinalIgnoreCase)
                || string.Equals(value, "s2", StringComparison.OrdinalIgnoreCase)
                || string.Equals(
                    value, "s3k", StringComparison.OrdinalIgnoreCase);
        }

        private static bool TryParseCount(
            string value,
            int minimum,
            out int parsed)
        {
            return int.TryParse(
                       value,
                       NumberStyles.None,
                       CultureInfo.InvariantCulture,
                       out parsed)
                   && parsed >= minimum;
        }
    }
}
