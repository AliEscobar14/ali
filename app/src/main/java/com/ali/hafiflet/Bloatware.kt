package com.ali.hafiflet

/**
 * Redmi 9A'da (MIUI Global) önyüklü gelen uygulamalar.
 * [safe] = false olanlar kullanılıyor olabilir; listede "Dikkat" başlığı altında gösterilir.
 * Sistemin çalışması için gereken paketler (güvenlik, başlatıcı, yükleyici vb.) bilerek listede yok.
 */
class Bloat(val pkg: String, val name: String, val note: String, val safe: Boolean = true)

val BLOATWARE = listOf(
    // MIUI — veri toplama ve reklam
    Bloat("com.miui.analytics", "MIUI Analytics", "Kullanım verisi toplar, arka planda çalışır"),
    Bloat("com.miui.msa.global", "MIUI System Ads", "Sistem uygulamalarındaki reklamlar"),
    Bloat("com.miui.daemon", "MIUI Daemon", "Sistem verisi toplar, sürekli çalışır"),
    Bloat("com.miui.bugreport", "Hata raporu", "Xiaomi'ye hata raporu gönderir"),
    Bloat("com.miui.miservice", "Servis ve geri bildirim", "Destek uygulaması"),
    Bloat("com.miui.hybrid", "Hızlı uygulamalar", "Arka planda güncellenen mini uygulamalar"),
    Bloat("com.miui.hybrid.accessory", "Hızlı uygulamalar eklentisi", "Hızlı uygulamaların yardımcı paketi"),
    Bloat("com.miui.yellowpage", "Sarı sayfalar", "Arayan kimliği / işletme rehberi"),
    Bloat("com.miui.contentcatcher", "Content Catcher", "Ekrandaki içeriği izleyen arka plan servisi"),
    Bloat("com.miui.catcherpatch", "Content Catcher yaması", "Content Catcher eklentisi"),
    Bloat("com.mi.android.globalminusscreen", "Uygulama kasası", "Ana ekranın solundaki kartlar; sürekli veri yeniler"),
    Bloat("com.miui.personalassistant", "Uygulama kasası", "Ana ekranın solundaki kartlar; sürekli veri yeniler"),

    // MIUI — uygulamalar
    Bloat("com.xiaomi.mipicks", "GetApps", "Xiaomi uygulama mağazası, bildirim ve reklam gönderir"),
    Bloat("com.xiaomi.glgm", "Oyunlar", "Xiaomi oyun merkezi"),
    Bloat("com.miui.android.fashiongallery", "Duvar kâğıdı karuseli", "Kilit ekranı duvar kâğıtları indirir"),
    Bloat("com.miui.videoplayer", "Mi Video", "Video oynatıcı"),
    Bloat("com.miui.player", "Mi Müzik", "Müzik çalar"),
    Bloat("com.miui.fm", "FM Radyo", "Radyo uygulaması"),
    Bloat("com.miui.compass", "Pusula", "Pusula uygulaması"),
    Bloat("com.miui.weather2", "Hava durumu", "Arka planda konum ve veri kullanır"),
    Bloat("com.xiaomi.midrop", "ShareMe", "Dosya paylaşımı, reklam içerir"),
    Bloat("com.miui.huanji", "Mi Mover", "Eski telefondan veri taşıma"),
    Bloat("com.miui.touchassistant", "Hızlı top", "Ekranda yüzen kısayol topu"),
    Bloat("com.miui.phrase", "Sık kullanılan ifadeler", "Klavye ifade önerileri"),
    Bloat("com.miui.mishare.connectivity", "Mi Share", "Xiaomi cihazlar arası dosya paylaşımı"),
    Bloat("com.miui.newmidrive", "Mi Drive", "Xiaomi bulut sürücü"),
    Bloat("com.duokan.phone.remotecontroller", "Mi Uzaktan Kumanda", "Kızılötesi kumanda uygulaması"),
    Bloat("com.mipay.wallet.in", "Mi Pay", "Hindistan'a özel ödeme uygulaması"),
    Bloat("com.mi.globalbrowser", "Mi Tarayıcı", "Başka tarayıcın varsa kaldır", safe = false),
    Bloat("com.mi.android.globalFileexplorer", "Mi Dosya Yöneticisi", "Başka dosya yöneticin varsa kaldır", safe = false),
    Bloat("com.miui.notes", "Notlar", "Notların varsa önce yedekle", safe = false),
    Bloat("com.facemoji.lite.xiaomi", "Facemoji Klavye", "Kullandığın klavye buysa kaldırma", safe = false),
    Bloat("com.miui.cloudservice", "Xiaomi Cloud", "Mi Hesabı yedeklemesi kullanıyorsan kaldırma", safe = false),
    Bloat("com.miui.cloudbackup", "Xiaomi Cloud Yedekleme", "Mi Hesabı yedeklemesi kullanıyorsan kaldırma", safe = false),

    // Google
    Bloat("com.google.android.apps.youtube.music", "YouTube Music", "Müzik akışı"),
    Bloat("com.google.android.videos", "Google TV", "Film ve dizi mağazası"),
    Bloat("com.google.android.apps.tachyon", "Google Meet / Duo", "Görüntülü arama"),
    Bloat("com.google.android.music", "Play Müzik", "Kapatılmış eski müzik uygulaması"),
    Bloat("com.google.android.apps.podcasts", "Google Podcasts", "Kapatılmış podcast uygulaması"),
    Bloat("com.google.android.apps.subscriptions.red", "Google One", "Depolama aboneliği"),
    Bloat("com.google.android.apps.wellbeing", "Dijital Denge", "Kullanım takibi, arka planda sürekli çalışır"),
    Bloat("com.google.android.feedback", "Google Geri Bildirim", "Hata raporlama"),
    Bloat("com.google.ar.lens", "Google Lens", "Görsel arama"),
    Bloat("com.google.android.apps.googleassistant", "Google Asistan kısayolu", "Asistan uygulama simgesi"),
    Bloat("com.google.android.apps.photos", "Google Fotoğraflar", "Bulut yedekleme kullanıyorsan kaldırma", safe = false),
    Bloat("com.google.android.apps.docs", "Google Drive", "Drive kullanıyorsan kaldırma", safe = false),
    Bloat("com.google.android.youtube", "YouTube", "Tarayıcıdan da izlenebilir", safe = false),
    Bloat("com.google.android.gm", "Gmail", "E-posta kullanıyorsan kaldırma", safe = false),
    Bloat("com.google.android.googlequicksearchbox", "Google uygulaması", "Asistan ve ana ekran araması çalışmaz", safe = false),

    // Üçüncü taraf
    Bloat("com.facebook.appmanager", "Facebook App Manager", "Facebook'u arka planda günceller"),
    Bloat("com.facebook.services", "Facebook Services", "Arka plan servisi"),
    Bloat("com.facebook.system", "Facebook App Installer", "Facebook yükleyicisi"),
    Bloat("com.netflix.partner.activation", "Netflix etkinleştirme", "Önyüklü Netflix servisi"),
    Bloat("com.amazon.mShop.android.shopping", "Amazon", "Önyüklü alışveriş uygulaması"),
    Bloat("cn.wps.moffice_eng", "WPS Office", "Ofis uygulaması, reklam içerir", safe = false),
)
