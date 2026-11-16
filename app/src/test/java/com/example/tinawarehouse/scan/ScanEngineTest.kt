package com.example.tinawarehouse.scan

import com.example.tinawarehouse.data.*
import com.example.tinawarehouse.domain.scan.ScanEngine
import com.example.tinawarehouse.domain.scan.ScanEngine.Action
import com.example.tinawarehouse.domain.station.RuleMatcher
import com.example.tinawarehouse.domain.station.StationProfile
import com.example.tinawarehouse.i18n.L10n
import com.example.tinawarehouse.sound.ScanSound
import com.example.tinawarehouse.state.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Clock(var t: Long = 1_000_000) { fun now() = t; fun adv(ms: Long) { t += ms } }

private val US = StationProfile.default
private val matcher = RuleMatcher(US.warehouseRules)
private fun match(lines: List<String>) = matcher.firstMatch(lines, US)

class ScanEngineTest {
    @Before fun vi() { L10n.codeProvider = { "vi" } }
    private val LABEL = listOf("SHIP TO", "FD VAT HUE")

    private fun engine(c: Clock, pending: Int = 0) = ScanEngine(::match, { pending }, c::now)

    @Test fun barcode_plus_warehouse_submits_after_extra_wait() {
        val c = Clock(); val e = engine(c)
        assertTrue(e.onBarcodes(listOf("1Z999")).isEmpty()); assertTrue(e.reading)
        assertTrue(e.onLines(LABEL).isEmpty())                 // đủ nhưng chờ 0,5 giây cho mã khác cùng tem
        c.adv(300); assertTrue(e.tick().isEmpty())
        c.adv(250)
        val a = e.tick().single() as Action.Submit
        assertEquals("1Z999", a.code); assertEquals("FD VAT HUE", a.text); assertFalse(e.reading)
    }

    @Test fun warehouse_read_before_barcode_and_extras_grouped() {
        val c = Clock(); val e = engine(c)
        e.onLines(LABEL); c.adv(100)
        e.onBarcodes(listOf("AAA", "BBB")); c.adv(100)
        val a = e.onBarcodes(listOf("AAA", "BBB", "CCC")).filterIsInstance<Action.Submit>()
        // 2 mã phụ → gửi ngay không chờ
        assertEquals(1, a.size); assertEquals(listOf("BBB", "CCC"), a[0].extras)
    }

    @Test fun no_warehouse_first_warns_then_second_sends_without() {
        val c = Clock(); val e = engine(c)
        e.onBarcodes(listOf("X1")); c.adv(2600)
        assertEquals(Action.NoWarehouse("X1"), e.tick().single())
        c.adv(1000); assertTrue(e.onBarcodes(listOf("X1")).isEmpty()); assertFalse(e.reading)   // còn trong thời gian chờ
        c.adv(2100); e.onBarcodes(listOf("X1")); c.adv(2600)
        val s = e.tick().single() as Action.Submit
        assertEquals("", s.text)
    }

    @Test fun next_tracking_of_pending_label_uses_last_label() {
        val c = Clock(); val e = engine(c, pending = 1)
        e.onLines(LABEL); c.adv(600); e.tick()      // mã kho không có mã vạch → hết giờ
        c.adv(3000); e.tick()
        e.onBarcodes(listOf("T2")); c.adv(100)
        // recentMatch đã quá 3 giây nên chưa có mã kho; hết giờ dùng mã kho của tem đang chờ
        c.adv(2600)
        val s = e.tick().single() as Action.Submit
        assertEquals("FD VAT HUE", s.text)
    }

    @Test fun sent_code_silent_in_frame_then_duplicate_when_reshown() {
        val c = Clock(); val e = engine(c)
        e.onBarcodes(listOf("D1")); e.onLines(LABEL); c.adv(600); e.tick()
        c.adv(100)
        val stay = e.onBarcodes(listOf("D1"))
        assertTrue(stay.single() is Action.Hint)              // vẫn trong khung: chỉ nhắc
        c.adv(1500)
        assertEquals(Action.Resend("D1"), e.onBarcodes(listOf("D1")).single())   // rời khung rồi đưa lại
        c.adv(1000)
        assertTrue(e.onBarcodes(listOf("D1")).single() is Action.Hint)   // không báo lặp trong 4 giây
    }

