package com.manahive.harbor.batch

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.manahive.harbor.NoticeEvent
import com.manahive.harbor.toJson
import java.io.File

private val mapper = jacksonObjectMapper()

/**
 * Fowler: "Move伴にメソッド" — the list knows how to write itself.
 * Eliminates the wrapper object.
 */
public fun List<NoticeEvent>.writeTo(file: File) {
    file.parentFile?.mkdirs()
    file.bufferedWriter().use { writer ->
        for (event in this) {
            val json = event.toJson()
            writer.write(mapper.writeValueAsString(json))
            writer.newLine()
        }
    }
}
