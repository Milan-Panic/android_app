package com.milan.nadanasnjidan

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.GregorianCalendar

/** Jedna fotografija snimljena na današnji dan neke ranije godine. */
data class Memory(val uri: Uri, val takenMillis: Long, val year: Int)

object PhotoRepository {

    /** Dozvole koje tražimo, zavisno od verzije Androida. */
    fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasPermission(ctx: Context): Boolean =
        requiredPermissions().any {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }

    /** Da li korisnik dao samo delimičan pristup (Android 14 "izaberi fotografije"). */
    fun isPartialAccess(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_IMAGES) !=
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Vraća sve slike iz galerije snimljene na isti dan i mesec kao [today],
     * ali u nekoj od prethodnih godina. Najnovije godine idu prve.
     */
    fun findForToday(ctx: Context, today: Calendar = Calendar.getInstance()): List<Memory> {
        if (!hasPermission(ctx)) return emptyList()

        val todayYear = today.get(Calendar.YEAR)
        val todayMonth = today.get(Calendar.MONTH)
        val todayDay = today.get(Calendar.DAY_OF_MONTH)

        // 28. februar u neprestupnoj godini prikazuje i slike sa 29. februara.
        val includeLeapDay = todayMonth == Calendar.FEBRUARY && todayDay == 28 &&
            !GregorianCalendar().isLeapYear(todayYear)

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val result = ArrayList<Memory>()
        val cal = Calendar.getInstance()

        ctx.contentResolver.query(collection, projection, null, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (c.moveToNext()) {
                // DATE_TAKEN je u milisekundama; ako ga nema (npr. screenshot bez EXIF-a),
                // koristimo DATE_ADDED (u sekundama).
                val taken = if (!c.isNull(takenCol) && c.getLong(takenCol) > 0) {
                    c.getLong(takenCol)
                } else {
                    c.getLong(addedCol) * 1000L
                }
                if (taken <= 0) continue

                cal.timeInMillis = taken
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH)
                val d = cal.get(Calendar.DAY_OF_MONTH)
                if (y >= todayYear || m != todayMonth) continue
                if (d != todayDay && !(includeLeapDay && d == 29)) continue

                val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                result += Memory(uri, taken, y)
            }
        }
        return result
    }

    /** Podaci o jednoj konkretnoj slici (za slučaj da nije na spisku za taj dan). */
    fun memoryFor(ctx: Context, uri: Uri): Memory? = try {
        val projection = arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
        ctx.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val taken = if (!c.isNull(0) && c.getLong(0) > 0) c.getLong(0) else c.getLong(1) * 1000L
            val year = Calendar.getInstance().apply { timeInMillis = taken }.get(Calendar.YEAR)
            Memory(uri, taken, year)
        }
    } catch (e: Exception) {
        null
    }

    /** Učitava umanjenu verziju slike (brzo, bez učitavanja originala u memoriju). */
    fun loadThumbnail(ctx: Context, uri: Uri, sizePx: Int): Bitmap? = try {
        ctx.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
    } catch (e: Exception) {
        null
    }

    /** "Pre 1 godinu", "Pre 3 godine", "Pre 5 godina", "Pre 21 godinu"... */
    fun yearsAgoText(yearsAgo: Int): String {
        val word = when {
            yearsAgo % 10 == 1 && yearsAgo % 100 != 11 -> "godinu"
            yearsAgo % 10 in 2..4 && yearsAgo % 100 !in 12..14 -> "godine"
            else -> "godina"
        }
        return "Pre $yearsAgo $word"
    }
}
