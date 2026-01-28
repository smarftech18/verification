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
import cds.gen.serialservice.SearchSerialNoContext; // ← 生成されるContext名は要確認
import cds.gen.s4_read_service.MachineView_; // ← 生成名は要確認

import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.cds.CqnService;

@Component
@ServiceName(SerialService_.CDS_NAME)
public class SerialSearchHandler implements EventHandler {

  private final CqnService s4ReadService; // 外部サービス

  public SerialSearchHandler(@Qualifier("S4_READ_SERVICE") CqnService s4ReadService) {
    this.s4ReadService = s4ReadService;
  }

  @On(event = "searchSerialNo")
  public void onSearchSerialNo(SearchSerialNoContext ctx) {

    // 1) inputValue（complex）を取得
    // ※ここが一番ズレやすい：生成された型/取得方法はプロジェクトの生成コードに依存
    Map<String, Object> input = ctx.getInputValue(); // 例：Mapで来る場合
    @SuppressWarnings("unchecked")
    List<String> serialNos = (List<String>) input.get("serialNo");
    @SuppressWarnings("unchecked")
    List<String> dataTypes = (List<String>) input.get("dataType");
    @SuppressWarnings("unchecked")
    List<String> registrationDateFrom = (List<String>) input.get("registrationDateFrom");
    @SuppressWarnings("unchecked")
    List<String> registrationDateTo = (List<String>) input.get("registrationDateTo");

    // 2) S/4検索（必要条件だけwhereに積む）
    CqnSelect s4Select = Select.from(MachineView_.CDS_NAME)
        .columns("*")
        .where(searchItem -> searchItem.get("serialNo").in(serialNos)
            .and(searchItem.get("dataType").in(dataTypes))
            .and(searchItem.get("registrationDate").ge(registrationDateFrom))
            .and(searchItem.get("registrationDate").le(registrationDateTo)));
    // 4) 実行
    Result r = s4ReadService.run(s4Select);

    // 5) Function結果として返す
    // SerialData型にマッピングされるならそれで返す
    List<SerialData> rows = r.listOf(SerialData.class);
    ctx.setResult(rows);
  }
}
