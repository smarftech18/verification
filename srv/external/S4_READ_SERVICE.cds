service S4_READ_SERVICE {
  entity MachineView {
    key s4Key     : String(30);
        plant     : String(4);
        status    : String(10);
        machineNo : String(30);
        reflectionDate : Date;
  }
}
