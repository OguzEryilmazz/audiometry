package audiometry;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Hata Yönetimi – Optional / Maybe Deseni
 *
 * Yazılım Mühendisliği Ekibi – YMH 334 Fonksiyonel Programlama
 *
 * Odyometre testinde oluşabilecek tüm hata durumları
 * exception fırlatmak yerine Optional/Maybe zinciriyle,
 * yan etkisiz (side-effect free) olarak yönetilir.
 *
 * Tüm fonksiyonlar PURE FUNCTION'dır.
 */
public final class AudiometryResult {

    // -------------------------------------------------------------------------
    // Maybe<T> — Fonksiyonel Hata Sarmalayıcı
    // -------------------------------------------------------------------------

    /**
     * Java'nın Optional'ını genişleten Maybe deseni.
     *
     * Fark: hata mesajı taşır, sadece "boş mu?" demez.
     * Her adım Maybe<T> döndürür → zincir kırılmadan devam eder.
     *
     * @param <T> Başarı durumunda taşınan değer tipi
     */
    public static final class Maybe<T> {
        private final T      value;
        private final String errorMessage;
        private final boolean success;

        // Private constructor – factory method'larla oluşturulur
        private Maybe(T value, String errorMessage, boolean success) {
            this.value        = value;
            this.errorMessage = errorMessage;
            this.success      = success;
        }

        /** Başarı durumu: değer var */
        public static <T> Maybe<T> of(T value) {
            return new Maybe<>(value, null, true);
        }

        /** Hata durumu: değer yok, mesaj var */
        public static <T> Maybe<T> failure(String errorMessage) {
            return new Maybe<>(null, errorMessage, false);
        }

        /** Optional'dan Maybe'e dönüştür */
        public static <T> Maybe<T> fromOptional(Optional<T> optional, String errorMsg) {
            return optional.map(Maybe::of).orElse(Maybe.failure(errorMsg));
        }

        // --- Zincirleme Operatörleri ---

        /**
         * map: başarılıysa değeri dönüştür, hatalıysa hata yay.
         * Aynı Optional.map() gibi çalışır.
         */
        public <U> Maybe<U> map(Function<T, U> f) {
            if (!success) return Maybe.failure(errorMessage);
            try {
                U result = f.apply(value);
                return result != null
                    ? Maybe.of(result)
                    : Maybe.failure("map sonucu null döndü");
            } catch (Exception e) {
                return Maybe.failure("map hatası: " + e.getMessage());
            }
        }

        /**
         * flatMap: Maybe döndüren fonksiyonları zincirler (W-monad bind).
         */
        public <U> Maybe<U> flatMap(Function<T, Maybe<U>> f) {
            if (!success) return Maybe.failure(errorMessage);
            try {
                return f.apply(value);
            } catch (Exception e) {
                return Maybe.failure("flatMap hatası: " + e.getMessage());
            }
        }

        /**
         * filter: koşul sağlanmıyorsa hata üret.
         */
        public Maybe<T> filter(Predicate<T> predicate, String failMessage) {
            if (!success) return this;
            return predicate.test(value) ? this : Maybe.failure(failMessage);
        }

        /** Değeri al; hatalıysa varsayılan değeri döndür */
        public T getOrElse(T defaultValue) {
            return success ? value : defaultValue;
        }

        /** Optional'a çevir */
        public Optional<T> toOptional() {
            return success ? Optional.of(value) : Optional.empty();
        }

        public boolean isSuccess()  { return success; }
        public boolean isFailure()  { return !success; }
        public String  getError()   { return errorMessage; }
        public T       getValue()   { return value; }

        @Override
        public String toString() {
            return success
                ? String.format("Maybe.Success(%s)", value)
                : String.format("Maybe.Failure(%s)", errorMessage);
        }
    }

    // -------------------------------------------------------------------------
    // PURE FUNCTIONS — Odyometre Hata Senaryoları
    // -------------------------------------------------------------------------

    /**
     * Seri porttan gelen ham string'i doğrular.
     *
     * Hata senaryoları:
     *   - null mesaj
     *   - boş string
     *   - "RESPONSE" dışında bir içerik
     *
     * @param raw Seri porttan gelen ham string
     * @return    Maybe<String> — başarıysa "RESPONSE", hatalıysa açıklama
     */
    public static Maybe<String> validateSerialMessage(String raw) {
        return Maybe.of(raw)
            .filter(s -> s != null,              "Mesaj null geldi (seri port bağlantısı?)")
            .filter(s -> !s.isBlank(),           "Mesaj boş (seri port gürültüsü?)")
            .filter(s -> s.trim()
                          .equalsIgnoreCase("RESPONSE"),
                         "Beklenmeyen mesaj: '" + raw + "' (sadece RESPONSE bekleniyor)");
    }

