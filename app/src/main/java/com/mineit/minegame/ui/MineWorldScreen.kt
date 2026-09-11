package com.mineit.minegame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.rotate
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
import kotlin.math.abs
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
            .background(Color(0xFF11161D))
            .statusBarsPadding(),
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
            onSteeringChange = viewModel::setSteering,
            onVerticalAngleChange = viewModel::setVerticalAngle,
            onToggleDigging = viewModel::toggleDigging,
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "MINEIT // 3D GEOLOGY 0.4.0",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "SOLID CT MINE + CONTINUOUS DIGGING",
                    color = Color(0xFF80CBC4),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                text = if (state.isDigging) "DIGGING" else "STOPPED",
                color = if (state.isDigging) Color(0xFFFFC857) else Color(0xFFB5BEC8),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HeaderStat("DEPTH", "${state.depthMetres.roundToInt()}m")
            HeaderStat("REMOVED", "${state.excavatedVolumeCubicMetres.roundToInt()}m³")
            HeaderStat("WASTE", "${state.wasteRockTonnes.roundToInt()}t")
            HeaderStat("ORE", if (state.oreBodyDiscovered) "FOUND" else "HIDDEN")
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
    onSteeringChange: (Float) -> Unit,
    onVerticalAngleChange: (Float) -> Unit,
    onToggleDigging: () -> Unit,
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                modifier = Modifier.weight(1.2f),
            ) {
                Text(if (clipEnabled) "SLICE ON" else "FULL")
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
                Text("OTHER SIDE")
            }
        }
        Slider(
            value = clipFraction,
            onValueChange = onClipFractionChange,
            valueRange = 0.02f..0.98f,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "STEER",
                    color = Color(0xFFB5BEC8),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "LEFT",
                        color = Color(0xFF9AA4B2),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(34.dp),
                    )
                    Slider(
                        value = state.steering,
                        onValueChange = onSteeringChange,
                        valueRange = MineWorldController.MIN_STEERING..MineWorldController.MAX_STEERING,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "RIGHT",
                        color = Color(0xFF9AA4B2),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(40.dp),
                    )
                }
                Text(
                    text = steeringDescription(state.steering),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )

                Button(
                    onClick = onToggleDigging,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isDigging) Color(0xFF8C3F3F) else Color(0xFFB7791F),
                    ),
                ) {
                    Text(if (state.isDigging) "STOP" else "START DIGGING")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    OutlinedButton(onClick = onResetView, modifier = Modifier.weight(1f)) {
                        Text("RESET VIEW")
                    }
                    OutlinedButton(onClick = onResetMine, modifier = Modifier.weight(1f)) {
                        Text("RESET MINE")
                    }
                }
            }

            VerticalAngleControl(
                value = state.verticalAngleDegrees,
                onValueChange = onVerticalAngleChange,
            )
        }

        Text(
            text = "Drag = rotate • pinch = zoom • X/Y/Z slices cut through solid rock like a CT scan. Once ore is hit, its connected vein becomes visible in slices.",
            color = Color(0xFF8D98A5),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun VerticalAngleControl(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.width(92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "ANGLE",
            color = Color(0xFFB5BEC8),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text("UP", color = Color(0xFF9AA4B2), style = MaterialTheme.typography.labelSmall)
        Box(
            modifier = Modifier
                .width(86.dp)
                .height(116.dp),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = MineWorldController.MIN_VERTICAL_ANGLE_DEGREES..MineWorldController.MAX_VERTICAL_ANGLE_DEGREES,
                modifier = Modifier
                    .width(116.dp)
                    .rotate(90f),
            )
        }
        Text("DOWN", color = Color(0xFF9AA4B2), style = MaterialTheme.typography.labelSmall)
        Text(
            text = angleDescription(value),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
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

private fun steeringDescription(value: Float): String = when {
    abs(value) < 0.05f -> "STRAIGHT"
    value < 0f -> "TURN LEFT ${(abs(value) * 100f).roundToInt()}%"
    else -> "TURN RIGHT ${(value * 100f).roundToInt()}%"
}

private fun angleDescription(value: Float): String = when {
    abs(value) < 2f -> "LEVEL"
    value < 0f -> "${abs(value).roundToInt()}° UP"
    else -> "${value.roundToInt()}° DOWN"
}

private fun clipValue(state: MineWorldState, axis: ClipAxis, fraction: Float): Float = when (axis) {
    ClipAxis.X -> state.bounds.minX + (state.bounds.width * fraction)
    ClipAxis.Y -> state.bounds.minY + (state.bounds.height * fraction)
    ClipAxis.Z -> state.bounds.minZ + (state.bounds.depth * fraction)
}
