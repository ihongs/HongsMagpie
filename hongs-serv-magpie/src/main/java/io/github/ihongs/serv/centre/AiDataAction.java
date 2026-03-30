package io.github.ihongs.serv.centre;

import io.github.ihongs.CoreConfig;
import io.github.ihongs.CruxException;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.action.anno.Action;
import io.github.ihongs.action.anno.AutoAdapter;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.serv.magpie.QueryAgent;
import io.github.ihongs.util.Synt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询辅助代理
 * @author Hongs
 */
@AutoAdapter
@Action("centre/data")
public class AiDataAction extends DataAction {

    public AiDataAction() {
        super();
    }

    @Action("aifind")
    public void refind(ActionHelper helper) throws CruxException {
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
        int limit = CoreConfig.getInstance("magpie").getProperty("magpie.llm.query.agent.max.round", 10) * 2;
        int count = messages.size();
        if (limit > 0 && count > limit ) {
            messages = messages.subList(count - limit, count);
        }

        Data       da = (Data) getEntity(helper);
        QueryAgent qa = new QueryAgent ( da );
        String s = qa.chat(messages, content);
        helper.reply(Synt.mapOf(
            "ok"  , false , // 阻断后续处理
            "cb"  , "ECHO", // 自定输出类型
            "type", "text/plain",
            "text", s
        ));
    }

}
