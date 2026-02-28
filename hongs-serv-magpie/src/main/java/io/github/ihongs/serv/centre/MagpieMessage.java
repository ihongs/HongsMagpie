package io.github.ihongs.serv.centre;

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
import io.github.ihongs.action.anno.Select;
import io.github.ihongs.action.anno.Verify;
import io.github.ihongs.db.DB;
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
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 消息接口
 * @author Hongs
 */
@Action("centre/data/magpie/assistant-message")
public class MagpieMessage {

    @Action("search")
    @Preset(conf="", form="")
    @Select(conf="", form="")
    @CustomReplies
    public void search(ActionHelper helper) throws CruxException {
        Map rd = helper.getRequestData();

        String tok = Synt.asString(rd.get("token"));
        String aid = Synt.asString(rd.get("assistant_id"));
        if (tok == null || tok.isEmpty()
        ||  aid == null || aid.isEmpty()) {
            throw new CruxException(400 , "token and assistant_id required");
        }

        Data bot = Data.getInstance("centra/data/magpie", "assistant");
        Data mod = Data.getInstance("centra/data/magpie", "assistant-message");

        Map ad = bot.getOne(Synt.mapOf(
            Cnst.ID_KEY, aid ,
            Cnst.RB_KEY, Synt.setOf(Cnst.ID_KEY, "state", "token", "name", "icon", "note", "regard")
        ));
        if (Synt.declare(ad.get("state"), 0) <= 0) {
            throw new CruxException(404 , "@magpie:magpie.assistant.state.invalid");
        }
        if (! tok.equals(ad.get("token"))) {
            throw new CruxException(403 , "@magpie:magpie.assistant.token.invalid");
        }

        // 限定用户
        Object uid = helper.getSessibute(Cnst.UID_SES);
        if (uid == null) {
        Object nid = helper.getRequest().getSession(true).getId();
            rd.put("anno_id", nid);
        } else {
            rd.put("user_id", uid);
        }

        Map sd ;
        Set ab = Synt.toSet(rd.get(Cnst.AB_KEY));
        if (ab == null || ! ab.contains("no-list")) {
            sd = mod.search(rd);
        } else {
            sd = new HashMap ();
        }

        // 助理信息
        if (ab != null && ab.contains("assistant")) {
            sd.put("assistant", ad);
            ad.remove("state");
            ad.remove("token");
        }

        // 用户信息
        if (ab != null && ab.contains("user")) {
            if (uid != null) {
                ad = DB.getInstance("master").getTable("user").fetchCase()
                       .filter("id = ?", uid).select("name, head AS icon")
                       .getOne();
            } else {
                ad = new HashMap();
            }
            sd.put("user", ad);
        }

        helper.reply(sd);
    }

    @Action("aerate")
    @Preset(conf="", form="")
    @Verify(conf="", form="", type=0)
    @CustomReplies
    public void aerate(ActionHelper helper) throws CruxException {
        Map rd = helper.getRequestData();

        String tok = Synt.asString(rd.get("token"));
        String aid = Synt.asString(rd.get("assistant_id"));
        String sid = Synt.asString(rd.get(  "session_id"));
        List   msgs   = Synt.asList (rd.get("messages" ) );
        String prompt = Synt.declare(rd.get("prompt"), "");
        int    stream = Synt.declare(rd.get("stream"), 0 );

        if (tok == null || tok.isEmpty()
        ||  aid == null || aid.isEmpty()) {
            throw new CruxException(400 , "token and assistant_id required");
        }

        Data bot = Data.getInstance("centra/data/magpie", "assistant");
        Map  ad  = bot .getOne(Synt.mapOf(
            Cnst.RB_KEY, Synt.setOf(Cnst.ID_KEY, "state", "token"),
            Cnst.ID_KEY, aid
        ));
        if (Synt.declare(ad.get("state"), 0) <= 0) {
            throw new CruxException(404 , "@magpie:magpie.assistant.state.invalid" );
        }
        if (! tok.equals(ad.get("token"))) {
            throw new CruxException(403 , "@magpie:magpie.assistant.token.invalid" );
        }

        // 当前用户/匿名信息
        HttpSession  ses = helper.getRequest().getSession(true);
        Object uid = ses .getAttribute(Cnst.UID_SES);
        String nid = null;
        String nip = null;
        if (uid == null) {
            nid =  ses .getId();
            nip =  Core.CLIENT_ADDR.get();
        }

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
          "assistant_id", aid,
            "session_id", sid
        ));

        // 缓存半分钟, 等下个接口取
        Roster.put("magpie.stream."+sid, Synt.mapOf(
          "assistant_id", aid,
            "session_id", sid,
               "user_id", uid,
               "anno_id", nid,
               "anno_ip", nip,
            "prompt", prompt ,
            "messages", msgs
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

    private void stream(ActionHelper helper, Map xd, int stream) throws CruxException {
        String aid = Synt.asString(xd.get("assistant_id"));
        String sid = Synt.asString(xd.get(  "session_id"));
        String uid = Synt.asString(xd.get(     "user_id"));
        String nid = Synt.asString(xd.get(     "anno_id"));
        String nip = Synt.asString(xd.get(     "anno_ip"));

        Map rd = Data.getInstance("centra/data/magpie", "assistant")
            .getOne(Synt.mapOf(
                Cnst.ID_KEY, aid
            ));
        if (rd == null || rd.isEmpty( )) {
            throw new CruxException (404 , "Assistant is not found");
        }

        // 下同 io.github.ihongs.serv.centra.MagpieMessage

        String prompt = Synt.declare(xd.get("prompt"), ""  );
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

        // 获取历史消息
        List<Map> messages = Synt.asList(xd.get("messages"));
        if (messages != null && ! messages.isEmpty()) {
            messages = new ArrayList(messages);
        } else {
            List<Map> rows = Data.getInstance("centra/data/magpie", "assistant-message")
                .search(Synt.mapOf(
                    Cnst.RB_KEY, Synt.setOf("prompt", "result"),
                    Cnst.OB_KEY, "ctime!",
                    "assistant_id", aid,
                    "sessoin_id"  , sid,
                    "user_id"     , uid,
                    "anno_id"     , nid
                ), 0, maxCn).toList();
            ListIterator<Map>  litr  ;
            litr = rows.listIterator(rows.size());
            messages = new ArrayList(rows.size() * 2 + 2);
            while (litr.hasPrevious()) {
                Map row = litr.previous();
                messages.add(Synt.mapOf(
                    "role", "user",
                    "content", row.get("prompt")
                ));
                messages.add(Synt.mapOf(
                    "role", "assistant",
                    "content", row.get("result")
                ));
            }
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

        // 记录对话消息
        List rids = new ArrayList(refs.size());
        List sids = new ArrayList(segs.size());
        for (Map one : refs) {
            rids.add(one.get("id"));
        }
        for (Map one : refs) {
            sids.add(one.get("id"));
        }
        Data.getInstance("centra/data/magpie", "assistant-message")
            .add(Synt.mapOf(
                "assistant_id", aid ,
                "session_id"  , sid ,
                "reference_id", rids,
                "segment_id"  , sids,
                "tools"       , tols,
                "prompt" , prompt,
                "remind" , remind,
                "result" , sb.toString(),
                "user_id", uid,
                "anon_id", nid,
                "anon_ip", nip,
                "ctime"  , System.currentTimeMillis() / 1000
            ));

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
