package io.github.ihongs.serv.magpie.tpl;

import com.jfinal.template.Engine;
import com.jfinal.template.Template;
import io.github.ihongs.Core;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Map;

/**
 * 模板工具
 * @author Hongs
 */
public class TplEngine {

    protected final Engine engine;

    protected TplEngine () {
        engine = new Engine()
            .setBaseTemplatePath(Core.CONF_PATH+"/magpie/temp")
            .addDirective("f", TplFigure.class, false)
            .addSharedMethod(new TplMethod());
    }

    public static TplEngine getInstance() {
        return Core.getInstance().got(TplEngine.class.getName(), () -> new TplEngine());
    }

    public Temp getTemplate (String name) {
        return new Temp (engine.getTemplate(name));
    }

    public String render(String name, Map info) {
        return engine.getTemplate(name)
                  .renderToString(info);
    }

    public  void  render(String name, Map info, Writer out) {
        engine.getTemplate(name)
              .render(info, out);
    }

    public  void  render(String name, Map info, OutputStream out) {
        engine.getTemplate(name)
              .render(info, out);
    }

    public static class Temp {

        private final Template that;

        private Temp (Template tpl) {
            this.that = tpl;
        }

        public String render(String name, Map info) {
            return that.renderToString(info);
        }

        public  void  render(String name, Map info, Writer out) {
            that.render(info, out);
        }

        public  void  render(String name, Map info, OutputStream out) {
            that.render(info, out);
        }

    }

}
