package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.*
import com.example.tinawarehouse.domain.station.StationProfile
import com.example.tinawarehouse.i18n.L10n
import com.example.tinawarehouse.state.FlowResult.Kind
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

private fun ok(data: String) = HttpResponse(200, """{"success":true,"data":$data}""")

/** Máy chủ giả: [found] = mã công ty có; [dup] = mã đã quét. Ghi lại các lần POST /trackings/scan. */
private class Server(val found: Set<String> = emptySet(), val dup: Set<String> = emptySet(), val scanDup: Boolean = false) {
    val scans = mutableListOf<String>()
    val t = FakeTransport { r ->
        val body = r.body?.toString(Charsets.UTF_8) ?: ""
        when {
            r.url.contains("check-tracking") -> {
                val code = Regex("\"code\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                ok("""{"status":"${if (code in dup) "DUPLICATE" else if (code in found) "FOUND" else "NOT_FOUND"}"}""")
            }
            r.url.endsWith("/trackings/scan") -> {
                synchronized(scans) { scans += body }
                ok("""{"id":"77","matchStatus":"${if (body.contains("customerCode")) "MATCHED" else "NO_NAME"}","checkInResult":"${if (scanDup) "DUPLICATE" else "CREATED"}"}""")
            }
            r.url.contains("page=") -> ok("[]")
            else -> ok("""{"id":24,"status":"OPEN"}""")
        }
    }
    fun flow() = ScanFlow(CompanyApi(t, InMemoryTokenStore("A", "R")), CompanyScanTarget(24, 2), { StationProfile.default })
}

class ScanFlowTest {
    @Before fun vi() { L10n.codeProvider = { "vi" } }

    @Test fun endcode_detection() {
        assertTrue(ScanFlow.isEndCode("ENDCODE")); assertTrue(ScanFlow.isEndCode(" *endcode* "))
        assertFalse(ScanFlow.isEndCode("END CODE")); assertFalse(ScanFlow.isEndCode("ENDCODE1"))
    }

    @Test fun three_unknown_then_end_required_then_endcode_writes_no_name() = runBlocking {
        val s = Server(); val f = s.flow()
        assertEquals(Kind.PENDING, f.submit("AAA111").kind)
        val r2 = f.submit("BBB222"); assertEquals(2, r2.seq); assertEquals("Tracking 2/3 chưa có thông tin", r2.message)
        val r3 = f.submit("CCC333"); assertEquals(3, r3.seq); assertEquals("Đủ 3 tracking — bấm nút END CODE", r3.message)
        assertEquals(listOf("AAA111", "BBB222", "CCC333"), f.pending.value)
        val calls = s.t.calls.size
        val r4 = f.submit("DDD444"); assertEquals(Kind.END_REQUIRED, r4.kind)
        assertEquals(calls, s.t.calls.size)                       // bị chặn: không hỏi công ty
        assertEquals(3, f.pending.value.size)
        val end = f.submit("*ENDCODE*"); assertEquals(Kind.NO_NAME, end.kind)
        assertTrue(f.pending.value.isEmpty())
        val body = s.scans.single()
        assertTrue(body, body.contains("\"trackingNumber\":\"AAA111\"") && body.contains("\"additionalTrackingNumbers\":[\"BBB222\",\"CCC333\"]"))
    }

    @Test fun found_writes_with_pending_as_additional() = runBlocking {
        val s = Server(found = setOf("FFF999")); val f = s.flow()
        f.submit("AAA111"); f.submit("BBB222")
        val r = f.submit("FFF999")
        assertEquals(Kind.WRITTEN, r.kind); assertEquals("FFF999", r.code); assertTrue(r.message.contains("+2 mã phụ"))
        assertTrue(f.pending.value.isEmpty())
        val body = s.scans.single()
        assertTrue(body, body.contains("\"trackingNumber\":\"FFF999\"") && body.contains("[\"AAA111\",\"BBB222\"]"))
    }

    @Test fun found_alone_no_additional() = runBlocking {
        val s = Server(found = setOf("FFF999")); val f = s.flow()
        val r = f.submit("fff999"); assertEquals(Kind.WRITTEN, r.kind); assertFalse(r.message.contains("mã phụ"))
        assertFalse(s.scans.single().contains("additionalTrackingNumbers"))
    }

    @Test fun duplicate_and_already_pending_and_local_listed() = runBlocking {
        val s = Server(dup = setOf("DUP1")); val f = s.flow()
        assertEquals(Kind.DUPLICATE, f.submit("DUP1").kind)
        f.submit("AAA111")
        assertEquals(Kind.ALREADY_PENDING, f.submit("AAA111").kind); assertEquals(1, f.pending.value.size)
        val before = s.t.calls.size
        assertEquals(Kind.DUPLICATE, f.submit("ZZZ1", listedLocally = { "ZZZ1" in it }).kind)
        assertEquals(before, s.t.calls.size)
        assertTrue(s.scans.isEmpty())
    }

    @Test fun remove_and_make_main() = runBlocking {
        val s = Server(); val f = s.flow()
        f.submit("AAA111"); f.submit("BBB222"); f.submit("CCC333")
        f.makeMain("CCC333"); assertEquals(listOf("CCC333", "AAA111", "BBB222"), f.pending.value)
        f.remove("AAA111"); assertEquals(listOf("CCC333", "BBB222"), f.pending.value)
        assertEquals(Kind.PENDING, f.submit("DDD444").kind)       // bỏ bớt 1 mã thì nhập tiếp được
        f.endCode()
        assertTrue(s.scans.single().contains("\"trackingNumber\":\"CCC333\""))
    }

    @Test fun endcode_without_pending_and_closed_session_check_only() = runBlocking {
        val s = Server(found = setOf("FFF999")); val f = s.flow()
        assertEquals(Kind.NOTHING_PENDING, f.endCode().kind)
        assertEquals(Kind.FOUND_ONLY, f.submit("FFF999", write = false).kind)
        assertEquals(Kind.NOT_FOUND_ONLY, f.submit("AAA111", write = false).kind)
        assertTrue(f.pending.value.isEmpty()); assertTrue(s.scans.isEmpty())
    }

    @Test fun error_keeps_pending() = runBlocking {
        var fail = false
        val t = FakeTransport { r ->
            if (fail) HttpResponse(500, """{"message":"boom"}""")
            else if (r.url.contains("check-tracking")) ok("""{"status":"NOT_FOUND"}""") else ok("{}")
        }
        val f = ScanFlow(CompanyApi(t, InMemoryTokenStore("A", "R")), CompanyScanTarget(24, 2), { StationProfile.default })
        f.submit("AAA111"); fail = true
        val r = f.endCode(); assertEquals(Kind.ERROR, r.kind); assertTrue(r.message.contains("boom"))
        assertEquals(listOf("AAA111"), f.pending.value)          // lỗi thì không mất mã đang chờ
    }

    @Test fun double_enter_is_serialized() = runBlocking {
        val s = Server(); val f = s.flow()
        val rs = listOf(async { f.submit("AAA111") }, async { f.submit("AAA111") }).awaitAll()
        assertEquals(setOf(Kind.PENDING, Kind.ALREADY_PENDING), rs.map { it.kind }.toSet())
        assertEquals(1, f.pending.value.size)
    }

    @Test fun store_submit_clears_input_and_refreshes() = runBlocking {
        val s = Server(found = setOf("FFF999"))
        val fb = mutableListOf<Feedback>()
        val session = CompanySession.from(Json.parseToJsonElement("""{"id":24,"carrierId":2,"status":"OPEN"}""").jsonObject)
        val store = SessionDetailStore(CompanyApi(s.t, InMemoryTokenStore("A", "R")), session, { StationProfile.default }, { fb += it })
        store.onCodeChange("AAA111"); store.submit()
        assertEquals("", store.ui.value.code); assertEquals(listOf("AAA111"), store.ui.value.pending)
        store.onCodeChange("FFF999"); store.submit()
        assertTrue(store.ui.value.pending.isEmpty()); assertEquals(Kind.WRITTEN, store.ui.value.result!!.kind)
        assertEquals(listOf(Feedback.PENDING, Feedback.WRITTEN), fb)
        assertTrue(s.t.calls.any { it.url.contains("page=1") })          // ghi xong thì tải lại danh sách
        store.onCodeChange("BBB222"); store.submit(); store.endCode()
        assertEquals(Feedback.NO_NAME, fb.last())
    }
}
