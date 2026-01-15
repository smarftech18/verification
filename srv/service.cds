using { S4_READ_SERVICE as S4Read } from './external/S4_READ_SERVICE';
using { local } from '../db/schema';


service MachineService {

  // 参照専用（残しても良い、使わなくても良い）
  // @readonly
  // entity Machines as projection on S4Read.MachineView;

  // UIメイン：Facade
  @odata.draft.enabled
  entity MachineEdit as projection on local.MachineEdit
}
