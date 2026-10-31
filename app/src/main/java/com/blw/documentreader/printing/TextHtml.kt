package com.blw.documentreader.printing
import android.text.Html
object TextHtml {
    fun document(text: String) = "<!doctype html><html><head><meta charset='utf-8'><meta name=viewport content='width=device-width,initial-scale=1'>" +
        "<style>body{margin:12px;color:#172338;background:#fff}pre{font:16px/1.4 monospace;white-space:pre-wrap;overflow-wrap:anywhere}@media print{body{margin:0}pre{font-size:12pt}}</style>" +
        "</head><body><pre>" + Html.escapeHtml(text) + "</pre></body></html>"
}
