# Astra Microblocks

Minecraft 26.2 / Fabric prototype for editing a persistent 16×16×16 block with the Astra Chisel.

## Rendering checkpoint (0.1.3)

Carved cells expose actual stone surfaces. `MicroblockRenderMesh` produces only solid-to-air
faces, including the walls of recesses and tunnels. Its exact grid-boundary vertices have
outward winding; the client converts them to block coordinates at 1/16 scale.

The client-only block entity renderer builds an immutable Fabric mesh and reuses it until
the host revision or texture atlas sprite changes. Each host owns a separate cache entry;
unloaded entities can be garbage collected. Extraction captures the mesh before submission,
so rendering never reads a live occupancy grid. The existing block entity update packets
provide edits and undo to clients, including hosts loaded from disk.

The static world cube is disabled. The block is non-occluding with dynamic shapes so
neighboring terrain remains visible through cuts. Collision, targeting, persistence and
undo continue to use the existing common code. Inventory models remain full stone cubes,
and the chisel uses its existing placeholder pickaxe texture.

This first renderer uses one quad per exposed microcell face, a fixed stone material and
block-entity lighting. Cross-host face culling, greedy render-face merging, material palettes
and per-cell ambient occlusion are future work. A solid host currently has 1,536 quads.

## Build and verification

Use Java 25 and Gradle 9.5.1:

```sh
gradle clean build
gradle runClientRenderTest
```

The client test opens a separate development client, waits for assets to load, checks
registration, geometry, UV scale, mesh caching, carve/undo invalidation and submission
(including breaking overlays), then exits. Success requires the log marker
`ASTRA_TEST: CLIENT_RENDER_PASS`; do not rely on the process exit code alone.

GitHub Actions also runs the existing disposable-world lifecycle gate across three server
starts. Phase zero now includes exact render-surface coverage, outward winding, surface and
interior cavities, adjacent cuts, through-holes, deterministic random shapes, immutability
and invalid-input tests. All prior lifecycle assertions remain enabled. CI runs the client
smoke gate under Xvfb and Mesa before uploading the compiled JAR.

## Live visual check

In a disposable creative world, give yourself `astra_microblocks:test_host` and
`astra_microblocks:astra_chisel`. Place a host and carve its top and side faces. Check that
the openings are 1/16 of a block, their interior walls are visible, adjacent cuts join,
and terrain behind a through-hole remains visible. Save/reload the world and reload resource
packs (F3+T). Test two adjacent hosts as well. Undo is currently a data-layer operation
covered by the automated tests; the chisel does not yet bind it to a player control.
The automated smoke gate checks rendering data and submission; this live check is still
needed to judge on-screen appearance and compatibility with other rendering mods.
