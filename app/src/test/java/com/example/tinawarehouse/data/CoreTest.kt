package com.example.tinawarehouse.data

import com.example.tinawarehouse.domain.station.StationProfile
import com.example.tinawarehouse.i18n.L10n
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.state.AuthStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FakeTransport(val handler: (HttpRequest) -> HttpResponse) : HttpTransport {
    val calls = mutableListOf<HttpRequest>()
    override suspend fun execute(request: HttpRequest): HttpResponse { synchronized(calls) { calls += request }; delay(5); return handler(request) }
}

private fun ok(data: String) = HttpResponse(200, """{"success":true,"statusCode":200,"message":"ok","data":$data}""")
private const val ME = """{"id":"u1","username":"tuan","systemRole":"STAFF","stations":[{"id":1,"code":"OR_1","name":"Oregon"}]}"""

class CoreTest {
    @Test fun tr_vi_and_en_and_args() {
        L10n.codeProvider = { "vi" }
        assertEquals("Tài khoản không có X", tr("Tài khoản không có {0}", "X"))
        L10n.codeProvider = { "en" }
        assertNotEquals("Tài khoản không có {0}", tr("Tài khoản không có {0}", "X"))
        assertTrue(tr("Tài khoản không có {0}", "X").contains("X"))
        assertEquals("không có trong bảng", tr("không có trong bảng"))
        L10n.codeProvider = { "vi" }
    }

    @Test fun session_displayCode() {
        val o = Json.parseToJsonElement("""{"id":24,"carrier":{"id":2,"code":"dhl","name":"DHL"}}""").jsonObject
        assertEquals("DHL0024", CompanySession.from(o).displayCode)
        assertEquals("#5", CompanySession.from(Json.parseToJsonElement("""{"id":5}""").jsonObject).displayCode)
        assertEquals("ABC", CompanySession.from(Json.parseToJsonElement("""{"id":5,"code":"ABC"}""").jsonObject).displayCode)
    }

    @Test fun tracking_lenient_and_unknown() {
        val t = CompanyTracking.from(Json.parseToJsonElement("""{"id":7,"trackingNumber":123,"matchStatus":"NO_NAME","orderItemCount":0,"labelPhotoUrls":["a","b"]}""").jsonObject)
        assertEquals("7", t.id); assertEquals("123", t.trackingNumber)
        assertTrue(t.isUnknown); assertEquals(2, t.labelPhotoCount)
    }

    @Test fun blockHints() {
        val j = Json.parseToJsonElement("""{"metadata":{"block":{"info":[{"reason":"Hold A"},{"reason":"Hold A"}]}}}""")
        assertEquals(listOf("Hold A"), j.blockHints())
        assertTrue(Json.parseToJsonElement("""{"a":1}""").blockHints().isEmpty())
        assertEquals(1, Json.parseToJsonElement("""{"blockRequestedAt":"2026-01-01"}""").blockHints().size)
    }

    private fun api(t: FakeTransport, tokens: TokenStore = InMemoryTokenStore()) = CompanyApi(t, tokens)

    @Test fun login_saves_tokens() = runBlocking {
        val tr = FakeTransport { r ->
            when {
                r.url.endsWith("/auth/login") -> ok("""{"accessToken":"A1","refreshToken":"R1"}""")
                r.url.endsWith("/auth/me") -> { assertEquals("Bearer A1", r.headers["Authorization"]); ok(ME) }
                else -> HttpResponse(404, "{}")
            }
        }
        val ts = InMemoryTokenStore(); val me = api(tr, ts).login("tuan", "pw")
        assertEquals("tuan", me.username); assertEquals("A1", ts.accessToken()); assertEquals("R1", ts.refreshToken())
    }

