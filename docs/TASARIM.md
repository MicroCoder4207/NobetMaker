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

Kıdem sırası kişi kartında elle tutulur, en kıdemliden en çömeze doğru. Her kişinin iki katsayısı vardır:

- **Nöbet katsayısı:** Kişinin ay içinde kaç nöbet tutacağını belirler.
- **Zor gün katsayısı:** Kişinin zor günlerden (Cumartesi, Cuma, Pazar, resmi tatil, bayram, yılbaşı) alacağı payı belirler. "Önce çömez" ilkesi bu katsayı üzerinden işler.

Katsayılar asistanlık yılına otomatik olarak bağlanmaz. Elle girilir ve geçerlilik tarihiyle saklanır, böylece sonradan yapılan bir değişiklik geçmiş hesapları bozmaz.

### 2.1 Başlangıç değerleri

| Sıra | Kişi | Nöbet katsayısı | Zor gün katsayısı | Kişiye özel kural |
|---|---|---|---|---|
| 1 | Yaren | 0.50 | 0.50 (yalnız Cuma için) | Cumartesi, Pazar, resmi tatil, bayram ve arefe yok |
| 2 | Kenan | 0.80 | 0.65 | — |
| 3 | Fatma | 0.90 | 0.70 | — |
| 4 | Betül | 0.97 | 0.94 | — |
| 5 | Şule | 0.98 | 0.96 | — |
| 6 | Oğuz | 0.99 | 0.98 | — |
| 7 | Aslı | 1.00 | 1.00 | — |

- Betül, Şule, Oğuz ve Aslı arasındaki fark bilerek çok küçük tutuldu. Yıllık etkisi kabaca şu: Aslı, Betül'den 2 nöbet ve 1-2 zor gün fazla tutar.
- İyi günler (Perşembe) kıdeme bakılmadan, uygun olan herkese eşit dağıtılır.
- Yaren'in özel kuralı yalnızca Yaren'e aittir. Yaren ayrılınca bu kural kimseye otomatik geçmez.
- **Canay** 2027 yazında eklenecek. Yeni gelen birinin ilk ayında katsayı elle düşük girilir.

### 2.2 Kişi ekleme ve çıkarma

- Her kişinin bir **başlangıç** tarihi, varsa bir de **ayrılış** tarihi vardır. Kişi ayrılış tarihinden sonra listeye girmez ve katsayılar kalanlar arasında kendiliğinden yeniden oranlanır.
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
- 1 Ocak: vizit varsayılan olarak **var** (genelde oluyor ama kesin değil).
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
| Kısa hafızalı | Cumartesi, Pazar, Cuma, Perşembe | Son 6 ay |
| Uzun hafızalı | Bayram, Resmi tatil, Yılbaşı | 2023'ten beri |
| Dengelenmeyen | Hafta içi (Pzt–Çar) | — (yalnızca kotayı doldurur) |

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
| Y4 | Kısa hafızalı kategori dengesi (bkz. 6) | 20 × sapma² |
| Y5 | Uzun hafızalı kategori dengesi (bkz. 6) | 30 × sapma² |
| Y6 | Yayılım: iki nöbet arasında 3 gün var | 15 |
|  | Yayılım: iki nöbet arasında 4 gün var | 5 |
| Y7 | Aynı kişi aynı ay içinde haftanın aynı gününe 3. kez yazıldı | 3 |
| Y8 | Aynı kişi aynı bayramda 2. güne yazıldı | 40 |

Y4 ve Y5'teki sapma, gün birimiyle ölçülür (bkz. 6).

---

## 5. Kota (kim kaç nöbet tutar)

### 5.1 Hesap

