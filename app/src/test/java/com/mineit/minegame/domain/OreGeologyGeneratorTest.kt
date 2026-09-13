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
    fun defaultSeedContainsRequiredCommodityAndArchetypeMapping() {
        val bodies = OreGeologyGenerator.generate(MineWorldContent.ORE_SEED)

        OreType.entries.forEach { type ->
            assertEquals(2, bodies.count { it.type == type })
        }
        assertEquals(OreDepositArchetype.TABULAR_VEIN, bodies.single { it.id == "gold-1" }.geometry.archetype)
        assertEquals(OreDepositArchetype.TABULAR_VEIN, bodies.single { it.id == "gold-2" }.geometry.archetype)
        assertEquals(OreDepositArchetype.TABULAR_VEIN, bodies.single { it.id == "silver-1" }.geometry.archetype)
        assertEquals(OreDepositArchetype.LENS_MASSIVE, bodies.single { it.id == "silver-2" }.geometry.archetype)
        assertEquals(
            OreDepositArchetype.DISSEMINATED_STOCKWORK,
            bodies.single { it.id == "copper-1" }.geometry.archetype,
        )
        assertEquals(
            OreDepositArchetype.LAYERED_STRATIFORM,
            bodies.single { it.id == "copper-2" }.geometry.archetype,
        )
        assertEquals(4, bodies.map { it.geometry.archetype }.toSet().size)
    }

    @Test
    fun everyArchetypeFieldContainsItsCentreAndRejectsFarOutsidePoint() {
        val bodies = OreGeologyGenerator.generate(MineWorldContent.ORE_SEED)

        bodies.forEach { body ->
            val bounds = body.geometry.bounds
            val centre = bounds.centre
            assertTrue("${body.id} centre should be solid ore", body.geometry.margin(centre) > 0f)
            val outside = MinePoint3D(
                x = bounds.maxX + 20f,
                y = bounds.maxY + 20f,
                z = bounds.maxZ + 20f,
            )
            assertTrue("${body.id} far point should be outside ore", body.geometry.margin(outside) < 0f)
            assertTrue(body.geometry.planningBounds().isNotEmpty())
        }
    }

    @Test
    fun generatedBodiesStayBelowMinimumOreDepth() {
        val bodies = OreGeologyGenerator.generate(MineWorldContent.ORE_SEED)

        assertTrue(bodies.all { it.geometry.bounds.minZ >= 5f })
    }

    @Test
    fun allFourArchetypesInheritTheSameSubtractiveExcavationField() {
        val centre = MinePoint3D(0f, 0f, 8f)
        val bodies = listOf(
            OreBody(
                "vein",
                OreType.GOLD,
                TabularVeinGeometry(centre, 0f, 0f, 20f, 20f, 8f, 0f, 0f, 1f, 0f),
            ),
            OreBody(
                "lens",
                OreType.SILVER,
                LensMassiveGeometry(centre, 0f, 0f, 8f, 8f, 6f, 0f, 0f),
            ),
            OreBody(
                "layered",
                OreType.COPPER,
                LayeredStratiformGeometry(centre, 0f, 0f, 20f, 20f, 8f, 0f, 0f),
            ),
            OreBody(
                "stockwork",
                OreType.COPPER,
                DisseminatedStockworkGeometry(centre, 0f, 0f, 8f, 8f, 6f, 0f, 0f),
            ),
        )
        val tunnel = TunnelGeometry(
            points = listOf(MinePoint3D(0f, 0f, 1f), MinePoint3D(0f, 0f, 15f)),
            radiusMetres = 3.2f,
        )
        val adjacentOre = MinePoint3D(4.5f, 0f, 8f)

        bodies.forEach { body ->
            assertTrue("${body.id} starts solid at cutter centre", MineWorldGeometry.oreMargin(centre, body) > 0f)
            assertTrue(
                "${body.id} should be empty inside the cutter sweep",
                MineWorldGeometry.remainingOreMargin(centre, body, tunnel) < 0f,
            )
            assertTrue(
                "${body.id} should retain adjacent ore",
                MineWorldGeometry.remainingOreMargin(adjacentOre, body, tunnel) > 0f,
            )
        }
    }

    @Test
    fun seededWorldContainsOreBeyondInitialVisibleExtent() {
        val state = MineWorldState()
        val world = state.bounds
        val hasOutsideBody = state.oreBodies.any { body ->
            val bounds = body.geometry.bounds
            bounds.minX < world.minX || bounds.maxX > world.maxX ||
                bounds.minY < world.minY || bounds.maxY > world.maxY ||
                bounds.maxZ > world.maxZ
        }

        assertTrue(hasOutsideBody)
    }
}
