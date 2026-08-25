package com.example.pokemonalertsv2.data.pokegenie

import java.io.Reader
import java.util.Locale
import kotlin.math.roundToInt

enum class ShadowState { NORMAL, SHADOW, PURIFIED }

/** One scan row from a Poke Genie CSV export. Only [name] is guaranteed. */
data class PokeGenieRow(
    val scanIndex: Int? = null,
    val name: String,
    val form: String? = null,
    val pokedexNumber: Int? = null,
    val gender: String? = null,
    val cp: Int? = null,
    val hp: Int? = null,
    val atkIv: Int? = null,
    val defIv: Int? = null,
    val staIv: Int? = null,
    val ivAvg: Double? = null,
    val levelMin: Double? = null,
    val levelMax: Double? = null,
    /** Midpoint of the level range, or whichever bound is known. */
    val level: Double? = null,
    val quickMove: String? = null,
    val chargeMove: String? = null,
    val chargeMove2: String? = null,
    val shadowState: ShadowState = ShadowState.NORMAL,
    val lucky: Boolean = false,
    val favorite: Boolean = false
)

sealed interface PokeGenieParseResult {
    data class Success(
        val rows: List<PokeGenieRow>,
        val dataLineCount: Int,
        val skippedLineCount: Int,
        val unmappedHeaders: List<String>
    ) : PokeGenieParseResult

    data class Failure(val reason: Reason, val detail: String? = null) : PokeGenieParseResult

    enum class Reason { EMPTY_FILE, NO_NAME_COLUMN, NO_DATA_ROWS }
}

/**
 * Reads a Poke Genie scan-history CSV.
 *
 * Written to be forgiving, because the export's shape moves between app versions and
 * locales: headers are matched by normalized name rather than position, the delimiter is
 * sniffed, and every column except the species name is optional. A real export was the
 * reference (49 columns, comma-delimited, UTF-8, no BOM, LF endings), but nothing here
 * depends on that exact layout.
 */
object PokeGenieCsvParser {

    private const val BOM = '\uFEFF'

    fun parse(reader: Reader): PokeGenieParseResult {
        val text = reader.readText().let { if (it.startsWith(BOM)) it.substring(1) else it }
        if (text.isBlank()) return PokeGenieParseResult.Failure(PokeGenieParseResult.Reason.EMPTY_FILE)

        val delimiter = sniffDelimiter(text)
        // A semicolon file is an Excel export from a comma-decimal locale.
        val commaDecimal = delimiter == ';'

        val records = tokenize(text, delimiter)
        if (records.isEmpty()) return PokeGenieParseResult.Failure(PokeGenieParseResult.Reason.EMPTY_FILE)

        val header = records.first()
        val index = HashMap<Field, Int>()
        val unmapped = mutableListOf<String>()
        header.forEachIndexed { position, rawName ->
            val field = Field.forHeader(rawName)
            if (field == null) {
                if (rawName.isNotBlank()) unmapped += rawName
            } else if (!index.containsKey(field)) {
                // First column wins, so a later near-duplicate cannot clobber a good mapping.
                index[field] = position
            }
        }

        val nameColumn = index[Field.NAME]
            ?: return PokeGenieParseResult.Failure(
                PokeGenieParseResult.Reason.NO_NAME_COLUMN,
                "Expected a Name column; found: " + header.take(8).joinToString(", ")
            )

        val rows = mutableListOf<PokeGenieRow>()
        var skipped = 0
        records.drop(1).forEach { record ->
            if (record.all { it.isBlank() }) return@forEach
            val name = record.getOrNull(nameColumn)?.trim().orEmpty()
            if (name.isEmpty()) {
                // Trailing summary or footer lines rather than scans.
                skipped++
                return@forEach
            }
            rows += buildRow(record, index, commaDecimal, name)
        }

        if (rows.isEmpty()) {
            return PokeGenieParseResult.Failure(PokeGenieParseResult.Reason.NO_DATA_ROWS)
        }
        return PokeGenieParseResult.Success(rows, records.size - 1, skipped, unmapped)
    }

