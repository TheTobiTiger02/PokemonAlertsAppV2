package com.example.pokemonalertsv2.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pokemonalertsv2.ui.components.GradientText
import com.example.pokemonalertsv2.ui.components.LinearModernBackground
import com.example.pokemonalertsv2.ui.components.LinearModernCard
import com.example.pokemonalertsv2.ui.theme.LocalLinearModernColors
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector
)

val pages = listOf(
    OnboardingPage(
        "Live Alerts",
        "Real-time intel on Pokémon spawns, Raids, and Research nearby.",
        Icons.Filled.Warning
    ),
    OnboardingPage(
        "Instant Alerts",
        "Get notified the second a rare Pokémon appears in your area.",
        Icons.Filled.Notifications
    ),
    OnboardingPage(
        "Track & Collect",
        "Save your favorites and filter the noise to catch 'em all.",
        Icons.Filled.Check
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex
    val colors = LocalLinearModernColors.current

    LinearModernBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex])
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .semantics {
                            stateDescription = "Page ${pagerState.currentPage + 1} of ${pages.size}"
                        }
                        .padding(bottom = 28.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(pages.size) { index ->
                            val dotColor = if (pagerState.currentPage == index) colors.accent else colors.borderHover
                            val dotWidth = if (pagerState.currentPage == index) 16.dp else 6.dp
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(dotWidth)
                                    .background(
                                        color = dotColor,
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isLastPage) {
                        TextButton(
                            onClick = onFinish,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colors.foregroundMuted
                            )
                        ) {
                            Text("Skip")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (isLastPage) {
                                onFinish()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(8.dp),
                            ambientColor = colors.accentGlow,
                            spotColor = colors.accentGlow
                        )
                    ) {
                        Text(
                            text = if (isLastPage) "Get started" else "Next",
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isLastPage) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    val colors = LocalLinearModernColors.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearModernCard(
            modifier = Modifier
                .size(140.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = colors.accentGlow,
                    spotColor = colors.accentGlow
                )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = colors.accent
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        GradientText(
            text = page.title,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.fillMaxWidth(),
            isAccent = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.foregroundMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
