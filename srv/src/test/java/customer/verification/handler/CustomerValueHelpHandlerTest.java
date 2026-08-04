package customer.verification.handler;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnStructuredTypeRef;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CustomerValueHelpHandler の READ / ページング検証テスト。
 *
 * <p>【テスト方針】
 * 4000件超のデータを {@code @BeforeEach} で動的に seed し、Value Help のスクロール
 * （$top/$skip）で 1000件の壁を超えて正しくページングされることを確認する。
 * db/data/API_BUSINESS_PARTNER.A_BusinessPartner.csv は変更しない（他用途への影響を避けるため）。
 *
 * <p>データは {@link PersistenceService} 経由で直接 A_BusinessPartner テーブルへ投入する。
 * このプロジェクトでは外部サービス（API_BUSINESS_PARTNER）は本番destination未設定時、
 * ローカルDB（H2）にモックされるため、db.run(Insert...) で投入したデータが
 * そのまま CustomerValueHelpHandler 経由の READ で参照できる。
 */
@SpringBootTest
@DisplayName("CustomerValueHelpHandler ページングテスト")
class CustomerValueHelpHandlerTest {

    private static final String S4_ENTITY = "API_BUSINESS_PARTNER.A_BusinessPartner";
    private static final String VH_ENTITY = "BusinessPartnerService.CustomerValueHelp";

    // CSV既存データ（"C0000001" 等）と衝突しないよう、テスト専用に数字のみのID帯を使う
    private static final String TEST_ID_PREFIX = "9";
    private static final int TOTAL_TEST_RECORDS = 4200;

    @Autowired
    private PersistenceService db;

    @Autowired
    private CdsRuntime runtime;

    @BeforeEach
    void seedLargeDataset() {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 1; i <= TOTAL_TEST_RECORDS; i++) {
            String id = TEST_ID_PREFIX + String.format("%09d", i); // 10桁固定
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("BusinessPartner", id);
            record.put("BusinessPartnerFullName", "テスト取引先" + i);
            record.put("BusinessPartnerCategory", "2");
            record.put("BusinessPartnerIsBlocked", false);
            record.put("CreationDate", LocalDate.of(2026, 1, 1));
            records.add(record);
        }
        db.run(Insert.into(S4_ENTITY).entries(records));
    }

    @AfterEach
    void cleanup() {
        db.run(Delete.from(S4_ENTITY).where(r -> r.get("BusinessPartner").startsWith(TEST_ID_PREFIX)));
    }

    private CqnService businessPartnerService() {
        return (CqnService) runtime.getServiceCatalog()
            .getService(CqnService.class, "BusinessPartnerService");
    }

    // ====================================================================
    // ページング（本題: 1000件の壁を超えられるか）
    // ====================================================================

    @Test
    @DisplayName("1000件を超えるデータでも、$skipによるスクロールで全件（4200件）にたどり着けること")
    void scrolling_beyond1000_records_returns_all_pages() {
        CqnService service = businessPartnerService();

        int pageSize = 1000;
        int skip = 0;
        int totalFetched = 0;
        int pages = 0;

        while (true) {
            Result result = service.run(
                Select.from(VH_ENTITY)
                      .where(r -> r.get("BusinessPartner").startsWith(TEST_ID_PREFIX))
                      .limit(pageSize, skip)
            );
            List<Row> rows = result.list();
            totalFetched += rows.size();
            pages++;

            if (rows.size() < pageSize) {
                break; // 返却件数 < 要求$top ＝ 最終ページ（VHの成長判定と同じロジック）
            }
            skip += pageSize;
            assertThat(pages).as("無限ループ防止").isLessThan(10);
        }

        assertThat(totalFetched).as("全件取得できること（1000件で頭打ちにならない）")
            .isEqualTo(TOTAL_TEST_RECORDS);
        assertThat(pages).as("1000件区切りで複数ページに分かれること").isGreaterThan(1);
    }

    @Test
    @DisplayName("2ページ目（$skip=1000）の返却件数が要求$topと一致し、VHの成長判定が壊れていないこと")
    void secondPage_returnsFullRequestedTop() {
        CqnService service = businessPartnerService();

        Result result = service.run(
            Select.from(VH_ENTITY)
                  .where(r -> r.get("BusinessPartner").startsWith(TEST_ID_PREFIX))
                  .limit(1000, 1000)
        );

        assertThat(result.list()).hasSize(1000);
    }

    // ====================================================================
    // WHERE句がそのまま引き継がれること（ref のみ差し替え、where は自動継承）
    // ====================================================================

    @Test
    @DisplayName("WHERE句（取引先IDの完全一致）が正しくS4側に引き継がれ、対象の1件だけ絞り込めること")
    void where_isForwardedUnchanged_filtersCorrectly() {
        String targetId = TEST_ID_PREFIX + String.format("%09d", 42);

        CqnService service = businessPartnerService();
        Result result = service.run(
            Select.from(VH_ENTITY).where(r -> r.get("BusinessPartner").eq(targetId))
        );

        List<Row> rows = result.list();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("BusinessPartner")).isEqualTo(targetId);
    }

    // ====================================================================
    // ナビゲーション経由（ref セグメントのキー述語 → WHERE への変換）
    // ====================================================================

    private static final String LOCAL_BP_ENTITY = "com.example.bp.BusinessPartners";
    private static final String NAV_ENTITY      = "BusinessPartnerService.BusinessPartners";

    @Test
    @DisplayName("BusinessPartners(ID)/to_valueHelpMatch のナビゲーション経由でも、親のキー述語がS4検索条件に正しく変換されること")
    void navigationRead_translatesParentKeyToS4Filter() {
        UUID localId = UUID.randomUUID();
        String matchingBpId = TEST_ID_PREFIX + String.format("%09d", 1); // 既存seedデータの1件と一致させる

        db.run(Insert.into(LOCAL_BP_ENTITY).entry(Map.of(
            "ID", localId,
            "businessPartnerID", matchingBpId,
            "fullName", "ナビゲーションテスト用BP"
        )));

        try {
            CqnStructuredTypeRef navRef = CQL.to(List.of(
                CQL.refSegment(NAV_ENTITY, CQL.get("ID").eq(localId)),
                CQL.refSegment("to_valueHelpMatch")
            )).asRef();

            CqnService service = businessPartnerService();
            Result result = service.run(Select.from(navRef));

            List<Row> rows = result.list();
            assertThat(rows).as("親BPのbusinessPartnerIDに一致する1件だけ返る").hasSize(1);
            assertThat(rows.get(0).get("BusinessPartner")).isEqualTo(matchingBpId);
        } finally {
            db.run(Delete.from(LOCAL_BP_ENTITY).where(b -> b.get("ID").eq(localId)));
        }
    }
}