    @Test fun refresh_once_and_retry() = runBlocking {
        val refreshes = AtomicInteger()
        val tr = FakeTransport { r ->
            when {
                r.url.endsWith("/auth/refresh") -> { refreshes.incrementAndGet(); ok("""{"accessToken":"A2","refreshToken":"R2"}""") }
                r.headers["Authorization"] == "Bearer A2" -> ok(ME)
                else -> HttpResponse(401, """{"message":"expired"}""")
            }
        }
        val ts = InMemoryTokenStore("A1", "R1"); val a = api(tr, ts)
        val res = (1..5).map { async { a.me() } }.awaitAll()
        assertEquals(5, res.size); assertEquals(1, refreshes.get()); assertEquals("R2", ts.refreshToken())
    }

    @Test fun refresh_fail_clears_and_expires() = runBlocking {
        val tr = FakeTransport { r -> if (r.url.endsWith("/auth/refresh")) HttpResponse(401, """{"message":"bad"}""") else HttpResponse(401, "{}") }
        val ts = InMemoryTokenStore("A1", "R1"); val a = api(tr, ts)
        var got = false
        val job = async { a.sessionExpired.collect { got = true } }
        delay(20)
        try { a.me(); fail() } catch (e: CompanyError) { assertEquals("SESSION_EXPIRED", e.code) }
        delay(20); job.cancel()
        assertNull(ts.accessToken()); assertTrue(got)
    }

    @Test fun allowedStations_admin_uses_stations_endpoint() = runBlocking {
        val tr = FakeTransport { r -> if (r.url.contains("/stations")) ok("""[{"id":1,"code":"OR_1","name":"O"},{"id":2,"code":"DE_FRANKFURT","name":"F"}]""") else HttpResponse(404, "{}") }
        val a = api(tr, InMemoryTokenStore("A", "R"))
        assertEquals(2, a.allowedStations(CompanyMe("1", "x", null, "ADMIN", emptyList())).size)
        assertEquals(0, a.allowedStations(CompanyMe("1", "x", null, "STAFF", emptyList())).size)
    }

    private fun loginTransport(meJson: String) = FakeTransport { r ->
        when {
            r.url.endsWith("/auth/login") -> if (r.body!!.toString(Charsets.UTF_8).contains("good")) ok("""{"accessToken":"A","refreshToken":"R"}""") else HttpResponse(401, """{"message":"x"}""")
            r.url.endsWith("/auth/me") -> ok(meJson)
            r.url.endsWith("/auth/logout") -> ok("null")
            else -> HttpResponse(404, "{}")
        }
    }

    @Test fun signIn_ok() = runBlocking {
        L10n.codeProvider = { "vi" }
        val ts = InMemoryTokenStore(); val a = api(loginTransport(ME), ts); val prefs = InMemoryKeyValueStore()
        val auth = AuthStore(a, prefs); auth.signIn("tuan", "good", StationProfile.default)
        assertTrue(auth.isSignedIn.value); assertEquals("tuan", auth.rememberedUsername)
        assertTrue(auth.availableStationIds.value.isNotEmpty()); assertNull(auth.errorMessage.value)
    }

    @Test fun signIn_wrong_password() = runBlocking {
        L10n.codeProvider = { "vi" }
        val auth = AuthStore(api(loginTransport(ME)), InMemoryKeyValueStore()); auth.signIn("tuan", "bad", StationProfile.default)
        assertFalse(auth.isSignedIn.value); assertEquals("Sai tên đăng nhập hoặc mật khẩu", auth.errorMessage.value)
    }

    @Test fun signIn_station_not_allowed() = runBlocking {
        L10n.codeProvider = { "vi" }
        val ts = InMemoryTokenStore()
        val me = """{"id":"u","username":"t","systemRole":"STAFF","stations":[{"id":2,"code":"DE_FRANKFURT","name":"F"}]}"""
        val auth = AuthStore(api(loginTransport(me), ts), InMemoryKeyValueStore())
        auth.signIn("t", "good", StationProfile.find("US") ?: StationProfile.default)
        assertFalse(auth.isSignedIn.value); assertNull(ts.accessToken()); assertTrue(auth.errorMessage.value!!.startsWith("Tài khoản không có"))
    }
}
