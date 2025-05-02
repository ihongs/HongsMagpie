package io.github.ihongs.serv.magpie.tpl;

import com.jfinal.template.Engine;
import io.github.ihongs.Core;
import io.github.ihongs.CoreConfig;
import java.io.Writer;
import java.util.Map;

/**
 * 模板工具
 * @author Hongs
 */
public class TplEngine {

    protected final Engine engine;

    protected TplEngine () {
        String tp = Core.CONF_PATH + "/magpie/temp";
        Object tk = new TplMethod ();
        engine    = new Engine ()
            .setBaseTemplatePath(tp)
                .addSharedMethod(tk);
    }

    public static TplEngine getInstance() {
        Core core = Core.getInstance();
        String rn = TplEngine.class.getName();
        String cn = CoreConfig.getInstance("magpie")
                              .getProperty("magpie.rander.class");
        if (null != cn && ! rn.equals(cn)) {
            return (TplEngine) core.got( cn );
        }
        return core.got(rn, () -> new TplEngine());
    }

    public String render(String name, Map info) {
        return engine.getTemplate(name)
                     .renderToString(info);
    }

    public  void  render(String name, Map info, Writer out) {
        engine.getTemplate(name)
              .render(info, out);
    }

    public String renderByString(String text, Map info) {
        return engine.getTemplateByString(text)
                     .renderToString(info);
    }

    public  void  renderByString(String text, Map info, Writer out) {
        engine.getTemplateByString(text)
              .render(info, out);
    }

}
