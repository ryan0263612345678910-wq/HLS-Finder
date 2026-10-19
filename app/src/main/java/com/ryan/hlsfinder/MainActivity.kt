package com.ryan.hlsfinder

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.URI
import java.util.Collections
import java.util.LinkedHashSet

class MainActivity : Activity() {
    private lateinit var address: EditText
    private lateinit var webView: WebView
    private lateinit var resultsBox: LinearLayout
    private lateinit var countText: TextView
    private val links = Collections.synchronizedSet(LinkedHashSet<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 18, 32)
        window.navigationBarColor = Color.rgb(12, 18, 32)
        buildUi()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
            setBackgroundColor(Color.rgb(12, 18, 32))
        }
        val title = TextView(this).apply {
            text = "HLS FINDER"
            textSize = 23f
            setTextColor(Color.rgb(94, 234, 212))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title)

        address = EditText(this).apply {
            hint = "Digite um site (https://...)"
            textSize = 15f
            isSingleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(10), 0, dp(10), 0)
            setBackgroundColor(Color.rgb(31, 41, 55))
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO) { openAddress(); true } else false
            }
        }
        root.addView(address, LinearLayout.LayoutParams(-1, dp(48)))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(makeButton("ABRIR", true) { openAddress() }, weightParams())
        actions.addView(makeButton("LIMPAR", false) {
            links.clear()
            renderLinks()
        }, weightParams())
        root.addView(actions)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    address.setText(url)
                    return false
                }
                return true
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                request?.url?.toString()?.let { detectLink(it) }
                return super.shouldInterceptRequest(view, request)
            }

            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String?): android.webkit.WebResourceResponse? {
                url?.let { detectLink(it) }
                return super.shouldInterceptRequest(view, url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null) address.setText(url)
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))

        val resultHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        countText = TextView(this).apply {
            text = "Links de vídeo encontrados: 0"
            textSize = 14f
            setTextColor(Color.WHITE)
        }
        resultHeader.addView(countText, LinearLayout.LayoutParams(0, dp(42), 1f))
        resultHeader.addView(makeButton("COPIAR TODOS", true) { copyAll() }, LinearLayout.LayoutParams(-2, dp(38)))
        root.addView(resultHeader)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(20, 29, 46))
        }
        resultsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        scroll.addView(resultsBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(175)))
        setContentView(root)
        renderLinks()
    }

    private fun weightParams() = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
        marginEnd = dp(5)
        bottomMargin = dp(6)
    }

    private fun makeButton(label: String, primary: Boolean, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(if (primary) Color.rgb(8, 25, 34) else Color.WHITE)
            setBackgroundColor(if (primary) Color.rgb(94, 234, 212) else Color.rgb(55, 65, 81))
            setOnClickListener { action() }
        }
    }

    private fun openAddress() {
        var url = address.text.toString().trim()
        if (url.isBlank()) {
            Toast.makeText(this, "Digite o endereço de um site", Toast.LENGTH_SHORT).show()
            return
        }
        if (!url.contains("://")) url = "https://$url"
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "Use um endereço HTTP ou HTTPS", Toast.LENGTH_SHORT).show()
            return
        }
        address.setText(url)
        webView.loadUrl(url)
    }

    private fun detectLink(raw: String) {
        val clean = raw.trim()
        if (!clean.startsWith("http://", ignoreCase = true) &&
            !clean.startsWith("https://", ignoreCase = true)) return
        val lower = clean.lowercase()
        val path = try { URI(clean).path?.lowercase() ?: lower.substringBefore('?') }
            catch (_: Exception) { lower.substringBefore('?') }
        // Detecta playlists HLS e MPEG-DASH; mantém a URL completa, incluindo tokens.
        val isPlaylist = path.endsWith(".m3u8") || path.endsWith(".mpd") ||
            lower.contains(".m3u8?") || lower.contains(".mpd?")
        if (!isPlaylist) return
        val added = synchronized(links) { links.add(clean) }
        if (added) runOnUiThread { renderLinks() }
    }

    private fun renderLinks() {
        if (!::resultsBox.isInitialized) return
        resultsBox.removeAllViews()
        val snapshot = synchronized(links) { links.toList().reversed() }
        countText.text = "Playlists encontradas: ${snapshot.size}"
        if (snapshot.isEmpty()) {
            resultsBox.addView(TextView(this).apply {
                text = "Abra um site e reproduza o vídeo. Os links detectados aparecerão aqui."
                setTextColor(Color.LTGRAY)
                textSize = 13f
                setPadding(dp(6), dp(10), dp(6), dp(10))
            })
            return
        }
        snapshot.forEachIndexed { index, url ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(7), dp(5), dp(7), dp(7))
                setBackgroundColor(Color.rgb(31, 41, 55))
            }
            val label = TextView(this).apply {
                text = "${index + 1}. ${url.take(220)}"
                setTextColor(Color.WHITE)
                textSize = 12f
                setTextIsSelectable(true)
            }
            card.addView(label)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(makeButton("COPIAR LINK", true) { copyText(url) }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(5) })
            row.addView(makeButton("ABRIR PLAYER", false) { openPlayer(url) }, LinearLayout.LayoutParams(0, dp(38), 1f))
            card.addView(row)
            card.addView(makeButton("DIAGNÓSTICO", false) { showLinkDiagnostic(url) },
                LinearLayout.LayoutParams(-1, dp(38)))
            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = dp(6)
            resultsBox.addView(card, params)
        }
    }

    private fun copyText(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("M3U8 URL", value))
        Toast.makeText(this, "Link M3U8 copiado!", Toast.LENGTH_SHORT).show()
    }

    private fun copyAll() {
        val snapshot = synchronized(links) { links.toList() }
        if (snapshot.isEmpty()) {
            Toast.makeText(this, "Nenhum link encontrado ainda", Toast.LENGTH_SHORT).show()
            return
        }
        copyText(snapshot.joinToString("\n"))
    }

    private fun showLinkDiagnostic(url: String) {
        val uri = Uri.parse(url)
        val path = uri.lastPathSegment.orEmpty().lowercase()
        val kind = when {
            path.endsWith(".m3u8") -> "HLS (.m3u8)"
            path.endsWith(".mpd") -> "MPEG-DASH (.mpd)"
            else -> "Formato de playlist não identificado"
        }
        val tokenHints = buildList {
            if (url.contains("token", true) || url.contains("signature", true) ||
                url.contains("expires", true) || url.contains("keyname", true)) {
                add("A URL parece conter token/assinatura temporária.")
            }
            if (url.contains("IPRanges", true) || url.contains("iprange", true)) {
                add("A URL contém um parâmetro de faixa de IP; o servidor pode restringir outros dispositivos/redes.")
            }
            if (url.contains("cookie", true)) add("A URL menciona cookie; pode depender da sessão.")
        }
        val message = buildString {
            append("Formato: ").append(kind).append("\\n")
            append("Servidor: ").append(uri.host ?: "não identificado").append("\\n\\n")
            append("URL completa preservada: sim\\n")
            if (tokenHints.isEmpty()) {
                append("Não foi possível identificar restrições apenas pela URL.\\n")
            } else {
                tokenHints.forEach { append("• ").append(it).append("\\n") }
            }
            append("\\nSe o servidor vincular o token à sessão ou ao IP original, o aplicativo não consegue tornar o link universal apenas editando a URL.")
        }
        AlertDialog.Builder(this)
            .setTitle("Diagnóstico da playlist")
            .setMessage(message)
            .setPositiveButton("Copiar URL completa") { _, _ -> copyText(url) }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun openPlayer(url: String) {
        val uri = Uri.parse(url)
        val mime = if (uri.lastPathSegment.orEmpty().lowercase().endsWith(".mpd"))
            "application/dash+xml" else "application/vnd.apple.mpegurl"
        // Tenta abrir explicitamente no VLC; se não estiver instalado, oferece o player padrão.
        try {
            val vlcIntent = Intent(Intent.ACTION_VIEW).apply {
                setPackage("org.videolan.vlc")
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(vlcIntent)
        } catch (_: Exception) {
            try {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                }, "Abrir playlist com"))
            } catch (_: Exception) {
                Toast.makeText(this, "Nenhum player compatível encontrado. Copie a URL e teste no VLC.", Toast.LENGTH_LONG).show()
                copyText(url)
            }
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
