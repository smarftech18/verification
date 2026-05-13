using { com.example.bp       as db } from '../db/schema';
using { ZC_SALESDOCUMENT_SERVICE as s4 } from './external/ZC_SALESDOCUMENT_SERVICE';

// ================================================================
// SalesReplicaService  ─  パターン2: データレプリケーション
//
// 【設計方針】
// S4（ZC_SALESDOCUMENT_SERVICE）のデータをローカル DB の SalesDocS4Replica テーブルに
// レプリケーションし、CAP ローカルエンティティ（SalesDocHeader）と DB レベルで JOIN する。
//
// 【メリット】
// - SalesReplicaList は通常の DB ビューのため、OData フィルタ・ソート・$count・
//   ページング（$top/$skip）が完全に動作する
// - Fiori Listreport のすべての機能（スマートフィルタ、ソート、ページング）をそのまま使える
// - S4 障害時でも過去のレプリカデータで表示継続できる
//
// 【制約】
// - データの鮮度は replicateFromS4 アクションの実行頻度に依存する
// - S4 スキーマ変更時は SalesDocS4Replica と本サービスの追従が必要
//
// 【レプリカ更新方法】
// 1. 手動: POST /api/sales-replica/replicateFromS4
// 2. 定期バッチ: Spring Scheduler や SAP BTP Job Scheduling Service で定期呼び出し
// 3. イベント駆動: SAP Event Mesh で S4 変更イベント受信時に自動更新
// ================================================================
service SalesReplicaService @(path: '/api/sales-replica') {

  // ----------------------------------------------------------
  // Listreport 向け DB JOIN ビュー
  //
  // SalesDocS4Replica（S4レプリカ）と SalesDocHeader（CAPローカル）を
  // SalesDocument キーで LEFT JOIN した読み取り専用ビュー。
  //
  // DB レベルの結合のため、OData の全クエリオプションが完全動作する：
  //   - $filter  : S4 フィールド・ローカルフィールド双方でフィルタ可能
  //   - $orderby : 全フィールドでソート可能
  //   - $top/$skip: DBページングが効くためメモリ効率が高い
  //   - $count   : DB COUNT クエリで正確な件数取得
  // ----------------------------------------------------------
  @readonly
  view SalesReplicaList as select from db.SalesDocS4Replica as r
    left join db.SalesDocHeader as h on h.SalesDocument = r.SalesDocument
  {
    // ---- キー ------------------------------------------------
    key r.SalesDocument,
    key r.SalesDocumentItem,
    key r.SequentialNumber,

    // ---- S4 レプリカフィールド --------------------------------
        r.SalesDocumentDate,
        r.SalesDocumentType,
        r.CustomerID,
        r.SalesOrganization,
        r.DistributionChannel,
        r.Division,
        r.MaterialCode,
        r.OrderQuantity,
        r.OrderQuantityUnit,
        r.NetAmount,
        r.Currency,
        r.Plant,
        r.StorageLocation,
        r.PricingDate,
        r.DetailCategory,
        r.DetailText,
        r.DetailAmount,
        r.ConditionType,
        r.ScheduleLineDate,
        r.DeliveryScheduleQty,
        r.ReplicatedAt,       // レプリカ最終更新日時

    // ---- CAP ローカルフィールド（SalesDocHeader から JOIN） -----
    // LEFT JOIN のため、ローカルに存在しない伝票の場合は null になる
        h.Status         as LocalStatus,        // 処理ステータス
        h.CustomerName,                         // 得意先名（マスタ補完済み）
        h.CustomerGroup,                        // 得意先グループ
        h.TotalNetAmount as LocalTotalNetAmount, // 合計正味金額
        h.ErrorMessage                          // エラーメッセージ
  };

  // ----------------------------------------------------------
  // replicateFromS4 アクション
  //
  // S4 から ZcSalesDocument を取得し、SalesDocS4Replica テーブルに UPSERT する。
  // salesDocument を指定すると対象伝票のみを更新し、未指定で全件レプリケーション。
  //
  // 呼び出し例（REST）:
  //   POST /api/sales-replica/replicateFromS4
  //   Body: { "salesDocument": "0000001234" }  ← 特定伝票のみ
  //   Body: {}                                  ← 全件
  // ----------------------------------------------------------
  action replicateFromS4(
    salesDocument  : String(10)   // 対象伝票番号（未指定で全件レプリケーション）
  ) returns {
    success         : Boolean;
    message         : String;
    replicatedCount : Integer;
  };

  // ----------------------------------------------------------
  // getReplicaStatus ファンクション
  //
  // レプリカテーブルの最終更新日時と総件数を返す。
  // データ鮮度の確認やモニタリングに使用する。
  // ----------------------------------------------------------
  function getReplicaStatus() returns {
    lastReplicatedAt : DateTime;
    totalRecords     : Integer;
  };
}
