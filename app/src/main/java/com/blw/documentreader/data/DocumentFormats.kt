package com.blw.documentreader.data

/** One registry for Android intents, pickers, Library names and sharing. */
object DocumentFormats {
    val byExtension=linkedMapOf("pdf" to "PDF","docx" to "Word","xlsx" to "Excel","txt" to "TXT",
        "csv" to "CSV","pptx" to "PowerPoint","epub" to "EPUB","rtf" to "RTF","odt" to "ODT",
        "jpg" to "JPG","jpeg" to "JPEG","png" to "PNG","webp" to "WebP")
    val images=setOf("JPG","JPEG","PNG","WebP")
    val mimeByType=mapOf("PDF" to "application/pdf","Word" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "Excel" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","TXT" to "text/plain",
        "CSV" to "text/csv","PowerPoint" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "EPUB" to "application/epub+zip","RTF" to "application/rtf","ODT" to "application/vnd.oasis.opendocument.text",
        "JPG" to "image/jpeg","JPEG" to "image/jpeg","PNG" to "image/png","WebP" to "image/webp")
    val pickerTypes=(mimeByType.values+listOf("text/comma-separated-values","application/csv","text/rtf","application/x-rtf","application/octet-stream")).distinct().toTypedArray()
    const val supported="PDF, DOCX, XLSX, TXT, CSV, PPTX, EPUB, RTF, ODT, JPG, PNG and WebP"
    fun extension(type:String)=byExtension.entries.firstOrNull{it.value==type}?.key ?: "pdf"
    fun detect(name:String,mime:String):String? = byExtension[name.substringAfterLast('.', "").lowercase()]
        ?: when(mime.lowercase().substringBefore(';')) {
            "text/rtf","application/x-rtf"->"RTF"
            "text/comma-separated-values","application/csv"->"CSV"
            else->mimeByType.entries.firstOrNull{it.value==mime.lowercase().substringBefore(';')}?.key
        }
    fun matches(filter:String,type:String)=filter==type || filter=="Images" && type in images
}
