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
  // ObjectPage（標準っぽい見た目）
  // ①ヘッダー（タイトル/説明）
  // ----------------------------
  UI.HeaderInfo: {
    $Type: 'UI.HeaderInfoType',
    TypeName: 'Serial',
    TypeNamePlural: 'Serials',
    Title: { $Type: 'UI.DataField', Value: serialNo },
    Description: { $Type: 'UI.DataField', Value: s4Key }
  },

  // ②ヘッダー直下の識別エリア（サマリ）
  UI.Identification: [
    { $Type: 'UI.DataField', Value: serialNo },
    { $Type: 'UI.DataField', Value: status },
    { $Type: 'UI.DataField', Value: plant }
  ],

  // ③セクション（Facet） → ④中身（FieldGroup）
  UI.Facets: [
    {
      $Type: 'UI.ReferenceFacet',
      ID: 'GeneralFacet',
      Label: 'General Information',
      Target: '@UI.FieldGroup#General'
    }
  ],

  UI.FieldGroup #General: {
    $Type: 'UI.FieldGroupType',
    Data: [
      { $Type: 'UI.DataField', Label: 'S4 Key',            Value: s4Key },
      { $Type: 'UI.DataField', Label: 'Plant',             Value: plant },
      { $Type: 'UI.DataField', Label: 'Status',            Value: status },
      { $Type: 'UI.DataField', Label: 'Serial No',         Value: serialNo },
      { $Type: 'UI.DataField', Label: 'Data Type',         Value: dataType },
      { $Type: 'UI.DataField', Label: 'Registration Date', Value: registrationDate }
    ]
  },

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
