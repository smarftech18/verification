package customer.verification;

import com.sap.cds.Row;
import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.persistence.PersistenceService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SalesDocumentBulkMappingService の動作確認テスト。
 *
 * <p>マスタ結合View（SalesDocItemView）が実際に値を返すよう、既存マスタ（CustomerMaster /
 * MaterialMaster / PlantMaster）のキーに合わせた OrderHeader レコードを動的に投入する。
 */
@SpringBootTest
@DisplayName("SalesDocumentBulkMappingService 動作確認テスト")
class SalesDocumentBulkMappingServiceTest {

    private static final String S4_ENTITY        = "ZC_SALESDOCUMENT_SERVICE.ZcSalesDocument";
    private static final String ORDER_HEADER      = "com.example.bp.OrderHeader";
    private static final String HEADER_ENTITY     = "com.example.bp.SalesDocHeader";
    private static final String ITEM_ENTITY       = "com.example.bp.SalesDocItem";
    private static final String DETAIL_ENTITY     = "com.example.bp.SalesDocDetail";

    private static final String TEST_DOC          = "8800000001";
    private static final String ITEM_1            = "000010";
    private static final String ITEM_2            = "000020";

    // 既存マスタ（CustomerMaster/MaterialMaster/PlantMaster）のキーに合わせる
    private static final String SALES_ORG  = "1000";
    private static final String DIST_CH    = "10";
    private static final String DIVISION   = "00";
    private static final String CUSTOMER   = "C0000001"; // -> 株式会社テック商事 / CustomerGroup 01
    private static final String MATERIAL   = "MATNR000001"; // -> ノートPC 15インチ
    private static final String PLANT      = "1000"; // -> 東京配送センター

    @Autowired
    private SalesDocumentBulkMappingService service;

    @Autowired
    private PersistenceService db;

    @BeforeEach
    void seedTestData() {
        // S4売上伝票データ: 1伝票・明細2件
        db.run(Insert.into(S4_ENTITY).entries(java.util.List.of(
            zcRecord(ITEM_1, "001"),
            zcRecord(ITEM_2, "001")
        )));

        // マスタ結合View（SalesDocItemView）の起点となる OrderHeader
        db.run(Insert.into(ORDER_HEADER).entries(java.util.List.of(
            orderHeaderRecord(ITEM_1),
            orderHeaderRecord(ITEM_2)
        )));
    }

    @AfterEach
    void cleanup() {
        db.run(Delete.from(DETAIL_ENTITY).where(r -> r.get("SalesDocument").eq(TEST_DOC)));
        db.run(Delete.from(ITEM_ENTITY).where(r -> r.get("SalesDocument").eq(TEST_DOC)));
        db.run(Delete.from(HEADER_ENTITY).where(r -> r.get("SalesDocument").eq(TEST_DOC)));
        db.run(Delete.from(ORDER_HEADER).where(r -> r.get("SalesDocument").eq(TEST_DOC)));
        db.run(Delete.from(S4_ENTITY).where(r -> r.get("SalesDocument").eq(TEST_DOC)));
    }

    @Test
    @DisplayName("1伝票・明細2件のS4データから、ヘッダ1件・明細2件・詳細2件が正しく組み立てられ、マスタ値も反映されること")
    void createSalesDocuments_buildsHeaderItemsDetailsWithMasterData() {

        SalesDocumentBulkMappingService.BulkCreateResult result =
            service.createSalesDocuments(TEST_DOC);

        assertThat(result.headersCreated()).as("ヘッダは伝票単位で1件").isEqualTo(1);
        assertThat(result.itemsCreated()).as("明細は2件").isEqualTo(2);
        assertThat(result.detailsCreated()).as("詳細は明細と同数（各1レコード=1詳細）").isEqualTo(2);

        Row header = db.run(Select.from(HEADER_ENTITY).where(r -> r.get("SalesDocument").eq(TEST_DOC)))
            .single();
        assertThat(header.get("CustomerName")).as("マスタ結合Viewから取得したCustomerNameが反映される")
            .isEqualTo("株式会社テック商事");
        assertThat(header.get("CustomerGroup")).as("業務ルール: グループ01はVIPに読み替え").isEqualTo("VIP");
        assertThat(header.get("Status")).as("伝票種別ORなので初期ステータスは01").isEqualTo("01");

        Row item1 = db.run(
            Select.from(ITEM_ENTITY)
                  .where(r -> r.get("SalesDocument").eq(TEST_DOC).and(r.get("SalesDocumentItem").eq(ITEM_1)))
        ).single();
        assertThat(item1.get("MaterialName")).as("マスタ結合Viewから取得したMaterialNameが反映される")
            .isEqualTo("ノートPC 15インチ");
        assertThat(item1.get("PlantName")).isEqualTo("東京配送センター");
        assertThat(item1.get("OrderQuantityUnit")).as("値加工: EA -> PC 変換").isEqualTo("PC");
    }

