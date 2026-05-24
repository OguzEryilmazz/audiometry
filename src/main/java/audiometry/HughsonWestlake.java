package audiometry;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Hughson-Westlake Pure Tone Audiometry Algorithm
 *
 * Yazılım Mühendisliği Ekibi – YMH 334 Fonksiyonel Programlama
 * IEC 60645-1 standardına uygun implementasyon.
 *
 * Tüm fonksiyonlar PURE FUNCTION'dır:
 *   - Aynı input → her zaman aynı output
 *   - Dış state'e okuma/yazma yok
 *   - Side effect yok
 */
public final class HughsonWestlake {

    // -------------------------------------------------------------------------
    // Sabitler (IEC 60645-1)
    // -------------------------------------------------------------------------

    public static final int    MIN_INTENSITY_DB      = 30;
    public static final int    MAX_INTENSITY_DB      = 110;
    public static final int    ASCENDING_STEP_DB     = 5;
    public static final int    DESCENDING_STEP_DB    = 10;
    public static final int    SEARCH_STEP_DB        = 20;   // "No response" durumunda atış
    public static final int    THRESHOLD_HIT_COUNT   = 2;    // Eşik için gereken yanıt sayısı

    // -------------------------------------------------------------------------
    // Immutable State Yapısı
    // -------------------------------------------------------------------------

    /**
     * Testin o anki anlık durumu.
     * Tüm alanlar final → immutable.
     * Durum değişikliği = yeni bir AudioTestState nesnesi döndürmek.
     */
    public static final class AudioTestState {
        public final int     intensityDb;        // Şu anki ses şiddeti (dB)
        public final int     frequencyHz;        // Şu anki frekans (Hz)
        public final List<Integer> responseLog;  // Hastanın yanıt verdiği dB değerleri
        public final int     consecutiveHits;    // Mevcut dB'de arka arkaya yanıt sayısı
        public final Phase   phase;              // Algoritmanın hangi aşamasında olduğu

        public enum Phase {
            SEARCHING,      // Duyma eşiği aranıyor (20dB arama aşaması)
            DESCENDING,     // Hasta duydu → 10dB azaltılıyor
            ASCENDING,      // Hasta duymadı → 5dB artırılıyor (Westlake aşaması)
            THRESHOLD_FOUND,// Eşik bulundu
            NO_RESPONSE     // 110dB'de yanıt alınamadı
        }

        public AudioTestState(int intensityDb,
                              int frequencyHz,
                              List<Integer> responseLog,
                              int consecutiveHits,
                              Phase phase) {
            this.intensityDb      = intensityDb;
            this.frequencyHz      = frequencyHz;
            this.responseLog      = Collections.unmodifiableList(responseLog);
            this.consecutiveHits  = consecutiveHits;
            this.phase            = phase;
        }

        /** Başlangıç durumu – her test bu state ile başlar */
        public static AudioTestState initial(int frequencyHz) {
            return new AudioTestState(
                MIN_INTENSITY_DB,
                frequencyHz,
                Collections.emptyList(),
                0,
                Phase.SEARCHING
            );
        }

        @Override
        public String toString() {
            return String.format(
                "AudioTestState{%dHz, %ddB, phase=%s, hits=%d, log=%s}",
                frequencyHz, intensityDb, phase, consecutiveHits, responseLog
            );
        }
    }

    // -------------------------------------------------------------------------
    // PURE FUNCTIONS – Algoritma Adımları
    // -------------------------------------------------------------------------

    /**
     * Hastanın yanıt vermesi durumunda çalışır.
     * Flowchart: "Does the patient hear? → Yes"
     *
     * Kural:
     *   - SEARCHING veya ASCENDING aşamasında → consecutiveHits +1
     *   - consecutiveHits THRESHOLD_HIT_COUNT'a ulaştıysa → THRESHOLD_FOUND
     *   - Değilse DESCENDING: sesi 10dB azalt
     *
     * @param state Mevcut test durumu
     * @return      Güncellenmiş yeni test durumu
     */
    public static AudioTestState onPatientResponse(AudioTestState state) {
        int newHits = state.consecutiveHits + 1;

        if (newHits >= THRESHOLD_HIT_COUNT) {
            // Eşik bulundu – mevcut dB'i response log'a ekle
            List<Integer> newLog = appendToList(state.responseLog, state.intensityDb);
            return new AudioTestState(
                state.intensityDb,
                state.frequencyHz,
                newLog,
                newHits,
                AudioTestState.Phase.THRESHOLD_FOUND
            );
        }

        // Henüz eşik doğrulanmadı → sesi 10dB azalt (descending)
        int newIntensity = state.intensityDb - DESCENDING_STEP_DB;
        List<Integer> newLog = appendToList(state.responseLog, state.intensityDb);
        return new AudioTestState(
            newIntensity,
            state.frequencyHz,
            newLog,
            newHits,
            AudioTestState.Phase.DESCENDING
        );
    }

