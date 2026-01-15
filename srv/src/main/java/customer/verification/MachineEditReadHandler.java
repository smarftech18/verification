// package customer.verification;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Qualifier;
// import org.springframework.stereotype.Component;

// import com.sap.cds.ql.Select;
// import com.sap.cds.services.cds.CdsReadEventContext;
// import com.sap.cds.services.cds.CdsService;
// import com.sap.cds.services.handler.EventHandler;
// import com.sap.cds.services.handler.annotations.On;
// import com.sap.cds.services.handler.annotations.ServiceName;

// @Component
// @ServiceName("MachineService")
// public class MachineEditReadHandler implements EventHandler {

//   @Autowired
//   @Qualifier("S4_READ_SERVICE")
//   CdsService s4Read;

//   @On(event = "READ")
//   public void onRead(CdsReadEventContext ctx) {
//     if (!"MachineEdit".equals(ctx.getTarget().getName())) return;

//     var result = s4Read.run(Select.from("S4_READ_SERVICE.MachineView"));
//     ctx.setResult(result);
//   }
// }
