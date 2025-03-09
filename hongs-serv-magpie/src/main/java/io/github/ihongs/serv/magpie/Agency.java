package io.github.ihongs.serv.magpie;

import io.github.ihongs.Cnst;
import io.github.ihongs.CruxException;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.action.SelectHelper;
import io.github.ihongs.combat.CombatHelper;
import io.github.ihongs.combat.anno.Combat;
import io.github.ihongs.serv.magpie.AIUtil.ETYPE;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Synt;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资料库维护
 *
 * 维护命令及相关方法
 *
 * @author Hongs
 */
@Combat("magpie")
public class Agency {

    /**
     * 重建向量库
     * @param args
     * @throws CruxException
     */
    @Combat("reference/rearrange")
    public static void rearrange(String[] args) throws CruxException {
        Map opts = CombatHelper.getOpts(args, new String[] {
            "find:s",
            "user:s",
            "?Usage: refresh --find QUERY"
        });

        String find = (String) opts.get("find");
        String user = (String) opts.get("user");

        Map qry = ActionHelper.parseQuery(find);
        user = Synt.defoult(user, Cnst.ADM_UID);
        ActionHelper.getInstance( ).setSessibute( Cnst.UID_SES, user );

        Data ref = Data.getInstance("centra/data/magpie", "reference");

        Agency.rearrange(ref, qry);
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

        for(Map item : list) {
            String id = (String) item.get( "id" );
            String bd = (String) item.get("body");

            List pa = AIUtil.split(bd);
            List va = AIUtil.embedding(pa, ETYPE.DOC);

            // 写入记录
            for (int i = 0; i < pa.size(); i ++ ) {
                String d = id+"-"+i ;
                Object p = pa.get(i);
                Object v = va.get(i);
                seg.set( d, Synt.mapOf(
                    "rf"  , id,
                    "id"  , d ,
                    "sn"  , i ,
                    "part", p ,
                    "vect", v
                ), 0);
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
    @Combat("reference/transform")
    public static void transform(String[] args) throws CruxException {
        Map opts = CombatHelper.getOpts(args, new String[] {
            "conf=s",
            "form=s",
            "find:s",
            "user:s",
            "?Usage: rebuild --conf CONF --form FORM --find QUERY"
        });

        String conf = (String) opts.get("conf");
        String form = (String) opts.get("form");
        String find = (String) opts.get("find");
        String user = (String) opts.get("user");

        Map qry = ActionHelper.parseQuery(find);
        user = Synt.defoult(user, Cnst.ADM_UID);
        ActionHelper.getInstance( ).setSessibute( Cnst.UID_SES, user );

        Data mod = Data.getInstance(conf, form);
        Data ref = Data.getInstance("centra/data/magpie", "reference");

        Agency.transform(ref, mod, qry);
    }

    public static void transform(Data ref, Data mod, Map qry) throws CruxException {
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

            transform(ref, mod, lis);

            bn  = bn + lop.size( );
            CombatHelper.println("Rebuilded " + bn + "/" + lop.hits());
        } while (rn <= lop.size());
    }

    public static void transform(Data ref, Data mod, List<Map> list) throws CruxException {
        String formId = mod.getFormId();

        for(Map item : list) {
            String itemId = Synt.asString(item.get(Cnst.ID_KEY));
            Map info = ref.getOne(Synt.mapOf(
                Cnst.RB_KEY, Synt.setOf(Cnst.ID_KEY),
                "s-form_id", formId,
                "s-id"     , itemId
            ));
            String refeId = Synt.asString(info.get(Cnst.ID_KEY));

            info.put("text", AIUtil.renderByTemp("transform/"+formId+".md", item));

            if (refeId != null) {
                info.put(Cnst.ID_KEY, refeId);
                ref .update(info);
            } else {
                long t = System.currentTimeMillis() / 1000;
                info.put("ctime", t);
                info.put("mtime", t);
                info.put("state", 1);
                info.put("args", Synt.setOf("form_id:"+formId, "data_id:"+itemId));
                ref .create(info);
            }
        }
    }

}
