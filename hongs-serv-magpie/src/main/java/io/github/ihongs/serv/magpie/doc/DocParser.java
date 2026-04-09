package io.github.ihongs.serv.magpie.doc;

import dev.langchain4j.data.document.Document;
import java.io.File;

/**
 * 文档解析器
 * @author Hongs
 */
public interface DocParser {
    
    /**
     * 解析文档
     * @param file
     * @return
     */
    public Document parse(File file);
    
}
