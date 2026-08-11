using System;

namespace OpenGGF.BizHawk.Headless
{
    public sealed partial class GpgxHost
    {
        internal GpgxAudioObserverAdapter CreateAudioObserverAdapter()
        {
            if (disposed) throw new ObjectDisposedException("GpgxHost");
            return new GpgxAudioObserverAdapter(core);
        }
    }
}
