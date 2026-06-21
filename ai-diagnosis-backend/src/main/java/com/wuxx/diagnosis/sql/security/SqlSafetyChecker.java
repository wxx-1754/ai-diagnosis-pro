package com.wuxx.diagnosis.sql.security;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.wuxx.diagnosis.config.DiagnosisSqlProperties;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SqlSafetyChecker {

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");
    private static final Pattern NAMED_PARAMETER = Pattern.compile("(^|[^:]):[A-Za-z_][A-Za-z0-9_]*");
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            " into outfile", " into dumpfile"
    );
    private static final Pattern FORBIDDEN_FUNCTION = Pattern.compile(
            "\\b(load_file|sleep|benchmark|get_lock|release_lock|is_free_lock|is_used_lock|"
                    + "sys_exec|sys_eval|updatexml|extractvalue)\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    private final DiagnosisSqlProperties properties;

    public String checkExplainableSelect(String sql) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("SQL 联合诊断功能未启用");
        }
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("SQL不能为空");
        }
        if (sql.length() > properties.getMaxSqlLength()) {
            throw new IllegalArgumentException("SQL长度超过限制：" + properties.getMaxSqlLength());
        }

        String codeOnly = stripCommentsAndLiterals(sql).toLowerCase(Locale.ROOT);
        if (codeOnly.contains("?")
                || NAMED_PARAMETER.matcher(codeOnly).find()
                || codeOnly.contains("${")
                || codeOnly.contains("#{")) {
            throw new IllegalArgumentException("SQL包含未绑定占位符，请先替换为可执行字面量");
        }
        String padded = " " + codeOnly.replaceAll("\\s+", " ").trim() + " ";
        for (String token : FORBIDDEN_TOKENS) {
            if (padded.contains(token)) {
                throw new SecurityException("SQL包含禁止的高风险语法或函数：" + token.trim());
            }
        }
        if (FORBIDDEN_FUNCTION.matcher(codeOnly).find()) {
            throw new SecurityException("SQL包含禁止的高风险函数");
        }

        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            List<Statement> parsed = statements.getStatements();
            if (parsed.size() != 1) {
                throw new SecurityException("禁止多语句SQL");
            }
            Statement statement = parsed.get(0);
            if (!(statement instanceof Select)) {
                throw new SecurityException("只允许 SELECT 或 WITH SELECT SQL");
            }
            return removeEndSemicolon(statement.toString());
        } catch (SecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("SQL解析失败：" + exception.getMessage(), exception);
        }
    }

    public String checkTableName(String tableName) {
        String value = StringUtils.hasText(tableName) ? tableName.trim() : "";
        if (!TABLE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("mainTableName不是合法的单表名称");
        }
        return value;
    }

    private String removeEndSemicolon(String sql) {
        String text = sql.trim();
        return text.endsWith(";") ? text.substring(0, text.length() - 1).trim() : text;
    }

    private String stripCommentsAndLiterals(String sql) {
        StringBuilder output = new StringBuilder(sql.length());
        boolean single = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                    output.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    index++;
                    output.append(' ');
                }
                continue;
            }
            if (!single && !doubleQuote && !backtick && current == '-' && next == '-') {
                lineComment = true;
                index++;
                continue;
            }
            if (!single && !doubleQuote && !backtick && current == '/' && next == '*') {
                blockComment = true;
                index++;
                continue;
            }
            if (!doubleQuote && !backtick && current == '\'') {
                if (single && next == '\'') {
                    output.append("  ");
                    index++;
                    continue;
                }
                single = !single;
                output.append(' ');
                continue;
            }
            if (!single && !backtick && current == '"') {
                doubleQuote = !doubleQuote;
                output.append(' ');
                continue;
            }
            if (!single && !doubleQuote && current == '`') {
                backtick = !backtick;
                output.append(current);
                continue;
            }
            output.append(single || doubleQuote ? ' ' : current);
        }
        return output.toString();
    }
}
