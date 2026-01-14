using { S4_READ_SERVICE as S4Read } from './external/S4_READ_SERVICE';

service MachineService {

  // ListReport用：S/4参照を投影
  @readonly
  entity Machines as projection on S4Read.MachineView;

  // ObjectPage用：編集受付のFacade（永続化しない前提でOK）
  entity MachineEdit {
    key s4Key     : String(30);
        plant     : String(4);
        status    : String(10);
        machineNo : String(30);
        reflectionDate : Date;
  }
}
