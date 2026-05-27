# Odyometri Projesi Kurulum ve Çalıştırma Rehberi

Bu proje, harici bir donanımla (Arduino / Proteus) haberleşebilen, aynı zamanda donanım olmadan da sanal olarak çalışabilen Java tabanlı bir Odyometri uygulamasıdır.

## 1. Gereksinimler
- **Java Development Kit (JDK):** En az Java 21 sürümü sisteminizde kurulu olmalıdır.
- **jSerialComm Kütüphanesi:** Projenin donanımla haberleşebilmesi için `lib` klasörü içinde `jSerialComm-2.10.3.jar` bulunmalıdır. (Depoda zaten mevcuttur).

---

## 2. Derleme (Compile)

Uygulamayı derlemek için terminal (veya komut satırı) açın ve projenin ana dizinine (`audiometry/`) gidin. İşletim sisteminize uygun olan komutu çalıştırın:

**Linux / macOS:**
```bash
javac -cp "lib/jSerialComm-2.10.3.jar" -d out $(find src/main -name "*.java")
```

**Windows (PowerShell):**
```powershell
Get-ChildItem -Path src\main -Filter *.java -Recurse | ForEach-Object { $_.FullName } > sources.txt
javac -cp "lib\jSerialComm-2.10.3.jar" -d out @sources.txt
```

---

## 3. Çalıştırma (Run)

Derleme işlemi başarılı olduktan sonra `out/` klasörü oluşacaktır. Uygulamayı başlatmak için işletim sisteminize uygun olan komutu kullanın (Dikkat: Linux/Mac'te iki nokta üst üste `:`, Windows'ta noktalı virgül `;` kullanılır).

**Linux / macOS:**
```bash
java -cp "lib/jSerialComm-2.10.3.jar:out" audiometry.gui.VirtualAudiometerApp
```

**Windows:**
```cmd
java -cp "lib\jSerialComm-2.10.3.jar;out" audiometry.gui.VirtualAudiometerApp
```

---

## 4. Kullanım Rehberi (Adım Adım Test)

Uygulama açıldıktan sonra aşağıdaki adımları izleyerek testi tamamlayabilirsiniz:

1. **Bağlantı Ayarları (Opsiyonel):** 
   - Eğer Arduino veya Proteus sanal portu (COM port) kullanacaksanız üst menüden ilgili COM Port'unu seçip **"Connect"** butonuna basın.
   - *Not:* Eğer fiziksel bir cihazınız yoksa bu adımı atlayabilirsiniz; test sanal olarak bilgisayar hoparlörü üzerinden çalışmaya devam edecektir.
2. **Kulak Seçimi:** 
   - Teste başlamadan önce **"Right Ear (Red O)"** veya **"Left Ear (Blue X)"** seçeneklerinden birini işaretleyin. Çizim buna göre yapılacaktır.
3. **Testi Başlatma:** 
   - **"Start Test"** butonuna basın. Sistem ilk frekans (250 Hz) için sesi oynatacaktır.
4. **Hasta Yanıtı (Response):** 
   - Eğer sesi duyuyorsanız (veya Arduino butonu yoksa), arayüzdeki **"Patient Response (Virtual Button)"** butonuna tıklayın. (Fiziksel cihaz kullanıyorsanız, Arduino'dan gelecek "RESPONSE" mesajı bu butona otomatik basılmasını sağlayacaktır).
   - Sistem Hughson-Westlake algoritmasına göre eşik değerlerini bulana kadar sesi açıp kapatacaktır.
5. **Raporlama:** 
   - Test bittiğinde (tüm frekanslar tarandığında) **"Save Audiogram"** butonuna tıklayarak grafiği bilgisayarınıza `.png` formatında kaydedebilirsiniz.
