package com.mineit.minegame.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
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
import com.mineit.minegame.ui.render.CameraMode
import com.mineit.minegame.ui.render.ClipAxis
import com.mineit.minegame.ui.render.FollowSlicePlanner
import com.mineit.minegame.ui.render.MineSurfaceView
import com.mineit.minegame.ui.render.RenderPerformanceStats
import com.mineit.minegame.ui.render.SliceFractions
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ControlPanel {
    CONTROL,
    VIEW,
    OTHER,
}

@Composable
fun MineWorldScreen(
    viewModel: MineWorldViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var surfaceView by remember { mutableStateOf<MineSurfaceView?>(null) }
    var performanceStats by remember { mutableStateOf(RenderPerformanceStats()) }
    var clipAxis by remember { mutableStateOf(ClipAxis.X) }
    var sliceFractions by remember { mutableStateOf(SliceFractions(0.18f, 0.18f, 0.18f)) }
    var clipFlipped by remember { mutableStateOf(false) }
    var clipEnabled by remember { mutableStateOf(true) }
    var followDigger by remember { mutableStateOf(false) }
    var cameraMode by remember { mutableStateOf(CameraMode.ORBIT) }
    var showDiagnostics by remember { mutableStateOf(true) }
    var selectedPanel by remember { mutableStateOf(ControlPanel.CONTROL) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipFraction = sliceFractions.forAxis(clipAxis)

    LaunchedEffect(followDigger, state.tunnel.end, state.extent) {
        if (followDigger) {
            sliceFractions = FollowSlicePlanner.forDigger(state)
        }
    }

    DisposableEffect(lifecycleOwner, surfaceView) {
        val activeView = surfaceView
        if (activeView != null) {
            activeView.setPerformanceListener { performanceStats = it }
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                activeView.onResume()
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> activeView?.onResume()
                Lifecycle.Event.ON_PAUSE -> activeView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activeView?.setPerformanceListener(null)
            activeView?.onPause()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF11161D))
            .statusBarsPadding(),
    ) {
        MineWorldHeader(state)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                factory = { context ->
                    MineSurfaceView(context).also { view ->
                        surfaceView = view
                        view.setWorldState(state)
                        view.setClip(clipAxis, clipFraction, clipFlipped, clipEnabled)
                        view.setFollowDigger(followDigger)
                        view.setCameraMode(cameraMode)
                    }
                },
                update = { view ->
                    view.setWorldState(state)
                    view.setClip(clipAxis, clipFraction, clipFlipped, clipEnabled)
                    view.setFollowDigger(followDigger)
                    view.setCameraMode(cameraMode)
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (showDiagnostics) {
                PerformanceOverlay(
                    stats = performanceStats,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }
        }

        MineWorldControls(
            state = state,
            selectedPanel = selectedPanel,
            onPanelChange = { selectedPanel = it },
            clipAxis = clipAxis,
            clipFraction = clipFraction,
            clipFlipped = clipFlipped,
            clipEnabled = clipEnabled,
            followDigger = followDigger,
            cameraMode = cameraMode,
            showDiagnostics = showDiagnostics,
            performanceStats = performanceStats,
            onClipAxisChange = { clipAxis = it },
            onClipFractionChange = { value ->
                sliceFractions = when (clipAxis) {
                    ClipAxis.X -> sliceFractions.copy(x = value)
                    ClipAxis.Y -> sliceFractions.copy(y = value)
                    ClipAxis.Z -> sliceFractions.copy(z = value)
                }
            },
            onFlipClip = { clipFlipped = !clipFlipped },
            onToggleClip = { clipEnabled = !clipEnabled },
            onToggleFollow = { followDigger = !followDigger },
            onCameraModeChange = { cameraMode = it },
            onToggleDiagnostics = { showDiagnostics = !showDiagnostics },
            onSteeringChange = viewModel::setSteering,
            onVerticalAngleChange = viewModel::setVerticalAngle,
            onTurnHeadingBy = viewModel::turnHeadingBy,
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
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "MINEIT // 3D GEOLOGY 0.7.0",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "PROGRESSIVE MESH + DIGGER POV",
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
                .padding(top = 4.dp),
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
private fun PerformanceOverlay(
    stats: RenderPerformanceStats,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildString {
            append("${stats.framesPerSecond} fps  ${format1(stats.frameTimeMs)}ms\n")
            append("mesh ${format1(stats.lastChunkBuildMs)}ms")
            if (stats.chunksRebuilt > 0) append(" ×${stats.chunksRebuilt}")
            append("  cap ${format1(stats.lastCapBuildMs)}ms\n")
            append("${stats.triangleCount / 1000}k tris  ${stats.cachedChunks} chunks  upload ${format1(stats.lastUploadMs)}ms\n")
            append("queue ${stats.queuedChunks}  mesh ${if (stats.meshWorkerBusy) "BUSY" else "IDLE"}  cap ${if (stats.capWorkerBusy) "BUSY" else "IDLE"}")
        },
        color = Color(0xFFE0E6EC),
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(Color(0xB311161D), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
    )
}

@Composable
private fun MineWorldControls(
    state: MineWorldState,
    selectedPanel: ControlPanel,
    onPanelChange: (ControlPanel) -> Unit,
    clipAxis: ClipAxis,
    clipFraction: Float,
    clipFlipped: Boolean,
    clipEnabled: Boolean,
    followDigger: Boolean,
    cameraMode: CameraMode,
    showDiagnostics: Boolean,
    performanceStats: RenderPerformanceStats,
    onClipAxisChange: (ClipAxis) -> Unit,
    onClipFractionChange: (Float) -> Unit,
    onFlipClip: () -> Unit,
    onToggleClip: () -> Unit,
    onToggleFollow: () -> Unit,
    onCameraModeChange: (CameraMode) -> Unit,
    onToggleDiagnostics: () -> Unit,
    onSteeringChange: (Float) -> Unit,
    onVerticalAngleChange: (Float) -> Unit,
    onTurnHeadingBy: (Float) -> Unit,
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
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        when (selectedPanel) {
            ControlPanel.CONTROL -> ControlPanelContent(
                state = state,
                onSteeringChange = onSteeringChange,
                onVerticalAngleChange = onVerticalAngleChange,
                onTurnHeadingBy = onTurnHeadingBy,
                onToggleDigging = onToggleDigging,
            )

            ControlPanel.VIEW -> ViewPanelContent(
                state = state,
                clipAxis = clipAxis,
                clipFraction = clipFraction,
                clipFlipped = clipFlipped,
                clipEnabled = clipEnabled,
                followDigger = followDigger,
                cameraMode = cameraMode,
                onClipAxisChange = onClipAxisChange,
                onClipFractionChange = onClipFractionChange,
                onFlipClip = onFlipClip,
                onToggleClip = onToggleClip,
                onToggleFollow = onToggleFollow,
                onCameraModeChange = onCameraModeChange,
                onResetView = onResetView,
            )

            ControlPanel.OTHER -> OtherPanelContent(
                showDiagnostics = showDiagnostics,
                performanceStats = performanceStats,
                onToggleDiagnostics = onToggleDiagnostics,
                onResetMine = onResetMine,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ControlPanel.entries.forEach { panel ->
                PanelButton(
                    panel = panel,
                    selected = selectedPanel == panel,
                    onClick = { onPanelChange(panel) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ControlPanelContent(
    state: MineWorldState,
    onSteeringChange: (Float) -> Unit,
    onVerticalAngleChange: (Float) -> Unit,
    onTurnHeadingBy: (Float) -> Unit,
    onToggleDigging: () -> Unit,
) {
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
                text = "${steeringDescription(state.steering)}  •  HEADING ${state.machineHeadingDegrees.roundToInt()}°",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )

            Text(
                text = if (state.isDigging) "STOP TO USE FIXED TURN" else "FIXED TURN",
                color = Color(0xFFB5BEC8),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 5.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                QuickTurnButton("L 90", -90f, !state.isDigging, onTurnHeadingBy, Modifier.weight(1f))
                QuickTurnButton("L 45", -45f, !state.isDigging, onTurnHeadingBy, Modifier.weight(1f))
                QuickTurnButton("R 45", 45f, !state.isDigging, onTurnHeadingBy, Modifier.weight(1f))
                QuickTurnButton("R 90", 90f, !state.isDigging, onTurnHeadingBy, Modifier.weight(1f))
            }
        }

        VerticalAngleControl(
            value = state.verticalAngleDegrees,
            onValueChange = onVerticalAngleChange,
        )
    }

    Text(
        text = "ABSOLUTE UP / DOWN ANGLE",
        color = Color(0xFFB5BEC8),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AnglePresetButton("UP 90", -90f, state.verticalAngleDegrees, onVerticalAngleChange, Modifier.weight(1f))
        AnglePresetButton("UP 45", -45f, state.verticalAngleDegrees, onVerticalAngleChange, Modifier.weight(1f))
        AnglePresetButton("LEVEL", 0f, state.verticalAngleDegrees, onVerticalAngleChange, Modifier.weight(1f))
        AnglePresetButton("DOWN 45", 45f, state.verticalAngleDegrees, onVerticalAngleChange, Modifier.weight(1f))
        AnglePresetButton("DOWN 90", 90f, state.verticalAngleDegrees, onVerticalAngleChange, Modifier.weight(1f))
    }

    Button(
        onClick = onToggleDigging,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (state.isDigging) Color(0xFF8C3F3F) else Color(0xFFB7791F),
        ),
    ) {
        Text(if (state.isDigging) "STOP" else "START DIGGING")
    }
}

@Composable
private fun ViewPanelContent(
    state: MineWorldState,
    clipAxis: ClipAxis,
    clipFraction: Float,
    clipFlipped: Boolean,
    clipEnabled: Boolean,
    followDigger: Boolean,
    cameraMode: CameraMode,
    onClipAxisChange: (ClipAxis) -> Unit,
    onClipFractionChange: (Float) -> Unit,
    onFlipClip: () -> Unit,
    onToggleClip: () -> Unit,
    onToggleFollow: () -> Unit,
    onCameraModeChange: (CameraMode) -> Unit,
    onResetView: () -> Unit,
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
        ToggleButton(
            selected = clipEnabled,
            selectedText = "SLICE ON",
            unselectedText = "FULL",
            onClick = onToggleClip,
            modifier = Modifier.weight(1.25f),
        )
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
            Text(if (clipFlipped) "FIRST SIDE" else "OTHER SIDE")
        }
    }
    Slider(
        value = clipFraction,
        onValueChange = onClipFractionChange,
        valueRange = 0.02f..0.98f,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ToggleButton(
            selected = followDigger,
            selectedText = "FOLLOW + SLICES ON",
            unselectedText = "FOLLOW + SLICES OFF",
            onClick = onToggleFollow,
            modifier = Modifier.weight(1.4f),
        )
        OutlinedButton(onClick = onResetView, modifier = Modifier.weight(1f)) {
            Text("RESET VIEW")
        }
    }

    Text(
        text = "CAMERA",
        color = Color(0xFFB5BEC8),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 5.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        CameraModeButton(
            label = "ORBIT / CT",
            selected = cameraMode == CameraMode.ORBIT,
            onClick = { onCameraModeChange(CameraMode.ORBIT) },
            modifier = Modifier.weight(1f),
        )
        CameraModeButton(
            label = "DIGGER POV",
            selected = cameraMode == CameraMode.DIGGER_POV,
            onClick = { onCameraModeChange(CameraMode.DIGGER_POV) },
            modifier = Modifier.weight(1f),
        )
    }

    Text(
        text = if (cameraMode == CameraMode.DIGGER_POV) {
            "Digger POV looks straight out from just behind the cutter and ignores CT clipping. Switch back to ORBIT / CT for slicing and free rotation."
        } else if (followDigger) {
            "Follow now keeps the camera and all X/Y/Z slice positions on the machine. Switching slice axis stays centred on the digger."
        } else {
            "Drag to rotate • pinch to zoom • CT slices update progressively while you move them."
        },
        color = Color(0xFF8D98A5),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 5.dp),
    )
}

@Composable
private fun OtherPanelContent(
    showDiagnostics: Boolean,
    performanceStats: RenderPerformanceStats,
    onToggleDiagnostics: () -> Unit,
    onResetMine: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ToggleButton(
            selected = showDiagnostics,
            selectedText = "DIAGNOSTICS ON",
            unselectedText = "DIAGNOSTICS OFF",
            onClick = onToggleDiagnostics,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onResetMine, modifier = Modifier.weight(1f)) {
            Text("RESET MINE")
        }
    }

    Text(
        text = "Renderer: ${performanceStats.framesPerSecond} fps • ${performanceStats.triangleCount / 1000}k tris • " +
            "${performanceStats.cachedChunks} cached chunks • ${performanceStats.queuedChunks} queued • " +
            "mesh ${if (performanceStats.meshWorkerBusy) "busy" else "idle"} • cap ${if (performanceStats.capWorkerBusy) "busy" else "idle"}",
        color = Color(0xFFB5BEC8),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp),
    )
    Text(
        text = "0.7 applies completed async geometry progressively instead of discarding useful intermediate revisions. Live excavation is also sampled more finely, with a second refinement pass when digging stops.",
        color = Color(0xFF8D98A5),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
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
                .height(112.dp),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = MineWorldController.MIN_VERTICAL_ANGLE_DEGREES..MineWorldController.MAX_VERTICAL_ANGLE_DEGREES,
                modifier = Modifier
                    .width(112.dp)
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
private fun QuickTurnButton(
    label: String,
    degrees: Float,
    enabled: Boolean,
    onClick: (Float) -> Unit,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = { onClick(degrees) },
        enabled = enabled,
        modifier = modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AnglePresetButton(
    label: String,
    angle: Float,
    current: Float,
    onClick: (Float) -> Unit,
    modifier: Modifier,
) {
    val selected = abs(current - angle) < 0.6f
    if (selected) {
        Button(
            onClick = { onClick(angle) },
            modifier = modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF356B75)),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        OutlinedButton(
            onClick = { onClick(angle) },
            modifier = modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
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
private fun CameraModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF356B75)),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(36.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ToggleButton(
    selected: Boolean,
    selectedText: String,
    unselectedText: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF356B75)),
        ) {
            Text(selectedText, style = MaterialTheme.typography.labelSmall)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(unselectedText, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PanelButton(
    panel: ControlPanel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF356B75)),
        ) {
            Text(panel.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(40.dp),
        ) {
            Text(panel.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun steeringDescription(value: Float): String = when {
    abs(value) < 0.05f -> "STRAIGHT"
    value < 0f -> "LEFT ${(abs(value) * 100f).roundToInt()}%"
    else -> "RIGHT ${(value * 100f).roundToInt()}%"
}

private fun angleDescription(value: Float): String = when {
    abs(value) < 1f -> "LEVEL"
    value < 0f -> "${abs(value).roundToInt()}° UP"
    else -> "${value.roundToInt()}° DOWN"
}

private fun clipValue(state: MineWorldState, axis: ClipAxis, fraction: Float): Float = when (axis) {
    ClipAxis.X -> state.bounds.minX + (state.bounds.width * fraction)
    ClipAxis.Y -> state.bounds.minY + (state.bounds.height * fraction)
    ClipAxis.Z -> state.bounds.minZ + (state.bounds.depth * fraction)
}

private fun format1(value: Float): String = "%.1f".format(value)