    @Test
    @DisplayName("マスタView該当なし（3-1がスキップされる想定）の場合でも例外を投げず、マスタ由来項目はnullで登録されること")
    void createSalesDocuments_masterViewNotFound_doesNotThrow() {
        String noMasterDoc = "8800000099";
        // OrderHeader は投入しない = SalesDocItemView は該当なし(=master が null) になる
        db.run(Insert.into(S4_ENTITY).entries(java.util.List.of(
            zcRecordForDocument(noMasterDoc, ITEM_1, "001")
        )));

        try {
            SalesDocumentBulkMappingService.BulkCreateResult result =
                service.createSalesDocuments(noMasterDoc);

            assertThat(result.headersCreated()).isEqualTo(1);

            Row header = db.run(Select.from(HEADER_ENTITY).where(r -> r.get("SalesDocument").eq(noMasterDoc)))
                .single();
            assertThat(header.get("CustomerName")).as("マスタ未取得のためnull").isNull();
            assertThat(header.get("CustomerGroup")).as("マスタ未取得のためnull").isNull();
        } finally {
            db.run(Delete.from(DETAIL_ENTITY).where(r -> r.get("SalesDocument").eq(noMasterDoc)));
            db.run(Delete.from(ITEM_ENTITY).where(r -> r.get("SalesDocument").eq(noMasterDoc)));
            db.run(Delete.from(HEADER_ENTITY).where(r -> r.get("SalesDocument").eq(noMasterDoc)));
            db.run(Delete.from(S4_ENTITY).where(r -> r.get("SalesDocument").eq(noMasterDoc)));
        }
    }

    // ====================================================================
    // テストデータ構築ヘルパー
    // ====================================================================

    private Map<String, Object> zcRecord(String itemNumber, String seqNumber) {
        return zcRecordForDocument(TEST_DOC, itemNumber, seqNumber);
    }

    private Map<String, Object> zcRecordForDocument(String document, String itemNumber, String seqNumber) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("SalesDocument", document);
        r.put("SalesDocumentItem", itemNumber);
        r.put("SequentialNumber", seqNumber);
        r.put("SalesOrganization", SALES_ORG);
        r.put("DistributionChannel", DIST_CH);
        r.put("Division", DIVISION);
        r.put("SalesDocumentDate", LocalDate.of(2026, 1, 1));
        r.put("SalesDocumentType", "OR");
        r.put("CustomerID", CUSTOMER);
        r.put("MaterialCode", MATERIAL);
        r.put("OrderQuantity", BigDecimal.TEN);
        r.put("OrderQuantityUnit", "EA");
        r.put("NetAmount", new BigDecimal("1234.567"));
        r.put("Currency", "JPY");
        r.put("Plant", PLANT);
        r.put("StorageLocation", "0001");
        r.put("PricingDate", LocalDate.of(2026, 1, 1));
        r.put("DetailCategory", "PR");
        r.put("DetailText", "  基本価格  ");
        r.put("DetailAmount", new BigDecimal("1234.567"));
        r.put("ConditionType", "PR00");
        r.put("ScheduleLineDate", LocalDate.of(2026, 1, 5));
        r.put("DeliveryScheduleQty", BigDecimal.TEN);
        return r;
    }

    private Map<String, Object> orderHeaderRecord(String itemNumber) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("SalesDocument", TEST_DOC);
        r.put("SalesDocumentItem", itemNumber);
        r.put("SalesOrganization", SALES_ORG);
        r.put("DistributionChannel", DIST_CH);
        r.put("Division", DIVISION);
        r.put("CustomerID", CUSTOMER);
        r.put("MaterialCode", MATERIAL);
        r.put("Plant", PLANT);
        r.put("OrderDate", LocalDate.of(2026, 1, 1));
        r.put("OrderQuantity", BigDecimal.TEN);
        r.put("OrderQuantityUnit", "EA");
        r.put("NetAmount", new BigDecimal("1234.567"));
        r.put("Currency", "JPY");
        r.put("StorageLocation", "0001");
        r.put("PricingDate", LocalDate.of(2026, 1, 1));
        return r;
    }
}
