Fix manual quest alerts overwriting each other by keying local alert identity on the server id.

## Root cause
`PokemonAlert.uniqueId` = `"$name|$endTime"` (app/src/main/java/com/example/pokemonalertsv2/data/PokemonAlert.kt:196) is used as the Room primary key (`AlertEntity.kt:24`) with `OnConflictStrategy.REPLACE` inserts (`AlertDao.kt:20`). Manual quest alerts from the dashboard share the same name and endTime, so every new one (via FCM push or full sync `replaceAll`) replaces the previous row. The dashboard still lists them all because they have distinct server `id`s.

## Changes

1. **`app/src/main/java/com/example/pokemonalertsv2/data/PokemonAlert.kt`** — change the identity to prefer the server id, falling back to the current format only for id-less alerts (unchanged legacy behavior):
   ```kotlin
   val uniqueId: String get() = id?.let { "server-$it" } ?: "${name.trim()}|${endTime.trim()}"
   ```
   No Room schema change or migration is needed — the PK column type is unchanged; existing cached rows self-heal because the next `fetchAlerts()` sees a key mismatch in `sameCachedAlerts()` and calls `replaceAll()`.

2. **`app/src/test/java/com/example/pokemonalertsv2/data/PokemonAlertsRepositoryTest.kt`** — add regression tests:
   - `upsertAlert` with two pushed alerts sharing name+endTime but different server ids → both rows present (previously the old one was REPLACEd).
   - `fetchAlerts` where the service returns two same-name/same-endTime alerts with different ids → both rows cached.
   - Assert `uniqueId` of an id-bearing alert is `"server-<id>"`.

## Verified-safe downstream behavior (no changes needed)
- FCM pushes carry the server id (alert JSON or `alertId` key via `withFallbackId`), so pushed quest alerts get distinct keys.
- Invalidated pushes delete old-format cached rows via the `id = :serverId` clause in `AlertDao.deleteAlert`.
- `seenKeys()` already includes `"server:$id"`, so previously-seen alerts stay seen.
- Weather-change removal matches by id first (`AffectedAlert.matches`), unaffected.
- Notification IDs (`uniqueId.hashCode()`) become distinct per alert — fixing the related symptom of colliding quest alerts overwriting each other's notifications.
- Dismissed-alert IDs / raid-team cache keyed by `uniqueId` reset once for pre-existing alerts after update (cosmetic, self-heals).
- History table (`historyId = id ?: uniqueId.hashCode()`) unaffected.

## Verification
- Run `./gradlew :app:testDebugUnitTest --tests "*PokemonAlertsRepositoryTest*"` (and the broader unit test task if quick).
- Build: `./gradlew :app:assembleDebug`.