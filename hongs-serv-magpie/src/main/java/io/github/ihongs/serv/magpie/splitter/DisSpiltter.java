package io.github.ihongs.serv.magpie.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;

/**
 * 占位文档拆分器
 *
 * 不作拆分, 总是返回空列表
 */
public class DisSpiltter implements DocumentSplitter {

    @Override
    public List<TextSegment> split(Document dcmnt) {
        return new ArrayList(0);
    }

}
