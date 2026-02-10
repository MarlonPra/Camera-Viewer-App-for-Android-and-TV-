package com.marlonpra.cameraviewertv

import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

object RtspProbe {

    data class Result(
        val ok: Boolean,
        val message: String,
    )

    fun probe(url: String, timeoutMs: Int = 2500): Result {
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return Result(false, "URL inválida")
        }

        val host = uri.host ?: return Result(false, "Host inválido")
        val port = if (uri.port > 0) uri.port else 554

        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs

            val out = PrintWriter(socket.getOutputStream(), true)
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))

            out.print("OPTIONS $url RTSP/1.0\r\n")
            out.print("CSeq: 1\r\n")
            out.print("User-Agent: CameraViewerTV\r\n")
            out.print("\r\n")
            out.flush()

            val statusLine = input.readLine() ?: return Result(false, "Sin respuesta RTSP")
            if (statusLine.startsWith("RTSP/1.0 200")) {
                Result(true, "OK")
            } else if (statusLine.startsWith("RTSP/1.0 401")) {
                Result(true, "Requiere autenticación")
            } else {
                Result(false, statusLine)
            }
        } catch (e: Exception) {
            Result(false, e.message ?: "No se pudo conectar")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }
}
