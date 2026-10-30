# NöbetMaker — Tasarım Dokümanı

> **Durum:** Taslak, onay bekliyor. Bu doküman konuşmalarda alınan kararları bir araya getirir. Onaylanmadan kod yazılmayacak.
> **Son güncelleme:** 23 Ekim 2026

## İçindekiler

1. Amaç ve kapsam
2. Ekip, kıdem ve katsayılar
3. Takvim ve gün zorluk puanı
4. Kurallar
5. Kota (kim kaç nöbet tutar)
6. Adalet defteri
7. İstekler
8. Liste üretimi (algoritma)
9. Resmi ve gayriresmi liste
10. Bayram planlama adımı
11. Aylık iş akışı
12. Ekranlar
13. Veri modeli
14. Çıktılar
15. Teknik mimari
16. Geçmiş verilerin aktarımı
17. Kapsam dışı / sonraki sürümler
18. Karar kaydı
19. Varsayılan olarak alınanlar

---

## 1. Amaç ve kapsam

Tıbbi mikrobiyoloji asistanlarının aylık nöbet listesini **olabildiğince adil** biçimde otomatik üreten, geçmişi saklayan ve elle düzeltmeye izin veren bir Android uygulaması.

- **Tek kullanıcı:** Uygulamayı yalnızca listeyi yapan kişi kullanır. Giriş ekranı, hesap ya da sunucu yoktur.
- **Tamamen çevrimdışı:** Bütün veri ve hesaplama telefonda kalır. Uygulama internet izni istemez.
- **Günde 1 nöbetçi.**
- **Dil:** Türkçe.

Elle hazırlanan listelerde görülen ve uygulamanın çözmesi gereken sorunlar:

- Liste her ay sıfırdan elle çiziliyor. Ay geçişleri, yani önceki ayın son günleri, elle kontrol ediliyor.
- Kimin hangi zor günü kaç kez tuttuğu bilgisi bir aydan ötekine taşınmıyor.
- İzin dönüşlerinde nöbetler 3'er gün arayla sıkışıyor (ör. Ağustos 18-21-24, Eylül 20-23-26-29).
- Liste yayınlandıktan sonra değişiklik yapmak zor ve bazen zincirleme değişim gerekiyor (Nisan'da önce 5↔12, ardından 13↔15).

---

## 2. Ekip, kıdem ve katsayılar

Kıdem sırası kişi kartında elle tutulur, en kıdemliden en çömeze doğru. Sıra herhangi bir tarihten türetilmez. Başka hastaneden geçiş yapan bir asistan bölüme sonradan katılmış olsa bile kıdemde önde olabilir. Örneğin Yaren bölüme Kenan ve Fatma'dan sonra katıldı ama daha eski asistan olduğu için kıdemde en önde.

Her kişinin iki katsayısı vardır:

- **Nöbet katsayısı:** Kişinin ay içinde kaç nöbet tutacağını belirler.
- **Zor gün (tatil) katsayısı:** Kişinin bayram, resmi tatil ve yılbaşından alacağı payı belirler; uygulamada "tatil" diye gösterilir. 0.4.4'ten beri hafta sonlarını belirlemez: cuma, cumartesi ve pazar hafta sonu tutan herkese eşit dağılır (6.1).

Katsayılar 0.4.0'dan itibaren **kıdem eğrisinden** gelir (2.3): asistanlık başlangıcı biliniyorsa her günün katsayısı o günkü asistanlık yılından hesaplanır. Kişi isterse katsayısını elle de girebilir; elle girilen katsayılar geçerlilik tarihiyle saklanır, böylece sonradan yapılan bir değişiklik geçmiş hesapları bozmaz.

### 2.1 Başlangıç değerleri

| Sıra | Kişi | Asistanlık başlangıcı | Kasım 2026'da | Nöbet | Zor gün | Kişiye özel kural |
|---|---|---|---|---|---|---|
| 1 | Yaren | 2021 (başka hastane; ay tahmini Nisan), bölüme Aralık 2023 | uzatma | 0.50 | — | Cumartesi, Pazar, resmi tatil, bayram ve arefe yok |
| 2 | Kenan | Ağustos 2023 (bölümün ilk asistanı) | 4. yıl 4. ay | 0.81 | 0.66 | — |
| 3 | Fatma | Ekim 2023 | 4. yıl 2. ay | 0.82 | 0.67 | — |
| 4 | Betül | Aralık 2023 | 3. yıl 12. ay | 0.97 | 0.94 | — |
| 5 | Şule | Ağustos 2024 | 3. yıl 4. ay | 0.98 | 0.96 | — |
| 6 | Oğuz | Aralık 2024 | 2. yıl 12. ay | 0.98 | 0.97 | — |
| 7 | Aslı | Ocak 2026 | 1. yıl 11. ay | 1.00 | 0.99 | — |

Eğri bulunmadan önce elle girilen değerler (Kenan 0.80/0.65, Fatma 0.90/0.70, Betül 0.97/0.94, Şule 0.98/0.96, Oğuz 0.99/0.98, Aslı 1.00) zaten asistanlık yılına göre gidiyordu; eğri bunu kurala bağladı. Fatma Kenan'dan iki ay çömez olduğu için katsayısı çok az yüksektir (başlangıcı 0.4.1'de Eylül'den Ekim 2023'e düzeltildi).

- Betül, Şule, Oğuz ve Aslı arasındaki fark bilerek çok küçük tutuldu. Yıllık etkisi kabaca şu: Aslı, Betül'den 2 nöbet ve 1-2 zor gün fazla tutar.
- İyi günler (Perşembe) kıdeme bakılmadan, uygun olan herkese eşit dağıtılır.
- Yaren'in özel kuralı yalnızca Yaren'e aittir. Yaren ayrılınca bu kural kimseye otomatik geçmez.
- **Canay** 2027 yazında doğum izninden dönecek; kıdemi Aslı'dan öndedir. Doğum izni asistanlık süresinden düşülür (2.3). Yeni gelenin ilk ayında vizit yazılmaz (6.1).

### 2.3 Kıdem eğrisi

Herkes aynı eğriden geçer: çömezken çok, kıdemliyken az. Böylece 4 yılın toplamında herkes kabaca aynı yükü taşır.

| Asistanlık süresi | Nöbet katsayısı | Zor gün katsayısı |
|---|---|---|
| İlk 6 ay | 1.00 (bu dönemde nöbet yok) | 1.00 |
| 6. ay – 3. yıl sonu | 1.00'dan 0.97'ye yavaşça iner | 1.00'dan 0.94'e |
| Son yıl (4. yıl) | 0.82'den 0.78'e | 0.67'den 0.63'e |
| Uzatma (4 yıl dolduktan sonra) | 0.50 | cuma, cumartesi, pazar ve tatil yazılmaz (kendisi kesin isterse yazılır) |

- Eğri aylıktır: bir ay kıdem farkı çok küçük bir katsayı farkı yaratır.
- Doğum izni (uzun izin) asistanlık süresinden düşülür.
- **Küçük ekip:** Nöbet tutan kişi sayısı 5'in altına düşünce son yıl ve uzatma indirimi yarıya iner. Az kişiyle bu indirim birkaç çömezin sırtına binerdi.
- Değerler, asistanlık süresi ve küçük ekip sınırı Ayarlar'dan değiştirilebilir; eğri kapatılırsa elle girilen katsayılar kullanılır.
- Adalet defteri geçmiş günleri o günkü katsayıyla hesaplar; kıdemli olan birinin çömezken tuttuğu bayramlar bugünkü düşük katsayısıyla yeniden değerlendirilmez.
- Kişiler ekranında eğri ve herkesin üzerindeki yeri gösterilir; kişi kartında asistanlık yılı, son yıla giriş ve 4 yılın dolacağı tarih yazar (uzatma olursa ayrılış sonra girilir).

### 2.2 Kişi ekleme ve çıkarma

- Her kişi için dört tarih girilebilir; hiçbiri kıdem sırasını etkilemez:
  - **Asistanlık başlangıcı:** başka hastanede geçen süre dahil. Kıdem eğrisi bu tarihten hesaplanır (2.3). Varsayılan olarak bölüme katılışla aynıdır; biri girilince boş olan diğeri de aynı tarihi alır.
  - **Bölüme katılış:** kişi bu tarihten önce listeye yazılmaz (nöbete katılış boşsa). Adalet hesabını başlatmaz.
  - **Nöbete katılış:** bölüme gelen hemen nöbete girmiyor; çömez ilk 5–6 ay nöbet tutmaz (listelerde 6–8 ay). Uygulama asistanlık başlangıcı + 6 ayı önerir. Kişi bu tarihten önce listeye yazılmaz ve adalet hesabında bu tarihten itibaren ekipte sayılır.
  - **Ayrılış:** kişi bu tarihten sonra listeye girmez ve katsayılar kalanlar arasında kendiliğinden yeniden oranlanır.
- **Adalet hesabında ekipte sayılma:** nöbete katılış girilmişse o tarih; girilmemişse kişinin kayıtlardaki ilk nöbeti. Hiç kaydı olmayan kişi (yeni gelen ya da izinden dönen) **borçsuz başlar**: kayıtların bittiği günün ertesinden itibaren sayılır. Bölüme katılış tek başına yetmez; yoksa nöbete başlamadan önceki bayramlar "tuttu mu tutmadı mı" diye borç yazardı. 0.3'teki "kendinden çömezin ilk nöbetine bak" çıkarımı kaldırıldı; geçişle ya da izinden dönen kıdemlilerde yanlış sonuç veriyordu.
- Bir kişi ekipte kalıp nöbetten çıkabilir (Yaren için bu ihtimal var). Bu durumda kişiye bir **nöbet muafiyeti** dönemi girilir (bkz. 5.3). Kişi listeye girmez, payı kalanlara katsayılarına göre dağılır, borç yazılmaz. Geçmişi ve istatistiği yerinde durur.
- Ayrılan kişinin geçmişi silinmez. İstatistikte ve geçmiş özel günlerde görünmeye devam eder ama adalet hesabına girmez.
- Ekipte artık bulunmayan ama geçmiş kayıtlarda adı geçen kişiler "ayrılmış kişi" olarak eklenebilir.

---

## 3. Takvim ve gün zorluk puanı

### 3.1 Gün nitelikleri

Her takvim gününün şu nitelikleri vardır:

- **Tatil türü:** yok · hafta sonu · resmi tatil · bayram · arefe (yarım gün) · idari izin/köprü · özel gün (yılbaşı gibi)
- **Vizit:** var · yok · belirsiz. Belirsiz günler planlama sırasında **var** sayılır, yani en kötü durum esas alınır. Gün geçtikten sonra vizitin olup olmadığı işaretlenir ve defter kendini düzeltir.
- **Ek puan:** elle verilen ekstra zorluk (ör. 31 Aralık için +1.0).
- **Not**

