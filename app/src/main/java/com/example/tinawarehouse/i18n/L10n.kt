package com.example.tinawarehouse.i18n

/**
 * Đa ngôn ngữ giống app iOS: khoá là câu tiếng Việt gốc, giá trị là bản tiếng Anh ([EN_TABLE]).
 * Dữ liệu từ web (tên hãng, mặt hàng…) không dịch.
 *
 * [codeProvider] trả về mã ngôn ngữ hiện tại ("vi" | "en"). App gắn nó vào state của Compose
 * (xem `LangStore`) để giao diện tự vẽ lại khi đổi ngôn ngữ; test thì gán thẳng một lambda.
 */
object L10n {
    @Volatile
    var codeProvider: () -> String = { "vi" }

    val isEnglish: Boolean get() = codeProvider() == "en"
}

/** Dịch một câu theo ngôn ngữ đang chọn; `{0}`, `{1}` được thay bằng tham số. */
fun tr(key: String, vararg args: Any?): String {
    var s = key
    if (L10n.isEnglish) EN_TABLE[key]?.let { s = it }
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}
