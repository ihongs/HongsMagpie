package io.github.ihongs.serv.magpie;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CruxException;
import io.github.ihongs.dh.lucene.quest.IQuest;
import io.github.ihongs.dh.lucene.quest.DoubleQuest;
import io.github.ihongs.dh.lucene.quest.StringQuest;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Dict;
import io.github.ihongs.util.Synt;
import io.github.ihongs.util.verify.Wrong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.search.BooleanQuery;

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
        Set tags = rd.containsKey("tags") ? Synt.asSet(rd.get("tags")) : Synt.asSet(dd.get("tags"));
        Set args = rd.containsKey("args") ? Synt.asSet(rd.get("args")) : Synt.asSet(dd.get("args"));

        // 合并标签
        Set addTags  = Synt.toSet(rd.get("add_tags"));
        if (addTags != null && ! addTags.isEmpty()) {
            if (tags != null) {
                tags.addAll(addTags);
            } else {
                tags  = addTags ;
            }
            rd.put("tags", tags);
        }
        Set delTags  = Synt.toSet(rd.get("del_tags"));
        if (delTags != null && ! delTags.isEmpty()) {
            if (tags != null) {
                tags.removeAll(delTags);
                rd.put("tags", tags);
            }
        }

        // 合并参数
        Set addArgs  = Synt.toSet(rd.get("add_args"));
        if (addArgs != null && ! addArgs.isEmpty()) {
            if (args != null) {
                args.addAll(addArgs);
            } else {
                args  = addArgs ;
            }
            rd.put("args", args);
        }
        Set delArgs  = Synt.toSet(rd.get("del_args"));
        if (delArgs != null && ! delArgs.isEmpty()) {
            if (args != null) {
                args.removeAll(delArgs);
                rd.put("args", args);
            }
        }

        // 解析参数
        if (rd.containsKey("args")) {
            args = Synt.asSet(rd.get("args"));
            Set argz = new TreeSet();
            Map opts = new TreeMap();
            Map nums = new TreeMap();
            Pattern pat = Pattern.compile("^[^\\s\\[\\]\\.:=?&#%]+$");

            // 反向遍历, 以便处理后面的覆盖前面的
            ListIterator l = new ArrayList(args).listIterator();
            while (l.hasPrevious()) {
                String arg = Synt.asString(l.previous());

                int i;
                i = arg.indexOf (":");
                if (i > 0) {
                    String k = arg.substring(0,i);
                    if (! opts.containsKey(k)) {
                    String v = arg.substring(1+i);
                        if ( ! pat.matcher(k).matches()) {
                            throw new Wrong("@magpie:magpie.reference.opts.key.invalid")
                                . withLabel(Dict.get(getFields(), "args", "args", "__text__"));
                        }
                        opts.put(k,v);
                        args.add(arg);
                    }
                    continue;
                }

                i = arg.indexOf ("=");
                if (i > 0) {
                    String k = arg.substring(0,i);
                    if (! nums.containsKey(k)) {
                    String v = arg.substring(1+i);
                        if ( ! pat.matcher(k).matches()) {
                            throw new Wrong("@magpie:magpie.reference.opts.key.invalid")
                                . withLabel(Dict.get(getFields(), "args", "args", "__text__"));
                        }
                        Double n;
                        String s;
                        try {
                            n = Synt.asDouble (v);
                            s = Synt.asString (n);
                            arg = k +"="+ s; // 统一数字格式
                        } catch ( ClassCastException e ) {
                            throw new Wrong("@magpie:magpie.reference.nums.val.invalid")
                                . withLabel(Dict.get(getFields(), "args", "args", "__text__"));
                        }
                        nums.put(k,n);
                        args.add(arg);
                    }
                    continue;
                }

                argz.add(arg);
            }

            rd.put("args", argz);
            rd.put("opts", opts);
            rd.put("nums", nums);
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

        // 写入数值选项
        Map nums = Synt.asMap(map.get("nums"));
        if (nums != null && !nums.isEmpty ()) {
            for (Object ot : nums.entrySet()) {
                Map.Entry et = (Map.Entry) ot ;
                Object k = et.getKey  ();
                Object v = et.getValue();
                doc.add(new DoublePoint("@n."+k, Synt.declare(v, 0D)));
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
