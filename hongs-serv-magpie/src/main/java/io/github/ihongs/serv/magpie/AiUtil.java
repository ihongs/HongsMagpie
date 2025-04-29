package io.github.ihongs.serv.magpie;

import com.jfinal.template.Engine;
import com.jfinal.template.Template;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionDeveloperMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import io.github.ihongs.Core;
import io.github.ihongs.CoreConfig;
import io.github.ihongs.CoreLogger;
import io.github.ihongs.CruxCause;
import io.github.ihongs.CruxExemption;
import io.github.ihongs.util.Inst;
import io.github.ihongs.util.Syno;
import io.github.ihongs.util.Synt;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 接口工具
 * 配置均在 magpie.properties
 * @author Hongs
 */
public final class AiUtil {

    public static enum ETYPE {DOC, QRY};

    //** OpenAI **/

    /**
     * 获取向量处理器
     * 配置:
     * magpie.ai.name.url=接口基础链接
     * magpie.ai.name.key=接口认证密钥
     * @param name
     * @return
     */
    public static OpenAIClient getAiClient(String name) {
        return Core.getInstance()
            .got(OpenAIClient.class.getName()+":"+name, ()->{
                CoreConfig cc = CoreConfig.getInstance("magpie");
                String url = cc.getProperty("magpie.ai."+name+".url");
                String key = cc.getProperty("magpie.ai."+name+".key");

                return OpenAIOkHttpClient
                    .builder()
                    .baseUrl(url)
                    .apiKey (key)
                    .build  ();
            });
    }

    /**
     * 获取向量
     * @param parts
     * @param etype 处理文档还是处理查询
     * @return
     */
    public static List<List<Float>> embedding(List<String> parts, ETYPE etype) {
        CoreConfig cc = CoreConfig.getInstance("magpie");
        String api = cc.getProperty("magpie.ai.embedding.api", "embedding");
        String mod = cc.getProperty("magpie.ai.embedding.mod", "test");

        // 临时测试
        if ("test".equals(mod)) {
            CoreLogger.warn ( "Test embedding..."  );
            List vects = new ArrayList(parts.size());
            for (int i = 0; i < parts.size(); i ++ ) {
                vects.add(null);
            }
            return vects;
        }

        EmbeddingCreateParams.Builder builder = EmbeddingCreateParams.builder();
        builder.inputOfArrayOfStrings(parts);
        builder.model(mod);

        return getAiClient(api)
            .embeddings()
            .create(builder.build())
            .data  ()
            .stream()
            .map   (
                a -> a.embedding ()
            .stream()
            .map   (
                b -> b.floatValue()
            )
            .toList()
            )
            .toList();
    }

    public static String chat(String model, List<Map> messages) {
        CoreConfig cc = CoreConfig.getInstance("magpie");
        String api = cc.getProperty("magpie.ai."+model+".api", model );
        String mod = cc.getProperty("magpie.ai."+model+".mod", "test");
        String sys = cc.getProperty("magpie.ai."+ api +".sys", "system");

        // 临时测试
        if ("test".equals(mod)) {
            CoreLogger.warn ( "Test chat {}...", model );
            return "Hello world";
        }

        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder();
        for (Map message : messages) {
            String role = Synt.asString(message.get( "role"  ));
            String text = Synt.asString(message.get("content"));
            if ("system".equals(role) || "developer".equals(role)) {
                   role = sys ;
            }
            switch(role) {
                case "assistant":
                    builder.addMessage(ChatCompletionAssistantMessageParam.builder().content(text).build());
                    break;
                case "developer":
                    builder.addMessage(ChatCompletionDeveloperMessageParam.builder().content(text).build());
                    break;
                case "system":
                    builder.addMessage(ChatCompletionSystemMessageParam.builder().content(text).build());
                    break;
                default:
                    builder.addMessage(ChatCompletionUserMessageParam.builder().content(text).build());
            }
        }
        builder.model(mod);

        return getAiClient(api)
            .chat   ( )
            .completions()
            .create (builder.build())
            .choices( )
            .get    (0)
            .message( )
            .content( )
            .orElse("");
    }

