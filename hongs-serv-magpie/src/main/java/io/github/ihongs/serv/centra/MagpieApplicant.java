package io.github.ihongs.serv.centra;

import io.github.ihongs.Cnst;
import io.github.ihongs.Core;
import io.github.ihongs.CoreConfig;
import io.github.ihongs.CoreLocale;
import io.github.ihongs.CoreLogger;
import io.github.ihongs.CruxException;
import io.github.ihongs.CruxExemption;
import io.github.ihongs.action.ActionHelper;
import io.github.ihongs.action.anno.Action;
import io.github.ihongs.action.anno.CommitSuccess;
import io.github.ihongs.action.anno.Preset;
import io.github.ihongs.action.anno.Verify;
import io.github.ihongs.serv.matrix.Data;
import io.github.ihongs.util.Dist;
import io.github.ihongs.util.Syno;
import io.github.ihongs.util.Synt;
import io.github.ihongs.util.daemon.Gate;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.servlet.http.HttpServletResponse;

/**
 * 智能体接口
 * @author Hongs
 */
@Action("centra/data/magpie/applicant")
public class MagpieApplicant {

    private static final Map<String, List<Consumer<String>>> LOGS = new HashMap();

    @Action("update")
    @Preset(conf="centra/data/magpie", form="applicant")
    @Verify(conf="centra/data/magpie", form="applicant")
    @CommitSuccess
    public void update(ActionHelper helper) throws CruxException {
        Data    sr = Data.getInstance("centra/data/magpie", "applicant");
        Map     rd = helper.getRequestData();
        Set     ab = Synt.toTerms(rd.get(Cnst.AB_KEY));

        // 执行中的任务不可修改
        if (ab == null || ! ab.contains("force")) {
            Set ids = Synt.asSet(rd.get(Cnst.ID_KEY));
            Map row = sr.getOne(Synt.mapOf(
                Cnst.RB_KEY, Synt.setOf(Cnst.ID_KEY),
                Cnst.ID_KEY, Synt.mapOf(Cnst.IN_REL , ids),
                "state"    , Synt.mapOf(Cnst.EQ_REL , 0x2) // 执行中
            ));
            if (row != null && !row.isEmpty()) {
                helper.fault("执行中的任务不可修改");
                return;
            }
        }

        int     sn = sr.update(rd);
        String  ss = CoreLocale.getInstance().translate("fore.update.success", sn);
        helper.reply(ss, sn);
    }

    @Action("accept")
    public void accept(ActionHelper helper) throws CruxException {
    synchronized (LOGS) {
        Data ent = Data.getInstance("centra/data/magpie", "applicant");
        Map  inf = ent.getOne(Synt.mapOf(
            Cnst.OB_KEY, Synt.setOf("mtime", "ctime", Cnst.ID_KEY),
            "state", Synt.mapOf(Cnst.EQ_REL, 1)
        ));
        if (inf == null || inf.isEmpty()) {
            helper.reply("No task");
            return;
        }

        // 状态改为执行
        String aid = (String) helper.getParameter("agent_id");
        String  id = (String) inf.get(Cnst.ID_KEY);
        long    tm =  System.currentTimeMillis(  );
        int     st =  2;
        Map dat = new HashMap();
        dat.put("agent", aid);
        dat.put("btime", tm);
        dat.put("state", st);
        ent.set(id, dat, 0 );

        // 大模型配置项
        Map cnf = new HashMap (6);
        String api, mod, url, key;
        CoreConfig conf = CoreConfig.getInstance("magpie");
        api = conf.getProperty("magpie.ai.agent.api", "default");
        mod = conf.getProperty("magpie.ai.agent.mod", "test");
        url = conf.getProperty("magpie.ai."+api+".url");
        key = conf.getProperty("magpie.ai."+api+".key");
        cnf.put(  "agent_mod", mod);
        cnf.put(  "agent_url", url);
        cnf.put(  "agent_key", key);
        api = conf.getProperty("magpie.ai.planner.api" , api);
        mod = conf.getProperty("magpie.ai.planner.mod" , mod);
        url = conf.getProperty("magpie.ai."+api+".url");
        key = conf.getProperty("magpie.ai."+api+".key");
        cnf.put("planner_mod", mod);
        cnf.put("planner_url", url);
        cnf.put("planner_key", key);

        // 删除旧的日志(可能重新执行任务)
        String fn = System.getProperty("logs.dir", Core.DATA_PATH+"/log")
                  + "/magpie-agent-task/" + Syno.splitPath (id) + ".log";
        File fo = new File(fn);
        if ( fo.exists()) {
             fo.delete();
        }

        helper.reply(Synt.mapOf(
            "info", inf,
            "conf", cnf
        ));
    } }

