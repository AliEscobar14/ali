# Hafiflet

Düşük özellikli Android telefonlar (öncelikle **Redmi 9A**) için [Shizuku](https://shizuku.rikka.app/) tabanlı optimizasyon uygulaması.
Root gerekmez. "RAM temizleyici" gibi sahte hızlandırma yapmaz. Sadece sistemin gerçekten yük bindiren kısımlarını kapatır.

## Özellikler

- **Gereksiz sistem uygulamaları:** MIUI reklam ve analiz servisleri, GetApps, Google ve Facebook önyüklü uygulamaları devre dışı bırakır. Liste "Güvenli" ve "Dikkat" olarak ikiye ayrılmıştır. Her işlem **Geri yükle** ile geri alınabilir.
- **Arka plan kısıtlama:** Seçilen uygulamaların arka planda çalışmasını engeller (`RUN_ANY_IN_BACKGROUND`).
- **Hızlı ayarlar:** Animasyon hızını (kapalı / 0.5x / 1x) ve arka plan Wi-Fi ile Bluetooth taramasını ayarlar.
- **Uygulamaları derle:** `bg-dexopt-job` ile uygulamaları önceden derler, böylece açılışları hızlanır.
- **Sağlık paneli:** RAM, depolama, pil sıcaklığı ve RAM'i en çok kullanan işlemler.

## Kurulum (Redmi 9A)

1. **Geliştirici seçeneklerini aç:** Ayarlar → Telefon hakkında → *MIUI sürümü*'ne 7 kez dokun.
2. Ayarlar → Ek ayarlar → Geliştirici seçenekleri bölümünde **USB hata ayıklama**'yı aç. Komutlar "izin yok" hatası verirse **USB hata ayıklama (Güvenlik ayarları)**'nı da aç. Bunun için SIM kart ve Mi Hesabı gerekir.
3. [Shizuku](https://shizuku.rikka.app/download/)'yu kur ve başlat:
   - **Android 10** kurulu ise telefonu bilgisayara bağla. Shizuku'da **Bilgisayara bağlanarak başlatın → Komutu görüntüle** bölümündeki komutu **Kopyala/Gönder** ile bilgisayara aktar ve `platform-tools` klasöründe açtığın CMD'de çalıştır. Komut `adb shell /data/app/moe.shizuku.privileged.api-.../lib/arm/libshizuku.so` biçimindedir ve her telefonda farklıdır. Bu işlemi telefon **her yeniden başladığında** tekrarlaman gerekir.
   - **Android 11+** kurulu ise Shizuku'yu kablosuz hata ayıklama ile doğrudan telefondan başlatabilirsin.
4. Hafiflet'i kur, aç ve **İzin ver**'e dokun.

## APK'yı indirme

Her push'ta GitHub Actions bir APK derler. Repo'nun **Actions** sekmesinde → son "APK" çalıştırması → **hafiflet-apk** artifact'ını indir.

> Not: CI derlemeleri her seferinde farklı bir debug anahtarıyla imzalanır. Yeni sürümü kurmadan önce eskisini kaldırman gerekebilir.

## Derleme

```sh
./gradlew assembleRelease
```

Android SDK gerekir (Android Studio ile gelir). Projede AndroidX ya da Compose yok: arayüz sadece platform bileşenleriyle yazıldı. Böylece APK küçük ve bellek kullanımı düşük kalır.
