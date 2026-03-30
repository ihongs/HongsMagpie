package io.github.ihongs.serv.magpie;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CoreLogger;
import io.github.ihongs.CruxException;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.action.ActionRunner;
import io.github.ihongs.action.FormSet;
import io.github.ihongs.dh.IFigure;
import io.github.ihongs.dh.JFigure;
import io.github.ihongs.jsp.Pagelet;
import io.github.ihongs.serv.magpie.AiUtil;
import io.github.ihongs.util.Dist;
import io.github.ihongs.util.Syno;
import io.github.ihongs.util.Synt;
import io.github.ihongs.util.Template;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询代理
 * 将自然语言翻译成查询结构
 * @author Hongs
 */
public class QueryAgent {

    private final IFigure that;

    public QueryAgent(IFigure that) {
        this.that = that;
    }

    public String getConf() {
        return Synt.asString(getParams().get("conf"));
    }

    public Map<String, Map> getFields() {
        return that.getFields();
    }

    public Map<String, Map> getParams() {
        return that.getFields();
    }

    public Set<String> getFindable() {
        return that.getFindable();
    }

    public Set<String> getSrchable() {
        return that instanceof JFigure ? ((JFigure) that).getSrchable() : new HashSet(0);
    }

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
        // 整理字段
        Set<String> findable = getFindable( );
        Set<String> srchable = getSrchable( );
        Map<String, Map> fieldz = getFields();
        List<List>  fields = new ArrayList( findable.size( ) );
        Map types = FormSet.getInstance().getEnum("__types__");

        for (String name : findable) {
            Map field = fieldz.get(name);
            if (field == null) continue ;
            String text = Synt.asString(field.get("__text__"));
            String type = Synt.asString(field.get("__type__"));
            String typx = Synt.asString(field.get(  "type"  )); // 内部类型
            String typa = Synt.asString(types.get(   type   )); // 概略类型

            if (typa == null) {
                typa  = type;
            }

            // hidden 由其内在 type 定义
            if ("hidden".equals(type)) {
                if ("number".equals(typx)
                ||  "double".equals(typx)
                ||  "float" .equals(typx)
                ||  "long"  .equals(typx)
                ||  "int"   .equals(typx)) {
                    typa = "number";
                } else {
                    typa = "string";
                }
            } else
            // date 类型需区分秒还是毫秒
            if ("date".equals(typa)) {
                if (typx != null && typx.endsWith("stamp")) {
                    typa = "timestamp";
                } else {
                    typa = "time";
                }
            }

            String rels = "is";
            switch(typa) {
                case "string":
                    if (!"textarea".equals(type)
                    &&  !"textview".equals(type)
                    &&  ! "search" .equals(type)) {
                        rels += ",eq,ne,in,no";
                    }
                    if (srchable. contains(name)) {
                        rels += ",se,ns";
                    }
                    break;
                case "number":
                    rels += ",eq,ne,in,ni,le,lt,ge,gt";
                    break;
                case "timestamp":
                case "time":
                    rels += ",le,lt,ge,gt";
                    break;
                case "enum":
                    rels += ",eq,ne,in,ni";
                    break;
                case "fork":
                    rels += ",eq,ne,in,ni";
                    break;
            }
            fields.add(Synt.listOf(text, name, typa, rels));
        }

        // 系统角色
        Template temp;
        try {
            temp = Template.compile(Path.of(Core.CONF_PATH + "/template/query-agent.md"));
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
        temp.assign("fields", fields);
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

        Set tks = Synt.setOf("find_mapping");
        Map env = Synt.mapOf("QUERY_AGENT", this);
        Map cnf = Synt.mapOf();
        String result = AiUtil.chat("query.agent", msgs, tks, cnf, env);
        CoreLogger.debug("result: {}", result);

        // 清理思考过程
        Pattern  pattern = Pattern.compile("<think>.*?</think>", Pattern.DOTALL);
        Matcher  matcher = pattern.matcher(result.trim());
        result = matcher.replaceAll("").trim();

        return result;
    }

