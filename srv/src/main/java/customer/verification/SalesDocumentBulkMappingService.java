package customer.verification;

import cds.gen.com.example.bp.SalesDocDetail;
import cds.gen.com.example.bp.SalesDocHeader;
import cds.gen.com.example.bp.SalesDocItem;
import cds.gen.zc_salesdocument_service.ZcSalesdocumentService;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.persistence.PersistenceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 実務の売上伝票データ作成フローを再現した実装（レビュー対象サンプル）。
 *
 * <p>{@link SalesDocumentHandler} との違い: あちらは S4 レコードを単一ループで
 * 流しながら Seen-Map で新規/既存判定するのに対し、本クラスは以下の4ステップを
 * 明示的に分離した構成になっている。
 *
 * <ol>
 *   <li>STEP1: S4 売上伝票データ API から登録対象データを取得</li>
 *   <li>STEP2: 取得結果を伝票番号ごとにグルーピング</li>
 *   <li>STEP3: 伝票番号単位でループし、ヘッダ/明細/詳細を
 *       {@code Map<キー項目のset, エンティティ>} として組み立てる
 *       <ul>
 *         <li>STEP3-1: レコードごとに受注データ（複数マスタ結合View）を取得</li>
 *         <li>STEP3-2: 取得したマスタ値をもとに設定値を加工し、マッピング対象項目に設定</li>
 *       </ul>
 *   </li>
 *   <li>STEP4: 組み立てたヘッダ/明細/詳細エンティティを対象テーブルに一括 INSERT</li>
 * </ol>
 *
 * <p>NOTE: 実務では3テーブル合計で約100項目、値加工メソッドは約30個ある想定だが、
 * ここではパターンを示すため代表的な項目・メソッドのみを抜粋している。
 */
@Component
public class SalesDocumentBulkMappingService {

    private static final Logger log = LoggerFactory.getLogger(SalesDocumentBulkMappingService.class);

    private static final String S4_ENTITY     = "ZC_SALESDOCUMENT_SERVICE.ZcSalesDocument";
    private static final String MASTER_VIEW   = "SalesDocumentService.SalesDocItemView";
    private static final String HEADER_ENTITY = "com.example.bp.SalesDocHeader";
    private static final String ITEM_ENTITY   = "com.example.bp.SalesDocItem";
    private static final String DETAIL_ENTITY = "com.example.bp.SalesDocDetail";

    @Autowired
    private ZcSalesdocumentService s4Service;

    @Autowired
    private PersistenceService db;

    /**
     * 売上伝票データ作成のエントリポイント。
     *
     * @param salesDocument 対象伝票番号（未指定で全件）
     */
    public BulkCreateResult createSalesDocuments(String salesDocument) {

        // STEP1: S4 売上伝票データ API から登録対象データを取得
        List<Row> targetRecords = fetchTargetRecords(salesDocument);
        log.info("STEP1: S4から{}件取得", targetRecords.size());

        // STEP2: 伝票番号ごとにグルーピング
        Map<String, List<Row>> recordsByDocument = groupByDocumentNumber(targetRecords);
        log.info("STEP2: {}伝票にグルーピング", recordsByDocument.size());

        // STEP3: 伝票番号単位でループし、ヘッダ/明細/詳細を Map<キー, エンティティ> として組み立てる
        Map<String, SalesDocHeader> headerByKey = new LinkedHashMap<>();
        Map<String, SalesDocItem> itemByKey     = new LinkedHashMap<>();
        Map<String, SalesDocDetail> detailByKey = new LinkedHashMap<>();

        for (Map.Entry<String, List<Row>> documentEntry : recordsByDocument.entrySet()) {
            for (Row record : documentEntry.getValue()) {

                // STEP3-1: レコードごとに受注データ（複数マスタ結合View）を取得
                Row master = fetchOrderMasterView(record);

                // STEP3-2: マスタ値をもとに設定値を加工し、マッピング対象項目に設定
                headerByKey.computeIfAbsent(headerKey(record), k -> mapToHeader(record, master));
                itemByKey.computeIfAbsent(itemKey(record), k -> mapToItem(record, master));
                detailByKey.put(detailKey(record), mapToDetail(record, master)); // 詳細は連番単位で必ず新規
            }
        }
        log.info("STEP3: ヘッダ{}件, 明細{}件, 詳細{}件を組み立て",
            headerByKey.size(), itemByKey.size(), detailByKey.size());

        // STEP4: 対象テーブルに一括 INSERT
        db.run(Insert.into(HEADER_ENTITY).entries(headerByKey.values()));
        db.run(Insert.into(ITEM_ENTITY).entries(itemByKey.values()));
        db.run(Insert.into(DETAIL_ENTITY).entries(detailByKey.values()));

        return new BulkCreateResult(headerByKey.size(), itemByKey.size(), detailByKey.size());
    }

