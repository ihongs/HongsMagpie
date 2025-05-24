package io.github.ihongs.serv.magpie.tpl;

import com.jfinal.template.Directive;
import com.jfinal.template.Env;
import com.jfinal.template.expr.ast.Expr;
import com.jfinal.template.expr.ast.ExprList;
import com.jfinal.template.io.Writer;
import com.jfinal.template.stat.ParseException;
import com.jfinal.template.stat.Scope;
import io.github.ihongs.util.Syno;
import io.github.ihongs.util.Synt;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * 模板输出
 * 
 * <pre>
 * 使用方法: #f("标签", 变量) 或 #f("标签", 变量, "操作")
 * 操作可选: 可用多个, 逗号分隔
 *  html 等同 cros,tags,trim
 *  cros 清除脚本
 *  tags 清除标签
 *  trim 清除首尾
 *  gaps 清除空行
 *  ends 清除换行
 *  unis 统一换行
 *  ind  第二行开始缩进
 *  mul  新起一行且缩进
 * </pre>
 * 
 * @author Hongs
 */
public class TplFigure extends Directive {

    private static final String R = "\r\n";
    private static final String S = "  ";
    private static final String T = ": ";

    private Expr label;
    private Expr value;
    private Expr strip;

    @Override
    public void setExprList(ExprList exprList) {
        switch (exprList.length()) {
            case 3 -> {
                label = exprList.getExpr(0);
                value = exprList.getExpr(1);
                strip = exprList.getExpr(2);
            }
            case 2 -> {
                label = exprList.getExpr(0);
                value = exprList.getExpr(1);
                strip = null;
            }
            default -> throw new ParseException("Params must be label,value or label,value,strip", location);
        }
    }

    @Override
    public void exec(Env env, Scope scope, Writer writer) {
        try {
            Object lab = label.eval(scope);
            Object val = value.eval(scope);

            // 多个值用逗号分隔
            if (val != null) {
            if (val instanceof Collection) {
                val = Syno.concat(", ", (Collection) val);
            } else
            if (val instanceof Object [ ]) {
                val = Syno.concat(", ", (Object [ ]) val);
            }}

            String l = Synt.asString(lab);
            String v = Synt.asString(val);

            // 空值跳过
            if (v == null || v.isBlank()) {
                return;
            }

            // 清理内容
            Set s = Synt.toTerms(strip != null ? strip.eval(scope) : null);
            if (s == null) {
                s = Set.of("ends","trim"); // 默认单行
            }
            if (! s.isEmpty()) {
                if (s.contains("cros") || s.contains("html")) {
                    v = Syno.stripCros(v); // 清除脚本
                }
                if (s.contains("tags") || s.contains("html")) {
                    v = Syno.stripTags(v); // 清除标签
                }
                if (s.contains("trim") || s.contains("html")) {
                    v = Syno.strip    (v); // 清理首尾
                }
                if (s.contains("gaps")) {
                    v = Syno.stripGaps(v); // 清除空行
                }
                if (s.contains("ends")) {
                    v = Syno.stripEnds(v); // 清除换行
                }
                if (s.contains("unis")) {
                    v = Syno.unifyEnds(v); // 统一换行
                }
                if (s.contains("ind" ) || s.contains("mul" )) {
                    v = Syno.indent(v, S); // 段首缩进
                    v = v.trim( );
                }
            }

            if (s.contains("mul")) {
                writer.write(l);
                writer.write(R);
                writer.write(v);
                writer.write(R);
            } else
            if (s.contains("ind")) {
                writer.write(l);
                writer.write(T);
                writer.write(v);
                writer.write(R);
            } else
            {
                writer.write(l);
                writer.write(T);
                writer.write(v);
                writer.write(R);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean hasEnd() {
        return false;
    }

}
