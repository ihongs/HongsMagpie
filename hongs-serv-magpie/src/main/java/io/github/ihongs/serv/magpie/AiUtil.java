package io.github.ihongs.serv.magpie;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import io.github.ihongs.Core;
import io.github.ihongs.CoreConfig;
import io.github.ihongs.CoreLogger;
import io.github.ihongs.CoreRoster;
import io.github.ihongs.CoreRoster.Mathod;
import io.github.ihongs.CruxCause;
import io.github.ihongs.CruxExemption;
import io.github.ihongs.util.Synt;
import io.github.ihongs.util.daemon.Chore.Defer;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 接口工具
 * 配置均在 magpie.properties
 * @author Hongs
 */
public final class AiUtil {

    private AiUtil () {}

    public static enum ETYPE {DOC, QRY};

    /**
     * 获取对话模型
     * @param name
     * @return
     */
    public static ChatLanguageModel getChatModel(String name) {
        CoreConfig cc = CoreConfig.getInstance("magpie");
        String api = cc.getProperty("magpie.llm."+name+".api", name);
        String mod = cc.getProperty("magpie.llm."+name+".mod");
        String url = cc.getProperty("magpie.llm."+api +".url");
        String key = cc.getProperty("magpie.llm."+api +".key");

        return OpenAiChatModel
            .builder  (   )
            .  apiKey (key)
            . baseUrl (url)
            .modelName(mod)
            .customHeaders(Synt.mapOf(
                "Accept-Charset", "UTF-8"
            ))
            .build    (   );
    }

    /**
     * 获取流式模型
     * @param name
     * @return
     */
    public static StreamingChatLanguageModel getStreamingModel(String name) {
        CoreConfig cc = CoreConfig.getInstance("magpie");
        String api = cc.getProperty("magpie.llm."+name+".api", name);
        String mod = cc.getProperty("magpie.llm."+name+".mod");
        String url = cc.getProperty("magpie.llm."+api +".url");
        String key = cc.getProperty("magpie.llm."+api +".key");

        return OpenAiStreamingChatModel
            .builder  (   )
            .  apiKey (key)
            . baseUrl (url)
            .modelName(mod)
            .customHeaders(Synt.mapOf(
                "Accept-Charset", "UTF-8"
            ))
            .build    (   );
    }

    /**
     * 获取向量模型
     * @param name
     * @return
     */
    public static EmbeddingModel getEmbeddingModel(String name) {
        CoreConfig cc = CoreConfig.getInstance("magpie");
        String api = cc.getProperty("magpie.llm."+name+".api", name);
        String mod = cc.getProperty("magpie.llm."+name+".mod");
        String url = cc.getProperty("magpie.llm."+api +".url");
        String key = cc.getProperty("magpie.llm."+api +".key");

        return OpenAiEmbeddingModel
            .builder  (   )
            .  apiKey (key)
            . baseUrl (url)
            .modelName(mod)
            .customHeaders(Synt.mapOf(
                "Accept-Charset", "UTF-8"
            ))
            .build    (   );
    }

    /**
     * 获取文档拆分器
     * 配置:
     * magpie.splitter.class=拆分器类名, 带静态方法 recursive(int, int)
     * magpie.splitter.max-segment-size=分块大小
     * magpie.splitter.max-overlay-size=重叠大小
     * @return
     */
    public static DocumentSplitter getDocumentSplitter() {
        return Core.getInstance()
            .got(DocumentSplitter.class.getName(), ()->{
                CoreConfig cc = CoreConfig.getInstance("magpie");
                String clsn = cc.getProperty("document.splitters", DocumentSplitters.class.getName());
                int maxSegmentSize = cc.getProperty("document.splitter.max-segment-size", 1000);
                int maxOverlaySize = cc.getProperty("document.splitter.max-overlay-size", 100 );

                try {
                    Class  clso = Class.forName(clsn);
                    Method mtho = clso.getMethod("recursive", int.class, int.class);
                    return (DocumentSplitter) mtho.invoke(null, maxSegmentSize, maxOverlaySize);
                } catch (ClassNotFoundException e ) {
                    throw new CruxExemption(e, 821);
                } catch ( NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException e) {
                    throw new CruxExemption(e, 823);
                } catch (InvocationTargetException e) {
                    Throwable ta = e.getCause( );
                    if (ta instanceof CruxCause) {
                      throw ((CruxCause) ta).toExemption();
                    }
                    if (ta instanceof StackOverflowError ) {
                      throw (StackOverflowError) ta;
                    }
                    throw new CruxExemption(e, 823);
                }
            });
    }

