package com.mineit.minegame.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mineit.minegame.domain.DigSimulation
import com.mineit.minegame.domain.DiggerState
import com.mineit.minegame.domain.WorldPoint
import kotlin.math.max

private const val MIN_CAMERA_ZOOM = 0.5f
private const val MAX_CAMERA_ZOOM = 4f

@Composable
fun DigGameScreen(
    viewModel: DigGameViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var cameraPanOffset by remember { mutableStateOf(Offset.Zero) }
    var cameraZoom by remember { mutableStateOf(1f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF171A1F)),
    ) {
        StatusPanel(state = state)

        MineCanvas(
            state = state,
            panOffset = cameraPanOffset,
            zoom = cameraZoom,
            onTransform = { pan, zoomChange ->
                cameraPanOffset += pan
                cameraZoom = (cameraZoom * zoomChange).coerceIn(MIN_CAMERA_ZOOM, MAX_CAMERA_ZOOM)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        ControlsPanel(
            state = state,
            onAngleChange = viewModel::setTargetHeading,
            onStart = viewModel::startDigging,
            onStop = viewModel::stopDigging,
            onReset = {
                viewModel.reset()
                cameraPanOffset = Offset.Zero
                cameraZoom = 1f
            },
        )
    }
}

@Composable
private fun StatusPanel(state: DiggerState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "MINEIT // DIG TEST 0.1.2",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (state.isDigging) "DIGGING" else "STOPPED",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.isDigging) Color(0xFF82D173) else Color(0xFFFFC857),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Depth ${"%.1f".format(state.depthMetres)} m",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Heading ${"%.0f".format(state.headingDegrees)}°",
                color = Color(0xFFB8C0CC),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MineCanvas(
    state: DiggerState,
    panOffset: Offset,
    zoom: Float,
    onTransform: (pan: Offset, zoomChange: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoomChange, _ ->
                onTransform(pan, zoomChange)
            }
        },
    ) {
        val baseMetresAcross = 28f
        val metresAcross = baseMetresAcross / zoom
        val pixelsPerMetre = size.width / metresAcross
        val viewportHeightMetres = size.height / pixelsPerMetre

        val initialCameraTop = -viewportHeightMetres * 0.24f
        val followCameraTop = state.position.yMetres - viewportHeightMetres * 0.55f
        val cameraTop = max(initialCameraTop, followCameraTop)
        val cameraCentreX = state.position.xMetres
        val cameraLeft = cameraCentreX - (metresAcross / 2f)

        fun toScreen(point: WorldPoint): Offset = Offset(
            x = ((point.xMetres - cameraLeft) * pixelsPerMetre) + panOffset.x,
            y = ((point.yMetres - cameraTop) * pixelsPerMetre) + panOffset.y,
        )

        val surfaceY = ((0f - cameraTop) * pixelsPerMetre) + panOffset.y

        drawRect(
            color = Color(0xFF73C5F5),
            size = Size(size.width, surfaceY.coerceAtLeast(0f)),
        )

        if (surfaceY < size.height) {
            drawRect(
                color = Color(0xFF666A70),
                topLeft = Offset(0f, surfaceY.coerceAtLeast(0f)),
                size = Size(size.width, size.height - surfaceY.coerceAtLeast(0f)),
            )
        }

        if (surfaceY in 0f..size.height) {
            drawLine(
                color = Color(0xFF4E6B3A),
                start = Offset(0f, surfaceY),
                end = Offset(size.width, surfaceY),
                strokeWidth = 6f,
            )
        }

        if (state.excavatedPath.size > 1) {
            val tunnelPath = Path().apply {
                val first = toScreen(state.excavatedPath.first())
                moveTo(first.x, first.y)
                state.excavatedPath.drop(1).forEach { point ->
                    val screenPoint = toScreen(point)
                    lineTo(screenPoint.x, screenPoint.y)
                }
                val current = toScreen(state.position)
                lineTo(current.x, current.y)
            }
            drawPath(
                path = tunnelPath,
                color = Color(0xFF282B2F),
                style = Stroke(
                    width = 3.7f * pixelsPerMetre,
                    cap = StrokeCap.Round,
                ),
            )
        }

        val diggerCentre = toScreen(state.position)
        val diggerWidth = 2.6f * pixelsPerMetre
        val diggerLength = 4.0f * pixelsPerMetre

        rotate(
            degrees = state.headingDegrees - 90f,
            pivot = diggerCentre,
        ) {
            drawRoundRect(
                color = Color(0xFFFFB000),
                topLeft = Offset(
                    diggerCentre.x - diggerWidth / 2f,
                    diggerCentre.y - diggerLength / 2f,
                ),
                size = Size(diggerWidth, diggerLength),
                cornerRadius = CornerRadius(8f, 8f),
            )
            drawRect(
                color = Color(0xFF2F3338),
                topLeft = Offset(
                    diggerCentre.x - diggerWidth * 0.46f,
                    diggerCentre.y + diggerLength * 0.31f,
                ),
                size = Size(diggerWidth * 0.92f, diggerLength * 0.12f),
            )
        }
    }
}

@Composable
private fun ControlsPanel(
    state: DiggerState,
    onAngleChange: (Float) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF22262C), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "STEERING ANGLE",
                    color = Color(0xFFB8C0CC),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "${"%.0f".format(state.targetHeadingDegrees)}°",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }

            Slider(
                value = state.targetHeadingDegrees,
                onValueChange = onAngleChange,
                valueRange = DigSimulation.MIN_HEADING_DEGREES..DigSimulation.MAX_HEADING_DEGREES,
            )

            Text(
                text = "0°/360° right • 90° down • 180° left • 270° up. Drag to pan • pinch to zoom.",
                color = Color(0xFF9AA4B2),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onStart,
                    enabled = !state.isDigging,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                ) {
                    Text("START DIGGING")
                }
                Button(
                    onClick = onStop,
                    enabled = state.isDigging,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                ) {
                    Text("STOP")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("RESET SHAFT")
            }
        }
    }
}
