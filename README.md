# Hafiflet

Düşük özellikli Android telefonlar (öncelikle **Redmi 9A**) için [Shizuku](https://shizuku.rikka.app/) tabanlı optimizasyon uygulaması.
Root gerekmez. "RAM temizleyici" gibi sahte hızlandırma yapmaz. Sadece sistemin gerçekten yük bindiren kısımlarını kapatır.

## Özellikler

**Panel**
- Canlı RAM, depolama ve pil durumu
- RAM'i en çok kullanan işlemler

**Optimize**
- **Animasyon hızı:** Kapalı / 0.5x / 1x seçenekleri.
- **Çözünürlük:** Ekran çözünürlüğünü %90 ya da %80'e düşürür. Yoğunluk da aynı oranda düşürüldüğü için yazı ve simge boyutları değişmez. Değişiklik 15 saniye içinde onaylanmazsa ekran kendiliğinden eski haline döner.
- **Reklam engelleyici DNS:** AdGuard ya da AdGuard Aile (özel DNS) ile reklamları tüm telefonda engeller.
- **Arka plan taramaları:** Wi-Fi ve Bluetooth arka plan taramasını kapatır.
- **Arka plan kısıtlama:** Seçilen uygulamaların arka planda çalışmasını engeller (`RUN_ANY_IN_BACKGROUND`). Uygulama arama özelliği vardır.
- **Önbellek temizliği:** Tüm uygulamaların önbelleğini temizler (`pm trim-caches`).
- **Uygulamaları derle:** Uygulamaları `bg-dexopt-job` ile önceden derler.
- **Gereksiz sistem uygulamaları:** MIUI, Google ve Facebook önyüklü uygulamalarını kapatır.

**Geçmiş**
- Yapılan her değişiklik, onu geri alacak komutla birlikte kaydedilir. Tek tek ya da **hepsi birden** geri alınabilir.

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

Android SDK gerekir (Android Studio ile gelir). Arayüz Material 3 (View tabanlı) ile yazıldı. Compose bilerek kullanılmadı, çünkü düşük RAM'li cihazlarda daha ağır çalışıyor.