    /**
     * Hastanın yanıt VERMEMESİ durumunda çalışır.
     * Flowchart: "Does the patient hear? → No"
     *
     * Kural (aşamaya göre farklılaşır):
     *   - SEARCHING aşamasında → 20dB artır; 110dB aşıldıysa NO_RESPONSE
     *   - DESCENDING veya ASCENDING aşamasında → 5dB artır (ascending adımı)
     *
     * @param state Mevcut test durumu
     * @return      Güncellenmiş yeni test durumu
     */
    public static AudioTestState onNoResponse(AudioTestState state) {
        if (state.phase == AudioTestState.Phase.SEARCHING) {
            int newIntensity = state.intensityDb + SEARCH_STEP_DB;

            if (newIntensity >= MAX_INTENSITY_DB) {
                return new AudioTestState(
                    MAX_INTENSITY_DB,
                    state.frequencyHz,
                    state.responseLog,
                    0,
                    AudioTestState.Phase.NO_RESPONSE
                );
            }

            return new AudioTestState(
                newIntensity,
                state.frequencyHz,
                state.responseLog,
                0,
                AudioTestState.Phase.SEARCHING
            );
        }

        // DESCENDING veya ASCENDING → 5dB artır, consecutiveHits sıfırla
        int newIntensity = state.intensityDb + ASCENDING_STEP_DB;
        return new AudioTestState(
            newIntensity,
            state.frequencyHz,
            state.responseLog,
            0,  // yanıt alınamadı → sayaç sıfırlanır
            AudioTestState.Phase.ASCENDING
        );
    }

    /**
     * Testin bitip bitmediğini kontrol eder.
     * PURE: sadece state'e bakar, hiçbir şey değiştirmez.
     *
     * @param state Mevcut test durumu
     * @return true → test bitti (eşik bulundu veya yanıt yok)
     */
    public static boolean isTestComplete(AudioTestState state) {
        return state.phase == AudioTestState.Phase.THRESHOLD_FOUND
            || state.phase == AudioTestState.Phase.NO_RESPONSE;
    }

    /**
     * Test tamamlandıysa eşik değerini döndürür.
     * Optional kullanımı: hata durumunda null değil, Optional.empty() döner.
     *
     * @param state Mevcut test durumu
     * @return      Eşik değeri (dB), test bitmemişse Optional.empty()
     */
    public static Optional<Integer> getThreshold(AudioTestState state) {
        if (state.phase == AudioTestState.Phase.THRESHOLD_FOUND) {
            return Optional.of(state.intensityDb);
        }
        return Optional.empty();
    }

    /**
     * Testin sonuç mesajını döndürür.
     * Flowchart'taki terminal node'larına karşılık gelir.
     *
     * @param state Tamamlanmış test durumu
     * @return      Sonuç mesajı
     */
    public static String getResultMessage(AudioTestState state) {
        return switch (state.phase) {
            case THRESHOLD_FOUND -> String.format(
                "Eşik değeri: %d dB @ %d Hz", state.intensityDb, state.frequencyHz
            );
            case NO_RESPONSE -> "No response. (≥110 dB'de yanıt alınamadı)";
            default -> "Test henüz tamamlanmadı.";
        };
    }

    /**
     * Bir sonraki adımda uygulanacak ses şiddetini hesaplar.
     * GUI katmanı bu değeri Bilgisayar Müh. ekibine iletecek.
     *
     * @param state Mevcut test durumu
     * @return      Uygulanacak şiddet (dB)
     */
    public static int getNextIntensity(AudioTestState state) {
        return state.intensityDb;
    }

    // -------------------------------------------------------------------------
    // Yardımcı Pure Function – Immutable Liste Ekleme
    // -------------------------------------------------------------------------

    /**
     * Var olan immutable listeye eleman ekleyerek YENİ bir liste döndürür.
     * Orijinal liste değişmez.
     */
    private static <T> List<T> appendToList(List<T> original, T element) {
        java.util.ArrayList<T> copy = new java.util.ArrayList<>(original);
        copy.add(element);
        return Collections.unmodifiableList(copy);
    }

    // -------------------------------------------------------------------------
    // Özel constructor – instantiation engellenir (utility class)
    // -------------------------------------------------------------------------
    private HughsonWestlake() {
        throw new UnsupportedOperationException("Utility class");
    }
}
