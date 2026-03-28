package io.github.ihongs.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.github.ihongs.CruxException;
import io.github.ihongs.serv.matrix.QueryAgent;
import io.github.ihongs.util.Dist;
import io.github.ihongs.util.Synt;
import java.util.Map;
import java.util.Set;

/**
 * 查询代理工具
 * @author Hongs
 */
public class QueryAgentTool implements Env {

    private Map ENV;

    @Override
    public void env(Map env) {
        ENV = env;
    }

    @Tool("""
    查询映射:
    针对指定字段的若干显示文本，找到对应的查询值和实际显示文本。
    返回列表:
    ```json
    [
        ["查询值", "实际显示文本", "传入显示文本"]
    ]
    ```
    """)
    public String find_mapping(
        @P("field code")
        String field,
        @P("field value labels, json array, like [\"A\",\"B\"]")
        String labels
    ) {
        try {
            Set labelz = Synt.toSet(labels);
            QueryAgent agent = (QueryAgent) ENV.get("QUERY_AGENT");
            return Dist.toString(agent.findMapping(field, labelz), true);
        } catch (CruxException ex) {
            throw ex.toExemption();
        }
    }


}