    @Test fun endcode_barcode_debounced() {
        val c = Clock(); val e = engine(c)
        e.onLines(LABEL)
        assertEquals(Action.EndCode("FD VAT HUE"), e.onBarcodes(listOf("*ENDCODE*")).single())
        c.adv(1000); assertTrue(e.onBarcodes(listOf("ENDCODE")).isEmpty())
        c.adv(2500); assertEquals(1, e.onBarcodes(listOf("ENDCODE")).size)
    }

    @Test fun block_hold_ignores_input_and_forget_allows_rescan() {
        val c = Clock(); val e = engine(c)
        e.blockHold = true
        assertTrue(e.onBarcodes(listOf("Q")).isEmpty()); assertTrue(e.onLines(LABEL).isEmpty())
        e.continueAfterBlock()
        e.onBarcodes(listOf("Q")); e.onLines(LABEL); c.adv(600); assertTrue(e.tick().single() is Action.Submit)
        e.forget(listOf("Q")); c.adv(2000)
        assertTrue(e.onBarcodes(listOf("Q")).isEmpty()); assertTrue(e.reading)   // quét lại như mã mới
    }
}

private fun ok(data: String) = HttpResponse(200, """{"success":true,"data":$data}""")

class ScanControllerTest {
    @Before fun vi() { L10n.codeProvider = { "vi" } }

