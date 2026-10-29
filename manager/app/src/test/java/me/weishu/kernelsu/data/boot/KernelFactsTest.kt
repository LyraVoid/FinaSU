package me.weishu.kernelsu.data.boot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KernelFactsTest {
    @Test
    fun `parses slot evidence image metadata and original field sources`() {
        val facts = KernelFacts.parse(
            """
            {
              "schema": 1,
              "parser": "finasu-boot-facts/1",
              "read_at": "2026-10-29T00:00:00Z",
              "current_slot": "a",
              "slot_evidence": [
                {"source":"ro.boot.slot_suffix","slot":"a","current":true,"status":"ok","detail":"_a"},
                {"source":"by-name","slot":"b","current":false,"status":"present","detail":"boot_b"}
              ],
              "conflicts": [],
              "slots": [
                {
                  "id":"a","current":true,"exists":true,"readable":true,"confidence":"high",
                  "reason":null,
                  "images":[{
                    "role":"boot","name":"boot_a","path":"/dev/block/by-name/boot_a",
                    "resolved_path":"/dev/block/sda1","exists":true,"readable":true,
                    "format":"boot_v0","header_version":0,"payload_size":4096,
                    "sha256":"hash-a","reason":null,
                    "avb":{"footer_version":"1.0","original_image_size":4096,
                      "vbmeta_offset":4096,"vbmeta_size":64,"signature_verified":false}
                  }],
                  "fields":{
                    "release":{"value":"6.6.30-a","source":"boot_a:linux_banner","confidence":"high"},
                    "version":{"value":"#1 A","source":"boot_a:linux_banner","confidence":"high"},
                    "cmdline":[{"value":"console=ttynull","source":"boot_a:header","confidence":"high"}],
                    "bootconfig":[]
                  },
                  "missing_fields":["bootconfig"]
                },
                {
                  "id":"b","current":false,"exists":false,"readable":false,
                  "confidence":"unavailable","reason":"not present","images":[],
                  "fields":{"release":null,"version":null,"cmdline":[],"bootconfig":[]},
                  "missing_fields":["release","version","cmdline","bootconfig"]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("a", facts.currentSlot)
        assertTrue(facts.evidence.first().current)
        val slot = facts.slot("a")!!
        assertEquals(0, slot.images.single().headerVersion)
        assertTrue(slot.images.single().avb!!.signatureVerified.not())
        assertEquals("boot_a:linux_banner", slot.release!!.source)
        assertTrue(slot.sourceIdentity!!.contains("hash-a"))
        assertEquals("not present", facts.slot("b")!!.reason)
    }

    @Test
    fun `absent evidence slot stays null instead of the text null`() {
        val facts = KernelFacts.parse(
            """{"parser":"x","current_slot":null,"slot_evidence":[
            {"source":"bootctl","slot":null,"current":true,"status":"unavailable","detail":null}],
            "conflicts":[],"slots":[],"read_at":"now"}""",
        )

        assertNull(facts.currentSlot)
        assertNull(facts.evidence.single().slot)
        assertNull(facts.evidence.single().detail)
    }
}
