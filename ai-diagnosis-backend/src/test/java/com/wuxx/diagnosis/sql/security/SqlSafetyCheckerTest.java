package com.wuxx.diagnosis.sql.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wuxx.diagnosis.config.DiagnosisSqlProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SqlSafetyCheckerTest {

    private SqlSafetyChecker checker;

    @BeforeEach
    void setUp() {
        DiagnosisSqlProperties properties = new DiagnosisSqlProperties();
        properties.setEnabled(true);
        properties.setMaxSqlLength(200);
        checker = new SqlSafetyChecker(properties);
    }

    @Test
    void acceptsSelectAndWithSelect() {
        assertThat(checker.checkExplainableSelect("select * from t_order where id = 1;"))
                .isEqualTo("SELECT * FROM t_order WHERE id = 1");
        assertThat(checker.checkExplainableSelect("""
                with recent as (select id from t_order where status = 'PAID')
                select * from recent
                """)).startsWith("WITH recent AS");
    }

    @Test
    void rejectsDmlAndMultipleStatements() {
        assertThatThrownBy(() -> checker.checkExplainableSelect("update t_order set status='X'"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> checker.checkExplainableSelect("select 1; drop table t_order"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsPlaceholdersButIgnoresQuestionMarkInsideLiteral() {
        assertThatThrownBy(() -> checker.checkExplainableSelect("select * from t_order where id = ?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("占位符");
        assertThatThrownBy(() -> checker.checkExplainableSelect("select * from t_order where id = :id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(checker.checkExplainableSelect("select '?' as marker from t_order"))
                .contains("'?'");
    }

    @Test
    void rejectsCommentBypassDangerousFunctionsAndOverlongSql() {
        assertThatThrownBy(() -> checker.checkExplainableSelect("select sleep /* bypass */ (1)"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> checker.checkExplainableSelect("select * from t_order into outfile '/tmp/a'"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> checker.checkExplainableSelect("select " + "x".repeat(210)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("长度");
    }

    @Test
    void validatesSingleUnqualifiedTableName() {
        assertThat(checker.checkTableName("t_order_2026")).isEqualTo("t_order_2026");
        assertThatThrownBy(() -> checker.checkTableName("app.t_order"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
