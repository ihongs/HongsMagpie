#!/usr/bin/env python3
import os
import argparse
from dotenv import load_dotenv
from flask import Flask, request
from markitdown import MarkItDown
from werkzeug.utils import secure_filename

# 加载 .env 文件
load_dotenv()

app = Flask(__name__)

# 配置变量
CONFIG = {
    'addr': [ip.strip() for ip in os.getenv('ADDR', '').split(',') if ip.strip()],
    'auth': os.getenv('AUTH'),
    'port': int (os.getenv('PORT', '8080')),
    'tmp' : str (os.getenv('TMP', '/tmp' )),
    'ocr' : bool(os.getenv('OCR', 'False')),
    'llm_model': os.getenv('LLM_MODEL'),
    'llm_api_key': os.getenv('LLM_API_KEY'),
    'llm_base_url': os.getenv('LLM_BASE_URL')
}

# 确保上传目录存在
os.makedirs(CONFIG['tmp'], exist_ok=True)

# 验证 IP 白名单
def check_addr():
    if not CONFIG['addr']:
        return True
    addr = request.remote_addr
    return addr in CONFIG['addr']

# 验证 API 密钥
def check_auth():
    if not CONFIG['auth']:
        return True
    auth = request.headers.get('Authorization')
    if not auth:
        return False
    try:
        sch, key = auth.split(' ', 2)
        return sch == 'Bearer' and key == CONFIG['auth']
    except:
        return False

# 健康检查端点
@app.route('/alive', methods=['GET'])
def health():
    return 'ok'

# 处理文件上传和 Markdown 转换
@app.route('/parse', methods=['POST'])
def parse():
    # 检查 IP 白名单
    if not check_addr():
        return 'ERROR: IP not allowed'  , 403

    # 检查 API 密钥
    if not check_auth():
        return 'ERROR: Invalid auth key', 401
    
    # 检查文件
    if 'file' not in request.files:
        return 'ERROR: No file provided', 400
    
    file = request.files['file']
    if file.filename == '':
        return 'ERROR: No file selected', 400
    
    # 保存文件
    filename = secure_filename(file.filename)
    filepath = os.path.join(CONFIG['tmp'], filename)
    file.save(filepath)
    
    try:
        # 使用 MarkItDown 库处理文件
        if CONFIG['ocr']:
            # 启用 OCR 插件
            from openai import OpenAI
            md = MarkItDown(
                enable_plugins=True,
                llm_model=CONFIG['llm_model'],
                llm_client=OpenAI(
                    api_key=CONFIG['llm_api_key'],
                    base_url=CONFIG['llm_base_url']
                )
            )
        else:
            md = MarkItDown()
        
        data = md.convert(filepath)
        text = data.text_content
        
        return text
    except Exception as e:
        return f'ERROR: Processing error: {str(e)}', 500
    finally:
        # 清理临时文件
        if os.path.exists(filepath):
            os.remove(filepath)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Markdown Server')
    parser.add_argument('--addr', nargs='*', default=[], help='Allow IPs')
    parser.add_argument('--auth', type=str, default=None, help='Auth key')
    parser.add_argument('--port', type=int, default=CONFIG['port'], help='Server port')
    parser.add_argument('--tmp' , type=str, default=CONFIG['tmp' ], help='Upload tmp dir')
    parser.add_argument('--ocr' , action='store_true', help='Enable OCR')
    parser.add_argument('--llm-model'   , type=str, default=None, help='LLM Model'   )
    parser.add_argument('--llm-api-key' , type=str, default=None, help='LLM API key' )
    parser.add_argument('--llm-base-url', type=str, default=None, help='LLM base URL')
    
    args = parser.parse_args()
    
    # 更新配置
    CONFIG['port'] = args.port
    CONFIG['auth'] = args.auth
    CONFIG['addr'] = args.addr
    CONFIG['tmp' ] = args.tmp
    if args.ocr:
        CONFIG['ocr'] = True
    if args.llm_model:
        CONFIG['llm_model'] = args.llm_model
    if args.llm_api_key:
        CONFIG['llm_api_key'] = args.llm_api_key
    if args.llm_base_url:
        CONFIG['llm_base_url'] = args.llm_base_url
    
    # 确保上传目录存在
    os.makedirs(CONFIG['tmp'], exist_ok=True)
    
    # 启动服务器
    print(f"Starting Markdown Parser Server on port {CONFIG['port']}")
    
    app.run(host='0.0.0.0', port=CONFIG['port'], debug=False, threaded=True)
