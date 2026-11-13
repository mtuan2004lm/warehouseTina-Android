package com.example.tinawarehouse.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tinawarehouse.R
import com.example.tinawarehouse.domain.session.SessionFormat
import com.example.tinawarehouse.ui.theme.Accent

/** Logo hãng giao hàng; hãng chưa có logo (vd. "Other") thì hiện tên chữ. Có bản logo riêng cho nền tối. */
@Composable
fun CarrierLogo(name: String, code: String?, height: Dp = 26.dp, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val res = logoRes(SessionFormat.carrierLogo(name, code), dark)
    if (res != null) {
        Image(
            painterResource(res), contentDescription = name, contentScale = ContentScale.Fit,
            modifier = modifier.height(height).widthIn(max = height * 3.6f),
        )
    } else {
        Text(name, color = Accent, fontWeight = FontWeight.Bold, modifier = modifier)
    }
}

@DrawableRes
private fun logoRes(asset: String?, dark: Boolean): Int? = when (asset) {
    "logo_fedex" -> R.drawable.logo_fedex
    "logo_dhl" -> R.drawable.logo_dhl
    "logo_ups" -> R.drawable.logo_ups
    "logo_ontrac" -> if (dark) R.drawable.logo_ontrac_dark else R.drawable.logo_ontrac
    "logo_amazon" -> if (dark) R.drawable.logo_amazon_dark else R.drawable.logo_amazon
    "logo_usps" -> if (dark) R.drawable.logo_usps_dark else R.drawable.logo_usps
    else -> null
}
