using SerialService as srv from './service';

annotate srv.SerialDataEdit with @(
  // Capabilities.InsertRestrictions: {Insertable: false},
  // Capabilities.DeleteRestrictions: {Deletable: false},
  UI.SelectionFields             : [
    s4Key,
    serialNo,
    toRead.plant,
    toRead.status,
    toRead.registrationDate
  ],
   UI.HeaderInfo: {
    TypeName: 'Serial',
    TypeNamePlural: 'Serials',
    Title: { Value: serialNo },
    Description: { Value: s4Key }
  },

  UI.LineItem                    : [
    {Value: s4Key},
    {Value: toRead.plant},
    {Value: toRead.status},
    {Value: serialNo},
    {Value: toRead.registrationDate}
  ],

  UI.Facets                      : [{
    $Type : 'UI.ReferenceFacet',
    ID    : 'General',
    Label : 'General',
    Target: '@UI.FieldGroup#General'
  }],

  UI.FieldGroup #General         : {Data: [
    {
      $Type: 'UI.DataField',
      Label: 'Plant',
      Value: toRead.plant
    },
    {
      $Type: 'UI.DataField',
      Label: 'Status',
      Value: toRead.status
    },
    {
      $Type: 'UI.DataField',
      Label: 'Registration Date',
      Value: toRead.registrationDate
    },
    {
      $Type: 'UI.DataField',
      Label: 'Serial No',
      Value: serialNo
    }
  ]}
);
