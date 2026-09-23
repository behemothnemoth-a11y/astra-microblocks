# Reusable sculptures — 0.2.0

This pass implements a complete single-block design workflow with the existing stone
and oak palette: capture a carved block, transform a saved copy, stamp another host,
or carry the design as a placeable sculpture item.

## Reference work

The original Chisels & Bits repository was inspected at commit
`61d368ccca157ac3d12f681fca8b1735404ab1b2`, particularly:

- [ChiseledBlockItem.java](https://github.com/ChiselsAndBits/Chisels-and-Bits/blob/61d368ccca157ac3d12f681fca8b1735404ab1b2/core/src/main/java/mod/chiselsandbits/item/ChiseledBlockItem.java): a sculpted item carries a multistate snapshot and places its geometry.
- [SingleBlockMultiStateItemStack.java](https://github.com/ChiselsAndBits/Chisels-and-Bits/blob/61d368ccca157ac3d12f681fca8b1735404ab1b2/core/src/main/java/mod/chiselsandbits/item/multistate/SingleBlockMultiStateItemStack.java): transformations operate on stored cell states and maintain material statistics.

These are architectural references. Astra's implementation is written independently
for its existing 16×16×16 occupancy/material masks and Minecraft 26.2 renderer and item
components. It does not import the original mod's code, registration system or save format.

## User workflow

| Action | Control | Result |
| --- | --- | --- |
| Capture a sculpture item | Normal Creative pick-block | An exact copy of its cells and materials |
| Retrieve a sculpture | Mine with a suitable tool | A placeable saved sculpture drop |
| Copy a reusable design | Hold chisel, aim, H → Copy | Clipboard stored on this chisel |
| Transform the clipboard | H → Rotate / Mirror | Changes the saved copy, leaving the source intact |
| Replace another sculpture | Aim at target, H → Stamp | One history entry on that target |
| Export clipboard | H → Get sculpture item | Creative-only item export |
| Rotate a held sculpture | Crouch + right-click air | 90-degree Y rotation |
| Place a sculpture | Ordinary block placement | Exact cells restored, fresh history |
| Undo / redo a stamp | Z / Y with the same chisel | Existing guarded history workflow |

Rotation is around the block center in quarter turns. X rotates `(x,y,z)` to
`(x,15-z,y)`, Y to `(15-z,y,x)`, Z to `(15-y,x,z)`. Mirroring reverses the selected
coordinate. Materials move with their cells. A stamp keeps the target's registered host
type and original-material identity, while copying all occupied cells and their actual
stone/oak materials. It replaces the whole host, including empty space.

## Storage and integration

`SculptureData` stores a versioned compound in an item's custom data, under
`astra_sculpture` for portable blocks or `astra_clipboard` for chisels. Each design
contains a 64-long occupancy mask, 64-long oak mask and original host material. Reads
validate version, lengths and material identity. Writes preserve unrelated chisel data.
Designs are snapshots, with no alias back to a world host or another item's data.

Portable designs intentionally omit world edit history and coordinates. Placing an item
initializes a new host without an undo entry. Stamping uses the existing bounded history,
entity-obstruction checks, revision updates and last-edited target tracking. Unchanged
stamps leave revision, undo and redo untouched. Blocked stamps do not consume history.

`SculptureBlockItem` handles saved-item placement and held-item rotation. Invalid or empty
saved shapes cannot place or consume an item. Placement checks actual occupied geometry,
so an entity in empty space does not incorrectly block a thin sculpture. Normal unsaved
host items continue through vanilla placement checks. The stone and oak hosts now belong
to the appropriate pickaxe/axe mining tags.

The item model wrapper selects the ordinary model for unsaved blocks and a special
renderer for saved sculptures. Its mesh uses the existing exposed-face builder and
per-cell material textures. A bounded per-renderer cache holds at most 64 designs;
resource reload rebuilds the models and their caches. Inventory, first-person and ground
display contexts retain their base model transforms. Placement previews use the existing
merged-cuboid geometry and the same shape-fit predicate as item placement.

## Automated verification

The existing lifecycle and client gates remain required in GitHub Actions. Added gates:

- Both original host types, all three rotation axes, four-turn identity, double-mirror
  identity, exact material counts, independent item copies and inventory stack identity.
- Real survival BlockItem placement and consumption; normal pick-block; actual block
  drop generation; mining-tool eligibility; drop-and-place equivalence.
- Held-item rotation and server copy/rotate/stamp/export commands; atomic stamp undo/redo;
  no-op history preservation; independent chisel settings; out-of-reach rejection;
  Creative-only export; malformed data rejection without consumption.
- Entity obstruction rejects a full sculpture while allowing a thin floor beneath the
  same entity. Blocked stamp and redo preserve both the world and their history stacks.
- Three real server boots: save mixed sculpture and clipboard in a chest, reload and
  place the recovered item, reload the placed block and verify its exact cells and drop.
- Real client model/atlas loading, each face's position and material texture coordinates,
  cached mesh reuse, nonempty GUI/hand/ground rendering submissions, and actual workbench
  button clicks and layout bounds at 320×240 and 640×360 GUI sizes.
- Optional local client captures exercise the rendered workbench and sculpture preview
  at two window sizes. These are automated disposable clients, not the user's world.

This release keeps the scope to one host per design and the existing two materials.
It does not yet implement cross-block selections, a pattern library, survival material
accounting or original Chisels & Bits save compatibility.
