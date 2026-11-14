package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.*
import com.example.tinawarehouse.domain.session.TrackingFormat
import com.example.tinawarehouse.domain.station.StationProfile
import com.example.tinawarehouse.i18n.L10n
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

private fun ok(data: String) = HttpResponse(200, """{"success":true,"data":$data}""")
private fun trk(json: String) = CompanyTracking.from(Json.parseToJsonElement(json).jsonObject)
private val OPEN = CompanySession.from(Json.parseToJsonElement("""{"id":24,"carrierId":2,"status":"OPEN","actualScannedCount":50,"carrier":{"id":2,"code":"dhl","name":"DHL"}}""").jsonObject)

class Dot4Test {
    @Before fun vi() { L10n.codeProvider = { "vi" } }

    @Test fun format_helpers() {
        assertEquals("Đã nhập", TrackingFormat.status("CHECKED_IN")); assertEquals("X", TrackingFormat.status("X")); assertEquals("—", TrackingFormat.status(null))
        assertEquals("3", TrackingFormat.qty(3.0)); assertEquals("2.5", TrackingFormat.qty(2.5)); assertEquals("—", TrackingFormat.qty(null))
        val t = trk("""{"id":1,"trackingNumber":"A","matchStatus":"MATCHED","orderItems":[{"itemName":"Kem","clientOrderId":"99"},{"itemId":"I2"}],"customerCode":"C1"}""")
        assertEquals("Kem (đơn 99), I2", TrackingFormat.matchLine(t)); assertEquals("khách C1", TrackingFormat.who(t))
        assertEquals("Kiện không xác định (không có trong danh sách)", TrackingFormat.matchLine(trk("""{"id":1,"matchStatus":"NO_NAME"}""")))
        assertEquals("Khớp", TrackingFormat.matchLine(trk("""{"id":1,"matchStatus":"MATCHED"}""")))
        assertEquals("Từ chối", TrackingFormat.decision("REJECTED")); assertEquals("Hư hỏng", TrackingFormat.condition("DAMAGED"))
    }

    @Test fun row_numbers() {
        val items = (1..3).map { trk("""{"id":$it}""") }
        val u = SessionDetailUi(OPEN, items = items, hasMore = true)
        assertEquals(50, u.rowTotal); assertEquals(50, u.rowNumber(0)); assertEquals(48, u.rowNumber(2))
        val done = u.copy(hasMore = false); assertEquals(3, done.rowNumber(0)); assertEquals(1, done.rowNumber(2))
    }

    private fun page(n: Int, from: Int) = "[" + (from until from + n).joinToString(",") { """{"id":"$it","trackingNumber":"T$it"}""" } + "]"

    @Test fun paging_and_dedupe() = runBlocking {
        val t = FakeTransport { r ->
            when {
                r.url.contains("/receiving-sessions/24") -> ok("""{"id":24,"status":"OPEN","actualScannedCount":3}""")
                r.url.contains("page=1") -> ok(page(2, 0))
                r.url.contains("page=2") -> ok(page(2, 1))        // trùng id 1
                else -> ok("[]")
            }
        }
        val s = SessionDetailStore(CompanyApi(t, InMemoryTokenStore("A", "R")), OPEN, { StationProfile.default }, pageSize = 2)
        s.refreshAll(); assertEquals(2, s.ui.value.items.size); assertEquals(3, s.ui.value.session.actualScannedCount)
        s.loadMore(); assertEquals(listOf("0", "1", "2"), s.ui.value.items.map { it.id }); assertTrue(s.ui.value.hasMore)
        s.loadMore(); assertFalse(s.ui.value.hasMore)
        s.loadMore(); assertEquals(3, t.calls.count { it.url.contains("/trackings?") })   // hết trang thì không gọi nữa
    }

    @Test fun close_session() = runBlocking {
        val t = FakeTransport { r -> if (r.method == "PATCH") ok("""{"id":24}""") else ok("[]") }
        val s = SessionDetailStore(CompanyApi(t, InMemoryTokenStore("A", "R")), OPEN, { StationProfile.default })
        assertTrue(s.closeSession()); assertTrue(s.ui.value.closed); assertFalse(s.ui.value.isOpen)
        assertTrue(t.calls.last().body!!.toString(Charsets.UTF_8).contains("\"status\":\"CLOSED\""))
        val s2 = SessionDetailStore(CompanyApi(FakeTransport { HttpResponse(403, """{"message":"no"}""") }, InMemoryTokenStore("A", "R")), OPEN, { StationProfile.default })
        assertFalse(s2.closeSession()); assertTrue(s2.ui.value.closeError!!.contains("no")); s2.dismissCloseError(); assertNull(s2.ui.value.closeError)
    }

    @Test fun tracking_detail_with_damage() = runBlocking {
        val t = FakeTransport { r ->
            when {
                r.url.contains("/damage-report") -> ok("""{"decision":"RECEIVED","damageTypes":[{"code":"WET","name":"Ướt"}],"photos":[{"url":"/uploads/a.jpg"}]}""")
                else -> ok("""{"id":"7","trackingNumber":"T7","condition":"DAMAGED","status":"CHECKED_IN"}""")
            }
        }
        val s = TrackingDetailStore(CompanyApi(t, InMemoryTokenStore("A", "R")), trk("""{"id":"7"}"""))
        s.refresh()
        val u = s.ui.value
        assertEquals("T7", u.tracking.trackingNumber); assertEquals(listOf("Ướt"), u.damage!!.typeNames)
        assertEquals(listOf("/uploads/a.jpg"), u.damage!!.photoUrls); assertTrue(u.canReportDamage)
        // Kiện bình thường: không gọi báo cáo hư hỏng
        val t2 = FakeTransport { ok("""{"id":"8","condition":"NORMAL","status":"RETURNED_TO_SELLER"}""") }
        val s2 = TrackingDetailStore(CompanyApi(t2, InMemoryTokenStore("A", "R")), trk("""{"id":"8"}""")); s2.refresh()
        assertEquals(1, t2.calls.size); assertFalse(s2.ui.value.canReportDamage); assertFalse(s2.ui.value.canEditDamage)
    }
}
