package com.example.pokemonalertsv2.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.pokemonalertsv2.ui.theme.typeColor
import kotlinx.coroutines.launch

/** Sections shown collapsed at first: useful now and then, but long and not about playing the event. */
private val COLLAPSED_SECTIONS = setOf("sales", "graphic", "go-pass")

/**
 * An event as LeekDuck describes it, full screen: the header with times and the reminder star, chips that
 * jump to each section, then every section of the LeekDuck page. Until the page arrives (or when the event
 * has none) the feed's own details stand in.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun EventDetailScreen(
    event: GameEvent,
    state: EventsUiState,
    onDismiss: () -> Unit,
    onToggleStar: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    loadPage: suspend (String) -> EventPage?,
    rememberedPage: (String) -> EventPage?,
) {
    var page by remember(event.id) { mutableStateOf(rememberedPage(event.id)) }
    var loading by remember(event.id) { mutableStateOf(page == null) }
    LaunchedEffect(event.id) {
        loadPage(event.id)?.let { page = it }
        loading = false
    }
    val sections = page?.sections.orEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val expanded = remember(event.id) { mutableStateMapOf<String, Boolean>() }
    // Items before the first section: header, reminder row, chips, and the loading/feed fallback.
    val headerItems = 4
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("event_detail")) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("event_detail_close")) { Text("Close") }
                    Spacer(Modifier.weight(1f))
                    event.link?.let { link ->
                        TextButton(onClick = { onOpenLink(link) }, modifier = Modifier.testTag("event_open_link")) { Text("Open on LeekDuck") }
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(key = "header") {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            event.image?.let {
                                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(170.dp).clip(MaterialTheme.shapes.medium))
                            }
                            Text(event.heading ?: eventTypeName(event.eventType), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                            Text(event.name, style = MaterialTheme.typography.headlineSmall)
                            Text(timeRange(event), style = MaterialTheme.typography.bodyMedium)
                            if (event.spawnRelevant) Text("Changes wild spawns", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    item(key = "reminder") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (event.id in state.settings.starredIds || event.eventType in state.settings.reminderTypes)
                                "You'll be reminded ${state.settings.leadMinutes} min before" else "No reminder",
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            StarButton(event, state.settings, onToggleStar)
                        }
                    }
                    item(key = "chips") {
                        if (sections.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(sections, key = { index, section -> "$index-${section.key}" }) { index, section ->
                                AssistChip(onClick = {
                                    expanded[section.key] = true
                                    scope.launch { listState.animateScrollToItem(headerItems + index) }
                                }, label = { Text(section.title) }, modifier = Modifier.testTag("event_chip_${section.key}"))
                            }
                        }
                    }
                    item(key = "fallback") {
                        when {
                            loading && sections.isEmpty() -> LinearProgressIndicator(Modifier.fillMaxWidth())
                            sections.isEmpty() -> FeedDetails(event)
                        }
                    }
                    itemsIndexed(sections, key = { index, section -> "section-$index-${section.key}" }) { _, section ->
                        val open = expanded[section.key] ?: (section.key !in COLLAPSED_SECTIONS)
                        SectionCard(section, open, onToggle = { expanded[section.key] = !open })
                    }
                }
            }
        }
    }
}

/** What the feed alone knows, for events without a LeekDuck page. */
@Composable
private fun FeedDetails(event: GameEvent) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (event.featured.isNotEmpty()) { SectionTitle("Featured Pokémon"); PokemonRow(event.featured) }
        if (event.raidBosses.isNotEmpty()) { SectionTitle("Raid bosses"); PokemonRow(event.raidBosses) }
        if (event.shinies.isNotEmpty()) { SectionTitle("Shinies"); PokemonRow(event.shinies) }
        if (event.bonuses.isNotEmpty()) {
            SectionTitle("Bonuses")
            BonusRows(event.bonuses)
            event.bonusDisclaimers.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (event.featured.isEmpty() && event.raidBosses.isEmpty() && event.bonuses.isEmpty()) {
            Text("LeekDuck has no further details for this event yet.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionCard(section: EventSection, open: Boolean, onToggle: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().testTag("event_section_${section.key}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                section.icon?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(24.dp)) }
                Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (open) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (open) section.blocks.forEach { EventBlockView(it) }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EventBlockView(block: EventBlock) {
    when (block.type) {
        "heading" -> block.text?.let {
            Text(it, style = if ((block.level ?: 2) <= 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
        "text" -> block.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        "list" -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            block.strings().forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        "pokemon" -> PokemonGrid(block.pokemon())
        "bonuses" -> BonusRows(block.bonuses())
        "research" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { block.tasks.forEach { ResearchTask(it) } }
        "specialResearch" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { block.steps.forEach { ResearchStep(it) } }
        "moves" -> block.moves().forEach { move ->
            Surface(color = (move.type?.let(::typeColor) ?: MaterialTheme.colorScheme.secondary).copy(alpha = 0.16f),
                shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    move.pokemon?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                    Text(listOfNotNull(move.move, move.category).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        "image" -> block.url?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
        }
        else -> Unit
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PokemonGrid(pokemon: List<EventPokemon>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pokemon.forEach { p -> PokemonTile(p.name, p.image, p.type, p.shinyAvailable, caption = null) }
    }
}

/** A Pokémon or reward: icon on a disc tinted by its type, name, ✨ when shiny, and an optional caption (CP, quantity). */
@Composable
private fun PokemonTile(name: String?, image: String?, type: String?, shiny: Boolean, caption: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(Modifier.size(52.dp).clip(CircleShape).background((type?.let(::typeColor) ?: MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center) {
                AsyncImage(model = image, contentDescription = name, modifier = Modifier.size(46.dp))
            }
            if (shiny) Text("✨", style = MaterialTheme.typography.labelSmall)
        }
        name?.let { Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        caption?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun BonusRows(bonuses: List<EventBonus>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        bonuses.forEach { bonus ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                bonus.image?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(28.dp)) }
                Text(bonus.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** "CP 389–422" for a Pokémon reward, "×20" for an item. */
internal fun rewardCaption(reward: EventReward): String? = when {
    reward.minCp != null && reward.maxCp != null -> "CP ${reward.minCp}–${reward.maxCp}"
    reward.quantity != null -> "×${reward.quantity}"
    else -> null
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ResearchTask(task: EventTask) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("event_task")) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.task, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (task.rewards.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                task.rewards.forEach { reward -> PokemonTile(reward.name, reward.image, reward.type, reward.shiny, rewardCaption(reward)) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ResearchStep(step: EventResearchStep) {
    var open by remember(step) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.testTag("event_step_${step.number}"),
                verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull(step.number?.let { "Step $it" }, step.name).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(if (open) "Hide" else "${step.tasks.size} tasks", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (open) {
                step.tasks.forEach { ResearchTask(it) }
                if (step.rewards.isNotEmpty()) {
                    Text("Step rewards", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        step.rewards.forEach { PokemonTile(it.name, it.image, it.type, it.shiny, rewardCaption(it)) }
                    }
                }
            }
        }
    }
}

