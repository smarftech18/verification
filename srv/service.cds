using { S4_READ_SERVICE as s4 } from './external/S4_READ_SERVICE';

service SerialService {

  /**
   * 表示用（S/4 CDS View の投影）
   * - READ専用
   * - ObjectPageの表示元
   */
  @readonly
  entity SerialData as projection on s4.MachineView;

  /**
   * 検索（設計書の「検索用function」）
   * - UIのFilterBarに合わせた入力
   * - 返り値は SerialData（＝テーブル表示に使える構造）
   */
  function searchSerialNo(input: SearchInput) returns many SerialData;

  /**
   * 保存（更新API呼び出しの入口）
   * - ObjectPageの「保存」ボタンから呼ぶ
   * - S/4 CDSは更新できないため、別チームの更新APIを呼ぶ
   */
  // Bound Action（この行を開いているレコードに対する保存）
  action saveSerialNo(newSerialNo: String(30)) returns SaveResult;

}



/**
 * 検索条件（UIに合わせる）
 * - serialNo / dataType は複数選択（ValueHelpのMultiSelect）
 * - registrationDate は単一or期間 → From/Toで表現（ODataは ge/le）
 */
type SearchInput {
  serialNo              : many String(30);
  dataType              : many String(2);
  registrationDateFrom  : Date;
  registrationDateTo    : Date;
}

/**
 * 更新要求（保存ボタン押下のpayload）
 * - 対象は s4Key で特定
 * - newSerialNo はカスタム入力欄の値
 */
type SaveRequest {
  s4Key        : String(30);
  newSerialNo  : String(30);
}

/**
 * 更新結果
 * - UIに返す最小情報（成功可否＋メッセージ）
 * - 必要なら updatedSerialNo 等を足す
 */
type SaveResult {
  success : Boolean;
  message : String;
}
