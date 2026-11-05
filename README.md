# Na današnji dan

Android aplikacija (Kotlin) koja prikazuje fotografije iz galerije snimljene na današnji datum prethodnih godina, plus widget za početni ekran koji se sam osvežava svake noći oko 00:01.

## Pokretanje

1. Instaliraj [Android Studio](https://developer.android.com/studio) (Ladybug ili noviji).
2. **File → Open** → izaberi folder `NaDanasnjiDan`. Sačekaj da se Gradle sync završi (prvi put skida zavisnosti, par minuta).
3. Na telefonu uključi **Developer options → USB debugging**, poveži telefon kablom.
4. Klikni zeleno ▶ **Run**. Aplikacija se instalira i otvara.
5. Dozvoli pristup fotografijama.
6. Widget: dugo pritisni početni ekran → **Widgeti** → **Na današnji dan** → prevuci na ekran.

APK za instaliranje bez kabla: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, pa prebaci `app/build/outputs/apk/debug/app-debug.apk` na telefon.

## Šta radi

- **Aplikacija**: mreža slika grupisanih po godinama ("2023 · Pre 3 godine · 12"). Tap otvara sliku u ugrađenom pregledniku preko celog ekrana; prevlačenjem se prelazi na ostale.
- **Widget**: slike se same smenjuju svake 2 sekunde (ViewFlipper, do 20 slika; svaka ima sopstveni klik), sa natpisom "Pre N godina". Tap na sliku otvara baš tu sliku u pregledniku. Redosled je izmešan i menja se svakog dana.
- APK se potpisuje stalnim ključem iz `keystore/`, da bi se nove verzije instalirale preko stare.
- **Ponoćno osvežavanje**: `AlarmManager.setAndAllowWhileIdle` za 00:01. Alarm se ponovo zakazuje posle restarta telefona i kad se promeni vreme ili vremenska zona.
- 28. februara u neprestupnoj godini prikazuju se i slike sa 29. februara.
- Datum se uzima iz `DATE_TAKEN` (EXIF), a ako ga nema, iz datuma dodavanja fajla.

## Struktura

| Fajl | Uloga |
|---|---|
| `PhotoRepository.kt` | Upit nad galerijom (MediaStore), filtriranje po danu/mesecu, umanjene slike |
| `MainActivity.kt` / `MemoriesAdapter.kt` | Ekran aplikacije |
| `OnThisDayWidget.kt` / `WidgetUpdater.kt` | Widget i njegovo crtanje |
| `MidnightScheduler.kt` | Alarm posle ponoći i receiver za restart |

## Napomene

- Minimalna verzija je Android 10 (API 29).
- Neki proizvođači (Xiaomi, Huawei, Samsung sa agresivnom štednjom) mogu da gase pozadinske alarme. Ako se widget ne osveži ujutru, u podešavanjima baterije stavi aplikaciju na "Bez ograničenja".
- Na Androidu 14+, ako izabereš "Dozvoli ograničen pristup", aplikacija vidi samo slike koje si izabrao. Dugme u aplikaciji nudi da daš pristup svima.
