## Fix: mega raid hundo catch CP uses mega stats instead of base form

**Root cause:** In `ManualRaidViewModel.refresh()` ([ManualRaidActivity.kt:102-112](app/src/main/java/com/example/pokemonalertsv2/ui/counters/ManualRaidActivity.kt)), the catch CP is computed via `perfectCatchCp(stats)` where `stats` resolves first to the boss's own species row. For a mega boss like `CHARIZARD_MEGA_X`, the game master stores a row with the mega's boosted base stats, so the displayed "100% CP L20/L25" is the mega's CP — but the Pokémon you actually catch from a mega raid is the **base form** (e.g., plain Charizard), so the CP must come from the base form's stats. (`PokebattlerNameNormalizer.baseSpeciesId` only strips shadow suffixes, and its fallback only kicks in when the mega row is missing.)

**Fix** (one change in `ManualRaidViewModel.refresh()`):

1. For each catalogue entry, derive a "catch species id" by stripping both shadow and mega/primal suffixes: `PokebattlerNameNormalizer.baseSpeciesId(entry.pokemonId).megaBaseSpeciesId()` (reusing the existing `megaBaseSpeciesId()` helper from `PersonalTeamBuilder.kt`, e.g. `CHARIZARD_MEGA_X` → `CHARIZARD`, `KYOGRE_PRIMAL` → `KYOGRE`).
2. Add those catch ids to the `simSpecies(...)` query (alongside the existing `ids + baseIds`).
3. Compute `hundoCP` from the catch species' stats: `species[catchId] ?: species[entry.pokemonId]` — the mega-row fallback stays so non-mega bosses and missing base rows behave exactly as today.

No changes needed elsewhere: the notification, widget, detail card, and DB persistence all just display `alert.hundoCP`, so fixing the value at creation fixes every display.

**Tests:** Update/add a unit test covering that a mega boss id yields base-form catch CP (e.g., `CHARIZARD_MEGA_X` resolves stats from `CHARIZARD`), following existing test patterns in `app/src/test`.

**Verification:** Build with `gradlew :app:compileDebugKotlin` (or assemble) and run the affected unit tests.