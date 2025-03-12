package io.github.ihongs.serv.centra;

import io.github.ihongs.Cnst;
import io.github.ihongs.CoreLocale;
import io.github.ihongs.CoreLogger;
import io.github.ihongs.CruxException;
import io.github.ihongs.CruxExemption;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.action.anno.Action;
import io.github.ihongs.action.anno.CustomReplies;
import io.github.ihongs.action.anno.Preset;
import io.github.ihongs.serv.magpie.AIUtil;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Dist;
import io.github.ihongs.util.Syno;
import io.github.ihongs.util.Synt;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletResponse;

/**
 * 消息接口
 * @author Hongs
 */
@Action("centra/data/magpie/assistant-message")
public class MagpieMessage {

    @Action("censor")
    @Preset(conf="", form="")
    @CustomReplies
    public void censor(ActionHelper helper) throws CruxException {
        try {
            Map rd = helper.getRequestData( );

            String prompt = Synt.declare(rd.get("prompt"), ""  );
            String system = Synt.declare(rd.get("system"), ""  );
            String remind = prompt;
            String model  = Synt.declare(rd.get("model" ), ""  );
            String query  = Synt.declare(rd.get("query" ), ""  );
            float  minUp  = Synt.declare(rd.get("min_up"), 0.5f);
            int    maxRn  = Synt.declare(rd.get("max_rn"), 10  );
            int    maxSn  = Synt.declare(rd.get("max_sn"), 20  );
            int    maxTk  = Synt.declare(rd.get("max_tk"), 0   );

            CoreLocale cl = CoreLocale.getInstance ( "magpie"  );

            // 提取上下文
            List<Map> messages = Synt.asList(rd.get("messages"));
            if (messages != null && ! messages.isEmpty()) {
                StringBuilder ms = new StringBuilder();
                for (Map ma : messages) {
                    ms.append("- " )
                      .append(ma.get("role"))
                      .append(":\n")
                      .append(Syno.indent((String) ma.get("content"), "  "))
                      .append( "\n");
                }
                remind = cl.translate("magpie.ai.remind.temp", Synt.mapOf(
                    "messages", ms , "prompt", prompt
                ));
                CoreLogger.debug("Remind: {}", remind);

                remind = AIUtil.chat("remind", Synt.listOf(
                    Synt.mapOf(
                        "role", "user",
                        "content", remind
                    )
                ));
                CoreLogger.debug("Remind: {}", remind);

                messages = new ArrayList(messages);
            } else {
                messages = new ArrayList();
            }

            // 获取向量
            Object vect = AIUtil.embedding(Synt.listOf(remind), AIUtil.ETYPE.QRY).get(0);

            // 查询资料
            Map qry;
            if (query.startsWith("?")) {
                qry = ActionHelper.parseQuery(query);
            } else {
                qry = Synt.toMap(query);
            }
            qry.put("vect" , Synt.mapOf(
                Cnst.AT_REL, vect,
                Cnst.UP_REL, minUp
            ));
            qry.put("state", Synt.mapOf(
                Cnst.GT_REL, 0
            ));
            qry.put(Cnst.OB_KEY, Synt.setOf("-"));
            qry.put(Cnst.RB_KEY, Synt.setOf("rf" , "id" , "sn" , "text"));
            Data seg = Data.getInstance("centra/data/magpie", "reference-segment");
            Data.Loop loop = seg.search(qry, 0, maxRn);

            // 引用资料
            List<Map> refs;
            Set rids = new HashSet( );
            Set eids = new HashSet( );
            if (loop.count() > 0) {
                StringBuilder ps = new StringBuilder();
                for (Map pa : loop) {
                    rids.add (pa.get( "rf" ));
                    eids.add (pa.get( "id" ));
                    ps.append(pa.get("text"))
                      .append("\n========\n");
                }
                ps .setLength(ps.length( )-8);

                refs = seg.getAll(Synt.mapOf(
                    Cnst.ID_KEY, Synt.mapOf(Cnst.IN_REL,  rids ),
                    Cnst.RB_KEY, Synt.setOf(Cnst.ID_KEY, "name")
                ));

                if (system == null || system.isBlank()) {
                    system = cl.getProperty("magpie.ai.system.temp");
                }
                system = Syno.inject( system, Synt.mapOf(
                    "documents", ps
                ));
                CoreLogger.debug("System: {}", system);

                messages.add(0, Synt.mapOf(
                    "role", "system",
                    "content", system
                ));
            } else {
                refs = new ArrayList(0);
            }

            messages.add(Synt.mapOf(
                "role", "user",
                "content", prompt
            ));

            // 流式输出
            HttpServletResponse rsp = helper.getResponse();
            Writer out = rsp.getWriter();
            rsp.setContentType("text/event-stream");
            rsp.setCharacterEncoding("UTF-8");
            rsp.setHeader("Connection", "keep-alive");
            rsp.setHeader("Cache-Control","no-cache");

            // 输出引用
            out.write(Dist.toString(Synt.mapOf(
                "references", refs
            ) , true ));
            out.flush();

            StringBuilder sb = new StringBuilder( );
            try {
                AIUtil.chat(model, messages, (thunk)-> {
                    try {
                        sb.append(thunk);
                        out.write(thunk);
                        out.flush();
                    }
                    catch (IOException e) {
                        throw new CruxExemption(e);
                    }
                });
            } finally {
                if (! sb.isEmpty()) {
                }
            }
        }
        catch (IOException ex) {
            throw new CruxException(ex);
        }
    }

}
