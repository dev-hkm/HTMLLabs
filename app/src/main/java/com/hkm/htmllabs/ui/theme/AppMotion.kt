package com.hkm.htmllabs.ui.theme

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Centralized Apple Motion Token System.
 * Instant lift-off, unhurried feather settle, C1 spring continuity, and 120Hz GPU transforms.
 */
object AppMotion {
    // Canonical Apple cubic-bezier deceleration curves
    val AppleSheet = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val AppleSilk = CubicBezierEasing(0.28f, 0.11f, 0.14f, 1f)
    val AppleEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val AppleEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    // Calibrated unhurried duration tokens (ms)
    const val Screen = 480
    const val ScreenPop = 420
    const val Tab = 360
    const val Sheet = 460
    const val Content = 380
    const val Fast = 240
    const val Micro = 200

    // Perceptual springs (zero bounce on navigation/content, tactile rebound on press)
    fun <T> navigationSpring() = spring<T>(dampingRatio = 0.90f, stiffness = 230f)
    fun <T> fluidSpring() = spring<T>(dampingRatio = 0.88f, stiffness = 250f)
    fun <T> layoutSpring() = spring<T>(dampingRatio = 0.88f, stiffness = 265f)
    fun <T> gentleSpring() = spring<T>(dampingRatio = 0.92f, stiffness = 200f)
    fun <T> snappySpring() = spring<T>(dampingRatio = 0.80f, stiffness = 360f)
    fun <T> pressDownSpring() = spring<T>(dampingRatio = 0.86f, stiffness = 420f)
    fun <T> pressReleaseSpring() = spring<T>(dampingRatio = 0.68f, stiffness = 300f)
    fun <T> softPopSpring() = spring<T>(dampingRatio = 0.72f, stiffness = 380f)

    fun intSizeLayoutSpring() = spring<IntSize>(dampingRatio = 0.88f, stiffness = 265f)
    fun intOffsetFluidSpring() = spring<IntOffset>(dampingRatio = 0.88f, stiffness = 250f)

    // Accordion / search bar reveal (no bounce)
    val softExpandEnter: EnterTransition =
        expandVertically(animationSpec = tween(360, easing = AppleSheet)) +
            fadeIn(tween(320, easing = AppleSilk))

    val softExpandExit: ExitTransition =
        shrinkVertically(animationSpec = tween(260, easing = AppleSilk)) +
            fadeOut(tween(190, easing = AppleEase))

    // Floating dock / FAB reveal (scaled >= 0.94, never 0)
    val floatingBarEnter: EnterTransition =
        slideInVertically(tween(Sheet, easing = AppleSheet)) { it / 2 } +
            fadeIn(tween(340, easing = AppleSilk)) +
            scaleIn(tween(Sheet, easing = AppleSheet), initialScale = 0.94f)

    val floatingBarExit: ExitTransition =
        slideOutVertically(tween(300, easing = AppleSilk)) { it / 2 } +
            fadeOut(tween(220, easing = AppleEase)) +
            scaleOut(tween(300, easing = AppleSilk), targetScale = 0.95f)

    fun softCrossDissolve(): ContentTransform =
        fadeIn(tween(Content, easing = AppleSilk)) togetherWith
            fadeOut(tween(Fast, easing = AppleEase))
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        scale == 0f
    }
}

/**
 * Staggered wave entrance capped at 5 items so long grids stay responsive.
 * Animates exclusively inside graphicsLayer (zero layout pass per frame).
 */
fun Modifier.appleEntrance(
    index: Int = 0,
    stepDelayMs: Int = 46,
    translationYFromPx: Float = 26f,
    reduceMotion: Boolean = false
): Modifier = composed {
    if (reduceMotion) return@composed this

    var visible by remember { mutableStateOf(false) }
    val clampedDelay = index.coerceIn(0, 5) * stepDelayMs

    LaunchedEffect(Unit) {
        if (clampedDelay > 0) delay(clampedDelay.toLong())
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppMotion.Content, easing = AppMotion.AppleSilk),
        label = "entrance_alpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else translationYFromPx,
        animationSpec = tween(AppMotion.Content, easing = AppMotion.AppleSheet),
        label = "entrance_ty"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.975f,
        animationSpec = tween(AppMotion.Content, easing = AppMotion.AppleSheet),
        label = "entrance_scale"
    )

    graphicsLayer {
        this.alpha = alpha
        this.translationY = offsetY
        this.scaleX = scale
        this.scaleY = scale
    }
}

/**
 * Tactile press compression on ACTION_DOWN with spring rebound on release.
 */
