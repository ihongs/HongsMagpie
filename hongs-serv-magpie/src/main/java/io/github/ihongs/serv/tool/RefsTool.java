package io.github.ihongs.serv.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.ihongs.Cnst;
import io.github.ihongs.CoreLocale;
import io.github.ihongs.CruxException;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.serv.magpie.AiUtil;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Synt;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 引用工具
 * @author Admin
 */
public class RefsTool implements Env {

    private Map ENV;

    @Override
    public void env(Map env) {
        ENV = env;
    }

    @Tool("refs: Referencer. Get references."
       + " Return reference contents, splice by '========'."
       + " Returns an empty string if no reference.")
    public String refs(
        @P("query text, required")
        String remind
    ) throws CruxException {
        Map rd = Synt.asMap(ENV.get("REQUEST"));

        String query  = Synt.declare(rd.get("query" ), ""  );
        int    quote  = Synt.declare(rd.get("quote" ), 1   );
        int    maxRn  = Synt.declare(rd.get("max_rn"), 10  );
        float  minUp  = Synt.declare(rd.get("min_up"), 0.5f);

        CoreLocale cl = CoreLocale.getInstance("magpie");
        if (remind == null || remind.isBlank()) {
            return cl.getProperty("magpie.assistant.remind.empty");
        }
        if ( query == null ||  query.isEmpty()) {
            return cl.getProperty("magpie.assistant.relate.empty");
        }

        StringBuilder scts = new StringBuilder();
        List<Map> refs = Synt.asList(ENV.get("REFS"));
        List<Map> segs = Synt.asList(ENV.get("SEGS"));
        if (refs == null) {
            refs  = new ArrayList();
            ENV.put( "REFS", refs );
        }
        if (segs == null) {
            segs  = new ArrayList();
            ENV.put( "SEGS", segs );
        }

        Map        find;
        Object     vect = null;
        Data.Loop  loop = null;
        Data ref = Data.getInstance("centra/data/magpie", "reference");
        Data seg = Data.getInstance("centra/data/magpie", "reference-segment");
        Set rb = Synt.toSet( ref.getParams( ).get("showable") );
        Map rq = Synt.mapOf(Cnst.RB_KEY, rb, Cnst.ID_KEY, null);
        Set rz = new HashSet( );

        if (query.startsWith("?")
        ||  query.startsWith("&")) {
            find = ActionHelper.parseQuery(query.substring(1));
        } else {
            find = Synt.toMap(query);
        }

        find.put("state", Synt.mapOf(
            Cnst.GT_REL , 0
        ));

        switch (quote) {
        case 0:
            // 引用全文
            rb = new HashSet(rb);
            rb . add(  "text"  );
            find.put(Cnst.RB_KEY, rb);
            loop = ref.search(find, 0, maxRn);
            if (loop != null && loop.count() > 0) {
                for (Map pa : loop) {
                    Object rf = pa.get("id");
                    if (! rz.contains ( rf )) {
                          rz.add(rf);
                        refs.add(pa);
                    }

                    scts.append(pa.get("text"))
                        .append("\n========\n");
                      pa.remove("text");
                }
                scts.setLength(scts.length() - 10);
            }
            break;
        case 2:
            // 查询片段，引用全文
            rb = new HashSet(rb);
            rb . add(  "text"  );
            find.put(Cnst.OB_KEY, Synt.setOf("-" ));
            find.put(Cnst.RB_KEY, Synt.setOf("rf", "id", "sn"));
            vect = AiUtil.embed(Synt.listOf(remind), AiUtil.ETYPE.QRY).get(0);
            find.put("vect" , Synt.mapOf(
                Cnst.AT_REL , vect,
                Cnst.UP_REL , minUp
            ));
            loop = seg.search(find, 0, maxRn * 5);
            if (loop != null && loop.count() > 0) {
                for (Map pa : loop) {
                    Object rf = pa.get("rf");
                    if (! rz.contains ( rf )) {
                        rz.add( rf );
                        rq.put(Cnst.ID_KEY, rf);
                        Map pr = ref.getOne(rq);
                        refs.add(pr);

                    scts.append(pr.get("text"))
                        .append("\n========\n");
                      pr.remove("text");
                    }

                    seg.add(pa);

                    if (refs.size() >= maxRn) {
                        break;
                    }
                }
                scts.setLength(scts.length() - 10);
            }
            break;
        case 1:
            // 查询片段, 引用片段
            find.put(Cnst.OB_KEY, Synt.setOf("-" ));
            find.put(Cnst.RB_KEY, Synt.setOf("rf", "id", "sn", "text"));
            vect = AiUtil.embed(Synt.listOf(remind), AiUtil.ETYPE.QRY).get(0);
            find.put("vect" , Synt.mapOf(
                Cnst.AT_REL , vect,
                Cnst.UP_REL , minUp
            ));
            loop = seg.search(find, 0, maxRn * 5);
            if (loop != null && loop.count() > 0) {
                for (Map pa : loop) {
                    Object rf = pa.get("rf");
                    if (! rz.contains ( rf )) {
                        rz.add( rf );
                        rq.put(Cnst.ID_KEY, rf);
                        Map pr = ref.getOne(rq);
                        refs.add(pr);
                    }

                    scts.append(pa.get("text"))
                        .append("\n========\n");
                      pa.remove("text");

                    segs.add(pa);

                    if (refs.size() >= maxRn) {
                        break;
                    }
                }
                scts.setLength(scts.length() - 10);
            }
            break;
        default:
            throw new UnsupportedOperationException("Unsupported quote mode: "+quote);
        }

        if (! scts.isEmpty()) {
            return scts.toString();
        }

        return cl.getProperty("magpie.assistant.relate.empty");
    }

}