1. Ayın gün sayısına N denir.
2. Her kişinin **etkin katsayısı** hesaplanır: nöbet katsayısı × (1 − kotayı düşüren dönem günleri / N). Ayın tamamında ekipte olmayan kişi için de (ay ortasında başlayan ya da ayrılan) aynı oranlama yapılır.
3. **Ham hak:** hak = N × etkin katsayı / Σ etkin katsayılar.
4. Önceki aydan devreden **küsurat** ham hakka eklenir.
5. **Yuvarlama:** Toplam N olacak şekilde en büyük kalan yöntemiyle yapılır. Eşitlik olursa fazla nöbet çömeze gider.
6. **Yeni küsurat** = (hak + eski küsurat) − verilen kota. Bu fark sonraki aya devreder. Örneğin Yaren'in hakkı 2.5 ise bir ay 2, bir ay 3 nöbet tutar.
7. **Sınırlar:** Üst sınır 6'dır (S6). Ayrıca fiziksel sığma kontrol edilir: kişinin uygun günlerine en az 3 gün arayla en fazla kaç nöbet sığabileceğine bakılır. Sınırı aşan kısım o ay diğerlerine dağıtılır. Bunun için **borç yazılmaz** ve küsurat devri yapılmaz.
8. Uygulama kotayı **önerir**. Liste yapan herhangi bir kişinin kotasını ±1 değiştirebilir, toplam N korunur.

Doğrulama:

- **Yaren varken, 31 günlük ay:** Yaren ≈2.5, Kenan ≈4.0, Fatma ≈4.5, Betül/Şule/Oğuz/Aslı ≈4.9–5.05. Ekim 2026'nın elle hazırlanan listesi (2-4-5-5-5-5-5) bununla uyumlu.
- **Yaren ayrıldıktan sonra, 30 günlük ay:** Kenan ≈4.3, Fatma ≈4.8, çömezler ≈5.2–5.3.

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
| Diğer | Kayıt bazında seçilir | Kayıt bazında seçilir |

Her dönem kaydı **kesin** ya da **belirsiz** olarak işaretlenebilir (ör. tarihi henüz netleşmemiş bir izin). Belirsiz dönem sert kural olarak değil, "mümkünse" öncelikli bir istek gibi uygulanır (Y2).

---

## 6. Adalet defteri

Defter, her kişinin her kategoride ne kadar yük taşıdığını izler. Sonraki listelerde geride kalan kişiye öncelik verilmesini sağlar.

