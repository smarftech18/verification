package customer.verification.handler;

import cds.gen.api_business_partner.ApiBusinessPartner;

import com.sap.cds.Result;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnStructuredTypeRef;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * BusinessPartnerService.CustomerValueHelp の READ ハンドラ。
 *
 * <p>【なぜこのハンドラが必要か】
 * CustomerValueHelp は {@code @cds.persistence.skip} な独立エンティティ（SalesMashupList
 * と同じ設計思想）のため、CAP は READ を自動委譲できず本ハンドラへ完全に委譲する。
 *
 * <p>【ページング不具合の修正方針】
 * 過去の実装では {@code ctx.getCqn()} から WHERE 句のみを取り出して CQN を
 * 手で再構築していたため、$top / $skip（CAP のデフォルトページングを含む）が
 * 再構築時に失われ、cds.query.limit.max（デフォルト1000件）が効いてしまい
 * Value Help のスクロールが機能しなかった。
 *
 * <p>本実装では {@link CQL#copy(com.sap.cds.ql.cqn.CqnStatement, Modifier)} で
 * 元の CqnSelect をコピーし、Modifier では FROM（ターゲットエンティティの ref）のみを
 * 差し替える。WHERE / items / orderBy / top / skip は一切触れず、そのまま自動的に
 * 引き継がれる。
 */
@Component
@ServiceName("BusinessPartnerService")
public class CustomerValueHelpHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomerValueHelpHandler.class);

    private static final String S4_ENTITY = "API_BUSINESS_PARTNER.A_BusinessPartner";

    @Autowired
    private ApiBusinessPartner s4Service;

    @On(event = CqnService.EVENT_READ, entity = "BusinessPartnerService.CustomerValueHelp")
    public void onReadCustomerValueHelp(CdsReadEventContext ctx) {

        CqnSelect original = ctx.getCqn();

        // Modifier の責務は FROM（ターゲットエンティティの ref）の差し替えのみ。
        // WHERE / items / orderBy / top / skip は触らない = すべて自動継承される。
        Modifier fromOnlyModifier = new Modifier() {
            @Override
            public CqnStructuredTypeRef ref(CqnStructuredTypeRef ref) {
                if (ref.segments().size() > 1) {
                    // ナビゲーション経由（キー述語が ref セグメント側にある）は本ハンドラの対象外
                    throw new ServiceException(ErrorStatuses.NOT_IMPLEMENTED,
                        "CustomerValueHelp はナビゲーション経由の READ に対応していません");
                }
                return CQL.to(CQL.refSegment(S4_ENTITY)).asRef();
            }
        };

        CqnSelect remoteQuery = CQL.copy(original, fromOnlyModifier);

        Result result = s4Service.run(remoteQuery);
        log.debug("CustomerValueHelp READ: {}件取得（top={}, skip={}）",
            result.list().size(), original.top(), original.skip());

        ctx.setResult(result);
        ctx.setCompleted();
    }
}
