package io.github.ihongs.serv.magpie;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CruxException;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Dict;
import io.github.ihongs.util.Synt;
import io.github.ihongs.util.verify.Wrong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 资料库
 * @author Hongs
 */
public class Reference extends Segment {

    private final Set syns; // 需同步的字段
    private       int sync; // 当前同步数
    private       Map data; // 当前数据

    protected Reference(String conf, String form) {
        super(conf, form);

        syns = Synt.toSet(getParams().get("syncable"));
    }

    public static Reference getInstance(String conf, String form) {
        return Core.getInstance().got(Data.class.getName()+":"+conf+":"+form, ()->new Reference(conf, form));
    }

    public Data getSegment() throws CruxException {
        return Data.getInstance("centra/data/magpie", "reference-segment");
    }

    @Override
    public int add(String id, Map rd, long time) throws CruxException {
        sync = 0;
        data = null;

        int n = super.add(id, rd, time);
        if (sync > 0) {
            updateSegments(id, data);
        }

        sync = 0;
        data = null;

        return n;
    }

    @Override
    public int put(String id, Map rd, long time) throws CruxException {
        sync = 0;
        data = null;

        int n = super.put(id, rd, time);
        if (sync > 0) {
            updateSegments(id, data);
        }

        sync = 0;
        data = null;

        return n;
    }

    @Override
    public int set(String id, Map rd, long time) throws CruxException {
        sync = 0;
        data = null;

        int n = super.set(id, rd, time);
        if (sync > 0) {
            updateSegments(id, data);
        }

        sync = 0;
        data = null;

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
            updateSegments(id, get(id));
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
            Pattern p = Pattern.compile("^[^\\s\\[\\]\\.:=?&#%]+$");
            for(Object obj : args) {
                String arg = Synt.asString( obj );
                int i = arg.indexOf(":");
                if (i > 0) {
                    String k = arg.substring(0,i);
                    String v = arg.substring(1+i);
                    if ( ! p.matcher(k).matches()) {
                        throw new Wrong("@magpie:magpie.reference.opts.key.invalid", " .:=?&#%[]")
                            . withLabel(Dict.get(getFields(), "args", "args", "__text__"));
                    }
                    opts.put(k, v);
                } else {
                    i = arg.indexOf("=");
                if (i > 0) {
                    String k = arg.substring(0,i);
                    String v = arg.substring(1+i);
                    if ( ! p.matcher(k).matches()) {
                        throw new Wrong("@magpie:magpie.reference.opts.key.invalid", " .:=?&#%[]")
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

        int n = 0;

        // 拆分文本, 获取向量
        String nt = Synt.asString(rd.get("text")); // 新的内容
        String ot = Synt.asString(dd.get("text")); // 旧的内容
        String sp = Synt.defxult(Synt.asString(rd.get("slit")), Synt.asString(dd.get("slit")), "default");
        if (nt != null && ! nt.equals(ot)) {
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
            sync ++ ;
            n ++ ;
        }

        data = dd;

        return n + super.padDif(dd, rd);
    }

    @Override
    protected boolean missable(String fn, Object fo, Object fr) {
        if (fn.startsWith("add-")    // 增加标签
        ||  fn.startsWith("del-")) { // 删减标签
            return true;
        }
        if (super.missable(fn, fo, fr)) {
            return true;
        }
        if (syns .contains(fn)) {
            sync ++ ;
        }
        return false;
    }

    public void updateSegments(String id, Map data) throws CruxException {
        List parts  = Synt.asList(data.get("parts"));
        if ( parts == null ) {
             return ;
        }

        Data seg = getSegment();

        // 写入新的
        int i = 0;
        if (! parts.isEmpty() ) {
            Map da = new HashMap(syns.size());
            for(Object kn : syns ) {
                da.put(kn , data . get( kn ));
            }
            for(Object po : parts) {
                List   pa = (List) po;
                String jd = id+"-"+Synt.asString(i);
                Map ba = new HashMap(da.size() + 5);
                ba.putAll(da);
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
            "rn", Synt.mapOf(Cnst.GE_REL, i),
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
            Cnst.RB_KEY, Synt.setOf("id"),
        //  "rn", Synt.mapOf(Cnst.GE_REL , 0 ),
            "rf", id
        ) , 0, 0).toList( );
        for(Map row : rows) {
            String jd = (String) row.get("id");
            Map ba = new HashMap(1);
            ba.put ("id" , jd);
            seg.end(jd, ba, 0);
        }
    }

}
