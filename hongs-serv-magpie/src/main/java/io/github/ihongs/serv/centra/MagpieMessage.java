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
import java.util.HashMap;
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

    @Action("create")
    public void create(ActionHelper helper) throws CruxException {
        throw new CruxException(404, "Unsupported");
    }

    @Action("update")
    public void update(ActionHelper helper) throws CruxException {
        throw new CruxException(404, "Unsupported");
    }

    @Action("aerate")
    @Preset(conf="", form="")
    @CustomReplies
    public void aerate(ActionHelper helper) throws CruxException {
        Map rd = helper.getRequestData();

        String sid = Synt.asString(  rd.get("session_id")  );
        String prompt = Synt.declare(rd.get("prompt"), ""  );
        String system = Synt.declare(rd.get("system"), ""  );
        String model  = Synt.declare(rd.get("model" ), ""  );
        String query  = Synt.declare(rd.get("query" ), ""  );
        int    quote  = Synt.declare(rd.get("quote" ), 1   );
        int    stream = Synt.declare(rd.get("stream"), 0   );
        float  minUp  = Synt.declare(rd.get("min_up"), 0.5f);
        int    maxRn  = Synt.declare(rd.get("max_rn"), 20  );
        int    maxSn  = Synt.declare(rd.get("max_sn"), 20  );
        int    maxTk  = Synt.declare(rd.get("max_tk"), 0   );
        int    topK   = Synt.declare(rd.get("top_k" ), 0   );
        double topP   = Synt.declare(rd.get("top_p" ), 0d  );
        double tmpr   = Synt.declare(rd.get("temperature"  ), 0d );
        Set    tools  = Synt.asSet  (rd.get("tools" )  );
        List   mesgs  = Synt.asList (rd.get("messages"));

        // 会话ID
        if (sid == null || sid.isEmpty()) {
            sid =  Core.newIdentity( );
            rd.put("session_id" , sid);
        }

        if (stream < 2) {
            stream(helper, rd, stream);
            return;
        }

        helper.reply(Synt.mapOf(
            "session_id" , sid
        ));

        // 缓存半分钟, 等下个接口取
        Roster.put("magpie.stream."+sid, Synt.mapOf(
            "session_id" , sid,
            "prompt", prompt,
            "system", system,
            "model" , model ,
            "query" , query ,
            "quote" , quote ,
            "min_up", minUp ,
            "max_rn", maxRn ,
            "max_sn", maxSn ,
            "max_tk", maxTk ,
            "top_p" , topP  ,
            "top_k" , topK  ,
            "temperature", tmpr,
            "messages" , mesgs ,
            "tools"    , tools
        ), 30);
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
        String sid = Synt.asString(rd.get("session_id"));

        String prompt = Synt.declare(rd.get("prompt"), ""  );
        String system = Synt.declare(rd.get("system"), ""  );
        String remind = Synt.declare(rd.get("remind"), ""  );
        String model  = Synt.declare(rd.get("model" ), ""  );
        String query  = Synt.declare(rd.get("query" ), ""  );
        int    quote  = Synt.declare(rd.get("quote" ), 1   );
        float  minUp  = Synt.declare(rd.get("min_up"), 0.5f);
        int    maxRn  = Synt.declare(rd.get("max_rn"), 20  );
        int    maxCn  = Synt.declare(rd.get("max_cn"), 5   );
        int    maxTr  = Synt.declare(rd.get("max_tr"), 1   );
        int    maxTk  = Synt.declare(rd.get("max_tk"), 0   );
        int    topK   = Synt.declare(rd.get("top_k" ), 0   );
        double topP   = Synt.declare(rd.get("top_p" ), 0d  );
        double tmpr   = Synt.declare(rd.get("temperature"  ), 0d );
        Set<String> tools = Synt.asSet ( rd.get( "tools" ) );

        List<Map>     tols = new ArrayList();
        List<Map>     refs = new ArrayList();
        List<Map>     segs = new ArrayList();
        StringBuilder scts = new StringBuilder();

        List<Map> messages = Synt.asList(rd.get("messages"));
        if (messages != null && ! messages.isEmpty()) {
            messages = new ArrayList(messages);
        } else {
            messages = new ArrayList(2);
        }

        // 限定上下文长度
        if (maxCn > 0 && maxCn * 2 < messages.size()) {
            messages = new ArrayList(messages.subList(messages.size() - (maxCn * 2), messages.size()));
        }

        CoreLocale cl = CoreLocale.getInstance("magpie");

        // 查询并引用资料, 使用 refs 工具则跳过
        if (query != null && ! query.isEmpty ( )
        && (tools == null || ! tools.contains("refs")) ) {
            Map        find;
            Object     vect;
            Data.Loop  loop;
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
                find.put(Cnst.OB_KEY, Synt.setOf("-"));
                find.put(Cnst.RB_KEY, Synt.setOf("rf", "id", "sn"));
              remind = remind(messages, prompt, remind, cl);
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

                        segs.add(pa);

                        if (refs.size() >= maxRn) {
                            break;
                        }
                    }
                    scts.setLength(scts.length() - 10);
                }
                break;
            case 1:
                // 查询片段, 引用片段
                find.put(Cnst.OB_KEY, Synt.setOf("-"));
                find.put(Cnst.RB_KEY, Synt.setOf("rf", "id", "sn", "text"));
              remind = remind(messages, prompt, remind, cl);
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
        }

        if (system == null || system.isBlank()) {
            system = cl.getProperty("magpie.assistant.system");
        }
        system = Syno.inject( system, Synt.mapOf(
            "sections", ! scts.isEmpty() ? scts.toString() : null
        ));

        messages.add(0, Synt.mapOf(
            "role", "system",
            "content", system
        ));
        messages.add(Synt.mapOf(
            "role", "user",
            "content", prompt
        ));

        // 参数放入环境, 以便工具读取
        Map env = new HashMap(1);
        env.put("REQUEST", rd);
        env.put("REFS" , refs);
        env.put("SEGS" , segs);
        env.put("TOOLS", tols);

        StringBuilder sb = new StringBuilder();

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
                        "session_id", sid,
                        "references", refs
                    ), true)
                  + "\n\n" );
                out.flush( );
            } catch (IOException e) {
                throw new CruxException(e);
            }

            try {
                Future ft = AiUtil.chat(model, messages, tools, tmpr, topP, topK, maxTk, maxTr, env, (token)-> {
                    try {
                        if (!token.isEmpty()) {
                            String thunk;
                            if (token.startsWith("TOOL{") && token.endsWith("}")) {
                                thunk = token.substring( 4 );
                                thunk = "data:{\"tool\":"  + thunk +  "}\n\n";
                            } else {
                                thunk = Dist.doEscape(token);
                                thunk = "data:{\"text\":\""+ thunk +"\"}\n\n";
                                sb.append(token);
                            }
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
                    String error = ex.getLocalizedMessage();
                    if (error == null) {
                        error = ex.getMessage();
                    if (error == null) {
                        error = ex.toString();
                    }}
                    try {
                        sb.append(error);
                        out.write(error);
                        out.flush(  );
                    } catch ( IOException e ) {
                        throw new CruxExemption(e);
                    }
                }
            } finally {
                Core.getInterior().remove("magpie.stream."+ sid);
                if (! sb.isEmpty()) {
                    String result = sb.toString();

                    // 完整内容
                    try {
                        String thunk ="data:"
                          + Dist.toString(Synt.mapOf(
                                "content", result,
                                "session_id", sid,
                                "references", refs
                            ), true)
                          + "\n\n";
                        out.write(thunk);
                        out.flush( );
                        out.close( );
                    } catch ( IOException e ) {
                        throw new CruxExemption(e);
                    }
                }
            }
        } else {
            try {
                Future ft = AiUtil.chat(model, messages, tools, tmpr, topP, topK, maxTk, maxTr, env, (token)-> {
                    if (!token.isEmpty()) {
                        sb.append(token);
                    }
                });
                // 登记任务并等待
                Core.getInterior().put("magpie.stream."+sid, ft);
                try {
                    ft.get();
                } catch (Exception ex) {
                    String error = ex.getLocalizedMessage();
                    if (error == null) {
                        error = ex.getMessage();
                    if (error == null) {
                        error = ex.toString();
                    }}
                    sb.append( error );
                }
            } finally {
                Core.getInterior().remove("magpie.stream."+ sid);
                if (! sb.isEmpty()) {
                    String result = sb.toString();

                    // 输出结果
                    helper.reply(Synt.mapOf(
                        "content", result,
                        "session_id", sid,
                        "references", refs
                    ));
                }
            }
        }
    }

    /**
     * 根据上下文补全问题
     * @param messages
     * @param prompt
     * @param remind
     * @param cl
     * @return
     */
    private String remind(List<Map> messages, String prompt, String remind, CoreLocale cl) {
        if (messages.isEmpty()) {
            return prompt;
        }

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

        return remind;
    }

}
