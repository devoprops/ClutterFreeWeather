package com.clutterfree.weather.ui.radar

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.clutterfree.weather.R
import com.clutterfree.weather.data.UsPlaceIndex
import com.clutterfree.weather.radar.RadarSource
import com.clutterfree.weather.radar.TileKey
import com.clutterfree.weather.radar.UsOutlines
import com.clutterfree.weather.radar.WebMercator
import com.clutterfree.weather.radar.frameOffsetMinutes
import com.clutterfree.weather.radar.radarTileZoom
import com.clutterfree.weather.radar.tilesCovering
import com.clutterfree.weather.radar.zoomForMiles
import com.clutterfree.weather.ui.DateFormats
import com.clutterfree.weather.ui.theme.Dimens
import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun RadarPane(
    place: Place,
    startMiles: Int,
    placeIndex: UsPlaceIndex?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val source = remember { RadarSource() }
    val outlines by produceState<UsOutlines?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { RadarAssets.outlines(context) }.getOrNull() }
    }
    val homeX = WebMercator.x(place.longitude)
    val homeY = WebMercator.y(place.latitude)
    var centerX by rememberSaveable(place.displayName) { mutableDoubleStateOf(homeX) }
    var centerY by rememberSaveable(place.displayName) { mutableDoubleStateOf(homeY) }
    var zoom by rememberSaveable(place.displayName, startMiles) { mutableDoubleStateOf(Double.NaN) }
    var frame by rememberSaveable { mutableIntStateOf(AppConfig.RADAR_FRAMES - 1) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var showRadar by rememberSaveable { mutableStateOf(true) }
    var showCounties by rememberSaveable { mutableStateOf(true) }
    var showTowns by rememberSaveable { mutableStateOf(true) }
    var valid by remember { mutableStateOf<Instant?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val tiles = remember { mutableStateMapOf<TileKey, ImageBitmap>() }
    val zone = runCatching { ZoneId.of(place.timeZone) }.getOrElse { ZoneId.systemDefault() }

    fun worldPx(z: Double = zoom) = AppConfig.RADAR_TILE_PX * 2.0.pow(z)

    LaunchedEffect(Unit) {
        while (true) {
            val latest = withContext(Dispatchers.IO) { source.latestValid() }
            unavailable = latest == null && valid == null
            if (latest != null && latest != valid) {
                tiles.clear()
                valid = latest
            }
            delay(Duration.ofMinutes(AppConfig.RADAR_REFRESH_MINUTES).toMillis())
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            delay(if (frame == AppConfig.RADAR_FRAMES - 1) AppConfig.RADAR_LOOP_HOLD_MILLIS else AppConfig.RADAR_FRAME_MILLIS)
            frame = (frame + 1) % AppConfig.RADAR_FRAMES
        }
    }
    val needed by remember {
        derivedStateOf {
            if (zoom.isNaN() || size == IntSize.Zero || !showRadar) return@derivedStateOf emptyList()
            val world = worldPx()
            val z = radarTileZoom(zoom)
            val cells = tilesCovering(
                z,
                centerX - size.width / 2.0 / world,
                centerY - size.height / 2.0 / world,
                centerX + size.width / 2.0 / world,
                centerY + size.height / 2.0 / world,
            )
            val frames = if (playing) 0 until AppConfig.RADAR_FRAMES else frame..frame
            frames.flatMap { f -> cells.map { (x, y) -> TileKey(f, z, x, y) } }
        }
    }
    LaunchedEffect(needed, valid) {
        if (valid == null) return@LaunchedEffect
        val gate = Semaphore(AppConfig.RADAR_TILE_REQUESTS)
        if (tiles.size > AppConfig.RADAR_TILE_CACHE) tiles.keys.filter { it !in needed }.forEach { tiles.remove(it) }
        needed.filter { it !in tiles }.forEach { key ->
            launch {
                gate.withPermit { withContext(Dispatchers.IO) { source.tile(key) } }?.let { tiles[key] = it }
            }
        }
    }

    val measurer = rememberTextMeasurer(cacheSize = AppConfig.RADAR_MAX_LABELS)
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    val stateColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppConfig.RADAR_STATE_ALPHA)
    val countyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = AppConfig.RADAR_COUNTY_ALPHA)
    val markerColor = MaterialTheme.colorScheme.primary
    val markerRing = MaterialTheme.colorScheme.surface
    val labelStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        shadow = Shadow(color = MaterialTheme.colorScheme.surface, blurRadius = AppConfig.RADAR_LABEL_HALO_PX),
    )

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    size = it
                    if (zoom.isNaN() && it.width > 0) zoom = zoomForMiles(startMiles.toDouble(), place.latitude, it.width.toFloat())
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        if (zoom.isNaN()) return@detectTransformGestures
                        val oldWorld = worldPx()
                        val nextZoom = (zoom + log2(gestureZoom.toDouble())).coerceIn(AppConfig.RADAR_MIN_ZOOM, AppConfig.RADAR_MAX_ZOOM)
                        val newWorld = worldPx(nextZoom)
                        val pointX = (centerX * oldWorld - size.width / 2.0 + centroid.x) / oldWorld
                        val pointY = (centerY * oldWorld - size.height / 2.0 + centroid.y) / oldWorld
                        centerX = (pointX * newWorld - centroid.x - pan.x + size.width / 2.0) / newWorld
                        centerY = ((pointY * newWorld - centroid.y - pan.y + size.height / 2.0) / newWorld).coerceIn(0.0, 1.0)
                        zoom = nextZoom
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { tap ->
                        if (zoom.isNaN()) return@detectTapGestures
                        val oldWorld = worldPx()
                        val nextZoom = (zoom + 1).coerceAtMost(AppConfig.RADAR_MAX_ZOOM)
                        val newWorld = worldPx(nextZoom)
                        val pointX = (centerX * oldWorld - size.width / 2.0 + tap.x) / oldWorld
                        val pointY = (centerY * oldWorld - size.height / 2.0 + tap.y) / oldWorld
                        centerX = (pointX * newWorld - tap.x + size.width / 2.0) / newWorld
                        centerY = ((pointY * newWorld - tap.y + size.height / 2.0) / newWorld).coerceIn(0.0, 1.0)
                        zoom = nextZoom
                    })
                },
        ) {
            drawRect(background)
            if (zoom.isNaN()) return@Canvas
            val world = worldPx()
            val originX = centerX * world - this.size.width / 2
            val originY = centerY * world - this.size.height / 2
            fun screen(x: Double, y: Double) = Offset((x * world - originX).toFloat(), (y * world - originY).toFloat())
            val minX = originX / world
            val minY = originY / world
            val maxX = (originX + this.size.width) / world
            val maxY = (originY + this.size.height) / world

            if (showRadar) {
                val z = radarTileZoom(zoom)
                val tileSize = world / (1 shl z)
                tilesCovering(z, minX, minY, maxX, maxY).forEach { (x, y) ->
                    val image = tiles[TileKey(frame, z, x, y)] ?: return@forEach
                    val left = (x * tileSize - originX).roundToInt()
                    val top = (y * tileSize - originY).roundToInt()
                    val right = ((x + 1) * tileSize - originX).roundToInt()
                    val bottom = ((y + 1) * tileSize - originY).roundToInt()
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(left, top),
                        dstSize = IntSize(right - left, bottom - top),
                        alpha = AppConfig.RADAR_ALPHA,
                    )
                }
            }

            outlines?.let { map ->
                val counties = Path()
                val states = Path()
                map.lines.forEach { line ->
                    if (!line.state && !showCounties) return@forEach
                    if (line.maxX < minX || line.minX > maxX || line.maxY < minY || line.minY > maxY) return@forEach
                    val path = if (line.state) states else counties
                    val points = line.points
                    var index = 0
                    while (index < points.size) {
                        val at = screen(points[index].toDouble(), points[index + 1].toDouble())
                        if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
                        index += 2
                    }
                }
                drawPath(counties, countyColor, style = Stroke(Dimens.radarCountyLine.toPx()))
                drawPath(states, stateColor, style = Stroke(Dimens.radarStateLine.toPx()))
            }

            if (showTowns && placeIndex != null) {
                val placed = mutableListOf<Rect>()
                val gap = Dimens.radarLabelGap.toPx()
                placeIndex.within(
                    WebMercator.latitude(maxY),
                    WebMercator.latitude(minY),
                    WebMercator.longitude(minX),
                    WebMercator.longitude(maxX),
                ).forEach { town ->
                    if (placed.size >= AppConfig.RADAR_MAX_LABELS) return@forEach
                    val at = screen(WebMercator.x(town.longitude), WebMercator.y(town.latitude))
                    if (placed.any { it.inflate(gap).contains(at) }) return@forEach
                    val layout = measurer.measure(town.name, labelStyle)
                    val box = Rect(
                        Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f),
                        androidx.compose.ui.geometry.Size(layout.size.width.toFloat(), layout.size.height.toFloat()),
                    )
                    if (placed.any { it.inflate(gap).overlaps(box) }) return@forEach
                    placed += box
                    drawText(layout, topLeft = box.topLeft)
                }
            }

            val home = screen(homeX, homeY)
            drawCircle(markerRing, Dimens.radarMarker.toPx() + Dimens.radarMarkerRing.toPx(), home)
            drawCircle(markerColor, Dimens.radarMarker.toPx(), home)
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(Dimens.cardGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap),
            ) {
                FilterChip(showRadar, { showRadar = !showRadar }, { Text(stringResource(R.string.radar_layer_radar)) })
                FilterChip(showCounties, { showCounties = !showCounties }, { Text(stringResource(R.string.radar_layer_counties)) })
                FilterChip(showTowns, { showTowns = !showTowns }, { Text(stringResource(R.string.radar_layer_towns)) })
            }
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = Dimens.hairline) {
                IconButton(onClick = {
                    centerX = homeX
                    centerY = homeY
                    if (size.width > 0) zoom = zoomForMiles(startMiles.toDouble(), place.latitude, size.width.toFloat())
                }) {
                    Icon(painterResource(R.drawable.ic_my_location), stringResource(R.string.radar_recenter, place.displayName))
                }
            }
        }

        Surface(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Dimens.cardGap),
            shape = MaterialTheme.shapes.large,
            tonalElevation = Dimens.hairline,
        ) {
            Column(Modifier.padding(horizontal = Dimens.cardGap)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { playing = !playing }) {
                        Icon(
                            painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                            stringResource(if (playing) R.string.radar_pause else R.string.radar_play),
                        )
                    }
                    val sliderState = remember {
                        SliderState(
                            value = frame.toFloat(),
                            steps = AppConfig.RADAR_FRAMES - 2,
                            trackRange = 0f..(AppConfig.RADAR_FRAMES - 1).toFloat(),
                        )
                    }
                    LaunchedEffect(frame) { sliderState.value = frame.toFloat() }
                    Slider(
                        state = sliderState,
                        onValueChange = {
                            playing = false
                            sliderState.value = it
                            frame = it.roundToInt()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    val frameTime = valid?.minus(Duration.ofMinutes(frameOffsetMinutes(frame)))
                        ?.atZone(zone)?.format(DateFormats.clock)
                    Text(
                        text = when {
                            unavailable -> stringResource(R.string.radar_unavailable)
                            frameTime == null -> stringResource(R.string.missing)
                            frame == AppConfig.RADAR_FRAMES - 1 -> stringResource(R.string.radar_frame_latest, frameTime)
                            else -> frameTime
                        },
                        modifier = Modifier.padding(start = Dimens.cardGap),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = stringResource(R.string.radar_credit),
                    modifier = Modifier.padding(bottom = Dimens.tightGap),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private object RadarAssets {
    @Volatile
    private var outlines: UsOutlines? = null

    fun outlines(context: Context): UsOutlines = outlines ?: synchronized(this) {
        outlines ?: context.assets.open(OUTLINES_ASSET).bufferedReader().use { UsOutlines.parse(it.readText()) }
            .also { outlines = it }
    }
}

private const val OUTLINES_ASSET = "us_counties_10m.json"
