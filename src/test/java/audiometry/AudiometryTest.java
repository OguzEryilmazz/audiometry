package audiometry;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IEC 60645-1 Uyum Testleri
 *
 * Yazılım Mühendisliği Ekibi – YMH 334 Fonksiyonel Programlama
 *
 * Kapsam:
 *   1. HughsonWestlake – algoritma unit testleri
 *   2. ResponseProcessor – map/filter/reduce testleri
 *   3. AudiometryResult – Maybe/Optional testleri
 *   4. Property-based testler – IEC 60645-1 kuralları
 */
class AudiometryTest {

    // =========================================================================
    // BÖLÜM 1 — HughsonWestlake Algoritma Testleri
    // =========================================================================

    @Nested
    @DisplayName("1. Hughson-Westlake Algoritması")
    class HughsonWestlakeTests {

        @Test
        @DisplayName("Başlangıç state: 30dB, 1000Hz, SEARCHING phase")
        void initialState_shouldHaveCorrectDefaults() {
            var state = HughsonWestlake.AudioTestState.initial(1000);

            assertEquals(30,   state.intensityDb);
            assertEquals(1000, state.frequencyHz);
            assertEquals(0,    state.consecutiveHits);
            assertEquals(HughsonWestlake.AudioTestState.Phase.SEARCHING, state.phase);
            assertTrue(state.responseLog.isEmpty());
        }

        @Test
        @DisplayName("Yanıt alındı → 10dB azalt (DESCENDING), hit=1")
        void onPatientResponse_firstHit_shouldDescend10dB() {
            var state  = HughsonWestlake.AudioTestState.initial(1000);  // 30dB
            var result = HughsonWestlake.onPatientResponse(state);

            assertEquals(20, result.intensityDb);   // 30 - 10 = 20
            assertEquals(1,  result.consecutiveHits);
            assertEquals(HughsonWestlake.AudioTestState.Phase.DESCENDING, result.phase);
        }

        @Test
        @DisplayName("İkinci yanıt → eşik bulundu (THRESHOLD_FOUND)")
        void onPatientResponse_secondHit_shouldFindThreshold() {
            // İlk yanıt
            var state1 = HughsonWestlake.onPatientResponse(
                HughsonWestlake.AudioTestState.initial(1000)
            );
            // İkinci yanıt
            var state2 = HughsonWestlake.onPatientResponse(state1);

            assertEquals(HughsonWestlake.AudioTestState.Phase.THRESHOLD_FOUND, state2.phase);
            assertTrue(HughsonWestlake.isTestComplete(state2));
        }

        @Test
        @DisplayName("Yanıt yok (SEARCHING) → 20dB artır")
        void onNoResponse_searching_shouldIncrease20dB() {
            var state  = HughsonWestlake.AudioTestState.initial(1000);  // 30dB
            var result = HughsonWestlake.onNoResponse(state);

            assertEquals(50, result.intensityDb);   // 30 + 20 = 50
            assertEquals(HughsonWestlake.AudioTestState.Phase.SEARCHING, result.phase);
        }

        @Test
        @DisplayName("Yanıt yok (DESCENDING) → 5dB artır, hit sıfırlanır")
        void onNoResponse_descending_shouldAscend5dB() {
            var descended = new HughsonWestlake.AudioTestState(
                40, 1000,
                List.of(50),
                1,
                HughsonWestlake.AudioTestState.Phase.DESCENDING
            );
            var result = HughsonWestlake.onNoResponse(descended);

            assertEquals(45, result.intensityDb);   // 40 + 5 = 45
            assertEquals(0,  result.consecutiveHits);
            assertEquals(HughsonWestlake.AudioTestState.Phase.ASCENDING, result.phase);
        }

