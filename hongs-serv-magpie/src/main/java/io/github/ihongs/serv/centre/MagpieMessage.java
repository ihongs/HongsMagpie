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
import io.github.ihongs.dh.Roster;
import io.github.ihongs.serv.magpie.AIUtil;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Dist;
import io.github.ihongs.util.Syno;
import io.github.ihongs.util.Synt;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 消息接口
 * @author Hongs
 */
@Action("centra/data/magpie/assistant-message")
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
        Map  ad  = bot .getOne(Synt.mapOf(
            Cnst.RB_KEY, Synt.setOf(Cnst.ID_KEY, "state", "token"),
            "assistant_id", aid
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

        Data mod = Data.getInstance("centra/data/magpie", "assistant-message");
        Map  sd  = mod.search(rd);
        helper.reply(sd);
    }

    @Action("create")
    @Preset(conf="", form="")
    @Verify(conf="", form="", type=0)
    @CustomReplies
    public void create(ActionHelper helper) throws CruxException {
        Map rd = helper.getRequestData();

        String tok = Synt.asString(rd.get("token"));
        String aid = Synt.asString(rd.get("assistant_id"));
        String sid = Synt.asString(rd.get(  "session_id"));
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
            nid = ses.getId();
            nip = Core.CLIENT_ADDR.get();
        }

        // 会话ID
        if (sid == null || sid.isEmpty()) {
            sid = Core.newIdentity ();
        }

        if (stream < 2) {
            stream(helper, rd, stream);
            return;
        }

        // 缓存半分钟, 等下个接口取
        String id = Core.newIdentity();
        Roster.put("magpie.stream."+id, Synt.mapOf(
            "prompt", prompt,
            "assistant_id", aid,
              "session_id", sid,
                 "user_id", uid,
                 "anno_id", nid,
                 "anno_ip", nip
        ), 30);
        helper.reply(Synt.mapOf(
              "session_id", sid,
               "stream_id",  id
        ));
    }

    @Action("stream")
    @CustomReplies
    public void stream(ActionHelper helper) throws CruxException {
        Map rd = helper.getRequestData();
        String id;

        id = Synt.declare(rd.get( "stream_id" ), "");
        if (id == null || id.isEmpty()) {
            throw new CruxException(400, "stream_id required");
        }

        rd = (Map) Roster.get("magpie.stream." + id);
        if ( rd == null ||  rd.isEmpty()) {
            throw new CruxException(400, "stream_id is invalid");
        }
        Roster.del("magpie.stream."+ id);

        stream(helper, rd, 1);
    }

    private void stream(ActionHelper helper, Map rd, int stream) throws CruxException {
        String aid = Synt.asString(rd.get("assistant_id"));
        String sid = Synt.asString(rd.get(  "session_id"));
        String uid = Synt.asString(rd.get(     "user_id"));
        String nid = Synt.asString(rd.get(     "anno_id"));
        String nip = Synt.asString(rd.get(     "anno_ip"));
        String prompt = Synt.declare(rd.get("prompt"), "");
        String remind = prompt;

        Data bot = Data.getInstance("centra/data/magpie", "assistant");
        Map  ad  = bot .getOne(Synt.mapOf(
            Cnst.ID_KEY, aid
        ));
        if (ad == null || ad.isEmpty( )) {
            throw new CruxException(400, "Assistant is not found");
        }

        String system = Synt.declare(ad.get("system"), ""  );
        String model  = Synt.declare(ad.get("model" ), ""  );
        String query  = Synt.declare(ad.get("query" ), ""  );
        float  minUp  = Synt.declare(ad.get("min_up"), 0.5f);
        int    maxRn  = Synt.declare(ad.get("max_rn"), 10  );
        int    maxSn  = Synt.declare(ad.get("max_sn"), 20  );
        int    maxTk  = Synt.declare(ad.get("max_tk"), 0   );

        CoreLocale cl = CoreLocale.getInstance ( "magpie"  );

        // 获取历史消息
        Data mod = Data.getInstance("centra/data/magpie", "assistant-message");
        List<Map> rows = mod.search(Synt.mapOf(
            "assistant_id", aid,
              "session_id", sid,
            Cnst.OB_KEY, Synt.setOf("ctime!"),
            Cnst.RB_KEY, Synt.setOf("prompt", "result")
        ), 0, maxSn).toList();
        List<Map> messages = new ArrayList(2 + 2 * rows.size()); // 系统人设一条, 当前消息一条, 对话每组两条

        // 反向放入列表
        for(int  i  = rows.size( ) -1 ; i > -1 ; i -- ) {
            Map row = rows.get (i);
            Map ro0 = new HashMap( 2 );
            ro0.put("role"   , "user");
            ro0.put("content", row.get("prompt"));
            messages.add(ro0);
            Map ro1 = new HashMap( 2 );
            ro1.put("role"   , "assistant");
            ro1.put("content", row.get("result"));
            messages.add(ro1);
        }

        // 提取上下文
        if (! messages.isEmpty()) {
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
        qry.put(Cnst.RB_KEY, Synt.setOf("rf", "id", "sn", "text"));
        Data ref = Data.getInstance("centra/data/magpie", "reference");
        Data seg = Data.getInstance("centra/data/magpie", "reference-segment");
        Data.Loop loop = seg.search(qry, 0, maxRn);

        // 引用资料
        List<Map> refs = new ArrayList ();
        List rids = new ArrayList();
        List eids = new ArrayList();
        if (loop.count() > 0) {
            StringBuilder ps = new StringBuilder();
            Set rb = Synt.toSet(ref.getParams().get("showable"));
            for (Map pa : loop) {
                refs.add(ref.getOne(Synt.mapOf(
                    Cnst.ID_KEY, Synt.mapOf(Cnst.IN_REL, pa.get("rf")),
                    Cnst.RB_KEY, rb
                )));
                rids.add (pa.get( "rf" ));
                eids.add (pa.get( "id" ));
                ps.append(pa.get("text"))
                  .append("\n========\n");
            }
            ps .setLength(ps.length( )-8);

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
        }

        messages.add(Synt.mapOf(
            "role", "user",
            "content", prompt
        ));

        final Writer out;
        HttpServletResponse rsp;
        try {
            rsp = helper.getResponse();
            out = rsp.getWriter();
        } catch ( IOException e ) {
            throw new CruxException(e);
        }

        if (stream != 0) {
            rsp.setHeader("Cache-Control", "no-store");
            rsp.setHeader("Connection" , "keep-alive");
            rsp.setContentType ( "text/event-stream" );
            rsp.setCharacterEncoding("UTF-8");

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
                AIUtil.chat(model, messages, (token)-> {
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
            } finally {
                // 记录消息
                if (! sb.isEmpty()) {
                    mod.create(Synt.mapOf(
                             "user_id", uid ,
                             "anon_id", nid ,
                             "anon_ip", nip ,
                          "session_id", sid ,
                        "assistant_id", aid ,
                          "segment_id", eids,
                        "reference_id", rids,
                        "prompt", prompt,
                        "remind", remind,
                        "result", sb.toString(),
                        "ctime" , System.currentTimeMillis() / 1000
                    ));
                }
            }
        } else {
            StringBuilder sb = new StringBuilder();
            try {
                AIUtil.chat(model, messages, (token)-> {
                    try {
                        if (!token.isEmpty()) {
                            sb.append(token);
                        }
                        // 试探连接
                        out.write("");
                        out.flush(  );
                    } catch ( IOException e ) {
                        throw new CruxExemption(e);
                    }
                });

                // 输出结果
                helper.reply(Synt.mapOf(
                    "text", sb.toString(),
                    "references", refs,
                    "session_id", sid
                ));
            } finally {
                // 记录消息
                if (! sb.isEmpty()) {
                    mod.create(Synt.mapOf(
                             "user_id", uid ,
                             "anon_id", nid ,
                             "anon_ip", nip ,
                          "session_id", sid ,
                        "assistant_id", aid ,
                          "segment_id", eids,
                        "reference_id", rids,
                        "prompt", prompt,
                        "remind", remind,
                        "result", sb.toString(),
                        "ctime" , System.currentTimeMillis() / 1000
                    ));
                }
            }
        }
    }

}
