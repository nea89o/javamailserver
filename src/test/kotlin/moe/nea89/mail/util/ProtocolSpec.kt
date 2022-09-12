package moe.nea89.mail.util

import Protocol
import findSubarray
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import readUntil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream


data class ByteArrayString(val hay: String, val needle: String, val position: Int?, val offset: Int = 0)
class ProtocolSpec : FreeSpec({
    "ByteArray.findSubarray" - {
        "should work correctly" - {
            withData(
                listOf(
                    ByteArrayString("abc", "a", 0),
                    ByteArrayString("abca", "a", 0),
                    ByteArrayString("abca", "a", 3, 2),
                    ByteArrayString("abca", "a", 3, 3),
                    ByteArrayString("bc", "a", null),
                    ByteArrayString("acbcab", "ab", 4),
                    ByteArrayString("abcbcab", "ab", 5, 1),
                )
            ) { (hay, needle, position, offset) ->
                val hay = hay.encodeToByteArray()
                val needle = needle.encodeToByteArray()
                assert(hay.findSubarray(needle, offset) == position)
            }
        }
    }
    "Protocol.IO" - {
        "readUntil" {
            val data = ("a".repeat(9) + "01" + "b".repeat(10) + "02").encodeToByteArray()
            val protIO = Protocol.IO.FromStreams(ByteArrayInputStream(data), ByteArrayOutputStream())
            assert(protIO.readUntil("01".encodeToByteArray()).decodeToString() == "a".repeat(9))
            assert(protIO.readUntil("02".encodeToByteArray()).decodeToString() == "b".repeat(10))
        }
    }
})
