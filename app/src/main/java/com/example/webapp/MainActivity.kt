package com.example.webapp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var progressBar: ProgressBar
    private lateinit var selectionView: LinearLayout
    private lateinit var webContainer: FrameLayout

    // ============================================================
    //  ★ 默认地址（拉不到远程配置时用）
    // ============================================================
    private val defaultUserUrl = "https://uu932268-9cd8-96b009b7.westc.seetacloud.com:8443"
    private val defaultAirportUrl = "https://u932268-9cd8-96b009b7.westc.seetacloud.com:8443"

    // 当前生效的地址（会被远程配置覆盖）
    private var currentUserUrl = defaultUserUrl
    private var currentAirportUrl = defaultAirportUrl


    private val configUrls = listOf(
        "https://raw.githubusercontent.com/YAN-625/luggage-app/main/config.json",
        "https://cdn.jsdelivr.net/gh/YAN-625/luggage-app@main/config.json",
        "https://ghproxy.net/https://raw.githubusercontent.com/YAN-625/luggage-app/main/config.json"
    )

    // 隐藏设置触发
    private val secretTapCount = 7
    private var tapCount = 0
    private var lastTapTime = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        val root = FrameLayout(this)

        // =========================================================
        //  选择页
        // =========================================================
        selectionView = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F5F6FA"))
        }

        val title = TextView(this).apply {
            text = "破损行李检测系统"
            textSize = 26f
            setTextColor(Color.parseColor("#222222"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "请选择要进入的界面"
            textSize = 15f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
        }

        val btnUser = Button(this).apply {
            text = "👤  用户端"
            textSize = 20f
            val params = LinearLayout.LayoutParams(
                (260 * resources.displayMetrics.density).toInt(),
                (80 * resources.displayMetrics.density).toInt()
            )
            params.topMargin = (40 * resources.displayMetrics.density).toInt()
            layoutParams = params
            setOnClickListener { openUrl(currentUserUrl) }
        }

        val btnAirport = Button(this).apply {
            text = "🛫  机场端"
            textSize = 20f
            val params = LinearLayout.LayoutParams(
                (260 * resources.displayMetrics.density).toInt(),
                (80 * resources.displayMetrics.density).toInt()
            )
            params.topMargin = (24 * resources.displayMetrics.density).toInt()
            layoutParams = params
            setOnClickListener { openUrl(currentAirportUrl) }
        }

        selectionView.addView(title)
        selectionView.addView(subtitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (10 * resources.displayMetrics.density).toInt() })
        selectionView.addView(btnUser)
        selectionView.addView(btnAirport)

        // =========================================================
        //  WebView 容器（初始隐藏）
        // =========================================================
        webContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                allowFileAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                }
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    progressBar.visibility = View.GONE
                }
            }
        }

        progressBar = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        // 刷新按钮（右下角）
        val refreshBtn = ImageButton(this).apply {
            val size = (56 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = (40 * resources.displayMetrics.density).toInt()
                rightMargin = (24 * resources.displayMetrics.density).toInt()
            }
            alpha = 0.55f
            setBackgroundColor(Color.parseColor("#66000000"))
            setImageDrawable(getDrawable(android.R.drawable.ic_menu_rotate))
            setOnClickListener {
                webView.reload()
                Toast.makeText(this@MainActivity, "已刷新", Toast.LENGTH_SHORT).show()
            }
        }

        // 返回选择页的按钮（左下角）
        val homeBtn = ImageButton(this).apply {
            val size = (56 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                bottomMargin = (40 * resources.displayMetrics.density).toInt()
                leftMargin = (24 * resources.displayMetrics.density).toInt()
            }
            alpha = 0.55f
            setBackgroundColor(Color.parseColor("#66000000"))
            setImageDrawable(getDrawable(android.R.drawable.ic_menu_revert))
            setOnClickListener {
                showSelection()
            }
        }

        // 隐藏设置手势区（右上角）
        val secretSize = (60 * resources.displayMetrics.density).toInt()
        val secretArea = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(secretSize, secretSize).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - lastTapTime > 1500) tapCount = 0
                lastTapTime = now
                tapCount++
                if (tapCount >= secretTapCount) {
                    tapCount = 0
                    showUrlDialog()
                }
            }
        }

        webContainer.addView(webView)
        webContainer.addView(progressBar)
        webContainer.addView(refreshBtn)
        webContainer.addView(homeBtn)
        webContainer.addView(secretArea)

        root.addView(selectionView)
        root.addView(webContainer)
        setContentView(root)

        // 后台拉远程配置（不阻塞界面）
        fetchRemoteConfig()
    }

    /** 打开某个界面 */
    private fun openUrl(url: String) {
        selectionView.visibility = View.GONE
        webContainer.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        webView.loadUrl(url)
    }

    /** 回到选择页 */
    private fun showSelection() {
        webContainer.visibility = View.GONE
        selectionView.visibility = View.VISIBLE
    }

    /** 拉远程配置 */
    private fun fetchRemoteConfig() {
        thread {
            var jsonText: String? = null
            for (cfgUrl in configUrls) {
                try {
                    val conn = URL(cfgUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.setRequestProperty("Cache-Control", "no-cache")
                    conn.connect()
                    if (conn.responseCode == 200) {
                        jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                        conn.disconnect()
                        break
                    } else {
                        conn.disconnect()
                    }
                } catch (e: Exception) {
                    // 尝试下一个
                }
            }

            if (!jsonText.isNullOrEmpty()) {
                try {
                    val json = JSONObject(jsonText)
                    val u = json.optString("userUrl").trim()
                    val a = json.optString("airportUrl").trim()
                    if (u.isNotEmpty()) currentUserUrl = u
                    if (a.isNotEmpty()) currentAirportUrl = a
                } catch (e: Exception) {
                    // 解析失败就不更新
                }
            }
        }
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply {
            hint = "https://xxx.autodl.com"
            setText(currentUserUrl)
            setSelection(text.length)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }
        container.addView(TextView(this).apply { text = "用户端地址：" })
        container.addView(input)

        val input2 = EditText(this).apply {
            hint = "https://xxx.autodl.com"
            setText(currentAirportUrl)
            setSelection(text.length)
        }
        container.addView(TextView(this).apply {
            text = "机场端地址："
            setPadding(0, 40, 0, 0)
        })
        container.addView(input2)

        AlertDialog.Builder(this)
            .setTitle("修改访问网址")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                var u = input.text.toString().trim()
                var a = input2.text.toString().trim()
                if (u.isNotEmpty()) {
                    if (!u.startsWith("http")) u = "https://$u"
                    currentUserUrl = u
                }
                if (a.isNotEmpty()) {
                    if (!a.startsWith("http")) a = "https://$a"
                    currentAirportUrl = a
                }
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("恢复默认") { _, _ ->
                currentUserUrl = defaultUserUrl
                currentAirportUrl = defaultAirportUrl
                Toast.makeText(this, "已恢复默认", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    override fun onBackPressed() {
        if (webContainer.visibility == View.VISIBLE) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                showSelection()
            }
        } else {
            super.onBackPressed()
        }
    }
}