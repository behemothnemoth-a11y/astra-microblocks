# Safe carved-light repair, 0.6.2

0.6.2 is a maintenance follow-up to 0.6.1 recessed microcell lighting. The emission policy and saved sculpture format are unchanged: any remaining luminous vanilla material contributes its normal default-state block light, and the host emits the maximum level present.

The load-repair path no longer schedules a block tick while block entities are still attaching to a deserializing chunk. Those early schedules produced `Trying to schedule tick in not loaded position` errors during persistence tests and could force unsafe chunk access.

Instead, block-entity load/attachment only queues the host position. A server end-of-level-tick hook drains that queue after chunk loading is safe, checks that the chunk is loaded, then recomputes the derived `light` block state. Normal edits, undo and redo still update light immediately.

The lifecycle harness now starts on the first end-of-server-tick rather than `SERVER_STARTED`, so its world queries happen after startup chunk work has settled.

## Regression coverage

The lighting test deliberately saves a luminous sculpture with a stale `light=0` state. On the next boot the deferred repair must restore level 15 before persistence assertions run. CI also fails if the lifecycle logs contain the old unloaded-position scheduling error or a server-watchdog failure.

The Kitchen and Recessed Lighting showroom remains the visual test target. 0.6.2 changes load safety, not fixture geometry or appearance.
