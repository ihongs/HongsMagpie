package io.github.ihongs.serv.magpie.parser;

import dev.langchain4j.data.document.Document;
import io.github.ihongs.CoreConfig;
import io.github.ihongs.CruxException;
import io.github.ihongs.util.Remote;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 调网络服务解析
 * 
 * 配置(magpie):
 * document.parse.url=http://SERVER:PORT/PATH|AUTH
 *
 * @author Hongs
 */
public class NetParser implements DocParser {

    @Override
    public Document parse(File file) {
        String  url = CoreConfig.getInstance("magpie").getProperty("document.parse.url");
        String  aut = null;
        int p = url.lastIndexOf('|');
        if (p > 0) {
            aut = url.substring(1+p);
            url = url.substring(0,p);
        }

        Map data = new HashMap();
        data.put( "file", file );

        Map head = new HashMap();
        head.put("Accept", "text/plain");
        head.put("Accept-Charset", "utf-8");
        if (aut != null) {
            head.put("Authorization", "Bearer "+aut);
        }

        String text;
        try {
            text = Remote.request(Remote.METHOD.POST, Remote.FORMAT.PART, url, data, head);
        } catch (CruxException ex) {
            throw ex.toExemption();
        }

        return Document.from(text);
    }

}