    @Action("result")
    @Verify(conf="centra/data/magpie", form="applicant")
    public void result(ActionHelper helper) throws CruxException {
        Map rd = helper.getRequestData();
        String id = Synt.asString(rd.get(Cnst.ID_KEY));
        int    st = Synt.declare (rd.get("state") , 3);
        long   tm = System.currentTimeMillis();
        rd.put("state", st);
        rd.put("etime", tm);

        Data  mod = Data.getInstance("centra/data/magpie", "applicant");
        mod.set(id, rd, 0 );

        helper.reply("");
    }

    @Action("reflow")
    public void reflow(ActionHelper helper) throws CruxException {
        Map rd = helper.getRequestData();
        String tx = Synt.asString(rd.get("text"));
        String id = Synt.asString(rd.get(Cnst.ID_KEY));
        String fn = System.getProperty("logs.dir", Core.DATA_PATH+"/log")
                  + "/magpie-applicant/" + Syno.splitPath( id ) + ".log";

        // 不换行, 一行一条记录
        tx = tx.trim().replaceAll("[\\r\\n]", "");

        // 写日志
        File fo = new File(fn);
        File fd = fo.getParentFile();
        if (!fd.exists()) {
             fd.mkdirs();
        }
        try (
            FileWriter fw = new FileWriter(fo, Charset.forName("utf-8"), true);
        ) {
            fw.write(tx+"\r\n");
        } catch (IOException e) {
            throw new CruxException(e);
        }

        // 写入流
        // 使用新数组以规避中途有退出的
        List<Consumer<String>> logz = getLogs( id );
        if (logz != null) {
            Consumer<String>[] logs = logz.toArray(
                new Consumer[logz.size()]
            );
            for (Consumer<String> log : logs) {
                log.accept(tx);
            }
        }

        helper.reply(Synt.mapOf("ok", true));
    }

