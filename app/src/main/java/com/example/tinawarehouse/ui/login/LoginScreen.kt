package com.example.tinawarehouse.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tinawarehouse.R
import com.example.tinawarehouse.TinaApplication
import com.example.tinawarehouse.domain.station.StationProfile
import com.example.tinawarehouse.i18n.tr
import com.example.tinawarehouse.ui.theme.Accent
import com.example.tinawarehouse.ui.theme.Navy
import kotlinx.coroutines.launch

/** Màn đăng nhập — luôn tối, giống LoginView.swift. */
@Composable
fun LoginScreen(app: TinaApplication) {
    val auth = app.auth
    val loading by auth.isLoading.collectAsState()
    val error by auth.errorMessage.collectAsState()
    val savedStation by app.app.station.collectAsState()
    var username by remember { mutableStateOf(auth.rememberedUsername) }
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var station by remember { mutableStateOf(savedStation) }
    var menu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val lang = app.ui.lang                       // đọc state để đổi ngôn ngữ là vẽ lại
    val canSubmit = username.isNotBlank() && password.isNotEmpty() && !loading

    fun submit() {
        if (!canSubmit) return
        focus.clearFocus()
        scope.launch {
            app.app.select(station)
            auth.signIn(username, password, station)
            if (auth.isSignedIn.value) password = ""
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, Color.Black)))) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxSize().align(Alignment.TopCenter).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Nút VI / EN
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).padding(3.dp)) {
                    listOf("vi" to "VI", "en" to "EN").forEach { (code, label) ->
                        val on = lang == code
                        Text(
                            label, color = if (on) Color.White else Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.clip(CircleShape).background(if (on) Accent else Color.Transparent)
                                .clickable { app.ui.updateLang(code) }.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 24.dp, vertical = 16.dp)) {
                Image(painterResource(R.drawable.logo_tina), contentDescription = "Tina", contentScale = ContentScale.Fit, modifier = Modifier.width(220.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(tr("Đăng nhập để bắt đầu nhận hàng"), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))

            // Chọn trạm
            Text(tr("Chọn trạm"), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable { menu = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(station.title), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text(if (station.isReady) station.subtitle else tr("Sắp có"), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.White.copy(alpha = 0.7f))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    StationProfile.all.forEach { p ->
                        val label = if (p.isReady) "${tr(p.title)} — ${p.subtitle}" else "${tr(p.title)} — ${tr("Sắp có")}"
                        DropdownMenuItem(
                            text = { Text(label) }, enabled = p.isReady,
                            trailingIcon = { if (p.id == station.id) Icon(Icons.Default.Check, null) },
                            onClick = { station = p; menu = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            val colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                focusedContainerColor = Color.White.copy(alpha = 0.08f), unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                cursorColor = Accent, focusedLeadingIconColor = Accent, unfocusedLeadingIconColor = Color.White.copy(alpha = 0.7f),
                focusedPlaceholderColor = Color.White.copy(alpha = 0.5f), unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it }, singleLine = true, shape = RoundedCornerShape(14.dp),
                placeholder = { Text(tr("Tên đăng nhập")) }, leadingIcon = { Icon(Icons.Default.Person, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, keyboardType = KeyboardType.Email),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                colors = colors, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, singleLine = true, shape = RoundedCornerShape(14.dp),
                placeholder = { Text(tr("Mật khẩu")) }, leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showPw) tr("Ẩn mật khẩu") else tr("Hiện mật khẩu"), tint = Color.White.copy(alpha = 0.7f))
                    }
                },
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Password),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                colors = colors, modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Accent, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { submit() }, enabled = canSubmit, shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, disabledContainerColor = Accent.copy(alpha = 0.4f), contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(tr("Đang đăng nhập…"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else Text(tr("Đăng nhập"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text(tr("Đăng nhập bằng tài khoản kho của công ty"), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}
