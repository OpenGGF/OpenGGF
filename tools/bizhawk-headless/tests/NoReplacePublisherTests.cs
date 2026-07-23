using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class NoReplacePublisherTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "Publisher creates missing nested directory",
                CreatesMissingNestedDirectory));
            tests.Add(new TestMain.TestCase(
                "Publisher removes temporary output after writer failure",
                RemovesTemporaryAfterWriterFailure));
            tests.Add(new TestMain.TestCase(
                "Publisher preserves a pre-existing final file",
                PreservesPreExistingFinalFile));
            tests.Add(new TestMain.TestCase(
                "Publisher loses deterministic final-link race without replacement",
                LosesDeterministicFinalLinkRace));
            tests.Add(new TestMain.TestCase(
                "Publisher leaves one UTF-8 LF final file",
                LeavesOneUtf8LfFinalFile));
            tests.Add(new TestMain.TestCase(
                "Publisher ignores temporary cleanup failure after final link",
                IgnoresTemporaryCleanupFailureAfterFinalLink));
        }

        private static void CreatesMissingNestedDirectory()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "missing", "nested");
                    new NoReplacePublisher().Publish(
                        output,
                        writer => writer.WriteLine("created"));

                    AssertEx.Equal(true, Directory.Exists(output));
                    AssertEx.Equal(
                        "created\n",
                        File.ReadAllText(Path.Combine(output, "smoke.csv")));
                });
        }

        private static void RemovesTemporaryAfterWriterFailure()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "failed");
                    AssertEx.Throws<InvalidOperationException>(
                        () => new NoReplacePublisher().Publish(
                            output,
                            writer =>
                            {
                                writer.WriteLine("partial");
                                throw new InvalidOperationException(
                                    "intentional writer failure");
                            }),
                        "intentional writer failure");

                    AssertEx.Equal(true, Directory.Exists(output));
                    AssertEx.Equal(
                        false,
                        File.Exists(Path.Combine(output, "smoke.csv")));
                    AssertEx.Equal(0, Directory.GetFileSystemEntries(output).Length);
                });
        }

        private static void PreservesPreExistingFinalFile()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "existing");
                    string finalPath = Path.Combine(output, "smoke.csv");
                    byte[] original = { 0x00, 0x7F, 0x80, 0xFF };
                    Directory.CreateDirectory(output);
                    File.WriteAllBytes(finalPath, original);

                    AssertEx.Throws<IOException>(
                        () => new NoReplacePublisher().Publish(
                            output,
                            writer => writer.WriteLine("replacement")),
                        "already exists");

                    AssertBytesEqual(original, File.ReadAllBytes(finalPath));
                    AssertOnlyFinalFile(output);
                });
        }

        private static void LosesDeterministicFinalLinkRace()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "race");
                    string finalPath = Path.Combine(output, "smoke.csv");
                    byte[] competing = Encoding.UTF8.GetBytes(
                        "competing-writer\n");
                    var link = new CompetingLinkOperation(competing);

                    AssertEx.Throws<IOException>(
                        () => new NoReplacePublisher(link).Publish(
                            output,
                            writer => writer.WriteLine("losing-writer")),
                        "already exists");

                    AssertEx.Equal(1, link.CreateCount);
                    AssertEx.Equal(
                        Path.GetFullPath(finalPath),
                        link.FinalPath);
                    AssertBytesEqual(competing, File.ReadAllBytes(finalPath));
                    AssertOnlyFinalFile(output);
                });
        }

        private static void LeavesOneUtf8LfFinalFile()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "success");
                    new NoReplacePublisher().Publish(
                        output,
                        writer =>
                        {
                            writer.WriteLine("frame,input");
                            writer.WriteLine("0000,0000");
                        });

                    AssertOnlyFinalFile(output);
                    byte[] bytes = File.ReadAllBytes(
                        Path.Combine(output, "smoke.csv"));
                    AssertBytesEqual(
                        Encoding.UTF8.GetBytes(
                            "frame,input\n0000,0000\n"),
                        bytes);
                    AssertEx.Equal(false, HasUtf8Bom(bytes));
                    AssertEx.Equal(false, bytes.Contains((byte)'\r'));
                });
        }

        private static void IgnoresTemporaryCleanupFailureAfterFinalLink()
        {
            WithTemporaryDirectory(
                root =>
                {
                    string output = Path.Combine(root, "cleanup-failure");
                    var deleteCalls = 0;
                    var publisher = new NoReplacePublisher(
                        LibcLinkOperation.Instance,
                        path =>
                        {
                            deleteCalls++;
                            throw new IOException(
                                "intentional temporary cleanup failure");
                        });

                    publisher.Publish(
                        output,
                        writer => writer.WriteLine("committed"));

                    AssertEx.Equal(1, deleteCalls);
                    AssertEx.Equal(
                        "committed\n",
                        File.ReadAllText(Path.Combine(output, "smoke.csv")));
                });
        }

        private static void AssertOnlyFinalFile(string output)
        {
            string[] entries = Directory.GetFileSystemEntries(output);
            AssertEx.Equal(1, entries.Length);
            AssertEx.Equal(
                "smoke.csv",
                Path.GetFileName(entries[0]));
        }

        private static void AssertBytesEqual(
            byte[] expected,
            byte[] actual)
        {
            AssertEx.Equal(
                BitConverter.ToString(expected),
                BitConverter.ToString(actual));
        }

        private static bool HasUtf8Bom(byte[] bytes)
        {
            return bytes.Length >= 3
                && bytes[0] == 0xEF
                && bytes[1] == 0xBB
                && bytes[2] == 0xBF;
        }

        private static void WithTemporaryDirectory(Action<string> body)
        {
            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-publisher-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(root);
            try
            {
                body(root);
            }
            finally
            {
                Directory.Delete(root, true);
            }
        }

        private sealed class CompetingLinkOperation : ILinkOperation
        {
            private readonly byte[] competing;

            public CompetingLinkOperation(byte[] competing)
            {
                this.competing = competing;
            }

            public int CreateCount { get; private set; }
            public string FinalPath { get; private set; }

            public void Create(string temporary, string finalPath)
            {
                CreateCount++;
                FinalPath = finalPath;
                File.WriteAllBytes(finalPath, competing);
                LibcLinkOperation.Instance.Create(temporary, finalPath);
            }
        }
    }
}
