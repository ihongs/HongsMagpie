package io.github.ihongs.serv.centra;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CoreLocale;
import io.github.ihongs.CoreLogger;
import io.github.ihongs.CruxException;
import io.github.ihongs.CruxExemption;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.action.anno.Action;
import io.github.ihongs.action.anno.CustomReplies;
import io.github.ihongs.action.anno.Preset;
import io.github.ihongs.dh.Roster;
import io.github.ihongs.serv.magpie.AiUtil;
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
import java.util.concurrent.Future;
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
        Map rd = helper.getRequestData();

        String sid = Synt.asString(  rd.get("session_id")  );
        String prompt = Synt.declare(rd.get("prompt"), ""  );
        String system = Synt.declare(rd.get("system"), ""  );
        String model  = Synt.declare(rd.get("model" ), ""  );
        String query  = Synt.declare(rd.get("query" ), ""  );
        float  minUp  = Synt.declare(rd.get("min_up"), 0.5f);
        int    maxRn  = Synt.declare(rd.get("max_rn"), 10  );
        int    maxSn  = Synt.declare(rd.get("max_sn"), 20  );
        int    maxTk  = Synt.declare(rd.get("max_tk"), 0   );
        int    stream = Synt.declare(rd.get("stream"), 0   );

        // 会话ID
        if (sid == null || sid.isEmpty()) {
            sid =  Core.newIdentity( );
            rd.put("session_id" , sid);
        }

        if (stream < 2) {
            stream(helper, rd, stream);
            return;
        }

        // 缓存半分钟, 等下个接口取
        Roster.put("magpie.stream."+sid, Synt.mapOf(
            "prompt", prompt,
            "system", system,
            "model" , model ,
            "query" , query ,
            "min_up", minUp ,
            "max_rn", maxRn ,
            "max_sn", maxSn ,
            "max_tk", maxTk ,
            "session_id" , sid
        ), 30);
        helper.reply(Synt.mapOf(
            "session_id" , sid
        ));
    }

    @Action("cancel")
    @CustomReplies
    public void cancel(ActionHelper helper) throws CruxException {
        Map rd = helper.getRequestData();
        String id;

        id = Synt.declare(rd.get("session_id"), "");
        if (id == null || id.isEmpty()) {
            throw new CruxException(400, "session_id required");
        }

        Future df = (Future) Core.getInterior().get("magpie.stream."+id);
        if (df != null) {
            df.cancel (true);
            helper.reply("");
        } else {
            helper.fault("");
        }
    }

    @Action("stream")
    @CustomReplies
    public void stream(ActionHelper helper) throws CruxException {
        Map rd = helper.getRequestData();
        String id;

        id = Synt.declare(rd.get("session_id"), "");
        if (id == null || id.isEmpty()) {
            throw new CruxException(400, "session_id required");
        }

        rd = (Map) Roster.get("magpie.stream."+ id);
        if ( rd == null ||  rd.isEmpty()) {
            throw new CruxException(400, "session_id invalid" );
        }
        Roster.del("magpie.stream."+ id);

        stream(helper, rd, 1);
    }

    private void stream(ActionHelper helper, Map rd, int stream) throws CruxException {
        String sid = Synt.asString(  rd.get("session_id")  );
        String prompt = Synt.declare(rd.get("prompt"), ""  );
        String system = Synt.declare(rd.get("system"), ""  );
        String remind = Synt.declare(rd.get("remind"), ""  );
        String model  = Synt.declare(rd.get("model" ), ""  );
        String query  = Synt.declare(rd.get("query" ), ""  );
        float  minUp  = Synt.declare(rd.get("min_up"), 0.5f);
        int    maxRn  = Synt.declare(rd.get("max_rn"), 10  );
        int    maxSn  = Synt.declare(rd.get("max_sn"), 20  );
        int    maxTr  = Synt.declare(rd.get("max_tr"), 1   );
        int    maxTk  = Synt.declare(rd.get("max_tk"), 0   );
        double tmpr   = Synt.declare(rd.get("temperature"  ), 0d );
        double topP   = Synt.declare(rd.get("top_p" ), 0d  );
        int    topK   = Synt.declare(rd.get("top_k" ), 0   );
        Set<String> tools = Synt.asSet( rd.get ( "tools" ) );

        CoreLocale cl = CoreLocale.getInstance ( "magpie"  );

        // 提取上下文
        List<Map> messages = Synt.asList(rd.get("messages"));
        if (messages != null && ! messages.isEmpty()) {
            messages  = new ArrayList ( messages );

            StringBuilder ms = new StringBuilder();
            for (Map ma : messages) {
                ms.append("- " )
                  .append(ma.get("role"))
                  .append(":\n")
                  .append(Syno.indent((String) ma.get("content"), "  "))
                  .append( "\n");
            }

            if (remind == null || remind.isBlank()) {
                remind = cl.getProperty("magpie.assistant.remind");
            }
            remind = Syno.inject( remind , Synt.mapOf(
                "messages", ms , "prompt", prompt
            ));
            CoreLogger.debug("Remind: {}", remind);

            remind = AiUtil.chat("reminding", Synt.listOf(
                Synt.mapOf(
                    "role", "user",
                    "content", remind
                )
            ));
            CoreLogger.debug("Remind: {}", remind);
        } else {
            messages  = new ArrayList () ;

            remind = prompt;
        }

        // 获取向量
        Object vect = AiUtil.embed(Synt.listOf(remind), AiUtil.ETYPE.QRY).get(0);

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
        qry.put(Cnst.RB_KEY, Synt.setOf("rf", "id", "sn", "text"));
        Data ref = Data.getInstance("centra/data/magpie", "reference");
        Data seg = Data.getInstance("centra/data/magpie", "reference-segment");
        Data.Loop loop = seg.search(qry, 0, maxRn);

        // 引用资料
        List<Map> refs = new ArrayList();
        if (loop.count() > 0 ) {
            StringBuilder ps = new StringBuilder();
            Set rb = Synt.toSet(ref.getParams().get("showable"));
            Set rl = new HashSet( );
            for(Map pa : loop) {
                Object rf = pa.get("rf");
                if (! rl.contains ( rf )) {
                      rl.add(rf);
                    refs.add(ref.getOne(Synt.mapOf(
                        Cnst.ID_KEY, Synt.mapOf(Cnst.IN_REL, rf),
                        Cnst.RB_KEY, rb
                    )));
                }
                ps.append(pa.get("text"))
                  .append("\n========\n");
            }
            ps .setLength(ps.length()-10);

            if (system == null || system.isBlank()) {
                system = cl.getProperty("magpie.assistant.system");
            }
            system = Syno.inject( system, Synt.mapOf(
                "documents", ps
            ));
            CoreLogger.debug("System: {}", system);

            messages.add(0, Synt.mapOf(
                "role", "system",
                "content", system
            ));
        }

        messages.add(Synt.mapOf(
            "role", "user",
            "content", prompt
        ));

        if (stream != 0) {
            HttpServletResponse rsp = helper.getResponse();
            rsp.setHeader("Connection" , "keep-alive");
            rsp.setHeader("Cache-Control", "no-store");
            rsp.setContentType ( "text/event-stream" );
            rsp.setCharacterEncoding("UTF-8");

            Writer out = helper.getOutputWriter();
            try {
                out.write("data:"
                  + Dist.toString(Synt.mapOf(
                        "references", refs,
                        "session_id", sid
                    ), true)
                  + "\n\n");
                out.flush();
            } catch (IOException e) {
                throw new CruxException(e);
            }

            StringBuilder sb = new StringBuilder();
            try {
                Future ft = AiUtil.chat(model, messages, tools, tmpr, topP, topK, maxTk, maxTr, (token)-> {
                    try {
                        if (!token.isEmpty()) {
                            String thunk = "data:{\"text\":\""+Dist.doEscape(token)+"\"}\n\n";
                            sb.append(token);
                            out.write(thunk);
                            out.flush(  );
                        } else {
                            out.write("");
                            out.flush(  );
                        }
                    } catch ( IOException e ) {
                        throw new CruxExemption(e);
                    }
                });
                // 登记任务并等待
                Core.getInterior().put("magpie.stream."+sid, ft);
                try {
                    ft.get();
                } catch (Exception ex) {
                    CoreLogger.trace(ex.toString());
                }
            } finally {
                Core.getInterior().remove("magpie.stream."+ sid);
                if (! sb.isEmpty()) {
                    String result = sb.toString();

                    // 完整内容
                    try {
                        String thunk = "data:{\"content\":\""+Dist.doEscape(result)+"\"}";
                        out.write(thunk);
                        out.flush(  );
                        out.close(  );
                    } catch ( IOException e ) {
                        throw new CruxExemption(e);
                    }
                }
            }
        } else {
            StringBuilder sb = new StringBuilder();
            try {
                Future ft = AiUtil.chat(model, messages, tools, tmpr, topP, topK, maxTk, maxTr, (token)-> {
                    if (!token.isEmpty()) {
                        sb.append(token);
                    }
                });
                // 登记任务并等待
                Core.getInterior().put("magpie.stream."+sid, ft);
                try {
                    ft.get();
                } catch (Exception ex) {
                    CoreLogger.trace(ex.toString());
                }
            } finally {
                Core.getInterior().remove("magpie.stream."+ sid);
                if (! sb.isEmpty()) {
                    String result = sb.toString();

                    // 输出结果
                    helper.reply(Synt.mapOf(
                        "references", refs,
                        "session_id", sid ,
                        "content", result
                    ));
                }
            }
        }
    }

}
