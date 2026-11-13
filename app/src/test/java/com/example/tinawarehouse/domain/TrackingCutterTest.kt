package com.example.tinawarehouse.domain

import com.example.tinawarehouse.domain.scan.TrackingCutter
import com.example.tinawarehouse.domain.station.StationDE
import com.example.tinawarehouse.domain.station.StationUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kiểm tra cắt mã vạch → tracking của trạm Mỹ (OR_1), theo đúng quy tắc trong StationUS. */
class TrackingCutterTest {

    private val us = StationUS.profile

    private fun cut(barcode: String) = TrackingCutter.candidates(barcode, us)

    // ---------- USPS ----------

    @Test fun usps_420_zip5_dropsFirst8() {
        val tracking = "9400111899223344556677"
        val r = cut("42097201$tracking")
        assertEquals(tracking, r.primary)
        assertEquals("42097201$tracking", r.list.first())     // mã gốc luôn đứng đầu
        assertTrue(tracking in r.list)
    }

    @Test fun usps_420_zip9_listContainsRealTracking() {
        val tracking = "9400111899223344556677"
        val r = cut("420972010123$tracking")
        assertTrue("danh sách phải có tracking thật", tracking in r.list)
        // GIỐNG iOS: mẫu "420+ZIP5" khớp trước mẫu "420+ZIP9" nên `primary` đang là "0123"+tracking, không phải tracking thật.
        // Giữ nguyên để Android khớp iOS; nếu sau này sửa cả hai app thì đổi assert này thành assertEquals(tracking, r.primary).
        assertEquals("0123$tracking", r.primary)
    }

    @Test fun usps_20to34digitsStartingWith9_usedAsIs() {
        val code = "9400111899223344556677"
        val r = cut(code)
        assertEquals(code, r.primary)
        assertEquals(listOf(code), r.list)
    }

    @Test fun usps_international_usedAsIs() {
        val r = cut("ee123456789us")                           // chữ thường -> viết hoa
        assertEquals("EE123456789US", r.primary)
    }

    @Test fun usps_zipPlus4_variants() {
        assertTrue("97201-0123" in cut("420972010123").list)   // 420 + ZIP9
        assertTrue("97201-0123" in cut("972010123").list)      // ZIP9
        val back = cut("97201-0123").list                      // bỏ dấu gạch
        assertTrue("972010123" in back)
    }

    // ---------- UPS ----------

    @Test fun ups_1Z_plain() {
        assertEquals("1Z999AA10123456784", cut("1Z999AA10123456784").primary)
    }

    @Test fun ups_1Z_insideLongerString() {
        val r = cut("ZZ 1Z999AA10123456784 XX")
        assertEquals("1Z999AA10123456784", r.primary)
    }

    // ---------- FedEx ----------

    @Test fun fedex_30to34digits_last12() {
        val code = "1001234567890123456789012345678901"       // 34 số
        assertEquals(code.takeLast(12), cut(code).primary)
    }

    @Test fun fedex_96_last15() {
        val code = "96" + "12345678901234567890"              // 22 số, bắt đầu 96
        assertEquals(code.takeLast(15), cut(code).primary)
    }

    @Test fun fedex_92_last20() {
        val code = "92" + "12345678901234567890"              // 22 số, bắt đầu 92
        assertEquals(code.takeLast(20), cut(code).primary)
    }

    @Test fun fedex_12_and_15_digits_usedAsIs() {
        assertEquals("123456789012", cut("123456789012").primary)
        assertEquals("123456789012345", cut("123456789012345").primary)
    }

    // ---------- DHL ----------

    @Test fun dhl_express_10digits() {
        assertEquals("1234567890", cut("1234567890").primary)
    }

    @Test fun dhl_ecommerce() {
        assertEquals("JD014600003216718264", cut("JD014600003216718264").primary)
        assertEquals("GM60512345678901234", cut("GM60512345678901234").primary)
    }

    // ---------- Amazon ----------

    @Test fun amazon_TBA_insideLongerString() {
        assertEquals("TBA123456789012", cut("XTBA123456789012Y").primary)
        assertEquals("TBM123456789012", cut("TBM123456789012").primary)
    }

    // ---------- Chuẩn hoá + trạm khác ----------

    @Test fun normalize_removesSpacesAndControlChars_andUppercases() {
        assertEquals("1ZABC", TrackingCutter.normalize(" 1z \n ab\u0007c\t"))
    }

    @Test fun unknownBarcode_isItsOwnPrimary() {
        val r = cut("HELLO123")
        assertEquals("HELLO123", r.primary)
        assertEquals(listOf("HELLO123"), r.list)
    }

    @Test fun germanyProfile_hasNoFormatsYet_andNoZipPlus4() {
        val r = TrackingCutter.candidates("972010123", StationDE.profile)
        assertEquals(listOf("972010123"), r.list)              // không có biến thể ZIP+4
        assertFalse(StationDE.profile.zipPlus4Variants)
    }

    @Test fun formatMode_unknownString_isFull() {
        assertEquals(TrackingCutter.Mode.FULL, TrackingCutter.Mode.from("???"))
        assertEquals(TrackingCutter.Mode.LAST, TrackingCutter.Mode.from("LAST"))
    }
}
