using System;
using System.Collections.Generic;
using System.Globalization;
using System.Security.Cryptography;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    public static class LoadQueueStateEvent
    {
        public sealed class Observation
        {
            public Observation(string boundary, int budget)
            {
                Boundary = boundary;
                Budget = budget;
            }

            public string Boundary { get; private set; }
            public int Budget { get; private set; }
        }

        public static string Fingerprint(
            int kindId, uint source, uint destination, int? totalWork)
        {
            if (kindId < 1 || kindId > 4)
            {
                throw new ArgumentOutOfRangeException("kindId");
            }
            var bytes = new byte[totalWork.HasValue ? 19 : 15];
            bytes[0] = (byte)'O';
            bytes[1] = (byte)'Q';
            bytes[2] = (byte)'D';
            bytes[3] = (byte)'F';
            bytes[4] = 1;
            bytes[5] = (byte)kindId;
            WriteU32(bytes, 6, source);
            WriteU32(bytes, 10, destination);
            bytes[14] = (byte)(totalWork.HasValue ? 1 : 0);
            if (totalWork.HasValue)
            {
                WriteU32(bytes, 15, unchecked((uint)totalWork.Value));
            }
            using (var sha = SHA256.Create())
            {
                return Hex(sha.ComputeHash(bytes));
            }
        }

        public static string Format(
            int frame,
            string kind,
            bool busy,
            bool prepared,
            int activeSource,
            int activeDestination,
            int totalWork,
            int remainingWork,
            IList<string> queuedFingerprints,
            IList<Observation> observations)
        {
            if (String.IsNullOrEmpty(kind))
            {
                throw new ArgumentException("kind is required", "kind");
            }
            if (queuedFingerprints == null)
            {
                throw new ArgumentNullException("queuedFingerprints");
            }
            if (observations == null)
            {
                throw new ArgumentNullException("observations");
            }
            if (observations.Count != 0)
            {
                throw new ArgumentException(
                    "version 1 service observations must be empty",
                    "observations");
            }
            var json = new StringBuilder();
            json.Append("{\"frame\":").Append(Dec(frame))
                .Append(",\"event\":\"load_queue_state\",\"kind\":\"")
                .Append(kind)
                .Append("\",\"busy\":").Append(busy ? "true" : "false")
                .Append(",\"prepared\":").Append(prepared ? "true" : "false")
                .Append(",\"active_source\":").Append(Dec(activeSource))
                .Append(",\"active_destination\":").Append(Dec(activeDestination))
                .Append(",\"total_work\":").Append(Dec(totalWork))
                .Append(",\"remaining_work\":").Append(Dec(remainingWork))
                .Append(",\"queued_fingerprints\":[");
            for (var index = 0; index < queuedFingerprints.Count; index++)
            {
                if (index != 0) json.Append(",");
                json.Append("\"").Append(queuedFingerprints[index]).Append("\"");
            }
            json.Append("],\"service_observations\":[");
            return json.Append("]}").ToString();
        }

        private static void WriteU32(byte[] bytes, int offset, uint value)
        {
            bytes[offset] = (byte)(value >> 24);
            bytes[offset + 1] = (byte)(value >> 16);
            bytes[offset + 2] = (byte)(value >> 8);
            bytes[offset + 3] = (byte)value;
        }

        private static string Hex(byte[] bytes)
        {
            var text = new StringBuilder(bytes.Length * 2);
            for (var index = 0; index < bytes.Length; index++)
            {
                text.Append(bytes[index].ToString("x2", CultureInfo.InvariantCulture));
            }
            return text.ToString();
        }

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }
    }
}
