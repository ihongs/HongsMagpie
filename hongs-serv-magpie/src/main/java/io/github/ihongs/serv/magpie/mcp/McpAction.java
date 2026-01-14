package io.github.ihongs.serv.magpie.mcp;

import io.github.ihongs.Core;
import io.github.ihongs.CoreLogger;
import io.github.ihongs.CoreRoster.Mathod;
import io.github.ihongs.action.ActionDriver;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.serv.magpie.AiUtil;
import io.github.ihongs.util.Dist;
import io.github.ihongs.util.Synt;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * MCP 服务接口
 * @author HuangHong
 */
public class McpAction extends ActionDriver {

    protected HttpServlet actor;

    @Override
    public void init(ServletConfig conf) throws ServletException {
        super.init(conf);

        String sseUrl = conf.getInitParameter("sse-url");
        String msgUrl = conf.getInitParameter("msg-url");
        Set<String> tools = Synt.toSet(conf.getInitParameter("tools"));

        if (sseUrl == null || sseUrl.isEmpty()
        ||  msgUrl == null || msgUrl.isEmpty()
        ||  tools  == null || tools .isEmpty()) {
            throw new ServletException("Init params sse-url, msg-url and tools required");
        }

        McpServerTransportProvider provider = HttpServletSseServerTransportProvider
            .builder()
            .baseUrl(Core.SERV_PATH)
            .messageEndpoint(msgUrl)
            .sseEndpoint(sseUrl)
            .build();

        McpSyncServer server = McpServer.sync(provider)
            .serverInfo("mcp-server", "1.0.0")
            .capabilities(ServerCapabilities
                .builder()
                .logging()
                .tools(true)
                .build()
            )
            .build();

        Map<String, Mathod> toolz = AiUtil.getTools();
        for(String  tool  : tools) {
            Mathod  mat = toolz.get(tool);
            Method  met = mat.getMethod();
            Class   mcl = mat.getMclass();
            Parameter[] mps = met.getParameters();

            // 工具描述
            dev.langchain4j.agent.tool.Tool  ta = met.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
            String name = Synt.defxult(ta.name(), met.getName());
            String desc = String.join ("\n", ta.value());
            String sche = Dist.toString(toSchema( mps ));

            server.addTool(
                new SyncToolSpecification(
                    new Tool(name, desc, sche), (exch, args) -> {
                        try {
                            Object obj = Core.getInstance ( mcl );
                            String mid = UUID.randomUUID().toString();
                            String rst = new McpRunner(obj, met ).execute(args, mid);
                            return new CallToolResult (rst,false);
                        } catch (Exception ex) {
                            Throwable ax = ex.getCause();
                            if (ax == null) {
                                ax  = ex;
                            }
                            CoreLogger.error(ax);
                            String msg = ax.getMessage();
                            return new CallToolResult (msg, true);
                        }
                    }
                )
            );
        }

        actor = (HttpServlet) provider;
        actor.init(conf);
    }

    public void destory() {
        super.destroy();
        actor.destroy();
    }

    @Override
    protected void doAction(Core core, ActionHelper helper)
    throws ServletException, IOException {
        HttpServletRequest  request  = helper.getRequest ();
        HttpServletResponse response = helper.getResponse();
        actor.service(request, response);
    }

    /**
     * Langchain 参数结构转 MCP JsonSchema
     * Langchain ToolSpecifications 转 JSON 也无法用于 MCP, 只好重写之
     * @param params
     * @return
     */
    protected Map  toSchema(Parameter[] params) {
        Map  ps = new LinkedHashMap(params.length);
        List rs = new ArrayList (params.length);
        int  i  = 0;

        for (Parameter param : params) {
             dev.langchain4j.agent.tool.P pa = param.getAnnotation( dev.langchain4j.agent.tool.P.class);
            io.github.ihongs.agent.tool.E ea = param.getAnnotation(io.github.ihongs.agent.tool.E.class);
            Map pm = new HashMap(0x3);
            String pn = "arg" + (i++);
            pm.put("description", "");
            ps.put(pn, pm);
            if (ea != null) {
                pm.put("enum", ea.value());
            }
            if (pa != null) {
                pm.put("description", pa.value());
                Class pt = param.getType();
                if (Boolean.class.isAssignableFrom(pt)) {
                    pm.put("type", "boolean");
                } else
                if (String.class.isAssignableFrom(pt)) {
                    pm.put("type", "string");
                } else
                if (Number.class.isAssignableFrom(pt)
                ||  double.class == pt
                ||  int   .class == pt
                ||  long  .class == pt
                ||  float .class == pt
                ||  short .class == pt
                ||  byte  .class == pt) {
                    pm.put("type", "number");
                } else
                if (Map.class.isAssignableFrom(pt)) {
                    pm.put("type", "object");
                } else
                if (Set.class.isAssignableFrom(pt)) {
                    pm.put("type", "array");
                } else
                if (List.class.isAssignableFrom(pt)) {
                    pm.put("type", "array");
                }
                if (pa.required()) {
                    rs.add(pn);
                }
            }
        }
        return Synt.mapOf(
            "id", "urn:jsonschema:Operation",
            "type", "object",
            "properties", ps,
            "required"  , rs
        );
    }

}
