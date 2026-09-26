# Astra Microblocks

Minecraft 26.2 / Fabric prototype for editing a persistent 16×16×16 block with the Astra Chisel.

## Dense collision and selection shapes (0.3.2)

Physical shapes now use a fixed voxel lattice and are cached while occupancy is
unchanged. This removes repeated cuboid unions and optimization from collision and
selection queries, particularly expensive on disconnected microcells. Saved data,
exact collision boundaries, carving and rendering remain compatible with 0.3.1.

## Surface rendering optimization (0.3.1)

Contiguous faces with the same material now merge into larger rectangles while
preserving all carved cells and texture coordinates. A solid block uses six quads
instead of 1,536. The Helix Foundry regression schematic drops from 876,178 to
117,391 mesh quads (86.6% fewer); this is a geometry measurement, not an FPS claim.
Existing worlds and schematics need no conversion or repaste.

See [rendering performance](docs/RENDER-PERFORMANCE.md) for the exact fixture,
validation and remaining rendering limits.

## Expanded material library (0.3.0)

**164 vanilla blocks / 250 supported block states** can now be sculpted and mixed inside
one Astra block. Place a supported vanilla block and right-click it with the chisel:
Astra converts it and applies your brush while preserving its material and log axis.
The first cut can be undone back to the full material-filled host. The chisel preview
also works before conversion. Unsupported blocks remain unchanged.

Press **B** with the chisel for the searchable **Material Library**, or choose Materials
in G. Each entry has a preview and full-name tooltip. Search names, families or log axes;
Recent recalls the last eight sampled/library materials. Select a material and use Add
or Replace as before. **P** now samples either a supported vanilla block or an exact
sculpted cell. Original restores the sculpture's original material, including its axis.

The library includes masonry, deepslate/tuff variants, sandstone, quartz, blackstone,
planks, overworld logs/wood and stripped variants, bamboo building blocks, concrete,
terracotta and wool colors, plus selected other opaque decorative blocks. Logs and
pillars preserve their directional textures through conversion, copying and rotation.

Existing stone/oak sculptures, saved items, clipboards and history remain readable.
Expanded-material cells and both history stacks persist through reload; saved sculpture
items, copy/stamp and transformations support the full palette. Commands also accept
`/astra material minecraft:stone_bricks` and
`/astra material minecraft:oak_log[axis=x]`.

See [the supported-block list and implementation notes](docs/MATERIAL-LIBRARY.md).
This remains a single-block creative building prototype: transparent, animated, tinted,
fluid and functional blocks are outside this release. Host hardness, mining requirements
and sounds remain those of Astra's registered host; conversion preserves appearance and
cell material, not every vanilla block behavior. No survival material costs are added.

## Reusable sculptures (0.2.0)

Carved stone/oak blocks can now become reusable **sculpture items**, carrying all 4096
cells and both materials. Normal creative pick-block captures the sculpture. Mining
with a suitable tool drops its saved design; placing that item restores the exact shape.
Inventory, hand and dropped-item models show the sculpture itself. Different designs
have different item components, so they cannot accidentally merge into the same stack.

**H opens the Design Workbench while holding a chisel.** Aim at a sculpture and Copy
to save its design on that chisel. Rotate X/Y/Z or Mirror X/Y/Z changes the saved copy;
Stamp replaces the aimed host's shape and materials as one undoable edit. Get sculpture
item exports the clipboard into inventory in Creative. The workbench shows the copied
design and its material counts. G continues to open the existing brush wheel.

Holding a sculpture item shows a placement ghost: cyan where it fits, red when blocked.
Crouch + right-click air rotates the held sculpture 90 degrees around Y. Placement and
stamping check the actual occupied cells against entities, including undo/redo of a stamp.
The clipboard and sculpture items survive storage and restart. New placements start a
fresh history; existing host histories still retain the full available 32-edit timeline.

Commands: `/astra design copy`, `stamp`, `export`, `rotate_x`, `rotate_y`, `rotate_z`,
`mirror_x`, `mirror_y`, `mirror_z` (each action follows `/astra design`). H is rebindable
under Controls > Astra Microblocks. This remains a two-material free-building prototype;
survival bit costs, a material bag, and multi-block patterns are not implemented.

See [implementation and verification notes](docs/REUSABLE-SCULPTURES.md), including the
original Chisels & Bits source references. Existing automated lifecycle, brush, mixed
material, history and client gates remain required; this release adds real item placement,
pick/drop preservation, transforms, stamping, obstruction and three-boot inventory tests.

The sections below describe earlier releases; their historical limitations may have
been superseded by the current behavior above.

## History and material workflow package (0.1.10)

The full available undo/redo timeline now survives saving, chunk unloading and restart.
The combined timeline remains bounded to 32 edits per host. If you save halfway through
undoing, both stacks and their exact stone/oak cells resume from that point. Older saves
retain whatever history they contain; discarded pre-0.1.10 history cannot be reconstructed.

