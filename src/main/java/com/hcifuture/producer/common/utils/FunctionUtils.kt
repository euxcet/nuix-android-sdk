package com.hcifuture.producer.common.utils

import java.lang.IllegalArgumentException

object FunctionUtils {
    inline fun <reified T> reifiedValue(value: Any): T {
        return when (value) {
            is T -> value
            else -> throw IllegalArgumentException("Unsupported type")
        }
    }

    fun <K, V> flatten(list: List<Map<K, V>>): Map<K, V> =
        mutableMapOf<K, V>().apply {
            for (innerMap in list) putAll(innerMap)
        }
}