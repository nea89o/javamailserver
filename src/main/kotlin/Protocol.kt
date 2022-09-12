import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class Invalidatable {
    var isInvalid: Boolean = false
    fun checkValid() {
        if (isInvalid)
            throw IllegalStateException("Accessed invalid object")
    }

    fun invalidate() {
        isInvalid = true
    }
}

abstract class Protocol {
    interface IO {
        fun isOpen(): Boolean
        suspend fun pushBack(data: ByteArray)
        suspend fun readBytes(into: ByteArray): Int
        suspend fun send(bytes: ByteArray)
        suspend fun close()

        class FromSocket(val socket: Socket) : FromStreams(socket.getInputStream(), socket.getOutputStream()) {
            override suspend fun close() {
                super.close()
                withContext(Dispatchers.IO) {
                    socket.close()
                }
            }
        }

        open class FromStreams(val inputStream: InputStream, val outputStream: OutputStream) : IO {
            private val i = Invalidatable()
            override fun isOpen(): Boolean =
                !i.isInvalid


            val readBuffer = mutableListOf<ByteArray>()
            override suspend fun pushBack(data: ByteArray) {
                i.checkValid()
                if (data.isEmpty()) return
                readBuffer.add(0, data)
            }

            override suspend fun send(bytes: ByteArray) {
                i.checkValid()
                withContext(Dispatchers.IO) {
                    outputStream.write(bytes)
                    outputStream.flush()
                }
            }

            override suspend fun close() {
                i.checkValid()
                i.invalidate()
                withContext(Dispatchers.IO) {
                    inputStream.close()
                    outputStream.close()
                }
            }

            override suspend fun readBytes(into: ByteArray): Int {
                i.checkValid()
                val rb = readBuffer.removeFirstOrNull()
                if (rb != null) {
                    val w = minOf(rb.size, into.size)
                    rb.copyInto(into, 0, 0, w)
                    return w
                }
                return withContext(Dispatchers.IO) {
                    inputStream.read(into)
                }
            }
        }
    }

    protected abstract suspend fun IO.execute()

    fun executeAsync(scope: CoroutineScope, io: Protocol.IO): Job {
        return scope.launch {
            io.execute()
        }
    }
}

suspend fun Protocol.IO.readAll(): ByteArray {
    var ret = ByteArray(0)
    val buffer = ByteArray(4096)
    while (true) {
        val read = readBytes(buffer)
        if (read == -1) {
            return ret
        }
        val oldSize = ret.size
        ret = ret.copyOf(oldSize + read)
        buffer.copyInto(ret, oldSize, endIndex = read)
    }
}

suspend fun Protocol.IO.send(string: String) = send(string.encodeToByteArray())
suspend fun Protocol.IO.readLine(): String = readUntil(CRLF).decodeToString()
suspend fun Protocol.IO.readUntil(search: ByteArray, errorOnEOF: Boolean = true): ByteArray {
    var ret = ByteArray(0)
    val buffer = ByteArray(4096)
    while (true) {
        val read = readBytes(buffer)
        if (read == -1) {
            if (errorOnEOF) {
                throw IllegalStateException("End of Protocol.IO")
            } else {
                return ret
            }
        }
        val oldSize = ret.size
        ret = ret.copyOf(oldSize + read)
        buffer.copyInto(ret, oldSize, endIndex = read)
        val firstFoundIndex = ret.findSubarray(search, startIndex = (oldSize - search.size - 1).coerceAtLeast(0))
        if (firstFoundIndex != null) {
            pushBack(ret.copyOfRange(firstFoundIndex + search.size, ret.size))
            return ret.copyOf(firstFoundIndex)
        }
    }
}

val CRLF = "\r\n".encodeToByteArray()

fun ByteArray.findSubarray(subarray: ByteArray, startIndex: Int = 0): Int? {
    if (subarray.size > size - startIndex) return null
    for (i in startIndex..size - subarray.size) {
        var isEqual = true
        for (j in subarray.indices) {
            if (this[i + j] != subarray[j]) {
                isEqual = false
                break
            }
        }
        if (isEqual) {
            return i
        }
    }
    return null
}

suspend fun Protocol.IO.pushBack(string: String) = pushBack(string.encodeToByteArray())
suspend fun Protocol.IO.lookahead(string: String): Boolean = lookahead(string.encodeToByteArray())
suspend fun Protocol.IO.lookahead(bytes: ByteArray): Boolean {
    val buffer = ByteArray(bytes.size)
    val read = readBytes(buffer)
    if (read != bytes.size || !buffer.contentEquals(bytes)) {
        pushBack(buffer.copyOf(read))
        return false
    }
    return true
}
