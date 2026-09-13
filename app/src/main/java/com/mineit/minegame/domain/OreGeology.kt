package com.mineit.minegame.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class OreType {
    GOLD,
    SILVER,
    COPPER,
}

data class OreBody(
    val id: String,
    val type: OreType,
    val nodes: List<OreBodyNode>,
)

/**
 * Small deterministic geology generator for the POC.
 *
 * Ore bodies are continuous 3D tubes defined by control nodes with varying radii. The same seed
 * always creates the same geology, including bodies that extend beyond the initial generated mine
 * extent. Gold tends to be thin/irregular, silver medium, and copper broad/continuous.
 */
object OreGeologyGenerator {
    private data class Profile(
        val type: OreType,
        val bodyCount: Int,
        val minNodes: Int,
        val maxNodes: Int,
        val minStepMetres: Float,
        val maxStepMetres: Float,
        val minRadiusMetres: Float,
        val maxRadiusMetres: Float,
        val headingJitterDegrees: Float,
        val verticalJitterDegrees: Float,
    )

    private val profiles = listOf(
        Profile(OreType.GOLD, 2, 8, 12, 7f, 11f, 1.2f, 2.8f, 30f, 18f),
        Profile(OreType.SILVER, 2, 8, 13, 8f, 13f, 2.3f, 4.6f, 22f, 14f),
        Profile(OreType.COPPER, 2, 9, 15, 10f, 17f, 4.5f, 8.5f, 14f, 10f),
    )

    fun generate(seed: Int): List<OreBody> {
        val random = Random(seed)
        return buildList {
            profiles.forEach { profile ->
                repeat(profile.bodyCount) { index ->
                    add(generateBody(profile, index, random))
                }
            }
        }
    }

    private fun generateBody(profile: Profile, index: Int, random: Random): OreBody {
        val starterGold = profile.type == OreType.GOLD && index == 0
        val nodeCount = random.nextInt(profile.minNodes, profile.maxNodes + 1)
        var headingDegrees = if (starterGold) 25f else random.nextFloat(0f, 360f)
        var verticalDegrees = if (starterGold) 50f else random.nextFloat(-18f, 58f)
        var point = if (starterGold) {
            MinePoint3D(
                x = 11f + random.nextFloat(-1.2f, 1.2f),
                y = 5f + random.nextFloat(-1.2f, 1.2f),
                z = 17f + random.nextFloat(-1.5f, 1.5f),
            )
        } else {
            MinePoint3D(
                x = random.nextFloat(-90f, 90f),
                y = random.nextFloat(-90f, 90f),
                z = random.nextFloat(18f, 135f),
            )
        }

        val nodes = ArrayList<OreBodyNode>(nodeCount)
        repeat(nodeCount) { nodeIndex ->
            nodes += OreBodyNode(
                centre = point,
                radiusMetres = random.nextFloat(profile.minRadiusMetres, profile.maxRadiusMetres),
            )
            if (nodeIndex == nodeCount - 1) return@repeat

            headingDegrees = normalizeHeading(
                headingDegrees + random.nextFloat(-profile.headingJitterDegrees, profile.headingJitterDegrees),
            )
            verticalDegrees = (
                verticalDegrees + random.nextFloat(-profile.verticalJitterDegrees, profile.verticalJitterDegrees)
                ).coerceIn(-38f, 68f)
            val step = random.nextFloat(profile.minStepMetres, profile.maxStepMetres)
            val heading = Math.toRadians(headingDegrees.toDouble())
            val vertical = Math.toRadians(verticalDegrees.toDouble())
            val horizontal = cos(vertical).toFloat() * step
            var next = MinePoint3D(
                x = point.x + (cos(heading).toFloat() * horizontal),
                y = point.y + (sin(heading).toFloat() * horizontal),
                z = point.z + (sin(vertical).toFloat() * step),
            )

            if (next.z < MIN_ORE_DEPTH_METRES) {
                next = next.copy(z = MIN_ORE_DEPTH_METRES + abs(next.z - MIN_ORE_DEPTH_METRES))
                verticalDegrees = max(8f, abs(verticalDegrees))
            }
            point = next
        }

        return OreBody(
            id = "${profile.type.name.lowercase()}-${index + 1}",
            type = profile.type,
            nodes = nodes,
        )
    }

    private fun Random.nextFloat(minimum: Float, maximum: Float): Float =
        minimum + (nextFloat() * (maximum - minimum))

    private const val MIN_ORE_DEPTH_METRES = 5f
}
