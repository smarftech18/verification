namespace db;

entity MachineEdit {
  key s4Key          : String(30);
      plant          : String(4);
      status         : String(10);
      serialNo      : String(30);
      registrationDate : Date;
}

  entity MachineView {
    key s4Key     : String(30);
        plant     : String(4);
        status    : String(10);
        serialNo : String(30);
        dataType: String(2);
        registrationDate : Date;
  }

