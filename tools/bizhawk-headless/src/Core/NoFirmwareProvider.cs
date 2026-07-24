using System;
using BizHawk.Emulation.Common;

namespace OpenGGF.BizHawk.Headless
{
    internal sealed class NoFirmwareProvider : ICoreFileProvider
    {
        public byte[] GetFirmware(FirmwareID id, string msg = null)
        {
            throw Unavailable(id, msg);
        }

        public byte[] GetFirmwareOrThrow(FirmwareID id, string msg = null)
        {
            throw Unavailable(id, msg);
        }

        public (byte[] FW, GameInfo Game) GetFirmwareWithGameInfoOrThrow(
            FirmwareID id,
            string msg = null)
        {
            throw Unavailable(id, msg);
        }

        public string GetRetroSaveRAMDirectory(IGameInfo game)
        {
            throw new InvalidOperationException(
                "Retro save RAM paths are unavailable in the headless GPGX host.");
        }

        public string GetRetroSystemPath(IGameInfo game)
        {
            throw new InvalidOperationException(
                "Retro system paths are unavailable in the headless GPGX host.");
        }

        public string GetUserPath(string sysID, bool temp)
        {
            throw new InvalidOperationException(
                "User paths are unavailable in the headless GPGX host.");
        }

        private static InvalidOperationException Unavailable(
            FirmwareID id,
            string message)
        {
            return new InvalidOperationException(
                "Firmware " + id + " is unavailable in the headless GPGX host"
                + (string.IsNullOrEmpty(message) ? "." : ": " + message));
        }
    }
}
