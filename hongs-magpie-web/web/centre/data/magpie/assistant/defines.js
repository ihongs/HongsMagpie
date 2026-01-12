
function in_centre_data_magpie_assistant_talk(context) {
    var tok = H$('&t' );
    var aid = H$('&id');
    if (! aid) {
        $.hsMask({
            mode : "warn",
            glass: "alert-danger",
            title: "参数错误"
        });
        return;
    }

    var anam ;
    var unam ;
    var ssid ;
    var that = {
        note: $.hsNote,
        warn: $.hsWarn
    };
    var msgs = [];
    var mbox = context.find(".msgs-list");
    var uinp = context.find('[name=prompt]');

    $.hsAjax({
        url : hsFixUri("centre/data/magpie/assistant-message/search.act"),
        data: {
            token: tok,
            assistant_id: aid,
            ab: [ "assistant", "user", "no-list" ]
        },
        cache : false,
        async : false,
        global: false,
        type    : "post",
        dataType: "json",
        dataKind: "json",
        complete: function(rst) {
            rst = hsResponse(rst, 3);
            if (! rst.ok) {
                $.hsMask( {
                    mode : "warn",
                    glass: "alert-danger",
                    title: rst.msg || rst.err || "未知错误",
                    backdrop: "static",
                    closable: false,
                    keyboard: false
                }).find(".close").remove();
                return;
            }

            anam = rst.assistant.name;
            unam = rst.user.name || "我";

            if (rst.assistant.regard) {
                $('<div><b>'+anam+':</b><div class="alert"></div></div>')
                    .appendTo(mbox).find('div')
                    .text(rst.assistant.regard);
            }

            context.find('[name=cleans]').click(function() {
                msgs.length = 0;
                mbox.empty( );
            });
            context.find('[name=cancel]').click(function() {
                if (! ssid) {
                    return;
                }
                fetch(hsFixUri("centre/data/magpie/assistant-message/cancel.act?session_id="+ssid));
            });
            context.find('.form-foot>form').on("submit", function() {
                // 校验
                if (! uinp.val() ) {
                    that.note("请先输入消息", "warning");
                    return;
                }
                if (that._waiting) {
                    that.note("处理中请稍等", "warning");
                    return ;
                }
                that._waiting = true;

                var data = {};
                var umsg = uinp.val();
                data.messages = msgs;
                data.prompt   = umsg;
                data.token    =  tok;
                data.assistant_id = aid;
                data.session_id =  ssid;

                fetch(hsFixUri("centre/data/magpie/assistant-message/aerate.act?stream=2"), {
                    body    : JSON.stringify(data),
                    method  : "POST",
                    headers : {
                        "Accept": "application/json",
                        "Content-Type": "application/json"
                    }
                })
                .then(rsp => {
                    if (! rsp.ok) {
                        throw new Error("网络错误, 请检查网络后重试...");
                    }
                    return rsp.json();
                })
                .then(rsp => {
                    if (! rsp.ok) {
                        throw new Error(rsp.msg || rsp.err || "未知错误");
                    }

                    ssid = rsp.session_id;

                    var ubox = $('<div><b>'+unam+':</b><div class="alert"></div></div>').appendTo(mbox).find('div');
                    var abox = $('<div><b>'+anam+':</b><div class="alert"></div></div>').appendTo(mbox).find('div');
                    var umap = {
                        role    : "user",
                        content :  umsg
                    };
                    var amap = {
                        role    : "assistant",
                        content : ""
                    };

                    msgs.push(umap);
                    msgs.push(amap);
                    ubox.text(umsg);
                    uinp.val ( "" );

                    const  evts = new EventSource(hsFixUri('centre/data/magpie/assistant-message/stream.act?session_id='+ssid));
                    evts.onmessage = function(ev) {
                        var dat = JSON.parse((ev.data));
                        if (dat .content) {
                            amap.content  = dat.content;
                            abox.text (amap.content);
                        } else
                        if (dat .text) {
                            amap.content += dat.text;
                            abox.text (amap.content);
                            //context.find('[name=cancel]').click();
                        } else
                        if (dat.references && dat.references.length) {
                            var ul = $('<blockquote class="small"><i>引用资料</i><ul></ul></blockquote>')
                                    .insertAfter(abox)
                                    .find( 'ul' );
                            for(var i = 0; i < dat.references.length; i ++) {
                                var m = dat.references[i];
                                var a = $('<li><a href="javascript:;"></a></li>')
                                    .appendTo(ul)
                                    .find( 'a'  );
                                a.attr("data-href", 'centre/data/magpie/reference/info.html?id=' + m.id);
                                a.text( m.name  );
                            }
                        }
                    };
                    evts.onerror = function(ev) {
                        evts.close ();
                        console.log(ev);
                        ssid =  undefined;
                        delete  that._waiting;
                    };
                })
                .catch(err => {
                    that.warn(err.message, "danger");
                    delete  that._waiting;
                });
            });
        }
    });
}
