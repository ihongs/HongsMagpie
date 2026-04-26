package io.github.ihongs.serv.magpie;

import io.github.ihongs.Core;
import io.github.ihongs.CoreLogger;
import io.github.ihongs.CruxException;
import io.github.ihongs.jsp.Pagelet;
import io.github.ihongs.util.Dist;
import io.github.ihongs.util.Synt;
import io.github.ihongs.util.Template;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表单代理
 * @author Hongs
 */
public class FormAgent {

    /**
     * 查询转换
     * 自然语言转查询条件
     * @param message 自然语言查询
     * @return 查询条件
     * @throws CruxException
     */
    public List conv(String message) throws CruxException {
        String result = chat(new ArrayList(0), message +"\n\n```json\n[]\n```");

        // 清理消息
        Pattern  pattern = Pattern.compile("(^.*```json|```$)", Pattern.DOTALL);
        Matcher  matcher = pattern.matcher(result.trim());
        result = matcher.replaceAll("").trim();

        return (List) Dist.toObject(result);
    }

    /**
     * 查询对话
     * @param messages 历史消息列表
     * @param content  当前消息内容
     * @return 返回内容
     * @throws CruxException
     */
    public String chat(List<Map> messages, String content) throws CruxException {
        // 系统角色
        Template temp;
        try {
            temp = Template.compile(Path.of(Core.CONF_PATH + "/template/form-agent.md"));
        }
        catch (IOException ex) {
            throw new CruxException(ex);
        }
        temp.regist("escape", args -> {
            String text = Synt.asString(args[0]);
            text = Pagelet.escape(text);
            return text;
        });
        temp.assign("time", Core.ACTION_TIME.get());
        temp.assign("zone", Core.ACTION_ZONE.get());
        String system = temp.render();
        CoreLogger.debug("system: {}", system);

        // 消息列表
        List<Map> msgs = new ArrayList(messages.size() + 2);
        msgs.add(Synt.mapOf(
            "role", "system",
            "content", system
        ));
        msgs.addAll(messages);
        msgs.add(Synt.mapOf(
            "role", "user",
            "content", content
        ));

        Set tks = Synt.setOf();
        Map cnf = Synt.mapOf();
        Map env = Synt.mapOf("FORM_AGENT", this);
        String result = AiUtil.chat("find.agent", msgs, tks, cnf, env);
        CoreLogger.debug("result: {}", result);

        // 清理思考过程
        Pattern  pattern = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
        Matcher  matcher = pattern.matcher(result.trim());
        result = matcher.replaceAll("").trim();

        return result;
    }

}
