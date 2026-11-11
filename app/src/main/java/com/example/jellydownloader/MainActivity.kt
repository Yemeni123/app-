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
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.FFmpeg
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
 private val worker=Executors.newSingleThreadExecutor()
 private lateinit var url:EditText
 private lateinit var start:EditText
 private lateinit var end:EditText
 private lateinit var quality:Spinner
 private lateinit var format:Spinner
 private lateinit var status:TextView
 private lateinit var progress:ProgressBar
 private lateinit var folder:TextView
 private lateinit var downloadButton:Button
 private var target:Uri?=null
 private val bg=Color.rgb(12,18,33)
 private val panel=Color.rgb(27,38,61)
 private val accent=Color.rgb(75,139,255)
 private fun rounded(c:Int)=GradientDrawable().apply { setColor(c); cornerRadius=24f }
 private fun label(s:String,size:Float=16f)=TextView(this).apply { text=s; textSize=size; setTextColor(Color.WHITE); gravity=Gravity.RIGHT; setPadding(8,16,8,12) }
 private fun field(h:String)=EditText(this).apply { hint=h; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE); setSingleLine(); setPadding(24,18,24,18); background=rounded(panel); textDirection=View.TEXT_DIRECTION_LTR }
 private fun button(s:String, action:()->Unit)=Button(this).apply { text=s; textSize=16f; setTextColor(Color.WHITE); background=rounded(accent); setOnClickListener { action() } }
 private fun spinner(values:Array<String>)=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,values) }
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState)
  window.statusBarColor=bg; window.navigationBarColor=bg
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,30,28,40); setBackgroundColor(bg) }
  setContentView(ScrollView(this).apply { addView(root) })
  root.addView(label("Jelly Downloader",28f).apply { gravity=Gravity.CENTER; setTypeface(null,Typeface.BOLD) })
  root.addView(label("הורדות וידאו ואודיו בקלות",14f).apply { gravity=Gravity.CENTER; setTextColor(Color.LTGRAY) })
  root.addView(label("קישור לסרטון"))
  url=field("https://..."); root.addView(url)
  root.addView(label("פורמט"))
  format=spinner(arrayOf("MP4 - וידאו","MP3 - שמע")); root.addView(format)
  root.addView(label("איכות וידאו"))
  quality=spinner(arrayOf("הכי טובה","1080p","720p","480p","360p")); root.addView(quality)
  root.addView(label("חיתוך (אופציונלי) – שעות:דקות:שניות"))
  start=field("התחלה 00:00:00"); root.addView(start)
  end=field("סיום 00:01:30"); root.addView(end)
  folder=label("לא נבחרה תיקיית שמירה",14f); root.addView(folder)
  root.addView(button("בחירת תיקייה") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),101) })
  progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=100 }; root.addView(progress)
  status=label("מוכן",14f); root.addView(status)
  downloadButton=button("התחל הורדה") { download() }; root.addView(downloadButton)
  root.addView(label("הורדה מאתרים הנתמכים ב־yt-dlp בלבד. אין תמיכה בעקיפת DRM.",12f).apply { setTextColor(Color.LTGRAY) })
  getPreferences(MODE_PRIVATE).getString("folder",null)?.let { target=Uri.parse(it); folder.text="תיקיית שמירה נבחרה" }
 }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==101 && resultCode==RESULT_OK) { val u=data?.data ?:return
   try { contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); target=u
    getPreferences(MODE_PRIVATE).edit().putString("folder",u.toString()).apply(); folder.text="תיקיית שמירה נבחרה"
   } catch(e:Exception) { status.text="שגיאת הרשאות: ${e.message}" }
  }
 }
 private fun download() {
  val link=url.text.toString().trim(); val dir=target
  if(!link.startsWith("https://") && !link.startsWith("http://")) { status.text="יש להזין כתובת תקינה"; return }
  if(dir==null) { status.text="יש לבחור תיקייה"; return }
  val audio=format.selectedItemPosition==1
  val q=quality.selectedItemPosition
  val from=start.text.toString().trim(); val to=end.text.toString().trim()
  if((from.isEmpty()!=to.isEmpty()) || (from.isNotEmpty() && (!Regex("\\d{1,2}:\\d{2}:\\d{2}").matches(from) || !Regex("\\d{1,2}:\\d{2}:\\d{2}").matches(to)))) { status.text="יש למלא זמני התחלה וסיום בפורמט 00:00:00"; return }
  downloadButton.isEnabled=false; status.text="מכין הורדה..."; progress.progress=0
  worker.execute {
   val temp=File(cacheDir,"downloads").apply { mkdirs() }
   try {
    YoutubeDL.getInstance().init(applicationContext)
    FFmpeg.getInstance().init(applicationContext)
    val request=YoutubeDLRequest(link)
    request.addOption("--no-playlist")
    request.addOption("--restrict-filenames")
    request.addOption("-P",temp.absolutePath)
    request.addOption("-o","%(title).90s-%(id)s.%(ext)s")
    if(audio) {
     request.addOption("-x"); request.addOption("--audio-format","mp3")
     request.addOption("--audio-quality","0")
    } else {
     val max=when(q) {1->1080;2->720;3->480;4->360;else->0}
     request.addOption("-f",if(max==0) "bv*+ba/b" else "bv*[height<=${max}]+ba/b[height<=${max}]/b")
     request.addOption("--merge-output-format","mp4")
    }
    if(from.isNotEmpty()) { request.addOption("--download-sections","*${from}-${to}"); request.addOption("--force-keyframes-at-cuts") }
    YoutubeDL.getInstance().execute(request,"jelly-${System.currentTimeMillis()}") { p,_,line ->
     runOnUiThread { progress.progress=p.toInt().coerceIn(0,100); status.text=if(line.isBlank()) "מוריד... ${p.toInt()}%" else line.take(130) }
    }
    val files=temp.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") } ?: emptyList()
    if(files.isEmpty()) throw Exception("לא נוצר קובץ להורדה")
    for(file in files) {
     val mime=if(file.extension.lowercase()=="mp3") "audio/mpeg" else "video/mp4"
     val output=DocumentsContract.createDocument(contentResolver,dir,mime,file.name) ?: throw Exception("לא ניתן ליצור קובץ בתיקייה")
     contentResolver.openOutputStream(output)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw Exception("אין הרשאת כתיבה")
     file.delete()
    }
    runOnUiThread { progress.progress=100; status.text="ההורדה הושלמה ונשמרה בתיקייה" }
   } catch(e:Exception) { runOnUiThread { status.text="שגיאה: ${e.message}" } }
   finally { runOnUiThread { downloadButton.isEnabled=true } }
  }
 }
}
