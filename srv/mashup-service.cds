using { com.example.bp       as db } from '../db/schema';
using { ZC_SALESDOCUMENT_SERVICE as s4 } from './external/ZC_SALESDOCUMENT_SERVICE';

// ================================================================
// SalesMashupService  ─  パターン1: Service Mashup
//
// 【設計方針】
// @cds.persistence.skip により DB テーブルを持たない transient entity を定義する。
// on READ ハンドラ（SalesMashupHandler.java）が:
//   1. S4（ZC_SALESDOCUMENT_SERVICE）から ZcSalesDocument を取得
//   2. ローカルDB（SalesDocHeader）から処理ステータスを取得
//   3. SalesDocument キーでアプリケーション層でマージして返す
//
// 【制約】
// - DB レベルの JOIN ではないため、大量データではパフォーマンスが劣化する
// - フィルタ・ソート・ページングはアプリ層で処理するため OData の完全サポートは難しい
// - リアルタイム性は高い（常に S4 から最新データを取得）
// ================================================================
service SalesMashupService @(path: '/api/sales-mashup') {

  // ----------------------------------------------------------
  // S4 × CAP ローカルの結合ビュー（transient entity）
  //
  // DBに保存されないため、CAPはREADリクエストをそのままハンドラに委譲する。
  // フィールドは S4 側（ZcSalesDocument）と CAP ローカル側（SalesDocHeader）の
  // 両方を含む。ハンドラ内でマージして返す。
  // ----------------------------------------------------------
  @cds.persistence.skip  // DBテーブル/ビューを生成しない
  @readonly
  entity SalesMashupList {

    // ---- キー ------------------------------------------------
    key SalesDocument       : String(10);   // 受注番号（S4）
    key SalesDocumentItem   : String(6);    // 明細番号（S4）
    key SequentialNumber    : String(3);    // 連番（S4）

    // ---- S4 フィールド（ZcSalesDocument から取得） ---------------
        SalesDocumentDate   : Date;
        SalesDocumentType   : String(4);
        CustomerID          : String(10);
        SalesOrganization   : String(4);
        DistributionChannel : String(2);
        Division            : String(2);
        MaterialCode        : String(18);
        OrderQuantity       : Decimal(13, 3);
        OrderQuantityUnit   : String(3);
        NetAmount           : Decimal(15, 2);
        Currency            : String(5);
        Plant               : String(4);
        StorageLocation     : String(4);
        PricingDate         : Date;

    // ---- CAP ローカルフィールド（SalesDocHeader から取得） ---------
    // SalesDocument で突合する。ローカルに存在しない場合は null。
        LocalStatus         : String(2);    // 処理ステータス（01:処理中 02:完了 09:エラー）
        CustomerName        : String(80);   // 得意先名（マスタ補完済み）
        CustomerGroup       : String(4);    // 得意先グループ
        TotalNetAmount      : Decimal(15, 2); // 合計正味金額
        ErrorMessage        : String(255);  // エラーメッセージ
        IsLocallyProcessed  : Boolean;      // ローカル処理済みフラグ（LocalStatus != null）
  };
}
