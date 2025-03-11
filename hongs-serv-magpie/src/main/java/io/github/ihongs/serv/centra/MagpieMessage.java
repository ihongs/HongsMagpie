package io.github.ihongs.serv.centra;

import io.github.ihongs.Cnst;
import io.github.ihongs.CruxException;
import io.github.ihongs.CruxExemption;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.action.anno.Action;
import io.github.ihongs.action.anno.CustomReplies;
import io.github.ihongs.action.anno.Preset;
import io.github.ihongs.action.anno.Select;
import io.github.ihongs.action.anno.Verify;
import io.github.ihongs.serv.magpie.AIUtil;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Synt;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;

/**
 * 消息接口
 * @author Hongs
 */
@Action("centra/data/magpie/assistant-message")
public class MagpieMessage {

    @Action("search")
    @Preset(conf="", form="")
    @Select(conf="", form="")
    public void search(ActionHelper helper) throws CruxException {
        Data mod = Data.getInstance("centra/data/magpie", "assistant-message");
        Map  rd  = helper.getRequestData();
        Map  sd  = mod.search(rd);
        helper.reply(sd);
    }

    @Action("create")
    @Preset(conf="", form="")
    @Verify(conf="", form="", type=0)
    @CustomReplies
    public void create(ActionHelper helper) throws CruxException {
        try {
            HttpServletResponse rsp = helper.getResponse();
            Writer out = rsp.getWriter();
            rsp.setContentType("text/event-stream");
            rsp.setCharacterEncoding("UTF-8");
            rsp.setHeader("Connection", "keep-alive");
            rsp.setHeader("Cache-Control","no-cache");
            out.write("");
            out.flush(  );

            Map  rd  = helper.getRequestData( );
            Data bot = Data.getInstance("centra/data/magpie", "assistant");
            Data mod = Data.getInstance("centra/data/magpie", "assistant-message");

            // 获取助理参数
            String aid = Synt.asString(rd.get("assistant_id"));
            Map  one = bot.getOne(Synt.mapOf(
                Cnst.RB_KEY, Synt.setOf(Cnst.ID_KEY, "model", "query"),
                Cnst.ID_KEY, Synt.mapOf(Cnst.EQ_REL, aid),
                "state", Synt.mapOf(Cnst.GT_REL, 0)
            ));
            if (one == null || one.isEmpty()) {
                throw new CruxException( 400, "Assistant not exists or disabled" );
            }

            String model  = (String) one.get("model");
            String query  = (String) one.get("query");
            String prompt = Synt.asString(rd.get("prompt"));

            List<Map> messages = Synt.listOf(
                Synt.mapOf(
                    "role", "user",
                    "content", prompt
                )
            );

            StringBuilder sb = new StringBuilder();
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
                    rd.put("result", sb.toString());
                    mod.add(rd);
                }
            }
        }
        catch (IOException ex) {
            throw new CruxException(ex);
        }
    }

    @Action("censor")
    @Preset(conf="", form="")
    @CustomReplies
    public void censor(ActionHelper helper) throws CruxException {
        try {
            HttpServletResponse rsp = helper.getResponse();
            Writer out = rsp.getWriter();
            rsp.setContentType("text/event-stream");
            rsp.setCharacterEncoding("UTF-8");
            rsp.setHeader("Connection", "keep-alive");
            rsp.setHeader("Cache-Control","no-cache");
            out.write("");
            out.flush(  );

            Map rd = helper.getRequestData( );

            List<Map> messages = Synt.asList(rd.get("messages"));
            String model = Synt.declare(rd.get("model" ), ""   );
            String query = Synt.declare(rd.get("query" ), ""   );
            float  minUp = Synt.declare(rd.get("min_up"), 0.1f );
            int    maxRn = Synt.declare(rd.get("max_rn"), 100  );
            int    maxTk = Synt.declare(rd.get("max_tk"), 0    );
            
            Map qry;
            if (query.startsWith("?")) {
                qry = ActionHelper.parseQuery(query);
            } else {
                qry = Synt.toMap(query);
            }
            qry.put("state", Synt.mapOf(Cnst.GT_REL, 0));
            qry.put(Cnst.RB_KEY, Synt.setOf("id", "rf", "sn", "part"));
            
            // 提取上下文
            String remind;
            remind = AIUtil.renderByTemp("remind", Synt.mapOf(
                "messages", messages
            ));
            remind = AIUtil.chat("remind", Synt.listOf(
                Synt.mapOf(
                    "role", "user",
                    "content", remind
                )
            ));
            
            // 获取向量
            Object vect = AIUtil.embedding(Synt.listOf(remind), AIUtil.ETYPE.QRY).get(0);
            
            // 查询资料
            Data segMod = Data.getInstance("centra/data/magpie", "reference-segment");
            Data.Loop l = segMod.search(qry, 0, maxRn);
            StringBuilder rf = new StringBuilder();
            for (Map  m : l) {
                rf.append(m.get("part")).append("\n\n--------\n\n");
            }

            StringBuilder sb = new StringBuilder();
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
