package com.example.pokemonalertsv2.data.database

/** As Room exports them in schemas/26.json; MIGRATION_25_26 creates them. */
internal val CHECKLIST_TABLES_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `checklists` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `title` TEXT NOT NULL, " +
        "`kind` TEXT NOT NULL, `url` TEXT, `useForAlerts` INTEGER NOT NULL, `includeEvolutions` INTEGER NOT NULL, " +
        "`sortOrder` INTEGER NOT NULL, `lastSyncMillis` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `checklist_entries` (`checklistId` TEXT NOT NULL, `entryKey` TEXT NOT NULL, " +
        "`pokedexId` INTEGER NOT NULL, `formSlug` TEXT, `gender` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
        "`needed` INTEGER NOT NULL, `spriteUrl` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`checklistId`, `entryKey`))",
    "CREATE INDEX IF NOT EXISTS `index_checklist_entries_pokedexId` ON `checklist_entries` (`pokedexId`)"
)

/** As Room exports it in schemas/27.json; MIGRATION_26_27 creates it. */
internal const val CHECKLIST_PENDING_TABLE_SQL =
    "CREATE TABLE IF NOT EXISTS `checklist_pending` (`checklistId` TEXT NOT NULL, `entryKey` TEXT NOT NULL, " +
        "`caught` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, " +
        "`attemptCount` INTEGER NOT NULL, `lastError` TEXT, PRIMARY KEY(`checklistId`, `entryKey`))"
