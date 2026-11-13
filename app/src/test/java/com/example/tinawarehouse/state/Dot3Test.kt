package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.*
import com.example.tinawarehouse.domain.session.SessionFormat
import com.example.tinawarehouse.domain.station.StationProfile
import com.example.tinawarehouse.i18n.L10n
import com.example.tinawarehouse.sound.ScanSound
import com.example.tinawarehouse.sound.SoundChoices
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private fun ok(data: String) = HttpResponse(200, """{"success":true,"data":$data}""")
private fun sess(json: String) = CompanySession.from(Json.parseToJsonElement(json).jsonObject)

class Dot3Test {
    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test fun format_time_and_count() {
        assertEquals("05/11 14:46", SessionFormat.time("2026-11-05T07:46:00Z", vn))
        assertEquals("05/11 14:46", SessionFormat.time("2026-11-05T07:46:00.123Z", vn))
        assertEquals("abc", SessionFormat.time("abc", vn))
        assertNull(SessionFormat.time("", vn)); assertEquals("—", SessionFormat.timeOrDash(null))
        val a = sess("""{"id":1,"actualScannedCount":6,"carrierReportedCount":20}""")
        assertEquals("6 / 20", SessionFormat.count(a)); assertEquals(SessionFormat.CountLevel.MISSING, SessionFormat.countLevel(a))
        val b = sess("""{"id":1,"actualScannedCount":20,"carrierReportedCount":20}""")
        assertEquals(SessionFormat.CountLevel.DONE, SessionFormat.countLevel(b))
        val c = sess("""{"id":1}""")
        assertEquals("0", SessionFormat.count(c)); assertEquals(SessionFormat.CountLevel.NEUTRAL, SessionFormat.countLevel(c))
    }

    @Test fun format_vehicle_user_carrier() {
        val s = sess("""{"id":1,"licensePlate":"51A","vehicleType":"","user":{"id":"1","username":"tu","fullName":"Tuan"}}""")
        assertEquals("51A", SessionFormat.vehicle(s)); assertEquals("Tuan", SessionFormat.openedBy(s))
        assertEquals("tu", SessionFormat.openedBy(sess("""{"id":1,"user":{"id":"1","username":"tu"}}""")))
        L10n.codeProvider = { "vi" }
        assertEquals("hãng 9", SessionFormat.carrierName(sess("""{"id":1,"carrierId":9}"""), emptyMap()))
        assertEquals("DHL X", SessionFormat.carrierName(sess("""{"id":1,"carrierId":9}"""), mapOf(9 to "DHL X")))
    }

    @Test fun carrier_logo_mapping() {
        assertEquals("logo_fedex", SessionFormat.carrierLogo("FEDEX GROUND", null))
        assertEquals("logo_dhl", SessionFormat.carrierLogo("x", "dhl"))
        assertEquals("logo_ups", SessionFormat.carrierLogo("UPS", null))
        assertEquals("logo_usps", SessionFormat.carrierLogo("U.S.P.S", "usps"))
        assertNull(SessionFormat.carrierLogo("Other", "oth")); assertNull(SessionFormat.carrierLogo("Groups", null))
        assertEquals("logo_amazon", SessionFormat.carrierLogo("Amazon Logistics", null))
    }

    private val SESS = """[
      {"id":3,"carrierId":1,"status":"OPEN","dockGate":"D2","station":{"id":1,"code":"OR_1","name":"Oregon"}},
      {"id":9,"carrierId":1,"status":"OPEN","dockGate":"D1","station":{"id":1,"code":"OR_1","name":"Oregon"}},
      {"id":5,"carrierId":1,"status":"OPEN","dockGate":"E9","station":{"id":2,"code":"DE_FRANKFURT","name":"F"}},
      {"id":4,"carrierId":1,"status":"OPEN"}]"""

    private fun store(t: FakeTransport, onUnauth: () -> Unit = {}): SessionsStore {
        val api = CompanyApi(t, InMemoryTokenStore("A", "R"))
        return SessionsStore(api, { StationProfile.default }, onUnauth) { vn }
    }

