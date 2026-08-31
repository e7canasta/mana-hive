package com.manahive.runtime

import com.manahive.kernel.BedId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class HiveResetController(
    private val runtime: NightWatchRuntime,
) {
    @PostMapping("/api/hive/reset")
    fun reset(
        @RequestParam(required = false) bedId: String?,
        @RequestParam(required = false) residentId: String?,
    ): ResponseEntity<Map<String, Any>> {
        var count = 0
        if (bedId != null) {
            val bed = BedId(bedId)
            runtime.residents().forEach { (rid, rt) ->
                if (rt.bed == bed) {
                    synchronized(rt) { rt.reset() }
                    count++
                }
            }
        } else if (residentId != null) {
            runtime.get(com.manahive.kernel.ResidentId(residentId))?.let {
                synchronized(it) { it.reset() }
                count = 1
            }
        } else {
            runtime.residents().forEach { (_, rt) -> synchronized(rt) { rt.reset() } }
            count = runtime.size
        }
        return ResponseEntity.ok(mapOf("reset" to count, "bedId" to (bedId ?: ""), "residentId" to (residentId ?: "")))
    }
}
