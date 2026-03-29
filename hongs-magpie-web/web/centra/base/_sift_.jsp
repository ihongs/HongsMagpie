<%@page import="java.util.Iterator"%>
<%@page import="java.util.HashSet"%>
<%@page import="java.util.Map"%>
<%@page import="java.util.Set"%>
<%@page import="io.github.ihongs.Cnst"%>
<%@page import="io.github.ihongs.dh.Figure"%>
<%@page import="io.github.ihongs.util.Synt"%>
<%@page contentType="text/html" pageEncoding="UTF-8" trimDirectiveWhitespaces="true"%>
<div class="siftbox openbox invisible well">
    <div class="row" style="margin-top: -15px;">
        <div class="col-xs-6 rollbox sift-body">
            <ul class="list-unstyled clearfix">
                <li class="sift-unit template">
                    <div>
                        <legend class="sift-hand">
                            <a href="javascript:;" class="erase bi bi-x pull-right"></a>
                            <span class="sift-lr"></span>
                        </legend>
                        <ul class="sift-list repeated labelbox" data-name="ar">
                            <li class="sift-item template label label-info">
                                <a href="javascript:;" class="erase bi bi-x pull-right"></a>
                                <span class="sift-hand">
                                    <span class="sift-fn"></span>
                                    <span class="sift-fr"></span>
                                    <span class="sift-fv"></span>
                                </span>
                            </li>
                        </ul>
                    </div>
                </li>
                <li class="sift-unit sift-root">
                    <div class="form-group">
                        <label  class="sift-hand form-label control-label">
                            高级查询
                        </label>
                        <div class="form-control multiple">
                            <form class="findbox" onsubmit="return false">
                                <ul class="sift-list repeated labelbox" data-name="ar" data-fn>
                                </ul>
                            </form>
                        </div>
                    </div>
                </li>
            </ul>
            <div class="form-group">
                <select data-sift="fn" class="form-control">
                    <option style="color: gray;" value="-" data-rels="-">字段...</option>
                    <%
                    Set<String> siftEnum = new HashSet();
                    Set<String> findable = new Figure( _fields ).getFindable();
                    Iterator it2 = _fields.entrySet().iterator();
                    while (it2.hasNext()) {
                        Map.Entry et = (Map.Entry) it2.next();
                        Map     info = (Map ) et.getValue();
                        String  name = (String) et.getKey();
                        String  type = (String) info.get("__type__");
                        String  text = (String) info.get("__text__");

                        if ("@".equals(name) || "id".equals(name)) {
                            continue;
                        }
                        // 排除不能查找的字段
                        if (!findable.contains(name)) {
                            continue;
                        }
                        // 明确不能筛选则跳过
                        if (!Synt.declare(info.get("siftable"), true )) {
                            continue;
                        }
                        // 没标签的字段也跳过
                        if (text == null || text.isEmpty()
                        ||  type == null || type.isEmpty()) {
                            continue;
                        }

                        String  kind = "";
                        String  rels = "";
                        String  extr = "";

                        if ("fork".equals(type) || "pick".equals(type)) {
                            String fn = name;
                            if (fn.endsWith( "." )) {
                                fn = fn.substring(0, fn.length() - 1);
                            }
                            String kn = fn +"_fork";
                            if (fn.endsWith("_id")) {
                                fn = fn.substring(0, fn.length() - 3);
                                kn = fn;
                            }
                            String tk = info.containsKey("data-tk") ? (String) info.get("data-tk") : "name";
                            String vk = info.containsKey("data-vk") ? (String) info.get("data-vk") : "id";
                            String ln = info.containsKey("data-ln") ? (String) info.get("data-ln") :  kn ;
                            String at = info.containsKey("data-at") ? (String) info.get("data-at") :  "" ;
                            String st = info.containsKey("data-st") ? (String) info.get("data-st") :
                                      ( info.containsKey("data-al") ? (String) info.get("data-al") :  "" );
                            at = at.replace("centre", "centra");
                            st = st.replace("centre", "centra");
                            // 选择时禁用创建
                            if ( ! st.isEmpty (   )) {
                            if ( ! st.contains("#")) {
                                st = st + "#.deny=.create";
                            } else {
                                st = st + "&.deny=.create";
                            }}
                            /**
                             * 关联路径: base/search|data/xxxx/search?rb=a,b,c
                             * 需转换为: data/xxxx/search.act?rb=a,b,c
                             */
                            if (!at.isEmpty()) {
                                int p  = at.indexOf  ('|');
                                if (p != -1) {
                                    at = at.substring(1+p);
                                }   p  = at.indexOf  ('?');
                                if (p != -1) {
                                    at = at.substring(0,p)
                                       +      Cnst.ACT_EXT
                                       + at.substring(0+p);
                                } else {
                                    at = at + Cnst.ACT_EXT;
                                }
                            }
                            extr = " data-ln=\""+ln+"\" data-vk=\""+vk+"\" data-tk=\""+tk+"\" data-href=\""+st+"\" data-target=\"@\"";
                            rels =  "is,eq,ne";
                            kind =  "fork";
                        } else
                        if ("enum".equals(type) || "type".equals(type) || "select".equals(type) || "check".equals(type) || "radio".equals(type)) {
                            String ln = info.containsKey("data-ln") ? (String) info.get("data-ln") : name;
                            siftEnum.add(ln); // 需从后端获取数据
                            extr = " data-ln=\""+ln+"\"";
                            rels =  "is,eq,ne";
                            kind =  "enum";
                        } else
                        if ("date".equals(type) || "time".equals(type) || "datetime".equals(type)) {
                            Object fomt = Synt.defoult(info.get("format"),type);
                            Object fset = Synt.defoult(info.get("offset"), "" );
                            type = Synt.declare(info.get("type"), "time");
                            extr = " data-format=\""+fomt+"\" data-offset=\""+fset+"\"";
                            rels =  "is,eq,ne,gt,ge,lt,le";
                            kind =  "date";
                        } else
                        if ("number".equals(type) || "range".equals(type) || "color".equals(type)) {
                            Object typa = info.get("type");
                            if ("int".equals(typa) || "long".equals(typa) || "color".equals(type)) {
                                extr = " data-for=\"int\"";
                            }
                            rels =  "is,eq,ne,gt,ge,lt,le";
                            kind =  "number";
                        } else
                        if ("string".equals(type) || "email".equals(type) || "url".equals(type) || "tel".equals(type) || "sms".equals(type) || "text".equals(type)) {
                            String ln = info.containsKey("data-ln") ? (String) info.get("data-ln") : name;
                            extr = " data-ln=\""+ln+"\"";
                            if (_sd.contains(name)) {
                                rels =  "is,eq,ne,se,ns";
                            } else {
                                rels =  "is,eq,ne";
                            }
                            kind =  "string";
                        } else
                        if ("search".equals(type) || "textarea".equals(type) || "textview".equals(type)) {
                            String ln = info.containsKey("data-ln") ? (String) info.get("data-ln") : name;
                            extr = " data-ln=\""+ln+"\"";
                            if (_sd.contains(name)) {
                                rels =  "is,se,ns";
                            } else {
                                rels =  "is,eq,ne";
                            }
                            kind =  "string";
                        } else
                        {
                            rels =  "is";
                            kind =  "no";
                        }
                    %>
                    <option value="<%=name%>" data-type="<%=type%>" data-kind="<%=kind%>" data-rels="<%=rels%>"<%=extr%>><%=text%></option>
                    <%} /*End For*/%>
                </select>
            </div>
            <div class="form-group">
                <select data-sift="fr" class="form-control">
                    <option value="-" style="color: gray;">关系...</option>
                    <option value="is">为</option>
                    <option value="eq">等于</option>
                    <option value="ne">不等于</option>
                    <option value="se">搜索</option>
                    <option value="ns">搜索排除</option>
                    <option value="gt">大于</option>
                    <option value="ge">大于或等于</option>
                    <option value="lt">小于</option>
                    <option value="le">小于或等于</option>
                </select>
            </div>
            <div class="form-group">
                <div data-sift="fv">
                    <div data-kind="-">
                        <div class="btn btn-block btn-default text-left disabled">请先选择<b>筛查字段</b>和<b>条件关系</b></div>
                    </div>
                    <div data-kind="is" class="sift-select">
                        <select class="value form-control">
                            <option value=""><%=_locale.translate("fore.form.select", "")%></option>
                            <option value="not-none">非空</option>
                            <option value="none">空</option>
                        </select>
                    </div>
                    <div data-kind="string" class="sift-input">
                        <div class="input-group">
                            <input class="value form-control" type="text"/>
                            <div class="input-group-btn">
                                <button type="button" class="ensue btn btn-info"><span class="bi bi-plus-lg"></span></button>
                            </div>
                        </div>
                    </div>
                    <div data-kind="number" class="sift-input">
                        <div class="input-group">
                            <input class="value form-control" type="number"/>
                            <div class="input-group-btn">
                                <button type="button" class="ensue btn btn-info"><span class="bi bi-plus-lg"></span></button>
                            </div>
                        </div>
                    </div>
                    <div data-kind="date">
                        <button type="button" class="btn btn-default btn-block text-left">选择...</button>
                    </div>
                    <div data-kind="fork">
                        <button type="button" class="btn btn-default btn-block text-left">选择...</button>
                    </div>
                    <div data-kind="sift">
                        <button type="button" class="btn btn-default btn-block text-left">取值...</button>
                    </div>
                    <%for (String ln : siftEnum) {%>
                    <div data-kind="enum" data-name="<%=ln%>" class="sift-select">
                        <select data-fn="<%=ln%>" data-ft="_enum" class="value form-control">
                            <option value=""><%=_locale.translate("fore.form.select")%></option>
                        </select>
                    </div>
                    <%} /*End for*/%>
                </div>
            </div>
            <div class="form-group">
                <div class="btn-toolbar">
                    <div class="btn-group">
                        <button type="button" class="btn btn-default" data-sift="lr" data-name="or" data-text="或: 满足此组内任意条件">+ 或</button>
                        <button type="button" class="btn btn-default" data-sift="lr" data-name="ar" data-text="与: 满足此组内所有条件">+ 与</button>
                        <button type="button" class="btn btn-default" data-sift="lr" data-name="nr" data-text="非: 排除此组内所有条件">+ 非</button>
                    </div>
                    <div class="btn-group pull-right form-control-static">
                        <a href="javascript:;" onclick="alert(hsSiftParamString($(this).closest('.HsSift')))">参数</a>
                        <a href="javascript:;" onclick="alert(hsSiftQueryString($(this).closest('.HsSift')))">配置</a>
                        <a href="javascript:;" onclick="alert(hsSiftHumanString($(this).closest('.HsSift')))">说人话</a>
                    </div>
                </div>
            </div>
            <div class="alert alert-warning alert-dismissible" role="alert">
                <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>
                <p>
                    <i class="bi bi-arrow-up-circle-fill"></i>
                    <strong>高级查询：</strong>
                    <span>请选择字段、关系、取值，这将添加一条筛查参数；</span>
                    <span>“与”、“或”、“非”，表示分组内各条间关系；</span>
                    <span>点击分组标题可激活分组，新设置的的将放入其下；</span>
                    <span>如需变更筛查条件的分组，按住条目拖拽过去即可；</span>
                    <span>点击“&times;”删除条目或分组。</span>
                </p>
                <p>
                    <i class="bi bi-arrow-right-circle-fill"></i>
                    <strong>查询助理：</strong>
                    <span>可让 AI 辅助设置查询条件，只需告诉 AI 你要查什么即可；</span>
                    <span>可与 AI 对话变更查询条件，也可以在 AI 基础上自行调整。</span>
                </p>
            </div>
        </div>
        <div class="col-xs-6 rollbox sift-chat">
            <form id="query-agent-chat-form" onsubmit="return false">
                <div class="form-group">
                    <label  class="sift-hand form-label control-label">
                        查询助理
                    </label>
                    <div class="form-control multiple">
                        <ul class="chatbox">
                        </ul>
                    </div>
                </div>
                <div class="form-group">
                    <div class="input-group">
                        <input name="message" type="text" class="form-control" placeholder="请输入查询需求..."/>
                        <span class="input-group-btn">
                            <button type="submit" class="btn btn-default"><i class="bi bi-send"></i></button>
                        </span>
                    </div>
                </div>
            </form>
        </div>
    </div>
    <hr style="margin-top: 0; border-color: #ccc;"/>
    <form class="findbox">
        <div class="form-foot">
            <div class="form-group board">
                <div  class="btn-toolbar">
                    <button type="submit" class="btn btn-primary">查找</button>
                    <button type="reset"  class="btn btn-default">重置</button>
                    <div class="btn-group pull-right form-control-static checkbox" style="margin: 0;">
                        <label><input type="checkbox" name="ob" value="-"> 按匹配度排序</label>
                    </div>
                </div>
            </div>
        </div>
    </form>
