package com.personal.calisthenics.core.rig

/**
 * Subtle muscle definition painted as soft light and shadow washes on the body (pec edges, abs, spine groove, arm and
 * thigh muscle bellies). They are clipped to the body part they sit on, so they read as shading of the surface, not
 * as shapes, and they only draw on the side of the body that faces the camera.
 */
internal object Anatomy {

    private class Spots(val host: String, val material: Material, val alpha: Float) {
        val shapes = mutableListOf<Shape3>()
    }

    fun washes(sk: Skeleton, cam: Camera): List<Wash3> {
        val out = mutableListOf<Spots>()
        fun spots(host: String, material: Material, alpha: Float) = Spots(host, material, alpha).also { out += it }
        val dark = Material.SHADOW
        val light = Material.GUIDE

        val front = sk.front
        val x = sk.side
        fun torso(t: Float) = if (t < 0.5f) lerp(sk.hip, sk.mid, t / 0.5f) else lerp(sk.mid, sk.shoulder, (t - 0.5f) / 0.5f)
        val facing = front.dot(cam.toCamera)

        if (facing > 0.05f) {
            val creases = spots("torso", dark, 0.16f * facing.coerceAtMost(1f).let { 0.4f + 0.6f * it })
            for (s in listOf(-1f, 1f)) {
                // Lower edge of each pec, and the ribs / abs rows.
                creases.shapes += Tube3(torso(0.69f) + x * (2.5f * s) + front * 9.3f, torso(0.70f) + x * (13f * s) + front * 7.2f, 1.7f, 1.4f)
                for (t in listOf(0.30f, 0.41f, 0.52f)) {
                    creases.shapes += Tube3(torso(t) + x * (1.6f * s) + front * 9.9f, torso(t) + x * (6.2f * s) + front * 9.0f, 0.9f, 0.9f)
                }
            }
            creases.shapes += Tube3(torso(0.20f) + front * 10.2f, torso(0.66f) + front * 10.4f, 0.8f, 0.8f)
            val shine = spots("torso", light, 0.10f)
            for (s in listOf(-1f, 1f)) {
                shine.shapes += Sphere3(torso(0.80f) + x * (7.5f * s) + front * 9.4f, 5.4f)
                shine.shapes += Tube3(torso(0.34f) + x * (3.0f * s) + front * 9.6f, torso(0.54f) + x * (3.2f * s) + front * 9.4f, 2.2f, 2.2f)
            }
        } else if (facing < -0.05f) {
            val grooves = spots("torso", dark, 0.15f)
            grooves.shapes += Tube3(torso(0.10f) - front * 10.2f, torso(0.92f) - front * 10.0f, 1.0f, 1.0f)
            for (s in listOf(-1f, 1f)) {
                grooves.shapes += Tube3(torso(0.62f) + x * (3.2f * s) - front * 9.8f, torso(0.84f) + x * (10.5f * s) - front * 8.6f, 1.3f, 1.3f)
                grooves.shapes += Tube3(torso(0.30f) + x * (4.5f * s) - front * 10.0f, torso(0.52f) + x * (4.6f * s) - front * 10.2f, 1.1f, 1.1f)
            }
            val shine = spots("torso", light, 0.09f)
            for (s in listOf(-1f, 1f)) {
                shine.shapes += Tube3(torso(0.40f) + x * (10f * s) - front * 8.2f, torso(0.78f) + x * (13f * s) - front * 7f, 4.2f, 3.6f)
                shine.shapes += Sphere3(torso(0.86f) + x * (6f * s) - front * 9.6f, 4.6f)
            }
        }

        // Arms: deltoid and biceps lit, triceps line darkened.
        for ((id, sh, el, wr) in listOf(
            Arm("armL", sk.shoulderL, sk.elbowL, sk.wristL), Arm("armR", sk.shoulderR, sk.elbowR, sk.wristR),
        )) {
            val n = FigureBuilder.frontNormal(sh, el)
            val facesCamera = n.dot(cam.toCamera)
            val lit = spots(id, light, 0.12f)
            val shade = spots(id, dark, 0.14f)
            val sign = if (id == "armL") -1f else 1f
            // Deltoid cap sits on the outside of the shoulder.
            lit.shapes += Sphere3(sh + x * (2.2f * sign) + Vec3(0f, 1.8f, 0f), 3.8f)
            if (facesCamera > -0.05f) {
                lit.shapes += Tube3(lerp(sh, el, 0.28f) + n * 3.9f, lerp(sh, el, 0.66f) + n * 3.6f, 1.5f, 1.4f)
                val nf = FigureBuilder.frontNormal(el, wr)
                lit.shapes += Tube3(lerp(el, wr, 0.10f) + nf * 3.0f, lerp(el, wr, 0.45f) + nf * 3.0f, 1.3f, 1.2f)
            }
            if (facesCamera < 0.05f) shade.shapes += Tube3(lerp(sh, el, 0.25f) - n * 3.9f, lerp(sh, el, 0.75f) - n * 3.4f, 1.1f, 1.0f)
        }

        // Legs: quadriceps lit with a darker line between the heads, calf belly lit.
        for ((id, hp, kn, an) in listOf(
            Arm("legL", sk.hipL, sk.kneeL, sk.ankleL), Arm("legR", sk.hipR, sk.kneeR, sk.ankleR),
        )) {
            val n = FigureBuilder.frontNormal(hp, kn)
            val nShin = FigureBuilder.frontNormal(kn, an)
            val lit = spots(id, light, 0.10f)
            val shade = spots(id, dark, 0.12f)
            if (n.dot(cam.toCamera) > -0.05f) {
                lit.shapes += Tube3(lerp(hp, kn, 0.22f) + n * 5.6f, lerp(hp, kn, 0.72f) + n * 5.2f, 2.0f, 1.8f)
                shade.shapes += Tube3(lerp(hp, kn, 0.35f) + n * 4.8f + x * (if (id == "legL") 2.6f else -2.6f), lerp(hp, kn, 0.85f) + n * 4.0f, 0.9f, 0.9f)
            }
            if (nShin.dot(cam.toCamera) < 0.05f) lit.shapes += Sphere3(lerp(kn, an, 0.27f) - nShin * 4.3f, 2.8f)
            if (nShin.dot(cam.toCamera) > -0.05f) shade.shapes += Tube3(lerp(kn, an, 0.2f) + nShin * 3.6f, lerp(kn, an, 0.7f) + nShin * 3.0f, 0.8f, 0.7f)
        }
        return out.filter { it.shapes.isNotEmpty() }.map { Wash3(it.host, it.shapes, it.material, it.alpha) }
    }

    private data class Arm(val id: String, val a: Vec3, val b: Vec3, val c: Vec3)
}
