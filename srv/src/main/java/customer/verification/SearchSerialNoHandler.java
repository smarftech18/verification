package customer.verification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.sap.cds.Result;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Select;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import cds.gen.OutputValue;
import cds.gen.s4_read_service.MachineView_;
import cds.gen.serialservice.SearchSerialNoContext;
import cds.gen.serialservice.SerialService_;

@Component
@ServiceName(SerialService_.CDS_NAME)
public class SearchSerialNoHandler implements EventHandler {

    @Autowired
    @Qualifier("S4_READ_SERVICE")
    private CqnService s4ReadService;

    @On(event = "searchSerialNo")
    public void onSearchSerialNo(SearchSerialNoContext ctx) {

        Collection<String> serialNos = ctx.getSerialNo();
        Collection<String> dataTypes = ctx.getDataType();
        Collection<LocalDate> registrationDates = ctx.getRegistrationDate();

        // 入力が空なら空で返す（設計に合わせて変更してOK）
        if ((serialNos == null || serialNos.isEmpty())
                && (dataTypes == null || dataTypes.isEmpty())
                && (registrationDates == null || registrationDates.isEmpty())) {
            ctx.setResult(List.of());
            return;
        }

        // 条件を “あるものだけ” 付ける
        // ※ CQL.and(...) は可変長引数で渡せる環境が多い
        // もしここでコンパイルが落ちたら次の「代替案」を使う
        Select<?> select = Select.from(MachineView_.CDS_NAME);

        if (serialNos != null && !serialNos.isEmpty()) {
            select.where(CQL.in(CQL.get("serialNo"),
                    serialNos.stream().map(CQL::val).toList()));
        }

        if (dataTypes != null && !dataTypes.isEmpty()) {
            select.where(CQL.in(CQL.get("dataType"),
                    dataTypes.stream().map(CQL::val).toList()));
        }

        if (registrationDates != null && !registrationDates.isEmpty()) {
            select.where(CQL.in(CQL.get("registrationDate"),
                    registrationDates.stream().map(CQL::val).toList()));
        }
        Result result = s4ReadService.run(select);
        List<OutputValue> output = result.listOf(OutputValue.class);
        ctx.setResult(output);
    }
}