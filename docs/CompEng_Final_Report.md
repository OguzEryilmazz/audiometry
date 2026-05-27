# Computer Engineering Team - Final Implementation Report

Bu doküman, Odyometri (YMH 334 / COM 2044) projesinde Bilgisayar Mühendisliği (Computer Engineering) takımı olarak üstlenilen görevleri ve projeye yapılan tüm teknik katkıları özetlemektedir.

## 1. Grafiksel Kullanıcı Arayüzü (GUI) Geliştirimi
- **Swing Tabanlı Arayüz:** Kullanıcıların frekans ve ses şiddeti testlerini kolayca yönetebilmesi için `VirtualAudiometerApp.java` oluşturulmuştur.
- **Durum Yönetimi:** Yazılım Mühendisliği ekibinin yazdığı durumsuz (stateless) `HughsonWestlake` fonksiyonel çekirdeği, arayüz tarafından sarmalanarak (wrapper) yönetilebilir ve interaktif bir duruma (stateful) dönüştürülmüştür.
- **Zamanlayıcılar (Timers):** Hastanın tepki vermesi için beklenen 3 saniyelik standart bekleme süresi `javax.swing.Timer` ile asenkron olarak kodlanmıştır.

## 2. Odyogram Çizimi ve Dışa Aktarımı
- **Uluslararası Tıbbi Standartlar:** Odyogram çizimi için `AudiogramPanel.java` sınıfı kodlanmıştır. PDF gereksinimlerine birebir uyumlu olarak; test sonuçları **Sağ Kulak için Kırmızı "O"**, **Sol Kulak için Mavi "X"** işaretleriyle koordinat sistemine yerleştirilmiştir.
- **Çift Kanal Desteği:** Sağ ve sol kulak verileri ayrı listelerde tutulmuş ve grafikte üst üste çizilebilmesi sağlanmıştır.
- **PNG Çıktısı:** Test sonlandığında hekimlerin raporlayabilmesi için odyogramın `.png` formatında dışa aktarılması sağlanmıştır.

## 3. Donanım (Serial Port) Entegrasyonu
- **jSerialComm Kütüphanesi:** Proje zorunlulukları gereği Arduino / Proteus donanımlarıyla haberleşmek için sisteme `jSerialComm` entegre edilmiştir (`pom.xml` güncellenmiştir).
- **SerialPortManager.java:**
  - Sisteme bağlı fiziksel (COM) veya sanal donanımları dinamik olarak tespit eden bir altyapı kurulmuştur.
  - Test frekanslarına göre C/C++ ekibinin yazdığı koda asenkron **"PLAY,frekans,şiddet"** komutları gönderilmiştir.
  - Donanımdan gelecek olan **"RESPONSE"** (hasta butona bastı) mesajlarını asenkron olarak dinleyen ve arayüzü güncelleyen bir "Listener" (Dinleyici) mekanizması kurulmuştur.

## 4. Yazılım Tarafındaki Algoritma Hatalarının Giderilmesi
- **Sonsuz Döngü Çözümü:** Yazılım Mühendisliği takımının yazdığı test algoritmasında bulunan kritik bir mantık hatası giderilmiştir. Orijinal kod, eşik değerini bulmak için peş peşe 2 doğru yanıt bekliyordu ve bu durum sonsuz bir Hughson-Westlake döngüsü yaratıyordu. Bilgisayar mühendisliği takımı olarak bu hata, "aynı desibel seviyesindeki toplam tepki sayısını loglayarak" tıbbi standartlara uygun şekilde revize edilmiştir.
- **Birim Testi Güncellemesi:** Algoritmada yapılan bu hayati düzeltmenin ardından `AudiometryTest.java` dosyasındaki birim testleri (unit tests) yeni ve doğru mantığa göre güncellenerek tüm testlerin başarıyla geçmesi sağlanmıştır.

## 5. Sanal Hoparlör ve Platform Bağımsızlık
- **Dahili Sinyal Üretici:** Donanımın mevcut olmadığı senaryolarda projenin test edilebilmesi için `javax.sound.sampled` kullanılarak donanımdan bağımsız bir sinüs dalgası (sine wave) jeneratörü yazılmıştır.
- **Sanal Buton (Fallback):** Herhangi bir fiziksel port bağlantısı zorunluluğunu ortadan kaldırmak için arayüze sanal bir hasta butonu eklenmiştir. Proje her iki koşulda da eksiksiz çalışmaktadır.
