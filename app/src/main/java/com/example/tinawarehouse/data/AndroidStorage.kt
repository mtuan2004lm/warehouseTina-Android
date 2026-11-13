package com.example.tinawarehouse.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cài đặt nhỏ lưu bằng SharedPreferences (giống UserDefaults). */
class SharedPrefsStore(context: Context, name: String = "tina_settings") : KeyValueStore {
    private val sp: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun getString(key: String): String? = sp.getString(key, null)
    override fun putString(key: String, value: String?) { sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
    override fun getInt(key: String): Int? = if (sp.contains(key)) sp.getInt(key, 0) else null
    override fun putInt(key: String, value: Int?) { sp.edit().apply { if (value == null) remove(key) else putInt(key, value) }.apply() }
    override fun getStringList(key: String): List<String>? = sp.getString(key, null)?.let { if (it.isEmpty()) emptyList() else it.split("\u0001") }
    override fun putStringList(key: String, value: List<String>?) { putString(key, value?.joinToString("\u0001")) }
}

/** Token đăng nhập, mã hóa bằng Android Keystore (giống Keychain của iOS). */
class EncryptedTokenStore(context: Context) : TokenStore {
    private val sp: SharedPreferences = EncryptedSharedPreferences.create(
        context, "tina_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    override fun accessToken(): String? = sp.getString("access", null)
    override fun refreshToken(): String? = sp.getString("refresh", null)
    override fun save(accessToken: String, refreshToken: String) {
        sp.edit().putString("access", accessToken).putString("refresh", refreshToken).apply()
    }
    override fun clear() { sp.edit().clear().apply() }
}

/** Gửi HTTP thật bằng OkHttp. */
class OkHttpTransport(private val base: OkHttpClient = OkHttpClient()) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse {
        val client = base.newBuilder().callTimeout(request.timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(request.timeoutSeconds, TimeUnit.SECONDS).build()
        val ct = request.headers["Content-Type"]?.toMediaTypeOrNull()
        val b = okhttp3.Request.Builder().url(request.url)
        request.headers.forEach { (k, v) -> if (k != "Content-Type") b.header(k, v) }
        val body = request.body?.toRequestBody(ct)
            ?: if (request.method == "POST" || request.method == "PATCH") ByteArray(0).toRequestBody(null) else null
        b.method(request.method, body)
        val call = client.newCall(b.build())
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { cont.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    response.use { cont.resume(HttpResponse(it.code, it.body?.string() ?: "")) }
                }
            })
        }
    }
}
