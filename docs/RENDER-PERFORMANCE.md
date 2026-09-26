# Rendering performance: 0.3.1

Helix Foundry exposed excessive surface submission in the block-entity renderer.
The original mesher emitted one quad per exposed microcell face, including 1,536
quads for a completely solid, single-material block.

The world and sculpture-item renderers now use deterministic greedy rectangles.
They merge only coplanar, contiguous faces with the exact same HostMaterial state.
Block-space UVs at the rectangle corners preserve texture scale, orientation,
mirrored face UVs, and log end grain. Empty cells, cavity walls and material seams
remain exact. Collision, chisel selection, persistence, and history are unchanged.
The original unit-face builder remains the independent topology oracle in tests.

## Exact stress fixture

`tools/fixtures/helix-foundry.litematic` is the generated Astra 0.3.0 test build
that reproduced the user's lag. It is a data fixture, not bundled into the mod.
The dedicated-server gate decodes all 1,111 host volumes and checks that expanded
merged rectangles exactly equal the original exposed unit-face set, with no
holes, duplicates, material-crossing merges or reversed winding.

| Measurement | Before | After |
| --- | ---: | ---: |
| Full single-material block quads | 1,536 | 6 |
| Helix Foundry host mesh quads | 876,178 | 117,391 |

The fixture reduction is 86.6%. These are emitted mesh quads, not measured FPS.
The earlier design estimate of 510,152 faces excluded occupied neighbors across
host boundaries; the current per-host renderer still includes those boundaries.

Additional regression cases cover cavities, tunnels, random mixed-material
volumes, and 2,048 isolated cells (all 12,288 faces must remain). Client tests check
all 250 supported material states as both individual cells and merged full blocks,
including exact atlas UV coordinates, cached mesh invalidation and undo/redo.

## Remaining limits and retest

Meshes are still submitted through block-entity rendering every frame. Neighbor
occlusion, chunk-batched rendering and GPU buffering are separate future work.
The disconnected-cell checkerboards cannot merge and remain intentionally costly.
No reduction in detail, render distance, or material support is used by this fix.

Retest the same pasted Foundry with the Litematica hologram hidden. Compare view
position, graphics settings, frame rate and editing responsiveness against 0.3.0.
Then turn the hologram on and compare separately. Reload the same saved world;
no schematic repaste or data migration is needed.

## 0.3.2: physical shape construction and caching

The September 26 13:53 freeze was recorded by Windows as AppHangB1. Minecraft's
log ended without a Java exception or crash report. This identifies an application
hang, but does not supply the blocked Java stack or prove its cause.

Code inspection found an independent severe hot path: every collision/selection
query copied occupancy, meshed cuboids, joined all of them, then optimized the
result. A checkerboard requires 2,048 disconnected boxes. The new implementation
fills Minecraft's BitSetDiscreteVoxelShape directly (at most 4,096 cells), wraps it
in CubeVoxelShape and caches it on each host. Exact occupancy comparison detects
edits, undo/redo and reloads even if a loaded revision happens to match. Material
changes alone reuse the physical shape. Empty/full shapes retain their fast paths.

The dense-shape regression reconstructs all 2,048 cells from the returned boxes,
checks ray and collision boundaries, runs 10,000 cache-hit queries, and verifies
invalidation after cutting and undo. World lifecycle and client render tests remain
required. A repeat in-game test is still necessary: this removes a known expensive
path, but absent a hang stack trace it cannot be claimed as the proven sole cause.
