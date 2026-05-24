package audiometry;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * RESPONSE Mesajı İşleme Modülü
 *
 * Yazılım Mühendisliği Ekibi – YMH 334 Fonksiyonel Programlama
 *
 * Seri porttan gelen ham "RESPONSE" string'lerini
 * map / filter / reduce zinciriyle işler.
 *
 * Tüm fonksiyonlar PURE FUNCTION'dır.
 */
public final class ResponseProcessor {

    // -------------------------------------------------------------------------
    // Immutable Veri Yapıları
    // -------------------------------------------------------------------------

    /**
     * Seri porttan gelen tek bir ham mesajı temsil eder.
     * Zaman damgası + içerik.
     */
    public static final class RawMessage {
        public final String  content;    // Örn: "RESPONSE", "NOISE", ""
        public final Instant timestamp;
        public final int     intensityDb; // O anki ses şiddeti (state'den gelir)

        public RawMessage(String content, Instant timestamp, int intensityDb) {
            this.content     = content;
            this.timestamp   = timestamp;
            this.intensityDb = intensityDb;
        }
    }

    /**
     * İşlenmiş, doğrulanmış bir hasta yanıtı.
     * Yalnızca geçerli RESPONSE mesajlarından üretilir.
     */
    public static final class ValidResponse {
        public final Instant timestamp;
        public final int     intensityDb;

        public ValidResponse(Instant timestamp, int intensityDb) {
            this.timestamp   = timestamp;
            this.intensityDb = intensityDb;
        }

        @Override
        public String toString() {
            return String.format("ValidResponse{%ddB @ %s}", intensityDb, timestamp);
        }
    }

    /**
     * Belirli bir dB seviyesindeki test oturumunun özeti.
     * reduce() ile üretilir.
     */
    public static final class ResponseSummary {
        public final int     intensityDb;
        public final int     totalResponses;
        public final boolean thresholdMet;   // consecutiveHits >= 2 mi?

        public ResponseSummary(int intensityDb, int totalResponses, boolean thresholdMet) {
            this.intensityDb     = intensityDb;
            this.totalResponses  = totalResponses;
            this.thresholdMet    = thresholdMet;
        }

        @Override
        public String toString() {
            return String.format(
                "ResponseSummary{%ddB, yanıt=%d, eşik=%s}",
                intensityDb, totalResponses, thresholdMet ? "BULUNDU" : "BULUNAMADI"
            );
        }
    }

    // -------------------------------------------------------------------------
    // ANA İŞLEME ZİNCİRİ
    // -------------------------------------------------------------------------