    @Action("stream")
    public void stream(ActionHelper helper) throws CruxException {
        Map rd = helper.getRequestData();
        String id = Synt.asString(rd.get(Cnst.ID_KEY));
        String fn = System.getProperty("logs.dir", Core.DATA_PATH+"/log")
                  + "/magpie-applicant/" + Syno.splitPath( id ) + ".log";

        // 状态
        Data ent = Data.getInstance("centra/data/magpie", "applicant");
        Map  inf = ent.getOne(Synt.mapOf(
            Cnst.RB_KEY, Synt.setOf(Cnst.ID_KEY, "state", "mtime"),
            Cnst.ID_KEY, id
        ));
        int  sta = Synt.declare(inf.get("state"), 0);
        if ( sta == 0 ) {
            return;
        }

        // 输出流
        final Writer out;
        HttpServletResponse rsp;
        try {
            rsp = helper.getResponse();
            out = rsp.getWriter();
        } catch ( IOException e ) {
            throw new CruxException(e);
        }
        rsp.setHeader("Connection" , "keep-alive");
        rsp.setHeader("Cache-Control", "no-store");
        rsp.setContentType ( "text/event-stream" );
        rsp.setCharacterEncoding("UTF-8");
        Thread thr = Thread.currentThread(  );
        Consumer<String> log = new Consumer<String>() {
            @Override
            public void accept(String tx) {
                try {
                    out.write("data:{\"text\":\""+Dist.doEscape(tx)+"\"}\n\n");
                    out.flush();
                    if ("__DONE__".equals(tx)
                    ||  "__STOP__".equals(tx)
                    ||  thr.isInterrupted() ) {
                    CoreLogger.debug("{} DONE!!!!!!", id);
                        delLog(id, this);
                        out.close();
                    }
                } catch (Exception ex) {
                    try {
                        delLog(id, this);
                        out.close();
                    } catch ( IOException e ) {
                    }
                    throw new CruxExemption(ex);
                }
            }
        };

        if (! Synt.declare(rd.get("reflow"), false)) {
        if (sta == 1) {
            // 排队数
           long t = Synt.declare(inf.get("mtime"), 0L);
            int w = ent.search(Synt.mapOf(
                "mtime", Synt.mapOf(Cnst.LT_REL, t),
                "state", Synt.mapOf(Cnst.EQ_REL, 1),
                Cnst.ID_KEY, Synt.mapOf(Cnst.NE_REL, id)
            ), 0, 0).hits();
            int r = ent.search(Synt.mapOf(
                "state", Synt.mapOf(Cnst.EQ_REL, 2),
                Cnst.ID_KEY, Synt.mapOf(Cnst.NE_REL, id)
            ), 0, 0).hits();
            log.accept("WAITING " + w + " RUNNING " + r);
        } else {
            // 读日志
            File fo = new File(fn);
            if ( fo.exists()) {
            try (
                FileReader fr = new FileReader(fo, Charset.forName("utf-8"));
                BufferedReader br = new BufferedReader(fr);
            ) {
                String ln ;
                while((ln = br.readLine()) != null) {
                    log.accept (ln.trim());
                }
            } catch (IOException e) {
                throw new CruxException(e);
            } }
        }
        }

        if (sta == 1 || sta == 2) {
            // 登记流
            addLog(id, log);

            // 等待关闭流
            WHILE: while (true) {
                try {
                    inf = ent.getOne(Synt.mapOf(
                        Cnst.RB_KEY, Synt.setOf(Cnst.ID_KEY, "state"),
                        Cnst.ID_KEY, id
                    ));
                    sta = Synt.declare(inf.get("state"), 0);
                    switch (sta) {
                        case 1-> {
                            CoreLogger.debug("{} Waiting", id);
                            Thread.sleep(10000L);
                        }
                        case 2-> {
                            CoreLogger.debug("{} Running", id);
                            Thread.sleep(10000L);
                        }
                        default -> {
                            CoreLogger.debug("{} Stopped", id);
                            out.close();
                            break WHILE;
                        }
                    }
                }catch (IOException | InterruptedException ex) {
                    CoreLogger.debug(ex.toString());
                    break;
                }
            }
        } else {
            // 直接关闭流
            try {
                CoreLogger.debug("{} Stopped", id );
                out.close();
            } catch (IOException ex) {
                CoreLogger.debug(ex.toString());
            }
        }
    }

    private static List<Consumer<String>> getLogs(String id) {
        Gate.Leader lead = Gate.getLeader("magpie.applicant.logs");
        List<Consumer<String>> logs;

        lead.lockr();
        try {
            logs = LOGS.get(id);
        } finally {
            lead.unlockr();
        }

        return logs;
    }

    private static void addLog(String id, Consumer<String> lg) {
        Gate.Leader lead = Gate.getLeader("magpie.applicant.logs");
        List<Consumer<String>> logs;

        lead.lockw();
        try {
            logs = LOGS.get(id);

            if (logs == null) {
                logs = new ArrayList();
                LOGS.put ( id , logs );
            }
            logs.add( lg );
        } finally {
            lead.unlockw();
        }
    }

    private static void delLog(String id, Consumer<String> lg) {
        Gate.Leader lead = Gate.getLeader("magpie.applicant.logs");
        List<Consumer<String>> logs;

        lead.lockw();
        try {
            logs = LOGS.get(id);

            if (logs != null) {
                logs.remove(lg);
            if (logs.isEmpty()) {
                LOGS.remove(id);
            }}
        } finally {
            lead.unlockw();
        }
    }

}