**New controls, while holding the chisel with no screen open:** Z undoes the last edited
host, Y redoes it, and P samples the aimed cell's material. Rebind them under Controls >
Astra Microblocks if another mod uses those keys. G opens the wheel; crouch-right-click
still undoes the aimed-at host. History shortcuts retain range and changed-target checks.

**Replace** changes the material of occupied cells without changing their shape. Cut is
amber, Add is green, Replace is violet. The preview includes only cells that would change.
The inspector shows Stone / Oak / Empty counts. G now has direct Stone, Oak and Original
buttons, plus Pick material. Original means the host's original registered type.
Sampling checks the current server ray against the exact microcell, including cavity
walls; it does not change the operation, brush, sculpture or remembered history target.

Commands: `/astra operation replace`, `/astra sample`, `/astra material stone`,
`/astra material oak`, `/astra material original`, `/astra undo`, `/astra redo`.

See [the six-build test course and recovery matrix](TESTING-0.1.10.md).
Automated checks retain all earlier behavior while upgrading the reload assertions to
full history. New tests cover 40-edit mixed timelines over three boots, redo-only saves,
legacy/malformed history, every replacement brush/face/material, real item replacement,
sampling all six faces and cavity floors, obstruction, material-only render invalidation,
preview coverage, shortcut guards and menu selection/navigation.

## Mixed-cell workshop test package (0.1.9)

**Stone and oak now share a single sculptable block.** Cut a cavity, open G, choose
Add, and click **Fill** to cycle **Original > Stone > Oak > Original**. Close the menu
and fill the cavity. All eight brushes support either material. Add only fills empty
cells; cut first to replace existing material. Fill is saved independently on each chisel.

Original means the original registered host material, not the face clicked. Old chisels
default to Original to preserve repair behavior. The HUD and LAST EDIT panel identify
mixed hosts as Stone + Oak; Add's HUD also shows the Fill selection.

Commands while holding the chisel: `/astra material original`, `/astra material stone`,
`/astra material oak`. Undo/redo restores materials and shape together. History remains
32 edits per loaded host, with one undo across reload. Old saves migrate automatically.

See [the large 0.1.9 testing plan](TESTING-0.1.9.md) and
[reference research notes](docs/MIXED-MATERIALS.md). All previous tests remain enabled.
This remains a two-material creative prototype; block-level sounds, hardness and drops
come from the original host. Add stays inside existing hosts.

## Holographic wheel test package (0.1.8)

Press **G** with the chisel in your main hand to open the new translucent cyan/violet
brush wheel. Its eight sections select brushes; **Cut / Add** remain in the center.
Click a section to request the setting; its highlight follows the server's response.
Moving across the wheel or its gaps does not select or apply a brush. The menu stays
open for settings changes. Done and Escape close it, and Tab/Enter provide keyboard access.

**Undo / Redo** are grouped beside the wheel under **LAST EDIT**. That panel shows the
held tool's remembered material and history counts when its target is valid and in reach;
it reports an absent, changed or unreachable target otherwise. These actions still use
that tool's last edited host, while crouch-right-click undo uses the aimed-at host.
The in-world HUD's counts still describe the aimed-at host. Commands and history rules
are unchanged; the wheel does not carve just by selecting a brush.

The chisel now has its own transparent 64×64 sprite: an obsidian blade with a violet
edge, brown wrapped mason's grip, iron collar and striking cap. Existing chisels use it
automatically. The original generated artwork and integration notes are in [art](art/README.md).

See [the 0.1.8 testing plan](TESTING-0.1.8.md) for GUI scales, click boundaries, keyboard
navigation, tool appearance, both materials, history targeting, and reload checks.
The 0.1.7 live reload was confirmed by the tester before this release. All earlier automated
lifecycle/material gates remain enabled; the new client gate tests actual wheel clicks,
seams, keyboard focus/activation, delayed server selection, resize, and sprite alpha.

## Two-material workshop test package (0.1.7)

**New:** Astra Sculptable Oak Planks joins Astra Sculptable Stone in the creative
**Building Blocks** tab. The old stone block keeps its `test_host` ID, so existing worlds
continue to load. Both blocks use the same 4096-cell data, geometry, Cut/Add brushes,
previews and history. Each block has one fixed material; Add restores that material.
The block state saves the material, so there is no grid-format migration.

```mcfunction
/give @s astra_microblocks:astra_chisel
/give @s astra_microblocks:test_host 64
/give @s astra_microblocks:oak_host 64
```

The HUD identifies the aimed-at material. The **G menu stays open when choosing Cut/Add
or a brush**, allowing both settings to be changed together. Its selected buttons follow
server-confirmed tool settings. Press Done or Escape to return to building; Undo/Redo
buttons still close the menu so the result is visible.

