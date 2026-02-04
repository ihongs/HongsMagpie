package io.github.ihongs.serv.magpie;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CruxException;
import io.github.ihongs.dh.lucene.quest.IQuest;
import io.github.ihongs.dh.lucene.quest.DoubleQuest;
import io.github.ihongs.dh.lucene.quest.StringQuest;
import io.github.ihongs.dh.lucene.stock.IStock;
import io.github.ihongs.dh.lucene.stock.DoubleStock;
import io.github.ihongs.dh.lucene.stock.StringStock;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Synt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.SortField;

/**
 * 资料库
 * @author Hongs
 */
public class Reference extends Data {

    private List parts;

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
        updateSegments(id, parts);
        parts = null;

        return n;
    }

    @Override
    public int put(String id, Map rd, long time) throws CruxException {
        parts = null;
        int n = super.put(id, rd, time);
        updateSegments(id, parts);
        parts = null;

        return n;
    }

    @Override
    public int set(String id, Map rd, long time) throws CruxException {
        parts = null;
        int n = super.set(id, rd, time);
        updateSegments(id, parts);
        parts = null;

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
        parts = null;
        int n = super.rev(id, rd, time);
        updateSegments(id, parts);
        parts = null;

        return n;
    }

    @Override
    protected int padDif(Map dd , Map rd) throws CruxException {
        // 合并标签
        Set tags = rd.containsKey("tags") ? Synt.toSet(rd.get("tags")) : Synt.asSet(dd.get("tags"));
        Set addTags = Synt.toSet(rd.get("add_tags"));
        if (addTags != null && ! addTags.isEmpty( )) {
            if (tags != null) {
                tags.addAll(addTags);
            } else {
                tags  = addTags ;
            }
            rd.put("tags", tags);
        }
        Set delTags = Synt.toSet(rd.get("del_tags"));
        if (delTags != null && ! delTags.isEmpty( )) {
            if (tags != null) {
                tags.removeAll(delTags);
                rd.put("tags", tags);
            }
        }
        if (rd.containsKey("tags")) {
            rd.put("tags", new TreeSet(tags));
        }

        // 合并参数
        Set args = rd.containsKey("args") ? Synt.toSet(rd.get("args")) : Synt.asSet(dd.get("args"));
        Set addArgs = Synt.toSet(rd.get("add_args"));
        if (addArgs != null && ! addArgs.isEmpty( )) {
            if (args != null) {
                args.addAll(addArgs);
            } else {
                args  = addArgs ;
            }
            rd.put("args", args);
        }
        Set delArgs = Synt.toSet(rd.get("del_args"));
        if (delArgs != null && ! delArgs.isEmpty( )) {
            if (args != null) {
                args.removeAll(delArgs);
                rd.put("args", args);
            }
        }
        if (rd.containsKey("args")) {
            rd.put("args", new TreeSet(args));
        }

        // 一般选项
        Map opts = rd.containsKey("opts") ? Synt.toMap(rd.get("opts")) : Synt.asMap(dd.get("opts"));
        Map setOpts = Synt.toMap(rd.get("set_opts"));
        if (setOpts != null && ! setOpts.isEmpty( )) {
            if (opts != null) {
                opts.putAll(setOpts);
            } else {
                opts  = setOpts ;
            }
            rd.put("opts", opts);
        }
        if (rd.containsKey("opts") ) {
            Map optz = new TreeMap();
            for(Object ot : opts.entrySet()) {
                Map.Entry et = (Map.Entry) ot;
                String k = Synt.asString(et.getKey  ());
                String v = Synt.asString(et.getValue());
                if (k != null && v != null && ! k.isEmpty() && ! v.isEmpty()) {
                    optz.put(k , v);
                }
            }
            rd.put("opts", optz);
        }

        // 数值选项
        Map optn = rd.containsKey("optn") ? Synt.toMap(rd.get("optn")) : Synt.asMap(dd.get("optn"));
        Map setOpns = Synt.toMap(rd.get("set_optn"));
        if (setOpns != null && ! setOpns.isEmpty( )) {
            if (optn != null) {
                optn.putAll(setOpns);
            } else {
                optn  = setOpns ;
            }
            rd.put("optn", optn);
        }
        if (rd.containsKey("optn") ) {
            Map opnz = new TreeMap();
            for(Object ot : optn.entrySet()) {
                Map.Entry et = (Map.Entry) ot;
                String k = Synt.asString(et.getKey  ());
                Double v = Synt.asDouble(et.getValue());
                if (k != null && v != null && ! k.isEmpty()) {
                    opnz.put(k , v);
                }
            }
            rd.put("optn", opnz);
        }

        // 拆分文本, 获取向量
        String nt = Synt.asString(rd.get("text")); // 新的内容
        String ot = Synt.asString(dd.get("text")); // 旧的内容
        if (nt != null && ! nt.equals(ot)) {
            String sp = Synt.defxult(Synt.asString(rd.get("slit")), Synt.asString(dd.get("slit")), "default");
            List pl = AiUtil.split(nt, sp);
            List vl = AiUtil.embed(pl, AiUtil.ETYPE.DOC);
            List ps = new ArrayList(vl.size());
            for(int i = 0; i < vl.size(); i++) {
            List pa = new ArrayList(2);
                pa.add(pl.get(i));
                pa.add(vl.get(i));
                ps.add(pa);
            }
            dd.put("slit" , sp);
            dd.put("parts", ps);
            parts = ps;
        }

        return super.padDif(dd, rd);
    }

    @Override
    protected void padDoc(Document doc, Map map, Set rep) {
        super.padDoc(doc, map, rep);

        // 写入一般选项
        Map opts  = Synt.asMap(map.get("opts"));
        if (opts != null && !opts.isEmpty ()) {
            for (Object ot : opts.entrySet()) {
                Map.Entry et = (Map.Entry) ot;
                String k = Synt.declare(et.getKey  (), "");
                String v = Synt.declare(et.getValue(), "");
                IStock s = new StringStock( );
                k = "opts." + k;
                doc.add(s.whr(k, v));
                doc.add(s.odr(k, v));
            }
        }

        // 写入数值选项
        Map optn  = Synt.asMap(map.get("optn"));
        if (optn != null && !optn.isEmpty ()) {
            for (Object ot : optn.entrySet()) {
                Map.Entry et = (Map.Entry) ot;
                String k = Synt.declare(et.getKey  (), "");
                Double v = Synt.declare(et.getValue(), 0D);
                IStock s = new DoubleStock( );
                k = "optn." + k;
                doc.add(s.whr(k, v));
                doc.add(s.odr(k, v));
            }
        }
    }

    @Override
    protected boolean padQry(BooleanQuery.Builder qr, Map rd, String k, Object v) throws CruxException {
        // 查询一般选项
        if ("opts".equals(k)) {
            Map m = Synt.asMap(v);
            for(Object o : m.entrySet() ) {
                Map.Entry e = (Map.Entry) o ;
                Object n = e.getKey  ();
                Object w = e.getValue();
                IQuest q = new StringQuest();
                padQry(qr, rd, "opts."+n, w, q);
            }
            return true;
        }

        // 查询数值选项
        if ("optn".equals(k)) {
            Map m = Synt.asMap(v);
            for(Object o : m.entrySet() ) {
                Map.Entry e = (Map.Entry) o ;
                Object n = e.getKey  ();
                Object w = e.getValue();
                IQuest q = new DoubleQuest();
                padQry(qr, rd, "optn."+n, w, q);
            }
            return true;
        }

        return super.padQry(qr, rd, k, v);
    }

    @Override
    protected boolean padSrt(List<SortField> sr, Map rd, String k, boolean r) throws CruxException {
        // 一般选项排序
        if (k.startsWith("opts.")) {
            sr.add(new SortField("#" + k, SortField.Type.STRING, r));
            return true;
        }

        // 数值选项排序
        if (k.startsWith("optn.")) {
            sr.add(new SortField("#" + k, SortField.Type.DOUBLE, r));
            return true;
        }

        return super.padSrt(sr, rd, k, r);
    }

    @Override
    protected boolean missable(String fn, Object fo, Object fr) {
        if (fn.startsWith("add_")    // 增加标签
        ||  fn.startsWith("del_")) { // 删减标签
            return true;
        }
        if (super.missable(fn, fo, fr)) {
            return true;
        }
        return false;
    }

    public void updateSegments(String id, List parts) throws CruxException {
        if (parts == null) {
            return;
        }

        Data seg = getSegment();

        // 写入新的
        int i = 0;
        if (! parts.isEmpty() ) {
            for(Object po : parts) {
                List   pa = (List) po;
                String jd = id+"-"+Synt.asString(i);
                Map ba = new HashMap(5);
                ba.put ("id" , jd);
                ba.put ("rf" , id);
                ba.put ("sn" , i );
                ba.put ("text", pa.get(0));
                ba.put ("vect", pa.get(1));
                seg.set(jd, ba, 0);
                i ++ ;
            }
        }

        // 删除多余
        List<Map> rows = seg.search(Synt.mapOf(
            "rf", id,
            "sn", Synt.mapOf(Cnst.GE_REL, i),
            Cnst.OB_KEY, Synt.setOf("sn!"),
            Cnst.RB_KEY, Synt.setOf("id" )
        ) , 0, 0).toList( );
        for(Map row : rows) {
            String jd = (String) row.get("id");
            Map ba = new HashMap(1);
            ba.put ("id" , jd);
            seg.end(jd, ba, 0);
        }
    }

    public void deleteSegments(String id) throws CruxException {
        Data seg = getSegment();

        // 删除全部
        List<Map> rows = seg.search(Synt.mapOf(
            "rf", id,
        //  "sn", Synt.mapOf(Cnst.GE_REL, 0),
        //  Cnst.OB_KEY, Synt.setOf("sn!"),
            Cnst.RB_KEY, Synt.setOf("id" )
        ) , 0, 0).toList( );
        for(Map row : rows) {
            String jd = (String) row.get("id");
            Map ba = new HashMap(1);
            ba.put ("id" , jd);
            seg.end(jd, ba, 0);
        }
    }

}
