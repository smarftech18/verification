using MachineService as svc from './service';

annotate svc.Machines with @(
  UI.SelectionFields: [ s4Key, plant, status, reflectionDate ],
  UI.LineItem: [
    { Value: s4Key },
    { Value: plant },
    { Value: status },
    { Value: machineNo },
    { Value: reflectionDate }
  ]
);

annotate svc.MachineEdit with @(
  UI.FieldGroup #Main: {
    Data: [
      { Value: s4Key,   @Common.FieldControl: #ReadOnly },
      { Value: plant,   @Common.FieldControl: #ReadOnly },
      { Value: status,  @Common.FieldControl: #ReadOnly },
      { Value: reflectionDate, @Common.FieldControl: #ReadOnly },
      { Value: machineNo } // ←これだけ編集
    ]
  }
);
