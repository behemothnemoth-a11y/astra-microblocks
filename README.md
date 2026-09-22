# Astra Microblocks

Minecraft 26.2 / Fabric prototype for editing a persistent 16×16×16 block with the Astra Chisel.

## Precision testing tools (0.1.5)

Hold the chisel in your **main hand** to use the new controls:

- Press **G** to open the mode menu. Click a mode to select it directly. Rebind
  **Open chisel mode menu** under **Astra Microblocks** in Minecraft's Controls if needed.
  Selection changes the held tool on the server; opening or closing the menu makes no cut.
- Aim at a host to see an amber preview of the occupied cells the next cut will remove.
  Empty cells are excluded. The highlight shows the full cut depth through the host.
- The top-left readout shows mode, occupied cells out of 4,096, and the next cut's cell count.
  Crouching hides the cut preview and labels the action as undo. F1 hides the inspector.
- The original crouch-right-click mode cycle and undo controls still work.
- `/astra mode single`, `line_x`, `line_y`, `line_z`, `plane`, `cube_2`, `cube_4`, or `cube_8`
  provides the same direct selection if another mod conflicts with the menu key.

The preview shares the cut's hit resolver and selection rules, intersects the selection
with the current occupied cells, and caches merged highlight boxes until the target or
block revision changes. It does not change block data, collision or world render meshes.
Counts reflect the client's latest received block state; a concurrent server edit can change
what the eventual click removes.

### Detailed live test

1. Select each mode from G and confirm the HUD and tooltip agree.
2. On a full host, check counts: Single 1, Line 16, Plane 256, cubes 8 / 64 / 512.
3. Aim at all six faces, cell boundaries, and interior cavity walls. Compare the amber
   highlight with the cut, then undo and check that the whole region returns.
4. Aim a larger brush over an existing hole. The hole must stay unhighlighted and the
   count must exclude its empty cells. Neighboring hosts must remain untouched.
5. Move the crosshair away, change tools, crouch, open the menu, and toggle F1. Check that
   stale cut highlights disappear. Rebind G and confirm the readout shows the new key.
6. Save and reopen the world. Confirm shape and selected mode survive; check the updated
   preview, collision on narrow ledges, and undo. Repeat after F3+T resource reload.

Automated gates verify preview coverage against real cuts on full, empty, patterned and
random grids; immutable snapshots; command registration and held-tool validation; highlight
coordinates and visibility; and mode-menu layout/extraction at two GUI sizes. Existing
lifecycle and renderer gates remain enabled. Live appearance and other-mod compatibility
still need the in-game check above. Bit placement and deeper history are future work.

## Chisel modes (0.1.4)

The chisel is available in the creative **Tools & Utilities** tab; the sculptable host is
in **Building Blocks**. Existing chisels default to Single cell. New chisels are unstackable.

- Right-click a host to cut with the current mode.
- Crouch + right-click **air** to cycle modes. Aim away from blocks. The mode appears above
  the hotbar and in the chisel tooltip, and is saved on the tool.
- Crouch + right-click a host to undo its last cut, including after saving/reloading.
- If the host is completely empty, hold the same chisel nearby and run `/astra undo`.
  This restores that tool's last cut only if it is still the block's latest edit and within
  normal interaction reach. The command does not require operator privileges.

| Mode | Removed region per click |
| --- | --- |
| Single cell | One 1/16 cell |
| Line X | 16 cells east/west through the clicked cell |
| Line Y | 16 cells vertically through the clicked cell |
| Line Z | 16 cells north/south through the clicked cell |
| Plane | One 16×16 layer parallel to the clicked face, one cell thick |
| 2×2×2 | An aligned 1/8-block cube (8 cells) |
| 4×4×4 | An aligned 1/4-block cube (64 cells) |
| 8×8×8 | An aligned 1/2-block cube (512 cells) |

Modes cycle in the table's order. Larger cubes snap to subdivisions of the host grid;
for example the 4×4×4 tool targets cell coordinates 0–3, 4–7, 8–11, or 12–15 on each axis.
Every cut stays inside the clicked host. Empty cells are skipped. Each successful click
publishes one revision and keeps one whole-cut undo snapshot; an empty cut preserves undo.
Undo is one level per host, shared between players. The black outline shows the remaining
host shape; the amber preview shows the region the next cut will remove.

The mode test gate covers all six faces, boundaries, exact selected cells, actual item
controls, item save round trips, batch persistence/undo, partial brushes and empty-host undo.
The client smoke gate also checks mesh rebuilding, caching and undo for every mode.

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
packs (F3+T). Test two adjacent hosts as well. Try each tool mode and crouch-right-click
to undo a cut. Verify that the entire cut returns in one step.
The automated smoke gate checks rendering data and submission; this live check is still
needed to judge on-screen appearance and compatibility with other rendering mods.
