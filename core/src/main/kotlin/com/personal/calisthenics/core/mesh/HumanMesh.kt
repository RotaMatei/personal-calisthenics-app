package com.personal.calisthenics.core.mesh

import com.personal.calisthenics.core.rig.Vec3
import java.io.DataInputStream
import java.io.InputStream

/**
 * The skinned human body mesh: a detailed, anatomically modelled male figure (MakeHuman 1.x base mesh, CC0) in its
 * rest pose, with up to four bone influences per vertex. It is built by `tools/mesh/build_human.py`; see CREDITS in
 * the README. Units are centimetres, +x = the figure's right side, +y up, +z the facing direction.
 */
class HumanMesh internal constructor(
    val vertexCount: Int,
    val triangleCount: Int,
    internal val boneNames: List<String>,
    internal val landmarks: Map<String, Vec3>,
    /** x, y, z per vertex. */
    val rest: FloatArray,
    /** Three vertex indices per triangle, counter-clockwise seen from outside. */
    val triangles: IntArray,
    /** Four bone indices per vertex (unused influences have weight 0). */
    internal val boneIndex: IntArray,
    /** Four weights per vertex (sum 1). */
    internal val boneWeight: FloatArray,
) {
    internal fun landmark(name: String): Vec3 = landmarks[name] ?: error("Missing mesh landmark $name")

    internal val boneByName: Map<String, Int> = boneNames.withIndex().associate { it.value to it.index }

    /** Body part each vertex belongs to, as a soft membership 0..1 per [Part]; see [partWeights]. */
    private val partWeight: Array<FloatArray> = Array(Part.values().size) { FloatArray(vertexCount) }

    init {
        val partOfBone = IntArray(boneNames.size) { Part.of(boneNames[it]).ordinal }
        for (v in 0 until vertexCount) {
            for (k in 0 until 4) {
                val w = boneWeight[v * 4 + k]
                if (w > 0f) partWeight[partOfBone[boneIndex[v * 4 + k]]][v] += w
            }
        }
    }

    /** Sum of the four bone weights of [vertex]; 1 for every vertex of a well-formed mesh. */
    fun weightSum(vertex: Int): Float =
        boneWeight[vertex * 4] + boneWeight[vertex * 4 + 1] + boneWeight[vertex * 4 + 2] + boneWeight[vertex * 4 + 3]

    /** Fraction (0..1) of vertex influence that belongs to [part], per vertex. */
    fun partWeights(part: Part): FloatArray = partWeight[part.ordinal]

    enum class Part {
        TORSO, HEAD, ARM_L, ARM_R, LEG_L, LEG_R;

        companion object {
            /** "L" is the figure's -x side (the rig's naming), which the converter calls N; P is the +x ("R") side. */
            fun of(bone: String): Part = when {
                bone == "lower" || bone == "upper" || bone == "neck" || bone.startsWith("clav") -> TORSO
                bone == "head" -> HEAD
                bone.startsWith("thigh") || bone.startsWith("shin") || bone.startsWith("foot") ->
                    if (bone.endsWith("N")) LEG_L else LEG_R
                else -> if (bone.endsWith("N")) ARM_L else ARM_R
            }
        }
    }

    companion object {
        private const val MAGIC = 0x48554D31 // "HUM1"
        const val RESOURCE = "/mesh/human.bin"

        fun parse(stream: InputStream): HumanMesh {
            val inp = DataInputStream(stream.buffered(1 shl 16))
            require(inp.readInt() == MAGIC) { "Not a human mesh file" }
            val nv = inp.readInt()
            val nt = inp.readInt()
            val nb = inp.readInt()
            val names = List(nb) { inp.readUTF() }
            val nl = inp.readInt()
            val lm = HashMap<String, Vec3>()
            repeat(nl) {
                val name = inp.readUTF()
                lm[name] = Vec3(inp.readFloat(), inp.readFloat(), inp.readFloat())
            }
            val rest = FloatArray(nv * 3) { inp.readFloat() }
            val tris = IntArray(nt * 3) { inp.readUnsignedShort() }
            val bi = IntArray(nv * 4)
            val bw = FloatArray(nv * 4)
            for (v in 0 until nv) {
                for (k in 0 until 4) {
                    bi[v * 4 + k] = inp.readUnsignedByte()
                    bw[v * 4 + k] = inp.readUnsignedByte() / 255f
                }
            }
            return HumanMesh(nv, nt, names, lm, rest, tris, bi, bw)
        }

        /** The mesh bundled with the library as a Java resource, or null when it is not on the class path. */
        fun fromResource(): HumanMesh? =
            HumanMesh::class.java.getResourceAsStream(RESOURCE)?.use { parse(it) }

        /** Process-wide instance for the app: set once at start-up by whoever can open the file (assets or class path). */
        @Volatile
        var shared: HumanMesh? = null
            private set

        @Synchronized
        fun install(stream: InputStream): HumanMesh = (shared ?: parse(stream)).also { shared = it }

        /** The shared mesh, loading the bundled resource on first use; null when none can be found. */
        fun sharedOrNull(): HumanMesh? = shared ?: synchronized(this) {
            shared ?: fromResource()?.also { shared = it }
        }
    }
}
