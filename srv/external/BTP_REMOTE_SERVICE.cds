service BTP_REMOTE_SERVICE {
  entity TableA {
    key s4Key     : String(30);
        serialNo : String(30);
  }

  entity TableB {
    key s4Key     : String(30);
        serialNo : String(30);
  }

  // こちらもAction型の可能性に備えられる
  action UpdateserialNoAll(s4Key:String(30), serialNo:String(30)) returns Boolean;
}
