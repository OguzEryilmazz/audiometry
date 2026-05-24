# Biyomedikal Mühendisliği Ekibine — Teslim Özeti
**Yazılım Mühendisliği Ekibi | YMH 334 Fonksiyonel Programlama**

---

## 1. Genel Bakış

Biyomedikal ekibinin tanımladığı Hughson-Westlake prosedürü ve IEC 60645-1 standart 
kuralları, Yazılım Mühendisliği ekibi tarafından fonksiyonel programlama paradigmasıyla 
Java'da implement edilmiştir. Tüm algoritma kuralları otomatik testlerle doğrulanmıştır.

---

## 2. Implement Edilen Algoritma Kuralları

Biyomedikal ekibinin tanımladığı her kural aşağıdaki şekilde karşılık bulmaktadır:

| Biyomedikal Kuralı | Yazılım Karşılığı | Test |
|---|---|---|
| Başlangıç: 30dB, 1000Hz | `AudioTestState.initial(1000)` → 30dB | ✅ |
| Hasta duydu → 10dB azalt | `onPatientResponse()` → intensityDb - 10 | ✅ |
| Hasta duymadı → 5dB artır | `onNoResponse()` → intensityDb + 5 | ✅ |
| Arama aşaması → 20dB artır | `onNoResponse()` SEARCHING phase → +20 | ✅ |
| 2 ardışık yanıt → eşik | `consecutiveHits >= 2` → THRESHOLD_FOUND | ✅ |
| 110dB'de yanıt yok → No response | `MAX_INTENSITY_DB = 110` → NO_RESPONSE | ✅ |
| Frekans aralığı: 250–8000 Hz | `validateFrequency()` → 6 geçerli değer | ✅ |

---

## 3. IEC 60645-1 Uyum Testleri

Aşağıdaki 7 property-based test, IEC 60645-1 standardının yazılım tarafındaki 
gereksinimlerini otomatik olarak doğrulamaktadır:

| Test | Kural | Sonuç |
|---|---|---|
| P1 | Ses şiddeti hiçbir zaman 110dB'yi aşmaz | ✅ |
| P2 | 2 yanıt → THRESHOLD_FOUND (terminal state) | ✅ |
| P3 | Tek yanıt eşik için yeterli değildir | ✅ |
| P4 | Adım büyüklükleri: ↓10dB, ↑5dB, ↑20dB (arama) | ✅ |
| P5 | consecutiveHits hiçbir zaman negatif olmaz | ✅ |
| P6 | Yalnızca 250/500/1000/2000/4000/8000 Hz kabul edilir | ✅ |
| P7 | Tek yanıt eşik sayılmaz | ✅ |

**Toplam test sonucu: 53/53 — Tüm testler geçti.**

---

## 4. Algoritma Akışı (Kodda Nasıl Yürüdüğü)

```
Başlangıç: 30dB, 1000Hz
        ↓
Ses çalınır (Elektrik-Elektronik ekibi)
        ↓
    Hasta duydu mu?
    ├── EVET → onPatientResponse()
    │         ├── consecutiveHits < 2 → 10dB azalt, tekrar çal
    │         └── consecutiveHits = 2 → THRESHOLD_FOUND → eşik kaydet
    │
    └── HAYIR → onNoResponse()
              ├── SEARCHING phase → 20dB artır
              │     └── ≥110dB → NO_RESPONSE → "No response."
              └── ASCENDING/DESCENDING → 5dB artır, tekrar çal
```

---

## 5. Sizden Beklenen Doğrulama

Biyomedikal ekibinin Proteus üzerinde yapacağı THD analizi ve osiloskop doğrulamasıyla 
birlikte, yazılım tarafındaki algoritma davranışının şu noktalarda tutarlı olduğunu 
teyit etmenizi rica ederiz:

1. **Başlangıç şiddeti** 30dB olarak uygulanıyor mu?
2. **Adım büyüklükleri** (↓10, ↑5, ↑20dB) DAC çıkışında doğru yansıyor mu?
3. **110dB sınırı** aşıldığında sistem duruyor mu?
4. **Frekans geçişleri** (250→500→1000→2000→4000→8000 Hz) doğru sırada mı?

---

## 6. Teslim Edilen Dosyalar

| Dosya | İçerik |
|---|---|
| `HughsonWestlake.java` | Ana algoritma — pure functions |
| `ResponseProcessor.java` | RESPONSE mesajı işleme |
| `AudiometryResult.java` | Hata yönetimi — IEC doğrulama |
| `TestRunner.java` | Tüm testlerin çalıştırılabilir koşucusu |

---

*Yazılım Mühendisliği Ekibi — YMH 334 Fonksiyonel Programlama*
*Ankara Üniversitesi Mühendislik Fakültesi | 2025–2026 Bahar Dönemi*
