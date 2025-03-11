
function in_centra_data_magpie_assistant_test(context, formobj) {
    var msgs = [];
    var uinp = context.find('[name=prompt]');
    var usub = context.find('[name=submit]');
    var that = formobj;
    usub.click(function() {
        // 校验
        if (!that.validate()) {
            return;
        }
        if (!uinp.val()) {
            that.note("请先输入消息", "warning");
            return;
        }
        if (that._waiting) {
            that.note("处理中请稍等", "warning");
            return ;
        }
        that._waiting = true;

        var unam = '我';
        var anam = context.find('[name=model]>:selected').text();
        var ubox = $('<div><b>'+unam+':</b><div class="alert"></div></div>').appendTo(context.find(".msgs-list")).find('div');
        var abox = $('<div><b>'+anam+':</b><div class="alert"></div></div>').appendTo(context.find(".msgs-list")).find('div');
        var umsg = uinp.val();
        var amsg = ""  ;

        uinp.val ( "" );
        ubox.text(umsg);

        // 登记用户消息
        msgs.push({
            role    : "user",
            content :  umsg
        });

        var data = hsSerialDat(that.formBox);
        data.messages = msgs;

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

            const reader  = rsp.body.getReader();
            const decoder = new TextDecoder("utf-8");

            var msg = "";
            function read() {
                reader.read().then(({done, value}) => {
                    if (done) return;

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
        })
        .finally(() => {
            // 登记助理消息
            msgs.push({
                role    : "assistant",
                content :  amsg||"无"
            });
            delete that._waiting;
        });
    });
}