Varsayılanlar:

- Cumartesi: vizit **var** (her zaman).
- Pazar: vizit **yok**.
- Sabit resmi tatiller uygulamada hazır gelir: 1 Ocak, 23 Nisan, 1 Mayıs, 19 Mayıs, 15 Temmuz, 30 Ağustos, 29 Ekim. 28 Ekim öğleden sonrası yarım gündür.
- Ramazan ve Kurban bayramı tarihleri (arefe ve bayram günleri) yerleşik bir tablodan gelir ve elle düzeltilebilir. İdari izin ve köprü günleri elle eklenir.
- Hafta içine veya Pazar'a denk gelen resmi tatil ve bayram günlerinde vizit varsayılan olarak **belirsiz**dir.
- 1 Ocak: vizit **var** (her yıl kesin oluyor; yine de gün bazında değiştirilebilir).
- 31 Aralık: "Yılbaşı" özel günüdür, ek puanı +1.0.

### 3.2 Zorluk puanı formülü

Puan elle doldurulan bir tablodan değil, aşağıdaki sorulardan hesaplanır. Burada "tatil" hafta sonu, resmi tatil, bayram ve idari izin demektir. Arefe yarım tatil sayılır, yani ilgili bileşenler yarıya iner.

| Bileşen | Puan |
|---|---|
| Temel nöbet | 1.0 |
| Nöbet günü tatil mi? (kişinin tatil günü gidiyor) | +0.5 |
| O gün vizit var mı? | +1.0 |
| Ertesi gün tatil mi? (nöbet ertesi izin boşa gidiyor) | +0.5 |
| Ertesi gün iş günü, ondan sonraki gün tatil mi? (uzun hafta sonu) | −0.3 |
| Elle ek puan | +x |

Örnek sonuçlar:

| Gün | Puan |
|---|---|
| Pazartesi–Çarşamba | 1.0 |
| Perşembe | 0.7 |
| Cuma | 1.5 |
| Pazar | 1.5 |
| Cumartesi (vizitli) | 3.0 |
| 29 Ekim 2026 (vizitli Perşembe) | 2.2 |
| Bayram ortası, vizitli | 3.0 |
| Bayram ortası, vizitsiz | 2.0 |
| 31 Aralık 2026 (Perşembe, ertesi gün 1 Ocak) | 2.5 |
| 1 Ocak 2027 (Cuma, vizitli) | 3.0 |

- Bileşen değerlerinin hepsi Ayarlar'dan değiştirilebilir.
- Formül, nöbetin ertesi günü herkesin izinli olduğunu varsayar.
- Bu yöntemin avantajı, yeni bir tatil ya da köprü için ayrıca tanım yapmaya gerek kalmamasıdır. Günü "tatil" ve "vizit var" diye işaretlemek yeterlidir.

### 3.3 Gün kategorileri

Her gün tek bir kategoriye girer. Bir gün birden fazla kategoriye uyuyorsa şu öncelik sırası geçerlidir:

**Yılbaşı › Bayram (arefe dahil) › Resmi tatil (28 Ekim yarım günü dahil) › Cumartesi › Pazar › Cuma › Perşembe › Hafta içi**

| Grup | Kategoriler | Hafıza |
|---|---|---|
| Sıralı (bkz. 6.1) | Vizit (Cumartesi ve vizitli tatil), Cuma, Pazar, Perşembe, Pazartesi | Son 3 ay |
| Uzun hafızalı | Bayram, Resmi tatil, Yılbaşı | 2023'ten beri |
| Dengelenmeyen | Salı, Çarşamba | — (yalnızca kotayı doldurur) |

### 3.4 Nöbet saati

Aralık 2025 ve Ocak 2026 kağıtlarının altındaki saat hesabı şu kurala uyuyor:

- Nöbet süresi iş gününde 16 saat, tatil gününde (hafta sonu, resmi tatil, bayram) 24 saattir.
- Ertesi gün iş günüyse, nöbet ertesi izin nedeniyle 8 saat düşülür.

| Gün | Saat |
|---|---|
| Pazartesi–Perşembe | 8 |
| Cuma | 16 |
| Cumartesi | 24 |
| Pazar | 16 |
| Hafta içi resmi tatil, ertesi gün iş günü (ör. 1 Ocak 2026 Perşembe) | 16 |
| Tatil, ertesi gün de tatil (ör. bayram ortası) | 24 |
| İş günü, ertesi gün tatil (ör. 31 Aralık) | 16 |
| Arefe ve 28 Ekim (nöbet öğlen başlar, ertesi gün tatil) | 20 |

- **Doğrulama:** Aralık 2025'te altı kişinin saati de bu kuralla birebir tutuyor. Örneğin Betül 7 nöbet tutmuş: 8+24+8+16+16+8+8 = 88 saat.
- Saatler her kişi için ay bazında iki ayrı şekilde hesaplanır: resmi listeye göre ve gerçekte tutulan nöbetlere göre. Ocak 2026 kağıdında saatler değişimlere göre düzeltilmiş.
- Saat ile zorluk puanı farklı şeyleri ölçer. Örneğin Perşembe 8 saattir ama en iyi gündür. Saat **yalnızca bilgi içindir**, adalet hesabına girmez (K23).
- Saat bileşenleri (iş günü 16, tatil 24, arefe 20, nöbet ertesi düşüm 8) Ayarlar'dan değiştirilebilir.

---

## 4. Kurallar

### 4.1 Sert kurallar (resmi listede asla çiğnenmez)

| Kod | Kural |
|---|---|
| S1 | Her güne tam olarak 1 nöbetçi yazılır. |
| S2 | Art arda nöbet yok. |
| S3 | Günaşırı nöbet yok. S2 ile S3 birlikte şu anlama gelir: iki nöbet arasında en az 3 gün olur (ayın 1'inde tutan en erken 4'ünde tutar). Ay geçişlerinde önceki ve sonraki ayın günleri de kontrol edilir. |
| S4 | Nöbet tutulmayan bir dönemde olan kişiye nöbet yazılmaz (yıllık izin, kongre/eğitim, rapor, halk sağlığı rotasyonu, doğum izni vb.; bkz. 5.3). |
| S5 | Kişiye özel kalıcı kurallar uygulanır (Yaren: Cumartesi, Pazar, resmi tatil, bayram ve arefe yok). Bu kurallar geçerlilik tarihlidir. |
| S6 | Bir kişi ayda en fazla 6 nöbet tutar. |
| S7 | Ay kotası tutturulur (bkz. 5). Çözüm bulunamazsa uygulama kota değişikliği önerir (bkz. 8.6). |
| S8 | Elle kilitlenmiş atamalar değiştirilmez. |
| S9 | "Kesin" öncelikli istekler karşılanır (bkz. 7). |

Gayriresmi listede bu kuralların hiçbiri engellemez, yalnızca **uyarı** verir.

### 4.2 Yumuşak kurallar (çiğnenebilir, ceza puanı yazar)

Aşağıdaki ağırlıklar başlangıç değerleridir. Hepsi Ayarlar'dan değiştirilebilir.

| Kod | Kural | Ceza |
|---|---|---|
| Y1 | **Uzun tatil koruması** (genelleştirilmiş Perşembe kuralı). Nöbetin ertesi günü iş günüyse ve hemen arkasından tatil başlıyorsa, o kişiye o tatil bloğunda nöbet yazılmaz. Normal bir Perşembe için bu "aynı hafta Pazar yok" demektir, çünkü Cuma ve Cumartesi zaten S2/S3 ile kapalıdır. 29 Ekim gibi tatile denk gelen Perşembeler ve bayramdan önceki günler de kendiliğinden kapsanır. Kural ay geçişini de aşar: 29 Ekim'i tutan kişi 1 Kasım Pazar'a yazılmaz. | 80 |
| Y2 | "Mümkünse" öncelikli istek karşılanmadı | 60 |
| Y3 | "Sıkışırsa önemli değil" öncelikli istek karşılanmadı | 10 |
| Y4 | Vizit, Cuma, Pazar ve Perşembe sırası (bkz. 6.1) | 60 × sapma² |
| Y4a | Pazartesi sırası (Salı dersini hep aynı kişi kaçırmasın; en kolay esneyen kural) | 3 × sapma² |
| Y5 | Uzun hafızalı kategori dengesi (bkz. 6) | 30 × sapma² |
| Y6 | Yayılım: iki nöbet arasında 3 gün var | 15 |
|  | Yayılım: iki nöbet arasında 4 gün var | 5 |
| Y7 | Aynı kişi aynı ay içinde Pazartesi–Perşembe arasında aynı güne 2. kez yazıldı | 5 |
| Y8 | Aynı kişi aynı bayramda 2. güne yazıldı | 40 |
| Y9 | **Aynı kişiye ayda 2. Cuma, 2. Cumartesi ya da 2. Pazar.** Neredeyse sert kuraldır; yalnızca başka çıkış yoksa çiğnenir. | 400 (fazla gün başına) |
| Y10 | Sıra tekrarı: ard arda iki gün, ay içinde aynı ikiliyle yeniden dizildi (ör. 2-3 ve 9-10'da Aslı → Oğuz). Liste haftadan haftaya aynı sırayla tekrar etmesin diye. | 8 (tekrar başına) |

Y4'teki sapma, ayın sırasında beklenen gün sayısından fark; Y5'teki sapma gün birimiyle ölçülür (bkz. 6).

---

## 5. Nöbet sayısı (kota: kim kaç nöbet tutar)

### 5.1 Hesap

1. Ayın gün sayısına N denir.
2. Her kişinin **etkin katsayısı** hesaplanır: nöbet katsayısı × (1 − kotayı düşüren dönem günleri / N). Ayın tamamında ekipte olmayan kişi için de (ay ortasında başlayan ya da ayrılan) aynı oranlama yapılır.
3. **Ham hak:** hak = N × etkin katsayı / Σ etkin katsayılar.
4. Önceki aylardan devreden **sayı dengesi** ham hakka eklenir (5.4): payından az tutan için +, fazla tutan için −. Ayda en fazla ±2 düzeltilir.
5. **Yuvarlama:** Toplam N olacak şekilde en büyük kalan yöntemiyle yapılır. Eşitlik olursa fazla nöbet çömeze gider. Kıdem kuralı (5.5) burada uygulanır: kimse kendinden kıdemliden az tutmaz.
6. Yuvarlamada kalan küsurat da sayı dengesine girer ve sonraki aya devreder. Örneğin Yaren'in hakkı 2.5 ise bir ay 2, bir ay 3 nöbet tutar.
7. **Katsayı:** Kıdem eğrisinden (2.3) ya da kişinin elle girdiği değerden gelir; her gün o günün katsayısı kullanılır.
8. **Sınırlar:** Üst sınır 6'dır (S6). Ekip küçülürse (ör. Eylül 2027'den sonra 4 kişi) kişi başı 7–8 nöbet düşer; bu durumda üst sınır Ayarlar'dan artırılır. Ayrıca fiziksel sığma kontrol edilir: kişinin uygun günlerine en az 3 gün arayla en fazla kaç nöbet sığabileceğine bakılır. Sınırı aşan kısım o ay diğerlerine dağıtılır. Üst sınırdan taşan kısım için borç yazılmaz; izin yüzünden sığmayan nöbet ise sayı defterinde kişinin kendi farkı olarak kalır ve sonraki aylarda telafi edilir (5.4, 0.4.5).
9. Uygulama sayıları **önerir** (uygulamada "Nöbet sayıları" sekmesi). Liste yapan, bir kişinin sayısını ±1 değiştirdiğinde o kişi **elle sabitlenir** ve kalan günler diğerlerine yukarıdaki adımlarla yeniden dağıtılır; toplam N korunur. Örnek: Kasım 2026'da Yaren 0'a indirilince 2 günü Fatma ve Aslı'ya geçer. Sabitleme kişi bazında kaldırılabilir ya da "Öneriye dön" ile hepsi sıfırlanır. Hoca kararıyla sabitlenen kişiye borç yazılmaz (5.4).
   - Hocalar bir kişi için sayı söylerse o ay elle sabitlenir. Kalıcıysa kişiye **"Ayda sabit nöbet sayısı"** kuralı girilir; her ay o sayıyla gelir, kural kalkınca öneriye döner.
   - Sayısı düşen kişinin Cuma/Cumartesi/Pazar sırası değişmez; düşen nöbet hafta içinden gelir. Böylece diğerleri onun yükünü iki kez (hem sayı hem hafta sonu) taşımaz.