    /**
     * 查询映射
     * 找到用户提到的选项、关联文本的值
     * @param name 字段名
     * @param texts  选项、关联文本列表
     * @return
     * @throws CruxException
     */
    public List<List<String>> findMapping(String name, Set<String> texts) throws CruxException {
        Map field = getFields().get(name);
        Map types = FormSet.getInstance().getEnum("__types__");
        String type = Synt.asString(field.get("__type__"));
               type = Synt.asString(types.get(   type   ));

        if ("enum".equals(type)) {
            return findEnumMapping(name, field, texts);
        } else
        if ("fork".equals(type)) {
            return findForkMapping(name, field, texts);
        }

        return new ArrayList(0);
    }

    public List<List<String>> findEnumMapping(String name, Map field, Set<String> texts) throws CruxException {
        String conf = Synt.asString(field.get("conf"));
        String link = Synt.asString(field.get("enum"));
        if (conf == null) conf = getConf();
        if (link == null) link = name;

        List<List<String>> list = new ArrayList(texts.size());
        Map dict  = FormSet.getInstance(conf).getEnum( link );
        if (dict != null) {
            for(String text : texts) {
                List<String> item = findMostSimilar(text, dict, 0.5); // 寻找最接近的选项
                if (null  != item) {
                    list.add(item);
                }
            }
        }
        return list;
    }

    public List<List<String>> findForkMapping(String name, Map field, Set<String> texts) throws CruxException {
        String conf = Synt.asString(field.get("conf"));
        String link = Synt.asString(field.get("enum"));
        if (conf == null) conf = getConf();
        if (link == null) link = name.replaceFirst("_id$", "");

        String at = (String) field.get("data-at");
        String vk = (String) field.get("data-vk");
        String tk = (String) field.get("data-tk");

        if (at == null || at.isEmpty()) {
            at = conf + "/" + link + "/search";
        }

        // 请求数据
        Map cd = new HashMap( );
        Map rd = new HashMap( );
        Set rb = new HashSet( );
        rb.add(tk);
        rb.add(vk);
        rb.add(Cnst.ID_KEY);
        rd.put(Cnst.RB_KEY, rb);
        rd.put(Cnst.RN_KEY, 1 );
        rd.put(Cnst.PN_KEY, 0 );

        // 请求环境
        ActionHelper ah = ActionHelper.newInstance();
        ah.setContextData( cd );
        ah.setRequestData( rd );
        ActionRunner ar = ActionRunner.newInstance(ah, at);

        List<List<String>> list = new ArrayList(texts.size());
        for (String text : texts) {
            rd.put(Cnst.WD_KEY, text);

            // 执行动作
            ar.doInvoke();

            // 获取结果
            Map rs = ah.getResponseData();
            List<Map> rows = (List<Map>) rs.get("list");
            if (rows != null && ! rows.isEmpty()) {
                 Map  row  = rows.get (0);
                List<String> item = new ArrayList(2);
                item.add(Synt.asString(row.get(vk)));
                item.add(Synt.asString(row.get(tk)));
                item.add(text); // 查找文本
                list.add(item);
            }
        }
        return list;
    }

    /**
     * 寻找最相似的选项
     * @param text 查找文本
     * @param dict 目标字典
     * @param minSimilarity 低于此相似度返回 null
     * @return
     */
    private static List<String> findMostSimilar(String text, Map<String, String> dict, double minSimilarity) {
        String maxSimilarTxt = null;
        String maxSimilarKey = null;
        double maxSimilarity = 0.0D;

        for(Map.Entry<String, String> et : dict.entrySet()) {
            String curKey = et.getKey(  );
            String curTxt = et.getValue();
            double curSim = Syno.sameness( text, curTxt );

            if (maxSimilarity < curSim) {
                maxSimilarity = curSim;
                maxSimilarTxt = curTxt;
                maxSimilarKey = curKey;
            }
        }

        if (maxSimilarity >= minSimilarity) {
            List<String> arr = new ArrayList(2);
            arr.add(maxSimilarKey);
            arr.add(maxSimilarTxt);
            arr.add(text); // 查找文本
            return arr;
        }

        return null;
    }

}