    /**
     * Ham mesaj listesini tam zincirle işler.
     *
     * Zincir:
     *   1. filter  → sadece "RESPONSE" içerenleri al
     *   2. map     → RawMessage → ValidResponse'a dönüştür
     *   3. reduce  → dB başına yanıt sayısını topla
     *
     * @param rawMessages Seri porttan gelen ham mesaj listesi
     * @return            dB → ResponseSummary haritası (immutable)
     */
    public static Map<Integer, ResponseSummary> process(List<RawMessage> rawMessages) {
        return rawMessages.stream()

            // ADIM 1 — filter: sadece geçerli "RESPONSE" mesajlarını geç
            .filter(ResponseProcessor::isValidResponse)

            // ADIM 2 — map: RawMessage → ValidResponse
            .map(ResponseProcessor::toValidResponse)

            // ADIM 3 — reduce benzeri groupingBy: dB başına say
            .collect(Collectors.groupingBy(
                r -> r.intensityDb,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    responses -> toResponseSummary(responses.get(0).intensityDb, responses)
                )
            ));
    }

    // -------------------------------------------------------------------------
    // PURE FUNCTIONS — Zincir Adımları
    // -------------------------------------------------------------------------

    /**
     * FILTER adımı (pure predicate).
     * Mesaj içeriği tam olarak "RESPONSE" mi?
     *
     * @param msg Ham mesaj
     * @return    true → geçerli, false → gürültü/boş mesaj
     */
    public static boolean isValidResponse(RawMessage msg) {
        return msg != null
            && msg.content != null
            && msg.content.trim().equalsIgnoreCase("RESPONSE");
    }

    /**
     * MAP adımı: RawMessage → ValidResponse dönüşümü (pure).
     *
     * @param msg Geçerliliği doğrulanmış ham mesaj
     * @return    İşlenmiş ValidResponse nesnesi
     */
    public static ValidResponse toValidResponse(RawMessage msg) {
        return new ValidResponse(msg.timestamp, msg.intensityDb);
    }

    /**
     * REDUCE adımı: ValidResponse listesini ResponseSummary'e indirger (pure).
     *
     * @param intensityDb Hangi dB seviyesi için özet oluşturuluyor
     * @param responses   O dB seviyesindeki tüm yanıtlar
     * @return            Özet nesne
     */
    public static ResponseSummary toResponseSummary(int intensityDb,
                                                     List<ValidResponse> responses) {
        // reduce: listedeki eleman sayısını 0'dan başlayarak topla
        int count = responses.stream()
            .reduce(0,
                (acc, r) -> acc + 1,
                Integer::sum);

        boolean thresholdMet = count >= HughsonWestlake.THRESHOLD_HIT_COUNT;

        return new ResponseSummary(intensityDb, count, thresholdMet);
    }

    // -------------------------------------------------------------------------
    // YARDIMCI PURE FUNCTIONS
    // -------------------------------------------------------------------------

    /**
     * Belirli bir dB seviyesindeki yanıtları filtreler.
     * map/filter zinciri örneği.
     *
     * @param responses   Tüm yanıtlar
     * @param intensityDb Filtrelenecek dB seviyesi
     * @return            Sadece o dB seviyesindeki yanıtlar
     */
    public static List<ValidResponse> filterByIntensity(List<ValidResponse> responses,
                                                         int intensityDb) {
        return responses.stream()
            .filter(r -> r.intensityDb == intensityDb)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Tüm yanıtların dB değerlerini bir listeye çıkarır (map örneği).
     *
     * @param responses Yanıt listesi
     * @return          dB değerleri listesi
     */
    public static List<Integer> extractIntensities(List<ValidResponse> responses) {
        return responses.stream()
            .map(r -> r.intensityDb)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * En düşük yanıt alınan dB değerini bulur (reduce örneği).
     * Eşik tahmini için kullanılır.
     *
     * @param responses Yanıt listesi
     * @return          En düşük dB (Optional — liste boş olabilir)
     */
    public static Optional<Integer> findLowestResponseDb(List<ValidResponse> responses) {
        return responses.stream()
            .map(r -> r.intensityDb)
            .reduce(Integer::min);
    }

    /**
     * Ham mesaj akışından sadece RESPONSE içerenlerin sayısını verir.
     * Saf reduce örneği.
     *
     * @param messages Ham mesaj listesi
     * @return         Geçerli RESPONSE sayısı
     */
    public static int countValidResponses(List<RawMessage> messages) {
        return messages.stream()
            .filter(ResponseProcessor::isValidResponse)
            .reduce(0,
                (acc, msg) -> acc + 1,
                Integer::sum);
    }

    /**
     * Stream'i ResponseSummary listesine dönüştürür.
     * Bilgisayar Müh. ekibine raporlama için kullanılır.
     *
     * @param summaryMap process() çıktısı
     * @return           Sıralı özet listesi (dB'e göre artan)
     */
    public static List<ResponseSummary> toSortedSummaryList(
            Map<Integer, ResponseSummary> summaryMap) {

        return summaryMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .collect(Collectors.toUnmodifiableList());
    }

    // -------------------------------------------------------------------------
    // Utility class – instantiation engellenir
    // -------------------------------------------------------------------------
    private ResponseProcessor() {
        throw new UnsupportedOperationException("Utility class");
    }
}
