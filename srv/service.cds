using {S4_READ_SERVICE as s4} from './external/S4_READ_SERVICE';

service SerialService {

  // 外部（S/4 CDS View）を投影して参照できるようにする
  entity SerialData as projection on s4.MachineView;

  // MARK: アクション・ファンクション

  // FunctionはOutputValue。複数条件は “分解して” collection params で受ける
  function searchSerialNo(serialNo: many String(30),
                          dataType: many String(2),
                          registrationDate: many Date) 
                          returns many OutputValue;
}

// UI入力の概念としては残してOK（設計書・注釈用）
type InputValue {
  serialNo         : many String(30);
  dataType         : many String(2);
  registrationDate : many Date;
}


type OutputValue {
  serialNo         : String(30);
  dataType         : String(2);
  registrationDate : Date;
}
