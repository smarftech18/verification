package customer.verification;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sap.cds.Result;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Upsert;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;

import cds.gen.serialservice.SerialData;
import cds.gen.serialservice.SerialDataEdit;
import cds.gen.serialservice.SerialDataEdit_;
import cds.gen.serialservice.SerialData_;
import cds.gen.serialservice.SerialService_;

@Component
@ServiceName(SerialService_.CDS_NAME)
public class SerialDataEditReadHandler implements EventHandler {

  private final PersistenceService db;

  public SerialDataEditReadHandler(PersistenceService db) {
    this.db = db;
  }

  @On(event = CqnService.EVENT_READ, entity = SerialDataEdit_.CDS_NAME)
  public void onReadSerialDataEdit(CdsReadEventContext context) {
     var originalQuery = context.getCqn();

    // 1) まず S/4 を呼ぶ（SerialData は projection on s4.MachineView なので外部へ行く）
    //    ※フィルタ反映は後で。まず動かす。
    Result s4Res = db.run(
        Select.from(SerialData_.CDS_NAME)
            .columns(s -> s.get("s4Key"), s -> s.get("serialNo"))
    );

    List<SerialData> s4List = s4Res.listOf(SerialData.class);

    // 2) DBに ensure（Upsert）
    for (SerialData row : s4List) {
      SerialDataEdit edit = SerialDataEdit.create();
      edit.setS4Key(row.getS4Key());
      edit.setSerialNo(row.getSerialNo());
      db.run(Upsert.into(SerialDataEdit_.class).entry(edit));
    }

    // 3) SerialDataEdit を返す（UIは toRead を $expand で引く）
    Result result = db.run(originalQuery);
    context.setResult(result);
  }
}
