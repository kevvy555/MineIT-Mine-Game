package com.mineit.minegame.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mineit.minegame.domain.MineLevel
import com.mineit.minegame.domain.MinePoint2D
import com.mineit.minegame.domain.MinePoint3D
import com.mineit.minegame.domain.MinePrismController
import com.mineit.minegame.domain.MinePrismState
import kotlin.math.hypot
import kotlin.math.min

private const val MIN_CAMERA_ZOOM = 0.65f
private const val MAX_CAMERA_ZOOM = 3.5f
private const val PRISM_WORLD_HALF_X = 58f
private const val PRISM_WORLD_HALF_Y = 42f

@Composable
fun MinePrismScreen(
    viewModel: MinePrismViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var cameraPan by remember { mutableStateOf(Offset.Zero) }
    var cameraZoom by remember { mutableStateOf(1f) }

    fun resetCamera() {
        cameraPan = Offset.Zero
        cameraZoom = 1f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12161C)),
    ) {
        PrismHeader(state)

        MineViewport(
            state = state,
            pan = cameraPan,
            zoom = cameraZoom,
            onTransform = { panChange, zoomChange ->
                cameraPan += panChange
                cameraZoom = (cameraZoom * zoomChange).coerceIn(MIN_CAMERA_ZOOM, MAX_CAMERA_ZOOM)
            },
            onSelectLevel = { levelId ->
                viewModel.selectLevel(levelId)
                resetCamera()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        PrismControls(
            state = state,
            onDepthChange = viewModel::setCutDepth,
            onToggleExploded = viewModel::toggleExploded,
            onBackToPrism = {
                viewModel.closeLevel()
                resetCamera()
            },
            onReset = {
                viewModel.reset()
                resetCamera()
            },
        )
    }
}

@Composable
private fun PrismHeader(state: MinePrismState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "MINEIT // MINE PRISM 0.2.0",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.selectedLevel?.let { "${it.name.uppercase()} // TOP-DOWN" }
                    ?: if (state.exploded) "EXPLODED MINE VIEW" else "GEOLOGICAL PRISM VIEW",
                color = Color(0xFF80CBC4),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${state.cutDepthMetres.toInt()} m",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "slice depth",
                color = Color(0xFF9AA4B2),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MineViewport(
    state: MinePrismState,
    pan: Offset,
    zoom: Float,
    onTransform: (Offset, Float) -> Unit,
    onSelectLevel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val transformModifier = modifier.pointerInput(Unit) {
        detectTransformGestures { _, panChange, zoomChange, _ ->
            onTransform(panChange, zoomChange)
        }
    }

    val interactionModifier = if (state.selectedLevel == null) {
        transformModifier.pointerInput(state.cutDepthMetres, state.exploded, pan, zoom) {
            detectTapGestures { tap ->
                hitTestLevel(
                    tap = tap,
                    canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                    state = state,
                    pan = pan,
                    zoom = zoom,
                )?.let(onSelectLevel)
            }
        }
    } else {
        transformModifier
    }

    Canvas(modifier = interactionModifier) {
        if (state.selectedLevel != null) {
            drawFocusedLevel(
                level = state.selectedLevel!!,
                pan = pan,
                zoom = zoom,
            )
        } else {
            drawMinePrism(
                state = state,
                pan = pan,
                zoom = zoom,
            )
        }
    }
}

private fun DrawScope.drawMinePrism(
    state: MinePrismState,
    pan: Offset,
    zoom: Float,
) {
    drawRect(Color(0xFF11161D))

    val scale = prismScale(size, zoom)
    val origin = prismOrigin(size, pan)
    val maxDepth = MinePrismController.MAX_DEPTH_METRES

    fun projected(point: MinePoint3D, extraY: Float = 0f): Offset = projectPrism(
        point = point,
        origin = origin,
        scale = scale,
        canvasHeight = size.height,
        extraY = extraY,
    )

    val cutDepth = state.cutDepthMetres
    val cutCorners = prismCorners(cutDepth).map { projected(it) }
    val bottomCorners = prismCorners(maxDepth).map { projected(it) }
    val surfaceCorners = prismCorners(0f).map { projected(it) }

    drawPolygon(
        listOf(cutCorners[1], cutCorners[2], bottomCorners[2], bottomCorners[1]),
        Color(0xFF4D535A),
    )
    drawPolygon(
        listOf(cutCorners[2], cutCorners[3], bottomCorners[3], bottomCorners[2]),
        Color(0xFF42474E),
    )
    drawPolygon(
        listOf(cutCorners[3], cutCorners[0], bottomCorners[0], bottomCorners[3]),
        Color(0xFF383E45),
    )
    drawPolygon(cutCorners, Color(0xFF5C6269))
    drawPolygon(cutCorners, Color(0xFF9AA2AA), Stroke(width = 1.5f * scale))

    drawPolygon(surfaceCorners, Color(0x2239A8DB))
    drawPolygon(surfaceCorners, Color(0xFF5DB7E8), Stroke(width = 1.8f * scale))

    val shaftTop = projected(MinePoint3D(0f, 0f, 0f))
    val shaftBottom = projected(MinePoint3D(0f, 0f, cutDepth))
    drawLine(
        color = Color(0xFFF9C74F),
        start = shaftTop,
        end = shaftBottom,
        strokeWidth = 4.5f * scale,
    )
    drawCircle(Color(0xFFFFD166), radius = 7f * scale, center = shaftTop)
    drawLabel("SURFACE / SHAFT", shaftTop + Offset(12f * scale, -8f * scale), scale, Color.White)

    val visibleOre = state.oreBody.filter { it.centre.depthMetres <= cutDepth }
    visibleOre.zipWithNext().forEach { (a, b) ->
        drawLine(
            color = Color(0xFFB56CFF),
            start = projected(a.centre),
            end = projected(b.centre),
            strokeWidth = ((a.radiusMetres + b.radiusMetres) * 0.20f * scale).coerceAtLeast(5f),
        )
    }
    visibleOre.forEach { sample ->
        drawCircle(
            color = Color(0xFFD39BFF),
            radius = sample.radiusMetres * 0.30f * scale,
            center = projected(sample.centre),
        )
        drawCircle(
            color = Color(0xFF7A3FC2),
            radius = sample.radiusMetres * 0.30f * scale,
            center = projected(sample.centre),
            style = Stroke(width = 1.6f * scale),
        )
    }

    state.levels.forEachIndexed { index, level ->
        if (level.depthMetres <= cutDepth) {
            val extraY = explodedOffset(state, index, scale)
            val corners = levelDiamond(level.depthMetres).map { projected(it, extraY) }
            val centre = projected(MinePoint3D(0f, 0f, level.depthMetres), extraY)

            drawPolygon(corners, Color(0xCC20262D))
            drawPolygon(corners, Color(0xFF78D5C4), Stroke(width = 2f * scale))
            drawCircle(Color(0xFFFFD166), radius = 5.5f * scale, center = centre)
            drawLine(
                color = Color(0xFFCBD3DA),
                start = centre + Offset(-38f * scale, 0f),
                end = centre + Offset(42f * scale, 0f),
                strokeWidth = 4.5f * scale,
            )
            drawLabel(
                "${level.name}  ${level.depthMetres.toInt()}m",
                centre + Offset(48f * scale, -4f * scale),
                scale,
                Color.White,
            )
        }
    }

    drawLabel(
        if (state.exploded) "EXPLODED: levels separated for readability"
        else "Drag / pinch • scrub depth • tap a revealed level",
        Offset(18f, size.height - 24f),
        1f,
        Color(0xFF80CBC4),
    )
}

private fun DrawScope.drawFocusedLevel(
    level: MineLevel,
    pan: Offset,
    zoom: Float,
) {
    drawRect(Color(0xFF4D535A))

    val scale = min(size.width / 190f, size.height / 180f) * zoom
    val centre = Offset(size.width / 2f + pan.x, size.height / 2f + pan.y)

    fun map(point: MinePoint2D): Offset = Offset(
        x = centre.x + point.x * scale,
        y = centre.y + point.y * scale,
    )

    level.drifts.forEach { drift ->
        val path = Path().apply {
            val first = map(drift.first())
            moveTo(first.x, first.y)
            drift.drop(1).forEach { point ->
                val p = map(point)
                lineTo(p.x, p.y)
            }
        }
        drawPath(
            path = path,
            color = Color(0xFF171C22),
            style = Stroke(width = 18f * scale),
        )
        drawPath(
            path = path,
            color = Color(0xFF2B323A),
            style = Stroke(width = 11f * scale),
        )
    }

    val orePath = Path().apply {
        val first = map(level.oreTrace.first())
        moveTo(first.x, first.y)
        level.oreTrace.drop(1).forEach { point ->
            val p = map(point)
            lineTo(p.x, p.y)
        }
    }
    drawPath(
        path = orePath,
        color = Color(0xFFC77DFF),
        style = Stroke(width = 13f * scale),
    )
    drawPath(
        path = orePath,
        color = Color(0xFF8D45C7),
        style = Stroke(width = 2f * scale),
    )

    drawCircle(
        color = Color(0xFFFFD166),
        radius = 10f * scale,
        center = centre,
    )
    drawCircle(
        color = Color(0xFF20262D),
        radius = 5f * scale,
        center = centre,
    )

    val machineA = map(MinePoint2D(-55f, 0f))
    val machineB = map(MinePoint2D(55f, 0f))
    drawRect(
        color = Color(0xFFF6AE2D),
        topLeft = machineA - Offset(7f * scale, 4f * scale),
        size = Size(14f * scale, 8f * scale),
    )
    drawRect(
        color = Color(0xFFF6AE2D),
        topLeft = machineB - Offset(7f * scale, 4f * scale),
        size = Size(14f * scale, 8f * scale),
    )

    drawLabel("${level.name.uppercase()} // ${level.depthMetres.toInt()}m", Offset(18f, 28f), 1f, Color.White)
    drawLabel("SHAFT STATION", centre + Offset(12f * scale, -10f * scale), scale, Color(0xFFFFE29A))
    drawLabel("FACE A", machineA + Offset(-10f * scale, -12f * scale), scale, Color.White)
    drawLabel("FACE B", machineB + Offset(-10f * scale, -12f * scale), scale, Color.White)
    drawLabel("Purple = ore-body slice", Offset(18f, size.height - 24f), 1f, Color(0xFFE2C5FF))
}

@Composable
private fun PrismControls(
    state: MinePrismState,
    onDepthChange: (Float) -> Unit,
    onToggleExploded: () -> Unit,
    onBackToPrism: () -> Unit,
    onReset: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF20262D),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (state.selectedLevel != null) {
                Text(
                    text = "TOP-DOWN LEVEL VIEW",
                    color = Color(0xFF80CBC4),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "Pinch and drag to inspect this working horizon. The purple trace is the level's slice through the same 3D ore body.",
                    color = Color(0xFFB5BEC8),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onBackToPrism,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF356B75)),
                    ) {
                        Text("BACK TO PRISM")
                    }
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("RESET")
                    }
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PEEL ROCK TO DEPTH",
                    color = Color(0xFFB5BEC8),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "${state.cutDepthMetres.toInt()} m",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            Slider(
                value = state.cutDepthMetres,
                onValueChange = onDepthChange,
                valueRange = MinePrismController.MIN_DEPTH_METRES..MinePrismController.MAX_DEPTH_METRES,
            )

            Text(
                text = "Slide deeper to reveal the shaft, ore body and working levels inside the geological prism.",
                color = Color(0xFF9AA4B2),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onToggleExploded,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.exploded) Color(0xFF7A4A9E) else Color(0xFF356B75),
                    ),
                ) {
                    Text(if (state.exploded) "COLLAPSE LEVELS" else "EXPLODE LEVELS")
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("RESET")
                }
            }
        }
    }
}

