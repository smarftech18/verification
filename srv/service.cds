using {S4_READ_SERVICE as s4} from './external/S4_READ_SERVICE';
using { db } from '../db/schema';

service SerialService @(requires: 'any') {
  
  // 一覧・参照は必ずS4
  @readonly
  entity SerialData  @(requires: 'any') as projection on s4.MachineView;

  @odata.draft.enabled
  entity SerialDataEdit  @(requires: 'any') as projection on db.MachineEdit{
    key s4Key,
    serialNo,
    toRead: Association to one SerialData on toRead.s4Key = $self.s4Key  // serialDataとSerialDataEditのs4keyで結合
  };


  // MARK: アクション・ファンクション

  // ①FunctionはOutputValue。複数条件は “分解して” collection params で受ける
//   function searchSerialNo(inputValue: InputValue) returns many SerialData;
// }

// UI入力の概念としては残してOK（設計書・注釈用）
type InputValue {
  serialNo              : many String(30);
  dataType              : many String(2);
  registrationDateFrom  : Date;
  registrationDateTo    : Date;
}


type OutputValue {
  s4Key            : String(30);
  plant            : String(4);
  status           : String(10);
  serialNo         : String(30);
  dataType         : String(2);
  registrationDate : Date;
}
}