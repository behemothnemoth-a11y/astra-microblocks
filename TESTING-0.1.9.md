# Astra Microblocks 0.1.9 - mixed-cell workshop

Use old sculptures for compatibility checks and fresh blocks for exact counts. Keep
your usual mods enabled. The blue easy-place overlay is from the other installed mod.

## Setup

```mcfunction
/give @s astra_microblocks:astra_chisel
/give @s astra_microblocks:test_host 64
/give @s astra_microblocks:oak_host 64
```

Hold the chisel and press G. Fill cycles **Original > Stone > Oak > Original**. Choose
Add and brush separately. Fill affects newly added cells only; Cut first to replace
material. Original means the host's registered material, not the last clicked face.
Commands: `/astra material original`, `/astra material stone`, `/astra material oak`.
Undo: crouch-right-click host or `/astra undo`. Redo: `/astra redo` or G > Redo.

## 1. Old sculptures

Before editing, inspect old stone/oak shape, texture, selection and collision. Existing
chisels default to Original. Repair a small cavity on each: Original should restore its
original host material. Undo the repairs and confirm the old shapes.

## 2. Oak in stone

Fresh stone: Cut one 2x2x2 group from the top (**4096 > 4088**). Select Add and Fill: Oak,
still 2x2x2. Aim at the cavity floor, check the green preview covers the missing group,
and click (**4088 > 4096**). An oak patch should appear. Walk around and look away: it
must persist without flicker or seams. The HUD should identify **Stone + Oak**.

## 3. Stone in oak and Original

Repeat on fresh oak with Fill: Stone. Verify 4088 > 4096 and visible stone. Use Original
in a different cavity of that oak host: it must add oak even after clicking the stone
patch. Repeat the reverse check on an originally stone host.

## 4. Single-cell checkerboard

Using Single, cut alternating cells and refill in the other material. Each edit changes
the count by one. Inspect all sides and diagonal angles. Cut through the pattern to
expose internal walls: each remaining cell must retain its own material on every face.

## 5. All line directions

Use fresh blocks for Line X, Y and Z. Cut a line (**4096 > 4080**) and refill in the other
material (**4080 > 4096**). Aim at surviving inner faces; Add stays inside the host.
Make parallel stripes. Repeat in the other host and from different faces.

## 6. Plane inlays

Cut a surface plane (**4096 > 3840**), then add the other material into that layer
(**3840 > 4096**) by aiming at the exposed inner surface. Test top, side and bottom.
Cut a channel through it to inspect the new layer and original interior. No flickering
or gaps should appear between solid materials.

## 7. Larger groups and partial repairs

On separate fresh blocks, cut/fill 4x4x4 (**4032 > 4096**) and 8x8x8 (**3584 > 4096**).
Make a large cavity; fill separate parts with stone and oak using smaller brushes. Leave
an opening to inspect internal walls. Counts must reflect newly added cells rather than
full brush size where some selected cells were already occupied.

## 8. No painting over solid cells

Use Add: Oak with a large brush spanning empty space and existing stone. Only empty
space should become oak. Reverse and repeat. Add with no empty cells must not alter
materials or history. Changing Fill/brush in the menu alone creates no undo entry.

## 9. Exact undo and redo

Sequence: cut stone, add oak, cut into the oak patch, add stone. Undo each step and
inspect colors and cavities, then Redo all. Change Fill before undo/redo: history must
restore stored materials, not the current Fill. After Undo, attempt a no-op: Redo should
remain available. After Undo, make a real edit: the old redo branch should clear.
Stress with over 32 real edits and verify the existing 32-edit history limit.

## 10. Adjacent mixed hosts

Place stone and oak hosts beside each other and add contrasting inlays to both. Editing
one must not change the other. Menu Undo/Redo still targets the held tool's last edited
host; crouch-right-click Undo targets the aimed host. LAST EDIT should say Stone + Oak
for a mixed target. World HUD counts describe the aimed host.

## 11. Reload twice

Leave two distinctive mixed sculptures, one originally stone, one oak, with a recognizable
final edit in each. Note shape, colors, counts and tool Fill. Save/quit and reopen: all
must match. Each host retains **one saved undo**; older history and redo are not saved.
Undo the final edit, check materials, and Redo it. Make another edit, reload again and
repeat. Also travel far enough to unload/reload the chunks and inspect both sculptures.

## 12. Collision and blocked restoration

Walk on mixed ledges and enter cavities. Filled cells collide regardless of material.
Try Add or Undo that would fill space occupied by a creature/player: rejection must
leave history and the whole brush unchanged. Move away and retry. Looking away must
not hide actual geometry.

## 13. Menu, separate tools and resource reload

Test G at normal and larger/smaller GUI scales. Fill, Undo, Redo and Done must fit and
remain clickable. Tab/Shift+Tab must reach Fill; Enter cycles it. The menu stays open
when changing Fill. Close/reopen to confirm settings. Use two chisels with different
Fill and brush settings, then swap them: settings follow each tool. F3+T reloads
resources; afterward inspect both materials, including cavity walls.

## 14. Extended build

Build a stone frame with oak trim, an oak panel with stone studs, and a striped column.
Use several brush sizes and all line directions. Keep ordinary neighboring blocks and
your easy-place mod active. Watch for missed clicks, wrong fills, stale previews,
disappearing cells, wrong history targets and reload changes.

## Current boundaries

Only the two Astra hosts support editing; only stone/oak fill is available. Add cannot
extend outside a host or convert ordinary terrain. Empty hosts have no normal selectable
face; recover with history. Breaking/picking up a sculpture does not preserve its cells.
Sounds, hardness and drops belong to its original host. This creative prototype does
not consume bits. Older mod versions cannot preserve mixed materials.

## Reporting issues

Record Fill, brush, operation, original host type, count before/after and whether Undo,
Redo or reload preceded the issue. A short clip showing G, one problematic click, another
view of the result and history is ideal.

## Automated coverage

All older lifecycle, collision, targeting, brush, history, menu, model and renderer gates
remain. New checks cover both hosts/all brushes/faces, no repaint/no-op history, defensive
snapshots, exact NBT/update tags, old occupancy/boolean migration, bounded mixed history,
actual chisel placement, tool serialization, exact materials across three server boots,
per-face atlas UVs on mixed solids/dense cavities, Fill commands, delayed server response,
keyboard reachability and menu bounds.
