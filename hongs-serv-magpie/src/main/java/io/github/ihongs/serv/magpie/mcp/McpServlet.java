package io.github.ihongs.serv.magpie.mcp;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CruxExemption;
import io.github.ihongs.action.ActionDriver;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.util.Dist;
import io.github.ihongs.util.Synt;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * MCP Servlet
 * @author HuangHong
 */
@WebServlet(
    name = "AiMcpServ",
    urlPatterns = "/centra/magpie/mcp/*",
    asyncSupported = true
)
public class McpServlet extends ActionDriver {

    private HttpServlet provider;

    @Override
    public void init(ServletConfig conf) throws ServletException {
        super.init(conf);

        provider = Core.getInterior().got(HttpServletSseServerTransportProvider.class.getName(), ()->{
            HttpServletSseServerTransportProvider sstp = HttpServletSseServerTransportProvider
                    .builder()
                    .baseUrl(Core.SERV_PATH)
                    .sseEndpoint("/centra/magpie/mcp/sse")
                    .messageEndpoint("/centra/magpie/mcp/message")
                    .build();

            // Create a server
            McpSyncServer server = McpServer.sync(sstp)
                .serverInfo   ( "mcp-server", "1.0.0" )
                .capabilities (ServerCapabilities.builder()
                    .resources(false, true)
                    .prompts(true)
                    .tools  (true)
                    .logging()
                    .build  ()
                )
                .build();

            // Load mcp config
            Map mcps;
            try {
                Reader      read = null;
                InputStream inps = null;
                try {
                    File file = new File(
                                Core.CONF_PATH + "/magpie-mcp.json");
                    if (!file.exists()) {
                         inps = this.getClass().getResourceAsStream(
                                Cnst.CONF_PACK + "/magpie-mcp.json");
                    } else {
                         inps = new FileInputStream(file);
                    }
                    read = new InputStreamReader(inps);
                    mcps = ( Map ) Dist.toObject(read);
                }
                finally {
                    if (inps != null) inps.close();
                    if (read != null) read.close();
                }
            }
            catch (IOException ex) {
                throw new CruxExemption(ex);
            }

            List<Map> tools = (List) mcps.get("tools");
            if (tools != null && ! tools.isEmpty()) {
                for(Map tool : tools) {
                    String name = (String) tool.get("name");
                    String desc = (String) tool.get("desc");
                    String clsn = (String) tool.get("class");
                    String sche = (String) Dist.toString(tool.get("scheme"));
                    Function <Map, String> func = (Function) Core.newInstance(clsn);
                    server.addTool(new SyncToolSpecification(new Tool(name, desc, sche), (exchange, arguments) -> {
                        // Check if client supports sampling
                        if (exchange.getClientCapabilities().sampling() == null) {
                            return new CallToolResult("Client does not support AI capabilities", false);
                        }

                        try {
                            Object rst = func.apply(arguments);
                            List   lst = Synt.asList(rst);
                            CallToolResult.Builder ctr = CallToolResult.builder();
                            for (Object one : lst) {
                                if (one instanceof Content) {
                                    ctr.addContent((Content) one);
                                } else {
                                    ctr.addTextContent(ctr.toString());
                                }
                            }
                            return ctr.build();
                        }
                        catch (Exception ex) {
                            return new CallToolResult(ex.getLocalizedMessage(), true);
                        }
                    }));
                }
            }

            return sstp;
        });
    }

    public void destory() {
        provider.destroy();
        super.destroy();
    }

    @Override
    protected void doAction(Core core, ActionHelper helper)
    throws ServletException, IOException {
        HttpServletRequest  request  = helper.getRequest ();
        HttpServletResponse response = helper.getResponse();
        provider.service(request, response);
    }

}
