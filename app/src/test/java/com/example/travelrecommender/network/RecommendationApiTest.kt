package com.example.travelrecommender.network

import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RecommendationApiTest {
    @Test fun `posts expected JSON and decodes recommendations and http errors`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create()).build()
                .create(RecommendationApi::class.java)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"recommendations":[{"name":"Example Park","reason":"Scenic outdoors","durationMinutes":90}]}"""
            ))
            val request = RecommendationRequestDto(47.4979, 19.0402,
                "2026-09-29T13:00:00+02:00", 180, "Scenic outdoors")
            val result = api.recommendations(request)
            assertEquals(listOf(RecommendationDto("Example Park", "Scenic outdoors", 90)), result.recommendations)
            val recorded = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("POST", recorded.method)
            assertEquals("/recommendations", recorded.path)
            assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/json"))
            assertEquals(JsonParser.parseString("""{
                "latitude":47.4979,"longitude":19.0402,
                "startDateTime":"2026-09-29T13:00:00+02:00",
                "availableMinutes":180,"request":"Scenic outdoors"
            }"""), JsonParser.parseString(recorded.body.readUtf8()))
            server.enqueue(MockResponse().setResponseCode(500))
            try {
                api.recommendations(request)
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(500, error.code())
            }
        } finally {
            server.shutdown()
        }
    }
}
