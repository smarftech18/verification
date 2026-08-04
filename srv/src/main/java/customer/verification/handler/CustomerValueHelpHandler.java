package customer.verification.handler;

import cds.gen.api_business_partner.ApiBusinessPartner;

import com.sap.cds.Result;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Predicate;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.ql.cqn.CqnPredicate;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnStructuredTypeRef;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

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
 *
 * <p>【ナビゲーション経由（BusinessPartners(ID)/to_valueHelpMatch）への対応】
 * {@code Authors(1)/books} のような呼び出しでは、絞り込み条件が WHERE 句ではなく
 * ref のセグメント側（{@code Authors[ID=1]}）に埋め込まれている。ref を丸ごと
 * 差し替えると、このキー述語が黙って失われてしまう。
 * そのため ref() で親セグメントのキー述語を {@link CqnAnalyzer} で読み取り、
 * where() 側で S4 の検索条件へ変換して埋め戻す（SAP公式 notes サンプルの
 * {@code NotesServiceHandler} と同じ手法）。
 * ローカルの主キー（UUID）と CustomerValueHelp のキー（S4準拠の取引先ID文字列）は
 * 型が異なるため、ローカル BusinessPartners を1件引いて businessPartnerID を
 * 解決してから条件に変換する。
 */
@Component
@ServiceName("BusinessPartnerService")
public class CustomerValueHelpHandler implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomerValueHelpHandler.class);

    private static final String S4_ENTITY          = "API_BUSINESS_PARTNER.A_BusinessPartner";
    private static final String LOCAL_BP_ENTITY    = "com.example.bp.BusinessPartners";

    @Autowired
    private ApiBusinessPartner s4Service;

    @Autowired
    private PersistenceService db;

    @Autowired
    private CdsModel model;

    private CqnAnalyzer analyzer;

    @PostConstruct
    private void init() {
        analyzer = CqnAnalyzer.create(model);
    }

    @On(event = CqnService.EVENT_READ, entity = "BusinessPartnerService.CustomerValueHelp")
    public void onReadCustomerValueHelp(CdsReadEventContext ctx) {

        CqnSelect original = ctx.getCqn();

        Modifier modifier = new Modifier() {

            /** ref() で読み取った親のキー述語（ナビゲーション経由の場合のみ設定される）。 */
            private Map<String, Object> parentKeys;

            @Override
            public CqnStructuredTypeRef ref(CqnStructuredTypeRef ref) {
                if (ref.segments().size() > 1) {
                    // ナビゲーション経由: 親セグメントのキー述語を later の where() で使うため保持する
                    parentKeys = analyzer.analyze(ref).rootKeys();
                }
                return CQL.to(CQL.refSegment(S4_ENTITY)).asRef();
            }

            @Override
            public CqnPredicate where(Predicate where) {
                if (parentKeys == null) {
                    // 通常時（ナビゲーション経由でない）は素通し
                    return where;
                }
                // 親のキー述語を S4 側の検索条件へ変換して埋め戻す
                // (rootKeys() が返す値の実際の型は文字列表現の場合もあるため、Object のまま扱う)
                Object localId = parentKeys.get("ID");
                String businessPartnerId = resolveBusinessPartnerId(localId);
                Predicate ofParent = CQL.get("BusinessPartner").eq(businessPartnerId);
                return where != null ? ofParent.and(where) : ofParent;
            }
        };

        CqnSelect remoteQuery = CQL.copy(original, modifier);

        Result result = s4Service.run(remoteQuery);
        log.debug("CustomerValueHelp READ: {}件取得（top={}, skip={}）",
            result.list().size(), original.top(), original.skip());

        ctx.setResult(result);
        ctx.setCompleted();
    }

    /**
     * ローカルキャッシュの BusinessPartners（UUID主キー）から、S4準拠の取引先ID
     * （businessPartnerID）を解決する。
     *
     * <p>ref セグメントのキー述語から直接取れるのはローカルの主キー（UUID）のみで、
     * CustomerValueHelp を絞り込むための取引先ID文字列とは型が異なるため、
     * この1件引きが必要になる。
     */
    private String resolveBusinessPartnerId(Object localId) {
        Result result = db.run(
            Select.from(LOCAL_BP_ENTITY)
                  .columns("businessPartnerID")
                  .where(b -> b.get("ID").eq(localId))
        );
        return result.first().map(row -> (String) row.get("businessPartnerID")).orElse(null);
    }
}
