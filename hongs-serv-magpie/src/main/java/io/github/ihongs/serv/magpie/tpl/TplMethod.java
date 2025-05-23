package io.github.ihongs.serv.magpie.tpl;

import io.github.ihongs.util.Inst;
import io.github.ihongs.util.Syno;
import io.github.ihongs.util.Synt;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * 模板函数
 * @author Hongs
 */
public class TplMethod {

    public String indent(String s) {
        return indent(s, 2);
    }

    public String indent(String s, int n) {
        if (s == null || "".equals(s)) {
            return "";
        }
        return Syno.indent(s, " " .repeat(n)).trim();
    }

    public String concat(Object o) {
        return concat(o, ", ");
    }

    public String concat(Object o, String s) {
        if (o == null || "".equals(o)) {
            return "";
        }
        return Syno.concat(s, Synt.asColl(o)).trim();
    }

    public String date_format(Object d, String f) {
        if (d == null || "".equals(d)) {
            return "";
        } else
        if (d instanceof Date) {
            return Inst.format((Date) d, f);
        } else
        if (d instanceof Instant) {
            return Inst.format((Instant) d, f);
        } else
        {
            return Inst.format(Synt.asLong(d), f);
        }
    }

    public String strip(String ob, Object sd) {
        Set    sa;
        String st;
        sa = Synt. toSet (sd);
        st = Synt.declare(ob , "" );
        if (sa == null) {
            sa = Synt.setOf("trim"); // 默认清除首尾空字符
        }
        if (! sa.isEmpty()) {
            if (sa.contains("cros") || sa.contains("html")) {
                st = Syno.stripCros(st); // 清除脚本
            }
            if (sa.contains("tags") || sa.contains("html")) {
                st = Syno.stripTags(st); // 清除标签
            }
            if (sa.contains("trim") || sa.contains("html")) {
                st = Syno.strip    (st); // 清理首尾
            }
            if (sa.contains("gaps")) {
                st = Syno.stripGaps(st); // 清除空行
            }
            if (sa.contains("ends")) {
                st = Syno.stripEnds(st); // 清除换行
            }
            if (sa.contains("unis")) {
                st = Syno.unifyEnds(st); // 统一换行
            }
            if (sa.contains("ind" ) || sa.contains("mul" )) {
                st = Syno.indent(st, S); // 段首缩进
                st = st.trim( );
            }
        }
        return  st;
    }

    private static final String S = "  ";

}
