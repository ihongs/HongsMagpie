
function in_centra_data_magpie_applicant_list(context, listobj) {
    context.find('.toolbox .recite').after(
        '<button type="button" class="stream for-choose btn btn-default">过程</button>'
    );
    context.find('.listbox thead .recite').parent().after(
        '<li><a href="javascript:;" class="stream">过程</a></li>'
    );
    context.on("click", ".stream", function(evt) {
        var btn = $(this);
        var id  = listobj.getIds(this).val();
        listobj.open(btn , "@", 'centra/data/magpie/applicant/task.html', {id: id});
    });

    context.on("saveBack", ".create,.update", function(evt, rst, dat) {
    setTimeout(function() {
        if (! rst.ok) return;
        var btn = context.find('.recite');
        var id  = hsGetSeria( rst, 'id' )
               || hsGetSeria( dat, 'id' );
        listobj.open(btn , '@', 'centra/data/magpie/applicant/task.html', {id: id});
    } , 0);
    });
}

function in_centra_data_magpie_applicant_form(context, formobj) {
    formobj.formBox
        .append ('<input type="hidden" name="state" value="1"/>');

    context.find( "button.submit" )
        .removeClass('btn-primary')
           .addClass('btn-danger' )
           .text("执行");
}

function in_centra_data_magpie_applicant_task(context) {
    var loadbox = context.closest(".loadbox");
    var rollbox = context.find(".rollbox");
    var taskbox = context.find('.taskbox');
    var id = H$('?id', loadbox);

    rollbox.scrollIntoView = function () {
        rollbox.scrollTop(rollbox.prop("scrollHeight"));
    };

    const evts = new EventSource(hsFixUri('centra/data/magpie/applicant/stream.act?id='+id));
    evts.onmessage = function(ev) {
        var data = JSON.parse(ev.data);
        var text = data.text ;
        var ma ;
        if (text) {
            // 终止
            if (text === '__DONE__'
            ||  text === '__STOP__') {
                evts.close();
                return;
            }
            // 等待
            ma = /WAITING (\d+) RUNNING (\d+)/.exec(text);
            if (ma) {
                if (ma[1] === '0' && ma[2] === '0') {
                    taskbox.append($('<p></p>').text("即将执行, 请稍等..."));
                } else {
                    taskbox.append($('<p></p>').text("等待执行, 前面有 "+ma[1]+" 个任务, "+ma[2]+" 个任务正在执行"));
                }
                rollbox.scrollIntoView();
                return;
            }
            // 步骤
            ma = /INFO\s+Planning Analysis:\s*(?:```\w*)?(\{.*\})(?:```)?/.exec(text);
            if (ma) {
                var info = JSON.parse(ma[1]);
                if (info['progress_evaluation']) {
                    taskbox.append($('<p></p>').text("进度: " + info['progress_evaluation']));
                }
                if (info['state_analysis']) {
                    taskbox.append($('<p></p>').text("状态: " + info['state_analysis']));
                }
                if (info['reasoning']) {
                    taskbox.append($('<p></p>').text("推理: " + info['reasoning']));
                }
                if (info['challenges'] && info['challenges'].length) {
                    taskbox.append($('<p></p>').text("问题: " + info['challenges'].join('; ')));
                }
                if (info['next_steps'] && info['next_steps'].length) {
                    taskbox.append($('<p></p>').text("后续: " + info['next_steps'].join('; ')));
                }
                rollbox.scrollIntoView();
                return;
            }
            // 其他
            taskbox.append($('<p></p>').text(text));
            rollbox.scrollIntoView();
        } else {
            console.log(data);
        }
    };
    evts.onerror = function(ev) {
        evts.close (  );
        console.log(ev);
    };
}