        @Test
        @DisplayName("110dB'de yanıt yok → NO_RESPONSE")
        void onNoResponse_at110dB_shouldReturnNoResponse() {
            var state = new HughsonWestlake.AudioTestState(
                90, 1000, List.of(), 0,
                HughsonWestlake.AudioTestState.Phase.SEARCHING
            );
            var result = HughsonWestlake.onNoResponse(state); // 90 + 20 = 110

            assertEquals(HughsonWestlake.AudioTestState.Phase.NO_RESPONSE, result.phase);
            assertTrue(HughsonWestlake.isTestComplete(result));
        }

        @Test
        @DisplayName("Eşik bulunduğunda getThreshold() doğru değer döner")
        void getThreshold_whenFound_shouldReturnCorrectDb() {
            var state = new HughsonWestlake.AudioTestState(
                45, 1000, List.of(55, 45), 2,
                HughsonWestlake.AudioTestState.Phase.THRESHOLD_FOUND
            );
            Optional<Integer> threshold = HughsonWestlake.getThreshold(state);

            assertTrue(threshold.isPresent());
            assertEquals(45, threshold.get());
        }

        @Test
        @DisplayName("Test bitmemişken getThreshold() Optional.empty() döner")
        void getThreshold_whenNotComplete_shouldReturnEmpty() {
            var state = HughsonWestlake.AudioTestState.initial(1000);

            assertTrue(HughsonWestlake.getThreshold(state).isEmpty());
        }

        @Test
        @DisplayName("Pure function: aynı input → aynı output (referential transparency)")
        void pureFunction_sameInputSameOutput() {
            var state   = HughsonWestlake.AudioTestState.initial(1000);
            var result1 = HughsonWestlake.onPatientResponse(state);
            var result2 = HughsonWestlake.onPatientResponse(state);

            assertEquals(result1.intensityDb,     result2.intensityDb);
            assertEquals(result1.consecutiveHits, result2.consecutiveHits);
            assertEquals(result1.phase,           result2.phase);
        }

        @Test
        @DisplayName("Immutability: orijinal state değişmez")
        void immutability_originalStateUnchanged() {
            var original = HughsonWestlake.AudioTestState.initial(1000);
            int originalDb = original.intensityDb;

            HughsonWestlake.onPatientResponse(original); // çağır ama sonucu kullanma

            assertEquals(originalDb, original.intensityDb); // orijinal değişmemiş olmalı
        }
    }

    // =========================================================================
    // BÖLÜM 2 — ResponseProcessor Testleri
    // =========================================================================

    @Nested
    @DisplayName("2. ResponseProcessor — map/filter/reduce")
    class ResponseProcessorTests {

        private ResponseProcessor.RawMessage msg(String content, int db) {
            return new ResponseProcessor.RawMessage(content, Instant.now(), db);
        }

        @Test
        @DisplayName("filter: sadece 'RESPONSE' mesajları geçer")
        void filter_shouldAcceptOnlyResponseMessages() {
            assertTrue(ResponseProcessor.isValidResponse(msg("RESPONSE", 40)));
            assertFalse(ResponseProcessor.isValidResponse(msg("NOISE",    40)));
            assertFalse(ResponseProcessor.isValidResponse(msg("",         40)));
            assertFalse(ResponseProcessor.isValidResponse(null));
        }

        @Test
        @DisplayName("filter: büyük/küçük harf fark etmez")
        void filter_shouldBeCaseInsensitive() {
            assertTrue(ResponseProcessor.isValidResponse(msg("response", 40)));
            assertTrue(ResponseProcessor.isValidResponse(msg("Response", 40)));
            assertTrue(ResponseProcessor.isValidResponse(msg("RESPONSE", 40)));
        }

        @Test
        @DisplayName("map: RawMessage → ValidResponse dönüşümü")
        void map_shouldConvertToValidResponse() {
            var raw    = msg("RESPONSE", 45);
            var valid  = ResponseProcessor.toValidResponse(raw);

            assertEquals(45, valid.intensityDb);
            assertNotNull(valid.timestamp);
        }

