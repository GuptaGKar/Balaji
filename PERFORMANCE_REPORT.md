# Performance report

2026-11-02 · **NOT TESTED for controlled before/after optimization**.

Device: Samsung SM-A346E, Android 16/API 36, 1080×2340, density 450. QA debug and minified builds 1.12 (13), local Supabase through wireless ADB reverse; Firebase disabled. No measured performance optimization was made.

| Metric                                | Before                                                                                                                    | After                    | Result                                            |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ------------------------ | ------------------------------------------------- |
| Cold start                            | One launch: AM TotalTime 1093 ms, WaitTime 1134 ms, notification permission activity; later cold Home launch 1233/1236 ms | No controlled comparison | Observations only; destinations/session differ    |
| Warm start/time to interactive        | Not collected                                                                                                             | Not collected            | NOT TESTED                                        |
| Frames/scroll/recomposition           | One uncontrolled minified snapshot below                                                                                  | No comparable run        | Controlled benchmark NOT TESTED                   |
| Memory/GC/leaks                       | PSS/RSS snapshot below                                                                                                    | No comparable run        | Leak/controlled memory study NOT TESTED           |
| Battery/CPU/wakeups                   | Not collected                                                                                                             | Not collected            | NOT TESTED                                        |
| Mobile API latency                    | Catalogue HTTP 200 responses observed at 44/51 ms, retry 77/80 ms over local tunnel                                       | No comparable run        | Diagnostic samples, not a benchmark               |
| Concurrent checkout request durations | Three local two-request races: [300,301], [54,32], [68,42] ms                                                             | No optimization          | Concurrency evidence only, not production latency |

The cold-launch samples are not a distribution or an improvement claim. Phone thermal/power/background conditions were not controlled; local host builds and Docker were active. No production network performance was measured.

Next controlled run: repeated cold/warm launches to the same authenticated screen; median/tail and interactive timings; fixed scroll route with gfxinfo; repeated navigation with heap snapshots; network request counts and lifecycle recovery. Repeat identical conditions after any proposed optimization. Fixed splash delay and tracking polls remain source observations, not measured bottlenecks.

Additional uncontrolled launch observations after the catalogue fix: QA debug 1568 ms TotalTime/1581 ms WaitTime; minified QA 543/555 ms. Different build types/cache/process conditions make these unsuitable for a causal before/after optimization claim. Both launches reached a functional catalogue; no statistically controlled benchmark was run.

## Executed minified QA diagnostic snapshot

One uncontrolled snapshot after customer checkout/history and staff acceptance/preparation/ready actions. Customer was backgrounded; staff was foregrounded. Same Samsung device, wireless loopback backend; no gfxinfo reset or fixed scroll workload.

| App         | Total PSS (KiB) | RSS (KiB) | Swap PSS (KiB) | Rendered / janky frames | p50 / p90 / p95 / p99 frame times |
| ----------- | --------------: | --------: | -------------: | ----------------------- | --------------------------------- |
| Customer QA |          49,737 |   118,808 |         40,686 | 1353 / 23 (1.70%)       | 11 / 17 / 29 / 117 ms             |
| Staff QA    |         108,011 |   214,351 |          4,146 | 1339 / 35 (2.61%)       | 10 / 29 / 53 / 150 ms             |

These are gfxinfo totals since the sampled process started, including launches/dialogs; they are not a controlled scrolling or recomposition benchmark. The background customer's swapped memory must not be interpreted as zero heap use or an optimization. No before/after memory, leak or CPU/battery conclusion follows. Current-process crash buffers contained no FATAL EXCEPTION at sampling time; this is not complete crash/ANR certification. Sanitized numeric evidence is in /tmp/balaji-device-metrics.json (ephemeral).

The diagnostic snapshot predates the staff preview/create-only-upload repairs. Later isolated staff cold launches were 558/565 ms (preview revision) and593/602 ms (upload revision), AM TotalTime/WaitTime. These remain uncontrolled observations; no before/after comparison, CPU attribution or improvement claim is justified. No performance implementation was applied without measurements.
