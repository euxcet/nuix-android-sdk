package com.hcifuture.producer.common.utils

open class NuixEvent() {
    constructor(name: String, source: String? = "") : this() {
        this.name = name
        this.source = source
    }
    constructor(name: String, data: Map<String, Any?>?, source: String? = "") : this(name, source) {
        this.data = data
    }
    var name: String = ""
    var source: String? = ""
    var data: Map<String, Any?>? = null
}
