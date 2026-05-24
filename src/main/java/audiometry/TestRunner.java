package audiometry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Manuel Test Koşucusu — JUnit olmadan tüm senaryoları doğrular.
 */
public class TestRunner {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== Audiometry Test Runner ===\n");

        testHughsonWestlake();
        testResponseProcessor();
        testAudiometryResult();
        testProperties();

        System.out.println("\n==============================");
        System.out.printf("✅ Geçti: %d   ❌ Kaldı: %d%n", passed, failed);
        System.out.println("==============================");
    }

    // ---------------------------------------------------------------
    static void testHughsonWestlake() {
        System.out.println("--- 1. HughsonWestlake ---");

        // Başlangıç state
        var s = HughsonWestlake.AudioTestState.initial(1000);
        check("Başlangıç 30dB",    s.intensityDb == 30);
        check("Başlangıç 1000Hz",  s.frequencyHz == 1000);
        check("Başlangıç SEARCHING", s.phase == HughsonWestlake.AudioTestState.Phase.SEARCHING);

        // İlk yanıt → 10dB azalt
        var s1 = HughsonWestlake.onPatientResponse(s);
        check("İlk yanıt: 20dB",   s1.intensityDb == 20);
        check("İlk yanıt: hit=1",  s1.consecutiveHits == 1);
        check("İlk yanıt: DESCENDING", s1.phase == HughsonWestlake.AudioTestState.Phase.DESCENDING);

        // İkinci yanıt → eşik bulundu
        var s2 = HughsonWestlake.onPatientResponse(s1);
        check("İkinci yanıt: THRESHOLD_FOUND",
            s2.phase == HughsonWestlake.AudioTestState.Phase.THRESHOLD_FOUND);
        check("Test tamamlandı", HughsonWestlake.isTestComplete(s2));

        // Yanıt yok (SEARCHING) → 20dB artır
        var sNoResp = HughsonWestlake.onNoResponse(s);
        check("Yanıt yok: 50dB",   sNoResp.intensityDb == 50);

        // 110dB sınırı
        var s90 = new HughsonWestlake.AudioTestState(90, 1000,
            List.of(), 0, HughsonWestlake.AudioTestState.Phase.SEARCHING);
        var sMax = HughsonWestlake.onNoResponse(s90);
        check("110dB → NO_RESPONSE",
            sMax.phase == HughsonWestlake.AudioTestState.Phase.NO_RESPONSE);

        // Immutability
        check("Orijinal state değişmedi", s.intensityDb == 30);

        // getThreshold
        check("Threshold Optional.empty (test bitmedi)",
            HughsonWestlake.getThreshold(s).isEmpty());
        check("Threshold 20dB (bulundu)",
            HughsonWestlake.getThreshold(s2).orElse(-1) == 20);
    }

    // ---------------------------------------------------------------
    static void testResponseProcessor() {
        System.out.println("\n--- 2. ResponseProcessor ---");

        var valid = new ResponseProcessor.RawMessage("RESPONSE", Instant.now(), 40);
        var noise = new ResponseProcessor.RawMessage("NOISE",    Instant.now(), 40);
        var empty = new ResponseProcessor.RawMessage("",         Instant.now(), 40);

        check("filter: RESPONSE geçer",  ResponseProcessor.isValidResponse(valid));
        check("filter: NOISE geçmez",   !ResponseProcessor.isValidResponse(noise));
        check("filter: boş geçmez",     !ResponseProcessor.isValidResponse(empty));
        check("filter: null geçmez",    !ResponseProcessor.isValidResponse(null));
        check("filter: küçük harf",
            ResponseProcessor.isValidResponse(
                new ResponseProcessor.RawMessage("response", Instant.now(), 40)));

        // map
        var vr = ResponseProcessor.toValidResponse(valid);
        check("map: dB doğru", vr.intensityDb == 40);

        // reduce / process
        var messages = List.of(
            new ResponseProcessor.RawMessage("RESPONSE", Instant.now(), 40),
            new ResponseProcessor.RawMessage("NOISE",    Instant.now(), 40),
            new ResponseProcessor.RawMessage("RESPONSE", Instant.now(), 40),
            new ResponseProcessor.RawMessage("RESPONSE", Instant.now(), 50)
        );
        var result = ResponseProcessor.process(messages);
        check("process: 40dB = 2 yanıt", result.get(40).totalResponses == 2);
        check("process: 40dB eşik met",  result.get(40).thresholdMet);
        check("process: 50dB = 1 yanıt", result.get(50).totalResponses == 1);
        check("process: 50dB eşik değil", !result.get(50).thresholdMet);

        // findLowestResponseDb
        var responses = List.of(
            new ResponseProcessor.ValidResponse(Instant.now(), 55),
            new ResponseProcessor.ValidResponse(Instant.now(), 40),
            new ResponseProcessor.ValidResponse(Instant.now(), 45)
        );
        Optional<Integer> lowest = ResponseProcessor.findLowestResponseDb(responses);
        check("findLowest: 40dB", lowest.orElse(-1) == 40);

        // countValidResponses
        check("count: 2 valid", ResponseProcessor.countValidResponses(messages) == 3);
    }

    // ---------------------------------------------------------------
    static void testAudiometryResult() {
        System.out.println("\n--- 3. AudiometryResult (Maybe) ---");

        // validateSerialMessage
        check("validate: RESPONSE → success",
            AudiometryResult.validateSerialMessage("RESPONSE").isSuccess());
        check("validate: null → failure",
            AudiometryResult.validateSerialMessage(null).isFailure());
        check("validate: gürültü → failure",
            AudiometryResult.validateSerialMessage("GARBLED").isFailure());
        check("validate: boş → failure",
            AudiometryResult.validateSerialMessage("").isFailure());

        // validateFrequency
        for (int f : new int[]{250, 500, 1000, 2000, 4000, 8000}) {
            check("freq " + f + "Hz geçerli",
                AudiometryResult.validateFrequency(f).isSuccess());
        }
        check("freq 999Hz geçersiz",
            AudiometryResult.validateFrequency(999).isFailure());

        // validateIntensity
        check("intensity 30dB geçerli",
            AudiometryResult.validateIntensity(30).isSuccess());
        check("intensity -1dB geçersiz",
            AudiometryResult.validateIntensity(-1).isFailure());
        check("intensity 121dB geçersiz",
            AudiometryResult.validateIntensity(121).isFailure());

        // initializeTest
        check("initTest: geçerli params → success",
            AudiometryResult.initializeTest(1000, 30).isSuccess());
        check("initTest: geçersiz frekans → failure",
            AudiometryResult.initializeTest(999, 30).isFailure());

        // safeGetThreshold
        var state = HughsonWestlake.AudioTestState.initial(1000);
        check("threshold: test bitmedi → failure",
            AudiometryResult.safeGetThreshold(state).isFailure());
        check("threshold: null → failure",
            AudiometryResult.safeGetThreshold(null).isFailure());

        // processSerialInput
        check("processSerial: RESPONSE → success",
            AudiometryResult.processSerialInput("RESPONSE", state).isSuccess());
        check("processSerial: geçersiz mesaj → failure",
            AudiometryResult.processSerialInput("INVALID", state).isFailure());
        check("processSerial: null state → failure",
            AudiometryResult.processSerialInput("RESPONSE", null).isFailure());
    }

    // ---------------------------------------------------------------
    static void testProperties() {
        System.out.println("\n--- 4. Property-Based (IEC 60645-1) ---");

        // P1: Şiddet hiçbir zaman MAX'ı aşmaz
        var cur = HughsonWestlake.AudioTestState.initial(1000);
        boolean maxViolated = false;
        for (int i = 0; i < 30; i++) {
            if (HughsonWestlake.isTestComplete(cur)) break;
            cur = HughsonWestlake.onNoResponse(cur);
            if (cur.intensityDb > HughsonWestlake.MAX_INTENSITY_DB) maxViolated = true;
        }
        check("P1: Şiddet MAX'ı aşmaz", !maxViolated);

        // P2: 2 yanıt → eşik bulunur
        var s1 = HughsonWestlake.onPatientResponse(HughsonWestlake.AudioTestState.initial(1000));
        var s2 = HughsonWestlake.onPatientResponse(s1);
        check("P2: 2 yanıt → THRESHOLD_FOUND",
            s2.phase == HughsonWestlake.AudioTestState.Phase.THRESHOLD_FOUND);

        // P3: Tek yanıt eşik değil
        check("P3: 1 yanıt eşik sayılmaz",
            s1.phase != HughsonWestlake.AudioTestState.Phase.THRESHOLD_FOUND);

        // P4: Adım büyüklükleri
        check("P4: ASCENDING_STEP = 5",   HughsonWestlake.ASCENDING_STEP_DB  == 5);
        check("P4: DESCENDING_STEP = 10", HughsonWestlake.DESCENDING_STEP_DB == 10);
        check("P4: SEARCH_STEP = 20",     HughsonWestlake.SEARCH_STEP_DB     == 20);

        // P5: consecutiveHits hiçbir zaman negatif değil
        var c = HughsonWestlake.AudioTestState.initial(1000);
        boolean hitsNegative = false;
        for (int i = 0; i < 10; i++) {
            if (HughsonWestlake.isTestComplete(c)) break;
            c = (i % 2 == 0)
                ? HughsonWestlake.onPatientResponse(c)
                : HughsonWestlake.onNoResponse(c);
            if (c.consecutiveHits < 0) hitsNegative = true;
        }
        check("P5: consecutiveHits >= 0", !hitsNegative);
    }

    // ---------------------------------------------------------------
    static void check(String name, boolean condition) {
        if (condition) {
            System.out.printf("  ✅ %s%n", name);
            passed++;
        } else {
            System.out.printf("  ❌ %s%n", name);
            failed++;
        }
    }
}
