package com.example.pokemonalertsv2.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.example.pokemonalertsv2.PokemonAlertsApplication
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.events.EventsRepository
import com.example.pokemonalertsv2.events.eventTypeName
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class EventsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = EventsFactory(
        applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    )
}

/**
 * Reads the calendar the Events tab keeps on disk; it fetches only when that copy is missing or
 * older than [STALE_AFTER_MILLIS], so a widget on a fresh install is not empty until the tab is
 * opened. Runs on the widget host's binder thread, where blocking is expected.
 */
private class EventsFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {
    private var rows: List<EventsWidgetRow> = emptyList()
    private var palette: WidgetThemePalette = WidgetThemePalette.Dark
    private val images = HashMap<String, Bitmap?>()

    override fun onCreate() = Unit

    /**
     * Reads the stored calendar only: the fetch happens in [EventsWidgetProvider]'s update, off
     * this call. On Android 16 the widget host interrupts a list load that runs long, and an
     * interrupted runBlocking throws, which took the whole app down; the rows from the last load
     * stay up instead, and artwork gets a bounded time.
     */
    override fun onDataSetChanged() {
        try {
            runBlocking {
                palette = resolveWidgetThemePalette(context)
                val events = EventsRepository(context).cached()
                val shown = EventsWidgetSettings.shownTypes(context, appWidgetId)
                rows = eventsWidgetRows(events, System.currentTimeMillis(), shown)
                // Artwork is loaded here, off the per-row call, and kept while the image URLs stay.
                val wanted = rows.mapNotNull { (it as? EventsWidgetRow.Event)?.event?.image }.toSet()
                images.keys.retainAll(wanted)
                withTimeoutOrNull(IMAGE_BUDGET_MILLIS) {
                    wanted.filterNot(images::containsKey).forEach { url -> images[url] = loadImage(url) }
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private suspend fun loadImage(url: String): Bitmap? {
        val sizePx = (40 * context.resources.displayMetrics.density).toInt()
        val request = ImageRequest.Builder(context).data(url).size(sizePx, sizePx).allowHardware(false).build()
        val result = PokemonAlertsApplication.imageLoader(context).execute(request)
        return (result as? SuccessResult)?.drawable?.toBitmap(sizePx, sizePx)
    }

    override fun onDestroy() {
        rows = emptyList()
        images.clear()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? = when (val row = rows.getOrNull(position)) {
        null -> null
        is EventsWidgetRow.Header -> RemoteViews(context.packageName, R.layout.widget_event_header).apply {
            setTextViewText(R.id.event_section_label, row.title)
            setTextColor(R.id.event_section_label, palette.primary)
        }
        is EventsWidgetRow.Event -> RemoteViews(context.packageName, R.layout.widget_event_item).apply {
            setInt(R.id.event_item_root, "setBackgroundResource", palette.itemDrawable)
            setTextViewText(R.id.event_item_title, row.event.name)
            setTextColor(R.id.event_item_title, palette.onSurface)
            setTextViewText(R.id.event_item_type, eventTypeName(row.event.eventType))
            setTextColor(R.id.event_item_type, palette.onSurfaceVariant)
            setTextViewText(R.id.event_item_when, row.detail)
            setTextColor(R.id.event_item_when, if (row.running) palette.warning else palette.primary)
            val image = row.event.image?.let(images::get)
            if (image != null) {
                setViewVisibility(R.id.event_item_image, View.VISIBLE)
                setImageViewBitmap(R.id.event_item_image, image)
            } else {
                setViewVisibility(R.id.event_item_image, View.GONE)
            }
            setOnClickFillInIntent(
                R.id.event_item_root,
                Intent().setData(EventsWidgetProvider.eventDeepLink(row.event.id))
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = when (val row = rows.getOrNull(position)) {
        is EventsWidgetRow.Header -> row.title.hashCode().toLong()
        is EventsWidgetRow.Event -> row.event.id.hashCode().toLong() shl 1
        null -> position.toLong()
    }

    override fun hasStableIds(): Boolean = true

    private companion object {
        const val IMAGE_BUDGET_MILLIS = 4_000L
    }
}
