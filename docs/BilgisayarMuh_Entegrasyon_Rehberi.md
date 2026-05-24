# Bilgisayar Mühendisliği Ekibine — Entegrasyon Rehberi
**Yazılım Mühendisliği Ekibi | YMH 334 Fonksiyonel Programlama**

---

## 1. Teslim Edilen Dosyalar

| Dosya | Açıklama |
|---|---|
| `HughsonWestlake.java` | Hughson-Westlake algoritması — pure functions |
| `ResponseProcessor.java` | RESPONSE mesajı işleme — map/filter/reduce |
| `AudiometryResult.java` | Hata yönetimi — Maybe/Optional deseni |

Tüm dosyalar `audiometry` package'ındadır.

---

## 2. Projenize Nasıl Eklersiniz?

Dosyaları projenizin şu klasörüne kopyalayın:

```
src/main/java/audiometry/
├── HughsonWestlake.java
├── ResponseProcessor.java
└── AudiometryResult.java
```

---

## 3. Entegrasyon API'si — Ne Zaman Ne Çağırırsınız?

### Adım 1 — Testi Başlatın
GUI'de "Testi Başlat" butonuna basıldığında:

```java
Maybe<AudioTestState> result = AudiometryResult.initializeTest(1000, 30);

if (result.isSuccess()) {
    AudioTestState state = result.getValue();
    // state'i bir değişkende saklayın, her adımda güncelleyeceksiniz
} else {
    // Hata mesajını GUI'ye yansıtın
    showError(result.getError());
}
```

### Adım 2 — Seri Porttan "RESPONSE" Geldiğinde
jSerialComm ile mesaj okuduğunuzda:

```java
// rawMessage = seri porttan okunan string ("RESPONSE")
Maybe<AudioTestState> result = AudiometryResult.processSerialInput(rawMessage, currentState);

if (result.isSuccess()) {
    currentState = result.getValue();          // state'i güncelleyin
    updateGUI(currentState);                   // GUI'yi yenileyin
} else {
    logWarning(result.getError());             // geçersiz mesaj, yoksayın
}
```

### Adım 3 — Timeout (Hasta Yanıt Vermedi)
Belirlediğiniz süre (örn. 3 saniye) dolduğunda:

```java
currentState = HughsonWestlake.onNoResponse(currentState);
updateGUI(currentState);
```

### Adım 4 — Test Bitti mi Kontrol Edin
Her adımdan sonra:

```java
if (HughsonWestlake.isTestComplete(currentState)) {

    Maybe<Integer> threshold = AudiometryResult.safeGetThreshold(currentState);

    if (threshold.isSuccess()) {
        int thresholdDb = threshold.getValue();
        // Odyograma işleyin: frekans → currentState.frequencyHz, dB → thresholdDb
        drawOnAudiogram(currentState.frequencyHz, thresholdDb);
    } else {
        // "No response" durumu
        showNoResponse(currentState.frequencyHz);
    }
}
```

### Adım 5 — Sonraki Frekanslara Geçin
Bir frekans tamamlandığında yeni state oluşturun:

```java
// Örn: 1000Hz bitti, 2000Hz'e geç
currentState = AudioTestState.initial(2000);
```

---

## 4. Tam Akış Özeti

```
GUI: "Başlat" butonu
        ↓
AudiometryResult.initializeTest(freq, db)
        ↓
Her adımda ses çal → bekle
        ↓
    RESPONSE geldi?
    ├── Evet → AudiometryResult.processSerialInput(msg, state)
    └── Hayır (timeout) → HughsonWestlake.onNoResponse(state)
        ↓
HughsonWestlake.isTestComplete(state)?
    ├── Evet → AudiometryResult.safeGetThreshold(state) → odyograma çiz
    └── Hayır → devam et
```

---

## 5. Önemli Notlar

- **State'i siz saklayın.** Bizim fonksiyonlarımız state almaz, yeni state döndürür. Her çağrı sonucunu bir değişkende tutun.
- **Maybe.isSuccess() kontrolü şart.** Her Maybe döndüren fonksiyonun sonucunu kontrol etmeden kullanmayın.
- **Thread safety:** Testler sırasında state'i yalnızca bir thread'den güncelleyin (EDT - Event Dispatch Thread).

---

## 6. Bağımlılık Yok

Bizim modülümüz saf Java'dır, harici kütüphane gerektirmez. Dosyaları kopyalamanız yeterli.

---

*Sorularınız için: Yazılım Mühendisliği Ekibi — YMH 334*
