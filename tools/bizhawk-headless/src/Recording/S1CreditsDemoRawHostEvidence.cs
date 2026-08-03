using System;
using System.Collections.Generic;
using System.Globalization;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// One independently observed raw-host value bound to the row and
    /// logical physics payload that later carried it. Task 7 serializes
    /// these records outside the candidate root; Task 3 deliberately keeps
    /// the capture seam in memory so evidence cannot affect trace bytes.
    /// </summary>
    internal sealed class S1CreditsRawHostEvidenceRecord
    {
        internal S1CreditsRawHostEvidenceRecord(
            int demoIndex,
            string candidateDirectory,
            int row,
            string commonField,
            string rawSource,
            string rawValue,
            string emittedValue,
            string candidateLogicalPayloadSha256)
        {
            DemoIndex = demoIndex;
            CandidateDirectory = candidateDirectory;
            Row = row;
            CommonField = commonField;
            RawSource = rawSource;
            RawValue = rawValue;
            EmittedValue = emittedValue;
            CandidateLogicalPayloadSha256 = candidateLogicalPayloadSha256;
        }

        public int DemoIndex { get; private set; }
        public string CandidateDirectory { get; private set; }
        public int Row { get; private set; }
        public string CommonField { get; private set; }
        public string RawSource { get; private set; }
        public string RawValue { get; private set; }
        public string EmittedValue { get; private set; }
        public string CandidateLogicalPayloadSha256 { get; private set; }
    }

    /// <summary>
    /// Reads the 20 predecessor-common values directly from the host before
    /// S1TraceCsvWriter formats the row. Reads and derivations are local to
    /// this observer: they do not reuse writer snapshots or parse writer
    /// output. Verification happens afterwards against a parsed emitted row.
    /// </summary>
    internal sealed class S1CreditsRawHostEvidenceCollector
    {
        private sealed class Observation
        {
            public string Source;
            public string Value;
        }

        private readonly Dictionary<string, Observation> observations =
            new Dictionary<string, Observation>(StringComparer.Ordinal);

        internal void Observe(int demoIndex, int row, IGpgxHost host)
        {
            if (host == null) throw new ArgumentNullException("host");

            byte status = ReadU8(host, S1Ram.PlayerBase + S1Ram.OffStatus);
            byte angle = ReadU8(host, S1Ram.PlayerBase + S1Ram.OffAngle);
            bool air = (status & S1Ram.StatusInAir) != 0;
            bool rolling = (status & S1Ram.StatusRolling) != 0;

            Add(demoIndex, row, "frame", "trace row ordinal", Hex4(row));
            Add(demoIndex, row, "input",
                "$FFF604 u8; direction bits 0-3, A/B/C collapsed to jump, Start excluded",
                Hex4(ControllerMask(ReadU8(host, S1Ram.Ctrl1))));
            AddU16(demoIndex, row, "x", "$FFD008 u16be", host,
                S1Ram.PlayerBase + S1Ram.OffXPos);
            AddU16(demoIndex, row, "y", "$FFD00C u16be", host,
                S1Ram.PlayerBase + S1Ram.OffYPos);
            AddU16(demoIndex, row, "x_speed", "$FFD010 s16be; emitted as u16 bits", host,
                S1Ram.PlayerBase + S1Ram.OffXVel);
            AddU16(demoIndex, row, "y_speed", "$FFD012 s16be; emitted as u16 bits", host,
                S1Ram.PlayerBase + S1Ram.OffYVel);
            AddU16(demoIndex, row, "g_speed", "$FFD014 s16be; emitted as u16 bits", host,
                S1Ram.PlayerBase + S1Ram.OffInertia);
            Add(demoIndex, row, "angle", "$FFD026 u8",
                Hex2(angle));
            Add(demoIndex, row, "air", "$FFD022 status bit 1",
                air ? "1" : "0");
            Add(demoIndex, row, "rolling", "$FFD022 status bit 2",
                rolling ? "1" : "0");
            Add(demoIndex, row, "ground_mode",
                "$FFD022 air bit plus $FFD026 angle thresholds",
                GroundMode(air, angle).ToString(CultureInfo.InvariantCulture));
            AddU16(demoIndex, row, "x_sub", "$FFD00A u16be", host,
                S1Ram.PlayerBase + S1Ram.OffXSub);
            AddU16(demoIndex, row, "y_sub", "$FFD00E u16be", host,
                S1Ram.PlayerBase + S1Ram.OffYSub);
            AddU8(demoIndex, row, "routine", "$FFD024 u8", host,
                S1Ram.PlayerBase + S1Ram.OffRoutine);
            AddU16(demoIndex, row, "camera_x", "$FFF700 u16be", host,
                S1Ram.CameraX);
            AddU16(demoIndex, row, "camera_y", "$FFF704 u16be", host,
                S1Ram.CameraY);
            AddU16(demoIndex, row, "rings", "$FFFE20 u16be", host,
                S1Ram.RingCount);
            Add(demoIndex, row, "status_byte", "$FFD022 u8", Hex2(status));
            AddU16(demoIndex, row, "v_framecount", "$FFFE04 u16be", host,
                S1Ram.FrameCount);
            AddU8(demoIndex, row, "stand_on_obj", "$FFD03D u8", host,
                S1Ram.PlayerBase + S1Ram.OffStandOnObj);
        }

        internal S1CreditsRawHostEvidenceRecord Verify(
            int demoIndex,
            string candidateDirectory,
            int row,
            string commonField,
            string emittedField,
            string emittedRow,
            string candidateLogicalPayloadSha256)
        {
            if (candidateDirectory == null) throw new ArgumentNullException("candidateDirectory");
            if (commonField == null) throw new ArgumentNullException("commonField");
            if (emittedField == null) throw new ArgumentNullException("emittedField");
            if (emittedRow == null) throw new ArgumentNullException("emittedRow");
            if (candidateLogicalPayloadSha256 == null
                || candidateLogicalPayloadSha256.Length != 64)
            {
                throw new ArgumentException(
                    "Candidate logical-payload SHA-256 must contain 64 hex characters.",
                    "candidateLogicalPayloadSha256");
            }

            Observation observation;
            if (!observations.TryGetValue(
                Key(demoIndex, row, commonField), out observation))
            {
                throw new InvalidOperationException(
                    "Missing raw-host observation for credits demo " + demoIndex
                    + " row " + row + " field " + commonField + ".");
            }
            string[] header = S1TraceCsvWriter.Header.Split(',');
            string[] fields = emittedRow.Split(',');
            if (header.Length != fields.Length)
            {
                throw new InvalidOperationException(
                    "Emitted credits row width does not match the v5 header.");
            }
            int column = Array.IndexOf(header, emittedField);
            if (column < 0)
            {
                throw new InvalidOperationException(
                    "Unknown emitted credits field " + emittedField + ".");
            }
            string emittedValue = fields[column];
            if (!String.Equals(observation.Value, emittedValue,
                StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    "Credits raw-host mismatch for demo " + demoIndex
                    + " row " + row + " field " + commonField
                    + ": raw=" + observation.Value
                    + " emitted=" + emittedValue + ".");
            }
            return new S1CreditsRawHostEvidenceRecord(
                demoIndex, candidateDirectory, row, commonField,
                observation.Source, observation.Value, emittedValue,
                candidateLogicalPayloadSha256);
        }

        private void AddU16(
            int demoIndex, int row, string field, string source,
            IGpgxHost host, int address)
        {
            Add(demoIndex, row, field, source, Hex4(ReadU16(host, address)));
        }

        private void AddU8(
            int demoIndex, int row, string field, string source,
            IGpgxHost host, int address)
        {
            Add(demoIndex, row, field, source, Hex2(ReadU8(host, address)));
        }

        private void Add(
            int demoIndex, int row, string field, string source, string value)
        {
            string key = Key(demoIndex, row, field);
            if (observations.ContainsKey(key))
            {
                throw new InvalidOperationException(
                    "Duplicate raw-host observation for credits demo "
                    + demoIndex + " row " + row + " field " + field + ".");
            }
            observations.Add(key, new Observation
            {
                Source = source,
                Value = value
            });
        }

        private static string Key(int demoIndex, int row, string field)
        {
            return demoIndex.ToString(CultureInfo.InvariantCulture) + ":"
                + row.ToString(CultureInfo.InvariantCulture) + ":" + field;
        }

        private static byte ReadU8(IGpgxHost host, int address)
        {
            return host.ReadMainRamByte(address);
        }

        private static ushort ReadU16(IGpgxHost host, int address)
        {
            byte high = host.ReadMainRamByte(address);
            byte low = host.ReadMainRamByte(address + 1);
            return (ushort)((high << 8) | low);
        }

        private static int ControllerMask(byte raw)
        {
            int mask = raw & 0x0F;
            if ((raw & 0x70) != 0) mask |= 0x10;
            return mask;
        }

        private static int GroundMode(bool air, byte angle)
        {
            if (air) return 0;
            if (angle <= 0x1F || angle >= 0xE0) return 0;
            if (angle <= 0x5F) return 1;
            if (angle <= 0x9F) return 2;
            return 3;
        }

        private static string Hex4(int value)
        {
            return value.ToString("X4", CultureInfo.InvariantCulture);
        }

        private static string Hex2(int value)
        {
            return value.ToString("X2", CultureInfo.InvariantCulture);
        }
    }
}