Doğrulama:

- **Yaren varken, 31 günlük ay:** Yaren ≈2.5, Kenan ≈4.0, Fatma ≈4.5, Betül/Şule/Oğuz/Aslı ≈4.9–5.05. Ekim 2026'nın elle hazırlanan listesi (2-4-5-5-5-5-5) bununla uyumlu.
- **Yaren ayrılır ya da nöbetten çıkarsa:**
  - 30 günlük ay: Kenan ≈4.3, Fatma ≈4.8, çömezler ≈5.2–5.3.
  - 31 günlük ay: Kenan ≈4.4, Fatma ≈4.9, çömezler ≈5.3–5.5.
  - Yaren zaten hafta sonu ve tatil tutmadığı için Cumartesi, Pazar ve tatil dağılımı değişmez. Fark hafta içi ve Cuma günlerine yansır.

### 5.4 Nöbet sayısı dengesi ve hoca kararı (0.4.2)

Hocalar bir ay için birinin sayısını söyleyebilir ("Yaren bu ay 1, sınavı var"; sonraki ay "Yaren 4"). Bu düzeni bozmamalı ve kimseye haksızlık olmamalı.

- **Hoca kararı:** Nöbet sayıları sekmesinde bir kişinin sayısı ilk kez değiştirilince uygulama sorar: "Hoca kararı mı?" → [Hoca kararı] / [Biz değiştirdik] (0.4.3). Hoca kararı olan kişiye o ay için borç ya da alacak yazılmaz. "Ayda sabit nöbet sayısı" kuralı da hoca kararı sayılır. İşaret sonradan kişinin altındaki "Hoca kararı" düğmesiyle değiştirilebilir.
- **Bizim düzeltmemiz:** "Hoca kararı" kapatılırsa sabitleme bizim düzeltmemiz sayılır; kişinin payından fazla ya da eksik tuttuğu sonraki aylarda ona geri yansır.
- **Kalan günler:** Hoca kararıyla artan ya da eksilen günler diğerlerine nöbet katsayısıyla paylaştırılır. Yuvarlamada fazladan gün alan sonraki aylarda daha az alır; böylece "artanı hep Aslı'ya verdik" durumu birikmez.
- **Hesap (sayı defteri):** Kayıt tutulmaz; her seferinde resmi listeden hesaplanır. Her ay: asistanların tuttuğu günlerden hoca kararı olan kişilerin tuttukları düşülür, kalan günler diğerleri arasında o ayki nöbet katsayısıyla paylaştırılır. Denge = tutulan − pay. Elle yapılan her düzeltme (kapanmış ay dahil) kendiliğinden yansır.
- **İzinli ay:** O ay izni (yıllık izin, kongre, rapor) olan kişi farkını kendisi taşır; yakın paylı grubuna bölünmez (5.5). Ör. 3–23 Aralık izindeki Oğuz 5 yerine 4'e sığar, sonraki ay 1 fazla tutar; Şule ve Aslı'ya "eksik tuttu" yazılmaz (0.4.5).
- **Başlangıç:** Kasım 2026 (uygulamanın ilk listesi). Öncesi elle yapılmış eski düzen sayılır, dengeye girmez (Ayarlar'dan değiştirilebilir).
- **Gösterim:** Nöbet sayıları sekmesinde "önceki aylardan eksik 0,6" ya da "fazla 0,4"; istatistikte "Nöbet sayısı 9 / payı 9,4"; dökümde "Hoca kararı: Yaren 1; kalan günler diğerlerine katsayıyla dağıtıldı."
- **Liste ile sayılar uyuşmazsa** (ör. sayı liste oluşturulduktan sonra değişti) takvimde uyarı çıkar: "Liste nöbet sayılarıyla uyuşmuyor: Yaren 2 (1 olmalı)". Uyarıdaki düğme listeyi (kesinleşmişse kalan günleri) yeni sayılarla oluşturur.

### 5.5 Kıdem kuralı (0.4.3)

**Aynı ay içinde kimse kendinden kıdemliden az tutmaz; en kötü ihtimalle eşit tutar.** "Betül benden çömez, nasıl benden az tutuyor?" durumu olmaz. Sayı dengesinin devrettiği küsurat bu sırayı bozamaz.

1. **Havuz:** Ayın tamamında olağan katılan kişiler (izin dışı dönem, yeni başlama, ayrılma yok) kıdem sırasına dizilir. Hakkı (pay + devreden) kendinden çömezinkinden büyük olan komşular birleştirilir ve ortalama hakkı alır; birleşen blok yine sırayı bozuyorsa bir öncekiyle birleşir. Sonuç kıdemliden çömeze azalmayan haklardır.
2. **Yuvarlama:** Toplam N olacak şekilde en büyük kalan yöntemi. Aynı tam sayıdaki kişiler kendi toplamlarını eşit paylaşır; artan nöbet **önce en çömeze** gider.
3. **Üst sınır:** Sınıra takılanın fazlası, sırayı bozmayan birine verilir (kendinden çömezlerin hiçbirini geçmeyen).
4. **Sayı dengesinde gruplar:** Payları birbirine 0,5'ten yakın, kıdemce ardışık kişiler o ayın yuvarlama farkını **birlikte** taşır: grubun toplam sapması üyelere payları oranında yazılır. Kişiye yazılsaydı ertesi ay devreden sırayı yine bozmaya çalışırdı. Elle verilen (hoca kararı olmayan) sayı ve ayın bir kısmında olmayan kişi kendi sapmasını taşır.
5. **Bedeli:** Grup içinde artan hep çömeze gittiği için yakın kıdemliler arasında küçük bir kıdem farkı oluşur. 12 aylık benzetimde Kenan 48, Fatma 50, Betül 53 tuttu (payları 50 / 50,5 / 50,8); sırayı bozan ay 0, grubun toplam farkı 48 ayda en fazla 0,8.

Örnek (telefonda oluşturulan Kasım 2026 listesinden Aralık): 0.4.2 Fatma 5, Betül 4 veriyordu. Kıdem kuralıyla: Yaren 3, Kenan 4, Fatma 4, Betül 5, Şule 5, Oğuz 5, Aslı 5.

**Geçiş:** 0.4.2 elle girilen her sayıyı hoca kararı saymıştı. 0.4.3, sayı dengesi döneminde öneriden fazla verilen elle sayıları "bizim düzeltmemiz" yapar (Kasım'da Yaren'den artanı Aslı'ya vermemiz gibi); hocanın düşürdüğü sayı hoca kararı kalır.

### 5.2 İzin kotayı düşürmez

Yıllık izin ve kongre/eğitim izni kotayı **düşürmez**. Kişinin nöbetleri aynı ay içinde kalan günlere yerleştirilir. Nöbetlerin sıkışması kabul edilir, tek sınır art arda ve günaşırı yasağıdır (S2/S3). Yayılım kuralı (Y6) sıkışmayı mümkün olduğunca azaltır.

### 5.3 Dönem türleri

| Tür | Nöbet tutar mı? | Kota düşer mi? |
|---|---|---|
| Yıllık izin | Hayır | Hayır |
| Kongre / eğitim izni | Hayır | Hayır |
| Rapor | Hayır | Hayır (varsayılan; kayıt bazında değiştirilebilir) |
| Halk sağlığı rotasyonu | Hayır | Evet (orantılı) |
| Diğer dış rotasyonlar | **Evet** (nöbeti bölümde tutar; kayıt yalnızca bilgi içindir) | Hayır |
| Doğum izni / uzun izin | Hayır | Evet (orantılı) |
| Nöbet muafiyeti (kişi ekipte ama nöbet tutmuyor) | Hayır | Evet (tamamen; payı diğerlerine dağılır, borç yazılmaz) |
| Sınav (ör. uzmanlık sınavı) | Hayır | Evet (orantılı; borç yazılmaz) |
| Diğer | Kayıt bazında seçilir | Kayıt bazında seçilir |

Her dönem kaydı **kesin** ya da **belirsiz** olarak işaretlenebilir (ör. tarihi henüz netleşmemiş bir izin). Belirsiz dönem sert kural olarak değil, "mümkünse" öncelikli bir istek gibi uygulanır (Y2).

---

## 6. Adalet defteri

Defter, her kişinin her kategoride ne kadar yük taşıdığını izler. Sonraki listelerde geride kalan kişiye öncelik verilmesini sağlar.

- **Kaynak:** Defter yalnızca **resmi listeye** (en güncel resmi sürüme) ve geçmiş özel gün kayıtlarına bakar. Gayriresmi değişimler deftere girmez. Bunlar kişiler arasındaki anlaşmalardır ve sistemin bunları ayrıca telafi etmesi aynı şeyin iki kez hesaba girmesi olur.
- **Sıralı günler** (Vizit, Cuma, Pazar, Perşembe, Pazartesi) için bkz. 6.1. Uzun hafızalı kategorilerin ölçüsü farklıdır, aşağıda anlatılıyor.
- **Pay:** Her gün, o gün **uygun** olan kişiler arasında paylaştırılır.
  - Uygunluk şöyle belirlenir: kişi o gün ekipte olmalı, kalıcı bir kural (Yaren'in kuralı gibi) ya da **kotayı düşüren** bir dönem (halk sağlığı rotasyonu, doğum izni) yüzünden dışarıda kalmamalı.
  - Yıllık izin uygunluğu düşürmez, tıpkı kotayı düşürmediği gibi. Kişinin izindeyken kaçırdığı zor gün payı önce aynı ayın başka bir zor gününe verilir (elle hazırlanan listelerde de böyle oluyor). Sığmazsa defterde kalır ve sonraki listelerde telafi edilir.
  - Kişinin ekipte bulunmadığı dönemler hedefe girmez. Örneğin Aslı'dan 2025 bayramları için pay beklenmez. Burada nöbete katılış (yoksa ilk kayıtlı nöbet) esas alınır (2.2).
- **Sapma** = gerçekleşen − hedef. Kategorinin ortalama gün puanına bölünerek gün birimine çevrilir. Pozitif sapma kişinin fazla tuttuğunu, negatif sapma geride kaldığını gösterir.
- **Hafıza süreleri:**
  - Sıra hafızası (Vizit, Cuma, Pazar, Perşembe, Pazartesi): son **6 ay** (0.4.0; 3 ayda kaymalar birikiyordu: son 12 ayda bir kişi 13, diğeri 7 cumartesi tutmuştu).
  - Uzun hafıza (Bayram, Resmi tatil, Yılbaşı): **ilk resmi listeden (Nisan 2024) beri** tüm geçmiş.
- **Uzun hafızalı kategorilerde ölçü:** Her olayın (bir bayram tatili, bir resmi tatil, bir yılbaşı gecesi) günleri, o gün ekipte olan asistanlar arasında **o günkü zor gün katsayısıyla** paylaştırılır. Sapma = tuttuğu gün − payı.
  - Yalnızca asistanların tuttuğu günler paylaştırılır; 2024'te bayramların üçte ikisini uzmanlar tuttu, o günler kimseye borç ya da alacak yazmaz.
  - Bayram tatili bütün olarak sayılır: idari izinle 9 güne çıkan tatilde idari izin günleri ve bitişik hafta sonları da bayramdır. Arefe yarım gün sayılır (idari izne bağlanan arefe tam gün).
  - Tatil günleri yalnızca kendi kategorisinde sayılır, sıra gruplarına (6.1) girmez. Böylece tatilde vizit olup olmadığının bilinmemesi hesabı etkilemez.
  - Puan yerine gün sayısı kullanılır.
- Salı ve Çarşamba dengelenmez. Toplam nöbet sayısı zaten kota ile dengelenir.
- İstatistik ekranında ayrıca şunlar gösterilir: toplam nöbet (resmi ve gerçekte tutulan), toplam puan, istek karşılanma oranı.

Defter ayrı bir tabloda saklanmaz. Her seferinde resmi atamalardan, geçmiş özel gün kayıtlarından ve takvimden yeniden hesaplanır. Böylece kayıtlarla defter arasında tutarsızlık oluşmaz.

### 6.2 Bayram ve tatil kayıtları

Eski kağıt ve Excel listeleri bazı bayramlarda birbirini tutmuyor; vizit de çoğu zaman bilinmiyor. Bu yüzden bayram dengesinin dayandığı her gün görünür ve elle düzeltilebilir (İstatistik → "Bayram ve tatil kayıtları").

- **Denge tablosu:** Bugünkü ekip için Yılbaşı, Bayram ve Resmi tatil: durum ("dengede", "1 gün geride") ve altında tutulan / payı (gün). Tatil tutmayanlar (Yaren) "—".
- **Olaylar:** Yeniden eskiye her olay (ör. "Kurban Bayramı 2026 · 9 gün") ve günleri: gün türü, yarım gün, vizit, resmi kişi (ya da Uzman), takasla gerçekte tutan, türü elle değiştiyse işareti.
- **Bir güne dokununca:** resmi kayıt (hesaba girer; kapanmış ayda yeni sürüm), gerçekte tutan (takas; hesaba girmez), gün türü (normal, bayram, arefe, idari izin, resmi tatil, yılbaşı) ve vizit düzeltilir. Denge hemen yeniden hesaplanır.
- Gün türü değişince bayram günleri yakındaki bayramın olayına bağlanır; takvimdeki türe dönülünce elle kayıt kalkar (vizit işareti korunur).

### 6.1 Sıra (vizit, Cuma, Pazar, Perşembe, Pazartesi)

Elle hazırlanan listelerde zor günler sırayla döner. Cumartesi Ağustos 2026'dan beri kıdem sırasıyla gidiyor: Ağustos'ta (5 cumartesi) Oğuz, Betül, Şule, Fatma (o zaman 3. yılda) ve artan biri Kenan; Eylül'de (4) Şule, Aslı, Betül, Oğuz; Ekim'de (5) dört çömez ve artan biri Kenan. 0.4.1'den itibaren program da böyle dağıtır (K43).

- **Gruplar:**
  - **Vizit (Cumartesi):** Tatil olmayan vizitli gün. Tatil günleri vizitli olsa da yalnızca kendi kategorisinde sayılır (6). Geçmişte yalnızca kesinleşmiş vizit sayılır, planlamada belirsiz vizit var kabul edilir. **Kıdem sırasıyla** gider:
    - Son yıla girmemiş herkes ayda bir cumartesi tutar. Cumartesi sayısı bunlara yetmezse aralarında son 6 aya göre borç sırası işler (eşitlikte önce çömez).
    - Artan cumartesiler son yıldakilere **sırayla** gider: en uzun süredir cumartesi tutmayan önce. Son yıla giren kişi, son tuttuğu cumartesiye göre sıranın sonuna geçer; böylece geçiş yumuşaktır, kimseye geçmişten borç yazılmaz.
    - Son yıldakine aynı ay hem cumartesi hem pazar yazılmaz (sıkışırsa esner).
    - Cumartesi sırası en zor esneyen sıradır (ceza ağırlığı 150; Cuma/Pazar/Perşembe 60).
    - Neden: oransal paylaşımda (kıdemli 0,65) çömezler de 4 ayda bir boş kalıyor, kıdemliler 2 ayda bir tutuyordu; Kasım 2026'da Kenan ve Fatma'ya cumartesi + pazar, Şule ve Oğuz'a yalnızca pazar düşüyordu. Kıdem sırası hem bölümün alışkanlığıdır hem de herkes aynı aşamalardan geçtiği için adildir.
  - **İlk ay:** Nöbete yeni başlayana (ve uzun izinden dönene) ilk ayında vizit yazılmaz; o ayın vizitleri ona borç da yazmaz.
  - **Cuma** ve **Pazar** (vizitsiz), **zor gün kuralı (0.4.4):** cuma, cumartesi ve pazar birlikte "zor gün" sayılır ve hafta sonu tutan herkes ayda **eşit sayıda** tutar (çoğu ay 2). Fazla gün önce **çömezlere**, eksik gün önce **kıdemliden** düşer; aynı kademede kendi dengesi en geride olan önce. Kıdem kuralındaki (5.5) "artan önce çömeze" ile aynı mantık.
    1. Önce cumartesiler dağıtılır (yukarıdaki kıdem sırası).
    2. Kişi başına kaç zor gün düştüğü tek tek, en az tutana verilerek hesaplanır; eşitlikte çömez, sonra kademesinde geride olan, sonra en uzun süredir tutmayan. Ayın bir kısmında olmayanın payı tutabildiği zor gün oranında.
    3. Gün türü: kimseye aynı haftanın gününden ikincisi, cumartesi tutan kıdemliye pazar ve cumartesi tutana üçüncü zor gün yazılmaz. Ay buna yetmiyorsa (ör. Ocak 2027: 14 zor gün, 5 cumartesi, 5 pazar) en az esneyen dağılım seçilir: önce cumartesi tutan kıdemliye pazar, en son aynı günden ikincisi.
    4. Tarih: o gün olabilecek kişilerden seçeneği az olan önce, sonra o günün kendi sırası.
    - **Denge kademe içinde:** çömezin tuttuğu zor gün çömezler, son yıldakinin tuttuğu son yıldakiler arasında eşit paylaşılır (son 6 ay). Cuma ve pazar sıraları da böyle. Hafta sonu tutmayan (uzatma) zor gün düzenine girmez; uzatmada cuma da yazılmaz.
    - **İstekler ve izin sayıyı değiştirmez (K22, 0.4.5):** kişinin zor gün payı yalnızca ekipte olmasına bağlıdır; istek ve izin yalnızca hangi günleri tutabileceğini belirler. Sığmayan pay defterde kalır, sonraki aylarda telafi edilir. İstekler dağılımı yine de değiştirirse (ör. biri bütün cuma ve pazarlarını kapattı) uygulama istek olmadan kurulan sırayla karşılaştırıp seçeneklerde ve Kontrol sekmesinde yazar: "İstekler zor gün dağılımını değiştirdi: Aslı 3 → 1, Betül 2 → 3 (sebep: Aslı: … olmasın). Fark sonraki aylarda dengelenir."
    - **Hoca kararı olan ay (0.4.5):** sayısı hoca kararıyla belirlenen kişi o ay cumartesi, cuma ve pazar sırasına girmez, zor gün defterine de yazılmaz (sayı dengesindeki gibi). Gün başkasına sığmazsa yine yazılabilir. Önceden hoca bir kişiyi 2'ye düşürünce iki nöbeti de hafta sonuna düşebiliyordu.
    - **Yılbaşı (0.4.5):** 31 Aralık'ı daha önce tutan (resmi listeye göre) mümkünse bir daha tutmaz: 2024 Betül, 2025 Fatma. Çiğnenirse seçeneklerde "✗" satırı olarak görünür; ağırlığı Ayarlar'da.
    - Şimdiki 6 kişiyle: 12 zor günlü aylarda (Aralık, Şubat, Mart, Nisan, Haziran) herkes 2; Mayıs 2027'de (11) bir kıdemli 1; Ocak ve Temmuz 2027'de (14) iki çömez 3. Kıdemlinin ayrıcalığı: vizitli cumartesi yok, ayda bir nöbet az, fazla zor gün önce çömezde. Bölümün kendi listelerinde de böyleydi: Haziran–Kasım 2026'da Kenan 13, Fatma 12, Betül 13, Şule 14, Oğuz 13 zor gün.
    - Neden (0.4.4): 0.4.2–0.4.3'te kıdemlinin zor gün payı kıdem eğrisinden (0,66) geliyordu. 6 kişi ve 12 zor günle "üçü birden yok" kuralı herkese 2 demek; 0,66 ancak çömezlere üç gün yazılarak işliyordu. Aralık 2026'da Kenan'a yalnız pazar, Oğuz'a cuma + cumartesi + pazar düştü (cumalar ileriye bakmadan dağıtılıyordu).
    - Tatil günleri (bayram, resmi tatil, yılbaşı; ör. vizitli 29 Ekim) zor gün toplamına girmez, kendi uzun hafızasında sayılır (K37); iki kez sayılmaz.
  - **Perşembe:** herkese eşit döner.
  - **Pazartesi:** Salı günleri ders var ve Pazartesi nöbetçisi nöbet ertesi olduğu için dersi kaçırıyor. Herkese eşit döner. Salı tatilse o Pazartesi sıraya girmez. En kolay esneyen kuraldır (Y4a); ama aynı kişiye ayda ikinci pazartesi yüksek ceza alır (30).
  - İsteğiyle nöbet sayısı dolan kişiye sıradan ayrıca gün verilmez.
- **Borç:** Son 6 ayda her kişi için beklenen − tutulan. Beklenen, her günün uygun kişiler arasındaki payının toplamıdır.
- **Ayın sırası:** Ayın o gruptaki günleri sırayla dağıtılır. Her gün, borcu en büyük olana gider (borç yarım güne yuvarlanır); eşitlikte o günü en uzun süredir tutmayan, o da eşitse çömez seçilir. Vizitte önce son yıla girmemişler (aralarında borç, eşitlikte çömez), sonra son yıldakiler (en uzun süredir tutmayan önce).
  - Aynı kişiye ayda o günden ikincisi verilmez (bkz. Y9).
  - O günü isteyen biri varsa istek sıradan önce gelir. O günü istemeyen kişi mümkünse atlanır.
- Ortaya çıkan sıra her kişi için bir hedef sayı verir (ör. "Vizit: Kenan 1, Aslı 1, Fatma 1, Oğuz 1"). Liste üretimi bu hedeften sapmayı cezalandırır (Y4). Sıra kurallarla tutmuyorsa esner ve skor kartında gösterilir.

---

## 7. İstekler

### 7.1 Türler

| Tür | Örnek |
|---|---|
| Belirli gün(ler)i istemiyor | "9'u istemiyorum", "2-3-4 mümkünse istemiyorum", "haftaya salı sunumum var, pazartesiye dikkat" |
| Belirli bir aralığı istemiyor | "ilk hafta yazmayın", "14-21 Kasım istemiyorum" |
| Belirli bir tarihten önce ya da sonra istemiyor | "nöbetlerim 12'si sonrası olsun" |
| Belirli bir günü istiyor | "5 ve 21 uygunsa alabilir miyim", "22'sine gelirse güzel olur" |
| Zor güne gönüllü | "ilk cumartesiyi alabilirim" |
| Haftalık dağılım | "diğer haftalara birer tane" |
| Yoğunlaştırma | "nöbetlerim ilk 2 hafta yoğun olsun" |

### 7.2 Öncelik

- **Kesin:** Sert kural olur (S9). İzin ya da kongre gibi durumlar için kullanılır. Bu tür durumlar mümkünse doğrudan dönem olarak girilir.
- **Mümkünse:** Karşılanmazsa yüksek ceza yazar (Y2).
- **Sıkışırsa önemli değil:** Karşılanmazsa düşük ceza yazar (Y3).

Ek kurallar:

- Her istekle birlikte mesajın **orijinal metni** saklanır. Metin WhatsApp'tan yapıştırılır.
- İleri tarihli istekler (ör. Eylül'de Kasım için yazılan) ilgili ay açılınca otomatik olarak gelir.
- Gönüllü bir istek karşılanırsa o zor gün deftere normal şekilde yazılır. Böylece gönüllü olan kişi karşılığını sonraki aylarda alır.
- Yoğunlaştırma isteği ilgili aralıkta yayılım cezasını (Y6) kaldırır ve nöbetlerin o aralığa yerleşmesini tercih ettirir.

### 7.3 Çakışmalar

- Çakışan isteklerde kıdemin önceliği **yoktur**. Çakışmaları ekip kendi arasında çözer.
- Liste üretilmeden önce **Çakışmalar** ekranı açılır ve aynı anda karşılanamayacak istekleri gösterir. Örnek: "3 Ekim'i ne Şule ne Aslı istiyor; o gün uygun başka kimse yok."
- Liste yapan isteklerden birini esnetebilir. Çakışma çözülmeden liste üretilirse uygulama, taraflardan her birinin isteğinin karşılandığı ayrı alternatifler üretir. Seçimi liste yapan yapar.
- İstek karşılanma oranı istatistikte yalnızca bilgi olarak görünür, otomatik öncelik sağlamaz.

---

## 8. Liste üretimi (algoritma)

Problem küçüktür (yaklaşık 7 kişi × 31 gün). Telefonda, çevrimdışı çalışır. Hedef süre 2 saniyenin altıdır.

### 8.1 Hazırlık

- Takvim nitelikleri, gün puanları ve kategoriler hesaplanır.
- Sert kurallardan bir **kişi × gün uygunluk matrisi** çıkarılır. Bu matris dönemleri, kişiye özel kuralları, kesin istekleri, kilitli atamaları ve önceki ayın son günlerini hesaba katar.
- Kotalar (bölüm 5) ile defter hedefleri ve sapmaları (bölüm 6) hazırlanır.

### 8.2 Yerleştirme (geri izlemeli arama)

- Günler zorluk sırasına göre ele alınır: yılbaşı, bayram ve vizitli tatil → Cumartesi → Cuma → Pazar → Perşembe → hafta içi. Eşitlik olursa uygun aday sayısı en az olan gün önce yerleştirilir.
- Her gün için sert kurallara uyan adaylar, ceza puanını en az artırandan başlayarak denenir. Pratikte bu, o kategoride defterde en geride olan ve kotası en çok boş kalan kişi demektir. Eşitlik olursa çömez seçilir.
- Arama tıkanırsa geri dönülür ve başka aday denenir.

### 8.3 İyileştirme (yerel arama)

- Sert kuralları ve kotaları bozmayan hamleler denenir: iki günün nöbetçileri yer değiştirir (takas) ya da üç günün nöbetçileri döner (A'nın günü B'ye, B'ninki C'ye, C'ninki A'ya).
- Hamle toplam cezayı düşürüyorsa kabul edilir. Yerel bir minimumda takılmamak için kontrollü rastgelelik kullanılır (benzetimli tavlama).
- Sonunda bütün takas ve üçlü dönüşler tek tek denenir, iyileştiren kalmayana kadar uygulanır (cilalama).
- Bu adımlar 64 farklı başlangıçla, her biri 40.000 adımla tekrarlanır (0.4.2; 32 × 15.000 sıkışık aylarda bir isteği kaçırabiliyordu). Masaüstünde bir ay 1 saniyenin altında üretilir.

### 8.4 Alternatifler

Farklı başlangıçlarla birden çok çözüm üretilir. Birbirinden yeterince farklı olan en iyi **3** çözüm, her biri bir skor kartıyla sunulur:

- Toplam ceza
- Karşılanan ve karşılanmayan istekler (kim, hangisi)
- Ayın sıraları (ör. "Vizit sırası: Kenan, Aslı, Fatma, Oğuz") ve sıradan sapmalar
- Ayda 2. Cuma, Cumartesi ya da Pazar alan olursa uyarı
- Sıra tekrarları (aynı ikilinin ard arda yeniden dizilmesi)
- Uzun tatil koruması ihlalleri
- En sıkışık aralık (kişi ve günler)
- Defter dengesi (kategori bazında en büyük sapma)

Aynı girdi ve aynı başlangıç değeriyle her zaman aynı sonuç üretilir (tekrarlanabilirlik).

### 8.5 "Neden?" açıklaması

Uygulama her atama için şunları açıklar:

- Bu kişinin neden seçildiği.
- Diğer adayların neden elendiği (hangi sert kural).
- Diğer adayların neden daha pahalı olduğu (hangi yumuşak kural, kaç puan).

### 8.6 Çözüm bulunamazsa

Uygulama "çözülemedi" deyip bırakmaz:

- Çakışan kısıtları gösterir. Örnek: "15-21 arası 3 kişi izinli; günaşırı kuralıyla bu günler doldurulamıyor."
- Gevşetme seçeneklerini en ucuzdan başlayarak sıralar: bir kişinin kotasını ±1 değiştirmek, bir "kesin" isteği "mümkünse"ye indirmek, belirli bir kilidi kaldırmak.
- Art arda ve günaşırı kuralı **hiçbir zaman** gevşetme önerisi olarak sunulmaz.
- **Erken uyarı:** Dönem ya da istek girilirken, o günlerin kurallara uygun doldurulup doldurulamayacağı hemen kontrol edilir. Ekip 6 kişiye inerse aynı haftada 3 kişinin izinli olması listeyi çok sıkıştırır; uygulama bunu liste üretilmeden önce söyler.

---

## 9. Resmi ve gayriresmi liste

### 9.1 Resmi liste

- Algoritma üretir, liste yapan düzeltir. S1–S9'dan herhangi biri ihlal edildiği sürece kaydedilemez.
- Sürümlüdür, her revizyon yeni bir sürüm oluşturur. Hocaya giden Excel en güncel sürümden üretilir. Adalet defteri de en güncel resmi sürüme bakar.
- Durumları: **Taslak → Kesinleşti → Kapandı** (uygulamada düğmeler: "Listeyi kesinleştir", "Listeyi kapat", "Listeyi yeniden aç").
  - Yanlışlıkla kesinleştirilen liste **"Taslağa geri al"** ile taslağa döner: kesinleşmiş hali sürümlerde kalır, taslak onun kopyasıyla başlar; liste baştan oluşturulabilir, sayılar değişebilir.
  - Kesinleşmiş ayda sayılar değişebilir (hoca kararı); "Kalan günleri yeniden oluştur" bugüne kadarki günleri korur, kalanları yeni sayılarla oluşturur.
  - Kapanan liste yeniden oluşturulmaz; ama her şey elle düzeltilebilir (K45): gün ekranında ya da bayram kayıtlarında bir günün resmi kaydı düzeltilince yeni sürüm açılır, eski hali sürümlerde kalır, ay kapalı kalır.
  - İstatistik, sıra, sayı dengesi ve bayram dengesi her zaman geçerli resmi listeden hesaplanır; her değişiklik kendiliğinden yansır.
- Elle düzeltmede kurallar engel olmaz, yalnızca uyarır (taslakta "Kurala aykırı — yine de yaz"). Bir gün "Uzman tuttu (asistan yok)" diye işaretlenebilir; o gün asistanların bayram ve sıra hesabından düşer.

### 9.2 Gayriresmi liste

- Ekip içindeki değişimlerin resmi listenin üzerine işlendiği katmandır, yani nöbeti gerçekte kimin tuttuğunu gösterir.
- Burada kurallar yalnızca uyarı verir.
- Takvimde resmi kişi ile gerçekte tutan farklıysa kağıt listelerdeki gösterim kullanılır: **Oğuz (Betül)**, yani resmide Oğuz yazıyor, nöbeti Betül tuttu. Bu okuma Ocak 2026 kağıdındaki saat düzeltmesiyle doğrulandı: 1 Ocak'taki "Yaren (Fatma)" için Yaren'den 16 saat düşülmüş, Fatma'ya 16 saat eklenmiş.
- Her değişim kaydedilir: tarih, resmi kişi, tutan kişi, bağlı değişim (takas çifti), kayıt zamanı ve not.
- Gayriresmi liste hocaya gitmez. WhatsApp görseli ve "gerçekte kim tuttu" istatistiği için kullanılır.

### 9.3 Değişim asistanı

"X, D gününü vermek istiyor" bilgisi girildiğinde asistan şunları listeler:

1. D'yi kurallara uyarak doğrudan alabilecek kişiler.
2. Birebir takas seçenekleri: Y, D gününü alır; X de Y'nin bir gününü alır. İkisi için de kurallara uygun olan seçenekler gösterilir.
3. Doğrudan bir seçenek yoksa iki adımlı zincirler (Nisan'daki 5↔12 ve ardından 13↔15 gibi).

Her seçenek için kural kontrolü sonucu gösterilir. Kurallara uyan seçenekler önce, uyarı içerenler sonra listelenir.

---

## 10. Bayram planlama adımı

Bayramda kıdem önemlidir ama kesin bir kural yoktur. Bu yüzden bayram günleri tamamen algoritmaya bırakılmaz.

1. Bayram içeren bir ay hazırlanırken önce **Bayram planlama** ekranı açılır.
2. Uygulama uzun hafızalı deftere (2023'ten beri tutulan bayram geçmişine) ve zor gün katsayılarına (kıdeme) bakarak bir dağılım önerir. Mümkünse herkese en fazla 1 bayram günü düşer (Y8).
3. Ekranda her kişinin geçmişte tuttuğu bayramlar görünür: hangi bayram, hangi gün, vizitli olup olmadığı.
4. Liste yapan dağılımı düzeltip kilitler. Ayın geri kalanı bu kilitlere göre üretilir.

31 Aralık'ı içeren Aralık ayında da aynı ekran, yılbaşı geçmişiyle birlikte açılır.

---

## 11. Aylık iş akışı

1. **Ayı aç:** Takvim tatillerle birlikte hazır gelir. Vizit günleri ve ek puanlar işaretlenir.
2. **Dönemler:** İzin, kongre, rotasyon gibi dönemler girilir.
3. **İstekler:** WhatsApp mesajları yapıştırılarak girilir.
4. **Kota:** Öneri gözden geçirilir, gerekirse ±1 değiştirilir.
5. **Bayram planlama** (varsa).
6. **Çakışmalar** kontrol edilir.
7. **Üret:** 3 alternatif ve skor kartları gelir, biri seçilir.
8. **Düzelt:** Elle değişiklik yapılır, istenen günler kilitlenir, "kalanı yeniden üret" kullanılır.
9. **Kesinleştir:** Resmi Excel hocaya, görsel WhatsApp grubuna gönderilir. Yanlışlıkla kesinleştirildiyse "Taslağa geri al".
10. **Ay içi:** Gayriresmi değişimler ve değişim asistanı kullanılır, gerekirse resmi revizyon yapılır.
11. **Ay sonu:** Belirsiz vizitler işaretlenir, ay kapatılır, defter güncellenir.

---

## 12. Ekranlar

1. **Ana sayfa:** İçinde bulunulan ayın takvimi (resmi ve gayriresmi görünüm arasında geçiş yapılabilir) ve hızlı erişim düğmeleri.
2. **Ay hazırlama sihirbazı:** İş akışının 1–6. adımları.
3. **Alternatifler:** 3 liste ve skor kartları, yan yana ya da kaydırmalı.
4. **Düzenleme:**
   - Bir güne dokununca bütün kişiler renkli olarak listelenir: 🟢 uygun · 🟡 yumuşak kural çiğner (hangi kural, kaç puan) · 🔴 sert kural çiğner (resmi listede seçilemez).
   - Sürükle-bırak ile iki günün nöbetçileri yer değiştirir.
   - Kilitle / kilidi aç ve "kalanı yeniden üret".
   - Her değişiklikte adalet göstergesi ve kota sayaçları anında güncellenir.
   - "Neden?" açıklaması.
5. **Dışa aktar / paylaş:** Excel, PNG ve PDF; Android paylaş menüsü.
6. **Gayriresmi değişimler ve değişim asistanı.**
7. **Kişiler:** Kişi ekleme ve çıkarma, kıdem sırası (sürükleyerek), katsayılar (geçerlilik tarihli), kişiye özel kurallar, renk, başlangıç ve ayrılış tarihi.
8. **İstatistik / defter:**
   - Kişi × kategori tablosu (kısa ve uzun hafıza ayrı ayrı).
   - Hedef, gerçekleşen ve sapma.
   - Aylık ve yıllık grafikler.
   - "Kim ne tuttu" geçmişi.
   - İstek karşılanma oranı.
   - Resmi liste ile gerçekte tutulanın karşılaştırması.
   - Aylık nöbet saatleri (resmi ve gerçekte tutulan).
9. **Bayram ve tatil kayıtları (6.2):** İlk listeden bu yana her bayram, resmi tatil ve yılbaşı günü; kimin tuttuğu, bugünkü ekibin dengesi ve her günün elle düzeltilmesi. Liste öncesi dönemler için toplu kayıt ekranı ("2024 Ramazan Bayramı, 10 gün: Yaren 2, Fatma 1") buradan açılır.
10. **Ayarlar:** Puan bileşenleri, saat bileşenleri, ceza ağırlıkları, nöbetler arası en az gün (3), aylık üst sınır (6), hafıza süreleri, Excel başlığı, yedekleme ve geri yükleme.

---

## 13. Veri modeli

Veriler telefonda tek bir JSON belgesinde tutulur (bkz. 15). Belgenin temel bölümleri:

| Tablo | Alanlar (özet) |
|---|---|
| Kişi | id, ad, kıdem sırası (elle), bölüme katılış tarihi, asistanlık başlangıç tarihi (bilgi), ayrılış tarihi, renk |
| KişiKatsayı | kişi, geçerlilik başlangıcı, nöbet katsayısı, zor gün katsayısı |
| KişiKuralı | kişi, tür (Cumartesi yok, Pazar yok, tatil yok, haftanın günü yok, …), parametre, geçerlilik başlangıcı/bitişi |
| TakvimGünü | tarih, tatil türü, tatil adı, planlanan vizit (var/yok/belirsiz), gerçekleşen vizit, ek puan, not |
| Dönem | kişi, başlangıç, bitiş, tür, nöbet tutar mı, kota düşer mi, kesinlik, not |
| İstek | kişi, ay, tür, tarihler/parametreler, öncelik, orijinal metin |
| Ay | yıl, ay, durum (taslak/yayınlandı/kapandı), aktif resmi sürüm |
| AyKotası | ay, kişi, önerilen, kesinleşen, devreden küsurat |
| ResmiSürüm | ay, sürüm no, oluşturma zamanı, açıklama, skor özeti |
| Atama | resmi sürüm, tarih, kişi, kaynak (otomatik/elle), kilitli |
| GayriresmiDeğişim | tarih, resmi kişi, tutan kişi, bağlı değişim grubu, kayıt zamanı, not |
| GeçmişOlay | olay adı (ör. 2024 Ramazan Bayramı), tür, başlangıç–bitiş, toplam gün sayısı |
| GeçmişÖzelGün | olay, tarih (biliniyorsa), vizit, tutan kişi, gün sayısı (tarih bilinmiyorsa toplu giriş), not (2023 ile Ekim 2025 arası için elle giriş) |
| DeğişiklikKaydı | zaman, kayıt türü, eylem, eski değer, yeni değer |
| Ayar | anahtar, değer |

---

## 14. Çıktılar

### 14.1 Excel (hocaya giden resmi liste)

Dosya adı şu biçimdedir: `Nobet_Listesi_2026_11_Kasim.xlsx`. Dosyada yalnızca resmi liste bulunur; puan ve defter bilgisi yer almaz.

- **Genel görünüm:** Segoe UI yazı tipi (her Windows'ta var), ızgara çizgileri kapalı, lacivert başlık satırları, kişi adları kişinin dolu renginde.
- **Takvim sayfası:**
  - Başlık Ayarlar'dan gelir (ör. "Tıbbi Mikrobiyoloji Asistan Nöbet Listesi"), altında ay adı ve liste türü.
  - Pazartesi–Pazar ızgarası; her gün beyaz aralıklarla ayrılmış bir kutu: gün numarası (vizitliyse "VİZİT"), nöbetçinin adı, tatil adı ya da not.
  - Hafta sonu ve tatil kutuları hafif tonlu, tatil adı kırmızı.
  - Izgaranın altında kişi başı nöbet sayıları ve izin listesi.
  - Baskı ayarı: A4 yatay, tek sayfaya sığacak şekilde.
- **Liste sayfası:** Tarih | Gün | Nöbetçi | Açıklama (tatil adı, vizit, değişim). Başlık satırı kaydırırken sabit kalır.
- **Özet sayfası:** Kişi | Toplam nöbet | Hafta içi | Hafta sonu/tatil | Saat (resmi listeye göre).

### 14.2 Görsel (WhatsApp için) ve PDF

Sade, modern bir takvim:

- Inter yazı tipi (açık lisanslı, uygulamanın içinde gelir; uygulama arayüzü de aynı yazı tipini kullanır).
- Pazartesi–Pazar ızgarası; her gün yuvarlak köşeli ayrı bir kutu. Nöbetçinin adı, kişinin dolu renginde bir hapın içinde yazılır (açık renklerde koyu, koyu renklerde beyaz yazı).
- Kişi renkleri birbirinden belirgin biçimde ayrılır: kırmızı, mavi, turuncu, yeşil, sarı, mor, camgöbeği; yeni kişiler için pembe, kahverengi, fıstık yeşili, antrasit.
- Hafta sonu kutuları hafif tonlu, tatil kutuları açık kırmızı ve tatil adı yazılı; vizitli günlerde "VİZİT" rozeti.
- Altta kişi başı nöbet sayısı ve saat kartları, varsa izinler.
- Resmi ya da gayriresmi görünüm seçilebilir. Gayriresmi görünümde "Oğuz (Betül)" gösterimi kullanılır.
- Biçimler: PNG (WhatsApp'a tek dokunuşla) ve PDF (A4 yatay).

### 14.3 Nöbet dökümü

Resmi listenin görseline (takvimin altına), PDF'ine (ikinci sayfa) ve Excel'ine ("Döküm" sayfası) herkesin bu ayki durumu ve nedeni eklenir. Amaç "neden bana bu kadar" sorusunu kuralı göstererek cevaplamak. Ayarlar'dan kapatılabilir; gerçekleşen listeye eklenmez.

- Kişi başına bir satır: asistanlık yılı, nöbet ve zor gün katsayısı, nöbet sayısı ve saat, bu ay tuttuğu Cuma/Cumartesi/Pazar, cumartesi durumu ("ayda bir" ve son 6 aydaki sayısı; son yıldakilerde "sırayla · sırada 1. · son 22 Ağu"; son yıla giren için "sıraya giriyor"), bayram ve resmi tatil dengesi (tam gün olarak: "dengede", "1 gün geride", "2 gün önde"; yarım günler gösterilmez).
- Elle ya da kuralla sabitlenen sayılar yıldızla işaretlenir ve notlarda yazar.
- Kıdem eğrisi grafiği: herkes aynı çizgi üzerinde, adıyla bir nokta (isimler birbirine ve noktalara çarpmayacak yere yazılır).
- "Nasıl hesaplandı" notları: kıdem eğrisi değerleri; cumartesi kuralı ve gelecek ayın artan cumartesi sırası (ör. "Artan cumartesi sırası (Aralık 2026): Fatma → Kenan → Betül."), son yıla girenler; son yıldakine aynı ay cumartesi ve pazar yazılmadığı; Cuma/Pazar/Pazartesi/Perşembe sırası; bu ay cumartesi tutmayan çömezin gelecek ay önce yazılacağı; bayram/tatil dengesinin ilk listeden beri sayıldığı; ilk ay kuralı; takasların hesaba girmediği.
- İstek karşılanma oranları görsele konmaz ("onunki karşılandı benimki karşılanmadı" tartışması çıkmasın).
- Liste elle değiştirilse de döküm yayınlanan listeden çizilir; denge her seferinde resmi listeden yeniden hesaplandığı için elle değişiklik sonraki aylarda kendiliğinden telafi edilir.

### 14.4 İç istatistik dışa aktarımı

Defter ve istatistikler ayrı bir Excel dosyası olarak dışa aktarılabilir. Bu dosya hocaya gitmez.

---

## 15. Teknik mimari

- **Dil ve arayüz:** Kotlin, Jetpack Compose, Material 3. Arayüz yalnızca Türkçe.
- **Veri:** Tek bir JSON belgesi (kotlinx.serialization), atomik yazma ve son 10 sürümün otomatik yedeği. Veri küçük ve tek kullanıcılı olduğu için veritabanı yerine bu yol seçildi; yedekleme ve geri yükleme dosya kopyalamak kadar basit.
- **Katmanlar:**
  - Arayüz.
  - Alan katmanı: kurallar, puan, kota, defter ve çözücü. Saf Kotlin ile yazılır, Android'den bağımsızdır ve birim testlerle sınanır.
  - Veri katmanı.
- **Çözücü:** Saf Kotlin, harici kütüphane yok. Rastgelelik tohumludur, sonuçlar tekrarlanabilir.
- **Excel:** Uygulamanın kendi küçük .xlsx yazıcısı; dış kütüphane yok.
- **Görsel ve PDF:** Takvim görünümü bitmap'e çizilir. PDF için Android'in PdfDocument sınıfı kullanılır.
- **Paylaşım:** Android paylaş menüsü (WhatsApp, Drive, e-posta).
- **Yedekleme:** Android'in otomatik yedeği (Google hesabı) ve elle dışa/içe aktarma (tek dosya). Telefon değiştiğinde ya da kaybolduğunda geri yükleme bunlarla yapılır.
- **İzinler:** İnternet izni yok.
- **En düşük Android sürümü:** 8.0 (API 26).
- **Testler:**
  - Üretilen her resmi listede S1–S9'un sağlandığını doğrulayan testler.
  - Geçmiş aylar (Eylül ve Ekim 2026) için aynı girdilerle üretim yapıp elle hazırlanan listeyle karşılaştırma.
  - Kota, küsurat devri, puan formülü ve defter hesabı için birim testleri.

---

## 16. Geçmiş verilerin aktarımı

0.4.0'dan itibaren **Nisan 2024 – Ekim 2026 resmi listelerinin tamamı** uygulamada:

- **Nisan 2024 – Mart 2025:** hastanenin Excel çalışma planlarının "Nöbet" sütunundan. Boş günleri uzmanlar tuttu (uygulamada "Uzman" yazar). Şubat 2025 dosyasının başlığı yanlışlıkla "Ocak" yazıyor; tarih sütunu esas alındı.
- **Ocak 2025 ve Nisan – Ekim 2025:** kağıt listelerin fotoğraflarından (Ocak 2025'te parantez içindeki saatler kontrolde kullanıldı: Kenan 64, Yaren 72, Fatma 104, Betül 88).
- **Kasım 2025 – Ekim 2026:** kağıt listelerden (aşağıda).
- 21 Nisan 2025'i doğum iznine çıkmadan önce Canay tuttu; Canay henüz uygulamada olmadığı için boş bırakıldı.
- **Bayramlarda el yazısı kağıt esastır (0.4.5):** Excel'le çelişirse kağıt, kağıtta olmayan günler Excel'den. İki gün düzeltildi: 13 Nisan 2024 (Excel'de Kenan, kağıtta Fatma) ve 19 Haziran 2024 (Excel'de Kenan, kağıtta Yaren). 2025 Ramazan (Şule 3, Fatma 3, Yaren, Betül, Kenan) ve 2025 Kurban (Fatma – Kenan – Oğuz – Fatma – Betül) zaten aynıydı.
  - 29 Ekim 2025'i Oğuz tuttu (listede Yaren yazıyordu); resmi kayıt düzeltildi. 31 Aralık 2024 Betül, 31 Aralık 2025 Fatma doğru.
  - Eski sürümden gelen veride göç (v8) bu üç günü, o gün hâlâ eski kişi yazılıysa düzeltir; kapanmış ayda "Kağıt listeye göre düzeltildi" notuyla yeni sürüm açılır.
  - Fatma'nın ilk nöbeti 13 Nisan 2024 olduğu için Ramazan 2024'ün payına da ortaktır. Bayram dengesi: Kenan 2,1 ve Şule 1,2 gün geride; Oğuz 1,8, Yaren 0,8 ve Fatma 0,7 önde.
  - Eski sürümlerin bayram sayfasından girdiği kayıtlar göçte kaldırıldı.
- 2024 Ramazan (6–14 Nisan), 2024 Kurban (15–23 Haziran), 2025 Ramazan (29 Mart – 6 Nisan) ve 2026 Kurban (23–31 Mayıs) idari izinle 9 gündü; idari izin günleri takvimde hazır gelir.
- Dini bayram tarihleri 2027'ye kadar hazır. Sonraki yıllar Diyanet açıklayınca Takvim'den girilir; uygulama o yılın ayını hazırlarken uyarır.

- **Kasım 2025 – Ekim 2026 (12 ay):** Kağıt listelerin fotoğraflarından aktarılacak.
  - Kağıttaki "A (B)" gösteriminde resmi kişi A, gerçekte tutan B olarak alınacak.
  - Çizikli ya da düzeltilmiş hücreler aktarım sırasında tek tek sorulacak (resmi olan ilk yazılan isim mi, düzeltme mi).
  - Fatma'nın nöbetlerindeki tik ve çarpılar dikkate alınmayacak.
  - Aralık 2025 ve Ocak 2026 kağıtlarındaki saat toplamları, aktarılan verinin kontrolünde kullanılacak.
  - Şubat'ta Yaren'in hiç nöbeti görünmüyor, Aslı da Ağustos'ta başlamış. Aktarım sırasında kişi tarihleri buna göre netleştirilecek.
  - Yaren Temmuz 2026'ya kadar hafta sonu tutmuş (son: 5 Temmuz Pazar). Hafta sonu kuralının başlangıç tarihi aktarım sırasında netleştirilecek.
- **Bayram geçmişi:** 2024 Nisan, 2024 Haziran, 2025 Mart ve 2025 Haziran bayramları bayram sayfasından toplu olarak girilecek.
  - Sayfada yalnızca bugünkü ekibin günleri var gibi görünüyor (ör. 2024 Nisan'da 10 günün 3'ü). Kalan günleri o dönem nöbet tutan uzmanlar ya da sonradan ayrılan asistanlar tutmuş; olay başına ortalama yöntemi bu kayıtlara ihtiyaç duymaz.
  - Sayfanın altındaki toplamla satırlar arasındaki farklar (ör. Betül) aktarım sırasında sorulacak.
  - 2026 Ramazan ve Kurban bayramları Mart ve Mayıs 2026 listelerinden gelir.
- **2023 – Ekim 2025:** Diğer bayram, yılbaşı ve resmi tatil nöbetleri "Geçmiş özel günler" ekranından elle girilecek.
- Herkesin bölüme katılış tarihi (ay/yıl) Kişiler ekranına girilecek. Uzun hafızalı defter için bu bilgi gerekli. Yaren başka hastaneden geçiş yaptığı için katılış tarihi Kenan ve Fatma'dan sonra olacak; kıdem sırası bundan etkilenmez.

---

## 17. Kapsam dışı / sonraki sürümler

- Çoklu kullanıcı, bulut senkronu, iOS.
- WhatsApp mesajlarından istekleri otomatik ayıklama (yapıştır → öneri gelir → onayla).
- Nöbet ücreti ve saat hesabı.
- Bildirimler ve hatırlatmalar.

---

## 18. Karar kaydı

| # | Karar |
|---|---|
| K1 | Kıdem sırası: Yaren › Kenan › Fatma › Betül › Şule › Oğuz › Aslı. |
| K2 | Nöbet sayısı katsayıyla belirlenir; kesirli kısım sonraki aya devreder. (Katsayıların kaynağı için K34.) |
| K3 | Betül, Şule, Oğuz ve Aslı arasında çok küçük bir kıdem farkı vardır. |
| K4 | Aylık üst sınır 6 nöbettir. |
| K5 | Art arda ve günaşırı yasağı resmi listede asla çiğnenmez; gayriresmi listede yalnızca uyarı verir. |
| K6 | İzin kotayı düşürmez; aynı ay içinde çözülür, nöbet sayısı için borç yazılmaz. |
| K7 | Halk sağlığı rotasyonu dışındaki dış rotasyonlarda olanlar nöbeti bölümde tutar. |
| K8 | Zor günler önce çömeze yazılır; mümkün değilse bir üst kıdeme geçer. |
| K9 | Pazar ve Cuma eşit zorluktadır; her zaman vizitli olan Cumartesi en ağır gündür. |
| K10 | Perşembe kuralı "uzun tatil koruması" olarak genelleştirildi (yumuşak kural). |
| K11 | Çakışan isteklerde kıdemin önceliği yoktur; ekip kendi arasında çözer, uygulama alternatif sunar. |
| K12 | Yaren: Cumartesi, Pazar, resmi tatil, bayram ve arefe yok. Kural yalnızca Yaren'e aittir, Yaren ayrılınca kimseye geçmez. |
| K13 | Kişi ekleme ve çıkarma özelliği olacak; Canay 2027 yazında eklenecek. |
| K14 | Tek kullanıcı, tek telefon, çevrimdışı çalışma. |
| K15 | Resmi liste Excel olarak hocaya gider; Excel formatının tasarımı uygulamaya bırakıldı. |
| K16 | Adalet defteri yalnızca resmi listeye bakar. |
| K17 | 31 Aralık kötü gün sayılır (yılbaşı); kimin tuttuğu ayrıca kaydedilir. |
| K18 | 1 Ocak her yıl vizitlidir; gün bazında yine de değiştirilebilir. |
| K19 | Bayram ve yılbaşı uzun hafızalıdır (2023'ten beri). Bayramda kıdem etkilidir ama kesin kural yoktur; bu yüzden bayram planlama ekranı var. |
| K20 | Şubat–Ekim 2026 kağıtlardan aktarılacak; 2023'ten bu yana özel günler elle girilecek. |
| K21 | Kıdem sırası elle tutulur, tarihlerden türetilmez. Bölüme katılış tarihi yalnızca ekipte bulunulan dönemi belirler (Yaren geçişle sonradan katıldı ama kıdemde en önde). |
| K22 | İzin zor gün payını azaltmaz. Kaçırılan pay önce aynı ayın başka bir zor gününe verilir; sığmazsa defterde kalır ve sonraki ay telafi edilir. |
| K23 | Nöbet saati yalnızca bilgi içindir, adalet hesabına girmez. Uygulamada resmi listeye ve gerçekte tutulana göre ayrı ayrı gösterilir; hocaya giden Excel'in özetinde resmi saat yer alır. |
| K24 | Yaren'in bundan sonra nöbet tutmama ihtimali var. Bunun için "nöbet muafiyeti" dönemi kullanılır: kişi ekipte kalır, listeye girmez, payı diğerlerine dağılır, borç yazılmaz. |
| K25 | Aynı kişiye ayda 2 Cuma, 2 Cumartesi ya da 2 Pazar yazılmaz; aşırı sıkışık aylar dışında (Y9). |
| K26 | Vizit, Cuma, Pazar ve Perşembe sırayla döner; son 3 ay hatırlanır (6.1). Sıra her ay sağlanamayabilir, sağlanabildiği aylarda sağlanır. |
| K27 | Liste mümkün olduğunca karışık dizilir: aynı ikili ard arda tekrar etmez (Y10), aynı kişi Pzt–Per'de aynı güne ayda 2. kez yazılmaz (Y7). |
| K28 | Salı dersi yüzünden Pazartesi de sırayla döner; bu en kolay esneyen kuraldır (Y4a). |
| K29 | "Kota" uygulamada "Nöbet sayıları" adını alır. Bir kişinin sayısı elle değişince o kişi sabitlenir, kalan günler diğerlerine algoritmayla dağılır (5.1-8). |
| K30 | Kişi tarihleri: asistanlık başlangıcı, bölüme katılış, nöbete katılış, ayrılış. Asistanlık başlangıcı varsayılan olarak bölüme katılışla aynıdır. Başlangıç verisinde tarihler boştur; adalet hesabı boş tarihleri kayıtlardan çıkarır (2.2). |
| K31 | İsteklerde takvim yalnızca hazırlanan ayı gösterir; günler takvimden seçilir. |
| K32 | Kişi renkleri belirgin biçimde farklıdır ve dolu renk olarak kullanılır. Görsel, PDF ve Excel modern tasarımdadır (14.1, 14.2). |
| K33 | Uygulama telefon ve tablete uyumludur: geniş ekranda sekmeler solda, takvim solda ve özet sağda; takvim ekran genişliğiyle büyür. |
| K34 | Katsayılar kıdem eğrisinden gelir (2.3); herkes aynı eğriden geçer. Fatma Kenan'dan çok az çömezdir. Kişi elle katsayı girebilir. K2'nin yerini alır. |
| K35 | Ekip 5 kişinin altına düşünce son yıl indirimi yarıya iner. Uzman şu an nöbet tutmaz; ekip çok küçülürse konuşulacak. |
| K36 | Vizit de zor gün katsayısıyla döner; eşitlikte önce çömez. Yeni başlayana ilk ay vizit yazılmaz. |
| K37 | Tatil günleri (bayram, resmi tatil, yılbaşı) yalnızca kendi kategorisinde sayılır; tatilde vizit olup olmadığı bilinmese de hesap etkilenmez. |
| K38 | Bayram/tatil dengesi ilk resmi listeden (Nisan 2024) başlar; her olay o gün ekipte olanlar arasında o günkü katsayıyla paylaştırılır; uzman günleri sayılmaz; 9 günlük tatil bütün olarak bayramdır; arefe yarım gündür. Kenan çömezken payından az bayram tuttuğu için son yılında da bayram tutabilir (tam hesap). |
| K39 | Sıra hafızası 6 aydır. |
| K40 | Adalet hesabı nöbete katılıştan (yoksa ilk kayıtlı nöbetten) başlar; kaydı olmayan borçsuz başlar. Bölüme katılış tek başına yetmez (2.2). |
| K41 | Hocalar sayı söylerse o ay sabitlenir ya da kişiye "ayda sabit sayı" kuralı girilir; düşen nöbet hafta içinden gelir. Yayınlanmış ayda "Kalan günleri yeniden üret" bugüne kadarki günleri kilitler. Sınav için dönem türü var. |
| K42 | Resmi listeye nöbet dökümü eklenir (14.3). |
| K43 | Cumartesi kıdem sırasıyla gider: son yıla girmemiş herkes ayda bir, artanlar son yıldakilere sırayla (en uzun süredir tutmayan önce). Son yıla giren sıranın sonuna geçer; geçmişten borç yazılmaz. K36'nın vizit kısmının yerini alır. |
| K44 | Son yıldakine aynı ay hem cumartesi hem pazar yazılmaz (sıkışırsa esner). Cumartesi sırası en zor esneyen sıradır. |
| K45 | Her şey elle düzeltilebilir: kapanmış ay dahil herhangi bir günün resmi kaydı (yeni sürüm), gerçekte tutan, "Uzman tuttu", gün türü ve vizit. Kurallar elle düzeltmede uyarır, engellemez. |
| K46 | Bayram ve tatil kayıtları ekranı (6.2): her tatil günü, kim tuttu ve denge şeffaf; oradan gün gün düzeltilir. |
| K47 | Fatma'nın asistanlık başlangıcı Ekim 2023'tür (Eylül değil). |
| K48 | Zor günler (cuma, cumartesi, pazar) toplam olarak dengelenir; kıdemlinin payı kıdem eğrisinden. Cumartesi tutmayan kıdemli cuma ve pazarı önce alır; kimseye aynı ay üçü birden yazılmaz. Tatiller kendi hafızasında kalır (K37). (K54 ile değişti.) |
| K49 | Hoca kararı borç yazmaz; artan ve eksilen günler diğerlerine katsayıyla paylaşılır ve sonraki aylarda dengelenir. Nöbet sayısı dengesi Kasım 2026'dan beri resmi listeden hesaplanır (5.4). Önceki küsurat devrinin yerini alır. |
| K50 | Liste "kesinleşir" (yayınlanmaz); yanlışlıkla kesinleştirilen liste taslağa geri alınabilir. Liste sayılarla uyuşmazsa takvimde uyarı ve yeniden oluşturma düğmesi çıkar. |
| K51 | Aynı kişiye ayda ikinci pazartesi yüksek ceza alır (Salı dersi). Çözücü 64 × 40.000 adımla arar. |
| K52 | Düğme yazıları: "Ay hazırlığı", "Liste oluştur", "Listeyi kesinleştir", "Taslağa geri al", "Listeyi kapat", "İki günü takasla", "Kalan günleri yeniden oluştur"; sayılar "2 nöbet (1 olmalı)", saat "saat". |
| K53 | Aynı ay içinde kimse kendinden kıdemliden az tutmaz (5.5): haklar kıdem sırasına göre havuzlanır, artan nöbet önce en çömeze, payları yakın olanlar yuvarlama farkını birlikte taşır. Elle sayı ilk değiştirilince "Hoca kararı mı?" diye sorulur. |
| K54 | Zor günler hafta sonu tutan herkese eşit: fazla gün önce çömezlere, eksik gün önce kıdemliden; kimseye üçü birden yazılmaz (zorunlu değilse). Denge kademe içinde (çömezler kendi arasında, son yıldakiler kendi arasında). Kıdem eğrisinin zor gün katsayısı yalnızca tatillerde kullanılır ve "tatil katsayısı" diye gösterilir (6.1). |
| K55 | İstek ve izin zor gün sayısını değiştirmez, yalnızca günleri; değiştirirse sebebiyle uyarı (6.1). Kesin "istiyor" o günü kişiye açar (uzatmada cuma dahil). |
| K56 | Hoca kararı olan ay kişi hafta sonu sırasına ve zor gün defterine girmez (6.1). |
| K57 | İzinli ayda sayı farkı kişinin kendisine yazılır, sonraki aylarda telafi edilir (5.4). |
| K58 | Uzatmada cuma da yazılmaz. 31 Aralık'ı tutan mümkünse bir daha tutmaz (6.1). |
| K59 | Bayram geçmişinde el yazısı kağıt esas; Excel yalnızca kağıtta olmayan günler için. 13 Nisan 2024 Fatma, 19 Haziran 2024 Yaren, 29 Ekim 2025 Oğuz (16). |

---

## 19. Varsayılan olarak alınanlar

Aşağıdakiler konuşmalarda açıkça kararlaştırılmadı. İtiraz gelmezse bu şekilde uygulanacak:

- Arefe yarım tatil sayılır (puan bileşenleri yarıya iner).
- Sıra hafızası 3 aydır.
- Resmi tatiller de bayram ve yılbaşı gibi uzun hafızaya girer, çünkü yılda bir geldikleri için 3 aylık sıra hafızası onları görmez.
- Rapor kotayı düşürmez. Bu, kayıt bazında değiştirilebilir.
- Belirsiz izin sert kural yerine "mümkünse" öncelikli bir istek gibi uygulanır.
- Kenan'ın zor gün katsayısı, kıdem sırasına uygun olarak Fatma'nınkinin biraz altındadır (0.65 / 0.70).
- Kişi renkleri sabittir ve Ayarlar'dan değiştirilebilir.
- Arefe ve 28 Ekim nöbeti 20 saat sayılır (nöbet öğlen başlar). Ertesi günü tatil olan iş günlerinde 8 saat düşülmez; yani 31 Aralık 16 saattir. Kağıtta 8 sayılmış, bu yüzden Aralık 2025 aktarılınca Fatma'nın saati kağıttaki 72 yerine 80 görünecek.
