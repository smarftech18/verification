// package customer.verification;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Component;

// import com.sap.cds.ql.Select;
// import com.sap.cds.reflect.CdsService;
// import com.sap.cds.services.cds.CdsReadEventContext;
// import com.sap.cds.services.cds.CdsUpdateEventContext;
// import com.sap.cds.services.cds.RemoteService;
// import com.sap.cds.services.handler.EventHandler;
// import com.sap.cds.services.handler.annotations.On;
// import com.sap.cds.services.handler.annotations.ServiceName;

// @Component
// @ServiceName("MachineService")
// public class MachineEditUpdateHandler implements EventHandler {

//   @Autowired RemoteService s4Update;   // S4_UPDATE_SERVICE
//   @Autowired RemoteService btpRemote;  // BTP_REMOTE_SERVICE

//   @On(event = CdsService.EVENT_UPDATE, entity = "MachineService.MachineEdit")
//   public void onUpdate(CdsUpdateEventContext ctx) {

//     var data = ctx.getData();
//     String s4Key = (String) data.get("s4Key");
//     String machineNo = (String) data.get("machineNo");

//     if (machineNo == null || machineNo.isBlank()) {
//       ctx.reject(400, "機番は必須です");
//       return;
//     }

//     // 1) S/4 更新（Entity更新型 or Action型のどちらで来ても対応できるように）
//     s4Update.run(
//       Update.entity("S4_UPDATE_SERVICE.MachineUpdate")
//         .data(Map.of("s4Key", s4Key, "machineNo", machineNo))
//     );
//     // もしくは Action 型なら:
//     // s4Update.run(Call.action("S4_UPDATE_SERVICE.UpdateMachineNo").params(...));

//     // 2) BTP別CAP TableA / TableB 更新
//     btpRemote.run(
//       Update.entity("BTP_REMOTE_SERVICE.TableA")
//         .data(Map.of("s4Key", s4Key, "machineNo", machineNo))
//     );
//     btpRemote.run(
//       Update.entity("BTP_REMOTE_SERVICE.TableB")
//         .data(Map.of("s4Key", s4Key, "machineNo", machineNo))
//     );

//     ctx.setCompleted(); // UIに保存成功を返す
//   }
// }
