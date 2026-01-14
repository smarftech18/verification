service BTP_REMOTE_SERVICE {
  entity TableA {
    key s4Key     : String(30);
        machineNo : String(30);
  }

  entity TableB {
    key s4Key     : String(30);
        machineNo : String(30);
  }

  // こちらもAction型の可能性に備えられる
  action UpdateMachineNoAll(s4Key:String(30), machineNo:String(30)) returns Boolean;
}
