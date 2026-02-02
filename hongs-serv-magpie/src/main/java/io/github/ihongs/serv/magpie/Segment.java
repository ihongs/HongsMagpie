package io.github.ihongs.serv.magpie;

import io.github.ihongs.Core;
import io.github.ihongs.CruxException;
import io.github.ihongs.serv.matrix.Data;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.join.JoinUtil;
import org.apache.lucene.search.join.ScoreMode;

/**
 * 可扩展参数的数据表(资料库/向量库)
 * @author Hongs
 */
public class Segment extends Data {

    protected Segment(String conf, String form) {
        super(conf, form);
    }

    public static Segment getInstance(String conf, String form) {
        return Core.getInstance().got(Data.class.getName()+":"+conf+":"+form, ()->new Segment(conf, form));
    }

    @Override
    protected void padQry(BooleanQuery.Builder qr, Map rd, int rl) throws CruxException {
        super.padQry(qr, rd, rl);

        // 引用参数
        Map rd2 = new HashMap(5);
        if (rd.containsKey("tags")) {
            rd2.put("tags", rd.get("tags"));
        }
        if (rd.containsKey("args")) {
            rd2.put("args", rd.get("args"));
        }
        if (rd.containsKey("opts")) {
            rd2.put("opts", rd.get("opts"));
        }
        if (rd.containsKey("opns")) {
            rd2.put("opns", rd.get("opns"));
        }
        if (rd.containsKey("state")) {
            rd2.put("state", rd.get("state"));
        }

        // 关联查询
        if (! rd2.isEmpty()) {
            try {
                Reference  ref;
                Query      qr2;
                ref = Reference.getInstance(conf, "reference");
                qr2 = ref.padQry(rd2);
                if (((BooleanQuery) qr2).clauses().size() > 1) { // 总有一个分区查询
                    qr2 = JoinUtil.createJoinQuery("#id", false, "@rf", qr2, ref.getFinder(), ScoreMode.None);
                    qr.add(qr2, BooleanClause.Occur.MUST);
                }
            } catch (IOException e) {
                throw new CruxException(e);
            }
        }
    }

}
