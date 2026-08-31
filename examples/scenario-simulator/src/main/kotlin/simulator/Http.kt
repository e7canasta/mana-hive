package simulator

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object Http {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    fun get(url: String): String {
        val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build()
        return client.send(req, HttpResponse.BodyHandlers.ofString()).body()
    }

    fun post(url: String, body: String = "", contentType: String = "application/json"): Pair<Int, String> {
        val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", contentType).build()
        val r = client.send(req, HttpResponse.BodyHandlers.ofString())
        return r.statusCode() to r.body()
    }

    fun putJson(url: String, json: String): Pair<Int, String> {
        val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .header("Content-Type", "application/json").build()
        val r = client.send(req, HttpResponse.BodyHandlers.ofString())
        return r.statusCode() to r.body()
    }

    fun delete(url: String): Pair<Int, String> {
        val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).DELETE().build()
        val r = client.send(req, HttpResponse.BodyHandlers.ofString())
        return r.statusCode() to r.body()
    }
}