    @Test fun sessions_filter_station_sort_and_params() = runBlocking {
        val t = FakeTransport { r ->
            if (r.url.contains("/carriers")) ok("""[{"id":1,"code":"dhl","name":"DHL"}]""") else ok(SESS)
        }
        val s = store(t)
        s.updateFilters { it.copy(status = "OPEN", carrier = 1, gate = "D1", from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 11, 2)) }
        s.load()
        val ui = s.ui.value
        assertEquals(listOf(9, 4, 3), ui.sessions.map { it.id })          // trạm khác bị lọc; không rõ trạm vẫn hiện; id giảm dần
        assertEquals(mapOf(1 to "DHL"), ui.carriers); assertTrue("D1" in ui.knownGates && "D2" in ui.knownGates)
        assertFalse(ui.loading); assertNull(ui.error)
        val u = t.calls.first { it.url.contains("/receiving-sessions") }.url
        assertTrue(u, u.contains("status=OPEN") && u.contains("carrierId=1") && u.contains("dockGate=D1"))
        assertTrue(u, u.contains("openedFrom=2026-10-31T17%3A00%3A00Z"))   // 00:00 ngày 1/11 giờ VN
        assertTrue(u, u.contains("openedTo=2026-11-02T16%3A59%3A59Z"))     // 23:59:59 ngày 2/11 giờ VN
    }

    @Test fun sessions_error_and_unauthorized() = runBlocking {
        var unauth = 0
        val s1 = store(FakeTransport { HttpResponse(500, """{"message":"boom"}""") })
        s1.load(); assertTrue(s1.ui.value.error!!.contains("boom")); assertFalse(s1.ui.value.loading)
        // Không đăng nhập → danh sách rỗng, không gọi mạng
        val t = FakeTransport { ok("[]") }
        val api = CompanyApi(t, InMemoryTokenStore())
        val s2 = SessionsStore(api, { StationProfile.default }, { unauth++ })
        s2.load(); assertTrue(t.calls.isEmpty())
    }

    @Test fun filters_active() {
        assertFalse(SessionFilters().active); assertTrue(SessionFilters(gate = "A").active)
        assertTrue(SessionFilters(from = LocalDate.now()).active)
    }

    private val ME = """{"id":"u","username":"t","systemRole":"STAFF","stations":[{"id":1,"code":"OR_1","name":"O"}]}"""

    @Test fun create_session_ok() = runBlocking {
        L10n.codeProvider = { "vi" }
        val t = FakeTransport { r ->
            when {
                r.url.endsWith("/auth/me") -> ok(ME)
                r.url.endsWith("/receiving-sessions") && r.method == "POST" -> ok("""{"id":77}""")
                else -> HttpResponse(404, "{}")
            }
        }
        val api = CompanyApi(t, InMemoryTokenStore("A", "R"))
        val r = createCompanySession(api, StationProfile.default, 2, " Bob ", 12, gate = " D1 ", plate = "51A", arrival = java.time.Instant.parse("2026-11-05T07:46:00.900Z"))
        assertEquals(CreateResult.Created(77), r)
        val body = t.calls.last().body!!.toString(Charsets.UTF_8)
        assertTrue(body, body.contains("\"stationId\":1") && body.contains("\"driverName\":\"Bob\"") && body.contains("\"dockGate\":\"D1\"")
            && body.contains("\"carrierReportedCount\":12") && body.contains("\"vehicleArrivedAt\":\"2026-11-05T07:46:00Z\"") && !body.contains("vehicleType"))
    }

    @Test fun create_session_no_station() = runBlocking {
        L10n.codeProvider = { "vi" }
        val me = """{"id":"u","username":"t","systemRole":"STAFF","stations":[]}"""
        val api = CompanyApi(FakeTransport { ok(me) }, InMemoryTokenStore("A", "R"))
        assertEquals(CreateResult.Failed("Tài khoản không có trạm nào"), createCompanySession(api, StationProfile.default, 1, "x", 1))
        val me2 = """{"id":"u","username":"t","stations":[{"id":2,"code":"DE_FRANKFURT","name":"F"},{"id":3,"code":"X","name":"X"}]}"""
        val api2 = CompanyApi(FakeTransport { ok(me2) }, InMemoryTokenStore("A", "R"))
        val r = createCompanySession(api2, StationProfile.default, 1, "x", 1) as CreateResult.Failed
        assertTrue(r.message.startsWith("Tài khoản không có"))
    }

    @Test fun create_session_api_error() = runBlocking {
        val api = CompanyApi(FakeTransport { r -> if (r.url.endsWith("/auth/me")) ok(ME) else HttpResponse(409, """{"message":"Trùng"}""") }, InMemoryTokenStore("A", "R"))
        val r = createCompanySession(api, StationProfile.default, 1, "x", 1) as CreateResult.Failed
        assertTrue(r.message.contains("Trùng") && r.message.contains("409"))
    }

    @Test fun scan_sound_defaults_and_reset() {
        val p = InMemoryKeyValueStore()
        assertEquals(SoundChoices.VOICE, ScanSound.REJECTED.current(p)); assertEquals("snd_rejected2", ScanSound.REJECTED.key)
        assertEquals("snd_no_name", ScanSound.NO_NAME.key); assertEquals(SoundChoices.ACK, ScanSound.FOUND.current(p))
        p.putInt(ScanSound.FOUND.key, 0); assertEquals(0, ScanSound.FOUND.current(p))
        ScanSound.resetAll(p); assertEquals(SoundChoices.ACK, ScanSound.FOUND.current(p))
        assertTrue(SoundChoices.all.size == 17 && SoundChoices.all.toSet().size == 17)
    }
}
