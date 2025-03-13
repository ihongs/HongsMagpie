
function in_centra_data_magpie_assistant_test(context, formobj) {
    var msgs = [];
    var that = formobj;
    var mbox = context.find(".msgs-list");
    var uinp = context.find('[name=prompt]');
    context.find('[name=cleans]').click(function() {
        msgs = [];
        mbox.empty();
    });
    context.find('[name=submit]').click(function() {
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

        var umsg = uinp.val();
        var amsg = ""  ;

        var data = hsSerialDat(that.formBox);
        data.messages = msgs;
        data.prompt   = umsg;

        fetch(hsFixUri("centra/data/magpie/assistant-message/censor.act"), {
            body    : JSON.stringify(data),
            method  : "POST",
            headers : {
                "Accept": "text/plain;q=0.9,*/*;q=0.8",
                "Content-Type": "application/json"
            }
        })
        .then(rsp => {
            if (! rsp.ok) {
                throw new Error("网络错误, 请检查网络后重试...");
            }

            var unam = '我';
            var anam = context.find('[name=model]>:selected').text();
            var ubox = $('<div><b>'+unam+':</b><div class="alert"></div></div>').appendTo(mbox).find('div');
            var abox = $('<div><b>'+anam+':</b><div class="alert"></div></div>').appendTo(mbox).find('div');

            // 用户消息
            msgs.push({
                role    : "user",
                content :  umsg
            });
            ubox.text(umsg);
            uinp.val ( "" );

            const reader  = rsp.body.getReader( );
            const decoder = new TextDecoder("utf-8");

            function read() {
                reader.read().then(({done, value}) => {
                    if (done) {
                        // 助理消息
                        msgs.push({
                            role    : "assistant",
                            content :  amsg||"无"
                        });
                        delete that._waiting;
                        return;
                    }

                    const chunk = decoder.decode(value, {stream: true});
                    amsg  +=  chunk;
                    abox.text(amsg);

                    read();
                });
            }
            read();
        })
        .catch(error => {
            that.warn(error, "danger");
            delete that._waiting;
        });
    });
}
