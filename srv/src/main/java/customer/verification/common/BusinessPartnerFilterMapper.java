package customer.verification.common;

import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Value;
import com.sap.cds.ql.cqn.CqnComparisonPredicate;
import com.sap.cds.ql.cqn.CqnElementRef;
import com.sap.cds.ql.cqn.CqnLiteral;
import com.sap.cds.ql.cqn.CqnPredicate;
import com.sap.cds.ql.cqn.Modifier;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * WHERE句のプロパティ名変更・値加工を行う共通部品。
 *
 * <p>外部サービス（S4等）へクエリを委譲する READ ハンドラから共通利用する。
 * 呼び出し側は元の {@link CqnPredicate}（{@code ctx.getCqn().where()} 由来）と、
 * 対象項目ごとの変換ルール（{@link FieldRule}）を渡すだけでよい。
 * ref（対象エンティティ）の差し替えや items/orderBy/top/skip の継承には一切関与しない
 * ＝ 呼び出し側の {@code Modifier} で ref のみ差し替え、本コンポーネントの出力を
 * 差し替え済みクエリの WHERE に差し込む、という役割分担を想定している。
 */
public final class BusinessPartnerFilterMapper {

    private BusinessPartnerFilterMapper() {
    }

    /**
     * WHERE句の比較述語（項目名・値）を変換ルールに従って変換する。
     *
     * @param predicate 変換前の {@link CqnPredicate}（null の場合は null を返す）
     * @param rules     項目名 → {@link FieldRule} の変換ルール
     * @return 変換後の {@link CqnPredicate}
     */
    public static CqnPredicate map(CqnPredicate predicate, Map<String, FieldRule> rules) {
        if (predicate == null) {
            return null;
        }
        return CQL.copy(predicate, new FieldRuleModifier(rules));
    }

    /**
     * 1項目分の変換ルール。
     *
     * @param targetField    委譲先エンティティ側の項目名（名称変更が不要な場合は元と同名を指定）
     * @param valueConverter 値加工関数（不要な場合は {@link UnaryOperator#identity()}）
     */
    public record FieldRule(String targetField, UnaryOperator<Object> valueConverter) {

        public static FieldRule renameOnly(String targetField) {
            return new FieldRule(targetField, UnaryOperator.identity());
        }

        public static FieldRule valueOnly(String sameField, UnaryOperator<Object> valueConverter) {
            return new FieldRule(sameField, valueConverter);
        }
    }

    /**
     * 比較述語の左辺（項目参照）・右辺（リテラル値）のみを書き換える Modifier。
     * ref や where 自体の差し替えなど、呼び出し側の責務には踏み込まない。
     */
    private static final class FieldRuleModifier implements Modifier {
        private final Map<String, FieldRule> rules;

        FieldRuleModifier(Map<String, FieldRule> rules) {
            this.rules = rules;
        }

        @Override
        public CqnPredicate comparison(Value<?> lhs, CqnComparisonPredicate.Operator op, Value<?> rhs) {
            if (lhs instanceof CqnElementRef ref && rhs instanceof CqnLiteral<?> literal) {
                FieldRule rule = rules.get(ref.lastSegment());
                if (rule != null) {
                    Object convertedValue = rule.valueConverter().apply(literal.value());
                    return CQL.comparison(CQL.get(rule.targetField()), op, CQL.val(convertedValue));
                }
            }
            return Modifier.super.comparison(lhs, op, rhs);
        }
    }
}