    // ====================================================================
    // STEP1
    // ====================================================================

    private List<Row> fetchTargetRecords(String salesDocument) {
        var query = Select.from(S4_ENTITY);
        if (salesDocument != null && !salesDocument.isBlank()) {
            query = query.where(r -> r.get("SalesDocument").eq(salesDocument));
        }
        Result result = s4Service.run(query);
        return result.list();
    }

    // ====================================================================
    // STEP2
    // ====================================================================

    private Map<String, List<Row>> groupByDocumentNumber(List<Row> records) {
        return records.stream().collect(Collectors.groupingBy(
            r -> (String) r.get("SalesDocument"),
            LinkedHashMap::new,
            Collectors.toList()
        ));
    }

    // ====================================================================
    // STEP3: キー生成
    // ====================================================================

    private String headerKey(Row r) {
        return (String) r.get("SalesDocument");
    }

    private String itemKey(Row r) {
        return r.get("SalesDocument") + "_" + r.get("SalesDocumentItem");
    }

    private String detailKey(Row r) {
        return itemKey(r) + "_" + r.get("SequentialNumber");
    }

    // ====================================================================
    // STEP3-1: 受注データ（複数マスタ結合View）取得
    // ====================================================================

    private Row fetchOrderMasterView(Row s4Record) {
        Result result = db.run(
            Select.from(MASTER_VIEW)
                  .where(v -> v.get("SalesDocument")      .eq((String) s4Record.get("SalesDocument"))
                         .and(v.get("SalesDocumentItem")   .eq((String) s4Record.get("SalesDocumentItem")))
                         .and(v.get("SalesOrganization")   .eq((String) s4Record.get("SalesOrganization")))
                         .and(v.get("DistributionChannel") .eq((String) s4Record.get("DistributionChannel")))
                         .and(v.get("Division")            .eq((String) s4Record.get("Division")))
                         .and(v.get("CustomerID")          .eq((String) s4Record.get("CustomerID"))))
        );
        return result.first().orElse(null);
    }

    // ====================================================================
    // STEP3-2: エンティティ組み立て（値加工メソッドを呼び出す）
    // ====================================================================

    private SalesDocHeader mapToHeader(Row rec, Row master) {
        SalesDocHeader h = SalesDocHeader.create();
        h.setSalesDocument      ((String) rec.get("SalesDocument"));
        h.setSalesOrganization  ((String) rec.get("SalesOrganization"));
        h.setDistributionChannel((String) rec.get("DistributionChannel"));
        h.setDivision            ((String) rec.get("Division"));
        h.setSalesDocumentType  ((String) rec.get("SalesDocumentType"));
        h.setCustomerID          ((String) rec.get("CustomerID"));
        h.setCurrency            ((String) rec.get("Currency"));
        h.setTotalNetAmount     (BigDecimal.ZERO);
        h.setStatus              (mapInitialStatus(rec));
        h.setCustomerName       (mapCustomerName(master));
        h.setCustomerGroup      (mapCustomerGroup(master));
        return h;
    }

