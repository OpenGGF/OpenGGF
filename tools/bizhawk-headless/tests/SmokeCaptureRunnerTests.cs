using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class SmokeCaptureRunnerTests
    {
        private const string LogKey =
            "LogKey:#Power|Reset|"
            + "#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
            + "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "SmokeCaptureRunner warms up then records post-advance UTF-8",
                WarmsUpThenRecordsPostAdvanceUtf8));
            tests.Add(new TestMain.TestCase(
                "SmokeCaptureRunner applies system and P1 buttons in order",
                AppliesSystemAndP1ButtonsInOrder));
            tests.Add(new TestMain.TestCase(
                "SmokeCaptureRunner rejects invalid ranges before capture",
                RejectsInvalidRangesBeforeCapture));
            tests.Add(new TestMain.TestCase(
                "SmokeCaptureRunner rejects movie exhaustion before capture",
                RejectsMovieExhaustionBeforeCapture));
            tests.Add(new TestMain.TestCase(
                "SmokeCaptureRunner rejects completed-frame drift",
                RejectsCompletedFrameDrift));
        }

        private static void WarmsUpThenRecordsPostAdvanceUtf8()
        {
            WithMovie(
                new[]
                {
                    "|..|........|........|",
                    "|..|........|........|",
                    "|..|...R.B..|........|"
                },
                movie =>
                {
                    var host = new RecordingHost();
                    byte[] bytes;
                    using (var stream = new MemoryStream())
                    {
                        using (var writer = new StreamWriter(
                            stream,
                            new UTF8Encoding(false),
                            1024,
                            true))
                        {
                            SmokeCaptureRunner.Capture(
                                movie,
                                host,
                                2,
                                1,
                                writer);
                        }
                        bytes = stream.ToArray();
                    }

                    AssertEx.Equal(3, host.AdvanceCount);
                    AssertEx.Equal(3, host.CompletedFrame);
                    AssertEx.Equal(
                        true,
                        host.CompletedFramesAtRead.All(frame => frame == 3));
                    AssertEx.Equal(
                        "frame,input,x,y,x_velocity,y_velocity\n"
                        + "0000,0028,0003,1003,2003,F003\n",
                        Encoding.UTF8.GetString(bytes));
                    AssertEx.Equal(false, HasUtf8Bom(bytes));
                    AssertEx.Equal(false, bytes.Contains((byte)'\r'));
                    AssertEx.Equal((byte)'\n', bytes[bytes.Length - 1]);
                    AssertEx.Equal(
                        false,
                        bytes.Length > 1
                        && bytes[bytes.Length - 2] == (byte)'\n');
                });
        }

        private static void AppliesSystemAndP1ButtonsInOrder()
        {
            WithMovie(
                new[]
                {
                    "|P.|UDLRABCS|........|",
                    "|.R|.D...B..|........|"
                },
                movie =>
                {
                    var host = new RecordingHost();
                    SmokeCaptureRunner.Capture(
                        movie,
                        host,
                        0,
                        2,
                        new StringWriter());

                    string[] expected =
                    {
                        "ClearButtons",
                        "SetButton(\"Power\", true)",
                        "SetButton(\"P1 Up\", true)",
                        "SetButton(\"P1 Down\", true)",
                        "SetButton(\"P1 Left\", true)",
                        "SetButton(\"P1 Right\", true)",
                        "SetButton(\"P1 A\", true)",
                        "SetButton(\"P1 B\", true)",
                        "SetButton(\"P1 C\", true)",
                        "SetButton(\"P1 Start\", true)",
                        "Advance",
                        "ReadMainRamByte(D008)",
                        "ReadMainRamByte(D009)",
                        "ReadMainRamByte(D00C)",
                        "ReadMainRamByte(D00D)",
                        "ReadMainRamByte(D010)",
                        "ReadMainRamByte(D011)",
                        "ReadMainRamByte(D012)",
                        "ReadMainRamByte(D013)",
                        "ClearButtons",
                        "SetButton(\"Reset\", true)",
                        "SetButton(\"P1 Down\", true)",
                        "SetButton(\"P1 B\", true)",
                        "Advance",
                        "ReadMainRamByte(D008)",
                        "ReadMainRamByte(D009)",
                        "ReadMainRamByte(D00C)",
                        "ReadMainRamByte(D00D)",
                        "ReadMainRamByte(D010)",
                        "ReadMainRamByte(D011)",
                        "ReadMainRamByte(D012)",
                        "ReadMainRamByte(D013)"
                    };
                    AssertEx.Equal(
                        string.Join("\n", expected),
                        string.Join("\n", host.Calls));
                });
        }

        private static void RejectsInvalidRangesBeforeCapture()
        {
            WithMovie(
                new[] { "|..|........|........|" },
                movie =>
                {
                    AssertRangeFailure(movie, -1, 1, "bk2FrameOffset");
                    AssertRangeFailure(movie, 0, 0, "maxFrames");
                    AssertRangeFailure(movie, 0, 1001, "maxFrames");
                });
        }

        private static void RejectsMovieExhaustionBeforeCapture()
        {
            WithMovie(
                new[]
                {
                    "|..|........|........|",
                    "|..|........|........|",
                    "|..|........|........|"
                },
                movie =>
                {
                    var host = new RecordingHost();
                    var output = new StringWriter();
                    AssertEx.Throws<InvalidDataException>(
                        () => SmokeCaptureRunner.Capture(
                            movie,
                            host,
                            2,
                            2,
                            output),
                        "4");
                    AssertEx.Equal(0, host.Calls.Count);
                    AssertEx.Equal(string.Empty, output.ToString());
                });
        }

        private static void RejectsCompletedFrameDrift()
        {
            WithMovie(
                new[] { "|..|........|........|" },
                movie =>
                {
                    var host = new RecordingHost(2);
                    AssertEx.Throws<InvalidOperationException>(
                        () => SmokeCaptureRunner.Capture(
                            movie,
                            host,
                            0,
                            1,
                            new StringWriter()),
                        "CompletedFrame");
                    AssertEx.Equal(0, host.CompletedFramesAtRead.Count);
                });
        }

        private static void AssertRangeFailure(
            Bk2Movie movie,
            int offset,
            int count,
            string messageFragment)
        {
            var host = new RecordingHost();
            var output = new StringWriter();
            AssertEx.Throws<ArgumentOutOfRangeException>(
                () => SmokeCaptureRunner.Capture(
                    movie,
                    host,
                    offset,
                    count,
                    output),
                messageFragment);
            AssertEx.Equal(0, host.Calls.Count);
            AssertEx.Equal(string.Empty, output.ToString());
        }

        private static bool HasUtf8Bom(byte[] bytes)
        {
            return bytes.Length >= 3
                && bytes[0] == 0xEF
                && bytes[1] == 0xBB
                && bytes[2] == 0xBF;
        }

        private static void WithMovie(
            IEnumerable<string> rows,
            Action<Bk2Movie> body)
        {
            string directory = Path.Combine(
                Path.GetTempPath(),
                "openggf-smoke-" + Guid.NewGuid().ToString("N"));
            string path = Path.Combine(directory, "synthetic.bk2");
            Directory.CreateDirectory(directory);
            try
            {
                using (var stream = File.Create(path))
                using (var archive = new ZipArchive(
                    stream,
                    ZipArchiveMode.Create,
                    false))
                {
                    WriteEntry(
                        archive,
                        "Header.txt",
                        Fixture("ghz1-header.txt"));
                    WriteEntry(
                        archive,
                        "SyncSettings.json",
                        Fixture("ghz1-sync-settings.json"));
                    WriteEntry(
                        archive,
                        "Input Log.txt",
                        "[Input]\r\n"
                        + LogKey + "\r\n"
                        + string.Join("\r\n", rows)
                        + "\r\n[/Input]\r\n");
                }
                body(Bk2Reader.Read(path));
            }
            finally
            {
                Directory.Delete(directory, true);
            }
        }

        private static void WriteEntry(
            ZipArchive archive,
            string name,
            string content)
        {
            ZipArchiveEntry entry =
                archive.CreateEntry(name, CompressionLevel.NoCompression);
            using (Stream stream = entry.Open())
            using (var writer = new StreamWriter(
                stream,
                new UTF8Encoding(false)))
            {
                writer.Write(content);
            }
        }

        private static string Fixture(string name)
        {
            return File.ReadAllText(Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory,
                "fixtures",
                name));
        }

        private sealed class RecordingHost : IGpgxHost
        {
            private readonly byte[] ram = new byte[65536];
            private readonly int completedFramesPerAdvance;

            public RecordingHost(int completedFramesPerAdvance = 1)
            {
                this.completedFramesPerAdvance = completedFramesPerAdvance;
                Calls = new List<string>();
                CompletedFramesAtRead = new List<int>();
            }

            public int CompletedFrame { get; private set; }
            public int AdvanceCount { get; private set; }
            public List<string> Calls { get; private set; }
            public List<int> CompletedFramesAtRead { get; private set; }

            public void ClearButtons()
            {
                Calls.Add("ClearButtons");
            }

            public void SetButton(string name, bool pressed)
            {
                Calls.Add(
                    "SetButton(\"" + name + "\", "
                    + pressed.ToString().ToLowerInvariant() + ")");
            }

            public void Advance()
            {
                Calls.Add("Advance");
                AdvanceCount++;
                CompletedFrame += completedFramesPerAdvance;
                WriteWord(0xD008, (ushort)CompletedFrame);
                WriteWord(0xD00C, (ushort)(0x1000 + CompletedFrame));
                WriteWord(0xD010, (ushort)(0x2000 + CompletedFrame));
                WriteWord(0xD012, (ushort)(0xF000 + CompletedFrame));
            }

            public byte ReadMainRamByte(int offset)
            {
                Calls.Add("ReadMainRamByte(" + offset.ToString("X4") + ")");
                CompletedFramesAtRead.Add(CompletedFrame);
                return ram[offset];
            }

            public void Dispose()
            {
            }

            private void WriteWord(int offset, ushort value)
            {
                ram[offset] = (byte)(value >> 8);
                ram[offset + 1] = (byte)value;
            }
        }
    }
}
