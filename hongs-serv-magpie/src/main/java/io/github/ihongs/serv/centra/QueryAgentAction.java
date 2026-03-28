package io.github.ihongs.serv.centra;

import io.github.ihongs.CruxException;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.action.ActionRunner;
import io.github.ihongs.action.NaviMap;
import io.github.ihongs.action.anno.Action;
import io.github.ihongs.dh.IActing;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.serv.matrix.QueryAgent;
import io.github.ihongs.util.Synt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询扶住代理
 * @author Hongs
 */
@Action("centra/data-summon")
public class QueryAgentAction extends DataAction {

    public QueryAgentAction() {
        super();
    }

    @Action("summon")
    public void action(ActionHelper helper) throws CruxException {
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

        Data       da = (Data) getEntity(helper);
        QueryAgent qa = new QueryAgent ( da );
        String s = qa.chat(messages, content);
        helper.write( "application/json", s );
    }

}