    private class Env(found: Set<String> = emptySet(), val c: Clock = Clock()) {
        var offline = false
        val scans = mutableListOf<String>()
        val sounds = mutableListOf<ScanSound>()
        val t = FakeTransport { r ->
            if (offline) throw java.io.IOException("no net")
            val body = r.body?.toString(Charsets.UTF_8) ?: ""
            when {
                r.url.contains("check-tracking") -> {
                    val code = Regex("\"code\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                    ok("""{"status":"${if (code in found) "FOUND" else "NOT_FOUND"}"}""")
                }
                r.url.endsWith("/trackings/scan") -> { synchronized(scans) { scans += body }; ok("""{"id":"9","matchStatus":"MATCHED","checkInResult":"CREATED"}""") }
                else -> ok("{}")
            }
        }
        fun ctl(scope: kotlinx.coroutines.CoroutineScope): ScanController {
            val flow = ScanFlow(CompanyApi(t, InMemoryTokenStore("A", "R")), CompanyScanTarget(24, 2), { US })
            return ScanController(flow, ::match, { matcher.match(it)?.isBusiness == true }, scope, { sounds += it }, c::now)
        }
    }

    @Test fun success_card_with_business() = runBlocking {
        val env = Env(found = setOf("1Z1")); val s = env.ctl(this)
        s.onBarcodes(listOf("1Z1")); s.onLines(listOf("SG HUE DOFA")); env.c.adv(600); s.tick(); s.idle()
        val u = s.ui.value
        assertEquals(1, u.sent); assertEquals(ScanCard.Kind.SUCCESS, u.last!!.kind)
        assertEquals(matcher.match("SG HUE DOFA")?.isBusiness == true, u.last!!.business)
        assertTrue(env.scans.single().contains("\"customerCode\":\"SG HUE DOFA\""))
        assertEquals("9", u.lastItemId)
    }

    @Test fun three_ting_then_endcode_no_name() = runBlocking {
        val env = Env(); val s = env.ctl(this)
        for (code in listOf("A1", "A2", "A3")) {
            s.onBarcodes(listOf(code)); s.onLines(listOf("FD VAT HUE")); env.c.adv(600); s.tick(); s.idle(); env.c.adv(3500)
        }
        assertEquals(ScanCard.Kind.TING, s.ui.value.last!!.kind); assertEquals(3, s.ui.value.last!!.track)
        assertTrue(s.ui.value.endRequired); assertEquals(listOf("A1", "A2", "A3"), s.pending.value)
        s.onBarcodes(listOf("A4")); s.onLines(listOf("FD VAT HUE")); env.c.adv(600); s.tick(); s.idle()
        assertEquals(ScanCard.Kind.END_BLOCKED, s.ui.value.last!!.kind)
        s.endCode(); s.idle()
        assertEquals(ScanCard.Kind.NO_NAME, s.ui.value.last!!.kind); assertFalse(s.ui.value.endRequired)
        val body = env.scans.single()
        assertTrue(body, body.contains("\"trackingNumber\":\"A1\"") && body.contains("\"customerCode\":\"FD VAT HUE\""))
        assertEquals(ScanSound.BUSINESS, env.sounds.last())          // FD VAT HUE là hàng kinh doanh
        assertTrue(s.ui.value.last!!.business)
        // mã bị chặn (A4) quét lại được sau khi END CODE
        env.c.adv(3000); s.onBarcodes(listOf("A4")); assertTrue(s.ui.value.let { s.engine.reading })
    }

    @Test fun offline_queues_and_retries() = runBlocking {
        val env = Env(found = setOf("N1")); val s = env.ctl(this)
        env.offline = true
        s.onBarcodes(listOf("N1")); s.onLines(listOf("FD VAT HUE")); env.c.adv(600); s.tick(); s.idle()
        assertEquals(1, s.ui.value.queued); assertEquals(0, s.ui.value.sent)
        env.offline = false
        env.c.adv(2000); s.tick(); s.idle(); assertEquals(1, s.ui.value.queued)     // chưa đủ 4 giây
        env.c.adv(2100); s.tick(); s.idle()
        assertEquals(0, s.ui.value.queued); assertEquals(1, s.ui.value.sent)
    }

    @Test fun block_holds_until_continue() = runBlocking {
        val c = Clock()
        val t = FakeTransport { r ->
            if (r.url.contains("check-tracking")) ok("""{"status":"FOUND","blockRequestedAt":"x","metadata":{"block":{"info":[{"reason":"Kiểm hàng"}]}}}""")
            else ok("""{"id":"5","matchStatus":"MATCHED","checkInResult":"CREATED"}""")
        }
        val sounds = mutableListOf<ScanSound>()
        val s = ScanController(ScanFlow(CompanyApi(t, InMemoryTokenStore("A", "R")), CompanyScanTarget(24, 2), { US }), ::match, { false }, this, { sounds += it }, c::now)
        s.onBarcodes(listOf("B1")); s.onLines(listOf("FD VAT HUE")); c.adv(600); s.tick(); s.idle()
        assertEquals(ScanCard.Kind.BLOCKED, s.ui.value.blockCard!!.kind); assertTrue(s.ui.value.blockCard!!.message!!.contains("Kiểm hàng"))
        assertEquals(ScanSound.BLOCK, sounds.last())
        assertTrue(s.engine.blockHold); s.onBarcodes(listOf("B2")); assertFalse(s.engine.reading)
        s.continueAfterBlock(); assertNull(s.ui.value.blockCard); s.onBarcodes(listOf("B2")); assertTrue(s.engine.reading)
    }

    @Test fun no_warehouse_card_and_endcode_without_pending_banner() = runBlocking {
        val env = Env(); val s = env.ctl(this)
        s.onBarcodes(listOf("Z9")); env.c.adv(2600); s.tick()
        assertEquals(ScanCard.Kind.NO_WAREHOUSE, s.ui.value.last!!.kind); assertEquals(ScanSound.NO_WAREHOUSE, env.sounds.last())
        s.onBarcodes(listOf("ENDCODE")); s.idle()
        assertNotNull(s.ui.value.banner); env.c.adv(3100); s.tick(); assertNull(s.ui.value.banner)
    }
}
