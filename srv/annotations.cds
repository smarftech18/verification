using SerialService as svc from './service';

annotate svc.SerialData with @(
  // ----------------------------
  // ListReport（フィルタと一覧）
  // ----------------------------
  UI.SelectionFields: [
    s4Key,
    plant,
    status,
    registrationDate
  ],

  UI.LineItem: [
    { $Type: 'UI.DataField', Value: s4Key },
    { $Type: 'UI.DataField', Value: plant },
    { $Type: 'UI.DataField', Value: status },
    { $Type: 'UI.DataField', Value: serialNo },
    { $Type: 'UI.DataField', Value: dataType },
    { $Type: 'UI.DataField', Value: registrationDate }
  ],

  // ----------------------------
  // Filter制約（既存のまま）
  // ----------------------------
  Capabilities.FilterRestrictions: {
    FilterExpressionRestrictions: [{
      Property: registrationDate,
      AllowedExpressions: 'SingleRange'
    }]
  },
  Capabilities.InsertRestrictions : { Insertable : false },
  Capabilities.DeleteRestrictions : { Deletable : false },
  Capabilities.UpdateRestrictions : { Updatable : true }, // 明示しておくと安全
  restrict: [
    { grant: ['READ','UPDATE'], to: 'any' }
  ],
  UI.UpdateHidden: false
);


annotate svc.SerialDataEdit with @(
  UI.HeaderInfo: {
    TypeName: 'Serial',
    TypeNamePlural: 'Serials',
    Title: { Value: serialNo },
    Description: { Value: s4Key }
  },

  UI.Facets: [
    { $Type: 'UI.ReferenceFacet', ID: 'General', Label: 'General', Target: '@UI.FieldGroup#General' }
  ],

  UI.FieldGroup #General: {
    Data: [
      { $Type: 'UI.DataField', Label: 'Plant', Value: toRead.plant },
      { $Type: 'UI.DataField', Label: 'Status', Value: toRead.status },
      { $Type: 'UI.DataField', Label: 'Registration Date', Value: toRead.registrationDate },
      { $Type: 'UI.DataField', Label: 'Serial No', Value: serialNo } // ←これだけ編集対象
    ]
  }
);