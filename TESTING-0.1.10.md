# Astra Microblocks 0.1.10 - history and material workflow test course

This is a full workshop package using only Astra stone and oak. It adds persistent
bounded history, direct undo/redo keys, cell material sampling, Replace, exact operation
previews, material counts and direct material buttons in the wheel.

## Prepare the course

Use six spaced work areas in your normal mod setup. Keep older sculptures nearby for
compatibility checks, and use fresh hosts where exact counts are specified.

```mcfunction
/give @s astra_microblocks:astra_chisel 2
/give @s astra_microblocks:test_host 64
/give @s astra_microblocks:oak_host 64
```

The chisel is unstackable, so the command gives two separate tools.

| Control | Action |
|---|---|
| G | Wheel: brushes, Cut/Add/Replace, material, history |
| Z | Undo this chisel's last edited host |
| Y | Redo this chisel's last edited host |
| P | Sample the exact aimed-at cell's material |
| Crouch + right-click host | Undo the aimed-at host |
| Crouch + right-click air | Cycle brush |

New keys are rebindable in Controls > Astra Microblocks. They act only with the chisel
in your main hand and no screen open. Other mods can also bind the same keys; use unused
bindings if necessary. Commands remain available: `/astra undo`, `/astra redo`,
`/astra sample`, `/astra operation replace`, and `/astra material stone|oak|original`
(the material command takes one choice, not the literal pipe-separated text).

Cut removes solids. Add fills air. Replace changes solids of the other material and
leaves empty cells alone. Original means the registered host material, not the last
sampled face. Selecting a setting or sampling creates no edit/history entry.

## Course 1 - stone frame with oak trim

1. Start with fresh stone: Stone 4096 / Oak 0 / Empty 0.
2. Choose Replace, Oak, 2x2x2. Click an aligned top group.
3. Expect Stone 4088 / Oak 8 / Empty 0. The shape and total occupancy stay unchanged.
4. Click that group again. Expect zero affected cells and no added undo step.
5. Press Z, then Y. Expect the colors and material counts to reverse and restore.
6. Carve a small window opening, then replace selected frame sections with oak.
7. Aim at an oak trim cell and press P. Material should become Oak while brush and
   operation stay unchanged. Aim at stone and repeat.

Pass: colors change only where selected, there is no shape/collision change on Replace,
no-op history stays intact, and sampling reads the tiny cell rather than the host type.

## Course 2 - striped column

1. On fresh stone, Replace an oak Line Y: Stone 4080 / Oak 16 / Empty 0.
2. On other fresh hosts, repeat with Line X and Line Z for the same counts.
3. Make parallel stripes and then cross one stripe with another.
4. A crossing counts only still-stone cells, so the affected count may be below 16.
5. Change material to Stone and replace selected portions back.
6. Repeat on originally oak hosts with stone stripes.

Pass: every line direction works; preview/count excludes cells already using the chosen
material; texture boundaries remain stable when viewing from different sides.

## Course 3 - layered panel

1. Fresh stone, Replace a top Plane with oak: Stone 3840 / Oak 256 / Empty 0.
2. Cut a channel through that layer to expose the stone beneath.
3. Sample oak on the channel wall and stone at its floor.
4. Use Add with the sampled material to repair part of the channel.
5. Use Replace across a brush containing air, stone and oak. Only occupied cells of the
   other material should change; holes stay open.
6. Repeat a side plane and a bottom plane.

Pass: cavity sampling is precise, Add and Replace have different behavior, and previews
exclude air from Replace. No see-through seams or internal flickering appear.

## Course 4 - detailed carved shape

1. Make a stepped shape or small arch using several brush sizes.
2. Inspect its outline, empty areas and walkable ledges.
3. Replace a 4x4x4 or 8x8x8 portion with the other material.
4. The affected count should equal selected solids of the other material, not the full
   cube volume when the sculpture contains holes.
5. Walk on it again. Compare silhouette and collision with the original shape.
6. Undo/redo replacement and carving in alternating order.

Pass: Replace never fills a hole or removes a solid. Preview is violet, Cut amber and
Add green. Each successful brush action adds one history entry, even for a large brush.

Fresh solid references: 4x4x4 replacement changes 64 cells (4032/64/0 in stone), and
8x8x8 replacement changes 512 (3584/512/0). Intersections/no-ops reduce those counts.

## Course 5 - history recovery (priority regression)

Use a fresh host and perform these six successful edits:

