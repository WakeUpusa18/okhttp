/*
 * Copyright (C) 2019 Square, Inc.
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

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class WebSocketExtensionsTest {
  private val minimalResponse = Response.Builder()
      .protocol(Protocol.HTTP_1_1)
      .code(200)
      .message("OK")
      .request(
          Request.Builder()
              .url("https://example.com/")
              .build()
      )
      .build()

  @Test
  fun emptyHeader() {
    assertThat(parse("")).isEqualTo(WebSocketExtensions())
  }

  @Test fun noExtensionHeader() {
    assertThat(WebSocketExtensions.parse(minimalResponse))
        .isEqualTo(WebSocketExtensions())
  }

  @Test
  fun emptyExtension() {
    assertThat(parse(", permessage-deflate"))
        .isEqualTo(WebSocketExtensions(perMessageDeflate = true, unknownValues = true))
  }

  @Test
  fun unknownExtension() {
    assertThat(parse("unknown-ext"))
        .isEqualTo(WebSocketExtensions(unknownValues = true))
  }

  @Test
  fun perMessageDeflate() {
    assertThat(parse("permessage-deflate"))
        .isEqualTo(WebSocketExtensions(perMessageDeflate = true))
  }

  @Test
  fun emptyParameters() {
    assertThat(parse("permessage-deflate;"))
        .isEqualTo(WebSocketExtensions(perMessageDeflate = true))
  }

  @Test
  fun repeatedPerMessageDeflate() {
    assertThat(parse("permessage-deflate, permessage-deflate; server_no_context_takeover"))
        .isEqualTo(WebSocketExtensions(
            perMessageDeflate = true,
            serverNoContextTakeover = true,
            unknownValues = true
        ))
  }

  @Test
  fun multiplePerMessageDeflateHeaders() {
    val response = minimalResponse.newBuilder()
        .header("Sec-WebSocket-Extensions", "")
        .header("Sec-WebSocket-Extensions", "permessage-deflate")
        .build()
    val extensions = WebSocketExtensions.parse(response)
    assertThat(extensions)
        .isEqualTo(WebSocketExtensions(
            perMessageDeflate = true
        ))
  }

  @Test
  fun noContextTakeoverServerAndClient() {
    assertThat(parse("permessage-deflate; server_no_context_takeover; client_no_context_takeover"))
        .isEqualTo(WebSocketExtensions(
            perMessageDeflate = true,
            clientNoContextTakeover = true,
            serverNoContextTakeover = true
        ))
  }

  @Test
  fun noWhitespace() {
    assertThat(parse("permessage-deflate;server_no_context_takeover;client_no_context_takeover"))
        .isEqualTo(WebSocketExtensions(
            perMessageDeflate = true,
            clientNoContextTakeover = true,
            serverNoContextTakeover = true
        ))
  }

  @Test
  fun excessWhitespace() {
    assertThat(parse(
        "  permessage-deflate\t ; \tserver_no_context_takeover\t ;  client_no_context_takeover  "
    )).isEqualTo(WebSocketExtensions(
        perMessageDeflate = true,
        clientNoContextTakeover = true,
        serverNoContextTakeover = true
    ))
  }

  @Test
  fun noContextTakeoverClientAndServer() {
    assertThat(parse("permessage-deflate; client_no_context_takeover; server_no_context_takeover"))
        .isEqualTo(WebSocketExtensions(
            perMessageDeflate = true,
            clientNoContextTakeover = true,
            serverNoContextTakeover = true
        ))
  }

  @Test
  fun noContextTakeoverClient() {
    assertThat(parse("permessage-deflate; client_no_context_takeover"))
        .isEqualTo(WebSocketExtensions(
            perMessageDeflate = true,
            clientNoContextTakeover = true
        ))
  }

  @Test
  fun noContextTakeoverServer() {
    assertThat(parse("permessage-deflate; server_no_context_takeover")).isEqualTo(
        WebSocketExtensions(perMessageDeflate = true, serverNoContextTakeover = true)
    )
  }

  @Test
  fun unknownParameters() {
    assertThat(parse("permessage-deflate; unknown"))
        .isEqualTo(WebSocketExtensions(perMessageDeflate = true, unknownValues = true))
    assertThat(parse("permessage-deflate; client_max_window_bits=15"))
        .isEqualTo(WebSocketExtensions(perMessageDeflate = true, unknownValues = true))
    assertThat(parse("permessage-deflate; client_max_window_bits=15; client_max_window_bits=15"))
        .isEqualTo(WebSocketExtensions(perMessageDeflate = true, unknownValues = true))
    assertThat(parse("permessage-deflate; client_no_context_takeover=true"))
        .isEqualTo(WebSocketExtensions(perMessageDeflate = true, unknownValues = true))
  }

  @Test
  fun uppercase() {
    assertThat(parse("PERMESSAGE-DEFLATE; SERVER_NO_CONTEXT_TAKEOVER; CLIENT_NO_CONTEXT_TAKEOVER"))
        .isEqualTo(WebSocketExtensions(
            perMessageDeflate = true,
            clientNoContextTakeover = true,
            serverNoContextTakeover = true
        ))
  }

  private fun parse(extension: String): WebSocketExtensions {
    val response = minimalResponse.newBuilder()
        .header("Sec-WebSocket-Extensions", extension)
        .build()
    return WebSocketExtensions.parse(response)
  }
}
