
function in_centra_data_magpie_assistant_info(context, listobj) {
    var btng = context.find('.form-foot .btn-group');
}

function in_centra_data_magpie_assistant_test(context, formobj) {
    var ssid ;
    var msgs = [];
    var that = formobj;
    var mbox = context.find(".msgs-list");
    var uinp = context.find('[name=prompt]');
    context.find('[name=cleans]').click(function() {
        msgs.length = 0;
        mbox.empty( );
    });
    context.find('[name=cancel]').click(function() {
        if (! ssid) {
            return;
        }
        fetch(hsFixUri("centra/data/magpie/assistant-message/cancel.act?session_id="+ssid));
    });
    context.find('.form-foot>form').on("submit", function() {
        // 校验
        if (! that.validate()) {
            that.note("请检查错误项, 修改后重试", "danger");
            return;
        }
        if (! uinp.val() ) {
            that.note("请先输入消息", "warning");
            return;
        }
        if (that._waiting) {
            that.note("处理中请稍等", "warning");
            return ;
        }
        that._waiting = true;

        var data = hsSerialDat(that.formBox);
        var umsg = uinp.val();
        data.messages = msgs;
        data.prompt   = umsg;
        data.session_id = ssid;

        fetch(hsFixUri("centra/data/magpie/assistant-message/aerate.act?stream=2"), {
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

            var unam = '我';
            var anam = context.find('[name=model]>:selected').text();
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

            const  evts = new EventSource(hsFixUri('centra/data/magpie/assistant-message/stream.act?session_id='+ssid));
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
                        var a = $('<li><a href="javascript:;" data-toggle="hsOpen" data-target="@"></a></li>')
                            .appendTo(ul)
                            .find( 'a'  );
                        a.attr("data-href", 'centra/data/magpie/reference/info.html?id=' + m.id);
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