- **Kaynak:** Defter yalnızca **resmi listeye** (en güncel resmi sürüme) ve geçmiş özel gün kayıtlarına bakar. Gayriresmi değişimler deftere girmez. Bunlar kişiler arasındaki anlaşmalardır ve sistemin bunları ayrıca telafi etmesi aynı şeyin iki kez hesaba girmesi olur.
- **Ölçü:** Her kategoride kişinin topladığı **puan** (tutulan gün × gün puanı). Böylece vizitli bir bayram günü vizitsiz olandan daha ağır sayılır.
- **Hedef:** Her günün puanı, o gün o kategori için **uygun** olan kişiler arasında zor gün katsayılarına göre paylaştırılır. Perşembe için herkesin payı eşittir. Kişinin hedefi bu payların toplamıdır.
  - Uygunluk şöyle belirlenir: kişi o gün ekipte olmalı, kalıcı bir kural (Yaren'in kuralı gibi) ya da **kotayı düşüren** bir dönem (halk sağlığı rotasyonu, doğum izni) yüzünden dışarıda kalmamalı.
  - Yıllık izin uygunluğu düşürmez, tıpkı kotayı düşürmediği gibi. Kişinin izindeyken kaçırdığı zor gün, defter tarafından sonraki listelerde telafi ettirilir.
  - Kişinin ekipte bulunmadığı dönemler hedefe girmez. Örneğin Aslı'dan 2023 bayramları için pay beklenmez.
- **Sapma** = gerçekleşen − hedef. Kategorinin ortalama gün puanına bölünerek gün birimine çevrilir. Pozitif sapma kişinin fazla tuttuğunu, negatif sapma geride kaldığını gösterir.
- **Hafıza süreleri:**
  - Kısa hafıza (Cumartesi, Pazar, Cuma, Perşembe): son **6 ay**.
  - Uzun hafıza (Bayram, Resmi tatil, Yılbaşı): **2023'ten beri** tüm geçmiş.
- Hafta içi günleri (Pzt–Çar) dengelenmez. Toplam nöbet sayısı zaten kota ile dengelenir.
- İstatistik ekranında ayrıca şunlar gösterilir: toplam nöbet (resmi ve gerçekte tutulan), toplam puan, istek karşılanma oranı.

Defter ayrı bir tabloda saklanmaz. Her seferinde resmi atamalardan, geçmiş özel gün kayıtlarından ve takvimden yeniden hesaplanır. Böylece kayıtlarla defter arasında tutarsızlık oluşmaz.

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

- Sert kuralları ve kotaları bozmayan hamleler denenir. Örneğin iki günün nöbetçileri yer değiştirir (takas) ya da bir gün başka bir kişiye verilip ondan başka bir gün geri alınır.
- Hamle toplam cezayı düşürüyorsa kabul edilir. Yerel bir minimumda takılmamak için kontrollü rastgelelik kullanılır (benzetimli tavlama).

### 8.4 Alternatifler

Farklı başlangıçlarla birden çok çözüm üretilir. Birbirinden yeterince farklı olan en iyi **3** çözüm, her biri bir skor kartıyla sunulur:

- Toplam ceza
- Karşılanan ve karşılanmayan istekler (kim, hangisi)
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

---

## 9. Resmi ve gayriresmi liste

### 9.1 Resmi liste

- Algoritma üretir, liste yapan düzeltir. S1–S9'dan herhangi biri ihlal edildiği sürece kaydedilemez.
- Sürümlüdür, her revizyon yeni bir sürüm oluşturur. Hocaya giden Excel en güncel sürümden üretilir. Adalet defteri de en güncel resmi sürüme bakar.
- Durumları: **Taslak → Yayınlandı → Kapandı**. Kapanan ay değiştirilemez, böylece geçmiş korunur.

### 9.2 Gayriresmi liste

- Ekip içindeki değişimlerin resmi listenin üzerine işlendiği katmandır, yani nöbeti gerçekte kimin tuttuğunu gösterir.
- Burada kurallar yalnızca uyarı verir.
- Takvimde resmi kişi ile gerçekte tutan farklıysa kağıt listelerdeki gösterim kullanılır: **Oğuz (Betül)**, yani resmide Oğuz yazıyor, nöbeti Betül tuttu.
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
9. **Yayınla:** Resmi Excel hocaya, görsel WhatsApp grubuna gönderilir.
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
9. **Geçmiş özel günler:** 2023'ten beri bayram, yılbaşı ve resmi tatil kayıtlarının elle girildiği ekran. Her kayıtta tarih, tür, vizit (var/yok/bilinmiyor), tutan kişi ve not bulunur.
10. **Ayarlar:** Puan bileşenleri, ceza ağırlıkları, nöbetler arası en az gün (3), aylık üst sınır (6), hafıza süreleri, Excel başlığı, yedekleme ve geri yükleme.

---

## 13. Veri modeli

Veriler telefonda SQLite (Room) ile tutulur. Temel tablolar:

| Tablo | Alanlar (özet) |
|---|---|
| Kişi | id, ad, kıdem sırası, başlangıç tarihi, ayrılış tarihi, renk |
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
| GeçmişÖzelGün | tarih, tür, vizit, tutan kişi, not (2023 ile Ocak 2026 arası için elle giriş) |
| DeğişiklikKaydı | zaman, kayıt türü, eylem, eski değer, yeni değer |
| Ayar | anahtar, değer |

---

## 14. Çıktılar

### 14.1 Excel (hocaya giden resmi liste)

Dosya adı şu biçimdedir: `Nobet_Listesi_2026_11_Kasim.xlsx`. Dosyada yalnızca resmi liste bulunur; puan ve defter bilgisi yer almaz.

- **Takvim sayfası:**
  - Başlık Ayarlar'dan gelir (ör. "Tıbbi Mikrobiyoloji Asistan Nöbet Listesi – Kasım 2026").
  - Pazartesi–Pazar ızgarası; her hücrede gün numarası ve nöbetçinin adı.
  - Kişi renkleri; hafta sonu sütunları açık gri.
  - Tatil günleri işaretli ve adı yazılı.
  - Izgaranın altında kişi başı nöbet sayıları ve izin listesi.
  - Baskı ayarı: A4 yatay, tek sayfaya sığacak şekilde.
- **Liste sayfası:** Tarih | Gün | Nöbetçi | Açıklama (resmi tatil ya da bayram adı).
- **Özet sayfası:** Kişi | Toplam nöbet | Hafta içi | Hafta sonu/tatil.

### 14.2 Görsel (WhatsApp için) ve PDF

Kağıt listelerin görsel dili korunur:

- Pazartesi–Pazar ızgarası, her kişiye sabit bir fosforlu renk, hafta sonu sütunları hafif tonlu.
- Tatil günleri daire ya da işaretle ve adıyla gösterilir; vizitli günlerde küçük bir ikon bulunur.
- Altta "Yaren → 2 nöbet …" özeti ve "Yıllık izinler: 7-11 Eylül → Oğuz" listesi yer alır.
- Resmi ya da gayriresmi görünüm seçilebilir. Gayriresmi görünümde "Oğuz (Betül)" gösterimi kullanılır.
- Biçimler: PNG (WhatsApp'a tek dokunuşla) ve PDF (A4 yatay).

### 14.3 İç istatistik dışa aktarımı

Defter ve istatistikler ayrı bir Excel dosyası olarak dışa aktarılabilir. Bu dosya hocaya gitmez.

---

## 15. Teknik mimari

- **Dil ve arayüz:** Kotlin, Jetpack Compose, Material 3. Arayüz yalnızca Türkçe.
- **Veri:** Room (SQLite) ve şema göçleri.
- **Katmanlar:**
  - Arayüz.
  - Alan katmanı: kurallar, puan, kota, defter ve çözücü. Saf Kotlin ile yazılır, Android'den bağımsızdır ve birim testlerle sınanır.
  - Veri katmanı.
- **Çözücü:** Saf Kotlin, harici kütüphane yok. Rastgelelik tohumludur, sonuçlar tekrarlanabilir.
- **Excel:** Uygulama boyutunu şişirmeyen hafif bir .xlsx yazıcı. Kesin seçim kodlama aşamasında yapılacak.
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

- **Şubat–Ekim 2026:** Kağıt listelerin fotoğraflarından resmi liste olarak aktarılacak.
  - Çizikli ya da düzeltilmiş hücreler aktarım sırasında tek tek sorulacak (resmi olan ilk yazılan isim mi, düzeltme mi).
  - Fatma'nın nöbetlerindeki tik ve çarpılar dikkate alınmayacak.
  - Şubat'ta Yaren'in hiç nöbeti görünmüyor, Aslı da Ağustos'ta başlamış. Aktarım sırasında kişi tarihleri buna göre netleştirilecek.
- **2023 – Ocak 2026:** Bayram, yılbaşı ve resmi tatil nöbetleri "Geçmiş özel günler" ekranından elle girilecek.
- Herkesin başlangıç tarihi (ay/yıl) Kişiler ekranına girilecek. Uzun hafızalı defter için bu bilgi gerekli.

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
| K2 | Nöbet sayısı katsayıyla belirlenir; katsayılar asistanlık yılına otomatik bağlı değildir. Kesirli kısım sonraki aya devreder. |
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
| K18 | 1 Ocak varsayılan olarak vizitlidir; değiştirilebilir. |
| K19 | Bayram ve yılbaşı uzun hafızalıdır (2023'ten beri). Bayramda kıdem etkilidir ama kesin kural yoktur; bu yüzden bayram planlama ekranı var. |
| K20 | Şubat–Ekim 2026 kağıtlardan aktarılacak; 2023'ten bu yana özel günler elle girilecek. |

---

## 19. Varsayılan olarak alınanlar

Aşağıdakiler konuşmalarda açıkça kararlaştırılmadı. İtiraz gelmezse bu şekilde uygulanacak:

- Arefe yarım tatil sayılır (puan bileşenleri yarıya iner).
- Kısa hafıza süresi 6 aydır.
- Resmi tatiller de bayram ve yılbaşı gibi uzun hafızaya girer, çünkü yılda bir geldikleri için 6 aylık hafıza onları görmez.
- İzindeyken denk gelen zor günler defterde "geride" olarak kalır: izin yükü azaltmaz ve kaçırılan zor gün sonraki listelerde telafi edilir. (Nöbet **sayısı** için borç yoktur, bkz. K6.)
- Rapor kotayı düşürmez. Bu, kayıt bazında değiştirilebilir.
- Belirsiz izin sert kural yerine "mümkünse" öncelikli bir istek gibi uygulanır.
- Kenan'ın zor gün katsayısı, kıdem sırasına uygun olarak Fatma'nınkinin biraz altındadır (0.65 / 0.70).
- Kişi renkleri sabittir ve Ayarlar'dan değiştirilebilir.