    private SalesDocItem mapToItem(Row rec, Row master) {
        SalesDocItem item = SalesDocItem.create();
        item.setSalesDocument    ((String) rec.get("SalesDocument"));
        item.setSalesDocumentItem((String) rec.get("SalesDocumentItem"));
        item.setMaterialCode      ((String) rec.get("MaterialCode"));
        item.setOrderQuantity     ((BigDecimal) rec.get("OrderQuantity"));
        item.setOrderQuantityUnit(mapQuantityUnit((String) rec.get("OrderQuantityUnit")));
        item.setNetAmount          (mapNetAmountRounded(rec));
        item.setCurrency           ((String) rec.get("Currency"));
        item.setPlant              ((String) rec.get("Plant"));
        item.setStorageLocation   ((String) rec.get("StorageLocation"));
        item.setMaterialName      (mapMaterialName(master));
        item.setMaterialGroup     (mapMaterialGroup(master));
        item.setPlantName          (mapPlantName(master));
        item.setCompanyCode        (mapCompanyCode(master));
        return item;
    }

    private SalesDocDetail mapToDetail(Row rec, Row master) {
        SalesDocDetail d = SalesDocDetail.create();
        d.setSalesDocument      ((String) rec.get("SalesDocument"));
        d.setSalesDocumentItem  ((String) rec.get("SalesDocumentItem"));
        d.setSequentialNumber   ((String) rec.get("SequentialNumber"));
        d.setDetailCategory      ((String) rec.get("DetailCategory"));
        d.setDetailText           (mapDetailText(rec));
        d.setDetailAmount        ((BigDecimal) rec.get("DetailAmount"));
        d.setConditionType        ((String) rec.get("ConditionType"));
        d.setDeliveryScheduleQty((BigDecimal) rec.get("DeliveryScheduleQty"));
        d.setCurrency             ((String) rec.get("Currency"));
        return d;
    }

    // ====================================================================
    // 値加工メソッド群
    //
    // 実務では条件分岐が複雑な項目ごとに約30個のメソッドに分割される想定。
    // ここではパターンを示すため代表的なものだけを抜粋する。
    // ====================================================================

    /** 伝票種別により初期ステータスを出し分ける例。 */
    private String mapInitialStatus(Row rec) {
        String docType = (String) rec.get("SalesDocumentType");
        return "ZOR".equals(docType) ? "02" : "01";
    }

    private String mapCustomerName(Row master) {
        return master != null ? (String) master.get("CustomerName") : null;
    }

    private String mapCustomerGroup(Row master) {
        return master != null ? (String) master.get("CustomerGroup") : null;
    }

    /** S4の数量単位コードをローカル表記に変換する例。 */
    private String mapQuantityUnit(String rawUnit) {
        return "EA".equalsIgnoreCase(rawUnit) ? "PC" : rawUnit;
    }

    /** 金額を小数点2桁に丸める例。 */
    private BigDecimal mapNetAmountRounded(Row rec) {
        BigDecimal amount = (BigDecimal) rec.get("NetAmount");
        return amount != null ? amount.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private String mapMaterialName(Row master) {
        return master != null ? (String) master.get("MaterialName") : null;
    }

    private String mapMaterialGroup(Row master) {
        return master != null ? (String) master.get("MaterialGroup") : null;
    }

    private String mapPlantName(Row master) {
        return master != null ? (String) master.get("PlantName") : null;
    }

    private String mapCompanyCode(Row master) {
        return master != null ? (String) master.get("CompanyCode") : null;
    }

    /** 前後空白を除去する例（S4側でパディングされているケースを想定）。 */
    private String mapDetailText(Row rec) {
        String text = (String) rec.get("DetailText");
        return text != null ? text.trim() : "";
    }

    // ====================================================================
    // 戻り値
    // ====================================================================

    public record BulkCreateResult(int headersCreated, int itemsCreated, int detailsCreated) {
    }
}
