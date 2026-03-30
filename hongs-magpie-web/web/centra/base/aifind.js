function hsAiFind( chatForm, url ) {
    var chatList = chatForm.find(".chat-list");
    var chatInp  = chatForm.find("[name=message]");
    var chatBtn  = chatForm.find("[type=submit]" );

    // 重置查询时也把消息清空
    var findBox  = chatForm.closest(".search-list,.select-list").find(".findbox");
    if (findBox && findBox.size()) {
        findBox.on("reset", function() {
            chatList.empty();
        });
    }

    function getMessages() {
        var messages = [];
        chatList.find(".chat-item").each(function() {
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
        item.appendTo( chatList );
    }

    function send() {
        var siftObj  = H$("@HsSift", chatForm);
        var messages = getMessages ( );
        var message  = chatInp.val ( );
        var queries  = siftObj.dump( );
        var content  = message + "\n\n```json\n" + JSON.stringify(queries) + "\n```";

        // 插入消息
        addMessage(message, content, "user");

        // 回复前不可发送
        chatBtn.prop("disabled", true);
        chatInp.prop("disabled", true);
        chatInp.val ("AI 处理中, 请稍等...");

        // 发送请求, 交给 AI 处理
        $.hsAjax({
            url :  url  ,
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

                console.log(rst);

                var content, message, queries;

                if (rst.responseJSON) {
                    if (! rst.responseJSON.ok) {
                        content = rst.responseJSON.msg
                               || rst.responseJSON.err
                               || "未知错误";
                    } else {
                        content = rst.responseJSON.content
                               || "未知消息";
                    }
                } else {
                    content = rst.responseText;
                }

                // 拆解消息
                var mq = /^(.*?)\n```json\n(.*?)\n```$/s.exec(content);
                if (mq) {
                    message = $.trim(mq[1]);
                    queries = $.trim(mq[2]);
                    queries = JSON.parse(queries);
                } else {
                    message = content;
                    console.warn( "No queries", content );
                }

                // 插入回复
                addMessage(message, content, "assistant");

                // 填充配置
                if (queries) {
                    siftObj.fill(queries);
                    $.hsNote("已设置查询", "success");
                }
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
}