| Step | Action | Stone | Oak | Empty |
|---|---|---:|---:|---:|
| 0 | Fresh stone | 4096 | 0 | 0 |
| 1 | Replace group A (2x2x2) with oak | 4088 | 8 | 0 |
| 2 | Cut that same group A | 4088 | 0 | 8 |
| 3 | Add oak into group A | 4088 | 8 | 0 |
| 4 | Replace a separate stone group B (2x2x2) with oak | 4080 | 16 | 0 |
| 5 | Replace group A back to stone | 4088 | 8 | 0 |
| 6 | Cut group B | 4088 | 0 | 8 |

Ensure A and B do not overlap. Check the actual preview before Add.

1. Undo twice. Expect step 4 and Undo 4 / Redo 2.
2. Save/quit to title and reopen. Expect the SAME shape, materials and 4/2 history counts.
3. Undo four more times. Expect fresh stone and Undo 0 / Redo 6.
4. Save/quit and reopen again. Expect all six redo steps still available.
5. Redo all six individually, checking each row of the table.
6. Save/reopen with Undo 6 / Redo 0 and verify all six undo steps still work.
7. After one undo, try a Replace that changes nothing: redo must remain available.
8. Make a real edit after undo: the old redo branch must clear and stay cleared on reload.

Repeat on oak with material roles reversed. Also leave the area until its chunk unloads,
return, and verify the same history counts and restoration behavior.

Stress extension: make 40 successful edits. The oldest eight fall outside the 32-edit
limit. Undo seven, reload, and expect Undo 25 / Redo 7. Undo the remaining 25, then redo
all 32. Counts and materials must match each retained state. The limit is 32 total steps
across undo and redo, not 32 independent steps on each side.

Pass: reload never collapses a newly created timeline to one undo. Existing older saves
can only supply the history they actually stored; missing old steps cannot be recovered.

## Course 6 - adjacent sculptures and two tools

1. Put one stone and one oak host side by side and mix both materials in each.
2. Set one chisel to Replace/Oak and the other to Add/Stone. Swap tools several times.
3. Settings and last-edit targets should follow their respective tools.
4. Use Z/Y while looking away: they affect that tool's last edit while still in reach.
5. Crouch-right-click the other host: it becomes the remembered target after a successful undo.
6. Press P on a cell in the neighboring block. Sampling must NOT change the remembered
   undo target, alter the block, or consume history.
7. Move out of reach: history must fail without changing either host. Return and retry.
8. Edit the same host with the other chisel: the older tool's stale target should be
   rejected rather than silently undoing someone else's newer work.

Pass: no host-to-host texture leaks; history targets and settings remain distinct.

## Input and menu checks

- All three operations and all three material choices are directly available in G.
- Current operation, brush and material have distinct selected states after server response.
- Hovering, gaps and closing the menu do not change the sculpture or consume history.
- Tab/Shift+Tab reaches all active controls; Enter activates the focused control.
- Test minimum/normal/larger GUI scale, resizing, bright sky and dark backgrounds.
- Pick material in G samples the block at your aim and closes the menu.
- Z/Y/P in chat, inventory or G must not dispatch their world actions. Typing those
  letters in chat should not undo, redo or sample anything.
- Without a main-hand chisel the new keys do nothing for Astra.
- Rebind each key and confirm behavior follows the binding.

## Obstruction, visuals and counts

Try sampling through an ordinary block, from beyond reach, and while aiming at air:
material should remain unchanged, with failure feedback. Test all six outer faces and
cavity walls. No material sampling should create geometry or change history.

Add and history restoration must still reject cells occupied by a player/creature
without partial placement or consumed history. Replace preserves occupancy.

Stone + Oak + Empty should always equal 4096. Verify after mixed edits, history actions
and reload. Use F3+T to reload resources, then inspect both materials and cavity walls.
Keep the normal easy-place mod enabled; its overlay is separate from Astra's preview.

## Scope and reporting

This still uses only the two Astra hosts and two materials. Add stays inside a host.
Breaking/picking up a sculpture does not preserve its cells. Fully empty hosts require
history controls to recover. Physical block properties remain those of the original host.

For a failure, capture the selected operation/brush/material, original host, counts,
undo/redo depths, one problematic action and its result. For reload issues, show counts
and history immediately before quitting and immediately after returning.

## Automated release gates

All earlier gates remain, with old one-snapshot assertions upgraded to full persisted
history and an explicit old-save compatibility test. New server checks cover exact
mixed 40-edit timelines at two restart positions, undo-only/redo-only serialization,
malformed history bounds, no-op/branch rules, every replacement brush/face/material,
preview cell coverage, real item replacement, tool serialization and server ray sampling.
Client checks cover material-only mesh invalidation, texture UVs, violet preview,
material counts, direct buttons, server acknowledgements, keyboard and shortcut guards.
