package com.mineit.minegame.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OreGeologyGeneratorTest {
    @Test
    fun sameSeedProducesSameGeology() {
        val first = OreGeologyGenerator.generate(123456)
        val second = OreGeologyGenerator.generate(123456)

        assertEquals(first, second)
    }

    @Test
    fun generatorCreatesMultipleGoldSilverAndCopperBodies() {
        val bodies = OreGeologyGenerator.generate(MineWorldContent.ORE_SEED)

        OreType.entries.forEach { type ->
            assertTrue(bodies.count { it.type == type } >= 2)
        }
        assertTrue(bodies.all { it.nodes.size >= 8 })
    }

    @Test
    fun copperIsTypicallyBroaderThanSilverAndGold() {
        val bodies = OreGeologyGenerator.generate(MineWorldContent.ORE_SEED)
        fun averageRadius(type: OreType): Double = bodies
            .filter { it.type == type }
            .flatMap { it.nodes }
            .map { it.radiusMetres.toDouble() }
            .average()

        assertTrue(averageRadius(OreType.COPPER) > averageRadius(OreType.SILVER))
        assertTrue(averageRadius(OreType.SILVER) > averageRadius(OreType.GOLD))
    }

    @Test
    fun seededWorldContainsOreBeyondInitialVisibleExtent() {
        val state = MineWorldState()
        val bounds = state.bounds
        val hasOutsideNode = state.oreBodies
            .flatMap { it.nodes }
            .any { node ->
                val point = node.centre
                point.x < bounds.minX || point.x > bounds.maxX ||
                    point.y < bounds.minY || point.y > bounds.maxY ||
                    point.z > bounds.maxZ
            }

        assertTrue(hasOutsideNode)
    }
}