private fun hitTestLevel(
    tap: Offset,
    canvasSize: Size,
    state: MinePrismState,
    pan: Offset,
    zoom: Float,
): String? {
    val scale = prismScale(canvasSize, zoom)
    val origin = prismOrigin(canvasSize, pan)

    return state.levels
        .mapIndexedNotNull { index, level ->
            if (level.depthMetres > state.cutDepthMetres) {
                null
            } else {
                val centre = projectPrism(
                    point = MinePoint3D(0f, 0f, level.depthMetres),
                    origin = origin,
                    scale = scale,
                    canvasHeight = canvasSize.height,
                    extraY = explodedOffset(state, index, scale),
                )
                level.id to hypot((tap.x - centre.x).toDouble(), (tap.y - centre.y).toDouble())
            }
        }
        .filter { (_, distance) -> distance <= 48f * scale }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

private fun prismScale(size: Size, zoom: Float): Float =
    min(size.width / 410f, size.height / 560f) * zoom

private fun prismOrigin(size: Size, pan: Offset): Offset =
    Offset(
        x = size.width / 2f + pan.x,
        y = 72f + pan.y,
    )

private fun projectPrism(
    point: MinePoint3D,
    origin: Offset,
    scale: Float,
    canvasHeight: Float,
    extraY: Float = 0f,
): Offset {
    val verticalExtent = (canvasHeight * 0.64f).coerceIn(230f, 390f) * scale
    val depthFraction = point.depthMetres / MinePrismController.MAX_DEPTH_METRES
    return Offset(
        x = origin.x + ((point.x - point.y) * 1.45f * scale),
        y = origin.y +
            ((point.x + point.y) * 0.46f * scale) +
            (depthFraction * verticalExtent) +
            extraY,
    )
}

private fun prismCorners(depthMetres: Float): List<MinePoint3D> = listOf(
    MinePoint3D(-PRISM_WORLD_HALF_X, -PRISM_WORLD_HALF_Y, depthMetres),
    MinePoint3D(PRISM_WORLD_HALF_X, -PRISM_WORLD_HALF_Y, depthMetres),
    MinePoint3D(PRISM_WORLD_HALF_X, PRISM_WORLD_HALF_Y, depthMetres),
    MinePoint3D(-PRISM_WORLD_HALF_X, PRISM_WORLD_HALF_Y, depthMetres),
)

private fun levelDiamond(depthMetres: Float): List<MinePoint3D> = listOf(
    MinePoint3D(-45f, -30f, depthMetres),
    MinePoint3D(45f, -30f, depthMetres),
    MinePoint3D(45f, 30f, depthMetres),
    MinePoint3D(-45f, 30f, depthMetres),
)

private fun explodedOffset(state: MinePrismState, index: Int, scale: Float): Float =
    if (state.exploded) index * 34f * scale else 0f

private fun DrawScope.drawPolygon(
    points: List<Offset>,
    color: Color,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle = androidx.compose.ui.graphics.drawscope.Fill,
) {
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path = path, color = color, style = style)
}

private fun DrawScope.drawLabel(
    text: String,
    position: Offset,
    scale: Float,
    color: Color,
) {
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color.toArgb()
        textSize = (12f * scale).coerceIn(10f, 22f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    drawContext.canvas.nativeCanvas.drawText(text, position.x, position.y, paint)
}

private fun Color.toArgb(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