fun Modifier.applePressable(
    pressedScale: Float = 0.965f,
    enabled: Boolean = true,
    enableHaptic: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = if (isPressed) AppMotion.pressDownSpring() else AppMotion.pressReleaseSpring(),
        label = "press_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                if (enableHaptic) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                onClick()
            }
        )
}

@Stable
class HeroBoundsRegistry {
    private val rects = mutableStateMapOf<String, Rect>()
    fun record(key: String, rect: Rect) { rects[key] = rect }
    operator fun get(key: String?): Rect? = if (key == null) null else rects[key]
}

val LocalHeroBounds = staticCompositionLocalOf<HeroBoundsRegistry> {
    error("LocalHeroBounds must be provided at root")
}

fun Modifier.heroSource(key: String, registry: HeroBoundsRegistry): Modifier =
    this.onGloballyPositioned { coords ->
        if (coords.isAttached) {
            registry.record(key, coords.boundsInRoot())
        }
    }

/**
 * Two-tier Apple layered navigation host:
 * - Layer 0 stays mounted with Z-depth parallax (0.945..1.0) and optical scrim.
 * - Layer 1 expands from HeroBounds (if present) or glides up as an AppleSheet,
 *   and supports Android 14+ PredictiveBackHandler edge peel with dynamic bezel rounding.
 */
