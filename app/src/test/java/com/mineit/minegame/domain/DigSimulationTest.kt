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
    fun headingIsClampedToDownwardDiggingRange() {
        val state = DiggerState()

        val tooLow = DigSimulation.setTargetHeading(state, -40f)
        val tooHigh = DigSimulation.setTargetHeading(state, 220f)

        assertEquals(DigSimulation.MIN_HEADING_DEGREES, tooLow.targetHeadingDegrees)
        assertEquals(DigSimulation.MAX_HEADING_DEGREES, tooHigh.targetHeadingDegrees)
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
