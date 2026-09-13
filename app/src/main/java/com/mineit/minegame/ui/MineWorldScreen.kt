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
import com.mineit.minegame.ui.render.OrbitGestureMode
import com.mineit.minegame.ui.render.RenderPerformanceStats
import com.mineit.minegame.ui.render.SliceConfiguration
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
    var selectedSliceAxis by remember { mutableStateOf(ClipAxis.X) }
    var sliceConfiguration by remember { mutableStateOf(SliceConfiguration()) }
    var rockVisible by remember { mutableStateOf(true) }
    var followDigger by remember { mutableStateOf(false) }
    var cameraMode by remember { mutableStateOf(CameraMode.ORBIT) }
    var orbitGestureMode by remember { mutableStateOf(OrbitGestureMode.ROTATE) }
    var seeOre by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(true) }
    var selectedPanel by remember { mutableStateOf(ControlPanel.CONTROL) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val selectedFraction = sliceConfiguration.fraction(selectedSliceAxis)
    val selectedFlipped = sliceConfiguration.isFlipped(selectedSliceAxis)
    val selectedEnabled = sliceConfiguration.isEnabled(selectedSliceAxis)

    LaunchedEffect(followDigger, state.tunnel.end, state.extent) {
        if (followDigger) {
            sliceConfiguration = sliceConfiguration.withFollowFractions(FollowSlicePlanner.forDigger(state))
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
                        view.setSlices(sliceConfiguration)
                        view.setRockVisible(rockVisible)
                        view.setFollowDigger(followDigger)
                        view.setCameraMode(cameraMode)
                        view.setOrbitGestureMode(orbitGestureMode)
                        view.setSeeOre(seeOre)
                    }
                },
                update = { view ->
                    view.setWorldState(state)
                    view.setSlices(sliceConfiguration)
                    view.setRockVisible(rockVisible)
                    view.setFollowDigger(followDigger)
                    view.setCameraMode(cameraMode)
                    view.setOrbitGestureMode(orbitGestureMode)
                    view.setSeeOre(seeOre)
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
            selectedSliceAxis = selectedSliceAxis,
            sliceConfiguration = sliceConfiguration,
            rockVisible = rockVisible,
            followDigger = followDigger,
            cameraMode = cameraMode,
            orbitGestureMode = orbitGestureMode,
            seeOre = seeOre,
            showDiagnostics = showDiagnostics,
            performanceStats = performanceStats,
            onSliceAxisChange = { selectedSliceAxis = it },
            onSliceFractionChange = { value ->
                sliceConfiguration = sliceConfiguration.withFraction(selectedSliceAxis, value)
            },
            onFlipSlice = {
                sliceConfiguration = sliceConfiguration.toggleFlipped(selectedSliceAxis)
            },
            onToggleSelectedSlice = {
                sliceConfiguration = sliceConfiguration.toggleAxis(selectedSliceAxis)
            },
            onToggleRock = { rockVisible = !rockVisible },
            onToggleFollow = { followDigger = !followDigger },
            onCameraModeChange = { cameraMode = it },
            onOrbitGestureModeChange = { orbitGestureMode = it },
            onToggleSeeOre = { seeOre = !seeOre },
            onToggleDiagnostics = { showDiagnostics = !showDiagnostics },
            onSteeringChange = viewModel::setSteering,
            onVerticalAngleChange = viewModel::setVerticalAngle,
            onDigSpeedChange = viewModel::setDigSpeedMultiplier,
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
                    text = "MINEIT // 3D GEOLOGY 0.11.0",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "TRI-PLANAR CT + BOUNDED ORE",
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
            HeaderStat("ORE", "${state.discoveredOreBodyIds.size}/${state.oreBodies.size}")
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
    selectedSliceAxis: ClipAxis,
    sliceConfiguration: SliceConfiguration,
    rockVisible: Boolean,
    followDigger: Boolean,
    cameraMode: CameraMode,
    orbitGestureMode: OrbitGestureMode,
    seeOre: Boolean,
    showDiagnostics: Boolean,
    performanceStats: RenderPerformanceStats,
    onSliceAxisChange: (ClipAxis) -> Unit,
    onSliceFractionChange: (Float) -> Unit,
    onFlipSlice: () -> Unit,
    onToggleSelectedSlice: () -> Unit,
    onToggleRock: () -> Unit,
    onToggleFollow: () -> Unit,
    onCameraModeChange: (CameraMode) -> Unit,
    onOrbitGestureModeChange: (OrbitGestureMode) -> Unit,
    onToggleSeeOre: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onSteeringChange: (Float) -> Unit,
    onVerticalAngleChange: (Float) -> Unit,
    onDigSpeedChange: (Float) -> Unit,
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
                onDigSpeedChange = onDigSpeedChange,
                onTurnHeadingBy = onTurnHeadingBy,
                onToggleDigging = onToggleDigging,
            )

            ControlPanel.VIEW -> ViewPanelContent(
                state = state,
                selectedSliceAxis = selectedSliceAxis,
                sliceConfiguration = sliceConfiguration,
                rockVisible = rockVisible,
                followDigger = followDigger,
                cameraMode = cameraMode,
                orbitGestureMode = orbitGestureMode,
                seeOre = seeOre,
                onSliceAxisChange = onSliceAxisChange,
                onSliceFractionChange = onSliceFractionChange,
                onFlipSlice = onFlipSlice,
                onToggleSelectedSlice = onToggleSelectedSlice,
                onToggleRock = onToggleRock,
                onToggleFollow = onToggleFollow,
                onCameraModeChange = onCameraModeChange,
                onOrbitGestureModeChange = onOrbitGestureModeChange,
                onToggleSeeOre = onToggleSeeOre,
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
    onDigSpeedChange: (Float) -> Unit,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SPEED ${format1(state.digSpeedMultiplier)}×",
            color = Color(0xFFB5BEC8),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(78.dp),
        )
        Slider(
            value = state.digSpeedMultiplier,
            onValueChange = onDigSpeedChange,
            valueRange = MineWorldController.MIN_DIG_SPEED_MULTIPLIER..MineWorldController.MAX_DIG_SPEED_MULTIPLIER,
            modifier = Modifier.weight(1f),
        )
    }

    Button(
        onClick = onToggleDigging,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp),
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
    selectedSliceAxis: ClipAxis,
    sliceConfiguration: SliceConfiguration,
    rockVisible: Boolean,
    followDigger: Boolean,
    cameraMode: CameraMode,
    orbitGestureMode: OrbitGestureMode,
    seeOre: Boolean,
    onSliceAxisChange: (ClipAxis) -> Unit,
    onSliceFractionChange: (Float) -> Unit,
    onFlipSlice: () -> Unit,
    onToggleSelectedSlice: () -> Unit,
    onToggleRock: () -> Unit,
    onToggleFollow: () -> Unit,
    onCameraModeChange: (CameraMode) -> Unit,
    onOrbitGestureModeChange: (OrbitGestureMode) -> Unit,
    onToggleSeeOre: () -> Unit,
    onResetView: () -> Unit,
) {
    val selectedFraction = sliceConfiguration.fraction(selectedSliceAxis)
    val selectedFlipped = sliceConfiguration.isFlipped(selectedSliceAxis)
    val selectedEnabled = sliceConfiguration.isEnabled(selectedSliceAxis)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ToggleButton(
            selected = rockVisible,
            selectedText = "ROCK ON",
            unselectedText = "ROCK OFF",
            onClick = onToggleRock,
            modifier = Modifier.weight(1f),
        )
        ToggleButton(
            selected = followDigger,
            selectedText = "FOLLOW ON",
            unselectedText = "FOLLOW OFF",
            onClick = onToggleFollow,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onResetView, modifier = Modifier.weight(1f)) {
            Text("RESET")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ToggleButton(
            selected = orbitGestureMode == OrbitGestureMode.PAN,
            selectedText = "PAN ON",
            unselectedText = "PAN",
            onClick = {
                onOrbitGestureModeChange(
                    if (orbitGestureMode == OrbitGestureMode.PAN) OrbitGestureMode.ROTATE else OrbitGestureMode.PAN,
                )
            },
            modifier = Modifier.weight(1f),
        )
        ToggleButton(
            selected = seeOre,
            selectedText = "SEE ORE ON",
            unselectedText = "SEE ORE",
            onClick = onToggleSeeOre,
            modifier = Modifier.weight(1f),
        )
    }

    if (seeOre) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text("GOLD", color = Color(0xFFF5B81D), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("SILVER", color = Color(0xFFB8CADB), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("COPPER", color = Color(0xFFD15C21), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }

    if (rockVisible) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ClipAxis.entries.forEach { axis ->
                AxisButton(
                    axis = axis,
                    selected = selectedSliceAxis == axis,
                    active = sliceConfiguration.isEnabled(axis),
                    onClick = { onSliceAxisChange(axis) },
                    modifier = Modifier.weight(1f),
                )
            }
            ToggleButton(
                selected = selectedEnabled,
                selectedText = "${selectedSliceAxis.name} CUT ON",
                unselectedText = "${selectedSliceAxis.name} CUT OFF",
                onClick = onToggleSelectedSlice,
                modifier = Modifier.weight(1.35f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${selectedSliceAxis.name} slice ${clipValue(state, selectedSliceAxis, selectedFraction).roundToInt()}m" +
                    "  •  ${sliceConfiguration.enabledAxes.size} cut${if (sliceConfiguration.enabledAxes.size == 1) "" else "s"} active",
                color = Color(0xFFB5BEC8),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onFlipSlice,
                enabled = selectedEnabled,
            ) {
                Text(if (selectedFlipped) "FIRST SIDE" else "OTHER SIDE")
            }
        }
        Slider(
            value = selectedFraction,
            onValueChange = onSliceFractionChange,
            valueRange = SliceConfiguration.MIN_SLICE_FRACTION..SliceConfiguration.MAX_SLICE_FRACTION,
        )
    } else {
        Text(
            text = "TUNNELS ONLY • grass remains visible as the surface reference",
            color = Color(0xFF80CBC4),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 7.dp),
        )
    }

    Text(
        text = "CAMERA",
        color = Color(0xFFB5BEC8),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 3.dp),
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
        text = when {
            seeOre -> "SEE ORE reveals bounded solid ore only on enabled CT faces. Select X/Y/Z to adjust it; enabled cuts stay active together for tri-planar inspection."
            !rockVisible -> "Rock is hidden using a direct tunnel skin, so this view stays responsive even if detailed geology is still refining."
            cameraMode == CameraMode.DIGGER_POV -> "Digger POV looks straight out from just behind the cutter and ignores CT clipping."
            orbitGestureMode == OrbitGestureMode.PAN -> "PAN selected • drag to move the camera • pinch to zoom. Turn PAN off to rotate again."
            followDigger -> "Follow keeps the camera and all X/Y/Z slice positions on the machine; enabled cuts remain simultaneous."
            else -> "Select X/Y/Z to adjust that plane • use its CUT button to keep multiple planes active • drag rotates • pinch zooms."
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
        text = "0.11 keeps the immediate global-shell architecture, clips ore to generated geology and supports simultaneous persistent X/Y/Z CT cuts.",
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
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val label = if (active) "${axis.name} ✓" else axis.name
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF356B75)),
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label, color = if (active) Color(0xFF80CBC4) else Color.Unspecified)
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
