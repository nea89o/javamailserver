import kotlinx.coroutines.MainScope
import java.io.ByteArrayOutputStream
import java.io.File

suspend fun main() {
    val io = Protocol.IO.FromStreams(File("samplemail.txt").inputStream(), ByteArrayOutputStream())
    val rfc = RFC822Parser()
    rfc.executeAsync(MainScope(), io).join()
    println(rfc.headers)
    println("Content: ${rfc.content.decodeToString()}")
}

class RFC822Parser() : Protocol() {

    class Header(val field: String, val value: ByteArray)

    private val _headers = mutableListOf<Header>()
    val headers: List<Header> get() = _headers
    lateinit var content: ByteArray
        private set

    override suspend fun IO.execute() {
        while (parseField()) Unit
        content = readAll()
    }

    private suspend fun IO.parseField(): Boolean {
        val read = readUntil(CRLF)
        if (read.contentEquals(CRLF)) {
            return false
        }
        val indexOfColon = read.indexOf(':'.code.toByte())
        if (indexOfColon == -1) {
            throw IllegalStateException("Expected : in MIME header")
        }
        val headerField = read.sliceArray(0 until indexOfColon).decodeToString().trim()
        var data = read.sliceArray(indexOfColon + 1 until read.size)
        while (true) {
            val nextLine = readUntil(CRLF)
            if (nextLine.isNotEmpty() && isWhitespaceCharacter(nextLine[0])) {
                val oldSize = data.size
                data = data.copyOf(oldSize + nextLine.size)
                nextLine.copyInto(data, oldSize)
            } else {
                pushBack(CRLF)
                pushBack(nextLine)
                break
            }
        }
        _headers.add(Header(headerField, data))
        return true
    }

    fun isWhitespaceCharacter(char: Byte): Boolean {
        val char = char.toInt().toChar()
        return char == ' ' || char == '\t'
    }
}
