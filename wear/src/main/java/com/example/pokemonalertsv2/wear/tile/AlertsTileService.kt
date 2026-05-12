package com.example.pokemonalertsv2.wear.tile

import android.content.Intent
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileService
import com.example.pokemonalertsv2.wear.MainActivity
import com.example.pokemonalertsv2.wear.data.PokemonAlertWearModel
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class AlertsTileService : TileService() {
    private val json = Json { ignoreUnknownKeys = true }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<androidx.wear.tiles.TileBuilders.Tile> {
        return Futures.immediateFuture(
            androidx.wear.tiles.TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(getLayout(requestParams.deviceConfiguration))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                ).build()
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        )
    }

    private fun getLayout(deviceParameters: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val alerts = getAlertsFromPrefs()
        
        val content = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        
        val headerText = LayoutElementBuilders.Text.Builder()
            .setText(if (alerts.isEmpty()) "No Alerts" else "${alerts.size} Active Alerts")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .setSize(sp(16f))
                    .build()
            )
            .build()
            
        content.addContent(headerText)
        content.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
        
        if (alerts.isNotEmpty()) {
            val topAlert = alerts.first()
            
            val pokemonName = LayoutElementBuilders.Text.Builder()
                .setText(topAlert.cleanPokemonName)
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setColor(ColorBuilders.argb(0xFF89B4FA.toInt()))
                        .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                        .setSize(sp(14f))
                        .build()
                )
                .build()
            
            val details = LayoutElementBuilders.Text.Builder()
                .setText(buildString {
                    if (topAlert.iv != null) append("${topAlert.iv}% ")
                    append("until ${topAlert.endTime.substringAfter(" ").take(5)}")
                })
                .setFontStyle(
                    LayoutElementBuilders.FontStyle.Builder()
                        .setColor(ColorBuilders.argb(0xFFCCCCCC.toInt()))
                        .setSize(sp(12f))
                        .build()
                )
                .build()
                
            content.addContent(pokemonName)
            content.addContent(details)
            content.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
        }

        // Open App Button
        val button = LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open_app")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setClassName(MainActivity::class.java.name)
                                            .setPackageName(packageName)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(0xFF313244.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(16f)).build())
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(dp(8f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText("Open List")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setColor(ColorBuilders.argb(0xFFFFFFFF.toInt()))
                            .setSize(sp(14f))
                            .build()
                    )
                    .build()
            )
            .build()
            
        content.addContent(button)
        
        return content.build()
    }
    
    private fun getAlertsFromPrefs(): List<PokemonAlertWearModel> {
        val prefs = getSharedPreferences("wear_prefs", MODE_PRIVATE)
        val jsonString = prefs.getString("alerts_json", null)
        if (jsonString != null) {
            try {
                return json.decodeFromString(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return emptyList()
    }
}
