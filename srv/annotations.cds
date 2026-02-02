using SerialService as svc from './service';

annotate svc.SerialData with @(
  // ----------------------------
  // ListReport（フィルタ／一覧）
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

  // registrationDate は単一／範囲入力を許す（あなたの設定を継続）
  Capabilities.FilterRestrictions: {
    FilterExpressionRestrictions: [{
      Property: registrationDate,
      AllowedExpressions: 'SingleRange'
    }]
  },

  // ----------------------------
  // ObjectPage（標準っぽい見た目）
  // ----------------------------
  UI.HeaderInfo: {
    $Type: 'UI.HeaderInfoType',
    TypeName: 'Serial',
    TypeNamePlural: 'Serials',
    Title:       { $Type: 'UI.DataField', Value: serialNo },
    Description: { $Type: 'UI.DataField', Value: s4Key }
  },

  // ヘッダー直下のサマリ領域
  UI.Identification: [
    { $Type: 'UI.DataField', Value: serialNo },
    { $Type: 'UI.DataField', Value: status },
    { $Type: 'UI.DataField', Value: plant }
  ],

  // セクション（General Information）
  UI.Facets: [
    {
      $Type: 'UI.ReferenceFacet',
      ID: 'GeneralFacet',
      Label: 'General Information',
      Target: '@UI.FieldGroup#General'
    }
  ],

  // セクション内フォーム（表示はread-only）
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
  }
);

// ----------------------------
// ObjectPageの「保存」ボタン（Action）
// ※Unbound Action なので entity ではなく service に付ける
// ----------------------------
annotate svc with @(
  UI.Identification: [
    {
      $Type: 'UI.DataFieldForAction',
      Label: '保存',
      Action: 'SerialService.saveSerialNo'
    }
  ]
);
