# Astra Microblocks 0.1.8 — holographic wheel and chisel test plan

The 0.1.7 stone/oak reload was confirmed in live testing. This package adds the radial
menu and custom chisel art while retaining the same editing, save and collision systems.
Use the existing sculptures as regression checks, plus fresh blocks for exact counts.

```mcfunction
/give @s astra_microblocks:astra_chisel
/give @s astra_microblocks:test_host 64
/give @s astra_microblocks:oak_host 64
```

## 1. Existing chisel and new icon

Your existing chisel should automatically show the obsidian/violet blade, brown wrapped
handle and iron fittings. Compare a newly given chisel. Check the hotbar, inventory,
first-person right and left hand, and third-person view. Look for a black background,
cropped edge, invisible tool or oversized silhouette. Its name and saved settings should
remain intact. The violet is part of the texture, not an emissive light effect.

## 2. Wheel appearance at different GUI scales

Hold the chisel and press G. Inspect the wheel at your normal scale, one larger scale,
one smaller scale, and after resizing the window. Eight brush sections, two center
operations, Undo, Redo and Done must remain visible and readable. Test against bright
sky and dark terrain. Check that selected amber/green, cyan outlines and violet hover
states are distinguishable and that the sculpture remains visible behind the menu.
At minimum normal GUI space (320×240), nothing should run off-screen.

## 3. Deliberate mouse selection

Move across all sections without clicking: the selected brush must not change. Click each
brush in turn, including near its inner and outer curved edges. Click the narrow gaps
between sections and outside the wheel: those spaces must not choose a brush. Right-click
a section: no change. Selecting a brush or operation must not carve, add, or consume history.

## 4. Cut/Add and close behavior

Choose Add, then 4×4×4 in the same open menu. Both selections should remain visible after
the server confirms them. Press Done and verify the HUD and actual brush. Reopen, choose
Cut and Single, then close with Escape. Settings stay selected. Reopen and close without
changes: neither counts nor history should move. Undo/Redo close the wheel so their result
is visible. The menu does not pause the world.

## 5. Keyboard and key conflicts

Use Tab and Shift+Tab to navigate the controls; Enter or Space should activate the focused
control. The focused section should be visibly highlighted. Check that all available
brushes, the other operation, Undo, Redo and Done can be reached. Rebind the Open chisel
mode menu action in Controls and confirm it still opens. Opening inventory/chat or using
another item must not accidentally open the wheel. No new global Undo/Redo keybinds are
introduced in this package.

## 6. Last-edit target versus aimed-at block

1. Make a cut in stone, then a cut in oak with the same chisel.
2. Aim at stone and open G. The LAST EDIT panel should identify oak and show oak's history.
3. Use menu Undo. Oak should change; stone should stay unchanged.
4. Crouch-right-click stone. That undoes stone and makes it the chisel's remembered target.
5. Reopen G: LAST EDIT should now identify stone. Redo should reapply that stone cut.
6. Move beyond reach and open G: it should report Out of reach. Failed history requests
   must not alter either shape. Move back and retry.

The in-world HUD still reports the block under the crosshair. The wheel history panel
reports the held chisel's remembered block. Both kinds of undo remain intentional.
A freshly given chisel has no last-edit target until it successfully edits or undoes a host.

## 7. Exact-count regression on both materials

Repeat on fresh stone and oak:

| Action | Cells | History while loaded |
| --- | ---: | --- |
| Fresh | 4096 | U 0 / R 0 |
| Cut 8×8×8 | 3584 | U 1 / R 0 |
| Add 4×4×4 entirely within the cavity | 3648 | U 2 / R 0 |
| Undo addition | 3584 | U 1 / R 1 |
| Redo addition | 3648 | U 2 / R 0 |

Then try all line axes, Plane, Single, 2×2×2 and 4×4×4. Compare preview and actual edits.
The material must not change. Switching settings in the wheel adds no history entries.

## 8. Mixed build and obstruction

Carve and rebuild an alternating stone/oak arch or column. Check cavities, seams, narrow
ledges, collision and material identity. Stand in a cavity and attempt Add, then an undo
or redo that would restore cells through you. Each blocked action must preserve history;
after stepping clear, it should succeed. Add still stays within one existing host.

## 9. Save and resource reload

Note the two shapes, counts and selected tool settings. Save, quit and reopen the world.
Verify everything, including the new icon, and use the persisted last undo. The history
contract remains 32 while loaded, one saved undo and no saved redo after reload. Also test
F3+T resource reload, a chunk unload/reload, and reopening the wheel afterward.

## 10. Extended interaction

Open/close the wheel repeatedly, choose settings quickly, and alternate between chisels.
If you stop holding a chisel while the wheel is open, the menu should close. Check the wheel
with your other installed mods and watch for missing clicks, delayed highlights, stuck
keyboard focus or noticeable frame-rate loss while open. Record the GUI scale, material,
operation, brush and last-edit target when reporting a problem.

## Automated gates for this package

- Existing three-boot world lifecycle, grid/mesh, collision, editing, material and history tests.
- Wheel pixel coverage, non-overlapping sectors, angular gaps, actual button clicks, no hover
  commands, server-acknowledged selection, native Tab/Enter navigation and resize at four sizes.
- Existing menu extraction checks and custom texture atlas identity, alpha border, visible
  pixel coverage, inventory and held-item model checks.
- Optional disposable-client screenshots at two sizes, visually reviewed before installation.
