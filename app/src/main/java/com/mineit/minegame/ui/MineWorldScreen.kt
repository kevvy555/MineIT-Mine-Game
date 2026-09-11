package com.mineit.minegame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mineit.minegame.domain.MineWorldController
import com.mineit.minegame.domain.MineWorldState
import com.mineit.minegame.ui.render.ClipAxis
import com.mineit.minegame.ui.render.MineSurfaceView
import kotlin.math.roundToInt

@Composable
fun MineWorldScreen(
    viewModel: MineWorldViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var surfaceView by remember { mutableStateOf<MineSurfaceView?>(null) }
    var clipAxis by remember { mutableStateOf(ClipAxis.Z) }
    var clipFraction by remember { mutableStateOf(0.18f) }
    var clipFlipped by remember { mutableStateOf(false) }
    var clipEnabled by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, surfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surfaceView?.onResume()
                Lifecycle.Event.ON_PAUSE -> surfaceView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            surfaceView?.onPause()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF11161D)),
    ) {
        MineWorldHeader(state)

        AndroidView(
            factory = { context ->
                MineSurfaceView(context).also { view ->
                    surfaceView = view
                    view.setWorldState(state)
                    view.setClip(clipAxis, clipFraction, clipFlipped, clipEnabled)
                }
            },
            update = { view ->
                view.setWorldState(state)
                view.setClip(clipAxis, clipFraction, clipFlipped, clipEnabled)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        MineWorldControls(
            state = state,
            clipAxis = clipAxis,
            clipFraction = clipFraction,
            clipFlipped = clipFlipped,
            clipEnabled = clipEnabled,
            onClipAxisChange = { clipAxis = it },
            onClipFractionChange = { clipFraction = it },
            onFlipClip = { clipFlipped = !clipFlipped },
            onToggleClip = { clipEnabled = !clipEnabled },
            onAzimuthChange = viewModel::setAzimuth,
            onDipChange = viewModel::setDip,
            onDig = viewModel::dig,
            onResetView = { surfaceView?.resetCamera() },
            onResetMine = viewModel::reset,
        )
    }
}

@Composable
private fun MineWorldHeader(state: MineWorldState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "MINEIT // 3D GEOLOGY 0.3.0",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "CUSTOM VOLUME + OPENGL POC",
                    color = Color(0xFF80CBC4),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                text = "${state.extent.chunkCount} chunks",
                color = Color(0xFFB5BEC8),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HeaderStat("DEPTH", "${state.tunnel.end.z.roundToInt()}m")
            HeaderStat("REMOVED", "${state.excavatedVolumeCubicMetres.roundToInt()}m³")
            HeaderStat("WASTE", "${state.wasteRockTonnes.roundToInt()}t")
            HeaderStat("ORE", "${state.exposedOreSegments.size} exposed")
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color(0xFF7F8995),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MineWorldControls(
    state: MineWorldState,
    clipAxis: ClipAxis,
    clipFraction: Float,
    clipFlipped: Boolean,
    clipEnabled: Boolean,
    onClipAxisChange: (ClipAxis) -> Unit,
    onClipFractionChange: (Float) -> Unit,
    onFlipClip: () -> Unit,
    onToggleClip: () -> Unit,
    onAzimuthChange: (Float) -> Unit,
    onDipChange: (Float) -> Unit,
    onDig: () -> Unit,
    onResetView: () -> Unit,
    onResetMine: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF20262D),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ClipAxis.entries.forEach { axis ->
                AxisButton(
                    axis = axis,
                    selected = clipAxis == axis,
                    onClick = { onClipAxisChange(axis) },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = onToggleClip,
                modifier = Modifier.weight(1.15f),
            ) {
                Text(if (clipEnabled) "CUT ON" else "FULL")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${clipAxis.name} slice ${clipValue(state, clipAxis, clipFraction).roundToInt()}m",
                color = Color(0xFFB5BEC8),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onFlipClip) {
                Text(if (clipFlipped) "CUT −" else "CUT +")
            }
        }
        Slider(
            value = clipFraction,
            onValueChange = onClipFractionChange,
            valueRange = 0.02f..0.98f,
        )

        SteeringSlider(
            label = "AZIMUTH",
            valueLabel = "${state.azimuthDegrees.roundToInt()}°",
            value = state.azimuthDegrees,
            valueRange = 0f..360f,
            onValueChange = onAzimuthChange,
        )
        SteeringSlider(
            label = "DIP",
            valueLabel = "${state.dipDegrees.roundToInt()}°",
            value = state.dipDegrees,
            valueRange = MineWorldController.MIN_DIP_DEGREES..MineWorldController.MAX_DIP_DEGREES,
            onValueChange = onDipChange,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onDig,
                modifier = Modifier.weight(1.55f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB7791F)),
            ) {
                Text("DIG +${MineWorldController.DIG_STEP_METRES.toInt()}m")
            }
            OutlinedButton(onClick = onResetView, modifier = Modifier.weight(1f)) {
                Text("VIEW")
            }
            OutlinedButton(onClick = onResetMine, modifier = Modifier.weight(1f)) {
                Text("RESET")
            }
        }

        Text(
            text = "Drag = rotate • pinch = zoom • purple = ore exposed on a real tunnel wall. The volume expands as excavation reaches an edge.",
            color = Color(0xFF8D98A5),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun AxisButton(
    axis: ClipAxis,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF356B75)),
        ) {
            Text(axis.name)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(axis.name)
        }
    }
}

@Composable
private fun SteeringSlider(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFFB5BEC8),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(0.9f),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(3.5f),
        )
        Text(
            text = valueLabel,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(0.8f),
        )
    }
}

private fun clipValue(state: MineWorldState, axis: ClipAxis, fraction: Float): Float = when (axis) {
    ClipAxis.X -> state.bounds.minX + (state.bounds.width * fraction)
    ClipAxis.Y -> state.bounds.minY + (state.bounds.height * fraction)
    ClipAxis.Z -> state.bounds.minZ + (state.bounds.depth * fraction)
}
