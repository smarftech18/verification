using { S4_READ_SERVICE as S4Read } from './external/S4_READ_SERVICE';

service MachineService {

  // 参照専用（残しても良い、使わなくても良い）
  @readonly
  entity Machines as projection on S4Read.MachineView;

  // UIメイン：Facade
  entity MachineEdit {
    key s4Key          : String(30);
        plant          : String(4);
        status         : String(10);
        machineNo      : String(30);
        reflectionDate : Date;
  }
}
