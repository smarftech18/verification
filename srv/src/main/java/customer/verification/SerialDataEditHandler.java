package customer.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.sap.cds.Result;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.handler.annotations.On;

import cds.gen.serialservice.SerialService_;
import cds.gen.serialservice.SerialData;
import cds.gen.s4_read_service.MachineView_; // ← 生成名は要確認

import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CqnService;

import java.util.Optional;

import org.springframework.stereotype.Component;

import org.springframework.stereotype.Component;

import com.sap.cds.Result;
import com.sap.cds.ql.Insert;
import com.sap.cds.ql.Select;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

import cds.gen.serialservice.SerialService_;
import cds.gen.serialservice.SerialData;
import cds.gen.serialservice.SerialDataEdit;
import cds.gen.serialservice.SerialDataEdit_;
import cds.gen.serialservice.SerialData_;

@Component
@ServiceName(SerialService_.CDS_NAME)
public class SerialDataEditHandler implements EventHandler {

  @On(event = CqnService.EVENT_READ, entity = SerialDataEdit_.CDS_NAME)
  public void ensureActiveExists(CdsReadEventContext ctx) {

    // まず「今来ているREAD」をそのまま実行してみる（= 既存ならそれでOK）
    Result existing = ctx.getService().run(ctx.getCqn());
    if (existing.rowCount() > 0) {
      // 既にあるので、そのまま返す（重要：ここで setResult しないと next が走る場合があるので setResult 推奨）
      ctx.setResult(existing);
      return;
    }

    // ここに来るのは「キー指定で読みに来たが存在しない」ケース
    // s4Key を取りたいが、KeyExtractor を作らずに最短でやるなら：
    // - URLから来るキーは必ず where に入っているので、いったん SerialDataEdit を key-only で読むCQNを作る
    // ただし extract が必要になるので、ここでは「ctx.getCqn().toString()から抜く」雑実装は避けたい。

    // なので手堅く：ActiveキーはOP遷移で分かっている前提で、ctx.getParameterInfo から取るのが一番楽。
    // CAP Javaでは key predicate は request parameters に入るので、ここを使う：
    Object s4KeyObj = ctx.getParameterInfo().getQueryParams().get("s4Key");
    if (s4KeyObj == null) {
      // もし取れない（一覧READ等）なら何もしない
      return;
    }
    String s4Key = String.valueOf(s4KeyObj);

    CqnSelect select = Select.from(MachineView_.CDS_NAME);

    // S/4 READ から serialNo を取得（初期値）
    Result s4 = ctx.getService().run(select);
       ;

    String serialNo = null;
    if (s4.rowCount() > 0) {
      SerialData row = s4.single(SerialData.class);
      serialNo = row.getSerialNo();
    }

    // Active 行を作る（Draftは CAP が管理する）
    SerialDataEdit newRow = SerialDataEdit.create();
    newRow.setS4Key(s4Key);
    newRow.setSerialNo(serialNo);

    ctx.getService().run(Insert.into(SerialDataEdit_.class).entry(newRow));

    // 作った後にもう一度同じREADを実行して返す
    Result created = ctx.getService().run(ctx.getCqn());
    ctx.setResult(created);
  }
}


// public class SerialSearchHandler implements EventHandler {

//   private final CqnService s4ReadService; // 外部サービス

//   public SerialSearchHandler(@Qualifier("S4_READ_SERVICE") CqnService s4ReadService) {
//     this.s4ReadService = s4ReadService;
//   }

//   @On(event = "searchSerialNo")
//   public void onSearchSerialNo(SearchSerialNoContext ctx) {

//     // 1) inputValue（complex）を取得
//     // ※ここが一番ズレやすい：生成された型/取得方法はプロジェクトの生成コードに依存
//     Map<String, Object> input = ctx.getInputValue(); // 例：Mapで来る場合
//     @SuppressWarnings("unchecked")
//     List<String> serialNos = (List<String>) input.get("serialNo");
//     @SuppressWarnings("unchecked")
//     List<String> dataTypes = (List<String>) input.get("dataType");
//     @SuppressWarnings("unchecked")
//     List<String> registrationDateFrom = (List<String>) input.get("registrationDateFrom");
//     @SuppressWarnings("unchecked")
//     List<String> registrationDateTo = (List<String>) input.get("registrationDateTo");

//     // 2) S/4検索（必要条件だけwhereに積む）
//     CqnSelect s4Select = Select.from(MachineView_.CDS_NAME)
//         .columns("*")
//         .where(searchItem -> searchItem.get("serialNo").in(serialNos)
//             .and(searchItem.get("dataType").in(dataTypes))
//             .and(searchItem.get("registrationDate").ge(registrationDateFrom))
//             .and(searchItem.get("registrationDate").le(registrationDateTo)));
//     // 4) 実行
//     Result r = s4ReadService.run(s4Select);

//     // 5) Function結果として返す
//     // SerialData型にマッピングされるならそれで返す
//     List<SerialData> rows = r.listOf(SerialData.class);
//     ctx.setResult(rows);
//   }
// }
