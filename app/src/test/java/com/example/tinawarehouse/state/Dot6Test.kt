package com.example.tinawarehouse.state

import com.example.tinawarehouse.data.*
import com.example.tinawarehouse.i18n.L10n
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

private fun ok(data: String) = HttpResponse(200, """{"success":true,"data":$data}""")
private const val TYPES = """[{"code":"WET","name":"Ướt"},{"code":"BOX","name":"Bẹp hộp"},{"code":"TORN","name":"Rách"}]"""

class Dot6Test {
    @Before fun vi() { L10n.codeProvider = { "vi" } }
    private fun transport(fail: Int = 0) = FakeTransport { r ->
        when {
            r.url.contains("/damage-types") -> ok(TYPES)
            fail > 0 -> HttpResponse(fail, """{"message":"Đã có báo cáo"}""")
            else -> ok("{}")
        }
    }
    private fun api(t: FakeTransport) = CompanyApi(t, InMemoryTokenStore("A", "R"))

    @Test fun new_report_post_multipart() = runBlocking {
        val t = transport(); val s = DamageStore(api(t), "77", "T77")
        s.loadTypes()
        assertEquals(listOf("Bẹp hộp", "Rách", "Ướt"), s.ui.value.types.map { it.name }); assertFalse(s.ui.value.loading)
        assertFalse(s.ui.value.canSubmit)                           // chưa chọn loại
        s.toggle("WET"); s.toggle("BOX"); s.toggle("TORN"); s.toggle("TORN")
        s.addPhoto(byteArrayOf(1, 2, 3)); s.setDecision("REJECTED")
        assertTrue(s.ui.value.canSubmit); assertTrue(s.send()); assertTrue(s.ui.value.done)
        val r = t.calls.last(); val body = r.body!!.toString(Charsets.ISO_8859_1)
        assertEquals("POST", r.method); assertTrue(r.url.endsWith("/trackings/77/damage-report"))
        assertTrue(r.headers["Content-Type"]!!.startsWith("multipart/form-data"))
        assertTrue(body.indexOf("BOX") in 1 until body.indexOf("WET"))          // theo thứ tự danh sách loại
        assertFalse(body.contains("TORN")); assertTrue(body.contains("REJECTED") && body.contains("filename=\"damage1.jpg\""))
        assertFalse(s.send())                                       // đã gửi rồi thì không gửi lại
    }

    @Test fun max_ten_photos_and_remove() {
        val s = DamageStore(api(transport()), "1", "T")
        repeat(12) { s.addPhoto(byteArrayOf(it.toByte())) }
        assertEquals(10, s.ui.value.photos.size); assertFalse(s.ui.value.canAddPhoto)
        s.removePhoto(0); assertEquals(9, s.ui.value.photos.size); assertEquals(1, s.ui.value.photos[0][0].toInt())
    }

    @Test fun edit_patch_only_changed_decision() = runBlocking {
        val ex = CompanyDamageReport("RECEIVED", null, listOf("Ướt"), listOf("WET"), listOf("/uploads/a.jpg"))
        val t = transport(); val s = DamageStore(api(t), "5", "T5", ex)
        s.loadTypes()
        assertEquals(setOf("WET"), s.ui.value.selected); assertFalse(s.ui.value.hasChanges); assertFalse(s.ui.value.canSubmit)
        s.toggle("BOX"); assertTrue(s.ui.value.canSubmit)
        assertTrue(s.ui.value.confirmMessage.contains("giữ nguyên ảnh cũ"))
        s.send()
        val r = t.calls.last(); val body = r.body!!.toString(Charsets.ISO_8859_1)
        assertEquals("PATCH", r.method); assertFalse(body.contains("name=\"decision\"")); assertFalse(body.contains("filename"))
        // đổi sang Từ chối: gửi kèm quyết định, cảnh báo không đảo lại được
        val s2 = DamageStore(api(t), "5", "T5", ex); s2.loadTypes(); s2.setDecision("REJECTED")
        assertTrue(s2.ui.value.confirmMessage.contains("KHÔNG đảo lại")); s2.send()
        assertTrue(t.calls.last().body!!.toString(Charsets.ISO_8859_1).contains("REJECTED"))
    }

    @Test fun decision_locked() {
        val rej = CompanyDamageReport("REJECTED", null, emptyList(), listOf("WET"), emptyList())
        val s = DamageStore(api(transport()), "5", "T", rej); s.setDecision("RECEIVED")
        assertEquals("REJECTED", s.ui.value.decision); assertTrue(s.ui.value.decisionLocked)
        val s2 = DamageStore(api(transport()), "5", "T", null, lockDecision = true); s2.setDecision("REJECTED")
        assertEquals("RECEIVED", s2.ui.value.decision)
    }

    @Test fun send_error_keeps_form() = runBlocking {
        val s = DamageStore(api(transport(fail = 409)), "5", "T"); s.loadTypes(); s.toggle("WET"); s.addPhoto(byteArrayOf(1))
        assertFalse(s.send())
        val u = s.ui.value
        assertTrue(u.error!!.contains("Đã có báo cáo") && u.error!!.contains("409")); assertFalse(u.done); assertFalse(u.saving)
        assertEquals(1, u.photos.size); assertTrue(u.canSubmit)
    }

    @Test fun label_photo_messages() = runBlocking {
        val t = transport()
        assertEquals("Đã gửi ảnh label cho T9", uploadLabelPhoto(api(t), "9", "T9", byteArrayOf(1)))
        assertTrue(t.calls.last().url.endsWith("/trackings/9/label-photos"))
        assertTrue(uploadLabelPhoto(api(transport(fail = 500)), "9", "T9", byteArrayOf(1)).startsWith("Gửi ảnh label thất bại"))
    }
}
