# NöbetMaker

Tıbbi mikrobiyoloji asistanları için aylık nöbet listesini otomatik ve adil şekilde hazırlayan Android uygulaması. Tasarım: [docs/TASARIM.md](docs/TASARIM.md)

## Neler yapıyor
- **Liste üretimi:** art arda ve günaşırı yasağı, izinler, kişiye özel kurallar ve kesin istekler hiçbir zaman çiğnenmez. "Mümkünse" istekler, uzun tatil koruması (Perşembe kuralı) ve zor günlerin adil dağılımı ceza puanıyla dengelenir. Her seferinde 3 alternatif liste sunulur.
- **Sıra:** vizit (Cumartesi ve vizitli tatiller), Cuma, Pazar, Perşembe ve Pazartesi (Salı dersi) sırayla döner; son 3 ay hatırlanır. Aynı kişiye ayda 2 Cuma, 2 Cumartesi ya da 2 Pazar yazılmaz. Liste haftadan haftaya aynı sırayla tekrar etmez.
- **Adalet defteri:** bayram, yılbaşı ve resmi tatiller için 2023'ten beri. Kıdem katsayılarıyla ağırlıklandırılır.
- **Kota:** kıdem katsayısı, küsurat devri, aylık üst sınır; izin kotayı düşürmez.
- **Resmi / gayriresmi liste:** yayınlanan listede değişiklik yeni sürüm açar; aranızdaki değişimler ayrıca kaydedilir. Değişim asistanı kurallara uyan takasları önerir.
- **Çıktılar:** hocaya Excel (Takvim, Liste, Özet), WhatsApp için görsel, çıktı için PDF.
- **Veri:** tek bir JSON dosyası, otomatik yedekler, dışa/içe aktarma. İnternet izni yok.
- Kasım 2025 – Ekim 2026 kağıt listeleri başlangıç verisi olarak gelir.

## Kurulum
`app-release.apk` dosyasını telefona indirip açın. Android ilk kez sorduğunda dosyayı açan uygulamaya "bilinmeyen uygulamaları yükleme" izni verin.

## Geliştirme
- Gereksinimler: JDK 17+, Android SDK (API 36).
- Derleme: `./gradlew :app:assembleRelease`
- Testler: `./gradlew :core:test :app:testDebugUnitTest`
  - `core` testleri kuralları, kota, defter, çözücü ve geçmiş verinin kağıtla tutarlılığını sınar.
  - `app` testleri uygulamayı Robolectric ile açıp ana akışı dolaşır ve `app/build/screens` altına ekran görüntüleri yazar.
- Modüller:
  - `core`: saf Kotlin; takvim, kurallar, kota, adalet defteri, çözücü, Excel ve görsel düzeni.
  - `app`: Jetpack Compose arayüzü, veri saklama, Android çizici ve paylaşım.

### İmza
Sürümlerin birbirinin üstüne veri kaybı olmadan kurulabilmesi için sabit bir anahtar (`app/signing/nobetmaker.keystore`) kullanılır. Repo herkese açık olduğu sürece bu anahtar repoya eklenmez; anahtar yoksa derleme hata vermez, yalnızca imza değişir (o durumda yedeği dışa aktarıp yeni kurulumda geri yükleyin).
