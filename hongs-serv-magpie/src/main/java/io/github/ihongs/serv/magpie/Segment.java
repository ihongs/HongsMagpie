package io.github.ihongs.serv.magpie;

import io.github.ihongs.Core;
import io.github.ihongs.CruxException;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Synt;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
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
        Map rd2 = new HashMap(5);

        Set tags  = Synt.asSet(rd.get("tags"));
        if (tags != null && ! tags.isEmpty( )) rd2.put("tags", tags);

        Set args  = Synt.asSet(rd.get("args"));
        if (args != null && ! args.isEmpty( )) rd2.put("args", args);

        Map opts  = Synt.asMap(rd.get("opts"));
        if (opts != null && ! opts.isEmpty( )) rd2.put("opts", args);

        Map nums  = Synt.asMap(rd.get("nums"));
        if (nums != null && ! nums.isEmpty( )) rd2.put("nums", args);

        Integer stat = Synt.asInt(rd.get("state"));
        if (stat != null) rd2.put( "state", stat );

        // 关联查询
        if (! rd2.isEmpty()) {
            try {
                Reference     ref;
                IndexSearcher is2;
                Query         qr2;
                ref = Reference.getInstance(conf, "reference");
                is2 = ref.getFinder();
                qr2 = ref.padQry(rd2);
                qr2 = JoinUtil.createJoinQuery("#id", false, "@rf", qr2, is2, ScoreMode.None);
                qr.add(qr2,BooleanClause.Occur.MUST);
            } catch ( IOException e ) {
                throw new CruxException(e);
            }
        }
    }

}
