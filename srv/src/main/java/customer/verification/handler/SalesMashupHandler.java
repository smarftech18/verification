package customer.verification.handler;

import cds.gen.zc_salesdocument_service.ZcSalesdocumentService;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.Select;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * SalesMashupService のイベントハンドラ。
 *
 * <p>【パターン1: Service Mashup】
 * {@code @cds.persistence.skip} なエンティティ {@code SalesMashupList} に対する
 * READ リクエストを処理する。DB テーブルが存在しないため CAP はハンドラに全委譲する。
 *
 * <p>処理フロー:
 * <ol>
 *   <li>S4（ZC_SALESDOCUMENT_SERVICE）から ZcSalesDocument を全件取得</li>
 *   <li>ローカル DB から SalesDocHeader を全件取得し、SalesDocument キーで Map 化</li>
 *   <li>S4 レコードに対してローカルデータをマージして返す</li>
 * </ol>
 *
 * <p>【制約・注意事項】
 * <ul>
 *   <li>フィルタは OData クエリパラメータで来るが、このデモ実装では全件取得後に
 *       CAP ランタイムが自動的に CQN フィルタを適用する（{@code ctx.setCompleted()} を
 *       呼ばないことで CAP のデフォルト後処理を維持することも可能だが、
 *       transient entity の場合は手動で結果をセットする必要がある）</li>
 *   <li>S4 側フィールドに対するフィルタをそのまま S4 に渡したい場合は、
 *       {@code ctx.getCqn()} から条件を取り出して S4 クエリに付け直す追加実装が必要</li>
 *   <li>大量データの場合はメモリ上でのマージがボトルネックになるため、
 *       パターン2（レプリケーション）の採用を検討すること</li>
 * </ul>
 */
@Component
@ServiceName("SalesMashupService")
public class SalesMashupHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(SalesMashupHandler.class);

    private static final String S4_ENTITY     = "ZC_SALESDOCUMENT_SERVICE.ZcSalesDocument";
    private static final String HEADER_ENTITY = "com.example.bp.SalesDocHeader";

    @Autowired
    private PersistenceService db;

    @Autowired
    private ZcSalesdocumentService salesDocumentService;

    /**
     * SalesMashupList の READ ハンドラ。
     *
     * <p>S4 とローカル DB を個別にクエリし、SalesDocument キーでアプリ層マージを行う。
     * transient entity のため、このハンドラが結果を完全に組み立てる責務を持つ。
     */
    @On(event = CqnService.EVENT_READ, entity = "SalesMashupService.SalesMashupList")
    public void onReadSalesMashupList(CdsReadEventContext ctx) {
        log.info("SalesMashupList READ 開始（パターン1: Service Mashup）");

        // ----------------------------------------------------------
        // Step1: S4 から ZcSalesDocument を取得
        //
        // external 配下の CDS を cds build した際に gen 配下へ生成される
        // 型付きサービスインターフェース（ZcSalesdocumentService）を Autowired し、
        // そのままクエリを実行する。実環境では Destination サービス経由で S4 に HTTP リクエストが飛ぶ。
        // ----------------------------------------------------------
        Result s4Result = salesDocumentService.run(Select.from(S4_ENTITY));
        List<Row> s4Rows = s4Result.list();
        log.info("S4 から {}件 取得", s4Rows.size());

        // ----------------------------------------------------------
        // Step2: ローカル DB から SalesDocHeader を取得し Map 化
        //
        // SalesDocument（受注番号）をキーに Map に変換する。
        // 同一 SalesDocument に複数明細がある場合でもヘッダは1件のため、
        // ヘッダ単位でマップすれば全明細に対して突合できる。
        // ----------------------------------------------------------
        Result localResult = db.run(Select.from(HEADER_ENTITY));
        Map<String, Row> localByDoc = new HashMap<>();
        for (Row row : localResult) {
            String salesDoc = (String) row.get("SalesDocument");
            if (salesDoc != null) {
                localByDoc.put(salesDoc, row);
            }
        }
        log.info("ローカル DB から {}件 取得", localByDoc.size());

        // ----------------------------------------------------------
        // Step3: S4 データにローカルデータをマージ
        //
        // S4 の全レコードを起点にループし、同一 SalesDocument の
        // ローカルヘッダが存在すればそのフィールドを付加する。
        // ローカルに存在しない（未処理）場合はローカルフィールドが null になる。
        // ----------------------------------------------------------
        List<Map<String, Object>> merged = new ArrayList<>();

        for (Row s4Row : s4Rows) {
            String salesDoc = (String) s4Row.get("SalesDocument");
            Row localRow = localByDoc.get(salesDoc);

            Map<String, Object> record = new LinkedHashMap<>();

            // S4 フィールド
            record.put("SalesDocument",       s4Row.get("SalesDocument"));
            record.put("SalesDocumentItem",   s4Row.get("SalesDocumentItem"));
            record.put("SequentialNumber",    s4Row.get("SequentialNumber"));
            record.put("SalesDocumentDate",   s4Row.get("SalesDocumentDate"));
            record.put("SalesDocumentType",   s4Row.get("SalesDocumentType"));
            record.put("CustomerID",          s4Row.get("CustomerID"));
            record.put("SalesOrganization",   s4Row.get("SalesOrganization"));
            record.put("DistributionChannel", s4Row.get("DistributionChannel"));
            record.put("Division",            s4Row.get("Division"));
            record.put("MaterialCode",        s4Row.get("MaterialCode"));
            record.put("OrderQuantity",       s4Row.get("OrderQuantity"));
            record.put("OrderQuantityUnit",   s4Row.get("OrderQuantityUnit"));
            record.put("NetAmount",           s4Row.get("NetAmount"));
            record.put("Currency",            s4Row.get("Currency"));
            record.put("Plant",               s4Row.get("Plant"));
            record.put("StorageLocation",     s4Row.get("StorageLocation"));
            record.put("PricingDate",         s4Row.get("PricingDate"));

            // CAP ローカルフィールド（ローカルに存在しない場合は null）
            record.put("LocalStatus",         localRow != null ? localRow.get("Status")         : null);
            record.put("CustomerName",        localRow != null ? localRow.get("CustomerName")   : null);
            record.put("CustomerGroup",       localRow != null ? localRow.get("CustomerGroup")  : null);
            record.put("TotalNetAmount",      localRow != null ? localRow.get("TotalNetAmount") : null);
            record.put("ErrorMessage",        localRow != null ? localRow.get("ErrorMessage")   : null);
            record.put("IsLocallyProcessed",  localRow != null);

            merged.add(record);
        }

        log.info("SalesMashupList READ 完了: マージ結果 {}件", merged.size());

        ctx.setResult(merged);
        ctx.setCompleted();
    }
}