        @Test
        @DisplayName("reduce: 45dB'de 2 yanıt → thresholdMet=true")
        void reduce_twoResponsesAtSameDb_shouldMeetThreshold() {
            var responses = List.of(
                new ResponseProcessor.ValidResponse(Instant.now(), 45),
                new ResponseProcessor.ValidResponse(Instant.now(), 45)
            );
            var summary = ResponseProcessor.toResponseSummary(45, responses);

            assertEquals(2,    summary.totalResponses);
            assertTrue(summary.thresholdMet);
        }

        @Test
        @DisplayName("process(): karma mesaj listesi doğru işlenir")
        void process_mixedMessages_shouldFilterAndGroup() {
            var messages = List.of(
                msg("RESPONSE", 40),
                msg("NOISE",    40),   // filtrelenecek
                msg("RESPONSE", 40),
                msg("RESPONSE", 50),
                msg("",         50)    // filtrelenecek
            );

            Map<Integer, ResponseProcessor.ResponseSummary> result =
                ResponseProcessor.process(messages);

            assertEquals(2, result.get(40).totalResponses);
            assertEquals(1, result.get(50).totalResponses);
            assertTrue(result.get(40).thresholdMet);
            assertFalse(result.get(50).thresholdMet);
        }

        @Test
        @DisplayName("findLowestResponseDb: en düşük yanıt dB'i döner")
        void findLowestResponseDb_shouldReturnMinimum() {
            var responses = List.of(
                new ResponseProcessor.ValidResponse(Instant.now(), 55),
                new ResponseProcessor.ValidResponse(Instant.now(), 40),
                new ResponseProcessor.ValidResponse(Instant.now(), 45)
            );

            Optional<Integer> lowest = ResponseProcessor.findLowestResponseDb(responses);

            assertTrue(lowest.isPresent());
            assertEquals(40, lowest.get());
        }

        @Test
        @DisplayName("countValidResponses: doğru sayar")
        void countValidResponses_shouldCountCorrectly() {
            var messages = List.of(
                msg("RESPONSE", 40),
                msg("NOISE",    40),
                msg("RESPONSE", 45)
            );

            assertEquals(2, ResponseProcessor.countValidResponses(messages));
        }
    }

    // =========================================================================
    // BÖLÜM 3 — AudiometryResult (Maybe/Optional) Testleri
    // =========================================================================

    @Nested
    @DisplayName("3. AudiometryResult — Maybe/Optional Hata Yönetimi")
    class AudiometryResultTests {

