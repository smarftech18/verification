package customer.verification.handler;

import com.sap.cds.Result;
import com.sap.cds.Row;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Upsert;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SalesReplicaService のイベントハンドラ。
 *
 * <p>【パターン2: データレプリケーション】
 * S4（ZC_SALESDOCUMENT_SERVICE）のデータをローカル DB の {@code SalesDocS4Replica}
 * テーブルに UPSERT するレプリケーションロジックを担う。
 *
 * <p>レプリカが最新状態であれば、{@code SalesReplicaList}（DB JOIN ビュー）への
 * READ リクエストは CAP が自動的に DB クエリに変換して処理するため、
 * このハンドラでは READ イベントを処理しない（CAP デフォルト動作を使う）。
 *
 * <p>ハンドラが担う処理:
 * <ul>
 *   <li>{@code replicateFromS4} アクション: S4 から取得して SalesDocS4Replica に UPSERT</li>
 *   <li>{@code getReplicaStatus} ファンクション: レプリカの最終更新日時と総件数を返す</li>
 * </ul>
 */
@Component
@ServiceName("SalesReplicaService")
public class SalesReplicaHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(SalesReplicaHandler.class);

    private static final String S4_SERVICE_NAME = "ZC_SALESDOCUMENT_SERVICE";
    private static final String S4_ENTITY       = "ZC_SALESDOCUMENT_SERVICE.ZcSalesDocument";
    private static final String REPLICA_ENTITY  = "com.example.bp.SalesDocS4Replica";

    @Autowired
    private PersistenceService db;

    @Autowired
    private CdsRuntime runtime;

    /**
     * S4 からレプリカテーブルにデータを同期するアクション。
     *
     * <p>処理フロー:
     * <ol>
     *   <li>S4 から ZcSalesDocument を取得（salesDocument 指定時はフィルタ）</li>
     *   <li>取得した全レコードを SalesDocS4Replica に UPSERT（既存は上書き、新規は挿入）</li>
     * </ol>
     *
     * <p>UPSERT を使う理由: 差分更新ではなく全件置き換えに近い運用を想定しているが、
     * S4 側で削除されたレコードの扱いは別途 DELETE ロジックが必要な場合がある。
     */
    @On(event = "replicateFromS4")
    public void onReplicateFromS4(EventContext ctx) {
        String salesDocument = (String) ctx.get("salesDocument");
        log.info("replicateFromS4 開始（パターン2: レプリケーション）: salesDocument={}", salesDocument);

        // ----------------------------------------------------------
        // Step1: S4 から ZcSalesDocument を取得
        //
        // salesDocument が指定された場合は該当伝票のみを対象とし、
        // 未指定（null または空文字）の場合は全件を取得する。
        // ----------------------------------------------------------
        CqnService s4Service = (CqnService) runtime.getServiceCatalog()
                .getService(CqnService.class, S4_SERVICE_NAME);

        var s4Select = Select.from(S4_ENTITY);
        if (salesDocument != null && !salesDocument.isBlank()) {
            s4Select = s4Select.where(r -> r.get("SalesDocument").eq(salesDocument));
        }

        Result s4Result = s4Service.run(s4Select);
        List<Row> s4Rows = s4Result.list();
        log.info("S4 から {}件 取得", s4Rows.size());

        if (s4Rows.isEmpty()) {
            setActionResult(ctx, false, "S4 にデータなし: salesDocument=" + salesDocument, 0);
            return;
        }

        // ----------------------------------------------------------
        // Step2: SalesDocS4Replica テーブルに UPSERT
        //
        // ReplicatedAt に現在時刻を設定することで、
        // いつレプリカされたデータかを後から確認できる。
        // ----------------------------------------------------------
        Instant now = Instant.now();
        List<Map<String, Object>> records = new ArrayList<>();

        for (Row row : s4Rows) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("SalesDocument",       row.get("SalesDocument"));
            record.put("SalesDocumentItem",   row.get("SalesDocumentItem"));
            record.put("SequentialNumber",    row.get("SequentialNumber"));
            record.put("SalesOrganization",   row.get("SalesOrganization"));
            record.put("DistributionChannel", row.get("DistributionChannel"));
            record.put("Division",            row.get("Division"));
            record.put("SalesDocumentDate",   row.get("SalesDocumentDate"));
            record.put("SalesDocumentType",   row.get("SalesDocumentType"));
            record.put("CustomerID",          row.get("CustomerID"));
            record.put("MaterialCode",        row.get("MaterialCode"));
            record.put("OrderQuantity",       row.get("OrderQuantity"));
            record.put("OrderQuantityUnit",   row.get("OrderQuantityUnit"));
            record.put("NetAmount",           row.get("NetAmount"));
            record.put("Currency",            row.get("Currency"));
            record.put("Plant",               row.get("Plant"));
            record.put("StorageLocation",     row.get("StorageLocation"));
            record.put("PricingDate",         row.get("PricingDate"));
            record.put("DetailCategory",      row.get("DetailCategory"));
            record.put("DetailText",          row.get("DetailText"));
            record.put("DetailAmount",        row.get("DetailAmount"));
            record.put("ConditionType",       row.get("ConditionType"));
            record.put("ScheduleLineDate",    row.get("ScheduleLineDate"));
            record.put("DeliveryScheduleQty", row.get("DeliveryScheduleQty"));
            record.put("ReplicatedAt",        now);
            records.add(record);
        }

        db.run(Upsert.into(REPLICA_ENTITY).entries(records));

        String msg = "レプリケーション完了: " + records.size() + "件";
        log.info(msg);
        setActionResult(ctx, true, msg, records.size());
    }

    /**
     * レプリカの状態（最終更新日時・総件数）を返すファンクション。
     */
    @On(event = "getReplicaStatus")
    public void onGetReplicaStatus(EventContext ctx) {
        Result result = db.run(Select.from(REPLICA_ENTITY).columns("ReplicatedAt"));
        List<Row> rows = result.list();

        // ReplicatedAt の最大値を最終レプリカ日時とする
        Instant lastReplicated = rows.stream()
                .map(r -> (Instant) r.get("ReplicatedAt"))
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        // アクション/ファンクションの戻り値は ctx.put() でフィールド単位にセットする
        ctx.put("lastReplicatedAt", lastReplicated);
        ctx.put("totalRecords",     rows.size());
    }

    private void setActionResult(EventContext ctx, boolean success, String message, int count) {
        ctx.put("success",         success);
        ctx.put("message",         message);
        ctx.put("replicatedCount", count);
    }
}