    /**
     * Ses şiddeti değerini IEC 60645-1 aralığında doğrular.
     *
     * Geçerli aralık: 0 dB – 120 dB
     *
     * @param intensityDb Doğrulanacak dB değeri
     * @return            Maybe<Integer>
     */
    public static Maybe<Integer> validateIntensity(int intensityDb) {
        return Maybe.of(intensityDb)
            .filter(db -> db >= 0,   "Ses şiddeti negatif olamaz: " + intensityDb + " dB")
            .filter(db -> db <= 120, "Ses şiddeti 120 dB'i aşamaz: " + intensityDb + " dB");
    }

    /**
     * Frekans değerini IEC 60645-1 aralığında doğrular.
     *
     * Geçerli frekanslar: 250, 500, 1000, 2000, 4000, 8000 Hz
     *
     * @param frequencyHz Doğrulanacak frekans değeri
     * @return            Maybe<Integer>
     */
    public static Maybe<Integer> validateFrequency(int frequencyHz) {
        int[] validFrequencies = {250, 500, 1000, 2000, 4000, 8000};

        boolean isValid = false;
        for (int f : validFrequencies) {
            if (f == frequencyHz) { isValid = true; break; }
        }

        return isValid
            ? Maybe.of(frequencyHz)
            : Maybe.failure("Geçersiz frekans: " + frequencyHz
                + " Hz. Geçerli değerler: 250, 500, 1000, 2000, 4000, 8000 Hz");
    }

    /**
     * Testi başlatmadan önce tüm parametreleri doğrular.
     * flatMap ile zincir: biri başarısız olursa zincir kırılır.
     *
     * @param frequencyHz Frekans
     * @param intensityDb Başlangıç şiddeti
     * @return            Maybe<HughsonWestlake.AudioTestState>
     */
    public static Maybe<HughsonWestlake.AudioTestState> initializeTest(int frequencyHz,
                                                                        int intensityDb) {
        return validateFrequency(frequencyHz)
            .flatMap(freq -> validateIntensity(intensityDb))
            .map(db -> HughsonWestlake.AudioTestState.initial(frequencyHz));
    }

    /**
     * Eşik değerini güvenli şekilde alır.
     * Test tamamlanmamışsa açıklayıcı hata döner.
     *
     * @param state Test durumu
     * @return      Maybe<Integer> — eşik dB değeri
     */
    public static Maybe<Integer> safeGetThreshold(HughsonWestlake.AudioTestState state) {
        if (state == null) {
            return Maybe.failure("Test state null (test hiç başlatılmamış)");
        }
        return Maybe.fromOptional(
            HughsonWestlake.getThreshold(state),
            state.phase == HughsonWestlake.AudioTestState.Phase.NO_RESPONSE
                ? "Eşik bulunamadı: 110 dB'de yanıt alınamadı"
                : "Test henüz tamamlanmadı (phase: " + state.phase + ")"
        );
    }

    /**
     * Seri port mesajını alıp HughsonWestlake state'ini güncelleyen
     * tam bir Maybe zinciri.
     *
     * Zincir:
     *   validateSerialMessage → mesajı doğrula
     *   → map → "RESPONSE" ise onPatientResponse() çağır
     *
     * @param rawMessage  Seri porttan gelen ham string
     * @param currentState Mevcut test durumu
     * @return             Güncellenmiş Maybe<AudioTestState>
     */
    public static Maybe<HughsonWestlake.AudioTestState> processSerialInput(
            String rawMessage,
            HughsonWestlake.AudioTestState currentState) {

        if (currentState == null) {
            return Maybe.failure("Test başlatılmamış");
        }

        return validateSerialMessage(rawMessage)
            .map(validMsg -> HughsonWestlake.onPatientResponse(currentState));
    }

    // -------------------------------------------------------------------------
    // Utility class – instantiation engellenir
    // -------------------------------------------------------------------------
    private AudiometryResult() {
        throw new UnsupportedOperationException("Utility class");
    }
}
