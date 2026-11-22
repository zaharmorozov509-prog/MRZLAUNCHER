package com.mrz.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val PICK_IMAGE_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = true
            domStorageEnabled = true
        }

        webView.addJavascriptInterface(Bridge(), "AndroidLauncher")
        webView.loadUrl("file:///android_asset/index.html")
    }

    // Обработка выбора фото из галереи
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    // Загружаем картинку и конвертируем в Base64
                    val inputStream: InputStream? = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream) // Сжимаем для скорости
                    val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                    
                    // Передаем картинку в JS
                    val jsCode = "javascript:setCustomWallpaper('$base64Image')"
                    webView.post { webView.evaluateJavascript(jsCode, null) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun getApps(): String {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val list = pm.queryIntentActivities(intent, 0)
            val arr = JSONArray()
            for (info in list) {
                val pkg = info.activityInfo.packageName
                if (pkg == packageName) continue
                val iconBase64 = try {
                    val drawable = info.loadIcon(pm)
                    drawableToBase64(drawable)
                } catch (e: Exception) { "" }

                arr.put(JSONObject().apply {
                    put("name", info.loadLabel(pm).toString())
                    put("pkg", pkg)
                    put("icon", iconBase64)
                })
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun openWallpaperPicker() {
            val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        @JavascriptInterface
        fun launchApp(pkg: String) {
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
            }
        }

        @JavascriptInterface
        fun getBatteryLevel(): Int {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }

        @JavascriptInterface
        fun isCharging(): Boolean {
            val i = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val s = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
        }

        private fun drawableToBase64(drawable: Drawable): String {
            val bitmap = if (drawable is BitmapDrawable) drawable.bitmap else {
                Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also {
                    val canvas = Canvas(it)
                    drawable.setBounds(0, 0, 128, 128)
                    drawable.draw(canvas)
                }
            }
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }
    }
}
