# Astra Microblocks 0.1.7 — two-material test course

Use a creative test area. Existing sculpted stone should load unchanged. The new block is
**Astra Sculptable Oak Planks**; the original is now named **Astra Sculptable Stone**.
Both are in Building Blocks. The chisel is in Tools & Utilities.

```mcfunction
/give @s astra_microblocks:astra_chisel
/give @s astra_microblocks:test_host 64
/give @s astra_microblocks:oak_host 64
```

## 1. Inventory and settings

- Confirm both blocks have distinct icons and names and appear when held and placed.
- Hold the chisel, press G, select Add and then 4×4×4 without reopening the menu.
- Confirm each selected button updates. Press Done; the HUD should show Add / 4×4×4.
- Repeat with Cut / Single. Close with Escape. Neither settings change should edit a block.
- Aim between stone and oak: the HUD should identify Stone or Oak planks correctly.

## 2. Repeatable counts on each material

Place fresh stone and oak blocks beside each other. Repeat this sequence on each:

| Action | Expected occupied cells | History while continuously loaded |
| --- | ---: | --- |
| Fresh block | 4096 | Undo 0 / Redo 0 |
| Cut one 8×8×8 corner | 3584 | Undo 1 / Redo 0 |
| Add 4×4×4 entirely inside that cavity | 3648 | Undo 2 / Redo 0 |
| Undo the addition | 3584 | Undo 1 / Redo 1 |
| Redo the addition | 3648 | Undo 2 / Redo 0 |
| Undo the addition, then undo the cut | 4096 | Undo 0 / Redo 2 |

Use the preview to place the 4×4×4 region fully inside the cavity. Stand clear of the
cells being restored. For menu or command history, finish this sequence on one block
before switching to the other: those actions target the held chisel's last edited block.
Crouch-right-click targets the block under the crosshair.

## 3. Every brush, every useful viewing angle

On fresh blocks, compare the stone and oak results for Single, Line X/Y/Z, Plane,
2×2×2, 4×4×4 and 8×8×8. Full-block cut counts are 1, 16, 256, 8, 64 and 512 respectively.
Try top, bottom, side faces and cavity walls. Partly empty selections should affect fewer
cells, exactly as the preview reports. Add should restore wood in wood and stone in stone.

Oak's grain should stay at normal block texture scale, with no whole-plank texture
repeated inside every 1/16 cell. Watch interior walls, thin ledges and through-holes from
several angles. Compare the outer faces with a normal Minecraft oak-plank block nearby.

## 4. Mixed-material neighbors

Build a small checkerboard or arch from alternating sculptable stone and oak. Carve near
shared edges, then repair the cavities. Each edit must stay inside its targeted block;
the neighbor's material, cells and history must remain unchanged. Look through a tunnel
that crosses the seam: each host should retain its own texture without an opaque full
cube covering the hole. Add on an outer face should report Outside host.

## 5. Save, reload and refresh textures

Leave different shapes in the two materials; note their counts. Save and Quit to Title,
then reopen. Check both textures, exact shapes, counts, collision and the tool settings.
Each edited host should have at most one saved undo and no saved redo. Try its saved undo
and the resulting redo. Repeat after a chunk unload and after F3+T resource reload.
The one-saved-undo limit is unchanged in 0.1.7.

## 6. Collision and blocked history

Walk on oak steps and narrow ledges, and through a sufficiently large carved opening.
Stand in a cavity and try Add into your space: the edit should fail as a whole, preserving
the count and history. Then try an undo that would refill your space. Step clear and retry;
the same undo should succeed. For blocked redo: Add a region, undo it, stand in the empty
region, then redo. The blocked redo must remain available after you step away.

## 7. Empty hosts and replacement

Carve every cell out of oak. Stay nearby with the same tool and run `/astra undo`:
oak should return. Redo should empty it again. Separately, break an oak host and place a
fresh stone host in the same position, then reverse the materials. The replacement must
start full with the correct texture and fresh history. This does not test or promise
preservation of carved geometry in dropped or picked-up items.

## 8. Extended session

Build several stone and oak sculptures, alternate materials frequently, and use repeated
undo/redo. More than 32 successful edits should retain only the newest 32. Record any
visible texture swaps, delayed updates, wrong counts, collision mismatch or noticeable
frame-rate degradation. Performance at large sculpture counts remains a live test.

When reporting a problem, include the material, selected operation/brush, counts before
and after, and whether it happened immediately, after undo/redo, or after loading.
