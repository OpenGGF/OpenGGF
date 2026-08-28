using System;
using System.Collections.Generic;
using System.Reflection;
using System.Runtime.InteropServices;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class GpgxAudioTraceNativeTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "GpgxAudioTraceNativeTests expose the frozen departure boundary",
                ExposesFrozenDepartureBoundary,
                serial: true));
        }

        private static void ExposesFrozenDepartureBoundary()
        {
            Type api = typeof(GpgxHost).Assembly.GetType(
                "OpenGGF.BizHawk.Headless.IGpgxAudioTraceApi", false);
            Type native = typeof(GpgxHost).Assembly.GetType(
                "OpenGGF.BizHawk.Headless.GpgxAudioTraceNative", false);
            Type traceEvent = typeof(GpgxHost).Assembly.GetType(
                "OpenGGF.BizHawk.Headless.GpgxAudioTraceEvent", false);
            AssertEx.Equal(true, api != null);
            AssertEx.Equal(true, native != null);
            AssertEx.Equal(true, traceEvent != null);
            AssertEx.Equal(32, Marshal.SizeOf(traceEvent));
            foreach (string method in new[] { "Configure", "BeginFrame", "EndFrame",
                "EventCount", "Drain", "AbortFrame", "Disable" })
            {
                AssertEx.Equal(true, api.GetMethod(method) != null);
            }
            foreach (string property in new[] { "AbiVersion", "EventSize", "Capacity" })
            {
                AssertEx.Equal(true, api.GetProperty(property) != null);
            }
        }
    }
}
