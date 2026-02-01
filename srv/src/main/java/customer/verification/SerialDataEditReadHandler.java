package customer.verification;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sap.cds.CdsResult;
import com.sap.cds.Result;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.Upsert;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
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

@Before(event = CqnService.EVENT_READ, entity = SerialDataEdit_.CDS_NAME)
  public void ensureBeforeRead(CdsReadEventContext ctx) {

    // ListReportはDraft情報を含む複雑なREADを投げるので、
    // ここでは「ensureだけ」して、結果は標準処理に任せる（setResultしない）

    // S/4 (SerialData projection) は service 経由で読む
    Result s4Res = ctx.getService().run(
        Select.from(SerialData_.CDS_NAME)
              .columns(s -> s.get("s4Key"), s -> s.get("serialNo"))
    );
    List<SerialData> s4List = s4Res.listOf(SerialData.class);

    for (SerialData row : s4List) {
      SerialDataEdit edit = SerialDataEdit.create();
      edit.setS4Key(row.getS4Key());
      edit.setSerialNo(row.getSerialNo());
      // DBへensure（Active行の器）
      db.run(Upsert.into(SerialDataEdit_.class).entry(edit));
    }
  }

// @On(event = CqnService.EVENT_READ, entity = SerialDataEdit_.CDS_NAME)
// public void onReadSerialDataEdit(CdsReadEventContext ctx) {

//     var cqn = ctx.getCqn().toString();

//     // Draft 内部アクセスは何もしない
//     if (cqn.contains("DraftAdministrativeData")
//         || cqn.contains("SiblingEntity")
//         || cqn.contains("IsActiveEntity=false")) {
//         return; // CAP 標準処理へ
//     }

//     // --- ensure ---
//     var s4List = ctx.getService()
//         .run(Select.from(SerialData_.class)
//                    .columns(s -> s.s4Key(), s -> s.serialNo()))
//         .listOf(SerialData.class);

//     for (var s4 : s4List) {
//         var edit = SerialDataEdit.create();
//         edit.setS4Key(s4.getS4Key());
//         edit.setSerialNo(s4.getSerialNo());
//         db.run(Upsert.into(SerialDataEdit_.class).entry(edit));
//     }

//     // ★ここが重要
//     ctx.setResult(db.run(ctx.getCqn()));
// }



}
