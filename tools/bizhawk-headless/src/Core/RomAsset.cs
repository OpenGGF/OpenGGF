using System;
using System.IO;
using BizHawk.Emulation.Common;
using BizHawk.Emulation.Cores;

namespace OpenGGF.BizHawk.Headless
{
    internal sealed class RomAsset : IRomAsset
    {
        public RomAsset(byte[] romData, string romPath, GameInfo game)
        {
            RomData = romData ?? throw new ArgumentNullException("romData");
            RomPath = romPath ?? throw new ArgumentNullException("romPath");
            Game = game ?? throw new ArgumentNullException("game");
            FileData = RomData;
            Extension = Path.GetExtension(romPath).TrimStart('.');
        }

        public byte[] RomData { get; private set; }
        public byte[] FileData { get; private set; }
        public string Extension { get; private set; }
        public string RomPath { get; private set; }
        public GameInfo Game { get; private set; }
    }
}
