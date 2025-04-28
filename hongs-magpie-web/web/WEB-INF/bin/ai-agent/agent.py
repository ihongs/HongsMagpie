# coding=utf-8

import os
import re
import time
import json
import dotenv
import asyncio
import logging
import traceback
from typing             import Any
from urllib.request     import Request, urlopen 
from openpyxl           import Workbook
from langchain_openai   import ChatOpenAI
from browser_use        import Agent, Controller, ActionResult

dotenv.load_dotenv()

id = None
files = []
cache = {}
controller = Controller()

# 截获 Agent 日志
class MyLogHandler(logging.Handler, object):
    def __init__(self):
        logging.Handler.__init__(self)
    def emit(self, record):
            try:
                msg = self.format(record)
                if  id is None:
                    tim = time.strftime(r"%Y/%m/%d %H:%M:%S", time.localtime())
                    print(f'{tim} {record.levelname} {msg}')
                else:
                    reflow(id , f'{record.levelname} {msg}')
            except Exception:
                self.handleError (record)
loh = MyLogHandler()
log = logging.getLogger('browser_use.agent.service')
log.addHandler(loh)
log = logging.getLogger(__name__)
log.addHandler(loh)

def req(api:str, reqs:dict):
    '''
    执行线上动作
    api   动作路径
    reqs  请求数据
    返回响应数据
    '''
    key  = os.getenv('AGENT_AUTH_KEY')
    url  = os.getenv('AGENT_BASE_URL') +'/'+ api
    print(url, reqs)
    dats = bytes(json.dumps(reqs), encoding='utf-8')
    head = {
        'Authorization': 'Bearer ' + key ,
        'Content-Type' : 'application/json; charset=UTF-8',
        'Accept'       : 'application/json' ,
        'X-Requested-With': 'XMLHttpRequest',
    }
    req  = Request(url=url, data=dats, headers=head, method="POST")
    rsp  = urlopen(req)
    rsps = rsp .read ()
    rsps = json.loads(rsps.decode('utf-8'))
    if  rsps and not rsps.get("ok" , True ):
        log.error("Reqeust error("+rsps.get("ern", "")+")"+rsps.get("err", "")+" "+rsps.get("msg", "")+" API: "+api)
    return rsps

def accept():
    '接收任务'
    return req('centra/data/magpie/applicant/accept.act', {
        'agent' : os.getenv('AGENT_ID')
    })

def reflow(id:str, text:str):
    '回传过程'
    return req('centra/data/magpie/applicant/reflow.act', {
        'id'    : id,
        'text'  : text,
    })

def result(id:str, result:str, state=3):
    '提交结果'
    return req('centra/data/magpie/applicant/result.act', {
        'id'    : id,
        'result': result,
        'state' : state ,
    })

async def run(info:dict, conf:dict):
    agent = Agent(
        task            = info.get("prompt", ""),
        message_context = info.get("remind", ""),
        llm         = ChatOpenAI(
            model   = conf.get( "agent_mod" ),
            api_key = conf.get( "agent_key" ),
            base_url= conf.get( "agent_url" ),
        ),
        planner_llm = ChatOpenAI(
            model   = conf.get("planner_mod"),
            api_key = conf.get("planner_key"),
            base_url= conf.get("planner_url"),
        ),
        controller  = controller,
    )
    try:
        return await agent.run(
            max_steps   = info.get("max_steps", 100),
        )
    except KeyboardInterrupt as ex:
        agent.stop()
        raise ( ex )

@controller.registry.action("""
打印、输出内容或数, 无返回值
Args:
    data: 数据、内容
""")
async def echo(data:Any)->str:
    log(str(data))

@controller.registry.action("""
获取缓存数据
Args:
    key: 存储键、缓存标记
""")
async def get_cache(key:str)->Any:
    data = cache.get(key)
    return ActionResult(extracted_content=data, include_in_memory=True)

@controller.registry.action("""
存储缓存数据
Args:
    key: 存储键、缓存标记
    data: 数据、内容
""")
async def set_cache(key:str, data:Any)->str:
    cache[key] = data
    return ActionResult(extracted_content=f'已登记 {key}')

@controller.registry.action("""
写入 xlsx 文件
Args:
    name: 文件名称
    data: 二维数组, 示例: [["列名1", "列名2"], ["列1取值", "列2取值"]]
""")
async def save_to_xlsx(name:str, data:list) -> str:
    wb = Workbook()
    sh = wb.active
    for row in data:
        sh.append(row)
    wb.save(os.getenv('TMP_DIR') +'/'+ name)
    files.append(name)
    return ActionResult(extracted_content=f'已写入 {name}')

if __name__ == "__main__":
    end = 0
    while True:
        try:
            task = accept()
            if  not task:
                log.info("No task, waiting...")
                time.sleep(30)
                continue
            
            info = task.get("info")
            conf = task.get("conf")
            if  not info and not conf:
                log.info("No task, waiting...")
                time.sleep(30)
                continue

            ok = True
            id = info["id"]
            print(info, conf)

            try:
                his = asyncio.run(run(info, conf))
                res = his.final_result()
                if  his.is_successful ():
                    reflow(id, "__DONE__")
                    result(id, res, 3)
                else:
                    reflow(id, "__DONE__")
                    result(id, res, 4)
            except KeyboardInterrupt as ex:
                reflow(id, "__STOP__")
                result(id, '', 9)
                raise (ex)
            except Exception as ex:
                reflow(id, "__STOP__")
                result(id, '', 5)
                raise (ex)
        except KeyboardInterrupt:
            # 连按 ctrl+c 退出, 只按一次中止任务
            if  end < time.time() - 2:
                end = time.time()
            else:
                log("WARN", "Exit")
                break
        except :
            traceback.print_exc()
            time.sleep(30)
        finally:
            id = None
            files = []
            cache = {}
            