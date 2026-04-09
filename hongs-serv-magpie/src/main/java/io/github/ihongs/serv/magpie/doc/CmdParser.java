package io.github.ihongs.serv.magpie.doc;

import dev.langchain4j.data.document.Document;
import io.github.ihongs.Core;
import io.github.ihongs.CoreConfig;
import io.github.ihongs.CruxExemption;
import io.github.ihongs.util.Syno;
import io.github.ihongs.util.Synt;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 调本地命令解析
 *
 * 配置(magpie):
 * document.parse.cmd=/PATH/TO/SCRIPT
 * 命令需支持一个参数, 参数为代解析的文件路径
 *
 * @author Hongs
 */
public class CmdParser implements DocParser {

    @Override
    public Document parse(File file) {
        String cmd = CoreConfig.getInstance("magpie").getProperty("document.parse.cmd");
        cmd = Syno.inject(cmd, Synt.mapOf(
            "CORE_PATH", Core.CORE_PATH
        ));

        try {
            StringBuilder  sb = new StringBuilder ( );
            ProcessBuilder pb = new ProcessBuilder(cmd, file.getAbsolutePath());
            Process process = pb.start();

            sb.setLength(0);
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            ) ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            String text = sb.toString().strip( );

            sb.setLength(0);
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)
            ) ) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            String errs = sb.toString().strip( );

            int exit  = process.waitFor(/**/);
            if (exit != 0) {
                throw new CruxExemption(errs);
            }

            return Document.from(text);
        } catch (IOException | InterruptedException e) {
            throw new CruxExemption(e);
        }
    }

}
