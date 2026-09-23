# Mixed-material research and implementation

Reviewed Chisels & Bits commit 61d368ccca157ac3d12f681fca8b1735404ab1b2 on 2026-09-22.
The reference checkout stays outside this repository; no source code was copied.

- [StateEntryStorage](https://github.com/ChiselsAndBits/Chisels-and-Bits/blob/61d368ccca157ac3d12f681fca8b1735404ab1b2/api/src/main/java/mod/chiselsandbits/api/block/storage/StateEntryStorage.java)
  stores per-cell palette indices, serializes palette/data and copies snapshots.
- [StateEntryPalette](https://github.com/ChiselsAndBits/Chisels-and-Bits/blob/61d368ccca157ac3d12f681fca8b1735404ab1b2/api/src/main/java/mod/chiselsandbits/api/block/storage/StateEntryPalette.java)
  maps block information to entries, including air.
- [BitItem](https://github.com/ChiselsAndBits/Chisels-and-Bits/blob/61d368ccca157ac3d12f681fca8b1735404ab1b2/core/src/main/java/mod/chiselsandbits/item/bit/BitItem.java)
  carries block information and chisel mode on the item.

Astra specializes per-cell material identity to its two existing materials.
MicroblockVolume composes the unchanged occupancy grid with an oak mask: air has no
occupancy, occupied without oak is stone, occupied with oak is oak. Oak bits are masked
against occupancy on construction/load. Defensive history snapshots include both masks.
Collision, selection, hit resolution, brush masks and face topology still use occupancy.

materials_v2=true adds oak_0..63 and undo_oak_0..63. Existing grid_format_v1,
grid_0..63, undo_0..63 and revision fields remain. Without v2, current and saved undo
materials derive from original host identity. Boolean prototype migration also remains.
Old saves upgrade on saving. Older versions cannot preserve mixed-cell materials.

Exposed faces use their owning cell's texture. Touching solid stone/oak cells have no
internal faces. Both atlas sprite identities and revision invalidate cached meshes.

Per-tool CustomData astra_material defaults to Original for old/unrecognized values.
Original resolves the original host type; Stone/Oak are explicit choices. Add changes
empty cells atomically, retaining the existing obstruction check. The menu waits for
the server's tool update before showing a changed Fill. Settings alone create no history.

General palettes, sampling, bit inventory, survival costs, cross-host placement, carved
item persistence and per-cell physical properties remain future work.
