import kotlinx.coroutines.*
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.EmptyCoroutineContext

class MailServer(
    val localhostName: String,
    val scope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)
) {

    fun createAndLaunchHandlerFor(socket: Socket): Job {
        val protocol = SMTPReceiveProtocol(localhostName, socket.inetAddress)
        return protocol.executeAsync(scope + CoroutineName("connection handler from ${socket.inetAddress}"), Protocol.IO.FromSocket(socket))
    }

    suspend fun createServer(port: Int) {
        listenToServerSocket(ServerSocket(port))
    }

    suspend fun listenToServerSocket(serverSocket: ServerSocket) {
        withContext(Dispatchers.Unconfined) {
            while (true) {
                val newIncomingConnection =
                    withContext(Dispatchers.IO) { serverSocket.accept() }
                createAndLaunchHandlerFor(newIncomingConnection)
            }
        }
    }

}