    private fun buildRow(
        record: List<String>,
        index: Map<Field, Int>,
        commaDecimal: Boolean,
        name: String
    ): PokeGenieRow {
        fun raw(field: Field): String? = index[field]
            ?.let { record.getOrNull(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        fun int(field: Field): Int? = raw(field)
            ?.filter { it.isDigit() }
            ?.toIntOrNull()

        fun double(field: Field): Double? = raw(field)
            ?.let { if (commaDecimal) it.replace(',', '.') else it }
            // Strips unit suffixes such as 17.24kg, 0.95m and 76.43%.
            ?.filter { it.isDigit() || it == '.' }
            ?.toDoubleOrNull()

        val levelMin = double(Field.LEVEL_MIN)
        val levelMax = double(Field.LEVEL_MAX)

        return PokeGenieRow(
            scanIndex = int(Field.INDEX),
            name = name,
            form = raw(Field.FORM),
            pokedexNumber = int(Field.DEX),
            gender = raw(Field.GENDER),
            cp = int(Field.CP),
            hp = int(Field.HP),
            atkIv = int(Field.ATK_IV),
            defIv = int(Field.DEF_IV),
            staIv = int(Field.STA_IV),
            ivAvg = double(Field.IV_AVG),
            levelMin = levelMin,
            levelMax = levelMax,
            level = midpointLevel(levelMin, levelMax),
            quickMove = raw(Field.QUICK_MOVE),
            chargeMove = raw(Field.CHARGE_MOVE),
            chargeMove2 = raw(Field.CHARGE_MOVE_2),
            shadowState = shadowState(raw(Field.SHADOW)),
            lucky = flag(raw(Field.LUCKY)),
            favorite = flag(raw(Field.FAVORITE))
        )
    }

    /** Levels come in half steps, so the midpoint is rounded to the nearest 0.5. */
    internal fun midpointLevel(min: Double?, max: Double?): Double? = when {
        min != null && max != null -> ((min + max) / 2.0 * 2).roundToInt() / 2.0
        else -> min ?: max
    }

    /** Poke Genie writes 0/1/2; other tools write words. Accept both. */
    internal fun shadowState(value: String?): ShadowState {
        val token = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            token == "1" || token.startsWith("shadow") -> ShadowState.SHADOW
            token == "2" || token.startsWith("purif") -> ShadowState.PURIFIED
            else -> ShadowState.NORMAL
        }
    }

    internal fun flag(value: String?): Boolean {
        val token = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return token == "1" || token == "true" || token == "yes" || token == "y"
    }

    /** Picks whichever candidate separator appears most often in the header line. */
    internal fun sniffDelimiter(text: String): Char {
        val headerLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        return listOf(',', ';', '\t').maxByOrNull { candidate ->
            headerLine.count { it == candidate }
        } ?: ','
    }

    /** RFC4180: double-quote escaping, embedded delimiters and newlines, CRLF or LF. */
    internal fun tokenize(text: String, delimiter: Char): List<List<String>> {
        val quote = '"'
        val records = mutableListOf<List<String>>()
        var record = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            record.add(field.toString())
            field.setLength(0)
        }

        fun endRecord() {
            endField()
            records.add(record)
            record = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == quote && i + 1 < text.length && text[i + 1] == quote -> {
                    field.append(quote)
                    i++
                }
                c == quote -> inQuotes = !inQuotes
                !inQuotes && c == delimiter -> endField()
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRecord()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) endRecord()
        return records.filterNot { it.size == 1 && it.first().isBlank() }
    }

    private enum class Field(vararg val aliases: String) {
        INDEX("index", "scanindex"),

        /**
         * The species name only. Deliberately excludes "pokemon", which in a real export
         * is the Pokedex *number* column, not the name.
         */
        NAME("name", "species", "pokemonname"),
        FORM("form"),
        DEX("pokemon", "pokemonnumber", "pokedexnumber", "dexnumber", "dex", "number"),
        GENDER("gender", "sex"),
        CP("cp", "combatpower"),
        HP("hp", "hitpoints"),
        ATK_IV("atkiv", "attackiv", "ivattack", "ivatk"),
        DEF_IV("defiv", "defenseiv", "ivdefense", "ivdef"),
        STA_IV("staiv", "stamiv", "staminaiv", "hpiv", "ivstamina", "ivsta"),
        IV_AVG("ivavg", "ivaverage", "iv", "ivpercent"),
        LEVEL_MIN("levelmin", "lvmin", "minlevel", "level", "lv"),
        LEVEL_MAX("levelmax", "lvmax", "maxlevel"),
        QUICK_MOVE("quickmove", "fastmove", "quickattack", "fastattack"),
        CHARGE_MOVE("chargemove", "chargedmove", "chargemove1", "chargedmove1", "specialmove"),
        CHARGE_MOVE_2("chargemove2", "chargedmove2", "secondchargemove"),
        SHADOW("shadowpurified", "shadow", "shadowpurifiedstatus", "purified"),
        LUCKY("lucky"),
        FAVORITE("favorite", "favourite");

        companion object {
            // Field.entries is qualified because inside buildMap an unqualified `entries`
            // resolves to the map being built.
            private val byAlias: Map<String, Field> = buildMap {
                Field.entries.forEach { field ->
                    field.aliases.forEach { alias -> put(alias, field) }
                }
            }

            fun forHeader(rawName: String): Field? =
                byAlias[rawName.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }]
        }
    }
}
