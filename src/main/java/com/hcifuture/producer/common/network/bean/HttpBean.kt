package com.hcifuture.producer.common.network.bean

data class BaseBean<T>(
    var data: T?,
    var errorCode: Int,
    var errorMsg: String,
)

data class CharacterResult(
    val result: List<String>,
)