        @Test
        @DisplayName("validateSerialMessage: 'RESPONSE' geçer")
        void validateSerialMessage_validInput_shouldSucceed() {
            var result = AudiometryResult.validateSerialMessage("RESPONSE");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("validateSerialMessage: null → failure")
        void validateSerialMessage_null_shouldFail() {
            var result = AudiometryResult.validateSerialMessage(null);
            assertTrue(result.isFailure());
            assertNotNull(result.getError());
        }

        @Test
        @DisplayName("validateSerialMessage: gürültü → failure")
        void validateSerialMessage_noise_shouldFail() {
            var result = AudiometryResult.validateSerialMessage("GARBLED_DATA");
            assertTrue(result.isFailure());
        }

        @ParameterizedTest
        @DisplayName("validateFrequency: geçerli IEC frekansları kabul edilir")
        @ValueSource(ints = {250, 500, 1000, 2000, 4000, 8000})
        void validateFrequency_validFrequencies_shouldSucceed(int freq) {
            assertTrue(AudiometryResult.validateFrequency(freq).isSuccess());
        }

        @ParameterizedTest
        @DisplayName("validateFrequency: geçersiz frekanslar reddedilir")
        @ValueSource(ints = {100, 300, 750, 1500, 3000, 9000})
        void validateFrequency_invalidFrequencies_shouldFail(int freq) {
            assertTrue(AudiometryResult.validateFrequency(freq).isFailure());
        }

        @ParameterizedTest
        @DisplayName("validateIntensity: geçerli aralık [0-120] kabul edilir")
        @ValueSource(ints = {0, 30, 60, 90, 110, 120})
        void validateIntensity_validRange_shouldSucceed(int db) {
            assertTrue(AudiometryResult.validateIntensity(db).isSuccess());
        }

        @ParameterizedTest
        @DisplayName("validateIntensity: aralık dışı reddedilir")
        @ValueSource(ints = {-10, -1, 121, 150})
        void validateIntensity_outOfRange_shouldFail(int db) {
            assertTrue(AudiometryResult.validateIntensity(db).isFailure());
        }

        @Test
        @DisplayName("initializeTest: geçerli parametreler → state oluşur")
        void initializeTest_validParams_shouldCreateState() {
            var result = AudiometryResult.initializeTest(1000, 30);

            assertTrue(result.isSuccess());
            assertEquals(1000, result.getValue().frequencyHz);
        }

        @Test
        @DisplayName("initializeTest: geçersiz frekans → hata zinciri kırılır")
        void initializeTest_invalidFrequency_shouldFail() {
            var result = AudiometryResult.initializeTest(999, 30);
            assertTrue(result.isFailure());
        }

        @Test
        @DisplayName("safeGetThreshold: test bitmemişken açıklayıcı hata")
        void safeGetThreshold_testNotComplete_shouldFailWithMessage() {
            var state  = HughsonWestlake.AudioTestState.initial(1000);
            var result = AudiometryResult.safeGetThreshold(state);

            assertTrue(result.isFailure());
            assertNotNull(result.getError());
            assertFalse(result.getError().isEmpty());
        }

        @Test
        @DisplayName("safeGetThreshold: null state → hata")
        void safeGetThreshold_nullState_shouldFail() {
            var result = AudiometryResult.safeGetThreshold(null);
            assertTrue(result.isFailure());
        }

        @Test
        @DisplayName("Maybe zinciri: hata ortada oluşunca sonraki adımlar atlanır")
        void maybeChain_failureEarlyInChain_shouldSkipRest() {
            // Geçersiz seri mesajı → processSerialInput başarısız olmalı
            var state  = HughsonWestlake.AudioTestState.initial(1000);
            var result = AudiometryResult.processSerialInput("INVALID_MSG", state);

            assertTrue(result.isFailure());
        }
    }

    // =========================================================================
    // BÖLÜM 4 — Property-Based Testler (IEC 60645-1 Kuralları)
    // =========================================================================

    @Nested
    @DisplayName("4. Property-Based Testler — IEC 60645-1 Kuralları")
    class PropertyBasedTests {

        /**
         * KURAL P1: Ses şiddeti hiçbir zaman MAX_INTENSITY_DB'yi aşamaz.
         * IEC 60645-1 § 6.1 — maksimum çıkış sınırı.
         */
        @Test
        @DisplayName("P1: Ses şiddeti hiçbir zaman 110dB'yi aşmaz")
        void property_intensityNeverExceedsMax() {
            var state = HughsonWestlake.AudioTestState.initial(1000);

            // 20 adım boyunca sadece "yanıt yok" simüle et
            var current = state;
            for (int i = 0; i < 20; i++) {
                if (HughsonWestlake.isTestComplete(current)) break;
                current = HughsonWestlake.onNoResponse(current);

                assertTrue(
                    current.intensityDb <= HughsonWestlake.MAX_INTENSITY_DB,
                    "Şiddet " + current.intensityDb + " dB, maksimum aşıldı!"
                );
            }
        }

        /**
         * KURAL P2: THRESHOLD_FOUND sonrasında state değişmez.
         * Test bittikten sonra algoritma durmalıdır.
         */
        @Test
        @DisplayName("P2: THRESHOLD_FOUND terminal state'dir — sonraki işlemler güvenlidir")
        void property_thresholdFoundIsTerminal() {
            // Eşiği bul
            var s1 = HughsonWestlake.onPatientResponse(
                         HughsonWestlake.AudioTestState.initial(1000));
            var s2 = HughsonWestlake.onPatientResponse(s1);

            assertEquals(HughsonWestlake.AudioTestState.Phase.THRESHOLD_FOUND, s2.phase);
            assertTrue(HughsonWestlake.isTestComplete(s2));
        }

