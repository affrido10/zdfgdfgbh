package com.paperscan.app

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var scansContainer: LinearLayout
    private lateinit var emptyState: TextView
    private lateinit var scanButton: Button
    private var pendingExportFile: File? = null

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scanResult?.pdf?.uri
            if (pdfUri == null) {
                showToast("Сканер не вернул PDF-файл")
            } else {
                saveScannedPdf(pdfUri)
            }
        } else if (result.resultCode != android.app.Activity.RESULT_CANCELED) {
            showToast("Не удалось получить результат сканирования")
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destination ->
        val source = pendingExportFile
        pendingExportFile = null
        if (destination != null && source != null) {
            copyToUri(source, destination)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(245, 247, 251)
        window.navigationBarColor = Color.rgb(245, 247, 251)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildScreen()
        refreshScans()
    }

    override fun onResume() {
        super.onResume()
        if (::scansContainer.isInitialized) refreshScans()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 251))
        }
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandMark = TextView(this).apply {
            text = "P"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(Color.rgb(47, 111, 237), 15)
        }
        brandRow.addView(brandMark, LinearLayout.LayoutParams(dp(48), dp(48)))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brandText.addView(textView("PaperScan", 23f, Color.rgb(23, 32, 51), true))
        brandText.addView(textView("СКАНЕР ДОКУМЕНТОВ", 10f, Color.rgb(112, 124, 145), true).apply {
            letterSpacing = 0.12f
            setPadding(0, dp(2), 0, 0)
        })
        brandRow.addView(brandText)
        page.addView(brandRow)

        val headline = textView("Бумага в PDF", 30f, Color.rgb(23, 32, 51), true)
        headline.setPadding(0, dp(30), 0, dp(7))
        page.addView(headline)
        page.addView(textView("Снимите документ или добавьте фото из галереи. Выравнивание, обрезка и обработка доступны в режиме сканирования.", 15f, Color.rgb(89, 101, 122), false).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
        })

        scanButton = Button(this).apply {
            text = "＋   Сканировать документ"
            textSize = 16f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(47, 111, 237), 16)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { startScanner() }
        }
        val scanParams = LinearLayout.LayoutParams(-1, dp(60)).apply {
            topMargin = dp(22)
        }
        page.addView(scanButton, scanParams)

        val note = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 16, Color.rgb(228, 233, 242))
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
        val noteTitle = textView("Что доступно", 14f, Color.rgb(23, 32, 51), true)
        note.addView(noteTitle)
        val features = textView("•  Импорт фото из галереи\n•  Автоматическое определение границ и коррекция перспективы\n•  Чёрно-белые и цветные фильтры\n•  Несколько страниц в одном PDF", 13f, Color.rgb(89, 101, 122), false)
        features.setLineSpacing(dp(5).toFloat(), 1f)
        features.setPadding(0, dp(7), 0, 0)
        note.addView(features)
        page.addView(note, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        val listHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val listTitle = textView("Мои документы", 20f, Color.rgb(23, 32, 51), true)
        listHeader.addView(listTitle, LinearLayout.LayoutParams(0, -2, 1f))
        listHeader.addView(textView("ЛОКАЛЬНО", 10f, Color.rgb(112, 124, 145), true).apply {
            letterSpacing = 0.10f
        })
        page.addView(listHeader, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
            bottomMargin = dp(12)
        })

        emptyState = textView("Пока нет сканов. Создайте первый документ — PDF появится здесь.", 14f, Color.rgb(112, 124, 145), false).apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(28), dp(20), dp(28))
            background = rounded(Color.WHITE, 15, Color.rgb(228, 233, 242))
        }
        page.addView(emptyState, LinearLayout.LayoutParams(-1, -2))

        scansContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(scansContainer, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    private fun startScanner() {
        scanButton.isEnabled = false
        scanButton.alpha = 0.75f
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scanButton.isEnabled = true
                scanButton.alpha = 1f
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { error ->
                scanButton.isEnabled = true
                scanButton.alpha = 1f
                showToast("Не удалось запустить сканер. Проверьте Google Play services.")
            }
    }

    private fun saveScannedPdf(sourceUri: Uri) {
        scanButton.isEnabled = false
        scanButton.alpha = 0.75f
        Thread {
            var saved: File? = null
            var failure: Exception? = null
            try {
                val directory = File(filesDir, "scans")
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException("Не удалось создать каталог документов")
                }
                val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                val file = File(directory, "PaperScan_${formatter.format(Date())}.pdf")
                val input = contentResolver.openInputStream(sourceUri)
                    ?: throw IOException("Не удалось прочитать PDF сканера")
                input.use { source -> file.outputStream().use { destination -> source.copyTo(destination) } }
                if (file.length() == 0L) throw IOException("Получен пустой PDF")
                saved = file
            } catch (e: Exception) {
                failure = e
            }
            runOnUiThread {
                scanButton.isEnabled = true
                scanButton.alpha = 1f
                if (saved != null) {
                    refreshScans()
                    showToast("PDF сохранён в PaperScan")
                    AlertDialog.Builder(this)
                        .setTitle("Документ готов")
                        .setMessage("PDF сохранён в приложении. Его можно открыть, экспортировать в папку устройства или отправить.")
                        .setPositiveButton("Экспортировать") { _, _ -> exportPdf(saved!!) }
                        .setNeutralButton("Отправить") { _, _ -> sharePdf(saved!!) }
                        .setNegativeButton("Позже", null)
                        .show()
                } else {
                    showToast("Не удалось сохранить PDF: ${failure?.localizedMessage ?: "неизвестная ошибка"}")
                }
            }
        }.start()
    }

    private fun refreshScans() {
        if (!::scansContainer.isInitialized) return
        scansContainer.removeAllViews()
        val directory = File(filesDir, "scans")
        val files = directory.listFiles { file -> file.isFile && file.extension.equals("pdf", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        emptyState.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        files.forEach { file -> scansContainer.addView(makeScanCard(file), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        }) }
    }

    private fun makeScanCard(file: File): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(Color.WHITE, 15, Color.rgb(228, 233, 242))
        }
        val fileRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pdfBadge = TextView(this).apply {
            text = "PDF"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(47, 111, 237))
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(235, 241, 255), 9)
        }
        fileRow.addView(pdfBadge, LinearLayout.LayoutParams(dp(46), dp(46)))
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        val name = textView(file.nameWithoutExtension, 14f, Color.rgb(23, 32, 51), true)
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.MIDDLE
        details.addView(name)
        val dateText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(file.lastModified()))
        details.addView(textView("$dateText · ${humanSize(file.length())}", 12f, Color.rgb(112, 124, 145), false).apply {
            setPadding(0, dp(4), 0, 0)
        })
        fileRow.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(fileRow)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(actionText("Открыть") { openPdf(file) }, weightedActionParams())
        actions.addView(actionText("Экспорт") { exportPdf(file) }, weightedActionParams())
        actions.addView(actionText("Отправить") { sharePdf(file) }, weightedActionParams())
        actions.addView(actionText("Удалить", destructive = true) { confirmDelete(file) }, weightedActionParams())
        card.addView(actions, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(10) })
        return card
    }

    private fun openPdf(file: File) {
        val uri = fileUri(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showToast("На устройстве нет приложения для просмотра PDF. Используйте «Экспорт».")
        } catch (e: Exception) {
            showToast("Не удалось открыть файл PDF")
        }
    }

    private fun exportPdf(file: File) {
        pendingExportFile = file
        exportLauncher.launch(file.name)
    }

    private fun sharePdf(file: File) {
        try {
            val uri = fileUri(file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Отправить PDF"))
        } catch (e: Exception) {
            showToast("Не удалось подготовить PDF для отправки")
        }
    }

    private fun confirmDelete(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Удалить документ?")
            .setMessage("Файл «${file.name}» будет удалён из PaperScan.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                if (file.delete()) {
                    refreshScans()
                    showToast("Документ удалён")
                } else showToast("Не удалось удалить документ")
            }
            .show()
    }

    private fun copyToUri(source: File, destination: Uri) {
        Thread {
            var error: Exception? = null
            try {
                val output = contentResolver.openOutputStream(destination)
                    ?: throw IOException("Не удалось открыть место назначения")
                source.inputStream().use { input -> output.use { target -> input.copyTo(target) } }
            } catch (e: Exception) {
                error = e
            }
            runOnUiThread {
                if (error == null) showToast("PDF экспортирован")
                else showToast("Ошибка экспорта PDF: ${error?.localizedMessage ?: "неизвестная ошибка"}")
            }
        }.start()
    }

    private fun fileUri(file: File): Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    private fun actionText(label: String, destructive: Boolean = false, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (destructive) Color.rgb(190, 59, 63) else Color.rgb(47, 111, 237))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun weightedActionParams() = LinearLayout.LayoutParams(0, -1, 1f)

    private fun textView(text: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f КБ", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024.0))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
