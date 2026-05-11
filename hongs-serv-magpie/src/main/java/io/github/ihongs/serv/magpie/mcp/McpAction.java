package io.github.ihongs.serv.magpie.mcp;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CoreLogger;
import io.github.ihongs.CoreRoster.Mathod;
import io.github.ihongs.CruxCause;
import io.github.ihongs.CruxException;
import io.github.ihongs.action.ActionDriver;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.serv.magpie.AiUtil;
import io.github.ihongs.util.Synt;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
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
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MCP 服务接口
 * @author HuangHong
 */
public class McpAction extends ActionDriver {

    protected HttpServlet actor;
    protected String baseUrl;
    protected String execUrl;

    @Override
    public void init(ServletConfig conf) throws ServletException {
        super.init (conf);

        Set<String> tools;
        tools   = Synt.toSet(conf.getInitParameter("tools"));
        baseUrl = conf.getInitParameter("base-url");
        if (tools   == null || tools  .isEmpty()
        ||  baseUrl == null || baseUrl.isEmpty()) {
            throw new ServletException("Init params tools and base-url required");
        }
        execUrl = baseUrl + "/execute";

        McpServerTransportProvider provider = HttpServletSseServerTransportProvider
            .builder()
            .baseUrl(baseUrl)
            .sseEndpoint("/sse")
            .messageEndpoint("/message")
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
            Map    sche = toSchema( mps );

            server.addTool(SyncToolSpecification.builder()
                .tool(Tool.builder()
                    .name(name)
                    .description(desc)
                    .inputSchema(sche)
                    .build()
                )
                .callHandler((exch, reqs) -> {
                    try {
                        Object obj = Core.getInstance ( mcl );
                        String mid = UUID.randomUUID().toString();
                        Object rst = new McpRunner(obj, met ).execute(reqs.arguments(), mid);
                        return toResult(rst);
                    } catch (Exception ex) {
                        Throwable ax = ex.getCause();
                        if (ax == null) {
                            ax  = ex;
                        }
                        CoreLogger.error(ax);
                        return toResult (ax);
                    }
                })
                .build()
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

        // JSON RPC
        String   url = getRecentPath ( request );
        if ( execUrl.equals(url)) {
            Map  req = helper.getRequestData ( );
            Object x = req.get ( "data" );

            if (x != null && x instanceof List ) {
                List a = Synt. asList (x);
                List r = new ArrayList(a.size());
                for(Object o : a) {
                    Map m = Synt.asMap(o);
                    Object s = execute(m);
                    r.add( s );
                }
                helper.reply(Synt.mapOf(
                    Cnst.CB_KEY, "~",
                    "data", r
                ));
            } else {
                Object s = execute(req);
                helper.reply(Synt.mapOf(
                    Cnst.CB_KEY, "~",
                    "data", s
                ));
            }
            return;
        }

        actor.service(request, response);
    }

    protected Object execute(Map req) {
            String  id = "";
        try {
                    id = Synt.declare (req.get("id"), id);
            String mtd = Synt.asString(req.get("method"));
            Object pms = req.get("params");

            Map<String, Mathod> toolz = AiUtil.getTools();
            Mathod mat = toolz.get ( mtd );
            if (mat == null) {
                throw new CruxException("@magpie:jsonrpc.error", -32601, "method not found: " + mtd);
            }

            Method met = mat.getMethod ( );
            Class  mcl = mat.getMclass ( );
            Object obj = Core.getInstance(mcl);
            String mid = UUID.randomUUID().toString();
            Object rst ;

            if (pms instanceof List) {
                rst = new McpRunner(obj , met).execute((List) pms, mid);
            } else
            if (pms instanceof Map ) {
                rst = new McpRunner(obj , met).execute((Map ) pms, mid);
            } else
            {
                throw new CruxException("@magpie:jsonrpc.error", -32603, "params must be list or dict");
            }
            return Synt.mapOf(
                "jsonrpc", "2.0",
                "result" ,  rst ,
                "id", id
            );
        }
        catch  (Exception ex) {
            Object  code ;
            Object  msg  ;
            if (ex instanceof CruxCause) {
                CruxCause cx = (CruxCause) ex;
                if ("@magpie:jsonrpc.error".equals(cx.getError())) {
                    code = cx.getCases()[0];
                    msg  = cx.getCases()[1];
                } else
                if (cx.getErrno() == 400) {
                    code = -32600;
                    msg  = ex.getMessage( );
                } else
                if (cx.getErrno() == 404) {
                    code = -32601;
                    msg  = ex.getMessage( );
                } else
                {
                    code = -32603;
                    msg  = ex.getMessage( );
                }
            } else {
                    code = -32603;
                    msg  = ex.getMessage( );
            }
            return Synt.mapOf(
                "jsonrpc", "2.0",
                "error"  , Synt.mapOf(
                    "code"   , code,
                    "message", msg
                ),
                "id", id
            );
        }
    }

    /**
     * Langchain 参数结构转 MCP JsonSchema
     * Langchain ToolSpecifications 转 JSON 也无法用于 MCP, 只好重写之
     * @param params
     * @return
     */
    protected Map toSchema(Parameter[] params) {
        Map  ps = new LinkedHashMap(params.length);
        List rs = new  ArrayList   (params.length);
        int  i  = 0;

        for (Parameter param : params) {
            dev.langchain4j.agent.tool.P pa = param.getAnnotation(dev.langchain4j.agent.tool.P.class);
            Map pm = new HashMap(0x3);
            String pn = "arg" + (i++);
            pm.put("description", "");
            ps.put(pn , pm);
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
                if (Map .class.isAssignableFrom(pt)) {
                    pm.put("type", "object");
                } else
                if (Set .class.isAssignableFrom(pt)) {
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
            //"id", "urn:jsonschema:Operation",
            "type", "object",
            "properties", ps,
            "required"  , rs
        );
    }

    protected CallToolResult toResult(Object data) {
        List<String> list = Synt.asColl(data)
            .stream( )
            .map(x -> Synt.asString(x))
            .toList( );
        return CallToolResult
            .builder()
            .textContent(list)
            .build ( );
    }

    protected CallToolResult toResult(Throwable e) {
        return CallToolResult
            .builder()
            .isError(true)
            .addTextContent(e.getMessage())
            .build ( );
    }

}
