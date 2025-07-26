-- DB=magpie

--
-- 数据
--

DROP TABLE IF EXISTS "a_magpie_data";
CREATE TABLE "a_magpie_data" (
  "id" CHAR(16) NOT NULL,
  "form_id" CHAR(16) NOT NULL,
  "user_id" CHAR(16) NOT NULL,
  "name" VARCHAR(255) DEFAULT NULL,
  "memo" VARCHAR(255) DEFAULT NULL,
  "meno" VARCHAR(100) DEFAULT NULL,
  "data" LONGTEXT NOT NULL,
  "ctime" INTEGER NOT NULL,
  "etime" INTEGER NOT NULL,
  "rtime" INTEGER DEFAULT NULL, /* 从哪个时间点恢复 */
  "state" TINYINT DEFAULT '1',
  PRIMARY KEY ("form_id","id","ctime")
);

CREATE INDEX "IK_a_magpie_data_id" ON "a_magpie_data" ("id");
CREATE INDEX "IK_a_magpie_data_form" ON "a_magpie_data" ("form_id");
CREATE INDEX "IK_a_magpie_data_user" ON "a_magpie_data" ("user_id");
CREATE INDEX "IK_a_magpie_data_meno" ON "a_magpie_data" ("meno");
CREATE INDEX "IK_a_magpie_data_state" ON "a_magpie_data" ("state");
CREATE INDEX "IK_a_magpie_data_etime" ON "a_magpie_data" ("etime");
CREATE INDEX "IK_a_magpie_data_rtime" ON "a_magpie_data" ("rtime");
CREATE INDEX "IK_a_magpie_data_ctime" ON "a_magpie_data" ("ctime" DESC);
CREATE UNIQUE INDEX "UK_a_magpie_data_uk" ON "a_magpie_data" ("form_id","id","etime");

--
-- 用户
--

INSERT INTO "a_master_user" ("id","password","username","name","head","note","ctime","mtime","rtime","ptime","state") VALUES ('ai-agent','','','AI Agent',NULL,NULL,'1424075622','1424075622','0','0',1);
INSERT INTO "a_master_unit_user" VALUES ('ai-agent','0','0');
