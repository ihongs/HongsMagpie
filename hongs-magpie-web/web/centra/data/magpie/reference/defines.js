
function in_centra_data_magpie_reference_form(context, formObj) {
    formObj.formBox.on("change", "ul[data-fn=file]", function() {
        var inp = $(this).find("input[type=file]");
        if (inp.size() == 0) {
            return;
        }

        var file = inp[0].files[0];
        var data = new FormData( );
        data.append("file" , file);
        $.hsAjax({
            url : "centra/data/magpie/reference/upload.act",
            data: data,
            type: "post",
            dataKind: "part",
            xhr : $.hsXhup("文档上传中...", function(evt) {
                if ("init" == evt) {
                    $(this).find(".alert-title").text("文档上传中...")
                } else
                if ("over" == evt) {
                    $(this).find(".alert-title").text("文档解析中...")
                }
            }),
            success : function  (rst) {
                rst = hsResponse(rst);
                if (! rst.ok) return ;

                // 已上传到临时文件, 不用再上传
                var nnp = $('<input type="hidden"/>');
                nnp.attr("name", inp.attr("name"));
                nnp.val (rst.info.name);
                inp.before(nnp);
                inp.remove();

                var exp = context.find("textarea[name=text]");
                if (! $.trim(rst.info.text)) {
                    $.hsWarn("文档解析完成, 但未能提取到内容!", "warning",
                        function() {
                        }
                    );
                } else
                if (  $.trim(exp.val(  ))  ) {
                    $.hsWarn("文档解析完成, 是否覆盖旧的内容?", "warning",
                        function() {
                            exp.val(rst.info.text);
                        },
                        function() {
                        }
                    );
                } else {
                    $.hsNote("文档解析完成, 将写入内容.", "success",
                        function() {
                            exp.val(rst.info.text);
                        }
                    );
                }
            }
        });
    });
}