    /**
     * 获取工具方法集
     * 配置:
     * magpie.tools=package.ClassName;package.name.*;package.name.**
     * 说明:
     * 指定类名仅在类下查找, .* 结尾在此包下查找, .** 结尾包含下级包
     * @return
     */
    public static Map<String, Mathod> getTools() {
        return Core.getInterior().got("magpie.tools", () -> {
            try {
                String[] ps = CoreConfig
                        .getInstance( "magpie" )
                        .getProperty( "magpie.tools", "io.github.ihongs.serv.tool.**")
                        .split(";");
                Map<String, Mathod> ts = new HashMap (ps.length);
                for(String pn : ps) {
                    pn = pn.trim( );
                    Set<String> cs ;
                    if (pn.endsWith(".**")) {
                        cs = CoreRoster.getClassNames(pn.substring(0, pn.length() - 3), true );
                    } else
                    if (pn.endsWith(".*" )) {
                        cs = CoreRoster.getClassNames(pn.substring(0, pn.length() - 2), false);
                    } else
                    {
                        cs = Synt.setOf(pn);
                    }
                    for(String cn : cs) {
                        Class  co = Class.forName(cn);
                        Method[] ms = co.getDeclaredMethods();
                        for(Method mo : ms) {
                            Tool to = mo.getAnnotation(Tool.class);
                            if (to != null) {
                                String n = to.name( );
                                if (n == null || n.isEmpty()) {
                                    n = mo.getName( );
                                }
                                ts.put(n, new Mathod(co, mo));
                            }
                        }
                    }
                }
                CoreLogger.debug( "Load tools: {}" , ts );
                return ts;
            } catch (IOException | ClassNotFoundException ex) {
                throw new CruxExemption(ex);
            }
        });
    }

