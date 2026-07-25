using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Recorded per-test wall clock, used only to schedule the slowest
    /// work first. It is a hint and nothing else: a stale, partial or
    /// absent file changes the order tests start in and never whether one
    /// runs, passes or fails.
    ///
    /// The format is one <c>seconds TAB name</c> line per test, sorted
    /// slowest first so the file reads as its own report. Names may
    /// contain anything except a tab or a newline, which the runner's own
    /// names already satisfy.
    /// </summary>
    internal static class TestTimings
    {
        private const string DefaultFileName = "test-timings.tsv";

        /// <summary>
        /// Where the timings live when <c>--timings</c> did not say:
        /// beside the test sources, resolved from the assembly location
        /// (<c>bin/Release/</c>) rather than the working directory, so the
        /// path holds however the runner was invoked.
        /// </summary>
        internal static string DefaultPath
        {
            get
            {
                return Path.GetFullPath(Path.Combine(
                    AppDomain.CurrentDomain.BaseDirectory,
                    "..",
                    "..",
                    "tests",
                    DefaultFileName));
            }
        }

        /// <summary>
        /// Reads the file if it is there. A missing or unreadable file is
        /// an empty map, not an error — the run then falls back to the
        /// static per-test estimates.
        /// </summary>
        internal static Dictionary<string, double> Load(string path)
        {
            var timings = new Dictionary<string, double>(
                StringComparer.Ordinal);
            if (path == null || !File.Exists(path))
            {
                return timings;
            }

            string[] lines;
            try
            {
                lines = File.ReadAllLines(path);
            }
            catch (IOException)
            {
                return timings;
            }

            foreach (string line in lines)
            {
                if (line.Length == 0 || line[0] == '#')
                {
                    continue;
                }

                int tab = line.IndexOf('\t');
                if (tab <= 0 || tab + 1 >= line.Length)
                {
                    continue;
                }

                double seconds;
                if (!double.TryParse(
                        line.Substring(0, tab),
                        NumberStyles.Float,
                        CultureInfo.InvariantCulture,
                        out seconds)
                    || seconds < 0.0)
                {
                    continue;
                }

                timings[line.Substring(tab + 1)] = seconds;
            }

            return timings;
        }

        /// <summary>
        /// Merges this run's measurements over whatever the file already
        /// held and rewrites it. Entries for tests this run did not
        /// execute survive, so a filtered run updates its own slice
        /// instead of truncating the file to it.
        /// </summary>
        internal static void Save(
            string path,
            Dictionary<string, double> existing,
            IEnumerable<KeyValuePair<string, double>> measured)
        {
            var merged = new Dictionary<string, double>(
                existing, StringComparer.Ordinal);
            foreach (KeyValuePair<string, double> entry in measured)
            {
                merged[entry.Key] = entry.Value;
            }

            var ordered = new List<KeyValuePair<string, double>>(merged);
            ordered.Sort(delegate(
                KeyValuePair<string, double> left,
                KeyValuePair<string, double> right)
            {
                int byDuration = right.Value.CompareTo(left.Value);
                return byDuration != 0
                    ? byDuration
                    : string.CompareOrdinal(left.Key, right.Key);
            });

            var text = new StringBuilder();
            text.Append(
                "# Recorded test wall clock, seconds TAB name, slowest"
                + " first.\n");
            text.Append(
                "# Scheduling hint only; regenerate with --update-timings."
                + "\n");
            foreach (KeyValuePair<string, double> entry in ordered)
            {
                text.Append(
                    entry.Value.ToString("F3", CultureInfo.InvariantCulture));
                text.Append('\t');
                text.Append(entry.Key);
                text.Append('\n');
            }

            string directory = Path.GetDirectoryName(path);
            if (!string.IsNullOrEmpty(directory))
            {
                Directory.CreateDirectory(directory);
            }

            File.WriteAllText(path, text.ToString(), new UTF8Encoding(false));
        }
    }
}
