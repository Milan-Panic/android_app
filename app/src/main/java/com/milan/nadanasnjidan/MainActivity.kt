package com.milan.nadanasnjidan

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var permissionButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var adapter: MemoriesAdapter

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            loadMemories()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recycler)
        emptyText = findViewById(R.id.emptyText)
        permissionButton = findViewById(R.id.permissionButton)
        progress = findViewById(R.id.progress)

        val dateFmt = SimpleDateFormat("d. MMMM", Locale.forLanguageTag("sr-Latn-RS"))
        findViewById<TextView>(R.id.title).text =
            getString(R.string.title_on_this_day, dateFmt.format(Calendar.getInstance().time))

        adapter = MemoriesAdapter(this, lifecycleScope) { memory ->
            // Otvori sliku u podrazumevanoj galeriji
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(memory.uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { startActivity(intent) }
        }

        val spanCount = 3
        recycler.layoutManager = GridLayoutManager(this, spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.isHeader(position)) spanCount else 1
            }
        }
        recycler.adapter = adapter

        permissionButton.setOnClickListener {
            permissionLauncher.launch(PhotoRepository.requiredPermissions())
        }
    }

    override fun onResume() {
        super.onResume()
        loadMemories()
    }

    private fun loadMemories() {
        if (!PhotoRepository.hasPermission(this)) {
            progress.visibility = View.GONE
            recycler.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
            emptyText.setText(R.string.need_permission)
            permissionButton.visibility = View.VISIBLE
            permissionButton.setText(R.string.grant_permission)
            return
        }

        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val memories = withContext(Dispatchers.IO) { PhotoRepository.findForToday(this@MainActivity) }
            progress.visibility = View.GONE
            adapter.submit(memories)

            val empty = memories.isEmpty()
            recycler.visibility = if (empty) View.GONE else View.VISIBLE
            emptyText.visibility = if (empty) View.VISIBLE else View.GONE
            if (empty) emptyText.setText(R.string.no_memories)

            // Na Androidu 14 sa delimičnim pristupom nudimo da se izabere još slika
            val partial = PhotoRepository.isPartialAccess(this@MainActivity)
            permissionButton.visibility = if (partial) View.VISIBLE else View.GONE
            if (partial) permissionButton.setText(R.string.allow_all_photos)

            // Pošto sada imamo dozvolu, osveži i widget
            WidgetUpdater.requestUpdate(this@MainActivity)
        }
    }
}
