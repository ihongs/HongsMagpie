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
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 接口工具
 * 配置均在 magpie.properties
 * @author Hongs
 */
public final class AiUtil {

    public static enum ETYPE {DOC, QRY};

    /**
     * 获取对话模型
     * @param nam
     * @return
     */
    public static ChatLanguageModel getChatModel(String nam) {
        CoreConfig cc = CoreConfig.getInstance("magpie");
        String api = cc.getProperty("magpie.llm."+nam+".api", nam);
        String mod = cc.getProperty("magpie.llm."+nam+".mod");
        String url = cc.getProperty("magpie.llm."+api+".url");
        String key = cc.getProperty("magpie.llm."+api+".key");

        return OpenAiChatModel
            .builder  (   )
            .  apiKey (key)
            . baseUrl (url)
            .modelName(mod)
            .build    (   );
    }

    /**
     * 获取流式模型
     * @param nam
     * @return
     */
    public static StreamingChatLanguageModel getStreamingModel(String nam) {
        CoreConfig cc = CoreConfig.getInstance("magpie");
        String api = cc.getProperty("magpie.llm."+nam+".api", nam);
        String mod = cc.getProperty("magpie.llm."+nam+".mod");
        String url = cc.getProperty("magpie.llm."+api+".url");
        String key = cc.getProperty("magpie.llm."+api+".key");

        return OpenAiStreamingChatModel
            .builder  (   )
            .  apiKey (key)
            . baseUrl (url)
            .modelName(mod)
            .build    (   );
    }

    /**
     * 获取向量模型
     * @param nam
     * @return
     */
    public static EmbeddingModel getEmbeddingModel(String nam) {
        CoreConfig cc = CoreConfig.getInstance("magpie");
        String api = cc.getProperty("magpie.llm."+nam+".api", nam);
        String mod = cc.getProperty("magpie.llm."+nam+".mod");
        String url = cc.getProperty("magpie.llm."+api+".url");
        String key = cc.getProperty("magpie.llm."+api+".key");

        return OpenAiEmbeddingModel
            .builder  (   )
            .  apiKey (key)
            . baseUrl (url)
            .modelName(mod)
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
                String clsn = cc.getProperty("magpie.splitters.class", DocumentSplitters.class.getName());
                int maxSegmentSize = cc.getProperty("magpie.splitter.max-segment-size", 1000);
                int maxOverlaySize = cc.getProperty("magpie.splitter.max-overlay-size", 100 );

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

    public static Map<String, Mathod> getToolMethods() {
        return Core.getInterior().got("magpie.tools" , ( ) -> {
            try {
                String[] ps = CoreConfig.getInstance("magpie" )
                                        .getProperty("magpie.tools.mount", "")
                                        .split( ";" );
                Map<String, Mathod> ts = new HashMap(ps.length);
                for(String pn : ps) {
                    pn = pn.trim( );
                    Set<String> cs ;
                    if (pn.endsWith(".**")) {
                        cs = CoreRoster.getClassNames(pn, true);
                    } else
                    if (pn.endsWith(".*" )) {
                        cs = CoreRoster.getClassNames(pn,false);
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
                return ts;
            }
            catch (IOException | ClassNotFoundException ex) {
                throw new CruxExemption(ex);
            }
        });
    }

    public static List<ToolSpecification> toToolSpecifications(Set<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return new ArrayList();
        }
        Map<String, Mathod> ts = getToolMethods();
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

    public static String chat(String model, List<Map> messages) {
        ChatLanguageModel lm = getChatModel(model);
        List<ChatMessage> ms = toChatMessages(messages);

        ChatRequest rq = ChatRequest.builder()
            .messages(ms)
            .build();

        return lm.chat(rq).toString();
    }

    public static void chat(String model, List<Map> messages, Consumer<String> callback) {
        StreamingChatLanguageModel lm = getStreamingModel(model);
        List<ChatMessage> ms = toChatMessages(messages);

        ChatRequest rq = ChatRequest.builder()
            .messages(ms)
            .build();

        lm.chat(rq, new StreamingChatResponseHandler() {
            @Override
            public void onError(Throwable ex ) {
                CoreLogger.warn(ex.toString());
                throw new CruxExemption ( ex );
            }
            @Override
            public void onPartialResponse (String rs ) {
                callback.accept(rs);
            }
            @Override
            public void onCompleteResponse(ChatResponse rp) {
                // Nothing to do
            }
        });
    }

    public static String chat(String model, Set<String> tools, List<Map> messages) {
        ChatLanguageModel lm = getChatModel(model);
        List<ChatMessage> ms = toChatMessages(messages);
        List<ToolSpecification> ts = toToolSpecifications(tools);

        ChatRequest rq = ChatRequest.builder()
            .toolSpecifications(ts)
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
                Mathod mat = getToolMethods().get(ter.name());
                Method met = mat.getMethod ();
                Class  cla = mat.getMclass ();
                Object obj = Core.getInstance(cla);

                ToolExecutor te = new DefaultToolExecutor(obj, met);
                String  rs = te.execute(ter, UUID.randomUUID().toString());
                ChatMessage  tm = ToolExecutionResultMessage.from(ter, rs);
                ms.add (tm);
            });

            ChatRequest cr = ChatRequest.builder()
                .toolSpecifications(ts)
                .messages(ms)
                .build();

            // 继续执行
            rp = lm.chat (cr);
            am = rp.aiMessage( );

            sb.append(rp.toString());
        }

        return sb.toString();
    }

    public static void chat(String model, Set<String> tools, List<Map> messages, Consumer<String> callback) {
        StreamingChatLanguageModel lm = getStreamingModel(model);
        List<ToolSpecification> ts = toToolSpecifications(tools);
        List<ChatMessage> ms = toChatMessages(messages);

        ChatRequest rq = ChatRequest.builder()
            .toolSpecifications(ts)
            .messages(ms)
            .build();

        lm.chat(rq, new StreamingChatResponseHandler() {
            @Override
            public void onError(Throwable ex ) {
                CoreLogger.warn(ex.toString());
                throw new CruxExemption ( ex );
            }
            @Override
            public void onPartialResponse (String rs ) {
                callback.accept(rs);
            }
            @Override
            public void onCompleteResponse(ChatResponse rp) {
                AiMessage am = rp.aiMessage();
                if (am != null && am.hasToolExecutionRequests()) {
                    ms.add(am);

                    // 调用工具
                    List<ToolExecutionRequest> tes = am.toolExecutionRequests();
                    tes.forEach(ter -> {
                        Mathod mat = getToolMethods().get(ter.name());
                        Method met = mat.getMethod ();
                        Class  cla = mat.getMclass ();
                        Object obj = Core.getInstance(cla);

                        ToolExecutor te = new DefaultToolExecutor(obj, met);
                        String  rs = te.execute(ter, UUID.randomUUID().toString());
                        ChatMessage  tm = ToolExecutionResultMessage.from(ter, rs);
                        ms.add (tm);
                    });

                    ChatRequest cr = ChatRequest.builder()
                        .toolSpecifications(ts)
                        .messages(ms)
                        .build();

                    // 递归执行
                    lm.chat(cr, this);
                }
            }
        });
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
                    a -> TextSegment.from(a)
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
