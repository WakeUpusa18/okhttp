/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.internal.ws

import java.io.IOException
import okhttp3.Response
import okhttp3.internal.delimiterOffset
import okhttp3.internal.trimSubstring

/**
 * Models the contents of a `Sec-WebSocket-Extensions` response header. OkHttp honors one extension
 * `permessage-deflate` and two of its parameters, `client_no_context_takeover` and
 * `server_no_context_takeover`.
 *
 * Typically this will look like one of the following:
 *
 * ```
 * Sec-WebSocket-Extensions: permessage-deflate
 * Sec-WebSocket-Extensions: permessage-deflate; client_no_context_takeover
 * Sec-WebSocket-Extensions: permessage-deflate; server_no_context_takeover
 * Sec-WebSocket-Extensions: permessage-deflate; server_no_context_takeover; client_no_context_takeover
 * ```
 *
 * If any other extension or parameter is specified, then [unknownValues] will be true. Such
 * responses should be refused as their web socket extensions will not be understood.
 *
 * Note that the `client_max_window_bits` and `server_max_window_bits` parameters are unknown
 * because we do not expect servers to return them. (OkHttp is unable to handle any number of bits
 * other than 15 (32 KiB) due to a lack of configurability in [java.util.zip.Deflater].)
 *
 * See [RFC 7692, 7.1][rfc_7692] for details on negotiation process.
 *
 * [rfc_7692]: https://tools.ietf.org/html/rfc7692#section-7.1
 */
data class WebSocketExtensions(
  /** True if the agreed upon extensions includes the permessage-deflate extension. */
  @JvmField val perMessageDeflate: Boolean = false,

  /** True if the agreed upon extension parameters includes "client_no_context_takeover". */
  @JvmField val clientNoContextTakeover: Boolean = false,

  /** True if the agreed upon extension parameters includes "server_no_context_takeover". */
  @JvmField val serverNoContextTakeover: Boolean = false,

  /**
   * True if the agreed upon extensions or parameters contained values unrecognized by OkHttp.
   * Typically this indicates that the client will need to close the web socket with code 1010.
   */
  @JvmField val unknownValues: Boolean = false
) {
  companion object {
    private const val HEADER_WEB_SOCKET_EXTENSION = "Sec-WebSocket-Extensions"

    @Throws(IOException::class)
    fun parse(response: Response): WebSocketExtensions {
      // Note that this code does case-insensitive comparisons, even though the spec doesn't specify
      // whether extension tokens and parameters are case-insensitive or not.

      var compressionEnabled = false
      var clientNoContextTakeover = false
      var serverNoContextTakeover = false
      var unexpectedValues = false

      // Parse each header.
      for (i in 0 until response.headers.size) {
        if (!response.headers.name(i).equals(HEADER_WEB_SOCKET_EXTENSION, ignoreCase = true)) {
          continue // Not a header we're interested in.
        }
        val header = response.headers.value(i)

        // Parse each extension.
        var pos = 0
        while (pos < header.length) {
          val extensionEnd = header.delimiterOffset(',', pos)
          val extensionTokenEnd = header.delimiterOffset(';', pos, extensionEnd)
          val extensionToken = header.trimSubstring(pos, extensionTokenEnd)
          pos = extensionTokenEnd + 1

          when {
            extensionToken.equals("permessage-deflate", ignoreCase = true) -> {
              if (compressionEnabled) unexpectedValues = true // Repeated extension!
              compressionEnabled = true

              // Parse each permessage-deflate parameter.
              while (pos < extensionEnd) {
                val parameterEnd = header.delimiterOffset(';', pos, extensionEnd)
                val parameter = header.trimSubstring(pos, parameterEnd)
                pos = parameterEnd + 1
                when {
                  parameter.equals("client_no_context_takeover", ignoreCase = true) -> {
                    if (clientNoContextTakeover) unexpectedValues = true // Repeated parameter!
                    clientNoContextTakeover = true
                  }
                  parameter.equals("server_no_context_takeover", ignoreCase = true) -> {
                    if (serverNoContextTakeover) unexpectedValues = true // Repeated parameter!
                    serverNoContextTakeover = true
                  }
                  else -> {
                    unexpectedValues = true // Unexpected parameter.
                  }
                }
              }
            }

            else -> {
              unexpectedValues = true // Unexpected extension.
            }
          }
        }
      }

      return WebSocketExtensions(
          perMessageDeflate = compressionEnabled,
          clientNoContextTakeover = clientNoContextTakeover,
          serverNoContextTakeover = serverNoContextTakeover,
          unknownValues = unexpectedValues
      )
    }
  }
}