@Composable
fun AppleLayeredOverlayHost(
    overlayKey: String?,
    heroKey: String?,
    onDismissOverlay: () -> Unit,
    baseContent: @Composable () -> Unit,
    overlayContent: @Composable (String) -> Unit
) {
    val heroBounds = LocalHeroBounds.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reduceMotion = rememberReduceMotion()

    // Track the visible overlay separately so exit animations play out smoothly.
    var displayedOverlayKey by remember { mutableStateOf<String?>(null) }
    var displayedHeroKey by remember { mutableStateOf<String?>(null) }
    val dismissProgress = remember { Animatable(1f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var startTouchY by remember { mutableFloatStateOf(0f) }
    var currentTouchY by remember { mutableFloatStateOf(0f) }
    var isPeeling by remember { mutableStateOf(false) }

    val activeHeroRect = remember(displayedOverlayKey, displayedHeroKey) {
        if (displayedOverlayKey != null) heroBounds[displayedHeroKey] else null
    }

    LaunchedEffect(overlayKey, heroKey) {
        if (overlayKey != null) {
            val isNewKey = displayedOverlayKey != overlayKey
            displayedOverlayKey = overlayKey
            displayedHeroKey = heroKey
            if (isNewKey) {
                dismissProgress.snapTo(1f)
                dismissProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = if (reduceMotion) tween(160) else AppMotion.navigationSpring()
                )
            }
        } else if (displayedOverlayKey != null) {
            val spec: AnimationSpec<Float> = if (reduceMotion) {
                tween(150)
            } else if (activeHeroRect != null) {
                AppMotion.navigationSpring()
            } else {
                tween(AppMotion.ScreenPop, easing = AppMotion.AppleSheet)
            }
            dismissProgress.animateTo(1f, spec)
            displayedOverlayKey = null
            displayedHeroKey = null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val rootW = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val rootH = with(density) { maxHeight.toPx().coerceAtLeast(1f) }

        val overlayVisible = displayedOverlayKey != null
        val reveal = if (overlayVisible) (1f - dismissProgress.value).coerceIn(0f, 1f) else 0f

        // LAYER 0: Base workspace with synchronized Z-depth parallax & optical scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val depthScale = if (reduceMotion) 1f else 1f - 0.055f * reveal
                    scaleX = depthScale
                    scaleY = depthScale
                    alpha = 1f - 0.10f * reveal
                }
        ) {
            baseContent()

            if (reveal > 0.001f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f * reveal))
                )
            }
        }

        // LAYER 1: Active detail / editor / settings overlay
        val currentKey = displayedOverlayKey
        if (currentKey != null) {
            PredictiveBackHandler(enabled = overlayKey != null) { events ->
                var committed = false
                isPeeling = true
                var capturedStart = false
                try {
                    events.collect { event ->
                        val raw = event.progress.coerceIn(0f, 1f)
                        val curved = raw * 0.70f + (1f - (1f - raw) * (1f - raw)) * 0.30f
                        swipeEdge = event.swipeEdge
                        if (!capturedStart) {
                            startTouchY = event.touchY
                            capturedStart = true
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        currentTouchY = event.touchY
                        dismissProgress.snapTo(curved)
                    }
                    committed = true
                } catch (_: CancellationException) {
                    committed = false
                } finally {
                    isPeeling = false
                }

                if (committed) {
                    val spec: AnimationSpec<Float> = if (reduceMotion) {
                        tween(140)
                    } else if (activeHeroRect != null) {
                        AppMotion.navigationSpring()
                    } else {
                        tween(280, easing = AppMotion.AppleSheet)
                    }
                    dismissProgress.animateTo(1f, spec)
                    displayedOverlayKey = null
                    displayedHeroKey = null
                    onDismissOverlay()
                } else {
                    dismissProgress.animateTo(0f, AppMotion.navigationSpring())
                }
            }

            val pReveal = (1f - dismissProgress.value).coerceIn(0f, 1f)
            val cornerDp = if (reduceMotion) 0.dp else {
                when {
                    activeHeroRect != null -> ((1f - pReveal) * 24f + if (isPeeling) dismissProgress.value * 30f else 0f)
                        .coerceIn(0f, 30f).dp
                    isPeeling || dismissProgress.value > 0.001f -> (dismissProgress.value * 30f).dp
                    else -> 0.dp
                }
            }

            Surface(
                shape = RoundedCornerShape(cornerDp),
                shadowElevation = if (dismissProgress.value > 0.01f) 16.dp else 0.dp,
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (reduceMotion) {
                            alpha = pReveal
                            return@graphicsLayer
                        }
                        if (activeHeroRect != null) {
                            val minScale = (activeHeroRect.width / rootW).coerceIn(0.22f, 0.88f)
                            val s = minScale + (1f - minScale) * pReveal
                            val peelBonusScale = if (isPeeling) 1f - 0.06f * dismissProgress.value else 1f
                            val edgeDir = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                            val deltaY = if (isPeeling) {
                                ((currentTouchY - startTouchY) * 0.06f).coerceIn(-12.dp.toPx(), 12.dp.toPx())
                            } else 0f
                            val peelOffsetX = if (isPeeling) edgeDir * 16.dp.toPx() * dismissProgress.value else 0f
                            scaleX = s * peelBonusScale
                            scaleY = s * peelBonusScale
                            translationX = (activeHeroRect.center.x - rootW / 2f) * (1f - pReveal) + peelOffsetX
                            translationY = (activeHeroRect.center.y - rootH / 2f) * (1f - pReveal) + deltaY * dismissProgress.value
                            rotationZ = if (isPeeling) edgeDir * (deltaY / 12.dp.toPx()) * 0.7f * dismissProgress.value else 0f
                            alpha = (pReveal / 0.18f).coerceIn(0f, 1f)
                        } else if (isPeeling) {
                            val d = dismissProgress.value
                            val s = 1f - 0.14f * d
                            val edgeDir = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                            val deltaY = ((currentTouchY - startTouchY) * 0.08f).coerceIn(-16.dp.toPx(), 16.dp.toPx())
                            scaleX = s
                            scaleY = s
                            translationX = edgeDir * 28.dp.toPx() * d
                            translationY = deltaY * d
                            rotationZ = edgeDir * (deltaY / 16.dp.toPx()) * 0.9f * d
                            alpha = (1f - (d - 0.72f).coerceAtLeast(0f) / 0.28f).coerceIn(0f, 1f)
                        } else {
                            val d = dismissProgress.value
                            translationY = rootH * 0.18f * d
                            val s = 1f - 0.04f * d
                            scaleX = s
                            scaleY = s
                            alpha = (1f - d * 1.15f).coerceIn(0f, 1f)
                        }
                    }
            ) {
                overlayContent(currentKey)
            }
        }
    }
}

/**
 * Apple / Tonser-style sliding segmented control with concentric corners and layoutSpring.
 */
@Composable
fun <T> AppleSegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colorScheme.secondary.copy(alpha = 0.75f))
            .padding(4.dp)
    ) {
        val pillWidth = maxWidth / options.size.coerceAtLeast(1)
        val pillOffsetX by animateDpAsState(
            targetValue = pillWidth * selectedIndex,
            animationSpec = AppMotion.layoutSpring(),
            label = "pill_offset"
        )

        // Concentric inner pill: 14dp outer - 4dp padding = 10dp
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colorScheme.primary,
            shadowElevation = 1.dp,
            modifier = Modifier
                .offset(x = pillOffsetX)
                .width(pillWidth)
                .height(36.dp)
        ) {}

        Row(Modifier.fillMaxWidth()) {
            options.forEach { item ->
                val isSelected = item == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isSelected) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onSelect(item)
                            }
                        }
                ) {
                    Text(
                        text = label(item),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) colorScheme.onPrimary else colorScheme.onSecondary.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
