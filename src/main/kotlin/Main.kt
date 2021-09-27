import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 1) {
            System.err.println("Use ./javamailteste run/install")
        }
        when (args[0]) {
            "run" -> runServer(2500)
        }
    }

    fun runServer(port: Int) = runBlocking(Dispatchers.Default) {
        val ss = ServerSocket(port)
        val jobs = mutableListOf<Job>()
        println("Starting SMTP socket on port $port")
        while (true) {
            val scope = CoroutineScope(Dispatchers.Default)
            val socket = with(Dispatchers.IO) { ss.accept() }
            val prot = SMTPReceiveProtocol("nea89.moe", socket.inetAddress)
            jobs.add(prot.executeAsync(scope, Protocol.IO.FromSocket(socket)))
            println("jobs: $jobs")
        }
    }
}
