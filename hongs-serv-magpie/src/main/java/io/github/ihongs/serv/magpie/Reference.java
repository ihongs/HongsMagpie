package io.github.ihongs.serv.magpie;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CruxException;
import io.github.ihongs.CruxExemption;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Dist;
import io.github.ihongs.util.Synt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.apache.lucene.document.Document;

/**
 * 资料库
 * @author Hongs
 */
public class Reference extends Segment {

    private List parts; // 分块跳线

    protected Reference(String conf, String form) {
        super(conf, form);
    }

    public static Reference getInstance(String conf, String form) {
        return Core.getInstance().got(Data.class.getName()+":"+conf+":"+form, ()->new Reference(conf, form));
    }

    public Data getSegment() throws CruxException {
        return Data.getInstance("centra/data/magpie", "reference-segment");
    }

    @Override
    public int add(String id, Map rd, long time) throws CruxException {
        parts = null;
        int n = super.add(id, rd, time);
        if (n > 0 && parts != null) {
            updateSegments( id, parts );
        }
        return n;
    }

    @Override
    public int put(String id, Map rd, long time) throws CruxException {
        parts = null;
        int n = super.put(id, rd, time);
        if (n > 0 && parts != null) {
            updateSegments( id, parts );
        }
        return n;
    }

    @Override
    public int set(String id, Map rd, long time) throws CruxException {
        parts = null;
        int n = super.set(id, rd, time);
        if (n > 0 && parts != null) {
            updateSegments( id, parts );
        }
        return n;
    }

    @Override
    public int del(String id, Map rd, long time) throws CruxException {
        int n = super.del(id, rd, time);
        deleteSegments( id );
        return n;
    }

    @Override
    public int end(String id, Map rd, long time) throws CruxException {
        int n = super.end(id, rd, time);
        deleteSegments( id );
        return n;
    }

    @Override
    public int rev(String id, Map rd, long time) throws CruxException {
        int n = super.rev(id, rd, time);
        if (n > 0) {
            Document dc = getDoc(id);
            String   ts = dc.getField("part").stringValue();
            List     ps = (List) Dist.toObject(ts);
            updateSegments( id , ps);
        }
        return n;
    }

    @Override
    protected int padInf(Map dd , Map rd) {
        Set ab = Synt.toTerms(rd.get(Cnst.AB_KEY));

        // 合并标签
        if (ab != null && ab.contains("add-tags")) {
            Set nts  = Synt.toSet(rd.get("tags"));
            Set ots  = Synt.toSet(dd.get("tags"));
            if (ots != null) {
                ots.addAll(nts);
                rd.put("tags", ots);
            }
        }

        // 合并参数
        if (ab != null && ab.contains("add-args")) {
            Set nts  = Synt.toSet(rd.get("args"));
            Set ots  = Synt.toSet(dd.get("args"));
            if (ots != null) {
                ots.addAll(nts);
                rd.put("args", ots);
            }
        }

        // 解析参数
        if (rd.containsKey("args")) {
            Set args = Synt.toSet(rd.get("args"));
            Map opts = new TreeMap();
            Pattern pa = Pattern.compile("^[^\\s\\[\\]\\.:=?&#]+$");
            for(Object obj : args) {
                String arg = Synt.asString( obj );
                int p = arg.indexOf(":");
                if (p > 0) {
                    String k = arg.substring(0,p);
                    String v = arg.substring(1+p);
                    if (!pa.matcher(k).matches()) {
                        throw new CruxExemption(400, "Option key `$0` contains illegal characters: .:=?&#[] or space...", k);
                    }
                    opts.put(k, v);
                } else {
                    p = arg.indexOf("=");
                if (p > 0) {
                    String k = arg.substring(0,p);
                    String v = arg.substring(1+p);
                    if (!pa.matcher(k).matches()) {
                        throw new CruxExemption(400, "Option key `$0` contains illegal characters: .:=?&#[] or space...", k);
                    }
                    try {
                        opts.put(k, Synt.asDouble(v));
                    }
                    catch (ClassCastException ex) {
                        throw new CruxExemption(ex, 400, "Option value for `$0` can not be cast to number, value: $1", k, v);
                    }
                }}
            }
            rd.put("opts", opts);
        }

        // 新旧内容
        String nt = Synt.asString(rd.get("text"));
        String ot = Synt.asString(dd.get("text"));

        int n = super.padInf(dd , rd);

        // 拆分文本
        List pl = Synt.asList(rd.get("parts"));
        if (pl == null && nt != null && ! nt.equals(ot)) {
            pl = AIUtil.split(nt);
        }

        // 获取向量
        if (pl != null) {
            List vl = AIUtil.embedding(pl, AIUtil.ETYPE.DOC);
            List ps = new ArrayList(vl.size());
            for(int i = 0; i < vl.size(); i++) {
            List pa = new ArrayList(2);
                pa.add(pl.get(i));
                pa.add(vl.get(i));
                ps.add(pa);
            }
            dd.put("part", Dist.toString(ps, true));
            parts = ps;
            n ++;
        }

        return n;
    }

    public void updateSegments(String id, List parts) throws CruxException {
        Data seg = getSegment();

        // 写入新的
        int i = 0;
        for(Object po : parts) {
            List   pa = (List) po;
            String jd = id+"-"+Synt.asString(i);
            seg.set ( jd, Synt.mapOf(
                "id", jd,
                "rf", id,
                "sn", i ,
                "part", pa.get(0),
                "vect", pa.get(1)
            ), 0);
            i ++ ;
        }

        // 删除多余
        List<Map> rows = seg.search(Synt.mapOf(
            "rf", id,
            "rn", Synt.mapOf(Cnst.GE_REL, i),
            Cnst.OB_KEY, Synt.setOf("sn!"),
            Cnst.RB_KEY, Synt.setOf("id" )
        ) , 0, 0).toList( );
        for(Map row : rows) {
            String jd = (String) row.get("id");
            seg.end ( jd, Synt.mapOf(
                "id", jd
            ), 0);
        }
    }

    public void deleteSegments(String id) throws CruxException {
        Data seg = getSegment();

        // 删除全部
        List<Map> rows = seg.search(Synt.mapOf(
            Cnst.RB_KEY, Synt.setOf("id"),
        //  "rn", Synt.mapOf(Cnst.GE_REL , 0 ),
            "rf", id
        ) , 0, 0).toList( );
        for(Map row : rows) {
            String jd = (String) row.get("id");
            seg.end ( jd, Synt.mapOf(
                "id", jd
            ), 0);
        }
    }

}
