package com.mineit.minegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigSimulationTest {
    @Test
    fun stoppedDiggerDoesNotMove() {
        val initial = DiggerState()

        val next = DigSimulation.tick(initial, 1f)

        assertEquals(initial.position, next.position)
        assertFalse(next.isDigging)
    }

    @Test
    fun stoppedDiggerRotatesImmediatelyWhenSteeringChanges() {
        val initial = DiggerState()

        val steered = DigSimulation.setTargetHeading(initial, 270f)

        assertEquals(270f, steered.headingDegrees, 0.01f)
        assertEquals(270f, steered.targetHeadingDegrees, 0.01f)
        assertFalse(steered.isDigging)
    }

    @Test
    fun fullRotationHeadingRangeIsAvailable() {
        val state = DiggerState()

        val zero = DigSimulation.setTargetHeading(state, 0f)
        val up = DigSimulation.setTargetHeading(state, 270f)
        val fullTurn = DigSimulation.setTargetHeading(state, 360f)

        assertEquals(0f, zero.headingDegrees, 0.01f)
        assertEquals(270f, up.headingDegrees, 0.01f)
        assertEquals(360f, fullTurn.headingDegrees, 0.01f)
    }

    @Test
    fun straightDownDiggingIncreasesDepth() {
        val started = DigSimulation.start(DiggerState())

        val next = DigSimulation.tick(started, 0.1f)

        assertTrue(next.depthMetres > 0f)
        assertEquals(0f, next.position.xMetres, 0.01f)
    }

    @Test
    fun steeringTurnsProgressivelyTowardTarget() {
        val started = DigSimulation.start(DiggerState())
        val steered = DigSimulation.setTargetHeading(started, 150f)

        val next = DigSimulation.tick(steered, 0.1f)

        assertTrue(next.headingDegrees > 90f)
        assertTrue(next.headingDegrees < 150f)
    }

    @Test
    fun steeringUsesShortestPathAcrossZeroDegrees() {
        val underground = DiggerState(
            position = WorldPoint(0f, 5f),
            headingDegrees = 350f,
            targetHeadingDegrees = 350f,
            excavatedPath = listOf(WorldPoint(0f, 5f)),
        )
        val started = DigSimulation.start(underground)
        val steered = DigSimulation.setTargetHeading(started, 10f)

        val next = DigSimulation.tick(steered, 0.1f)

        assertTrue(next.headingDegrees > 350f || next.headingDegrees < 10f)
    }

    @Test
    fun surfaceDiggerCannotStartTowardSky() {
        val upward = DigSimulation.setTargetHeading(DiggerState(), 270f)

        val started = DigSimulation.start(upward)

        assertFalse(started.isDigging)
        assertEquals(WorldPoint(0f, 0f), started.position)
    }

    @Test
    fun surfaceDiggerCannotTravelSidewaysWithoutEnteringRock() {
        val sideways = DigSimulation.setTargetHeading(DiggerState(), 0f)

        val started = DigSimulation.start(sideways)

        assertFalse(started.isDigging)
        assertEquals(WorldPoint(0f, 0f), started.position)
    }

    @Test
    fun undergroundDiggerCanDrillUpwardButStopsAtSurface() {
        val underground = DiggerState(
            position = WorldPoint(0f, 0.15f),
            headingDegrees = 270f,
            targetHeadingDegrees = 270f,
            excavatedPath = listOf(WorldPoint(0f, 0.15f)),
        )
        val started = DigSimulation.start(underground)

        val next = DigSimulation.tick(started, 0.1f)

        assertEquals(0f, next.position.yMetres, 0.001f)
        assertFalse(next.isDigging)
        assertTrue(next.excavatedPath.size > 1)
    }

    @Test
    fun diggingBuildsExcavatedPath() {
        var state = DigSimulation.start(DiggerState())

        repeat(10) {
            state = DigSimulation.tick(state, 0.1f)
        }

        assertTrue(state.excavatedPath.size > 1)
    }
}