    public static void chat(String model, List<Map> messages, Consumer<String> callback) {
        CoreConfig cc = CoreConfig.getInstance("magpie");
        String api = cc.getProperty("magpie.ai."+model+".api", model );
        String mod = cc.getProperty("magpie.ai."+model+".mod", "test");
        String sys = cc.getProperty("magpie.ai."+ api +".sys", "system");

        // 临时测试
        if ("test".equals(mod)) {
            CoreLogger.warn ( "Test chat {}...", model );
            callback.accept ( "Hello world" );
            return;
        }

        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder();
        for (Map message : messages) {
            String role = Synt.asString(message.get( "role"  ));
            String text = Synt.asString(message.get("content"));
            if ("system".equals(role) || "developer".equals(role)) {
                   role = sys ;
            }
            switch(role) {
                case "assistant":
                    builder.addMessage(ChatCompletionAssistantMessageParam.builder().content(text).build());
                    break;
                case "developer":
                    builder.addMessage(ChatCompletionDeveloperMessageParam.builder().content(text).build());
                    break;
                case "system":
                    builder.addMessage(ChatCompletionSystemMessageParam.builder().content(text).build());
                    break;
                default:
                    builder.addMessage(ChatCompletionUserMessageParam.builder().content(text).build());
            }
        }
        builder.model(mod);

        try (
            StreamResponse<ChatCompletionChunk> stream =  getAiClient(api)
                .chat()
                .completions()
                .createStreaming(builder.build())
        ) {
            stream.stream().forEach(chunk-> {
                callback.accept(
                    chunk.choices( )
                         .get    (0)
                         .delta  ( )
                         .content( )
                         .orElse("")
                );
            });
        }
    }

    //** 文档拆分 **/

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

    //** 模板渲染 **/

    /**
     * 获取渲染引擎
     * 模板基础目录 etc/magpie/temp
     * @return
     */
    public static Engine getRenderEngine() {
        return Core.getInstance().got(
            Engine.class.getName()+":magpie",
            () -> new Engine()
                .addSharedMethod(new RenderTempFunc())
                .setBaseTemplatePath(Core.CONF_PATH+"/magpie/temp")
        );
    }

    /**
     * 获取渲染模板
     * @param temp
     * @return
     */
    public static Template getRenderTemp(String temp) {
        return Core.getInstance().got(
            Template.class.getName()+":magpie:"+temp,
            () -> getRenderEngine().getTemplate(temp)
        );
    }

    /**
     * 应用指定模板渲染数据
     * @param temp 模板文件
     * @param info 数据
     * @return
     */
    public static String renderByTemp(String temp, Map info) {
        return getRenderTemp(temp).renderToString(info);
    }

    /**
     * 应用模板文本渲染数据
     * @param text 模板文本
     * @param info 数据
     * @return
     */
    public static String renderByText(String text, Map info) {
        return getRenderEngine().getTemplateByString(text).renderToString(info);
    }

    /**
     * 模板函数
     */
    public static class RenderTempFunc {
        public String indent(String s) {
            return indent(s, 2);
        }
        public String indent(String s, int n) {
            if (s == null || "".equals(s)) {
                return "";
            }
            return Syno.indent(s, " " .repeat(n)).trim();
        }
        public String concat(Object o) {
            return concat(o, ", ");
        }
        public String concat(Object o, String s) {
            if (o == null || "".equals(o)) {
                return "";
            }
            return Syno.concat(s, Synt.asColl(o)).trim();
        }
        public String date_format(Object d, String f) {
            if (d == null || "".equals(d)) {
                return "";
            } else
            if (d instanceof Date) {
                return Inst.format((Date) d, f);
            } else
            if (d instanceof Instant) {
                return Inst.format((Instant) d, f);
            } else
            {
                return Inst.format(Synt.asLong(d), f);
            }
        }
        public String strip(String ob, Object sd) {
            Set    sa;
            String st;
            sa = Synt. toSet (sd);
            st = Synt.declare(ob , "" );
            if (sa == null) {
                sa = Synt.setOf("trim"); // 默认清除首尾空字符
            }
            if (! sa.isEmpty()) {
                if (sa.contains("cros") || sa.contains("html")) {
                    st = Syno.stripCros(st); // 清除脚本
                }
                if (sa.contains("tags") || sa.contains("html")) {
                    st = Syno.stripTags(st); // 清除标签
                }
                if (sa.contains("gaps") || sa.contains("html")) {
                    st = Syno.stripGaps(st); // 空行清理
                }
                if (sa.contains("ends") || sa.contains("text")) {
                    st = Syno.stripEnds(st); // 换行清理
                }
                if (sa.contains("trim") || sa.contains("html") || sa.contains("text")) {
                    st = st.strip();
                }
            }
            return  st;
        }
    }

}
