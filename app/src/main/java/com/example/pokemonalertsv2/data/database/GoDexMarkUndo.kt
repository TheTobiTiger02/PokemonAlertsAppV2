package com.example.pokemonalertsv2.data.database

/** One entry as it was before a mark in the checklist editor, so the mark can be undone. */
data class GoDexMarkUndo(
    val entryKey: String,
    val neededBefore: Boolean,
    /** The mark that was waiting to be sent before, if any. */
    val previous: GoDexPendingUpdateEntity?
)
