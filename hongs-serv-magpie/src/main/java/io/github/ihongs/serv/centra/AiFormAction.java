package io.github.ihongs.serv.centra;

import io.github.ihongs.CoreConfig;
import io.github.ihongs.CruxException;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.action.anno.Action;
import io.github.ihongs.serv.magpie.FormAgent;
import io.github.ihongs.util.Synt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 表单构建代理
 * @author Hongs
 */
@Action("centra/data/aiform")
public class AiFormAction {

    @Action("create")
    public void create(ActionHelper helper) throws CruxException {
        Map rd = helper.getRequestData();
        String    content  = Synt.asString(rd.get("content" ));
        List<Map> messages = Synt.asList  (rd.get("messages"));

        if (content  == null || content.isBlank()) {
            helper.fault("message required");
            return;
        }
        if (messages == null) {
            messages  = new ArrayList(0);
        }

        // 限定最多对话轮数
        int limit = CoreConfig.getInstance("magpie").getProperty("magpie.llm.form.agent.max.round", 10) * 2;
        int count = messages.size();
        if (limit > 0 && count > limit ) {
            messages = messages.subList(count - limit, count);
        }

        FormAgent  fa = new FormAgent();
        String s = fa.chat(messages, content);
        helper.reply(Synt.mapOf(
            "ok"  , false , // 阻断后续处理
            "cb"  , "ECHO", // 自定输出类型
            "type", "text/plain",
            "text", s
        ));
    }

}