Start with [the 0.1.7 live test course](TESTING-0.1.7.md), which includes expected cell
counts and mixed-material checks. Automated gates exercise all eight brushes on all six
faces for both materials, real chisel and obstruction handling for each, exact adjacent
stone/oak grids and collision through three world starts, loaded item models, both atlas
textures, all-brush render invalidation and texture bounds, and menu selection updates.
All earlier lifecycle and renderer tests remain enabled.

History still holds 32 edits per loaded host and saves one undo across unload/restart.
The preview still shows geometric selection in green for Add; the server rejects entity
overlap when clicked. This package does not add mixed materials inside one block,
conversion of ordinary Minecraft blocks, carved-item pickup, or survival material costs.

## Build and repair test package (0.1.6)

Hold the chisel in your main hand and press **G**. The menu now contains **Cut / Add**, all
 eight brush shapes, and **Undo last edit / Redo last edit**. In 0.1.6, choosing a setting closes the
menu; 0.1.7 keeps it open for settings changes. Cut keeps the amber preview; Add uses green.

**Add:** aim at an inner face of a cavity and right-click. It selects the adjacent cell on
 the empty side of that face, then applies the selected brush. All eight shapes work:
 single cell, X/Y/Z lines, a face-parallel plane, and aligned 2/4/8-cell cubes. Already-solid
 cells are skipped. Add stays inside that host: clicking an exterior face reports
 **Outside host**, and never creates or changes a neighboring block. Completely empty
 hosts need undo because they have no surface to target. This remains a free-building
 prototype; placement does not consume bits or implement survival material costs.

**History:** each brush stroke is one edit, with up to **32 undo steps per loaded host**.
Undo and redo are shared by everyone editing that host. A new successful edit clears redo;
a no-op preserves it. Crouch-right-click undoes the targeted host. The menu's history buttons
and `/astra undo` / `/astra redo` address the held chisel's **last edited host**, within normal
reach; revision checks reject a stale target changed by another edit. Successful undo/redo
updates that reference so you can repeat the action. Tap once per desired history step.

The HUD shows remaining undo/redo counts for the block you aim at. Older history and redo
are session-only and disappear when the host unloads or the world restarts. The existing
saved last-edit undo remains compatible with older worlds; after reload, that one undo
can itself be redone during the new session. The grid/save format is unchanged. Existing
data-layer single-edit helpers retain their original one-level undo contract.

Add, user undo and redo reject newly occupied cells that overlap a living or building-blocking
entity. A rejected action does not consume history. Green preview/counts show geometric
changes; the server checks entity overlap when you click. Move the obstruction and retry.

Commands: `/astra operation cut`, `/astra operation add`, `/astra mode <mode>`,
`/astra undo`, `/astra redo`. Settings are saved on the tool and require it in your main hand.

### Large live test course

1. **Single-cell repair:** cut a cell from each face. Choose Add / Single, aim at a cavity
   wall and restore it. Check the green target, count, texture, selection and collision.
2. **Every brush:** carve an 8×8×8 recess, then repair parts with 2×2×2 and 4×4×4. Try all
   three lines and the plane on several face orientations. Green cells should become solid;
   existing solid parts must stay untouched.
3. **Boundary containment:** place two hosts together. Try Add at their outer faces and
   at recesses beside the seam. The target must stay within one host, with no spillover.
4. **Mixed history:** make five distinct cuts and three additions. Undo eight times, then
   redo eight times. Compare exact shapes/counts at each step. Switch brush/operation in
   between; settings changes should not add history entries.
5. **Branching:** undo two edits, make a new cut or addition, and confirm redo becomes zero.
   In contrast, an Add stroke over already-filled cells must not discard available redo.
6. **History limit:** make more than 32 successful edits on one host. The count caps at 32;
   undo stops after those most recent edits. Another host keeps its own history.
7. **Obstruction:** stand in a carved space and try a brush that would refill your occupied
   cells. It should refuse the whole edit. Move aside and repeat. Try undo/redo that restores
   those cells too; a refusal must preserve the history count.
8. **Persistence:** save after mixed edits, exit, and reopen. Verify shape, operation and
   brush survive. Expect one saved undo and zero redo; try undo then redo. Repeat after
   moving far enough away to unload the host. F3+T should refresh assets without editing it.
9. **Empty host:** carve away the last cells, remain nearby with the same tool, and use
   `/astra undo`. Redo should empty it again; undo should recover it again.

Automated gates add six-face placement/boundary checks, all brushes, operation persistence,
exact mixed-history order, 32-step bounds, branching/no-ops, saved-undo compatibility, real
item interaction, active-world entity obstruction and retry, history sync counts, green
preview emission, expanded menu layouts, and Add/undo/redo render mesh rebuilding. All
previous lifecycle and render gates still run.

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
still need the in-game check above. See 0.1.6 above for placement and deeper history.

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
In 0.1.4, undo was one level per host; 0.1.6 expands the loaded-session history. The black outline shows the remaining
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
