# NöbetMaker

Tıbbi mikrobiyoloji asistanları için aylık nöbet listesini otomatik ve adil şekilde hazırlayan Android uygulaması. Tasarım: [docs/TASARIM.md](docs/TASARIM.md)

## Neler yapıyor
- **Liste üretimi:** art arda ve günaşırı yasağı, izinler, kişiye özel kurallar ve kesin istekler hiçbir zaman çiğnenmez. "Mümkünse" istekler, uzun tatil koruması (Perşembe kuralı) ve zor günlerin adil dağılımı ceza puanıyla dengelenir. Her seferinde 3 alternatif liste sunulur.
- **Kıdem eğrisi:** katsayılar asistanlık yılından hesaplanır; herkes aynı eğriden geçer (ilk yıllar 1,00 → 0,97, son yıl 0,82 → 0,78, uzatma 0,50 ve cuma, hafta sonu, tatil yok). Doğum izni süreden düşülür; ekip 5 kişinin altına inince son yıl indirimi yarıya iner. İsteyen kişi katsayısını elle girebilir.
- **Cumartesi (vizit) kıdem sırasıyla:** son yıla girmemiş herkes ayda bir cumartesi tutar; artanlar son yıldakilere sırayla gider (en uzun süredir tutmayan önce). Son yıla giren sıranın sonuna geçer, geçmişten borç yazılmaz. Son yıldakine aynı ay hem cumartesi hem pazar yazılmaz. Yeni başlayana ilk ay vizit yazılmaz.
- **Zor günler:** cuma, cumartesi ve pazar birlikte sayılır; hafta sonu tutan herkes ayda eşit sayıda tutar (çoğu ay 2). Fazla gün önce çömezlere, eksik gün önce kıdemliden; kimseye aynı ay üçü birden yazılmaz (zorunlu değilse). Cumartesi tutmayan kıdemli cuma ve pazar tutar. Denge kademe içinde tutulur. İstek ve izin zor gün sayısını değiştirmez, yalnızca günleri; değiştirirse uygulama sebebiyle yazar. Hoca kararı olan ay o kişi hafta sonu sırasına girmez. 31 Aralık'ı tutan mümkünse bir daha tutmaz. Perşembe ve Pazartesi (Salı dersi) herkese eşit döner; aynı kişiye ayda ikinci pazartesi neredeyse hiç yazılmaz. Son 6 ay hatırlanır. Aynı kişiye ayda 2 Cuma, 2 Cumartesi ya da 2 Pazar yazılmaz. Liste haftadan haftaya aynı sırayla tekrar etmez.
- **Adalet defteri:** bayram, yılbaşı ve resmi tatiller ilk resmi listeden (Nisan 2024) beri sayılır; her olay o gün nöbette olanlar arasında o günkü katsayıyla paylaştırılır, uzman günleri sayılmaz, arefe yarım gündür, 9 günlük tatil bütün olarak bayramdır. Tatil günleri sıraya girmez, böylece tatilde vizit olup olmadığının bilinmemesi hesabı etkilemez. Kişi nöbete başladığı günden itibaren sayılır; yeni gelen borçsuz başlar.
- **Nöbet sayıları ve hoca kararı:** kıdem eğrisi, aylık üst sınır; izin sayıyı düşürmez. Hoca bir kişinin sayısını söylerse "Hoca kararı" olarak girilir: o kişiye borç yazılmaz, artan ya da eksilen günler diğerlerine katsayıyla paylaşılır ve sonraki aylarda dengelenir (sayı dengesi Kasım 2026'dan beri resmi listeden hesaplanır). Kalıcı sayı için "ayda sabit sayı" kuralı var. **Kıdem kuralı:** aynı ay içinde kimse kendinden kıdemliden az tutmaz; artan nöbet önce en çömeze gider. Sayı ilk değiştirilince "Hoca kararı mı?" diye sorulur. Liste sayılarla uyuşmazsa takvimde uyarı ve yeniden oluşturma düğmesi çıkar.
- **Kesinleştirme:** liste kesinleşince değişiklikler yeni sürüm açar; yanlışlıkla kesinleştirildiyse "Taslağa geri al". Kesinleşmiş ayda "Kalan günleri yeniden oluştur" bugüne kadarki günleri korur. İstatistik ve denge her zaman geçerli resmi listeden hesaplanır.
- **Resmi / gayriresmi liste:** yayınlanan listede değişiklik yeni sürüm açar; aranızdaki değişimler ayrıca kaydedilir. Değişim asistanı kurallara uyan takasları önerir.
- **Her şey elle düzeltilebilir:** kapanmış aylar dahil herhangi bir günün resmi kaydı (yeni sürüm açılır), gerçekte tutan, "Uzman tuttu", gün türü (bayram, arefe, idari izin, resmi tatil, yılbaşı, normal) ve vizit. Kurallar elle düzeltmede uyarır, engellemez.
- **Bayram ve tatil kayıtları:** Nisan 2024'ten beri her bayram, resmi tatil ve yılbaşı günü, kimin tuttuğu ve bugünkü ekibin dengesi (tutulan / payı); bir güne dokunup düzeltirsiniz, denge hemen yeniden hesaplanır (İstatistik ekranından).
- **Çıktılar:** hocaya Excel (Takvim, Liste, Özet, Döküm), WhatsApp için görsel, çıktı için PDF; modern tasarım, belirgin kişi renkleri. Resmi listeye **nöbet dökümü** eklenir: herkesin asistanlık yılı, katsayısı, sayısı, hafta sonları, cumartesi durumu ve gelecek ayın kıdemli cumartesi sırası, bayram/tatil dengesi ve kıdem eğrisi; "neden bana bu kadar" sorusunun cevabı listenin üstünde.
- **Geçmiş:** Nisan 2024 – Ekim 2026 resmi listelerinin tamamı (Excel çalışma planları ve kağıt listeler); uzmanların tuttuğu günler "Uzman" olarak görünür.
- **Telefon ve tablet:** geniş ekranda yan menü ve iki sütunlu takvim.
- **Veri:** tek bir JSON dosyası, otomatik yedekler, dışa/içe aktarma. İnternet izni yok.
- Nisan 2024 – Ekim 2026 resmi listeleri başlangıç verisi olarak gelir; eski sürümden güncellenince eksik aylar eklenir (girdiğiniz hiçbir şeyin üstüne yazılmaz).

## Kurulum
`app-release.apk` dosyasını telefona indirip açın. Android ilk kez sorduğunda dosyayı açan uygulamaya "bilinmeyen uygulamaları yükleme" izni verin.

## Güncelleme ve veri güvenliği
- Veri telefonda uygulamanın kendi klasöründe tek bir dosyada durur. Yeni sürüm eskisinin **üstüne kurulunca** veri olduğu gibi kalır; uygulamayı silmeyin (silmek veriyi de siler).
- Üstüne kurulabilmesi için her sürüm aynı imza anahtarıyla imzalanmalı (bkz. İmza).
- Uygulama içi önlemler:
  - Değişikliklerden önce otomatik yedek: son 10 yedek ve son 30 günün her biri saklanır (Ayarlar → Otomatik yedekler).
  - Yedekten dönme, yedek yükleme ve başlangıç verisine dönmeden önce her zaman yedek alınır.
  - Yeni sürüm eski veriyi güncellemeden önce dosyanın kopyasını "kurtarma" klasörüne koyar.
  - Dosya okunamazsa üstüne yazılmaz; kopyası saklanır, en yeni okunabilen yedek açılır.
- Telefon kaybına ya da yanlışlıkla silmeye karşı: Ayarlar → **Yedeği dışa aktar** ile Drive'a/e-postaya yedek alın (özellikle her güncellemeden önce). Yeni telefonda **Yedekten geri yükle** ile her şey geri gelir.

## Geliştirme
- Gereksinimler: JDK 17+, Android SDK (API 36).
- Derleme: `./gradlew :app:assembleRelease`
- Testler: `./gradlew :core:test :app:testDebugUnitTest`
  - `core` testleri kuralları, kıdem eğrisi, kota, defter, çözücü, nöbet dökümü, veri göçü ve geçmiş verinin kağıtla tutarlılığını sınar.
  - `app` testleri uygulamayı Robolectric ile telefon ve tablet boyutunda açar; her ekrandaki düğmelere basıp sonucun veriye yazıldığını kontrol eder (takvim, gün ayrıntısı, kapanmış ayda düzeltme, değişim, paylaşım, ay hazırlığı, liste üretimi, kişiler, istatistik, bayram ve tatil kayıtları, ayarlar, yedekler) ve `app/build/screens` altına ekran görüntüleri yazar.
- Modüller:
  - `core`: saf Kotlin; takvim, kurallar, kota, adalet defteri, çözücü, Excel ve görsel düzeni.
  - `app`: Jetpack Compose arayüzü, veri saklama, Android çizici ve paylaşım.

### İmza
Sürümlerin birbirinin üstüne veri kaybı olmadan kurulabilmesi için sabit bir anahtar (`app/signing/nobetmaker.keystore`) kullanılır. Repo herkese açık olduğu sürece bu anahtar repoya eklenmez; anahtar yoksa derleme hata vermez, yalnızca imza değişir (o durumda yedeği dışa aktarıp yeni kurulumda geri yükleyin).

### Yazı tipi
Uygulama ve çıktılar [Inter](https://github.com/rsms/inter) yazı tipini kullanır (SIL Open Font License 1.1; lisans metni `app/src/main/assets/licenses/Inter-OFL.txt`).
