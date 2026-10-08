package org.unividuell.auth

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.unividuell.auth.testapp.TestApplication
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Over real HTTP: a container re-dispatches every sendError to /error, which MockMvc skips. */
@SpringBootTest(classes = [TestApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDispatchTest(@LocalServerPort val port: Int) {

    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    @Test
    fun `an anonymous 404 stays a 404`() {
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/login/nothing")).build()

        client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() shouldBe 404
    }
}
