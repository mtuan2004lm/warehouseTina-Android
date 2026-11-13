package com.example.tinawarehouse.domain

import com.example.tinawarehouse.domain.station.CompanyRef
import com.example.tinawarehouse.domain.station.RuleMatcher
import com.example.tinawarehouse.domain.station.StationDE
import com.example.tinawarehouse.domain.station.StationProfile
import com.example.tinawarehouse.domain.station.StationUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kiểm tra sửa lỗi OCR + khớp quy tắc mã kho + nhận trạm của hồ sơ trạm Mỹ/Đức. */
class StationRulesTest {

    private val us = StationUS.profile
    private val matcher = RuleMatcher(us.warehouseRules)

    /** Cả đường đi OCR: chuẩn hoá -> khớp quy tắc, trả về nhóm khách. */
    private fun groupOf(ocr: String): String? = matcher.match(RuleMatcher.normalizeOCR(ocr, us))?.customerGroup

    // ---------- Khớp quy tắc ----------

    @Test fun giaonhan247() {
        assertEquals("Giaonhan247", groupOf("SGVO_A12"))
        assertEquals("Giaonhan247", groupOf("HNKYDUYEN_B3"))
    }

    @Test fun fado168() {
        assertEquals("FADO 168", groupOf("CHHEN_X1"))
        assertEquals("FADO 168", groupOf("SG HUE_DOFA168"))
    }

    @Test fun micafi() {
        assertEquals("MICAFI", groupOf("HNHUE MCF"))
    }

    @Test fun fadoVn_isBusiness_andRequiresImportCheck() {
        val rule = matcher.match(RuleMatcher.normalizeOCR("FD VAT HUE", us))
        assertNotNull(rule)
        assertEquals("FADO.VN", rule!!.customerGroup)
        assertTrue(rule.isBusiness)
        assertTrue(rule.requiresImportCheck)
    }

    @Test fun nonBusinessRules_areNotBusiness() {
        assertFalse(matcher.match("SGVO_A12")!!.isBusiness)
    }

    @Test fun noMatch_returnsNull() {
        assertNull(groupOf("RANDOM TEXT"))
        assertNull(matcher.match("   "))
    }

    // ---------- Sửa lỗi OCR ----------

    @Test fun ocr_removesShipToPrefix() {
        assertEquals("SGHUE DOFA", StationUS.ocrFix("SHIP TO SGHUE DOFA"))
        assertEquals("FDVAT HUE", StationUS.ocrFix("USPS DELIVER TO: FDVAT HUE"))
        assertEquals("FADO.VN", groupOf("SHIP TO SGHUE DOFA"))
    }

    @Test fun ocr_HUF_becomesHUE() {
        assertEquals("HNHUE MCF", StationUS.ocrFix("HNHUF MCF"))
        assertEquals("MICAFI", groupOf("HNHUF MCF"))
    }

    @Test fun ocr_zeroInsteadOfLetterO() {
        assertEquals("SGVO_A12", StationUS.ocrFix("SGV0 A12"))
        assertEquals("Giaonhan247", groupOf("SGV0 A12"))
    }

    @Test fun ocr_spaceOrDashBecomesUnderscore() {
        assertEquals("SGVO_A12", StationUS.ocrFix("SGVO A12"))
        assertEquals("CHHEN_X1", StationUS.ocrFix("CHHEN-X1"))
    }

    @Test fun ocr_dofa168_misreads_goToFado168_notFadoVn() {
        assertEquals("SG HUE_DOFA168", StationUS.ocrFix("SG HUE DOFA16&."))
        assertEquals("SG FRAGILE_DOFA168", StationUS.ocrFix("SG FRAGILE DOFA 168"))
        assertEquals("FADO 168", groupOf("SG HUE DOFA16&."))
        assertEquals("FADO 168", groupOf("SG FRAGILE DOFA168"))
        // không có "168" thì là FADO.VN (kinh doanh)
        assertEquals("FADO.VN", groupOf("SG HUE DOFA"))
    }

    // ---------- Khớp theo dòng ----------

    @Test fun firstMatch_findsCodeOnSecondLine() {
        val r = matcher.firstMatch(listOf("USPS", "DELIVER TO: FDVAT HUE"), us)
        assertNotNull(r)
        assertEquals("FADO.VN", r!!.rule.customerGroup)
        assertEquals("FDVAT HUE", r.text)
    }

    @Test fun firstMatch_joinsTwoSplitLines() {
        // Mã kho bị ngắt dòng: "FD VAT" / "HUE" riêng thì không khớp, ghép lại "FD VAT HUE" thì khớp FADO.VN.
        val r = matcher.firstMatch(listOf("FD VAT", "HUE"), us)
        assertNotNull(r)
        assertEquals("FADO.VN", r!!.rule.customerGroup)
        assertEquals("FD VAT HUE", r.text)
        // Giống iOS: đoạn ghép chỉ ghép các dòng ĐÃ chuẩn hoá, không chạy lại ocrFix -> "SGVO","A12" không ghép thành "SGVO_A12".
        assertNull(matcher.firstMatch(listOf("SGVO", "A12"), us))
    }

    @Test fun firstMatch_dropsLeadingNoiseWords() {
        val r = matcher.firstMatch(listOf("ABC XYZ FD VAT HUE"), us)
        assertNotNull(r)
        assertEquals("FD VAT HUE", r!!.text)
    }

    @Test fun firstMatch_noMatch() {
        assertNull(matcher.firstMatch(listOf("HELLO", "WORLD"), us))
    }

    @Test fun emptyRules_matchNothing() {
        val m = RuleMatcher(StationDE.profile.warehouseRules)
        assertTrue(m.isEmpty)
        assertNull(m.match("SGVO_A12"))
    }

    @Test fun inactiveRules_areIgnored() {
        val rules = us.warehouseRules.map { it.copy(isActive = false) }
        assertTrue(RuleMatcher(rules).isEmpty)
    }

    // ---------- Nhận trạm ----------

    @Test fun usMatches_onlyOR_1_notTestOregon() {
        assertTrue(us.matches(CompanyRef(1, "OR_1", "Hub Oregon")))
        assertTrue(us.matches(CompanyRef(1, "or1", "x")))
        assertFalse(us.matches(CompanyRef(2, "OREGON", "Oregon test")))
    }

    @Test fun deMatches_byCode_andKeywords() {
        val de = StationDE.profile
        assertTrue(de.matches(CompanyRef(3, "DE_FRANKFURT", "Hub Frankfurt")))
        assertTrue(de.matches(CompanyRef(9, "X", "Frankfurt am Main")))
        assertFalse(de.matches(CompanyRef(1, "OR_1", "Hub Oregon")))
    }

    @Test fun unknownStation_matchesEverything() {
        assertTrue(us.matches(null))
    }

    @Test fun resolve_fallsBackToUs() {
        assertSame(StationUS.profile, StationProfile.resolve(null))
        assertSame(StationUS.profile, StationProfile.resolve("???"))
        assertSame(StationDE.profile, StationProfile.resolve("DE"))
        assertEquals(listOf("US", "DE"), StationProfile.all.map { it.id })
    }
}
