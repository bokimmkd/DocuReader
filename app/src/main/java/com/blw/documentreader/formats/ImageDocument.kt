package com.blw.documentreader.formats

import android.content.Context
import android.net.Uri
import android.graphics.*
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.util.Base64

object ImageDocument {
    fun read(context:Context,uri:Uri):LocalFormats.Page {
        val bytes=context.contentResolver.openInputStream(uri)?.use{LocalFormats.bounded(it,24*1024*1024)}?:error("Image access expired. Locate it again.")
        val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
        require(options.outWidth>0&&options.outHeight>0){"This image is damaged or unsupported."}
        var sample=1
        while(options.outWidth/sample>2048 || options.outHeight/sample>2048)sample*=2
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})?:error("Could not decode image.")
        val orientation=runCatching{ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
        val matrix=Matrix().apply{when(orientation){2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f);5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(270f);postScale(-1f,1f)};8->setRotate(270f)}}
        val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        val flat=Bitmap.createBitmap(rotated.width,rotated.height,Bitmap.Config.ARGB_8888)
        Canvas(flat).apply{drawColor(Color.WHITE);drawBitmap(rotated,0f,0f,null)}
        val data=ByteArrayOutputStream();flat.compress(Bitmap.CompressFormat.JPEG,92,data)
        flat.recycle();if(rotated!==bitmap)rotated.recycle();bitmap.recycle()
        return LocalFormats.Page(LocalFormats.wrap("<div class='image'><img alt='Selected image' src='data:image/jpeg;base64,${Base64.getEncoder().encodeToString(data.toByteArray())}'></div>"),
            "Image preview optimized to 2048 px · Original file unchanged.")
    }
}
