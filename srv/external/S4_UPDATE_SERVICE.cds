service S4_UPDATE_SERVICE {
  // 更新先がEntity更新型だと仮定
  entity MachineUpdate {
    key s4Key     : String(30);
        serialNo : String(30);
  }

  // Action型で来る可能性も高いので、両方用意しておくと強い
  action UpdateserialNo(s4Key:String(30), serialNo:String(30)) returns Boolean;
}
