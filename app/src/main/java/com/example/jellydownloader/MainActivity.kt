package com.example.jellydownloader

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
 private val executor = Executors.newSingleThreadExecutor()
 private lateinit var url: EditText
 private lateinit var status: TextView
 private lateinit var progress: ProgressBar
 private lateinit var folder: TextView
 private var folderUri: Uri? = null
 private val dark = Color.rgb(12,19,34)
 private val surface = Color.rgb(25,37,58)
 private val blue = Color.rgb(88,151,255)
 private fun shape(color:Int, radius:Float=24f)=GradientDrawable().apply { setColor(color); cornerRadius=radius }
 private fun text(s:String, size:Float=16f, color:Int=Color.WHITE)=TextView(this).apply { text=s; textSize=size; setTextColor(color); gravity=Gravity.RIGHT; setPadding(12,12,12,12) }
 private fun edit(hintText:String)=EditText(this).apply { hint=hintText; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE); textDirection=View.TEXT_DIRECTION_LTR; setSingleLine(); background=shape(surface); setPadding(24,20,24,20) }
 private fun button(s:String, action:()->Unit)=Button(this).apply { text=s; textSize=16f; setTextColor(Color.WHITE); background=shape(blue); setOnClickListener { action() } }
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState)
  window.statusBarColor=dark; window.navigationBarColor=dark
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,36,24,24); setBackgroundColor(dark) }
  val scroll=ScrollView(this).apply { fillViewport=true; addView(root) }; setContentView(scroll)
  root.addView(text("Jelly Downloader",28f).apply { gravity=Gravity.CENTER; setTypeface(null,Typeface.BOLD) })
  root.addView(text("הורדה פשוטה • ממשק נקי • שמירה לתיקייה שלך",14f,Color.LTGRAY).apply { gravity=Gravity.CENTER })
  root.addView(text("כתובת הקובץ",18f))
  url=edit("https://example.com/video.mp4"); root.addView(url,LinearLayout.LayoutParams(-1,120))
  root.addView(text("מיקום שמירה",18f))
  folder=text("בחר תיקייה לשמירת הקובץ",15f,Color.LTGRAY); root.addView(folder)
  root.addView(button("בחירת תיקייה") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),100) })
  root.addView(text("התקדמות",18f))
  progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=100 }; root.addView(progress,LinearLayout.LayoutParams(-1,48))
  status=text("מוכן להורדה",15f,Color.LTGRAY); root.addView(status)
  root.addView(button("התחל הורדה") { download() })
  root.addView(text("תומך כרגע בקישורים ישירים לקבצים. קישורים לדפי צפייה, המרת MP3 וחיתוך סרטונים עדיין אינם נתמכים.",13f,Color.LTGRAY))
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==100 && resultCode==RESULT_OK) { val u=data?.data ?: return; folderUri=u
   contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
   getPreferences(MODE_PRIVATE).edit().putString("folder",u.toString()).apply(); folder.text="התיקייה נבחרה בהצלחה"
  }
 }
 private fun download() {
  val link=url.text.toString().trim()
  if(!link.startsWith("https://") && !link.startsWith("http://")) { status.text="יש להזין כתובת תקינה"; return }
  val target=folderUri ?: getPreferences(MODE_PRIVATE).getString("folder",null)?.let { Uri.parse(it) }
  if(target==null) { status.text="יש לבחור תיקייה תחילה"; return }
  status.text="מוריד..."; progress.progress=0
  executor.execute {
   var connection:HttpURLConnection?=null
   try {
    connection=URL(link).openConnection() as HttpURLConnection
    connection.connectTimeout=15000; connection.readTimeout=30000; connection.instanceFollowRedirects=true
    connection.connect()
    if(connection.responseCode !in 200..299) throw Exception("HTTP ${connection.responseCode}")
    val name=Uri.parse(connection.url.toString()).lastPathSegment?.substringBefore("?")?.takeIf { it.isNotBlank() }?.replace(Regex("[^a-zA-Z0-9._-]"),"_") ?: "download.mp4"
    val mime=connection.contentType?.substringBefore(";") ?: "application/octet-stream"
    val doc=DocumentsContract.createDocument(contentResolver,target,mime,name) ?: throw Exception("לא ניתן ליצור קובץ")
    val total=connection.contentLengthLong
    contentResolver.openOutputStream(doc)?.use { out ->
     connection.inputStream.use { input ->
      val buf=ByteArray(65536); var done=0L
      while(true) { val n=input.read(buf); if(n<0) break; out.write(buf,0,n); done+=n
       if(total>0) runOnUiThread { progress.progress=(done*100/total).toInt().coerceIn(0,100) }
      }
     }
    } ?: throw Exception("אין הרשאת כתיבה")
    runOnUiThread { progress.progress=100; status.text="הקובץ נשמר בהצלחה: $name" }
   } catch(e:Exception) { runOnUiThread { status.text="שגיאה: ${e.message}" } }
   finally { connection?.disconnect() }
  }
 }
}
