package io.github.ihongs.serv.magpie;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CruxException;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Dict;
import io.github.ihongs.util.Dist;
import io.github.ihongs.util.Synt;
import io.github.ihongs.util.verify.Wrong;
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
    protected int padDif(Map dd , Map rd) throws CruxException {
        Set tags = rd.containsKey("tags") ? Synt.asSet(rd.get("tags")) : Synt.asSet(dd.get("tags"));
        Set args = rd.containsKey("args") ? Synt.asSet(rd.get("args")) : Synt.asSet(dd.get("args"));

        // 合并标签
        Set addTags  = Synt.toSet(rd.get("add-tags"));
        if (addTags != null && ! addTags.isEmpty()) {
            if (tags != null) {
                tags.addAll(addTags);
            } else {
                tags  = addTags ;
            }
            rd.put("tags", tags);
        }
        Set delTags  = Synt.toSet(rd.get("del-tags"));
        if (delTags != null && ! delTags.isEmpty()) {
            if (tags != null) {
                tags.removeAll(delTags);
                rd.put("tags", tags);
            }
        }

        // 合并参数
        Set addArgs  = Synt.toSet(rd.get("add-args"));
        if (addArgs != null && ! addArgs.isEmpty()) {
            if (args != null) {
                args.addAll(addArgs);
            } else {
                args  = addArgs ;
            }
            rd.put("args", args);
        }
        Set delArgs  = Synt.toSet(rd.get("del-args"));
        if (delArgs != null && ! delArgs.isEmpty()) {
            if (args != null) {
                args.removeAll(delArgs);
                rd.put("args", args);
            }
        }

        // 解析参数
        if (rd.containsKey("args")) {
            Map opts;
            opts = new TreeMap();
            args = Synt.asSet(rd.get("args"));
            Pattern p = Pattern.compile("^[^\\s\\[\\]\\.:=?&#]+$");
            for(Object obj : args) {
                String arg = Synt.asString( obj );
                int i = arg.indexOf(":");
                if (i > 0) {
                    String k = arg.substring(0,i);
                    String v = arg.substring(1+i);
                    if ( ! p.matcher(k).matches()) {
                        throw new Wrong("@magpie:magpie.reference.opts.key.invalid", " .:=?&#[]")
                            . withLabel(Dict.get(getFields(), "args", "args", "__text__"));
                    }
                    opts.put(k, v);
                } else {
                    i = arg.indexOf("=");
                if (i > 0) {
                    String k = arg.substring(0,i);
                    String v = arg.substring(1+i);
                    if ( ! p.matcher(k).matches()) {
                        throw new Wrong("@magpie:magpie.reference.opts.key.invalid", " .:=?&#[]")
                            . withLabel(Dict.get(getFields(), "args", "args", "__text__"));
                    }
                    try {
                        opts.put(k, Synt.asDouble(v));
                    } catch (ClassCastException e) {
                        throw new Wrong("@magpie:magpie.reference.nums.val.invalid")
                            . withLabel(Dict.get(getFields(), "args", "args", "__text__"));
                    }
                }}
            }
            rd.put("opts", opts);
        }

        // 新旧内容
        String nt = Synt.asString(rd.get("text"));
        String ot = Synt.asString(dd.get("text"));

        int n = super.padDif(dd , rd);

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

    @Override
    protected boolean missable(String fn, Object fo, Object fr) {
        if (fn.startsWith("add-") || fn.startsWith("del-")) {
            return true;
        }
        return super.missable(fn, fo, fr);
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
