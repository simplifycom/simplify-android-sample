package com.simplify.android.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import java.lang.IllegalArgumentException

class SimplifySecure3DActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val extraTitle by lazyAndroid { intent.extras?.getString(EXTRA_TITLE) ?: getString(R.string.simplify_3d_secure_authentication) }
    private val extraAcsUrl by lazyAndroid { intent.extras?.getString(EXTRA_ACS_URL) ?: "" }
    private val extraPaReq by lazyAndroid { intent.extras?.getString(EXTRA_PA_REQ) ?: "" }
    private val extraMerchantData by lazyAndroid { intent.extras?.getString(EXTRA_MERCHANT_DATA) ?: "" }
    private val extraTermUrl by lazyAndroid { intent.extras?.getString(EXTRA_TERM_URL) ?: "" }

    private val startingHtml by lazyAndroid {
        resources.openRawResource(R.raw.secure3d1).readBytes().toString(Charsets.UTF_8)
                .replace(PLACEHOLDER_ACS_URL, extraAcsUrl.urlEncode())
                .replace(PLACEHOLDER_PA_REQ, extraPaReq.urlEncode())
                .replace(PLACEHOLDER_MERCHANT_DATA, extraMerchantData.urlEncode())
                .replace(PLACEHOLDER_TERM_URL, extraTermUrl.urlEncode())
    }


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simplify_3dsecure)

        // if required param missing, back out
        if (extraAcsUrl.isEmpty() || extraPaReq.isEmpty() || extraTermUrl.isEmpty() || extraMerchantData.isEmpty()) {
            onBackPressed()
            return
        }

        // init toolbar
        findViewById<Toolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { onBackPressed() }
            title = extraTitle
        }

        // init web view
        webView = findViewById<WebView>(R.id.webview).apply {
            webChromeClient = WebChromeClient()
            settings.domStorageEnabled = true
            settings.javaScriptEnabled = true
            webViewClient = buildWebViewClient()

            loadData(startingHtml, "text/html", Charsets.UTF_8.name())
        }
    }

    internal fun webViewUrlChanges(uri: Uri) {
        when {
            REDIRECT_SCHEME.equals(uri.scheme, ignoreCase = true) -> complete(uri)
            MAILTO_SCHEME.equals(uri.scheme, ignoreCase = true) -> mailto(uri)
            else -> redirect(uri)
        }
    }

    private fun complete(uri: Uri) {
        val intent = Intent().apply {
            putExtra(EXTRA_RESULT, getResultFromUri(uri))
        }

        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun mailto(uri: Uri) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            data = uri
        }

        startActivity(intent)
    }

    private fun redirect(uri: Uri) {
        webView.loadUrl(uri.toString())
    }

    private fun getResultFromUri(uri: Uri): String? {
        var result: String? = null

        uri.queryParameterNames.forEach { param ->
            if (REDIRECT_QUERY_PARAM.equals(param, ignoreCase = true)) {
                result = uri.getQueryParameter(param)
            }
        }

        return result
    }

    private fun buildWebViewClient(): WebViewClient {
        return object : WebViewClient() {

            @Suppress("OverridingDeprecatedMember")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                webViewUrlChanges(Uri.parse(url))
                return true
            }

            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                webViewUrlChanges(request.url)
                return true
            }
        }
    }

    companion object {

        /**
         * The ACS URL returned from card token create
         */
        const val EXTRA_ACS_URL = "com.simplify.android.sdk.ASC_URL"

        /**
         * The PaReq returned from card token create
         */
        const val EXTRA_PA_REQ = "com.simplify.android.sdk.PA_REQ"

        /**
         * The Merchant Data (md) returned from card token create
         */
        const val EXTRA_MERCHANT_DATA = "com.simplify.android.sdk.MERCHANT_DATA"

        /**
         * The Termination URL (termUrl) returned from card token create
         */
        const val EXTRA_TERM_URL = "com.simplify.android.sdk.TERM_URL"

        /**
         * An OPTIONAL title to display in the toolbar for this activity
         */
        const val EXTRA_TITLE = "com.simplify.android.sdk.TITLE"

        /**
         * The result data after performing 3DS
         */
        const val EXTRA_RESULT = "com.simplify.android.sdk.RESULT"


        private const val REDIRECT_SCHEME = "simplifysdk"
        private const val REDIRECT_QUERY_PARAM = "result"
        private const val MAILTO_SCHEME = "mailto"
        private const val PLACEHOLDER_ACS_URL = "{{{acsUrl}}}"
        private const val PLACEHOLDER_PA_REQ = "{{{paReq}}}"
        private const val PLACEHOLDER_MERCHANT_DATA = "{{{md}}}"
        private const val PLACEHOLDER_TERM_URL = "{{{termUrl}}}"

        /**
         * Construct an intent to the [SimplifySecure3DActivity] activity, adding the relevant 3DS data from the card token as intent extras
         *
         * @param context The calling context
         * @param cardToken The card token used for a 3DS transaction
         * @param title An OPTIONAL title to display in the toolbar
         * @throws IllegalArgumentException If the card token does not contain valid 3DS data
         */
        @JvmOverloads
        @JvmStatic
        fun buildIntent(context: Context, cardToken: SimplifyMap, title: String? = null): Intent {
            if (!cardToken.containsKey("card.secure3DData")) {
                throw IllegalArgumentException("The provided card token must contain 3DS data.")
            }

            return Intent(context, SimplifySecure3DActivity::class.java).apply {
                putExtra(EXTRA_ACS_URL, cardToken["card.secure3DData.acsUrl"] as String?)
                putExtra(EXTRA_PA_REQ, cardToken["card.secure3DData.paReq"] as String?)
                putExtra(EXTRA_MERCHANT_DATA, cardToken["card.secure3DData.md"] as String?)
                putExtra(EXTRA_TERM_URL, cardToken["card.secure3DData.termUrl"] as String?)
                putExtra(EXTRA_TITLE, title)
            }
        }
    }
}