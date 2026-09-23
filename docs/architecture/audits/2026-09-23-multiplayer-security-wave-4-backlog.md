# Multiplayer security wave 4 backlog

Wave 3 addresses the seven concrete findings carried from wave 2. The
following lower-priority design questions remain for the next read-only audit;
they are not claims of a fresh exploit or authorization bypass.

| Question | Why it remains | Next audit probe |
| --- | --- | --- |
| Prove predeadline completion during finish grace | The host can keep an already-active attempt open for the advertised two-second finish transit window, but the current finish message has no trusted completion timestamp. Arrival time alone cannot distinguish predeadline completion from completion during grace. | Trace normal client frame and finish ordering around the deadline. Evaluate a host-observed finished frame or other authenticated protocol evidence without trusting a client-supplied clock; assess compatibility and verified-room behavior before changing the wire format. |
| Correlate asynchronous master replies without sacrificing availability | FIFO tombstones prevent a timed-out request's late response from completing a later request. If no response to the earlier request arrives at all, the next response may be dropped conservatively. | Exercise timeout A/no A reply/request B/B reply for each master request type. Evaluate explicit request IDs across client and broker, including old-client compatibility and bounded pending state. |
| Identity paths under attacker-writable parents | Atomic private file creation closes the original umask window, but a separate same-host threat model should ask whether a hostile process can swap a parent directory between path checks and file creation. | Test under a shared writable parent with controlled rename/symlink races; decide whether ownership checks, `SecureDirectoryStream`, or refusing such locations is the supported boundary. |
| Older v2 browser presentation of list throttling | A pre-wave-3 client can decode the new negative-page-count rate-limit sentinel as an empty successful list, showing “No rooms found” or “Selected room closed” until refresh. Its page controls clamp the value, so review found no crash; this is a compatibility/UX issue rather than a new authorization bypass. | Test old and new clients against the updated broker; decide whether to version the protocol or provide an explicitly backward-compatible rejection during the next protocol review. |

Revisit these after the wave-3 integration is verified, alongside a fresh
threat-model pass of transport close/cleanup ordering and relay admission. Do
not promote a hypothesis to a fix solely from this list.
