package com.monito.expense.ui.main

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/** Renders report HTML in an off-screen WebView and hands it to the system print dialog (paper or PDF). */
object PrintHelper {

    // The print framework keeps a reference to the adapter; we keep the WebView alive alongside it.
    private var current: WebView? = null

    fun print(context: Context, jobName: String, html: String) {
        val webView = WebView(context)
        current = webView
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = view.createPrintDocumentAdapter(jobName)
                val attributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                manager.print(jobName, adapter, attributes)
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
