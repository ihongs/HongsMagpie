package io.github.ihongs.serv.magpie;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CruxException;
import io.github.ihongs.dh.lucene.quest.DoubleQuest;
import io.github.ihongs.dh.lucene.quest.IQuest;
import io.github.ihongs.dh.lucene.quest.StringQuest;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Synt;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.search.BooleanQuery;

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
    protected void padDoc(Document doc, Map map, Set rep) {
        super.padDoc(doc, map, rep);

        // 写入数值选项
        Map opts = Synt.asMap(map.get("opts"));
        if (opts != null && !opts.isEmpty ()) {
            for (Object ot : opts.entrySet()) {
                Map.Entry et = (Map.Entry) ot ;
                Object k = et.getKey  ();
                Object v = et.getValue();
                if (v instanceof Number) {
                    doc.add(new DoublePoint("@n."+k, Synt.declare(v, 0D)));
                }
            }
        }
    }

    @Override
    protected boolean padQry(BooleanQuery.Builder qr, Map rd, String k, Object v) throws CruxException {
        // 查询数值选项
        if ("opts".equals(k)) {
            Map m = Synt.asMap(v);
            for(Object o : m.entrySet() ) {
                Map.Entry e = (Map.Entry) o ;
                String n = Synt.asString(e.getKey( ));
                Set    a = Synt.asSet( e.getValue( ));
                List   b = a.stream().map(c->n+":"+c).toList(); // 转回标签
                Object w = Synt.mapOf(Cnst.IN_REL, b);
                IQuest q = new StringQuest();
                padQry(qr, rd, "args", w, q);
            }
            return true;
        } else
        if ("nums".equals(k)) {
            Map m = Synt.asMap(v);
            for(Object o : m.entrySet() ) {
                Map.Entry e = (Map.Entry) o ;
                String n = Synt.asString(e.getKey( ));
                Set    a = Synt.asSet( e.getValue( ));
                Object w = Synt.mapOf(Cnst.AT_REL, a);
                IQuest q = new DoubleQuest();
                padQry(qr, rd, "n."+n, w, q);
            }
            return true;
        }

        return super.padQry(qr, rd, k, v);
    }

}
