# Expanded material library — 0.3.0

This release replaces the two-material cell restriction with a local palette of supported
vanilla block states. It keeps single-block editing, the existing collision/hit mesher,
and the original stone/oak registrations.

## Controls and behavior

- Place a supported vanilla full cube and right-click with the Astra Chisel to convert
  and edit it. Cut, Add and Replace retain their existing brush rules. A cut records a
  full-material host as its previous state; undo restores its cells, not the vanilla block ID.
- B opens a searchable material library; G also has a Materials button. Preview thumbnails
  include log orientation. Hover for the full state name. Selection stays server-authoritative.
- P samples the first supported vanilla block or sculpted cell hit by the server ray.
  An intervening vanilla stone block now samples stone instead of looking through it.
- Recent stores up to eight selections on that chisel. Original repairs with the
  sculpture's original material and axis. It does not infer material from the clicked face.
- All existing sculpture-item, clipboard, stamping, rotation, mirroring and history
  operations carry the full cell palette. Rotation also rotates log/pillar axes.

## Supported scope

The catalog contains **164 block IDs and 250 states**. The full ID list is
[material_blocks.txt](../tools/material_blocks.txt). It includes 16 concrete colors,
16 wool colors, 16 colored terracottas plus plain terracotta; the plank families;
overworld logs/wood and stripped forms; bamboo blocks; and selected masonry and decorative
full cubes. X/Y/Z axis variants account for the additional states.

The catalog is explicit, not a permissive test for any full-looking block. Glass, leaves,
fluids, stairs, inventories, redstone machinery, animated surfaces and tinted surfaces
are not converted. Nether stems and animated prismarine are excluded while their supported
opaque plank/brick variants remain available. Other mods' blocks are not yet included.

Converted blocks use Astra's existing host behavior for hardness, mining eligibility,
sounds and lighting; the material data represents visual block states. This is not a
survival-economy or vanilla-functionality emulation release. Ordinary resource-pack texture
replacements work; arbitrary changes to vanilla model topology are outside the catalog.

## Storage and migration

`HostMaterial` now interns supported state identities such as
`minecraft:oak_log[axis=x]`; identity comparisons remain valid throughout the edit path.
`MicroblockVolume` keeps exact material references alongside the established occupancy
mask. A material-only edit leaves collision geometry untouched.

`VolumePalette` assigns local nonzero indices to the materials used in a sculpture;
zero means empty. Indices are bit-packed into longs using the smallest width needed for
that palette. The record also stores the original material and validates version, palette
size, state IDs, bit width, packed length and every decoded index. Invalid saved items
cannot be placed or consumed. The supported-state catalog excludes functional blocks.

Legacy stone/oak block entities still read their old occupancy/oak masks and v3 history.
Expanded-material hosts write a v4 current volume and bounded v4 history; both history
stacks preserve their order and cursor. Only current cells and history counts are sent to
clients, never the full history. A malformed v4 history is discarded as a whole while its
valid current volume is retained. Stone/oak-only saves can keep the compact legacy format.

New portable items and clipboard designs use palette version 2. The reader also supports
0.2.0's version-1 stone/oak item data. Per-tool material settings are additive, preserving
brush, operation, clipboard and last-edit metadata. The previous versions cannot represent
expanded materials; downgrading a world after using them is not supported.

## Rendering and source references

The original Chisels & Bits
[StateEntryPalette](https://github.com/ChiselsAndBits/Chisels-and-Bits/blob/61d368ccca157ac3d12f681fca8b1735404ab1b2/api/src/main/java/mod/chiselsandbits/api/block/storage/StateEntryPalette.java)
and
[BlockInformation](https://github.com/ChiselsAndBits/Chisels-and-Bits/blob/61d368ccca157ac3d12f681fca8b1735404ab1b2/api/src/main/java/mod/chiselsandbits/api/blockinformation/BlockInformation.java)
were reviewed for their palette and block-state architecture. Astra's implementation and
save format are independent; original-mod code and saves are not imported.

The catalog's six face textures, face UV rotations and block-state model rotations are
resolved from the installed Minecraft 26.2 client model definitions. Rebuild with:

```text
python tools/generate_material_catalog.py /path/to/minecraft-26.2-client.jar
```

The generator verifies full-cube geometry, six faces, non-animated textures and supported
state properties. It copies texture identifiers and model metadata, not texture images.
World meshes, saved-item meshes and material-library thumbnails share the same directional
texture/UV mapping. Log end grain follows its stored axis, including after design rotation.

## Verification

All previous lifecycle, brush, material, full-history, sculpture and client gates remain
required. The existing sampling test now checks the deliberately expanded behavior:
sampling an occluding vanilla stone block selects stone and never the cell behind it.
Menu checks count the added Materials control and retain all earlier navigation assertions.

Additional automated gates cover:

- Real chisel conversion and sampling for all 250 states; correct original-material repair,
  4095-cell first cuts, and undo/redo for every conversion.
- Explicit rejection of glass, leaves, chests, stairs, water and air without converting them.
- Every brush on all six faces with new masonry, colored concrete and horizontal logs;
  Add, Replace, material-only preview coverage, and exact undo/redo.
- A sculpture mixing all 250 states; bit-packed storage; invalid fields/unsupported IDs;
  old sculpture-item migration; item codec round trips; whole-design stamp undo/redo;
  four rotations and double mirrors; log-axis transformations.
- Three real world boots preserving a 40-edit palette history at its 32-edit bound, both
  undo and redo stacks, chest sculpture items, clipboard, recent material choices, placed
  recovered items and their subsequent drops.
- Current-volume network serialization without full history; invalid-history isolation.
- Loaded client atlas textures and UV scale for all states and a dense mixed sculpture;
  searchable library selection, real mouse clicks, and both small/large screen layouts.

These are disposable automated worlds and clients; they do not modify the user's builds.
