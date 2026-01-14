using MachineService as svc from './service';

annotate svc.MachineEdit with @(
  UI.SelectionFields: [ s4Key, plant, status, reflectionDate ],
  UI.LineItem: [
    { Value: s4Key },
    { Value: plant },
    { Value: status },
    { Value: machineNo },
    { Value: reflectionDate }
  ],
  UI.Facets: [
    { $Type: 'UI.ReferenceFacet', Label: 'General Information', Target: '@UI.FieldGroup#Main' }
  ],
  UI.FieldGroup #Main: {
    Data: [
      { Value: s4Key,          @Common.FieldControl: #ReadOnly },
      { Value: plant,          @Common.FieldControl: #ReadOnly },
      { Value: status,         @Common.FieldControl: #ReadOnly },
      { Value: reflectionDate, @Common.FieldControl: #ReadOnly },
      { Value: machineNo } // ←ここだけ編集
    ]
  }
);
