package customer.verification.handler;

import com.sap.cds.Result;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnPredicate;
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
import com.sap.cds.services.runtime.CdsRuntime;

import customer.verification.common.BusinessPartnerFilterMapper;
import customer.verification.common.BusinessPartnerFilterMapper.FieldRule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * BusinessPartnerService.CustomerValueHelp の READ ハンドラ。
 *
 * <p>【なぜこのハンドラが必要か】
 * CustomerValueHelp は S4（API_BUSINESS_PARTNER.A_BusinessPartner）と項目名が
 * 完全に一致するパススルーだが、Value Help でユーザーが取引先IDを先頭ゼロ抜きで
 * 入力する運用のため、S4 側の固定10桁ゼロ埋めキーに合わせる値加工が WHERE 句に必要。
 * これは CDS プロジェクションのエイリアスだけでは実現できないため、
 * {@code @cds.persistence.skip} により READ を本ハンドラへ完全委譲している。
 *
 * <p>【ページング不具合の修正方針】
 * 過去の実装では {@code ctx.getCqn()} から WHERE 句のみを取り出して CQN を
 * 手で再構築していたため、$top / $skip（CAP のデフォルトページングを含む）が
 * 再構築時に失われ、cds.query.limit.max（デフォルト1000件）が効いてしまい
 * Value Help のスクロールが機能しなかった。
 *
 * <p>本実装では {@link CQL#copy(com.sap.cds.ql.cqn.CqnStatement, Modifier)} で
 * 元の CqnSelect をコピーし、Modifier では FROM（ターゲットエンティティの ref）のみを
 * 差し替える。items / orderBy / top / skip は一切触れず自動的に引き継がれる。
 * WHERE 句のプロパティ名変更・値加工は共通部品（{@link BusinessPartnerFilterMapper}）
 * に完全に委譲し、その出力をコピー済みクエリの {@code where(...)} にそのまま上書きする
 * （Modifier 側では WHERE の加工ロジックを一切実装しない）。
 */
@Component
@ServiceName("BusinessPartnerService")
public class CustomerValueHelpHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomerValueHelpHandler.class);

    private static final String S4_SERVICE_NAME = "API_BUSINESS_PARTNER";
    private static final String S4_ENTITY       = "API_BUSINESS_PARTNER.A_BusinessPartner";

    /**
     * WHERE句の変換ルール。
     * BusinessPartner: 項目名はS4と同じだが、ユーザー入力（ゼロ抜き）をS4のゼロ埋め10桁に変換する。
     */
    private static final Map<String, FieldRule> FIELD_RULES = Map.of(
        "BusinessPartner", FieldRule.valueOnly("BusinessPartner", CustomerValueHelpHandler::padBusinessPartnerId)
    );

    @Autowired
    private CdsRuntime runtime;

    @On(event = CqnService.EVENT_READ, entity = "BusinessPartnerService.CustomerValueHelp")
    public void onReadCustomerValueHelp(CdsReadEventContext ctx) {

        CqnSelect original = ctx.getCqn();

        // Modifier の責務は FROM（ターゲットエンティティの ref）の差し替えのみ。
        // items / orderBy / top / skip は触らない = 自動継承される。
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

        // WHERE句のプロパティ名変更・値加工は共通部品に完全委譲し、
        // その出力をコピー済みクエリの where(...) にそのまま上書きする。
        Optional<CqnPredicate> mappedWhere = original.where()
            .map(where -> BusinessPartnerFilterMapper.map(where, FIELD_RULES));
        if (mappedWhere.isPresent()) {
            remoteQuery = ((Select<?>) remoteQuery).where(mappedWhere.get());
        }

        CqnService s4Service = (CqnService) runtime.getServiceCatalog()
            .getService(CqnService.class, S4_SERVICE_NAME);

        Result result = s4Service.run(remoteQuery);
        log.debug("CustomerValueHelp READ: {}件取得（top={}, skip={}）",
            result.list().size(), original.top(), original.skip());

        ctx.setResult(result);
        ctx.setCompleted();
    }

    /**
     * ユーザーが入力した取引先ID（ゼロ抜き可）をS4の固定10桁ゼロ埋め形式に変換する。
     * 数字以外（ワイルドカード検索文字列等）を含む場合は加工せずそのまま返す。
     */
    private static Object padBusinessPartnerId(Object value) {
        if (value instanceof String s && !s.isEmpty() && s.length() < 10 && s.chars().allMatch(Character::isDigit)) {
            return "0".repeat(10 - s.length()) + s;
        }
        return value;
    }
}