    public static List<ToolSpecification> toToolSpecifications(Set<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return new ArrayList();
        }
        Map<String, Mathod> ts = getTools();
        return tools
            .stream()
            .map(tool -> {
                Mathod ma = ts.get(tool);
                if (ma == null) {
                    throw new CruxExemption ( "Can not find tool `{}`" , tool );
                }
                return ToolSpecifications.toolSpecificationFrom(ma.getMethod());
            })
            .toList();
    }

    public static List<ChatMessage> toChatMessages(List<Map> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList();
        }
        return messages
            .stream()
            .map(
                message -> {
                    String role = Synt.asString(message.get( "role"  ));
                    String text = Synt.asString(message.get("content"));
                    return switch(role) {
                        case "system" -> SystemMessage.from(text);
                        case "user"   ->   UserMessage.from(text);
                        default       ->     AiMessage.from(text);
                    };
                }
            )
            .toList();
    }

    /**
     * 快捷对话
     * @param model
     * @param messages
     * @return
     */
    public static String chat(String model, List<Map> messages) {
        ChatLanguageModel lm = getChatModel(model);
        List<ChatMessage> ms = toChatMessages(messages);

        ChatRequest  rq = ChatRequest.builder()
            .messages(ms)
            .build();

        ChatResponse rp = lm.chat(rq);

        return rp.toString();
    }

    /**
     * 快捷对话
     * @param model
     * @param messages
     * @param tools
     * @return
     */
    public static String chat(String model, List<Map> messages, Set<String> tools) {
        ChatLanguageModel lm = getChatModel(model);
        List<ChatMessage> ms = toChatMessages(messages);
        List<ToolSpecification> ts = toToolSpecifications(tools);

        ChatRequest  rq = ChatRequest.builder()
            .toolSpecifications(ts)
            .messages(ms)
            .build();

        ChatResponse rp = lm.chat(rq);
        AiMessage am = rp.aiMessage();

        StringBuilder sb = new StringBuilder(rp.toString());

        if (am != null && am.hasToolExecutionRequests()) {
            ms.add(am);

            // 调用工具
            List<ToolExecutionRequest> tes = am.toolExecutionRequests();
            tes.forEach(ter -> {
                Mathod mat = getTools().get(ter.name());
                Method met = mat.getMethod ();
                Class  cla = mat.getMclass ();
                Object obj = Core.getInstance(cla);

                ToolExecutor te = new DefaultToolExecutor(obj, met);
                String  rs = te.execute(ter, UUID.randomUUID().toString());
                ChatMessage  tm = ToolExecutionResultMessage.from(ter, rs);
                ms.add (tm);
            });

            rq = ChatRequest.builder()
                .messages(ms)
                .build();

            rp = lm.chat (rq);

            sb.append( rp.toString() );
        }

        return sb.toString( );
    }

    /**
     * 标准对话
     * @param model
     * @param messages
     * @param tools
     * @param temp temperature
     * @param topP
     * @param topK
     * @param maxT max output tokens
     * @param r 工具递归限定, 0 不限
     * @return
     */
    public static String chat(String model, List<Map> messages, Set<String> tools, double temp, double topP, int topK, int maxT, int r) {
        ChatLanguageModel lm = getChatModel(model);
        List<ChatMessage> ms = toChatMessages(messages);
        List<ToolSpecification> ts = toToolSpecifications(tools);

        if (r == 0) r = Integer.MAX_VALUE;

        DefaultChatRequestParameters.Builder pb = ChatRequestParameters.builder();
        if (temp != 0d) {
            pb.temperature(temp);
        }
        if (topP != 0d) {
            pb.topP(topP);
        }
        if (topK != 0 ) {
            pb.topK(topK);
        }
        if (maxT != 0 ) {
            pb.maxOutputTokens (maxT);
        }

        // 带工具和不带工具
        ChatRequestParameters ps, pz ;
            pz  = pb.build();
        if (ts != null && ! ts.isEmpty()) {
            pb.toolSpecifications(ts);
            ps  = pb.build();
        } else {
            ps  = pz;
        }

        ChatRequest rq = ChatRequest.builder()
            .parameters( ps )
            .messages(ms)
            .build();

        ChatResponse rp = lm.chat(rq);
        AiMessage am = rp.aiMessage();

        StringBuilder sb = new StringBuilder(rp.toString());

        while (am != null && am.hasToolExecutionRequests()) {
            ms.add(am);

            // 调用工具
            List<ToolExecutionRequest> tes = am.toolExecutionRequests();
            tes.forEach(ter -> {
                Mathod mat = getTools().get(ter.name());
                Method met = mat.getMethod ();
                Class  cla = mat.getMclass ();
                Object obj = Core.getInstance(cla);

                ToolExecutor te = new DefaultToolExecutor(obj, met);
                String  rs = te.execute(ter, UUID.randomUUID().toString());
                ChatMessage  tm = ToolExecutionResultMessage.from(ter, rs);
                ms.add (tm);
            });

            ChatRequestParameters px = (-- r) > 0 ? ps : pz ;

            ChatRequest rx = ChatRequest.builder()
                .parameters(px)
                .messages  (ms)
                .build     (  );

            // 继续执行
            rp = lm.chat ( rx );
            am = rp.aiMessage();

            sb.append( rp.toString() );
        }

        return sb.toString( );
    }

    /**
     * 流式对话
     * @param model
     * @param messages
     * @param tools
     * @param temp temperature
     * @param topP
     * @param topK
     * @param maxT max output tokens
     * @param r 工具递归限定, 0 不限
     * @param callback
     */
    public static Defer chat(String model, List<Map> messages, Set<String> tools, double temp, double topP, int topK, int maxT, int r, Consumer<String> callback) {
        StreamingChatLanguageModel lm = getStreamingModel(model);
        List<ToolSpecification> ts = toToolSpecifications(tools);
        List<ChatMessage> ms = toChatMessages(messages);

        DefaultChatRequestParameters.Builder pb = ChatRequestParameters.builder();
        if (temp != 0d) {
            pb.temperature(temp);
        }
        if (topP != 0d) {
            pb.topP(topP);
        }
        if (topK != 0 ) {
            pb.topK(topK);
        }
        if (maxT != 0 ) {
            pb.maxOutputTokens (maxT);
        }

        // 带工具和不带工具
        ChatRequestParameters ps, pz ;
            pz  = pb.build();
        if (ts != null && ! ts.isEmpty()) {
            pb.toolSpecifications(ts);
            ps  = pb.build();
        } else {
            ps  = pz;
        }

        ChatRequest rq = ChatRequest.builder()
            .parameters( ps )
            .messages(ms)
            .build();

        Defer df = new Defer();

        lm.chat(rq, new StreamingChatResponseHandler() {
            int x = r;
            @Override
            public void onPartialResponse (String rs ) {
                callback.accept(rs);
                
                if (df.interrupted()) {
                    throw new CruxExemption("@magpie.stream.cancel");
                }
            }
            @Override
            public void onCompleteResponse(ChatResponse rp) {
                AiMessage am = rp.aiMessage();
                if (am != null && am.hasToolExecutionRequests()) {
                    ms.add(am);

                    // 调用工具
                    List<ToolExecutionRequest> tes = am.toolExecutionRequests();
                    tes.forEach(ter -> {
                        Mathod mat = getTools().get(ter.name());
                        Method met = mat.getMethod ();
                        Class  cla = mat.getMclass ();
                        Object obj = Core.getInstance(cla);

                        ToolExecutor te = new DefaultToolExecutor(obj, met);
                        String  rs = te.execute(ter, UUID.randomUUID().toString());
                        ChatMessage  tm = ToolExecutionResultMessage.from(ter, rs);
                        ms.add (tm);
                    });

                    ChatRequestParameters px = (-- x) > 0 ? ps : pz ;

                    ChatRequest rx = ChatRequest.builder()
                        .parameters(px)
                        .messages  (ms)
                        .build     (  );

                    // 递归执行
                    lm.chat( rx, this );
                } else {
                    // 外部放行
                    df.done();
                }
            }
            @Override
            public void onError(Throwable ex) {
                // 异常中止
                df.fail(ex);
            }
        });

        return  df;
    }

    /**
     * 获取向量
     * @param parts
     * @param etype 处理文档还是处理查询
     * @return
     */
    public static List<List<Float>> embed(List<String> parts, ETYPE etype) {
        return getEmbeddingModel("embedding")
            .embedAll(parts
                .stream()
                .map(
                    a -> TextSegment.from (a)
                )
                .toList()
            )
            .content (  )
            .stream()
            .map(
                b -> b.vectorAsList()
            )
            .toList();
    }

    /**
     * 拆分文本
     * @param text
     * @return
     */
    public static List<String> split(String text) {
        return getDocumentSplitter()
            .split  (Document.from(text))
            .stream ()
            .map    (seg->seg.text ())
            .map    (str->str.strip())
            .filter (str->!str.isEmpty())
            .toList ();
    }

}
