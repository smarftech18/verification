using SerialService as svc from './service';

annotate svc.SerialData with @(
  UI.SelectionFields             : [
    s4Key,
    plant,
    status,
    registrationDate
  ],
  UI.LineItem                    : [
    {Value: s4Key},
    {Value: plant},
    {Value: status},
    {Value: serialNo},
    {Value: dataType},
    {Value: registrationDate}
  ],
  // UI.Facets: [
  //   { $Type: 'UI.ReferenceFacet', Label: 'General Information', Target: '@UI.FieldGroup#Main' }
  // ],
  UI.FieldGroup #Main            : {Data: [
    {
      Value               : s4Key,
      @Common.FieldControl: #ReadOnly
    },
    {
      Value               : plant,
      @Common.FieldControl: #ReadOnly
    },
    {
      Value               : status,
      @Common.FieldControl: #ReadOnly
    },
    {
      Value               : registrationDate,
      @Common.FieldControl: #ReadOnly
    },
    {Value: machineNo} // ←ここだけ編集
  ]},
  UI.FieldGroup #GeneratedGroup  : {
    $Type: 'UI.FieldGroupType',
    Data : [
      {
        $Type: 'UI.DataField',
        Label: 's4Key',
        Value: s4Key,
      },
      {
        $Type: 'UI.DataField',
        Label: 'plant',
        Value: plant,
      },
      {
        $Type: 'UI.DataField',
        Label: 'status',
        Value: status,
      },
      {
        $Type: 'UI.DataField',
        Label: 'machineNo',
        Value: machineNo,
      },
      {
        $Type: 'UI.DataField',
        Label: 'registrationDate',
        Value: registrationDate,
      },
    ],
  },
  // UI.Facets                      : [{
  //   $Type : 'UI.ReferenceFacet',
  //   ID    : 'GeneratedFacet1',
  //   Label : 'General Information',
  //   Target: '@UI.FieldGroup#GeneratedGroup',
  // }, ],
  Capabilities.FilterRestrictions: {FilterExpressionRestrictions: [{
    Property          : registrationDate,
    AllowedExpressions: 'SingleRange'
  }]}
);

// annotate SerialService.SerialData with {
//   serialNo @Common.FieldControl: #Mandatory; // もしくは #Editable は無いので Mandatoryが効く
// };
