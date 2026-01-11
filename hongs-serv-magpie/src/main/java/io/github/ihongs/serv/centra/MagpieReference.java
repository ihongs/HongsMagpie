package io.github.ihongs.serv.centra;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CoreConfig;
import io.github.ihongs.CruxException;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.action.FormSet;
import io.github.ihongs.action.UploadHelper;
import io.github.ihongs.action.anno.Action;
import io.github.ihongs.util.Dict;
import io.github.ihongs.util.Synt;
import io.github.ihongs.util.verify.Wrong;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import javax.servlet.http.Part;

/**
 * 文档补充接口
 * @author Hongs
 */
@Action("centra/data/magpie/reference")
public class MagpieReference {

    @Action("upload")
    public void upload(ActionHelper helper) throws Wrong, CruxException {
        String uid  = Synt.declare( helper.getSessibute(Cnst.UID_SES), "0" );
        String nid  = Core.newIdentity();

        Object item = helper.getRequestData().get("file");
        if (item == null) {
            helper.reply(Synt.mapOf(
                "ok" , false,
                "ern", "Er400",
                "err", "file required!"
            ));
            return;
        }
        if (! (item instanceof Part)) {
            helper.reply(Synt.mapOf(
                "ok" , false,
                "ern", "Er400",
                "err", "file required?"
            ));
            return;
        }

        Set accept = Synt.toSet(Dict.getWorth(FormSet.getInstance("centra/data/magpie").getForm("reference"), "file", "accept"));

        UploadHelper uh = new UploadHelper( );
        uh.setUploadPath("static/upload/tmp");
        uh.setUploadHref("static/upload/tmp");
        uh.setAccept(accept);

        Part   part = (Part) item;
        String name = uid +"-"+ nid +".magpie-ref-doc.";
        File   file = uh.upload(part , name);
        String href = uh.getResultHref(/**/);
        String link = Core.SERVER_HREF.get()
                    + Core.SERVER_PATH.get()
                    + "/" + href;
        name = file.getName( ) + "|" + part.getSubmittedFileName();

        String text;
        String extn = file.getName( );
        int p = extn.lastIndexOf(".");
        extn  = extn.substring(p + 0);
        String clsn = CoreConfig.getInstance("magpie").getProperty("document.parser"+extn);
        if (clsn == null) {
            helper.reply(Synt.mapOf(
                "ok" , false,
                "ern", "Er400",
                "err", "Unsupported file type: " + extn
            ));
            return;
        }

        // 解析文档, 提取内容
         DocumentParser dp = (DocumentParser) Core.newInstance(clsn);
        try (
            InputStream ip = new FileInputStream(file)
        ) {
            Document dc = dp.parse(ip);
            text = dc.text();
        }
        catch (BlankDocumentException ex) {
            text = "";
        }
        catch (IOException ex) {
            helper.reply(Synt.mapOf(
                "ok" , false,
                "ern", "Er400",
                "err", "Can not read file. " + ex.getMessage()
            ));
            return;
        }

        helper.reply(Synt.mapOf(
            "info" , Synt.mapOf(
                "name", name,
                "href", href,
                "link", link,
                "text", text
            )
        ));
    }

}
