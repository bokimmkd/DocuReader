-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn org.bouncycastle.**
# PDFBox's documented optional JPEG2000 codec is not packaged; core page copying,
# text and native Android PdfRenderer do not depend on it. Do not suppress other classes.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
