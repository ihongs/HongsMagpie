package io.github.ihongs.serv.magpie.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import io.github.ihongs.CoreConfig;
import java.util.List;

/**
 * 默认文档拆分器
 *
 * magpie 配置项:
 * type.splitter.max-segment-size=分块大小
 * type.splitter.max-overlay-size=交叠大小
 * type 为拆分器分类名称, 默认 default
 */
public class DefSpliter implements DocumentSplitter {

    private final DocumentSplitter that;

    public DefSpliter (String type) {
        CoreConfig cc = CoreConfig.getInstance("magpie");
        int maxSegmentSize = cc.getProperty(type+".splitter.max-segment-size", 1000);
        int maxOverlaySize = cc.getProperty(type+".splitter.max-overlay-size", 100 );
        that = DocumentSplitters.recursive (maxSegmentSize, maxOverlaySize);
    }

    @Override
    public List<TextSegment> split(Document docu) {
        return that.split(docu);
    }

}
