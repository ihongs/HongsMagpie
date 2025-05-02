package io.github.ihongs.serv.magpie.mcp;

import io.github.ihongs.Core;
import io.github.ihongs.CoreRoster.Mathod;
import io.github.ihongs.action.ActionDriver;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.serv.magpie.AiUtil;
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

    protected HttpServlet that;

    @Override
    public void init(ServletConfig conf) throws ServletException {
        super.init(conf);
        that .init(conf);

        String sseUrl = conf.getInitParameter("sse-url");
        String msgUrl = conf.getInitParameter("msg-url");

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

        Set<String> tools = Synt.toSet(conf.getInitParameter("tools"));
        Map<String, Mathod> toolz = AiUtil.getToolMethods();
        for(String  tool  : tools) {
            Mathod  mat = toolz.get(tool);
            Method  met = mat.getMethod();
            Class   mcl = mat.getMclass();

            dev.langchain4j.agent.tool.ToolSpecification spe = dev.langchain4j.agent.tool.ToolSpecifications.toolSpecificationFrom(met);
            String name = spe.name();
            String desc = spe.description();
            String sche = dev.langchain4j.internal.Json.toJson(spe.parameters());

            server.addTool(
                new SyncToolSpecification(
                    new Tool(name, desc, sche) , (exch, args) -> {
                        Object obj = Core.getInstance ( mcl );
                        String mid = UUID.randomUUID().toString( );
                        String rst = new McpRunner(obj, met ).execute(args, mid);
                        return new CallToolResult (rst,false);
                    }
                )
            );
        }

        that = (HttpServlet) provider;
    }

    public void destory() {
        super.destroy();
        that .destroy();
    }

    @Override
    protected void doAction(Core core, ActionHelper helper)
    throws ServletException, IOException {
        HttpServletRequest  request  = helper.getRequest ();
        HttpServletResponse response = helper.getResponse();
        that .service(request, response);
    }

}
