package com.hcifuture.producer.detector.utils

import android.util.Log
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MadgwickFilter(val beta: Float, var q: Quaternion, val frequency: Float) {
    private var initalized = false
    fun update(imu: List<Float>) {
        var acc = Vector3(imu[0], imu[1], imu[2])
        val gyr = Vector3(imu[3], imu[4], imu[5])
        if (acc.len() == 0.0f) {
            return
        }
        acc /= acc.len()
        if (!initalized) {
            initalized = true
            val ex = atan2(acc.y, acc.z)
            val ey = atan2(-acc.x, sqrt(acc.y * acc.y + acc.z * acc.z))
            val cx2 = cos(ex / 2.0f)
            val sx2 = sin(ex / 2.0f)
            val cy2 = cos(ey / 2.0f)
            val sy2 = sin(ey / 2.0f)
            q = Quaternion(cx2 * cy2, sx2 * cy2, cx2 * sy2, -sx2 * sy2).unit()
            return
        }
        val f = Vector3(
            2 * (q.x * q.z - q.w * q.y) - acc.x,
            2 * (q.w * q.x + q.y * q.z) - acc.y,
            2 * (0.5f - q.x * q.x - q.y * q.y) - acc.z,
        )
        val j = listOf(
            listOf(-2f * q.y, 2f * q.x, 0.0f),
            listOf(2f * q.z, 2f * q.w, -4 * q.x),
            listOf(-2f * q.w, 2f * q.z, -4f * q.y),
            listOf(2f * q.x, 2f * q.y, 0.0f),
        )
        val step = Quaternion(
            j[0][0] * f.x + j[0][1] * f.y + j[0][2] * f.z,
            j[1][0] * f.x + j[1][1] * f.y + j[1][2] * f.z,
            j[2][0] * f.x + j[2][1] * f.y + j[2][2] * f.z,
            j[3][0] * f.x + j[3][1] * f.y + j[3][2] * f.z,
        ).unit()
        val qdot = (q * Quaternion(0.0f, gyr.x, gyr.y, gyr.z)) * 0.5f - beta * step
        q += qdot * (1.0f / frequency)
        q = q.unit()
    }
}

//class MadgwickFilter(val beta: Double, val q: MutableList<Double>, val frequency: Double) {
//    fun update(data: MutableList<Double>) {
//        var norm = 0.0
//        val s: MutableList<Double> = mutableListOf(0.0, 0.0, 0.0, 0.0)
//        val qDot: MutableList<Double> = mutableListOf(0.0, 0.0, 0.0, 0.0)
//        val _2q: MutableList<Double> = mutableListOf(0.0, 0.0, 0.0, 0.0)
//        val _4q: MutableList<Double> = mutableListOf(0.0, 0.0, 0.0)
//        val _8q: MutableList<Double> = mutableListOf(0.0, 0.0)
//        val qq: MutableList<Double> = mutableListOf(0.0, 0.0, 0.0, 0.0)
//
//        qDot[0] = 0.5 * (-q[1] * data[0] - q[2] * data[1] - q[3] * data[2])
//        qDot[1] = 0.5 * (q[0] * data[0] + q[2] * data[2] - q[3] * data[1])
//        qDot[2] = 0.5 * (q[0] * data[1] - q[1] * data[2] + q[3] * data[0])
//        qDot[3] = 0.5 * (q[0] * data[2] + q[1] * data[1] - q[2] * data[0])
//        if (!((data[3] == 0.0) && (data[4] == 0.0) && (data[5] == 0.0))) {
//            norm = 1.0 / sqrt(data[3] * data[3] + data[4] * data[4] + data[5] * data[5])
//            data[3] *= norm
//            data[4] *= norm
//            data[5] *= norm
//            _2q[0] = 2 * q[0]
//            _2q[1] = 2 * q[1]
//            _2q[2] = 2 * q[2]
//            _2q[3] = 2 * q[3]
//            _4q[0] = 4 * q[0]
//            _4q[1] = 4 * q[1]
//            _4q[2] = 4 * q[2]
//            _8q[0] = 8 * q[0]
//            _8q[1] = 8 * q[1]
//            qq[0] = q[0] * q[0]
//            qq[1] = q[1] * q[1]
//            qq[2] = q[2] * q[2]
//            qq[3] = q[3] * q[3]
//
//            s[0] = _4q[0] * qq[2] + _2q[2] * data[3] + _4q[0] * qq[1] - _2q[1] * data[4]
//            s[1] = _4q[1] * qq[3] - _2q[3] * data[3] + 4.0 * qq[0] * q[1] - _2q[0] * data[4] - _4q[1] + _8q[0] * qq[1] + _8q[0] * qq[2] + _4q[1] * data[5]
//            s[2] = 4.0f * qq[0] * q[2] + _2q[0] * data[3] + _4q[2] * qq[3] - _2q[3] * data[4] - _4q[2] + _8q[1] * qq[1] + _8q[1] * qq[2] + _4q[2] * data[5]
//            s[3] = 4.0f * qq[1] * q[3] - _2q[1] * data[3] + 4.0f * qq[2] * q[3] - _2q[2] * data[4]
//
//            norm = 1.0 / sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2] + s[3] * s[3])
//
//            qDot[0] -= beta * s[0] * norm
//            qDot[1] -= beta * s[1] * norm
//            qDot[2] -= beta * s[2] * norm
//            qDot[3] -= beta * s[3] * norm
//        }
//        q[0] += qDot[0] / frequency
//        q[1] += qDot[1] / frequency
//        q[2] += qDot[2] / frequency
//        q[3] += qDot[3] / frequency
//
//        norm = 1.0 / sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
//        q[0] *= norm
//        q[1] *= norm
//        q[2] *= norm
//        q[3] *= norm
//    }
//}