</div>
<script type="text/javascript">
    (function($) {
        var chatForm = H$("#query-agent-chat-form");
        var chatBox  = chatForm.find( ".chatbox"  );
        var chatInp  = chatForm.find("[name=message]");
        var chatBtn  = chatForm.find("[type=submit]" );

        // 重置查询时也把消息清空
        var findBox  = chatForm.closest(".search-list,.select-list").find(".findbox");
        if (findBox && findBox.size()) {
            findBox.on("reset", function() {
                chatBox.empty();
            });
        }

        function getMessages() {
            var messages = [];
            chatBox.find(".chat-item").each(function() {
                if (!$(this).data("role")) {
                    return ;
                }
                messages.push({
                    role   : $(this).data("role"   ),
                    content: $(this).data("content")
                });
            });
            return messages;
        }

        function addMessage(message, content, role) {
            var item = $('<li class="chat-item chat-'+role+'"></li>');
            item.data("role"    , role   );
            item.data("content" , content);
            item.text( message );
            chatBox.append(item);
        }

        function send() {
            var siftObj  = H$("@HsSift" , chatForm);
            var messages = getMessages();
            var message  = chatInp.val();
            var queries  = siftObj.dump( );
            var content  = message + "\n\n```json\n" + JSON.stringify(queries) + "\n```";

            // 插入消息
            addMessage(message, content, "user");

            // 回复前不可发送
            chatBtn.prop("disabled", true);
            chatInp.prop("disabled", true);
            chatInp.val ("AI 解析中, 请稍等...");

            // 发送请求, 交给 AI 处理
            $.hsAjax({
                url : "<%=_module%>/<%=_entity%>/refind<%=Cnst.ACT_EXT%>",
                type: "post",
                data: {
                    messages: messages,
                    content : content
                },
                cache   : false ,
                global  : false ,
                dataKind: "json",
                dataType: "json",
                complete: function(rst) {
                    // 恢复可发送状态
                    chatBtn.prop("disabled", false);
                    chatInp.prop("disabled", false);
                    chatInp.val ("");

                    var content = rst.responseText;
                    var message = content;
                    var queries = "[]";
                    
                    // 拆解消息
                    var mq = /^(.*?)\n```json\n(.*?)\n```$/s.exec(content);
                    if (mq) {
                        message = $.trim( mq[1] );
                        queries = $.trim( mq[2] );
                    }
                    queries = JSON.parse(queries);

                    // 插入回复
                    addMessage(message, content, "assistant");

                    // 填充配置
                    siftObj.fill(queries);
                }
            });
        }

        chatInp.on("keypress", function(ev) {
            if (ev.keyCode == 13) {
                send();
                ev.stopPropagation();
                ev.preventDefault ();
                return false;
            }
        });
        chatBtn.click(function(ev) {
            send();
            ev.stopPropagation();
            ev.preventDefault ();
            return false;
        });
    })(jQuery);
</script>
<style type="text/css">
    ul.chatbox {
        list-style: none;
        margin-bottom: 0;
        padding: 0 10px;
    }
    ul.chatbox > li {
        margin-bottom: 8px;
    }
    ul.chatbox > li:last-child {
        margin-bottom: 0px;
    }
    ul.chatbox > li:before {
        margin-right : 8px;
        display: inline-block;
        font-family: bootstrap-icons !important;
        font-weight: normal !important;
        font-variant: normal;
        text-transform: none;
        line-height: 1;
        vertical-align: -.12em;
        -webkit-font-smoothing: antialiased;
        -moz-osx-font-smoothing: grayscale;
    }
    ul.chatbox > li.chat-assistant:before {
        content: "\f6b1";
    }
    ul.chatbox > li.chat-user:before {
        content: "\f4d7";
    }
    ul.chatbox > li.chat-user{
        font-weight: bold;
    }
</style>