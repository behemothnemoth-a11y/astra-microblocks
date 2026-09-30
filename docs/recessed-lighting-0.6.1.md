# Carved lighting, 0.6.1

Luminous vanilla materials now contribute their default-state emission to a sculpted host. The host emits the maximum level among its remaining materials. One sea-lantern cell therefore emits level 15, just as a larger sea-lantern volume does. RGB white is not emissive. Light is vanilla, uncolored, and block-level; this does not simulate directional beams or light proportional to microcell surface area.

The host has a derived `light` state property, defaulting to zero for old saved states. Edits, undo and redo refresh it immediately. Attaching/loading an entity schedules one block tick to repair derived emission after chunk load or schematic imports. No permanent block entity ticker, frame polling, hidden light blocks or separate light entities are introduced. The existing volume and history formats remain unchanged.

Light state changes preserve the existing block entity and orientation. Rotation/mirror change orientation only. The standard engine handles light propagation when block emission changes.

## Checks

The lifecycle suite covers one-cell emission, empty/RGB darkness, palette serialization, edit/undo/redo, repair of stale imported state, block entity preservation and emission/history over two reloads. Existing data, collision, mesh, color and transform tests remain enabled.

Manual verification remains necessary: paste recessed fixtures, inspect illumination at night, rotate/mirror, remove the last luminous cell, undo/redo, unload/reload the chunk and reopen the world. Test actual neighboring block light, not daytime sky light. Compare isolated one-cell, 2x2x2, 4x4x4 and thin-strip specimens with a vanilla sea lantern, at least 32 blocks apart. All should share source level 15 under this initial policy; occlusion and placement can change the visible result.

The accompanying Recessed Lighting 04 schematic collection uses 8x4x1 sea-lantern inserts recessed two microcells inside open-bottom housings. It requires this update for emitted light; in 0.6.0 the shape renders but does not illuminate its surroundings.
