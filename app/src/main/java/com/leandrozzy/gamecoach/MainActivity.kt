package com.leandrozzy.gamecoach

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.webkit.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (r.url.toString().startsWith("http")) {
                    startActivity(Intent(Intent.ACTION_VIEW, r.url)); return true
                }
                return false
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null); fileCb = cb
                val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(Intent.createChooser(i, "Prints"), 7); return true
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("pt", "BR") }
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req != 7) { super.onActivityResult(req, res, data); return }
        val uris = mutableListOf<Uri>()
        if (res == RESULT_OK && data != null) {
            val c = data.clipData
            if (c != null) for (k in 0 until c.itemCount) uris.add(c.getItemAt(k).uri)
            else data.data?.let { uris.add(it) }
        }
        fileCb?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray()); fileCb = null
    }

    inner class Bridge {
        @JavascriptInterface
        fun request(id: String, method: String, url: String, body: String) {
            thread {
                var code = 0
                var out: String
                try {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.requestMethod = method; c.connectTimeout = 30000; c.readTimeout = 240000
                    c.setRequestProperty("Content-Type", "application/json")
                    if (method == "POST") { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
                    code = c.responseCode
                    out = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
                } catch (e: Exception) {
                    out = "{\"error\":{\"message\":" + JSONObject.quote(e.toString()) + "}}"
                }
                val js = "window.__resp(" + JSONObject.quote(id) + "," + code + "," + JSONObject.quote(out) + ")"
                runOnUiThread { web.evaluateJavascript(js, null) }
            }
        }
        @JavascriptInterface fun speak(t: String) { tts?.speak(t, TextToSpeech.QUEUE_ADD, null, "g" + t.hashCode()) }
        @JavascriptInterface fun stop() { tts?.stop() }
        @JavascriptInterface fun copy(t: String) {
            runOnUiThread { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("coach", t)) }
        }
    }

    @Deprecated("") override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
