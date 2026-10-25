package com.aroundus.app.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * Embedded HTTP Loopback Server running on localhost:3000.
 *
 * When Supabase OAuth redirect URL (aroundus://auth/callback) is not yet
 * whitelisted in the Supabase Dashboard, Supabase falls back to its default
 * Site URL: http://localhost:3000/#access_token=...
 *
 * On Android, this normally causes Chrome to display:
 * "localhost:3000 This site can't be reached (ERR_CONNECTION_REFUSED)"
 *
 * This server catches that request on port 3000, returns an HTML bridge that
 * reads the fragment (#access_token=...) and query parameters (?code=...),
 * and immediately redirects Chrome back into AroundUS via aroundus://auth/callback.
 */
object OAuthLoopbackServer {

    private const val TAG = "OAuthLoopbackServer"
    private const val PORT = 3000
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    fun start(context: Context) {
        stop()

        serverJob = scope.launch {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(PORT))
                serverSocket = socket
                Log.d(TAG, "OAuth loopback server successfully started on port $PORT")

                // Auto stop after 3 minutes if login flow is abandoned
                val timeoutJob = launch {
                    delay(180_000)
                    stop()
                }

                while (socket.isBound && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        launch {
                            handleClient(client)
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
                timeoutJob.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Could not start loopback server on port $PORT: ${e.message}")
            }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 4000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: ""
            Log.d(TAG, "Incoming HTTP request on loopback: $requestLine")

            // Consume remaining headers
            var line = reader.readLine()
            while (!line.isNullOrBlank()) {
                line = reader.readLine()
            }

            // Respond 204 to favicon requests
            if (requestLine.startsWith("GET /favicon.ico")) {
                val noContent = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
                client.getOutputStream().write(noContent.toByteArray(StandardCharsets.UTF_8))
                client.getOutputStream().flush()
                client.close()
                return
            }

            val html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>AroundUS Authentication</title>
                    <style>
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                            background: linear-gradient(135deg, #090d16 0%, #111827 50%, #1e1b4b 100%);
                            color: #ffffff;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            min-height: 100vh;
                            padding: 24px;
                            text-align: center;
                        }
                        .container {
                            background: rgba(30, 41, 59, 0.75);
                            backdrop-filter: blur(20px);
                            -webkit-backdrop-filter: blur(20px);
                            border: 1px solid rgba(255, 255, 255, 0.12);
                            border-radius: 24px;
                            padding: 40px 28px;
                            max-width: 400px;
                            width: 100%;
                            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7);
                        }
                        .icon-box {
                            width: 72px;
                            height: 72px;
                            margin: 0 auto 24px auto;
                            border-radius: 20px;
                            background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            box-shadow: 0 10px 25px rgba(99, 102, 241, 0.4);
                        }
                        .spinner {
                            width: 36px;
                            height: 36px;
                            border: 3.5px solid rgba(255, 255, 255, 0.25);
                            border-top-color: #ffffff;
                            border-radius: 50%;
                            animation: spin 0.8s linear infinite;
                        }
                        @keyframes spin {
                            to { transform: rotate(360deg); }
                        }
                        h2 {
                            font-size: 22px;
                            font-weight: 700;
                            margin-bottom: 10px;
                            color: #ffffff;
                            letter-spacing: -0.3px;
                        }
                        p {
                            font-size: 14px;
                            color: #94a3b8;
                            margin-bottom: 26px;
                            line-height: 1.5;
                        }
                        .btn {
                            display: block;
                            background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
                            color: #ffffff;
                            text-decoration: none;
                            padding: 14px 24px;
                            border-radius: 14px;
                            font-weight: 600;
                            font-size: 15px;
                            letter-spacing: 0.2px;
                            box-shadow: 0 4px 15px rgba(79, 70, 229, 0.4);
                            transition: transform 0.15s ease;
                        }
                        .btn:active {
                            transform: scale(0.98);
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="icon-box">
                            <div class="spinner"></div>
                        </div>
                        <h2>Returning to AroundUS...</h2>
                        <p>Google sign-in completed! Redirecting you back to the app to complete your profile.</p>
                        <a id="appBtn" href="#" class="btn">Open AroundUS App</a>
                    </div>
                    <script>
                        (function() {
                            var search = window.location.search || '';
                            var hash = window.location.hash || '';
                            var target = 'aroundus://auth/callback' + search + hash;
                            
                            var btn = document.getElementById('appBtn');
                            if (btn) btn.href = target;
                            
                            try {
                                window.location.replace(target);
                            } catch (e) {}

                            setTimeout(function() {
                                try {
                                    window.location.href = target;
                                } catch (e) {}
                            }, 250);
                        })();
                    </script>
                </body>
                </html>
            """.trimIndent()

            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            val responseHeader = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=UTF-8\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"

            val out: OutputStream = client.getOutputStream()
            out.write(responseHeader.toByteArray(StandardCharsets.UTF_8))
            out.write(bytes)
            out.flush()
            client.close()
            Log.d(TAG, "Sent redirect HTML response to browser on port $PORT")
        } catch (e: Exception) {
            Log.w(TAG, "Error responding to HTTP client: ${e.message}")
            try { client.close() } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        Log.d(TAG, "OAuth loopback server stopped")
    }
}