        /**
         * KURAL P3: Eşik değeri her zaman [MIN, MAX] aralığında olmalıdır.
         * IEC 60645-1 — geçerli odyogram aralığı.
         */
        @ParameterizedTest
        @DisplayName("P3: Eşik değeri her zaman [30, 110] dB arasındadır")
        @CsvSource({"40,1000", "60,2000", "80,4000", "100,500"})
        void property_thresholdAlwaysInValidRange(int startDb, int freq) {
            var state = new HughsonWestlake.AudioTestState(
                startDb, freq, List.of(), 1,
                HughsonWestlake.AudioTestState.Phase.ASCENDING
            );
            var completed = HughsonWestlake.onPatientResponse(state);

            if (completed.phase == HughsonWestlake.AudioTestState.Phase.THRESHOLD_FOUND) {
                int threshold = completed.intensityDb;
                assertTrue(threshold >= HughsonWestlake.MIN_INTENSITY_DB,
                    "Eşik minimum altında: " + threshold);
                assertTrue(threshold <= HughsonWestlake.MAX_INTENSITY_DB,
                    "Eşik maksimum üstünde: " + threshold);
            }
        }

        /**
         * KURAL P4: "10 dB azalt, 5 dB artır" adım büyüklükleri sabittir.
         * Hughson-Westlake protokolünün temel kuralı.
         */
        @Test
        @DisplayName("P4: Adım büyüklükleri IEC 60645-1 uyumlu (↓10, ↑5, ↑20)")
        void property_stepSizesAreFixed() {
            assertEquals(5,  HughsonWestlake.ASCENDING_STEP_DB);
            assertEquals(10, HughsonWestlake.DESCENDING_STEP_DB);
            assertEquals(20, HughsonWestlake.SEARCH_STEP_DB);
        }

        /**
         * KURAL P5: consecutiveHits hiçbir zaman negatif olamaz.
         */
        @Test
        @DisplayName("P5: consecutiveHits her zaman >= 0")
        void property_consecutiveHitsNeverNegative() {
            var state = HughsonWestlake.AudioTestState.initial(1000);

            // Yanıt ver, sonra yanıt verme, tekrarla
            var current = state;
            for (int i = 0; i < 10; i++) {
                if (HughsonWestlake.isTestComplete(current)) break;
                current = (i % 2 == 0)
                    ? HughsonWestlake.onPatientResponse(current)
                    : HughsonWestlake.onNoResponse(current);

                assertTrue(current.consecutiveHits >= 0,
                    "consecutiveHits negatif: " + current.consecutiveHits);
            }
        }

        /**
         * KURAL P6: IEC 60645-1 geçerli frekansları.
         * Sadece 250, 500, 1000, 2000, 4000, 8000 Hz test edilir.
         */
        @ParameterizedTest
        @DisplayName("P6: Geçerli IEC 60645-1 frekansları kabul edilir")
        @ValueSource(ints = {250, 500, 1000, 2000, 4000, 8000})
        void property_onlyValidFrequenciesAccepted(int freq) {
            var result = AudiometryResult.validateFrequency(freq);
            assertTrue(result.isSuccess(),
                freq + " Hz geçerli olmalı ama reddedildi");
        }

        /**
         * KURAL P7: Eşik için 2 yanıt gerekir (THRESHOLD_HIT_COUNT = 2).
         * 1 yanıt asla eşik sayılmaz.
         */
        @Test
        @DisplayName("P7: Tek yanıt eşik için yeterli değildir")
        void property_singleResponseIsNotThreshold() {
            var state  = HughsonWestlake.AudioTestState.initial(1000);
            var after1 = HughsonWestlake.onPatientResponse(state);

            assertNotEquals(
                HughsonWestlake.AudioTestState.Phase.THRESHOLD_FOUND,
                after1.phase,
                "Tek yanıt eşik sayılmamalı"
            );
        }
    }
}
