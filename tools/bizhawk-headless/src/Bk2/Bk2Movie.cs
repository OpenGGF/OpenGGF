using System.Collections.Generic;
using BizHawk.Emulation.Cores.Consoles.Sega.gpgx;

namespace OpenGGF.BizHawk.Headless
{
    public sealed class Bk2Movie
    {
        private readonly string path;

        internal Bk2Movie(string path)
        {
            this.path = path;
        }

        public string MovieVersion { get; internal set; }
        public string Author { get; internal set; }
        public string Core { get; internal set; }
        public string Platform { get; internal set; }
        public string EmulatorVersion { get; internal set; }
        public string OriginalEmulatorVersion { get; internal set; }
        public string GameName { get; internal set; }
        public string Sha1 { get; internal set; }
        public int FrameCount { get; internal set; }
        public GPGX.GPGXSyncSettings SyncSettings { get; internal set; }

        public IEnumerable<Bk2Frame> OpenFrameStream()
        {
            return Bk2Reader.OpenFrameStream(path);
        }
    }
}
