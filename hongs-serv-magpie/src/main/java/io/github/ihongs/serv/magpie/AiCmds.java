package io.github.ihongs.serv.magpie;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CruxException;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.action.SelectHelper;
import io.github.ihongs.combat.CombatHelper;
import io.github.ihongs.combat.anno.Combat;
import io.github.ihongs.serv.magpie.tpl.TplEngine;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Synt;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 维护命令
 * @author Hongs
 */
@Combat("magpie")
public class AiCmds {

    /**
     * 重建向量库
     * @param args
     * @throws CruxException
     */
    @Combat("reference.rearrange")
    public static void rearrange(String[] args) throws CruxException {
        Map opts = CombatHelper.getOpts(args, new String[] {
            "find:s",
            "user:s",
            "?Usage: refresh --find QUERY"
        });

        String find = (String) opts.get("find");
        String user = (String) opts.get("user");
        user = Synt.defoult(user, Cnst.ADM_UID);
        find = Synt.defoult(find, "");

        Map qry = ActionHelper.parseQuery(find);
        ActionHelper.getInstance( ).setSessibute( Cnst.UID_SES, user );

        Data ref = Data.getInstance("centra/data/magpie", "reference");

        AiCmds.rearrange(ref, qry);
    }

    public static void rearrange(Data ref, Map qry) throws CruxException {
        int  rn  = 50;
        int  bn  = 0 ;
        Data.Loop lop;
        List<Map> lis;
        do {
            lop = ref.search( qry, bn, rn );
            lis = lop.toList( );

            rearrange(ref, lis);

            bn  = bn + lop.size( );
            CombatHelper.println("Refreshed " + bn + "/" + lop.hits());
        } while (rn <= lop.size());
    }

    public static void rearrange(Data ref, List<Map> list) throws CruxException {
        Data seg = ((Reference) ref).getSegment();

        Set  syn = Synt.toSet (ref.getParams().get("syncable"));
        Map  sub = new HashMap(syn.size() + 5);

        for(Map item : list) {
            String id = (String) item.get( "id" );
            String bd = (String) item.get("text");
            String st = (String) item.get("slit");

            List   pa = AiUtil.split(bd, st);
            List   va = AiUtil.embed(pa, AiUtil.ETYPE.DOC);

            // 同步数据
            for (Object kn : syn) {
                sub.put(kn, item.get(kn));
            }

            // 写入记录
            for (int i = 0; i < pa.size(); i ++) {
                String d = id+"-"+i ;
                Object p = pa.get(i);
                Object v = va.get(i);
                sub.put("rf", id);
                sub.put("id", d );
                sub.put("sn", i );
                sub.put("text", p);
                sub.put("vect", v);
                seg.set(d, new HashMap(sub), 0);
            }

            // 删除多余
            List<Map> ls = seg.getAll(Synt.mapOf(
                "rf", id ,
                "sn", Synt.mapOf(Cnst.GE_REL, pa.size()),
                Cnst.OB_KEY, Synt.setOf("sn!"),
                Cnst.RB_KEY, Synt.setOf("id" )
            ));
            Map vo = new HashMap(0);
            for (Map lo : ls) {
                seg.end((String) lo.get("id" ), vo , 0 );
            }
        }
    }

    /**
     * 转入向量库
     * @param args
     * @throws CruxException
     */
    @Combat("reference.transform")
    public static void transform(String[] args) throws CruxException {
        Map opts = CombatHelper.getOpts(args, new String[] {
            "conf=s",
            "form=s",
            "find:s",
            "user:s",
            "slit:s",
            "?Usage: rebuild --conf CONF --form FORM --find QUERY --slit SPLITTER"
        });

        String conf = (String) opts.get("conf");
        String form = (String) opts.get("form");
        String find = (String) opts.get("find");
        String user = (String) opts.get("user");
        String slit = (String) opts.get("slit");

        Map qry = ActionHelper.parseQuery(find);
        user = Synt.defoult(user, Cnst.ADM_UID);
        ActionHelper.getInstance( ).setSessibute( Cnst.UID_SES, user );

        Data mod = Data.getInstance(conf, form);
        Data ref = Data.getInstance("centra/data/magpie", "reference");

        AiCmds.transform(ref, mod, qry, slit);
    }

    public static void transform(Data ref, Data mod, Map qry, String slit) throws CruxException {
        SelectHelper sh = new SelectHelper().addItemsByForm(mod.getFields());
        byte st  = SelectHelper.TEXT | SelectHelper.LINK
                 | SelectHelper.FORK | SelectHelper.FALL;

        int  rn  = 50;
        int  bn  = 0 ;
        Data.Loop lop;
        List<Map> lis;
        Map       rsp;
        do {
            lop = mod.search( qry, bn, rn );
            lis = lop.toList( );

            rsp = Synt.mapOf( "list", lis );
            sh.inject(rsp, st );

            transform(ref, mod, lis, slit );

            bn  = bn + lop.size( );
            CombatHelper.printlr("Rebuilded " + bn + "/" + lop.total());
        } while (rn <= lop.size());
        CombatHelper.printed( );
    }

    public static void transform(Data ref, Data mod, List<Map> list, String slit) throws CruxException {
        String fid = mod.getFormId();
        String tpn = "form/" + fid + ".md";
        TplEngine  eng = TplEngine.getInstance();

        for(Map item : list) {
            String did = Synt.asString(item.get(Cnst.ID_KEY));
            Map info = ref.getOne(Synt.mapOf(Cnst.RB_KEY, Synt.setOf(Cnst.ID_KEY),
                "opts", Synt.mapOf(
                    "from", "transform",
                    "form_id", fid,
                    "data_id", did
                )
            ));
            String rid = Synt.asString(info.get(Cnst.ID_KEY));
            long t = System.currentTimeMillis( );

            if (rid == null) {
                rid  = Core.newIdentity();
                info.put("args" , Synt.setOf(
                    "from:transform",
                    "form_id:"+fid,
                    "data_id:"+did
                ));
                info.put("slit" , slit);
                info.put("state", 1);
                info.put("ctime", t);
                info.put("mtime", t);
            }

            String text = eng.render (tpn, item);
            info.put("text", text);

            String name = mod.getName(item);
            info.put("name", name);

            ref .set( rid, info, t / 1000 );
        }
    }

}
