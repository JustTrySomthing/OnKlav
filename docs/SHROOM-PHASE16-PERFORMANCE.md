# SHROOM Phase 16 — Mobile performance optimization

Source baseline: SHROOM Phase 15 cumulative checkpoint (not a standalone demo).
Scope: two specific optimizations; no changes to the main OnKlav APK.

## 1. Static environment draw batching

- `decor_box`, `decor_ball`, `add_decor_instance`, and `flush_decor_batches` group immutable detail geometry by shape and cached material.
- Each group becomes one `MultiMeshInstance3D` with a unit cube or shared low-poly sphere plus per-instance transforms. Decoration is built once during sector construction, not each frame.
- Converted room landmarks, unique sector focal signs, objective cues, wall damage, contamination stains and static hazard boundary trim. Preserves procedural seeded placements and material colors.
- No interactive geometry was batched: wall/cover colliders, pickups, canisters, objective and exit markers, enemy geometry, animated toxic/electrical hazard puddles and all lights remain individual objects.
- `decor_batches` resets for a new room and flushes once for both expedition and legacy arena paths.

## 2. Enemy crowd separation

- `crowd_forces` computes each unique unordered enemy pair once and adds the equal/opposite avoidance vector to both actors.
- The original double-loop performed up to 13*12=156 pair checks per frame; the unique-pair pass performs at most 13*12/2=78 checks (50% reduction in distance comparisons).
- No spatial hash, pathfinding alteration, new physics bodies, or garbage-heavy per-enemy caches. Steering strength and proximity radius are unchanged.
- Separation forces are sampled once at the start of an AI tick; previously they were sampled as enemies moved sequentially. This can subtly change trajectories but preserves the local avoidance formula.

## Verification performed

- `python check_phase16.py`: 15 named structural / simulation checks passed, including 200 randomized numerical equivalence cases for crowd-forces.
- `shroom/tests.gd`: additional Godot assertions that static detail batches exist, animated hazard nodes remain independent, batch buffers flush, and crowd forces sum to zero.
- Source archive integrity: verified via `unzip -t`.

## Not verified

Godot 4 runtime was unavailable in this environment; the GDScript regression suite has NOT executed. An Android phone was not attached. No frame-rate, GPU draw-call or thermal measurements are claimed. A handset playtest should inspect all five sectors, hazardous puddle pulses, boss battle, legacy arena, save/reload, low and high detail areas, and framerate on the Galaxy S21+.

No APK or `.onklav-g` pack was built; preserve all other OnKlav workstreams until integration is deliberately performed.
