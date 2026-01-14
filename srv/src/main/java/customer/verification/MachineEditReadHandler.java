package customer.verification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sap.cds.ql.Select;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.RemoteService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

@Component
@ServiceName("MachineService")
public class MachineEditReadHandler implements EventHandler {

  @Autowired
  RemoteService s4Read; // requires名に合わせる: S4_READ_SERVICE

  @On(event = CdsService.EVENT_READ, entity = "MachineService.MachineEdit")
  public void onReadMachineEdit(CdsReadEventContext ctx) {

    // 1) 元リクエストの CQN を読む
    // 2) S4_READ_SERVICE.MachineView に投げ替える
    //    （フィルタ等も可能なら移植）
    var result = s4Read.run(
      Select.from("S4_READ_SERVICE.MachineView")
    );

    ctx.setResult(result);
  }
}

